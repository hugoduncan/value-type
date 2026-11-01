(ns value-type.loader-policy-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.identity-loader-checks :as loader-checks]
            [value-type.jvm-config :as config]))

(defn- case-form
  "Cell form: run check (a symbol in identity-loader-checks) for one case in
   the cell namespace (own LOADER frame); *ns* is read once, on the cell
   thread."
  [check [expand mode]]
  (registry/check-form (symbol "value-type.identity-loader-checks" (name check))
                       '*ns* expand mode))

(deftest fresh-policy-loader-rejection-test
  ;; Real stale imports must fail even when strict policy permits value owners.
  ;; Policy and execution options are process-wide: one child for each
  ;; configuration, one cell for each [expand mode] case. With no
  ;; value support each case is a fallback cell; the error-policy children
  ;; are the warn-guard evidence of the grouped warn cells.
  (let [{:keys [baseline preview-off valhalla]} (config/configuration)]
    (harness/run-cells
     (concat
      (for [[runtime policies warning] [[baseline ["silent" "warn"] harness/unavailable-detail]
                                        [preview-off ["silent" "warn"] :any]
                                        [valhalla config/policies nil]]
            policy policies
            execution config/executions
            :let [label (harness/runtime-context runtime policy execution)]]
        (harness/cell-child
         (config/child-configuration runtime execution policy)
         (for [case-key loader-checks/cases]
           {:context [label (pr-str case-key)]
            :form (case-form 'stale-sibling-case case-key)
            :expected loader-checks/public-stale-sibling-expected
            :fallback? (some? warning)
            :warning warning})))
      (for [[runtime warning] [[baseline harness/unavailable-detail]
                               [preview-off :any]]
            execution config/executions
            :let [label (harness/runtime-context runtime "error" execution)]]
        (harness/cell-child
         (config/child-configuration runtime execution "error")
         (for [case-key loader-checks/cases]
           {:context [label (pr-str case-key)]
            :form (case-form 'stale-sibling-strict-case case-key)
            :expected (harness/described (harness/failed-definition warning {}))
            :fallback? true})))))))
