(ns com.brunobonacci.mulog.publishers.kinesis.aws-utils-test
  (:require [com.brunobonacci.mulog.publishers.kinesis :refer :all]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates com.brunobonacci.mulog.publishers.kinesis has-failures?)



(fact "successful response does not have failures"
  (has-failures?
      {:FailedRecordCount 0,
       :Records [{:SequenceNumber "49606637818941416612640445792517439640700155362084388866",
                  :ShardId "shardId-000000000000"}]})
  => false)



(fact "response with failed records has failures"
  (has-failures?
      {:FailedRecordCount 5,
       :Records [{:SequenceNumber "49606637818941416612640445792517439640700155362084388866",
                  :ShardId "shardId-000000000000"}]})
  => true)



(fact "when requested an absent stream then response has failures"
  (has-failures?
      {:__type "ResourceNotFoundException",
       :message "Stream Stream-1 under account 000000000000 not found.",
       :cognitect.anomalies/category :cognitect.anomalies/incorrect})
  => true)



(fact "when the request has validation errors then response has failures"
  (has-failures?
      {:__type "ValidationException",
       :message "1 validation error detected: Value null at 'shardIterator' failed to satisfy constraint: Member must not be null",
       :cognitect.anomalies/category :cognitect.anomalies/incorrect})
  => true)
