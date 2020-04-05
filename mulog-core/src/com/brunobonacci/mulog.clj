(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "
Logging library designed to log data events instead of plain words.

This namespace provides the core functions of **μ/log**.

The purpose of **μ/log** is provide the ability to generate events
directly from your code. The instrumentation process is very simple
and similar to add a traditional log line, but instead of logging
a message which hardly anyone will ever read, your can log an
event, a data point, with all the attributes and properties which
make sense for that particular event and let the machine use it
in a later time.

**μ/log** provides the functions to instrument your code with minimal
impact to performances (few nanoseconds), to buffer events and manage
the overflow, the dispatching of the stored events to downstream
systems where they can be processed, indexed, organized and queried to
provide rich information (quantitative and qualitative) about your
system.

Once you start using event-based metrics you will not want to use
traditional metrics any more.

Additionally, **μ/log** offer the possibility to trace execution with
a micro-tracing function called **μ/trace**.  It provides in app
distributed tracing capabilities with the same simplicity.

The publisher sub-system makes extremely easy to write new publishers
for new downstream system. **μ/log** manages the hard parts like: the
buffering, the retries, the memory buffers and the publisher's state.
It is often required only a dozen lines of code to write a new powerful
custom publisher and use it in your system.

For more information, please visit: https://github.com/BrunoBonacci/mulog
"}
    com.brunobonacci.mulog
  (:require [com.brunobonacci.mulog.core :as core]
            [com.brunobonacci.mulog.buffer :as rb]))



(defonce ^{:doc "The default logger buffers the messages in a ring buffer
             waiting to be dispatched to a destination like a file
             or a centralized logging management system."
       :dynamic true}
  *default-logger*
  ;; The choice of an atom against an agent it is mainly based on
  ;; bechmarks. Items can be added to the buffer with a mean time of
  ;; 285 nanos, against the 1.2μ of the agent. The agent might be
  ;; better in cases in which the atom is heavily contended and many
  ;; retries are required in that case the agent could be better,
  ;; however, the performance difference is big enough that I can
  ;; afford at least 4 retries to make the cost of 1 send to an agent.
  (atom (rb/ring-buffer 1000)))




(defonce ^{:doc "The global logging context is used to add properties
             which are valid for all subsequent log events.  This is
             typically set once at the beginning of the process with
             information like the app-name, version, environment, the
             pid and other similar info."}
  global-context (atom {}))



(def ^{:doc "The local context is local to the current thread,
             therefore all the subsequent call to log withing the
             given context will have the properties added as well. It
             is typically used to add information regarding the
             current processing in the current thread. For example
             who is the user issuing the request and so on."
       :dynamic true}
  *local-context* nil)




(defn log*
  "Event logging function. Given a logger (buffer) an event name and a
  list/map of event's attribute key/values, it enqueues the event in
  the the buffer and returns nil.  Asynchronous process will take care
  to send the content of the buffer to the registered publishers.
  (for more information, see the `log` macro below)
  "
  [logger event-name pairs]
  (when (and logger event-name)
    (core/enqueue!
     logger
     (list @global-context *local-context*
           (list
            :mulog/timestamp (System/currentTimeMillis)
            :mulog/event-name event-name)
           pairs)))
  nil)



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
  (μ/log ::user-logged :user-id \"1234567\" :remote-ip \"1.2.3.4\"
     :auth-method :password-login)
  ```

  Logging an event is extremely cheap (less 300 nanos), so you can log
  plenty without impacting the application performances.
  "
  [event-name & pairs]
  (when (= 1 (rem (count pairs) 2))
    (throw (IllegalArgumentException.
            "You must provide a series of key/value pairs in the form: :key1 value1, :key2 value2, etc.")))
  (let [ns# (str *ns*)]
    `(log* *default-logger* ~event-name (list :mulog/namespace ~ns# ~@pairs))))



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
  as well as how to write your own pulishers please check
  https://github.com/BrunoBonacci/mulog#publishers

  "
  ([config]
   (start-publisher! *default-logger* config))
  ([logger {:keys [type publishers] :as config}]
   (if (= :multi type)
     ;; if multi publisher then start them all
     (->>  publishers
         (map (partial core/start-publisher! logger))
         (doall)
         ((fn [sf] (fn [] (run! #(%) sf)))))
     ;; otherwise start the single publisher
     (core/start-publisher! logger config))))



(defn set-global-context!
  "Adding events which are rich in attributes and dimensions is
  extremely useful, however it is not easy to have all the attributes
  and dimensions at your disposal everywhere in the code. To get
  around this problem **μ/log** supports the use of context.

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
  (reset! global-context context))



(defn update-global-context!
  "If you want to atomically update the global context, use
  `update-global-context!` similarly of how you would use
  `clojure.core/update` function.
  "
  [f & args]
  (apply swap! global-context f args))



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

  ;; {:mulog/timestamp 1572711123826,
  ;;  :mulog/event-name :your-ns/item-processed,
  ;;  :mulog/namespace \"your-ns\",
  ;;  :app-name \"mulog-demo\",
  ;;  :version \"0.1.0\",
  ;;  :env \"local\",
  ;;  :order \"abc123\",
  ;;  :item-id \"sku-123\",
  ;;  :qt 2}
  ```

  The local context can be nested and ti will be inherited by
  all the **μ/log** calls within nested functions as long as they
  are in the same execution thread and which the scope of the block.
  "
  [context & body]
  `(binding [*local-context* (merge *local-context* ~context)]
     ~@body))



(defmacro trace
  "Traces the execution of an operation with the outcome and the time taken in nanoseconds.
   NOTE: API unstable, might change in future releases.
  "
  {:style/indent 1}
  ([event-name pairs expr]
   `(let [ts# (System/currentTimeMillis)
          t0# (System/nanoTime)]
      (try
        (let [r# ~expr]
          (log ~event-name ~@pairs
               :mulog/duration (- (System/nanoTime) t0#)
               :mulog/timestamp ts#
               :mulog/outcome :ok)
          r#)
        (catch Exception x#
          (log ~event-name ~@pairs
               :mulog/duration (- (System/nanoTime) t0#)
               :mulog/timestamp ts#
               :mulog/outcome :error
               :exception x#)
          (throw x#)))))
  ;; allows to provide a function which extracts values
  ;; from the expression result.
  ([event-name pairs result* expr]
   `(let [ts# (System/currentTimeMillis)
          t0# (System/nanoTime)]
      (try
        (let [r# ~expr]
          (with-context (core/on-error {:mulog/result-fn :error} (~result* r#))
            (log ~event-name ~@pairs
                 :mulog/duration (- (System/nanoTime) t0#)
                 :mulog/timestamp ts#
                 :mulog/outcome :ok))
          r#)
        (catch Exception x#
          (log ~event-name ~@pairs
               :mulog/duration (- (System/nanoTime) t0#)
               :mulog/timestamp ts#
               :mulog/outcome :error
               :exception x#)
          (throw x#))))))




(comment

  (def st
    (start-publisher!
     {:type :console}))

  (log :test :t (rand))

  (trace :test-trace
    [:foo 1, :t (rand)]
    (Thread/sleep (rand-int 50)))

  (trace :test-trace
    [:foo 1, :t (rand)]
    {:hello "world"})

  (st)

  )
