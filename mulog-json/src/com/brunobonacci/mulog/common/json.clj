(ns com.brunobonacci.mulog.common.json
  (:require [jsonista.core :as json]
            [com.brunobonacci.mulog.utils :as ut])
  (:import com.fasterxml.jackson.core.json.WriterBasedJsonGenerator))



;;
;; Add encoders for various types
;;
;; TODO: add ability to register/deregister new types
(def encoders
  {;; Add Exception encoder to JSON generator
   java.lang.Throwable
   (fn [x ^WriterBasedJsonGenerator gen]
     (.writeString gen ^String (ut/exception-stacktrace x)))

   ;; Add Flake encoder to JSON generator
   com.brunobonacci.mulog.core.Flake
   (fn [x ^WriterBasedJsonGenerator gen]
     (.writeString gen ^String (str x)))})



(def object-mapper-options
  {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSSX"
   :encoders encoders})



(def plain-mapper
  (json/object-mapper object-mapper-options))



(def pretty-mapper
  (json/object-mapper
    (assoc object-mapper-options :pretty? true)))



(defn to-json
  "It takes a map and return a JSON encoded string of the given map data."
  ([m]
   (json/write-value-as-string m plain-mapper))
  ([m {:keys [pretty?]}]
   (json/write-value-as-string m (if pretty? pretty-mapper plain-mapper))))



(defn from-json
  "Parses a JSON encoded string `s` into the representing data"
  ([s]
   (json/read-value s json/keyword-keys-object-mapper))
  ([s {:keys [keywordize]}]
   (json/read-value s (if keywordize json/keyword-keys-object-mapper json/default-object-mapper))))
