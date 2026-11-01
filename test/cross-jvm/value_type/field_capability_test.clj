(ns value-type.field-capability-test
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

(defn- reasons
  "Return the explicit child configurations and their expected capability
   failure reason (nil when supported)."
  []
  (let [{:keys [baseline preview-off valhalla]} (config/configuration)]
    [[baseline :missing-classfile-api]
     [preview-off :unsupported-class-version]
     [valhalla nil]]))

(deftest capability-matrix-test
  ;; Require remains lazy; explicit child configurations determine expectations.
  ;; Rule 5: the first capability detection needs a fresh library runtime, so
  ;; each configuration is a one-cell child.
  (harness/run-cells
   (for [[configuration reason] (reasons)]
     (harness/cell-child
      configuration
      [{:context [(harness/runtime-context configuration)]
        :form '(do
                 (require 'value-type.core 'value-type.impl.runtime)
                 (let [cache @#'value-type.impl.runtime/detected-field-capability
                       lazy? (not (realized? cache))
                       result (value-type.impl.runtime/field-capability)]
                   {:lazy? lazy? :capability (dissoc result :detail)
                    :cached? (identical? result (value-type.impl.runtime/field-capability))}))
        :expected {:lazy? true :cached? true
                   :capability (if reason {:available? false :reason reason}
                                   {:available? true})}}]))))

(deftest malformed-probe-test
  ;; Policy must not weaken real linkage, verification or enforcement errors.
  ;; Policy and execution options are process-wide: one child for each pair.
  (let [{:keys [valhalla]} (config/configuration)]
    (harness/run-cells
     (for [policy config/policies
           execution config/executions]
       (harness/cell-child
        (config/child-configuration valhalla execution policy)
        [{:context [(pr-str [policy execution])]
          :form '(do
                   (require 'value-type.impl.field-capability 'value-type.cell-registry)
                   (let [emit value-type.impl.capability/probe-bytes
                         verify @#'value-type.impl.field-capability/verify-enforcement
                         major (java.lang.classfile.ClassFile/latestMajorVersion)
                         loader (clojure.lang.DynamicClassLoader.)
                         define (fn [name descriptor restricted?]
                                  (.defineClass loader name (emit major name descriptor restricted?) nil))
                         outcome #(value-type.cell-registry/failed-call
                                   (fn [e] (symbol (.getName (class e))))
                                   %)
                         leaf (define "FieldProbeLeaf" "J" false)
                         holder (define "FieldProbeNullable" "LFieldProbeLeaf;" false)]
                     [(outcome #(verify leaf holder))
                      (outcome #(define "FieldProbePrimitive" "J" true))
                      (outcome #(let [c (define "FieldProbeBadSlots" "I" false)]
                                  (.getConstructors c)))
                      (outcome #(let [c (define "FieldProbeIdentity" "Ljava/lang/String;" true)]
                                  (.getConstructors c)))]))
          :expected '[java.lang.IllegalStateException
                      java.lang.ClassFormatError
                      java.lang.VerifyError
                      java.lang.IncompatibleClassChangeError]}])))))

(deftest required-capability-policy-matrix-test
  ;; Native requirements never warn, fall back, or become successful on retry.
  ;; Policy is process-wide: one child for each configuration and policy.
  (harness/run-cells
   (for [[configuration reason] (reasons)
         policy config/policies]
     (let [expected (if reason
                      {:value-type/error :field-capability-unavailable
                       :class-name "RequiredHolder"
                       :reason reason}
                      :supported)]
       (harness/cell-child
        (assoc configuration :policy policy)
        [{:context [(harness/runtime-context configuration policy)]
          :form '(do
                   (require 'value-type.impl.runtime)
                   (let [attempt (fn []
                                   (try (value-type.impl.runtime/require-field-capability!
                                         "RequiredHolder")
                                        :supported
                                        (catch clojure.lang.ExceptionInfo e (ex-data e))))]
                     (value-type.cell-registry/twice attempt)))
          :expected (registry/each-attempt expected)}])))))

(defn- policy-expected
  "Return the predicate on a field-policy-checks result {:detail :attempts}:
   with a reason, detail is present, else nil (harness/detailed); under
   error, each attempt is the rejection for detail merged with rejected;
   else each attempt is success."
  [reason policy rejected success]
  (let [rejected? (and reason (= "error" policy))]
    (harness/described
     (harness/detailed
      (boolean reason)
      (fn [detail]
        {:attempts (registry/each-attempt
                    (if rejected?
                      (merge (harness/policy-rejection detail) rejected)
                      success))})))))

(defn- policy-cell
  "Return a cell that evaluates (check & args) after requiring the check
   namespace. With a reason, the cell is a fallback cell; its current check
   counts warning lines, so the warning context is :any."
  [context reason expected check & args]
  {:context [context]
   :form (apply registry/check-form check args)
   :expected expected
   :fallback? (boolean reason)
   :warning :any})

(deftest public-field-policy-test
  ;; Representation policy is independent of field enforcement.
  ;; Policy is process-wide: one child for each configuration and policy.
  (harness/run-cells
   (for [[configuration reason] (reasons)
         policy config/policies]
     (harness/cell-child
      (assoc configuration :policy policy)
      [(policy-cell (harness/runtime-context configuration policy)
                    reason
                    (policy-expected reason policy {} :expanded)
                    'value-type.field-policy-checks/expansion)]))))

(deftest public-field-evaluation-policy-test
  ;; Public constructors retain checks in both representations.
  ;; Policy and execution options are process-wide: one child for each
  ;; configuration, policy and execution; one cell for each field tag. Each
  ;; cell defines in its own namespace. The warn String cell matches the
  ;; error String cell of the same configuration and execution.
  (harness/run-cells
   (for [[configuration reason] (reasons)
         policy config/policies
         execution config/executions]
     (harness/cell-child
      (config/child-configuration configuration execution policy)
      (vec
       (for [[tag valid invalid expected-type]
             [['Long 7 "wrong" "java.lang.Long"]
              ['String "retained" 7 "java.lang.String"]]]
         (policy-cell (harness/runtime-context configuration policy execution tag)
                      reason
                      (policy-expected
                       reason policy {:absent? true}
                       (assoc registry/new-definition
                              :type expected-type
                              :constructions
                              (checks/each-construction
                               {:stored valid :rejections checks/checked-rejections})))
                      'value-type.field-policy-checks/evaluation
                      (list 'quote tag) valid invalid)))))))
