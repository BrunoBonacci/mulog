(ns com.brunobonacci.mulog.publishers.advanced-console.ansi-console-test
  (:require [midje.sweet :refer [fact =>]]
            [midje.util :refer [testable-privates]]
            [com.brunobonacci.mulog.publishers.ansi-console :as adv]
            [where.core :refer [where]]))



;; importing private functions
(testable-privates
 com.brunobonacci.mulog.publishers.ansi-console
 pick-entry-format find-pair-formats)



(fact "entry matcher selects nil when formats are not setup"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  [(where :mulog/event-name :is? :line-test) :event-format]
            formats {}]
        (pick-entry-format entry rules formats) => nil))



(fact "entry matcher selects nil when there are no rules"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  []
            formats {}]
        (pick-entry-format entry rules formats) => nil))



(fact "entry matcher selects the default formatter when no rules match for entry"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  [(where :mulog/event-name :is? :line-test) :event-format]
            formats {:event-format          {:event :green}
                     :default-formatter      :magenta}]
        (pick-entry-format entry rules formats) => :magenta))



(fact "entry picker selects the last entry format in which matches the rules in the order they apply"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :line-test
                   :mulog/namespace "ansi-console-test"
                   :some-value 40}

            rules  [(where :mulog/event-name :is? :line-test) :event-format
                    (where [:and
                            [:some-value :is-not? nil]
                            [:some-value >= 20]]) :more-specific-format]

            formats {:event-format          {:event :green}
                     :more-specific-format  {:event :blue}
                     :default-formatter     :magenta}]
        (pick-entry-format entry rules formats) => :blue))



(fact "entry picker selects the last entry format in which matches the rules in the order they apply"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :line-test
                   :mulog/namespace "ansi-console-test"}
            entry-with-more-specific-rules
            {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
             :mulog/timestamp 1615129101667
             :mulog/event-name :line-test
             :key-for-more-specific-matching :some-value
             :mulog/namespace "ansi-console-test"}
            rules  [(where :mulog/event-name :is? :line-test) :event-format
                    (where contains? :key-for-more-specific-matching) :more-specific-format]
            formats {:event-format          {:event :green}
                     :more-specific-format  {:event :blue}
                     :default-formatter     :magenta}]
        (pick-entry-format entry rules formats) => :green
        (pick-entry-format entry-with-more-specific-rules rules formats) => :blue))


(fact "pair picker selects a pair format in which matches the rule"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :line-test
                   :http-error 404
                   :mulog/namespace "ansi-console-test"
                   :some-value 40}

            rules  [(where :mulog/event-name :is? :line-test) :event-format
                    (where :http-error :is-not? nil) :pair-format]

            formats {:event-format          {:event :green}
                     :pair-format           {:pair {:http-error :red}}
                     :default-formatter     :magenta}]
        (pick-entry-format entry rules formats) => :green
        (find-pair-formats entry rules formats) => {:http-error :red}))

(fact "pair picker selects the last format matching when there is more than one rule matching"
      (let [entry {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :line-test
                   :http-error 404
                   :mulog/namespace "ansi-console-test"}
            
            entry-with-pair-override
            {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
             :mulog/timestamp 1615129101667
             :mulog/event-name :line-test
             :http-error 500
             :mulog/namespace "ansi-console-test"
             :some-value 40}
            
            entry-with-deeper-entry-and-pair-override
            {:mulog/trace-id #mulog/flake "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
             :mulog/timestamp 1615129101667
             :mulog/event-name :line-test
             :http-error 503
             :mulog/namespace "ansi-console-test"
             :some-value 40}

            rules  [(where :mulog/event-name :is? :line-test) :event-format
                    (where :http-error :is-not? nil) :pair-format
                    (where [:and
                            [:some-value :is-not? nil]
                            [:some-value >= 20]]) :second-event-format
                    (where :http-error :is? 500) :override-pair-format
                    (where :http-error :is? 503) :bright-pair-format
                    ]

            formats {:event-format          {:event :green}
                     :second-event-format   {:event :yellow}
                     :pair-format           {:pair {:http-error :red}}
                     :override-pair-format  {:pair {:http-error :blue}}
                     :bright-pair-format    {:pair {:http-error [:white :bright]}}
                     :default-formatter     :magenta}]
        (pick-entry-format entry rules formats) => :green
        (find-pair-formats entry rules formats) => {:http-error :red}
        
        (pick-entry-format entry-with-pair-override rules formats) => :yellow
        (find-pair-formats entry-with-pair-override rules formats) => {:http-error :blue}
        
        (pick-entry-format entry-with-deeper-entry-and-pair-override rules formats) => :yellow
        (find-pair-formats entry-with-deeper-entry-and-pair-override rules formats) => {:http-error [:white :bright]}
        ))