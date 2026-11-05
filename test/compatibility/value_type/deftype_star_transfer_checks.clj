(ns value-type.deftype-star-transfer-checks
  "Checks of serialized `defvaluetype*` fallback expansions in a receiving
   cell. Each expansion names the class Transferred in the namespace of its
   receiving cell."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.capability :as capability]
            [value-type.transfer-checks :as transfer])
  (:import (clojure.lang Reflector)))

(def shapes
  "Field shapes, in cell order."
  [:plain :record])

(defn- first-field
  "Return the first field: x, with enabled? a non-nil Long field."
  [enabled?]
  (if enabled? (with-meta 'x {:tag 'Long :non-nil true}) 'x))

(defn- fields [shape enabled?]
  (case shape
    :plain [(first-field enabled?)]
    :record (into [(first-field enabled?)] registry/record-tail)))

(defn- form [namespace-name shape enabled?]
  (list 'value-type.core/defvaluetype* 'Transferred
        (symbol (registry/class-name namespace-name 'Transferred))
        (fields shape enabled?)))

(defn produce
  "Expand the defvaluetype* form of shape for each spec [namespace-name
   expand shape enabled?]. With enabled?, the first field is the non-nil
   field ^Long ^:non-nil x. Return the expansions printed with metadata
   (hash-field tags survive), in spec order."
  [specs]
  (mapv (fn [[namespace-name expand shape enabled?]]
          (transfer/serialize namespace-name expand
                              (form namespace-name shape enabled?)))
        specs))

(defn- construct
  "Call the one-argument constructor of Class c with x by reflection: the
   main constructor of the plain shape, the declared-fields constructor of
   the record shape."
  [^Class c x]
  (Reflector/invokeConstructor c (object-array [x])))

(defn- evaluate-twice
  "Evaluate serialized twice in the current namespace in mode. Return per
   attempt {:named? :value? :hash-fields :new-class?} merged with
   (extra-facts c), or (failure e)."
  [serialized mode failure extra-facts]
  (let [class-name (registry/class-name *ns* 'Transferred)]
    (registry/twice
     (fn []
       (registry/outcome failure
                         #(transfer/evaluate serialized mode)
                         (fn [^Class c]
                           (merge {:named? (= class-name (.getName c))
                                   :value? (capability/value-class? c)
                                   :hash-fields (registry/hash-field-count c)
                                   :new-class? (registry/new-class! c)}
                                  (extra-facts c))))))))

(defn consume
  "Evaluate serialized twice in the current namespace in mode (see
   transfer-checks/evaluate). Return per attempt {:named? :value?
   :hash-fields :new-class?} or the root failure."
  [serialized mode]
  (evaluate-twice serialized mode registry/failure (constantly nil)))

(defn consume-enabled
  "Evaluate serialized, an expansion with the non-nil field x, twice in
   the current namespace in mode. Return per attempt the consume keys and
   the facts of x (consumer-aot-checks/enabled-facts), or the root failure
   with :data (cell-registry/root-failure)."
  [serialized mode]
  (evaluate-twice serialized mode registry/root-failure
                  (fn [^Class c]
                    (checks/enabled-facts (.getField c "x")
                                          #(construct c %)))))
