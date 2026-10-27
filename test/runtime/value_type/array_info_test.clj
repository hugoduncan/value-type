(ns value-type.array-info-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.array :as array]
            [value-type.array-test-support :as fixtures])
  (:import (jdk.internal.value ValueClass)
           (value_type.array_test_support ArrayPoint ArrayPair)))

(defn- direct-info
  [value]
  (let [component (.getComponentType (class value))]
    {:component-class component
     :component-value? (.isValue component)
     :null-restricted? (ValueClass/isNullRestrictedArray value)
     :atomic? (ValueClass/isAtomicArray value)
     :flat? (ValueClass/isFlatArray value)}))

(defn- flattening-disabled?
  []
  (boolean
   (some #{"-XX:-UseArrayFlattening"}
         (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean)))))

(defn- check-info
  [value restricted?]
  (let [before (vec value)
        expected (direct-info value)
        actual (array/array-info value)]
    (is (= (set (keys expected)) (set (keys actual))))
    (is (= expected actual))
    (is (identical? (.getComponentType (class value)) (:component-class actual)))
    (doseq [key [:component-value? :null-restricted? :atomic? :flat?]]
      (is (instance? Boolean (get actual key)) (str key)))
    (is (= restricted? (:null-restricted? actual)))
    (is (true? (:atomic? actual)))
    (when (flattening-disabled?)
      (is (false? (:flat? actual))))
    (is (= before (vec value)))))

(defn- check-ownership
  [^objects value replacement restricted?]
  (check-info value restricted?)
  (when (pos? (alength value))
    (let [before (vec value)]
      (aset value 0 replacement)
      (check-info value restricted?)
      (is (= (assoc before 0 replacement) (vec value)))))
  (check-info value restricted?))

(deftest ordinary-reference-arrays-test
  ;; Ordinary and nested reference arrays are inspected without changing contents.
  (doseq [length [0 1 3]
          [component seed replacement] [[Object :seed :replacement]
                                        [String "seed" "replacement"]
                                        [(class (int-array 0)) (int-array [1]) (int-array [2])]]]
    (testing (str component " length " length)
      (let [value (into-array component (repeat length seed))]
        (check-ownership value replacement false)
        (is (false? (:component-value? (array/array-info value))))))))

(deftest value-component-arrays-test
  ;; The same value fixtures distinguish nullable arrays from restricted arrays.
  (doseq [[component seed replacement] (fixtures/fixtures)
          length [0 1 3]]
    (testing (str component " length " length " seed " seed)
      (let [ordinary (into-array component (repeat length seed))
            restricted (array/make-array component length seed)]
        (check-ownership ordinary replacement false)
        (check-ownership restricted replacement true)
        (is (true? (:component-value? (array/array-info ordinary))))
        (is (true? (:component-value? (array/array-info restricted))))
        (when (pos? length)
          (aset ^objects ordinary 0 nil)
          (check-info ordinary false)
          (is (nil? (aget ^objects ordinary 0))))))))

(deftest controlled-layout-test
  ;; Width and startup flags affect flattening, not null restriction or atomicity.
  (doseq [length [0 1 3]]
    (let [narrow (array/make-array ArrayPoint length
                                   (ArrayPoint. 7))
          wide (array/make-array ArrayPair length
                                 (ArrayPair. 1 2))]
      (check-info narrow true)
      (check-info wide true)
      (is (= (not (flattening-disabled?)) (:flat? (array/array-info narrow))))
      (is (false? (:flat? (array/array-info wide)))))))
