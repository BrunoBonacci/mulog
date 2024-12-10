(ns com.brunobonacci.mulog.publishers.elasticsearch-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.common.json :as json]
            [com.brunobonacci.rdt :refer [repl-test]]
            [clj-test-containers.core :as tc]
            [clj-http.client :as http]
            [where.core :refer [where]]))



(defn wait-for-condition
  "retries the execution of `f` until it succeeds or times out after 60sec"
  [service f]
  (let [f (fn [] (try (f) true (catch Exception _ false)))
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
      (http/get (str (:protocol client-settings) "://" host ":" port "/")
        client-settings)
      :body
      json/from-json
      :tagline
      count)))


(repl-test {:labels [:container]} "test elasticsearch publisher on elasticsearch service"

  (def username "elastic")
  (def password "bitnami")

  (def container (tc/create
                   {:image-name "bitnami/elasticsearch:8.16.1"
                    :exposed-ports [9200]
                    :env-vars {"node.name" "node1"
                               "discovery.type" "single-node"
                               "ELASTICSEARCH_PASSWORD" password}
                    :wait-for {:strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 9200)))
  (def client-settings {:basic-auth [username password] :insecure? true
                        :protocol "http"
                        :headers {"content-type" "application/json; charset=UTF-8"}
                        :accept :json})

  (wait-for-condition "ELS-ready" (service-ready? host port client-settings))

  (def publisher (u/start-publisher! {:type :elasticsearch
                                      :url (str "http://" host ":" port "/")
                                      :publish-delay 500
                                      :http-opts client-settings}))

  (u/log :test/event :foo 1 :bar "2")

  (u/log :test/event2 :some {:object "value"})

  (u/trace :test/trace [:wait 100] (Thread/sleep 100))

  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "index-created"
    (fn []
      (when (->> (http/get (str "http://" host ":" port "/_cat/indices")
                 client-settings)
              :body
              json/from-json
              (filterv (where :index :starts-with? "mulog"))
              (empty?))
        (throw (ex-info "Index not created yet" {})))))


  ;; change the index refresh interval to 1s
  (http/put (str "http://" host ":" port "/mulog-*/_settings")
    (merge
      client-settings
      {:body-encoding "UTF-8"
       :body
       (json/to-json
         {:index
          {:refresh_interval "1s"}})}))

  => {:status 200}

  ;; this depends on the publish-delay + index refresh interval
  (wait-for-condition "index-refreshed"
    (fn []
      (when-not
          (pos?
            (->>
              (http/get (str "http://" host ":" port "/mulog-*/_search")
                (merge client-settings
                  {:body-encoding "UTF-8"
                   :body
                   (json/to-json {:query {:match_all {}}})}))
              :body
              json/from-json
              :hits
              :total
              :value))
        (throw (ex-info "Index not yet refreshed" {})))))


  ;; search: mulog.event_name.k:"test/event"
  (-> (http/post (str "http://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/event\""}}})}))
    :body
    json/from-json
    :hits)
  =>
  {:total {:value 1},
   :hits
   [{:_source
     {:mulog.event_name.k "test/event",
      :foo.i 1,
      :bar.s "2"}}]}


  ;; search: mulog.event_name.k:"test/event2"
  (-> (http/post (str "http://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/event2\""}}})}))
    :body
    json/from-json
    :hits)
  =>
  {:total {:value 1},
   :hits
   [{:_source
     {:mulog.event_name.k "test/event2",
      :some.o {:object.s "value"}}}]}


  ;; search: mulog.event_name.k:"test/trace"
  (-> (http/post (str "http://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/trace\""}}})}))
    :body
    json/from-json
    :hits)

  => {:total {:value 1},
     :hits
     [{:_source
       {:wait.i 100,
        :mulog.duration.i number?
        :mulog.trace_id string?
        :mulog.event_name.k "test/trace",
        :mulog.root_trace string?,
        :mulog.namespace.s string?
        :mulog.outcome.k "ok"}}]}


  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )






