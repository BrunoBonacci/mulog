(ns com.brunobonacci.mulog.publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [clojure.java.io :as io]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;            ----==| P U B L I S H E R   P R O T O C O L |==----             ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defprotocol PPublisher
  "Publisher protocol"

  (agent-buffer [this]
    "Returns the agent-buffer where items are sent to, basically your
    inbox.")

  (publish-delay [this]
    "The number of milliseconds between two calls to `publish` function.
     return `nil` if you don't want mu/log call the `publish` function")

  (publish [this buffer]
    "publishes the items in the buffer and returns the new state of
    the buffer which presumably doesn't contains the messages
    successfully sent.")

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| C O N S O L E |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype ConsolePublisher
    [config buffer]


  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item (map second (rb/items buffer))]
      (printf "%s\n" (pr-str item)))
    (flush)
    (rb/clear buffer)))



(defn console-publisher
  [config]
  (ConsolePublisher. config (rb/agent-buffer 10000)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                   ----==| S I M P L E - F I L E |==----                    ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype SimpleFilePublisher
    [config ^java.io.Writer filewriter buffer]


  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    500)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item (map second (rb/items buffer))]
      (.write filewriter (prn-str item)))
    (.flush filewriter)
    (rb/clear buffer)))



(defn simple-file-publisher
  [{:keys [filename] :as config}]
  {:pre [filename]}
  (let [filename (io/file filename)]
    ;; make parte dirs
    (.mkdirs (.getParentFile filename))
    (SimpleFilePublisher.
     config
     (io/writer filename :append true)
     (rb/agent-buffer 10000))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| F A C T O R Y |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn- load-function-from-name
  ([fqn-fname]
   (if (string? fqn-fname)
     (let [[_ fns ff] (re-find #"([^/]+)/([^/]+)" fqn-fname)]
       (when (not (and fns ff))
         (throw
          (ex-info
           (str "function '" fqn-fname
                "' is invalid format. must be \"namespace/fun-name\".")
           {:fqn-fname fqn-fname})))
       (load-function-from-name fns ff))
     fqn-fname))
  ([fn-ns fn-name]
   (when (not (and fn-ns fn-name))
     (throw (ex-info (str "function '" fn-ns "/" fn-name "' not found.")
                     {:fn-ns fn-ns :fn-name fn-name})))
   ;; requiring the namespace
   (require (symbol fn-ns))
   (let [fn-symbol (resolve (symbol fn-ns fn-name))]
     (when-not fn-symbol
       (throw (ex-info (str "function '" fn-ns "/" fn-name "' not found.")
                       {:fn-ns fn-ns :fn-name fn-name})))
     fn-symbol)))



(defn- loading-error [stage info cause]
  (case stage
    :loading
    (throw
     (ex-info
      (str "Unable to load appropriate publisher."
           " Please ensure you have the following dependency "
           "[com.brunobonacci/mulog-"
           (some-> info :config :type name) " \"x.y.z\"]"
           " in your project.clj")
      info cause))

    :init
    (throw
     (ex-info
      (str "Unable to initialize publisher."
           " Please ensure you have the publisher has a function with 1 argument.")
      info cause))

    :verify
    (throw
     (ex-info "Invalid publisher, not an instance of com.brunobonacci.mulog.publisher.PPublisher"
              info))))



(defn- load-dynamic-publisher
  [publisher-name config]
  (let [;; load publisher factory function
        publisher* (try
                     (load-function-from-name publisher-name)
                     (catch Exception x
                       (loading-error :loading {:config config} x)))
        ;; initialize publisher
        publisher  (try (publisher* config)
                        (catch Exception x
                          (loading-error :init {:config config} x)))]
    ;; verify type
    (when-not (instance? com.brunobonacci.mulog.publisher.PPublisher publisher)
      (loading-error :verify {:config config
                              :name publisher-name
                              :type (type publisher)} nil))
    ;; if all ok return the publisher
    publisher))



(defmulti publisher-factory
  ""
  (fn [cfg] (:type cfg)))



(defmethod publisher-factory :default
  [cfg]
  (throw
   (ex-info "mu/log Invalid or no reporting method selected."
            {:type (:type cfg)
             :config cfg})))



(defmethod publisher-factory :custom
  [{:keys [fqn-function] :as cfg}]
  (load-dynamic-publisher fqn-function cfg))



(defmethod publisher-factory :console
  [config]
  (console-publisher config))



(defmethod publisher-factory :simple-file
  [config]
  (simple-file-publisher config))



(defmethod publisher-factory :elasticsearch
  [config]
  (load-dynamic-publisher
   "com.brunobonacci.mulog.publishers.elasticsearch/elasticsearch-publisher"
   config))



(defmethod publisher-factory :kafka
  [config]
  (load-dynamic-publisher
   "com.brunobonacci.mulog.publishers.kafka/kafka-publisher"
   config))
