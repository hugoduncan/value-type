(ns value-type.deftype-star-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.deftype-star-aot-checks :as checks]
            [value-type.jvm-config :as config]))

(def ^:private consume-form
  '(do (require 'value-type.deftype-star-aot-checks)
       (value-type.deftype-star-aot-checks/consume)))

(deftest identity-artifact-test
  ;; Fallback output stays identity on every receiver; source-free loads
  ;; keep the fixed fallback policy.
  (testing "Java 8 artifacts on baseline and capable receivers"
    (let [configuration (config/configuration)]
      (fixture/source-free-consumers
       'star.aot-fixture checks/fixture-source (config/identity-producer configuration)
       (config/receivers configuration) config/policies
       consume-form
       (fn [policy]
         (if (= "error" policy)
           harness/fallback-rejection
           (checks/expected false)))
       :fallback? true :warning harness/fallback-context))))

(deftest value-artifact-test
  ;; Value output stays value output on capable receivers under every policy.
  (testing "EA3 preview artifacts in normal and interpreter execution"
    (let [{:keys [valhalla]} (config/configuration)]
      (fixture/source-free-consumers
       'star.aot-fixture checks/fixture-source valhalla
       (config/with-executions valhalla) config/policies
       consume-form
       (constantly (checks/expected true))))))

(deftest incompatible-value-artifact-test
  ;; Incompatible receivers reject value output; no conversion to identity.
  (testing "source-free value artifacts on baseline and preview off"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (doseq [owner ["star.aot_fixture.Plain" "star.aot_fixture.Rec"]]
        (fixture/incompatible-consumers
         'star.aot-fixture checks/fixture-source valhalla
         (config/incompatible-receivers configuration)
         {:source "star/aot_fixture.clj" :owner owner})))))
