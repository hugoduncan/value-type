(ns value-type.deftype-star-aot-checks
  "Baseline-safe checks of saved `defvaluetype*` output in source-free
   consumer JVMs."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks])
  (:import (clojure.lang Reflector)))

(def fixture-source
  "Source of the star.aot-fixture namespace: one plain and one record-shaped
   `defvaluetype*` without enabled fields, then the same shapes with one
   `:non-nil` field, then the record shapes again with the closed shape."
  (str "(ns star.aot-fixture (:require [value-type.core :as vt]))\n"
       "(vt/defvaluetype* Plain star.aot_fixture.Plain [x])\n"
       "(vt/defvaluetype* Rec star.aot_fixture.Rec"
       " [x __meta __extmap ^int __hash ^int __hasheq])\n"
       "(vt/defvaluetype* PlainNonNil star.aot_fixture.PlainNonNil"
       " [^Long ^:non-nil x])\n"
       "(vt/defvaluetype* RecNonNil star.aot_fixture.RecNonNil"
       " [^Long ^:non-nil x __meta __extmap ^int __hash ^int __hasheq])\n"
       "(vt/defvaluetype* Closed star.aot_fixture.Closed"
       " [x __meta __extmap ^int __hash ^int __hasheq]"
       " :value-type/closed true)\n"
       "(vt/defvaluetype* ClosedNonNil star.aot_fixture.ClosedNonNil"
       " [^Long ^:non-nil x __meta __extmap ^int __hash ^int __hasheq]"
       " :value-type/closed true)\n"))

(def classes
  "Saved class name and constructor arguments of each fixture."
  {:plain ["star.aot_fixture.Plain" [42]]
   :record ["star.aot_fixture.Rec" [42 nil nil]]
   :plain-non-nil ["star.aot_fixture.PlainNonNil" [42]]
   :record-non-nil ["star.aot_fixture.RecNonNil" [42 nil nil]]
   :closed ["star.aot_fixture.Closed" [42 nil nil]]
   :closed-non-nil ["star.aot_fixture.ClosedNonNil" [42 nil nil]]})

(def owners
  "Saved owner class names, in fixture order."
  (mapv first (vals classes)))

(def ^:private checked
  "Fixtures with an enabled field x."
  #{:plain-non-nil :record-non-nil :closed-non-nil})

(def ^:private record-shaped
  "Fixtures with the meta-and-extmap constructor."
  #{:record :record-non-nil :closed :closed-non-nil})

(defn- construct [c arguments]
  (Reflector/invokeConstructor c (object-array arguments)))

(defn- facts [k [^String class-name arguments]]
  (let [c (Class/forName class-name)
        field (.getField c "x")]
    (cond-> {:value? (checks/value-class? c)
             :stored (.get field (construct c arguments))
             :hash-fields (registry/hash-field-count c)}
      (record-shaped k)
      (assoc :meta-rejected? (registry/meta-rejected? c))

      (checked k)
      (merge (checks/enabled-facts
              field #(construct c (assoc arguments 0 %)))))))

(defn consume
  "Require the saved star.aot-fixture without source. Return per fixture the
   facts {:value? :stored :hash-fields}, :meta-rejected? for record shapes,
   plus for an enabled field x its consumer-aot-checks/enabled-facts, or the
   root failure of the load."
  []
  (assert (checks/source-absent? "star/aot_fixture.clj"))
  (registry/outcome registry/failure
                    (fn []
                      (require 'star.aot-fixture)
                      (into {} (map (fn [[k v]] [k (facts k v)])) classes))
                    identity))

(defn expected
  "Expected consume result of a successful load of value? output."
  [value?]
  (let [record-hash (if value? 0 2)
        enabled (assoc (checks/enabled-expected value?) :value? value?)]
    {:plain {:value? value? :stored 42 :hash-fields 0}
     :record {:value? value? :stored 42 :hash-fields record-hash
              :meta-rejected? false}
     :plain-non-nil (assoc enabled :hash-fields 0)
     :record-non-nil (assoc enabled :hash-fields record-hash
                            :meta-rejected? false)
     :closed {:value? value? :stored 42 :hash-fields record-hash
              :meta-rejected? true}
     :closed-non-nil (assoc enabled :hash-fields record-hash
                            :meta-rejected? true)}))
