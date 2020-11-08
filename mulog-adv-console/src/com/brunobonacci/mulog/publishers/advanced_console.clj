 (ns com.brunobonacci.mulog.publishers.advanced-console
   (:require [com.brunobonacci.mulog.flakes :refer [flake?]]
             [com.brunobonacci.mulog.publisher :as p]
             [com.brunobonacci.mulog.buffer :as rb]
             [clansi :refer [style]]))


(defn wrap-quotes
  [^String s]
  (str "\"" s "\""))

(defn colorize
  [thing color]
  (apply style (if (or (string? thing)
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

(def formatters
  (atom {}))

(defn register-formatters
  [formatter-config]
  (reset! formatters formatter-config))

(defn find-format
  [rules [key val]]
  (->> rules
       (partition 2)
       (keep
        (fn [[match? fmt]]
          (when (match? (hash-map key val))
            fmt)))))

(defn find-all-formats
  [rules entry]
  (mapcat (partial find-format rules) entry))

(defn extract-format
  [format-type formats]
  (->> formats
       (keep
        (fn [fmt]
          (let [rule-format-key (-> fmt vals first)]
            (case format-type
              :pair (hash-map (-> fmt keys first)
                              (get-in @formatters [rule-format-key]))
              :event (get-in @formatters [rule-format-key format-type])
              (throw (Exception. "Format type not supported"))))))
       (into [])))

(defn format-for
  [entry rules format-type]
  (->> entry
       (find-all-formats rules)
       (extract-format format-type)))

(defn entry-format
  [entry rules]
  (->> (format-for entry rules :event)
       (cons (:default-formatter @formatters))
       last))

(defn pair-formats
  [entry rules]
  (->> (format-for entry rules :pair)
       (filter #(:pair (-> % vals first)))
       (apply merge)))

(deftype AdvancedConsolePublisher
         [config buffer]
  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    200)

  (publish [_ buffer]
    (doseq [item (map second (rb/items buffer))
            :let [{:keys [rules pretty?]} config
                  event-fmt (entry-format item rules)
                  pair-formats (pair-formats item rules)
                  pair-keys (keys pair-formats)
                  event-without-pair-fmt (apply dissoc item pair-keys)
                  event-pairs (select-keys item pair-keys)
                  item-output (->> event-pairs
                                   (map (fn [[k v]]
                                          (colorize-item (hash-map k v)
                                                         (get-in pair-formats [k :pair]))))
                                   (apply merge
                                          (colorize-item event-without-pair-fmt event-fmt)))]]
      (println (if pretty? 
                 (naive-prettify item-output)
                 item-output)))
    (flush)
    (rb/clear buffer)))

(defn advanced-console-publisher
  [config]
  (AdvancedConsolePublisher. config (rb/agent-buffer 10000)))
