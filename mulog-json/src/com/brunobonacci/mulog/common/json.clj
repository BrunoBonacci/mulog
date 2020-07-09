(ns com.brunobonacci.mulog.common.json
  (:require [cheshire.core :as json]
            [cheshire.generate :as gen]
            [com.brunobonacci.mulog.utils :as ut]))



;;
;; Add Flake encoder to JSON generator
;;
(gen/add-encoder com.brunobonacci.mulog.core.Flake
  (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
    (gen/write-string json ^String (str x))))



;;
;; Add Exception encoder to JSON generator
;;
(gen/add-encoder java.lang.Throwable
  (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
    (gen/write-string json ^String (ut/exception-stacktrace x))))



(defn to-json
  "It takes a map and return a JSON encoded string of the given map data."
  [m]
  (json/generate-string m {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSSX"}))



(defn from-json
  "Parses a JSON encoded string `s` into the representing data"
  [s & {:keys [keywordize] :or {keywordize true}}]
  (json/parse-string s keywordize))
