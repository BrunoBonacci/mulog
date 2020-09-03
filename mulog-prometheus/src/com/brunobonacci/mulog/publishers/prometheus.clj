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
  (when gateway (.push gateway registry job)))



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
   :registry      (reg/create-default)
   :push-gateway  {:endpoint nil
                   :job      nil
                   :gateway  nil}

   ;; function to transform records
   :transform         identity
   ;; function to transform metrics
   :transform-metrics identity})

(defn prometheus-publisher
  [config]
  (PrometheusPublisher.
    (let [{{:keys [endpoint job gateway]} :push-gateway :as config} (merge DEFAULT-CONFIG config)]
      (assoc-in config [:push-gateway :gateway]
        (when (and (not gateway) endpoint job)
          (PushGateway. (as-url endpoint)))))
    (rb/agent-buffer 10000)
    (or (:registry config) (reg/create-default))
    (or (:transform :registry) identity)))



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