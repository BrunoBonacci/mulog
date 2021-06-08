(ns com.brunobonacci.mulog.publishers.prometheus
  (:require
   [clojure.java.io :refer [as-url]]
   [com.brunobonacci.mulog.publisher :as p]
   [com.brunobonacci.mulog.buffer :as rb]
   [com.brunobonacci.mulog.utils :as ut]
   [com.brunobonacci.mulog.publishers.prometheus.metrics   :as met]
   [com.brunobonacci.mulog.publishers.prometheus.collector :as col])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter.common TextFormat]
           [io.prometheus.client.exporter PushGateway]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                      ----==| R E G I S T R Y |==----                       ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


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




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| M E T R I C S |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- events->metrics
  "Takes a seq of events and converts it into a seq of metrics.
  Applying the `transform-metrics` function that you can provide in the config."
  [transform-metrics events]
  (->> events
    (met/events->metrics)
    (transform-metrics)
    (col/cleanup-metrics)))



(defn- metrics->met-cols
  "Takes a seq of metrics and converts it into a seq of `[metric collection]`"
  [registry metrics]
  (->> metrics
    (map (partial register-dynamically registry))
    (map col/cleanup-labels)))



(defn- record-metrics
  "Converts events into collections and records them in the registry."
  [registry transform-metrics events]
  (->> events
    (events->metrics transform-metrics)
    (metrics->met-cols registry)
    (run! col/record-collection)))



(defn- push-metrics
  "Will push metrics to the `PushGateway` when defined."
  [^CollectorRegistry registry ^PushGateway gateway ^String job]
  (when (and gateway job)
    (.push gateway registry job)))



(defn- publish-records!
  [{:keys [registry transform-metrics]
    {:keys [gateway job push-interval-ms]} :push-gateway} push-ts events]
  ;; metrics are collected every 100ms
  (record-metrics registry transform-metrics events)
  ;; if a PushGateway is setup then we publish the metrics
  ;; only once every 10s (configured)
  (when (and gateway job
          ;; if last push was more than 10s (push-interval-ms) ago
          ;; then perform a new push
          (< (+ @push-ts push-interval-ms) (System/currentTimeMillis)))
    (push-metrics registry gateway job)
    (swap! push-ts (constantly (System/currentTimeMillis)))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                    ----==| P R O M E T H E U S |==----                     ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype PrometheusPublisher
         [config buffer collector-registry transform push-ts]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:publish-delay config))

  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (rb/items buffer)
          last-offset (-> items last first)]
      ;; send to prometheus
      (publish-records! config push-ts (transform (map second items)))
      (rb/dequeue buffer last-offset)))

  com.brunobonacci.mulog.publishers.prometheus.ReadRegistry
  (registry [_]
    (registry collector-registry))

  (write-out [_ out]
    (write-out collector-registry out))

  (write-str [_]
    (write-str collector-registry))

  java.io.Closeable
  (close [_]))



