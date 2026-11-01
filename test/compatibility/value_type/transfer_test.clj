(ns value-type.transfer-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]
            [value-type.warning-checks :as warning]))

(def ^:private identity-form
  '(value-type.core/defvaluetype Transferred [x]))

(def ^:private constrained-form
  '(value-type.core/defvaluetype Transferred [^Long ^:non-nil x]))

(defn- produce
  "Run one baseline producer child that expands form once for each spec
   [namespace-name expand]. Return the expansions in spec order, or nil
   after a failing support assertion."
  [form print-meta? specs]
  (harness/produce (config/identity-producer (config/configuration))
                   (registry/check-form 'value-type.transfer-checks/produce
                                        (list 'quote form) print-meta? specs)))

(def ^:private transferred-cells
  "Receiving mode and expansion form of each cell of
   transferred-expansion-test."
  (vec (for [mode [:eval :source] expand transfer/expands] [mode expand])))

(deftest transferred-expansion-test
  ;; A real incapable producer grants no permission to independent consumers.
  (let [{:keys [selected]} (config/configuration)]
    (when-let [expansions (produce identity-form false (harness/cell-specs (map second transferred-cells)))]
      (harness/run-cells
       (for [policy config/policies]
         (harness/cell-child
          (assoc selected :policy policy)
          (mapv (fn [[mode expand] serialized]
                  {:context [policy (name mode) (name expand)]
                   :form (registry/check-form 'value-type.transfer-checks/consume serialized mode)
                   :expected (harness/described
                              (harness/entries
                               (registry/each-attempt
                                (if (= "error" policy)
                                  (harness/failed-definition harness/fallback-context {})
                                  (harness/fixed (merge {:stored 42 :identity? true}
                                                        registry/new-definition))))))
                   :fallback? true
                   :warning harness/fallback-context})
                transferred-cells expansions)))))))

(defn- concurrent-expected
  "Expected result of a warning-checks concurrent check: worker, the expected
   result of one worker, for each of the warning/worker-count workers."
  [worker]
  (vec (repeat warning/worker-count worker)))

(defn- concurrent-defined
  "Expected result of concurrent-definitions. nil-construction is :accepted
   for an unconstrained field, else the root class of the non-nil rejection."
  [nil-construction]
  (concurrent-expected
   (registry/each warning/identity-types
                  (merge {:stored 42 :identity? true
                          :nil-construction nil-construction}
                         registry/new-definition))))

(defn- concurrent-form
  "Return the cell form that calls check with serialized in cell i (namespace
   cell.i, class Transferred)."
  [check serialized i]
  (registry/check-form check serialized
                       (registry/class-name (registry/cell-ns-name i) 'Transferred)))

(defn- concurrent-cells
  "Return one fallback cell for each expansion in transfer/expands order. The
   cell calls check; its context is the prefix and the expansion name."
  [check prefix expected expansions]
  (vec (map-indexed
        (fn [i [expand serialized]]
          {:context [prefix (name expand)]
           :form (concurrent-form check serialized i)
           :expected expected
           :fallback? true
           :warning harness/fallback-context})
        (map vector transfer/expands expansions))))

(deftest concurrent-warning-scope-test
  ;; Concurrent namespaces share one warning; a new runtime gets its own warning.
  ;; Isolation rule 5: each runtime is its own child; the evidence is the first
  ;; warning of a fresh library runtime.
  (let [{:keys [selected]} (config/configuration)]
    (when-let [[serialized] (produce identity-form false (harness/cell-specs [:macroexpand]))]
      (harness/run-cells
       (for [runtime-index (range 2)]
         (harness/cell-child
          (assoc selected :policy "warn")
          [{:context [(str "independent runtime " runtime-index)]
            :form (concurrent-form 'value-type.warning-checks/concurrent-definitions serialized 0)
            :expected (concurrent-defined :accepted)
            :fallback? true
            :warning harness/fallback-context}]))))))

(deftest constrained-concurrent-warning-scope-test
  ;; Concurrent fixed fields stay silent or share one warning per receiving runtime.
  ;; Isolation rule 5: each runtime index is its own child (warning scope
  ;; across runtimes); the two expansion forms share one runtime.
  (let [{:keys [selected]} (config/configuration)]
    (when-let [expansions (produce constrained-form true (harness/cell-specs transfer/expands))]
      (harness/run-cells
       (for [execution config/executions
             runtime-index (range 2)
             policy ["silent" "warn"]]
         (harness/cell-child
          (config/child-configuration selected execution policy)
          (concurrent-cells 'value-type.warning-checks/concurrent-definitions
                            (str policy " execution " execution " runtime " runtime-index)
                            (concurrent-defined "java.lang.NullPointerException")
                            expansions)))))))

(deftest constrained-concurrent-strict-test
  ;; Strict concurrent analysis repeatedly rejects without installing definitions.
  (let [{:keys [selected]} (config/configuration)]
    (when-let [expansions (produce constrained-form true (harness/cell-specs transfer/expands))]
      (harness/run-cells
       (for [execution config/executions]
         (harness/cell-child
          (config/child-configuration selected execution "error")
          (concurrent-cells 'value-type.warning-checks/concurrent-rejections
                            (str "strict concurrent receiving " execution)
                            (concurrent-expected
                             (registry/each-attempt {:outcome harness/fallback-rejection
                                                     :absent? true}))
                            expansions)))))))
