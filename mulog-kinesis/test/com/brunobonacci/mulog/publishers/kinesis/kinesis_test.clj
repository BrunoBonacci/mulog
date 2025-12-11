(ns com.brunobonacci.mulog.publishers.kinesis.kinesis-test
  (:require
   [clj-test-containers.core :as tc]
   [cognitect.aws.client.api :as aws]
   [com.brunobonacci.mulog :as u]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.mulog.flakes :as f]
   [com.brunobonacci.rdt :refer [repl-test]]))



(defn wait-for-condition
  "retries the execution of `f` until it succeeds or times out after 60sec"
  [service f]
  (let [f (fn [] (try (f) (catch Exception _ false)))
        start (System/currentTimeMillis)]
    (loop [ready (f)]
      (if (> (System/currentTimeMillis) (+ start 60000))
        (throw (ex-info (str "Waiting for " service " to meet the required condition, but timed out.") {}))
        (when (not ready)
          (Thread/sleep 500)
          (recur (f)))))))



(defn consume-stream
  [kin stream-name]
  (let [starting-sequence-number
        (->> (aws/invoke kin {:op :DescribeStream :request {:StreamName stream-name}})
          :StreamDescription
          :Shards
          first
          :SequenceNumberRange
          :StartingSequenceNumber)

        shard-iterator
        (->> (aws/invoke kin {:op      :GetShardIterator
                              :request {:StreamName             stream-name
                                        :ShardId                "shardId-000000000000"
                                        :ShardIteratorType      "AT_SEQUENCE_NUMBER"
                                        :StartingSequenceNumber starting-sequence-number}})
          :ShardIterator)

        ;; in theory this could return empty and we should use NextShardIterator
        ;; and keep scrolling the shard for new records
        kinesis-records
        (->> (aws/invoke kin {:op      :GetRecords
                              :request {:StreamName    stream-name
                                        :ShardIterator shard-iterator}})
          :Records)

        records (mapv (comp json/from-json slurp :Data) kinesis-records)]
    records))



(repl-test {:labels [:container]} "publish to local kinesis stream and assert the published message"

  (def container
    (tc/create
      { ;; locking version - see https://github.com/localstack/localstack/issues/6786
       :image-name    "localstack/localstack:0.14.0"
       :exposed-ports [4566]
       :env-vars      {"SERVICES"       "kinesis"
                       "DEBUG"          "1"
                       "DEFAULT_REGION" "eu-west-1"}
       ;; wait until container is ready
       :wait-for      {:wait-strategy :port :startup-timeout 60}}))


  (def container (tc/start! container))

  (def aws {:api               :kinesis
            :auth              :basic
            :region            "eu-west-1"
            :access-key-id     "foo"
            :secret-access-key "secret"
            :endpoint-override
            {:protocol :http
             :hostname (:host container)
             :port     (-> container :mapped-ports (get 4566))}})


  (def kin (aws/client aws))

  (wait-for-condition "kinesis service is ready"
    (fn []
      (aws/invoke kin {:op :ListStreams :request {}})))

  (def stream-name (format "mulog-test-%s" (f/snowflake)))

  (def stream
    (aws/invoke kin {:op :CreateStream :request {:ShardCount 1 :StreamName stream-name}}))

  (wait-for-condition "kinesis stream is ready"
    (fn []
      (->>
        (aws/invoke kin {:op :DescribeStream :request {:StreamName stream-name}})
        :StreamDescription
        :StreamStatus
        (= "ACTIVE"))))


  (def publisher
    (u/start-publisher!
      {:type                  :kinesis
       :stream-name           stream-name
       :publish-delay         500
       :kinesis-client-config aws}))

  ;; log a message
  (u/log ::hello :to "kinesis test message")

  (Thread/sleep 1000)

  (wait-for-condition "records to be available on kinesis stream"
    (fn []
      (->> (consume-stream kin stream-name)
        (not-empty))))

  (def records (consume-stream kin stream-name))

  (count records)
  => 1

  (first records)
  => {:mulog/trace-id string?
      :mulog/timestamp number?
      :mulog/event-name "com.brunobonacci.mulog.publishers.kinesis.kinesis-test/hello",
      :mulog/namespace "com.brunobonacci.mulog.publishers.kinesis.kinesis-test",
      :to "kinesis test message"}

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)

  )



(repl-test {:labels [:container]} "publish a message where the partition-key is missing"

  (def container
    (tc/create
      { ;; locking version - see https://github.com/localstack/localstack/issues/6786
       :image-name    "localstack/localstack:0.14.0"
       :exposed-ports [4566]
       :env-vars      {"SERVICES"       "kinesis"
                       "DEBUG"          "1"
                       "DEFAULT_REGION" "eu-west-1"}
       ;; wait until container is ready
       :wait-for      {:wait-strategy :port :startup-timeout 60}}))


  (def container (tc/start! container))

  (def aws {:api               :kinesis
            :auth              :basic
            :region            "eu-west-1"
            :access-key-id     "foo"
            :secret-access-key "secret"
            :endpoint-override
            {:protocol :http
             :hostname (:host container)
             :port     (-> container :mapped-ports (get 4566))}})


  (def kin (aws/client aws))

  (wait-for-condition "kinesis service is ready"
    (fn []
      (aws/invoke kin {:op :ListStreams :request {}})))

  (def stream-name (format "mulog-test-%s" (f/snowflake)))

  (def stream
    (aws/invoke kin {:op :CreateStream :request {:ShardCount 1 :StreamName stream-name}}))

  (wait-for-condition "kinesis stream is ready"
    (fn []
      (->>
        (aws/invoke kin {:op :DescribeStream :request {:StreamName stream-name}})
        :StreamDescription
        :StreamStatus
        (= "ACTIVE"))))


  (def publisher
    (u/start-publisher!
      {:type                  :kinesis
       :stream-name           stream-name
       :publish-delay         500
       :kinesis-client-config aws}))

  ;; log a message
  (u/log ::hello :to "kinesis message without partition key" :mulog/trace-id nil)

  (Thread/sleep 1000)

  (wait-for-condition "records to be available on kinesis stream"
    (fn []
      (->> (consume-stream kin stream-name)
        (not-empty))))

  (def records (consume-stream kin stream-name))

  (count records)
  => 1

  (first records)
  => {:mulog/trace-id nil?
      :mulog/timestamp number?
      :mulog/event-name "com.brunobonacci.mulog.publishers.kinesis.kinesis-test/hello",
      :mulog/namespace "com.brunobonacci.mulog.publishers.kinesis.kinesis-test",
      :to "kinesis message without partition key"}

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)

  )
