(ns value-type.generated-value-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def checked-source
  "(ns checked.aot-fixture (:require [value-type.impl.loader :as loader]))
   (defmacro define-holder []
     (loader/install! \"checked.aot_fixture.Holder\"
       [{:field 'required :type String :enforcement :generated}] :value)
     `(let []
        (deftype ~'Holder [~'required ~'^long count ~'^double weight ~'nullable]
          java.util.concurrent.Callable
          (~'call [~'this] (fn [] (. ~'this ~'-required))))))
   (define-holder)")

(deftest java-generated-value-consumer
  ;; Real Java calls must retain generated checks in saved value-class output.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (fixture/with-fixture
      'checked.aot-fixture checked-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)
              classpath (fixture/classpath-with valhalla output)]
          (when (fixture/compile-producer valhalla paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java valhalla classpath output
                                                 "test/resources/non-nil-fields/IdentityJavaConsumer.java")]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                        policy ["silent" "warn" "error"]]
                  (let [consumer (runner/run-jvm
                                  (assoc valhalla :classpath classpath :jvm-opts options :policy policy
                                         :form (pr-str (checks/java-consumer-form
                                                        checks/string-holder "fields.IdentityJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? true :annotations [] :checks :passed}
                           (:result consumer)) consumer)))))))))))

(deftest clojure-generated-value-consumer
  ;; Saved generated value checks must apply at every Clojure construction entry.
  (testing "source-free direct, factory and reflective construction"
    (fixture/with-fixture
      'checked.aot-fixture checked-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer valhalla paths 'checked.aot-fixture)
            (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                    policy ["silent" "warn" "error"]]
              (let [consumer (runner/run-jvm
                              (assoc valhalla
                                     :classpath (fixture/classpath-with valhalla output)
                                     :jvm-opts options
                                     :policy policy
                                     :form (pr-str (checks/holder-consumer-form checks/string-holder))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= (checks/string-holder-result true) (:result consumer)) consumer)))))))))

(deftest incompatible-generated-value-consumer
  ;; Generated checks must not permit saved value output to change representation.
  (testing "source-free generated artifacts reject incompatible receivers under every policy"
    (fixture/with-fixture
      'checked.aot-fixture checked-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer valhalla paths 'checked.aot-fixture)
            (doseq [receiver [baseline (assoc valhalla :jvm-opts [])]
                    policy ["silent" "warn" "error"]]
              (testing (str (:java receiver) " / " policy)
                (let [consumer (runner/run-jvm
                                (assoc receiver :policy policy
                                       :classpath (fixture/classpath-with receiver output)
                                       :form (pr-str (checks/incompatible-consumer-form checks/string-holder))))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= "" (:err consumer)) consumer)
                  (is (= checks/incompatible-result (:result consumer)) consumer))))))))))
