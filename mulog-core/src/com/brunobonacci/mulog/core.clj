(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."}
    com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.agents :as ag]
            [com.brunobonacci.mulog.publisher :as p])
  (:import [com.brunobonacci.mulog.publisher PPublisher]))



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



(defn- merge-pairs
  [& pairs]
  (into {} (mapcat (fn [v] (if (sequential? v) (map vec (partition 2 v)) v)) pairs)))



(defonce dispatch-publishers
  (ag/recurring-task
   200
   (fn []
     (try
       (let [pubs @publishers
             ;;    group-by buffer
             pubs (group-by first pubs)]

         (doseq [[buf dests] pubs]   ;; for every buffer
           (let [items (rb/items @buf)
                 offset (-> items last first)]
             (doseq [[_ _ pub] dests]  ;; and each destination
               ;; send to the agent-buffer
               (send (p/agent-buffer pub)
                     (partial reduce rb/enqueue)
                     (->> items
                        (map second)
                        (map (partial apply merge-pairs)))))
               ;; remove items up to the offset
               (swap! buf rb/dequeue offset))))
       (catch Exception x
         ;; TODO:
         (.printStackTrace x))))))



(defn start-publisher!
  [buffer config]
  (let [^PPublisher publisher (p/publisher-factory config)
        period (p/publish-delay publisher)
        _ (register-publisher! buffer (:type config) publisher)
        stop (if (and period (> period 0))
               (ag/recurring-task
                (p/publish-delay publisher)
                (fn []
                  (send-off (p/agent-buffer publisher)
                            (partial p/publish publisher)))))]
    (fn []
      ;;TODO: deregister
      (stop))))



(defmacro on-error
  [default & body]
  `(try
     ~@body
     (catch Exception _#
       ~default)))
