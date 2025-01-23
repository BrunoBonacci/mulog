(ns com.brunobonacci.mulog.publishers.elasticsearch.util
  (:require [clojure.string :as str]
            [clojure.walk :as w]
            [com.brunobonacci.mulog.utils :as ut]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| N A M E   M A N G L I N G |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



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
    (sequential? v) [(str k ".a") (mapv (fn [item] (conj {} (type-mangle ["_aVal" item]))) v)]
    (set? v)        [(str k ".a") (mapv (fn [item] (conj {} (type-mangle ["_aVal" item]))) v)]
    (boolean? v)    [(str k ".b") v]
    (keyword? v)    [(str k ".k") v]
    (symbol? v)     [(str k ".k") (str v)]
    (instance? java.util.Date v) [(str k ".t") v]
    (instance? Throwable v) [(str k ".x") (ut/exception-stacktrace v)]
    :else e
    ))



(defn mangle-map
  [m]
  (let [mangler (comp type-mangle snake-case-mangle)]
    (w/postwalk
      (fn [i]
        (if (map? i)
          (->> i
            (map (fn [entry] (mangler entry)))
            (into {}))
          i))
      m)))
