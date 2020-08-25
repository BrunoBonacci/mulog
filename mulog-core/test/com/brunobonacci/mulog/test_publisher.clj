(ns com.brunobonacci.mulog.test-publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.core :as uc]
            [com.brunobonacci.mulog.utils :as ut]))



(deftype TestPublisher [buffer delivery-buffer process]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_] buffer)

  (publish-delay [_] 200)

  (publish [_ buffer]
    (->> buffer
      rb/items
      (map second)
      (process)
      (swap! delivery-buffer into))
    (rb/clear buffer)))



(defn test-publisher
  ([delivery-buffer]
   (test-publisher delivery-buffer identity))
  ([delivery-buffer process]
   (TestPublisher. (rb/agent-buffer 2000) delivery-buffer process)))



(defn wait-until
  "it waits for test* (thunk) to return true or for `or-at-most` millis to have passed."
  [test* or-at-most]
  (let [start (System/currentTimeMillis)]
    (loop []
      (when-not (or (test*)
                  (> (- (System/currentTimeMillis) start) or-at-most))
        (Thread/sleep (/ or-at-most 5))
        (recur)))))



(defmacro with-processing-publisher
  [config & body]
  `(let [cfg#     (merge {:process identity :rounds 1} ~config)
         inbox#   (atom (rb/ring-buffer 100))
         outbox#  (atom [])
         flush#   (atom 0)
         w#       (add-watch outbox# :flush
                    (fn [_# _# o# n#]
                      (when (< (count o#) (count n#))
                        (swap! flush# inc))))
         gbc#     @com.brunobonacci.mulog.core/global-context
         _#       (reset! com.brunobonacci.mulog.core/global-context {})
         tp#      (test-publisher outbox# (:process cfg#))
         sp#      (uc/start-publisher! inbox# {:type :inline :publisher tp#})]

     (binding [com.brunobonacci.mulog.core/*default-logger* inbox#]
       ~@body)

     (reset! com.brunobonacci.mulog.core/global-context gbc#)
     ;; wait for the publisher to deliver the events
     (wait-until
       (fn [] (> @flush# 0))
       (* (inc (:rounds cfg#))
         uc/PUBLISH-INTERVAL))
     ;; stop the publisher
     (sp#)
     @outbox#))



(defmacro with-test-publisher
  ""
  {:style/indent 0}
  [& body]
  `(with-processing-publisher {} ~@body))



(defn rounds
  "ex `(rounds [:ok :fail :ok])` produces a function which when called
  with a non empty collection, the first time will do nothing, the
  second time will throw an exception the third time will be fine (and
  any subsequent call)"
  [spec]
  (let [round (volatile! spec)]
    (fn [recs]
      (when (seq recs)
        (if (= :fail (first @round))
          (do
            (vswap! round rest)
            (throw (ex-info "boom!!!" {})))
          (vswap! round rest)))
      recs)))



(defmacro ignore
  [& body]
  `(try
     ~@body
     (catch Exception x#
       x#)))
