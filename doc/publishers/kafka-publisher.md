## Apache Kafka publisher
![since v0.1.0](https://img.shields.io/badge/since-v0.1.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-kafka "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-kafka {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-kafka.svg)](https://clojars.org/com.brunobonacci/mulog-kafka)


The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :kafka

 ;; kafka configuration
 :kafka {;; the comma-separated list of brokers (REQUIRED)
         :bootstrap.servers "localhost:9092"
         ;; you can add more kafka connection properties here
         }

 ;; the name of the kafka topic where events will be sent
 ;; :topic "mulog"

 ;; maximum number of events in a single batch
 ;; :max-items     1000

 ;; how often it will send the events Kafka  (in millis)
 ;; :publish-delay 1000

 ;; the format of the events to send into the topic
 ;; can be one of: :json, :edn, :nippy (default :json)
 ;; :format        :json

 ;; If you choose the :nippy encoding you can provide a nippy
 ;; configuration. By default it compresses the data using
 ;; LZ4 fast compressor.
 ;; For more info on the nippy configuration see official
 ;; documentation: https://github.com/ptaoussanis/nippy
 ;; :nippy {:compressor nippy/lz4-compressor}

 ;; The name of the field which it will be used as partition key
 ;; :mulog/trace-id is a unique identifier for the event it ensures
 ;; a reasonably even spread of events across all partitions
 ;; :key-field :mulog/trace-id

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 ;; :transform identity
 }
```

How to use it:

``` clojure
(μ/start-publisher!
  {:type :kafka
   :kafka {:bootstrap.servers "localhost:9092"}})
```
