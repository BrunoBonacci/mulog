# μ/log -> Kinesis publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-kinesis.svg)](https://clojars.org/com.brunobonacci/mulog-kinesis)

This project contains the `publisher` for [Kinesis](https://aws.amazon.com/kinesis/)


## Usage
A μ/log publisher for Kinesis.

Please see [Documentation page](../doc/publishers/kinesis-publisher.md).

## Testing

To test just run:
``` shell
lein test
```


Start AWS Localstack with the configured kinesis service
``` shell
docker-compose rm -f && docker-compose up -d
lein midje
docker-compose kill && docker-compose rm -f
```

## License

Copyright © 2019-2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
