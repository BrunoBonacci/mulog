(ns com.brunobonacci.mulog.publishers.kafka-test
  (:require [midje.sweet :refer :all]
            [com.brunobonacci.mulog :as mu]
            [com.brunobonacci.mulog.publishers.container.kafka-setup :as kafka-setup]
            [clojure.core.async :as async]
            [jsonista.core :as json]
            [org.slf4j.impl.mulog]))

(def ^:private ^:const TOPIC "mulog")

(def ^:private json-mapper (json/object-mapper {:decode-key-fn true}))

(defn- <!!? [chan milliseconds]
  (let [timeout (async/timeout milliseconds)
        [value _] (async/alts!! [chan timeout])]
    value))

(let [stop-console-pub (atom nil)
      stop-kafka-pub   (atom nil)
      kafka-container  (atom nil)
      kafka-consumer   (atom nil)]
  (with-state-changes [(before :facts
                               (do
                                 (reset! stop-console-pub (mu/start-publisher! {:type :console}))
                                 (reset! kafka-container (kafka-setup/start-container!))
                                 (reset! stop-kafka-pub (mu/start-publisher! {:type      :kafka
                                                                              :kafka     {:bootstrap.servers (kafka-setup/get-bootstrap-servers @kafka-container)}
                                                                              :topic     TOPIC
                                                                              :transform (fn [events] (filter #(= (:mulog/event-name %) :ns/event) events))}))
                                 (reset! kafka-consumer (kafka-setup/start-consuming @kafka-container TOPIC))))
                       (after :facts (do
                                       (kafka-setup/stop-consuming! @kafka-consumer)
                                       (@stop-kafka-pub)
                                       (kafka-setup/stop-container! @kafka-container)
                                       (@stop-console-pub)))]

    (fact "messages published to kafka can be retrieved"

          (mu/log :ns/event :int 1 :string "data" :map {:a 1})

          (some-> kafka-consumer
                  deref
                  :channel
                  (<!!? 10000)
                  (json/read-value json-mapper))

          => (contains {:int              1
                        :map              {:a 1}
                        :mulog/event-name "ns/event"
                        :string           "data"}))))
