(ns com.brunobonacci.mulog.publishers.kinesis.kinesis-test
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.publishers.kinesis.test-publisher :as tp]
            [midje.sweet :refer :all]))



(fact "publish to local kinesis stream and assert the published message"
  (tp/with-local-kinesis-publisher
      (μ/log ::hello :to "kinesis test message"))
  => (just
        {:mulog/trace-id anything
         :mulog/timestamp number?
         :mulog/event-name "com.brunobonacci.mulog.publishers.kinesis.kinesis-test/hello",
         :mulog/namespace "com.brunobonacci.mulog.publishers.kinesis.kinesis-test",
         :to "kinesis test message"}))
