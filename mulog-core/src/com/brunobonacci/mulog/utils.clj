(ns com.brunobonacci.mulog.utils
  (:require [com.brunobonacci.mulog.flakes :refer [snowflake]]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.walk :as w])
  (:import [com.brunobonacci.mulog.core ClojureThreadLocal]))



(defn try-parse-long
  [^String value]
  (try
    (Long/parseLong value)
    (catch Exception _
      0)))



(defn java-version
  "It returns the current Java major version as a number"
  []
  (as->  (System/getProperty "java.specification.version") $
    (str/split $ #"[^\d]")
    (if (= "1" (first $)) (second $) (first $))
    (try-parse-long $)))



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
       (try-parse-long))))



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
      ;; compact representation, exception are represented multiple lines
      (str/replace (pr-str v) #"\n+" " "))))



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



;;; Credit Metosin
;;; https://github.com/metosin/reitit/blob/0bcfda755f139d14cf4eff37e2b294f573215213/modules/reitit-core/src/reitit/impl.cljc#L136
(defn fast-assoc
  "Like assoc but only takes one kv pair. Slightly faster."
  {:inline
   (fn [a k v]
     `(.assoc ~(with-meta a {:tag 'clojure.lang.Associative}) ~k ~v))}
  [^clojure.lang.Associative a k v]
  (.assoc ^clojure.lang.Associative a k v))



;;; Credit Metosin
;;; https://github.com/metosin/compojure-api/blob/master/src/compojure/api/common.clj#L46
(defn fast-map-merge
  "Returns a map that consists of the second of the maps assoc-ed onto
  the first. If a key occurs in more than one map, the mapping from
  te latter (left-to-right) will be the mapping in the result."
  [x y]
  (cond
    (nil? x) y
    (nil? y) x
    :else
    (reduce-kv
      (fn [m k v]
        (fast-assoc m k v))
      x
      y)))



(defn thread-local
  "A thread-local variable which can be deref'd"
  ([]
   (ClojureThreadLocal.))
  ([init]
   (ClojureThreadLocal. init)))



(defmacro thread-local-binding
  "Like the `binding` macro but for thread-local vars. (only 1 binding is supported)"
  {:style/indent 1}
  [binding & body]
  (when-not (and (vector? binding) (= 2 (count binding)))
    (throw (ex-info "the binding vector must be a clojure vector with 2 elements, symbol and value"
             {:bindings binding})))
  (let [[sym val] binding]
    `(let [^ClojureThreadLocal sym# ~sym
           val# ~val
           b# (deref sym#)]
       (.set sym# val#)
       (try ~@body (finally (.set sym# b#))))))



(defn deep-merge
  "Like merge, but merges maps recursively. It merges the maps from left
  to right and the right-most value wins. It is useful to merge the
  user defined configuration on top of the default configuration."
  [& maps]
  (let [maps (filter (comp not nil?) maps)]
    (if (every? map? maps)
      (apply merge-with deep-merge maps)
      (last maps))))



(defn iso-datetime-from-millis
  "Returns a ISO 8601 formatted string of the given UTC timestamp in milliseconds"
  [^long millis]
  (.format java.time.format.DateTimeFormatter/ISO_INSTANT (java.time.Instant/ofEpochMilli millis)))
