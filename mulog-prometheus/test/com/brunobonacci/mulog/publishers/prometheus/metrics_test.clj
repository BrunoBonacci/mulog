(ns com.brunobonacci.mulog.publishers.prometheus.metrics-test
  (:require [com.brunobonacci.mulog.publishers.prometheus.metrics :refer [kw-str label-key-str]]
            [com.brunobonacci.mulog.publishers.prometheus.metrics-spec :as met-spec]
            [midje.sweet :refer [facts fact =>]]))

(facts "Names follow the conventions"
  (fact "bad name 1"
    (kw-str "@@@@bad_name1") => met-spec/valid-metric-name-chars))

(facts "Label names follow the conventions"
  (fact "bad label 1"
    (label-key-str "_____bad_label_1") => met-spec/valid-metric-label-chars))