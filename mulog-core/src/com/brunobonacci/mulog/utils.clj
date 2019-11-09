(ns com.brunobonacci.mulog.utils
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]))



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
        u2   (.getLeastSignificantBits uuid)
        u1   (if (neg? u1) (- u1) u1)
        u2   (if (neg? u2) (- u2) u2)]
    (str (Long/toString u1 36)
         (Long/toString u2 36))))



(defn puid
  "It returns a random 128-bit unique id"
  []
  (random-uid))



(defn pprint-event-str
  "pretty print event to a string"
  [m]
  (let [top [:mulog/event-name :mulog/timestamp]
        tops (set top)
        mks (->> (keys m) (filter #(= "mulog" (namespace %))) (remove tops) (sort))
        oks (->> (keys m) (remove #(= "mulog" (namespace %))) (sort))
        get-value (fn [k] (get m k))]
    (with-out-str
      (->> (mapcat (juxt identity get-value) (concat top mks oks))
         (apply array-map)
         (pp/pprint)))))




(defn pprint-event
  "pretty print event"
  [m]
  (println (pprint-event-str m)))
