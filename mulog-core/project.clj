(defproject com.brunobonacci/mulog (-> "../ver/mulog.version" slurp .trim)
  :description "μ/log is a micro-logging library that logs events and data, not words!"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :java-source-paths ["java"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [amalloy/ring-buffer "1.3.1"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:source-paths ["perf"]
                   :dependencies [[midje "1.9.9"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [com.clojure-goes-fast/clj-async-profiler "0.4.1"]
                                  [jmh-clojure "0.3.1"]]
                   :jvm-opts ["-server" "-Djdk.attach.allowAttachSelf"]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]
                                  [lein-jmh "0.2.8"]]}}

  :auto    {"javac" {:file-pattern #"\.java$"}}

  :aliases {"test" "midje"
            "perf" ["with-profile" "dev" "jmh" #=(pr-str {:file "./perf/benchmarks.edn" :status true :pprint true})]}
  )
