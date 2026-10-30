(ns portable-fields.value-experiment-test
  (:require [clojure.test :refer [deftest is testing]]
            [portable-fields.value-experiment :refer [checked-value]]
            [value-type.core :refer [defvaluetype]])
  (:import (java.lang.reflect InvocationTargetException Modifier)))

(defvaluetype Child [nullable])

(definterface Reads
  (snapshot [])
  (captured []))

(checked-value Owner [^long count ^String ^:non-nil label ^double weight
                      ^Child ^:non-nil child nullable]
               Reads
               (snapshot [_] [count label weight child nullable])
               (captured [this] (fn [] [(.-label this) (.-child this)])))

(deftest generated-value-contract
  ;; Run real strict-initialization constructors without native field annotations.
  (testing "value representation with concrete final generated-only storage"
    (is (.isValue Owner))
    (doseq [[field expected] [["label" String] ["child" Child]]]
      (let [reflected (.getField Owner field)]
        (is (= expected (.getType reflected)))
        (is (Modifier/isFinal (.getModifiers reflected)))
        (is (empty? (.getAnnotations reflected))))))
  (testing "mixed slots and owner/companion reads"
    (let [child (->Child nil)
          value (Owner. 7 "valid" 2.5 child nil)]
      (is (= [7 "valid" 2.5 child nil] (.snapshot ^Reads value)))
      (is (= ["valid" child] ((.captured ^Reads value))))
      (is (= child (.get (.getField Owner "child") value)))))
  (testing "actual constructors enforce both identity and value constraints"
    (let [ctor (.getConstructor Owner (into-array Class [Long/TYPE Object Double/TYPE Object Object]))
          child (->Child nil)]
      (doseq [[label nested expected] [[nil child NullPointerException] [42 child ClassCastException] ["valid" nil NullPointerException] ["valid" "wrong" ClassCastException]] construct [(fn [] (->Owner 7 label 2.5 nested nil)) (fn [] (Owner. 7 label 2.5 nested nil)) (fn [] (.newInstance ctor (object-array [(long 7) label (double 2.5) nested nil])))]]
        (is (= expected
               (try
                 (construct)
                 nil
                 (catch InvocationTargetException e (class (.getCause e)))
                 (catch Exception e (class e)))))))))
