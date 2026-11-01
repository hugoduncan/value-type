(ns value-type.field-transfer-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]
            [value-type.value-capability :as value-capability]))

(defn- each-expansion
  "Return the specs [namespace-name expand] of n cells that each receive
   an expansion of each expand of transfer/expands."
  [n]
  (vec (for [i (range n) expand transfer/expands] [(registry/cell-ns-name i) expand])))

(defn- cell-expansions
  "Return the expansions of cell i, one for each expand (each-expansion
   order)."
  [expansions i]
  (let [n (count transfer/expands)]
    (subvec expansions (* n i) (* n (inc i)))))

(defn- produce
  "Run one producer child from runtime that evaluates check (a
   field-transfer-checks produce function) on specs. Return the
   expansions, or nil after a failing support assertion."
  [runtime check specs]
  (harness/produce runtime (registry/check-form check specs)))

(defn- tagged
  "Return the produce specs [tag namespace-name expand] of specs with tag."
  [tag specs]
  (mapv #(into [tag] %) specs))

(defn- unavailable
  "Return the expected result predicate of consume of class-name on a
   receiver without the capability."
  [class-name]
  (let [attempts (fn [capability]
                   (->> (value-capability/unavailable class-name capability [])
                        (harness/repeated registry/attempt-count)
                        (harness/repeated (count transfer/expands))))]
    (harness/described
     (harness/observed [:capability :reason]
                       (harness/detail keyword?)
                       #(harness/including {:attempts (attempts {:reason %})})))))

(defn- unsupported-cell
  "Return the cell at index that consumes expansions in mode and expects
   the unavailable failure of simple-name."
  [context index expansions mode simple-name]
  {:context context
   :form (registry/check-form 'value-type.field-transfer-checks/consume (vec expansions) mode)
   :expected (unavailable (registry/class-name (registry/cell-ns-name index) simple-name))})

(deftest unsupported-public-transfer-test
  ;; Neither native nor generated checks permit a value representation downgrade.
  (let [{:keys [valhalla] :as configuration} (config/configuration)
        tags ['Long 'String]
        cells (vec (for [tag tags mode transfer/modes] [tag mode]))]
    ;; One producer gives the expansions of each cell, with the cell tag.
    (when-let [expansions (produce valhalla 'value-type.field-transfer-checks/produce
                                   (vec (for [[ns-name [tag]] (harness/cell-specs cells)
                                              expand transfer/expands]
                                          [(list 'quote tag) ns-name expand])))]
      (harness/run-cells
       (for [runtime (config/incompatible-receivers configuration)
             execution config/executions
             policy config/policies
             :let [child (config/child-configuration runtime execution policy)
                   label (harness/runtime-context child policy)]]
         (harness/cell-child
          child
          (vec (map-indexed
                (fn [i [tag mode]]
                  (unsupported-cell [label (str tag) (name mode)] i
                                    (cell-expansions expansions i)
                                    mode "TransferredField"))
                cells))))))))

(defn- supported
  "Return the expected consume-supported result of tag."
  [tag identity?]
  (let [string? (= tag 'String)
        expected (assoc registry/new-definition
                        :identity? identity?
                        :type (if string? "java.lang.String" "java.lang.Long")
                        :native? (and (not identity?) (not string?))
                        :constructions
                        (checks/each-construction {:class? true :stored (if string? "valid" 42)
                                                   :rejections checks/checked-rejections}))]
    {:initially-absent? true :attempts (registry/each-attempt expected)}))

(deftest supported-public-transfer-test
  ;; Receiving compilation installs fixed native or generated enforcement.
  (let [{:keys [valhalla]} (config/configuration)
        tags ['Long 'String]
        cells (vec (for [tag tags mode transfer/modes [expand-index expand] (map-indexed vector transfer/expands)]
                     [tag mode expand-index expand]))]
    ;; One producer gives the expansion of each cell, with the cell tag.
    (when-let [expansions (produce valhalla 'value-type.field-transfer-checks/produce
                                   (mapv (fn [[ns-name [tag _ _ expand]]] [(list 'quote tag) ns-name expand])
                                         (harness/cell-specs cells)))]
      (harness/run-cells
       (for [execution config/executions
             policy config/policies]
         (harness/cell-child
          (config/child-configuration valhalla execution policy)
          (vec (map-indexed
                (fn [i [tag mode expand-index]]
                  {:context [(pr-str [execution policy mode expand-index])
                             (str tag)]
                   :form (registry/check-form 'value-type.field-transfer-checks/consume-supported (expansions i) mode)
                   :expected (supported tag false)})
                cells))))))))

(def ^:private identity-cells
  (vec (for [mode transfer/modes [expand-index expand] (map-indexed vector transfer/expands)]
         [mode expand-index expand])))

(deftest identity-public-transfer-test
  ;; Baseline-selected identity fields must not upgrade on a capable receiver.
  (let [{:keys [baseline] :as configuration} (config/configuration)]
    (when-let [expansions (produce baseline 'value-type.field-transfer-checks/produce
                                   (tagged ''String (harness/cell-specs (map last identity-cells))))]
      (harness/run-cells
       (for [runtime (config/receivers configuration)
             execution config/executions
             policy ["silent" "warn"]
             :let [child (config/child-configuration runtime execution policy)]]
         (harness/cell-child
          child
          (mapv (fn [[mode expand-index] serialized]
                  {:context [(harness/runtime-context child policy mode expand-index)]
                   :form (registry/check-form 'value-type.field-transfer-checks/consume-supported serialized mode)
                   :expected (supported 'String true)
                   :fallback? true
                   :warning harness/fallback-context})
                identity-cells expansions)))))))

;; Each cell attempts each expansion, so the attempts of one mode share the
;; cell namespace; each attempt is rejected, so no Class is defined.
(defn- attempts-of [expected]
  (let [attempts (transfer/each-expand (registry/each-attempt expected))]
    (harness/described (harness/including {:attempts (harness/fixed attempts)}))))

(deftest strict-identity-public-transfer-test
  ;; Strict receivers reject fixed identity output even when value output is available.
  (let [{:keys [baseline] :as configuration} (config/configuration)]
    (when-let [expansions (produce baseline 'value-type.field-transfer-checks/produce
                                   (tagged ''String (each-expansion (count transfer/modes))))]
      (harness/run-cells
       (for [runtime (config/receivers configuration)
             execution config/executions]
         (harness/cell-child
          (config/child-configuration runtime execution "error")
          (vec (map-indexed
                (fn [i mode]
                  {:context [(harness/runtime-context runtime execution)
                             (name mode)]
                   :form (registry/check-form 'value-type.field-transfer-checks/consume (cell-expansions expansions i) mode)
                   :expected (attempts-of (assoc harness/fallback-rejection
                                                 :data nil :absent? true))
                   :fallback? true
                   :warning harness/fallback-context})
                transfer/modes))))))))

(def ^:private mixed
  (let [construction {:class? true :stored [42 "valid"]
                      :rejections [checks/checked-rejections checks/checked-rejections]}
        attempt (assoc registry/new-definition
                       :type ["java.lang.Long" "java.lang.String"]
                       :native [true false]
                       :constructions (checks/each-construction construction))]
    (registry/each-attempt attempt)))

(deftest mixed-public-transfer-test
  ;; A fresh receiver must preserve both enforcement modes in one public owner.
  (let [{:keys [valhalla]} (config/configuration)]
    (when-let [expansions (produce valhalla 'value-type.field-transfer-checks/produce-mixed
                                   (harness/cell-specs (map last identity-cells)))]
      (harness/run-cells
       (for [execution config/executions
             policy config/policies]
         (harness/cell-child
          (config/child-configuration valhalla execution policy)
          (mapv (fn [[mode expand-index] serialized]
                  {:context [(pr-str [execution policy mode expand-index])]
                   :form (registry/check-form 'value-type.field-transfer-checks/consume-mixed serialized mode)
                   :expected mixed})
                identity-cells expansions)))))))

(deftest unsupported-mixed-public-transfer-test
  ;; Mixed enforcement must not downgrade or install an unchecked owner.
  (let [{:keys [valhalla] :as configuration} (config/configuration)]
    (when-let [expansions (produce valhalla 'value-type.field-transfer-checks/produce-mixed
                                   (each-expansion (count transfer/modes)))]
      (harness/run-cells
       (for [runtime (config/incompatible-receivers configuration)
             execution config/executions
             policy config/policies]
         (harness/cell-child
          (config/child-configuration runtime execution policy)
          (vec (map-indexed
                (fn [i mode]
                  (unsupported-cell [(pr-str [(:java runtime) execution policy]) (name mode)]
                                    i (cell-expansions expansions i)
                                    mode "TransferredMixed"))
                transfer/modes))))))))
