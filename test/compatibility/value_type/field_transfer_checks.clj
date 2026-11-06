(ns value-type.field-transfer-checks
  "Checks of serialized public-field expansions in a receiver. Each check
   evaluates in the current namespace; the producer expands in the namespace
   of the receiving cell, so the class name of an expansion is that of the
   cell namespace."
  (:require [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.impl.capability :as capability]
            [value-type.transfer-checks :as transfer])
  (:import (java.lang.reflect Field)))

(defn produce
  "Serialize a public definition of TransferredField, once for each spec
   [tag namespace-name expand], with the field tag of the spec, in spec
   order."
  [specs]
  (mapv (fn [[tag namespace-name expand]]
          (transfer/serialize namespace-name expand
                              (list 'value-type.core/defvaluetype 'TransferredField
                                    [(with-meta 'value {:tag tag :non-nil true})])))
        specs))

(defn produce-mixed
  "Serialize a public definition with native and generated constraints, once
   for each spec [namespace-name expand], in spec order."
  [specs]
  (transfer/produce '(value-type.core/defvaluetype TransferredMixed
                       [^Long ^:non-nil number ^String ^:non-nil text])
                    true specs))

(defn- attempt
  "Return cell-registry/unexpected-success, else the root failure
   (cell-registry/root-failure) with :absent? of both transferred
   owners (cell-registry/absent?)."
  [serialized mode]
  (registry/failed-call #(assoc (registry/root-failure %)
                                :absent? (and (registry/absent? *ns* 'TransferredField)
                                              (registry/absent? *ns* 'TransferredMixed)))
                        #(transfer/evaluate serialized mode)))

(defn consume
  "Receive each serialized expansion twice in mode (transfer-checks/evaluate).
   Return the receiver :capability and the :attempts of each expansion."
  [expansions mode]
  {:capability (capability/capability)
   :attempts (mapv (fn [serialized]
                     (registry/twice (fn [] (attempt serialized mode))))
                   expansions)})

(defn consume-supported
  "Install one serialized TransferredField twice in mode. Return
   :initially-absent? (cell-registry/absent?) and per attempt its
   definition map (cell-registry/defined), identity, storage and the
   construction results (consumer-aot-checks/construction-results) with
   :class?."
  [serialized mode]
  (let [n *ns*]
    {:initially-absent? (registry/absent? n 'TransferredField)
     :attempts
     (registry/twice
      (fn []
        (let [^Class installed (transfer/evaluate serialized mode)
              ^Field field (.getField installed "value")
              input (if (= String (.getType field)) "valid" 42)
              definition (registry/defined n 'TransferredField installed input)
              factory (ns-resolve n '->TransferredField)]
          (merge definition
                 (checks/enforcement-facts field)
                 {:identity? (registry/identity? factory input)
                  :constructions
                  (checks/construction-results
                   n installed field input (Object.)
                   :facts (fn [value] {:class? (identical? installed (class value))}))}))))}))

(defn consume-mixed
  "Install one serialized TransferredMixed twice in mode. Return per attempt
   its definition map (cell-registry/defined), the field types (:type) and
   storage, and per construction (consumer-aot-checks/builders) the checked
   outcomes (consumer-aot-checks/checked-outcomes) of each field."
  [serialized mode]
  (let [n *ns*]
    (registry/twice
     (fn []
       (let [^Class installed (transfer/evaluate serialized mode)
             definition (registry/defined n 'TransferredMixed installed 42 "valid")
             fields (mapv #(.getField installed ^String %) ["number" "text"])]
         (merge definition
                {:type (mapv (comp :type checks/field-facts) fields)
                 :native (mapv checks/native-field? fields)
                 :constructions
                 (mapv (fn [build]
                         (let [value (build [42 "valid"])]
                           {:class? (identical? installed (class value))
                            :stored (mapv #(.get ^Field % value) fields)
                            :rejections
                            [(checks/checked-outcomes
                              (fn [[number]] (build [number "valid"])) (Object.))
                             (checks/checked-outcomes
                              (fn [[text]] (build [42 text])) (Object.))]}))
                       (checks/builders n installed))}))))))
