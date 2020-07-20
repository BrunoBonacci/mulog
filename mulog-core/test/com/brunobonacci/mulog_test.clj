(ns com.brunobonacci.mulog-test
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.test-publisher :as tp]
            [midje.sweet :refer :all]))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                         ----==| μ / L O G |==----                          ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(fact "μ/log can log events just with the event name"

  (tp/with-test-publisher
    (u/log :test))

  => (just
        [(just
           {:mulog/event-name :test
            :mulog/timestamp anything
            :mulog/trace-id anything
            :mulog/namespace (str *ns*)})])
  )



(fact "μ/log can log events with additional properties"

  (tp/with-test-publisher
    (u/log :test :value1 1 :v2 "b" :v3 {:d [:e :f :g]}))

  => (just
        [(contains
           {:mulog/event-name :test
            :value1 1 :v2 "b" :v3 {:d [:e :f :g]}})])
  )



(fact "μ/log adds attributes from global context if set"

  (tp/with-test-publisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :v1 1))

  => (just
        [(contains
           {:mulog/event-name :test :v1 1
            :app "demo" :version 1 :env "local"})])
  )



(fact "global context can be changed, and changes are reflected"

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



(fact "global context can be update, and changes are reflected"

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



(fact "global context can be overwritten by μ/log"

  (tp/with-test-publisher
    (u/set-global-context! {:app "demo" :version 1 :env "local"})
    (u/log :test :app "new-app"))

  => (just
        [(contains
           {:mulog/event-name :test
            :app "new-app" :version 1 :env "local"})])
  )



(fact "local-context: with-context can be used to add local info"

  (tp/with-test-publisher
    (u/with-context {:local 1}
      (u/log :test)))

  => (just
        [(contains
           {:mulog/event-name :test :local 1})])
  )



(fact "local-context: can be nested"

  (tp/with-test-publisher
    (u/with-context {:local 1}
      (u/with-context {:sub-local :a}
        (u/log :test))))

  => (just
        [(contains
           {:mulog/event-name :test :local 1 :sub-local :a})])
  )



(fact "local-context: is only valid within the scope"

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



(fact "local-context: can overwrite parent context"

  (tp/with-test-publisher
    (u/with-context {:local 1}
      (u/with-context {:local 2 :sub-local :a}
        (u/log :test))))

  => (just
        [(contains
           {:mulog/event-name :test :local 2 :sub-local :a})])
  )



(fact "local-context: can overwrite global context"

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



(fact "μ/log: can overwrite local & global context"

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



(fact "μ/log: can overwrite base-event properties"

  (tp/with-test-publisher
    (u/log :test :mulog/timestamp 1 :mulog/namespace "test" :mulog/trace-id "id1"))

  => [#:mulog{:trace-id "id1", :timestamp 1, :event-name :test, :namespace "test"}]
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                       ----==| μ / T R A C E |==----                        ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(fact "μ/trace: body behaviour is unchanged"

  (fact "success case"
    (tp/with-test-publisher
      (u/trace :test
        []
        (Thread/sleep 1)
        :value1)
      => :value1
      ))


  (fact "fail case"
    (tp/with-test-publisher

      (u/trace :test
        []
        (Thread/sleep 1)
        (throw (ex-info "BOOM" {})))
      => (throws Exception "BOOM")
      ))
  )



(fact "μ/trace: track execution duration and outcome"

  (tp/with-test-publisher
    (u/trace :test
      []
      (Thread/sleep 1))
    )

  => (just
        [(just
           {:mulog/event-name   :test
            :mulog/timestamp    anything
            :mulog/trace-id     anything
            ;; no parent trace
            :mulog/parent-trace nil?
            :mulog/root-trace   anything
            :mulog/namespace    (str *ns*)
            :mulog/outcome      :ok
            ;; duration is in nanoseconds
            :mulog/duration     #(and (number? %) (> % 1000000))})])
  )



(fact "μ/trace: track execution duration and outcome (fail case)"

  (tp/with-test-publisher

    (tp/ignore
      (u/trace :test
        []
        (Thread/sleep 1)
        (throw (ex-info "BOOM" {}))))
    )

  => (just
        [(just
           {:mulog/event-name   :test
            :mulog/timestamp    anything
            :mulog/trace-id     anything
            ;; no parent trace
            :mulog/parent-trace nil?
            :mulog/root-trace   anything
            :mulog/namespace    (str *ns*)
            :mulog/outcome      :error
            ;; duration is in nanoseconds
            :mulog/duration     #(and (number? %) (> % 1000000))
            :exception          #(instance? Exception %)})])
  )



(fact "μ/trace: additional pairs can be specified and will be added
       only to the trace (not the inner logs)"

  (tp/with-test-publisher
    (u/trace :test
      [:key1 :value1 :key2 2]

      (u/log :inner :key3 3)
      {:foo "bar"}
      )
    )

  => (just
        [(contains
           {:mulog/event-name :inner
            :key3             3})

         (contains
           {:mulog/event-name :test
            :key1             :value1
            :key2             2})])

  )



(fact "μ/trace: respects local context"

  (tp/with-test-publisher
    (u/with-context {:cntx1 "value1" }
      (u/trace :test
        [:key1 :value1 :key2 2]

        (u/log :inner :key3 3)
        {:foo "bar"}))
    )

  => (just
        [(contains
           {:mulog/event-name :inner
            :key3             3
            :cntx1            "value1"})

         (contains
           {:mulog/event-name :test
            :key1             :value1
            :key2             2
            :cntx1            "value1"})])

  )



(fact "μ/trace: respects global context"

  (tp/with-test-publisher
    (u/set-global-context! {:global 1})
    (u/trace :test
      [:key1 :value1 :key2 2]

      (u/log :inner :key3 3)
      {:foo "bar"})
    )

  => (just
        [(contains
           {:mulog/event-name :inner
            :key3             3
            :global           1})

         (contains
           {:mulog/event-name :test
            :key1             :value1
            :key2             2
            :global           1})])

  )



(fact "μ/trace: nests and inherit parent trace"

  (let [result
        (->>
            (tp/with-test-publisher

              (u/trace :outer
                [:key1 "value1"]

                (u/trace :middle
                  [:key1 "value2"]

                  (u/trace :inner
                    [:key1 "value3"]

                    (u/log :message :key1 "value4")
                    {:foo "bar"}))))
          #_(def result))]

    (count result) => 4
    (mapv :mulog/event-name result) => [:message :inner :middle :outer]
    ;; root trace id are all the same
    (->> (mapv :mulog/root-trace result)
      (apply =)) => true
    ;; all trace id are different
    (->> (mapv :mulog/trace-id result)
      (apply not=)) => true
    ;; the tarce id is the parent-trace for the inner block
    (->> (mapcat (juxt :mulog/trace-id :mulog/parent-trace) result)
      (rest)
      (partition 2)
      (mapv (partial apply =))) => [true true true]

    ))



(fact "μ/trace: can extract values from results, expression body is untouched"

  (fact "success case"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs [:key1 "value1"]
         :capture #(select-keys % [:http-status])}
        {:http-status 200 :body "OK"})
      => {:http-status 200 :body "OK"}
      ))


  (fact "fail case"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs [:key1 "value1"]
         :capture #(select-keys % [:http-status])}
        (throw (ex-info "BOOM" {})))
      => (throws Exception "BOOM")))


  (fact "success case"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs [:key1 "value1"]
         :capture #(throw (ex-info "BOOM" {}))}
        {:http-status 200 :body "OK"})
      => {:http-status 200 :body "OK"}
      ))
  )



(fact "μ/trace: can extract values from results, test extraction"

  (fact "success case"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs   [:key1 "value1"]
         :capture #(select-keys % [:http-status])}
        {:http-status 200 :body "OK"}))
    => (just
          [(contains
             {:mulog/event-name :test
              :key1             "value1"
              :http-status      200})]))


  (fact "fail case"
    (tp/with-test-publisher
      (tp/ignore
        (u/trace :test
          {:pairs   [:key1 "value1"]
           :capture #(select-keys % [:http-status])}
          (throw (ex-info "BOOM" {})))))

    => (just
          [(contains
             {:mulog/event-name :test
              :key1             "value1"
              :mulog/outcome    :error})]))


  (fact "fail case, doesn't contain the extraction"
    (->> (tp/with-test-publisher
         (tp/ignore
           (u/trace :test
             {:pairs   [:key1 "value1"]
              :capture #(select-keys % [:http-status])}
             (throw (ex-info "BOOM" {})))))
      first
      :http-status) => nil)


  (fact "success case but failing extraction"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs   [:key1 "value1"]
         :capture #(throw (ex-info "BOOM" {}))}
        {:http-status 200 :body "OK"}))

    => (just
          [(contains
             {:mulog/event-name :test
              :key1             "value1"
              :mulog/capture    :error})]))


  (fact "extraction can redefine internal properties such as :outcome and :exception"
    (tp/with-test-publisher
      (u/trace :test
        {:pairs   [:key1 "value1"]
         :capture (constantly {:mulog/outcome :error :exception (ex-info "Logical errror" {})})}
        {:http-status 500 :body "some error as a value"}))
    => (just
          [(contains
             {:mulog/event-name :test
              :mulog/outcome    :error
              :exception        (partial instance? Exception)
              :key1             "value1"
              })]))
  )
