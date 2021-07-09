# Custom transformations

All built-in publisher support custom transformation of the events
before being processed by the publisher.  ***μ/log***'s publishers
accept a custom transformation function via the `:transform` key in
the publisher configuration options.

The transformation function is a function that takes a sequence of
events and returns a potentially modified sequence of events.

```
transform -> event-seq -> event-seq
```

With this type of function you can filter the events you wish to be
sent to the specified publisher, apply transformations, drop events
which are not interesting to you etc.

Here is an example of the general usage with an identity function:

``` clojure
(μ/start-publisher!
  {:type :console
   :transform (fn [events] events)})
```

For example, let assume that I would like to send to the console
publisher only the events with the following names:
`:myapp/payment-done` and `:myapp/transaction-closed`, here is a
sample of the transform function


``` clojure
(u/start-publisher!
  {:type :console
   :pretty? true

   :transform
   (fn [events]
     (filter #(or (= (:mulog/event-name %) :myapp/payment-done)
                  (= (:mulog/event-name %) :myapp/transaction-closed))
       events))})
```

With the above transform we will only see on the console events of the
above type.  There is an easier way to write the `filter` predicate
function via a library called `where` [https://github.com/BrunoBonacci/where](https://github.com/BrunoBonacci/where).

`where` offers a small DSL to write powerful predicate functions which
are robust, nil-safe, and easier to read.  With this library we can
rewrite the above predicate function as follow:

``` clojure
(u/start-publisher!
  {:type :console
   :pretty? true
   :transform
   (partial filter (where :mulog/event-name :in? [:myapp/payment-done :myapp/transaction-closed]))})
```

As another example we might want to see only large transactions:

``` clojure
(u/start-publisher!
  {:type :console
   :pretty? true
   :transform
   (partial filter (where [:and [:mulog/event-name :is? :myapp/transaction-closed]
                          [:amount > 1000]]))})
```

Or maybe we are only interested into events with errors:

``` clojure
(u/start-publisher!
  {:type :console
   :pretty? true
   :transform
   (partial filter :exception)})
```


Another possibility is to change the events in way that are specific
to the destination system or the particular way our project or team
needs.

One common need is to send the `:mulog/duration` in milliseconds
rather than nanoseconds. This is easily done with `:transform`.

``` clojure
 (μ/start-publisher!
   {:type :console
    :transform (fn [events]
                 (map (fn [{:keys [mulog/duration] :as e}]
                        (if duration
                          (update e :mulog/duration quot 1000000)
                          e)) events))})
```

Should the custom transformation raise an exception, the exception
will be recorded as event in ***μ/log*** with the following event name
`:mulog/publisher-error` and the actual exception in the `:exception`
field.


## Custom transformation of samplers.

Samplers are special publishers. Instead of sending events off to
another system, they generating new events by sampling system
variables. Although they use the publishers infrastructure they are
not publishers. Examples of samplers are the
[`:jvm-metrics`](./publishers/jvm-metrics-sampling.md) which samples
memory and garbage collector metrics at regular intervals, or
[`:filesystem-metrics`](./publishers/filesystem-metrics-sampling.md)
which samples total and free spaces for attached file-systems.

The samplers accept a similar custom transforming function called `:transform-samples`
with the following signature:

```
transform-samples -> sample-seq -> sample-seq
```

**A key difference between the `:transform-samples` and `:transform`
is that the `:transform-samples` is only available in the built-in
samplers.**

Samplers do not support the general `:transform` function. The reason
is that the `:transform` function works on events, while
`:transform-samples` works on samples **before** they are recorded as
events, and they act on the sampled value. This means that the transformation
function received maps which don't have all the common event fields like
`:mulog/event-name` and `:mulog/timestamp` etc.

For example, the custom `:transform-samples` for `:filesystem-metrics` sampler
in this example access a field called `:total-bytes` which is part of the sample

``` clojure
(μ/start-publisher!
  {:type :filesystem-metrics
   ;; (e.g. filter only volumes over 1 GB)
   :transform-samples (partial filter #(> (:total-bytes %) 1e9))})
```

The generated sample will look like:

``` clojure
;; This is a SAMPLE
{:name "/dev/disk1s1"
 :type "apfs"
 :path "/"
 :readonly? false
 :total-bytes 499963170816
 :unallocated-bytes 40646381568
 :usable-bytes 30255194112}
```

and the recorded events will look like:

``` clojure
;; This is an EVENT
{:mulog/event-name :mulog/filesystem-metrics-sampled,
 :mulog/timestamp 1601629811722,
 :mulog/trace-id #mulog/flake "4YcWozKEUcfE9JxfQMnuwb-NnO8ygEpE",
 :filesystem-metrics
 {:name "/dev/disk1s1"
  :type "apfs"
  :path "/"
  :readonly? false
  :total-bytes 499963170816
  :unallocated-bytes 40646381568
  :usable-bytes 30255194112}}
```
