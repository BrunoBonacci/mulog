(ns com.brunobonacci.mulog.publishers.elasticsearch-util-test
  (:require [com.brunobonacci.mulog.publishers.elasticsearch.util :as ut]
            [com.brunobonacci.rdt :refer [repl-test]]))



(repl-test "mangling of simple types"

  (ut/mangle-map
    {:keyword :test
     :symbol  'symbol
     :string  "bar"
     :integer 23
     :double  23.3
     :float   (float 23.3)
     :boolean true})

  => {"keyword.k" :test,
     "symbol.k" "symbol",
     "string.s" "bar",
     "integer.i" 23,
     "double.f" 23.3,
     "float.f" (float 23.3),
     "boolean.b" true}

  )



(repl-test "mangling of dates types"

  (ut/mangle-map
    {:datetime (java.util.Date. 1737645334431)})

  => {"datetime.t" #inst "2025-01-23T15:15:34.431-00:00"}

  )



(repl-test "mangling of exception types"

  (ut/mangle-map
    {:exception (ex-info "error" {:foo :bar})})

  => {"exception.x" string?}

  )



(repl-test "mangling of collection types"

  (ut/mangle-map
    {:map    {:foo 1 :bar 2}
     :vector [1 "2" true {:foo 1 :bar 2} [1]]
     :list   '(1 "2" true {:foo 1 :bar 2} [1])
     :set    #{1 "2" true {:foo 1 :bar 2} [1]}})

  => {"map.o" {"foo.i" 1, "bar.i" 2},
     "vector.a"
     [{"_aVal.i" 1}
      {"_aVal.s" "2"}
      {"_aVal.b" true}
      {"_aVal.o" {"foo.i" 1, "bar.i" 2}}
      {"_aVal.a" [{"_aVal.i" 1}]}],
     "list.a"
     [{"_aVal.i" 1}
      {"_aVal.s" "2"}
      {"_aVal.b" true}
      {"_aVal.o" {"foo.i" 1, "bar.i" 2}}
      {"_aVal.a" [{"_aVal.i" 1}]}],
     "set.a"
     [{"_aVal.i" 1}
      {"_aVal.o" {"foo.i" 1, "bar.i" 2}}
      {"_aVal.b" true}
      {"_aVal.s" "2"}
      {"_aVal.a" [{"_aVal.i" 1}]}]}

  )



(repl-test "mangling of arrays with nil types"

  (ut/mangle-map
    {:vector [1 nil "2"]})

  => {"vector.a" [{"_aVal.i" 1} {"_aVal" nil} {"_aVal.s" "2"}]}


  )
