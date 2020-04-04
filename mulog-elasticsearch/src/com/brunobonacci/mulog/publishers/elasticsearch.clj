(ns com.brunobonacci.mulog.publishers.elasticsearch
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.levels :as lvl]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.publishers.util :as u]
            [clj-http.client :as http]
            [cheshire.core :as json]
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



(defn- index-name
  ([]
   (index-name "'mulog-'yyyy.MM.dd"))
  ([pattern]
   (let [fmt (tf/formatter pattern)]
     (fn [ts] (tf/unparse fmt (tc/from-long ts))))))



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



;; TODO: handle records which can't be serialized.
(defn- to-json
  [m]
  (json/generate-string m {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSSX"}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;         ----==| P O S T   T O   E L A S T I C S E A R C H |==----          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- prepare-records
  [{:keys [index* name-mangling els-version] :as config} records]
  (let [mangler (if name-mangling mangle-map identity)]
    (->> records
       (mapcat (fn [{:keys [mulog/timestamp] :as r}]
                 (let [index   (index* timestamp)
                       ;; https://www.elastic.co/guide/en/elasticsearch/reference/7.x/removal-of-types.html
                       idx-map (if (= els-version :v6.x) {:_index index :_type "_doc"} {:_index index})]
                   [(str (json/generate-string {:index idx-map}) \newline)
                    (-> r
                       (mangler)
                       (dissoc :mulog/timestamp)
                       (assoc "@timestamp" (format-date-from-long timestamp))
                       (ut/remove-nils)
                       (to-json)
                       (#(str % \newline)))]))))))



(defn- post-records
  [{:keys [url publish-delay] :as config} records]
  (http/post
   url
   {:content-type "application/x-ndjson"
    :accept :json
    :as :json
    :socket-timeout publish-delay
    :connection-timeout publish-delay
    :body
    (->> (prepare-records config records)
       (apply str))}))



(comment

  (prepare-records
   {:url "http://localhost:9200/_bulk"
    :index* (index-name)
    :name-mangling true}
   [{:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k 1}
    {:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k nil}])


  (post-records
   {:url "http://localhost:9200/_bulk"
    :index* (index-name)
    :name-mangling true}
   [{:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k 1 :r1 (rand) :r2 (rand-int 100)}
    {:mulog/timestamp (System/currentTimeMillis) :event-name :hello :k nil :r1 (rand) :r2 (rand-int 100)}])
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| E L A S T I C S E A R C H |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype ElasticSearchPublisher
    [config buffer transform]


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
          (post-records config (transform (map second items)))
          (rb/dequeue buffer last-offset))))))



(def ^:const DEFAULT-CONFIG
  {:max-items     5000
   :publish-delay 5000
   :index-pattern "'mulog-'yyyy.MM.dd"
   :name-mangling true
   :els-version   :v7.x   ;; one of: `:v6.x`, `:v7.x`
   :level nil
   ;; function to transform records
   :transform identity
   })



(defn elasticsearch-publisher
  [{:keys [url max-items index-pattern] :as config}]
  {:pre [url]}
  (let [f (lvl/->filter (:level config))
        ->transform (fnil (fn [t] (comp t f)) f)]
    (ElasticSearchPublisher.
     (as-> config $
       (merge DEFAULT-CONFIG $)
       (update $ :url normalize-endpoint-url)
       (assoc $ :index* (index-name (:index-pattern $))))
     (rb/agent-buffer 20000)
     (->transform (:transform config)))))
