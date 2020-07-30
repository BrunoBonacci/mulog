(ns benchmarks
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f]
            [amalloy.ring-buffer :refer [ring-buffer]])
  (:import com.brunobonacci.mulog.core.Flake))



;;
;; track performances of a simple μ/log entry
;;
(defn log-call-simple
  []
  (u/log :test-log :bechmark "speed"))



;;
;; track the cost of the context use
;;
(defn log-call-with-context
  []
  (u/with-context {:context :value1}
    (u/log :test-log :bechmark "speed")))



;;
;; track the cost of the context use (two nested)



;;
(defn log-call-with-context2
  []
  (u/with-context {:context :value1}
    (u/with-context {:level 2}
      (u/log :test-log :bechmark "speed"))))



;;
;; track the cost of μ/trace call
;;
(defn trace-call-simple
  []
  (u/trace :test-trace
    [:bechmark "speed"]
    1))



;;
;; track the cost of capturing values from the output
;;
(defn trace-call-with-capture
  []
  (u/trace :test-trace
    {:pairs [:bechmark "speed"]
     :capture (fn [v] {:value v})}
    1))



;;
;; track the cost of a flake creation
;;
(defn flake-creation
  []
  (Flake/flake))



;;
;; track the cost of a flake representation as string
;;
(defn flake-string-representation
  []
  (.toString (Flake/flake)))



;;
;; track the cost of a snowflake creation
;;
(defn flake-snowflake
  []
  (f/snowflake))



;;
;; track the cost of a flake hex representation as string
;;
(defn flake-hex-representation
  []
  (Flake/formatFlakeHex (Flake/flake)))



;;
;; track the cost of a flake parsing
;;
(defn flake-create-and-parse-string
  []
  (Flake/parseFlake (.toString (Flake/flake))))
