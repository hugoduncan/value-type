(ns value-type.array-sequence-boundary-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private exports
  "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED")

(defn- configurations
  []
  (let [{:keys [baseline valhalla]} (config/configuration)]
    [[baseline :missing-classfile-api]
     [(assoc valhalla :jvm-opts []) :unsupported-class-version]
     [(assoc valhalla :jvm-opts [exports]) :unsupported-class-version]
     [valhalla :array-access-denied]
     [(update valhalla :jvm-opts conj exports) nil]
     [(update valhalla :jvm-opts into [exports "-XX:-UseArrayFlattening"]) nil]]))

(deftest sequence-configuration-matrix-test
  ;; Each order and policy gets a fresh process; no value fixture loads in controls.
  (doseq [[configuration reason] (configurations)
          policy ["silent" "warn" "error"]
          require-order [['value-type.array 'value-type.core]
                         ['value-type.core 'value-type.array]]]
    (testing (pr-str [(:jvm-opts configuration) reason policy require-order])
      (let [{:keys [exit result err]}
            (runner/run-jvm
             (assoc configuration :policy policy :timeout-ms 120000
                    :form (str "(require 'value-type.array-sequence-checks) "
                               "(prn (value-type.array-sequence-checks/check '"
                               (pr-str require-order) " " (nil? reason) "))")))
            outcomes (:outcomes result)]
        (is (= 0 exit) err)
        (is (not (re-find #"(?i)fallback" err)) err)
        (is (seq (:runtime result)))
        (is (= policy (:policy result)))
        (is (= require-order (:require-order result)))
        (is (= (conj (:jvm-opts configuration)
                     (str "-Dvalue-type.fallback=" policy))
               (:flags result)))
        (if reason
          (do
            (is (= [false true] (mapv :explicit? outcomes)))
            (doseq [{:keys [calls]} outcomes]
              (is (= 2 (count calls)))
              (is (= (first calls) (second calls)))
              (doseq [outcome calls]
                (is (= :rejected (:status outcome)))
                (is (= 0 (:effects outcome)))
                (is (= :array-unavailable (get-in outcome [:data :value-type/error])))
                (is (= reason (get-in outcome [:data :reason])))
                (is (= "clojure.lang.ExceptionInfo" (:exception outcome)))
                (is (= (when (= reason :array-access-denied) "java.lang.IllegalAccessException")
                       (:cause outcome)))
                (is (re-find #"--enable-preview" (:message outcome)))
                (is (re-find #"--add-exports" (:message outcome))))))
          (is (= [{:status :constructed :component? true :length 2 :contents? true}
                  {:status :constructed :component? true :length 2 :contents? true}
                  {:status :constructed :component? true :length 0 :contents? true}]
                 outcomes)))))))
