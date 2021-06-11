(ns com.brunobonacci.mulog.publishers.container.kafka-setup
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.flakes :as f]
            [clj-test-containers.core :as tc]
            [ketu.async.source :as source]
            [clojure.core.async :as async]
            [midje.sweet :refer :all])
  (:import (org.testcontainers.containers KafkaContainer)
           (org.testcontainers.utility DockerImageName)
           (java.util UUID)))



(def ^:private ^:const container-port 9093)



(defn get-bootstrap-servers [kafka-container]
  (.getBootstrapServers ^KafkaContainer (:container kafka-container)))



(defn start-container! []
  (-> {:container     (KafkaContainer. (DockerImageName/parse "confluentinc/cp-kafka:6.1.0"))
       :exposed-ports [container-port]}
    tc/init
    tc/start!))



(defn stop-container! [kafka-container]
  (tc/stop! kafka-container))



(defn start-consuming [kafka-container topic]
  (let [consumer-id (str "test-consumer-" (f/snowflake))
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



(defmacro with-test-broker
  [& facts]
  `(let [stop-console-pub# (atom nil)
         stop-kafka-pub#   (atom nil)
         kafka-container#  (atom nil)
         kafka-consumer#   (atom nil)]

     (with-state-changes
       ;; start cluster
       [(before :contents
          (do
            ;; start console publisher
            (reset! stop-console-pub#
              (u/start-publisher! {:type :console :pretty? true}))

            ;; start kafka broker
            (reset! kafka-container# (start-container!))))

        ;; test setup
        (before :facts
          (let [topic# (str "mulog-" (f/snowflake))]
            ;; start publisher
            (reset! stop-kafka-pub#
              (u/start-publisher!
                {:type  :kafka
                 :kafka {:bootstrap.servers (get-bootstrap-servers @kafka-container#)}
                 :topic topic#}))

            ;; init consumer
            (reset! kafka-consumer#
              (start-consuming @kafka-container# topic#))))


        (after :facts
          (do
            ;; stop publisher
            (@stop-kafka-pub#)
            ;; stop consumer
            (stop-consuming! @kafka-consumer#)))


        ;; tear-down cluster
        (after :contents
          (do
            ;; stop broker
            (stop-container! @kafka-container#)
            ;; stop console publisher
            (@stop-console-pub#)))]


       (let [~'_consumer kafka-consumer#]
         ~@facts))))
