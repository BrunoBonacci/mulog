 (ns com.brunobonacci.mulog.publishers.ansi-console
   (:require [com.brunobonacci.mulog.flakes :refer [flake?]]
             [com.brunobonacci.mulog.buffer :as rb]
             [clansi :refer [style]]))


(defn wrap-quotes
  [^String s]
  (str "\"" s "\""))

(defn colorize
  [thing color]
  (apply style
         (if (or (string? thing)
                 (flake? thing))
           (wrap-quotes thing)
           thing)
         (if (keyword? color)
           [color]
           color)))

(defn colorize-item
  [item color]
  (reduce-kv (fn [acc k v]
               (assoc acc
                      (colorize k color)
                      (colorize v color)))
             {}
             item))

(defn naive-prettify
  [items]
  (as-> items $
    (reduce-kv (fn [acc k v]
                 (conj acc (str k " " v ",\n")))
               ["{\n"]
               $)
    (conj $ "}")
    (apply str $)))


(defn find-format
  [rules [key val]]
  (->> rules
       (partition 2)
       (keep
        (fn [[match? fmt]]
          (when (match? (hash-map key val))
            fmt)))))

(defn match-formats-for
  [rules entry]
  (mapcat (partial find-format rules) entry))

(defn entry-format
  [entry rules formats]
  (->> entry
       (match-formats-for rules)
       (keep (fn [fmt]
               (get-in formats [fmt :event])))
       (cons (:default-formatter formats))
       last))

(defn pair-formats
  [entry rules formats]
  (->> entry
       (match-formats-for rules)
       (keep (fn [fmt]
               (get-in formats [fmt :pair])))
       (into {})))

(deftype AnsiConsolePublisher
         [config buffer]
  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    200)

  (publish [_ buffer]
    (doseq [item (map second (rb/items buffer))
            :let [{:keys [formats rules pretty?]} config
                  event-fmt (entry-format item rules formats)
                  pair-formats (pair-formats item rules formats)
                  pair-keys (keys pair-formats)
                  event-without-pair-fmt (apply dissoc item pair-keys)
                  event-pairs (select-keys item pair-keys)
                  item-output (->> event-pairs
                                   (map (fn [[k v]]
                                          (colorize-item (hash-map k v)
                                                         (pair-formats k))))
                                   (apply merge
                                          (colorize-item event-without-pair-fmt event-fmt)))]]
      (println (if pretty?
                 (naive-prettify item-output)
                 item-output)))
    (flush)
    (rb/clear buffer)))

(defn ansi-console-publisher
  [config]
  (AnsiConsolePublisher. config (rb/agent-buffer 10000)))
