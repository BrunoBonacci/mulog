# How to transfer the local-context

The *local-context* is a thread based context.  Different threads will
have different *local-context*.  So if you start a computation on one
thread and then you offload the rest of the computation on a different
thread you will need to propagate the *local-context* into the new
thread for the values to be visible.

Here is an example:

``` clojure
;; let's start a publisher
(def p1 (u/start-publisher! {:type :console :pretty? true}))

;; simple event logging without context
(u/log ::hello :to "World!")

;; {:mulog/event-name :user/hello,
;;  :mulog/timestamp 1596107461713,
;;  :mulog/trace-id #mulog/flake "4XP3B6hxSicK-nvYgLjvoq_AhGwrEw6I",
;;  :mulog/namespace "user",
;;  :to "World!"}


;; and with context (NOTE `:context :v1` in the event)
(u/with-context {:context :v1}
  (u/log ::hello :to "World!"))

;; {:mulog/event-name :user/hello,
;;  :mulog/timestamp 1596108086680,
;;  :mulog/trace-id #mulog/flake "4XP2qHapQxkd7vXqU9vseLQ2ZtCAVB_U",
;;  :mulog/namespace "user",
;;  :context :v1,
;;  :to "World!"}

```


Now if we send the `u/log` statement into a different thread the
context disappear.

``` clojure
(u/with-context {:context :v1}
  ;; on a different thread
  (future
    (u/log ::hello :to "World!")))

;; NOTE: missing `:context :v1`
;; {:mulog/event-name :user/hello,
;;  :mulog/timestamp 1596108119498,
;;  :mulog/trace-id #mulog/flake "4XP2sBrC4ODsG3aAGWOKW4FY4na117Wj",
;;  :mulog/namespace "user",
;;  :to "World!"}
```

In order for the new thread to see the context we need to explicitly
transfer the *local-context*.


``` clojure
(u/with-context {:context :v1}
  ;; capture context
  (let [ctx (u/local-context)]
    ;; on a different thread
    (future
      ;; restore context in the different thread
      (u/with-context ctx
        (u/log ::hello :to "World!")))))

;; {:mulog/event-name :user/hello,
;;  :mulog/timestamp 1596108227200,
;;  :mulog/trace-id #mulog/flake "4XP2yT4Kx79cWOIq0EIVOKgcm-_KbxJR",
;;  :mulog/namespace "user",
;;  :context :v1,
;;  :to "World!"}

```

So basically,

  * you capture the old context value with `(u/local-context)` before
    triggering the call
  * you send your processing call into a new thread
  * before starting the execution in the new thread you restore the
    context by calling `(w/with-context captured-ctx)` with the value
    of the context you captured.
