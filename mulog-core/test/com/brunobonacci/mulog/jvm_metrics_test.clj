(ns com.brunobonacci.mulog.jvm-metrics-test
  (:require [com.brunobonacci.mulog.jvm-metrics :refer [jvm-sample]]
            [midje.sweet :refer [facts fact => contains anything]]
            [clojure.spec.test.alpha :as st])
  (:import (java.lang.management ManagementFactory)))

(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools)

(fact "it should capture JVM metrics for some key groups"
  (jvm-sample {:memory {:heap true
                        :buffers true}
               :gc {:collections true
                    :duration true}})
  => (contains
       {:memory {:used_heap int?
                 :max_heap int?}
        :gc {:collections int?
             :duration int?}}))

(facts "can capture memory usage"
  (let [mxbean (ManagementFactory/getMemoryMXBean)
        pools (into [] (ManagementFactory/getMemoryPoolMXBeans))]
    (fact "from mxbean"
      (#'com.brunobonacci.mulog.jvm-metrics/capture-memory mxbean) =>
      anything)

    (fact "from memory pool"
      (#'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools pools) =>
      anything)))
