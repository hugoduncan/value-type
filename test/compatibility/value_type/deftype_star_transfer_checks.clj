(ns value-type.deftype-star-transfer-checks
  "Checks of serialized `defvaluetype*` fallback expansions in a receiving
   cell. Each expansion names the class Transferred in the namespace of its
   receiving cell."
  (:require [value-type.cell-registry :as registry]
            [value-type.core]
            [value-type.impl.capability :as capability]
            [value-type.transfer-checks :as transfer]))

(def shapes
  "Field shapes, in cell order."
  [:plain :record])

(defn- fields [shape]
  (case shape
    :plain '[x]
    :record ['x '__meta '__extmap
             (with-meta '__hash {:tag 'int})
             (with-meta '__hasheq {:tag 'int})]))

(defn- form [namespace-name shape]
  (list 'value-type.core/defvaluetype* 'Transferred
        (symbol (registry/class-name namespace-name 'Transferred))
        (fields shape)))

(defn produce
  "Expand the defvaluetype* form of shape for each spec [namespace-name
   expand shape]. Return the expansions printed with metadata (hash-field
   tags survive), in spec order."
  [specs]
  (mapv (fn [[namespace-name expand shape]]
          (transfer/serialize namespace-name expand (form namespace-name shape)))
        specs))

(defn consume
  "Evaluate serialized twice in the current namespace in mode (see
   transfer-checks/evaluate). Return per attempt {:named? :value?
   :hash-fields :new-class?} or the root failure."
  [serialized mode]
  (let [class-name (registry/class-name *ns* 'Transferred)]
    (registry/twice
     (fn []
       (registry/outcome registry/failure
                         #(transfer/evaluate serialized mode)
                         (fn [^Class c]
                           {:named? (= class-name (.getName c))
                            :value? (capability/value-class? c)
                            :hash-fields (registry/hash-field-count c)
                            :new-class? (registry/new-class! c)}))))))
