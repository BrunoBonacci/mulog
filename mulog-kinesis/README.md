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
TODO

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0) 