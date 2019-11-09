(ns com.brunobonacci.publisher-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.test-publisher :as tp]
            [midje.sweet :refer :all]))



(fact
 "a successful delivery"

 (tp/with-processing-pusblisher
   {}
   (u/log :test))

 => (just
    [(contains
      {:mulog/event-name :test})])
 )



(fact
 "the inbox buffer limits the number of events"

 (tp/with-processing-pusblisher
   {}
   (dotimes [_ 200]
     (u/log :test)))

 => (n-of (contains {:mulog/event-name :test}) 100)
 )



(fact
 "if the publisher fails it retries"

 (tp/with-processing-pusblisher
   {:process (tp/rounds [:fail :ok]) :rounds 2}
   (u/log :test))

 => (just
    [(contains
      {:mulog/event-name :test})])
 )



(fact
 "if the publisher fails it retries until it succeeds"

 (tp/with-processing-pusblisher
   {:process (tp/rounds [:fail :fail :fail :fail :ok]) :rounds 5}
   (u/log :test))

 => (just
    [(contains
      {:mulog/event-name :test})])
 )
