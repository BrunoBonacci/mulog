(ns com.brunobonacci.mulog.publishers.container.kafka-setup
  (:require [com.brunobonacci.mulog :as logger]
            [clj-test-containers.core :as tc]
            [ketu.async.source :as source]
            [clojure.core.async :as async])
  (:import (org.testcontainers.containers KafkaContainer)
           (org.testcontainers.utility DockerImageName)
           (java.util UUID)))

(def ^:private ^:const container-port 9093)

(defn get-bootstrap-servers [kafka-container]
  (.getBootstrapServers ^KafkaContainer (:container kafka-container)))

(defn start-container! []
  (logger/log ::creating-kafka-container)
  (-> {:container     (KafkaContainer. (DockerImageName/parse "confluentinc/cp-kafka:6.1.0"))
       :exposed-ports [container-port]}
      tc/init
      tc/start!))

(defn stop-container! [kafka-container]
  (tc/stop! kafka-container))

(defn start-consuming [kafka-container topic]
  (let [consumer-id (str "test-consumer-" (UUID/randomUUID))
        channel     (async/chan 100)
        source      (source/source
                      channel
                      {:name            consumer-id
                       :topic           topic
                       :group-id        consumer-id
                       :brokers         (get-bootstrap-servers kafka-container)
                       :value-type      :string
                       :shape           :value
                       :internal-config {"auto.offset.reset" "earliest"}})]
    {:source  source
     :channel channel}))

(defn stop-consuming! [consumer]
  (source/stop! (:source consumer)))
