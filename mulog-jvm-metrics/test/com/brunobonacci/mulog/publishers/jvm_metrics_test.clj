(ns com.brunobonacci.mulog.publishers.jvm-metrics-test
  (:require [com.brunobonacci.mulog.publishers.jvm-metrics :refer [jvm-sample]]
            [midje.sweet :refer [facts fact => contains anything just]]
            [clojure.spec.test.alpha :as st])
  (:import  [java.lang.management ManagementFactory]))


(st/instrument 'com.brunobonacci.mulog.publishers.jvm-metrics/capture-memory)
(st/instrument 'com.brunobonacci.mulog.publishers.jvm-metrics/capture-memory-pools)
(st/instrument 'com.brunobonacci.mulog.publishers.jvm-metrics/capture-garbage-collector)
(st/instrument 'com.brunobonacci.mulog.publishers.jvm-metrics/capture-jvm-attrs)
(st/instrument 'com.brunobonacci.mulog.publishers.jvm-metrics/capture-jvx-attrs)



(defn kebab? [s]
  (let [matcher (re-matcher #"[(A-Z)._']+" s)]
    (not (re-find matcher))))



(defn each-key-is-kebab? [actual]
  (every? kebab? (->> actual keys (map str))))



(facts "it should sample JVM metrics"
  (fact "Memory"
    (jvm-sample {:memory true})
    => (just {:memory
             (just {:total
                    (contains {:init      int?
                               :used      int?
                               :max       int?
                               :committed int?})
                    :heap
                    (contains
                      {:init        int?
                       :used        int?
                       :max         int?
                       :committed   int?
                       :usage-ratio double?})

                    :non-heap
                    (contains
                      {:init        int?
                       :used        int?
                       :max         int?
                       :committed   int?
                       :usage-ratio #(or (int? %) (double? %))})

                    :pools map?})}))


  (fact "Garbage collector metrics"
    (jvm-sample {:gc true}) => (contains {:gc map?}))

  (fact "Threads"
    (jvm-sample {:threads true}) => (contains {:threads map?}))

  (fact "JVM attributes"
    (jvm-sample {:jvm-attrs true}) => (contains {:jvm-attrs map?})))



(facts "can capture memory usage"
  (let [mxbean (ManagementFactory/getMemoryMXBean)
        pools  (vec (ManagementFactory/getMemoryPoolMXBeans))]

    (fact "from mxbean, no opts should yield an empty map"
      (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-memory mxbean {})
      =>
      empty?)

    (fact "from mxbean, can ask for the total memory"
      (:total
          (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-memory mxbean {:total true}))
      =>
      (just
          {:init      anything
           :used      anything
           :max       anything
           :committed anything}))

    (fact "from memory pool, keys should use kebab notation "
      (->>
          (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-memory-pools pools))
      =>
      each-key-is-kebab?)))



(fact "can capture garbage collector metrics"
  (let [gc (into [] (ManagementFactory/getGarbageCollectorMXBeans))]
    (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-garbage-collector gc)
    =>
    each-key-is-kebab?))



(fact "can capture JVM attributes"
  (let [runtime (ManagementFactory/getRuntimeMXBean)]
    (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-jvm-attrs runtime)
    =>
    each-key-is-kebab?))



(fact "can capture threads states"
  (let [threads (ManagementFactory/getThreadMXBean)]
    (#'com.brunobonacci.mulog.publishers.jvm-metrics/capture-thread-states threads)
    =>
    (contains
      {:deadlocks map?
       :waiting-count int?
       :blocked-count int?
       :timed-waiting-count int?
       :runnable-count int?
       :deadlock-count int?
       :count int?
       :daemon-count int?
       :new-count int?
       :terminated-count int?})))
