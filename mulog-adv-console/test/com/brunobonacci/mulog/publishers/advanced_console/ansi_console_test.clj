(ns com.brunobonacci.mulog.publishers.advanced-console.ansi-console-test
  (:require [midje.sweet :refer [fact =>]]
            [com.brunobonacci.mulog.publishers.ansi-console
             :refer [pick-entry-format find-pair-formats]]
            [where.core :refer [where]]))

(fact "entry matcher selects nil when formats are not setup"
      (let [entry {:mulog/trace-id "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  [(where :mulog/event-name :is? :line-test) :event-format]
            formats {}]
        (pick-entry-format entry rules formats) => nil))

(fact "entry matcher selects nil when there are no rules"
      (let [entry {:mulog/trace-id "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  []
            formats {}]
        (pick-entry-format entry rules formats) => nil))

(fact "entry matcher selects the default formatter when no rules match for entry"
      (let [entry {:mulog/trace-id "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :some-random-value
                   :mulog/namespace "ansi-console-test"}
            rules  [(where :mulog/event-name :is? :line-test) :event-format]
            formats {:event-format          {:event :green}
                     :default-formatter      :magenta}]
        (pick-entry-format entry rules formats) => :magenta))

(fact "entry picker selects the last entry format in which matches the rules in the order they apply"
      (let [entry {:mulog/trace-id "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
                   :mulog/timestamp 1615129101667
                   :mulog/event-name :line-test
                   :mulog/namespace "ansi-console-test"}
            entry-with-more-specific-rules 
            {:mulog/trace-id "4acMM4C1AMa4u6YoHDxKsBsLLs7EMhZi"
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