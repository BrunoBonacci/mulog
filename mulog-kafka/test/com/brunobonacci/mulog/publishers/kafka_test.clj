(ns com.brunobonacci.mulog.publishers.kafka-test
  (:require [midje.sweet :refer :all]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.publishers.container.kafka-setup :as kafka-setup]
            [clojure.core.async :as async]
            [jsonista.core :as json]))



(def ^:private json-mapper (json/object-mapper {:decode-key-fn true}))



(defn- <!!? [chan milliseconds]
  (let [timeout (async/timeout milliseconds)
        [value _] (async/alts!! [chan timeout])]
    value))


(kafka-setup/with-test-broker

  (fact "messages published to kafka can be retrieved"

    (u/log :test/event :int 1 :string "data" :map {:a 1})

    (some-> _consumer
      deref
      :channel
      (<!!? 10000)
      (json/read-value json-mapper))

    => (contains
         {:int              1
          :map              {:a 1}
          :mulog/event-name "test/event"
          :string           "data"}))


  (fact "messages published to kafka can be retrieved also when containing exceptions"

    (u/log :test/event :int 2 :string "data" :map {:a 1} :exception (ex-info "BOOM!" {}))

    (some-> _consumer
      deref
      :channel
      (<!!? 10000)
      (json/read-value json-mapper))

    => (contains
         {:int              2
          :map              {:a 1}
          :mulog/event-name "test/event"
          :string           "data"
          :exception        #"BOOM"})))
