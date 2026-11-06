(ns value-type.controlled-value-capability-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as array-capability]
            [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]))

(def ^:private absent
  {:available? false :reason :controlled-absence})

(def ^:private cell-form
  "Load the public namespaces, then replace only the value-class accessor
   before any capability read. The definition compiles inside the scope."
  (list 'do
        '(require 'value-type.capability 'value-type.core 'value-type.array
                  'value-type.consumer-aot-checks)
        (list 'with-redefs
              ['value-type.impl.capability/capability (list 'constantly absent)]
              '(let [report (value-type.capability/report)
                     allocation (try (value-type.array/make-array Object 1 nil)
                                     :allocated
                                     (catch clojure.lang.ExceptionInfo e
                                       (select-keys (ex-data e)
                                                    [:value-type/error :reason])))
                     c (eval '(value-type.core/defvaluetype ControlledPoint [^long x]))]
                 {:report report
                  :allocation allocation
                  :value? (value-type.consumer-aot-checks/value-class? c)}))))

(deftest controlled-value-capability-test
  ;; Acceptance 4: one value-class computation feeds every dependent result.
  (let [runtime (array-capability/runtime (config/configuration) :export)]
    (harness/run-cells
     [(harness/cell-child
       (assoc runtime :policy "silent")
       [{:context ["export silent"]
         :form cell-form
         :expected {:report {:value-class absent
                             :native-non-nil-field absent
                             :array-allocation absent
                             :array-inspection {:available? true}}
                    :allocation {:value-type/error :array-unavailable
                                 :reason :controlled-absence}
                    :value? false}}])])))
