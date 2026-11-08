(ns value-type.ordinary-transfer-analysis-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]))

(defn- definition
  [ns-name shape]
  (if (= :deftype shape)
    '(value-type.core/defvaluetype Transferred [x])
    (list 'value-type.core/defvaluetype* 'Transferred
          (symbol (registry/class-name ns-name 'Transferred))
          (if (= :plain shape) '[x] (into '[x] registry/record-tail)))))

(deftest ordinary-transfer-analysis-test
  ;; Compilation alone must reject fixed value output, before invocation.
  (let [configuration (config/configuration)
        specs (harness/cell-specs
               (for [shape [:deftype :plain :record]
                     expand transfer/expands]
                 [shape expand]))
        producer-form
        (registry/check-form
         'value-type.transfer-checks/produce-forms
         (list 'quote
               (mapv (fn [[ns-name [shape expand]]]
                       [ns-name expand (definition ns-name shape)])
                     specs)))]
    (when-let [expansions (harness/produce (config/value-producer configuration)
                                           producer-form)]
      (harness/run-cells
       (for [receiver (config/incompatible-receivers configuration)]
         (harness/cell-child
          (assoc receiver :policy "silent")
          (mapv (fn [[ns-name [shape expand]] serialized]
                  {:context [(name shape) (name expand) (:java receiver)
                             (pr-str (:jvm-opts receiver))]
                   :form (registry/check-form
                          'value-type.transfer-checks/compile-only serialized)
                   :expected
                   (harness/described
                    (harness/including
                     {:class (harness/fixed "clojure.lang.Compiler$CompilerException")
                      :cause-data
                      (harness/including
                       {:value-type/error
                        (harness/fixed :value-capability-unavailable)
                        :class-name
                        (harness/fixed (registry/class-name ns-name 'Transferred))})}))})
                specs expansions)))))))
