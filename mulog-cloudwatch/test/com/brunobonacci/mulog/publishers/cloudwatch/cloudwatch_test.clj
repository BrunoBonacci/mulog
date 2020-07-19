(ns com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.publishers.cloudwatch.test-publisher :as tp]
            [midje.sweet :refer :all]))



(fact "publish to local cloudwatch logs service and assert the published message"

  (tp/with-local-cloudwatch-publisher
      (μ/log ::hello :to "cloudwatch test message"))
  => (just
        {:mulog/trace-id anything
         :mulog/timestamp number?
         :mulog/event-name "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/hello",
         :mulog/namespace "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test",
         :to "cloudwatch test message"}))
