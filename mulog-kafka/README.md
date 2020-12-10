# μ/log -> Kafka publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)


This project contains the `publisher` for [Apache Kafka](https://kafka.apache.org/)


## Usage

Please see [README](../README.md#apache-kafka-publisher) on main page.

## Testing

``` shell
docker-compose rm -f && docker-compose up -d
docker exec -ti mulog-kafka_kafka_1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic mulog
```

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
