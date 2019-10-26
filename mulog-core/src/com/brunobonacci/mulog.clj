(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."}
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
  [event-name & pairs]
  (when (= 1 (rem (count pairs) 2))
    (throw (IllegalArgumentException.
            "You must provide a series of key/value pairs in the form: :key1 value1, :key2 value2, etc.")))
  (let [ns# (str *ns*)]
    `(log* *default-logger* ~event-name (list :mulog/namespace ~ns# ~@pairs))))



(defn start-publisher!
  ([config]
   (start-publisher! *default-logger* config))
  ([buffer config]
   (core/start-publisher! buffer config)))



(defn set-global-context!
  [context]
  (reset! global-context context))



(defn update-global-context!
  [f & args]
  (apply swap! global-context f args))



(defmacro with-context
  [context & body]
  `(binding [*local-context* (merge *local-context* ~context)]
     ~@body))


(comment

  (def st
    (start-publisher!
     {:type :console}))

  (log :test :t (rand))

  (st)

  )
