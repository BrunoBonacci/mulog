(defn ver [] (-> "../ver/mulog.version" slurp .trim))
(defn java-version
  "It returns the current Java major version as a number"
  []
  (as->  (System/getProperty "java.specification.version") $
    (str/split $ #"[^\d]")
    (if (= "1" (first $)) (second $) (first $))
    (Long/parseLong $)))

(defproject com.brunobonacci/mulog-filesystem-metrics #=(ver)
  :description "A filesystem metrics sampler for μ/log."

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [com.brunobonacci/mulog #=(ver)]]

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ~(if (>= (java-version) 9)
               ;; required due to reflection used in
               ;; `com.brunobonacci.mulog.publishers.filesystem-metrics/store-hacks`
               (vector "--add-opens" "java.base/sun.nio.fs=ALL-UNNAMED" "-server")
               (vector "-server"))


  :profiles {:dev {:dependencies [[midje "1.9.10"]
                                  [org.clojure/test.check "1.1.0"]
                                  [criterium "0.4.6"]
                                  [org.slf4j/slf4j-log4j12 "2.0.0-alpha1"]]
                   :resource-paths ["dev-resources"]
                   :plugins      [[lein-midje "3.2.2"]]}}

  :aliases {"test" "midje"}
  )
