(ns com.brunobonacci.mulog.publishers.aws-utils
  (:require
    [cognitect.aws.client.api :as aws]))

(def kinesis (aws/client {:api  :kinesis}))

(defn publish!
  [stream-name records]
  (aws/invoke kinesis {:op      :PutRecords
                       :request {:StreamName stream-name
                                 :Records    records}
                       }))
(defn create-records
  [k d]
  {:PartitionKey k :Data d})