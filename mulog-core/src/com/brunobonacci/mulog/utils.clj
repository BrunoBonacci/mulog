(ns com.brunobonacci.mulog.utils
  (:require [com.brunobonacci.mulog.flakes :refer [snowflake]]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.walk :as w]))



(defn java-version
  "It returns the current Java major version as a number"
  []
  (as->  (System/getProperty "java.version") $
    (str/split $ #"\.")
    (if (= "1" (first $)) (second $) (first $))
    (Integer/parseInt $)))



(defmacro os-java-pid
  "it returns the OS pid for the current java process."
  []
  (if (>= (java-version) 9)
    `(.pid (java.lang.ProcessHandle/current))
    ;; java <= 8
    `(-> (java.lang.management.ManagementFactory/getRuntimeMXBean)
        (.getName)
        (str/split #"@")
        (first)
        (Long/parseLong))))



(defn uuid
  "It returns a random UUID as string"
  []
  (str (java.util.UUID/randomUUID)))



(defn random-uid
  "It returns a random 128-bit unique id with a base 36 encoding"
  []
  (let [uuid (java.util.UUID/randomUUID)
        u1   (.getMostSignificantBits uuid)
        u2   (.getLeastSignificantBits uuid)]
    (str (Long/toUnsignedString u1 36)
         (Long/toUnsignedString u2 36))))



(defn puid
  "It returns a random 192-bit unique id"
  []
  (snowflake))



(defn edn-str
  "Return a EDN representation of the given value.
   Same as `pr-str` but without ellipsis."
  [v & {:keys [pretty?] :or {pretty? false}}]
  (binding [*print-length* nil
            *print-level*  nil]
    (if pretty?
      ;; pretty-printed representation
      (with-out-str
        (pp/pprint v))
      ;; compact representation
      (pr-str v))))



(defn pprint-event-str
  "pretty print event to a string"
  [m]
  (let [top [:mulog/event-name :mulog/timestamp]
        tops (set top)
        mks (->> (keys m) (filter #(= "mulog" (namespace %))) (remove tops) (sort))
        oks (->> (keys m) (remove #(= "mulog" (namespace %))) (sort))
        get-value (fn [k] (get m k))]
    (->> (mapcat (juxt identity get-value) (concat top mks oks))
       (apply array-map)
       (#(edn-str % :pretty? true)))))



(defn pprint-event
  "pretty print event"
  [m]
  (println (pprint-event-str m)))



(defn exception-stacktrace
  "returns a string representation of an exception and its stack-trace"
  [^Throwable x]
  (let [sw (java.io.StringWriter.)
        pw (java.io.PrintWriter. sw)]
    (.printStackTrace x ^java.io.PrintWriter pw)
    (str sw)))



(defn remove-nils
  "recursively remove nils from maps, vectors and lists."
  [m]
  (->> m
     (w/postwalk
      (fn [i]
        (cond
          (map? i)        (into {} (remove (comp nil? second) i))
          (map-entry? i)  i
          (vector? i)     (into [] (remove nil? i))
          (set? i)        (into #{} (remove nil? i))
          (sequential? i) (remove nil? i)
          :else           i)))))



(defn ->transducer
  "For backward compatibility. Takes a ***μ/log***
  transformation function and turns it into a
  transducer as of 0.1.9.

  Up to 0.1.8, all the built-in publisher supported custom
  transformation only via the `:transform` configuration,
  which defaults to `identity`.

  Users could provide *custom transformation functions*
  which take events and return events to handle any
  kind of transformation of filtering.

  ```clojure
  (fn [events]
    (map (fn [{:keys [mulog/duration] :as e}]
           (if duration
             (update e :mulog/duration quot 1000000)
             e)) events))
  ```

  As of 0.1.9, the built-in publishers have been changed
  to support built-in leveled logging and transformation
  functions provided as transducers, not functions, via
  the `:transduce` configuration.

  In order to maintain backward compatibility, these
  publishers still support the `:transform` configuration.

  `->transducer` takes such a function and turns it
  into a stateful transducer suitable for publishers which
  allow a single `transducer` parameter in place of
  the `transform` parameter defined pre-0.1.8.

  For such publishers, `:transduce`, if provided, takes
  precedence over `:transform` which, if provided, takes
  precedence over the default `(map identity)` transducer.
  "
  [transform]
  (letfn [(->xform [transform]
            (fn [rf]
              (let [acc (volatile! [])]
                (fn
                  ([] (rf))
                  ([result]
                   (reduce rf [] (transform @acc)))
                  ([result input]
                   (vswap! acc conj input)
                   result)))))]
    (or (some-> transform ->xform)
        (map identity))))



(comment

  (defn tx [coll]
    (map #(do (println "inn" %)
              (let [r (inc %)]
                (println "out" r)
                r))
         coll))

  (sequence (comp (map #(do (println "foo" %) %))
                  (->transducer tx)
                  (map #(do (println "bar" %) %))
                  (map dec)
                  (map #(do (println "baz" %) %)))
            [1 2 3])

  (transduce (comp (map #(do (println "foo" %) %))
                   (->transducer tx)
                   (map #(do (println "bar" %) %))
                   (map dec)
                   (map #(do (println "baz" %) %)))
             conj
             [1 2 3])

  )


