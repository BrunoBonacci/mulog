(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."
      :no-doc true}
 com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :refer [flake snowflake]])
  (:import [com.brunobonacci.mulog.publisher PPublisher]))



(def ^:const PUBLISH-INTERVAL 200)



(defn dequeue!
  [buffer offset]
  (swap! buffer rb/dequeue offset))



(defn enqueue!
  [buffer value]
  (swap! buffer rb/enqueue value))



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
             who is the user issuing the request and so on."}
  local-context (ut/thread-local nil))



(defn log-append
  "Append to given a logger (buffer) an event represented by one or more set
  of key/values pairs. it enqueues the event in the the buffer and returns nil.
  Pairs can be lists of key value pairs (in the form `'(:key1 \"v1\", :key2 2 ,,,)`)
  or maps.
  "
  ([logger pairs1]
   (when logger
     (enqueue! logger (list @global-context @local-context pairs1)))
   nil)
  ([logger pairs1 pairs2]
   (when logger
     (enqueue! logger (list @global-context @local-context pairs1 pairs2)))
   nil)
  ([logger pairs1 pairs2 pairs3]
   (when logger
     (enqueue! logger (list @global-context @local-context pairs1 pairs2 pairs3)))
   nil))



(defn log*
  "Event logging function. Given a logger (buffer) an event name and a
  list/map of event's attribute key/values, it enqueues the event in
  the the buffer and returns nil.  Asynchronous process will take care
  to send the content of the buffer to the registered publishers.
  (for more information, see the `log` macro below)
  "
  [logger event-name pairs]
  (when (and logger event-name)
    (log-append logger
      (list
        :mulog/trace-id  (flake)
        :mulog/timestamp (System/currentTimeMillis)
        :mulog/event-name event-name)
      pairs))
  nil)



;;
;; Stores the registered publishers
;; id -> {buffer, publisher, ?stopper}
;;
(defonce publishers
  (atom {}))



(defn register-publisher!
  [buffer publisher]
  (let [id (snowflake)]
    (swap! publishers assoc id {:buffer buffer :publisher publisher})
    id))



(defn deregister-publisher!
  [id]
  (swap! publishers dissoc id))



(defn registered-publishers
  []
  (->> @publishers
    (map (fn [[id {:keys [publisher]}]] {:id id :publisher publisher}))
    (sort-by :id)))



(defn- merge-pairs
  [& pairs]
  (into {} (mapcat (fn [v] (if (sequential? v) (map vec (partition 2 v)) v)) pairs)))



(defonce dispatch-publishers
  (delay ;; Delay the task initialisation at runtime (GraalVM)
    (rb/recurring-task
      PUBLISH-INTERVAL
      (fn []
        (let [pubs @publishers
              ;;    group-by buffer
              pubs (group-by :buffer (map second pubs))]

          (doseq [[buf dests] pubs]   ;; for every buffer
            (let [items (rb/items @buf)
                  offset (-> items last first)]
              (when (seq items)
                (doseq [{pub :publisher} dests]  ;; and each destination
                  ;; send to the agent-buffer
                  (send (p/agent-buffer pub)
                    (partial reduce rb/enqueue)
                    (->> items
                      (map second)
                      (map (partial apply merge-pairs)))))
                ;; remove items up to the offset
                (swap! buf rb/dequeue offset))))))
      ;; this shouldn't happen,
      (fn [exception]
        (log* *default-logger* :mulog/internal-error
          (list ;; pairs
            :mulog/namespace (str *ns*)
            :mulog/action    :event-dispatch
            :mulog/origin    :mulog/core
            :exception       exception))))))



(defn start-publisher!
  [buffer config]
  ;; force the initialisation on the first start-publisher! call
  (deref dispatch-publishers)
  ;; now register and start the publisher
  (let [^PPublisher publisher (p/publisher-factory config)
        period (p/publish-delay publisher)
        period (max (or period PUBLISH-INTERVAL) PUBLISH-INTERVAL)

        ;; register publisher in dispatch list
        publisher-id (register-publisher! buffer publisher)
        deregister (fn [] (deregister-publisher! publisher-id))

        ;; single attempt to call `publish` on a publisher
        ;; errors are logged via μ/log ;-)
        publish-attempt
        (fn publish-attempt
          [buffer]
          (try
            (p/publish publisher buffer)
            (catch Exception exception
              (log* *default-logger* :mulog/publisher-error
                (list ;; pairs
                  :mulog/namespace (str *ns*)
                  :mulog/action    :publish
                  :mulog/origin    :mulog/core
                  :exception       exception
                  ;; can't log the full `config` as it could contain
                  ;; sensitive info like passwords and service-keys
                  :publisher-type  (:type config)
                  :publisher-id    publisher-id))
              (throw exception))))

        publish (fn [] (send-off (p/agent-buffer publisher) publish-attempt))

        ;; register periodic call publish
        stop (rb/recurring-task period publish)

        ;; prepare a stop function
        stopper
        (fn stop-publisher
          []
          ;; remove publisher from listeners
          (deregister)
          ;; stop recurring calls to publisher
          (stop)
          ;; flush buffer
          (publish)
          ;; close publisher
          (when (instance? java.io.Closeable publisher)
            (send-off (p/agent-buffer publisher)
              (fn [_]
                (.close ^java.io.Closeable publisher))))
          :stopped)]
    ;; register the stop function
    (swap! publishers assoc-in [publisher-id :stopper] stopper)
    ;; return the stop function
    stopper))



(defn stop-publisher!
  [publisher-id]
  ((get-in @publishers [publisher-id :stopper] (constantly :stopped))))



(defmacro on-error
  "internal utility macro"
  [default & body]
  `(try
     ~@body
     (catch Exception _#
       ~default)))



(defmacro log-trace
  "internal utility macro"
  [event-name tid ptid duration ts outcome pairs captures]
  `(when-let [event-name# ~event-name]
     (log-append
       *default-logger*
       (list
         :mulog/event-name   event-name#
         :mulog/trace-id     ~tid
         :mulog/parent-trace ~ptid
         :mulog/duration     ~duration
         :mulog/timestamp    ~ts
         :mulog/outcome      ~outcome
         :mulog/namespace    ~(str *ns*))
       ~pairs
       ~captures)))
