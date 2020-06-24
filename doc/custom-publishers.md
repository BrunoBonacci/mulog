# How to write custom publishers

This guide explains how to write a publisher for a new target system.
The target system can be anything: from the standard output to a fancy
timeseries database, a SQL or NOSQL database, a streaming platform,
a cloud system or even a machine learning system.

***μ/log*** tries to make as simple as possible to write a *stateful*
publisher. Most of the time is fairly easy to write a publisher which
is stateless and doesn't care about downstream system availability.
However, we know that real systems and real environments are usually
more complex, they have to account for short time glitches, speed and
throughput.

***μ/log*** tries to do the heavy lifting of managing the publishing
buffers in such way that downstream system can have transient failure
and still send the events once they recover. At the same time, we want
to avoid that the failure of a downstream system makes our events to
pile up which could ultimately cause our system to run out of memory.

For this reason all the buffers we use are [ring-buffers](https://en.wikipedia.org/wiki/Circular_buffer)
with a limited capacity.  If the issue with the downstream system
persists in time then older events are automatically dropped out to
make place for fresher events.  Should the downstream system recover
at any point, the publisher will be able to send the full content of
the buffer.

For more information about how the ***μ/log***'s internals work, please
read [μ/log internals](./mulog-internals.md).


``` clojure
(defprotocol PPublisher
  "Publisher protocol"

  (agent-buffer [this]
    "Returns the agent-buffer where items are sent to, basically your
    inbox.")

  (publish-delay [this]
    "The number of milliseconds between two calls to `publish` function.
     return `nil` if you don't want mu/log call the `publish` function")

  (publish [this buffer]
    "publishes the items in the buffer and returns the new state of
    the buffer which presumably doesn't contains the messages
    successfully sent.")

  )

```

Let's dissect it function by function.  The first one is
`agent-buffer`, it returns a Clojure `agent` which is wrapping a
ring-buffer which is where the events will be delivered.  You should
size this according to how events will be pushed to the downstream
system. For example 10000 events is good size to account for a
transient error in the target system without taking too much memory.
We already have an agent wrapping a ring-buffer so pretty much this
will always return `(rb/agent-buffer 10000)`.

Next we have the `publish-deplay`, this is the delay in milliseconds
between two consecutive calls of the `publish` function.  This is
usually between `200` and a few seconds. The smaller it is, the more
frequent the target system will be called.  The larger it is the more
you have to account for buffering space. Strike a good balance between
DDOS'ing the target system and buffering too much.

Finally, `publish` is the actual call. ***μ/log*** will call this
function at regular intervals (`publish-delay`) and pass the content
of the buffer.  The function has to push the events to the target
system and return **the new content of the buffer**.  Calls to this
function will be serialized (no concurrent calls) so it is important
to not take up too much time and set a timeout that is smaller than
the `publish-delay`.


Let's see how to write a simple publisher that writes the events to
the console.  Let's add also some configurable option, for example we
might want to be able to pretty-print (`{:pretty-print true}`) the
events or to simply print them one per line.


``` clojure
(ns my-custom.publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [clojure.pprint :refer [pprint]]))


(deftype MyCustomPublisher
    [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    500)

  (publish [_ buffer]
    ;; check our printer option
    (let [printer (if (:pretty-print config) pprint prn)]
      ;; items are pairs [offset <item>]
      (doseq [item (map second (rb/items buffer))]
        ;; print the item
        (printer item)))
    ;; return the buffer minus the published elements
    (rb/clear buffer)))


(defn my-custom-publisher
  [config]
  (MyCustomPublisher. config (rb/agent-buffer 10000)))
```

That's it! That's all it takes to write a publisher.  Now to use it
you can start it with:

``` clojure
(u/start-publisher!
  {:type :custom
   :fqn-function "my-custom.publisher/my-custom-publisher"
   :pretty-print true})
```

That was simple right? Let's add some complications; instead of
printing to the console we want to write to a file so that we have to
keep the file writer at hand (in our state) and we want also to pace
how many items we print at once.  This might be useful if the target
system can only receive a limited number of items.

Firstly let's make a version of pprint which output to a string.

``` clojure
(defn- pprint-str
    [v]
    (with-out-str
      (pprint v)))
```

Now let's amend `MyCustomPublisher` to accept the filerwriter and push
at most 1000 items.

``` clojure
(deftype MyCustomPublisher
  [config buffer ^java.io.Writer filewriter]


  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    500)


  (publish [_ buffer]
    ;;    check our printer option
    (let [printer (if (:pretty-print config) pprint-str prn-str)
          ;; take at most `:max-items` items
          items (take (:max-items config) (rb/items buffer))
          ;; save the offset of the last items
          last-offset (-> items last first)]
      ;; write the items to the file
      (doseq [item (map second items)]
        ;; print the item
        (.write filewriter (printer item)))
      ;; flush the buffer
      (.flush filewriter)
      ;; return the buffer minus the published elements
      (rb/dequeue buffer last-offset)))


  ;; If you need to release/close resources when the pubslisher
  ;; is stopped you can implement the `java.io.Closeable` and
  ;; it will be called when the publisher is stopped as last call.
  java.io.Closeable
  (close [_]
    (.flush filewriter)
    (.close filewriter)))


(defn my-custom-publisher
  [{:keys [filename] :as config}]
  (let [config (merge {:pretty-print false :max-items 1000} config)]
    (MyCustomPublisher. config (rb/agent-buffer 10000)
                        (io/writer (io/file filename) :append true))))
```

In the above example we can see that we take only a portion of the
buffer content. Importantly we **save the offset of the last item** we
publish so that we can discard all the messages in the buffer up and
including last offset. The rest of the publisher is pretty much the same.


## Error handling

So far we haven't spoke about error handling at all, that's
because there is not much to say. If the `publish` function raises an
exception, nothing to worry about, the publish will be retried after
the `publish-delay` interval passed. So for example, if you are
posting to a remote system and the system in temporarily unavailable,
nothing to worry about, ***μ/log*** will keep retrying.  Should the
target system not be available for a longer period of time, nothing to
worry about in this case as well, because the buffer will keep filling
up until it is full and then start dropping older events ensuring that
the system won't run out of memory.


## Support for user-defined transformations

If you are building a general purpose publisher it is a good idea to
provide the ability to take a general transformation which can be
applied to the events.  This can be very useful for filtering which
events you wish to send to a specific publisher or for performing
simple event transformations.  For example, the transformation could
be used to anonymize some sensitive fields which you might not want to
see in one destination.
Sometimes it is useful to filter noisy events out and get only the
events you are interested into a particular publisher.

All the built-in publishers support custom transformation via the
`:transform` configuration.

If you are implementing a Publisher, consider adding the support as
well.  To add the support is easy, just look for a function associated
to the `:transform` key in your configuration and apply the
transformation to the events you get from the buffer.

For example, in our previous example:

``` clojure
(ns my-custom.publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [clojure.pprint :refer [pprint]]))


(deftype MyCustomPublisher
    [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    500)

  (publish [_ buffer]
    ;; check our printer option
    (let [printer (if (:pretty-print config) pprint prn)
          ;; HERE: retrieve the transformation function
          transform (:transform config)]
      ;; items are pairs [offset <item>], APPLY HERE the transform
      (doseq [item (transform (map second (rb/items buffer)))]
        ;; print the item
        (printer item)))
    ;; return the buffer minus the published elements
    (rb/clear buffer)))


(defn my-custom-publisher
  [config]
  ;; if a `transform` function is not provided, then use identity
  (let [config (update config :transform (fn [f] (or f identity)))]
    (MyCustomPublisher. config (rb/agent-buffer 10000))))
```

Remember the transform is a function which applies to all events, it
can do any sort of operation and it is optional.
