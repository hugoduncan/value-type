(ns portable-fields.experiment-test
  (:require [clojure.test :refer [deftest is testing]]
            [portable-fields.fixture :as fixture])
  (:import (java.lang.reflect InvocationTargetException Modifier)
           (portable_fields.fixture Mixed Reads)))

(deftest checked-identity-contract
  ;; Exercise real transformed constructors and field reads, not a factory check.
  (testing "concrete final storage and unchanged constructor parameters"
    (let [field (.getField Mixed "label")]
      (is (= String (.getType field)))
      (is (Modifier/isFinal (.getModifiers field))))
    (is (.getConstructor Mixed (into-array Class [Long/TYPE Object Double/TYPE Object]))))
  (testing "mixed slots, nullable control, owner and captured reads"
    (let [value (Mixed. 7 "valid" 2.5 nil)]
      (is (= [7 "valid" 2.5 nil] (.snapshot ^Reads value)))
      (is (= "valid" ((.captured ^Reads value))))
      (is (= "valid" (.-label value)))
      (is (= "valid" (.get (.getField Mixed "label") value)))))
  (testing "factory, direct and reflective constructors enforce checks"
    (is (thrown? NullPointerException (fixture/->Mixed 7 nil 2.5 nil)))
    (is (thrown? ClassCastException (fixture/->Mixed 7 42 2.5 nil)))
    (is (thrown? NullPointerException (Mixed. 7 nil 2.5 nil)))
    (is (thrown? ClassCastException (Mixed. 7 42 2.5 nil)))
    (let [ctor (.getConstructor Mixed (into-array Class [Long/TYPE Object Double/TYPE Object]))]
      (doseq [[input expected] [[nil NullPointerException] [42 ClassCastException]]]
        (is (= expected
               (try
                 (.newInstance ctor (object-array [(long 7) input (double 2.5) nil]))
                 nil
                 (catch InvocationTargetException e (class (.getCause e))))))))))
