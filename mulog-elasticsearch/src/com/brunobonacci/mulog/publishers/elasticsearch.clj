(ns com.brunobonacci.mulog.publishers.elasticsearch
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.publishers.util :as u]
            [com.brunobonacci.mulog.common.json :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.walk :as w]))



(def date-time-formatter
  "the ISO8601 date format with milliseconds"
  (tf/formatters :date-time))



(defn format-date-from-long
  [timestamp]
  (->> timestamp
    (tc/from-long)
    (tf/unparse date-time-formatter)))



(defn- normalize-endpoint-url
  [url]
  (cond
    (str/ends-with? url "/_bulk") url
    (str/ends-with? url "/") (str url "_bulk")
    :else (str url "/_bulk")))



(defmulti index-name first)


(defmethod index-name :index-pattern [[_ v]]
  (let [fmt (tf/formatter v)]
    {:op :index :index* (fn [ts] (tf/unparse fmt (tc/from-long ts)))}))


(defmethod index-name :data-stream [[_ v]] {:op :create :index* (constantly v)})


(defmethod index-name :default [_] (index-name [:index-pattern "'mulog-'yyyy.MM.dd"]))



(defn mangle-map
  [m]
  (let [mangler (comp u/type-mangle u/snake-case-mangle)]
    (w/postwalk
      (fn [i]
        (if (map? i)
          (->> i
            (map (fn [entry] (mangler entry)))
            (into {}))
          i))
      m)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;         ----==| P O S T   T O   E L A S T I C S E A R C H |==----          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- prepare-records
  [{:keys [op index* name-mangling els-version]} records]
  (let [mangler (if name-mangling mangle-map identity)]
    (->> records
      (mapcat (fn [{:keys [mulog/timestamp mulog/trace-id] :as r}]
                (let [metaidx (merge
                                {:_index (index* timestamp)}
                                ;; if the trace-id is available use it as ELS _id
                                (when trace-id {:_id (str trace-id)})
                                ;; https://www.elastic.co/guide/en/elasticsearch/reference/7.x/removal-of-types.html
                                (when (= els-version :v6.x) {:_type "_doc"}))]
                  [(str (json/to-json (hash-map op metaidx)) \newline)
                   (-> r
                     (mangler)
                     (dissoc :mulog/timestamp)
                     (assoc "@timestamp" (format-date-from-long timestamp))
                     (ut/remove-nils)
                     (json/to-json)
                     (#(str % \newline)))]))))))



(defn- post-records
  [{:keys [url publish-delay http-opts] :as config} records]
  (http/post
   (normalize-endpoint-url url)
   (merge {:content-type "application/x-ndjson"
           :accept :json
           :as :json
           :socket-timeout publish-delay
           :connection-timeout publish-delay
           :body
           (->> (prepare-records config records)
                (apply str))}
          http-opts)))



(defn detect-els-version
  "It contacts the ELS API and retrieve the major version group"
  [url http-opts]
  (some->
   (http/get
    url
    (merge http-opts
           {:content-type "application/json"
            :accept :json
            :as :json
            :socket-timeout 500
            :connection-timeout 500
            :throw-exceptions false
            :ignore-unknown-host? true}))
   :body
   :version
   :number
   (str/split #"\.")
   first
   (#(format "v%s.x" %))
   keyword))



(def ^:const DEFAULT-CONFIG
  {;; :url endpoint for Elasticsearch
   ;; :url "http://localhost:9200/" ;; REQUIRED
   :max-items     5000
   :publish-delay 5000
   :name-mangling true
   :http-opts {}
   :els-version   :auto   ;; one of: `:v6.x`, `:v7.x`, `:auto`
   ;; function to transform records
   :transform     identity})



(defn make-config [{:keys [url] :as config}]
  {:pre [url]}
  (as-> config $
    (merge DEFAULT-CONFIG
           $
           (-> (select-keys $ [:index-pattern :data-stream]) first index-name))
      ;; autodetect version when set to `:auto`
    (update $ :els-version (fn [v] (if (= v :auto) (or (detect-els-version url (:http-opts $)) :v7.x) v)))))



(comment


  (make-config {:url "http://localhost:9200" :data-stream "tutorial-100"})


  (prepare-records
   (make-config
    {:url "http://localhost:9200/_bulk"
     :name-mangling true})
   [{:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k 1}
    {:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k nil}])


  (post-records
   (make-config
    {:url "http://localhost:9200"
     :data-stream "tutorial-100"
     :name-mangling true})
   [{:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k 1 :r1 (rand) :r2 (rand-int 100)}
    {:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k nil :r1 (rand) :r2 (rand-int 100)}])


  (post-records
   (make-config
    {:url "http://localhost:9200/_bulk"
     :name-mangling true})
   [{:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k 1 :r1 (rand) :r2 (rand-int 100)}
     {:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k nil :r1 (rand) :r2 (rand-int 100)}])
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| E L A S T I C S E A R C H |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype ElasticsearchPublisher [config buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    (:publish-delay config))


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)]
      (if-not (seq items)
        buffer
        ;; else send to ELS
        (do
          (some->> (seq (transform (map second items)))
            (post-records config))
          (rb/dequeue buffer last-offset))))))


(defn elasticsearch-publisher
  [{:keys [url] :as config}]
  {:pre [url]}
  (ElasticsearchPublisher.
   (make-config config)
   (rb/agent-buffer 20000)
   (or (:transform config) identity)))
