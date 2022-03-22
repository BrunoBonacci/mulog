(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defn ts  [] (System/currentTimeMillis))
(defn jdk [] (clojure.string/replace (str (System/getProperty "java.vm.vendor") "-" (System/getProperty "java.vm.version")) #" " "_"))

(defproject com.brunobonacci/mulog (ver)
  :description "μ/log is a micro-logging library that logs events and data, not words!"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]
  :java-source-paths ["java"]

  :dependencies [[org.clojure/clojure "1.10.3" :scope "provided"]
                 [amalloy/ring-buffer "1.3.1"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:source-paths ["perf"]
                   :dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [com.clojure-goes-fast/clj-async-profiler "0.5.0"]
                                  [jmh-clojure "0.4.0"]]
                   :jvm-opts ["-server" "-Djdk.attach.allowAttachSelf"]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]
                                  [lein-jmh "0.2.8"]]}

             ;; compatibility with 1.8+ for the core.
             :1.8    {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9    {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10.3 {:dependencies [[org.clojure/clojure "1.10.3"]]}
             :1.11.0 {:dependencies [[org.clojure/clojure "1.11.0-rc1"]]}}

  :auto    {"javac" {:file-pattern #"\.java$"}}

  :aliases
  {"test"
   ["with-profile" "+1.8:+1.9:+1.10.3:+1.11.0" "midje"]

   "perf-quick"
   ["with-profile" "dev" "jmh"
    #=(pr-str {:file "./perf/benchmarks.edn"
               :status true :pprint true :format :table
               :fork 1 :measurement 5
               :output #=(clojure.string/join "-" ["./mulog" #=(ver) #=(jdk) #=(ts) "results.edn"])})]

   "perf"
   ["with-profile" "dev" "jmh"
    #=(pr-str {:file "./perf/benchmarks.edn"
               :status true :pprint true :format :table
               :output #=(clojure.string/join "-" ["./mulog" #=(ver) #=(jdk) #=(ts) "results.edn"])})]}
  )
