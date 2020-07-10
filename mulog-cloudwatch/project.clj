(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-cloudwatch #=(ver)
  :description "A publisher for μ/log to Cloudwatch."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]
                 [com.cognitect.aws/api "0.8.456"]
                 [com.cognitect.aws/endpoints "1.1.11.789"]
                 [com.cognitect.aws/logs "798.2.672.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [org.clojure/test.check "1.0.0"]
                                  [criterium "0.4.5"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]
                                  [lein-shell "0.5.0"]]}}

  :aliases {"test" ["do"
                    ["shell" "docker-compose" "up" "-d"]
                    ["shell" "sleep" "5"]
                    ["midje"]
                    ["shell" "docker-compose" "kill"]
                    ["shell" "docker-compose" "rm" "-f"]]}
  )