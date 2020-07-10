(ns ^{:author "Bruno Bonacci (@BrunoBonacci)"
      :doc
      "
 Flakes are like snowflakes, no two are the same.
 ------------------------------------------------

 This is an implementation of a 192-bits unique ids which has the
 following characteristics:

  - **Monotonic IDs**
    Every new ID is larger than the previous one. The idea is that
    the 'happens before' relationship applies to these IDs. If you
    observe a Flake and you create a new one in the same thread, the
    new Flake is going to be larger than the previous one. There is
    no synchronisation and it uses a wall clock as well as a
    monotonic clock as part of the generation, therefore Flakes
    created across processes/machines might suffer from clock skew
    and hard reset.  Generally the following condition should apply
    for all Flakes `flake0 < flake1 < flake2 < ... < flakeN`

  - **Two components: one time-based (64 bits), one random (128 bits)**
    The most significant bits are time based and they use a monotonic
    clock with nanosecond resolution. The next 128 bits are randomly
    generated.

  - **Random-based**
    The Random component is built with a PRNG for speed.
    It uses 128 full bits, more bits than `java.util.UUID/randomUUID`
    which has 6 bits reserved for versioning and type, therefore
    effectively only using 122 bits.

  - **Homomorphic representation**
    Whether you choose to have a bytes representation or string representation
    it uses an encoding which maintain the ordering.
    Which it means that:
    if `flake1 < flake2` then `flake1.toString() < flake2.toString()`
    Internally it uses a NON STANDARD base64 encoding to preserve the ordering.
    Unfortunately, the standard Base64 encoding doesn't preserve this property
    as defined in https://en.wikipedia.org/wiki/Base64.

  - **Web-safe string representation**.
    The string representation uses only characters which are web-safe and can
    be put in a URL without the need of URL encoding.

  - **Fast**, speed is important so we target under 50 nanosecond for 1 id.
    These are the performances measured with Java 1.8.0_232 for the creation
    of a new Flake.

        Evaluation count : 1570017720 in 60 samples of 26166962 calls.
        Execution time mean : 36.429623 ns
        Execution time std-deviation : 0.519843 ns
        Execution time lower quantile : 35.684111 ns ( 2.5%)
        Execution time upper quantile : 37.706974 ns (97.5%)
        Overhead used : 2.090673 ns

        Found 4 outliers in 60 samples (6.6667 %)
        low-severe 4 (6.6667 %)
        Variance from outliers : 1.6389 % Variance is slightly inflated by outliers

    These are the performances for creating an new Flake and turn it into a string

        Evaluation count : 755435400 in 60 samples of 12590590 calls.
        Execution time mean : 78.861983 ns
        Execution time std-deviation : 1.006261 ns
        Execution time lower quantile : 77.402593 ns ( 2.5%)
        Execution time upper quantile : 81.006901 ns (97.5%)
        Overhead used : 1.881732 ns

  "}
    com.brunobonacci.mulog.flakes
  (:import com.brunobonacci.mulog.core.Flake))



(defn flake
  "A time-ordered, pseudo-random, Universal ID of 192 bits"
  []
  (Flake/flake))



(defn snowflake
  "A time-ordered, pseudo-random, Universal ID of 192 bits represented as a 32bytes strings"
  []
  (str (Flake/flake)))



(defn flake-time
  "Returns the timestamp in nanoseconds"
  [^Flake flake]
  (.getTimestampNanos flake))



(defn flake-hex
  "Hexadecimal representation"
  [^Flake flake]
  (Flake/formatFlakeHex flake))



;; Flake representation is just a string base64 homomorphic
(defmethod print-method Flake
  [f ^java.io.Writer w]
  (.write w "#mulog/flake ")
  (print-method (str f) w))



(defmethod print-dup Flake
  [f ^java.io.Writer w]
  (print-method f w))



;; Reader macro data reader
(defn read-method
  "Reader method"
  [flake]
  {:pre [(string? flake)]}
  (Flake/parseFlake flake))
