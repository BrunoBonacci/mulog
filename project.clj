(defproject com.brunobonacci/mulog "0.1.0-SNAPSHOT"
  :description "μ/log is a micro-logging library that logs data, not words!"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [amalloy/ring-buffer "1.3.0"
                  :exclusions [[org.clojure/clojurescript]]]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [org.clojure/test.check "0.10.0"]
                                  [criterium "0.4.5"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.1"]]}}
  )
