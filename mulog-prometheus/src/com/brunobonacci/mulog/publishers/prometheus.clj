(ns com.brunobonacci.mulog.publishers.prometheus
  (:require
   [clojure.java.io :refer [as-url]]
   [com.brunobonacci.mulog.publisher :as p]
   [com.brunobonacci.mulog.buffer :as rb]
   [com.brunobonacci.mulog.publishers.prometheus.metrics   :as met]
   [com.brunobonacci.mulog.publishers.prometheus.registry  :as reg]
   [com.brunobonacci.mulog.publishers.prometheus.collector :as col])
  (:import [io.prometheus.client CollectorRegistry]
           [io.prometheus.client.exporter PushGateway]))

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
    (map (partial reg/register-dynamically registry))
    (map col/cleanup-labels)))

(defn- record-metrics
  "Converts events into collections and records them in the registry."
  [registry transform-metrics events]
  (->> events
    (events->metrics transform-metrics)
    (metrics->met-cols registry)
    (run! col/record-collection)))



(defn push-metrics
  "Will push metrics to the `PushGateway` when defined."
  [^CollectorRegistry registry ^PushGateway gateway ^String job]
  (when (and gateway job) (.push gateway registry job)))



(defn- publish-records!
  [{:keys [registry transform-metrics]
    {:keys [gateway job]} :push-gateway} events]
  (record-metrics registry transform-metrics events)
  (push-metrics registry gateway job))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                    ----==| P R O M E T H E U S |==----                     ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype PrometheusPublisher
         [config buffer registry transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:publish-delay config))

  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)]
      (if-not (seq items)
        buffer
        ;; else send to prometheus
        (do
          (publish-records! config (transform (map second items)))
          (rb/dequeue buffer last-offset)))))

  com.brunobonacci.mulog.publishers.prometheus.registry.ReadRegistry
  (registry [_]
    (reg/registry registry))

  (write-out [_ out]
    (reg/write-out registry out))

  (write-str [_]
    (reg/write-str registry))

  java.io.Closeable
  (close [_]))

(def ^:private DEFAULT-CONFIG
  {:max-items     1000
   :publish-delay 100

   ;; You can supply your own registry which will be used for all events.
   ;; If you do not specify a registry, the default registry is used.
   ;; The default registry is the static `CollectorRegistry.defaultRegistry`
   :registry      (reg/create-default)

   ;; You can setup the prometheus-publisher to push to a prometheus pushgateway.
   ;; When to use the pushgateway: https://prometheus.io/docs/practices/pushing/
   ;; 
   ;; `job` is a string identification used within the Promethues PushGateway
   ;; and it is always required. The pushgateway adds this to the `job` label.
   ;; Typically a unique job/application name is used. You can read more here:
   ;; https://github.com/prometheus/pushgateway#about-the-job-and-instance-labels
   ;; 
   ;; `gateway` is a `io.prometheus.client.exporter.PushGateway` you can provide
   ;; an existing one that your application/job uses. If one is not provided a new
   ;; one is created with the `endpoint` configuration.
   ;; 
   ;; `endpoint` is the address which the PushGateway client should push to.
   ;; e.g "localhost:9091"
   ;; 
   ;; 
   ;; For example:
   ;; endpoint configuration:
   ;; :push-gateway {:job      "my-awesome-job"
   ;;                :endpoint "http://localhost:9091"}
   ;; 
   ;; existing pushgateway:
   ;; :push-gateway {:job      "my-awesome-job"
   ;;                :gateway  ^PushGateway existing-prometheus-pushgateway}
   ;; 
   ;; Notice in either configuration `job` is required.
   ;; 
   :push-gateway  {:endpoint nil
                   :job      nil
                   :gateway  nil}

   ;; A function to apply to the sequence of events before publishing.
   ;; This transformation function can be used to filter, transform,
   ;; anonymise events before they are published to a external system.
   ;; by default there is no transformation.  (since v0.1.8)
   :transform         identity

   ;; A function to apply to the sequence of metrics before converting into a collection.
   ;; This tranformation function can be used to (alter/add/remove) metric types, 
   ;; (alter/add/remove) labels or add more detailed descriptions of what the metric does.
   ;; 
   ;; 
   ;; For the `:histogram` metric type. You can supply `:buckets` which will be used instead
   ;; of the defaults.
   ;; default: [0.005 0.01 0.025 0.05 0.075 0.1 0.25 0.5 0.75 1 2.5 5 7.5 10]
   ;; 
   ;; 
   ;; For the `:summary` metric type. You can suppy the following to be used instead of the defaults.
   ;; 
   ;; `:quantiles` - quantiles to be used over the sliding window of time.
   ;; default: [[0.5 0.001][0.9 0.001][0.95 0.001][0.99 0.001][0.999 0.001]]
   ;; 
   ;; `:max-age-seconds` - duration of the time window.
   ;; default: 600
   ;; 
   ;; `:age-buckets` - buckets used to implement sliding window.
   ;; default: 5
   ;; 
   ;; by default there is no transformation.
   :transform-metrics identity})

(defn- setup-pushgateway
  [{{:keys [endpoint job gateway]} :push-gateway :as config}]
  (assoc-in config [:push-gateway :gateway]
    (when (and (not gateway) endpoint job)
      (PushGateway. (as-url endpoint)))))

(defn prometheus-publisher
  [config]
  (let [cfg (-> (merge DEFAULT-CONFIG config)
              (setup-pushgateway))]
    ;; create the prometheus publisher
    (PrometheusPublisher.
      cfg
      (rb/agent-buffer 10000)
      (or (:registry cfg)  (reg/create-default))
      (or (:transform cfg) identity))))

(comment
  ;; to be removed
  (def pp (prometheus-publisher {} #_{:push-gateway {:job      "prometheus-publisher"
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