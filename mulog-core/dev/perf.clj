(ns perf
  (:require [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.utils :as ut]
            [com.brunobonacci.mulog.flakes :as f]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [criterium.core :refer [bench quick-bench]]
            [clj-async-profiler.core :as prof]
            [jmh.core :as jmh]
            [clojure.edn :as edn]
            [clojure.string :as str])
  (:import com.brunobonacci.mulog.core.Flake))



(comment
  ;;  Clojure 1.10.1, Java 1.8.0_232

  ;; perf tests
  ;; control
  (bench (reduce + (range 1000))) ;; 3.341261 µs
  (bench (ut/random-uid))  ;; 1.058840 µs


  (bench (u/log :test :bechmark "speed"))
  ;; Evaluation count : 220888320 in 60 samples of 3681472 calls.
  ;; Execution time mean : 270.882526 ns (best 196.138017 ns)
  ;; Execution time std-deviation : 3.633504 ns
  ;; Execution time lower quantile : 266.717025 ns ( 2.5%)
  ;; Execution time upper quantile : 279.984914 ns (97.5%)
  ;; Overhead used : 1.571562 ns
  ;;
  ;; Found 2 outliers in 60 samples (3.3333 %)
  ;; low-severe  2 (3.3333 %)
  ;; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


  (prof/profile
    (bench (u/log :test :bechmark "speed")))


  ;; After enabling the publisher, the performances are unaffected ;-)
  (def pbs
    (u/start-publisher! {:type :simple-file :filename "/tmp/bench/mulog.log"}))
  ;; Evaluation count : 217401360 in 60 samples of 3623356 calls.
  ;; Execution time mean : 278.147233 ns
  ;; Execution time std-deviation : 4.550985 ns
  ;; Execution time lower quantile : 271.739036 ns ( 2.5%)
  ;; Execution time upper quantile : 287.976440 ns (97.5%)
  ;; Overhead used : 1.571562 ns
  ;;
  ;; Found 1 outliers in 60 samples (1.6667 %)
  ;; low-severe  1 (1.6667 %)
  ;; Variance from outliers : 4.2725 % Variance is slightly inflated by outliers


  (def buffer (agent (ring-buffer 10000) :error-mode :continue))
  (bench
    (send buffer
      (fn [buffer]
        (conj buffer
          (assoc {:bechmark "speed"}
            :mulog/timestamp (System/currentTimeMillis)
            :mulog/event-name :test)))))
  ;; Evaluation count : 70285560 in 60 samples of 1171426 calls.
  ;; Execution time mean : 924.824874 ns
  ;; Execution time std-deviation : 48.549956 ns
  ;; Execution time lower quantile : 820.913748 ns ( 2.5%)
  ;; Execution time upper quantile : 1.015406 µs (97.5%)
  ;; Overhead used : 1.860115 ns


  (def buffer (atom (ring-buffer 10000)))
  (bench
    (swap! buffer
      (fn [buffer]
        (conj buffer
          (assoc {:bechmark "speed"}
            :mulog/timestamp (System/currentTimeMillis)
            :mulog/event-name :test)))))
  ;; Evaluation count : 206594940 in 60 samples of 3443249 calls.
  ;; Execution time mean : 285.340496 ns
  ;; Execution time std-deviation : 2.438685 ns
  ;; Execution time lower quantile : 281.985632 ns ( 2.5%)
  ;; Execution time upper quantile : 291.015028 ns (97.5%)
  ;; Overhead used : 1.860115 ns


  (bench
    (u/with-context {:context :v1}
      (u/log :test :bechmark "speed")))
  ;; Execution time mean : 297.677945 ns
  ;; Execution time std-deviation : 11.220127 ns
  ;; Execution time lower quantile : 290.369672 ns ( 2.5%)
  ;; Execution time upper quantile : 324.592734 ns (97.5%)
  ;; Overhead used : 2.100004 ns


  (bench
    (u/with-context {:context :v1}
      (u/with-context {:level :2}
        (u/log :test :bechmark "speed"))))
  ;; Execution time mean : 407.999100 ns
  ;; Execution time std-deviation : 5.069265 ns
  ;; Execution time lower quantile : 403.607057 ns ( 2.5%)
  ;; Execution time upper quantile : 418.810819 ns (97.5%)
  ;; Overhead used : 2.100004 ns


  ;; --------------------------------------
  ;; dynamic bindings are relatively slow
  ;; thread-local variables are much faster
  ;; --------------------------------------
  (def ^:dynamic *b* nil)
  (bench
    (binding [*b* 1]
      (+ 1 *b*)))
  ;; Execution time mean : 386.223452 ns
  ;; Execution time std-deviation : 13.368965 ns
  ;; Execution time lower quantile : 378.564691 ns ( 2.5%)
  ;; Execution time upper quantile : 406.533186 ns (97.5%)
  ;; Overhead used : 6.656971 ns


  (def tl (ut/thread-local nil))
  (bench
    (ut/thread-local-binding [tl 1]
      (+ 1 @tl)))
  ;; Execution time mean : 26.640304 ns
  ;; Execution time std-deviation : 0.383869 ns
  ;; Execution time lower quantile : 26.293054 ns ( 2.5%)
  ;; Execution time upper quantile : 27.107705 ns (97.5%)
  ;; Overhead used : 2.096266 ns
  ;; --------------------------------------



  (bench (do)) ;; 0.72ns
  (bench (u/trace :bench [] (do)))
  ;; Execution time mean : 432.572847 ns
  ;; Execution time std-deviation : 4.696741 ns
  ;; Execution time lower quantile : 428.480115 ns ( 2.5%)
  ;; Execution time upper quantile : 447.053438 ns (97.5%)
  ;; Overhead used : 2.100004 ns


  )



