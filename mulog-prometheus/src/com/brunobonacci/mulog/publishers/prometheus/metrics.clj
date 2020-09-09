(ns com.brunobonacci.mulog.publishers.prometheus.metrics
  (:require [clojure.string :as str]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec
             :refer [invalid-metric-name-chars
                     invalid-metric-label-chars
                     reserved-metric-label-chars]]))



(defn- exception?
  [e]
  (instance? java.lang.Exception e))



(defn- cleanup-event
  [event]
  (->> event
    (#(dissoc % :mulog/trace-id :mulog/parent-trace :mulog/root-trace :mulog/timestamp))
    (filter (fn [[_ v]] (or (string? v) (number? v) (keyword? v) (exception? v))))
    (into {})))



(defn kw-str
  ([k]
   (kw-str k false))
  ([k namespace?]
   (-> (if (keyword? k)
         (if (and namespace? (namespace k))
           (str (namespace k) "_" (name k))
           (name k))
         (str k))
     (str/replace invalid-metric-name-chars "_"))))



(defn label-key-str
  [k]
  (->
    (cond
      (keyword? k) (kw-str k)
      :else (str k))
    (str/replace invalid-metric-label-chars  "_")
    (str/replace reserved-metric-label-chars "_")))



(defn- as-labels
  [m]
  (->> m
    (#(dissoc % :mulog/namespace :mulog/event-name))
    (map (fn [[k v]]
           [(label-key-str k)
            (cond
              (keyword? v) (name v)
              (exception? v) (str (type v) ": " (.getMessage ^Exception v))
              :else (str v))]))
    (into {})))



(defn- event->metrics
  [{:keys [mulog/namespace mulog/event-name mulog/duration] :as event}]
  (let [numeric        (filter (comp number? second) (dissoc event :mulog/duration))
        numeric-labels (conj (map label-key-str (keys numeric)) "duration")
        namespace      (kw-str namespace)
        event-name     (kw-str event-name)
        labels         (as-labels event)]
    (conj
      ;;
      ;; for every numeric value, setup a gauge
      ;;
      (map (fn [[k v]]
             (let [key-str (kw-str k)
                   e-name  (str event-name "_" key-str)]
               {:metric/type        :gauge
                :metric/value       v
                :metric/namespace   namespace
                :metric/name        e-name
                :metric/description (str/join " " [event-name key-str "gauge"])
                :metric/labels      (dissoc labels (label-key-str k) "duration")}))
        numeric)
      ;;
      ;; if the event has a :mulog/duration, then add a summary timer
      ;;
      (when duration
        {:metric/type        :summary
         :metric/value       duration
         :metric/namespace   namespace
         :metric/name        (str event-name "_timer_nanos")
         :metric/description (str event-name " Summary timer in nanos")
         :metric/labels      (dissoc labels "duration")})
      ;;
      ;; For every event, add a counter on the event name.
      ;;
      {:metric/type        :counter
       :metric/namespace   namespace
       :metric/name        event-name
       :metric/description (str event-name " counter")
       :metric/labels      (apply dissoc labels numeric-labels)})))



(defn events->metrics
  [events]
  (->> events
    (map cleanup-event)
    (mapcat event->metrics)
    (remove nil?)))
