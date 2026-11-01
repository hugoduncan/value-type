(ns value-type.public-native-field-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.function-aot-checks :as function-checks]
            [value-type.jvm-config :as config]))

(def native-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Holder [^Long ^:non-nil required ^long count ^double weight nullable]
     java.util.concurrent.Callable
     (call [this] (fn [] (.-required this))))")

(def ^:private consumer-spec
  (assoc checks/string-holder :valid [42] :invalid (checks/invalid-arguments [42] ["wrong"])))

(def ^:private fields
  "The expected checked field facts of the saved Holder."
  {"required" {:type "java.lang.Long" :final? true
               :annotations checks/native-annotations}})

(def ^:private consumer-result
  (checks/holder-result consumer-spec fields true 42))

(deftest source-free-public-native-value-test
  ;; Public selection must persist native checks in value output without source.
  (testing "all policies retain native value storage and constructor checks"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture native-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (fixture/reload-form 'checked.aot-fixture (checks/holder-consumer-form consumer-spec))
       (constantly {:before consumer-result :after consumer-result})))))

(deftest java-public-native-value-consumer
  ;; Public value output must retain native checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture native-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (checks/java-consumer-form consumer-spec "fields.NativeJavaConsumer")
       (constantly (checks/java-consumer-result true checks/native-annotations))
       :java ["test/resources/non-nil-fields/NativeJavaConsumer.java"]))))

(deftest incompatible-public-native-value-consumer
  ;; Native artifacts must not change representation on incompatible receivers.
  (testing "source-free native artifacts reject incompatible receivers under every policy"
    (let [configuration (config/configuration)]
      (fixture/incompatible-consumers 'checked.aot-fixture native-source (config/value-producer configuration)
                                      (config/incompatible-receiver-jvms configuration)
                                      consumer-spec))))

(def native-function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^Long ^:non-nil required]))")

(def ^:private function-spec
  "The spec of the function Holder [required] of native-function-source."
  (assoc consumer-spec :arguments '(fn [required] [required])))

(deftest function-public-native-value-aot-test
  ;; Saved value functions retain native checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture native-function-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (function-checks/invoke-install-form function-spec)
       (constantly
        (function-checks/install-result
         (function-checks/holder-invocation function-spec fields true)))))))
