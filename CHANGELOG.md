# Changelog

## v0.3.0 - (unreleased)
  - [**NEW**] Added Kinesis publisher (thanks to @etolbakov)
  - [**NEW**] Added Slack publisher (thanks to @anonimitoraf)
  - [**NEW**] Added JVM Metrics publisher (thanks to @PabloReszczynski)
  - ELS: auto-detects the version by default

## v0.2.0 - (2020-05-03)
  - Added flag to support ElasticSearch v6.x correctly
  - Added `:mulog/trace-id` to base event with a flake (192 bit time-ordered unique id)
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
