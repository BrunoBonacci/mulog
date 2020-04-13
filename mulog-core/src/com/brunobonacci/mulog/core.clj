(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."
      :no-doc true}
    com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.core :as core])
  (:import [com.brunobonacci.mulog.publisher PPublisher]))



(def ^:const PUBLISH-INTERVAL 200)



(defn dequeue!
  [buffer offset]
  (swap! buffer rb/dequeue offset))



(defn enqueue!
  [buffer value]
  (swap! buffer rb/enqueue value))



(defonce publishers
  (atom #{}))



(defn register-publisher!
  [buffer id publisher]
  (swap! publishers conj [buffer id publisher]))



(defn deregister-publisher!
  ([id]
   (swap! publishers
          (fn [publishers]
            (->> publishers
               (remove #(= id (second %)))
               (into #{})))))

  ([buffer id]
   (swap! publishers
          (fn [publishers]
            (->> publishers
               (remove #(and (= id (second %))
                           (= buffer (first %))))
               (into #{})))))

  ([buffer id publisher]
   (swap! publishers
          (fn [publishers]
            (->> publishers
               (remove #(= % [buffer id publisher]))
               (into #{}))))))



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
             pubs (group-by first pubs)]

         (doseq [[buf dests] pubs]   ;; for every buffer
           (let [items (rb/items @buf)
                 offset (-> items last first)]
             (when (seq items)
               (doseq [[_ _ pub] dests]  ;; and each destination
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
  ([buffer config]
   (start-publisher! buffer (p/publisher-factory config) (:type config)))
  ([buffer ^PPublisher publisher publisher-name]
   (let [period (p/publish-delay publisher)
         period (max (or period PUBLISH-INTERVAL) PUBLISH-INTERVAL)

         ;; register publisher in dispatch list
         _ (register-publisher! buffer publisher-name publisher)
         deregister (fn [] (deregister-publisher!
                           buffer publisher-name publisher))


         publish (fn [] (send-off (p/agent-buffer publisher)
                                 (partial p/publish publisher)))
         ;; register periodic call publish
         stop (rb/recurring-task period publish)]
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
       :stopped))))



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
  `(com.brunobonacci.mulog/log ~event-name
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
    `(com.brunobonacci.mulog/with-context (core/on-error {:mulog/capture :error} (~capture ~result))
       (core/log-trace ~event-name ~tid ~ptid ~duration ~ts ~outcome ~@pairs))))
