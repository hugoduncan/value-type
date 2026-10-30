(ns value-type.public-mixed-field-aot-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as checks]
            [value-type.function-aot-checks :as function-checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

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
         :invalid [[nil "text"] ["wrong" "text"] [42 nil] [42 :wrong]]))

(def ^:private consumer-result
  (checks/holder-result
   true
   {"required" {:type "java.lang.Long" :final? true
                :annotations ["jdk.internal.vm.annotation.NullRestricted"]}
    "text" {:type "java.lang.String" :final? true :annotations []}}
   {:stored {"required" 42 "count" 7 "text" "text" "weight" 2.5 "nullable" nil}
    :read {"required" 42 "text" "text"}
    :call [42 "text"]
    :rejections ["java.lang.NullPointerException" "java.lang.ClassCastException"
                 "java.lang.NullPointerException" "java.lang.ClassCastException"]}))

(deftest source-free-public-mixed-value-test
  ;; Public selection must persist both enforcement modes in value output without source.
  (testing "all policies retain mixed value storage and constructor checks"
    (fixture/with-fixture
      'checked.aot-fixture mixed-source
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

(deftest incompatible-public-mixed-value-consumer
  ;; Mixed artifacts must not change representation on incompatible receivers.
  (testing "source-free mixed artifacts reject incompatible receivers under every policy"
    (fixture/with-fixture
      'checked.aot-fixture mixed-source
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

(deftest java-public-mixed-value-consumer
  ;; Public value output must retain native and generated checks for separate Java consumers.
  (testing "source-free value constructors and companions in normal and interpreter execution"
    (fixture/with-fixture
      'checked.aot-fixture mixed-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)
              classpath (fixture/classpath-with valhalla output)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (let [compiler (fixture/compile-java valhalla classpath output
                                                 "test/resources/non-nil-fields/MixedJavaConsumer.java")]
              (is (= 0 (:exit compiler)) compiler)
              (is (= 0 (:result compiler)) compiler)
              (when (= 0 (:result compiler))
                (doseq [options [["--enable-preview"] ["--enable-preview" "-Xint"]]
                        policy ["silent" "warn" "error"]]
                  (let [consumer (runner/run-jvm
                                  (assoc valhalla :classpath classpath :jvm-opts options :policy policy
                                         :form (pr-str (checks/java-consumer-form
                                                        consumer-spec "fields.MixedJavaConsumer"))))]
                    (is (= 0 (:exit consumer)) consumer)
                    (is (= "" (:err consumer)) consumer)
                    (is (= {:source-absent? true :value? true
                            :annotations ["jdk.internal.vm.annotation.NullRestricted"]
                            :checks :passed}
                           (:result consumer)) consumer)))))))))))

(def mixed-function-source
  "(ns checked.aot-fixture (:require [value-type.core :refer [defvaluetype]]))
   (defn install []
     (defvaluetype Holder [^Long ^:non-nil required ^String ^:non-nil text]))")

(deftest function-public-mixed-value-aot-test
  ;; Saved value functions retain both field checks without fallback policy effects.
  (testing "source-free value function loading and repeated invocation stay silent under every policy"
    (fixture/with-fixture
      'checked.aot-fixture mixed-function-source
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
                                     :form (pr-str (function-checks/invoke-install-form ["required" "text"] [42 "text"]
                                                                                        [[nil "text"] ["wrong" "text"] [42 nil] [42 :wrong]]))))]
                (is (= 0 (:exit consumer)) consumer)
                (is (= "" (:err consumer)) consumer)
                (is (= (function-checks/install-result
                        {:value? true
                         :fields {"required" {:type "java.lang.Long"
                                              :annotations ["jdk.internal.vm.annotation.NullRestricted"]
                                              :value 42}
                                  "text" {:type "java.lang.String" :annotations [] :value "text"}}
                         :rejections ["java.lang.NullPointerException"
                                      "java.lang.ClassCastException"
                                      "java.lang.NullPointerException"
                                      "java.lang.ClassCastException"]})
                       (:result consumer)) consumer)))))))))

(deftest persisted-public-mixed-output-test
  ;; Inspect real value compiler files separately from executing saved classes.
  (testing "public mixed AOT persists concrete storage and companion descriptors"
    (fixture/with-fixture
      'checked.aot-fixture mixed-source
      (fn [{:keys [output] :as paths}]
        (let [{:keys [valhalla]} (config/configuration)]
          (when (fixture/compile-producer (assoc valhalla :policy "error") paths 'checked.aot-fixture)
            (doseq [[field descriptor annotations]
                    [["required" "Ljava/lang/Long;" ["Ljdk/internal/vm/annotation/NullRestricted;"]]
                     ["text" "Ljava/lang/String;" []]]]
              (let [inspection (runner/run-jvm
                                (assoc valhalla :policy "error"
                                       :form (str "(require '[value-type.field-aot-checks :as c]) "
                                                  "(prn (c/inspect-output " (pr-str (.getPath output))
                                                  " \"checked/aot_fixture/Holder\" " (pr-str field) "))")))]
                (is (= 0 (:exit inspection)) inspection)
                (is (= "" (:err inspection)) inspection)
                (is (= {:fields {field descriptor
                                 "nullable" "Ljava/lang/Object;" "count" "J" "weight" "D"}
                        :annotations annotations
                        :constructors ["(Ljava/lang/Object;JLjava/lang/Object;DLjava/lang/Object;)V"]}
                       (dissoc (:result inspection) :companion-references)) inspection)
                (is (seq (get-in inspection [:result :companion-references])) inspection)
                (is (every? #{descriptor}
                            (get-in inspection [:result :companion-references])) inspection)))))))))
