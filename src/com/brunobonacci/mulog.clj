(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain
    words."}

    com.brunobonacci.mulog
  (:require [com.brunobonacci.mulog.buffer :refer [ring-buffer] :as rb]
            [com.brunobonacci.mulog.agents :as ag]
            [com.brunobonacci.mulog.publisher :as p]))



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
  (atom (ring-buffer 1000)))



(defn- dequeue!
  [buffer offset]
  (swap! buffer rb/dequeue offset))



(defn- enqueue!
  [buffer value]
  (swap! buffer rb/enqueue value))



(defn log*
  [logger event-name values-map]
  (when (and logger event-name)
    (enqueue! logger
              (assoc values-map
                     :mulog/timestamp (System/currentTimeMillis)
                     :mulog/event-name event-name))))



(defn log
  [event-name & pairs]
  (let [pairs (if (and (seq pairs) (not (next pairs)))
                (cons :mulog/data pairs) pairs)]
    (log* *default-logger* event-name (apply hash-map pairs))))



(defonce ^:private publishers
  (atom #{}))



(defn register-publisher!
  [buffer id publisher]
  (swap! publishers conj [buffer id publisher]))



(defonce dispatch-publishers
  (ag/recurring-task
   200
   (fn []
     (try
       (let [pubs @publishers
             pubs (group-by first pubs)]

         (doseq [[buf dests] pubs]
           (doseq [[_ _ pub] dests]
             (let [items (rb/items @buf)
                   offset (-> items last first)]
               ;; send to the agent-buffer
               (send (p/agent-buffer pub)
                     (partial reduce rb/enqueue)
                     (map second items))
               ;; remove items up to the offset
               (swap! buf rb/dequeue offset)))))
       (catch Exception x
         ;; TODO:
         (.printStackTrace x))))))



(comment
  (reset! publishers #{})

  (dispatch-publishers)

  (def term
    (p/console-publisher))

  (register-publisher!
   *default-logger* :console term)

  *default-logger*

  (log :test :t (rand))

  (p/agent-buffer term)

  (def printer
    (ag/recurring-task
     200
     (fn []
       (send-off (p/agent-buffer term)
                 (p/publish term)))))

  (printer)
  )
