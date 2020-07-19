(ns com.brunobonacci.mulog.publishers.cloudwatch
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.common.json :as json]
            [cognitect.aws.client.api :as aws]))



(defn- has-invalid-token?
  [rs]
  (-> rs
    (:__type)
    (= "InvalidSequenceTokenException")))



(defn- has-anomaly?
  [rs]
  (contains? rs :cognitect.anomalies/category))



(defn- create-cloudwatch-client
  [params]
  (aws/client params))



(defn- create-log-stream
  [client group-name stream-name]
  (aws/invoke client {:op :CreateLogStream
                      :request {:logGroupName group-name
                                :logStreamName stream-name}}))



(defn- publish!
  [cloudwatch-client group-name stream-name records next-token]
  (let [rq {:logGroupName  group-name
            :logStreamName stream-name
            :logEvents     records}
        token @next-token
        rs  (aws/invoke cloudwatch-client {:op  :PutLogEvents
                                           :request (if (nil? token)
                                                      rq
                                                      (merge rq token))})]

    (if (has-anomaly? rs)
      (if (has-invalid-token? rs)
        (swap! next-token assoc :sequenceToken (:expectedSequenceToken rs))
        (throw
          (ex-info
            (str "μ/log cloudwatch publisher publish failure, group '"
              group-name "'" " stream '"
              stream-name "' reason '" (:message rs) "'")
            {:rs rs})))
      (swap! next-token assoc :sequenceToken (:nextSequenceToken rs)))))



(defn- put-log-events
  [cw-client stream-name {:keys [group-name] :as config} records  next-token]
  (let [request  (->> records
                   (map (juxt #(get % :mulog/timestamp) json/to-json))
                   (map (fn [[k v]] {:timestamp k :message v})))]
    (publish! cw-client group-name stream-name request  next-token)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                                ;;
;;              ----==| C L O U D W A T C H    L O G S |==----                    ;;
;;                                                                                ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype CloudwatchPublisher
    [config buffer transform cw-client stream-name next-token]

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
          (put-log-events cw-client stream-name config (transform (map second items)) next-token)
          (rb/dequeue buffer last-offset))))))



(def ^:const DEFAULT-CONFIG
  {;; name of the cloudwatch log group where to put the data (REQUIRED)
   ;;:group-name              "mulog"
   :max-items                5000
   :publish-delay            1000
   ;; function to transform records
   :transform                identity
   :cloudwatch-client-config {:api :logs}})



(defn cloudwatch-publisher
  [{:keys [group-name] :as config}]
  {:pre [group-name]}
  (let [cfg               (->> config
                            (merge DEFAULT-CONFIG))
        cloudwatch-client (create-cloudwatch-client (:cloudwatch-client-config cfg))
        token             (atom nil)
        stream-name       (ut/puid)
        rs                (create-log-stream cloudwatch-client group-name stream-name)]
    (if (has-anomaly? rs)
      (throw
        (ex-info
          (format (str "μ/log cloudwatch publisher initialization failure,"
                  " group: '%s', stream: '%s', reason: %s")
            (str group-name)
            (str stream-name)
            (:message rs))
          {:rs rs}))
      (CloudwatchPublisher.
        cfg
        (rb/agent-buffer 10000)
        (or (:transform cfg) identity)
        cloudwatch-client
        stream-name
        token))))
