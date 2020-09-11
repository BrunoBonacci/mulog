(ns com.brunobonacci.mulog.publishers.prometheus-test
  (:require [com.brunobonacci.mulog.publishers.prometheus]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec :as met-spec]
            [com.brunobonacci.mulog.flakes :refer [flake]]
            [midje.sweet :refer [facts fact => contains anything just]]
            [midje.util :refer [testable-privates]])
  (:import [io.prometheus.client
            CollectorRegistry
            Counter
            Gauge
            Summary]))



(testable-privates com.brunobonacci.mulog.publishers.prometheus
  events->metrics
  metrics->met-cols)



(def counter-event
  {:mulog/event-name :com.brunobonacci.mulog.publishers.prometheus/hello
   :mulog/timestamp  1599648898455
   :mulog/trace-id   #mulog/flake "4YBNBeM0aJWpDGDRggKRcSbwkNbwXYNI"
   :mulog/namespace  "com.brunobonacci.mulog.publishers.prometheus"
   :to               "world!"})



(def counter-event-2 ;; An extra label is added to this event
  {:mulog/event-name :com.brunobonacci.mulog.publishers.prometheus/hello
   :mulog/timestamp  1599648898455
   :mulog/trace-id   #mulog/flake "4YBNBeM0aJWpDGDRggKRcSbwkNbwXYNI"
   :mulog/namespace  "com.brunobonacci.mulog.publishers.prometheus"
   :to               "world!"
   :from             "The big bang"})



(def gauge-event
  {:mulog/event-name :com.brunobonacci.mulog.publishers.prometheus/hello
   :mulog/timestamp  1599648898455
   :mulog/trace-id   #mulog/flake "4YBNBeM0aJWpDGDRggKRcSbwkNbwXYNI"
   :mulog/namespace  "com.brunobonacci.mulog.publishers.prometheus"
   :v                1.0
   :to               "world!"})



(def counter-duration-event
  {:mulog/event-name :com.brunobonacci.mulog.publishers.prometheus/hello
   :mulog/timestamp  1599648898455
   :mulog/trace-id   #mulog/flake "4YBNBeM0aJWpDGDRggKRcSbwkNbwXYNI"
   :mulog/namespace  "com.brunobonacci.mulog.publishers.prometheus"
   :mulog/duration   8195
   :to               "world!"})



(def counter-duration-gauge-event
  {:mulog/event-name :com.brunobonacci.mulog.publishers.prometheus/hello
   :mulog/timestamp  1599648898455
   :mulog/trace-id   #mulog/flake "4YBNBeM0aJWpDGDRggKRcSbwkNbwXYNI"
   :mulog/namespace  "com.brunobonacci.mulog.publishers.prometheus"
   :mulog/duration   8195
   :v                1.0
   :to               "world!"})



(defn check-label-keys
  [label-keys]
  (fn [v]
    (let [v (set v)]
      (and (= v label-keys)
        (every?
          #(and
             (re-matches met-spec/valid-metric-label-chars %)
             (not (re-find met-spec/reserved-metric-label-chars %)))
          v)))))



(defn counter-check
  [labels label-keys]
  (contains {:metric/type        :counter
             :metric/name        met-spec/valid-metric-name-chars
             :metric/value       nil?
             :metric/description string?
             :metric/buckets     empty?
             :metric/labels      labels
             :metric/label-keys  (check-label-keys label-keys)}))



(defn gauge-check
  [labels label-keys]
  (contains {:metric/type        :gauge
             :metric/name        met-spec/valid-metric-name-chars
             :metric/value       double?
             :metric/description string?
             :metric/buckets     empty?
             :metric/labels      labels
             :metric/label-keys  (check-label-keys label-keys)}))



(defn summary-check
  [labels label-keys]
  (contains {:metric/type        :summary
             :metric/name        met-spec/valid-metric-name-chars
             :metric/value       double?
             :metric/description string?
             :metric/buckets     empty?
             :metric/labels      labels
             :metric/label-keys  (check-label-keys label-keys)}))



(defn empty-registry
  []
  (CollectorRegistry.))



(facts "It should create metrics"


  (fact "It should create a counter"
    (events->metrics identity [counter-event])
    => (just #{(counter-check {"to" "world!"} #{"to"})}))


  (fact "It should create a counter and a gauge"
    (events->metrics identity [gauge-event])
    => (just #{(counter-check {"to" "world!"} #{"to"})
               (gauge-check   {"to" "world!"} #{"to"})}))


  (fact "It should create a counter and a summary for duration"
    (events->metrics identity [counter-duration-event])
    => (just #{(counter-check {"to" "world!"} #{"to"})
               (summary-check {"to" "world!"} #{"to"})}))


  (fact "It should create a counter, gauge, and a summary for duration"
    (events->metrics identity [counter-duration-gauge-event])
    => (just #{(counter-check {"to" "world!"}           #{"to"})
               (gauge-check   {"to" "world!"}           #{"to"})
               (summary-check {"to" "world!"}           #{"to"})})))



(facts "Given metrics identical collection types should be registered"

  ;; A new registry is created every time for a clean test.
  ;; This is to prevent any labeling issues during checks


  (fact "It should register a counter collection"
    (->>  (events->metrics identity [counter-event])
      (metrics->met-cols (empty-registry)))
    => (just #{(just [(counter-check {"to" "world!"} #{"to"})
                      #(instance? Counter %)])}))


  (fact "It should register a counter and a gauge collection"
    (->> (events->metrics identity [gauge-event])
      (metrics->met-cols (empty-registry)))
    => (just #{(just [(counter-check {"to" "world!"} #{"to"})
                      #(instance? Counter %)])
               (just [(gauge-check {"to" "world!"}   #{"to"})
                      #(instance? Gauge %)])}))


  (fact "It should register a counter and a summary for duration"
    (->> (events->metrics identity [counter-duration-event])
      (metrics->met-cols (empty-registry)))
    => (just #{(just [(counter-check {"to" "world!"} #{"to"})
                      #(instance? Counter %)])
               (just [(summary-check {"to" "world!"} #{"to"})
                      #(instance? Summary %)])}))


  (fact "It should register a counter, gauge, and a summary for duration"
    (->> (events->metrics identity [counter-duration-gauge-event])
      (metrics->met-cols (empty-registry)))
    => (just #{(just [(counter-check {"to" "world!"} #{"to"})
                      #(instance? Counter %)])
               (just [(gauge-check {"to" "world!"}   #{"to"})
                      #(instance? Gauge %)])
               (just [(summary-check {"to" "world!"} #{"to"})
                      #(instance? Summary %)])})))



(facts "Given dynamic labels the registry remembers the first set"

  (fact "Given the same metric with different labels the first set of labels is always used"
    (->>  (events->metrics identity [counter-event counter-event-2])
      (metrics->met-cols (empty-registry)))
    => (just #{(just [(counter-check {"to" "world!"} #{"to"})
                      #(instance? Counter %)])
               (just [(counter-check {"to" "world!" "from" "The big bang"} #{"to"})
                      #(instance? Counter %)])})))
