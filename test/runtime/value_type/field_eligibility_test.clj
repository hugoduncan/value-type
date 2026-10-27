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
  (let [field (with-meta 'nested {:tag NestedValue :nil-restricted true})]
    (testing "a concrete value class with nullable contents is eligible"
      (is (identical? NestedValue
                      (fields/validate-type "example.Holder" field NestedValue))))
    (testing "the same Class is not eligible as its own replacement's field"
      (let [data (try
                   (fields/validate-type (.getName NestedValue) field NestedValue)
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
                                    "test/resources/nil-restricted-fields/ExternalValue.java"]))]
      (is (zero? status) "External value fixture compiles on EA3")
      (when (zero? status)
        (with-open [loader (URLClassLoader.
                            (into-array URL [(.toURL (.toURI dir))])
                            (.getContextClassLoader (Thread/currentThread)))]
          (let [external (.loadClass loader "fields.ExternalValue")
                first-field (with-meta 'external-value
                              {:tag external :nil-restricted true :custom :kept})
                last-field (with-meta 'library-value
                             {:tag NestedValue :nil-restricted true})
                specs (fields/specifications
                       (the-ns 'value-type.field-eligibility-test) "example.Holder"
                       [first-field 'ordinary last-field])]
            (is (= [{:field first-field :type external
                     :descriptor "Lfields/ExternalValue;"}
                    {:field last-field :type NestedValue
                     :descriptor "Lvalue_type/field_eligibility_test/NestedValue;"}]
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
            '[^{:nil-restricted :invalid} item] 'item :invalid-nil-restricted]
           ["missing tag" 'MissingTag
            '[^:nil-restricted item] 'item :missing-tag]
           ["unresolved qualified tag" 'UnresolvedTag
            '[^example.missing.Value ^:nil-restricted item] 'item :unresolved-tag]
           ["forward tag" 'ForwardTag
            '[^NotYetDefinedValue ^:nil-restricted item] 'item :unresolved-tag]
           ["primitive tag" 'PrimitiveTag
            '[^long ^:nil-restricted item] 'item :primitive-type]
           ["identity tag" 'IdentityTag
            '[^String ^:nil-restricted item] 'item :identity-type]
           ["interface tag" 'InterfaceTag
            '[^Runnable ^:nil-restricted item] 'item :interface-type]
           ["abstract tag" 'AbstractTag
            '[^java.util.AbstractList ^:nil-restricted item] 'item :abstract-type]
           ["array tag" 'ArrayTag
            '[^objects ^:nil-restricted item] 'item :array-type]
           ["existing enclosing value Class" 'NestedValue
            '[^NestedValue ^:nil-restricted item] 'item :enclosing-type]
           ["metadata errors precede earlier type errors" 'MetadataFirst
            '[^String ^:nil-restricted identity
              ^{:nil-restricted :invalid} invalid]
            'invalid :invalid-nil-restricted]
           ["first invalid metadata wins" 'FirstMetadata
            '[^{:nil-restricted 0} first ^{:nil-restricted :invalid} second]
            'first :invalid-nil-restricted]
           ["first enabled type error wins" 'FirstType
            '[^String ^:nil-restricted identity ^:nil-restricted missing]
            'identity :identity-type]
           ["missing tag precedes a later ineligible type" 'FirstMissing
            '[^:nil-restricted missing ^String ^:nil-restricted identity]
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
