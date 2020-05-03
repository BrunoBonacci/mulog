# μ/log -> Kinesis publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)

This project contains the `publisher` for [Kinesis](https://aws.amazon.com/kinesis/)


## Usage
A μ/log publisher for Kinesis.

```
(μ/start-publisher!
  {:type :kinesis :stream-name  "your-stream-name"})
```
Please see [README](../README.md) on main page.

## Testing
1. start localstack with the configured kinesis service
``` shell
docker-compose rm -f && docker-compose up -d
```

2. Create a kinesis stream
 2.1 The stream can be created with aws cli, commands below:

`create-stream`

```shell
aws --endpoint-url=http://localhost:4568 kinesis create-stream --stream-name mulog-test-stream --shard-count 1
```

`describe-stream`
```shell
aws --endpoint-url=http://localhost:4568 kinesis describe-stream --stream-name mulog-test-stream
```
 2.2 Alternatively `kinesis_test.clj` has the util function to create a stream
 
3. Test step are mentioned in `kinesis_test.clj`  

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0) 