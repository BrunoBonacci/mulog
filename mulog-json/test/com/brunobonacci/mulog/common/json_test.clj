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



(fact "ensure that dates are serialised with milliseconds precision"

  (let [date (java.util.Date. 1607446659809)]
    (to-json {:date date}))
  => "{\"date\":\"2020-12-08T16:57:39.809Z\"}"

  (let [date (java.time.Instant/ofEpochMilli 1607446659809)]
    (to-json {:date date}))
  => "{\"date\":\"2020-12-08T16:57:39.809Z\"}"

  )



(fact "can serialize and parse"

  (->> {:a 1 :b "hello" :c {:foo true}}
    to-json
    from-json)
  => {:a 1 :b "hello" :c {:foo true}}
  )



(fact "can serialize and parse keywords with namespace"

  (->> {:one/a 1 :two.three/b "hello" :four/c {:foo true}
      :value :some.namespaced/keyword}
    to-json
    from-json)
  => {:one/a 1 :two.three/b "hello" :four/c {:foo true}
     :value "some.namespaced/keyword"}
  )



(fact "can serialiase and deserialiase an event"

  (->>
    {:mulog/event-name :your-ns/availability,
     :mulog/timestamp  1587504242983,
     :mulog/trace-id   #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
     :mulog/root-trace #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
     :mulog/duration   254402837,
     :mulog/namespace  "your-ns",
     :mulog/outcome    :ok,
     :app-name         "mulog-demo",
     :env              "local",
     :version          "0.1.0"}
    to-json
    from-json)
  =>
  {:mulog/event-name "your-ns/availability",              ;; str
   :mulog/timestamp  1587504242983,
   :mulog/trace-id   "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",  ;; str
   :mulog/root-trace "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",  ;; str
   :mulog/duration   254402837,
   :mulog/namespace  "your-ns",
   :mulog/outcome    "ok",                                ;; str
   :app-name         "mulog-demo",
   :env              "local",
   :version          "0.1.0"}

  )
