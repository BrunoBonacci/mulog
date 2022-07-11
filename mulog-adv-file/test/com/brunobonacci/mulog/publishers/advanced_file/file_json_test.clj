(ns com.brunobonacci.mulog.publishers.advanced-file.file-json-test
  (:require [midje.sweet :refer :all]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.common.json :as json]))



(defmacro with-file-publisher
  [config & body]
  `(let [out# (new java.io.StringWriter)
         pub# (u/start-publisher! (assoc ~config :filename out#))]
     (try
       ~@body
       (Thread/sleep 550)
       (.flush out#)
       (str out#)
       (finally (pub#)))))



(fact "testing file-json publisher valid-json"

  (json/from-json
    (with-file-publisher {:type :file-json}
      (u/log :test :message "encoded" :format :json :value 42 :datetime (java.util.Date. 1603909111875))))

  => (contains
       {:mulog/event-name "test",
        :mulog/namespace "com.brunobonacci.mulog.publishers.advanced-file.file-json-test",
        :message "encoded",
        :format "json",
        :value 42,
        :datetime "2020-10-28T18:18:31.875Z"}))
