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
      (u/trace :my-app/http-request
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


## Reitit ring tracking middleware example

The mulog `wrap-trace-events` for reitit/ring-handler is almost the 
same as ring, except it can take an identity to assist tracing the events.

In the follow example, further information is captured about the response.

```clojure
(ns practicalli.service.middleware
  (:require
    [com.brunobonacci.mulog :as mulog]))

(defn wrap-trace-events
  "Log event trace for each api event with mulog/log and reitit-ring"
  [handler id]
  (fn [request]
    ;; Add context of to every trace event generated
    (mulog/with-context
      {:uri            (get request :uri)
       :request-method (get request :request-method)}

      ;; track the request duration and outcome
      (mulog/trace 
        :my-app/http-request
        ;; add key/value pairs for tracking event only
        {:pairs [:content-type     (get-in request [:headers "content-type"])
                 :content-encoding (get-in request [:headers "content-encoding"])
                 :middleware       id]
         ;; capture http status code from the response 
         ;; - add body and headers keys for more information
         :capture (fn [{:keys [status]}] {:http-status status})}

         ;; call the request handler
         (handler request)))))
```

Include the `wrap-trace-events` middleware in the reitit/ring-handler configuratition, 
with a key that will be used as the id in the mulog/trace.

```clojure
(def router-configuration
  "Reitit configuration of coercion, data format transformation 
   and middleware for all routing"
  {:data {:coercion   reitit.coercion.spec/coercion
          :muuntaja   muuntaja/instance
          :middleware [;; swagger feature for OpenAPI documentation
                       api-docs/swagger-feature
                       ;; query-params & form-params
                       parameters/parameters-middleware
                       ;; content-negotiation
                       middleware-muuntaja/format-middleware
                       ;; coercing response bodys
                       coercion/coerce-response-middleware
                       ;; coercing request parameters
                       coercion/coerce-request-middleware
                       ;; logging with mulog
                       [middleware-practicalli/wrap-trace-events :trace-events]]}})
```

Include the middleware configuration in the app configuration (reitit routing) when using `wrap-trace-events` for all API endpoints.

```clojure
(defn app
  "Router for all requests to the Fraud API service and OpenAPI documentation,
  using `ring-handler` to manage HTTP request and responses."
  [environment]

  (ring/ring-handler
   (ring/router
    [open-api-docs  ;; Open API (Swagger) Documentation
     ;; API routes
     ["/api/v1" {:swagger {:description "Base route of endpoints"}}
       (practicalli/routes)]]

     ;; Router configuration - middleware, coersion & content negotiation
     router-configuration)

   ;; Default handlers
   (ring/routes
    ;; Open API documentation as default route
    (api-docs-ui/create-swagger-ui-handler {:path "/"})
    ;; Respond to any other route - returns blank page
    (ring/create-default-handler))))
```

Or include the middleware on a specific part of the routing structure

```clojure
 ["/busines"
   {:swagger {:tags ["Important business endpoints"]
              :description "log business related requests and responses"}
              :middleware [[middleware/wrap-trace-events]]}
      ["/customers" {:get {:tag 'Information about all customers"
                           :handler (handlers/customers persistence)}]]
```
