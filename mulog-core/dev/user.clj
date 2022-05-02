(ns user
  (:require [com.brunobonacci.mulog :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :refer [flake]]
            [com.brunobonacci.mulog.core :as core]
            [clojure.walk :as walk]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| μ / L O G |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (u/log ::hello :to "World!")

  (def p1 (u/start-publisher! {:type :console :pretty? true}))

  (u/log ::hello :to "World!" :v 2)

  (def p2 (u/start-publisher! {:type :simple-file :filename "/tmp/mulog.edn"}))

  (u/set-global-context! {:app "demo" :version 1 :env "local"})

  (u/log ::hello :to "World!")

  (u/update-global-context! update :version inc)

  (u/log ::hello :to "World!")

  (u/log ::hello :to "World" :app "new-app")

  (u/with-context {:order "abc123"}
    (u/log ::process-item :item-id "sku-123" :qt 2))


  (defn process-item [sku quantity]
    ;; ... do something
    (u/log ::item-processed :item-id "sku-123" :qt quantity)
    ;; ... do something
    )


  (u/with-context {:order "abc123"}
    (process-item "sku-123" 2))


  (u/with-context {:order "abc123"}
    (u/with-context {:batch "b9876"}
      (process-item "sku-123" 2)))

  ;; stop publishers
  (p1)
  (p2)
  )



(comment

  ;; list registered publishers
  (core/registered-publishers)
  ;; STOP LAST publisher
  (core/stop-publisher! (->> (core/registered-publishers) last :id))

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| μ / T R A C E |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (def st
    (u/start-publisher!
      {:type :console}))

  (def st2
    (u/start-publisher!
      {:type :zipkin :url "http://localhost:9411/"}))

  (u/log :test :t (rand))

  (u/trace :test-trace
    [:foo 1, :t (rand)]
    (Thread/sleep (rand-int 50)))

  (u/trace :test-trace-wth-result
    {:pairs [:foo 1, :t (rand)] :capture #(select-keys % [:hello])}
    {:hello "world" :capture "test"})

  (u/trace :test-trace-capture-error
    {:pairs [:foo 1, :t (rand)] :capture #(select-keys % [:hello])}
    (rand-int 100))

  (u/trace :test-trace-wth-result
    {:pairs [:foo 1, :t (rand)] :capture (fn [x] {:return x})}
    (rand-int 100))

  (u/trace :test-syntax-error
    (identity {:pairs [:foo 1, :t (rand)] :capture-result :hello})
    {:hello "world"})

  (u/trace :big-operation
    [:v 1 :level 0]
    (Thread/sleep (rand-int 2000))

    (u/trace :small-operation
      [:level 1 :seq 1]
      (Thread/sleep (rand-int 2000)))

    (u/trace :small-operation
      [:level 1 :seq 2]
      (Thread/sleep (rand-int 2000))

      (u/trace :operation
        [:level 3]
        (Thread/sleep (rand-int 1000)))

      (Thread/sleep (rand-int 10)))
    (Thread/sleep (rand-int 200)))

  (st2)


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| T R A N S F O R M |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


;; testing transform
(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console :pretty? true})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (def x (u/start-publisher!
           {:type :console
            :transform (fn [events]
                         (->> events
                           (filter #(< (:v %) 500))
                           (map #(update % :v -))))}))

  (x)

  (u/start-publisher!
    {:type :console
     :pretty? true
     :transform
     (fn [events]
       (filter #(or (= (:mulog/event-name %) :myapp/payment-done )
                  (= (:mulog/event-name %) :myapp/transaction-closed ))
         events))})

  (require '[where.core :refer [where]])

  (u/start-publisher!
    {:type :console
     :pretty? true
     :transform
     (partial filter (where :mulog/event-name :in? [:myapp/payment-done :myapp/transaction-closed]))})

  (u/log :myapp/payment-done :amount 150.30)

  (core/stop-publisher! (->> (core/registered-publishers) last :id))
  )




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                  ----==| C O N S O L E - J S O N |==----                   ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (u/log ::hello :to "World!")

  (def x (u/start-publisher! {:type :console-json}))
  (def x (u/start-publisher! {:type :console-json :pretty? true}))

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (u/log ::hello :to "World!" :v "ciao")

  (x)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| E L A S T I C S E A R C H |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (u/log ::hello :to "World!" :v "ciao")
  (def x (u/start-publisher! {:type :elasticsearch
                              :url "http://localhost:9200/"}))

  (def x (u/start-publisher! {:type :elasticsearch
                              :url "http://localhost:9200/"
                              :data-stream "mulog-stream"}))

  (x)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                   ----==| J V M - M E T R I C S |==----                    ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console :pretty? true})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (u/log ::hello :to "World!" :v "ciao")
  (def x (u/start-publisher! {:type :jvm-metrics
                              :sampling-interval 3000
                              ;;:transform-samples (partial map walk/stringify-keys)
                              }))

  (x)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;            ----==| F I L E S Y S T E M   M E T R I C S |==----             ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console :pretty? true})

  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher!
           {:type :filesystem-metrics
            ;; the interval in millis between two samples (default: 60s)
            :sampling-interval 5000
            ;; transform metrics (e.g. filter only volumes over 1 GB)
            ;; (default: `nil` leaves metrics unchanged)
            ;;:transform (partial map walk/stringify-keys)
            ;;:transform-samples (partial map walk/stringify-keys)
            }))

  (x)

  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                 ----==| M B E A N - S A M P L E R |==----                  ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console :pretty? true})
  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher!
           {:type :mbean
            :mbeans-patterns ["java.lang:type=Memory" "java.nio:*"]
            :sampling-interval 5000
            ;;:transform walk/stringify-keys
            ;;:transform-samples (partial map walk/stringify-keys)
            }))

  (x)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| K A F K A |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher! {:type :kafka
                              :format :nippy
                              :kafka {:bootstrap.servers "localhost:9092"}}))

  (x)


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| S L A C K |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello2 :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher!
           {:type        :slack
            :webhook-url "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
            :transform   (partial filter #(= ::hello (:mulog/event-name %)))}))

  (x)


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                        ----==| Z I P K I N |==----                         ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/set-global-context! {:app-name "zipkin-test"})

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console :pretty? true})

  (def x (u/start-publisher! {:type :zipkin
                              :url "http://localhost:9411/"}))

  (let [t (rand-int 1000)]
    (u/trace ::sleep
      [:time t :struct {:foo 1 :bar 2}]
      (Thread/sleep t)))


  (u/trace ::sleep-outer
    [:struct {:foo 1 :bar 2}]
    (Thread/sleep (rand-int 1000))

    (u/trace ::sleep-inner
      [:step 1]
      (Thread/sleep (rand-int 1000)))

    (try
      (u/trace ::sleep-inner
        [:step 2]
        (Thread/sleep (rand-int 1000))
        (/ 1 0))
      (catch Exception _))

    (u/trace ::sleep-inner
      [:step 3]
      (Thread/sleep (rand-int 1000)))
    )

  (x)


  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                    ----==| P R O M E T H E U S |==----                     ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello2 :to "World!" :v (rand-int 1000))

  (u/trace ::long-op
    [:mode :test]
    (Thread/sleep (+ 1000 (rand-int 300))))

  (def x (u/start-publisher!
           {:type :prometheus
            :push-gateway
            {:job      "mulog-demo"
             :endpoint "http://localhost:9091"}}))

  (x)


  (require '[com.brunobonacci.mulog.publishers.prometheus :as prom])
  ;; create your publisher
  (def pub (prom/prometheus-publisher {:type :prometheus}))
  ;; start the publisher
  (def px (u/start-publisher! {:type :inline :publisher pub}))

  (prom/registry pub)
  (prom/write-str pub)

  ;; ring - handler to export
  (fn [_]
    {:status  200
     :headers {"Content-Type" "text/plain; version=0.0.4"}
     :body    (prom/write-str pub)})


  ;; compojure example
  (def my-routes
    (routes
      ;; your existing routes
      (GET "/hello" [] "Hello World!")
      ;; here you can expose the metrics to Prometheus scraping process.
      (GET "/metrics" []
        {:status  200
         :headers {"Content-Type" "text/plain; version=0.0.4"}
         :body    (prom/write-str pub)})
      (route/not-found "<h1>Page not found</h1>")))

  ;;(def x (u/start-publisher! {:type :prometheus}))

  )




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                          ----==| J S O N |==----                           ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (def data {:color (java.awt.Color. 202 255 238)})

  (require '[charred.api])

  (extend-protocol charred.api/PToJSON

    java.awt.Color
    (->json-data [^java.awt.Color x]
      (str/upper-case
        (str "#"
          (Integer/toHexString (.getRed x))
          (Integer/toHexString (.getGreen x))
          (Integer/toHexString (.getBlue x))))))


  (require '[com.brunobonacci.mulog.common.json :refer [to-json]])

  (to-json data)
  ;; => "{\"color\": \"#CAFFEE\"}"


  )




;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                    ----==| E V E N T S   D O C |==----                     ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;;
  ;; list events by event-name in pretty-printed format
  ;;

  (->> (io/file "/tmp/mulog.edn")
    slurp
    (str/split-lines)
    (map read-string)
    (map (juxt :mulog/event-name identity))
    (into {})
    (map second)
    (sort-by :mulog/timestamp)
    (run! ut/pprint-event))

  )



