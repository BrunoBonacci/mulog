## Inline publishers
![since v0.2.0](https://img.shields.io/badge/since-v0.2.0-brightgreen)

Custom publisher can be loaded dynamically via the [Custom Publishers](./custom-publishers.md)
mechanism alternatively they can also be started by providing an instance of
`com.brunobonacci.mulog.publisher.PPublisher` in `μ/start-publisher!`
as follow:

``` clojure
(def my-publisher (my-custom-publisher {})

(μ/start-publisher!
  {:type :inline :publisher my-publisher})
```
