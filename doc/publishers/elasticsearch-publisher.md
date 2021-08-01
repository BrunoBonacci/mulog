## Elasticsearch publisher
![since v0.1.0](https://img.shields.io/badge/since-v0.1.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-elasticsearch "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-elasticsearch {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-elasticsearch.svg)](https://clojars.org/com.brunobonacci/mulog-elasticsearch)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :elasticsearch

 ;; Elasticsearch endpoint (REQUIRED)
 :url  "http://localhost:9200/"


 ;; The Elasticsearch version family.
 ;; one of: `:auto` `:v6.x`  `:v7.x`
 :els-version   :auto

 ;; the maximum number of events which can be sent in a single
 ;; batch request to Elasticsearch
 :max-items     5000

 ;; Interval in milliseconds between publish requests.
 ;; μ/log will try to send the records to Elasticsearch
 ;; with the interval specified.
 :publish-delay 5000

 ;; Choose an indexing strategy:
 ;; between `:index-pattern` or `:data-stream`, the default is `:index-pattern`

 ;; The index pattern to use for the events
 ;; The pattern uses the Java DateTimeFormatter format:
 ;; see: https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html
 ;; :index-pattern "'mulog-'yyyy.MM.dd"

 ;; data streams are available since Elasticsearch 7.9
 ;; :data-stream   "mulog-stream"

 ;; extra http options to pass to the HTTP client
 :http-opts {}


 ;; Whether or not to change the attribute names
 ;; to facilitate queries and avoid type clashing
 ;; See more on that in the link below.
 :name-mangling true

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 :transform identity
 }

```

For more information about the index patterns check the
[DateTimeFormatter](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html)
documentation.

How to use it:

``` clojure
(μ/start-publisher!
  {:type :elasticsearch
   :url  "http://localhost:9200/"})
```

Supported versions: `6.7+`, `7.x`

Read more on [Elasticsearch name mangling](../els-name-mangling.md) here.
