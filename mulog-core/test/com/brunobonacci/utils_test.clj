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
