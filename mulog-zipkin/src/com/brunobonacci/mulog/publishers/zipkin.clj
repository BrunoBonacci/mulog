(ns com.brunobonacci.mulog.publishers.zipkin
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f :refer [flake]]
            [com.brunobonacci.mulog.common.json :as json]
            [com.brunobonacci.mulog :as u]
            [clj-http.client :as http]
            [clojure.string :as str]))



;;
;; OpenZipkin only accepts a 32 characters long ID for the root trace
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



;;
;; Converts μ/trace events into Zipkin traces and spans
;;
;; According to OpenZipkin api v2.
;; https://zipkin.io/zipkin-api/
;;
(defn- prepare-records
  [config events]
  (->> events
    (filter #(and (:mulog/root-trace %) (:mulog/duration %)))
    (map (fn [{:keys [mulog/trace-id mulog/parent-trace mulog/root-trace
                      mulog/duration mulog/event-name mulog/timestamp
                      app-name] :as e}]
           ;; zipkin IDs are much lower bits than flakes
           {:id        (hexify trace-id 16)
            :traceId   (if (f/flake? root-trace)   (hexify root-trace 32)   root-trace)
            :parentId  (if (f/flake? parent-trace) (hexify parent-trace 16) parent-trace)
            :name      event-name
            :kind      "SERVER"
            ;; timestamp in μs
            :timestamp (* timestamp 1000)
            ;; duration in μs
            :duration  (quot duration 1000)
            ;; use app-name as localEndpoint if available
            :localEndpoint {:serviceName (or app-name "unknown")}
            ;; tags values must be a string (can't be maps)
            ;; although numbers are accepted in zipkin, they're not
            ;; accepted in Jaeger (and maybe other).
            :tags      (ut/map-values ut/edn-str (ut/remove-nils (flag-if-error e)))}))))



(defn- post-records
  [{:keys [url publish-delay] :as config} records]
  (http/post
    url
    {:content-type "application/json"
     :accept :json
     :socket-timeout     publish-delay
     :connection-timeout publish-delay
     :body (json/to-json (prepare-records config records))}))



(comment

  (def url "http://localhost:9411/api/v2/spans")
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
      :app-name         "sample",
      :env              "local1",
      :version          "0.1.0"}])

  (post-records config events)

  (-> events first :mulog/root-trace (f/flake-hex) (#(subs % 0 32)))
  )



(comment

  ;; Annotations


  ;; {
  ;;  "id": "84eff3347d5b4f47",
  ;;  "traceId":  "1600fe61a1aa07e08e98613ba3b063aa",
  ;;  "shared": true,
  ;;  "annotations":
  ;;  [ { "timestamp": 1585616777793000, "value": "test3"} ]
  ;;  }
  ;;

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                        ----==| Z I P K I N |==----                         ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype ZipkinPublisher [config buffer transform]

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
        ;; else send to Zipkin
        (do
          (post-records config (transform (map second items)))
          (rb/dequeue buffer last-offset))))))



(def ^:const DEFAULT-CONFIG
  {;; :url endpoint for Zipkin/Jaeger
   ;; :url  "http://localhost:9411/" ;; REQUIRED
   :max-items     5000
   :publish-delay 5000
   ;; function to transform records
   :transform identity
   })



(defn- normalize-endpoint-url
  [url]
  (cond
    (str/ends-with? url "/api/v2/spans") url
    (str/ends-with? url "/") (str url "api/v2/spans")
    :else (str url "/api/v2/spans")))



(defn zipkin-publisher
  [{:keys [url max-items] :as config}]
  {:pre [url]}
  (ZipkinPublisher.
    (as-> config $
      (merge DEFAULT-CONFIG $)
      (update $ :url normalize-endpoint-url))
    (rb/agent-buffer 20000)
    (or (:transform config) identity)))
