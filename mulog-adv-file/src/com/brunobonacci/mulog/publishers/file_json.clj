(ns com.brunobonacci.mulog.publishers.file-json
  (:require [com.brunobonacci.mulog.publisher]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.common.json :as json]
            [clojure.java.io :as io]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| J S O N   F I L E |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype JsonFilePublisher [config ^java.io.Writer filewriter buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item (transform (map second (rb/items buffer)))]
      (.write filewriter ^String (str (json/to-json item) \newline)))
    (.flush filewriter)
    (rb/clear buffer)))



(defn json-file-publisher
  [{:keys [filename transform] :as config}]
  {:pre [filename]}
  (when (or (string? filename) (instance? java.io.File filename))
    (io/make-parents filename))
  (JsonFilePublisher.
    config
    (io/writer filename :append true)
    (rb/agent-buffer 10000)
    (or transform identity)))
