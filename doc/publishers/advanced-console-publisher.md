## Advanced console publisher
![since v0.6.0](https://img.shields.io/badge/since-v0.6.0-brightgreen)

It outputs the events into the standard output in JSON format

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-adv-console "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-adv-console {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-adv-console.svg)](https://clojars.org/com.brunobonacci/mulog-adv-console)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](../json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :console-json

 ;; Whether or not to output must be pretty-printed (multiple lines)
 :pretty? false

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 :transform identity
 }
```

How to use it:

``` clojure
(μ/start-publisher! {:type :console-json})
```
