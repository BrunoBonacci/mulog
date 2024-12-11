(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-kafka #=(ver)
  :description "A publisher for μ/log to Kafka."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]
                 [org.apache.kafka/kafka-clients "3.9.0"]
                 [com.taoensso/nippy "3.4.2"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.10.10"]
                                  [org.slf4j/slf4j-log4j12 "2.0.16"]

                                  ; Clojure Kafka client
                                  [com.appsflyer/ketu "2.0.0"]

                                  ; Kafka docker-in-docker
                                  [org.testcontainers/kafka "1.20.4"]
                                  [clj-test-containers "0.7.4"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"}
  )
