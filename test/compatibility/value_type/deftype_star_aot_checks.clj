(ns value-type.deftype-star-aot-checks
  "Baseline-safe checks of saved `defvaluetype*` output in source-free
   consumer JVMs."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks])
  (:import (clojure.lang Reflector)))

(def fixture-source
  "Source of the star.aot-fixture namespace: one plain and one record-shaped
   `defvaluetype*`."
  (str "(ns star.aot-fixture (:require [value-type.core :as vt]))\n"
       "(vt/defvaluetype* Plain star.aot_fixture.Plain [x])\n"
       "(vt/defvaluetype* Rec star.aot_fixture.Rec"
       " [x __meta __extmap ^int __hash ^int __hasheq])\n"))

(def classes
  "Saved class name and constructor arguments of each shape."
  {:plain ["star.aot_fixture.Plain" [42]]
   :record ["star.aot_fixture.Rec" [42 nil nil]]})

(defn- facts [^String class-name arguments]
  (let [c (Class/forName class-name)
        o (Reflector/invokeConstructor c (object-array arguments))]
    {:value? (checks/value-class? c)
     :stored (.get (.getField c "x") o)
     :hash-fields (registry/hash-field-count c)}))

(defn consume
  "Require the saved star.aot-fixture without source. Return per shape the
   facts {:value? :stored :hash-fields}, or the root failure of the load."
  []
  (assert (checks/source-absent? "star/aot_fixture.clj"))
  (registry/outcome registry/failure
                    (fn []
                      (require 'star.aot-fixture)
                      (update-vals classes #(apply facts %)))
                    identity))

(defn expected
  "Expected consume result of a successful load of value? output."
  [value?]
  {:plain {:value? value? :stored 42 :hash-fields 0}
   :record {:value? value? :stored 42 :hash-fields (if value? 0 2)}})
