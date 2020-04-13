(ns ^{:author "Pablo Reszczynski @PabloReszczynski"
      :doc "Module for sampling some JVM metrics"}
  com.brunobonacci.mulog.jvm-metrics
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import  [java.lang.management MemoryMXBean
                                  MemoryPoolMXBean
                                  MemoryUsage]))

(defn jvm-sample [opts] nil)

(s/def :memory/total.init int?)
(s/def :memory/total.used int?)
(s/def :memory/total.max int?)
(s/def :memory/total.committed int?)
(s/def :memory/heap.init int?)
(s/def :memory/heap.used int?)
(s/def :memory/heap.max int?)
(s/def :memory/heap.committed int?)
(s/def :memory/heap.usage-ratio ratio?)
(s/def :memory/non-heap.init int?)
(s/def :memory/non-heap.used int?)
(s/def :memory/non-heap.max int?)
(s/def :memory/non-heap.committed int?)
(s/def :memory/non-heap.usage-ratio ratio?)

(s/def ::captured-memory
  (s/keys :req [:memory/total.init
                :memory/total.used
                :memory/total.max
                :memory/total.commited
                :memory/heap.init
                :memory/heap.used
                :memory/heap.max
                :memory/heap.committed
                :memory/heap.usage-ratio
                :memory/non-heap.init
                :memory/non-heap.used
                :memory/non-heap.max
                :memory/non-heap.committed
                :memory/non-heap.usage-ratio]))

(defn- get-usage-ratio [^MemoryUsage usage]
  (/ (.getUsed usage) (.getMax usage)))

(defn- get-pool-name [^MemoryMXBean pool]
  (-> pool
      .getName
      (str/replace #"\s+" "-")
      str/lower-case))

(s/fdef capture-memory
  :args (s/cat :mxbean (partial instance? MemoryMXBean))
  :ret ::captured-memory)

;; FIXME: Maybe this is not the shape we would like.
(defn- capture-memory [^MemoryMXBean mxbean]
  {:total.init (+ (-> mxbean .getHeapMemoryUsage .getInit)
                  (-> mxbean .getNonHeapMemoryUsage .getInit))
   :total.used (+ (-> mxbean .getHeapMemoryUsage .getUsed)
                  (-> mxbean .getNonHeapMemoryUsage .getUsed))
   :total.max  (+ (-> mxbean .getHeapMemoryUsage .getMax)
                  (-> mxbean .getNonHeapMemoryUsage .getMax))
   :total.committed
               (+ (-> mxbean .getHeapMemoryUsage .getCommitted)
                  (-> mxbean .getNonHeapMemoryUsage .getCommitted))
   :heap.init (-> mxbean .getHeapMemoryUsage .getInit)
   :heap.used (-> mxbean .getHeapMemoryUsage .getUsed)
   :heap.max  (-> mxbean .getHeapMemoryUsage .getMax)
   :heap.committed
              (-> mxbean .getHeapMemoryUsage .getCommitted)
   :heap.usage-ratio (get-usage-ratio (.getHeapMemoryUsage mxbean))
   :non-heap.init (-> mxbean .getNonHeapMemoryUsage .getInit)
   :non-heap.used (-> mxbean .getNonHeapMemoryUsage .getUsed)
   :non-heap.max  (-> mxbean .getNonHeapMemoryUsage .getMax)
   :non-heap.committed
                  (-> mxbean .getNonHeapMemoryUsage .getCommitted)
   :non-heap.usage-ratio (get-usage-ratio (.getNonHeapMemoryUsage mxbean))})

(s/fdef capture-memory-pools
  :args (s/cat :pools (s/coll-of (partial instance? MemoryPoolMXBean)))
  :ret (s/map-of keyword? ratio?))

(defn- capture-memory-pools [pools]
  (into {}
    (for [^MemoryPoolMXBean pool pools
          :let [pname (get-pool-name pool)
                usage (.getUsage pool)]]
      [(keyword (str pname ".usage"))
       (/ (.getUsed usage)
          (if (= (.getMax usage) -1)
            (.getCommitted usage)
            (.getMax usage)))])))
