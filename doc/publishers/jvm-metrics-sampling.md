## JVM Metrics sampling
![since v0.3.0](https://img.shields.io/badge/since-v0.3.0-brightgreen)

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-jvm-metrics "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-jvm-metrics {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-jvm-metrics.svg)](https://clojars.org/com.brunobonacci/mulog-jvm-metrics)

It is possible to sample JVM metrics such as **memory, garbage
collector, threads, etc** using a special publisher.


``` clojure
;; configuration options
{:type :jvm-metrics
 ;; the interval in millis between two samples (default: 60s)
 :sampling-interval 60000

 ;; which metrics are you interested in sampling
 ;; (default: `{:all true}`)
 :jvm-metrics
 {:memory true
  :gc true
  :threads true
  :jvm-attrs true}

 ;; Transformation to apply to the samples before publishing.
 ;;
 ;; It is a function that takes a sequence of samples and
 ;; returns and updated sequence of samples:
 ;; `transform-samples -> sample-seq -> sample-seq`
 ;; This functions takes a sequence of `:jvm-metrics` samples
 ;; (eg filter for head > 85%)
 ;; `(partial filter #(> (get-in % [:memory :heap :usage-ratio] 0) 0.85))`
 :transform-samples identity}
```

Usage example:

``` clojure
(μ/start-publisher! {:type :jvm-metrics})
```


Here an example of the metrics sampled

``` clojure
{:mulog/event-name :mulog/jvm-metrics-sampled,
 :mulog/timestamp 1587504242983,
 :mulog/trace-id #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
 :mulog/root-trace #mulog/flake "4VTF9QBbnef57vxVy-b4uKzh7dG7r7y4",
 :jvm-metrics
 {:memory
  {:total
   {:init 276103168,
    :used 422243480,
    :max 4294967295,
    :committed 548872192},
   :heap
   {:init 268435456,
    :used 294888096,
    :max 4294967296,
    :committed 374341632,
    :usage-ratio 0.06866},
   :non-heap
   {:init 7667712,
    :used 127355384,
    :max -1,
    :committed 174530560,
    :usage-ratio 0.7297},
   :pools
   {:codeheap-non-nmethods-usage 0.2269,
    :metaspace-usage 0.694,
    :codeheap-profiled-nmethods-usage 0.1748,
    :compressed-class-space-usage 0.01662,
    :g1-eden-space-usage 0.8018,
    :g1-old-gen-usage 0.02473,
    :g1-survivor-space-usage 0.9691,
    :codeheap-non-profiled-nmethods-usage 0.08243}},
 :gc
 {:g1-young-generation-count 56,
  :g1-young-generation-time 1068,
  :g1-old-generation-count 0,
  :g1-old-generation-time 0},
 :threads
 {:deadlock-count 0,
  :deadlocks {},
  :daemon-count 18,
  :new-count 0,
  :runnable-count 7,
  :terminated-count 0,
  :blocked-count 0,
  :waiting-count 12,
  :count 25,
  :timed-waiting-count 6},
  :jvm-attrs {:name "20366@hostname.local",
  :vendor "AdoptOpenJDK (14)",
  :version "14+36",
  :process-id 20366}}}
```

*NOTE: values and keys will change depending on JVM/GC settings.*
