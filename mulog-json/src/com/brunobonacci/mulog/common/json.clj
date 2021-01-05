(ns com.brunobonacci.mulog.common.json
  (:require [jsonista.core :as json]
            [com.brunobonacci.mulog.utils :as ut])
  (:import com.fasterxml.jackson.core.json.WriterBasedJsonGenerator))



;;
;; Add encoders for various types
;;
(def encoders
  (atom
    {;; Add Exception encoder to JSON generator
     java.lang.Throwable
     (fn [x ^WriterBasedJsonGenerator gen]
       (.writeString gen ^String (ut/exception-stacktrace x)))

     ;; Add Flake encoder to JSON generator
     com.brunobonacci.mulog.core.Flake
     (fn [x ^WriterBasedJsonGenerator gen]
       (.writeString gen ^String (str x)))}))



(def default-mapper-options
  {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSSX"})



(def ^:private mapper-options
  (memoize
    (fn [pretty? encoders]
      (json/object-mapper
        (assoc default-mapper-options
          :pretty pretty?
          :encoders encoders)))))



(def pretty-mapper
  (json/object-mapper
    (assoc default-mapper-options :pretty? true)))



(defn to-json
  "It takes a map and return a JSON encoded string of the given map data."
  ([m]
   (json/write-value-as-string m (mapper-options false @encoders)))
  ([m {:keys [pretty?]}]
   (json/write-value-as-string m (mapper-options pretty? @encoders))))



(defn from-json
  "Parses a JSON encoded string `s` into the representing data"
  ([s]
   (json/read-value s json/keyword-keys-object-mapper))
  ([s {:keys [keywordize]}]
   (json/read-value s (if keywordize json/keyword-keys-object-mapper json/default-object-mapper))))
