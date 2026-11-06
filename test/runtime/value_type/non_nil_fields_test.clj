(ns value-type.non-nil-fields-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.core :refer [defvaluetype]]
            [value-type.test-support :as support :refer [with-temp-dir]])
  (:import (java.lang.reflect InvocationTargetException)))

(defvaluetype Leaf [nullable])
(defvaluetype Pair [^Leaf ^:non-nil left
                    ^{:tag Leaf :non-nil true} right])

(deftest public-construction-test
  ;; All entry points retain Object parameters and native enforcement.
  (let [leaf (->Leaf nil)
        constructor (.getConstructor Pair (into-array Class [Object Object]))
        reflective (fn [left right]
                     (try
                       (.newInstance constructor (object-array [left right]))
                       (catch InvocationTargetException e (throw (.getCause e)))))]
    (doseq [construct [(fn [left right] (Pair. left right)) ->Pair reflective]]
      (testing "both metadata forms accept values with nullable contents"
        (let [^Pair pair (construct leaf leaf)]
          (is (= leaf (.-left pair)))
          (is (= leaf (.-right pair)))
          (is (= leaf (.get (.getField Pair "left") pair)))))
      (doseq [position [0 1]]
        (testing (str "native enforcement at position " position)
          (is (thrown? NullPointerException
                       (apply construct (assoc [leaf leaf] position nil))))
          (is (thrown? ClassCastException
                       (apply construct (assoc [leaf leaf] position "wrong")))))))
    (is (= [Leaf Leaf] (mapv #(.getType ^java.lang.reflect.Field %)
                             [(.getField Pair "left") (.getField Pair "right")])))))

(defvaluetype MixedFields
  [^Leaf ^:non-nil first-leaf
   ^long count-value
   ^String nullable
   ^double weight
   ^{:tag Leaf :non-nil true} last-leaf])

(deftest mixed-field-construction-test
  ;; Concrete reference storage must not shift two-slot primitive arguments.
  (let [first-value (->Leaf nil)
        last-value (->Leaf :last)
        arguments [first-value 42 nil 2.5 last-value]
        parameter-types [Object Long/TYPE Object Double/TYPE Object]
        constructor (.getConstructor MixedFields (into-array Class parameter-types))
        reflective (fn [& values]
                     (try
                       (.newInstance constructor (object-array values))
                       (catch InvocationTargetException e (throw (.getCause e)))))]
    (is (= parameter-types (vec (.getParameterTypes constructor))))
    (is (= [Leaf Long/TYPE Object Double/TYPE Leaf]
           (mapv #(.getType (.getField MixedFields %))
                 ["first_leaf" "count_value" "nullable" "weight" "last_leaf"])))
    (doseq [construct [(fn [first-leaf count-value nullable weight last-leaf]
                         (MixedFields. first-leaf count-value nullable weight last-leaf))
                       ->MixedFields reflective]]
      (testing "mixed arguments retain their order and nullable controls"
        (let [^MixedFields value (apply construct arguments)]
          (is (= arguments
                 [(.-first-leaf value) (.-count-value value) (.-nullable value)
                  (.-weight value) (.-last-leaf value)]))
          (is (= arguments
                 (mapv #(.get (.getField MixedFields %) value)
                       ["first_leaf" "count_value" "nullable" "weight" "last_leaf"])))
          (is (nil? (.-nullable ^Leaf (.-first-leaf value)))))
        (is (= "optional"
               (.-nullable ^MixedFields
                (apply construct (assoc arguments 2 "optional"))))))
      (doseq [position [0 4]]
        (testing (str "restricted mixed argument at position " position)
          (is (thrown? NullPointerException
                       (apply construct (assoc arguments position nil))))
          (is (thrown? ClassCastException
                       (apply construct (assoc arguments position "wrong")))))))))

(defprotocol FieldBehavior
  (selected-field [this choose-required?])
  (field-reader [this]))

(defvaluetype BehavioralFields [^Leaf ^:non-nil required optional]
  FieldBehavior
  (selected-field [_ choose-required?]
                  (if choose-required? required optional))
  (field-reader [this]
                (fn [choose-required?]
                  (letfn [(read-field [fallback]
                            (let [required-value (.-required this)
                                  optional-value (.-optional this)]
                              (if choose-required?
                                (if (nil? (.-nullable required-value))
                                  required-value optional-value)
                                (if (nil? optional-value) fallback optional-value))))]
                    (fn [fallback] (read-field fallback)))))
  java.util.function.Supplier
  (get [_] required)
  Object
  (toString [_] (str "required=" (.-nullable required) ";optional=" optional)))

(deftest public-method-behavior-test
  ;; Public macro expansion must preserve owner references in all method kinds.
  (let [leaf (->Leaf :payload)
        value (->BehavioralFields leaf :optional)]
    (testing "protocol and Java interface reads use concrete storage"
      (is (= leaf (selected-field value true)))
      (is (= :optional (selected-field value false)))
      (is (= leaf (.get ^java.util.function.Supplier value))))
    (testing "Object overrides can read nested restricted contents"
      (is (= "required=:payload;optional=:optional" (str value))))))

