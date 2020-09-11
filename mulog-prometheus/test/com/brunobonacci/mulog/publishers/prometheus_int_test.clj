(ns com.brunobonacci.mulog.publishers.prometheus-int-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.flakes :as f]
            [com.brunobonacci.mulog.publishers.prometheus :as prom]
            [com.brunobonacci.mulog.publishers.prometheus.metrics :as m]
            [midje.sweet :refer :all]
            [clojure.string :as str]))



(defn random-event-name
  []
  (str "mulog_test_" (f/flake-hex (f/flake))))



(defn normalized-ns
  []
  (m/kw-str (str *ns*)))



(defn fetch-gateway-metrics
  [event-name]
  (->> (slurp "http://localhost:9091/metrics")
    (str/split-lines)
    (remove #(str/starts-with? % "#"))
    (filter (partial re-find (re-pattern event-name)))
    (map (partial re-find #"(^[^{]+)(\{[^}].*\}) ([\d.-]+)"))
    (map rest)
    (map (fn [[e l v]] [e l (read-string v)]))))



(defmacro with-test-publisher
  [event-name & body]
  `(let [pub# (u/start-publisher!
                {:type :prometheus
                 :push-gateway {:endpoint "http://localhost:9091/"
                                :job "mulog-test"
                                :push-interval-ms 1000}})]

     ;; logging test event
     ~@body

     ;; wait for flush
     (Thread/sleep 1500)

     ;; stop publisher
     (pub#)

     ;; fetching metrics
     (fetch-gateway-metrics ~event-name)))



(facts "PushGateway integration tests" :integration


  (fact "A simple event ends up as a counter"

    (let [event-name (random-event-name)
          ns         (normalized-ns)
          full-name  (str ns "_" event-name)]
      (with-test-publisher event-name

             ;; logging test event
        (u/log event-name :version "1.2.3"))

      => [;; a counter for each event
          [full-name "{instance=\"\",job=\"mulog-test\",version=\"1.2.3\"}" 1]]))



  (fact "An event with a numerical value ends up as a gauge with the value"

    (let [event-name (random-event-name)
          ns         (normalized-ns)
          full-name  (str ns "_" event-name)]
      (with-test-publisher event-name

             ;; logging test event
        (u/log event-name :version "1.2.3" :number 123))

      => [;; a counter for each event
          [full-name "{instance=\"\",job=\"mulog-test\",version=\"1.2.3\"}" 1]
              ;; a gauge with the value of the number
          [(str full-name "_number") "{instance=\"\",job=\"mulog-test\",version=\"1.2.3\"}" 123]]))



  (fact "An event with a numerical value ends up as a gauge with the value"

    (let [event-name (random-event-name)
          ns         (normalized-ns)
          full-name  (str ns "_" event-name)

          metrics
          (with-test-publisher event-name

                 ;; logging test event
            (u/trace event-name
              [:version "1.2.3"]
              (+ 1 1)))]

      (count metrics) => 8 ;; 1 counter + 5 quantiles + 1 total sum + 1 count
      (map first metrics)
      => [full-name
          (str full-name "_timer_nanos")
          (str full-name "_timer_nanos")
          (str full-name "_timer_nanos")
          (str full-name "_timer_nanos")
          (str full-name "_timer_nanos")
          (str full-name "_timer_nanos_sum")
          (str full-name "_timer_nanos_count")])
    )

  )
