(ns com.brunobonacci.mulog.publishers.kafka
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.common.json :as json]
            [clojure.string :as str]
            [taoensso.nippy :as nippy])
  (:import [java.util Map]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord RecordMetadata]
           [org.apache.kafka.common.serialization StringSerializer Serializer
            ByteArraySerializer]))



(defn- normalize-config
  [{:keys [format] :as config}]
  (->> config
    :kafka
    (merge {:key.serializer   StringSerializer
            :value.serializer (if (= :nippy format) ByteArraySerializer StringSerializer)})
    (map (fn [[k v]]
           [(name k)
            (cond
              (string? v)  v
              (keyword? v) (name v)
              (number? v)  (str v)
              (class? v)   (.getName ^Class v)
              (nil? v)     v
              :else        (str v))]))
    (into {})))



(defn producer
  ([config]
   (KafkaProducer. ^Map (normalize-config config)))
  ([config key-serde value-serde]
   (KafkaProducer. ^Map (normalize-config config) ^Serializer key-serde ^Serializer value-serde)))



(defn- RecordMetadata->data
  [^RecordMetadata rm]
  {:topic-name            (.topic rm)
   :partition             (.partition rm)
   :timestamp             (.timestamp rm)
   :offset                (.offset rm)
   :serialized-key-size   (.serializedKeySize rm)
   :serialized-value-size (.serializedValueSize rm)})



(defn send!
  [^KafkaProducer producer ^String topic key value]
  (.send producer (ProducerRecord. topic key value)))



(comment

  (def kcfg {:bootstrap.servers "localhost:9092"
             :key.serializer    StringSerializer
             :value.serializer  StringSerializer})

  (def kp (producer {:kafka kcfg}))

  (RecordMetadata->data @(send! kp "mulog" "key1" "value1"))

  )



;; TODO: handle records which can't be serialized.
(defn- publish-records!
  [{:keys [key-field format topic producer* fmt*] :as  config} records]
  (->> records
    (map (juxt #(some-> (get % key-field) str) fmt*))
    (map (fn [[k v]] (send! producer* topic k v)))
    (doall)))



(comment
  (publish-records! {:key-field :puid :format :json :topic "mulog" :producer* kp}
    [{:timestamp (System/currentTimeMillis) :event-name :hello :k 1}
     {:timestamp (System/currentTimeMillis) :event-name :hello :k 2}])
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| K A F K A |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype KafkaPublisher [config buffer transform]

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
        ;; else send to kafka
        (do
          (publish-records! config (transform (map second items)))
          (rb/dequeue buffer last-offset)))))


  java.io.Closeable
  (close [_]
    (.close ^java.io.Closeable (:producer* config))))



(def DEFAULT-CONFIG
  {:max-items     1000
   :publish-delay 1000
   :kafka         {;; the comma-separated list of brokers to connect
                   ;; :bootstrap.servers "localhost:9092"
                   ;; you can add more kafka connection properties here
                   }
   :topic         "mulog"
   ;; one of: :json, :edn, :nippy
   :format        :json
   ;; nippy configuration
   :nippy         {:compressor nippy/lz4-compressor}
   ;; kafka records key
   :key-field     :mulog/trace-id
   ;; function to transform records
   :transform     identity
   })



(defn- serialization-format
  [{:keys [format nippy] :as config}]
  (case format
    :json json/to-json
    :edn ut/edn-str
    :nippy #(nippy/freeze % nippy)
    json/to-json))



(defn kafka-publisher
  [config]
  {:pre [(get-in config [:kafka :bootstrap.servers])]}
  (KafkaPublisher.
    (as-> config $
      (ut/deep-merge DEFAULT-CONFIG $)
      (assoc $ :producer* (producer $))
      (assoc $ :fmt* (serialization-format $)))
    (rb/agent-buffer 10000)
    (or (:transform config) identity)))
