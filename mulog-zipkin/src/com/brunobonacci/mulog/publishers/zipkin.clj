(ns com.brunobonacci.mulog.publishers.zipkin
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [cheshire.generate :as gen]
            [clojure.string :as str]
            [clj-time.format :as tf]
            [clj-time.coerce :as tc]
            [clojure.walk :as w])
  (:import com.brunobonacci.mulog.core.Flake))

;;
;; Add Exception encoder to JSON generator
;;
(gen/add-encoder java.lang.Throwable
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (ut/exception-stacktrace x))))

(gen/add-encoder com.brunobonacci.mulog.core.Flake
                 (fn [x ^com.fasterxml.jackson.core.JsonGenerator json]
                   (gen/write-string json ^String (str x))))



(comment

  (require '[com.brunobonacci.mulog.flakes :refer [flake]])

  (defn sample-traces
    []
    (let [t1 (flake)
          t2 (flake)
          t3 (flake)
          t4 (flake)
          tm0 (System/currentTimeMillis)]
      [{:service "user-lookup-cache",
        :mulog/duration 3541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mutrace/trace t3
        :mutrace/parent-trace t2
        :mutrace/root-trace t1
        :mulog/timestamp (+ tm0 142)
        :mulog/event-name :user/cache-store}

       {:service "user-lookup-cache",
        :mulog/duration 69541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mutrace/trace t4
        :mutrace/parent-trace t2
        :mutrace/root-trace t1
        :mulog/timestamp (+ tm0 42)
        :mulog/event-name :user/db-lookup}

       {:service "user-lookup",
        :mulog/duration 150541288,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mutrace/trace t2
        :mutrace/parent-trace t1
        :mutrace/root-trace t1
        :mulog/timestamp (+ tm0 12)
        :mulog/event-name :user/lookup-user}

       {:mulog/timestamp 1585500762833,
        :mulog/event-name
        :user/looup-user,
        :mulog/namespace "user",
        :service "user-lookup",
        :mulog/duration 154128691,
        :mulog/outcome :ok}

       {:mulog/timestamp 1585500555734,
        :mulog/event-name :user/hello,
        :mulog/namespace "user",
        :to "World!"}

       {:service "user-verification",
        :mulog/duration 160905819,
        :mulog/namespace "user",
        :mulog/outcome :ok,
        :mutrace/trace t1
        :mutrace/parent-trace nil,
        :mutrace/root-trace t1
        :mulog/timestamp tm0
        :user "jonny",
        :mulog/event-name :user/remote-call}]))

  (sample-traces)


  )


(def date-time-formatter
  "the ISO8601 date format with milliseconds"
  (tf/formatters :date-time))



(defn format-date-from-long
  [timestamp]
  (->> timestamp
     (tc/from-long)
     (tf/unparse date-time-formatter)))


;; TODO: handle records which can't be serialized.
(defn- to-json
  [m]
  (json/generate-string m {:date-format "yyyy-MM-dd'T'HH:mm:ss.SSS000Z"}))


(defn- post-records
  [{:keys [url publish-delay] :as config} records]
  (http/post
   url
   {:content-type "application/json"
    :accept :json
    :as :json
    :socket-timeout publish-delay
    :connection-timeout publish-delay
    :body records}))



(defn hexify [^Flake s]
  (when s
    (format "%x" (new java.math.BigInteger (.getBytes s)))))


(defn f16 [^String s]
  (when s (subs s 0 16)))

(defn f32 [^String s]
  (when s (subs s 0 32)))

(comment

  (def url "http://localhost:9411/api/v2/spans")
  (def publish-delay 5000)

  (def events (sample-traces))



  (->> events
     (filter :mutrace/trace)
     (map (fn [{:keys [mutrace/trace mutrace/parent-trace mutrace/root-trace
                      mulog/duration mulog/event-name mulog/timestamp] :as e}]
            {:id       (f16 (hexify trace))
             :traceId  (f32 (hexify root-trace))
             :parentId (f16 (hexify parent-trace))
             :name event-name
             :kind "SERVER"
             :timestamp (* timestamp 1000)
             :duration (quot duration 1000)
             :localEndpoint {:serviceName "test-app"}
             :tags (ut/remove-nils e)}))
     (to-json)
     (def records))


  (post-records {:url url :publish-delay publish-delay}  records)

  (println records)

  )

(comment
  ;; {
  ;;  "id": "352bff9a74ca9ad2",
  ;;  "traceId": "5af7183fb1d4cf5f",
  ;;  "parentId": "6b221d5bc9e6496c",
  ;;  "name": "get /api",
  ;;  "timestamp": 1556604172355737,
  ;;  "duration": 1431,
  ;;  "kind": "SERVER",
  ;;  "localEndpoint": {
  ;;                    "serviceName": "backend",
  ;;                    "ipv4": "192.168.99.1",
  ;;                    "port": 3306
  ;;                    },
  ;;  "remoteEndpoint": {
  ;;                     "ipv4": "172.19.0.2",
  ;;                     "port": 58648
  ;;                     },
  ;;  "tags": {
  ;;           "http.method": "GET",
  ;;           "http.path": "/api"
  ;;           }
  ;;  }




  ;; {
  ;;  "id": "84eff3347d5b4f47",
  ;;  "traceId":  "1600fe61a1aa07e08e98613ba3b063aa",
  ;;  "shared": true,
  ;;  "annotations":
  ;;  [ { "timestamp": 1585616777793000, "value": "test3"} ]
  ;;  }
  ;;

  )
