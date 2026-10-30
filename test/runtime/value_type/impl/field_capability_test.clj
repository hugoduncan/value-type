(ns value-type.impl.field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.impl.capability :as capability]
            [value-type.impl.field-capability :as field-capability])
  (:import (java.lang.classfile ClassFile)
           (java.lang.classfile.instruction InvokeInstruction)
           (java.lang.reflect InvocationTargetException)
           (clojure.lang DynamicClassLoader)))

(deftest native-probe-enforcement-test
  ;; Inspect and execute the same bytes: native rejection must not use a helper.
  (let [emit capability/probe-bytes
        major (ClassFile/latestMajorVersion)
        leaf-name "value_type.probe.NativeEvidenceLeaf"
        holder-name "value_type.probe.NativeEvidenceHolder"
        leaf-bytes (emit major (.replace leaf-name "." "/") "J" false)
        holder-bytes (emit major (.replace holder-name "." "/")
                           "Lvalue_type/probe/NativeEvidenceLeaf;" true)
        model (.parse (ClassFile/of) holder-bytes)
        calls (into []
                    (comp (mapcat #(.toList (.elementStream (.get (.code %)))))
                          (filter #(instance? InvokeInstruction %))
                          (map (fn [^InvokeInstruction instruction]
                                 [(.asInternalName (.owner (.method instruction)))
                                  (.stringValue (.name (.method instruction)))])))
                    (.methods model))
        loader (DynamicClassLoader.)
        leaf (.defineClass loader leaf-name leaf-bytes nil)
        holder (.defineClass loader holder-name holder-bytes nil)
        ctor (.getConstructor holder (into-array Class [leaf]))]
    (testing "the only constructor call initializes Object"
      (is (= [["java/lang/Object" "<init>"]] calls)))
    (testing "real native storage accepts values and rejects nil"
      (is (= {:available? true} (#'field-capability/verify-enforcement leaf holder)))
      (is (try
            (.newInstance ctor (object-array [nil]))
            false
            (catch InvocationTargetException error
              (instance? NullPointerException (.getCause error))))))))
