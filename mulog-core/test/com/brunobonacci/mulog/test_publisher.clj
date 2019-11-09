(ns com.brunobonacci.mulog.test-publisher
  (:require [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.core :as uc]
            [com.brunobonacci.mulog.utils :as ut]))


(deftype TestPublisher
    [buffer delivery-buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_] buffer)

  (publish-delay [_] 200)

  (publish [_ buffer]
    (swap! delivery-buffer into (map second (rb/items buffer)))
    (rb/clear buffer)))



(defn test-publisher
  [delivery-buffer]
  (TestPublisher. (rb/agent-buffer 2000) delivery-buffer))



(defmacro with-test-pusblisher
  [& body]
  `(let [inbox#  (atom (rb/ring-buffer 100))
         outbox# (atom [])
         gbc#    @com.brunobonacci.mulog/global-context
         _#      (reset! com.brunobonacci.mulog/global-context {})
         tp#     (test-publisher outbox#)
         sp#     (uc/start-publisher! inbox# tp# (keyword (str "test-" (ut/random-uid))))]
     (binding [com.brunobonacci.mulog/*default-logger* inbox#]
       ~@body)

     (reset! com.brunobonacci.mulog/global-context gbc#)
     (Thread/sleep (* 2 uc/PUBLISH-INTERVAL))
     (sp#)
     @outbox#))
