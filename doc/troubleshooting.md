# Troubleshooting

This guide walks through the most common issues publishing logs/traces with ***μ/log***.


## μ/log stopped publishing logs/traces

In ***μ/log*** each publisher has it own life-cycle. Logs are stored in a in-memory ring buffer
and at regular intervals, ***μ/log*** will attempt to publish the logs to the target system.

If an error occurs during the publishing, ***μ/log*** uses itself to log publishing errors. The best way to debug it is to add another publisher like a file publisher for example and then search for `:mulog/event-name = :mulog/publisher-error`. It will contain the details of the exception.

The simplest way to figure out what is the publisher error is to add another publisher to a different medium

```clojure
;; publisher errors only
(u/start-publisher!
  {:type :simple-file
   :filename "/tmp/log/mulog-publishers-errors.edn"
   :transform (partial filter (where :mulog/event-name = :mulog/publisher-error))})
```

Adding this new publisher will allow to inspect the file and see the actual exception.


## Publishers fails due to invalid JSON: `JSON encoding error`

Most of the external logs collector have a JSON API. The most common issue is related to the type mismatch between EDN maps and JSON objects.
JSON objects must have string keys while EDN maps can have any datatype for example:

```clojure
;; this is valid in clojure but not in JSON
{1     "key must be a string in JSON"
 4.5   "still invalid"
 [1 2] "not valid"}
```

***μ/log*** by default converts keywords to strings, but everything else if left to the user as it potentially can render the item un-searchable.

So you know that there is a non-serializeable log, but you don't which, how can you find out which log entry is causing the trouble?

```clojure
(require
   '[clojure.string :as str]
   '[com.brunobonacci.mulog :as u]
   '[com.brunobonacci.mulog.common.json :as j]
   '[com.brunobonacci.mulog.utils :as ut])

(defn capture-invalid-json
  "FOR DEBUG ONLY: it attempts to serialize to JSON and if it fails, it logs the reason.
   it removes all non-serializeable events."
  [events]
  (filter
    (fn [event]
      (try
        (j/to-json event)
        true
        (catch Exception _
          (u/log :mulog/invalid-json-value :value (ut/edn-str event :pretty? true))
          false)))
    events))

;; add the transformer to the your logger FOR DEBUG ONLY
(u/start-publisher! {:type xyz, :transform capture-invalid-json})
```

This will attempt to serialize each item and upon failure it will log a new event `:mulog/invalid-json-value` where the `:value` key
contains a EDN string of the failing event.

Then all you need to do is to search for the logs with the `:mulog/event-name = :mulog/invalid-json-value`.

Once you identified which log entries are causing the error you can just fix it.
Alternatively, if you don't care about searching those non-string keys, you can force all the keys to be strings:

```clojure
(require '[clojure.walk :as walk])

(defn stringify-all-keys
  "Recursively transforms all map keys of any type to strings."
  [m]
  (let [f (fn [[k v]]
            (cond
              (and (keyword? k) (= "mulog" (namespace k))) [k v]
              (and (keyword? k) (namespace k)) [(str (namespace k) "/" (name k)) v]
              (keyword? k) [(name k) v]
              :else [(str k) v]))]
    ;; only apply to maps
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

;; force all the keys to be a string
(u/start-publisher! {:type xyz, :transform (partial map stringify-all-keys)})
```


## Cloudwatch publisher fails due to size log size

If you having trouble identifying which events are larger than 256Kb allowed by Cloudwatch,
here is a custom transformer that can help you to find the items

```clojure
(require
   '[clojure.string :as str]
   '[com.brunobonacci.mulog :as u]
   '[com.brunobonacci.mulog.common.json :as j]
   '[com.brunobonacci.mulog.utils :as ut])


(defn capture-large-json
  "FOR DEBUG ONLY: it attempts to serialize to JSON and check the payload size.
   it removes all larger events."
  [events]
  (filter
    (fn [event]
      (try
        ;; 256K is the max allowed size by Cloudwatch API
        (if (> (count (j/to-json event)) (* 256 1024))
          (u/log :mulog/invalid-event-size :event-name (:mulog/event-name event))
          true)
        (catch Exception _
          (u/log :mulog/invalid-json-value :value (ut/edn-str event :pretty? true))
          false)))
    events))

;; add the transformer to the your logger FOR DEBUG ONLY
(u/start-publisher! {:type :cloudwatch, :transform capture-large-json})
```

Then all you need to do is to search for the logs with the `:mulog/event-name = :mulog/invalid-event-size`.
