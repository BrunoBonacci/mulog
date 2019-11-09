(ns com.brunobonacci.mulog-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.test-publisher :as tp]
            [midje.sweet :refer :all]))

(facts
 "about: μ/log"

 (fact
  "μ/log can log events just with the event name"

  (tp/with-test-pusblisher
    (u/log :test))

  => (just
     [(just
       {:mulog/event-name :test
        :mulog/timestamp anything
        :mulog/namespace (str *ns*)})])
  )



 (fact
  "μ/log can log events with additional properties"

  (tp/with-test-pusblisher
    (u/log :test :value1 1 :v2 "b" :v3 {:d [:e :f :g]}))

  => (just
     [(contains
       {:mulog/event-name :test
        :value1 1 :v2 "b" :v3 {:d [:e :f :g]}})])
  )



 (fact
  "μ/log adds attributes from global context if set"

  (tp/with-test-pusblisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :v1 1))

  => (just
     [(contains
       {:mulog/event-name :test :v1 1
        :app "demo" :version 1 :env "local"})])
  )



 (fact
  "global context can be changed, and changes are reflected"

  (tp/with-test-pusblisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :v1 1)
    (u/set-global-context! {:app "demo" :version 2 :env "local"})
    (u/log :test :v1 2))

  => (just
     [(contains
       {:mulog/event-name :test :v1 1
        :app "demo" :version 1 :env "local"})
      (contains
       {:mulog/event-name :test :v1 2
        :app "demo" :version 2 :env "local"})])
  )



 (fact
  "global context can be update, and changes are reflected"

  (tp/with-test-pusblisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :v1 1)
    (u/update-global-context! update :version inc)
    (u/log :test :v1 2))

  => (just
     [(contains
       {:mulog/event-name :test :v1 1
        :app "demo" :version 1 :env "local"})
      (contains
       {:mulog/event-name :test :v1 2
        :app "demo" :version 2 :env "local"})])
  )


 (fact
  "global context can be overwritten by μ/log"

  (tp/with-test-pusblisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :app "new-app"))

  => (just
     [(contains
       {:mulog/event-name :test
        :app "new-app" :version 1 :env "local"})])
  )
 )
