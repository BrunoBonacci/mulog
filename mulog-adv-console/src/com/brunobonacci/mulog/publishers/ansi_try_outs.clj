(ns com.brunobonacci.mulog.publishers.ansi-try-outs
  (:require [com.brunobonacci.mulog.publisher :as p]
            [com.brunobonacci.mulog :as μ]
   [where.core :refer [where]]))

(def formats {:http-format         {:event :yellow}
            :event-format          {:event :green}
            :http-error-format     {:pair {:http-error :red}}
            :override-pair-format  {:pair {:http-error :blue}}
            :birght-pair-format {:pair {:http-error [:white :bright]}}
            :default-formatter     :magenta})

(def rules [(where :mulog/event-name :is? :line-test) :event-format
            (where contains? :http-test) :http-format
            (where contains? :http-error) :http-error-format
            (where :http-error :is? 500) :override-pair-format
            (where :http-error :is? 503) :bright-pair-format])

(comment (def pub (μ/start-publisher!
                   {:type :ansi-console
                    :formats formats
                    :rules rules
                    :pretty? true}))

         (μ/log :line-test)

         (μ/log :line-test 
                :http-test "something"
                :http-error 404)
         (μ/log :line-test
                :http-test "something"
                :http-error 500)
         (μ/log :line-test
                :http-test "something"
                :http-error 503)

         (pub))