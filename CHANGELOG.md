# Changelog

## v0.8.2 - (2022-03-27)

  - Fixed incorrect namespace captured by u/log #85
  - Fixed Elasticsearch Bulk API soft-error responses being ignored #79

## v0.8.1 - (2021-08-19)

  - Fixed link to JSON encode custom Java classes (#80) (thanks @practicalli-john)
  - Fixed mbeans sampler error when no transformation function is provided.


## v0.8.0 - (2021-07-09)

  - Dropped dependency to clj-time (deprecated) in favour of java.time (thanks @frankitox)
  - Bumped jasonista to 0.3.3 with jackson 2.12.13
  - Fixed name shadowing issue with registry in Prometheus publisher (#68) (thanks @piotr-yuxuan)
  - Added integration tests for Kafka publisher (#71) (thanks @evg-tso)
  - Added `:transform-samples` for samplers and fix (#72)
  - Added DEPRECATION WARNING: on `:filesystem-metrics` sampler config option (#74)
  - Added DEPRECATION WARNING: on `:mbean` sampler config option (#75)


## v0.7.1 - (2021-03-23)

  - [**NEW**] Added JMX MBean sampler to capture/sample the value of MBeans.
  - [**NEW**] Added Kafka metrics sampler to sample metrics from Kafka apps
  - [**NEW**] Fixed GraalVM native compilation
  - Delayed initialisation of thread-pools until first publisher is initialised.


## v0.6.5 - (2021-02-14)

  - [**SECURITY**] Fix security vulnerabilities found in transitive dependencies (#59) [cloudwatch, kinesis]


## v0.6.4 - (2021-01-16)

  - Fix issue with configuration of `jvm-metrics` when `:sampling-interval` isn't provided


## v0.6.3 - (2021-01-16)

  - [**NEW**] Added documentation and scripts on how to use ***μ/log*** with Amazon Athena (thanks to @etolbakov)
  - Fix issue with `os-java-pid` and JDK EarlyAdopters versions
  - Fix issue with `jvm-metrics` sampling `divide-by-zero` error (#57).


## v0.6.2 - (2021-01-08)

  - Fix order of events in Cloudwatch publisher


## v0.6.1 - (2021-01-05)

  - Fix Json pretty printing
  - Bumped Jasonista version
  - Fix Elasticsearch custom http-options #56 (thanks @ozimos)


## v0.6.0 - (2020-12-19)

  - [**NEW**] Added Filesystem metrics sampler (thanks to @emlyn)
  - [**NEW**] Added Advanced Console Publisher with JSON formatting.
  - [**NEW**] Added support for Elasticsearch data-streams. (thanks to @ozimos)
  - [**NEW**] Added support for Nippy encoding to the Kafka publisher
  - [**POTENTIALLY BREAKING**] Migrated to JSON encoding from Cheshire
    to Jasonista (thanks to @ozimos).  It is potentially breaking if
    you have custom JSON encoders setup for Cheshire you will need to
    add them as described into [How to JSON encode custom Java classes](/doc/json-encode.md)
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
