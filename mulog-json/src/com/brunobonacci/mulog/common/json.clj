(ns com.brunobonacci.mulog.common.json
  (:require [charred.api :as json]
            [com.brunobonacci.mulog.utils :as ut]))


;;
;; Add encoders for various types
;;
(extend-protocol json/PToJSON

  ;; Clojure keywords are serialised without the namespace
  ;; by default :-(
  clojure.lang.Keyword
  (->json-data [item]
    (-> (.sym ^clojure.lang.Keyword item)
            (.toString)))


  java.util.Date
  (->json-data [item]
    (-> (.getTime ^java.util.Date item)
            (java.time.Instant/ofEpochMilli)
            (.toString)))


  java.lang.Throwable
  (->json-data [item]
    (ut/exception-stacktrace item))


  com.brunobonacci.mulog.core.Flake
  (->json-data [item]
    (str item)))



(defn to-json
  "It takes a map and return a JSON encoded string of the given map data."
  ([m]
   (json/write-json-str m :indent-str nil :escape-slash false))
  ([m {:keys [pretty?]}]
   (json/write-json-str m :indent-str (if pretty? "  " nil) :escape-slash false)))



(defn from-json
  "Parses a JSON encoded string `s` into the representing data"
  ([s]
   (json/read-json s :key-fn keyword))
  ([s {:keys [keywordize]}]
   (json/read-json s :key-fn
     (if keywordize
       (fn [^String x] (clojure.lang.Keyword/intern x) )
       identity))))
