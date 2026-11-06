(ns value-type.field-policy-checks
  "Baseline-safe checks of public non-nil field declarations under fallback
   policy. Each check runs twice and returns data; a failed attempt gives
   the root failure class and message."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.capability :as capability])
  (:import (java.lang.reflect Field)))

(defn- policy-attempts
  "Return the capability detail and two outcomes of f (cell-registry/outcome):
   (success value) of its value, or (failure-of error). The default
   success is identity and the default failure-of is the root failure."
  ([f]
   (policy-attempts registry/failure f identity))
  ([failure-of f success]
   {:detail (:detail (capability/capability))
    :attempts (registry/twice #(registry/outcome failure-of f success))}))

(defn expansion
  "Macroexpand-1 a RequiredPublic declaration with a Long non-nil field.
   An attempt gives :expanded on success."
  []
  (policy-attempts #(when (macroexpand-1 '(value-type.core/defvaluetype RequiredPublic
                                            [^Long ^:non-nil value]))
                      :expanded)))

(defn evaluation
  "Evaluate an EvaluatedField declaration with a non-nil field of tag in the
   cell namespace (*ns*, read once), then construct it directly, by factory
   and reflectively. Valid and invalid are field values. A successful
   attempt is the definition map {:type :constructions [..] :new-class?
   :factory?} with the field type; each construction gives the stored valid
   value and the outcomes of nil and invalid. A failed attempt is the root
   failure with :absent?."
  [tag valid invalid]
  (let [cell-ns *ns*]
    (policy-attempts
     #(registry/failed-definition registry/failure % cell-ns 'EvaluatedField)
     #(eval (list 'value-type.core/defvaluetype 'EvaluatedField
                  [(with-meta 'value {:tag tag :non-nil true})]))
     (fn [_]
       (let [^Class owner (ns-resolve cell-ns 'EvaluatedField)
             ^Field field (.getField owner "value")]
         (assoc (registry/defined cell-ns 'EvaluatedField owner valid)
                :type (:type (checks/field-facts field))
                :constructions
                (checks/construction-results cell-ns owner field valid invalid)))))))
