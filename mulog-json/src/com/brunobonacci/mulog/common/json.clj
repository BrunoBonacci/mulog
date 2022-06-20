(ns com.brunobonacci.mulog.common.json
  (:require [charred.api :as json]
            [com.brunobonacci.mulog.utils :as ut]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                      ----==| I N T E R N A L |==----                       ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn- to-keyword
  "turns the argument into a keyword and interns it"
  {:inline (fn [x] `(clojure.lang.Keyword/intern ^String ~x))}
  [^String x]
  (clojure.lang.Keyword/intern x))



;;
;; Add encoders for various types
;;
(extend-protocol json/PToJSON

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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                        ----==| P U B L I C |==----                         ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(let [with-output-to-str
      (fn [writer]
        (fn [m] (str (doto (java.io.StringWriter.) (writer m)))))

      packed-writer
      (with-output-to-str
        (json/write-json-fn {:indent-str nil :escape-slash false}))

      pretty-writer
      (with-output-to-str
        (json/write-json-fn {:indent-str "    " :escape-slash false}))]

  (defn to-json
    "It takes a map and return a JSON encoded string of the given map data."
    ([m]
     (packed-writer m))
    ([m {:keys [pretty?]}]
     (if pretty?
       (pretty-writer m)
       (packed-writer m)))))



(defn from-json
  "Parses a JSON encoded string `s` into the representing data"
  ([s]
   (json/read-json s :key-fn to-keyword))
  ([s {:keys [keywordize] :or {keywordize true}}]
   (json/read-json s :key-fn (if keywordize to-keyword identity))))
