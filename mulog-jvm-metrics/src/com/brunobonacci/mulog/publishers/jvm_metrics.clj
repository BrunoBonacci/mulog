(ns ^{:author "Pablo Reszczynski (@PabloReszczynski) and Bruno Bonacci (@BrunoBonacci)"
      :doc "Publisher for sampling some JVM metrics"}
    com.brunobonacci.mulog.publishers.jvm-metrics
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [com.brunobonacci.mulog.utils :refer [os-java-pid]]
            [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.buffer :as rb])
  (:import  [java.lang.management MemoryMXBean
             MemoryPoolMXBean
             MemoryUsage
             GarbageCollectorMXBean
             RuntimeMXBean
             ThreadMXBean
             ThreadInfo
             ManagementFactory]
            [javax.management MBeanServerConnection ObjectName]))

(s/def :memory/init int?)
(s/def :memory/used int?)
(s/def :memory/max int?)
(s/def :memory/committed int?)
(s/def :memory/usage-ratio ratio?)



(defprotocol HasName
  "Gives the name of the object"
  (get-name [this]))

(extend-type MemoryPoolMXBean
  HasName
  (get-name [this] (.getName this)))

(extend-type GarbageCollectorMXBean
  HasName
  (get-name [this] (.getName this)))



(s/def ::memory
  (s/keys :req [:memory/init
                :memory/used
                :memory/max
                :memory/committed]
    :opt [:memory/usage-ratio]))



(s/def :memory/total ::memory)
(s/def :memory/heap ::memory)
(s/def :memory/non-heap ::memory)
(s/def ::captured-memory
  (s/keys :opt [:memory/total
                :memory/heap
                :memory/non-heap]))



(defn- fix-precision-ratio
  [v]
  (double
    (with-precision 4
      (bigdec v))))



(defn- get-usage-ratio [^MemoryUsage usage]
  (fix-precision-ratio
    (/ (.getUsed usage)
      (if (= (.getMax usage) -1)
        (.getCommitted usage)
        (.getMax usage)))))



