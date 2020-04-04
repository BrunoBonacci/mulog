(ns com.brunobonacci.mulog.levels-test
  (:require [com.brunobonacci.mulog.levels :as lvl]
            [midje.sweet :refer :all]))

(fact
  "->filter does what it says"

  ((lvl/->filter nil)           [{:id 1 :mulog/level ::lvl/info}])
  => [{:id 1 :mulog/level ::lvl/info}]

  ((lvl/->filter ::lvl/warning) [{:id 1 :mulog/level ::lvl/info}])
  => ()

  ((lvl/->filter ::lvl/info)    [{:id 1 :mulog/level ::lvl/info}])
  => [{:id 1 :mulog/level ::lvl/info}]

  ((lvl/->filter ::lvl/debug)   [{:id 1 :mulog/level ::lvl/info}])
  => [{:id 1 :mulog/level ::lvl/info}]

  (let [ad-hoc (-> (make-hierarchy)
                   (derive ::bar ::foo)
                   (derive ::baz ::bar))]
    ((lvl/->filter ::bar ad-hoc :some)
              [{:id 1 :some ::foo}
               {:id 2 :some ::bar}
               {:id 3 :some ::bar}
               ])) => [{:id 2 :some ::bar}
                       {:id 3 :some ::bar}
                       ]
  )