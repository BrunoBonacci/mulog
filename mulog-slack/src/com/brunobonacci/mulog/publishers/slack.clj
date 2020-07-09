(ns com.brunobonacci.mulog.publishers.slack
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.common.json :as json]
            [clj-http.client :as http]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.string :as str]))



(defn- unix-ms-to-iso8601
  [unix-ms]
  (let [iso-8601-fmt (tf/formatters :date-time)]
    (->> unix-ms
      (tc/from-long)
      (tf/unparse iso-8601-fmt))))



(defn- default-render-message
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
    {:content-type "application/json"
     :accept :json
     :socket-timeout publish-delay
     :connection-timeout publish-delay
     :body (json/to-json {:text message})}))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| S L A C K |==----                          ;;
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
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)
          transform (:transform config)
          ;; items are pairs [offset <item>]
          transformed-events (transform (map second items))
          render-message (:render-message config)
          rendered-messages (map render-message transformed-events)]
      (if-not (seq items)
        buffer
        (do
          (send-slack-message (:webhook-url config)
            (str/join "\n" rendered-messages)
            (:publish-delay config))
          (rb/dequeue buffer last-offset))))))



(def ^:const default-config
  {;; Should look something like "https://hooks.slack.com/services/..."
   ;; See https://api.slack.com/messaging/webhooks
   ;;   :webhook-url (REQUIRED)
   ;; Function applied to the list of records retrieved from the queue
   ;;   :transform (REQUIRED)
   :max-items     20 ;; By default, only send 20 events per slack message
   :publish-delay 3000 ;; 3s
   ;; Function applied to each event for rendering the slack message to be sent
   :render-message default-render-message})



(defn slack-publisher
  [{:keys [webhook-url transform] :as config}]
  {:pre [webhook-url transform]}
  (SlackPublisher.
    (merge default-config config)
    (rb/agent-buffer 100)))