(defn- get-bean-name [bean]
  (-> bean
    get-name
    (str/replace #"\s+" "-")
    (str/replace #"'" "")
    str/lower-case))


(s/def :opts/heap boolean?)
(s/def :opts/total boolean?)
(s/def :opts/non-heap boolean?)
(s/def ::capture-memory-opts
  (s/keys :opt [:opts/total :opts/heap :opts/non-heap]))

(s/fdef capture-memory
  :args (s/cat :mxbean (partial instance? MemoryMXBean)
          :opts ::capture-memory-opts)
  :ret ::captured-memory)



(defn- capture-memory [^MemoryMXBean mxbean {:keys [total heap non-heap]}]
  (letfn [(get-total []
            {:total {:init (+ (-> mxbean .getHeapMemoryUsage .getInit)
                             (-> mxbean .getNonHeapMemoryUsage .getInit))
                     :used (+ (-> mxbean .getHeapMemoryUsage .getUsed)
                             (-> mxbean .getNonHeapMemoryUsage .getUsed))
                     :max  (+ (-> mxbean .getHeapMemoryUsage .getMax)
                             (-> mxbean .getNonHeapMemoryUsage .getMax))
                     :committed
                     (+ (-> mxbean .getHeapMemoryUsage .getCommitted)
                       (-> mxbean .getNonHeapMemoryUsage .getCommitted))}})
          (get-heap []
            {:heap {:init (-> mxbean .getHeapMemoryUsage .getInit)
                    :used (-> mxbean .getHeapMemoryUsage .getUsed)
                    :max  (-> mxbean .getHeapMemoryUsage .getMax)
                    :committed
                    (-> mxbean .getHeapMemoryUsage .getCommitted)
                    :usage-ratio (get-usage-ratio (.getHeapMemoryUsage mxbean))}})
          (get-non-heap []
            {:non-heap {:init (-> mxbean .getNonHeapMemoryUsage .getInit)
                        :used (-> mxbean .getNonHeapMemoryUsage .getUsed)
                        :max  (-> mxbean .getNonHeapMemoryUsage .getMax)
                        :committed
                        (-> mxbean .getNonHeapMemoryUsage .getCommitted)
                        :usage-ratio (get-usage-ratio (.getNonHeapMemoryUsage mxbean))}})]
    (cond-> {}
      total (merge (get-total))
      heap  (merge (get-heap))
      non-heap (merge (get-non-heap)))))



(s/fdef capture-memory-pools
  :args (s/cat :pools (s/coll-of (partial instance? MemoryPoolMXBean)))
  :ret (s/map-of keyword? ratio?))



(defn- capture-memory-pools [pools]
  (into {}
    (for [^MemoryPoolMXBean pool pools
          :let [pname (get-bean-name pool)
                usage (.getUsage pool)]]
      [(keyword (str pname "-usage"))
       (fix-precision-ratio
         (/ (.getUsed usage)
           (if (= (.getMax usage) -1)
             (.getCommitted usage)
             (.getMax usage))))])))



(s/fdef capture-garbage-collector
  :args (s/cat :gc (s/coll-of (partial instance? GarbageCollectorMXBean)))
  :ret  (s/map-of keyword? int?))



(defn- capture-garbage-collector [gc]
  (apply merge
    (for [^GarbageCollectorMXBean mxbean gc
          :let [name (get-bean-name mxbean)]]
      {(keyword (str name "-count")) (.getCollectionCount mxbean)
       (keyword (str name "-time"))  (.getCollectionTime mxbean)})))



(s/def :attrs/name string?)
(s/def :attrs/vendor string?)
(s/def :attrs/jvm-version string?)
(s/def :attrs/process-id int?)
(s/fdef capture-jvm-attrs
  :args (s/cat :runtime (partial instance? RuntimeMXBean))
  :ret (s/keys :req [:attrs/name :attrs/vendor :attrs/jvm-version :attrs/process-id]))



(defn- capture-jvm-attrs [^RuntimeMXBean runtime]
  {:name (.getName runtime)
   :vendor (format "%s (%s)"
             (.getVmVendor runtime)
             (.getSpecVersion runtime))
   :version (.getVmVersion runtime)
   :process-id (os-java-pid)})


(s/fdef capture-jvx-attrs
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
        (for [^ThreadInfo info (.getThreadInfo threads ids 100)]
          {(keyword (.getThreadName info))
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
                  :daemon-count (.getDaemonThreadCount threads)
                  :deadlock-count (count deadlocks)
                  :deadlocks deadlocks}
        convert-name (fn [s] (str/replace (str/lower-case s) #"_" "-"))]
    (merge
      base-map
      (apply merge
        (for [^Thread$State state (Thread$State/values)]
          {(keyword (str (convert-name state) "-count"))
           (get-thread-count state threads)})))))



(defn jvm-sample-memory
  "Captures JVM memory metrics"
  ([]
   (jvm-sample-memory {:pools true :total true :heap true :non-heap true}))
  ([{:keys [pools] :as opts}]
   (let [mxbean (ManagementFactory/getMemoryMXBean)
         captured-memory (capture-memory mxbean opts)
         poolmxbean (when pools (into [] (ManagementFactory/getMemoryPoolMXBeans)))
         captured-pools (when pools {:pools (capture-memory-pools poolmxbean)})]
     (merge captured-memory captured-pools))))



(defn jvm-sample-gc
  "Captures JVM garbage collector metrics"
  []
  (let [gc (into [] (ManagementFactory/getGarbageCollectorMXBeans))]
    (capture-garbage-collector gc)))



(defn jvm-sample-threads
  "Captures JVM threads metrics"
  []
  (let [threads (ManagementFactory/getThreadMXBean)]
    (capture-thread-states threads)))



(defn jvm-sample-attrs
  "Captures JVM attributes"
  []
  (let [runtime (ManagementFactory/getRuntimeMXBean)]
    (capture-jvm-attrs runtime)))



(defn jvm-sample
  "Samples the JVM runtime for some metrics.
   The metrics available are:
   - Memory
     - Total memory
     - Heap memory
     - Non-heap memory
     - Memory pools
  - Garbage Collector
  - JVM attributes
  - Threads

  usage:

  ```
  (jvm-sample {:memory true :gc true :threads true :jvm-attrs true})
  ```
  or:
  ```
  (jvm-sample {:all true})
  ```

  "
  [{:keys [memory gc threads jvm-attrs all]}]
  (let [mem       (when (or all memory)    {:memory    (jvm-sample-memory)})
        gc        (when (or all gc)        {:gc        (jvm-sample-gc)})
        threads   (when (or all threads)   {:threads   (jvm-sample-threads)})
        jvm-attrs (when (or all jvm-attrs) {:jvm-attrs (jvm-sample-attrs)})]
    (merge {} mem gc threads jvm-attrs)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| P U B L I S H E R |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype JvmMetricsPublisher
    [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:sampling-interval config 60000))

  (publish [_ buffer]
    ;; sampling the jvm metrics
    (u/log :mulog/jvm-metrics-sampled
      :jvm-metrics (jvm-sample (:jvm-metrics config)))))



(def ^:const DEFAULT-CONFIG
  {;; Interval in milliseconds between two samples
   :sampling-interval 60000
   ;; metrics to sample
   :jvm-metrics {:memory true :gc true :threads true :jvm-attrs true}})



(defn jvm-metrics-publisher
  [{:keys [sampling-interval jvm-metrics] :as config}]
  (let [config (as-> config $
                 (merge DEFAULT-CONFIG $)
                 (assoc $ :sampling-interval
                   (max sampling-interval 1000)))]
    ;; create the metrics publisher
    (JvmMetricsPublisher. config (rb/agent-buffer 1))))
