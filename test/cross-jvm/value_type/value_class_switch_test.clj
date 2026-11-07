(ns value-type.value-class-switch-test
  "The value-type.value-classes switch: off gives :disabled on every JVM,
   auto changes nothing, and an invalid value throws at the first
   capability read."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.array-capability :as array-capability]
            [value-type.capability-expectations :as expectations]
            [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.field-aot-test :as field-aot]
            [value-type.jvm-config :as config]
            [value-type.public-field-aot-test :as public-field-aot]
            [value-type.temp-dir :as temp]))

(def ^:private disabled-detail
  "Value classes are disabled by value-type.value-classes=off.")

(defn- switched
  "Return runtime with the switch option of value."
  [runtime value]
  (update runtime :jvm-opts (fnil conj []) (str "-Dvalue-type.value-classes=" value)))

(defn- runtimes
  "Return the map of label to switch runtime, in insertion order."
  []
  (let [configuration (config/configuration)
        export (array-capability/runtime configuration :export)]
    (array-map
     :export-off (switched export "off")
     :java8-off (switched (:baseline configuration) "off")
     :export-auto (switched export "auto"))))

(defn- child
  "Return a one-cell child of runtime with policy. The cell has the
   context label context, the check form and expected; cell gives more
   cell options (for example :fallback? and :warning)."
  [runtime policy context form expected & {:as cell}]
  (harness/cell-child
   (assoc runtime :policy policy)
   [(merge {:context [context] :form form :expected expected} cell)]))

(deftest switch-report-test
  ;; Acceptance 1, 2 and 4: off rows are :disabled on EA3 and Java 8; the
  ;; definition falls back by policy with the :disabled detail; auto
  ;; changes nothing.
  (harness/run-cells
   (for [[label runtime] (runtimes)
         policy config/policies
         :let [fallback? (not (:available? (:value-class (rows/report label))))]]
     (child runtime policy (pr-str [label policy])
            (registry/check-form 'value-type.capability-matrix-checks/check
                                 ''[value-type.core value-type.array
                                    value-type.capability])
            (expectations/expected label policy disabled-detail)
            :fallback? fallback?
            :warning disabled-detail))))

(def ^:private allocation-rejection
  {:status :rejected
   :exception "clojure.lang.ExceptionInfo"
   :message (str "Value-array allocation is unavailable. " disabled-detail)
   :data {:value-type/error :array-unavailable
          :reason :disabled
          :detail disabled-detail}
   :cause nil})

(deftest switch-allocation-message-test
  ;; Acceptance 1: make-array and into-array name the :disabled detail.
  (let [rows (runtimes)]
    (harness/run-cells
     (for [label [:export-off :java8-off]]
       (child (rows label) "silent" (pr-str [label "messages"])
              (registry/check-form 'value-type.value-class-switch-checks/allocation-messages)
              {:make-array allocation-rejection
               :into-array allocation-rejection})))))

(deftest switch-probe-test
  ;; Design "Scope": off defines no probe class. The child JVM logs class
  ;; loads; auto shows that the log records the probe.
  (temp/with-temp-dir
    "value-class-switch"
    (fn [dir]
      (let [rows (runtimes)]
        (harness/run-cells
         (for [[label expected] [[:export-off {:reason :disabled :probe-loaded? false}]
                                 [:export-auto {:reason nil :probe-loaded? true}]]
               :let [runtime (rows label)
                     log (str (io/file dir (str (name label) ".log")))]]
           (child (update runtime :jvm-opts conj (str "-Xlog:class+load:file=" log))
                  "silent" (pr-str [label "probe"])
                  (registry/check-form 'value-type.value-class-switch-checks/probe-loads log)
                  expected)))))))

(deftest switch-fixed-read-test
  ;; Design "Scope": the switch is read once per loaded library runtime; a
  ;; later property change has no effect.
  (harness/run-cells
   [(child (:export-off (runtimes)) "silent" (pr-str [:export-off "fixed read"])
           (registry/check-form 'value-type.value-class-switch-checks/fixed-read)
           {:first :disabled :second :disabled})]))

(defn- invalid-message [value]
  (str "Invalid value-type.value-classes: " (pr-str value) "; expected auto or off"))

(deftest invalid-value-test
  ;; Acceptance 3: an invalid value throws at the first read, the same
  ;; exception at each later read, through each entry point but array-info.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (harness/run-cells
     (for [[label runtime inspection-reason]
           [[:no-export valhalla :array-access-denied]
            [:java8 baseline :missing-array-api]]
           value ["OFF" " off" ""]]
       (child (switched runtime value) "silent" (pr-str [label value])
              (registry/check-form 'value-type.value-class-switch-checks/invalid-value)
              {:thrown {:class "java.lang.IllegalArgumentException"
                        :message (invalid-message value)
                        :chain ["java.lang.IllegalArgumentException"]}
               :second-identical? true
               :definition {:class "clojure.lang.Compiler$CompilerException"
                            :cause-identical? true}
               :make-array-identical? true
               :into-array-identical? [true true]
               :effects 0
               :inspection {:value-type/error :array-inspection-unavailable
                            :reason inspection-reason}})))))

(deftest invalid-policy-first-test
  ;; report reads the policy before the capability.
  (harness/run-cells
   [(child (switched (:baseline (config/configuration)) "OFF") "unknown"
           "java8 invalid policy and switch"
           (registry/check-form 'value-type.value-class-switch-checks/policy-first)
           {:class "java.lang.IllegalArgumentException"
            :message (registry/invalid-policy-message "unknown")
            :chain ["java.lang.IllegalArgumentException"]})]))

(def ^:private restricted-load
  (registry/check-form 'value-type.value-class-switch-checks/aot-load
                       ''restricted.aot-fixture "restricted/aot_fixture.clj"))

(defn- restricted-consumers
  "Compile restricted.aot-fixture as value output (no switch property), then
   run restricted-load source-free on Valhalla with the switch value under
   policies. expected is the described :load outcome."
  [value policies expected]
  (let [{:keys [valhalla] :as configuration} (config/configuration)]
    (fixture/source-free-consumers
     'restricted.aot-fixture field-aot/restricted-source
     (config/value-producer configuration)
     [(switched valhalla value)] policies
     restricted-load
     (constantly (harness/described
                  (harness/keyed {:source-absent? (harness/fixed true)
                                  :load expected}))))))

(deftest aot-native-field-off-test
  ;; Contract: value output with native non-nil fields checks the field
  ;; capability at initialization, so off throws :disabled.
  (testing "off rejects saved native-field value output under every policy"
    (restricted-consumers
     "off" config/policies
     (harness/keyed
      {:class (harness/fixed "clojure.lang.ExceptionInfo")
       :message (harness/contains [disabled-detail])
       :data (harness/fixed {:value-type/error :field-capability-unavailable
                             :reason :disabled})}))))

(deftest aot-native-field-invalid-test
  ;; Contract: initialization of native-field value output reads the switch.
  (testing "an invalid value throws at initialization"
    (restricted-consumers
     "OFF" ["error"]
     (harness/keyed
      {:class (harness/fixed "java.lang.IllegalArgumentException")
       :message (harness/fixed (invalid-message "OFF"))
       :data (harness/fixed {})}))))

(deftest aot-value-output-off-test
  ;; Interim limit until task 026: other value output does not read the
  ;; switch, so off keeps value classes.
  (testing "off keeps saved value output without native fields"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture public-field-aot/identity-source
       (config/value-producer configuration)
       [(switched valhalla "off")] ["error"]
       (checks/holder-consumer-form checks/string-holder)
       (constantly (checks/string-holder-result true))))))
