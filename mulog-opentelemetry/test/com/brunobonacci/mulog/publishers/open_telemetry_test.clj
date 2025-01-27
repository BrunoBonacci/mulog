(ns com.brunobonacci.mulog.publishers.open-telemetry-test
  (:require
   [clj-http.client :as http]
   [clj-test-containers.core :as tc]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.brunobonacci.mulog :as u]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.mulog.flakes :as f]
   [com.brunobonacci.mulog.publishers.open-telemetry :as ot]
   [com.brunobonacci.rdt :refer [repl-test]]
   [where.core :refer [where]]))



(defn wait-for-condition
  "retries the execution of `f` until it succeeds or times out after 60sec"
  [service f]
  (let [f (fn [] (try (f) (catch Exception _ false)))
        start (System/currentTimeMillis)]
    (loop [ready (f)]
      (if (> (System/currentTimeMillis) (+ start 60000))
        (throw (ex-info (str "Waiting for " service " to meet the required condition, but timed out.") {}))
        (when (not ready)
          (Thread/sleep 500)
          (recur (f)))))))



(defn rm-fr
  "Deletes all the files and directories recursively from the given base.
   with `:force true` it won't raise exception on failure.
  "
  [base & {:keys [force] :or {force true}}]
  (let [^java.io.File f (io/file base)]
    (when (.isDirectory f)
      (run! #(rm-fr % :force force) (.listFiles f)))
    (io/delete-file f force)))



(defn service-ready?
  [host port client-settings]
  (fn []
    (->
      ;; undocumented api: https://www.jaegertracing.io/docs/2.1/apis/#http-json
      ;; best I can do is to lookup for a trace that doesn't exists
      ;; and check if I get a 404 instead of a 500
      (http/get (str "http://" host ":" port "/api/traces/b0f60d3fa82635a6ebe3f526861a84b3")
        (merge client-settings
          {:accept :json
           :throw-exceptions false}))
      :status
      (= 404))))



(repl-test {:labels [:container]} "test OpenTelemetry publisher on Jaeger via OTLP collector"

  (def container (tc/create
                   {:image-name    "jaegertracing/all-in-one:1.64.0"
                    :exposed-ports [4318 16686]
                    :env-vars      {"COLLECTOR_OTLP_ENABLED" "true"}
                    :wait-for      {:wait-strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 4318)))
  (def port2 (-> container :mapped-ports (get 16686)))
  (def client-settings {})

  ;; get the UI URL
  ;; (println (str "http://" host ":" port2 "/")),

  (wait-for-condition "opentelemetry service ready" (service-ready? host port2 {}))


  (def publisher (u/start-publisher!
                   {:type          :open-telemetry
                    :send          :traces
                    :url           (str "http://" host ":" port "/")
                    :publish-delay 500}))

  (def global (u/global-context))
  (u/set-global-context!
    {:app-name "test" :version "1.2.3" :env "test-container"})

  (def test-id (f/flake))

  (u/with-context {:mulog/root-trace test-id}
    (u/trace :test/trace [:wait 100 :level :root]
      (Thread/sleep 100)
      (u/trace :test/inner-trace [:wait 200 :level 1]
        (Thread/sleep 200)
        (try
          (u/trace :test/inner-trace
            [:wait 300 :level 2
             :factor1          3.5
             :factor2          2/5
             :test?            true
             :missing          nil
             :simplearray      ["one" "two" "three"]
             :array            [1 "two" true {:four 5} nil "last"]
             :obj              {:foo "bar" :baz nil}
             :set              #{"blue" "green" 3}]
            (Thread/sleep 300)
            (throw (ex-info "BOOOM" {})))
          (catch Exception _ nil)))))


  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "traces are indexed"
    (fn []
      (->
        (http/get (str "http://" host ":" port2 "/api/traces/" (#'ot/hexify test-id 32))
          (merge client-settings
            {:accept :json}))
        :body
        json/from-json)))


  ;; search: event
  (def result
    (->
      (http/get (str "http://" host ":" port2 "/api/traces/" (#'ot/hexify test-id 32))
        (merge client-settings
          {:accept :json}))
      :body
      json/from-json
      :data))

  ;; restore global context
  (u/set-global-context! global)

  ;; one trace found
  (count result)
  => 1

  ;; three spans in that trace found
  (count (:spans (first result)))
  => 3


  (def spans
    (->> result
      first
      :spans
      (sort-by :startTime)))

  ;; testing order
  (mapv :operationName spans)
  => ["test/trace" "test/inner-trace" "test/inner-trace"]

  (mapv (comp :value first (partial filterv (where :key :is? "level")) :tags) spans)
  => ["root" 1 2]


  ;; testing outcome
  (mapv (comp :value first (partial filterv (where :key :is? "otel.status_code")) :tags) spans)
  => ["OK" "OK" "ERROR"]

  (mapv (comp :value first (partial filterv (where :key :is? "otel.status_description")) :tags) spans)
  => [nil nil "BOOOM"]


  ;; testing global-env
  (mapv (comp :value first (partial filterv (where :key :is? "app-name")) :tags) spans)
  => ["test" "test" "test"]

  (mapv (comp :value first (partial filterv (where :key :is? "version")) :tags) spans)
  => ["1.2.3" "1.2.3" "1.2.3"]

  (mapv (comp :value first (partial filterv (where :key :is? "env")) :tags) spans)
  => ["test-container" "test-container" "test-container"]


  ;; testing types. (API doesn't return the actual types, but in the UI are displayed correctly),
  (->> spans
    last
    :tags
    (map (juxt :key :type))
    (into {}))
  => {"test?"                   "bool",
     "factor2"                 "float64",
     "mulog/outcome"           "string",
     "app-name"                "string",
     "obj"                     "string",
     "otel.scope.name"         "string",
     "internal.span.format"    "string",
     "exception"               "string",
     "error"                   "bool",
     "mulog/timestamp"         "int64",
     "otel.scope.version"      "string",
     "span.kind"               "string",
     "mulog/namespace"         "string",
     "level"                   "int64",
     "simplearray"             "string",
     "wait"                    "int64",
     "mulog/root-trace"        "string",
     "factor1"                 "float64",
     "mulog/parent-trace"      "string",
     "env"                     "string",
     "version"                 "string",
     "mulog/duration"          "int64",
     "set"                     "string",
     "mulog/event-name"        "string",
     "mulog/trace-id"          "string",
     "array"                   "string",
     "otel.status_description" "string",
     "otel.status_code"        "string"}

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )



(defn parse-file
  [file]
  (try
    (some->> (io/file file)
      (slurp)
      (str/split-lines)
      (mapv json/from-json)
      (mapcat (fn [line]
                (or
                  ((comp :scopeSpans first :resourceSpans) line)
                  ((comp :scopeLogs first :resourceLogs) line)))))
    (catch Exception _ nil)))



(repl-test {:labels [:container]} "test OpenTelemetry publisher to OTLP collector [traces]"

  (def test-id (f/flake))
  (def test-dir (str "/tmp/optel-" test-id))
  (io/make-parents (io/file (str test-dir "/test")))
  (io/copy (io/file "./test-config.yml") (io/file (str test-dir "/config.yml")))

  (def container (-> (tc/create
                      {:image-name    "otel/opentelemetry-collector-contrib:0.118.0"
                       :exposed-ports [4318]
                       :command       ["--config=/out/config.yml"]
                       :wait-for      {:wait-strategy :log
                                       :message "Everything is ready."
                                       :startup-timeout 60}})
                   (tc/bind-filesystem! {:host-path      test-dir
                                         :container-path "/out"
                                         :mode           :read-write})))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 4318)))

  (def client-settings {})

  (wait-for-condition "opentelemetry service ready"
    (fn []
      (try (.exists (io/file (str test-dir "/optel.json")))
           (catch Exception _ false))))

  (def publisher (u/start-publisher!
                   {:type          :open-telemetry
                    :send          :traces
                    :url           (str "http://" host ":" port "/")
                    :publish-delay 500}))

  (def global (u/global-context))
  (u/set-global-context!
    {:app-name "test" :version "1.2.3" :env "test-container"})


  (u/with-context {:mulog/root-trace test-id}
    (u/trace :test/trace [:wait 100 :level :root]
      (Thread/sleep 100)
      (u/trace :test/inner-trace [:wait 200 :level 1]
        (Thread/sleep 200)
        (try
          (u/trace :test/inner-trace
            [:wait 300 :level 2
             :factor1          3.5
             :factor2          2/5
             :test?            true
             :missing          nil
             :simplearray      ["one" "two" "three"]
             :array            [1 "two" true {:four 5} nil "last"]
             :obj              {:foo "bar" :baz nil}
             :set              #{"blue" "green" 3}]
            (Thread/sleep 300)
            (throw (ex-info "BOOOM" {})))
          (catch Exception _ nil)))))


  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "traces are indexed"
    (fn []
      (not-empty (parse-file (str test-dir "/optel.json")))))


  ;; load events
  (def result (parse-file (str test-dir "/optel.json")))

  ;; restore global context
  (u/set-global-context! global)

  ;; one trace found
  (count result)
  => 1

  ;; three spans in that trace found
  (count (:spans (first result)))
  => 3


  (def spans
    (->> result
      first
      :spans
      (sort-by :startTimeUnixNano)))

  ;; testing order
  (mapv :name spans)
  => ["test/trace" "test/inner-trace" "test/inner-trace"]

  (mapv (comp :value first (partial filterv (where :key :is? "level")) :attributes) spans)
  => [{:stringValue "root"} {:intValue "1"} {:intValue "2"}]

  ;; testing outcome
  (mapv :status spans)
  => [{:code 1} {:code 1} {:message "BOOOM", :code 2}]


  ;; testing global-env
  (mapv (comp :stringValue :value first (partial filterv (where :key :is? "app-name")) :attributes) spans)
  => ["test" "test" "test"]

  (mapv (comp :stringValue :value first (partial filterv (where :key :is? "version")) :attributes) spans)
  => ["1.2.3" "1.2.3" "1.2.3"]

  (mapv (comp :stringValue :value first (partial filterv (where :key :is? "env")) :attributes) spans)
  => ["test-container" "test-container" "test-container"]


  ;; testing types. (API doesn't return the actual types, but in the UI are displayed correctly),
  (->> spans
    last
    :attributes
    (map (juxt :key (comp first keys :value)))
    (into {}))
  => {"test?"              :boolValue,
     "factor2"            :doubleValue,
     "mulog/outcome"      :stringValue,
     "app-name"           :stringValue,
     "obj"                :kvlistValue,
     "exception"          :stringValue,
     "error"              :stringValue,
     "mulog/timestamp"    :intValue,
     "mulog/namespace"    :stringValue,
     "level"              :intValue,
     "simplearray"        :arrayValue,
     "wait"               :intValue,
     "mulog/root-trace"   :stringValue,
     "factor1"            :doubleValue,
     "mulog/parent-trace" :stringValue,
     "env"                :stringValue,
     "version"            :stringValue,
     "mulog/duration"     :intValue,
     "set"                :arrayValue,
     "mulog/event-name"   :stringValue,
     "mulog/trace-id"     :stringValue,
     "array"              :arrayValue}


  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  (rm-fr test-dir))



(repl-test {:labels [:container]} "test OpenTelemetry publisher to OTLP collector [logs]"

  (def test-id (f/flake))
  (def test-dir (str "/tmp/optel-" test-id))
  (io/make-parents (io/file (str test-dir "/test")))
  (io/copy (io/file "./test-config.yml") (io/file (str test-dir "/config.yml")))

  (def container (-> (tc/create
                      {:image-name    "otel/opentelemetry-collector-contrib:0.118.0"
                       :exposed-ports [4318]
                       :command       ["--config=/out/config.yml"]
                       :wait-for      {:wait-strategy :log
                                       :message "Everything is ready."
                                       :startup-timeout 60}})
                   (tc/bind-filesystem! {:host-path      test-dir
                                         :container-path "/out"
                                         :mode           :read-write})))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 4318)))

  (def client-settings {})

  (wait-for-condition "opentelemetry service ready"
    (fn []
      (try (.exists (io/file (str test-dir "/optel.json")))
           (catch Exception _ false))))

  (def publisher (u/start-publisher!
                   {:type          :open-telemetry
                    :send          :logs
                    :url           (str "http://" host ":" port "/")
                    :publish-delay 500}))

  (def global (u/global-context))
  (u/set-global-context!
    {:app-name "test" :version "1.2.3" :env "test-container"})

  (u/log :test/event
    :test-id          test-id
    :factor1          3.5
    :factor2          2/5
    :test?            true
    :missing          nil
    :simplearray      ["one" "two" "three"]
    :array            [1 "two" true {:four 5} nil "last"]
    :obj              {:foo "bar" :baz nil}
    :set              #{"blue" "green" 3})

  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "traces are indexed"
    (fn []
      (not-empty (parse-file (str test-dir "/optel.json")))))


  ;; load events
  (def result (parse-file (str test-dir "/optel.json")))

  ;; restore global context
  (u/set-global-context! global)

  ;; one trace found
  (count result)
  => 1

  ;; three spans in that trace found
  (count (:logRecords (first result)))
  => 1


  (def log-entry (first (:logRecords (first result))))

  (update log-entry :attributes (partial sort-by :key))
  => {:timeUnixNano string?
     :observedTimeUnixNano string?
     :body {:stringValue "test/event"},
     :attributes
     [{:key "app-name", :value {:stringValue "test"}}
      {:key "array",
       :value
       {:arrayValue
        {:values
         [{:intValue "1"}
          {:stringValue "two"}
          {:boolValue true}
          {:kvlistValue
           {:values [{:key "four", :value {:intValue "5"}}]}}
          {:stringValue "null"}
          {:stringValue "last"}]}}}
      {:key "env", :value {:stringValue "test-container"}}
      {:key "factor1", :value {:doubleValue 3.5}}
      {:key "factor2", :value {:doubleValue 0.4}}
      {:key "mulog/event-name", :value {:stringValue "test/event"}}
      {:key "mulog/namespace",
       :value
       {:stringValue
        "com.brunobonacci.mulog.publishers.open-telemetry-test"}}
      {:key "mulog/timestamp", :value {:intValue string?}}
      {:key "mulog/trace-id",
       :value {:stringValue string?}}
      {:key "obj",
       :value
       {:kvlistValue
        {:values [{:key "foo", :value {:stringValue "bar"}}]}}}
      {:key "set",
       :value
       {:arrayValue
        {:values
         [{:stringValue "blue"}
          {:stringValue "green"}
          {:intValue "3"}]}}}
      {:key "simplearray",
       :value
       {:arrayValue
        {:values
         [{:stringValue "one"}
          {:stringValue "two"}
          {:stringValue "three"}]}}}
      {:key "test-id",
       :value {:stringValue (str test-id)}}
      {:key "test?", :value {:boolValue true}}
      {:key "version", :value {:stringValue "1.2.3"}}],
     :traceId "",
     :spanId ""}


  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  (rm-fr test-dir))
