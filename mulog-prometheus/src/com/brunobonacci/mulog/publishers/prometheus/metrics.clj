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
  [k]
  (-> (if (keyword? k)
        (if (namespace k)
          (str (namespace k) "_" (name k))
          (name k))
        (str k))
    (str/replace invalid-metric-name-chars "_")))



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
    (remove (comp number? second))
    (map (fn [[k v]]
           [(label-key-str k)
            (cond
              (keyword? v) (name v)
              (exception? v) (str (type v) ": " (.getMessage ^Exception v))
              :else (str v))]))
    (into {})))



(defn- event->metrics
  [{:keys [mulog/namespace mulog/event-name mulog/duration] :as event}]
  (let [;; labels
        namespace      (kw-str namespace)
        namespace      (if-not (str/blank? namespace) (str namespace "_") namespace)
        event-name     (kw-str event-name)
        metric-name    (if (str/starts-with? event-name namespace) event-name (str namespace event-name))
        ;; labels
        numeric        (filter (fn [[k v]] (and (number? v) (not= k :mulog/duration))) event)
        numeric-labels (map label-key-str (keys numeric))
        labels         (as-labels event)]
    (concat
      ;;
      ;; For every event, add a counter on the event name.
      ;;
      [{:metric/type        :counter
        :metric/name        metric-name
        :metric/description (format "Counter of %s/%s events."
                              (str (:mulog/namespace event)) (pr-str (:mulog/event-name event)))
        :metric/labels      labels}
       ;;
       ;; if the event has a :mulog/duration, then add a summary timer
       ;;
       (when duration
         {:metric/type        :summary
          :metric/value       duration
          :metric/name        (str metric-name "_timer_nanos")
          :metric/description (format "Time distribution of %s/%s event's duration (in nanoseconds)."
                                (str (:mulog/namespace event)) (pr-str (:mulog/event-name event)))
          :metric/labels      labels})]
      ;;
      ;; for every numeric value, setup a gauge
      ;;
      (map (fn [[k v]]
             (let [key-str (kw-str k)
                   e-name  (str metric-name "_" key-str)]
               {:metric/type        :gauge
                :metric/value       v
                :metric/name        e-name
                :metric/description (format "Current value of %s in event %s/%s"
                                      (pr-str k) (str (:mulog/namespace event)) (pr-str (:mulog/event-name event)))
                :metric/labels      (dissoc labels (label-key-str k))}))
        numeric))))


(defn events->metrics
  "Given a bunch of events it returns a number of metrics
   which describe the given events in a number of dimensions."
  [events]
  (->> events
    (map cleanup-event)
    (mapcat event->metrics)
    (remove nil?)))
