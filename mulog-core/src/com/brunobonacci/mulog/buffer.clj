(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc "
Logging library designed to log data events instead of plain words.

This namespace contains the implementation of a ring-buffer and a
wrapper agent used to buffer the events before their are published
to the downstream systems by the publishers.
"}
    com.brunobonacci.mulog.buffer
  (:require [amalloy.ring-buffer :as rb])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor
            TimeUnit ScheduledFuture Future ThreadFactory]))



(defprotocol PRingBuffer
  "RingBuffer protocol"

  (enqueue [this item]
    "Add an item to the Ring Buffer.")

  (dequeue [this offset]
    "removes all the items in the ring buffer up to the and including the given offset")

  (clear [this]
    "removes all the items in the ring buffer")

  (items [this]
    "Returns a sequence of pairs [<offset> <item>]")

  )



(defn- pop-while
  "like drop-while but for amalloy/ring-buffer"
  [pred buffer]
  (if (some-> (peek buffer) pred)
    (recur pred (pop buffer))
    buffer))



(deftype RingBuffer [counter buffer]
  ;; This type uses amalloy/ring-buffer.  Every item added has an
  ;; monotonically increasing offset in the form of pairs in a tuple
  ;; `[<offset> <item>]`.  The offset can be used as high-water-mark
  ;; to dequeue processed items.

  Object
  (toString [this]
    (pr-str buffer))

  clojure.lang.Counted
  (count [this]
    (count buffer))

  PRingBuffer
  (enqueue [this item]
    (let [id (inc counter)]
      (RingBuffer. id (conj buffer [id item]))))

  (dequeue [this offset]
    (RingBuffer.
      counter
      (pop-while #(<= (first %) (or offset 0)) buffer)))

  (clear [this]
    (RingBuffer. counter (empty buffer)))

  (items [this]
    buffer)

  )



(defn ring-buffer
  "Create an empty ring buffer with the specified [capacity]."
  [capacity]
  {:pre [(> capacity 0)]}
  (RingBuffer. 0 (rb/ring-buffer capacity)))



(defn scheduled-thread-pool
  [core-pool-size]
  (ScheduledThreadPoolExecutor.
    ^int core-pool-size
    ^ThreadFactory
    (reify ThreadFactory
      (^Thread newThread [this ^Runnable r]
       (let [t (Thread. r)]
         (.setName   t (str "mu/log-task-" (.getId t)))
         (.setDaemon t true)
         t)))))



(defonce timer-pool
  (scheduled-thread-pool 2))



(defn recurring-task
  [delay-millis task]
  (let [^ScheduledFuture ftask
        (.scheduleAtFixedRate
          ^ScheduledThreadPoolExecutor timer-pool
          (fn [] (try (task) (catch Exception x))) ;; TODO log errors, this shouldn't happen
          delay-millis delay-millis TimeUnit/MILLISECONDS)]
    (fn [] (.cancel ftask true))))



(defn agent-buffer
  [capacity]
  (agent (ring-buffer capacity)
    :error-mode :continue))
