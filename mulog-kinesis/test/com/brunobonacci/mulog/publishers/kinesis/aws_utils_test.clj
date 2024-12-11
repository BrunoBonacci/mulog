(ns com.brunobonacci.mulog.publishers.kinesis.aws-utils-test
  (:require [com.brunobonacci.mulog.publishers.kinesis :as kp]
            [com.brunobonacci.rdt :refer [repl-test]]))


(repl-test "successful response does not have failures"
  (#'kp/has-failures?
    {:FailedRecordCount 0,
     :Records [{:SequenceNumber "49606637818941416612640445792517439640700155362084388866",
                :ShardId "shardId-000000000000"}]})
  => false)



(repl-test "response with failed records has failures"
  (#'kp/has-failures?
    {:FailedRecordCount 5,
     :Records [{:SequenceNumber "49606637818941416612640445792517439640700155362084388866",
                :ShardId "shardId-000000000000"}]})
  => true)



(repl-test "when requested an absent stream then response has failures"
  (#'kp/has-failures?
    {:__type "ResourceNotFoundException",
     :message "Stream Stream-1 under account 000000000000 not found.",
     :cognitect.anomalies/category :cognitect.anomalies/incorrect})
  => true)



(repl-test "when the request has validation errors then response has failures"
  (#'kp/has-failures?
    {:__type "ValidationException",
     :message "1 validation error detected: Value null at 'shardIterator' failed to satisfy constraint: Member must not be null",
     :cognitect.anomalies/category :cognitect.anomalies/incorrect})
  => true)
