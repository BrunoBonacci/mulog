(ns com.brunobonacci.disruptions.api
  (:require [com.brunobonacci.disruptions.tfl-api :as tfl]
            [com.brunobonacci.mulog :as μ]
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
      ;; Log poll event
      (μ/log :disruptions/initiated-poll)

      (safely
          (let [disruptions (tfl/all-disruptions config)]

            ;; record the active disruptions
            (μ/log :disruptions/poll-completed
                   :active-disruptions (count disruptions))

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



(defn wrap-events
  "tracks api events"
  [handler]
  (fn [req]
    (μ/with-context
      {:uri (get req :uri)
       :request-method (get req :request-method)
       :content-type (get-in req [:headers "content-type"])
       :content-encoding (get-in req [:headers "content-encoding"])}

      (μ/trace :disruptions/http-request
        []
        (fn [{:keys [status]}] {:http-status status})
        (handler req)))))



(defn service-api
  [config]
  (wrap-events             ;; tracks all requests with μ/log
   (wrap-json-response
    (routes

     (GET "/verbose" []
          (μ/verbose ::request)
          {:status 200
           :body {:status "OK" :message "verbose."}})

     (GET "/debug" []
          (μ/debug ::request)
          {:status 200
           :body {:status "OK" :message "debug."}})

     (GET "/info" []
          (μ/info ::request)
          {:status 200
           :body {:status "OK" :message "info."}})

     (GET "/warning" []
          (μ/warning ::request)
          {:status 200
           :body {:status "OK" :message "warning."}})

     (GET "/error" []
          (μ/error ::request)
          {:status 200
           :body {:status "OK" :message "error."}})

     (GET "/fatal" []
          (μ/fatal ::request)
          {:status 200
           :body {:status "OK" :message "fatal."}})

     (GET "/healthcheck" []
          {:status 200
           :body {:status "OK" :message "All good."}})


     (GET "/disruptions" []
          {:status 200
           :body {:status "OK"
                  :active-disruptions
                  @active-disruptions}})

     (route/not-found
      {:status "ERROR" :message "Resource not found."})))))
