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

(defn- publish-records!
  [{:keys [^CollectorRegistry registry transform-metrics]
    {:keys [^PushGateway gateway ^String job]} :push-gateway} events]
  (->> events
    (met/events->metrics)
    (transform-metrics)
    (col/cleanup-metrics)
    (map (partial reg/register-dynamically registry))
    (run! col/record-collection))
  (when (and gateway job) (.push gateway registry job)))



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
  (get-registry [_]
    (reg/get-registry registry))

  (write-out [_ out]
    (reg/write-out registry out))

  (write-text [_]
    (reg/write-text registry))

  java.io.Closeable
  (close [_]))

(def ^:private DEFAULT-CONFIG
  {:max-items     1000
   :publish-delay 100

   ;; You can supply your own registry which will be used for all events.
   :registry      (reg/create-default)

   ;; You can setup the prometheus-publisher to push to a prometheus push gateway.
   ;; `job` is always required - These are what your metrics are identified by.
   ;; Typically a unique job name or application name.
   ;;
   ;; You can then either supply your own `^PushGateway :gateway` or supply an
   ;; `:endpoint` which will create a new `PushGateway`.
   :push-gateway  {:endpoint nil
                   :job      nil
                   :gateway  nil}

   ;; A function to apply to the sequence of events before publishing.
   ;; This transformation function can be used to filter, transform,
   ;; anonymise events before they are published to a external system.
   ;; by defatult there is no transformation.  (since v0.1.8)
   :transform         identity

   ;; A function to apply to the sequence of metrics before converting into a collection.
   ;; You will receive a `com.brunobonacci.mulog.publishers.prometheus.metrics-spec/metric` and
   ;; should produce the same or nil.
   ;; This tranformation function can be used to remove metric types, (alter/add/remove) labels
   ;; or add more detailed descriptions of what the metric does.
   ;; 
   ;; For the :histogram metric type. You can supply buckets which will be used instead of the defaults.
   ;; default: [0.005 0.01 0.025 0.05 0.075 .1 .25 .5 .75 1 2.5 5 7.5 10]
   ;; 
   ;; For the :summary metric type. You can suppy the following to be used instead of the defaults.
   ;; quantiles - quantiles to be used over the sliding window of time.
   ;; default: [[0.5 0.001][0.9 0.001][0.95 0.001][0.99 0.001][0.999 0.001]]
   ;; max-age-seconds - duration of the time window.
   ;; default: 600
   ;; age-buckets - buckets used to implement sliding window.
   ;; default: 5
   ;; 
   ;; by default there is no transformation.
   :transform-metrics identity})

(defn prometheus-publisher
  [config]
  (let [{{:keys [endpoint job gateway]} :push-gateway :as config} (merge DEFAULT-CONFIG config)]
    (assoc-in config [:push-gateway :gateway]
      (when (and (not gateway) endpoint job)
        (PushGateway. (as-url endpoint))))
    ;; create the prometheus publisher
    (PrometheusPublisher.
      config
      (rb/agent-buffer 10000)
      (or (:registry config) (reg/create-default))
      (or (:transform :registry) identity))))

(comment
  ;; to be removed
  (def pp (prometheus-publisher {}))

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

  (print (.write-text ^PrometheusPublisher pp)))