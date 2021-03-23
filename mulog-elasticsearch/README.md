# μ/log -> Elasticsearch publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-elasticsearch.svg)](https://clojars.org/com.brunobonacci/mulog-elasticsearch)

This project contains the `publisher` for [Elasticsearch](https://www.elastic.co/products/elastic-stack)


## Usage

Please see [Documentation page](../doc/publishers/elasticsearch-publisher.md).

## Testing

``` shell
docker-compose rm -f && docker-compose up -d
```

Then open: http://localhost:9000/ for Kibana, then add the index pattern `mulog-*`

Follow the instructions at
https://www.elastic.co/guide/en/elasticsearch/reference/current/set-up-a-data-stream.html
to set up a data stream `mulog-stream`

## License

Copyright © 2019-2021 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
