(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "
Logging library designed to log data events instead of plain words.

This namespace provides the core functions of ***μ/log***.

The purpose of ***μ/log*** is provide the ability to generate events
directly from your code. The instrumentation process is very simple
and similar to add a traditional log line, but instead of logging
a message which hardly anyone will ever read, your can log an
event, a data point, with all the attributes and properties which
make sense for that particular event and let the machine use it
in a later time.

***μ/log*** provides the functions to instrument your code with minimal
impact to performances (few nanoseconds), to buffer events and manage
the overflow, the dispatching of the stored events to downstream
systems where they can be processed, indexed, organized and queried to
provide rich information (quantitative and qualitative) about your
system.

Once you start using event-based metrics you will not want to use
traditional metrics any more.

Additionally, ***μ/log*** offer the possibility to trace execution with
a micro-tracing function called **μ/trace**.  It provides in app
distributed tracing capabilities with the same simplicity.

The publisher sub-system makes extremely easy to write new publishers
for new downstream system. ***μ/log*** manages the hard parts like: the
buffering, the retries, the memory buffers and the publisher's state.
It is often required only a dozen lines of code to write a new powerful
custom publisher and use it in your system.

For more information, please visit: https://github.com/BrunoBonacci/mulog
"}
 com.brunobonacci.mulog
  (:require [com.brunobonacci.mulog.core :as core]
            [com.brunobonacci.mulog.utils
             :refer [defalias fast-map-merge thread-local-binding]]
            [com.brunobonacci.mulog.flakes :refer [flake]]))



;; create var alias in local namespace
(defalias log* core/log*)