(repl-test {:labels [:container]} "test elasticsearch publisher on opensearch"

  (def username "admin")
  (def password "ChangeMe_2024")

  (def container (tc/create
                   {:image-name "opensearchproject/opensearch:2.18.0"
                    :exposed-ports [9200]
                    :env-vars {"node.name" "node1"
                               "discovery.type" "single-node"
                               "OPENSEARCH_INITIAL_ADMIN_PASSWORD" password}
                    :wait-for {:strategy :port :startup-timeout 60}}))

  (def container (tc/start! container))

  (def host (-> container :host))
  (def port (-> container :mapped-ports (get 9200)))
  (def client-settings {:basic-auth [username password] :insecure? true
                        :protocol "https"
                        :headers {"content-type" "application/json; charset=UTF-8"}
                        :accept :json})

  (wait-for-condition "ELS" (service-ready? host port client-settings))

  (def publisher (u/start-publisher! {:type :elasticsearch
                                      :url (str "https://" host ":" port "/")
                                      :publish-delay 500
                                      :http-opts client-settings}))

  (u/log :test/event :foo 1 :bar "2")

  (u/log :test/event2 :some {:object "value"})

  (u/trace :test/trace [:wait 100] (Thread/sleep 100))

  ;; wait for publisher to push the events and the index to be created
  (wait-for-condition "index-created"
    (fn []
      (when (->> (http/get (str "https://" host ":" port "/_cat/indices")
                 client-settings)
              :body
              json/from-json
              (filterv (where :index :starts-with? "mulog"))
              (empty?))
        (throw (ex-info "Index not created yet" {})))))

  ;; change the index refresh interval to 1s
  (http/put (str "https://" host ":" port "/mulog-*/_settings")
    (merge
      client-settings
      {:body-encoding "UTF-8"
       :body
       (json/to-json
         {:index
          {:refresh_interval "1s"}})}))

  => {:status 200}

  ;; this depends on the publish-delay + index refresh interval
  (wait-for-condition "index-refreshed"
    (fn []
      (when-not
          (pos?
            (->>
              (http/get (str "https://" host ":" port "/mulog-*/_search")
                (merge client-settings
                  {:body-encoding "UTF-8"
                   :body
                   (json/to-json {:query {:match_all {}}})}))
              :body
              json/from-json
              :hits
              :total
              :value))
        (throw (ex-info "Index not yet refreshed" {})))))


  ;; search: mulog.event_name.k:"test/event"
  (-> (http/post (str "https://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/event\""}}})}))
    :body
    json/from-json
    :hits)
  =>
  {:total {:value 1},
   :hits
   [{:_source
     {:mulog.event_name.k "test/event",
      :foo.i 1,
      :bar.s "2"}}]}


  ;; search: mulog.event_name.k:"test/event2"
  (-> (http/post (str "https://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/event2\""}}})}))
    :body
    json/from-json
    :hits)
  =>
  {:total {:value 1},
   :hits
   [{:_source
     {:mulog.event_name.k "test/event2",
      :some.o {:object.s "value"}}}]}


  ;; search: mulog.event_name.k:"test/trace"
  (-> (http/post (str "https://" host ":" port "/mulog-*/_search")
       (merge
         client-settings
         {:body-encoding "UTF-8"
          :body
          (json/to-json
            {:query
             {:query_string
              {:query "mulog.event_name.k:\"test/trace\""}}})}))
    :body
    json/from-json
    :hits)

  => {:total {:value 1},
     :hits
     [{:_source
       {:wait.i 100,
        :mulog.duration.i number?
        :mulog.trace_id string?
        :mulog.event_name.k "test/trace",
        :mulog.root_trace string?,
        :mulog.namespace.s string?
        :mulog.outcome.k "ok"}}]}


  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )



(comment
  ;; useful queries

  ;; list indices
  (->
    (http/get (str "https://" host ":" port "/_cat/indices")
      client-settings)
    :body
    json/from-json)


  ;; get index mapping
  (->
    (http/get (str "https://" host ":" port "/mulog-*/_mapping")
      client-settings)
    :body
    json/from-json)


  ;; search all docs
  (->
    (http/get (str "https://" host ":" port "/mulog-*/_search")
      (merge client-settings
        {:body-encoding "UTF-8"
         :body
         (json/to-json {:query {:match_all {}}})}))
    :body
    json/from-json)

  )
