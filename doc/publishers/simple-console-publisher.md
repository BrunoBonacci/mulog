## Simple console publisher
![since v0.1.0](https://img.shields.io/badge/since-v0.1.0-brightgreen)

It is bundled with the core module, no extra dependencies required.
It outputs the events into the standard output in EDN format, mostly
intended for local development.

The available configuration options:

``` clojure
{:type :console

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
(μ/start-publisher! {:type :console})
```
