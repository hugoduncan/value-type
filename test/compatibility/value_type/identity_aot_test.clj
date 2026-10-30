(ns value-type.identity-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def checked-source
  "(ns checked.aot-fixture (:require [value-type.impl.loader :as loader]))
   (defmacro define-holder []
     (loader/install! \"checked.aot_fixture.Holder\"
       [{:field 'required :type String :enforcement :generated}] :identity)
     `(let []
        (deftype ~'Holder [~'required ~'^long count ~'^double weight ~'nullable]
          java.util.concurrent.Callable
          (~'call [~'this] (fn [] (. ~'this ~'-required))))))
   (define-holder)")

(deftest source-free-identity-backend
  ;; Saved production backend output must retain constructor and companion code.
  (testing "Java 8 artifacts execute without producer source on baseline and capable receivers"
    (fixture/with-fixture
      'checked.aot-fixture checked-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)]
          (when (fixture/compile-producer baseline paths 'checked.aot-fixture)
            (doseq [jvm [baseline (assoc valhalla :jvm-opts []) valhalla]
                    policy ["silent" "warn" "error"]]
              (let [consumer (runner/run-jvm
                              (assoc jvm :policy policy
                                     :classpath (fixture/classpath-with jvm output)
                                     :form (pr-str (checks/holder-consumer-form checks/string-holder))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= (checks/string-holder-result false) (:result consumer)) consumer)))))))))

(deftest java-identity-backend-consumer
  ;; Compile Java against saved identity output, then execute without producer source.
  (testing "Java 8 constructors retain checks on baseline and capable receivers"
    (fixture/with-fixture
      'checked.aot-fixture checked-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [baseline valhalla]} (config/configuration)
              classpath (fixture/classpath-with baseline output)]
          (when (fixture/compile-producer baseline paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java baseline classpath output
                                                 "test/resources/non-nil-fields/IdentityJavaConsumer.java"
                                                 :baseline? true)]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [jvm [baseline (assoc valhalla :jvm-opts []) valhalla]]
                  (let [consumer (runner/run-jvm
                                  (assoc jvm :classpath classpath
                                         :form (pr-str (checks/java-consumer-form
                                                        checks/string-holder "fields.IdentityJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? false :annotations [] :checks :passed}
                           (:result consumer)) consumer)))))))))))
