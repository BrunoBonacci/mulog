## Prometheus publisher
![since v0.5.0](https://img.shields.io/badge/since-v0.5.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-prometheus "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-prometheus {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-prometheus.svg)](https://clojars.org/com.brunobonacci/mulog-prometheus)

The available configuration options:

``` clojure
{
   ;; You can supply your own registry which will be used for all
   ;; events.  If you do not specify a registry, the default registry
   ;; is used.  The default registry is the static
   ;; `CollectorRegistry.defaultRegistry`
   ;; :registry   (CollectorRegistry/defaultRegistry)


   ;; You can setup the prometheus-publisher to push to a prometheus
   ;; PushGateway.  When to use the pushgateway:
   ;; https://prometheus.io/docs/practices/pushing/
   ;;
   ;; `:job` is a string identification used within the Promethues
   ;; PushGateway and it is always required. The pushgateway adds this
   ;; to the `job` label.  Typically a unique job/application name is
   ;; used. You can read more here:
   ;; https://github.com/prometheus/pushgateway#about-the-job-and-instance-labels
   ;;
   ;; `:gateway` is a `io.prometheus.client.exporter.PushGateway` you
   ;; can provide an existing one that your application/job uses. If
   ;; one is not provided a new one is created with the `endpoint`
   ;; configuration.
   ;;
   ;; `:endpoint` is the address which the PushGateway client should
   ;; push to.  e.g `"http://localhost:9091"`
   ;;
   ;; `:push-interval-ms` is how often (in millis) the metrics needs
   ;; to be published to the PushGateway (if configured) by default
   ;; will be every `10000` (`10s`)
   ;;
   ;;
   ;; For example:
   ;;  * endpoint configuration:
   ;;    ```
   ;;    :push-gateway {:job      "my-awesome-job"
   ;;                   :endpoint "http://localhost:9091"}
   ;;    ```
   ;;
   ;;  * existing pushgateway:
   ;;    ```
   ;;    :push-gateway {:job      "my-awesome-job"
   ;;                   :gateway  ^PushGateway existing-prometheus-pushgateway}
   ;;    ```
   ;; Notice in either configuration `job` is required.
   ;;

   ;; A function to apply to the sequence of events before publishing.
   ;; This transformation function can be used to filter, transform,
   ;; anonymise events before they are published to a external system.
   ;; by default there is no transformation.  (since v0.1.8)
   :transform         identity


   ;; A function to apply to the sequence of metrics before converting
   ;; into a collection.  This tranformation function can be used to
   ;; (alter/add/remove) metric types, (alter/add/remove) labels or
   ;; add more detailed descriptions of what the metric does.
   ;;
   ;;
   ;; For the `:histogram` metric type. You can supply `:buckets`
   ;; which will be used instead of the defaults.
   ;;
   ;; default: `[0.005 0.01 0.025 0.05 0.075 0.1 0.25 0.5 0.75 1 2.5 5 7.5 10]``
   ;;
   ;;
   ;; For the `:summary` metric type. You can suppy the following to
   ;; be used instead of the defaults.
   ;;
   ;; `:quantiles` - quantiles to be used over the sliding window of time.
   ;; default: `[[0.5 0.001][0.9 0.001][0.95 0.001][0.99 0.001][0.999 0.001]]`
   ;;
   ;; `:max-age-seconds` - duration of the time window.
   ;; default: `600`
   ;;
   ;; `:age-buckets` - buckets used to implement sliding window.
   ;; default: `5`
   ;;
   ;; by default there is no transformation.
   :transform-metrics identity}
```

How to use it:

* Example with PushGateway:

``` clojure
(μ/start-publisher!
  {:type :prometheus
   :push-gateway {:job "mulog-demo"
                  :endpoint "http://localhost:9091"}})
```


* Usage example with *Prometheus endpoint* scraping:

To expose the metrics such that they will be scraped by Prometheus,
***μ/log*** exposes the registry and offers a facility to expose the
metrics in a Ring (or Compojure) application.

``` clojure
(require '[com.brunobonacci.mulog.publishers.prometheus :as prom])
;; create your publisher
(def pub (prom/prometheus-publisher {:type :prometheus}))
;; start the publisher
(def px (μ/start-publisher! {:type :inline :publisher pub}))

;; now it is possible to access the registry
(prom/registry pub)  ;; => registry
(prom/write-str pub) ;; formatted string with metrics in scrape format
```

In order to expose these metrics you need a standard Ring handler,
here an example:

``` clojure
;; ring handler to export metrics
(fn [_]
  {:status  200
   :headers {"Content-Type" "text/plain; version=0.0.4"}
   :body    (prom/write-str pub)})
```

Or if you are using Compojure then:

``` clojure
(def my-routes
    (routes
      ;; your existing routes
      (GET "/hello" [] "Hello World!")
      ;; here you can expose the metrics to Prometheus scraping process.
      (GET "/metrics" []
        {:status  200
         :headers {"Content-Type" "text/plain; version=0.0.4"}
         :body    (prom/write-str pub)})
      (route/not-found "<h1>Page not found</h1>")))
```
