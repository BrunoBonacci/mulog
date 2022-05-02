# How to JSON encode custom Java classes

All the publishers which use JSON encoding use a common (internal)
library that manages the JSON encoding in a centralised manner.  The
`mulog-json` encapsulates the JSON encoding and decoding.  Internally
it uses the [Charred](https://github.com/cnuernber/charred) library.
It is super fast and it has no external dependencies.

I don't recommend to use Java Classes as values in ***μ/log***,
especially if mutable, however, if you have custom java classes that
need to be serialized, you can control how the Java class gets
translated into JSON via custom JSON encoders.

Custom JSON encoders are function that will be called by the JSON
encoder during the encoding process when it finds a class that it
doesn't know how to serialize it.

The general form for a custom encoders is:
``` Clojure
(->json-data [item] "encoded-value"))
```

The custom encoder can be provided by extending the protocol [PToJSON](https://cnuernber.github.io/charred/charred.api.html#var-PToJSON).

As an example, let's imagine we want to serialize the `java.awt.Color`
RGB value to the standard hexadecimal Web representation (`#AABBCC`).
Here is how we could inform the JSON encoder how to transform the
Java value into it's encoded representation.

Let's assume we want to serialize the following map:

``` Clojure
{:color (java.awt.Color. 202 255 238)}
```

We need to add a custom encoder for the class `java.awt.Color`


Here is an example of how to add the Web hexadecimal encoding for
`java.awt.Color`.

``` Clojure
(require '[charred.api])
;; type hints are important for performances.

(extend-protocol charred.api/PToJSON

  java.awt.Color
  (->json-data [^java.awt.Color x]
    (str/upper-case
        (str "#"
          (Integer/toHexString (.getRed x))
          (Integer/toHexString (.getGreen x))
          (Integer/toHexString (.getBlue x))))))
```

At this point if we try to serialize the our map with the Color into
JSON we would get the following representation:

``` Clojure
(require '[com.brunobonacci.mulog.common.json :refer [to-json]])

(println (to-json {:color (java.awt.Color. 202 255 238)}))
;; {"color":"#CAFFEE"}
```
