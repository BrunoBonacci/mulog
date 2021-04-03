## Filesystem Metrics sampling
![since v0.6.0](https://img.shields.io/badge/since-v0.6.0-brightgreen)

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-filesystem-metrics "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-filesystem-metrics {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-filesystem-metrics.svg)](https://clojars.org/com.brunobonacci/mulog-filesystem-metrics)

It is possible to sample filesystem metrics such as **total and
available disk space** for each mounted filesystem using a special
publisher.


``` clojure
(μ/start-publisher!
  {:type :filesystem-metrics
   ;; the interval in millis between two samples (default: 60s)
   :sampling-interval 60000
   ;; transform metrics (e.g. filter only volumes over 1 GB)
   ;; (default: `nil` leaves metrics unchanged)
   :transform
   (partial filter #(> (:total-bytes %) 1e9))})
```

Here an example of the metrics sampled for one filesystem

``` clojure
{:mulog/event-name :mulog/filesystem-metrics-sampled,
 :mulog/timestamp 1601629811722,
 :mulog/trace-id #mulog/flake "4YcWozKEUcfE9JxfQMnuwb-NnO8ygEpE",
 :filesystem-metrics
 {:name "/dev/disk1s1"
  :type "apfs"
  :path "/"
  :readonly? false
  :total-bytes 499963170816
  :unallocated-bytes 40646381568
  :usable-bytes 30255194112}}
```

*NOTE: values will change depending on available disks.*
