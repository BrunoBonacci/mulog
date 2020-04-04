(ns com.brunobonacci.disruptions.main
  (:require [com.brunobonacci.mulog :as μ]
            [com.brunobonacci.mulog.levels :as lvl]
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
    [ ;; send events to the stdout
     {:type :console
      :level ::lvl/verbose}
     ;; send events to a file
     {:type :simple-file
      :level ::lvl/debug
      :filename "/tmp/mulog/events.log"}
     ;; send events to ELS
     {:type :elasticsearch
      :level ::lvl/info
      :url  "http://localhost:9200/"}
     ;; send events to kafka
     {:type :kafka
      :level ::lvl/info
      :kafka {:bootstrap.servers "192.168.200.200:9092,127.0.0.1:9092"}}]}})



(defn- init-events!
  [{:keys [mulog] :as config}]

  ;; set global context
  (μ/set-global-context!
   {:mulog/level ::lvl/info
    :app-name "roads-disruptions"
    :version "0.1.0"
    :env "local"})

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
