(ns ^{:doc
      "
Logging library designed to log data events instead of plain words.

This namespace contains a hierarchy for built-in leveled logging
as well as a helper to build an event filter (transducer).
"}
    com.brunobonacci.mulog.levels)

(defonce ^{:doc
           "Leveled logging hierarchy used by the built-in
            publishers via the `:level` configuration.

            Users can leverage this hierarchy in custom
            publishers, or build their own version as they
            see fit.

            `->filter` is a helper to help implementing custom
            leveled logging mechanisms using transducers.
            "}
  default-levels (-> (make-hierarchy)
                     (derive ::debug    ::verbose)
                     (derive ::info     ::debug)
                     (derive ::warning  ::info)
                     (derive ::error    ::warning)
                     (derive ::fatal    ::error)))

(defn ->filter
  "takes a logging level, a `hierarchy` (or `default-levels`)
  and an event key `k` (or `:mulog/level`) and returns a
  transducer which filters events on the given key in the hierarchy"
  ([level]
   (or (some-> level (->filter default-levels :mulog/level))
       (map identity)))
  ([level hierarchy k]
   (filter (fn [{e-level k}]
             (isa? hierarchy e-level level)))))

