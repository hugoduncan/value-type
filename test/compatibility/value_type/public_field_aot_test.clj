(ns value-type.public-field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.function-aot-checks :as checks]
            [value-type.consumer-aot-checks :as consumer-checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def identity-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Holder [^String ^:non-nil required ^long count ^double weight nullable]
     java.util.concurrent.Callable
     (call [this] (fn [] (.-required this))))")

(deftest source-free-public-identity-test
  ;; Public AOT retains constructor checks and fixed fallback policy without source.
  (testing "permitted receivers retain identity storage and companion reads after reload"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc baseline :policy "silent") paths 'checked.aot-fixture)
            (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
                    interpreter? [false true]
                    :let [jvm (cond-> runtime
                                interpreter? (update :jvm-opts conj "-Xint"))]
                    policy ["silent" "warn"]]
              (let [consumer (runner/run-jvm
                              (assoc jvm :policy policy
                                     :classpath (fixture/classpath-with jvm output)
                                     :form (fixture/reload-form
                                            'checked.aot-fixture
                                            (consumer-checks/holder-consumer-form consumer-checks/string-holder))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= (if (= "warn" policy)
                         "value-type: identity deftype fallback: fixed fallback representation\n"
                         "")
                       (:err consumer)) consumer)
                (let [expected (consumer-checks/string-holder-result false)]
                  (is (= {:before expected :after expected} (:result consumer)) consumer))))))))))

(deftest java-public-identity-consumer
  ;; Direct Java access enforces fields but does not evaluate namespace policy guards.
  (testing "Java 8 constructors retain checks on baseline and capable receivers"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)
              classpath (fixture/classpath-with baseline output)]
          (when (fixture/compile-producer (assoc baseline :policy "silent") paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java baseline classpath output
                                                 "test/resources/non-nil-fields/IdentityJavaConsumer.java"
                                                 :baseline? true)]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
                        interpreter? [false true]
                        policy ["silent" "warn" "error"]
                        :let [jvm (cond-> runtime
                                    interpreter? (update :jvm-opts conj "-Xint"))]]
                  (let [consumer (runner/run-jvm
                                  (assoc jvm :policy policy :classpath classpath
                                         :form (pr-str (consumer-checks/java-consumer-form
                                                        consumer-checks/string-holder "fields.IdentityJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? false :annotations [] :checks :passed}
                           (:result consumer)) consumer)))))))))))

(deftest strict-public-identity-aot-test
  ;; Saved identity definitions must not bypass strict policy on capable receivers.
  (testing "strict namespace loading repeatedly rejects source-free identity output"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc baseline :policy "silent") paths 'checked.aot-fixture)
            (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
                    interpreter? [false true]
                    :let [jvm (cond-> runtime
                                interpreter? (update :jvm-opts conj "-Xint"))]]
              (let [consumer (runner/run-jvm
                              (assoc jvm :policy "error"
                                     :classpath (fixture/classpath-with jvm output)
                                     :form (pr-str (consumer-checks/strict-consumer-form
                                                    consumer-checks/string-holder))))
                    [first-load second-load] (:loads (:result consumer))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (true? (:source-absent? (:result consumer))) consumer)
                (is (= (assoc consumer-checks/fallback-rejection :factory-bound? false)
                       first-load) consumer)
                ;; Failed JVM initialization is sticky; retry wrappers differ by JDK.
                (is (contains? #{"java.lang.NoClassDefFoundError"
                                 "java.lang.ExceptionInInitializerError"}
                               (:class second-load)) consumer)
                (is (false? (:factory-bound? second-load)) consumer)))))))))

(def function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^String ^:non-nil required]))")

(def ^:private string-holder
  {:fields {"required" {:type "java.lang.String" :annotations [] :value "valid"}}
   :rejections ["java.lang.NullPointerException" "java.lang.ClassCastException"]})

(deftest function-public-identity-aot-test
  ;; Loading a saved function defers fixed fallback policy until each invocation.
  (testing "source-free function invocation retains policy timing and constructor checks"
    (fixture/with-fixture
      'checked.aot-fixture function-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc baseline :policy "silent") paths 'checked.aot-fixture)
            (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
                    interpreter? [false true]
                    policy ["silent" "warn" "error"]
                    :let [jvm (cond-> runtime
                                interpreter? (update :jvm-opts conj "-Xint"))]]
              (let [consumer (runner/run-jvm
                              (assoc jvm :policy policy
                                     :classpath (fixture/classpath-with jvm output)
                                     :form (pr-str (checks/invoke-install-form ["required"] ["valid"] [[nil] [42]]))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= (if (= "warn" policy)
                         "value-type: identity deftype fallback: fixed fallback representation\n"
                         "") (:err consumer)) consumer)
                (is (= (checks/install-result (if (= "error" policy)
                                                consumer-checks/fallback-rejection
                                                (assoc string-holder :value? false)))
                       (:result consumer)) consumer)))))))))

