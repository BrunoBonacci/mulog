(ns com.brunobonacci.disruptions.tfl-api
  (:require [com.brunobonacci.mulog :as μ]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [safely.core :refer [safely]]
            [cemerick.url :refer [url url-encode]]))



(defn roads
  "Returns the list of London main road-corridors"
  [config]
  (->>
      (safely
          (->> (μ/trace :disruptions/remote-request
               {:pairs   [:request-type :get-all-roads]
                :capture (fn [{:keys [status]}] {:http-status status})}

               (http/get (get-in config [:endpoints :roads])
                 {:as "UTF-8"
                  :accept :json
                  :socket-timeout 3000
                  :connection-timeout 3000}))
            :body
            (json/parse-string))
        :on-error
        :max-retries :forever
        ;;:circuit-breaker :list-roads ;;TODO: re-enable circuit-breaker
        :message "Problem retrieving the list of roads"
        :log-stacktrace false)

    ;; selecting relevant fields
    (map #(as-> % $
            (select-keys $  ["id" "url" "displayName" "statusSeverity"
                             "statusSeverityDescription"])
            (update $ "url" (get-in config [:endpoints :base]))))))



(defn disruptions
  "Retrieve the list of disruptions for a given road"
  [config road-id]
  (->>
      (safely
          (->> (μ/trace :disruptions/remote-request
               {:pairs [:road-id road-id :request-type :disruptions-by-road]
                :capture (fn [{:keys [status]}] {:http-status status})}

               ;; http-rest request to TFL api
               (http/get ((get-in config [:endpoints :disruptions]) road-id)
                 {:as "UTF-8"
                  :accept :json
                  :socket-timeout 3000
                  :connection-timeout 3000}))
            :body
            (json/parse-string))
        :on-error
        :max-retries 5
        :default []
        ;;:circuit-breaker :disruptions ;;TODO: re-enable circuit-breaker
        :message "Problem retrieving the disruptions"
        :log-stacktrace false)

    ;; selecting relevant fields
    (map #(as-> % $
            (select-keys $  ["id" "url" "location" "corridorIds"
                             "currentUpdate" "category" "severity"
                             "subCategory"])
            (update $ "url" (get-in config [:endpoints :base]))))))



(defn all-disruptions
  "Returns all the active disruptions on all the main roads around London"
  [config]
  (μ/trace :disruptions/retrieve-disruptions
    []
    (->> config
      roads
      (map #(get % "id"))
      (mapcat (partial disruptions config))
      (map (juxt #(get % "id") identity))
      (into {})
      (map second))))
