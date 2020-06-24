(ns user
  (:require [com.brunobonacci.mulog :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :refer [flake]]
            [com.brunobonacci.mulog.core :as core]))



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

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (def x (u/start-publisher!
           {:type :console
            :transform (fn [events]
                         (->> events
                           (filter #(< (:v %) 500))
                           (map #(update % :v -))))}))

  (x)

  )



(comment

  ;; stop publisher
  (core/registered-publishers)
  ;; STOPLAST
  (core/stop-publisher! (->> (core/registered-publishers) last :id))


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

  (x)

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                   ----==| J V M - M E T R I C S |==----                    ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (u/log ::hello :to "World!" :v "ciao")
  (def x (u/start-publisher! {:type :jvm-metrics
                              :sampling-interval 3000}))

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
                              :kafka {:bootstrap.servers "192.168.200.200:9092"}}))

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

  (u/start-publisher! {:type :console})

  (def x (u/start-publisher! {:type :zipkin
                              :url "http://localhost:9411/"}))

  (let [t (rand-int 3000)]
    (u/trace ::sleep
      [:time t :struct {:foo 1 :bar 2}]
      (Thread/sleep t)))


  (x)


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


  (deftype ExamplesPublisher
      [config buffer]


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
