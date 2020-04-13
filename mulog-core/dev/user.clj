(ns user
  (:require [com.brunobonacci.mulog :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :refer [flake]]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| μ / L O G |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (u/log ::hello :to "World!")

  (def p1 (u/start-publisher! {:type :console}))

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
;;                       ----==| Μ / T R A C E |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(comment

  (def st
    (start-publisher!
     {:type :console}))

  (log :test :t (rand))

  (trace :test-trace
         [:foo 1, :t (rand)]
         (Thread/sleep (rand-int 50)))

  (trace :test-trace-wth-result
         {:pairs [:foo 1, :t (rand)] :capture #(select-keys % [:hello])}
         {:hello "world"})

  (trace :test-trace-capture-error
         {:pairs [:foo 1, :t (rand)] :capture #(select-keys % [:hello])}
         (rand-int 100))

  (trace :test-trace-wth-result
         {:pairs [:foo 1, :t (rand)] :capture (fn [x] {:return x})}
         (rand-int 100))

  (trace :test-syntax-error
         (identity {:pairs [:foo 1, :t (rand)] :capture-result :hello})
         {:hello "world"})

  (st)


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
