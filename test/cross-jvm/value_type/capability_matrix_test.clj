(ns value-type.capability-matrix-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as array-capability]
            [value-type.capability-expectations :as expectations]
            [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]))

(def ^:private require-orders
  "The rotations of the three public namespaces: each loads first once."
  [['value-type.capability 'value-type.core 'value-type.array]
   ['value-type.core 'value-type.array 'value-type.capability]
   ['value-type.array 'value-type.capability 'value-type.core]])

(deftest capability-report-matrix-test
  ;; Acceptance 1 and 3. Require order is process-wide: one one-cell child
  ;; for each runtime, policy and order (54 children).
  (harness/run-cells
   (for [[label runtime] (array-capability/runtimes (config/configuration))
         policy config/policies
         require-order require-orders
         :let [fallback? (not (:available? (:value-class (rows/report label))))]]
     (harness/cell-child
      (assoc runtime :policy policy)
      [{:context [(pr-str [label policy require-order])]
        :form (registry/check-form 'value-type.capability-matrix-checks/check
                                   (list 'quote require-order))
        :expected (expectations/expected
                   label policy
                   (harness/rejection-context {:baseline? (= :java8 label)}))
        :fallback? fallback?
        :warning :any}]))))
