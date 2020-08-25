(ns your-ns
  (:require [com.brunobonacci.mulog :as μ]))



(comment

  (μ/log ::hello :to "New World!")

  (μ/start-publisher! {:type :console})

  (μ/log ::event-name, :key1 "value1", :key2 :value2, :keyN "valueN")

  (μ/log ::system-started :version "0.1.0")

  (μ/log ::user-logged :user-id "1234567" :remote-ip "1.2.3.4" :auth-method :password-login)

  (μ/log ::http-request :path "/orders", :method :post, :remote-ip "1.2.3.4", :http-status 201)

  (def x (RuntimeException. "Boom!"))
  (μ/log ::invalid-request :exception x, :user-id "123456789", :items-requested 47)

  (μ/log ::position-updated :poi "1234567" :location {:lat 51.4978128, :lng -0.1767122} )




  (μ/log ::system-started :init-time 32)

  (μ/set-global-context! {:app-name "mulog-demo", :version "0.1.0", :env "local"})

  ;; {:mulog/event-name :your-ns/system-started,
  ;;  :mulog/timestamp 1587501375129,
  ;;  :mulog/trace-id #mulog/flake "4VTCYUcCs5KRbiRibgulnns3l6ZW_yxk",
  ;;  :mulog/namespace "your-ns",
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :init-time 32,
  ;;  :version "0.1.0"}


  (μ/set-global-context! {})

  (μ/with-context {:order "abc123"}
    (μ/log ::process-item :item-id "sku-123" :qt 2))

  ;; {:mulog/event-name :your-ns/process-item,
  ;;  :mulog/timestamp 1587501473472,
  ;;  :mulog/trace-id #mulog/flake "4VTCdCz6T_TTM9bS5LCwqMG0FhvSybkN",
  ;;  :mulog/namespace "your-ns",
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :item-id "sku-123",
  ;;  :order "abc123",
  ;;  :qt 2,
  ;;  :version "0.1.0"}



  (μ/with-context {:transaction-id "tx-098765"}
    (μ/with-context {:order "abc123"}
      (μ/log ::process-item :item-id "sku-123" :qt 2)))

  ;; {:mulog/event-name :your-ns/process-item,
  ;;  :mulog/timestamp 1587501492168,
  ;;  :mulog/trace-id #mulog/flake "4VTCeIc_FNzCjegzQ0cMSLI09RqqC2FR",
  ;;  :mulog/namespace "your-ns",
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :item-id "sku-123",
  ;;  :order "abc123",
  ;;  :qt 2,
  ;;  :transaction-id "tx-098765",
  ;;  :version "0.1.0"}


  (defn process-item [sku quantity]
    ;; ... do something
    (μ/log ::item-processed :item-id "sku-123" :qt quantity)
    ;; ... do something
    )


  (μ/with-context {:order "abc123"}
    (process-item "sku-123" 2))

  ;; {:mulog/event-name :your-ns/item-processed,
  ;;  :mulog/timestamp 1587501555926,
  ;;  :mulog/trace-id #mulog/flake "4VTCi08XrCWQLrR8vS2nP8sG1zDTGuY_",
  ;;  :mulog/namespace "your-ns",
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :item-id "sku-123",
  ;;  :order "abc123",
  ;;  :qt 2,
  ;;  :version "0.1.0"}

  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| Μ / T R A C E |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment

  (defn product-availability [product-id]
    (http/get availability-service {:product-id product-id}))

  (defn product-availability [product-id]
    (Thread/sleep (rand-int 500)))

  (μ/trace ::availability
    []
    (product-availability product-id))

  ;; {:mulog/event-name :your-ns/availability,
  ;;  :mulog/timestamp 1587504242983,
  ;;  :mulog/trace-id #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
  ;;  :mulog/root-trace #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
  ;;  :mulog/duration 254402837,
  ;;  :mulog/namespace "your-ns",
  ;;  :mulog/outcome :ok,
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :version "0.1.0"}


  (def product-id "2345-23-545")
  (def order-id   "34896-34556")
  (def user-id    "709-6567567")

  (μ/with-context {:order order-id, :user user-id}
    (μ/trace ::availability
      [:product-id product-id]
      (product-availability product-id)))

  ;; {:mulog/event-name :your-ns/availability,
  ;;  :mulog/timestamp 1587506497789,
  ;;  :mulog/trace-id #mulog/flake "4VTHCez0rr3TpaBmUQrTb2DZaYmaWFkH",
  ;;  :mulog/root-trace #mulog/flake "4VTHCez0rr3TpaBmUQrTb2DZaYmaWFkH",
  ;;  :mulog/duration 280510026,
  ;;  :mulog/namespace "your-ns",
  ;;  :mulog/outcome :ok,
  ;;  :app-name "mulog-demo",
  ;;  :env "local",
  ;;  :order "34896-34556",
  ;;  :product-id "2345-23-545",
  ;;  :user "709-6567567",
  ;;  :version "0.1.0"}
  )



;;
;; Nested trace example
;;

(comment

  (defn warehouse-availability [product-id]
    (Thread/sleep (rand-int 100))
    (rand-int 100))

  (defn shopping-carts [product-id mode]
    (Thread/sleep (rand-int 100))
    (rand-int 10))

  (defn availability-estimator [warehouse in-flight-carts]
    (Thread/sleep (rand-int 100))
    (- warehouse in-flight-carts))

  (defn product-availability [product-id]
    (let [warehouse
          (μ/trace ::warehouse-availability
            [:product-id product-id :app-name "warehouse"]
            (warehouse-availability product-id))

          in-flight-carts
          (μ/trace ::shopping-carts
            [:product-id product-id :app-name "carts"]
            (shopping-carts product-id :in-flight))

          estimated
          (μ/trace ::availability-estimator
            [:product-id product-id :app-name "stock-mgmt"]
            (availability-estimator warehouse in-flight-carts))]

      {:availability estimated}))

  (defn process-order [order-id items]
    (Thread/sleep (rand-int 100))
    {:order order-id
     :items (mapv
              (fn [product-id]
                (μ/trace ::availability
                  [:product-id product-id :app-name "stock-mgmt"]
                  (product-availability product-id))) items)})


  (def items ["2345-23-545" "6543-43-0032"])
  (def order-id   "34896-34556")
  (def user-id    "709-6567567")


  (μ/with-context {:user user-id :order-id order-id}
    (μ/trace ::process-order
      [:order-type :premium :app-name "order-api"]
      (process-order order-id items)))


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

  (require
    '[com.brunobonacci.mulog.buffer :as rb]
    '[clojure.pprint :refer [pprint]])


  (deftype MyCustomPublisher [config buffer]

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


  (def st
    (μ/start-publisher!
      {:type :inline :publisher (my-custom-publisher {:pretty-print true})}))

  (μ/log :test-event)




  (defn- pprint-str
    [v]
    (with-out-str
      (pprint v)))


  (deftype MyCustomPublisher [config buffer ^java.io.Writer filewriter]

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
