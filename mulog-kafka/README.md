# μ/log -> Kafka publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-kafka.svg)](https://clojars.org/com.brunobonacci/mulog-kafka)


This project contains the `publisher` for [Apache Kafka](https://kafka.apache.org/)


## Usage

Please see [Documentation page](../doc/publishers/kafka-publisher.md).

## Testing

``` shell
docker-compose rm -f && docker-compose up -d
docker exec -ti mulog-kafka_kafka_1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic mulog
```

## License

Copyright © 2019-2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
