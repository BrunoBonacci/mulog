(ns com.brunobonacci.mulog.publishers.cloudwatch
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [cognitect.aws.client.api :as aws]
            [cheshire.core :as json]
            [cheshire.generate :as gen]))


(defn- has-failures?
  [rs]
  (not (= {} rs)))


(defn- create-cloudwatch-client
  [params]
  (aws/client params))


(defn- publish!
  [cloudwatch-client group-name stream-name records]
  (let [create-rs (aws/invoke cloudwatch-client {:op :CreateLogStream
                                                 :request {:logGroupName group-name
                                                           :logStreamName stream-name}})]
    (if (has-failures? create-rs)
      (throw
        (ex-info
          (str "μ/log cloudwatch publisher failure, group '" group-name "'" " stream '"  stream-name "'")
          {:rs create-rs}))
      (aws/invoke cloudwatch-client {:op      :PutLogEvents
                                     :request {:logGroupName  group-name
                                               :logStreamName stream-name
                                               :logEvents     records}
                                     }))))


;;
;; Add Flake encoder to JSON generator
;;
(gen/add-encoder com.brunobonacci.mulog.core.Flake
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (str x))))



(defn- put-log-events
  [cw-client {:keys [group-name key-field format] :as config} records]
  (let [fmt* (if (= :json format) json/generate-string ut/edn-str)
        request  (->> records
                         (map (juxt #(str (get % key-field)) fmt*))
                         (map (fn [[k v]] {:timestamp (Long/parseLong k) :message v})))
        stream-name  (ut/puid)]
    (publish! cw-client group-name stream-name request)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                                ;;
;;              ----==| C L O U D W A T C H    L O G S |==----                    ;;
;;                                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype CloudwatchPublisher
  [config buffer transform cw-client]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:publish-delay config))

  (publish [_ buffer]
    ;; items are pairs [offset <item>]
    (let [items (take (:max-items config) (rb/items buffer))
          last-offset (-> items last first)]
      (if-not (seq items)
        buffer
        ;; else send to cloudwatch
        (do
          (put-log-events cw-client config (transform (map second items)))
          (rb/dequeue buffer last-offset))))))


(def ^:const DEFAULT-CONFIG
  {;; name of the cloudwatch group where to put the data (REQUIRED)
   ;:group-name              "mulog"
   :key-field                :mulog/timestamp
   :max-items                5000
   :publish-delay            1000
   :format                   :json
   ;; function to transform records
   :transform                identity
   :cloudwatch-client-config {:api :logs}})

(defn cloudwatch-publisher
  [{:keys [group-name] :as config}]
  {:pre [group-name]}
  (let [cfg (as-> config $
                  (merge DEFAULT-CONFIG $))]

    (CloudwatchPublisher.
      cfg
      (rb/agent-buffer 10000)
      (or (:transform cfg) identity)
      (create-cloudwatch-client (:cloudwatch-client-config cfg)))))