(ns com.brunobonacci.mulog.buffer-test
  (:require [com.brunobonacci.mulog.buffer :refer :all]
            [midje.sweet :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))



(def operations
  (gen/frequency
    [[6 (gen/tuple (gen/return :enqueue)
          (gen/frequency
            [[9 gen/any]
             [1 (gen/return nil)]]))]

     [2 (gen/tuple (gen/return :dequeue)
          (gen/frequency
            [[9 gen/small-integer]
             [1 (gen/return nil)]]))]

     [2 (gen/tuple (gen/return :clear) )]]))



(def ops-sequence
  (gen/vector operations))



(def robust-operation-property
  "It doesn't exists a sequence of operations which can throw an exception"
  (prop/for-all
    [ops ops-sequence]
    (reduce (fn [rb [op v]]
              (case op
                :enqueue (enqueue rb v)
                :dequeue (dequeue rb v)
                :clear   (clear   rb)))
      (ring-buffer 5)
      ops)))



(fact "Property: Ensures that the operations on the ring-buffer are robust"
  (tc/quick-check 1000 robust-operation-property)
  => (contains {:pass? true}))
