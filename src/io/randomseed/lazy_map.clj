(ns io.randomseed.lazy-map

  "Main namespace, contains utility functions, type definitions, and
  lazy map functions.

  Public API is:

  - `->LazyMap`,
  - `->?LazyMap`,
  - `lazy-map`,
  - `force-map`,
  - `freeze-map`,
  - `lazy-map-dispatch`."

  (:require [clojure.pprint :as pp])
  (:import  (java.io Writer)))

;;;; Utility functions

(defmacro is-not-thrown?
  "Used in clojure.test assertions because (is (not (thrown? ...)))  doesn't work. See
  http://acidwords.com/posts/2015-07-23-fixing-negation-in-clojure-test.html"
  [e expr]
  {:style/indent 1}
  `(is (not ('thrown? ~e ~expr))))

(defmacro extend-print
  "Convenience macro for overriding the string representation of a
  class. Note that you also need to override `toString` in order to
  customize the return value of `str` on your object, and you need to
  create your own dispatch function to customize the pretty-printed
  representation."
  {:private true}
  [class str-fn]
  `(do
     ;; for serialization
     (defmethod print-dup ~class
       [obj# ^Writer writer#]
       (.write writer# ^String (~str-fn obj#)))
     ;; for reader-friendly printing
     (defmethod print-method ~class
       [obj# ^Writer writer#]
       (.write writer# ^String (~str-fn obj#)))))

(defn map-entry
  "Creates a map entry (as returned by calling seq on a map) with the
  given key and value."
  ^clojure.lang.MapEntry [k v]
  (clojure.lang.MapEntry/create k v))

(defn map-keys
  "Applies f to each of the keys of a map, returning a new map."
  [f map]
  (reduce-kv (fn [m k v]
               (assoc m (f k) v))
             {}
             map))

(defn map-vals
  "Applies f to each of the values of a map, returning a new map."
  [f map]
  (reduce-kv (fn [m k v]
               (assoc m k (f v)))
             {}
             map))

;;;; PlaceholderText type

(defrecord PlaceholderText [text])

(extend-print PlaceholderText :text)

;;;; LazyMapEntry type

(defn- seq2?
  "Helper to check if the sequable has 2 elements. Return nil when not a 2-element
  sequence."
  [o]
  (when (instance? clojure.lang.Seqable o)
    (let [s (seq o)]
      (when (and s (next s) (nil? (next (next s))))
        s))))

(defn- entry-eq-target
  "Returns `o` (unchanged) when it's a safe equality target for a map-entry,
   or returns a 2-element seq for seqables, or nil when not comparable."
  [o]
  (cond
    (instance? clojure.lang.IMapEntry o)                                o
    (instance? java.util.Map$Entry    o)                                o
    (and (instance? clojure.lang.IPersistentVector o) (== 2 (count o))) o
    (and (instance? java.util.List o) (== 2 (.size ^java.util.List o))) o
    :else
    (seq2? o))) ; 2-el. seq or nil

(deftype LazyMapEntry [key_ val_]

  clojure.lang.Associative

  (containsKey [_ k]
    (boolean (#{0 1} k)))

  (entryAt [_ k]
    (cond
      (= k 0) (map-entry 0 key_)
      (= k 1) (LazyMapEntry. 1 val_)
      :else   nil))

  (assoc [_ k v]
    (cond
      (= k 0) (LazyMapEntry. v val_)
      (= k 1) (LazyMapEntry. key_ v)
      (= k 2) (vector k (force val_) v)
      :else   (throw (IndexOutOfBoundsException.))))

  clojure.lang.IFn

  (invoke [this k]
    (.valAt this k))

  clojure.lang.IHashEq

  (hasheq [_]
    (.hasheq
     ^clojure.lang.IHashEq
     (vector key_ (force val_))))

  clojure.lang.ILookup

  (valAt [_ k]
    (cond
      (= k 0) key_
      (= k 1) (force val_)
      :else   nil))

  (valAt [_ k not-found]
    (cond
      (= k 0) key_
      (= k 1) (force val_)
      :else   not-found))

  clojure.lang.IMapEntry

  (key [_] key_)
  (val [_] (force val_))

  clojure.lang.Indexed

  (nth [this i]
    (cond
      (= i 0)      key_
      (= i 1)      (force val_)
      (integer? i) (throw (IndexOutOfBoundsException.))
      :else        (throw (IllegalArgumentException. "Key must be integer")))
    (.valAt this i))

  (nth [this i not-found]
    (try
      (.nth this i)
      (catch Exception _ not-found)))

  clojure.lang.IPersistentCollection

  (count [_] 2)

  (empty [_] false)

  (equiv [_ o]
    (cond (nil?   o) false
          (ident? o) false
          :else      (if-some [t (entry-eq-target o)]
                       (.equiv [key_ (force val_)] t)
                       false)))

  clojure.lang.IPersistentStack

  (peek [_] (force val_))
  (pop [_] [key_])

  clojure.lang.IPersistentVector

  (assocN [_ i v]
    (.assocN [key_ (force val_)] i v))

  (cons [_ o]
    (.cons [key_ (force val_)] o))

  clojure.lang.Reversible

  (rseq [_] (lazy-seq (list (force val_) key_)))

  clojure.lang.Seqable

  (seq [_]
    (cons key_ (lazy-seq (list (force val_)))))

  clojure.lang.Sequential
  java.io.Serializable
  java.lang.Comparable

  (compareTo [_ o]
    (.compareTo
     ^java.lang.Comparable
     (vector key_ (force val_))
     o))

  java.lang.Iterable

  (iterator [this]
    (.iterator
     ^java.lang.Iterable
     (.seq this)))

  java.lang.Object

  (toString [_]
    (str [key_ (if (and (delay? val_)
                        (not (realized? val_)))
                 (->PlaceholderText "<unrealized>")
                 (force val_))]))

  java.util.Map$Entry

  (getKey [_] key_)
  (getValue [_] (force val_))

  java.util.RandomAccess)

(defn lazy-map-entry
  "Construct a lazy map entry with the given key and value. If you
  want to take advantage of the laziness, the value should be a
  delay."
  ^LazyMapEntry [k v]
  (LazyMapEntry. k v))

(extend-print LazyMapEntry #(.toString ^LazyMapEntry %))

;;;; LazyMap type

;; We need to use this function in .toString so it needs to be defined
;; before the deftype. But the definition of this function needs a
;; ^LazyMap type hint, so the definition can't come until after the
;; deftype.

(declare freeze-map)

(deftype LazyMap [^clojure.lang.IPersistentMap contents]

  clojure.lang.Associative

  (containsKey [_ k]
    (and contents
         (.containsKey contents k)))

  (entryAt [_ k]
    (and contents
         (lazy-map-entry k (.valAt contents k))))

  clojure.lang.IFn

  (invoke [this k]
    (.valAt this k))

  (invoke [this k not-found]
    (.valAt this k not-found))

  clojure.lang.IKVReduce

  (kvreduce [this f init]
    (reduce-kv f init (into {} this)))

  clojure.lang.ILookup

  (valAt [_ k]
    (and contents
         (force (.valAt contents k))))

  (valAt [_ k not-found]
    (and contents
         (if-some [^clojure.lang.MapEntry me (.entryAt contents k)]
           (force (.val me))
           not-found)))

  clojure.lang.IMapIterable

  (keyIterator [_]
    (.keyIterator ^clojure.lang.IMapIterable contents))

  (valIterator [_]
    (.iterator
     ;; Using the higher-arity form of map prevents chunking.
     ^java.lang.Iterable
     (map (fn [[_ v] _]
            (force v))
          contents
          (repeat nil))))

  clojure.lang.IPersistentCollection

  (count [_]
    (if contents
      (.count contents)
      0))

  (empty [_]
    (LazyMap. (if contents (.empty contents) {})))

  (cons [_ o]
    (LazyMap. (.cons (or contents {}) o)))

  (equiv [this o]
    (if (or (instance? clojure.lang.IPersistentMap o)
            (instance? java.util.Map o))
      (.equiv
       ^clojure.lang.IPersistentCollection
       (into {} this) o)
      false))

  clojure.lang.IPersistentMap

  (assoc [_ key val]
    (LazyMap. (.assoc (or contents {}) key val)))

  (without [_ key]
    (LazyMap. (.without (or contents {}) key)))

  clojure.lang.Seqable

  (seq [_]
    ;; Using the higher-arity form of map prevents chunking.
    (map (fn [[k v] _]
           (lazy-map-entry k v))
         contents
         (repeat nil)))

  java.lang.Iterable

  (iterator [this]
    (.iterator
     ^java.lang.Iterable
     (.seq this)))

  java.lang.Object

  (equals [this o]
    (.equiv this o))

  (toString [this]
    (str (freeze-map (->PlaceholderText "<unrealized>") this))))

(alter-meta!
  #'->LazyMap assoc :doc
  "Turn a regular map into a lazy map. Any values that are delays
  are interpreted as values that have yet to be realized.")

(extend-print LazyMap #(.toString ^LazyMap %))

;;;; Functions for working with lazy maps

(defn ->?LazyMap
  "Behaves the same as ->LazyMap, except that if m is already a lazy
  map, returns it directly. This prevents the creation of a lazy map
  wrapping another lazy map, which (while not terribly wrong) is not
  the best idea."
  ^LazyMap [map]
  (if (instance? LazyMap map)
    map
    (->LazyMap map)))

(defmacro lazy-map
  "Constructs a lazy map from a literal map. None of the values are
  evaluated until they are accessed from the map."
  [map]
  `(->LazyMap
     ~(->> map
        (apply concat)
        (partition 2)
        (clojure.core/map (fn [[k v]] [k `(delay ~v)]))
        (into {}))))

(defn force-map
  "Realizes all the values in a lazy map, returning a regular map."
  [map]
  (into {} map))

(defn freeze-map
  "Replace all the unrealized values in a lazy map with placeholders,
  returning a regular map. No matter what is done to the returned map,
  the values in the original map will not be forced. v can be an
  object to use for all the values or a function of the key."
  [val map]
  (let [val (if (fn? val)
              val
              (constantly val))]
    (reduce-kv (fn [m k v]
                 (assoc m k (if (and (delay? v)
                                     (not (realized? v)))
                              (val k)
                              (force v))))
               {}
               (.contents ^LazyMap map))))

(defn lazy-map-dispatch
  "This is a dispatch function for clojure.pprint that prints
  lazy maps without forcing them."
  [obj]
  (cond
    (instance? LazyMap obj)
    (pp/simple-dispatch (freeze-map (->PlaceholderText "<unrealized>") obj))

    (instance? LazyMapEntry obj)
    (pp/simple-dispatch
     (let [raw-value (.val_ obj)]
       (assoc obj 1 (if (and (delay? raw-value)
                             (not (realized? raw-value)))
                      (->PlaceholderText "<unrealized>")
                      (force raw-value)))))
    (instance? PlaceholderText obj)
    (pr obj)

    :else
    (pp/simple-dispatch obj)))
