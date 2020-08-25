(ns com.brunobonacci.mulog.publishers.kinesis
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f]
            [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.common.json :as json]
            [cognitect.aws.client.api :as aws]))



(defn- has-failures?
  [rs]
  (not
    (and
      (contains? rs :FailedRecordCount)
      (zero? (:FailedRecordCount rs)))))



(defn- create-kinesis-client
  [params]
  (aws/client params))



;; https://docs.aws.amazon.com/cli/latest/reference/kinesis/put-records.html
(defn- publish!
  [kinesis-client stream-name records]
  (let [rs (aws/invoke kinesis-client {:op      :PutRecords
                                       :request {:StreamName stream-name
                                                 :Records    records}})]
    (if (has-failures? rs)
      (throw
        (ex-info
          (str "μ/log kinesis publisher failure, stream '" stream-name "'")
          {:rs rs})))))



(defn- put-records
  [kinesis-client {:keys [stream-name key-field format] :as config} records]
  (let [fmt* (if (= :json format) json/to-json ut/edn-str)
        request     (->> records
                      ;; partition-key is required in Kinesis, so in
                      ;; its absence we generate a snowflake to ensure
                      ;; a uniform random distribution across partitions
                      (map (juxt #(str (or (get % key-field) (f/flake))) fmt*))
                      (map (fn [[k v]]  {:PartitionKey k :Data v})))]
    (publish! kinesis-client stream-name request)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                                ;;
;;                         ----==| K I N E S I S |==----                          ;;
;;                                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype KinesisPublisher [config buffer transform kinesis-client]

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
        ;; else send to kinesis
        (do
          (put-records kinesis-client config (transform (map second items)))
          (rb/dequeue buffer last-offset))))))



(def ^:const KINESIS-MAX-RECORDS-NUMBER
  "Each PutRecords request can support up to 500 records. (AWS limit)"
  500)



(def ^:const DEFAULT-CONFIG
  {;; name of the stream where to send the data (REQUIRED)
   ;;:stream-name       "mulog"
   :key-field             :mulog/trace-id
   :max-items             KINESIS-MAX-RECORDS-NUMBER
   :publish-delay         1000
   :format                :json
   ;; function to transform records
   :transform             identity
   :kinesis-client-config {:api :kinesis}
   })



(defn kinesis-publisher
  [{:keys [stream-name max-items] :as config}]
  {:pre [stream-name]}
  (let [cfg (as-> config $
              (merge DEFAULT-CONFIG $)
              (update $ :max-items min KINESIS-MAX-RECORDS-NUMBER))]

    (KinesisPublisher.
      cfg
      (rb/agent-buffer 10000)
      (or (:transform cfg) identity)
      (create-kinesis-client (:kinesis-client-config cfg)))))
