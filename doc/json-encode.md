# How to JSON encode custom Java classes

All the publishers which use JSON encoding use a common (internal)
library that manages the JSON encoding in a centralised manner.  The
`mulog-json` encapsulates the JSON encoding and decoding.  Internally
it uses the fantastic Metosin's library [Jasonista](https://github.com/metosin/jsonista).
It is super fast and it has minimal dependencies.

I don't recommend to use Java Classes as values in ***μ/log***,
especially if mutable, however, if you have custom java classes that
need to be serialized, you can control how the Java class gets
translated into JSON via custom JSON encoders.

Custom JSON encoders are function that will be called by the JSON
encoder during the encoding process when it finds a class that it
doesn't know how to serialize it.

The general form for a custom encoders is:
``` Clojure
(fn [value generator]
    (.writeString generator value))
```

The function will be passed the value to encode at runtime
and a instance of a [WriterBasedJsonGenerator](https://fasterxml.github.io/jackson-core/javadoc/2.8/com/fasterxml/jackson/core/json/WriterBasedJsonGenerator.html).
With the generator you can use various `writeXXX` methods to
output various JSON elements.


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
;; type hints are important for performances.
(swap! com.brunobonacci.mulog.common.json/encoders
  assoc java.awt.Color
  (fn [^java.awt.Color x
       ^com.fasterxml.jackson.core.json.WriterBasedJsonGenerator gen]
    (.writeString gen
      ^String
      (str/upper-case
        (str "#"
          (Integer/toHexString (.getRed x))
          (Integer/toHexString (.getGreen x))
          (Integer/toHexString (.getBlue x)))))))
```

At this point if we try to serialize the our map with the Color into
JSON we would get the following representation:

``` Clojure
(require '[com.brunobonacci.mulog.common.json :refer [to-json]])

(println (to-json {:color (java.awt.Color. 202 255 238)}))
;; {"color":"#CAFFEE"}
```
