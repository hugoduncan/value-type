(ns value-type.child-transfer-checks
  "Baseline-safe checks of serialized parent expansions against a receiver
   child. Checks evaluate in the current namespace and return inspected facts;
   a failure gives the root failure class and message
   (cell-registry/failure)."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.transfer-checks :as transfer])
  (:import (java.lang.reflect Field)))

(defn- define-child
  "Define TransferChild with fields; a value declaration when native?."
  [native? fields]
  (eval (list (if native? 'value-type.core/defvaluetype 'deftype)
              'TransferChild fields)))

(defn produce
  "Expand a parent against the producer's child definition, once for each
   spec [namespace-name expand] (transfer-checks/serialize), in that
   namespace. Return the serialized expansions in spec order; each has the
   class name of its namespace."
  [native? specs]
  (mapv (fn [[namespace-name expand]]
          (transfer/serialize namespace-name expand
                              '(value-type.core/defvaluetype TransferParent
                                 [^TransferChild ^:non-nil child])
                              :prepare #(define-child native? '[producer])))
        specs))

(defn consume
  "Resolve the transferred constraint against a different receiver child.

   Return the child field names, exact-class?, value?, final? and annotation
   names (consumer-aot-checks/field-facts) of the child field and, per
   construction (direct, factory, reflective), stored-input? of a valid child
   and the checked outcomes of nil and Object
   (consumer-aot-checks/checked-outcomes).
   :definitions holds the definition maps of TransferChild and
   TransferParent (cell-registry/defined)."
  ([serialized mode] (consume serialized mode false))
  ([serialized mode native?]
   (let [n *ns*
         ^Class child (define-child native? '[receiver replacement])
         child-definition (registry/defined n 'TransferChild child :a :b)
         input ((ns-resolve n '->TransferChild) :receiver :replacement)
         ^Class owner (transfer/evaluate serialized mode)
         owner-definition (registry/defined n 'TransferParent owner input)
         ^Field field (.getField owner "child")]
     (merge
      (select-keys (checks/field-facts field) [:final? :annotations])
      {:child-fields (sort (map #(.getName ^Field %) (.getFields child)))
       :exact-class? (identical? child (.getType field))
       :value? (checks/value-class? owner)
       :constructions
       (checks/input-results n owner field input (Object.))
       :definitions {'TransferChild child-definition
                     'TransferParent owner-definition}}))))

(defn reject-replaced-child
  "Install a receiver-local replacement child, then evaluate serialized twice.

   Return :definitions (the TransferChild definition map) and :attempts:
   per evaluation, the root failure (or :accepted), whether the child is
   retained, and :absent? of TransferParent (cell-registry/absent?)."
  [serialized mode]
  (let [n *ns*
        child (define-child false '[receiver replacement])]
    {:definitions {'TransferChild (registry/defined n 'TransferChild child :a :b)}
     :attempts
     (registry/twice
      (fn []
        {:outcome (registry/outcome registry/failure
                                    #(transfer/evaluate serialized mode))
         :child-retained? (identical? child (ns-resolve n 'TransferChild))
         :absent? (registry/absent? n 'TransferParent)}))}))
