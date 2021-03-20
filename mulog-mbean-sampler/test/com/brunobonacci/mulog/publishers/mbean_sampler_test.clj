(ns com.brunobonacci.mulog.publishers.mbean-sampler-test
  (:require [com.brunobonacci.mulog.publishers.mbean-sampler :refer [mbeans-sample]]
            [midje.sweet :refer [facts fact => just contains]]))


(facts "it should sample MBeans metrics"

  (fact "memory"
    (mbeans-sample "java.lang:type=Memory")
    => (just
         [(contains
            {:domain "java.lang",
             :keys {"type" "Memory"},
             :attributes
             (contains
               {:Verbose boolean?
                :ObjectPendingFinalizationCount int?
                :HeapMemoryUsage
                (contains
                  {:committed int?
                   :init int?
                   :max int?
                   :used int?}),
                :NonHeapMemoryUsage
                (contains
                  {:committed int?,
                   :init int?,
                   :max int?,
                   :used int?}),
                :ObjectName "java.lang:type=Memory"})})])))
