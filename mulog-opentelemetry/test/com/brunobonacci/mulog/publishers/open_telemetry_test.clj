(ns com.brunobonacci.mulog.publishers.open-telemetry-test
  (:require
   [clj-http.client :as http]
   [clj-test-containers.core :as tc]
   [com.brunobonacci.mulog.publishers.open-telemetry :as ot]
   [com.brunobonacci.mulog :as u]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.mulog.flakes :as f]
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


(repl-test {:labels [:container]} "test OpenTelemetry publisher on OTLP collector"

  (def container (tc/create
                   {:image-name    "jaegertracing/all-in-one:1.64.0"
                    :exposed-ports [4318 16686]
                    :env-vars      {"COLLECTOR_OTLP_ENABLED" "true"}
                    :wait-for      {:strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 4318)))
  (def port2 (-> container :mapped-ports (get 16686)))
  (def client-settings {})

  ;; get the UI URL
  ;; (println (str "http://" host ":" port2 "/"))

  (wait-for-condition "opentelemetry service ready" (service-ready? host port2 {}))

  #_(def publisher (u/start-publisher!
                     {:type           :open-telemetry
                      :url            (str "http://" host ":" port "/")
                      "publish-delay" 500}))

  (def publisher (u/start-publisher!
                   {:type :inline
                    :publisher
                    (ot/open-telemetry-publisher
                      {:type          :open-telemetry
                       :url           (str "http://" host ":" port "/")
                       :publish-delay 500})}))

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


  ;; testing types. (API doesn't return the actual types, but in the UI are diplayed correctly)
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
