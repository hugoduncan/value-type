(ns value-type.capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.capability :refer [report]]
            [value-type.impl.array :as adapter]
            [value-type.impl.capability :as capability]
            [value-type.impl.field-capability :as field-capability]))

(def ^:private predicate
  (.getMethod Object "equals" (into-array Class [Object])))

(deftest report-translation-test
  (testing "report keeps only :available? and :reason of each owner result"
    (testing "in the :export-only state"
      (let [unavailable {:available? false
                         :reason :unsupported-class-version
                         :detail "class version 69"}]
        (with-redefs [capability/capability (constantly unavailable)
                      field-capability/capability (constantly unavailable)
                      adapter/allocation-capability (constantly unavailable)
                      adapter/inspection-capability
                      (constantly {:available? true
                                   :predicate predicate
                                   :null-restricted? predicate
                                   :atomic? predicate
                                   :flat? predicate})]
          (is (= {:value-class {:available? false
                                :reason :unsupported-class-version}
                  :native-non-nil-field {:available? false
                                         :reason :unsupported-class-version}
                  :array-allocation {:available? false
                                     :reason :unsupported-class-version}
                  :array-inspection {:available? true}}
                 (report))))))
    (testing "in the :no-export state"
      (let [denied {:available? false
                    :reason :array-access-denied
                    :cause (IllegalAccessException. "not exported")}]
        (with-redefs [capability/capability (constantly {:available? true})
                      field-capability/capability (constantly {:available? true})
                      adapter/allocation-capability (constantly denied)
                      adapter/inspection-capability (constantly denied)]
          (is (= {:value-class {:available? true}
                  :native-non-nil-field {:available? true}
                  :array-allocation {:available? false
                                     :reason :array-access-denied}
                  :array-inspection {:available? false
                                     :reason :array-access-denied}}
                 (report))))))))

(deftest report-defect-propagation-test
  (testing "a probe defect propagates and no partial report is returned"
    (let [defect (RuntimeException. "probe defect")
          thrown (with-redefs [adapter/inspection-capability
                               (fn [] (throw defect))]
                   (try
                     (report)
                     nil
                     (catch Throwable t t)))]
      (is (identical? defect thrown)))))
