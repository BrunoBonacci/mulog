## Custom publishers
![since v0.1.0](https://img.shields.io/badge/since-v0.1.0-brightgreen)

To use your own publisher create a function which take a configuration
and return an instance of `PPublisher` protocol and then use the
`:custom` dynamic loader.  Ensure that the jar is added to the
classpath and then just add the fully qualified function name:

``` clojure
(μ/start-publisher!
  {:type :custom
   :fqn-function "my-namespace.publisher/my-custom-publisher"

   ;; add here additional configuration options which will be passed
   ;; to the custom publisher.
   })
```

For more information about how to implement custom publisher see:
[How to write custom publishers](../custom-publishers.md)
