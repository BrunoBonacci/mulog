(ns com.brunobonacci.mulog.common.json-test
  (:require [com.brunobonacci.mulog.common.json :refer :all]
            [midje.sweet :refer :all]
            [com.brunobonacci.mulog.flakes :as f]))



(fact "can serialize Flakes"

  (let [flake (f/flake)]
    (to-json {:flake flake})
    => (contains (str flake)))
  )



(fact "can serialize java Exceptions"

  (let [exception (RuntimeException. "BOOM" (IllegalArgumentException. "BAM"))]
    (to-json {:exception exception}))
  => (contains "BOOM")
  )



(fact "can serialize Clojure Exceptions"

  (let [exception (ex-info "BOOM" {:error :data})]
    (to-json {:exception exception}))
  => (contains "BOOM")
  )



(fact "can serialize and parse"

  (->> {:a 1 :b "hello" :c {:foo true}}
    to-json
    from-json)
  => {:a 1 :b "hello" :c {:foo true}}
  )
