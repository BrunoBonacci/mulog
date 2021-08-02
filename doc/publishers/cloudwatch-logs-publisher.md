## Cloudwatch Logs publisher
![since v0.4.0](https://img.shields.io/badge/since-v0.4.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-cloudwatch "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-cloudwatch {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-cloudwatch.svg)](https://clojars.org/com.brunobonacci/mulog-cloudwatch)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :cloudwatch

 ;; name of the CloudWatch log group where events will be sent
 ;; The log group be already present.
 :group-name        "mulog" ;; (REQUIRED)

 ;; maximum number of events in a single batch
 ;; :max-items     5000

 ;; how often it will send events to Amazon CloudWatch Logs (in millis)
 ;; :publish-delay 1000

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.
 ;; :transform identity

 ;; The kinesis client configuration can be used to override endpoints
 ;; and provide credentials. By default it uses the AWS DefaultAWSCredentialsProviderChain
 ;; check here for more info: https://github.com/cognitect-labs/aws-api#credentials
 ;; :cloudwatch-client-config {:api :logs}
 }
```

How to use it:

``` clojure
(μ/start-publisher!
  {:type        :cloudwatch
   :group-name  "mulog"})
```
