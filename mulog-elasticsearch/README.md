# μ/log -> Elasticsearch publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)


This project contains the `publisher` for [Elasticsearch](https://www.elastic.co/products/elastic-stack)


## Usage

Please see [README](../README.md#elasticsearch-publisher) on main page.

## Testing

``` shell
docker-compose rm -f && docker-compose up -d
```

Then open: http://localhost:9000/ for Kibana, then add the index pattern `mulog-*`

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
