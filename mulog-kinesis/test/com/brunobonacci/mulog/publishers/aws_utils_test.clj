(ns com.brunobonacci.mulog.publishers.aws-utils-test
  (:require [com.brunobonacci.mulog.publishers.aws-utils :as utils]
            [midje.sweet :refer :all]))

(fact "successful response does not have failures"
      (utils/has-failures? {:FailedRecordCount 0,
                            :Records [{:SequenceNumber "49606637818941416612640445792517439640700155362084388866",
                                       :ShardId "shardId-000000000000"}]})
      => false)

(fact "when requested an absent stream then response has failures"
      (utils/has-failures? {:__type "ResourceNotFoundException",
                            :message "Stream Stream-1 under account 000000000000 not found.",
                            :cognitect.anomalies/category :cognitect.anomalies/incorrect})
      => true)

(fact "when the request has validation errors then response has failures"
      (utils/has-failures? {:__type "ValidationException",
                            :message "1 validation error detected: Value null at 'shardIterator' failed to satisfy constraint: Member must not be null",
                            :cognitect.anomalies/category :cognitect.anomalies/incorrect})
      => true)