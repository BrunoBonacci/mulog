(ns com.brunobonacci.disruptions.main
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.disruptions.api :as api]
            [cemerick.url :refer [url url-encode]]
            [ring.adapter.jetty :as http])
  (:gen-class))



(def DEFAULT-CONFIG
  {:server {:port 8000 :join? false}

   :endpoints
   {:base  #(str (url "https://api.tfl.gov.uk" %))
    :roads "https://api.tfl.gov.uk/road"
    :disruptions #(str (url "https://api.tfl.gov.uk/road"
                         (url-encode %)
                         "disruption"))}


   :mulog
   {:type :multi
    :publishers
    [;; send events to the stdout
     {:type :console :pretty? true}
     ;; send events to a file
     {:type :simple-file :filename "/tmp/mulog/events.log"}
     ;; send events to ELS
     {:type :elasticsearch :url  "http://localhost:9200/"}
     ;; send events to kafka
     {:type :kafka :kafka {:bootstrap.servers "127.0.0.1:9092"}}
     ;; send events to zipkin
     {:type :zipkin :url  "http://localhost:9411/"}
     ;; send events to prometheus pushgateway
     ;; {:type :prometheus :push-gateway {:endpoint "http://localhost:9091" :job "road-disruptions"}}
     ;; send events to slack
     #_{:type :slack
        :webhook-url "https://hooks.slack.com/services/.../.../..."
        :transform identity}]}})



(defn- init-events!
  [{:keys [mulog] :as config}]

  ;; set global context
  (μ/set-global-context!
    {:app-name "roads-disruptions", :version "0.1.0", :env "local"})

  (μ/start-publisher! mulog))



(defn- init-polling!
  "Initiates background thread to poll TFL api"
  [config]
  (api/poll-disruptions! config))



(defn- init!
  "Initialize system"
  [config]
  (let [_       (init-events! config)
        handler (api/service-api config)
        _       (init-polling! config)
        server  (http/run-jetty handler (:server config))]

    (fn [] (.close ^java.io.Closeable server))))



(defn -main [& args]
  (init! DEFAULT-CONFIG)
  (println "Server started on http://localhost:8000/disruptions")
  (μ/log :disruptions/app-started)
  @(promise))



;;
