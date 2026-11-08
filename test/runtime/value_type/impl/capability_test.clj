(ns value-type.impl.capability-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [value-type.impl.capability :as capability]
   [value-type.probe-test-support :as support])
  (:import
   (value_type.probe_test_support IdentityLong)))

(deftest passes-self-check-test
  (testing "a builder value probe passes"
    (is (true? (capability/passes-self-check? support/builder-probe-class))))
  (testing "an identity class fails: two 42 instances are not identical"
    (is (false? (capability/passes-self-check? IdentityLong))))
  (testing "a value class with wrong field values fails"
    (is (false? (capability/passes-self-check? support/constant-value-class)))))

(deftest classify-test
  (testing "a builder value probe is available with its Class"
    (is (= {:available? true :probe-class support/builder-probe-class}
           (capability/classify support/builder-probe-class))))
  (testing "an identity class is not a value class"
    (is (= {:available? false :reason :probe-not-value-class
            :detail "The JVM defined the probe class, but the probe class is not a value class."}
           (capability/classify IdentityLong))))
  (testing "a value class with wrong semantics fails the self-check"
    (is (= {:available? false :reason :failed-self-check
            :detail "The value-class probe failed the semantic self-check."}
           (capability/classify support/constant-value-class)))))

(deftest detect-test
  (let [{:keys [available? probe-class] :as result} (capability/detect)]
    (testing "an available result has only :available? and :probe-class"
      (is (= #{:available? :probe-class} (set (keys result))))
      (is (true? available?)))
    (testing "the probe Class is a value class"
      (is (class? probe-class))
      (is (capability/value-class? probe-class)))))

(deftest probe-instance-fn-test
  (testing "the fn makes an instance with its value"
    (is (= 42 (.getLong (.getField ^Class support/builder-probe-class "value")
                        ((capability/probe-instance-fn support/builder-probe-class) 42)))))
  (testing "a constructor exception propagates as its cause"
    (is (thrown-with-msg? UnsupportedOperationException #"probe constructor failed"
                          ((capability/probe-instance-fn support/throwing-class) 42)))))

(deftest require-value-capability-test
  (testing "an available capability returns nil"
    (with-redefs [capability/capability (constantly {:available? true})]
      (is (nil? (capability/require-value-capability! "a.B")))))
  (testing "an unavailable capability throws with key, class name and reason"
    (with-redefs [capability/capability
                  (constantly {:available? false :reason :failed-self-check
                               :detail "probe detail"})]
      (let [e (try (capability/require-value-capability! "a.B")
                   (catch clojure.lang.ExceptionInfo e e))]
        (is (= {:value-type/error :value-capability-unavailable
                :class-name "a.B" :reason :failed-self-check}
               (ex-data e)))
        (is (= (str "Cannot define value class a.B: fixed value "
                    "representation is unavailable. "
                    (if (capability/no-value-classes?)
                      capability/no-value-classes-message
                      "probe detail"))
               (ex-message e)))))))
