(ns value-type.field-eligibility-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.core :refer [defvaluetype]]
            [value-type.impl.fields :as fields]
            [value-type.test-support :as support])
  (:import (clojure.lang Compiler DynamicClassLoader)
           (java.net URL URLClassLoader)
           (javax.tools ToolProvider)))

(defvaluetype NestedValue [nullable])

(deftest value-type-eligibility-test
  ;; Keep the exact resolved Class, and reject an older enclosing definition.
  (let [field (with-meta 'nested {:tag NestedValue :non-nil true})]
    (testing "a concrete value class with nullable contents is eligible"
      (is (identical? NestedValue
                      (fields/validate-native-type "example.Holder" field NestedValue))))
    (testing "the same Class is not eligible as its own replacement's field"
      (let [data (try
                   (fields/validate-native-type (.getName NestedValue) field NestedValue)
                   nil
                   (catch clojure.lang.ExceptionInfo e (ex-data e)))]
        (is (= :enclosing-type (:reason data)))))))

(deftest ordered-specifications-test
  ;; External value classes need no library registration; preserve Class identity.
  (support/with-temp-dir [dir]
    (let [compiler (ToolProvider/getSystemJavaCompiler)
          status (.run compiler nil nil nil
                       (into-array String
                                   ["--enable-preview" "--release" "27"
                                    "-d" (str dir)
                                    "test/resources/non-nil-fields/ExternalValue.java"]))]
      (is (zero? status) "External value fixture compiles on EA3")
      (when (zero? status)
        (with-open [loader (URLClassLoader.
                            (into-array URL [(.toURL (.toURI dir))])
                            (.getContextClassLoader (Thread/currentThread)))]
          (let [external (.loadClass loader "fields.ExternalValue")
                first-field (with-meta 'external-value
                              {:tag external :non-nil true :custom :kept})
                last-field (with-meta 'library-value
                             {:tag NestedValue :non-nil true})
                specs (fields/select-enforcement
                       (fields/specifications
                        (the-ns 'value-type.field-eligibility-test) "example.Holder"
                        [first-field 'ordinary last-field])
                       (constantly true))]
            (is (= [{:field first-field :type external :enforcement :native}
                    {:field last-field :type NestedValue :enforcement :native}]
                   specs))
            (is (identical? external (:type (first specs))))
            (is (identical? NestedValue (:type (second specs))))
            (is (= (meta first-field) (meta (:field (first specs)))))
            (testing "the public macro accepts independently compiled values"
              (with-bindings {Compiler/LOADER (DynamicClassLoader. loader)
                              #'*ns* (the-ns 'value-type.field-eligibility-test)}
                (let [holder-name (gensym "ExternalHolder")
                      factory (eval `(do
                                       (defvaluetype ~holder-name [~first-field])
                                       ~(symbol (str "->" holder-name))))
                      value (.newInstance
                             (.getConstructor external (into-array Class [Object]))
                             (object-array [nil]))
                      holder (factory value)
                      field (.getField (class holder) "external_value")]
                  (is (identical? external (.getType field)))
                  (is (= value (.get field holder)))
                  (is (nil? (.get (.getField external "nullable") value)))
                  (is (thrown? NullPointerException (factory nil)))
                  (is (thrown? ClassCastException (factory "wrong"))))))))))))

(deftest public-declaration-rejection-test
  ;; Public expansion must report declaration errors before JVM class definition.
  (doseq [[label type-name declarations field reason]
          [["invalid restriction metadata" 'InvalidMetadata
            '[^{:non-nil :invalid} item] 'item :invalid-non-nil]
           ["missing tag" 'MissingTag
            '[^:non-nil item] 'item :missing-tag]
           ["unresolved qualified tag" 'UnresolvedTag
            '[^example.missing.Value ^:non-nil item] 'item :unresolved-tag]
           ["forward tag" 'ForwardTag
            '[^NotYetDefinedValue ^:non-nil item] 'item :unresolved-tag]
           ["primitive tag" 'PrimitiveTag
            '[^long ^:non-nil item] 'item :primitive-type]
           ["interface tag" 'InterfaceTag
            '[^Runnable ^:non-nil item] 'item :interface-type]
           ["abstract tag" 'AbstractTag
            '[^java.util.AbstractList ^:non-nil item] 'item :abstract-type]
           ["array tag" 'ArrayTag
            '[^objects ^:non-nil item] 'item :array-type]
           ["existing enclosing value Class" 'NestedValue
            '[^NestedValue ^:non-nil item] 'item :enclosing-type]
           ["metadata errors precede earlier type errors" 'MetadataFirst
            '[^String ^:non-nil identity
              ^{:non-nil :invalid} invalid]
            'invalid :invalid-non-nil]
           ["first invalid metadata wins" 'FirstMetadata
            '[^{:non-nil 0} first ^{:non-nil :invalid} second]
            'first :invalid-non-nil]
           ["first enabled type error wins" 'FirstType
            '[^String ^:non-nil identity ^:non-nil missing]
            'missing :missing-tag]
           ["missing tag precedes a later ineligible type" 'FirstMissing
            '[^:non-nil missing ^String ^:non-nil identity]
            'missing :missing-tag]]]
    (testing label
      (let [failure (binding [*ns* (the-ns 'value-type.field-eligibility-test)]
                      (try
                        (macroexpand
                         (list 'value-type.core/defvaluetype type-name declarations))
                        (catch clojure.lang.Compiler$CompilerException e e)))
            cause (ex-cause failure)]
        (is (instance? clojure.lang.Compiler$CompilerException failure))
        (is (instance? clojure.lang.ExceptionInfo cause))
        (is (= {:value-type/error :invalid-field
                :class-name (str "value_type.field_eligibility_test." type-name)
                :field field
                :reason reason}
               (select-keys (ex-data cause)
                            [:value-type/error :class-name :field :reason])))))))

(deftest enforcement-selection-test
  ;; Real Classes select per-field modes; capability absence is explicit input.
  (let [declarations '[^NestedValue ^:non-nil nested ^String ^:non-nil text]]
    (doseq [[capable? expected] [[true [:native :generated]]
                                 [false [:generated :generated]]]]
      (let [specs (fields/select-enforcement
                   (fields/specifications
                    (the-ns 'value-type.field-eligibility-test)
                    "example.Holder" declarations)
                   (constantly capable?))]
        (is (= expected (mapv :enforcement specs)))
        (is (= [NestedValue String] (mapv :type specs)))
        (is (= declarations (mapv :field specs)))))))
