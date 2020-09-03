(ns com.brunobonacci.mulog.publishers.prometheus.metrics-spec
  (:require [clojure.spec.alpha :as s]))

(def valid-metric-name-chars     #"[a-zA-Z0-9_:]*")
(def valid-metric-label-chars    #"[a-zA-Z0-9_]*")
(def reserved-metric-label-chars #"__.*")
(def invalid-metric-name-chars   #"[^a-zA-Z0-9_:]+")
(def invalid-metric-label-chars  #"[^a-zA-Z0-9_]+")



(defn- good-name? [name] (s/and string? (re-matches valid-metric-name-chars name)))

(s/def :metric/type #{:counter :gauge :histogram :summary})
(s/def :metric/value double?)
(s/def :metric/namespace good-name?)
(s/def :metric/name good-name?)
(s/def :metric/description string?)
(s/def :metric/labels (s/map-of (s/and string?
                                  #(re-matches valid-metric-label-chars %)
                                  #(not (re-find reserved-metric-label-chars %)))
                        string?))
(s/def :metric/buckets (s/every number?))
(s/def :metric/quantiles (s/every (s/and (s/coll-of number? :kind vector? :count 2)
                                    (s/every #(<= 0 % 1)))))


(s/def ::metric (s/and (s/keys :req [:metric/type
                                     :metric/namespace
                                     :metric/name]
                         :opt [:metric/value
                               :metric/labels
                               :metric/description
                               :metric/buckets
                               :metric/quantiles])
                  (fn [{:metric/keys [type value]}]
                    (if (contains? #{:histogram :summary} type) value true))))
