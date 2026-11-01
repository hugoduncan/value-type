(ns value-type.array-sequence-checks
  "Baseline-safe sequence checks; value fixtures load only on supported rows."
  (:require [value-type.cell-registry :as registry]))

(defn- unavailable-call
  [explicit?]
  (let [effects (atom 0)
        input (lazy-seq (swap! effects inc) (list nil))
        constructor (resolve 'value-type.array/into-array)]
    (-> (registry/rejected-call #(if explicit?
                                   (constructor nil nil input)
                                   (constructor nil input)))
        (assoc :effects @effects))))

(defn- construction
  []
  (eval
   '(do
      (value-type.core/defvaluetype SequencePoint [^long x])
      (let [definition (value-type.cell-registry/defined *ns* 'SequencePoint SequencePoint 0)
            seed (SequencePoint. 0)
            values [(SequencePoint. 7) (SequencePoint. 9)]
            inferred (value-type.array/into-array SequencePoint values)
            explicit (value-type.array/into-array SequencePoint seed values)
            empty-array (value-type.array/into-array SequencePoint seed nil)]
        {:outcomes (mapv (fn [array expected]
                           {:status :constructed
                            :component? (= SequencePoint (.getComponentType (class array)))
                            :length (alength array)
                            :contents? (= expected (vec array))})
                         [inferred explicit empty-array] [values values []])
         :definitions {'SequencePoint definition}}))))

(defn check
  "Load namespaces in order and report both sequence arities without
   diagnostics. A supported row adds :definitions with the definition map of
   SequencePoint (cell-registry/defined)."
  [require-order supported?]
  (run! require require-order)
  (let [constructed (when supported? (construction))]
    (cond-> (assoc (registry/runtime-facts require-order)
                   :outcomes (if supported?
                               (:outcomes constructed)
                               (mapv (fn [explicit?]
                                       {:explicit? explicit?
                                        :calls (registry/twice (fn [] (unavailable-call explicit?)))})
                                     [false true])))
      supported? (assoc :definitions (:definitions constructed)))))
