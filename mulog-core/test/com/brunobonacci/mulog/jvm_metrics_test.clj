(ns com.brunobonacci.mulog.jvm-metrics-test
  (:require [com.brunobonacci.mulog.jvm-metrics :refer [jvm-sample]]
            [midje.sweet :refer [facts fact => contains anything just]]
            [clojure.spec.test.alpha :as st])
  (:import  [java.lang.management ManagementFactory]))

(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-garbage-collector)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-jvm-attrs)
(st/instrument 'com.brunobonacci.mulog.jvm-metrics/capture-jvx-attrs)

(defn kebab? [s]
  (let [matcher (re-matcher #"[(A-Z)._']+" s)]
    (not (re-find matcher))))

(defn each-key-is-kebab? [actual]
  (every? kebab? (->> actual keys (map str))))

(comment
  (fact "it should capture JVM metrics for some key groups"
        (jvm-sample {:memory {:heap true
                              :buffers true}
                     :gc {:collections true
                          :duration true}})
        => (contains
            {:memory {:used_heap int?
                      :max_heap int?}
             :gc {:collections int?}
             :duration int?})))

(facts "it should sample JVM metrics"
  (fact "Total memory"
    (jvm-sample {:memory {:total true}})
    => (just {:memory
              (just {:total
                     (contains {:init      int?
                                :used      int?
                                :max       int?
                                :committed int?})})}))
  (fact "Heap memory"
    (jvm-sample {:memory {:heap true}})
    => (contains {:memory
                  (just
                    {:heap
                     (contains
                       {:init        int?
                        :used        int?
                        :max         int?
                        :committed   int?
                        :usage-ratio ratio?})})}))

  (fact "Non-heap memory"
    (jvm-sample {:memory {:non-heap true}})
    => (just {:memory
              (just
                {:non-heap
                 (contains
                  {:init        int?
                   :used        int?
                   :max         int?
                   :committed   int?
                   :usage-ratio #(or (int? %) (ratio? %))})})}))

  (fact "Pool memory"
    (jvm-sample {:memory {:pools true}})
    => (just {:memory (just {:pools map?})}))

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
      (#'com.brunobonacci.mulog.jvm-metrics/capture-memory mxbean {})
      =>
      empty?)

    (fact "from mxbean, can ask for the total memory"
      (:total
       (#'com.brunobonacci.mulog.jvm-metrics/capture-memory mxbean {:total true}))
      =>
      (just
        {:init      anything
         :used      anything
         :max       anything
         :committed anything}))

    (fact "from memory pool, keys should use kebab notation "
      (->>
        (#'com.brunobonacci.mulog.jvm-metrics/capture-memory-pools pools))
      =>
      each-key-is-kebab?)))

(fact "can capture garbage collector metrics"
  (let [gc (into [] (ManagementFactory/getGarbageCollectorMXBeans))]
    (#'com.brunobonacci.mulog.jvm-metrics/capture-garbage-collector gc)
    =>
    each-key-is-kebab?))

(fact "can capture JVM attributes"
      (let [runtime (ManagementFactory/getRuntimeMXBean)]
        (#'com.brunobonacci.mulog.jvm-metrics/capture-jvm-attrs runtime)
        =>
        each-key-is-kebab?))

(fact "can capture threads states"
      (let [threads (ManagementFactory/getThreadMXBean)]
        (#'com.brunobonacci.mulog.jvm-metrics/capture-thread-states threads)
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
