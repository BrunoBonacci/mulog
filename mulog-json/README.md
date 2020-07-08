# μ/log json
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)


This project contains the common handling of JSON marshalling and unmarshalling.


## Usage

``` clojure
(ns your.ns
  (:require [com.brunobonacci.mulog.common.json :as json]))

;; serializa events with correct datetime formatting
;; support of exception serialization and flakes.
(json/to-json {:your :data})
;; => "{\"your\":\"data\"}"
```

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
