# F.A.Q.

## Q: How do I **μ/log** to send the `:mulog/duration` in milliseconds?

**μ/log** will send the `:mulog/duration` in nanoseconds because it
uses the monotonic timer with a nanoseconds precision which guarantee
the highest precision even on very small measurement.

However, if you want to send the duration in *milliseconds* instead,
you can provide a *custom transformation function* to the publisher
which will be applied to all the events, prior the publishing.

For example, the following snippet starts the console publisher
with a transformation which convert the duration into milliseconds.

``` clojure
(μ/start-publisher!
 {:type :console
  :transform (fn [events]
               (map (fn [{:keys [mulog/duration] :as e}]
                      (if duration
                        (update e :mulog/duration quot 1000000)
                        e)) events))})
```
