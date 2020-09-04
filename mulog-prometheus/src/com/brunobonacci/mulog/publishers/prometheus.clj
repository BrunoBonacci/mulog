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

(defn record-metrics
  "Converts events into collections that are recorded in the registry."
  [registry transform-metrics events]
  (->> events
       (met/events->metrics)
       (transform-metrics)
       (col/cleanup-metrics)
       (map (partial reg/register-dynamically registry))
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
    (reg/get-registry registry))

  (write-out [_ out]
    (reg/write-out registry out))

  (write-str [_]
    (reg/write-text registry))

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
   ;; {:job      "my-awesome-job"
   ;;  :endpoint "localhost:9091"}
   ;; 
   ;; existing pushgateway:
   ;; {:job      "my-awesome-job"
   ;;  :gateway  ^PushGateway existing-prometheus-pushgateway}
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
     (or (:registry config)  (reg/create-default))
     (or (:transform config) identity))))

(comment
  ;; to be removed
  (def pp (prometheus-publisher {}))

  (publish-records! (.config ^PrometheusPublisher pp)
                    [{:mulog/event-name :disruptions/list-roads
                      :mulog/timestamp 1599157187146
                      :mulog/trace-id #mulog/flake "4Y4NzHzOMYl0TY3xP5AgY_z2EzPPzdY-"
                      :mulog/root-trace #mulog/flake "4Y4NzHyXE8-cZtCuyl2L-jabMpQFYrOS"
                      :mulog/parent-trace #mulog/flake "4Y4NzHyy2PVHp5FAYkJrWEJYsnCDqiRq"
                      :mulog/duration 538582300
                      :mulog/namespace "user"
                      :mulog/origin :safely.core
                      :mulog/outcome :ok
                      :app-name "roads-disruptions"
                      :env "local"
                      :http-status 200
                      :request-type :remote-api-call
                      :version "0.1.0"
                      :safely/attempt 0
                      :safely/call-level :inner
                      :safely/call-site "com.brunobonacci.disruptions.tfl-api[l:15, c:5]"
                      :safely/call-type :circuit-breaker
                      :safely/circuit-breaker :list-roads
                      :safely/circuit-breaker-outcome :success
                      :safely/max-retries 9223372036854775807
                      :safely/timeout nil}
                     {:mulog/event-name :disruptions/list-roads
                      :mulog/timestamp 1599157187144
                      :mulog/trace-id #mulog/flake "4Y4NzHyy2PVHp5FAYkJrWEJYsnCDqiRq"
                      :mulog/root-trace #mulog/flake "4Y4NzHyXE8-cZtCuyl2L-jabMpQFYrOS"
                      :mulog/parent-trace #mulog/flake "4Y4NzHyXE8-cZtCuyl2L-jabMpQFYrOS"
                      :mulog/duration 540638400
                      :mulog/namespace "user"
                      :mulog/outcome :ok
                      :app-name "roads-disruptions"
                      :env "local"
                      :http-status 200
                      :request-type :remote-api-call
                      :version "0.1.0"
                      :safely/call-level :outer
                      :safely/call-site "com.brunobonacci.disruptions.tfl-api[l:15, c:5]"
                      :safely/circuit-breaker :list-roads}])

  (= [:a {:foo [(quote bar)], :bar (quote foo)} nil] [:a {:foo ['bar] :bar 'foo} nil])
  
  (print (.write-text ^PrometheusPublisher pp)))