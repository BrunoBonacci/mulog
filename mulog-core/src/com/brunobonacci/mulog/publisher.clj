(ns com.brunobonacci.mulog.publisher
  (:require [com.brunobonacci.mulog.agents :as ag]
            [com.brunobonacci.mulog.buffer :as rb]))



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



(deftype ConsolePublisher
    [config buffer]


  PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item (map second (rb/items buffer))]
      (printf "%s\n" (pr-str item)))
    (flush)
    (rb/clear buffer)))



(defn console-publisher
  [config]
  (ConsolePublisher. config (ag/buffer-agent 10000)))
