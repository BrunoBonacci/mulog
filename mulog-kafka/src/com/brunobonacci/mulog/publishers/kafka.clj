(ns com.brunobonacci.mulog.publishers.kafka
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.levels :as lvl]
            [com.brunobonacci.mulog.utils :as ut]
            [cheshire.core :as json]
            [cheshire.generate :as gen]
            [clojure.string :as str])
  (:import [java.util Map]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord RecordMetadata]
           [org.apache.kafka.common.serialization StringSerializer Serializer]))



;;
;; Add Exception encoder to JSON generator
;;
(gen/add-encoder java.lang.Throwable
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (ut/exception-stacktrace x))))



(defn- normalize-config
  [config]
  (->> config
     (merge {:key.serializer   StringSerializer
             :value.serializer StringSerializer})
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

  (def kcfg {:bootstrap.servers "192.168.200.200:9092"
             :key.serializer    StringSerializer
             :value.serializer  StringSerializer})

  (def kp (producer kcfg))

  (RecordMetadata->data @(send! kp "mulog" "key1" "value1"))

  )



;; TODO: handle records which can't be serialized.
(defn- publish-records!
  [{:keys [key-field format topic producer*] :as  config} records]
  (let [fmt* (if (= :json format) json/generate-string ut/edn-str)]
    (->> records
       (map (juxt #(get % key-field) fmt*))
       (map (fn [[k v]] (send! producer* topic k v)))
       (doall))))



(comment
  (publish-records! {:key-field :puid :format :json :topic "mulog" :producer* kp}
                    [{:timestamp (System/currentTimeMillis) :event-name :hello :k 1}
                     {:timestamp (System/currentTimeMillis) :event-name :hello :k 2}])
  )



(defn deep-merge
  "Like merge, but merges maps recursively. It merges the maps from left
  to right and the right-most value wins. It is useful to merge the
  user defined configuration on top of the default configuration."
  [& maps]
  (let [maps (filter (comp not nil?) maps)]
    (if (every? map? maps)
      (apply merge-with deep-merge maps)
      (last maps))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| K A F K A |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype KafkaPublisher
    [config buffer transducer]


  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    (:publish-delay config))


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)
          items (sequence (comp (map second)
                                transducer) items)]
      (if-not (seq items)
        buffer
        ;; else send to kafka
        (do
          (publish-records! config items)
          (rb/dequeue buffer last-offset)))))


  java.io.Closeable
  (close [_]
    (.close ^java.io.Closeable (:producer* config))))



(def DEFAULT-CONFIG
  {:max-items     1000
   :publish-delay 1000
   :kafka {;; the comma-separated list of brokers to connect
           ;; :bootstrap.servers "localhost:9092"
           ;; you can add more kafka connection properties here
           }
   :topic "mulog"
   ;; one of: :json, :edn
   :format    :json
   :key-field :puid
   :level nil
   ;; function to transform records
   :transduce (map identity)
   })



(defn kafka-publisher
  [config]
  {:pre [(get-in config [:kafka :bootstrap.servers])]}
  (KafkaPublisher.
   (as-> config $
         (deep-merge DEFAULT-CONFIG $)
         (assoc $ :producer* (producer (:kafka $))))
   (rb/agent-buffer 10000)
   (comp (lvl/->filter (:level config))
         ;; Wrap transform in transducer for backward compatibility
         (or (:transduce config) (ut/->transducer (:transform config))))))
