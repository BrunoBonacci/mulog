(defn ver [] (-> "../../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/roads-disruptions #=(ver)
  :description "A small webservice which return live road disruption data to showcase the use of μ/log"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.3"]
                 [metosin/jsonista "0.3.1"]
                 [cheshire "5.10.0"]
                 [compojure "1.6.2"]
                 [ring/ring-jetty-adapter "1.9.2"]
                 [ring/ring-core "1.9.2"]
                 [ring/ring-json "0.5.0"]
                 [clj-http "3.12.1"]
                 [com.cemerick/url "0.1.1"]
                 [com.brunobonacci/safely "0.7.0-alpha1"]
                 [org.clojure/tools.logging "1.1.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-elasticsearch #=(ver)]
                 [com.brunobonacci/mulog-kafka #=(ver)]
                 [com.brunobonacci/mulog-zipkin #=(ver)]
                 ;;[com.brunobonacci/mulog-prometheus #=(ver)]
                 ;;[com.brunobonacci/mulog-slack #=(ver)]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]]

  :main com.brunobonacci.disruptions.main

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  )
