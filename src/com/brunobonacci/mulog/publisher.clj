(ns com.brunobonacci.mulog.publisher
  (:require [com.brunobonacci.mulog.agents :as ag]
            [com.brunobonacci.mulog.buffer :as rb]))



(defprotocol PPublisher
  "Publisher protocol"

  (agent-buffer [this]
    "Returns the agent-buffer where items are sent to")

  (publish [this]
    "publishes the items in the buffer")

  )



(deftype ConsolePublisher [config buffer]

  PPublisher
  (agent-buffer [this]
    buffer)


  (publish [this]
    (fn [buf]
      (doseq [item (map second (rb/items buf))]
        (printf "%s\n" (pr-str item)))
      (flush)
      (rb/clear buf)))

  )



(defn console-publisher
  []
  (ConsolePublisher. {} (ag/buffer-agent 10000)))
