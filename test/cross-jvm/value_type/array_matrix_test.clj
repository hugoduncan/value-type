(ns value-type.array-matrix-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as capability]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]))

(def ^:private allocated
  [(merge {:status :allocated :component? true :length 3
           :initial? true :changed? true :independent? true}
          registry/new-definition)])

(defn- outcomes-expected
  [reason]
  {:outcomes (if reason
               (capability/repeated-rejection reason)
               (harness/fixed allocated))})

(deftest configuration-matrix-test
  ;; Configuration and policy select the child (rules 1, 2). Require order is
  ;; process-wide: each order is a one-cell child (rule 5). No value fixture
  ;; loads in controls. Empty cell *err* replaces the fallback check.
  (harness/run-cells
   (capability/allocation-children 'value-type.array-matrix-checks/check
                                   outcomes-expected)))
