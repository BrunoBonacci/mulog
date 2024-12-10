(ns com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test
  (:require
   [com.brunobonacci.mulog :as u]
   [com.brunobonacci.mulog.flakes :as f]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.rdt :refer [repl-test]]
   [clj-test-containers.core :as tc]
   [cognitect.aws.client.api :as aws]))


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



(repl-test {:labels [:container]} "publish to local cloudwatch logs service and assert the published message"

  (def container
    (tc/create
      { ;; locking version - see https://github.com/localstack/localstack/issues/6786
       :image-name "localstack/localstack:0.14.0"
       :exposed-ports [4566]
       :env-vars {"SERVICES" "logs"
                  "DEBUG"    "1"
                  "DEFAULT_REGION" "eu-west-1"}
       ;; wait until container is ready
       :wait-for {:strategy :port :startup-timeout 60}}))


  (def container (tc/start! container))

  (def aws {:api    :logs
            :auth              :basic
            :region            "eu-west-1"
            :access-key-id     "foo"
            :secret-access-key "secret"
            :endpoint-override
            {:protocol :http
             :hostname (:host container)
             :port     (-> container :mapped-ports (get 4566))}})


  (def log-group (format "mulog-test-%s" (f/snowflake)))


  (def cwl (aws/client aws))

  (wait-for-condition "cloudwatch-logs service is ready"
    (fn []
      (aws/invoke cwl {:op :DescribeLogGroups :request {}})))


  ;; create log group
  (aws/invoke cwl {:op      :CreateLogGroup
                   :request {:logGroupName log-group}})
  => {}

  ;; verify log group is present
  (->> (aws/invoke cwl {:op :DescribeLogGroups :request {:logGroupNamePrefix log-group}})
    :logGroups
    first)
  => {:logGroupName log-group}


  ;; start publisher
  (def publisher
    (u/start-publisher!
      {:type                     :cloudwatch
       :group-name               log-group
       :publish-delay            500
       :cloudwatch-client-config aws}))

  (u/log ::hello :to "cloudwatch test message")


  ;; retrieve log stram name
  (def log-stream
    (->> (aws/invoke cwl {:op  :DescribeLogStreams
                        :request {:logGroupName log-group}})
      :logStreams
      first
      :logStreamName))


  (wait-for-condition "events are published"
    (fn []
      (->>
        (aws/invoke cwl {:op :GetLogEvents
                         :request {:logGroupName log-group
                                   :logStreamName log-stream}})
        :events
        not-empty)))

  ;; retrie events
  (def events
    (->> (aws/invoke cwl {:op :GetLogEvents
                        :request {:logGroupName log-group
                                  :logStreamName log-stream}})
      :events
      (map :message)
      (map json/from-json)))

  (count events)
  => 1

  (first events)
  => {:mulog/trace-id string?
     :mulog/timestamp number?
     :mulog/event-name "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/hello",
     :mulog/namespace "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test",
     :to "cloudwatch test message"}

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )




(repl-test {:labels [:container]} "publish nested traces (events must be published in timestamp order)"

  (def container
    (tc/create
      { ;; locking version - see https://github.com/localstack/localstack/issues/6786
       :image-name "localstack/localstack:0.14.0"
       :exposed-ports [4566]
       :env-vars {"SERVICES" "logs"
                  "DEBUG"    "1"
                  "DEFAULT_REGION" "eu-west-1"}
       ;; wait until container is ready
       :wait-for {:strategy :port :startup-timeout 60}}))


  (def container (tc/start! container))

  (def aws {:api    :logs
            :auth              :basic
            :region            "eu-west-1"
            :access-key-id     "foo"
            :secret-access-key "secret"
            :endpoint-override
            {:protocol :http
             :hostname (:host container)
             :port     (-> container :mapped-ports (get 4566))}})


  (def log-group (format "mulog-test-%s" (f/snowflake)))


  (def cwl (aws/client aws))

  (wait-for-condition "cloudwatch-logs service is ready"
    (fn []
      (aws/invoke cwl {:op :DescribeLogGroups :request {}})))


  ;; create log group
  (aws/invoke cwl {:op      :CreateLogGroup
                   :request {:logGroupName log-group}})
  => {}

  ;; verify log group is present
  (->> (aws/invoke cwl {:op :DescribeLogGroups :request {:logGroupNamePrefix log-group}})
    :logGroups
    first)
  => {:logGroupName log-group}


  ;; start publisher
  (def publisher
    (u/start-publisher!
      {:type                     :cloudwatch
       :group-name               log-group
       :publish-delay            1500
       :cloudwatch-client-config aws}))

  (u/trace ::level1
    []
    (Thread/sleep 1)
    (u/trace ::level2
      []
      (Thread/sleep 1)
      (u/log ::level3 :to "cloudwatch test message")))


  ;; retrieve log stram name
  (def log-stream
    (->> (aws/invoke cwl {:op  :DescribeLogStreams
                        :request {:logGroupName log-group}})
      :logStreams
      first
      :logStreamName))


  (wait-for-condition "events are published"
    (fn []
      (->>
        (aws/invoke cwl {:op :GetLogEvents
                         :request {:logGroupName log-group
                                   :logStreamName log-stream}})
        :events
        not-empty)))

  ;; retrie events
  (def events
    (->> (aws/invoke cwl {:op :GetLogEvents
                        :request {:logGroupName log-group
                                  :logStreamName log-stream}})
      :events
      (map :message)
      (map json/from-json)))

  (count events)
  => 3

  (mapv :mulog/event-name events)
  => ["com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level1"
      "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level2"
      "com.brunobonacci.mulog.publishers.cloudwatch.cloudwatch-test/level3"]

  :rdt/finalize
  ;; stop publisher
  (publisher)
  (tc/stop! container)
  )
