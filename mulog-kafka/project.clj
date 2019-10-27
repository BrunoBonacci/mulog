(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-kafka #=(ver)
  :description "FIXME: write description"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.brunobonacci/mulog #=(ver)]
                 [org.apache.kafka/kafka-clients "2.3.0"]
                 [cheshire "5.9.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [org.clojure/test.check "0.10.0"]
                                  [criterium "0.4.5"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.1"]]}}
  )
