(ns perf
  (:require [com.brunobonacci.mulog :as u]
            [amalloy.ring-buffer :refer [ring-buffer]]
            [criterium.core :refer [bench quick-bench]]))


(comment

  ;; perf tests

  (bench (u/log :test :bechmark "speed"))
  ;;Evaluation count : 65503680 in 60 samples of 1091728 calls.
  ;;Execution time mean : 1.123529 µs
  ;;Execution time std-deviation : 171.427848 ns
  ;;Execution time lower quantile : 918.162591 ns ( 2.5%)
  ;;Execution time upper quantile : 1.499876 µs (97.5%)
  ;;Overhead used : 1.626780 ns


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
