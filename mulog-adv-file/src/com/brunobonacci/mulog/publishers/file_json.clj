(ns com.brunobonacci.mulog.publishers.file-json
  (:require [com.brunobonacci.mulog.publisher]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.common.json :as json]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| J S O N   F I L E |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype JsonFilePublisher [config buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; FIXME:
    ))



(defn json-file-publisher
  [{:keys [transform] :as config}]
  (JsonFilePublisher. config (rb/agent-buffer 10000) (or transform identity)))
