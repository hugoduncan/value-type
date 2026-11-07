(ns value-type.impl.field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.impl.capability :as capability]
            [value-type.impl.field-capability :as field-capability]
            [value-type.impl.probe-builder :as probe-builder])
  (:import (java.lang.classfile ClassFile)
           (java.lang.classfile.instruction InvokeInstruction)
           (java.lang.reflect InvocationTargetException)
           (clojure.lang DynamicClassLoader)))

(deftest native-probe-enforcement-test
  ;; Inspect and execute the same bytes: native rejection must not use a helper.
  (let [emit probe-builder/probe-bytes
        leaf-name (probe-builder/unique-probe-name "NativeEvidenceLeaf")
        holder-name (probe-builder/unique-probe-name "NativeEvidenceHolder")
        leaf-bytes (emit leaf-name "J" false)
        holder-bytes (emit holder-name (str "L" leaf-name ";") true)
        model (.parse (ClassFile/of) holder-bytes)
        calls (into []
                    (comp (mapcat #(.toList (.elementStream (.get (.code %)))))
                          (filter #(instance? InvokeInstruction %))
                          (map (fn [^InvokeInstruction instruction]
                                 [(.asInternalName (.owner (.method instruction)))
                                  (.stringValue (.name (.method instruction)))])))
                    (.methods model))
        loader (DynamicClassLoader.)
        leaf (probe-builder/define-probe loader leaf-name leaf-bytes)
        holder (probe-builder/define-probe loader holder-name holder-bytes)
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

(deftest native-restriction-ignored-test
  ;; A holder without NullRestricted accepts nil: a classified absence.
  (let [loader (DynamicClassLoader.)
        leaf (probe-builder/define-probe-class loader "IgnoredLeaf" Long/TYPE false)
        holder (probe-builder/define-probe-class loader "IgnoredHolder" leaf false)]
    (is (= {:available? false
            :reason :native-restriction-ignored
            :detail "The JVM accepted nil in a NullRestricted probe field."}
           (#'field-capability/verify-enforcement leaf holder)))))

(deftest detect-inherits-value-class-absence-test
  (let [absence {:available? false :reason :failed-self-check
                 :detail "The value-class probe failed the semantic self-check."}]
    (with-redefs [capability/capability (constantly absence)]
      (is (= absence (field-capability/detect))))))
