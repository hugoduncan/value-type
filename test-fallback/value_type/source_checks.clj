(ns value-type.source-checks
  "Source checks executed in isolated policy JVMs."
  (:require [value-type.core :refer [defvaluetype]]))

(defprotocol Reading
  (reading [this]))

(deftype Ordinary [^long n ^String text]
  :load-ns true
  Reading
  (reading [_] n)
  java.util.concurrent.Callable
  (call [_] text)
  Object
  (toString [_] (str text ":" n)))

(defvaluetype Portable [^long n ^String text]
  :load-ns true
  Reading
  (reading [_] n)
  java.util.concurrent.Callable
  (call [_] text)
  Object
  (toString [_] (str text ":" n)))

(defn- observations
  [^Class cls factory]
  (let [a (factory 42 "sample")
        b (factory 42 "sample")]
    {:factory (= cls (class a))
     :fields (mapv #(.getName (.getType (.getDeclaredField cls %))) ["n" "text"])
     :protocol (reading a)
     :interface (.call ^java.util.concurrent.Callable a)
     :method (str a)
     :same (identical? a a)
     :distinct (not (identical? a b))
     :unequal (not= a b)
     :hash (= (.hashCode a) (System/identityHashCode a))
     :clojure-hash (= (hash a) (.hashCode a))
     :lookup (get {a :found} a)
     :other-lookup (get {a :found} b :absent)}))

(defn compatibility
  "Compare fallback source behaviour with ordinary deftype."
  []
  {:ordinary (observations Ordinary ->Ordinary)
   :portable (observations Portable ->Portable)
   :hinted-field (.-n ^Portable (->Portable 42 "sample"))
   :constructor (= Portable (class (Portable. 42 "sample")))
   :import (eval '(do (import 'value_type.source_checks.Portable)
                      (= Portable (class (Portable. 42 "sample")))))})
