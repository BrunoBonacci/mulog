(ns perf
  (:require [com.brunobonacci.mulog :as u]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [criterium.core :refer [bench quick-bench]]))


(comment

  ;; perf tests

  (bench (u/log :test :bechmark "speed"))
  ;; Evaluation count : 220888320 in 60 samples of 3681472 calls.
  ;; Execution time mean : 270.882526 ns
  ;; Execution time std-deviation : 3.633504 ns
  ;; Execution time lower quantile : 266.717025 ns ( 2.5%)
  ;; Execution time upper quantile : 279.984914 ns (97.5%)
  ;; Overhead used : 1.571562 ns
  ;;
  ;; Found 2 outliers in 60 samples (3.3333 %)
  ;; low-severe	 2 (3.3333 %)
  ;; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


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
  ;; low-severe	 1 (1.6667 %)
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



  )
