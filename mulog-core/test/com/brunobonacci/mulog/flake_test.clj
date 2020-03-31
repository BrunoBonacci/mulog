(ns com.brunobonacci.mulog.flake-test
  (:require [com.brunobonacci.mulog.flakes :as f]
            [midje.sweet :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
  (:import com.brunobonacci.mulog.core.Flake))



(def flake
  "Generates flake."
  (gen/fmap (fn [[l1 l2 l3]] (Flake/makeFlake l1 l2 l3))
            (gen/tuple gen/large-integer gen/large-integer gen/large-integer)))



(def homomorphic-representation
  "For all flakes, if f1 < f2 -> (str f1) < (str f2)"
  (prop/for-all
   [f1 flake
    f2 flake]
   (cond
     (< 0 (compare f1 f2)) (< 0 (compare (str f1) (str f2)))
     (> 0 (compare f1 f2)) (> 0 (compare (str f1) (str f2)))
     (= 0 (compare f1 f2)) (= 0 (compare (str f1) (str f2))))))



(fact "Property: Ensures that the homomorphic-representation property is respected"
  (tc/quick-check 100000 homomorphic-representation)
  => (contains {:pass? true}))




(def random-flake
  "Generates flake."
  (gen/no-shrink
   (gen/fmap (fn [_] (str (Flake/flake))) (gen/return 1))))



(def monotonic-property
  "For all flakes, if f1 happens before f2 -> then f1 < f2"
  (prop/for-all
   [f1 random-flake
    f2 random-flake]
   (< (compare f1 f2) 0)))


(fact "Property: Ensures that the monotonic property is respected"
  (tc/quick-check 1000000 monotonic-property)
  => (contains {:pass? true}))


(def random-flake-hex
  "Generates flake."
  (gen/no-shrink
   (gen/fmap (fn [_] (Flake/formatFlakeHex (Flake/flake))) (gen/return 1))))


(def monotonic-property-hex
  "For all flakes, if f1 happens before f2 -> then f1 < f2"
  (prop/for-all
   [f1 random-flake-hex
    f2 random-flake-hex]
   (< (compare f1 f2) 0)))


(fact "Property: Ensures that the monotonic property is respected (hex)"
  (tc/quick-check 1000000 monotonic-property-hex)
  => (contains {:pass? true}))

;;
