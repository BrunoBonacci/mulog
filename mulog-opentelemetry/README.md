# μ/log -> OpenTelemetry publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-opentelemetry.svg)](https://clojars.org/com.brunobonacci/mulog-opentelemetry)

This project contains the `publisher` for [OpenTelemetry](https://opentelemetry.io/)


## Usage

Please see [Documentation page](../doc/publishers/opentelemetry-publisher.md).

## Testing

``` shell
docker-compose rm -f && docker-compose up -d
```

Then open: http://localhost:16686/ for Jaeger UI

Here is an example of OpenTelemetry traces:

![disruption traces](../examples/roads-disruptions/doc/images/disruption-trace.png)


## License

Copyright © 2019-2025 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
