## Advanced File publishers
![since v0.6.0](https://img.shields.io/badge/since-v0.6.0-brightgreen)

### JSON File publisher

It outputs the events into a file in JSON format, one entry per line

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-adv-file "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-adv-file {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-adv-file.svg)](https://clojars.org/com.brunobonacci/mulog-adv-file)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](../json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :file-json

 ;; the destination for the serialized JSON events.
 ;; Can be a path as a String or java.io.File, in which case
 ;; parent directories will be created before writing,
 ;; or anything that is coercible to a java.io.Writer.
 :filename "/path/to/file.json"

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 :transform identity
 }
```

How to use it:

``` clojure
(μ/start-publisher! {:type :file-json :filename "/path/to/file.json"})
```
