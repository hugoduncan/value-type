(ns value-type.identity-receiving-cross-jvm-test
  "Cross-JVM identity-receiving tests: children from :baseline and :valhalla."
  (:require [clojure.test :refer [deftest]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.identity-receiving-checks :as identity-checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]
            [value-type.value-capability :as value-capability]))

(defn- check-cell
  "Return the cell that calls the identity-receiving-checks function check
   with context and expected; the cell defines in its own namespace."
  [check context expected & {:as options}]
  (merge {:context [context]
          :form (registry/check-form (symbol "value-type.identity-receiving-checks"
                                             (name check)))
          :expected expected}
         options))

(defn- run-check-cells
  "Run one cell child for each [child cell] pair; child is the child
   configuration (runtime, execution and policy)."
  [pairs]
  (harness/run-cells (for [[child cell] pairs]
                       (harness/cell-child child [cell]))))

(def ^:private cell-ns
  "Namespace of the only cell of a one-cell child."
  (registry/cell-ns-name 0))

(def ^:private defined registry/new-definition)

(deftest receiving-identity-policy-test
  ;; Fixed identity analysis must not upgrade on a capable receiving JVM.
  (let [configuration (config/configuration)]
    (run-check-cells
     (for [runtime (config/receivers configuration)
           policy config/policies]
       (let [expected (if (= "error" policy)
                        (harness/failed-definition harness/fallback-context {})
                        (harness/fixed (merge {:same-class? true :type "java.lang.String"}
                                              defined)))]
         [(assoc runtime :policy policy)
          (check-cell 'receiving-policy
                      (harness/runtime-context runtime policy)
                      (harness/described
                       (harness/entries
                        (registry/each identity-checks/holder-types expected)))
                      :fallback? true
                      :warning harness/fallback-context)])))))

(deftest incompatible-value-receiving-test
  ;; A fixed value expansion must reject baseline receivers, not select identity.
  (let [{:keys [baseline] :as configuration} (config/configuration)
        holder (registry/class-name cell-ns 'Holder)]
    (run-check-cells
     (for [runtime (config/incompatible-receivers configuration)
           policy config/policies
           :let [extra-names (when (= baseline runtime)
                               ["no value classes" "Use a JEP 401 JDK"])]]
       [(assoc runtime :policy policy)
        (check-cell
         'incompatible-value
         (pr-str [(:java runtime) policy])
         (harness/described
          (harness/observed
           [:capability :reason]
           (harness/detail keyword?)
           (fn [reason]
             (harness/keyed
              {:class-name (harness/fixed holder)
               :capability (harness/including {:available? (harness/fixed false)})
               :outcome (value-capability/unavailable
                         holder {:reason reason} extra-names)})))))]))))

(deftest generated-value-receiving-policy-test
  ;; Identity-class field tags must not trigger identity representation policy.
  (let [{:keys [valhalla]} (config/configuration)]
    (run-check-cells
     (for [policy config/policies]
       [(assoc valhalla :policy policy)
        (check-cell 'generated-value
                    (str "generated value enforcement under " policy)
                    {:definition defined
                     :value? true
                     :type "java.lang.String"
                     :final? true
                     :annotations []
                     :constructions (checks/each-construction {:stored "valid"
                                                               :rejections checks/checked-rejections})})]))))

(deftest strict-nested-public-declaration-test
  ;; A valid concrete child must not bypass strict representation policy.
  (let [configuration (config/configuration)]
    (run-check-cells
     (for [runtime (config/incompatible-receivers configuration)
           execution config/executions]
       [(config/child-configuration runtime execution "error")
        (check-cell
         'strict-nested
         (pr-str [(:java runtime) execution])
         (let [rejected (fn [detail]
                          {:outcome (assoc (harness/policy-rejection detail) :data nil)
                           :owner nil :factory nil})
               declarations (fn [detail]
                              (transfer/each-expand
                               (registry/each identity-checks/parent-types
                                              (rejected detail))))]
           (harness/described
            (harness/detailed
             true
             (fn [detail]
               {:child defined :declarations (declarations detail)})))))]))))

(deftest nested-public-warning-scope-test
  ;; Nested declarations share runtime warning state, not per-type warning state.
  (let [configuration (config/configuration)
        child (registry/class-name cell-ns 'Child)
        declaration {:definition defined
                     :cell-loader? true
                     :type child
                     :constructions (checks/each-construction
                                     {:stored-input? true
                                      :rejections checks/checked-rejections})}]
    (run-check-cells
     (for [runtime (config/incompatible-receivers configuration)
           execution config/executions
           policy ["silent" "warn"]]
       [(config/child-configuration runtime execution policy)
        (check-cell 'nested-warning-scope
                    (pr-str [(:java runtime) execution policy])
                    {:child defined
                     :declarations (transfer/each-expand
                                    (registry/each identity-checks/parent-types
                                                   declaration))}
                    :fallback? true
                    :warning :any)]))))

