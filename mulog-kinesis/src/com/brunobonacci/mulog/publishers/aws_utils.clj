(ns com.brunobonacci.mulog.publishers.aws-utils
  (:require
    [cognitect.aws.client.api :as aws]))

(defn has-failures?
  [rs]
  (not
    (and
      (contains? rs :FailedRecordCount)
      (zero? (:FailedRecordCount rs)))))

(defn create-kinesis-client
  [params]
  (aws/client params))

(defn publish!
  [kinesis-client stream-name records]
  (let [rs (aws/invoke kinesis-client {:op      :PutRecords
                                        :request {:StreamName stream-name
                                                  :Records    records}})]
    (if (has-failures? rs)
      (throw
        (ex-info
          (str "μ/log kinesis publisher failure, stream '" stream-name "'")
          {:rs rs})))))