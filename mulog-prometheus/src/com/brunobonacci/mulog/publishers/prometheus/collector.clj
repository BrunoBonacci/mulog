(ns com.brunobonacci.mulog.publishers.prometheus.collector
  (:import [io.prometheus.client
            SimpleCollector
            SimpleCollector$Builder
            Counter
            Counter$Child
            Gauge
            Gauge$Child
            Histogram
            Histogram$Child
            Summary
            Summary$Builder
            Summary$Child]))

(def ^:private summary-quantiles-default [[0.5   0.001]
                                          [0.9   0.001]
                                          [0.95  0.001]
                                          [0.99  0.001]
                                          [0.999 0.001]])

(def ^:private metric-suffix
  {:counter    "cntr"
   :gauge      "gauge"
   :histogram  "hstgm"
   :summary    "smry"})

(defprotocol ChildWithLabels
  (child-with-labels [t label-values]))

(defprotocol Increment
  (increment [t]))

(defprotocol SetValue
  (set-value [t value]))

(defprotocol ObserveValue
  (observe-value [t value]))

(extend-type SimpleCollector
  ChildWithLabels
  (child-with-labels [t label-values] (.labels t label-values)))

(extend-type Counter$Child
  Increment
  (increment [t] (.inc t)))

(extend-type Gauge$Child
  SetValue
  (set-value [t value] (.set t value)))

(extend-type Histogram$Child
  ObserveValue
  (observe-value [t value] (.observe t value)))

(extend-type Summary$Child
  ObserveValue
  (observe-value [t value] (.observe t value)))

(defn- simple-collector-builder
  [builder-constructor
   {:keys [name namespace description label-keys]}]
  (-> ^SimpleCollector$Builder
   (builder-constructor)
      (.name name)
      (.namespace namespace)
      (.help description)
      (.labelNames label-keys)
      (.create)))

(defmulti create-collection (fn [metric] (:metric-type metric)))

(defmethod create-collection :counter
  [metric]
  (simple-collector-builder #(Counter/build) metric))

(defmethod create-collection :gauge
  [metric]
  (simple-collector-builder #(Gauge/build) metric))

(defmethod create-collection :histogram
  [{:keys [buckets] :as metric}]
  (simple-collector-builder #(cond-> (Histogram/build)
                               (seq buckets) (.buckets buckets))
                            metric))

(declare summary-builder)
(defmethod create-collection :summary
  [{:keys [quantiles max-age-seconds] :as metric}]
  (simple-collector-builder #(summary-builder quantiles max-age-seconds) metric))

(defn add-quantile [^Summary$Builder builder [quantile error]]
  (.quantile builder quantile error))

(defn summary-builder
  [quantiles max-age-seconds]
  (cond-> (Summary/build)
    max-age-seconds (.maxAgeSeconds max-age-seconds)
    :always         ((partial reduce add-quantile) (or (seq quantiles) summary-quantiles-default))))

(defmulti record-collection (fn [[metric collection]] (:metric-type metric)))

(defmethod record-collection :counter
  [[{:keys [label-values]} collection :as met-col]]
  (increment (child-with-labels collection label-values))
  met-col)

(defmethod record-collection :gauge
  [[{:keys [label-values metric-value]} collection :as met-col]]
  (set-value (child-with-labels collection label-values) metric-value)
  met-col)

(defmethod record-collection :histogram
  [[{:keys [label-values metric-value]} collection :as met-col]]
  (observe-value (child-with-labels collection label-values) metric-value)
  met-col)

(defmethod record-collection :summary
  [[{:keys [label-values metric-value]} collection :as met-col]]
  (observe-value (child-with-labels collection label-values) metric-value)
  met-col)

(defn cleanup-metrics
  [metrics]
  (map (fn [{:keys [metric-type metric-value namespace name description labels buckets] :as m}]
         (let [new-name (str name "_" (metric-type metric-suffix))]
           (merge m
                  {:metric-value (when metric-value (double metric-value))
                   :namespace    (str namespace)
                   :name         (str new-name)
                   :full-name    (str namespace "_" new-name)
                   :description  (str description)
                   :label-keys   (into-array String (first labels))
                   :label-values (into-array String (second labels))
                   :buckets      (double-array buckets)})))
       metrics))