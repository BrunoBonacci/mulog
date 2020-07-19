(ns com.brunobonacci.mulog.publishers.cloudwatch.test-publisher
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.utils :as ut]
            [cheshire.core :as json]
            [cognitect.aws.client.api :as aws])
  (:import (java.util.concurrent TimeUnit)))



(def CLOUDWATCH-LOCAL-SETTINGS
  {:api    :logs
   :region "eu-west-1"
   :endpoint-override
   {:protocol :http
    :hostname "localhost"
    :port     4586}})



(defn operation-status
  [op rs]
  (if (= rs {})
    (str "Operation '" op "' succeeded")
    (str "Operation '" op "' failed. Details:" rs)))



(defn parse-events
  [events]
  (some-> events
    (:events)
    first
    (:message)
    (json/parse-string true)))



(defmacro with-local-cloudwatch-publisher
  [command]
  `(let [lg-name# (format "mulog-test-%s" (ut/uuid))
         lc# (aws/client CLOUDWATCH-LOCAL-SETTINGS)
         create-lg# (aws/invoke lc# {:op      :CreateLogGroup
                                     :request {:logGroupName lg-name#}})
         cwp# (μ/start-publisher!
                {:type                     :cloudwatch
                 :group-name               lg-name#
                 :cloudwatch-client-config CLOUDWATCH-LOCAL-SETTINGS})]
     (do
       (println "Creating cloudwatch log-group: " lg-name#)
       (.sleep (TimeUnit/SECONDS) 5)                        ;; delay to create a log group
       (println (operation-status "create log-group" create-lg#))
       (~@command)
       (println "Message was put into cloudwatch")
       (.sleep (TimeUnit/SECONDS) 5)                        ;; delay for cloudwatch processing
       (cwp#)
       (let [describe-lg#  (aws/invoke lc# {:op :DescribeLogGroups
                                            :request {:logGroupNamePrefix "mulog-test-"}})
             lg-exact-name# (-> describe-lg#
                              :logGroups
                              first
                              :logGroupName)
             describe-stream#   (aws/invoke lc# {:op  :DescribeLogStreams
                                                 :request {:logGroupName lg-exact-name#}})
             stream-exact-name#  (-> describe-stream#
                                   :logStreams
                                   first
                                   :logStreamName)
             log-events#        (aws/invoke lc# {:op :GetLogEvents
                                                 :request {:logGroupName lg-exact-name#
                                                           :logStreamName stream-exact-name#}})
             remove-lg#         (aws/invoke lc# {:op  :DeleteLogGroup
                                                 :request {:logGroupName lg-exact-name#}})]
         (do
           (println (operation-status "remove log-group" remove-lg#))
           (if (seq (:events log-events#))
             (parse-events log-events#) {}))))))
