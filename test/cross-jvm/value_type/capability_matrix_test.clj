(ns value-type.capability-matrix-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as array-capability]
            [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]))

(def ^:private require-orders
  "The rotations of the three public namespaces: each loads first once."
  [['value-type.capability 'value-type.core 'value-type.array]
   ['value-type.core 'value-type.array 'value-type.capability]
   ['value-type.array 'value-type.capability 'value-type.core]])

(defn- expected-call
  "Return the expected result of an array call for a capability result:
   available, or the ex-data map {:value-type/error error :reason reason}."
  [{:keys [available? reason]} available error]
  (if available? available {:value-type/error error :reason reason}))

(defn- expected
  "Return the described expected child result of runtime label and
   policy."
  [label policy]
  (let [{:keys [value-class native-non-nil-field array-allocation
                array-inspection]
         :as report}
        (rows/report label)
        rejected? (and (not (:available? value-class)) (= "error" policy))
        context (harness/rejection-context {:baseline? (= :java8 label)})]
    (harness/described
     (harness/keyed
      {:report (harness/fixed report)
       :final (harness/fixed report)
       :owner (harness/fixed report)
       :definition (cond rejected? (harness/failed-definition context {})
                         (:available? value-class) (harness/fixed :value)
                         :else (harness/fixed :identity))
       :native (harness/fixed (if rejected?
                                :skipped
                                (:available? native-non-nil-field)))
       :allocation (harness/fixed
                    (expected-call array-allocation :allocated :array-unavailable))
       :inspection (harness/fixed
                    (expected-call array-inspection :inspected
                                   :array-inspection-unavailable))}))))

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
        :expected (expected label policy)
        :fallback? fallback?
        :warning :any}]))))
