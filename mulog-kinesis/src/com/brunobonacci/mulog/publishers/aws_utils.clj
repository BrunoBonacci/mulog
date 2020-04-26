(ns com.brunobonacci.mulog.publishers.aws-utils
  (:require
    [cognitect.aws.client.api :as aws]
    [cognitect.aws.credentials :as credentials]
    [cognitect.aws.util :as u])
  (:import (java.nio ByteBuffer)))

(def region (u/getenv "AWS_REGION"))

(def kinesis (aws/client {:api                  :kinesis
                          :region               (keyword region)
                          :credentials-provider (credentials/environment-credentials-provider)}))
(defn publish!
  [stream-name records]
  (aws/invoke kinesis {:op      :PutRecords
                       :request {:StreamName stream-name
                                 :Records    records}
                       }))
(defn create-record!
  [k d]
  {:PartitionKey k
   :Data         (-> d
                     .getBytes
                     ByteBuffer/wrap)
   })