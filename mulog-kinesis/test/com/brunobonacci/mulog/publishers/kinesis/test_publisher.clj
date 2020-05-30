(ns com.brunobonacci.mulog.publishers.kinesis.test-publisher
  (:require [com.brunobonacci.mulog :as μ]
            [cheshire.core :as json]
            [cognitect.aws.client.api :as aws])
  (:import (java.util.concurrent TimeUnit)))



(def KINESIS-LOCAL-STREAM-NAME "mulog-test-stream")



(def KINESIS-LOCAL-SETTINGS {:api :kinesis
                             :endpoint-override
                             {:protocol :http
                              :hostname "localhost"
                              :port 4568}})



(defmacro with-local-kinesis-client
  []
  `(aws/client KINESIS-LOCAL-SETTINGS))



(defmacro with-local-kinesis-stream
  [client op params]
  `(let [rq# (merge ~params {:StreamName KINESIS-LOCAL-STREAM-NAME})]
     (aws/invoke ~client {:op ~op :request rq#})))



(defmacro with-local-kinesis-publisher
  [command]
  `(let [sp# (μ/start-publisher!
              {:type                  :kinesis
               :stream-name           KINESIS-LOCAL-STREAM-NAME
               :kinesis-client-config KINESIS-LOCAL-SETTINGS})]
     (do
       (~@command)
       ;; delay for kinesis processing
       (.sleep (TimeUnit/SECONDS) 5)
       (sp#))))



(defn get-records-from-stream
  []
  (let [kinesis                   (with-local-kinesis-client)
        stream-desc               (with-local-kinesis-stream kinesis :DescribeStream {})
        starting-sequence-number  (-> stream-desc
                                    (get-in [:StreamDescription :Shards])
                                    (first)
                                    (get-in [:SequenceNumberRange :StartingSequenceNumber]))
        shard-iterator            (with-local-kinesis-stream kinesis :GetShardIterator {:ShardId                "shardId-000000000000"
                                                                                        :ShardIteratorType      "AT_SEQUENCE_NUMBER"
                                                                                        :StartingSequenceNumber starting-sequence-number})
        kinesis-response          (with-local-kinesis-stream kinesis :GetRecords { :ShardIterator (:ShardIterator shard-iterator)})]
    (if (seq (:Records kinesis-response))
      (->
          kinesis-response
        (:Records)
        (first)
        (:Data)
        (slurp)
        (json/parse-string true))
      {})))
