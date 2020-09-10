(ns com.brunobonacci.mulog.publishers.prometheus.registry
  (:require [com.brunobonacci.mulog.publishers.prometheus.collector :as col])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter.common TextFormat]))



;;
;; Access to the private fields
;; `namesToCollectors` and `namesCollectorsLock`
;; are required in order to share a registry
;; if a user provides one. This ensures thread
;; safety when adding a new collection and will
;; use the same collection if it already exists.
;;
;; `namesToCollectors` is a map of
;; collectionName -> collection
;;
;; `namesCollectorsLock` is a lock object that
;; is synchronized when doing any operation with
;; `namesToCollectors`.
;;

(defonce ^:private names-to-collectors
  (-> CollectorRegistry
    (.getDeclaredField "namesToCollectors")
    (doto (.setAccessible true))))



(defonce ^:private names-collectors-lock
  (-> CollectorRegistry
    (.getDeclaredField "namesCollectorsLock")
    (doto (.setAccessible true))))



(defn- field-value
  "Reflective call to retrieve internal registry value"
  [^java.lang.reflect.Field f o]
  (.get f o))



(defprotocol Registry
  "This protocol is used to extend the `CollectorRegistry`.
  This is done to ultimately ensure thread safety and to dynamically
  register collections.  The prometheus java client currently only
  lets you register new collections, existing collections will throw
  an `IllegalArgumentException`"
  (nc-map
    [t]
    "Retrieve the `namesToCollectors` map")
  (nc-lock
    [t]
    "Retrieve the `nameCollectorsLock` lock Object")
  (register-dynamically
    [t metric]
    "This will try and register a new collection if it doesn't or
    return an existing collection by doing the following:

    - syncronize a lock on `nameCollectorsLock`
    - get collection from `namesToCollectors` using `:metric/name`
    - if collection exists return
    - else register new collection and return"))



(defprotocol ReadRegistry
  "This protocol is used to extract the metric information from the
  registry."

  (registry
    [t]
    "Returns the `^CollectorRegistry t`.")

  (write-out
    [t out]
    "Writes the `^CollectorRegistry t` to `^java.io.Writer out`.")

  (write-str
    [t]
    "Writes the `^CollectorRegistry t` to a `java.io.StringWriter` and
    returns the String result."))



(extend-type CollectorRegistry

  Registry
  (nc-map  [t] (field-value names-to-collectors   t))
  (nc-lock [t] (field-value names-collectors-lock t))

  (register-dynamically
    [t metric]
    (locking (nc-lock t)
      [metric
       (let [collection (get (nc-map t) (:metric/name metric))]
         (if-not collection
           (let [collection (col/create-collection metric)]
             (.register t collection)
             collection)
           collection))]))


  ReadRegistry
  (registry [t] t)
  (write-out
    [t out]
    (TextFormat/write004 out (.metricFamilySamples t)))
  (write-str
    [t]
    (with-open [out (java.io.StringWriter.)]
      (write-out t out)
      (str out))))



(defn create-default []
  (CollectorRegistry/defaultRegistry))
