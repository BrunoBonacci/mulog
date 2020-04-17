(ns com.brunobonacci.mulog.publishers.util
  (:require [clojure.string :as str]))


(defn kebab-to-title-case
  "kebab-case -> Kebab Case"
  [kebab]
  (-> kebab
      ;; "kebab-case" -> " Kebab Case 123"
      (str/replace #"(?:^|-)(\w)" (fn [[_ c]] (str " " (str/upper-case c))))
      (str/triml)))
;;test
;; (kebab-to-title-case "kebab-case-123")

(defn humanize
  "Transforms symbols, keywords into strings"
  [x]
  (cond
    (keyword? x) (name x)
    (symbol? x) (name x)
    :else x
    ))
