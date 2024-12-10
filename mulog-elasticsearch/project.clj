(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-elasticsearch #=(ver)
  :description "A publisher for μ/log to Elasticsearch."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]
                 [clj-http "3.13.0"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[com.brunobonacci/rdt "0.5.0-alpha6"]
                                  [clj-test-containers "0.7.4"]
                                  [org.slf4j/slf4j-log4j12 "2.0.16"]]
                   :resource-paths ["dev-resources"]
                   :main com.brunobonacci.rdt.runner
                   :plugins      []}}

  :aliases {"test" "run"}
  )
