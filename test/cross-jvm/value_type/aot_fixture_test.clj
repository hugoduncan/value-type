(ns value-type.aot-fixture-test
  "In-process tests of aot-fixture/persisted-output?, the verdict of the
   inspection cells. No child JVM starts."
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]))

(def ^:private storage
  {:fields {"required" "LLeaf;"}
   :annotations []
   :constructors ["(Ljava/lang/Object;)V"]})

(def ^:private persisted?
  (fixture/persisted-output? storage "LLeaf;"))

(deftest persisted-output-test
  (testing "storage and companion references"
    (is (true? (persisted? (assoc storage :companion-references ["LLeaf;" "LLeaf;"])))))
  (testing "other storage"
    (is (false? (persisted? (assoc storage
                                   :fields {"required" "Ljava/lang/Object;"}
                                   :companion-references ["LLeaf;"])))))
  (testing "empty companion references"
    (is (false? (persisted? (assoc storage :companion-references [])))))
  (testing "other descriptor"
    (is (false? (persisted? (assoc storage :companion-references ["LLeaf;" "LOther;"]))))))
