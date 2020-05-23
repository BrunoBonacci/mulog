(ns com.brunobonacci.mulog.publishers.kinesis
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.publisher :as p]
            [cognitect.aws.client.api :as aws]
            [cheshire.core :as json]
            [cheshire.generate :as gen]))



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



;;
;; Add Flake encoder to JSON generator
;;
(gen/add-encoder com.brunobonacci.mulog.core.Flake
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (str x))))



(defn- put-records
  [kinesis-client {:keys [stream-name partition-key-name format] :as config} records]
  (let [key-field partition-key-name
        fmt* (if (= :json format) json/generate-string ut/edn-str)
        request     (->> records
                      (map (juxt #(str (get % key-field)) fmt*))
                      (map (fn [[k v]]  {:PartitionKey k :Data v})))]
    (publish! kinesis-client stream-name request)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                                ;;
;;                         ----==| K I N E S I S |==----                          ;;
;;                                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype KinesisPublisher
    [config buffer transform kinesis-client]

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
  {
   :partition-key-name  :mulog/trace-id
   :max-items           KINESIS-MAX-RECORDS-NUMBER
   :publish-delay       5000
   :format    :json
   ;; function to transform records
   :transform               identity
   :kinesis-client-params   {:api  :kinesis}
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
     (create-kinesis-client (:kinesis-client-params cfg)))))
