(ns com.brunobonacci.mulog.publishers.open-telemetry
  (:require
   [clj-http.client :as http]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.brunobonacci.mulog.publisher]
   [com.brunobonacci.mulog.buffer :as rb]
   [com.brunobonacci.mulog.common.json :as json]
   [com.brunobonacci.mulog.flakes :as f]
   [com.brunobonacci.mulog.utils :as ut]))



(def mulog-version
  (delay
    (try
      (some-> (io/resource "mulog.version") slurp str/trim)
      (catch Exception _
        "0.0.0-SNAPSHOT"))))



;; OpenTelemetry Protocol (OTLP),
;; https://github.com/open-telemetry/opentelemetry-proto
;;
;; Trace examples:
;; https://github.com/open-telemetry/opentelemetry-proto/blob/v1.4.0/examples/trace.json
;;
;; Specs:
;; https://opentelemetry.io/docs/specs/otlp/#otlphttp

;;
;; OpenTelemetry only accepts a 32 characters long ID for the root trace
;; in hexadecimal format while it accepts 16 characters for a span ID
;; (in hexadecimal format).
;;
(defn- hexify
  "Returns an hexadecimal representation of a flake.
  If a size of 32 is provided it will return the most significant bits.
  If a size of 16 is provided it will return the least signification bits.
  Other sizes will have no effect."
  ([f sz]
   (when f
     (cond
       (= sz 32) (subs (f/flake-hex f) 0 32)
       (= sz 16) (subs (f/flake-hex f) 32 48)
       :else     (f/flake-hex f))))
  ([f]
   (when f
     (f/flake-hex f))))



(defn flag-if-error
  "If the trace contains an error the special tag `:error` will be added."
  [{:keys [mulog/outcome exception error] :as event}]
  (if (and (or (= outcome :error) exception) (not error))
    (assoc event
      :error (if (instance? java.lang.Exception exception)
               (.getMessage ^java.lang.Exception exception)
               (str "Error: " exception)))
    event))



;; tags values must converted to the following structure
;; depending on the type
;; https://github.com/open-telemetry/opentelemetry-proto/blob/main/opentelemetry/proto/common/v1/common.proto#L28-L40
;;
(defn- convert-key-tag
  [v]
  (cond
    (string? v)  v
    (keyword? v) (if (namespace v) (str (namespace v) "/" (name v)) (name v))
    :else
    {:stringValue (ut/edn-str v)}))



(defn- convert-tag-value
  [v]
  (cond
    (nil? v)     {:stringValue "null"}
    (string? v)  {:stringValue v}
    (boolean? v) {:boolValue v}
    (keyword? v) {:stringValue (if (namespace v) (str (namespace v) "/" (name v)) (name v))}
    (f/flake? v) {:stringValue (str v)}
    (integer? v) {:intValue v}
    (double? v)  {:doubleValue v}
    (decimal? v) {:doubleValue (double v)}
    (ratio? v)   {:doubleValue (double v)}
    (or (sequential? v) (set? v))  {:arrayValue {:values (mapv convert-tag-value v)}}
    (map? v)     {:kvlistValue {:values (mapv (fn [[k v]] {:key (convert-key-tag k) :value (convert-tag-value v)}) v)}}
    :else
    {:stringValue (ut/edn-str v)}))



(defn- convert-tags
  [event]
  (->> event
    ut/remove-nils
    (mapv (fn [[k v]] {:key (convert-key-tag k) :value (convert-tag-value v)}))))



;; (convert-tags (first events)),
;; (ut/remove-nils (flag-if-error (first events))),




