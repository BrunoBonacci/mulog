(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."
      :no-doc true}
    com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.publisher :as p])
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
  ([buffer config]
   (start-publisher! buffer (p/publisher-factory config) (:type config)))
  ([buffer ^PPublisher publisher publisher-name]
   (let [period (p/publish-delay publisher)
         period (max (or period PUBLISH-INTERVAL) PUBLISH-INTERVAL)

         ;; register publisher in dispatch list
         _ (register-publisher! buffer publisher-name publisher)
         deregister (fn [] (deregister-publisher!
                           buffer publisher-name publisher))

         ;; register periodic call publish
         stop (rb/recurring-task
               period
               (fn []
                 (send-off (p/agent-buffer publisher)
                           (partial p/publish publisher))))]
     (fn []
       (deregister)
       (stop)))))



(defmacro on-error
  [default & body]
  `(try
     ~@body
     (catch Exception _#
       ~default)))
