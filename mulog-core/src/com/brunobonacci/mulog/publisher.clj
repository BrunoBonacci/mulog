(ns com.brunobonacci.mulog.publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
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
     return `nil` if you don't want μ/log call the `publish` function")

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


(deftype ConsolePublisher [config buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    200)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item  (transform (map second (rb/items buffer)))]
      (if (:pretty? config)
        (printf "%s\n" (ut/pprint-event-str item))
        (printf "%s\n" (ut/edn-str item))))
    (flush)
    (rb/clear buffer)))



(defn console-publisher
  [{:keys [transform pretty?] :as config}]
  (ConsolePublisher. config (rb/agent-buffer 10000) (or transform identity)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                   ----==| S I M P L E - F I L E |==----                    ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype SimpleFilePublisher [config ^java.io.Writer filewriter buffer transform]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    500)


  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (doseq [item (transform (map second (rb/items buffer)))]
      (.write filewriter ^String (str (ut/edn-str item) \newline)))
    (.flush filewriter)
    (rb/clear buffer))


  java.io.Closeable
  (close [_]
    (.flush filewriter)
    (.close filewriter)))



(defn simple-file-publisher
  [{:keys [filename transform] :as config}]
  {:pre [filename]}
  (when (or (string? filename) (instance? java.io.File filename))
    (io/make-parents filename))
  (SimpleFilePublisher.
    config
    (io/writer filename :append true)
    (rb/agent-buffer 10000)
    (or transform identity)))



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
          "[" (some-> info :jar-name) " \"x.y.z\"]"
          " in your project.clj or deps.edn")
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
  [publisher-name jar-name config]
  (let [;; load publisher factory function
        publisher* (try
                     (load-function-from-name publisher-name)
                     (catch Exception x
                       (loading-error :loading {:jar-name jar-name :config config} x)))
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
  "Creates a publisher of the give `:type`."
  (fn [cfg] (:type cfg)))



(defmethod publisher-factory :default
  [cfg]
  (throw
    (ex-info "μ/log Invalid or no reporting method selected."
      {:type (:type cfg)
       :config cfg})))



(defmethod publisher-factory :custom
  [{:keys [fqn-function] :as cfg}]
  (load-dynamic-publisher fqn-function "your-publisher-jar" cfg))



(defmethod publisher-factory :inline
  [{:keys [publisher] :as cfg}]
  ;; verify type
  (when-not (instance? com.brunobonacci.mulog.publisher.PPublisher publisher)
    (loading-error :verify {:config cfg
                            :name :direct
                            :type (type publisher)} nil))
  publisher)



(defmethod publisher-factory :console
  [config]
  (console-publisher config))



(defmethod publisher-factory :console-json
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.console-json/json-console-publisher"
    "com.brunobonacci/mulog-adv-console"
    config))



(defmethod publisher-factory :simple-file
  [config]
  (simple-file-publisher config))



(defmethod publisher-factory :elasticsearch
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.elasticsearch/elasticsearch-publisher"
    "com.brunobonacci/mulog-elasticsearch"
    config))


(defmethod publisher-factory :file-json
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.file-json/json-file-publisher"
    "com.brunobonacci/mulog-adv-file"
    config))


(defmethod publisher-factory :jvm-metrics
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.jvm-metrics/jvm-metrics-publisher"
    "com.brunobonacci/mulog-jvm-metrics"
    config))



(defmethod publisher-factory :filesystem-metrics
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.filesystem-metrics/filesystem-metrics-publisher"
    "com.brunobonacci/mulog-filesystem-metrics"
    config))



(defmethod publisher-factory :kafka
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.kafka/kafka-publisher"
    "com.brunobonacci/mulog-kafka"
    config))



(defmethod publisher-factory :zipkin
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.zipkin/zipkin-publisher"
    "com.brunobonacci/mulog-zipkin"
    config))



(defmethod publisher-factory :kinesis
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.kinesis/kinesis-publisher"
    "com.brunobonacci/mulog-kinesis"
    config))



(defmethod publisher-factory :cloudwatch
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.cloudwatch/cloudwatch-publisher"
    "com.brunobonacci/mulog-cloudwatch"
    config))



(defmethod publisher-factory :slack
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.slack/slack-publisher"
    "com.brunobonacci/mulog-slack"
    config))



(defmethod publisher-factory :prometheus
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.prometheus/prometheus-publisher"
    "com.brunobonacci/mulog-prometheus"
    config))



(defmethod publisher-factory :mbean
  [config]
  (load-dynamic-publisher
    "com.brunobonacci.mulog.publishers.mbean-sampler/mbean-sampler-publisher"
    "com.brunobonacci/mulog-mbean-sampler"
    config))
