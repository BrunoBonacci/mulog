(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-adv-console #=(ver)
  :description "A publisher for μ/log to Console and Terminal."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-json #=(ver)]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"}
  )
