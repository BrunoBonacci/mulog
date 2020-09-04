(ns com.brunobonacci.mulog.publishers.prometheus.metrics
  (:require [clojure.string :as str]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec :refer [invalid-metric-name-chars
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



(defn- kw-str
  [k]
  (-> (if (keyword? k)
        (if (namespace k)
          (str (namespace k) "_" (name k))
          (name k))
        (str k))
    (str/replace invalid-metric-name-chars "_")))



(defn- as-labels
  [m]
  (->> m
    (#(dissoc % :mulog/namespace :mulog/event-name))
    (map (fn [[k v]]
           [(->
              (cond
                (keyword? k) (kw-str k)
                :else (str k))
              (str/replace invalid-metric-label-chars  "_")
              (str/replace reserved-metric-label-chars "_"))
            (cond
              (keyword? v) (name v)
              (exception? v) (str (type v) ": " (.getMessage ^Exception v))
              :else (str v))]))
    (into {})))



(defn- event->metrics
  [{:keys [mulog/namespace mulog/event-name mulog/duration] :as event}]
  (let [numeric      (filter (comp number? second) (dissoc event :mulog/duration))
        namespace    (kw-str namespace)
        event-name   (kw-str event-name)
        labels       (as-labels event)]
    (conj (map (fn [[k v]]
                 (let [key-str (kw-str k)
                       e-name  (str event-name "_" key-str)]
                   #:metric{:type         :gauge
                            :value        v
                            :namespace    namespace
                            :name         e-name
                            :description  (str/join " " [event-name key-str "gauge"])
                            :labels       labels}))
            numeric)
      (when duration
        #:metric{:type        :summary
                 :value       duration
                 :namespace   namespace
                 :name        (str event-name "_timer_nanos")
                 :description (str event-name " Summary timer in nanos")
                 :labels      labels})
      #:metric{:type :counter
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