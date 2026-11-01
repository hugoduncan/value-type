(ns value-type.public-field-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.function-aot-checks :as function-checks]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

(def identity-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Holder [^String ^:non-nil required ^long count ^double weight nullable]
     java.util.concurrent.Callable
     (call [this] (fn [] (.-required this))))")

(def ^:private identity-java-consumer
  "test/resources/non-nil-fields/IdentityJavaConsumer.java")

(def ^:private holder-form
  (fixture/reload-form 'checked.aot-fixture
                       (checks/holder-consumer-form checks/string-holder)))

(defn- reloaded [value?]
  (let [expected (checks/string-holder-result value?)]
    {:before expected :after expected}))

(deftest source-free-public-identity-test
  ;; Public AOT retains constructor checks and fixed fallback policy without source.
  (testing "permitted receivers retain identity storage and companion reads after reload"
    (let [configuration (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture identity-source (config/identity-producer configuration)
       (config/receiver-jvms configuration) ["silent" "warn"]
       holder-form (constantly (reloaded false))
       :fallback? true :warning harness/fallback-context))))

(deftest java-public-identity-consumer
  ;; Direct Java access enforces fields but does not evaluate namespace policy guards.
  (testing "Java 8 constructors retain checks on baseline and capable receivers"
    (let [configuration (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture identity-source (config/identity-producer configuration)
       (config/receiver-jvms configuration) config/policies
       (checks/java-consumer-form checks/string-holder "fields.IdentityJavaConsumer")
       (constantly (checks/java-consumer-result false []))
       :java [identity-java-consumer :baseline? true]))))

(def ^:private strict-loads?
  "True when the strict-consumer result rejects both loads. Failed JVM
   initialization is sticky; the retry wrapper differs by JDK."
  (let [first-expected (assoc harness/fallback-rejection :factory-bound? false)
        retry-classes #{"java.lang.NoClassDefFoundError"
                        "java.lang.ExceptionInInitializerError"}]
    (harness/described
     (harness/keyed
      {:source-absent? (harness/fixed true)
       :loads (harness/entries
               [(harness/fixed first-expected)
                (harness/including {:class [retry-classes #(contains? retry-classes %)]
                                    :factory-bound? (harness/fixed false)})])}))))

(deftest strict-public-identity-aot-test
  ;; Saved identity definitions must not bypass strict policy on capable receivers.
  (testing "strict namespace loading repeatedly rejects source-free identity output"
    (let [configuration (config/configuration)]
      ;; The retry is a second load in the same cell (isolation rule 5).
      (fixture/source-free-consumers
       'checked.aot-fixture identity-source (config/identity-producer configuration)
       (config/receiver-jvms configuration) ["error"]
       (checks/strict-consumer-form checks/string-holder)
       (constantly strict-loads?)
       :fallback? true :warning harness/fallback-context))))

(def function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^String ^:non-nil required]))")

(def ^:private function-spec
  "The spec of the function Holder [required] of function-source."
  (assoc checks/string-holder :arguments '(fn [required] [required])))

(defn- function-holder
  "Return the expected install invocation facts of function-spec."
  [value?]
  (function-checks/holder-invocation function-spec checks/string-holder-fields value?))

(def ^:private install-form
  (function-checks/invoke-install-form function-spec))

(deftest function-public-identity-aot-test
  ;; Loading a saved function defers fixed fallback policy until each invocation.
  (testing "source-free function invocation retains policy timing and constructor checks"
    (let [configuration (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture function-source (config/identity-producer configuration)
       (config/receiver-jvms configuration) config/policies
       install-form
       #(function-checks/install-result (if (= "error" %)
                                          harness/fallback-rejection
                                          (function-holder false)))
       :fallback? true :warning harness/fallback-context))))

(deftest source-free-public-generated-value-test
  ;; Public selection must persist generated checks in value output without source.
  (testing "all policies retain generated value storage and constructor checks"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture identity-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       holder-form (constantly (reloaded true))))))

(deftest java-public-generated-value-consumer
  ;; Public value output must retain generated checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture identity-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (checks/java-consumer-form checks/string-holder "fields.IdentityJavaConsumer")
       (constantly (checks/java-consumer-result true []))
       :java [identity-java-consumer]))))

(deftest incompatible-public-generated-value-consumer
  ;; Generated checks must not permit saved value output to change representation.
  (testing "source-free generated artifacts reject incompatible receivers under every policy"
    (let [configuration (config/configuration)]
      (fixture/incompatible-consumers 'checked.aot-fixture identity-source (config/value-producer configuration)
                                      (config/incompatible-receiver-jvms configuration)
                                      checks/string-holder))))

(deftest function-public-generated-value-aot-test
  ;; Saved value functions retain generated checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture function-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       install-form (constantly (function-checks/install-result (function-holder true)))))))

(def ^:private persisted-output?
  "True when the inspected Holder output has concrete storage and companion
   descriptors."
  (fixture/persisted-output?
   {:fields {"required" "Ljava/lang/String;"
             "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
    :annotations []
    :constructors ["(Ljava/lang/Object;JDLjava/lang/Object;)V"]}
   "Ljava/lang/String;"))

(defn- inspection
  "Return the one-cell inspection child of output on the valhalla receiver."
  [valhalla ^java.io.File output]
  (fixture/inspection-child
   valhalla output
   [[[(harness/runtime-context valhalla "error")] "required" persisted-output?]]))

(deftest persisted-public-identity-output-test
  ;; Inspect real baseline compiler files separately from executing saved classes.
  (testing "public identity AOT persists concrete storage and companion descriptors"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/with-produced
        'checked.aot-fixture identity-source (config/identity-producer configuration)
        (fn [{:keys [output]}]
          (harness/run-cells [(inspection valhalla output)]))))))

(deftest persisted-public-generated-value-output-test
  ;; Inspect real value compiler files separately from executing saved classes.
  (testing "public generated-value AOT persists concrete storage and companion descriptors"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/with-produced
        'checked.aot-fixture identity-source (config/value-producer configuration)
        (fn [{:keys [output]}]
          (harness/run-cells [(inspection valhalla output)]))))))
