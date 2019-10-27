(ns user
  (:require [com.brunobonacci.mulog :as u]))


(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v 2)

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


  )




(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher! {:type :custom :fqn-function "com.brunobonacci.mulog.publishers.elasticsearch/elasticsearch-publisher"
                              :max-items 5000 :url "http://localhost:9200/"}))

  (x)

  )



(comment

  (u/log ::hello :to "World!")

  (u/start-publisher! {:type :console})

  (u/log ::hello :to "World!" :v (rand-int 1000))

  (def x (u/start-publisher! {:type :custom :fqn-function "com.brunobonacci.mulog.publishers.kafka/kafka-publisher"
                              :kafka {:bootstrap.servers "localhost:9092"}}))

  (x)


  )