(comment
  ;;
  ;; Various reference measurements for random values
  ;;

  (def t (java.util.Random.))
  (bench (.nextInt ^java.util.Random t)) ;; 10.274500 ns
  (bench (.nextLong ^java.util.Random t));; 20.784382 ns

  (def s (java.security.SecureRandom.))
  (bench (.nextInt ^java.security.SecureRandom s)) ;; 233.216824 ns

  (def tl (java.util.concurrent.ThreadLocalRandom/current))
  (bench (.nextLong ^java.util.Random tl)) ;; 6.528802 ns +/- 0.3

  (bench (java.util.UUID/randomUUID)) ;; 720.110052 ns

  )



(comment
  ;;
  ;; Time retrieval measurements
  ;;

  (bench (System/currentTimeMillis)) ;; 31.014067 ns +/- 1.7
  (bench (System/nanoTime))          ;; 36.411609 ns +/- 1.8

  (import com.brunobonacci.mulog.core.NanoClock)
  (bench (NanoClock/currentTimeNanos))

  ;; Execution time mean : 36.277681 ns
  ;; Execution time std-deviation : 2.084653 ns
  ;; Execution time lower quantile : 33.923126 ns ( 2.5%)
  ;; Execution time upper quantile : 42.149699 ns (97.5%)
  ;; Overhead used : 2.117150 ns
  ;;
  ;; Found 4 outliers in 60 samples (6.6667 %)
  ;; low-severe  4 (6.6667 %)
  ;; Variance from outliers : 43.4030 % Variance is moderately inflated by outliers

  )



