## MBean Metrics sampling (JMX)
![since v0.7.0](https://img.shields.io/badge/since-v0.7.0-brightgreen)

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-mbean-sampler "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-mbean-sampler {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-mbean-sampler.svg)](https://clojars.org/com.brunobonacci/mulog-mbean-sampler)

This sampler uses the publisher infrastructure to sample MBeans values
at regular intervals and publish them as ***μ/log*** events.
The sampled events will be then published to all registered publisher
and dispatched to third-party systems.


``` clojure
;; Configuration options
{:type :mbean
 ;; Interval in milliseconds between two samples (Default: 60s)
 ;; :sampling-interval 60000

 ;; list of MBean patterns to sample
 :mbeans-patterns []  ;; REQUIRED

 ;; Transformation to apply to the samples before publishing.
 ;;
 ;; It is a function that takes a sequence of samples and
 ;; returns and updated sequence of samples:
 ;; `transform-samples -> sample-seq -> sample-seq`
 ;; This function takes in input a sequence of `:mbean` samples
 ;; (eg filter a particular domain)
 ;; `(partial filter #(= "java.lang" (:domain %)))`
 :transform-samples identity
}
```

An ObjectName can be written as a String with the following elements in order:

  - The domain.
  - A colon (:).
  - A key property list as defined below.

Example:

  - `*:type=Foo,name=Bar` to match names in any domain whose exact
     set of keys is `type=Foo,name=Bar`.
  - `d:type=Foo,name=Bar,*` to match names in the domain d that
     have the keys `type=Foo,name=Bar` plus zero or more other keys.
  - `*:type=Foo,name=Bar,*` to match names in any domain that has
     the keys `type=Foo,name=Bar` plus zero or more other keys.
  - `d:type=F?o,name=Bar` will match e.g. `d:type=Foo,name=Bar` and
    `d:type=Fro,name=Bar`.
  - `d:type=F*o,name=Bar` will match e.g. `d:type=Fo,name=Bar` and
    `d:type=Frodo,name=Bar`.
  - `d:type=Foo,name="B*"` will match e.g. `d:type=Foo,name="Bling"`.
     Wildcards are recognized even inside quotes, and like other
     special characters can be escaped with `\`



``` clojure
(μ/start-publisher!
  {:type :mbean
   :mbeans-patterns ["java.lang:type=Memory"
                     "java.nio:*"]})
```

Here an example of the metrics sampled:

``` clojure
{:mulog/event-name :mulog/mbean-sampled,
 :mulog/timestamp 1616332036536,
 :mulog/trace-id #mulog/flake "4atSQ58zWup7bezb1HL7xtgDAdBS24yK",
 :mbean
 {:domain "java.lang",
  :keys {"type" "Memory"},
  :attributes
  {:Verbose false,
   :ObjectPendingFinalizationCount 0,
   :HeapMemoryUsage
   {:committed 473956352,
    :init 268435456,
    :max 4294967296,
    :used 195876944},
   :NonHeapMemoryUsage
   {:committed 178298880, :init 7667712, :max -1, :used 134567328},
   :ObjectName "java.lang:type=Memory"}},
 :search-pattern "java.lang:type=Memory"}

{:mulog/event-name :mulog/mbean-sampled,
 :mulog/timestamp 1616332015292,
 :mulog/trace-id #mulog/flake "4atSOr-sgBAz8nqdgeqwvjDDBuFLnWoJ",
 :mbean
 {:domain "java.nio",
  :keys {"name" "mapped", "type" "BufferPool"},
  :attributes
  {:Name "mapped",
   :Count 0,
   :TotalCapacity 0,
   :MemoryUsed 0,
   :ObjectName "java.nio:name=mapped,type=BufferPool"}},
 :search-pattern "java.nio:*"}

{:mulog/event-name :mulog/mbean-sampled,
 :mulog/timestamp 1616332015292,
 :mulog/trace-id #mulog/flake "4atSOr-uLeb9YPltUhc68_OTpHD0T9uI",
 :mbean
 {:domain "java.nio",
  :keys {"name" "direct", "type" "BufferPool"},
  :attributes
  {:Name "direct",
   :Count 1,
   :TotalCapacity 16383,
   :MemoryUsed 16383,
   :ObjectName "java.nio:name=direct,type=BufferPool"}},
 :search-pattern "java.nio:*"}
```

**NOTE:** *Results will vary depending on JVM used*.
