## Kinesis publisher
![since v0.3.0](https://img.shields.io/badge/since-v0.3.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-kinesis "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-kinesis {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-kinesis.svg)](https://clojars.org/com.brunobonacci/mulog-kinesis)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :kinesis

 ;; the name of the Amazon Kinesis steam where events will be sent
 ;; The stream must be already present.
 :stream-name       "mulog" (REQUIRED)

 ;; maximum number of events in a single batch
 ;; :max-items     500

 ;; how often it will send events to Amazon Kinesis (in millis)
 ;; :publish-delay 1000

 ;; the format of the events to send into the topic
 ;; can be one of: :json, :edn (default :json)
 ;; :format        :json

 ;; The name of the field which it will be used as partition key
 ;; :mulog/trace-id is a unique identifier for the event it ensures
 ;; a reasonably even spread of events across all partitions
 ;; :key-field :mulog/trace-id

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 ;; :transform identity

 ;; The kinesis client configuration can be used to override endpoints
 ;; and provide credentials. By default it uses the AWS DefaultAWSCredentialsProviderChain
 ;; check here for more info: https://github.com/cognitect-labs/aws-api#credentials
 ;; :kinesis-client-config {:api :kinesis}
 }
```

How to use it:

``` clojure
(μ/start-publisher!
  {:type        :kinesis
   :stream-name "mulog"})
```