(deftest public-companion-behavior-test
  ;; Escaping nested functions must link rewritten owner field references.
  (doseq [payload [nil :payload]
          optional [nil :optional]
          choose-required? [false true]]
    (let [leaf (->Leaf payload)
          value (->BehavioralFields leaf optional)
          reader ((field-reader value) choose-required?)
          expected (if choose-required?
                     (if (nil? payload) leaf optional)
                     (if (nil? optional) :fallback optional))]
      (testing (str "nested branch " [payload optional choose-required?])
        (is (= expected (reader :fallback)))))))

(defn- define-merge-fixture
  "Eval `form` in this namespace and return its value. `form` defines a
  type whose code merges a concrete field read with another class at a
  branch, and returns a function of a Leaf. Eval keeps a transform
  failure inside the test."
  [form]
  (support/eval-in 'value-type.non-nil-fields-test form))

(deftest unloaded-class-merge-test
  ;; Stack maps of rewritten field reads must resolve classes that the
  ;; loader cannot load yet: the class in transform and the owner.
  (let [leaf (->Leaf :payload)]
    (testing "owner merges the field with its receiver"
      (let [f (define-merge-fixture
                '(do (defvaluetype MergeOwnerSelf [^Leaf ^:non-nil required]
                       java.util.function.Function
                       (apply [this choose] (if choose required this)))
                     (fn [leaf]
                       (let [^java.util.function.Function v (MergeOwnerSelf. leaf)]
                         [(.apply v true) (.apply v false) v]))))
            [field other v] (f leaf)]
        (is (= leaf field))
        (is (identical? v other))))
    (testing "owner merges the field with a new owner"
      (let [f (define-merge-fixture
                '(do (defvaluetype MergeOwnerNew [^Leaf ^:non-nil required]
                       java.util.function.Function
                       (apply [this choose]
                              (if choose required (MergeOwnerNew. required))))
                     (fn [leaf]
                       (let [^java.util.function.Function v (MergeOwnerNew. leaf)]
                         [(.apply v true) (class (.apply v false)) MergeOwnerNew]))))
            [field other-class owner-class] (f leaf)]
        (is (= leaf field))
        (is (= owner-class other-class))))
    (testing "reify companion merges the field with its receiver"
      (let [f (define-merge-fixture
                '(do (defvaluetype MergeReify [^Leaf ^:non-nil a]
                       clojure.lang.ILookup
                       (valAt [this k]
                              (reify clojure.lang.ILookupThunk
                                (get [thunk target]
                                  (if (identical? (class target) MergeReify)
                                    (.-a ^MergeReify target)
                                    thunk))))
                       (valAt [this k nf] nf))
                     (fn [leaf]
                       (let [v (MergeReify. leaf)
                             ^clojure.lang.ILookupThunk thunk (.valAt v :a)]
                         [(.get thunk v) (.get thunk :other) thunk]))))
            [field other thunk] (f leaf)]
        (is (= leaf field))
        (is (identical? thunk other))))
    (testing "fn companion merges the field with itself"
      (let [f (define-merge-fixture
                '(do (defvaluetype MergeFnSelf [^Leaf ^:non-nil required]
                       java.util.function.Supplier
                       (get [this]
                            (fn self [choose]
                              (if choose (.-required this) self))))
                     (fn [leaf]
                       (let [g (.get ^java.util.function.Supplier (MergeFnSelf. leaf))]
                         [(g true) (g false) g]))))
            [field other g] (f leaf)]
        (is (= leaf field))
        (is (identical? g other))))
    (testing "fn companion merges the field with a new owner"
      (let [f (define-merge-fixture
                '(do (defvaluetype MergeFnNew [^Leaf ^:non-nil required]
                       java.util.function.Supplier
                       (get [this]
                            (fn [choose]
                              (if choose
                                (.-required this)
                                (MergeFnNew. (.-required this))))))
                     (fn [leaf]
                       (let [g (.get ^java.util.function.Supplier (MergeFnNew. leaf))]
                         [(g true) (class (g false)) MergeFnNew]))))
            [field other-class owner-class] (f leaf)]
        (is (= leaf field))
        (is (= owner-class other-class))))))

(deftest native-probe-scope-test
  ;; A fresh JVM shows when the cached native field probe is realized.
  (with-temp-dir [dir]
    (let [form (str "(require 'value-type.core)"
                    "(let [probe @#'value-type.impl.runtime/detected-field-capability"
                    "      error (fn [form]"
                    "              (try (eval form) nil"
                    "                (catch Throwable e"
                    "                  (:value-type/error (ex-data (or (ex-cause e) e))))))"
                    "      _ (eval '(value-type.core/defvaluetype Leaf [x]))"
                    "      invalid (error '(value-type.core/defvaluetype Invalid"
                    "                        [^Leaf ^{:non-nil :yes} leaf]))"
                    "      after-invalid (realized? probe)"
                    "      _ (eval '(value-type.core/defvaluetype Label"
                    "                 [^String ^:non-nil text]))"
                    "      label-check (class (try ((resolve 'user/->Label) nil)"
                    "                           (catch Throwable e e)))"
                    "      after-identity (realized? probe)"
                    "      _ (eval '(value-type.core/defvaluetype Pair [^Leaf ^:non-nil leaf]))]"
                    "  (prn [invalid after-invalid (symbol (.getName label-check))"
                    "        after-identity (realized? probe)]))")
          {:keys [exit result err]} (support/run-in-new-jvm dir form)]
      (is (= 0 exit) (str "stderr: " err))
      (is (= [:invalid-field false 'java.lang.NullPointerException false true]
             result)
          (str "stderr: " err)))))
