# μ/log -> Cloudwatch publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-cloudwatch.svg)](https://clojars.org/com.brunobonacci/mulog-cloudwatch)

This project contains the `publisher` for [Amazon CloudWatch Logs](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/WhatIsCloudWatchLogs.html)


## Usage
A μ/log publisher for Amazon CloudWatch Logs.

Please see [Documentation page](../doc/publishers/cloudwatch-logs-publisher.md).

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

Copyright © 2019-2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