(def ^:private DEFAULT-CONFIG
  {:publish-delay 100

   ;; You can supply your own registry which will be used for all
   ;; events.  If you do not specify a registry, the default registry
   ;; is used.  The default registry is the static
   ;; `CollectorRegistry.defaultRegistry`
   :registry (create-default)

   ;; You can setup the prometheus-publisher to push to a prometheus
   ;; PushGateway.  When to use the pushgateway:
   ;; https://prometheus.io/docs/practices/pushing/
   ;;
   ;; `:job` is a string identification used within the Promethues
   ;; PushGateway and it is always required. The pushgateway adds this
   ;; to the `job` label.  Typically a unique job/application name is
   ;; used. You can read more here:
   ;; https://github.com/prometheus/pushgateway#about-the-job-and-instance-labels
   ;;
   ;; `:gateway` is a `io.prometheus.client.exporter.PushGateway` you
   ;; can provide an existing one that your application/job uses. If
   ;; one is not provided a new one is created with the `endpoint`
   ;; configuration.
   ;;
   ;; `:endpoint` is the address which the PushGateway client should
   ;; push to.  e.g `"http://localhost:9091"`
   ;;
   ;; `:push-interval-ms` is how often (in millis) the metrics needs
   ;; to be published to the PushGateway (if configured) by default
   ;; will be every `10000` (`10s`)
   ;;
   ;; For example:
   ;;  * endpoint configuration:
   ;;    ```
   ;;    :push-gateway {:job      "my-awesome-job"
   ;;                   :endpoint "http://localhost:9091"}
   ;;    ```
   ;;
   ;;  * existing pushgateway:
   ;;    ```
   ;;    :push-gateway {:job      "my-awesome-job"
   ;;                   :gateway  ^PushGateway existing-prometheus-pushgateway}
   ;;    ```
   ;; Notice in either configuration `job` is required.
   ;;
   :push-gateway {:endpoint         nil
                  :job              nil
                  :gateway          nil
                  :push-interval-ms 10000}

   ;; A function to apply to the sequence of events before publishing.
   ;; This transformation function can be used to filter, transform,
   ;; anonymise events before they are published to a external system.
   ;; by default there is no transformation.  (since v0.1.8)
   :transform identity

   ;; A function to apply to the sequence of metrics before converting
   ;; into a collection.  This tranformation function can be used to
   ;; (alter/add/remove) metric types, (alter/add/remove) labels or
   ;; add more detailed descriptions of what the metric does.
   ;;
   ;;
   ;; For the `:histogram` metric type. You can supply `:buckets`
   ;; which will be used instead of the defaults.
   ;;
   ;; default: `[0.005 0.01 0.025 0.05 0.075 0.1 0.25 0.5 0.75 1 2.5 5 7.5 10]``
   ;;
   ;;
   ;; For the `:summary` metric type. You can suppy the following to
   ;; be used instead of the defaults.
   ;;
   ;; `:quantiles` - quantiles to be used over the sliding window of time.
   ;; default: `[[0.5 0.001][0.9 0.001][0.95 0.001][0.99 0.001][0.999 0.001]]`
   ;;
   ;; `:max-age-seconds` - duration of the time window.
   ;; default: `600`
   ;;
   ;; `:age-buckets` - buckets used to implement sliding window.
   ;; default: `5`
   ;;
   ;; by default there is no transformation.
   :transform-metrics identity})



(defn- setup-pushgateway
  [{{:keys [endpoint job gateway]} :push-gateway :as config}]
  (assoc-in config [:push-gateway :gateway]
    (when (and (not gateway) endpoint job)
      (PushGateway. (as-url endpoint)))))



(defn- normalize-push-interval
  "The push interval can only be a multiple of the `publish-delay`
  because it will be called in the same loop. It cannot be called each
  time because it would overwhelm the PushGateway"
  [{:keys [publish-delay] :as cfg}]
  (let [push-interval-ms (or (get-in cfg [:push-gateway :push-interval-ms]) 10000)
        push-interval-ms (max publish-delay push-interval-ms 1000)
        push-interval-ms (* (long (Math/ceil (/ push-interval-ms publish-delay) )) publish-delay)]
    (assoc-in cfg [:push-gateway :push-interval-ms] push-interval-ms)))



(defn prometheus-publisher
  [config]
  (let [cfg (-> (ut/deep-merge DEFAULT-CONFIG config)
              (normalize-push-interval)
              (setup-pushgateway))]
    ;; create the prometheus publisher
    (PrometheusPublisher.
      cfg
      (rb/agent-buffer 10000)
      (or (:registry cfg)  (create-default))
      (or (:transform cfg) identity)
      (atom 0))))



(comment
  ;; to be removed
  (def pp (prometheus-publisher {}
            #_{:push-gateway {:job      "prometheus-publisher"
                              :endpoint "http://localhost:9091"}}))

  (publish-records! (.config ^PrometheusPublisher pp)
    [{:app-name "sample-app"
      :version "0.1.0"
      :env "local"
      :mulog/trace-id #mulog/flake "4XWSuAXIyabhrxYHukmN5dPgv2mvcXg2"
      :mulog/timestamp 1596629322013
      :mulog/event-name :disruptions/initiated-poll
      :mulog/namespace "user"
      :foo 0.1
      :mulog/duration 396739657}
     {:app-name "sample-app"
      :version "0.1.0"
      :env "local"
      :mulog/trace-id #mulog/flake "4XWSuAXIyabhrxYHukmN5dPgv2mvcXg2"
      :mulog/timestamp 1596629322013
      :mulog/event-name :disruptions/initiated-poll
      :mulog/namespace "user"
      :foo 0.2
      :mulog/duration 396739657}])

  (print (.write-str ^PrometheusPublisher pp)))
