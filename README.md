# lazy-map

> Lazy maps for Clojure

[![lazy-map on Clojars](https://img.shields.io/clojars/v/io.randomseed/lazy-map.svg)](https://clojars.org/io.randomseed/lazy-map)
[![lazy-map on cljdoc](https://cljdoc.org/badge/io.randomseed/lazy-map)](https://cljdoc.org/d/io.randomseed/lazy-map/CURRENT)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/randomseed-io/lazy-map/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/randomseed-io/lazy-map/tree/master)

## Summary

This library provides a new Clojure data type: the *lazy map*. Lazy maps behave like
regular (persistent) maps, except their values are not computed until they are
actually requested.

It is based on code from [raxod502](https://github.com/raxod502/lazy-map), with three
important changes:

* The equality method is modified to **compare only to maps**. This prevents unwanted
  realization of values when a lazy map is compared with booleans, keywords, or other
  non-map-like objects, which will always differ from a map anyway so there is no
  reason to force values.

* **The namespace is `io.randomseed.lazy-map`**, and the artifact is
  `io.randomseed/lazy-map`, to prevent collisions (many lazy map packages are
  published as `lazy-map/lazy-map`).

* The JAR includes **AOT-compiled Java classes** so the types are available to
  consumers.

## Installation

To use `lazy-map` in your project, add the following to the dependencies section of
`project.clj` or `build.boot`:

```clojure
[io.randomseed/lazy-map "1.0.2"]
```

For `deps.edn`, add the following under the `:deps` or `:extra-deps` key:

```clojure
io.randomseed/lazy-map {:mvn/version "1.0.2"}
```

Additionally, if you want to use the specs and generators provided by `lazy-map`, you
can add (in your development profile):

```clojure
org.clojure/spec.alpha {:mvn/version "0.6.249"}
org.clojure/test.check {:mvn/version "1.1.3"}
```

You can also download the JAR from
[Clojars](https://clojars.org/io.randomseed/lazy-map).

## Usage

Start by requiring the namespace:

    user> (require '[io.randomseed.lazy-map :as lm])

You can then construct a lazy map using the `lazy-map` macro.

    user> (def m (lm/lazy-map {:a (do (println "resolved :a") "value :a")
                               :b (do (println "resolved :b") "value :b")}))
    #'user/m
    user> m
    {:a <unrealized>, :b <unrealized>}

When you request a value from the map, it will be evaluated and its value will be
cached:

    user> (:a m)
    resolved :a
    "value :a"
    user> (:a m)
    "value :a"

You can `assoc` values onto lazy maps just like regular maps. If you `assoc` a delay,
it will be treated as an unrealized value and not forced until necessary:

    user> (assoc (lm/lazy-map {}) :a 1 :b (delay 2))
    {:a 1, :b <unrealized>}

Lazy maps are very lazy. In practice, this means they probably will not compute their
values until absolutely necessary. For example, taking the `seq` of a lazy map does
not force any computation, and map entries have been made lazy as well:

    user> (def m (lm/lazy-map {:a (do (println "resolved :a") "value :a")
                               :b (do (println "resolved :b") "value :b")}))
    #'io.randomseed.io.randomseed.lazy-map/m
    io.randomseed.lazy-map> (dorun m)
    nil
    io.randomseed.lazy-map> (keys m)
    (:a :b)
    io.randomseed.lazy-map> (key (first m))
    :a
    io.randomseed.lazy-map> (val (first m))
    resolved :a
    "value :a"

You can also initialize a lazy map from a regular map, where delays are taken as
unrealized values:

    user> (lm/->LazyMap {:a 1 :b (delay 2)})
    {:a 1, :b <unrealized>}

You might prefer to use `->?LazyMap` instead of `->LazyMap`. The only difference is
that `->?LazyMap` acts as the identity function if you pass it a map that is already
lazy. This prevents nested lazy maps, which are not inherently wrong but which could
be bad for performance if you nest them thousands of layers deep.

There are also some utility functions for dealing with lazy maps. You can use
`force-map` to compute all of the values in a lazy map.  Alternatively, you can use
`freeze-map` to replace all the unrealized values with a placeholder. Here is an
illustration:

    user> (lm/force-map
            (lm/->LazyMap {:a (delay :foo)
                           :b :bar}))
    {:a :foo, :b :bar}
    user> (lm/force-map
            (lm/freeze-map
              :quux
              (lm/->LazyMap {:a (delay :foo)
                             :b :bar})))
    {:a :quux, :b :bar}

Finally, lazy maps will automatically avoid computing their values when they are
converted to strings using `str`, `pr-str`, and `print-dup`. To accomplish the same
for `pprint`, you must use a special pretty-print dispatch function:

    user> (pp/with-pprint-dispatch lm/lazy-map-dispatch
            (pp/pprint (lm/lazy-map {:a (println "lazy")})))
    {:a <unrealized>}

Check out the [unit tests] for more information on the exact behavior of lazy maps.

[unit tests]: test/io/randomseed/lazy_map_test.clj

## Organization

All the code is currently in the `io.randomseed.lazy-map` namespace, and the unit
tests are in the `io.randomseed.lazy-map-test` namespace.

## See also

**[Malabarba's implementation] of lazy maps in Clojure.**

[Malabarba's implementation]: https://github.com/Malabarba/lazy-map-clojure

Features unique to `malabarba/lazy-map`:

* ClojureScript support
* Transform Java classes into lazy maps (methods become keys)

Features unique to `raxod502/lazy-map`:

* More robust handling of laziness: all possible operations on maps
  are supported correctly (e.g. `seq` and `reduce-kv`)
* Pretty string representation and support for pretty-printing

Features unique to `io.randomseed/lazy-map`:

* Equality method compares only to maps (so no sentinel will cause accidental
  realization of values).
* AOT-compiled Java classes.
* Artifact group is unique (no name collisions with packages requiring other lazy map
  libraries).
