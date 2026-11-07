(ns value-type.impl.array-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.array :as array]
   [value-type.impl.capability :as capability]
   [value-type.probe-test-support :as support])
  (:import
   (value_type.probe_test_support IdentityLong)))

(deftest passes-round-trip-test
  (let [allocator (:allocator (array/allocation-capability))]
    (testing "discovery found the allocator"
      (is (some? allocator)))
    (testing "the builder probe passes the round trip"
      (is (true? (array/passes-round-trip? allocator support/builder-probe-class))))
    (testing "a value class whose instances do not keep their value fails"
      (is (false? (array/passes-round-trip? allocator support/constant-value-class))))))

(deftest require-allocation-failed-self-check-test
  (let [result {:available? false :reason :failed-self-check
                :detail "The value-array round trip failed the semantic self-check."}]
    (with-redefs [array/allocation-capability (constantly result)]
      (let [error (try (array/require-allocation) nil
                       (catch clojure.lang.ExceptionInfo e e))]
        (is (= (str "Value-array allocation is unavailable. " (:detail result))
               (ex-message error)))
        (is (= {:reason :failed-self-check :detail (:detail result)
                :value-type/error :array-unavailable}
               (ex-data error)))))))

(deftest discover-guard-test
  (testing "an available value-class result without :probe-class is a defect"
    (with-redefs [capability/capability (constantly {:available? true})]
      (is (thrown-with-msg? IllegalStateException #"no :probe-class"
                            (#'array/discover))))))

(deftest discover-failed-round-trip-test
  (testing "a false round trip maps to :failed-self-check"
    (with-redefs [capability/capability
                  (constantly {:available? true
                               :probe-class support/constant-value-class})]
      (is (= {:available? false :reason :failed-self-check
              :detail "The value-array round trip failed the semantic self-check."}
             (#'array/discover))))))

(deftest round-trip-exception-test
  (let [allocator (:allocator (array/allocation-capability))]
    (testing "passes-round-trip? removes InvocationTargetException"
      (is (thrown-with-msg? IllegalArgumentException #"not a value class"
                            (array/passes-round-trip? allocator IdentityLong))))
    (testing "a constructor exception propagates as its cause"
      (is (thrown-with-msg? UnsupportedOperationException #"probe constructor failed"
                            (array/passes-round-trip? allocator support/throwing-class))))
    (testing "discover propagates a round-trip exception"
      (with-redefs [capability/capability
                    (constantly {:available? true :probe-class IdentityLong})]
        (is (thrown-with-msg? IllegalArgumentException #"not a value class"
                              (#'array/discover)))))))

(deftest discover-inherits-value-class-absence-test
  (let [absence {:available? false :reason :failed-self-check
                 :detail "The value-class probe failed the semantic self-check."}]
    (with-redefs [capability/capability (constantly absence)]
      (is (= absence (#'array/discover))))))
