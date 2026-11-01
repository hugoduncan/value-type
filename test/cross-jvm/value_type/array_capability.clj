(ns value-type.array-capability
  "Array capability axis and expectations of the cross-JVM array tests."
  (:require [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def export-option
  "The JVM option that exports the JDK value internals."
  "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")

(def export-name
  "The option name that an array capability message names."
  "--add-exports")

(def require-orders
  "The require-order axis: each order of `value-type.array` and
   `value-type.core`. Require order is process-wide, so each order is a
   one-cell child."
  [['value-type.array 'value-type.core]
   ['value-type.core 'value-type.array]])

(defn- equal-outcomes?
  "Return true when the outcomes (of each attempt of cell-registry/twice)
   are equal. The count rule is cell-harness/repeated."
  [outcomes]
  (or (empty? outcomes) (apply = outcomes)))

(defn equal-repeated
  "Return [description predicate] (see cell-harness/described) of
   cell-registry/attempt-count equal outcomes that each pass [description
   predicate] expected (cell-harness/repeated)."
  [expected]
  (let [[description repeated?] (harness/repeated registry/attempt-count expected)]
    [description #(and (repeated? %) (equal-outcomes? %))]))

(defn runtimes
  "Return the array runtimes of configuration (jvm-config/configuration):
   a vector of [label runtime], in this order: baseline (:java8), preview
   off (:no-options), Valhalla with only export-option (:export-only),
   Valhalla (:no-export), Valhalla with export-option (:export), and
   Valhalla with export-option and no array flattening (:no-flattening)."
  [{:keys [baseline preview-off valhalla]}]
  (let [export (update valhalla :jvm-opts conj export-option)]
    [[:java8 baseline]
     [:no-options preview-off]
     [:export-only (assoc valhalla :jvm-opts [export-option])]
     [:no-export valhalla]
     [:export export]
     [:no-flattening (update export :jvm-opts conj "-XX:-UseArrayFlattening")]]))

(defn runtime
  "Return the runtime of label in (runtimes configuration)."
  [configuration label]
  (get (into {} (runtimes configuration)) label))

(def ^:private allocation-reasons
  "The `:array-unavailable` reason of allocation in each runtime label, or
   nil when allocation is available."
  {:java8 :missing-classfile-api
   :no-options :unsupported-class-version
   :export-only :unsupported-class-version
   :no-export :array-access-denied
   :export nil
   :no-flattening nil})

(defn allocation-configurations
  "Return the allocation capability rows: [runtime reason], in the order of
   runtimes.

   reason is the `:array-unavailable` reason, or nil when allocation is
   available."
  []
  (mapv (fn [[label runtime]] [runtime (allocation-reasons label)])
        (runtimes (config/configuration))))

(defn expected-flags
  "Return the JVM input arguments of a cell child of configuration and policy."
  [configuration policy]
  (conj (:jvm-opts configuration) (runner/policy-option policy)))

(defn runtime-expected
  "Return the map of key to [description predicate] (see
   cell-harness/keyed) of the runtime facts (cell-registry/runtime-facts)
   of a cell child of configuration, policy and require-order. runtime is
   [description predicate] of :runtime."
  [runtime configuration policy require-order]
  {:runtime runtime
   :policy (harness/fixed policy)
   :require-order (harness/fixed require-order)
   :flags (harness/fixed (expected-flags configuration policy))})

(defn- rejection-cause [reason]
  (when (= reason :array-access-denied) "java.lang.IllegalAccessException"))

(defn- rejection-data
  "Return the map of key to [description predicate] of the ex-data of an
   allocation capability rejection of reason: a runtime :detail, except
   for :array-access-denied (the cause gives the detail)."
  [reason]
  (cond-> {:value-type/error (harness/fixed :array-unavailable)
           :reason (harness/fixed reason)}
    (not= reason :array-access-denied) (assoc :detail (harness/detail))))

(def ^:private rejection-names
  "The texts that an allocation capability rejection message names."
  [config/preview-option export-name])

(def rejection-exception
  "The class name of an array capability rejection (allocation or
   inspection)."
  "clojure.lang.ExceptionInfo")

(defn rejection
  "Return [description predicate] (see cell-harness/described) of an
   allocation capability rejection of reason (cell-registry/rejection),
   with the entries of extra (a map)."
  ([reason] (rejection reason {}))
  ([reason extra]
   (harness/keyed
    (merge {:exception (harness/fixed rejection-exception)
            :data (harness/keyed (rejection-data reason))
            :cause (harness/fixed (rejection-cause reason))
            :message (harness/contains rejection-names)}
           (update-vals extra harness/fixed)))))

(defn repeated-rejection
  "Return [description predicate] of the outcomes of cell-registry/twice of
   a cell-registry/rejected-call that gives an allocation capability
   rejection of reason: cell-registry/attempt-count equal rejections with
   :status :rejected and the entries of extra (a map). The predicate takes
   the outcomes."
  ([reason] (repeated-rejection reason {}))
  ([reason extra]
   (equal-repeated (rejection reason (assoc extra :status :rejected)))))

(defn allocation-children
  "Return the cell children of the allocation matrix: one one-cell child for
   each row of allocation-configurations, each policy and each
   require-order. The cell evaluates (check 'require-order supported?),
   with check a namespace-qualified symbol.

   expected is a function of reason that gives the map of key to
   [description predicate] (see cell-harness/keyed) of the result keys
   beside the runtime facts (runtime-expected, with a <detail> runtime)."
  [check expected]
  (for [[configuration reason] (allocation-configurations)
        policy config/policies
        require-order require-orders]
    (harness/cell-child
     (assoc configuration :policy policy)
     [{:context [(pr-str [(:jvm-opts configuration) reason policy require-order])]
       :form (registry/check-form check (list 'quote require-order) (nil? reason))
       :expected (harness/described
                  (harness/keyed
                   (merge (runtime-expected (harness/detail)
                                            configuration policy require-order)
                          (expected reason))))}])))
