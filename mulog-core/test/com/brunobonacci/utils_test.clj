(ns com.brunobonacci.utils-test
  (:require [com.brunobonacci.mulog.utils :as ut]
            [midje.sweet :refer :all]))

(fact
 "remove-nils does what it says"

 (ut/remove-nils
  {:a 1 :b 2 :c nil :d false
   :e {:foo 1 :bar nil :zoo {:zulu nil :v [1 nil 3]}}})
 => {:a 1, :b 2, :d false, :e {:foo 1, :zoo {:v [1 3]}}}


 (ut/remove-nils nil) => nil
 (ut/remove-nils {}) => {}
 (ut/remove-nils [1 2 3]) => [1 2 3]
 (ut/remove-nils [1 nil 3]) => [1 3]
 (ut/remove-nils '(1 nil 3)) => '(1 3)
 (ut/remove-nils #{1 nil 3}) => #{1 3}
 )
