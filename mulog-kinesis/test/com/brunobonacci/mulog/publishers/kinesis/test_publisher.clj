(ns com.brunobonacci.mulog.publishers.kinesis.test-publisher
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.common.json :as json]
            [cognitect.aws.client.api :as aws])
  (:import (java.util.concurrent TimeUnit)))



(def KINESIS-LOCAL-SETTINGS {:api :kinesis
                             :endpoint-override
                             {:protocol :http
                              :hostname "localhost"
                              :port 4568}})



(defn parse-kinesis-response
  [rs]
  (some-> rs
    (:Records)
    (first)
    (:Data)
    (slurp)
    (json/from-json)))



(defn kinesis-invoke
  [client name op params]
  (let [rq (merge params {:StreamName name})]
    (aws/invoke client {:op op :request rq})))



(defmacro with-local-kinesis-publisher
  [command]
  `(let [name#      (format "mulog-test-%s" (ut/random-uid))
         lc#        (aws/client KINESIS-LOCAL-SETTINGS)
         create-rs# (kinesis-invoke lc# name# :CreateStream {:ShardCount 1})
         sp#        (μ/start-publisher!
                      {:type                  :kinesis
                       :stream-name           name#
                       :kinesis-client-config KINESIS-LOCAL-SETTINGS})]
     (do
       (println "Creating kinesis stream: " name#)
       (.sleep (TimeUnit/SECONDS) 5)                         ;; delay to create a stream
       (println "Kinesis stream has been created successfully: " (= create-rs# {}))
       (~@command)
       (println "Message was published to kinesis stream")
       (.sleep (TimeUnit/SECONDS) 5)                         ;; delay for kinesis processing
       (sp#)
       (let [description#               (kinesis-invoke lc# name# :DescribeStream {})
             starting-sequence-number#  (-> description#
                                          (get-in [:StreamDescription :Shards])
                                          (first)
                                          (get-in [:SequenceNumberRange :StartingSequenceNumber]))
             shard-iterator#            (kinesis-invoke lc# name# :GetShardIterator
                                          {:ShardId       "shardId-000000000000"
                                           :ShardIteratorType      "AT_SEQUENCE_NUMBER"
                                           :StartingSequenceNumber starting-sequence-number#})
             kinesis-response#          (kinesis-invoke lc# name# :GetRecords
                                          { :ShardIterator (:ShardIterator shard-iterator#)})]
         (do
           (println "Kinesis stream has been removed successfully:"
             (= {} (kinesis-invoke lc# name# :DeleteStream {})))
           (if (seq (:Records kinesis-response#))
             (parse-kinesis-response kinesis-response#) {}))))))
