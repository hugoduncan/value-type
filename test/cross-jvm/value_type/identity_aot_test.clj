(ns value-type.identity-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

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
    (let [{:keys [baseline] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture checked-source baseline
       (config/receivers configuration) config/policies
       (checks/holder-consumer-form checks/string-holder)
       (constantly (checks/string-holder-result false))))))

(deftest java-identity-backend-consumer
  ;; Compile Java against saved identity output, then execute without producer source.
  (testing "Java 8 constructors retain checks on baseline and capable receivers"
    (let [{:keys [baseline] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture checked-source baseline
       (config/receivers configuration) [nil]
       (checks/java-consumer-form checks/string-holder "fields.IdentityJavaConsumer")
       (constantly (checks/java-consumer-result false []))
       :java ["test/resources/non-nil-fields/IdentityJavaConsumer.java" :baseline? true]))))
