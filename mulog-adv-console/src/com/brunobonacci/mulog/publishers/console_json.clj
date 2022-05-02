(ns com.brunobonacci.mulog.publishers.console-json
  (:require [com.brunobonacci.mulog.publisher]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.common.json :as json]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                  ----==| J S O N   C O N S O L E |==----                   ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype JsonConsolePublisher [config buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item  (transform (map second (rb/items buffer)))]
      (printf "%s\n" (json/to-json item (select-keys config [:pretty?]))))
    (flush)
    (rb/clear buffer)))



(defn json-console-publisher
  [{:keys [transform pretty?] :as config}]
  (JsonConsolePublisher. config (rb/agent-buffer 10000) (or transform identity)))
