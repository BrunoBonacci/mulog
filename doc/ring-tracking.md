# How to write a tracking Ring middleware

If you only going to put one tracking statement in your code add it to
track every single incoming request to your system.

If you are using Ring then it is very easy to write a middleware
that track the requests, measure the time, and sends that information
to ***μ/log***.

Here is how to do it:

``` Clojure
(require '[com.brunobonacci.mulog :as u])

(defn wrap-tracking-events
  "tracks api events with μ/log."
  [handler]
  (fn [req]
    ;; enhance the context with the info that
    ;; will be attached to all events within
    ;; this request.
    (u/with-context
      {:uri            (get req :uri)
       :request-method (get req :request-method)}

      ;; track the request duration and outcome
      (u/trace :io.redefine.datawarp/http-request
        ;; add here all the key/value pairs for tracking event only
        {:pairs [:content-type     (get-in req [:headers "content-type"])
                 :content-encoding (get-in req [:headers "content-encoding"])]
         ;; out of the response capture the http status code.
         :capture (fn [{:keys [status]}] {:http-status status})}

        ;; call the request handler
        (handler req)))))


;; add the middleware to your ring-handler
(def app (wrap-tracking-events ring-handler))
```

With this middleware you will be able extract the following information:

  - Requests per seconds (or minutes/hours/days)
  - Requests per seconds by endpoint (:uri)
  - Errors per seconds (or minutes/hours/days)
  - Latency distribution of the requests
  - Median, 95%ile, 99%ile.
  - Requests by content-type or content-encoding
  - Requests by request method

If you added the `app-name`, `version` and `environment` as described
in the best practices all above queries can be done by version or
environment.

And if you add caller information such as: `ip-address`,
`principal-id` or the resource type and identifier the query
possibilities grow exponentially. For example, you will be able to run
all the above queries by user or by resource, or plotting heat-maps of
from where your users are connecting to you (via geo-location).
