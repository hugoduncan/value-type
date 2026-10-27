(ns value-type.array-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [value-type.array :as array]
            [value-type.core :refer [defvaluetype]]
            [value-type.impl.array :as adapter]
            [value-type.test-support :as support])
  (:import (java.lang.invoke MethodHandles)
           (java.net URL URLClassLoader)
           (javax.tools ToolProvider)))

(defvaluetype ArrayPoint [^long x])

(defn- failure
  [component length initial]
  (try
    (array/make-array component length initial)
    nil
    (catch clojure.lang.ExceptionInfo error error)))

(deftest allocation-test
  ;; Exercise the native boundary with valid empty and nonempty allocations.
  (doseq [length [0 1]]
    (let [seed (ArrayPoint. 42)
          result (array/make-array ArrayPoint length seed)]
      (is (= ArrayPoint (.getComponentType (class result))))
      (is (= length (alength result)))
      (is (= (vec (repeat length seed)) (vec result))))))

(deftest native-allocation-failure-test
  ;; A native rejection must escape unchanged and must not poison capability.
  (let [methods (adapter/require-allocation)
        seed (ArrayPoint. 42)
        error (try
                (adapter/allocate methods ArrayPoint (int -1) seed)
                nil
                (catch Exception error error))]
    (is (= IllegalArgumentException (class error)))
    (let [result (array/make-array ArrayPoint 1 seed)]
      (is (= ArrayPoint (.getComponentType (class result))))
      (is (= [seed] (vec result))))))

