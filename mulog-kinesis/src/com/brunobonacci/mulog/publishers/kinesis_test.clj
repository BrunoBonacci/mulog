(ns com.brunobonacci.mulog.publishers.kinesis-test
  (:require [com.brunobonacci.mulog :as μ]
            [cheshire.core :as json]
            [cognitect.aws.client.api :as aws]))

(def KINESIS-LOCAL-STREAM-NAME  "mulog-test-stream")
(def KINESIS-LOCAL-SETTINGS  {:api  :kinesis
                              :endpoint-override {:protocol :http :hostname "localhost" :port 4568}})

(def local-kinesis (aws/client KINESIS-LOCAL-SETTINGS))

(defn get-records-from-stream
  [kinesis stream-name]
  (let [stream-desc               (aws/invoke kinesis { :op      :DescribeStream
                                                       :request {:StreamName stream-name}})
        starting-sequence-number  (-> stream-desc
                                      (get-in [:StreamDescription :Shards])
                                      (first)
                                      (get-in [:SequenceNumberRange :StartingSequenceNumber]))
        shard-iterator            (aws/invoke kinesis { :op      :GetShardIterator
                                                       :request {
                                                                 :StreamName             stream-name
                                                                 :ShardId                "shardId-000000000000"
                                                                 :ShardIteratorType      "AT_SEQUENCE_NUMBER"
                                                                 :StartingSequenceNumber starting-sequence-number
                                                                 }})
        kinesis-response          (aws/invoke local-kinesis {:op      :GetRecords
                                                             :request {
                                                                       :ShardIterator (:ShardIterator shard-iterator)
                                                                       }})]
    (->
      kinesis-response
      (:Records)
      (first)
      (:Data)
      (slurp)
      (json/parse-string true))))

(defn describe-stream
  [kinesis stream-name]
  (aws/invoke kinesis { :op  :DescribeStream
                        :request {:StreamName stream-name}}))
(defn create-stream
  [kinesis stream-name]
  (aws/invoke kinesis { :op      :CreateStream
                        :request {:StreamName stream-name :ShardCount 1}}))                        :request {:StreamName stream-name}}))
(defn delete-stream
  [kinesis stream-name]
  (aws/invoke kinesis { :op  :DeleteStream
                        :request {:StreamName stream-name}}))

(comment

  ;; 1.1.(optional) check if the stream exist
  (describe-stream local-kinesis KINESIS-LOCAL-STREAM-NAME)

  ;; 1.2.(optional) create stream if needed
  (create-stream local-kinesis KINESIS-LOCAL-STREAM-NAME)

  ;; 2. start a publisher
  (μ/start-publisher!
    {:type :kinesis
     :stream-name KINESIS-LOCAL-STREAM-NAME
     :kinesis-client-params   KINESIS-LOCAL-SETTINGS})

  ;; 3. send data
  (μ/log ::hello :to "kinesis test message")

  ;; 4. get the response
  (def response-from-kinesis
    (get-records-from-stream local-kinesis KINESIS-LOCAL-STREAM-NAME))

  ;; 5. get the message
  (def original-message
    (:to response-from-kinesis))

  ;; 6.(optional) delete stream
  (delete-stream local-kinesis KINESIS-LOCAL-STREAM-NAME))