(comment
  ;;
  ;; Generate unique IDs
  ;;
  (bench (java.util.UUID/randomUUID)) ;; 720.110052 ns


  (bench (Flake/flake))
  ;; Evaluation count : 1570017720 in 60 samples of 26166962 calls.
  ;; Execution time mean : 36.429623 ns
  ;; Execution time std-deviation : 0.519843 ns
  ;; Execution time lower quantile : 35.684111 ns ( 2.5%)
  ;; Execution time upper quantile : 37.706974 ns (97.5%)
  ;; Overhead used : 2.090673 ns
  ;;
  ;; Found 4 outliers in 60 samples (6.6667 %)
  ;; low-severe  4 (6.6667 %)
  ;; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


  (bench (.toString (Flake/flake)))
  ;; Evaluation count : 755435400 in 60 samples of 12590590 calls.
  ;; Execution time mean : 78.861983 ns
  ;; Execution time std-deviation : 1.006261 ns
  ;; Execution time lower quantile : 77.402593 ns ( 2.5%)
  ;; Execution time upper quantile : 81.006901 ns (97.5%)
  ;; Overhead used : 1.881732 ns


  (bench (Flake/formatFlakeHex (Flake/flake)))
  ;; Evaluation count : 675648240 in 60 samples of 11260804 calls.
  ;; Execution time mean : 88.884251 ns
  ;; Execution time std-deviation : 2.297962 ns
  ;; Execution time lower quantile : 86.277624 ns ( 2.5%)
  ;; Execution time upper quantile : 94.576332 ns (97.5%)
  ;; Overhead used : 2.144793 ns
  ;;
  ;; Found 5 outliers in 60 samples (8.3333 %)
  ;; low-severe  4 (6.6667 %)
  ;; low-mild    1 (1.6667 %)
  ;; Variance from outliers : 12.6407 % Variance is moderately inflated by outliers


  (bench (Flake/parseFlake (.toString (Flake/flake))))
  ;; Evaluation count : 380853420 in 60 samples of 6347557 calls.
  ;;
  ;; Execution time mean : 155.868847 ns (- 78ns => 77s)
  ;; Execution time std-deviation : 16.031191 ns
  ;; Execution time lower quantile : 145.733294 ns ( 2.5%)
  ;; Execution time upper quantile : 186.844744 ns (97.5%)
  ;; Overhead used : 2.116975 ns
  ;;
  ;; Found 3 outliers in 60 samples (5.0000 %)
  ;; low-severe  3 (5.0000 %)
  ;; Variance from outliers : 70.4022 % Variance is severely inflated by outliers


  (bench (.getTimestampNanos (Flake/flake)))
  ;; Evaluation count : 1562994420 in 60 samples of 26049907 calls.
  ;; Execution time mean : 36.792359 ns
  ;; Execution time std-deviation : 1.216609 ns
  ;; Execution time lower quantile : 35.824906 ns ( 2.5%)
  ;; Execution time upper quantile : 38.692940 ns (97.5%)
  ;; Overhead used : 2.090673 ns
  ;;
  ;; Found 5 outliers in 60 samples (8.3333 %)
  ;; low-severe  4 (6.6667 %)
  ;; low-mild    1 (1.6667 %)
  ;; Variance from outliers : 19.0473 % Variance is moderately inflated by outliers


  (bench (.getTimestampMicros (Flake/flake)))
  ;; Evaluation count : 1548419220 in 60 samples of 25806987 calls.
  ;; Execution time mean : 37.306024 ns
  ;; Execution time std-deviation : 1.574925 ns
  ;; Execution time lower quantile : 36.264730 ns ( 2.5%)
  ;; Execution time upper quantile : 41.652258 ns (97.5%)
  ;; Overhead used : 2.090673 ns
  ;;
  ;; Found 6 outliers in 60 samples (10.0000 %)
  ;; low-severe  3 (5.0000 %)
  ;; low-mild    3 (5.0000 %)
  ;; Variance from outliers : 28.6849 % Variance is moderately inflated by outliers


  )



(comment


  (->> (jmh/run
       (merge
         (clojure.edn/read-string (slurp "./perf/benchmarks.edn"))
         {:selectors {:one (comp #(str/includes? % "context") name :fn)}})
       {:type  :quick
        :status true
        :pprint true
        :select :one
        })
    (prn-str)
    (spit (format "./temp/mulog-perf-run-%d.edn" (System/currentTimeMillis))))

  )
