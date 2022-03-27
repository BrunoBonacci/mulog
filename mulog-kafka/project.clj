(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-kafka #=(ver)
  :description "A publisher for μ/log to Kafka."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]
                 [org.apache.kafka/kafka-clients "2.7.0"]
                 [com.taoensso/nippy "3.1.1"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [org.slf4j/slf4j-log4j12 "1.7.30"]

                                  ; Clojure Kafka client
                                  [com.appsflyer/ketu "0.6.0"]

                                  ; Kafka docker-in-docker
                                  [org.testcontainers/kafka "1.16.3"]
                                  [clj-test-containers "0.5.0"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"}
  )
