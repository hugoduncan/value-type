(ns value-type.array-info-boundary-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as capability]
            [value-type.capability-rows :as rows]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.jvm-facts :as facts]))

(def ^:private invalid-inputs
  "Input forms that array-info rejects as :not-reference-array."
  `[nil "not an array" (long-array 1)])

(def ^:private valid-input
  "Input form of a reference array."
  `(object-array 1))

(def ^:private inspect-form
  "Child form of a function of a reference array that gives its array-info
   with the component class name."
  `(fn [input#]
     (update (value-type.array/array-info input#)
             :component-class #(.getName ^Class %))))

(defn- control-form
  [require-order]
  `(do
     (run! require '~require-order)
     (require 'value-type.array-info-observation)
     (assoc
      (value-type.cell-registry/runtime-facts '~require-order)
      :runtime (System/getProperty "java.runtime.version")
      :native (value-type.array-info-observation/observe)
      :outcomes
      (mapv (fn [input#]
              (value-type.cell-registry/twice
               (fn []
                 (value-type.cell-registry/guarded-call
                  (fn [] (~inspect-form input#))
                  (fn [info#] {:info info#})))))
            ~(conj invalid-inputs valid-input)))))

(defn- native-expected
  [java8? supported?]
  {:component-value
   (if java8?
     {:exception "java.lang.NoSuchMethodException"}
     {:parameters [] :return "boolean" :static? false
      :invocation {:value false}})
   :array
   (if java8?
     {:exception "java.lang.ClassNotFoundException"}
     (into {}
           (map (fn [[method-name value]]
                  [method-name
                   {:parameters ["java.lang.Object"]
                    :return "boolean" :static? true
                    :invocation (if supported?
                                  {:value value}
                                  {:exception "java.lang.IllegalAccessException"})}]))
           {"isNullRestrictedArray" false
            "isAtomicArray" true
            "isFlatArray" false}))})

(def ^:private supported-info
  {:component-class "java.lang.Object"
   :component-value? false :null-restricted? false
   :atomic? true :flat? false})

(def ^:private invalid-outcome
  {:status :rejected
   :exception capability/rejection-exception
   :data {:value-type/error :invalid-array :reason :not-reference-array}
   :cause nil})

(def ^:private invalid-expected
  "[description predicate] of the outcome of an invalid input, without
   :message."
  [invalid-outcome #(= invalid-outcome (dissoc % :message))])

(def ^:private unavailable-names
  "The texts that an inspection capability rejection message names."
  [capability/export-name])

(defn- unavailable-expected
  "Return [description predicate] of the inspection rejection of reason and
   cause."
  [reason cause]
  (let [outcome {:status :rejected
                 :exception capability/rejection-exception
                 :data {:value-type/error :array-inspection-unavailable :reason reason}
                 :cause cause}]
    (harness/with-entry outcome :message (harness/contains unavailable-names))))

(defn- valid-expected
  "Return [description predicate] of the outcome of the valid input."
  [supported? reason cause]
  (if supported?
    [{:info supported-info} (partial = {:info supported-info})]
    (unavailable-expected reason cause)))

(defn- runtime-build [java8?]
  (:build (facts/select (facts/read-facts)
                        (if java8? :baseline :valhalla) :linux-x64 :plain)))

(defn- control-expected
  [configuration java8? supported? reason cause policy require-order]
  (let [build (runtime-build java8?)]
    (harness/described
     (harness/keyed
      (assoc (capability/runtime-expected
              (harness/fixed build)
              configuration policy require-order)
             :native (harness/including (update-vals (native-expected java8? supported?)
                                                     harness/fixed))
             :outcomes (harness/entries
                        (conj (registry/each invalid-inputs
                                             (capability/equal-repeated invalid-expected))
                              (capability/equal-repeated
                               (valid-expected supported? reason cause)))))))))

(def ^:private inspection-causes
  "Inspection rejection reason to the class name of its cause."
  {:missing-array-api "java.lang.ClassNotFoundException"
   :array-access-denied "java.lang.IllegalAccessException"})

(defn- inspection-rows
  "Return the inspection rows of each runtime label
   (array-capability/runtimes): [label supported? reason cause], from the
   `:array-inspection` column of capability-rows/report."
  []
  (for [[label] (capability/runtimes (config/configuration))
        :let [{:keys [available? reason]} (:array-inspection (rows/report label))]]
    [label available? reason (inspection-causes reason)]))

(deftest safe-inspection-boundary-test
  ;; Crash-risk public inputs. Configuration and policy select the child
  ;; (rules 1, 2). Require order is process-wide: each order is a one-cell
  ;; child (rule 5). Empty cell *err* replaces the fallback check.
  (let [configurations (config/configuration)]
    (harness/run-cells
     (for [[label supported? reason cause] (inspection-rows)
           :let [configuration (capability/runtime configurations label)]
           policy config/policies
           require-order capability/require-orders]
       (harness/cell-child
        (assoc configuration :policy policy)
        [{:context [(pr-str [label policy require-order])]
          :form (control-form require-order)
          :expected (control-expected configuration (= label :java8) supported?
                                      reason cause policy require-order)}])))))

(def ^:private export-only-reason
  "The allocation rejection reason of the export-only runtime."
  (:reason (:array-allocation (rows/report :export-only))))

(def ^:private export-only-form
  `(do
     (require 'value-type.array)
     (let [input# (object-array 1)]
       {:before (~inspect-form input#)
        :allocation
        (value-type.cell-registry/twice
         (fn []
           (value-type.cell-registry/rejected-call
            #(value-type.array/make-array nil -1 nil))))
        :after (~inspect-form input#)})))

(def ^:private export-only-expected
  (harness/described
   (harness/keyed
    {:before (harness/fixed supported-info)
     :allocation (capability/repeated-rejection export-only-reason)
     :after (harness/fixed supported-info)})))

(deftest export-only-capabilities-test
  ;; Inspection must not inherit allocation's preview prerequisite. Policy is
  ;; process-wide, so each policy is a one-cell child.
  (let [export-only (capability/runtime (config/configuration) :export-only)]
    (harness/run-cells
     (for [policy config/policies]
       (harness/cell-child
        (assoc export-only :policy policy)
        [{:context [policy]
          :form export-only-form
          :expected export-only-expected}])))))

(def ^:private independent-inspection-form
  `(do
     (require 'value-type.array)
     (let [info# (~inspect-form (object-array 0))]
       {:info info#
        :allocation-realized?
        (realized? @(ns-resolve 'value-type.impl.array '~'detected-allocation-capability))
        :value-realized?
        (realized? @(ns-resolve 'value-type.impl.capability '~'detected-capability))
        :allocation-loaded? (boolean (find-ns 'value-type.impl.array-allocation))
        :inspection-loaded? (boolean (find-ns 'value-type.impl.array-inspection))})))

(deftest independent-inspection-test
  ;; Real inspection must leave both allocation and value discovery untouched.
  (let [configuration (config/configuration)]
    (harness/run-cells
     (for [label [:export-only :export]
           disabled? [false true]
           :let [runtime (cond-> (capability/runtime configuration label)
                           disabled? (update :jvm-opts conj "-Dvalue-type.value-classes=off"))]]
       (harness/cell-child
        (assoc runtime :policy "silent")
        [{:context [(pr-str [label disabled?])]
          :form independent-inspection-form
          :expected (harness/described
                     (harness/fixed
                      {:info supported-info
                       :allocation-realized? false :value-realized? false
                       :allocation-loaded? false :inspection-loaded? true}))}])))))

(def ^:private independent-allocation-form
  `(do
     (require 'value-type.array)
     (let [result# (value-type.impl.array/allocation-capability)]
       {:available? (:available? result#)
        :cached? (identical? result# (value-type.impl.array/allocation-capability))
        :inspection-realized?
        (realized? @(ns-resolve 'value-type.impl.array '~'detected-inspection-capability))
        :allocation-loaded? (boolean (find-ns 'value-type.impl.array-allocation))
        :inspection-loaded? (boolean (find-ns 'value-type.impl.array-inspection))})))

(deftest independent-allocation-test
  ;; Successful discovery runs the real allocation self-check, not diagnostics.
  (harness/run-cells
   [(harness/cell-child
     (assoc (capability/runtime (config/configuration) :export) :policy "silent")
     [{:context ["allocation discovery"]
       :form independent-allocation-form
       :expected (harness/described
                  (harness/fixed
                   {:available? true :cached? true
                    :inspection-realized? false
                    :allocation-loaded? true :inspection-loaded? false}))}])]))

(def ^:private disabled-allocation-form
  `(do
     (require 'value-type.array 'value-type.capability)
     (let [rejection# (try (value-type.array/make-array nil -1 nil)
                           (catch clojure.lang.ExceptionInfo error#
                             (select-keys (ex-data error#) [:value-type/error :reason])))
           info# (~inspect-form (object-array 0))
           report# (value-type.capability/report)]
       {:rejection rejection#
        :info info#
        :report (select-keys report# [:value-class :array-allocation :array-inspection
                                      :fallback-policy])
        :allocation-loaded? (boolean (find-ns 'value-type.impl.array-allocation))
        :inspection-loaded? (boolean (find-ns 'value-type.impl.array-inspection))})))

(deftest disabled-allocation-independent-inspection-test
  ;; The switch blocks allocation before validation but does not block inspection.
  (harness/run-cells
   (for [label [:export-only :export]
         :let [runtime (-> (capability/runtime (config/configuration) label)
                           (update :jvm-opts conj "-Dvalue-type.value-classes=off")
                           (assoc :policy "silent"))]]
     (harness/cell-child
      runtime
      [{:context [(name label)]
        :form disabled-allocation-form
        :expected (harness/described
                   (harness/fixed
                    {:rejection {:value-type/error :array-unavailable :reason :disabled}
                     :info supported-info
                     :report {:value-class {:available? false :reason :disabled}
                              :array-allocation {:available? false :reason :disabled}
                              :array-inspection {:available? true}
                              :fallback-policy :silent}
                     :allocation-loaded? false :inspection-loaded? true}))}]))))
