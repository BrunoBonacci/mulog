(ns com.brunobonacci.mulog.publishers.zipkin
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f :refer [flake]]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [cheshire.generate :as gen]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as u]))

;;
;; Add Exception encoder to JSON generator
;;
(gen/add-encoder java.lang.Throwable
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (ut/exception-stacktrace x))))

(gen/add-encoder com.brunobonacci.mulog.core.Flake
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (str x))))



;; TODO: handle records which can't be serialized.
(defn- to-json
  [m]
  (json/generate-string m {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSSX"}))


(comment

    (defn sample-traces
    []
    (let [t1 (flake)
          t2 (flake)
          t3 (flake)
          t4 (flake)
          tm0 (System/currentTimeMillis)]
      [{:app-name "user-lookup-cache",
        :mulog/duration 3541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mulog/trace t3
        :mulog/parent-trace t2
        :mulog/root-trace t1
        :mulog/timestamp (+ tm0 142)
        :mulog/event-name :user/cache-store}

       {:app-name "user-lookup-cache",
        :mulog/duration 69541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mulog/trace t4
        :mulog/parent-trace t2
        :mulog/root-trace t1
        :mulog/timestamp (+ tm0 42)
        :mulog/event-name :user/db-lookup}

       {:app-name "user-lookup",
        :mulog/duration 150541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mulog/trace t2
        :mulog/parent-trace t1
        :mulog/root-trace t1
        :mulog/timestamp (+ tm0 12)
        :mulog/event-name :user/lookup-user}

       {:mulog/timestamp 1585500762833,
        :mulog/event-name
        :user/looup-user,
        :mulog/namespace "user",
        :app-name "user-lookup",
        :mulog/duration 154128691,
        :mulog/outcome :ok}

       {:mulog/timestamp 1585500555734,
        :mulog/event-name :user/hello,
        :mulog/namespace "user",
        :to "World!"}

       {:app-name "user-verification",
        :mulog/duration 160905819,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mulog/trace t1
        :mulog/parent-trace nil,
        :mulog/root-trace t1
        :mulog/timestamp tm0
        :user "jonny",
        :mulog/event-name :user/remote-call}]))

  (sample-traces)


  )

;;
;; OpenZipkin only accepts a 32 characters long ID for the root trace in hexadecimal format
;; while it accepts 16 characters for a span ID (in hexadecimal format)
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



;;
;; Converts μ/trace events into Zipkin traces and spans
;;
;; According to OpenZipkin api v2.
;; https://zipkin.io/zipkin-api/
;;
(defn- prepare-records
  [config events]
  (->> events
     (filter :mulog/trace)
     (map (fn [{:keys [mulog/trace mulog/parent-trace mulog/root-trace
                      mulog/duration mulog/event-name mulog/timestamp
                      app-name] :as e}]
            ;; zipkin IDs are much lower bits than flakes
            {:id        (hexify trace 16)
             :traceId   (hexify root-trace 32)
             :parentId  (hexify parent-trace 16)
             :name      event-name
             :kind      "SERVER"
             ;; timestamp in μs
             :timestamp (* timestamp 1000)
             ;; duration in μs
             :duration  (quot duration 1000)
             ;; use app-name as localEndpoint if available
             :localEndpoint {:serviceName (or app-name "unknown")}
             ;; tags values must be a string (can't be maps)
             :tags      (ut/map-values str (ut/remove-nils e))}))))


(defn- post-records
  [{:keys [url publish-delay] :as config} records]
  (http/post
   url
   {:content-type "application/json"
    :accept :json
    :as     :json
    :socket-timeout     publish-delay
    :connection-timeout publish-delay
    :body (to-json (prepare-records config records))}))




(comment

  (def url "http://localhost:9411/api/v2/spans")
  (def publish-delay 5000)
  (def config {:url url :publish-delay publish-delay})

  (def events (sample-traces))
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


(deftype ZipkinPublisher
    [config buffer transform]


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
  {;; :url endpoint for Elasticsearch
   ;; :url "http://localhost:9200/" ;; REQUIRED
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
