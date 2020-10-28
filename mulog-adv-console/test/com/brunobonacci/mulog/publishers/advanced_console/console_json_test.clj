(ns com.brunobonacci.mulog.publishers.advanced-console.console-json-test
  (:require [midje.sweet :refer :all]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.common.json :as json]))



(defmacro with-console-publisher
  [config & body]
  `(let [pub# (u/start-publisher! ~config)
         out# (new java.io.StringWriter)]
     (try
       ;; with-out-str wouldn't work because publisher runs on a
       ;; different thread
       (with-redefs [*out* out#]
         ~@body
         (Thread/sleep 550)
         (flush)
         (str out#))
       (finally (pub#)))))



(fact "testing console-json publisher valid-json"

  (json/from-json
    (with-console-publisher {:type :console-json}
      (u/log :test :message "encoded" :format :json :vlaue 42 :datetime (java.util.Date. 1603909111875))))

  => (contains
       {:mulog/event-name "test",
        :mulog/namespace "com.brunobonacci.mulog.publishers.advanced-console.console-json-test",
        :message "encoded",
        :format "json",
        :vlaue 42,
        :datetime "2020-10-28T18:18:31.875Z"})
  )



(fact "testing console-json publisher valid-json (even in pretty-print)"

  (json/from-json
    (with-console-publisher {:type :console-json :pretty? true}
      (u/log :test :message "encoded" :format :json :vlaue 42 :datetime (java.util.Date. 1603909111875))))

  => (contains
       {:mulog/event-name "test",
        :mulog/namespace "com.brunobonacci.mulog.publishers.advanced-console.console-json-test",
        :message "encoded",
        :format "json",
        :vlaue 42,
        :datetime "2020-10-28T18:18:31.875Z"})
  )



(fact "testing console-json publisher datetime formatting"

  (with-console-publisher {:type :console-json}
    (u/log :test :message "encoded" :format :json :vlaue 42 :datetime (java.util.Date. 1603909111875)))
  ;; datetimes are formatted to the millis
  => #"\"datetime\":\"2020-10-28T18:18:31.875Z\""
  )
