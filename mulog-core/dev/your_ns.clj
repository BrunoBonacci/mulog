(ns your-ns
  (:require [com.brunobonacci.mulog :as μ]))


(comment

  (μ/log ::hello :to "New World!")


  (μ/start-publisher! {:type :console})

  (μ/log ::system-started :version "0.1.0")

  (μ/log ::user-logged :user-id "1234567" :remote-ip "1.2.3.4" :auth-method :password-login)

  (μ/log ::http-request :path "/orders", :method :post, :remote-ip "1.2.3.4", :http-status 201)


  (μ/log ::system-started :init-time 32)

  (μ/set-global-context! {:app-name "mulog-demo", :version "0.1.0", :env "local"})

  {:mulog/timestamp 1572709332340,
   :mulog/event-name :your-ns/system-started,
   :mulog/namespace "your-ns",
   :app-name "mulog-demo",
   :version "0.1.0",
   :env "local",
   :init-time 32}


  (μ/set-global-context! {})

  (μ/with-context {:order "abc123"}
    (μ/log ::process-item :item-id "sku-123" :qt 2))


  {:mulog/timestamp 1572711123826,
   :mulog/event-name :your-ns/process-item,
   :mulog/namespace "your-ns",
   :app-name "mulog-demo",
   :version "0.1.0",
   :env "local",
   :order "abc123",
   :item-id "sku-123",
   :qt 2}

  (μ/with-context {:transaction-id "tx-098765"}
    (μ/with-context {:order "abc123"}
      (μ/log ::process-item :item-id "sku-123" :qt 2)))

  {:mulog/timestamp 1572711123826,
   :mulog/event-name :your-ns/process-item,
   :mulog/namespace "your-ns",
   :app-name "mulog-demo",
   :version "0.1.0",
   :env "local",
   :transaction-id "tx-098765",
   :order "abc123",
   :item-id "sku-123",
   :qt 2}


  (defn process-item [sku quantity]
    ;; ... do something
    (μ/log ::item-processed :item-id "sku-123" :qt quantity)
    ;; ... do something
    )


  (μ/with-context {:order "abc123"}
    (process-item "sku-123" 2))
  )




(comment

  (def stop-all
    (μ/start-publisher!
     {:type :multi
      :publishers
      [{:type :console}
       {:type :simple-file :filename "/tmp/disk1/mulog/events1.log"}
       {:type :simple-file :filename "/tmp/disk2/mulog/events2.log"}]}))

  (μ/log ::hello :to "New World!")

  (stop-all)

  )




(comment

  (defn product-availability [product-id]
    (Thread/sleep (rand-int 500)))
  (def product-id "2345-23-545")
  (def order-id   "34896-34556")
  (def user-id    "709-6567567")

  (μ/trace ::availability
    [:product-id product-id, :order order-id, :user user-id]
    (product-availability product-id))


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


  )
