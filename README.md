# μ/log
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)

![mulog](./doc/mulog.png)

***μ/log*** is a micro-logging library that logs events and data, not words!


> **μ**, **mu** *(Pronunciation: /mjuː/)* <br/>
> The twelfth letter of the
> Greek alphabet (Μ, μ), often used as a prefix for *mirco-* which is
> 10<sup>-6</sup> in the SI (System of Unis). Lowercase letter "`u`" is often
> substituted for "`μ`" when the Greek character is not typographically
> available.<p/>
> *(source: [https://en.wikipedia.org/wiki/Mu_(letter)](https://en.wikipedia.org/wiki/Mu_(letter)))*
> <br/>


## Features

Here some features and key design decisions that make ***μ/log*** special:

  * Effortlessly, logs events as data points.
  * No need to construct strings that then need to be deconstructed later.
  * Fast, extremely fast, under **300 nanoseconds** per log entry
  * Memory bound; no unbounded use of memory
  * All the processing and rendering happens asynchronously.
  * Ability to add contextual logging.
  * Adding publishers won't affect logging performances
  * Extremely easy to create *stateful* publishers for new systems
  * Wide range of publishers available
  * *Event logs are useful, but not as important as process flow
    (therefore preferable to drop events rather than crashing the
    process)*
  * Because is cheap to log events, you can freely log plenty.

Available publishers:

  * Simple console publisher (stdout)
  * Simple file publisher
  * ElasticSearch
  * Apache Kafka

## Motivation

Existing logging libraries are based on a design from the 80s and
early 90s.  Most of the systems at the time where developed in
standalone servers where logging messages to console or file was the
predominant thing to do. Logging was mostly providing debbugging
information and system behavioural introspection.

Most of modern systems are distributed in virtualized machines that
live in the cloud. These machines could disappear any time. In this
context logging on the local file system isn't useful as logs are
easily lost if virtual machines are destroyed. Therefore it is common
practice to use log collectors and centralized log processors. The ELK
stack it has been predominant in this space for years, but there are a
multitude of other commercial and open-source products.

Most of these systems have to deal with non structured data
represented as formatted strings in files. The process of extracting
information out of these strings is very tedious, error prone, and
definitely not fun. But the question is: **why did we encode these as
strings in the first place?** This is just because existing log
frameworks, which have been redesigned in various decades follow the
same structure as when systems lived on the same single server for
decades.

I believe we need the break free of these anachronistic design and use
event loggers, *not message loggers*, which are designed for dynamic
distributed systems living in cloud and using centralized log
aggregators. *So here is **μ/log** designed for this very purpose.*


## Usage

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog "0.1.2"]

;; deps.edn format
{:deps { com.brunobonacci/mulog "0.1.2" }}
```

Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)


Then require the namespace:

``` clojure
(ns your-ns
  (:require [com.brunobonacci.mulog :as μ]))

;; or for the more ASCII traditionalists
(ns your-ns
  (:require [com.brunobonacci.mulog :as u]))
```

Check the [online documentation](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT)

The instrument your code with the log you deem useful. Then general structure is

``` clojure
(μ/log event-name, key1 value1, key2 value2, ... keyN valueN)
```

For example:
``` clojure
;; good to use namespaced keywords for the event-name
(μ/log ::hello :to "New World!")
```

However you will NOT be able to see any events until you add a
publisher which it will take your events and send to a distributed
logger of your local console (if you are developing).

You can add as many key-value pairs as you deem useful to express the event in your system
``` clojure
(μ/start-publisher! {:type :console})
```

At this point you should be able to see the previous event in your
REPL terminal and it will look as follow:

``` clojure
{:mulog/timestamp 1572707670555, :mulog/event-name :your-ns/hello, :mulog/namespace "your-ns", :to "New World!"}
```

Here some example of events you might want to log.

``` clojure
(μ/log ::system-started :version "0.1.0" :init-time 32)

(μ/log ::user-logged :user-id "1234567" :remote-ip "1.2.3.4" :auth-method :password-login)

(μ/log ::http-request :path "/orders", :method :post, :remote-ip "1.2.3.4", :http-status 201, :request-time 129)

(μ/log ::invalid-request :exception x, :user-id "123456789", :items-requested 47)

(μ/log ::position-updated :poi "1234567" :location {:lat 51.4978128, :lng -0.1767122 )
```

All above are examples of events you might want to track, collect and
aggregate on it in a specialized timeseries database.

### Use of context

Adding events which are rich in attributes and dimensions is extremely
useful, however it is not easy to have all the attributes and
dimensions at your disposal everywhere in the code. To get around
this problem **μ/log** supports the use of context.

There are two levels of context, a global level and a local one.

The global context allows you to define properties and values which
will be added to all the events logged afterwards.

For example:

``` clojure
(μ/log ::system-started :init-time 32)
;; {:mulog/timestamp 1572709206048, :mulog/event-name :your-ns/system-started, :mulog/namespace "your-ns", :init-time 32}

;; set global context
(μ/set-global-context! {:app-name "mulog-demo", :version "0.1.0", :env "local"})

(μ/log ::system-started :init-time 32)
;; {:mulog/timestamp 1572709332340,
;;  :mulog/event-name :your-ns/system-started,
;;  :mulog/namespace "your-ns",
;;  :app-name "mulog-demo",
;;  :version "0.1.0",
;;  :env "local",
;;  :init-time 32}
```

Typically, you will set the global context once in your main function
at the starting of your application with properties which are valid
for all events emitted by the process. User `set-global-context!` to
specify a given value, or `update-global-context!` with a update
function to change some of the values. Examples of properties you should
consider adding in the global context are `app-name`, `version`, `environment`,
`process-id`, `host-ip`, `os-type`, `jvm-version` etc etc


The second type of context is the (thread) local context. If can be
used to inject information about the current processing and all the
events withing the scope of the context will inherit the properties
and their values.

For example the following line will contain all the properties of the *global context*,
all the properties of the *local context* and all *inline properties*.

``` clojure
(μ/with-context {:order "abc123"}
  (μ/log ::item-processed :item-id "sku-123" :qt 2))

;; {:mulog/timestamp 1572711123826,
;;  :mulog/event-name :your-ns/item-processed,
;;  :mulog/namespace "your-ns",
;;  :app-name "mulog-demo",
;;  :version "0.1.0",
;;  :env "local",
;;  :order "abc123",
;;  :item-id "sku-123",
;;  :qt 2}
```

The local context can be nested:

``` clojure
(μ/with-context {:transaction-id "tx-098765"}
  (μ/with-context {:order "abc123"}
    (μ/log ::item-processed :item-id "sku-123" :qt 2)))

;; {:mulog/timestamp 1572711123826,
;;  :mulog/event-name :your-ns/item-processed,
;;  :mulog/namespace "your-ns",
;;  :app-name "mulog-demo",
;;  :version "0.1.0",
;;  :env "local",
;;  :transaction-id "tx-098765",
;;  :order "abc123",
;;  :item-id "sku-123",
;;  :qt 2}
```

Local context works across function boundaries:

``` clojure
(defn process-item [sku quantity]
    ;; ... do something
    (u/log ::item-processed :item-id "sku-123" :qt quantity)
    ;; ... do something
    )

(μ/with-context {:order "abc123"}
    (process-item "sku-123" 2))

;; {:mulog/timestamp 1572711818791,
;;  :mulog/event-name :your-ns/item-processed,
;;  :mulog/namespace "your-ns",
;;  :app-name "mulog-demo",
;;  :version "0.1.0",
;;  :env "local",
;;  :order "abc123",
;;  :item-id "sku-123",
;;  :qt 2}

```

## Best practices

Here some best practices to follow while logging events:

  * Use namespaced keywords or qualified strings for the `event-name`
  * Log values not opaque objects, objects will be turned into strings
    which diminishes their value
  * Do now log mutable values, since rendering is done asynchronously
    you could be logging a different state. If values are mutable
    capture the current state (deref) and log it.
  * Avoid logging deeply nested maps, they are hard to query.
  * Log timestamps with milliseconds precision.

## Publishers

### Simple console publisher

It outputs the events into the standard output in EDN format, mostly intended for local development.

``` clojure
(μ/start-publisher! {:type :console})
```

### Simple file publisher

It sends the output of each log into a file in EDN format.

``` clojure
(μ/start-publisher! {:type :simple-file :filename "/tmp/mulog/events.log"})
```

### ElasticSearch publisher

The events must be serializeable in JSON format ([Cheshire](https://github.com/dakrone/cheshire))

``` clojure
(μ/start-publisher!
  {:type :elasticsearch

   ;; els endpoint
   :url  "http://localhost:9200/"

   ;; maximum number of events in a single post
   ;; :max-items     5000

   ;; how often it will send the events to ELS (in millis)
   ;; :publish-delay 5000

   ;; the name of the index where events will be sent
   ;; :index-pattern "'mulog-'yyyy.MM.dd"

   ;; Whether or not to change the attribute names
   ;; to facilitate queries and avoid type clashing
   ;; :name-mangling true
   })
```

Read more on [Elasticsearch name mangling](./doc/els-name-mangling.md) here.

### Apache Kafka publisher

The events must be serializeable in JSON format ([Cheshire](https://github.com/dakrone/cheshire))

``` clojure
(μ/start-publisher!
  {:type :kafka

   ;; kafka configuration
   :kafka {;; the comma-separated list of brokers
           ;; :bootstrap.servers "localhost:9092"
           ;; you can add more kafka connection properties here
           }

   ;; the name of the kafka topic where events will be sent
   ;; :topic "mulog"

   ;; maximum number of events in a single batch
   ;; :max-items     5000

   ;; how often it will send the events to ELS (in millis)
   ;; :publish-delay 5000

   ;; the format of the events to send into the topic
   ;; can be one of: :json, :edn (default :json)
   ;; :format    :json

   ;; The name of the field which it will be used as partition key
   ;; the :puid is the process unique identifier which can be injected
   ;; as global context
   ;; :key-field :puid
   })
```

Supported versions: `6.7+`, `7.x`

### Custom publishers

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
[How to write custom publishers](./doc/custom-publishers.md)

## More docs

  * Read about [μ/log internals](./doc/mulog-internals.md)
  * [How to write custom publishers](./doc/custom-publishers.md)
  * Read more on [Elasticsearch name mangling](./doc/els-name-mangling.md)

## License

Copyright © 2019 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