(deftest source-free-public-generated-value-test
  ;; Public selection must persist generated checks in value output without source.
  (testing "all policies retain generated value storage and constructor checks"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                    policy ["silent" "warn" "error"]]
              (let [consumer (runner/run-jvm
                              (assoc valhalla :policy policy :jvm-opts options
                                     :classpath (fixture/classpath-with valhalla output)
                                     :form (fixture/reload-form
                                            'checked.aot-fixture
                                            (consumer-checks/holder-consumer-form consumer-checks/string-holder))))
                    expected (consumer-checks/string-holder-result true)]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= {:before expected :after expected} (:result consumer)) consumer)))))))))

(deftest java-public-generated-value-consumer
  ;; Public value output must retain generated checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)
              classpath (fixture/classpath-with valhalla output)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java valhalla classpath output
                                                 "test/resources/non-nil-fields/IdentityJavaConsumer.java")]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                        policy ["silent" "warn" "error"]]
                  (let [consumer (runner/run-jvm
                                  (assoc valhalla :classpath classpath :jvm-opts options :policy policy
                                         :form (pr-str (consumer-checks/java-consumer-form
                                                        consumer-checks/string-holder "fields.IdentityJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? true :annotations [] :checks :passed}
                           (:result consumer)) consumer)))))))))))

(deftest incompatible-public-generated-value-consumer
  ;; Generated checks must not permit saved value output to change representation.
  (testing "source-free generated artifacts reject incompatible receivers under every policy"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (doseq [receiver [baseline (assoc valhalla :jvm-opts [])]
                    interpreter? [false true]
                    policy ["silent" "warn" "error"]
                    :let [jvm (cond-> receiver
                                interpreter? (update :jvm-opts conj "-Xint"))]]
              (testing (str (:java receiver) " / " policy)
                (let [consumer (runner/run-jvm
                                (assoc jvm :policy policy
                                       :classpath (fixture/classpath-with receiver output)
                                       :form (pr-str (consumer-checks/incompatible-consumer-form
                                                      consumer-checks/string-holder))))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= consumer-checks/incompatible-result (:result consumer)) consumer))))))))))

(deftest function-public-generated-value-aot-test
  ;; Saved value functions retain generated checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (fixture/with-fixture
      'checked.aot-fixture function-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (doseq [interpreter? [false true]
                    policy ["silent" "warn" "error"]
                    :let [jvm (cond-> valhalla
                                interpreter? (update :jvm-opts conj "-Xint"))]]
              (let [consumer (runner/run-jvm
                              (assoc jvm :policy policy
                                     :classpath (fixture/classpath-with jvm output)
                                     :form (pr-str (checks/invoke-install-form ["required"] ["valid"] [[nil] [42]]))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= (checks/install-result (assoc string-holder :value? true))
                       (:result consumer)) consumer)))))))))

(deftest persisted-public-identity-output-test
  ;; Inspect real baseline compiler files separately from executing saved classes.
  (testing "public identity AOT persists concrete storage and companion descriptors"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc baseline :policy "silent") paths 'checked.aot-fixture)
            (let [inspection (runner/run-jvm
                              (assoc valhalla :policy "error"
                                     :form (str "(require '[value-type.field-aot-checks :as c]) "
                                                "(prn (c/inspect-output " (pr-str (.getPath output))
                                                " \"checked/aot_fixture/Holder\" \"required\"))")))]
              (is (= 0 (:exit inspection)) inspection)
              (is (= "" (:err inspection)) inspection)
              (is (= {:fields {"required" "Ljava/lang/String;"
                               "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
                      :annotations []
                      :constructors ["(Ljava/lang/Object;JDLjava/lang/Object;)V"]}
                     (dissoc (:result inspection) :companion-references)) inspection)
              (is (seq (get-in inspection [:result :companion-references])) inspection)
              (is (every? #{"Ljava/lang/String;"}
                          (get-in inspection [:result :companion-references])) inspection))))))))

(deftest persisted-public-generated-value-output-test
  ;; Inspect real value compiler files separately from executing saved classes.
  (testing "public generated-value AOT persists concrete storage and companion descriptors"
    (fixture/with-fixture
      'checked.aot-fixture identity-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (let [inspection (runner/run-jvm
                              (assoc valhalla :policy "error"
                                     :form (str "(require '[value-type.field-aot-checks :as c]) "
                                                "(prn (c/inspect-output " (pr-str (.getPath output))
                                                " \"checked/aot_fixture/Holder\" \"required\"))")))]
              (is (= 0 (:exit inspection)) inspection)
              (is (= "" (:err inspection)) inspection)
              (is (= {:fields {"required" "Ljava/lang/String;"
                               "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
                      :annotations []
                      :constructors ["(Ljava/lang/Object;JDLjava/lang/Object;)V"]}
                     (dissoc (:result inspection) :companion-references)) inspection)
              (is (seq (get-in inspection [:result :companion-references])) inspection)
              (is (every? #{"Ljava/lang/String;"}
                          (get-in inspection [:result :companion-references])) inspection))))))))
