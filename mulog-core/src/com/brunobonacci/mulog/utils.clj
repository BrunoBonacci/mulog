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
  (let [ids [:mulog/trace-id   :mulog/root-trace :mulog/parent-trace]
        top [:mulog/event-name :mulog/timestamp ]
        mks (->> (keys m) (filter #(= "mulog" (namespace %))) (remove (set (concat ids top))) (sort))
        oks (->> (keys m) (remove #(= "mulog" (namespace %))) (sort))
        get-value (fn [k] (get m k))]
    (->> (map (juxt identity get-value) (concat top ids mks oks))
      (remove (let [ids (set ids)]
                (fn [[k v]] (and (ids k) (nil? v)))))
      (apply concat)
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
  "recursively remove nils from maps (in keys or values)"
  [m]
  (w/postwalk
    #(if (map? %)
       (into {} (remove (fn [[k v]] (or (nil? v) (nil? k))) %))
       %)
    m))



(defn map-values
  "Applies f to all the value of the map m"
  [f m]
  (->> m
    (map (fn [[k v]] [k (f v)]))
    (into {})))



(defmacro defalias
  "Create a local var with the same value of a var from another namespace"
  [dest src]
  `(do
     (def ~dest (var ~src))
     (alter-meta! (var ~dest) merge (select-keys (meta (var ~src)) [:doc :arglists]))))