(comment

  ;; publisher for examples
  (require '[com.brunobonacci.mulog.buffer :as rb]
    '[com.brunobonacci.mulog.utils :as ut]
    '[clojure.pprint :refer [pprint]])


  (deftype ExamplesPublisher [config buffer]

    com.brunobonacci.mulog.publisher.PPublisher
    (agent-buffer [_]
      buffer)


    (publish-delay [_]
      500)


    (publish [_ buffer]
      ;; items are pairs [offset <item>]
      (doseq [item (map second (rb/items buffer))]
        ;; print the item
        (-> item
          ut/pprint-event-str
          (str/replace #"^" ";; ")
          (str/replace #"\n" "\n;; ")
          (str/replace #"\n;; $" "\n")
          ((partial printf "%s\n"))))
      ;; return the buffer minus the published elements
      (rb/clear buffer)))


  (defn examples-publisher
    []
    (ExamplesPublisher. {} (rb/agent-buffer 10000)))

  (st)
  (def st
    (u/start-publisher!
      {:type :inline :publisher (examples-publisher)}))

  (u/log ::example :foo :baz, :bar 1)
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;              ----==| T R A N S F E R   C O N T E X T |==----               ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (def p1 (u/start-publisher! {:type :console :pretty? true}))

  (u/log ::hello :to "World!")

  ;; {:mulog/event-name :user/hello,
  ;;  :mulog/timestamp 1596107461713,
  ;;  :mulog/trace-id #mulog/flake "4XP3B6hxSicK-nvYgLjvoq_AhGwrEw6I",
  ;;  :mulog/namespace "user",
  ;;  :to "World!"}


  (u/with-context {:context :v1}
    (u/log ::hello :to "World!"))

  ;; {:mulog/event-name :user/hello,
  ;;  :mulog/timestamp 1596108086680,
  ;;  :mulog/trace-id #mulog/flake "4XP2qHapQxkd7vXqU9vseLQ2ZtCAVB_U",
  ;;  :mulog/namespace "user",
  ;;  :context :v1,
  ;;  :to "World!"}


  (u/with-context {:context :v1}
    ;; on a different thread
    (future
      (u/log ::hello :to "World!")))

  ;; NOTE: missing `:context :v1`
  ;; {:mulog/event-name :user/hello,
  ;;  :mulog/timestamp 1596108119498,
  ;;  :mulog/trace-id #mulog/flake "4XP2sBrC4ODsG3aAGWOKW4FY4na117Wj",
  ;;  :mulog/namespace "user",
  ;;  :to "World!"}

  (u/with-context {:context :v1}

    ;; capture context
    (let [ctx (u/local-context)]
      ;; on a different thread
      (future
        ;; restore context in the different thread
        (u/with-context ctx
          (u/log ::hello :to "World!")))))

  ;; {:mulog/event-name :user/hello,
  ;;  :mulog/timestamp 1596108227200,
  ;;  :mulog/trace-id #mulog/flake "4XP2yT4Kx79cWOIq0EIVOKgcm-_KbxJR",
  ;;  :mulog/namespace "user",
  ;;  :context :v1,
  ;;  :to "World!"}
  )
