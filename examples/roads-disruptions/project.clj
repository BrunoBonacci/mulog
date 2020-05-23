(defn ver [] (-> "../../ver/mulog.version" slurp .trim))
(defproject com.brunobonacci/roads-disruptions "0.1.0-SNAPSHOT"
  :description "A small webservice which return live road disruption data to showcase the use of μ/log"

  :url "https://github.com/BrunoBonacci/mulog"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/mulog.git"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire "5.10.0"]
                 [compojure "1.6.1"]
                 [ring/ring-jetty-adapter "1.8.1"]
                 [ring/ring-core "1.8.1"]
                 [ring/ring-json "0.5.0"]
                 [clj-http "3.10.1"]
                 [com.cemerick/url "0.1.1"]
                 [com.brunobonacci/safely "0.5.0"]
                 [org.clojure/tools.logging "1.1.0"]
                 [com.brunobonacci/mulog #=(ver)]
                 [com.brunobonacci/mulog-elasticsearch #=(ver)]
                 [com.brunobonacci/mulog-kafka #=(ver)]
                 [com.brunobonacci/mulog-zipkin #=(ver)]
                 [com.brunobonacci/mulog-slack #=(ver)]
                 [org.slf4j/slf4j-log4j12 "1.7.30"]]

  :main com.brunobonacci.disruptions.main

  :global-vars {*warn-on-reflection* true}

  :jvm-opts ["-server"]

  )
