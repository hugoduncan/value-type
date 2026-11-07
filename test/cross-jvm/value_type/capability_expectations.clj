(ns value-type.capability-expectations
  "Expected results of value-type.capability-matrix-checks/check."
  (:require [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]))

(defn expected-call
  "Return the expected result of an array call for a capability result:
   available, or the ex-data map {:value-type/error error :reason reason}."
  [{:keys [available? reason]} available error]
  (if available? available {:value-type/error error :reason reason}))

(defn expected
  "Return the described expected child result of runtime label and
   policy. context is the fallback context of a strict policy rejection
   (see cell-harness/failed-definition)."
  [label policy context]
  (let [{:keys [value-class native-non-nil-field array-allocation
                array-inspection]
         :as report}
        (assoc (rows/report label) :fallback-policy (keyword policy))
        rejected? (and (not (:available? value-class)) (= "error" policy))]
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
