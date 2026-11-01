(ns value-type.array-info-boundary-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as capability]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]))

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

(defn- runtime-prefix [java8?]
  (if java8? config/baseline-version-prefix config/valhalla-version-prefix))

(defn- control-expected
  [configuration java8? supported? reason cause policy require-order]
  (let [prefix (runtime-prefix java8?)]
    (harness/described
     (harness/keyed
      (assoc (capability/runtime-expected
              (harness/starts-with prefix)
              configuration policy require-order)
             :native (harness/including (update-vals (native-expected java8? supported?)
                                                     harness/fixed))
             :outcomes (harness/entries
                        (conj (registry/each invalid-inputs
                                             (capability/equal-repeated invalid-expected))
                              (capability/equal-repeated
                               (valid-expected supported? reason cause)))))))))

(def ^:private inspection-rows
  "The inspection rows of each runtime label (array-capability/runtimes):
   [label supported? reason cause]."
  [[:java8 false :missing-array-api "java.lang.ClassNotFoundException"]
   [:no-options false :array-access-denied "java.lang.IllegalAccessException"]
   [:no-export false :array-access-denied "java.lang.IllegalAccessException"]
   [:export true nil nil]
   [:export-only true nil nil]
   [:no-flattening true nil nil]])

(deftest safe-inspection-boundary-test
  ;; Crash-risk public inputs. Configuration and policy select the child
  ;; (rules 1, 2). Require order is process-wide: each order is a one-cell
  ;; child (rule 5). Empty cell *err* replaces the fallback check.
  (let [configurations (config/configuration)]
    (harness/run-cells
     (for [[label supported? reason cause] inspection-rows
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
  :unsupported-class-version)

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
