(ns com.brunobonacci.mulog.publishers.kinesis-test
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.publishers.test-publisher :as tp]
            [midje.sweet :refer :all])
  (:import (java.util.concurrent TimeUnit)))

(fact "create kinesis stream"
      (tp/with-local-kinesis-stream (tp/with-local-kinesis-client) :CreateStream {:ShardCount 1})
      => (just {}))

(fact "describe kinesis stream"
      (.sleep (TimeUnit/SECONDS) 5)                         ;; delay to create a stream
      (def describe-stream-response
        (tp/with-local-kinesis-stream (tp/with-local-kinesis-client) :DescribeStream {}))

      describe-stream-response

      => (just
            {:StreamDescription anything})

      (:StreamDescription describe-stream-response)

      => (just
            {:EncryptionType "NONE",
                 :EnhancedMonitoring anything,
                 :HasMoreShards false,
                 :RetentionPeriodHours 24,
                 :Shards anything,
                 :StreamCreationTimestamp anything,
                 :StreamARN "arn:aws:kinesis:us-east-1:000000000000:stream/mulog-test-stream",
                 :StreamName "mulog-test-stream",
                 :StreamStatus "ACTIVE"}))

(fact "publish to local kinesis stream"
      (tp/with-local-kinesis-publisher
        (μ/log ::hello :to "kinesis test message"))
      (tp/get-records-from-stream)
      => (just
           {:mulog/trace-id anything
            :mulog/timestamp number?
            :mulog/event-name "com.brunobonacci.mulog.publishers.kinesis-test/hello",
            :mulog/namespace "com.brunobonacci.mulog.publishers.kinesis-test",
            :to "kinesis test message"}))

(fact "delete kinesis stream"
      (tp/with-local-kinesis-stream (tp/with-local-kinesis-client) :DeleteStream {})
      => (just {}))