(defmacro log
  "Event logging function. Given an event name and an (inline) sequence
  of event's attribute key/values, it enqueues the event in the the
  buffer and returns nil.  Asynchronous process will take care to send
  the content of the buffer to the registered publishers.

  Use this function similarly to how you would use a message logging
  library to log important events, but rather than logging words, add
  data points. To log a new event this is the format:

  ``` clojure
  (μ/log event-name, key1 value1, key2 value2, ... keyN valueN)
  ```

  Where:
  - `event-name` should be a keyword or string (preferably namespaced)
  - `key1 value1` is a key/value pair which provides additional information
  about the event. This can be used later to further refine your query
  of aggregate on.

  For example should you want to log the event of a your login, you
  could add:

  ``` clojure
  (μ/log ::user-logged, :user-id \"1234567\", :remote-ip \"1.2.3.4\",
     :auth-method :password-login)
  ```

  Logging an event is extremely cheap (less 300 nanos), so you can log
  plenty without impacting the application performances.
  "
  [event-name & pairs]
  `(core/log* core/*default-logger* ~event-name (list :mulog/namespace ~(str *ns*) ~@pairs)))



(defn start-publisher!
  "It loads and starts a event's publisher. Publisher, asynchronously,
  send events to downstream systems for processing, indexing,
  aggregation, alerting and querying.
  There are a number of built-in publishers you can use. Each publisher
  has its own configuration properties. For example to print the events
  to the standard output you can use the `:console` publisher

  The `start-publisher!` function returns a zero-argument function
  which can be used to stop the publisher.

  ``` clojure
  (def stop (μ/start-publisher! {:type :console}))
  (μ/log ::hi)
  ;; prints something like:
  ;; {:mulog/timestamp 1572709206048, :mulog/event-name :user/hi, :mulog/namespace \"user\"}

  ;; stop the publisher
  (stop)
  ```

  The console publisher is really only intended for development
  purposes.  There are other publishers which are more suitable for
  modern cloud-based, distributed systems.

  Another built-in publisher is the `:simple-file` publisher which
  just outputs events to a file in EDN format.

  ``` clojure
  (μ/start-publisher! {:type :simple-file :filename \"/tmp/mulog/events.log\"})
  ```

  You can also have multiple publishers defined in one place using the `:multi`
  publisher configuration:

  ``` clojure
  (μ/start-publisher!
   {:type :multi
    :publishers
    [{:type :console}
     {:type :simple-file :filename \"/tmp/disk1/mulog/events1.log\"}
     {:type :simple-file :filename \"/tmp/disk2/mulog/events2.log\"}]})
  ```

  which it will start all the defined publishers all at once.

  For more information about available publishers and their configuration
  as well as how to write your own publishers please check
  https://github.com/BrunoBonacci/mulog#publishers

  "
  ([config]
   (start-publisher! core/*default-logger* config))
  ([logger {:keys [type publishers] :as config}]
   (if (= :multi type)
     ;; if multi publisher then start them all
     (->>  publishers
       (map (partial core/start-publisher! logger))
       (doall)
       ((fn [sf] (fn [] (run! #(%) sf)))))
     ;; otherwise start the single publisher
     (core/start-publisher! logger config))))



(defn global-context
  "Return the current value of the `global-context`.
  The global logging context is used to add properties which are valid
  for all subsequent log events.  This is typically set once at the
  beginning of the process with information like the app-name,
  version, environment, the pid and other similar info."
  []
  @core/global-context)



(defn set-global-context!
  "Adding events which are rich in attributes and dimensions is
  extremely useful, however it is not easy to have all the attributes
  and dimensions at your disposal everywhere in the code. To get
  around this problem ***μ/log*** supports the use of context.

  There are two levels of context, a global level and a local one.

  The global context allows you to define properties and values which
  will be added to all the events logged afterwards.

  Typically, you will set the global context once in your main
  function at the starting of your application with properties which
  are valid for all events emitted by the process. Use
  `set-global-context!` to specify a given value, or
  `update-global-context!` with a update function to change some of
  the values. Examples of properties you should consider adding in the
  global context are `app-name`, `version`, `environment`,
  `process-id`, `host-ip`, `os-type`, `jvm-version` etc etc

  You might find some useful function in the `com.brunobonacci.mulog.utils`
  namespace.

  "
  [context]
  (reset! core/global-context context))



(defn update-global-context!
  "If you want to atomically update the global context, use
  `update-global-context!` similarly of how you would use
  `clojure.core/update` function.
  "
  [f & args]
  (apply swap! core/global-context f args))



(defn local-context
  "Returns the current `local-context`.
  The local context is local to the current thread, therefore all the
  subsequent call to log withing the given context will have the
  properties added as well. It is typically used to add information
  regarding the current processing in the current thread. For example
  who is the user issuing the request and so on."
  []
  @core/local-context)



(defmacro with-context
  "
  The (thread) local context it can be used to inject information
  about the current processing and all the events withing the scope of
  the context will inherit the properties and their values.

  For example the following line will contain all the properties of the
  *global context*, all the properties of the *local context* and all
  *inline properties*.

  ``` clojure
  (μ/with-context {:order \"abc123\"}
    (μ/log ::item-processed :item-id \"sku-123\" :qt 2))

  ;; {:mulog/trace-id #mulog/flake \"4VIKxhMPB2eS0uc1EV9M9a5G7MYn3TMs\",
  ;;  :mulog/event-name :your-ns/item-processed,
  ;;  :mulog/timestamp 1572711123826,
  ;;  :mulog/namespace \"your-ns\",
  ;;  :app-name \"mulog-demo\",
  ;;  :version \"0.1.0\",
  ;;  :env \"local\",
  ;;  :order \"abc123\",
  ;;  :item-id \"sku-123\",
  ;;  :qt 2}
  ```

  The local context can be nested and ti will be inherited by
  all the ***μ/log*** calls within nested functions as long as they
  are in the same execution thread and which the scope of the block.
  "
  {:style/indent 1}
  [context-map & body]
  `(thread-local-binding [core/local-context (fast-map-merge @core/local-context ~context-map)]
     ~@body))



(defmacro trace
  "Traces the execution of an operation with the outcome and the time
  taken in nanoseconds.

  ### Track duration and outcome (errors)

  ***μ/trace*** will generate a trace object which can be understood by
  distributed tracing systems.

  It computes the duration in nanoseconds of the current trace/span
  and it links via the context to the parent trace and root traces.

  It tracks the `:outcome` of the evaluation of the `body`.  If the
  evaluation it throws an exception `:outcome` will be `:error`
  otherwise it will be `:ok`

  The trace information will be tracked across function calls as long as
  the execution is in the same thread. If the execution spans more threads
  or more processes the context must be passed forward.

  Example of usage:

  ``` Clojure
  (u/trace ::availability
    [:product-id product-id, :order order-id, :user user-id]
    (product-availability product-id))
  ```

  Will produce an event as follow:

  ``` Clojure
  {:mulog/trace-id #mulog/flake \"4VIKxhMPB2eS0uc1EV9M9a5G7MYn3TMs\",
   :mulog/event-name :your-ns/availability,
   :mulog/timestamp 1586804894278,
   :mulog/duration 253303600,
   :mulog/namespace \"your-ns\",
   :mulog/outcome :ok,
   :mulog/root-trace #mulog/flake \"4VILF82cx_mFKlbKN-PUTezsRdsn8XOY\",
   :mulog/parent-trace #mulog/flake \"4VILL47ifjeHTaaG3kAWtZoELvk9AGY9\",
   :order \"34896-34556\",
   :product-id \"2345-23-545\",
   :user \"709-6567567\"}
  ```

  Note the `:mulog/duration` and `:mulog/outcome` reporting
  respectively the duration of the execution of `product-availablity`
  in **nanoseconds** as well as the outcome (`:ok` or `:error`). If an
  exception is raised within the body an additional field is added
  `:exception` with the exception raised.

  The `:pairs` present in the vector are added in the event, but they
  are not propagated to nested traces, use `with-context` for that.

  Finally, `:mulog/trace-id`, `:mulog/parent-trace` and
  `:mulog/root-trace` identify respectively this trace, the outer
  trace wrapping this trace if present otherwise `nil` and the
  `:mulog/root-trace` is the outer-most trace with not parents.  Keep
  in mind that *parent-trace* and *root-trace* might come from another
  system and they are propagated by the context.

  ### Capture evaluation result

  Sometimes it is useful to add to the trace pairs which come from the
  result of the body's evaluation. For example to capture the http
  response status or other valuable metrics from the response.
  ***μ/trace*** offers the possibility to pass a function to capture
  such info from the evaluation result.
  To achieve this, instead of passing a simple vector of pairs
  you need to provide a map which contains a `:capture` function
  in addition to the `:pairs`.

  The `capture` function is a function which takes one argument,
  *the result* of the evaluation and returns a map of key-value pairs
  which need to be added to the trace. The `capture` function will only
  run when the `:mulog/outcome :ok`

  Example of usage:

  ``` Clojure
  (u/trace ::availability
    {:pairs [:product-id product-id, :order order-id, :user user-id]
     :capture (fn [r] {:http-status (:status r)
                       :etag (get-in r [:headers \"etag\"])})}
    (product-availability product-id))
  ```

  Will produce an event as follow:

  ``` Clojure
  {:mulog/trace-id #mulog/flake \"4VIKxhMPB2eS0uc1EV9M9a5G7MYn3TMs\",
   :mulog/event-name :your-ns/availability,
   :mulog/timestamp 1586804894278,
   :mulog/duration 253303600,
   :mulog/namespace \"your-ns\",
   :mulog/outcome :ok,
   :mulog/root-trace #mulog/flake \"4VILF82cx_mFKlbKN-PUTezsRdsn8XOY\",
   :mulog/parent-trace #mulog/flake \"4VILL47ifjeHTaaG3kAWtZoELvk9AGY9\",
   :order \"34896-34556\",
   :product-id \"2345-23-545\",
   :user \"709-6567567\",
   :http-status 200,
   :etag \"1dfb-2686-4cba2686fb8b1\"}
  ```

  Note that in addition to the pairs like in the previous example
  this one contains `:http-status` and `:etag` which where extracted
  from the http response of `product-availability` evaluation.

  Should the execution of the `capture` function fail for any reason
  the pair will be added to this trace with `:mulog/capture :error`
  to signal the execution error.

  "
  {:style/indent 1
   :arglists '([event-name [k1 v1, k2 v2, ... :as pairs] & body]
               [event-name {:keys [pairs capture]} & body])}
  [event-name details & body]
  `(let [details# ~details
         pairs#   (if (map? details#) (:pairs details#)   details#)
         capture# (if (map? details#) (:capture details#) nil)
         ;; :mulog/trace-id and :mulog/timestamp are created in here
         ;; because the log function is called after the evaluation of body
         ;; is completed, and the timestamp wouldn't be correct
         ptid# (get @core/local-context :mulog/parent-trace)
         tid#  (flake)
         ts#   (System/currentTimeMillis)
         ;; start timer to track body execution
         t0#   (System/nanoTime)]
     ;; setting up the tracing re
     (with-context {:mulog/root-trace   (or (get @core/local-context :mulog/root-trace) tid#)
                    :mulog/parent-trace tid#}
       (try
         (let [r# (do ~@body)]
           (core/log-trace ~event-name tid# ptid# (- (System/nanoTime) t0#) ts# :ok pairs#
             ;; if there is something to capture form the evaluation result
             ;; then use the capture function
             (core/on-error {:mulog/capture :error} (when capture# (capture# r#))))
           ;; return the body result
           r#)
         ;; If and exception occur, then log the error.
         (catch Exception x#
           (core/log-trace ~event-name tid# ptid# (- (System/nanoTime) t0#) ts#
             :error (list :exception x#) pairs#)
           (throw x#))))))
