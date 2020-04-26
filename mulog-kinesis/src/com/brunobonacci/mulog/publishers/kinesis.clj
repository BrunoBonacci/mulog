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
  [{:keys [stream-name partition-key-name format max-items] :as config} records]
  (let [key-field partition-key-name
        fmt* (if (= :json format) json/generate-string ut/edn-str)]
    (->> records
         (map (juxt #(str (get % key-field)) fmt*))
         (map (fn [[k v]] (awsutils/create-record! k v)))
         (partition-all max-items)
         (map #(awsutils/publish! stream-name %))
         (doall))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                                ;;
;;                         ----==| K I N E S I S |==----                          ;;
;;                                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype KinesisPublisher
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
        ;; else send to kinesis
        (do
          (put-records config (transform (map second items)))
          (rb/dequeue buffer last-offset))))))

(def ^:const DEFAULT-CONFIG
  {
   :partition-key-name  :mulog/trace-id
   :max-items           500
   :publish-delay       5000
   :format    :json
   ;; function to transform records
   :transform           identity
   })

;https://docs.aws.amazon.com/cli/latest/reference/kinesis/put-records.html
(defn kinesis-publisher
  [{:keys [stream-name] :as config}]
  {:pre [stream-name]}
  (KinesisPublisher.
    (as-> config $
          (merge DEFAULT-CONFIG $))
    (rb/agent-buffer 500)           ;; Each PutRecords request can support up to 500 records.
    (or (:transform config) identity)))