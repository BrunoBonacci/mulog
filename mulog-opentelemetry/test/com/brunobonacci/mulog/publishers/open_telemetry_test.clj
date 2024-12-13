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
                   {:image-name "jaegertracing/all-in-one:1.64.0"
                    :exposed-ports [4318 16686]
                    :env-vars {"COLLECTOR_OTLP_ENABLED" "true"}
                    :wait-for {:strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 4318)))
  (def port2 (-> container :mapped-ports (get 16686)))
  (def client-settings {})

  ;; get the UI URL
  ;; (println (str "http://" host ":" port2 "/"))

  (wait-for-condition "opentelemetry service ready" (service-ready? host port2 {}))

  #_(def publisher (u/start-publisher!
                     {:type :open-telemetry
                      :url (str "http://" host ":" port "/")
                      "publish-delay" 500}))

  (def publisher (u/start-publisher!
                   {:type :inline
                    :publisher
                    (ot/open-telemetry-publisher
                      {:type :open-telemetry
                       :url (str "http://" host ":" port "/")
                       :publish-delay 500})}))


  (def test-id (f/flake))

  (u/with-context {:mulog/root-trace test-id}
    (u/trace :test/trace [:wait 100 :level :root]
      (Thread/sleep 100)
      (u/trace :test/inner-trace [:wait 200 :level 1]
        (Thread/sleep 200)
        (u/trace :test/inner-trace [:wait 300 :level 2]
          (Thread/sleep 300)))))

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

  (mapv :operationName spans)
  => ["test/trace" "test/inner-trace" "test/inner-trace"]


  (mapv (comp :value first (partial filterv (where :key :is? "level")) :tags) spans)
  => ["root" 1 2]

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )
