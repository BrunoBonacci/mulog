(ns com.brunobonacci.utils-test
  (:require [com.brunobonacci.mulog.utils :as ut]
            [midje.sweet :refer :all]))



(fact "remove-nils from maps recursively"

  (ut/remove-nils
    {:a 1 :b 2 :c nil :d false nil :alpha
     :e {:foo 1 :bar nil :zoo {:zulu nil :v [1 nil 3]}}})
  => {:a 1, :b 2, :d false, :e {:foo 1, :zoo {:v [1 nil 3]}}}

  (ut/remove-nils {:a nil}) => {}
  (ut/remove-nils {nil :a}) => {}

  (ut/remove-nils {:a {:b {:c nil}}}) => {:a {:b {}}}

  (ut/remove-nils nil) => nil
  (ut/remove-nils {}) => {}
  (ut/remove-nils [1 2 3]) => [1 2 3]
  (ut/remove-nils [1 nil 3]) => [1 nil 3]
  (ut/remove-nils '(1 nil 3)) => '(1 nil 3)
  (ut/remove-nils #{1 nil 3}) => #{1 nil 3}
  )



(fact "about pprint-events: pretty prints all different type of events"

  (ut/pprint-event-str
    {:a              1
     :foo/a          2
     "string"        "bar"
     nil             nil
     :mulog/trace-id #mulog/flake "4k1urR3C1p6SzVlF18urraWHAUPiK4-A"
     :mulog/event-name 1234
     :mulog/timestamp 1234567890
     true            false
     1.2             2.1
     [1 2 3]         [3 2 1]
     {:b 1}          {:c 2}})
  => "{:mulog/event-name 1234,\n :mulog/timestamp 1234567890,\n :mulog/trace-id #mulog/flake \"4k1urR3C1p6SzVlF18urraWHAUPiK4-A\",\n nil nil,\n 1.2 2.1,\n :a 1,\n :foo/a 2,\n [1 2 3] [3 2 1],\n \"string\" \"bar\",\n true false,\n {:b 1} {:c 2}}\n"

  )
