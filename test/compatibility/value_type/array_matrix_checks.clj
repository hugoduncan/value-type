(ns value-type.array-matrix-checks
  "Baseline-safe child checks. Value fixtures are evaluated only on
   supported rows."
  (:require [value-type.cell-registry :as registry]))

(defn- unavailable-call
  []
  (registry/rejected-call #((resolve 'value-type.array/make-array) nil -1 nil)))

(defn- allocation
  []
  (eval
   '(do
      (value-type.core/defvaluetype MatrixPoint [^long x])
      (let [definition (value-type.cell-registry/defined *ns* 'MatrixPoint MatrixPoint 0)
            seed (MatrixPoint. 7)
            replacement (MatrixPoint. 9)
            first-array (value-type.array/make-array MatrixPoint 3 seed)
            second-array (value-type.array/make-array MatrixPoint 3 seed)
            initial? (= [seed seed seed] (vec first-array))]
        (aset first-array 1 replacement)
        (merge
         {:status :allocated
          :component? (= MatrixPoint (.getComponentType (class first-array)))
          :length (alength first-array)
          :initial? initial?
          :changed? (= [seed replacement seed] (vec first-array))
          :independent? (= [seed seed seed] (vec second-array))}
         definition)))))

(defn check
  "Load public namespaces in order, then return structured boundary outcomes.
   A supported row gives one allocation outcome; it is the definition map of
   MatrixPoint (cell-registry/defined keys added)."
  [require-order supported?]
  (run! require require-order)
  (assoc (registry/runtime-facts require-order)
         :outcomes (if supported? [(allocation)] (registry/twice unavailable-call))))
