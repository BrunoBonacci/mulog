(ns com.brunobonacci.mulog.buffer
  (:require [amalloy.ring-buffer :as rb]))



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
     (pop-while #(<= (first %) offset) buffer)))

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
