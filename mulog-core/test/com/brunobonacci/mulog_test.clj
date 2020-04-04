(ns com.brunobonacci.mulog-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.test-publisher :as tp]
            [midje.sweet :refer :all]))



(fact
 "μ/log can log events just with the event name"

 (tp/with-test-publisher
   (u/log :test))

 => (just
    [(just
      {:mulog/event-name :test
       :mulog/timestamp anything
       :mulog/namespace (str *ns*)})])
 )



(fact
 "μ/log can log events with additional properties"

 (tp/with-test-publisher
   (u/log :test :value1 1 :v2 "b" :v3 {:d [:e :f :g]}))

 => (just
    [(contains
      {:mulog/event-name :test
       :value1 1 :v2 "b" :v3 {:d [:e :f :g]}})])
 )



(fact
 "μ/log adds attributes from global context if set"

 (tp/with-test-publisher
   (u/set-global-context! {:app "demo" :version 1 :env "local"})
   (u/log :test :v1 1))

 => (just
    [(contains
      {:mulog/event-name :test :v1 1
       :app "demo" :version 1 :env "local"})])
 )



(fact
 "global context can be changed, and changes are reflected"

 (tp/with-test-publisher
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

 (tp/with-test-publisher
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

 (tp/with-test-publisher
   (u/set-global-context! {:app "demo" :version 1 :env "local"})
   (u/log :test :app "new-app"))

 => (just
    [(contains
      {:mulog/event-name :test
       :app "new-app" :version 1 :env "local"})])
 )



(fact
 "local-context: with-context can be used to add local info"

 (tp/with-test-publisher
   (u/with-context {:local 1}
     (u/log :test)))

 => (just
    [(contains
      {:mulog/event-name :test :local 1})])
 )



(fact
 "local-context: can be nested"

 (tp/with-test-publisher
   (u/with-context {:local 1}
     (u/with-context {:sub-local :a}
       (u/log :test))))

 => (just
    [(contains
      {:mulog/event-name :test :local 1 :sub-local :a})])
 )



(fact
 "local-context: is only valid within the scope"

 (tp/with-test-publisher
   (u/log :before :l 0)
   (u/with-context {:local 1}
     (u/log :ctx :l 1)
     (u/with-context {:sub-local :a}
       (u/log :ctx :l 2))
     (u/log :ctx :l 1 :out true))
   (u/log :after :l 0))

 => (just
    [(contains
      {:mulog/event-name :before :l 0})
     (contains
      {:mulog/event-name :ctx :l 1 :local 1})
     (contains
      {:mulog/event-name :ctx :l 2 :local 1 :sub-local :a})
     (contains
      {:mulog/event-name :ctx :l 1 :local 1 :out true})
     (contains
      {:mulog/event-name :after :l 0})])
 )



(fact
 "local-context: can overwrite parent context"

 (tp/with-test-publisher
   (u/with-context {:local 1}
     (u/with-context {:local 2 :sub-local :a}
       (u/log :test))))

 => (just
    [(contains
      {:mulog/event-name :test :local 2 :sub-local :a})])
 )



(fact
 "local-context: can overwrite global context"

 (tp/with-test-publisher
   (u/set-global-context! {:global 1})
   (u/log :test)
   (u/with-context {:global 2 :local :a}
     (u/log :test)))

 => (just
    [(contains
      {:mulog/event-name :test :global 1})
     (contains
      {:mulog/event-name :test :global 2 :local :a})])
 )



(fact
 "μ/log: can overwrite local & global context"

 (tp/with-test-publisher
   (u/set-global-context! {:global 1})
   (u/log :test :global :overwrite)
   (u/with-context {:global 2 :local :a}
     (u/log :test :global :overwrite2 :local :overwrite2)))

 => (just
    [(contains
      {:mulog/event-name :test :global :overwrite})
     (contains
      {:mulog/event-name :test :global :overwrite2 :local :overwrite2})])
 )



(fact
 "μ/log: can overwrite timestamp and namespace"

 (tp/with-test-publisher
   (u/log :test :mulog/timestamp 1 :mulog/namespace "test"))

 => [#:mulog{:timestamp 1, :event-name :test, :namespace "test"}]
 )
