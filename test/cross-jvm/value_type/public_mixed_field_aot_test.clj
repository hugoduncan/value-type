(ns value-type.public-mixed-field-aot-test
  (:require [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.consumer-aot-checks :as checks]
            [value-type.function-aot-checks :as function-checks]
            [value-type.jvm-config :as config]))

(def mixed-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype Holder [^Long ^:non-nil required ^long count ^String ^:non-nil text ^double weight nullable]
     java.util.concurrent.Callable
     (call [this] (fn [] [(.-required this) (.-text this)])))")

(def ^:private consumer-spec
  (assoc checks/string-holder
         :checked ["required" "text"]
         :arguments '(fn [required text] [required 7 text 2.5 nil])
         :valid [42 "text"]
         :invalid (checks/invalid-arguments [42 "text"] ["wrong" :wrong])))

(def ^:private fields
  "The expected checked field facts of the saved Holder."
  {"required" {:type "java.lang.Long" :final? true
               :annotations checks/native-annotations}
   "text" {:type "java.lang.String" :final? true :annotations []}})

(def ^:private consumer-result
  (checks/holder-result consumer-spec fields true [42 "text"]))

(deftest source-free-public-mixed-value-test
  ;; Public selection must persist both enforcement modes in value output without source.
  (testing "all policies retain mixed value storage and constructor checks"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture mixed-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (fixture/reload-form 'checked.aot-fixture (checks/holder-consumer-form consumer-spec))
       (constantly {:before consumer-result :after consumer-result})))))

(deftest incompatible-public-mixed-value-consumer
  ;; Mixed artifacts must not change representation on incompatible receivers.
  (testing "source-free mixed artifacts reject incompatible receivers under every policy"
    (let [configuration (config/configuration)]
      (fixture/incompatible-consumers 'checked.aot-fixture mixed-source (config/value-producer configuration)
                                      (config/incompatible-receiver-jvms configuration)
                                      consumer-spec))))

(deftest java-public-mixed-value-consumer
  ;; Public value output must retain native and generated checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture mixed-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (checks/java-consumer-form consumer-spec "fields.MixedJavaConsumer")
       (constantly (checks/java-consumer-result true checks/native-annotations))
       :java ["test/resources/non-nil-fields/MixedJavaConsumer.java"]))))

(def mixed-function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^Long ^:non-nil required ^String ^:non-nil text]))")

(def ^:private function-spec
  "The spec of the function Holder [required text] of mixed-function-source."
  (assoc consumer-spec :arguments '(fn [required text] [required text])))

(deftest function-public-mixed-value-aot-test
  ;; Saved value functions retain both field checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/source-free-consumers
       'checked.aot-fixture mixed-function-source (config/value-producer configuration)
       (config/with-executions valhalla) config/policies
       (function-checks/invoke-install-form function-spec)
       (constantly
        (function-checks/install-result
         (function-checks/holder-invocation function-spec fields true)))))))

(defn- persisted-output?
  "Return a predicate: true when the inspected Holder output stores field
   with descriptor and annotations, and has companion references of
   descriptor only."
  [field descriptor annotations]
  (fixture/persisted-output?
   {:fields {field descriptor
             "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
    :annotations annotations
    :constructors ["(Ljava/lang/Object;JLjava/lang/Object;DLjava/lang/Object;)V"]}
   descriptor))

(deftest persisted-public-mixed-output-test
  ;; Inspect real value compiler files separately from executing saved classes.
  (testing "public mixed AOT persists concrete storage and companion descriptors"
    (let [{:keys [valhalla] :as configuration} (config/configuration)]
      (fixture/with-produced
        'checked.aot-fixture mixed-source (config/value-producer configuration)
        (fn [{:keys [output]}]
          ;; Both inspections read files only; no artifact is on the class
          ;; path, so they share one child.
          (harness/run-cells
           [(fixture/inspection-child
             valhalla output
             (for [[field descriptor annotations]
                   [["required" "Ljava/lang/Long;" (mapv fixture/descriptor checks/native-annotations)]
                    ["text" "Ljava/lang/String;" []]]]
               [[field] field (persisted-output? field descriptor annotations)]))]))))))
