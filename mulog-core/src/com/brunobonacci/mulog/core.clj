(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."
      :no-doc true}
    com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.flakes :refer [snowflake]])
  (:import [com.brunobonacci.mulog.publisher PPublisher]))



(def ^:const PUBLISH-INTERVAL 200)



(defn dequeue!
  [buffer offset]
  (swap! buffer rb/dequeue offset))



(defn enqueue!
  [buffer value]
  (swap! buffer rb/enqueue value))



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
  (rb/recurring-task
    PUBLISH-INTERVAL
    (fn []
      (try
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
                (swap! buf rb/dequeue offset)))))
        (catch Exception x
          ;; TODO:
          (.printStackTrace x))))))



(defn start-publisher!
  [buffer config]
  (let [^PPublisher publisher (p/publisher-factory config)
        period (p/publish-delay publisher)
        period (max (or period PUBLISH-INTERVAL) PUBLISH-INTERVAL)

        ;; register publisher in dispatch list
        publisher-id (register-publisher! buffer publisher)
        deregister (fn [] (deregister-publisher! publisher-id))


        publish (fn [] (send-off (p/agent-buffer publisher)
                         (partial p/publish publisher)))
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
  [event-name tid ptid duration ts outcome & pairs]
  `(com.brunobonacci.mulog/log
     ~event-name
     :mulog/trace-id  ~tid
     :mulog/parent-trace ~ptid
     :mulog/duration ~duration
     :mulog/timestamp ~ts
     :mulog/outcome ~outcome
     ~@pairs))



(defmacro log-trace-capture
  "internal utility macro"
  [event-name tid ptid duration ts outcome capture result & pairs]
  (when capture
    `(com.brunobonacci.mulog/with-context
       (on-error {:mulog/capture :error} (~capture ~result))
       (log-trace ~event-name ~tid ~ptid ~duration ~ts ~outcome ~@pairs))))
