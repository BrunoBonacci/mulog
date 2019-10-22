(ns perf
  (:require [com.brunobonacci.mulog :as u]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [criterium.core :refer [bench quick-bench]]))


(comment

  ;; perf tests

  (bench (u/log :test :bechmark "speed"))
  ;; Evaluation count : 120009600 in 60 samples of 2000160 calls.
  ;; Execution time mean : 505.318089 ns
  ;; Execution time std-deviation : 5.400954 ns
  ;; Execution time lower quantile : 499.397866 ns ( 2.5%)
  ;; Execution time upper quantile : 518.259399 ns (97.5%)
  ;; Overhead used : 1.872577 ns
  ;;
  ;; Found 5 outliers in 60 samples (8.3333 %)
  ;; low-severe	 4 (6.6667 %)
  ;; low-mild	 1 (1.6667 %)
  ;; Variance from outliers : 1.6389 % Variance is slightly inflated by outliers


  (def pbs
    (u/start-publisher! {:type :simple-file :filename "/tmp/mulog.log"}))



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
