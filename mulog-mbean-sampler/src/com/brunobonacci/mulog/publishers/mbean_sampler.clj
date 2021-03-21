(ns com.brunobonacci.mulog.publishers.mbean-sampler
  (:require [clojure.spec.alpha :as s]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.buffer :as rb]
            [com.brunobonacci.mulog.utils :as ut]
            [clojure.java.jmx :as jmx]
            [clojure.stacktrace :as stack]
            [clojure.walk :as walk])
  (:import [javax.management ObjectName]))



;;
;; Some values can be retrieved and an exception is thrown.
;; replacing those cases with `nil`
;;
(defn- sanitize-values
  [value]
  (as-> value $
    ;; Turns ObjectName into strings with canonical represtantion
    (update $ :ObjectName #(and % (.getCanonicalName ^ObjectName %)))
    ;; remove exception
    (walk/postwalk
      (fn [v]
        (if (instance? Exception v)
          ;; some values can't be retrieved
          (if (instance? java.lang.UnsupportedOperationException
                (stack/root-cause v))
            nil
            (ut/exception-stacktrace v))
          v))
      $)))



(defn mbeans-sample
  "Returns list of MBeans that match the given `pattern`. For each
   MBean it will returns the current value of each attribute/property.

     - `*:type=Foo,name=Bar` to match names in any domain whose exact
        set of keys is `type=Foo,name=Bar`.
     - `d:type=Foo,name=Bar,*` to match names in the domain d that
        have the keys `type=Foo,name=Bar` plus zero or more other keys.
     - `*:type=Foo,name=Bar,*` to match names in any domain that has
        the keys `type=Foo,name=Bar` plus zero or more other keys.
     - `d:type=F?o,name=Bar` will match e.g. `d:type=Foo,name=Bar` and
       `d:type=Fro,name=Bar`.
     - `d:type=F*o,name=Bar` will match e.g. `d:type=Fo,name=Bar` and
       `d:type=Frodo,name=Bar`.
     - `d:type=Foo,name=\"B*\"` will match
        e.g. `d:type=Foo,name=\"Bling\"`. Wildcards are recognized even inside
        quotes, and like other special characters can be escaped with \\
  "
  [pattern]
  (->> (jmx/mbean-names pattern)
    (map (juxt (memfn ^ObjectName getDomain) (memfn  ^ObjectName getKeyPropertyList) jmx/mbean))
    (map (fn [[domain keys attrs]]
           {:domain domain
            :keys (into {} keys)
            :attributes (sanitize-values attrs)}))))



(defn sample-mbeans [mbeans-patterns transform]
  (doseq [pattern mbeans-patterns
          mbean   (mbeans-sample pattern)]
    (u/log :mulog/mbean-sampled :search-pattern pattern :mbean (transform mbean))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| P U B L I S H E R |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(deftype MBeanSamplerPublisher [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:sampling-interval config 60000))

  (publish [_ buffer]
    ;; sampling MBeans
    (sample-mbeans (:mbeans-patterns config) (:transform config))))



(def ^:const DEFAULT-CONFIG
  {;; Interval in milliseconds between two samples
   :sampling-interval 60000
   ;; list of MBean patterns to sample
   :mbeans-patterns []
   ;; Transformation to apply to the sample before publishing
   ;; this is applied to the `:mbean`
   :transform identity})



(defn- apply-defaults
  [config]
  (as-> config $
    (merge DEFAULT-CONFIG $)
    (update $ :sampling-interval #(max (or % 1000) 1000))
    (update $ :transform #(or % identity))))



(defn mbean-sampler-publisher
  [config]
  ;; create the metrics publisher
  (MBeanSamplerPublisher. (apply-defaults config) (rb/agent-buffer 100)))




(comment

  (sample-mbeans ["java.lang:type=Memory"] identity)
  (sample-mbeans ["java.nio:*"] identity)

  )
