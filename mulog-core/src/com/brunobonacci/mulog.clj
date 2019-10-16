(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."}
    com.brunobonacci.mulog
  (:require [com.brunobonacci.mulog.core :as core]
            [com.brunobonacci.mulog.buffer :as rb]))



(def ^{:doc "The default logger buffers the messages in a ring buffer
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



(defn log*
  [logger event-name values-map]
  (when (and logger event-name)
    (core/enqueue! logger
                   (assoc values-map
                          :mulog/timestamp (System/currentTimeMillis)
                          :mulog/event-name event-name))))



(defn log
  [event-name & pairs]
  (let [pairs (if (and (seq pairs) (not (next pairs)))
                (cons :mulog/data pairs) pairs)]
    (log* *default-logger* event-name (apply hash-map pairs))))



(defn register-publisher!
  ([id publisher]
   (register-publisher! *default-logger* id publisher))
  ([buffer id publisher]
   (swap! core/publishers conj [buffer id publisher])))



(defn start-publisher!
  ([config]
   (start-publisher! *default-logger* config))
  ([buffer config]
   (core/start-publisher! buffer config)))



(comment

  (def st
    (start-publisher!
     {:type :console}))

  (log :test :t (rand))

  (st)

  )
