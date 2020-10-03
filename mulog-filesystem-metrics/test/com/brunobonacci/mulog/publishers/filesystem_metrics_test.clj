(ns com.brunobonacci.mulog.publishers.filesystem-metrics-test
  (:require [com.brunobonacci.mulog.publishers.filesystem-metrics :refer [capture-fs-metrics]]
            [midje.sweet :refer [facts fact => just]]
            [clojure.spec.test.alpha :as st])
  (:import  [java.nio.file FileSystems]))


(st/instrument 'com.brunobonacci.mulog.publishers.filesystem-metrics/capture-fs-metrics)



(facts "it should sample filesystem metrics"
  (fact "Filesystem"
    (let [fs (FileSystems/getDefault)
          store (first (.getFileStores fs))]
      (capture-fs-metrics store))
    => (just {:name string?
              :type string?
              :path string?
              :readonly? boolean?
              :total-bytes int?
              :unallocated-bytes int?
              :usable-bytes int?})))
