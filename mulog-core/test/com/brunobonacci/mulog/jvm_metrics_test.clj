(ns com.brunobonacci.mulog.jvm-metrics-test
  (:require [com.brunobonacci.mulog.jvm-metrics :refer [jvm-sample]]
            [midje.sweet :refer [facts fact => contains anything]]
            [clojure.spec.test.alpha :as st])
  (:import  [java.lang.management ManagementFactory]))

(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-garbage-collector)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-jvm-attrs)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-jvx-attrs)

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
      (#'com.brunobonacci.mulog.jvm-metrics/capture-memory mxbean)
      =>
      anything)

    (fact "from memory pool"
      (#'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools pools)
      =>
      anything)))

(fact "can capture garbage collector metrics"
  (let [gc (into [] (ManagementFactory/getGarbageCollectorMXBeans))]
    (#'com.brunobonacci.mulog.jvm-metrics/capture-garbage-collector gc)
    =>
    anything))

(fact "can capture JVM attributes"
  (let [runtime (ManagementFactory/getRuntimeMXBean)]
    (#'com.brunobonacci.mulog.jvm-metrics/capture-jvm-attrs runtime)
    =>
    anything))


(fact "can capture threads states"
  (let [threads (ManagementFactory/getThreadMXBean)]
    (#'com.brunobonacci.mulog.jvm-metrics/capture-thread-states threads)
    =>
    (contains
      {:deadlocks map?
       :waiting.count int?
       :blocked.count int?
       :timed_waiting.count int?
       :runnable.count int?
       :deadlock.count int?
       :count int?
       :daemon.count int?
       :new.count int?
       :terminated.count int?})))
