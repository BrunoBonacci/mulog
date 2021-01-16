(ns com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.publishers.cloudwatch.test-publisher :as tp]
            [midje.sweet :refer :all]))



(fact "publish to local cloudwatch logs service and assert the published message"

  (tp/with-local-cloudwatch-publisher
    (u/log ::hello :to "cloudwatch test message"))
  => (just
       [(just
          {:mulog/trace-id anything
           :mulog/timestamp number?
           :mulog/event-name "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/hello",
           :mulog/namespace "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test",
           :to "cloudwatch test message"})]))



(fact "publish nested traces (events must be published in timestamp order)"

  (->>
    (tp/with-local-cloudwatch-publisher
      (u/trace ::level1
        []
        (Thread/sleep 1)
        (u/trace ::level2
          []
          (Thread/sleep 1)
          (u/log ::level3 :to "cloudwatch test message"))))
    (map :mulog/event-name))

  => ["com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level1"
      "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level2"
      "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level3"])
