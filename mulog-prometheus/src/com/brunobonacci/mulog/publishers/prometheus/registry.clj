(ns com.brunobonacci.mulog.publishers.prometheus.registry
  (:require [com.brunobonacci.mulog.publishers.prometheus.collector :as col])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter.common TextFormat]))

(defonce ^:private names-to-collectors (-> CollectorRegistry
                                         (.getDeclaredField "namesToCollectors")
                                         (doto (.setAccessible true))))

(defonce ^:private names-collectors-lock (-> CollectorRegistry
                                           (.getDeclaredField "namesCollectorsLock")
                                           (doto (.setAccessible true))))



(defprotocol Registry
  (get-nc-map           [t])
  (get-nc-lock          [t])
  (register-dynamically [t metric]))

(defprotocol ReadRegistry
  (get-registry [t])
  (write-out    [t out])
  (write-text  [t]))

(extend-type CollectorRegistry
  Registry
  (get-nc-map  [t] (.get ^java.lang.reflect.Field names-to-collectors   t))
  (get-nc-lock [t] (.get ^java.lang.reflect.Field names-collectors-lock t))
  (register-dynamically
    [t metric]
    (locking (get-nc-lock t)
      [metric
       (let [collection (get (get-nc-map t) (:metric/full-name metric))]
         (if-not collection
           (let [collection (col/create-collection metric)]
             (.register t collection)
             collection)
           collection))]))

  ReadRegistry
  (get-registry [t] t)
  (write-out
    [t out]
    (TextFormat/write004 out (.metricFamilySamples t)))
  (write-text
    [t]
    (with-open [out (java.io.StringWriter.)]
      (write-out t out)
      (str out))))



(defn create-default [] (CollectorRegistry/defaultRegistry))