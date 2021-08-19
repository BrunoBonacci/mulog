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
    (if (and (contains? $ :ObjectName) (instance? ObjectName (get $ :ObjectName)))
      (update $ :ObjectName #(.getCanonicalName ^ObjectName %))
      $)
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
    (map (juxt (memfn ^ObjectName getCanonicalName) (memfn ^ObjectName getDomain)
           (memfn  ^ObjectName getKeyPropertyList) jmx/mbean))
    (map (fn [[canonical-name domain keys attrs]]
           {:canonical-name canonical-name
            :domain domain
            :keys (into {} keys)
            :attributes (sanitize-values attrs)}))))



(defn sample-mbeans [mbeans-patterns transform-samples]
  (doseq [pattern mbeans-patterns
          mbean   (transform-samples (mbeans-sample pattern))]
    (u/log :mulog/mbean-sampled :search-pattern pattern :mbean mbean)))



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
    (sample-mbeans (:mbeans-patterns config) (:transform-samples config))))



(def ^:const DEFAULT-CONFIG
  {;; Interval in milliseconds between two samples
   :sampling-interval 60000

   ;; list of MBean patterns to sample
   :mbeans-patterns []

   ;; Transformation to apply to the samples before publishing.
   ;;
   ;; It is a function that takes a sequence of samples and
   ;; returns and updated sequence of samples:
   ;; `transform-samples -> sample-seq -> sample-seq`
   :transform-samples identity})



(defn- apply-defaults
  [{:keys [transform-samples transform] :as config}]
  (as-> config $
    (merge DEFAULT-CONFIG $)
    (update $ :sampling-interval #(max (or % 1000) 1000))
    (assoc  $ :transform-samples (or transform-samples (and transform (partial map transform)) identity))))



(defn mbean-sampler-publisher
  [{:keys [transform] :as config}]
  (when transform
    (println
      "[μ/log] DEPRECATION WARNING: on `:mbean` sampler,"
      "please update config key `:transform` to `:transform-samples`")
    (println
      "[μ/log] DEPRECATION WARNING: for more info: https://github.com/BrunoBonacci/mulog/issues/75"))
  ;; create the metrics publisher
  (MBeanSamplerPublisher. (apply-defaults config) (rb/agent-buffer 100)))



(comment

  (sample-mbeans ["java.lang:type=Memory"] identity)
  (sample-mbeans ["java.nio:*"] identity)

  )
