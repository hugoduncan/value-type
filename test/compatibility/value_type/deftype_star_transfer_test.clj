(ns value-type.deftype-star-transfer-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as aot-checks]
            [value-type.deftype-star-transfer-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.transfer-checks :as transfer]))

(defn- shape-cells
  "Receiving mode, expansion and shape of each cell, for each of shapes."
  [shapes]
  (vec (for [mode transfer/modes expand transfer/expands shape shapes]
         [mode expand shape])))

(defn- transfer-cells
  "Produce the expansion of each cell of shapes with producer (a function
   of the configuration), the first field non-nil with enabled?. Consume each
   expansion with the check function symbol consume, under each policy.
   (expected configuration policy shape ns-name) gives the expected value
   of a cell; cell-keys add to each cell."
  [shapes producer enabled? consume expected & {:as cell-keys}]
  (let [{:keys [selected] :as configuration} (config/configuration)
        cells (shape-cells shapes)
        specs (mapv (fn [[ns-name [_ expand shape]]]
                      [ns-name expand shape enabled?])
                    (harness/cell-specs cells))]
    (when-let [expansions (harness/produce
                           (producer configuration)
                           (registry/check-form
                            'value-type.deftype-star-transfer-checks/produce
                            specs))]
      (harness/run-cells
       (for [policy config/policies]
         (harness/cell-child
          (assoc selected :policy policy)
          (mapv (fn [[mode expand shape] serialized [ns-name]]
                  (merge {:context [policy (name mode) (name expand) (name shape)]
                          :form (registry/check-form consume serialized mode)
                          :expected (expected configuration policy shape ns-name)}
                         cell-keys))
                cells expansions specs)))))))

(def ^:private fallback-keys
  "Cell keys of a fixed fallback expansion."
  {:fallback? true :warning harness/fallback-context})

(defn- meta-rejected?
  "Expected rejection of a non-nil __meta argument for shape: nil for the
   plain shape (no such constructor), true only for the closed shape."
  [shape]
  (case shape :plain nil :record false :closed true))

(defn- identity-output
  "Facts of identity output of shape, merged with extra."
  [shape extra]
  (merge {:named? true :value? false :new-class? true
          :hash-fields (if (= :plain shape) 0 2)
          :meta-rejected? (meta-rejected? shape)}
         extra))

(deftest deftype*-transferred-expansion-test
  ;; Fallback expansions of a real incapable producer enforce receiving
  ;; policy in each mode; they keep the hash fields. Plain and record cells
  ;; are fixed fallback expansions; closed cells are identity receiving
  ;; expansions (`identity-field-deftype*`) that keep the D10 check.
  (transfer-cells
   checks/shapes config/identity-producer false
   'value-type.deftype-star-transfer-checks/consume
   (fn [_ policy shape _]
     (registry/each-attempt
      (if (= "error" policy)
        harness/fallback-rejection
        (identity-output shape nil))))
   fallback-keys))

(deftest deftype*-enabled-identity-transfer-test
  ;; Identity receiving expansions of an incapable producer keep the
  ;; generated checks in each mode, and enforce receiving policy.
  (transfer-cells
   checks/shapes config/identity-producer true
   'value-type.deftype-star-transfer-checks/consume-enabled
   (fn [_ policy shape _]
     (registry/each-attempt
      (if (= "error" policy)
        (assoc harness/fallback-rejection :data nil)
        (identity-output shape (aot-checks/enabled-expected false)))))
   fallback-keys))

(defn- capability-unavailable
  "Expected attempts of a value expansion on a receiver without value
   capability, for the class name of the cell."
  [class-name]
  (harness/described
   (harness/repeated
    registry/attempt-count
    (harness/including
     {:class (harness/fixed "clojure.lang.ExceptionInfo")
      :data (harness/including
             {:value-type/error (harness/fixed :value-capability-unavailable)
              :class-name (harness/fixed class-name)})}))))

(deftest deftype*-enabled-value-transfer-test
  ;; Value receiving expansions on a capable receiver give value classes
  ;; with native checks and no hash fields; closed cells also reject a
  ;; non-nil __meta (D10 check). A receiver without value capability
  ;; rejects them.
  (transfer-cells
   checks/shapes config/value-producer true
   'value-type.deftype-star-transfer-checks/consume-enabled
   (fn [{:keys [available?]} _ shape ns-name]
     (if available?
       (registry/each-attempt
        (merge {:named? true :value? true :new-class? true :hash-fields 0
                :meta-rejected? (meta-rejected? shape)}
               (aot-checks/enabled-expected true)))
       (capability-unavailable
        (registry/class-name ns-name 'Transferred))))))

(deftest deftype*-ordinary-value-transfer-test
  ;; A form without enabled fields has a value receiving expansion (D20): a
  ;; capable receiver installs capture at analysis and gives a value class
  ;; with no hash fields (closed cells also reject a non-nil __meta, D10
  ;; check); a receiver without value capability rejects it at analysis.
  (transfer-cells
   checks/shapes config/value-producer false
   'value-type.deftype-star-transfer-checks/consume-data
   (fn [{:keys [available?]} _ shape ns-name]
     (if available?
       (registry/each-attempt
        {:named? true :value? true :new-class? true :hash-fields 0
         :meta-rejected? (meta-rejected? shape)})
       (capability-unavailable (registry/class-name ns-name 'Transferred))))))