;;
;; Converts μ/trace events into OpenTelemetry traces and spans
;;
;;
(defn- prepare-records
  [config events]
  (->> events
    (filter #(and (:mulog/root-trace %) (:mulog/duration %)))
    (mapv (fn [{:keys [mulog/trace-id mulog/parent-trace mulog/root-trace
                      mulog/duration mulog/event-name mulog/timestamp
                      app-name] :as event}]
            (let [timestampNano (* timestamp (long 1e6))
                  event         (flag-if-error event)]
              ;; OpenTelemtry IDs have a much lower bits than flakes
              (merge
                (when parent-trace
                  {:parentSpanId  (if (f/flake? parent-trace) (hexify parent-trace 16) parent-trace)})
                {:spanId        (hexify trace-id 16)
                 :traceId       (if (f/flake? root-trace)   (hexify root-trace 32)   root-trace)
                 :name          (convert-key-tag event-name)
                 :kind          1
                 ;; timestamp in ns
                 :startTimeUnixNano timestampNano
                 :endTimeUnixNano   (+ timestampNano duration)
                 ;; status
                 :status (if (:error event) {:code 2 :message (str (:error event))} {:code 1})
                 ;; use app-name as localEndpoint if available
                 ;;              :localEndpoint {:serviceName (or app-name "unknown")}
                 :attributes  (convert-tags event)}))))
    ((fn [spans]
       {:resourceSpans
        [{:resource
          {:attributes
           [{:key "service.name" :value {:stringValue (or #_app-name "unknown")}}]}
          :scopeSpans
          [{:scope {:name "mulog" :version @mulog-version}
            :spans spans}]}]}))))



(defn- post-records
  [{:keys [url publish-delay http-opts] :as config} records]
  (http/post
    url
    (merge
      http-opts
      {:content-type       "application/json"
       :accept             :json
       :socket-timeout     publish-delay
       :connection-timeout publish-delay
       :body               (json/to-json (prepare-records config records))})))



(comment

  (def url "http://localhost:4318/v1/traces")
  (def publish-delay 5000)
  (def config {:url url :publish-delay publish-delay})

  (def f1 (f/flake))
  (def events
    [{:mulog/event-name :mulog/sample-event
      :mulog/timestamp  (System/currentTimeMillis)
      :mulog/duration   242414196,
      :mulog/namespace  "com.brunobonacci.mulog",
      :mulog/outcome    :ok,
      :mulog/root-trace f1
      :mulog/trace-id   f1
      :http-status      202
      :factor1          3.5
      :factor2          2/5
      :test?            true
      :missing          nil
      :simplearray      ["one" "two" "three"]
      :array            [1 "two" true {:four 5} nil "last"]
      :obj              {:foo "bar" :baz nil}
      :set              #{"blue" "green" 3}
      ;; :exception        (RuntimeException. "Something bad happened")
      :app-name         "sample",
      :env              "local1",
      :version          "0.1.0"}])

  (prepare-records nil events)

  (post-records config events)

  (-> events first :mulog/root-trace (f/flake-hex) (#(subs % 0 32)))
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                ----==| O P E N   T E L E M E T R Y |==----                 ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(deftype OpenTelemetryPublisher [config buffer transform]

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
        ;; else send to OpenTelemetry
        (do
          (when-let [records (not-empty (transform (map second items)))]
            (post-records config records))
          (rb/dequeue buffer last-offset))))))



(def ^:const DEFAULT-CONFIG
  {;; OpenTelemetry Collector endpoint for OTLP HTTP/JSON (REQUIRED)
   ;; :url  "http://localhost:4318/" ;; REQUIRED
   :max-items     5000
   :publish-delay 5000
   ;; function to transform records
   :transform identity

   ;; extra http options
   :http-opts {}
   })



(defn- normalize-endpoint-url
  [url]
  (cond
    (str/ends-with? url "/v1/traces") url
    (str/ends-with? url "/") (str url "v1/traces")
    :else (str url "/v1/traces")))



(defn open-telemetry-publisher
  [{:keys [url max-items] :as config}]
  {:pre [url]}
  (OpenTelemetryPublisher.
    (as-> config $
      (merge DEFAULT-CONFIG $)
      (update $ :url normalize-endpoint-url))
    (rb/agent-buffer 20000)
    (or (:transform config) identity)))