(ns com.brunobonacci.mulog.publishers.filesystem-metrics
  (:require [clojure.spec.alpha :as s]
            [com.brunobonacci.mulog :as u]
            [com.brunobonacci.mulog.buffer :as rb])
  (:import  [java.nio.file FileSystems FileSystem FileStore]))



;; Unfortunately, there doesn't seem to be a supported way to get the
;; mount path for a FileStore object, despite it always having one,
;; and it even appearing in the string representation. Therefore we
;; use reflection to access the private file or root field (depending
;; on the concrete class type). This produces a number of warnings,
;; and in future Java versions it may fail. If that happens we will
;; need to take an alternative approach, such as parsing the path out
;; of the `.toString()` representation of the FileStore object.  See
;; this StackOverflow question for more details:
;; https://stackoverflow.com/questions/10678363/find-the-directory-for-a-filestore
(def store-hacks
  [(when-let [klazz (try (Class/forName "sun.nio.fs.UnixFileStore")
                         (catch ClassNotFoundException _ nil))]
     (let [field (doto (.getDeclaredField ^Class klazz "file")
                   (.setAccessible true))]
       [klazz (fn unix-file-store-hack [store] (str (.get field store)))]))

   (when-let [klazz (try (Class/forName "sun.nio.fs.WindowsFileStore")
                         (catch ClassNotFoundException _ nil))]
     (let [field (doto (.getDeclaredField ^Class klazz "root")
                   (.setAccessible true))]
       [klazz (fn windows-file-store-hack [store] (str (.get field store)))]))])



(defn- file-store-path
  [^FileStore store]
  (->> store-hacks
    (map (fn hack-path [[klazz hack]]
           (when (and klazz (instance? klazz store))
             (hack store))))
    (remove nil?)
    first))



(s/def :fs/name string?)
(s/def :fs/type string?)
(s/def :fs/path (s/nilable string?))
(s/def :fs/readonly? boolean?)
(s/def :fs/total-bytes int?)
(s/def :fs/unallocated-bytes int?)
(s/def :fs/usable-bytes int?)
(s/fdef capture-fs-metrics
  :args (s/cat :store (partial instance? FileStore))
  :ret (s/keys :req-un [:fs/name :fs/type :fs/path :fs/readonly?
                        :fs/total-bytes :fs/unallocated-bytes :fs/usable-bytes]))



(defn capture-fs-metrics [^FileStore store]
  {:name (.name store)
   :type (.type store)
   :path (file-store-path store)
   :readonly? (.isReadOnly store)
   :total-bytes (.getTotalSpace store)
   :unallocated-bytes (.getUnallocatedSpace store)
   :usable-bytes (.getUsableSpace store)})



(defn publish-fs-metrics
  [^FileSystem fs transform-samples]
  (doseq [metrics (->> fs .getFileStores (map capture-fs-metrics) transform-samples)]
    (u/log :mulog/filesystem-metrics-sampled
      :filesystem-metrics metrics)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                            ;;
;;                     ----==| P U B L I S H E R |==----                      ;;
;;                                                                            ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype FilesystemMetricsPublisher [config buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    (:sampling-interval config 60000))

  (publish [_ buffer]
    ;; sampling the file system metrics
    (publish-fs-metrics (FileSystems/getDefault) (:transform-samples config))))



(def ^:const DEFAULT-CONFIG
  {;; Interval in milliseconds between two samples
   :sampling-interval 60000

   ;; Transformation to apply to the samples before publishing.
   ;;
   ;; It is a function that takes a sequence of samples and
   ;; returns and updated sequence of samples:
   ;; `transform-samples -> sample-seq -> sample-seq`
   :transform-samples identity})



(defn filesystem-metrics-publisher
  [{:keys [sampling-interval transform-samples transform] :as config}]
  (when transform
    (println
      "[μ/log] DEPRECATION WARNING: on `:filesystem-metrics` sampler,"
      "please update config key `:transform` to `:transform-samples`")
    (println
      "[μ/log] DEPRECATION WARNING: for more info: https://github.com/BrunoBonacci/mulog/issues/74"))
  (let [config (as-> config $
                 (merge DEFAULT-CONFIG $)
                 (assoc $ :transform-samples (or transform-samples transform identity))
                 (assoc $ :sampling-interval
                   (max (:sampling-interval $) 1000)))]
    ;; create the metrics publisher
    (FilesystemMetricsPublisher. config (rb/agent-buffer 1))))
