(ns value-type.array-sequence-boundary-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as capability]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]))

(def ^:private constructed
  [{:status :constructed :component? true :length 2 :contents? true}
   {:status :constructed :component? true :length 2 :contents? true}
   {:status :constructed :component? true :length 0 :contents? true}])

(def ^:private definitions
  {'SequencePoint registry/new-definition})

(defn- expected
  [reason]
  (if reason
    (let [calls (capability/repeated-rejection reason {:effects 0})]
      {:outcomes (harness/entries
                  (for [explicit? [false true]]
                    (harness/keyed {:explicit? (harness/fixed explicit?)
                                    :calls calls})))})
    {:outcomes (harness/fixed constructed)
     :definitions (harness/fixed definitions)}))

(deftest sequence-configuration-matrix-test
  ;; Configuration and policy select the child (rules 1, 2). Require order is
  ;; process-wide: each order is a one-cell child (rule 5). No value fixture
  ;; loads in controls; the empty cell *err* replaces the fallback check.
  (harness/run-cells
   (capability/allocation-children 'value-type.array-sequence-checks/check
                                   expected)))
