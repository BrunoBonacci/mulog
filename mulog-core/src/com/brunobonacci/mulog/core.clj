(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "Logging library designed to log data events instead of plain words."}
    com.brunobonacci.mulog.core
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.agents :as ag]
            [com.brunobonacci.mulog.publisher :as p]))



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



(defonce dispatch-publishers
  (ag/recurring-task
   200
   (fn []
     (try
       (let [pubs @publishers
             ;;    group-by buffer
             pubs (group-by first pubs)]

         (doseq [[buf dests] pubs]   ;; for every buffer
           (doseq [[_ _ pub] dests]  ;; and each destination
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



(defn start-publisher!
  [buffer config]
  (when-not (= :console (:type config))
    (throw (ex-info "Unknown publisher" config)))
  (let [publisher (p/console-publisher config)
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
