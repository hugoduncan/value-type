(ns value-type.array-boundary-test
  (:require [clojure.test :refer [deftest]]
            [value-type.array-capability :as capability]
            [value-type.cell-harness :as harness]
            [value-type.jvm-config :as config]))

(def ^:private control-form
  '(do (require 'value-type.array 'value-type.cell-registry 'value-type.core)
       (value-type.cell-registry/twice
        (fn []
          (value-type.cell-registry/rejected-call
           #(value-type.array/make-array nil -1 nil))))))

(deftest unavailable-boundary-test
  ;; Child JVMs prove safe loading and capability-first repeated failures.
  ;; Each configuration is its own child (rule 1); the empty cell *err*
  ;; replaces the fallback check.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (harness/run-cells
     (for [[configuration reason] [[baseline :missing-classfile-api]
                                   [valhalla :array-access-denied]]]
       (harness/cell-child
        configuration
        [{:context [(harness/runtime-context configuration)]
          :form control-form
          :expected (harness/described
                     (capability/repeated-rejection reason))}])))))
