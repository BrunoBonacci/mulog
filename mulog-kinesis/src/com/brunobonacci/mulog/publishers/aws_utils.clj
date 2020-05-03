(ns com.brunobonacci.mulog.publishers.aws-utils
  (:require
    [cognitect.aws.client.api :as aws]))

(defn create-kinesis-client
  [params]
  (aws/client params))

(defn publish!
  [kinesis-client stream-name records]
  (let [rsp (aws/invoke kinesis-client {:op      :PutRecords
                                        :request {:StreamName stream-name
                                                  :Records    records}})
        failed-record-count (:FailedRecordCount rsp)]
    (if (or (nil? failed-record-count)
          (not (zero? failed-record-count)))
      (println
        (format "mu/log kinesis stream '%s' publisher failure; Reason: %s" stream-name rsp)))))

(defn create-records
  [k d]
  {:PartitionKey k :Data d})