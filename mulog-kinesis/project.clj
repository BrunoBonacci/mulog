(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-kinesis #=(ver)
  :description "A publisher for μ/log to Kinesis."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]
                 [clj-http "3.12.1"]

                 [com.cognitect.aws/api "0.8.505"]
                 [com.cognitect.aws/endpoints "1.1.11.976"]
                 [com.cognitect.aws/kinesis "809.2.784.0"]
                 [com.cognitect.aws/sts "809.2.784.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]
                                  [lein-shell "0.5.0"]]}}

  :aliases {"test" ["do"
                    ["shell" "docker-compose" "up" "-d"]
                    ["shell" "../scripts/wait_for.sh" "Localstack" "localhost" "4566"]
                    ["midje"]
                    ["shell" "docker-compose" "kill"]
                    ["shell" "docker-compose" "rm" "-f"]]}
  )
