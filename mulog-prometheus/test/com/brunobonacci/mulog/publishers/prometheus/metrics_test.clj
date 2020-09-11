(ns com.brunobonacci.mulog.publishers.prometheus.metrics-test
  (:require [com.brunobonacci.mulog.publishers.prometheus.metrics
             :refer [events->metrics kw-str label-key-str]]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec :as met-spec]
            [midje.sweet :refer [facts fact => tabular]]))



(facts "Names follow the conventions"
  (fact "bad name 1"
    (kw-str "@@@@bad_name1") => met-spec/valid-metric-name-chars))



(facts "Label names follow the conventions"
  (fact "bad label 1"
    (label-key-str "_____bad_label_1") => met-spec/valid-metric-label-chars))



(tabular
  (fact "Conversion between events names to metrics names"

    (->> [?evt]
      (events->metrics)
      (map :metric/name))
    => [?metric-name])

  ?evt                                                                     ?metric-name
  {:mulog/namespace "namespace" :mulog/event-name  "event-name"}          "namespace_event_name"
  {:mulog/namespace "namespace" :mulog/event-name  :event-name}           "namespace_event_name"
  {:mulog/namespace "namespace" :mulog/event-name  :group/event-name}     "namespace_group_event_name"
  {:mulog/event-name  :event-name}                                        "event_name"
  {:mulog/event-name  :group/event-name}                                  "group_event_name"
  {:mulog/namespace  "same.namespace"
   :mulog/event-name :same.namespace/event-name}                          "same_namespace_event_name"
  {:mulog/namespace  (str *ns*) :mulog/event-name ::event-name}           "com_brunobonacci_mulog_publishers_prometheus_metrics_test_event_name"
  )
