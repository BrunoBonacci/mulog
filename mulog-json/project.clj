(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/mulog-json #=(ver)
  :description "μ/log json handling"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.12.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.cnuernber/charred "1.034"]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  :profiles {:dev {:dependencies [[midje "1.10.10"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"}
  )
