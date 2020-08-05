(ns com.brunobonacci.mulog.publishers.metrics
  (:require [clojure.string :as str]
            [com.brunobonacci.mulog.flakes :as f]))



(defn exception?
  [e]
  (instance? java.lang.Exception e))



(defn cleanup-event
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
    (str/replace #"[^a-zA-Z0-9_:]+" "_")))



(defn as-labels
  [m]
  (->> m
    (map (fn [[k v]]
           [(->
                (cond
                  (number? k) (str "_" k)
                  (keyword? k) (kw-str k)
                  :else (str k))
              (str/replace #"[^a-zA-Z0-9_:]+" "_"))
            (cond
              (number? v) v
              (keyword? v) (str/replace (pr-str v) #"^:" "")
              (exception? v) (str (type v) ": " (.getMessage ^Exception v))
              :else (str v))]))
    (into {})))



(defn event->metrics
  [{:keys [mulog/event-name mulog/duration] :as event}]
  (let [numeric (filter (comp number? second) event)
        event-name (kw-str event-name)]
    [[event-name :counter 1 (as-labels event)]

     (when duration
       [(str event-name "_timer_nanos") :summary duration (as-labels event)])]))



(defn events->metrics
  "Given a list of events it returns a list of quadruplets in the following form:
   [ [metric-name metric-type value labels] ...]
  "
  [events]
  (->> events
    (map cleanup-event)
    (mapcat event->metrics)
    (remove nil?)))


(comment

  (->> [{:app-name "sample-app",
       :version "0.1.0",
       :env "local",
       :mulog/trace-id #mulog/flake "4XWSuAXIyabhrxYHukmN5dPgv2mvcXg2",
       :mulog/timestamp 1596629322013,
       :mulog/event-name :disruptions/initiated-poll,
       :mulog/namespace "user"
       :mulog/duration 396739657}]
    events->metrics)

  )






;;
