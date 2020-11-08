# μ/log -> Advanced Console publisher
[![Clojars Project](https://img.shields.io/clojars/v/com.brunobonacci/mulog.svg)](https://clojars.org/com.brunobonacci/mulog)  [![cljdoc badge](https://cljdoc.org/badge/com.brunobonacci/mulog)](https://cljdoc.org/d/com.brunobonacci/mulog/CURRENT) ![CircleCi](https://img.shields.io/circleci/project/BrunoBonacci/mulog.svg) ![last-commit](https://img.shields.io/github/last-commit/BrunoBonacci/mulog.svg)


This project contains the `publisher` for terminal and consoles with
advanced formatting options.


## Usage

Please see [README](../README.md#advanced-console-publisher) on main page.

### ANSI color printing
For better visual checks of the logs there is an ANSI color printer.

To use it you need to setup the rules when the format applies, as well as the formatters

The current setup supports 8 colors: 
  - white
  - black
  - red
  - green
  - blue
  - yellow
  - magenta
  - cyan

You can add a background using `:bg-` in front of the color - e.g. `:bg-red` will make the background red.

In addition to these you can use `:bright` to achieve a similar effect to bold, `:underline`

`:inverse` will inverse the font color and the background color.

#### Example for setting up the formats:

There are two types of formatters:
- `event` - when the value is found in an log item it will color the whole event
  - when there are two or more matches it will pick the last rule it matches
  - when there are not matches it will pick the `:default-formatter` otherwise it will print a plain string
- `pair` - it overrides the event color and colors a specific key-value pair.
  - when there are two or more matches it will pick the last rule


```clojure
(advanced-console/register-formatters
  :http-format           {:event :yellow}
  :event-format          {:event :green}
  :http-error-format     {:pair :red}
  :override-pair-format  {:pair :blue}
  :underline-pair-format {:pair [:cyan :underline :bg-red :inverse]}
  :default-formatter     :magenta})
```

In order to apply multiple formatting effects you need to wrap them in a vector.

#### Example for setting up the rules

To make use of the rules setup you need `where` to be included in your project: `[com.brunobonacci/where "0.5.5"]`

```clojure
;; notice that the matching values are used to match the formatter.
(def format-rules
  [(where :mulog/event-name :is? :line-test)
   {:line-test :event-format}

   (where contains? :http-test)
   {:http-test :http-format}

   (where contains? :http-error)
   {:http-error :http-error-format}
   
   (where :http-error :is? 500)
   {:http-error :override-pair-format}
   
   (where :http-error :is? 503)
   {:http-error :underline-pair-format}])
```

The rules need to be passed onto the publisher. The publisher supports simple pretty printing for the event. Key-value pairs in the event are displayed on separate line.

When the values are nested data structures they are displayed as one line.

```clojure
(mu/start-publisher!
   {:type :advanced-console
    :rules format-rules
    :pretty? true})
```

## License

Copyright © 2019-2020 Bruno Bonacci - Distributed under the [Apache License v2.0](http://www.apache.org/licenses/LICENSE-2.0)