(deftest validation-test
  ;; Multiple faults establish component, length, then seed precedence.
  (testing "component validation precedes length and seed"
    (doseq [component [nil :point "point" Long/TYPE Void/TYPE String Object]]
      (let [data (ex-data (failure component -1 nil))]
        (is (= :invalid-array-component (:value-type/error data)))
        (is (= component (:component data)))
        (is (= :not-reference-value-class (:reason data))))))
  (testing "length validation precedes seed"
    (doseq [length [-1 1/2 1.0 nil "1" 2147483648N 999999999999999999999N]]
      (let [data (ex-data (failure ArrayPoint length nil))]
        (is (= :invalid-array-length (:value-type/error data)))
        (is (= length (:length data)))
        (is (= ArrayPoint (:component data)))
        (is (= :out-of-range-or-non-integer (:reason data))))))
  (testing "an empty array still requires a compatible initial value"
    (doseq [length [0 1] seed [nil "wrong" 42]]
      (let [error (failure ArrayPoint length seed)
            data (ex-data error)]
        (is (= :invalid-array-element (:value-type/error data)))
        (is (= ArrayPoint (:component data)))
        (is (= length (:length data)))
        (is (= (if (nil? seed) :null-initial :incompatible-initial)
               (:reason data)))
        (is (not (contains? data :index)))
        (is (re-find #"initial value" (ex-message error)))
        (is (not (re-find #"(?i)\b(index|slot)\b" (ex-message error))))))))

(deftest length-boundary-test
  ;; Test accepted limits without requesting large native allocations.
  (doseq [length [0 1 (byte 2) (short 3) (int 4) 5N (biginteger 6)
                  Integer/MAX_VALUE]]
    (let [result (#'array/checked-length ArrayPoint length)]
      (is (= length result))
      (is (instance? Integer result))))
  (doseq [length [-1N 2147483648N 999999999999999999999999999N]]
    (is (thrown? clojure.lang.ExceptionInfo
                 (#'array/checked-length ArrayPoint length)))))

(defvaluetype ArrayEmpty [])
(defvaluetype ArrayPair [^long x ^long y])
(defvaluetype ArrayReference [payload])

(defn- fixtures
  []
  [[ArrayEmpty (ArrayEmpty.) (ArrayEmpty.)]
   [ArrayPoint (ArrayPoint. 42) (ArrayPoint. 99)]
   [ArrayPair (ArrayPair. 1 2) (ArrayPair. 3 4)]
   [ArrayReference (ArrayReference. nil) (ArrayReference. "replacement")]
   [ArrayReference (ArrayReference. "seed") (ArrayReference. nil)]])

(deftest fixture-allocation-test
  ;; A null payload is not a null element; every fixture also accepts length zero.
  (doseq [[component seed] (fixtures)
          length [0 1 3]]
    (testing (str (.getName ^Class component) " length " length " seed " seed)
      (let [result (array/make-array component length seed)]
        (is (= component (.getComponentType (class result))))
        (is (= length (alength result)))
        (is (= (vec (repeat length seed)) (vec result)))))))

(defn- check-selected-stores
  [read-slot write-slot array-length]
  (doseq [[component seed replacement] (fixtures)
          index (range 3)
          [value error-class] [[replacement nil]
                               [nil NullPointerException]
                               ["wrong" ArrayStoreException]]]
    (testing (str (.getName ^Class component) " index " index " store " value)
      (let [result (array/make-array component 3 seed)
            before (vec (repeat 3 seed))]
        (is (= 3 (array-length result)))
        (if error-class
          (let [error (try (write-slot result index value) nil
                           (catch Exception error error))]
            (is (= error-class (class error))))
          (write-slot result index value))
        (is (= (if error-class before (assoc before index replacement))
               (mapv #(read-slot result %) (range 3))))))))

(deftest clojure-selected-stores-test
  ;; Each write has its own array, so failures at every index must preserve all slots.
  (check-selected-stores #(aget ^objects %1 %2)
                         #(aset ^objects %1 %2 %3)
                         #(alength ^objects %)))

(defn- with-java-fixtures
  [f]
  (support/with-temp-dir [dir]
    (let [compiler (ToolProvider/getSystemJavaCompiler)
          status (.run compiler nil nil nil
                       (into-array String
                                   ["--enable-preview" "--release" "27"
                                    "-d" (str dir)
                                    (str (io/file "test-resources/value-array/ArrayAccess.java"))
                                    (str (io/file "test-resources/value-array/IndependentValue.java"))]))]
      (is (zero? status) "Java fixtures compile with the current EA3 JDK")
      (when (zero? status)
        (with-open [loader (URLClassLoader.
                            (into-array URL [(.toURL (.toURI dir))])
                            (.getContextClassLoader (Thread/currentThread)))]
          (f (.loadClass loader "ArrayAccess")
             (.loadClass loader "IndependentValue")))))))

(defn- static-call
  [^Class component method-name parameter-types]
  ;; Method handles preserve the native exception rather than a reflection wrapper.
  (let [method (.getMethod component method-name (into-array Class parameter-types))
        handle (.unreflect (MethodHandles/lookup) method)]
    (fn [& arguments]
      (.invokeWithArguments handle ^java.util.List (vec arguments)))))

(deftest java-selected-stores-test
  ;; Exercise ordinary Java aaload/aastore/arraylength against the same slot contract.
  (with-java-fixtures
    (fn [access _]
      (let [array-class (class (object-array 0))
            read-slot (static-call access "read" [array-class Integer/TYPE])
            write-slot (static-call access "write" [array-class Integer/TYPE Object])]
        (check-selected-stores
         #(read-slot %1 (int %2))
         #(write-slot %1 (int %2) %3)
         (static-call access "length" [array-class]))))))

(deftest independent-java-component-test
  ;; Eligibility depends on the runtime class, not a defvaluetype registration.
  (with-java-fixtures
    (fn [_ ^Class component]
      (let [constructor (.getConstructor component (into-array Class [Long/TYPE]))
            seed (.newInstance constructor (object-array [(long 17)]))]
        (doseq [length [0 1 3]]
          (let [result (array/make-array component length seed)]
            (is (= component (.getComponentType (class result))))
            (is (= length (alength result)))
            (is (= (vec (repeat length seed)) (vec result)))))))))

(deftest restricted-atomic-test
  ;; Inspect only non-null arrays; diagnostics are not public allocation prerequisites.
  (let [value-class (Class/forName "jdk.internal.value.ValueClass")
        restricted? (static-call value-class "isNullRestrictedArray" [Object])
        atomic? (static-call value-class "isAtomicArray" [Object])]
    (doseq [[component seed] (fixtures) length [0 1 3]]
      (let [result (array/make-array component length seed)]
        (is (true? (restricted? result)))
        (is (true? (atomic? result)))))))

(deftest wide-layout-test
  ;; Non-flat storage remains valid restricted atomic storage on the verified EA3.
  (let [flat? (static-call (Class/forName "jdk.internal.value.ValueClass")
                           "isFlatArray" [Object])]
    (is (false? (flat? (array/make-array ArrayPair 3 (ArrayPair. 1 2)))))))
