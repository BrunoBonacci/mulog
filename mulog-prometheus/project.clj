(defproject com.brunobonacci/mulog-prometheus "0.5.0-SNAPSHOT"
  :description "A publisher for μ/log to Prometheus."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.brunobonacci/mulog "0.5.0-SNAPSHOT"]
                 [io.prometheus/simpleclient             "0.9.0"]
                 [io.prometheus/simpleclient_common      "0.9.0"]
                 [io.prometheus/simpleclient_pushgateway "0.9.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"})
