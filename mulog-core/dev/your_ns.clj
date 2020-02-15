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
