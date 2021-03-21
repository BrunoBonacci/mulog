## Multi publisher
![since v0.1.8](https://img.shields.io/badge/since-v0.1.8-brightgreen)

The multi publisher allows you to define multiple publishers
configuration all in one place. It is equivalent to calling
`μ/start-publisher!` on all the individual configurations, it is just
provided for ease of use.

``` clojure
;; it will initialize all the configured publishers
(μ/start-publisher!
 {:type :multi
  :publishers
  [{:type :console}
   {:type :simple-file :filename "/tmp/disk1/mulog/events1.log"}
   {:type :simple-file :filename "/tmp/disk2/mulog/events2.log"}]}))
```

It will initialize all the configured publishers and return a function
with no arguments which when called will stop all the publishers.
