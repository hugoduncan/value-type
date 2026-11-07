(ns value-type.impl.probe-builder-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.annotation-support :as annotation-support]
   [value-type.impl.capability :as capability]
   [value-type.impl.jvm-format :as jvm-format]
   [value-type.impl.probe-builder :as probe-builder])
  (:import
   (clojure.lang DynamicClassLoader)
   (java.lang.classfile ClassFile ClassModel FieldModel)
   (java.lang.classfile.attribute RuntimeInvisibleAnnotationsAttribute
                                  RuntimeVisibleAnnotationsAttribute)
   (java.lang.reflect Modifier)))

(set! *warn-on-reflection* true)

(def ^:private null-restricted
  {["Ljdk/internal/vm/annotation/NullRestricted;" {}] 1})

(defn- value-field
  ^FieldModel [^bytes bytes]
  (let [^ClassModel model (.parse (ClassFile/of) bytes)]
    (first (filter #(= "value" (.stringValue (.fieldName ^FieldModel %)))
                   (.fields model)))))

(deftest long-probe-test
  (let [class-name (probe-builder/unique-probe-name "BuilderTestLong")
        bytes (probe-builder/probe-bytes class-name "J" false)
        field-model (value-field bytes)
        probe (probe-builder/define-probe (DynamicClassLoader.) class-name bytes)
        ctor (.getConstructor probe (into-array Class [Long/TYPE]))
        field (.getField probe "value")
        instance (.newInstance ctor (object-array [(long 42)]))]
    (testing "the class is a public final value class"
      (is (capability/value-class? probe))
      (is (Modifier/isPublic (.getModifiers probe)))
      (is (Modifier/isFinal (.getModifiers probe))))
    (testing "the field and constructor exist"
      (is (= Long/TYPE (.getType field)))
      (is (= 42 (.getLong field instance))))
    (testing "the field is public final with the strict flag"
      (is (= 0x811 (.flagsMask (.flags field-model)))))))

(deftest define-probe-class-test
  (let [loader (DynamicClassLoader.)
        leaf (probe-builder/define-probe-class loader "BuilderTestLeaf" Long/TYPE false)
        holder (probe-builder/define-probe-class loader "BuilderTestHolder" leaf true)]
    (testing "each call defines a new value class with a unique probe name"
      (is (capability/value-class? leaf))
      (is (.startsWith (.getName leaf) "value_type.probe.BuilderTestLeaf_"))
      (is (not= leaf (probe-builder/define-probe-class
                       loader "BuilderTestLeaf" Long/TYPE false))))
    (testing "the holder field has the leaf type"
      (is (= leaf (.getType (.getField holder "value")))))))

(deftest malformed-int-probe-builds-test
  ;; Stack-map risk recheck: the build of an aload of an int slot completes;
  ;; the JVM verifier rejects the class (malformed-probe-test).
  (is (pos? (alength (probe-builder/probe-bytes
                      "value_type/probe/BuilderTestBadSlots" "I" false)))))

(deftest null-restricted-attribute-test
  (let [owner "value_type/probe/BuilderTestHolder"
        descriptor "Lvalue_type/probe/Leaf;"
        restricted (value-field (probe-builder/probe-bytes owner descriptor true))
        plain (value-field (probe-builder/probe-bytes owner descriptor false))]
    (testing "restricted? adds a runtime-visible NullRestricted annotation"
      (is (= null-restricted
             (annotation-support/annotation-contents
              restricted RuntimeVisibleAnnotationsAttribute)))
      (is (empty? (annotation-support/annotation-contents
                   restricted RuntimeInvisibleAnnotationsAttribute))))
    (testing "no NullRestricted without restricted?"
      (is (empty? (annotation-support/annotation-contents
                   plain RuntimeVisibleAnnotationsAttribute))))))

(deftest constructor-order-assertion-test
  (let [row (assoc (jvm-format/row) :constructor-order :super-before-fields)]
    (with-redefs [jvm-format/row (constantly row)]
      (is (thrown-with-msg? IllegalStateException
                            #"Unsupported value-constructor order"
                            (probe-builder/probe-bytes
                             "value_type/probe/BuilderTestOrder" "J" false))))))
