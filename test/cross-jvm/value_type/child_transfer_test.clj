(ns value-type.child-transfer-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]))

(def ^:private cell-specs
  "One consumer cell for each receiving mode and expansion form, in cell
   order. Cell i runs in namespace cell.i, which is also the producer
   namespace of its expansion, so each cell has its own class name."
  (vec (for [mode transfer/modes
             expand transfer/expands]
         [mode expand])))

(defn- produce
  "Run one producer child from runtime; return its expansions, one for each
   cell spec, or nil after a failing support assertion."
  [runtime native?]
  (harness/produce
   runtime
   (registry/check-form 'value-type.child-transfer-checks/produce native?
                        (harness/cell-specs (map second cell-specs)))))

(defn- consumer-child
  "Return a cell child that calls check with each expansion, its mode and
   the args vector, and expects expected. label gives the first context of a
   mode."
  [configuration label expansions check args expected fallback?]
  (harness/cell-child
   configuration
   (mapv (fn [[mode expand] serialized]
           {:context [(label mode) (name expand)]
            :form (apply registry/check-form check serialized mode args)
            :expected expected
            :fallback? fallback?
            :warning harness/fallback-context})
         cell-specs expansions)))

(def ^:private definitions
  {'TransferChild registry/new-definition
   'TransferParent registry/new-definition})

(defn- consumed
  "Return the expected consume result of a value? parent with annotations."
  [value? annotations]
  {:child-fields ["receiver" "replacement"] :exact-class? true
   :value? value? :final? true :annotations annotations
   :constructions (checks/each-construction {:stored-input? true
                                             :rejections checks/checked-rejections})
   :definitions definitions})

(deftest receiver-child-replacement-test
  ;; Serialized field tags resolve to the receiver Class, not producer storage.
  (let [{:keys [baseline] :as configuration} (config/configuration)]
    (when-let [expansions (produce baseline false)]
      (harness/run-cells
       (for [runtime (config/receivers configuration)
             policy ["silent" "warn"]
             execution config/executions]
         (consumer-child (config/child-configuration runtime execution policy)
                         #(harness/runtime-context runtime policy execution %)
                         expansions 'value-type.child-transfer-checks/consume [false]
                         (consumed false []) true))))))

(defn- value-parent-replacement
  "Check that a value parent produced against a native? child resolves the
   receiver child under every policy, with the expected annotations."
  [native? annotations]
  (let [{:keys [valhalla] :as configuration} (config/configuration)]
    (when-let [expansions (produce (config/value-producer configuration) native?)]
      (harness/run-cells
       (for [policy config/policies
             execution config/executions]
         (consumer-child (config/child-configuration valhalla execution policy)
                         #(pr-str [native? policy execution %])
                         expansions 'value-type.child-transfer-checks/consume [native?]
                         (consumed true annotations) false))))))

(deftest value-receiver-child-replacement-test
  ;; A fixed value parent resolves an identity child locally under every policy.
  (value-parent-replacement false []))

(deftest native-receiver-child-replacement-test
  ;; Native storage resolves the replacement value child under every policy.
  (value-parent-replacement true checks/native-annotations))

(deftest strict-receiver-child-replacement-test
  ;; Replacement must not let a fixed identity expansion bypass strict policy.
  (let [{:keys [baseline] :as configuration} (config/configuration)]
    (when-let [expansions (produce baseline false)]
      (harness/run-cells
       (for [runtime (config/receivers configuration)
             execution config/executions]
         (consumer-child (config/child-configuration runtime execution "error")
                         #(harness/runtime-context runtime execution %)
                         expansions 'value-type.child-transfer-checks/reject-replaced-child []
                         {:definitions (select-keys definitions ['TransferChild])
                          :attempts (registry/each-attempt {:outcome harness/fallback-rejection
                                                            :child-retained? true
                                                            :absent? true})}
                         true))))))
