(ns com.brunobonacci.mulog.publishers.prometheus.collector
  (:require [clojure.spec.alpha :as s]
            [com.brunobonacci.mulog.publishers.prometheus.metrics :as met]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec :as ms])
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



(defonce ^:private label-names-f
  (-> SimpleCollector
    (.getDeclaredField "labelNames")
    (doto (.setAccessible true))))



(defn- field-value
  "Reflective call to retrieve internal registry value"
  [^java.lang.reflect.Field f o]
  (.get f o))



(def ^:private summary-quantiles-default
  [[0.5   0.001]
   [0.9   0.001]
   [0.95  0.001]
   [0.99  0.001]
   [0.999 0.001]])



(defprotocol SimpleCollectorLabels
  (label-names ^"[Ljava.lang.String;" [t])
  (child-with-labels                  [t label-values]))



(defprotocol Increment
  (increment [t]))



(defprotocol SetValue
  (set-value [t value]))



(defprotocol ObserveValue
  (observe-value [t value]))



(extend-type SimpleCollector
  SimpleCollectorLabels
  (label-names [t] (field-value label-names-f t))
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
   {:keys [metric/name metric/description metric/label-keys]}]
  (-> ^SimpleCollector$Builder (builder-constructor)
    (.name name)
    (.help description)
    (.labelNames label-keys)
    (.create)))



(defmulti create-collection
  "Dispatches on the `:metric/type`.
  Returns a `SimpleCollector`"
  (fn [metric] (:metric/type metric)))



(defmethod create-collection :counter
  [metric]
  (simple-collector-builder #(Counter/build) metric))



(defmethod create-collection :gauge
  [metric]
  (simple-collector-builder #(Gauge/build) metric))



(defmethod create-collection :histogram
  [{:keys [metric/buckets] :as metric}]
  (simple-collector-builder #(cond-> (Histogram/build)
                               (seq buckets) (.buckets buckets))
    metric))



(declare summary-builder)



(defmethod create-collection :summary
  [{:keys [metric/quantiles metric/max-age-seconds metric/age-buckets] :as metric}]
  (simple-collector-builder #(summary-builder quantiles max-age-seconds age-buckets) metric))



(defn add-quantile [^Summary$Builder builder [quantile error]]
  (.quantile builder quantile error))



(defn summary-builder
  [quantiles max-age-seconds age-buckets]
  (cond-> (Summary/build)
    age-buckets     (.ageBuckets age-buckets)
    max-age-seconds (.maxAgeSeconds max-age-seconds)
    :always         ((partial reduce add-quantile) (or (seq quantiles) summary-quantiles-default))))



(defmulti record-collection
  "Receives a `[metric collection]`.
  Dispatches on the `:metric/type`.
  This will cause the collection to record the events value.
  Returns `[metric collection]`"
  (fn [[metric collection]] (:metric/type metric)))



(defmethod record-collection :counter
  [[{:keys [metric/label-values]} collection :as met-col]]
  (increment (child-with-labels collection label-values))
  met-col)



(defmethod record-collection :gauge
  [[{:keys [metric/label-values metric/value]} collection :as met-col]]
  (set-value (child-with-labels collection label-values) value)
  met-col)



(defmethod record-collection :histogram
  [[{:keys [metric/label-values metric/value]} collection :as met-col]]
  (observe-value (child-with-labels collection label-values) value)
  met-col)



(defmethod record-collection :summary
  [[{:keys [metric/label-values metric/value]} collection :as met-col]]
  (observe-value (child-with-labels collection label-values) value)
  met-col)



(defn cleanup-labels
  "Collection labels are not allowed to change.
  This guarantees only first detected labels are used
  and that label order is maintained."
  [[{:keys [metric/labels] :as metric} collection]]
  (let [label-k (label-names collection)]
    [(merge metric
       {:metric/label-keys label-k
        :metric/label-values (into-array String
                               ;; labels are not allowed to be null, replacing with ""
                               (reduce #(conj %1 (or (get labels %2) "")) [] label-k))})
     collection]))



(defn cleanup-metrics
  [metrics]
  (->> metrics
    (remove nil?)
    (map (fn [{:keys [ metric/value metric/name metric/description
                      metric/labels metric/buckets] :as m}]
           (let [nm      (met/kw-str name)]
             (merge m
               {:metric/value        (when value (double value))
                :metric/name         nm
                :metric/description  (str description)
                :metric/label-keys   (into-array String (map met/label-key-str (keys labels)))
                :metric/buckets      (double-array buckets)}))))))
