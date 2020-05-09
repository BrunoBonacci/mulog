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
    (cond
      (nil? failed-record-count)        (println (format
                                                   "mu/log kinesis stream '%s' publisher failure; Reason: %s"
                                                   stream-name rsp))
      (not (zero? failed-record-count)) (throw
                                          (ex-info
                                            (str "μ/log kinesis stream '" stream-name "' publisher failure")
                                                  {:rsp rsp})))))