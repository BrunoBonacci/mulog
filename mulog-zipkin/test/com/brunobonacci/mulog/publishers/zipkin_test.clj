(ns com.brunobonacci.mulog.publishers.zipkin-test
  (:require
   [clj-http.client :as http]
   [clj-test-containers.core :as tc]
   [com.brunobonacci.mulog :as u]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.mulog.flakes :as f]
   [com.brunobonacci.rdt :refer [repl-test]]))



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
      (http/get (str "http://" host ":" port "/")
        client-settings)
      :status
      (= 200))))


(repl-test {:labels [:container]} "test elasticsearch publisher on zipkin service"

  (def container (tc/create
                   {:image-name "openzipkin/zipkin:latest"
                    :exposed-ports [9411]
                    :env-vars {"STORAGE_TYPE" "mem"}
                    :wait-for {:strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 9411)))
  (def client-settings {:headers {"content-type" "application/json; charset=UTF-8"}
                        :accept :json})

  (wait-for-condition "Zipking service ready" (service-ready? host port client-settings))

  (def publisher (u/start-publisher! {:type :zipkin
                                      :url (str "http://" host ":" port "/")
                                      :publish-delay 500
                                      :http-opts client-settings}))


  (def test-id (f/snowflake))

  (u/trace :test/trace [:wait 100 :level :root :test-id test-id]
    (Thread/sleep 100)
    (u/trace :test/inner-trace [:wait 200 :level 1]
      (Thread/sleep 200)
      (u/trace :test/inner-trace [:wait 300 :level 2]
        (Thread/sleep 300))))

  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "traces are indexed"
    (fn []
      (->
        (http/get (str "http://" host ":" port "/api/v2/traces")
          {:query-params {"serviceName" "unknown"
                          "spanName" "test/trace"
                          "annotationQuery" (str "test-id=" test-id)}})
        :body
        json/from-json
        not-empty)))


  ;; search: event
  (def result
    (->
      (http/get (str "http://" host ":" port "/api/v2/traces")
        {:query-params {"serviceName" "unknown"
                        "spanName" "test/trace"
                        "annotationQuery" (str "test-id=" test-id)}})
      :body
      json/from-json))

  ;; one trace found
  (count result)
  => 1

  ;; three spans in that trace found
  (count (first result))
  => 3


  (->> result
    first
    (sort-by :timestamp)
    (mapv :name))
  => ["test/trace" "test/inner-trace" "test/inner-trace"]


  (->> result
    first
    (sort-by :timestamp)
    (mapv :tags)
    (mapv :level))
  => ["root" "1" "2"]

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container))
