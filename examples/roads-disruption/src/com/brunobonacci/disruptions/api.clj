(ns com.brunobonacci.disruptions.api
  (:require [com.brunobonacci.disruptions.tfl-api :as tfl]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response]]
            [safely.core :refer [safely sleep]]))



(defonce active-disruptions
  (atom []))



(defn index-by
  [f coll]
  (->> coll
     (map (juxt f identity))
     (into {})))



(defn poll-disruptions!
  [config]
  (future
    (loop []
      (safely
          (let [disruptions (tfl/all-disruptions config)]
            (swap! active-disruptions
                   (fn [old new]
                     (->>
                      (merge
                       (index-by #(get % "id") old)
                       (index-by #(get % "id") new))
                      (map second)))
                   disruptions))
        :on-error
        :max-retries :forever
        :message "Polling disruptions data")
      ;; sleep between 30s-2min between polls
      (sleep :min 30000 :max 120000)
      (recur))))



(defn service-api
  [config]
  (wrap-json-response
   (routes

    (GET "/disruptions" []
         {:status 200
          :body {:status "OK"
                 :active-disruptions
                 @active-disruptions}})

    (route/not-found
     {:status "ERROR" :message "Resource not found."}))))
