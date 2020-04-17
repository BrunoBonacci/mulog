(ns com.brunobonacci.mulog.publishers.slack
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.publishers.util :as u]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.walk :as w]))


(defn- unix-ms-to-iso8601
  [unix-ms]
  (let [iso-8601-fmt (tf/formatters :date-time)]
    (->> unix-ms
        (tc/from-long)
        (tf/unparse iso-8601-fmt))))


(defn- default-chat-msg-format
  "Timestamp and event name with the log content in a code block"
  [event]
  (let [timestamp (unix-ms-to-iso8601 (:mulog/timestamp event))
        event-name (:mulog/event-name event)]
    (str timestamp " - " event-name
        \newline
        "```"
        \newline
        (ut/pprint-event-str event)
        "```")))


(defn- send-slack-message
  [webhook-url message publish-delay]
  (http/post
   webhook-url
   {:content-type "application/ndjson"
    :accept :json
    :as :json
    :socket-timeout publish-delay
    :connection-timeout publish-delay
    :body (json/generate-string {:text message})}))


;; test
(comment
  (let [f default-chat-msg-format
        message (f {:mulog/timestamp (System/currentTimeMillis)
                    :mulog/event-name :hello
                    :event-details {:venue
                                    {:over
                                     {:the "rainbow"}}
                                    :how-many-people-saw 4
                                    :severity "critical"}})]
    (println message)
    (send-slack-message "https://hooks.slack.com/services/..." message 5000)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| S L A C K |==----                                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype SlackPublisher
    [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:publish-delay config))

  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)
          event-to-chat-msg (comp (:chat-msg-format config)
                                  (:transform config))]
      (if-not (seq items)
        buffer
        (do
          (send-slack-message (:webhook-url config)
                              (event-to-chat-msg (map second items))
                              (:publish-delay config))
          (rb/dequeue buffer last-offset))))))


(def ^:const default-config
  {;; :webhook-url see https://api.slack.com/messaging/webhooks
   ;; :webhook-url "https://hooks.slack.com/services/..." ;; REQUIRED
   :max-items     5000
   :publish-delay 5000 ;; 5s
   ;; function to transform records
   :transform     identity
   ;; function applied to each event to render the content for the chat
   :chat-msg-format default-chat-msg-format})


(defn slack-publisher
  [{:keys [webhook-url] :as config}]
  {:pre [webhook-url]}
  (SlackPublisher.
   (merge default-config config)
   (rb/agent-buffer 20000)))
