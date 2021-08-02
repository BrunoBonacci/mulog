## Slack publisher
![since v0.3.0](https://img.shields.io/badge/since-v0.3.0-brightgreen)

In order to use the library add the dependency to your `project.clj`

``` clojure
;; Leiningen project
[com.brunobonacci/mulog-slack "x.x.x"]

;; deps.edn format
{:deps { com.brunobonacci/mulog-slack {:mvn/version "x.x.x"}}}
```
Current version: [![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog-slack.svg)](https://clojars.org/com.brunobonacci/mulog-slack)

The events must be serializeable in JSON format (see [How to JSON encode custom Java classes](/doc/json-encode.md) for more info.)

First get an [Incoming Webhook in Slack](https://api.slack.com/messaging/webhooks)
following these steps:

- **Create a Slack App**, go to: https://api.slack.com/apps/new
- `App Name` put something like `mulog`, then select the workspace, and click in `Create App`
- **Enable Incoming Webhooks**, In the app settings, *Basic
  Information* -> *Add features and functionality*, click on
  **Incoming Webhooks**, then click on the toggle to enable it.
- **Create an Incoming Webhook** by clicking on **Add New Webhook to Workspace**,
  select the channel where to send the notifications and click on **Allow**.
- In the **WebHook URL** table you should see a new entry for the channel you
  just added in the form of `https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX`
  *note the URL you will need it later*.


The available configuration options:

``` clojure
{:type :slack

 ;; Slack webhook url (REQUIRED)
 :webhook-url  "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"

 ;; a function to select and/or transform the events to send (REQUIRED)
 ;; Select only the events you wish to see in your Slack channel
 ;; A simpler way to express filter is by using [where](https://github.com/BrunoBonacci/where)
 ;; With where the following filter would look like:
 ;; :transform (partial filter (where :mulog/event-name :in? [:my-event1 :my-event2]))
 :transform (partial filter #(#{:my-event1 :my-event2} (:mulog/event-name %)))


 ;; the maximum number of events which can be sent in a single
 ;; batch request Slack
 ;; :max-items     20

 ;; Interval in milliseconds between publish requests.
 ;; μ/log will try to send the records to Slack
 ;; with the interval specified.
 ;; :publish-delay 3000

 ;; You can customize the appearance of the messages by providing
 ;; a function which takes an event and returns one or more blocks
 ;; ad described here: https://api.slack.com/reference/block-kit/blocks
 ;; :render-message (fn [event] )

 }

```

How to use it:

``` clojure
(μ/start-publisher!
  {:type        :slack
   :webhook-url "https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXXXXXXXXXX"
   :transform   (partial filter #(= :my-event1 (:mulog/event-name %)))})
```


Once you send a message you should get a Slack notification which looks as follow:

![mulog slack example](../images/mulog-slack-exmaple.png)

You can customize the look by providing a `:render-message` function
which returns one or more [Slack Blocks](https://api.slack.com/reference/block-kit/blocks).
