(ns com.brunobonacci.mulog.publishers.kinesis
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.publishers.aws-utils :as awsutils]
            [cheshire.core :as json]
            [cheshire.generate :as gen]))

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
    (awsutils/publish! kinesis-client stream-name request)))

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

(def ^:const KINESIS-MAX-RECORDS-NUMBER 500) ;; Each PutRecords request can support up to 500 records.

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

;https://docs.aws.amazon.com/cli/latest/reference/kinesis/put-records.html
(defn kinesis-publisher
  [{:keys [stream-name max-items] :as config}]
  {:pre [stream-name]}
  (let [cfg (as-> config $
               (merge DEFAULT-CONFIG $)
               (update $ :max-items min KINESIS-MAX-RECORDS-NUMBER))]
    (if (and
          (not (nil? max-items))
          (> max-items KINESIS-MAX-RECORDS-NUMBER))
      (println (format "!!! Provided %d buffer size will be capped to maximum allowed 500" max-items)))
    (KinesisPublisher.
      cfg
      (rb/agent-buffer 10000)
      (or (:transform cfg) identity)
      (awsutils/create-kinesis-client (:kinesis-client-params cfg)))))