(ns value-type.array-test-support
  (:require [value-type.core :refer [defvaluetype]]))

(defvaluetype ArrayPoint [^long x])

(defvaluetype ArrayEmpty [])
(defvaluetype ArrayPair [^long x ^long y])
(defvaluetype ArrayReference [payload])

(defn fixtures
  "Return component, seed and replacement triples for each array value shape."
  []
  [[ArrayEmpty (ArrayEmpty.) (ArrayEmpty.)]
   [ArrayPoint (ArrayPoint. 42) (ArrayPoint. 99)]
   [ArrayPair (ArrayPair. 1 2) (ArrayPair. 3 4)]
   [ArrayReference (ArrayReference. nil) (ArrayReference. "replacement")]
   [ArrayReference (ArrayReference. "seed") (ArrayReference. nil)]])
