# Changelog

## v0.1.9 - (unreleased)
  - Added flag to support ElasticSearch v6.x correctly
  - Added Zipkin publisher
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


## v0.1.8 - (2020-03-09)

  - Added ability to configure multi-publishers
  - Added ability to provide custom transformation to built-in publishers
  - Added ability to release resources when a publisher is stopped
  - Fixed issue with Kafka publisher handling serialization of Exceptions
