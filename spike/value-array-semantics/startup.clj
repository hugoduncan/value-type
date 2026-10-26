(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [discover]}}}} startup
  (:require [clojure.pprint :as pprint]
            [value-type.core]))

;; Helper loading is Java 8-safe and does not run discovery.
(load-file "spike/value-array-semantics/support/discover.clj")

(defn startup-cases
  "Check strict value creation before attempting dependent array operations."
  []
  (let [definition (discover/observe
                    :startup/value-definition :define-value-class {}
                    #(do (eval '(value-type.core/defvaluetype StartupValue [x]))
                         true))
        allocation (if (= :pass (:status definition))
                     (discover/observe
                      :startup/allocation :atomic-allocation {:length 0 :seed :valid}
                      #(let [seed (eval '(->StartupValue 17))
                             component (class seed)
                             api (Class/forName "jdk.internal.value.ValueClass")
                             method (.getMethod api "newNullRestrictedAtomicArray"
                                                (into-array Class [Class Integer/TYPE Object]))
                             array (.invoke method nil (object-array [component (int 0) seed]))]
                         {:length (alength ^objects array)
                          :value-class? (.invoke (.getMethod Class "isValue" (make-array Class 0))
                                                 component (object-array 0))}))
                     {:id :startup/allocation :operation :atomic-allocation
                      :status :unresolved :reason :value-definition-rejected})]
    [definition allocation
     {:id :startup/dependent-semantics :operation :semantic-suite
      :status :unresolved
      :reason (if (= :pass (:status allocation))
                :run-separate-semantic-suite
                :allocation-not-established)}]))

;; Preserve independent discovery evidence before strict value creation.
(discover/run-probe! (first *command-line-args*))

(spit (second *command-line-args*)
      (with-out-str
        (pprint/pprint {:schema-version 1 :phase :startup
                        :environment (discover/environment) :cases (startup-cases)})))
