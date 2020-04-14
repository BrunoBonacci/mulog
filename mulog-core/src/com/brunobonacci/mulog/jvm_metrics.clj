(ns ^{:author "Pablo Reszczynski @PabloReszczynski"
      :doc "Module for sampling some JVM metrics"}
 com.brunobonacci.mulog.jvm-metrics
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str])
  (:import  [java.lang.management MemoryMXBean
             MemoryManagerMXBean
             MemoryPoolMXBean
             MemoryUsage
             GarbageCollectorMXBean
             RuntimeMXBean
             ThreadMXBean
             ThreadInfo
             LockInfo]
            [javax.management MBeanServerConnection ObjectName]))

(defn jvm-sample [opts] nil)

;; FIXME maybe move the specs elsewhere?
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

(defn- get-bean-name [^MemoryManagerMXBean bean]
  (-> bean
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
              :let [pname (get-bean-name pool)
                    usage (.getUsage pool)]]
          [(keyword (str pname ".usage"))
           (/ (.getUsed usage)
              (if (= (.getMax usage) -1)
                (.getCommitted usage)
                (.getMax usage)))])))

(s/fdef capture-garbage-collector
  :args (s/cat :gc (s/coll-of (partial instance? GarbageCollectorMXBean)))
  :ret  (s/map-of keyword? int?))

(defn- capture-garbage-collector [gc]
  (apply merge
         (for [^GarbageCollectorMXBean mxbean gc
               :let [name (get-bean-name mxbean)]]
           {(keyword (str name ".count")) (.getCollectionCount mxbean)
            (keyword (str name ".time"))  (.getCollectionTime mxbean)})))

(s/def :attrs/name string?)
(s/def :attrs/vendor string?)
(s/fdef capture-jvm-attrs
  :args (s/cat :runtime (partial instance? RuntimeMXBean))
  :ret (s/keys :req [:attrs/name :attrs/vendor]))

(defn- capture-jvm-attrs [^RuntimeMXBean runtime]
  {:name (.getName runtime)
   :vendor (format "%s %s %s (%s)"
                   (.getVmVendor runtime)
                   (.getVmName runtime)
                   (.getVmVersion runtime)
                   (.getSpecVersion runtime))})

(s/fdef capture-jvm-attrs
  :args (s/cat :server (partial instance? MBeanServerConnection)
               :object-name (partial instance? ObjectName)
               :attr-name string?)
  :ret (s/nilable string?))

;; TODO: Rewrite in a better style
(defn- capture-jvx-attrs
  [^MBeanServerConnection server ^ObjectName object-name attr-name]
  (letfn [(get-obj-name []
            (if (.isPattern object-name)
              (let [found-names (.queryNames server object-name nil)]
                (if (= (.size found-names) 1)
                  (-> found-names .iterator .next)
                  object-name))
              object-name))]
    (try
      (.getAttribute server (get-obj-name) attr-name)
      (catch java.io.IOException _ nil)
      (catch javax.management.JMException _ nil))))

(defn detect-deadlocks [^ThreadMXBean threads]
  (let [ids (.findDeadlockedThreads threads)]
    (if (some? ids)
      (apply merge
            (for [^ThreadInfo info (.getThreadInfo ids 100)]
              {(keyword (.getName info))
               (.getStackTrace info)}))
      {})))

(defn get-thread-count [^Thread$State state ^ThreadMXBean threads]
  (count
    (filter
      (fn [^ThreadInfo info] (and (some? info) (= (.getThreadState info) state)))
      (into [] (.getThreadInfo threads (.getAllThreadIds threads) 100)))))


(s/fdef capture-thread-states
  :args (s/cat :threads (partial instance? ThreadMXBean))
  :ret map?)

(defn capture-thread-states [^ThreadMXBean threads]
  (let [deadlocks (detect-deadlocks threads)
        base-map {:count (.getThreadCount threads)
                  :daemon.count (.getDaemonThreadCount threads)
                  :deadlock.count (count deadlocks)
                  :deadlocks deadlocks}]
    (merge
      base-map
      (apply merge
        (for [^Thread$State state (Thread$State/values)]
          {(keyword (str (str/lower-case state) ".count"))
           (get-thread-count state threads)})))))
