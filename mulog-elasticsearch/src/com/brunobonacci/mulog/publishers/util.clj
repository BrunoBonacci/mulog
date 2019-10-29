(ns com.brunobonacci.mulog.publishers.util
  (:require [clojure.string :as str]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| N A M E   M A N G L I N G |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn convert-exception
  [^Exception x]
  (let [out (new java.io.StringWriter)]
    (binding [*err* out]
      (.printStackTrace x))
    (str out)))

(defn snake-case
  [n]
  (when n
    (-> (str n)
       (str/replace #"^:" "")
       (str/replace #"/" ".")
       (str/replace #"[^\w\d_.]" "_"))))

(defn snake-case-mangle
  [[k v]]
  [(if (= :mulog/timestamp k) k (snake-case k)) v])



(defn type-mangle
  "takes a clojure map and turns into a new map where attributes have a
  type-suffix to avoid type clash in ELS"
  [[k v :as e]]
  (cond
    (= :mulog/timestamp k) e
    (int? v)        [(str k ".i") v]
    (string? v)     [(str k ".s") v]
    (double? v)     [(str k ".f") v]
    (float? v)      [(str k ".f") v]
    (map? v)        [(str k ".o") v]
    (sequential? v) [(str k ".a") v]
    (set? v)        [(str k ".a") v]
    (boolean? v)    [(str k ".b") v]
    (keyword? v)    [(str k ".k") v]
    (instance? java.util.Date v) [(str k ".t") v]
    (instance? Exception v) [(str k ".x") (convert-exception v)]
    :else e
    ))
