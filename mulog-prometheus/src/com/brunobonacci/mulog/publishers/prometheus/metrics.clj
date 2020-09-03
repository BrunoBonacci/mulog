(ns com.brunobonacci.mulog.publishers.prometheus.metrics
  (:require [clojure.string :as str]
            [com.brunobonacci.mulog.publishers.prometheus.metrics_spec :refer [sanitize-metric-name-re
                                                                               sanitize-metric-label-name-re
                                                                               reserved-metric-label-name-re]]))

(defn- exception?
  [e]
  (instance? java.lang.Exception e))



(defn- cleanup-event
  [event]
  (->> event
    (#(dissoc % :mulog/trace-id :mulog/parent-trace :mulog/root-trace :mulog/timestamp))
    (filter (fn [[_ v]] (or (string? v) (number? v) (keyword? v) (exception? v))))
    (into {})))



(defn- kw-str
  [k]
  (-> (if (keyword? k)
        (if (namespace k)
          (str (namespace k) "_" (name k))
          (name k))
        (str k))
    (str/replace sanitize-metric-name-re "_")))



(defn- as-labels
  [m]
  (->> m
    (#(dissoc % :mulog/namespace :mulog/event-name))
    (map (fn [[k v]]
           [(->
              (cond
                (keyword? k) (kw-str k)
                :else (str k))
              (str/replace sanitize-metric-label-name-re "_")
              (str/replace reserved-metric-label-name-re "_"))
            (cond
              (keyword? v) (name v)
              (exception? v) (str (type v) ": " (.getMessage ^Exception v))
              :else (str v))]))
    (into {})))



(defn- event->metrics
  [{:keys [mulog/namespace mulog/event-name] :as event}]
  (let [numeric      (filter (comp number? second) event)
        namespace    (kw-str namespace)
        event-name   (kw-str event-name)
        labels       (as-labels event)]
    (conj (for [type  [:gauge :histogram :summary]
                [k v] numeric
                :let  [key-str (kw-str k)
                       e-name  (str event-name "_" key-str)]]
            #:metric{:metric-type  type
                     :metric-value v
                     :namespace    namespace
                     :name         e-name
                     :description  (str/join " " [event-name key-str (name type)])
                     :labels labels})
      #:metric{:metric-type :counter
               :namespace   namespace
               :name        event-name
               :description (str event-name " counter")
               :labels      labels})))



(defn events->metrics
  [events]
  (->> events
    (map cleanup-event)
    (mapcat event->metrics)
    (remove nil?)))