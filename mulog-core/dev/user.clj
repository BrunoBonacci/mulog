(ns user
  (:require [com.brunobonacci.mulog :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.levels :as lvl]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :refer [flake]]))


(comment

  (u/log ::hello :to "World!")

  (def p1 (u/start-publisher! {:type :console}))

  (u/log ::hello :to "World!" :v 2)

  (p1)

  (def p1 (u/start-publisher! {:type :console
                               :level ::lvl/info}))

  (u/log ::hello :to "World!")

  (u/verbose ::hello :to "World!")
  (u/debug   ::hello :to "World!")
  (u/info    ::hello :to "World!")
  (u/warning ::hello :to "World!")
  (u/error   ::hello :to "World!")
  (u/fatal   ::hello :to "World!")

  (u/set-global-context! {:app "demo" :version 1 :env "local"
                          :mulog/level ::lvl/warning})

  (u/log ::hello :to "World!")

  (u/verbose ::hello :to "World!")
  (u/debug   ::hello :to "World!")
  (u/info    ::hello :to "World!")
  (u/warning ::hello :to "World!")
  (u/error   ::hello :to "World!")
  (u/fatal   ::hello :to "World!")

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

  (u/log ::hello :to "World!" :v 1000)
  (u/log ::hello :to "World!" :v 200)

  (x)

  (def xform1
    (u/start-publisher!
      {:type :console
       :transform (fn [events]
                    (->> events
                         (filter #(< (:v %) 500))
                         (map #(update % :v -))
                         (map #(update % :mulog/level (comp str/upper-case
                                                            name)))))}))

  (u/info ::hello :to "World!" :v 1000)
  (u/info ::hello :to "World!" :v 200)

  (xform1)

  (def h (-> (make-hierarchy)
             (derive ::wockety ::wack)
             (derive ::pockety ::wockety)
             (derive ::hockety ::pockety)))

  (def xform2 (u/start-publisher!
                {:type :console
                 :transform (fn [events]
                              (->> events
                                   ((lvl/->filter ::pockety h :log-level))
                                   (map #(update % :v inc))))}))

  (u/log ::hello :to "World!" :v 1)
  (u/log ::hello :to "World!" :log-level ::wack    :v 1)
  (u/log ::hello :to "World!" :log-level ::wockety :v 2)
  (u/log ::hello :to "World!" :log-level ::pockety :v 3)
  (u/log ::hello :to "World!" :log-level ::hockety :v 4)

  (xform2)

  )


;;
;; ElasticSearch
;;
(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))
  (u/log ::hello :to "World!" :v "ciao")
  (def x (u/start-publisher! {:type :elasticsearch
                              :url "http://localhost:9200/"}))

  (x)

  )



(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher! {:type :kafka
                              :kafka {:bootstrap.servers "192.168.200.200:9092"}}))

  (x)


  )


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

  (require '[com.brunobonacci.mulog.buffer :as rb]
           '[clojure.pprint :refer [pprint]])


  (deftype MyCustomPublisher
      [config buffer]


    com.brunobonacci.mulog.publisher.PPublisher
    (agent-buffer [_]
      buffer)


    (publish-delay [_]
      500)


    (publish [_ buffer]
      ;; check our printer option
      (let [printer (if (:pretty-print config) pprint prn)]
        ;; items are pairs [offset <item>]
        (doseq [item (map second (rb/items buffer))]
          ;; print the item
          (printer item)))
      ;; return the buffer minus the published elements
      (rb/clear buffer)))


  (defn my-custom-publisher
    [config]
    (MyCustomPublisher. config (rb/agent-buffer 10000)))




  (defn- pprint-str
    [v]
    (with-out-str
      (pprint v)))


  (deftype MyCustomPublisher
      [config buffer ^java.io.Writer filewriter]


    com.brunobonacci.mulog.publisher.PPublisher
    (agent-buffer [_]
      buffer)


    (publish-delay [_]
      500)


    (publish [_ buffer]
      ;;    check our printer option
      (let [printer (if (:pretty-print config) pprint-str prn-str)
            ;; take at most `:max-items` items
            items (take (:max-items config) (rb/items buffer))
            ;; save the offset of the last items
            last-offset (-> items last first)]
        ;; write the items to the file
        (doseq [item (map second items)]
          ;; print the item
          (.write filewriter (printer item)))
        ;; flush the buffer
        (.flush filewriter)
        ;; return the buffer minus the published elements
        (rb/dequeue buffer last-offset))))


  (defn my-custom-publisher
    [{:keys [filename] :as config}]
    (let [config (merge {:pretty-print false :max-items 1000} config)]
      (MyCustomPublisher. config (rb/agent-buffer 10000)
                          (io/writer (io/file filename) :append true))))



  (require '[com.brunobonacci.mulog.publisher :as pl])

  (def p (my-custom-publisher {:filename "/tmp/foo.txt" :max-items 2}))

  (def b (-> (rb/ring-buffer 4)
            (rb/enqueue {::a 1 ::b 2 :c 3 :d 23 :x 34 :l 345678 :z (range 10)})
            (rb/enqueue {::a 1 ::b 2 :c 3 :d 23 :x 34 :l 345678 :z (range 10)})
            (rb/enqueue {::a 1 ::b 2 :c 3 :d 23 :x 34 :l 345678 :z (range 10)})))

  (pl/publish p b)

  )
