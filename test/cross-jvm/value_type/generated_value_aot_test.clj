(ns value-type.generated-value-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]))

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
    (let [{:keys [valhalla]} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture checked-source valhalla
       (config/with-executions valhalla) config/policies
       (checks/java-consumer-form checks/string-holder "fields.IdentityJavaConsumer")
       (constantly (checks/java-consumer-result true []))
       :java ["test/resources/non-nil-fields/IdentityJavaConsumer.java"]))))

(deftest clojure-generated-value-consumer
  ;; Saved generated value checks must apply at every Clojure construction entry.
  (testing "source-free direct, factory and reflective construction"
    (let [{:keys [valhalla]} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture checked-source valhalla
       (config/with-executions valhalla) config/policies
       (checks/holder-consumer-form checks/string-holder)
       (constantly (checks/string-holder-result true))))))

(deftest incompatible-generated-value-consumer
  ;; Generated checks must not permit saved value output to change representation.
  (testing "source-free generated artifacts reject incompatible receivers under every policy"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/incompatible-consumers 'checked.aot-fixture checked-source valhalla
                                      (config/incompatible-receivers configuration)
                                      checks/string-holder))))
