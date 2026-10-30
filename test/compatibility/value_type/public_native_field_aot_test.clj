(ns value-type.public-native-field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.function-aot-checks :as function-checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def native-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Holder [^Long ^:non-nil required ^long count ^double weight nullable]
     java.util.concurrent.Callable
     (call [this] (fn [] (.-required this))))")

(def ^:private consumer-spec
  (assoc checks/string-holder :valid [42] :invalid [[nil] ["wrong"]]))

(def ^:private consumer-result
  (checks/holder-result
   true
   {"required" {:type "java.lang.Long" :final? true
                :annotations ["jdk.internal.vm.annotation.NullRestricted"]}}
   {:stored {"required" 42 "count" 7 "weight" 2.5 "nullable" nil}
    :read {"required" 42}
    :call 42
    :rejections ["java.lang.NullPointerException" "java.lang.ClassCastException"]}))

(deftest source-free-public-native-value-test
  ;; Public selection must persist native checks in value output without source.
  (testing "all policies retain native value storage and constructor checks"
    (fixture/with-fixture
      'checked.aot-fixture native-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                    policy ["silent" "warn" "error"]]
              (let [consumer (runner/run-jvm
                              (assoc valhalla :policy policy :jvm-opts options
                                     :classpath (fixture/classpath-with valhalla output)
                                     :form (fixture/reload-form 'checked.aot-fixture
                                                                (checks/holder-consumer-form consumer-spec))))
                    expected consumer-result]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= {:before expected :after expected} (:result consumer)) consumer)))))))))

(deftest java-public-native-value-consumer
  ;; Public value output must retain native checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (fixture/with-fixture
      'checked.aot-fixture native-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)
              classpath (fixture/classpath-with valhalla output)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java valhalla classpath output
                                                 "test/resources/non-nil-fields/NativeJavaConsumer.java")]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                        policy ["silent" "warn" "error"]]
                  (let [consumer (runner/run-jvm
                                  (assoc valhalla :classpath classpath :jvm-opts options :policy policy
                                         :form (pr-str (checks/java-consumer-form
                                                        consumer-spec "fields.NativeJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? true
                            :annotations ["jdk.internal.vm.annotation.NullRestricted"]
                            :checks :passed}
                           (:result consumer)) consumer)))))))))))

(deftest incompatible-public-native-value-consumer
  ;; Native artifacts must not change representation on incompatible receivers.
  (testing "source-free native artifacts reject incompatible receivers under every policy"
    (fixture/with-fixture
      'checked.aot-fixture native-source
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
                                       :form (pr-str (checks/incompatible-consumer-form consumer-spec))))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= checks/incompatible-result (:result consumer)) consumer))))))))))

(def native-function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^Long ^:non-nil required]))")

(deftest function-public-native-value-aot-test
  ;; Saved value functions retain native checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (fixture/with-fixture
      'checked.aot-fixture native-function-source
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
                                     :form (pr-str (function-checks/invoke-install-form ["required"] [42] [[nil] ["wrong"]]))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= (function-checks/install-result
                        {:value? true
                         :fields {"required" {:type "java.lang.Long"
                                              :annotations ["jdk.internal.vm.annotation.NullRestricted"]
                                              :value 42}}
                         :rejections ["java.lang.NullPointerException"
                                      "java.lang.ClassCastException"]})
                       (:result consumer)) consumer)))))))))
