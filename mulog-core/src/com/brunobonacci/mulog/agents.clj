(ns com.brunobonacci.mulog.agents
  (:require [com.brunobonacci.mulog.buffer
             :refer [ring-buffer] :as rb])
  (:import [java.util.concurrent ScheduledThreadPoolExecutor
            TimeUnit ScheduledFuture Future ThreadFactory]))



(defn scheduled-thread-pool
  [core-pool-size]
  (ScheduledThreadPoolExecutor.
   ^int core-pool-size
   ^ThreadFactory
   (reify ThreadFactory
     (^Thread newThread [this ^Runnable r]
       (let [t (Thread. r)]
         (.setName   t (str "mu/log-task-" (.getId t)))
         (.setDaemon t true)
         t)))))



(def timer-pool
  (scheduled-thread-pool 2))



(defn recurring-task
  [delay-millis task]
  (let [^ScheduledFuture ftask
        (.scheduleAtFixedRate
         ^ScheduledThreadPoolExecutor timer-pool
         (fn [] (try (task) (catch Exception x))) ;; TODO log
         delay-millis delay-millis TimeUnit/MILLISECONDS)]
    (fn [] (.cancel ftask true))))



(defn buffer-agent
  [capacity]
  (agent (rb/ring-buffer capacity)
         :error-mode :continue))
