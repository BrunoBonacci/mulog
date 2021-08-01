## Zipkin publisher
![since v0.2.0](https://img.shields.io/badge/since-v0.2.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-zipkin "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-zipkin {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-zipkin.svg)](https://clojars.org/com.brunobonacci/mulog-zipkin)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

The available configuration options:

``` clojure
{:type :zipkin

 ;; Zipkin endpoint (REQUIRED)
 :url  "http://localhost:9411/"


 ;; the maximum number of events which can be sent in a single
 ;; batch request to Zipkin
 :max-items     5000

 ;; Interval in milliseconds between publish requests.
 ;; μ/log will try to send the records to Zipkin
 ;; with the interval specified.
 :publish-delay 5000

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 :transform identity
 }

```

How to use it:

``` clojure
(μ/start-publisher!
  {:type :zipkin
   :url  "http://localhost:9411/"})
```

Here is an example of how the traces look like:

![zipkin traces](../images/nested-traces.png)


**NOTE: Zipkin requires an application name for the traces, use
`set-global-context` to define one**, like:

``` clojure
;; set global context
(μ/set-global-context!
  {:app-name "my-app", :version "0.1.0", :env "local"})
```

see [example here](https://github.com/BrunoBonacci/mulog/blob/master/examples/roads-disruptions/src/com/brunobonacci/disruptions/main.clj#L44-L46).
