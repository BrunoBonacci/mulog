## OpenTelemetry (OTLP) publisher
![since v0.10.0](https://img.shields.io/badge/since-v0.10.0-brightgreen)

This publisher doesn't require java/clojure instrumentation libraries.
It relies on existing ***μ/trace*** forms to capture and publish traces.

It uses the OpenTelemetry Protocol (OTLP) for wire communication over
HTTP/JSON (not GRPC).

For a full list of supported vendors see: [OpenTelemetry Ecosystem](https://opentelemetry.io/ecosystem/vendors/)

It can publish to any OpenTelemetry compatible collector.

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-opentelemetry "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-opentelemetry {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-opentelemetry.svg)](https://clojars.org/com.brunobonacci/mulog-opentelemetry)


The available configuration options:

``` clojure
{:type :open-telemetry

 ;; OpenTelemetry Collector endpoint for OTLP HTTP/JSON (REQUIRED)
 :url  "http://localhost:4318/"

 ;; Whether to send traces or logs (metrics in mulog are just logs)
 :send :traces

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

 ;; extra http options to pass to the HTTP client
 :http-opts {}
 }

```

How to use it:

In order to send **traces** and **logs** use:
``` clojure
(μ/start-publisher!
  {:type :multi
   :publishers
   [{:type :open-telemetry
     :send :traces
     :url  "http://localhost:4318/"}
    {:type :open-telemetry
     :send :logs
     :url  "http://localhost:4318/"}]})
```

In order to send **traces** only use:
``` clojure
(μ/start-publisher!
  {:type :open-telemetry
   :send :traces
   :url  "http://localhost:4318/"})
```

In order to send **logs** only use:
``` clojure
(μ/start-publisher!
  {:type :open-telemetry
   :send :logs
   :url  "http://localhost:4318/"})
```


Here is an example of how the traces look like:

![traces](../images/nested-traces.png)


**NOTE: OpenTelemetry requires an application name (`:app-name`) for the traces, use
`set-global-context` to define one**, like:

``` clojure
;; set global context
(μ/set-global-context!
  {:app-name "my-app", :version "0.1.0", :env "local"})
```

see [example here](https://github.com/BrunoBonacci/mulog/blob/master/examples/roads-disruptions/src/com/brunobonacci/disruptions/main.clj#L44-L46).


Mulog's OpenTelemetry Publisher doesn't require, nor depends on, a OpenTelemetry wrapper library,
however if you are already using the Java OpenTelemetry wrapper and you wish to attach the traces to an existing OTEL span then use the `with-context` as follow:


``` clojure
;; import (io.opentelemetry.api.trace Span)

(defn otel-context
  "Extract the current OTEL trace-id and span-id from the active span.
   Returns a map with :trace-id and :span-id, or nil if no active span."
  []
  (let [span (Span/fromContext (Context/current))
        span-ctx (.getSpanContext span)]
    (when (.isValid span-ctx)
      {:mulog/root-trace (.getTraceId span-ctx)
       :mulog/parent-trace  (.getSpanId span-ctx)})))


;; attach the mulog context to the OTEL context
(u/with-context (otel-context)
   (u/log ::event-name ,,,)
   (u/trace ::trace-name [] ,,,))
```

This typically would only be required at the edge of a system (such API endpoint or similar).
