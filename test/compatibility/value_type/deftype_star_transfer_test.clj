(ns value-type.deftype-star-transfer-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.deftype-star-transfer-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]))

(def ^:private cells
  "Receiving mode, expansion and shape of each cell."
  (vec (for [mode transfer/modes expand transfer/expands shape checks/shapes]
         [mode expand shape])))

(deftest deftype*-transferred-expansion-test
  ;; Fixed fallback expansions of a real incapable producer enforce
  ;; receiving policy in each mode; they keep the hash fields.
  (let [{:keys [selected] :as configuration} (config/configuration)
        specs (mapv (fn [[ns-name [_ expand shape]]] [ns-name expand shape])
                    (harness/cell-specs cells))]
    (when-let [expansions (harness/produce
                           (config/identity-producer configuration)
                           (registry/check-form
                            'value-type.deftype-star-transfer-checks/produce specs))]
      (harness/run-cells
       (for [policy config/policies]
         (harness/cell-child
          (assoc selected :policy policy)
          (mapv (fn [[mode expand shape] serialized]
                  {:context [policy (name mode) (name expand) (name shape)]
                   :form (registry/check-form
                          'value-type.deftype-star-transfer-checks/consume
                          serialized mode)
                   :expected (registry/each-attempt
                              (if (= "error" policy)
                                harness/fallback-rejection
                                {:named? true :value? false :new-class? true
                                 :hash-fields (if (= :record shape) 2 0)}))
                   :fallback? true
                   :warning harness/fallback-context})
                cells expansions)))))))
