# μ/log -> Kafka publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)


This project contains the `publisher` for [Apache Kafka](https://kafka.apache.org/)


## Usage

Please see [README](../README.md#apache-kafka-publisher) on main page.

## Testing

Create an alias with

``` shell
# on linux
sudo ip addr add 192.168.200.200/32 dev wlan0

# on Mac OSX
sudo ifconfig en0 alias 192.168.200.200/32 up

# to remove alias
# sudo ifconfig en0 -alias 192.168.200.200/32
```

Alternatively, edit the `docker-compose.yaml` and add your local ip (non 127.0.0.1).
This is necessary for the producer to connect to the broker.


``` shell
docker-compose rm -f && docker-compose up -d
docker exec -ti mulog-kafka_kafka_1 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic mulog
```

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
