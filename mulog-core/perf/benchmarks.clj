(ns benchmarks
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f]
            [amalloy.ring-buffer :refer [ring-buffer]])
  (:import com.brunobonacci.mulog.core.Flake))



(defn log-call-simple
  []
  (u/log :test-log :bechmark "speed"))



(defn log-call-with-context
  []
  (u/with-context {:context :value1}
    (u/log :test-log :bechmark "speed")))



(defn trace-call-simple
  []
  (u/trace :test-trace
    [:bechmark "speed"]
    1))



(defn trace-call-with-capture
  []
  (u/trace :test-trace
    {:pairs [:bechmark "speed"]
     :capture (fn [v] {:value v})}
    1))



(defn flake-creation
  []
  (Flake/flake))



(defn flake-string-representation
  []
  (.toString (Flake/flake)))



(defn flake-snowflake
  []
  (f/snowflake))



(defn flake-hex-representation
  []
  (Flake/formatFlakeHex (Flake/flake)))



(defn flake-create-and-parse-string
  []
  (Flake/parseFlake (.toString (Flake/flake))))
