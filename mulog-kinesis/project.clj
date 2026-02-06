(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-kinesis #=(ver)
  :description "A publisher for μ/log to Kinesis."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]

                 [com.cognitect.aws/api "0.8.711"]
                 [com.cognitect.aws/endpoints "1.1.12.772"]
                 [com.cognitect.aws/kinesis "869.2.1687.0"]
                 [com.cognitect.aws/sts "857.2.1574.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[com.brunobonacci/rdt "0.5.0-alpha6"]
                                  [com.brunobonacci/where "0.5.6"]
                                  [clj-test-containers "0.7.4"]
                                  ;; clj-test-containers is outdated.
                                  [org.testcontainers/testcontainers "1.21.4"]
                                  [org.apache.logging.log4j/log4j-slf4j-impl "2.20.0"]]
                   :resource-paths ["dev-resources"]
                   :main com.brunobonacci.rdt.runner
                   :plugins      [[lein-shell "0.5.0"]]}}

  :aliases {"test" "run"}
  )
