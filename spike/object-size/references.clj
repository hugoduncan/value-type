(ns object-size-references
  "Reference-field size probe. Requires the SizeControl instrumentation agent."
  (:require [value-type.core :refer [defvaluetype]])
  (:import [investigation SizeControl]))

(deftype IdentityRef1 [f0])
(defvaluetype ValueRef1 [f0])
(deftype IdentityRef2 [f0 f1])
(defvaluetype ValueRef2 [f0 f1])
(deftype IdentityRef3 [f0 f1 f2])
(defvaluetype ValueRef3 [f0 f1 f2])

(doseq [field-value [(Object.) nil]
        constructor [->IdentityRef1 ->ValueRef1
                     #(->IdentityRef2 % %) #(->ValueRef2 % %)
                     #(->IdentityRef3 % % %) #(->ValueRef3 % % %)]]
  (let [object (constructor field-value)]
    (println "REFERENCE-SIZE"
             (pr-str {:class (.getName (class object))
                      :value? (.isValue (class object))
                      :nil-fields? (nil? field-value)
                      :bytes (SizeControl/size object)}))
    (java.lang.ref.Reference/reachabilityFence object)))
