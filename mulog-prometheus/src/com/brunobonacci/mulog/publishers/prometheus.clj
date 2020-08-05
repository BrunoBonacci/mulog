(ns com.brunobonacci.mulog.publishers.prometheus
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [clojure.string :as str]))



(defn publish-records!
  [config events]
  ;; TODO: increment counters
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                    ----==| P R O M E T H E U S |==----                     ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype PrometheusPublisher
    [config buffer transform]


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


  java.io.Closeable
  (close [_]
    ;; TODO:
    ))



(def ^:const DEFAULT-CONFIG
  {:max-items     1000
   :publish-delay 100
   ;; publish-method :push-gateway | :scape
   ;; push-gateway-endpoint ""
   ;; ....

   ;; function to transform records
   :transform     identity
   })



(defn prometheus-publisher
  [config]
  (PrometheusPublisher.
    (merge DEFAULT-CONFIG config)
    (rb/agent-buffer 10000)
    (or (:transform config) identity)))
