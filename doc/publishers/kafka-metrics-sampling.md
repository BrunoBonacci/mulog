## Kafka Metrics sampling
![since v0.7.0](https://img.shields.io/badge/since-v0.7.0-brightgreen)

Apache Kafka exposes a lot of information via MBeans for the [Producer](https://docs.confluent.io/platform/current/kafka/monitoring.html#producer-metrics), [Consumer](https://docs.confluent.io/platform/current/kafka/monitoring.html#new-consumer-metrics) and [Kafka Streams](https://docs.confluent.io/platform/current/streams/monitoring.html).

If you are looking to publish ***μ/log*** events into Kafka then have
a look at the [Kafka publisher](./kafka-publisher.md). If instead you
are looking to monitor Kafka applications, then you are in the right
place.

To sample these metrics we can leverage ***μ/log*** MBean sampler:

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-mbean-sampler "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-mbean-sampler {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-mbean-sampler.svg)](https://clojars.org/com.brunobonacci/mulog-mbean-sampler)

This sampler uses the publisher infrastructure to sample MBeans values
at regular intervals and publish them as ***μ/log*** events.
The sampled events will be then published to all registered publisher
and dispatched to third-party systems.

To capture everything:

``` clojure
(μ/start-publisher!
  {:type :mbean
   :mbeans-patterns ["kafka.*:*"]})
```

Or pick & choose:

``` clojure
(μ/start-publisher!
  {:type :mbean
   :mbeans-patterns ["kafka.producer:*"
                     "kafka.consumer:*"
                     "kafka.streams:*"]})
```


Here a list of patterns that can be used:

### For Kafka Producers

  - MBean: `kafka.producer:type=producer-metrics,client-id=([-.w]+)`
  - MBean: `kafka.producer:type=producer-metrics,client-id=([-.w]+)`
  - MBean: `kafka.producer:type=producer-node-metrics,client-id=([-.w]+),node-id=([0-9]+)`
  - MBean: `kafka.producer:type=producer-topic-metrics,client-id=([-.w]+),topic=([-.w]+)`

### For Kafka Consumers

  - MBean: `kafka.consumer:type=consumer-fetch-manager-metrics,client-id=([-.w]+)`
  - MBean: `kafka.consumer:type=consumer-fetch-manager-metrics,client-id=([-.w]+),topic=([-.w]+)`
  - MBean: `kafka.consumer:type=consumer-coordinator-metrics,client-id=([-.w]+)`
  - MBean: `kafka.consumer:type=consumer-metrics,client-id=([-.w]+)`
  - MBean: `kafka.consumer:type=consumer-metrics,client-id=([-.w]+)`
  - MBean: `kafka.consumer:type=consumer-node-metrics,client-id=([-.w]+),node-id=([0-9]+)`

### For Kafka Streams apps

  - MBean: `kafka.streams:type=stream-metrics,client-id=[clientId]`
  - MBean: `kafka.streams:type=stream-thread-metrics,thread-id=[threadId]`
  - MBean: `kafka.streams:type=stream-task-metrics,thread-id=[threadId],task-id=[taskId]`
  - MBean: `kafka.streams:type=stream-processor-node-metrics,thread-id=[threadId],task-id=[taskId],processor-node-id=[processorNodeId]`
  - MBean: `kafka.streams:type=stream-state-metrics,thread-id=[threadId],task-id=[taskId],[storeType]-id=[storeName]`
  - MBean: `kafka.streams:type=stream-state-metrics,thread-id=[threadId],task-id=[taskId],[storeType]-id=[storeName]`
  - MBean: `kafka.streams:type=stream-record-cache-metrics,thread-id=[threadId],task-id=[taskId],record-cache-id=[storeName]`


For more fine grained sampling please look at [MBean sampler documentation](./mbean-metrics-sampling.md).
