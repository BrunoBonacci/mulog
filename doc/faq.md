# F.A.Q.

Here a summary of frequently asked questions.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [F.A.Q.](#faq)
    - [Q: How does ***μ/log*** compare to Dropwizard's Metrics/Prometheus/Riemann?](#q-how-does-μlog-compare-to-dropwizards-metricsprometheusriemann)
    - [Q: Can I use ***μ/log*** as the sole logging library and send my traditional logging to it?](#q-can-i-use-μlog-as-the-sole-logging-library-and-send-my-traditional-logging-to-it)
    - [Q: How do I get ***μ/log*** to send the `:mulog/duration` in milliseconds?](#q-how-do-i-get-μlog-to-send-the-mulogduration-in-milliseconds)
    - [Q: Why do I get `No reader function for tag mulog/flake`?](#q-why-do-i-get-no-reader-function-for-tag-mulogflake)
    - [Q: How do I flush the logs currently in the buffer?](#q-how-do-i-flush-the-logs-currently-in-the-buffer)

<!-- markdown-toc end -->



## Q: How does ***μ/log*** compare to Dropwizard's Metrics/Prometheus/Riemann?

There is some overlapping between Riemann's concepts and ***µ/log***,
in fact both systems are _event-based systems_, although in Riemann the
basic event is a _metric event_ (an event that describes or samples a
metric) .  In ***µ/log***, each event is a free-form, pure, event
which means that like in Riemann you have a bunch of categorical
properties (tags) which can be used to _"slice & dice"_ the events and
group them the way you want, but, in opposition to Riemann,
***µ/log*** doesn't constrain the user to a single numerical field.
If an event needs multiple numerical properties to describe it fully
in ***µ/log*** you can pack this information in a single event, is
is just data, a free form map.

Another difference is that Riemann's core is a streaming and
aggregation engine which allows you to turn raw data into high level
(meaningful) insights. ***µ/log*** (at this stage) is just a client to
produce the raw events.

It is entirely possible to write a ***µ/log*** publisher to send
***µ/log*** events to Riemann in its expected format.

Regarding [Dropwizard's Metrics](https://metrics.dropwizard.io/) the
difference is more fundamental. `Metrics`, like many other similar
libraries, provides some basic instrumentation tool for a metering
system.  Events happen on a remote system and get aggregated at the
source, then, time to time, the metric is sampled and the sample is
sent to a collection system.  Because the events are aggregated at the
source, you automatically lost the fine-grained high-resolution
capabilities to *slice & dice* the metrics at query time unless you
have expressly captured that particular metric separately.

I'm very familiar with this approach, I used it for many years and I
even wrote a Clojure wrapper for it
([TRACKit!](https://github.com/samsara/trackit)). Some tools like
Prometheus try to overcome the lack of categorical dimensions
providing a hybrid approach, but still not as rich as ***µ/log***.

The benefits of switching to an event-based system are enormous
although not very apparent at the start.  Instrumenting your code with
a `metrics` library and produce a rich set of metrics is a very
tedious and time consuming.  Gathering raw events, is more costly, but
much more rich of information compared to `metrics`.

For example, if you instrument only your webservice request handlers
with ***µ/log*** you could ask the following questions:

- how many requests I've received in the last week
- how many requests by day/hour/minute/second
- how many requests by user over time
- how many requests by endpoint overtime
- how many requests were failures (4xx or 5xx)
- of the error requests, how many were for a specific endpoint
- which user issued the failing requests
- what do they have different than the successful requests
- which content-type/content-encoding was used
- what's the latency distribution of the successful request vs the
  failed requests
- what's the latency distribution of version `X` compared to version `Y`
- what is the error-rate of version `X` compared to version `Y`
- what's the distribution of the failures by host/jvm
- what's the status JVM metrics (GC/memory/etc) of failing hosts
  during that time.
- what's the repartition of the latencies between internal processing
  and external connections (db query, caches, etc)

... and much more. **All this from 1 single good *μ/log* instrumentation**.

To achieve the same with a metrics system you will need several dozens
of metrics to be collected and published.

***µ/log*** works incredibly well with
[Elasticsearch](https://www.elastic.co/elasticsearch/) which is an
amazing tool to slice and dice the data the way you need.  One side of
*Elasticsearch* which is not very well known is that *Elasticsearch*
has a very fast and robust aggregation engine as well.

The final point is that traditional systems consider _logs_ different
from _metrics_ and different from _traces_ (*the 3 pillars of
observability*), in reality, they are all different forms of
*events*. For example, the same events that you use for the logs and
you can use to capture metrics can represent traces. In ***µ/log***,
if you add the Zipkin publisher you get the traces collected and
visualised as follow:

![disruption traces](https://github.com/BrunoBonacci/mulog/raw/master/examples/roads-disruptions/doc/images/disruption-trace.png)

all this just come from simple ***µ/log*** instrumentation.


## Q: Can I use ***μ/log*** as the sole logging library and send my traditional logging to it?

Yes, although I wouldn't recommend it.!

Traditional logs are high-volume low value. The presumption is that
there will be a human to consume them.  The reality is that most of
the logs are only read during the development. Any non trivial
application will have so many logs from so many libraries that they
are hard to consume at scale. Many companies built very profitable
building tools to extract information from the logs. The truth is that
_logs at scale are useless to find a problem, they are only useful
when you already know where to look at._

What if you had to design logs not for human consumption, but for
_machine-only_ (or _machine-first_) consumption?  I'm pretty sure that
they won't look anything like the traditional logs.  Instead they will
look more and more like raw data.

For this reason I believe it is more productive and useful to
instrument the system with real _machine-fist_ events instead of
spending money and time to work on old-fashioned human logs.

However, if you still want to send your old logs via ***μ/log***,
you can look at [slf4j-mulog](https://gitlab.com/nonseldiha/slf4j-mulog)
which is a SLF4j backend for ***μ/log***.

The upside of using `slf4j-mulog` is that you can pre-process your logs
as data before sending them so you won't need to configure Logstash and
you can send logs directly to Elasticsearch.

**NOTE: `slf4j-mulog` is an open-source library developed by Peter Nagy (@xificurC)
outside of *μ/log* distribution.**


## Q: How do I get ***μ/log*** to send the `:mulog/duration` in milliseconds?

***μ/log*** will send the `:mulog/duration` in nanoseconds because it
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


## Q: Why do I get `No reader function for tag mulog/flake`?

Flakes are unique `196-bits` unique IDs, in Clojure, they are
represented as [custom tagged
literals](https://clojure.org/reference/reader#tagged_literals), for
example this is a valid EDN flake:

`#mulog/flake "4XQ_3iGMT9DVRa5cYN4iyLGu58SFQcm9"`

EDN is an **Extensible** data format and it allows for custom reader
tags.  Custom reader tags are handled by the reader, here and excerpt
from the Clojure doc:

> Reader tags without namespace qualifiers are reserved for
> Clojure. Default reader tags are defined in default-data-readers but
> may be overridden in `data_readers.clj` or by rebinding
> `*data-readers*`. If no data reader is found for a tag, the function
> bound in `*default-data-reader-fn*` will be invoked with the tag and
> value to produce a value. If `*default-data-reader-fn*` is `nil` (the
> default), a `RuntimeException` will be thrown.

So depending on whether the `data_readers.clj` has been loaded of not
you will get different error messages:

For example if you don't have ***μ/log*** in your project:

``` clojure
;; no data_readers.clj - possibly missing dependency
;; add [com.brunobonacci/mulog "x.x.x"] to project

user=> (read-string "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\"")
Execution error at user/eval1563 (form-init10114461616665553314.clj:1).
No reader function for tag mulog/flake
```

Here the JAR is in the classpath, but the namespace isn't loaded.

``` clojure
;; Dependency not loaded, just require the namespace: com.brunobonacci.mulog.flakes
;;
user=> (read-string "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\"")
Execution error (IllegalStateException) at user/eval1558 (form-init10186056973176272264.clj:1).
Attempting to call unbound fn: #'com.brunobonacci.mulog.flakes/read-method

user=> (require 'com.brunobonacci.mulog.flakes)
nil
user=> (read-string "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\"")

#mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR"
```

However is not recommended to use the `clojure.core` reader for
untrusted code or data, it is recommended to use the `clojure.edn`
namespace with will read the data without attempting to evaluate the
forms and symbols.

**SOLUTION**: So to read ***μ/log*** events read as follow:
``` clojure
(require '[com.brunobonacci.mulog.flakes :as f]
         '[clojure.edn :as edn])

(edn/read-string
 {:readers {'mulog/flake #'com.brunobonacci.mulog.flakes/read-method}}
 "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\"")

#mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR"
```

**ALTERNATIVE SOLUTION:** If you don't care about reading the tagged values and
you just want to do some unrelated processing and pass it on, then you can always read
the EDN raw tag.

``` clojure
;; read it as tagged literal
(binding [*default-data-reader-fn* tagged-literal]
  (read-string "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\""))

#mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR"


;; same as above but when using `clojure.edn`
(edn/read-string {:default tagged-literal }
  "#mulog/flake \"4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR\"")

#mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR"
```

The difference is that in the first solution you obtain an actual `flake`
while with the second solution you just get at `tagged-literal`

``` clojure
;; with first solution
(type #mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR")
com.brunobonacci.mulog.core.Flake

;; with second solution
(type #mulog/flake "4XQ_3fpCt2-dxDHzqGNjJOub2qZGBmhR")
clojure.lang.TaggedLiteral
```


## Q: How do I flush the logs currently in the buffer?

*When you stop a publisher it automatically attempt to flush the buffer.*

When you start a publisher it will return a function with no arguments
which when invoked it will stop the given publisher:

for example

``` clojure
;; start publisher
(def pub (μ/start-publisher! {:type :console}))

;; stop publisher and flush the buffer.
(pub)
```

When you call the /stopping function/, ***μ/log*** will call one last
time the `publish` function and then the `close` function if your
publisher is `java.io.Closeable` (see [source code](https://github.com/BrunoBonacci/mulog/blob/53c531866401f29652c098a3a538133518c187c9/mulog-core/src/com/brunobonacci/mulog/core.clj#L210-L224)).

Therefore to flush the events in the publisher inbox you only need to
call the stop function.

The only caveat is that if you log something right before closing the
publisher you might have the situation that the event hasn't been
dispatched to the publisher yet (see [internals](https://cljdoc.org/d/com.brunobonacci/mulog/0.5.0/doc/%CE%BC-log-internals))

``` clojure
;; start publisher
(def pub (μ/start-publisher! {:type :console}))

;; THIS EVENT MIGHT NOT MAKE IT to the publisher in time
(μ/log ::some-event)
;; stop publisher and flush the buffer.
(pub)
```

If you have such a scenario, I would suggest adding a small wait
before stopping to allow the dispatcher to deliver the events to the
publisher.

``` clojure
;; start publisher
(def pub (μ/start-publisher! {:type :console}))

(μ/log ::some-event)

;; stop publisher
(Thread/sleep 250) ;; Wait for dispatcher
(pub)
```

This small wait should suffice to get all the events into their
destination systems given that the `publish` function succeeds and
completes before the application termination.
