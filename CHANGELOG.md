# Changelog

## v0.6.0 - (2020-12-19)

  - [**NEW**] Added Filesystem metrics sampler (thanks to @emlyn)
  - [**NEW**] Added Advanced Console Publisher with JSON formatting.
  - [**NEW**] Added support for Elasticsearch data-streams. (thanks to @ozimos)
  - [**NEW**] Added support for Nippy encoding to the Kafka publisher
  - [**POTENTIALLY BREAKING**] Migrated to JSON encoding from Cheshire
    to Jasonista (thanks to @ozimos).  It is potentially breaking if
    you have custom JSON encoders setup for Cheshire you will need to
    add add them as described into [How to JSON encode custom Java classes](./doc/json-encode.md)
  - Fixed issue in SlackPublisher trying to send empty messages #41 (thanks @ak-coram)
  - Fixed issue in `simple-file-publisher` not handling files without parent dir #43 (thanks to @emlyn)


## v0.5.0 - (2020-09-22)

  - [**NEW**] Added Prometheus publisher (thanks to @brandonstubbs)
  - [#35] Fixed issue by which the wrong namespace was captured when
    using ***μ/trace*** (thanks @DarinDouglass)
  - Support for external root-trace and parent-trace
  - Updated dependencies


## v0.4.0 - (2020-08-04)

  - [**NEW**] Added CloudWatch Logs publisher (thanks to @etolbakov)
  - [**NEW**] Jaeger Tracing publisher
  - Performance improvement in ***μ/trace*** and `with-context`
  - Zipkin, Kafka, Kinesis, File publisher stability improvements and
    small bug fixes
  - Publishers failures are now tracked with ***μ/log***
  - Relaxed compile-time requirement for pairs


## v0.3.1 - (2020-07-10)

  - Zipkin: fix issue on publishing tags with nil values


## v0.3.0 - (2020-07-08)

  - [**NEW**] Added Kinesis publisher (thanks to @etolbakov)
  - [**NEW**] Added Slack publisher (thanks to @anonimitoraf)
  - [**NEW**] Added JVM Metrics publisher (thanks to @PabloReszczynski)
  - ELS: auto-detects the version by default


## v0.2.0 - (2020-05-03)

  - Added flag to support Elasticsearch v6.x correctly
  - Added `:mulog/trace-id` to base event with a flake (192 bit
    time-ordered unique id)
  - [**NEW**] Added Zipkin publisher
  - `μ/trace` function api change. (**BREAKING CHANGE**)
    * If you were using the ***μ/trace*** function
    with the `result*` function you will have change the code as follow:
    ``` Clojure
    ;; before
    (μ/trace ::availability
     [:product-id product-id, :order order-id, :user user-id]
     (fn [{:keys [status]}] {:http-status status})  ;; THIS CHANGED
     (product-availability product-id))

    ;; after
    (μ/trace ::availability
     {:pairs [:product-id product-id, :order order-id, :user user-id]
      :capture (fn [{:keys [status]}] {:http-status status})}
     (product-availability product-id))
    ```
    `μ/trace` uses without the `result*` function are NOT AFFECTED.
  - Added ability to pretty print events on the console.
  - Bumped dependency to **Kafka-2.5.0** in `mulog-kakfa`.
    `mulog-kakfa` doesn't require **Kafka-2.5.0** specifically,
    therefore if you need to use a different version, just exclude
    the dependency.
  - Added `:inline` publishers


## v0.1.8 - (2020-03-09)

  - Added ability to configure multi-publishers
  - Added ability to provide custom transformation to built-in publishers
  - Added ability to release resources when a publisher is stopped
  - Fixed issue with Kafka publisher handling serialization of Exceptions
