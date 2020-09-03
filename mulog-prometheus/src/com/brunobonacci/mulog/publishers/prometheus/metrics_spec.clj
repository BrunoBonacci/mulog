(ns com.brunobonacci.mulog.publishers.prometheus.metrics_spec
  (:require [clojure.spec.alpha :as s]))

(def metric-name-re                #"[a-zA-Z0-9_:]*")
(def metric-label-name-re          #"[a-zA-Z0-9_]*")
(def reserved-metric-label-name-re #"__.*")
(def sanitize-metric-name-re       #"[^a-zA-Z0-9_:]+")
(def sanitize-metric-label-name-re #"[^a-zA-Z0-9_]+")



(defn- good-name? [name] (s/and string? (re-matches metric-name-re name)))

(s/def :metric/metric-type #{:counter :gauge :histogram :summary})
(s/def :metric/metric-value double?)
(s/def :metric/namespace good-name?)
(s/def :metric/name good-name?)
(s/def :metric/description string?)
(s/def :metric/labels (s/map-of (s/and string?
                                  #(re-matches metric-label-name-re %)
                                  #(not (re-find reserved-metric-label-name-re %)))
                        string?))
(s/def :metric/buckets (s/every number?))
(s/def :metric/quantiles (s/every (s/and (s/coll-of number? :kind vector? :count 2)
                                    (s/every #(<= 0 % 1)))))


(s/def ::metric (s/and (s/keys :req [:metric/metric-type
                                     :metric/namespace
                                     :metric/name]
                         :opt [:metric/value
                               :metric/labels
                               :metric/description
                               :metric/buckets
                               :metric/quantiles])
                  (fn [{:metric/keys [metric-type metric-value]}]
                    (if (contains? #{:histogram :summary} metric-type) metric-value true))))
