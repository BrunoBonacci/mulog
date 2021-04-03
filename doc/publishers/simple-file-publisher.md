## Simple file publisher
![since v0.1.0](https://img.shields.io/badge/since-v0.1.0-brightgreen)

It is bundled with the core module, no extra dependencies required.
It sends the output of each log into a file in EDN format.

The available configuration options:

``` clojure
{:type :simple-file

 ;; the name of the file, including the path, where the logs will be written
 ;; If the directory doesn't exists, it will try to create them, same for the file.
 ;; If the file already exists, it will append the new events.
 :filename "/tmp/mulog/events.log"

 ;; a function to apply to the sequence of events before publishing.
 ;; This transformation function can be used to filter, tranform,
 ;; anonymise events before they are published to a external system.
 ;; by defatult there is no transformation.  (since v0.1.8)
 :transform identity
 }

```

How to use it:

``` clojure
(μ/start-publisher! {:type :simple-file :filename "/tmp/mulog/events.log"})
```
