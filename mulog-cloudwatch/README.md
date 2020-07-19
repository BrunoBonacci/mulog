# μ/log -> Cloudwatch publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)

This project contains the `publisher` for [Amazon CloudWatch Logs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/WhatIsCloudWatchLogs.html)


## Usage
A μ/log publisher for Amazon CloudWatch Logs.

Please see [README](../README.md#cloudwatch-publisher) on main page.

## Testing

```
 lein do test
```

Alternatively, start AWS Localstack with the configured cloudwatch service

``` shell
docker-compose rm -f && docker-compose up -d
```

and execute `lein midje`

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
