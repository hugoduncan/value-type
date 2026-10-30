(ns value-type.controlled-field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.consumer-aot-checks :as consumer-checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private controlled-absence
  "with-redefs bindings that replace only the native capability accessor."
  '[value-type.impl.runtime/field-capability
    (constantly {:available? false :reason :controlled-absence})])

(def ^:private holder-definition
  '(value-type.core/defvaluetype ControlledHolder [^Long ^:non-nil value]))

(def ^:private holder-check
  "Child function: inspect ControlledHolder and construct it through each entry."
  '(fn [factory-name]
     (let [owner (resolve 'ControlledHolder)
           field (.getField owner "value")
           factory (resolve factory-name)
           direct (eval '(fn [value] (ControlledHolder. value)))
           constructor (.getConstructor owner (into-array Class [Object]))
           reflective (fn [value]
                        (try
                          (.newInstance constructor (object-array [value]))
                          (catch java.lang.reflect.InvocationTargetException e
                            (throw (.getCause e)))))
           outcome (fn [construct value]
                     (try
                       (construct value)
                       :accepted
                       (catch Throwable e (.getName (class e)))))]
       {:value? (not (.isIdentity owner))
        :eligible? (not (.isIdentity Long))
        :type (.getName (.getType field))
        :final? (java.lang.reflect.Modifier/isFinal (.getModifiers field))
        :annotations (mapv #(.getName (.annotationType %))
                           (.getDeclaredAnnotations field))
        :construction
        (mapv (fn [construct]
                [(.get field (construct (Long/valueOf 7)))
                 (outcome construct nil)
                 (outcome construct "wrong")])
              [factory direct reflective])})))

(def ^:private expected
  {:value? true :eligible? true :type "java.lang.Long"
   :final? true :annotations []
   :construction (vec (repeat 3 [7 "java.lang.NullPointerException"
                                 "java.lang.ClassCastException"]))})

(defn- construction-check
  "Return a child program that evaluates each definition form in order and
   checks ControlledHolder after it. The program prints one result per form.

   With :source, each result also has :source-absent?, read before the
   definition form."
  [definitions factory-name & {:keys [source]}]
  (list 'do
        '(require 'value-type.core 'value-type.impl.runtime 'clojure.java.io)
        (list 'with-redefs controlled-absence
              (list 'let ['check holder-check]
                    (list 'prn (mapv (fn [definition]
                                       (let [check (list 'check (list 'quote factory-name))]
                                         (if source
                                           (list 'let ['absent? (list 'nil? (list 'clojure.java.io/resource source))]
                                                 definition
                                                 (list 'assoc check :source-absent? 'absent?))
                                           (list 'do definition check))))
                                     definitions))))))

(defn- run-check
  "Run program in jvm with execution options and policy; assert its results."
  [jvm execution policy program results]
  (let [{:keys [exit result err]}
        (runner/run-jvm
         (assoc (update jvm :jvm-opts into execution)
                :policy policy
                :form (binding [*print-meta* true] (pr-str program))))]
    (is (= 0 exit) err)
    (is (= results result))
    (is (not (.contains ^String err "identity deftype fallback")) err)))

(deftest controlled-native-absence-construction-test
  ;; Only the native capability accessor is replaced; every constructor is real.
  (let [{:keys [valhalla]} (config/configuration)
        definitions (mapv (fn [expand]
                            (list 'eval (list expand (list 'quote holder-definition))))
                          ['macroexpand-1 'macroexpand])]
    (doseq [policy ["silent" "warn" "error"]
            execution [[] ["-Xint"]]]
      (testing (pr-str [policy execution])
        (run-check valhalla execution policy
                   (construction-check definitions '->ControlledHolder)
                   (vec (repeat (count definitions) expected)))))))

(def ^:private controlled-expansions
  (list 'do
        '(require 'value-type.core 'value-type.impl.runtime)
        (list 'with-redefs controlled-absence
              (list 'prn
                    (list 'mapv
                          (list 'fn '[expand]
                                (list 'binding '[*print-meta* true]
                                      (list 'pr-str
                                            (list 'expand (list 'quote holder-definition)))))
                          '[macroexpand-1 macroexpand])))))

(defn- receiving-definition
  "Return a child form that defines ControlledHolder from a serialized expansion."
  [analysis expansion]
  (case analysis
    :eval (list 'eval (list 'read-string expansion))
    :function (list (list 'eval (list 'list ''fn [] (list 'read-string expansion))))
    :source (list 'load-string expansion)))

(deftest controlled-native-absence-receiving-test
  ;; Serialized expansions cross a process boundary without producer compiler state.
  (let [{:keys [valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :policy "error"
                         :form (binding [*print-meta* true]
                                 (pr-str controlled-expansions))))]
    (is (= 0 (:exit producer)) (:err producer))
    (is (= 2 (count (:result producer))) producer)
    (is (not (.contains ^String (:err producer) "identity deftype fallback")))
    (when (= 0 (:exit producer))
      (doseq [policy ["silent" "warn" "error"]
              execution [[] ["-Xint"]]
              analysis [:eval :function :source]
              expansion (:result producer)]
        (testing (pr-str [policy execution analysis])
          (run-check valhalla execution policy
                     (construction-check [(receiving-definition analysis expansion)]
                                         '->ControlledHolder)
                     [expected]))))))

(def ^:private controlled-aot-source
  "(ns checked.controlled (:require [value-type.core :refer [defvaluetype]]))
   (defvaluetype ControlledHolder [^Long ^:non-nil value])")

(defn- compile-controlled
  "AOT compile checked.controlled with native capability absent; delete its
   source. Return the compile result on success, else nil."
  [valhalla paths]
  (fixture/compile-producer
   (assoc valhalla :policy "error") paths 'checked.controlled
   :wrap #(list 'do
                '(require 'value-type.impl.runtime)
                (list 'with-redefs controlled-absence %))))

(def ^:private saved-definition
  '(do
     (require 'checked.controlled)
     (eval '(import checked.controlled.ControlledHolder))))

(deftest controlled-native-absence-aot-test
  ;; Saved generated enforcement must load without source or native capability.
  (fixture/with-fixture
    'checked.controlled controlled-aot-source
    (fn [{:keys [output] :as paths}]
      (let [{:keys [valhalla]} (config/configuration)
            consumer (assoc valhalla :classpath (fixture/classpath-with valhalla output))]
        (when (compile-controlled valhalla paths)
          (doseq [policy ["silent" "warn" "error"]
                  execution [[] ["-Xint"]]]
            (testing (pr-str [policy execution])
              (run-check consumer execution policy
                         (construction-check [saved-definition] 'checked.controlled/->ControlledHolder
                                             :source "checked/controlled.clj")
                         [(assoc expected :source-absent? true)]))))))))

(deftest controlled-native-absence-java-aot-test
  ;; A separately compiled Java caller uses saved constructors, not Clojure factories.
  (fixture/with-fixture
    'checked.controlled controlled-aot-source
    (fn [{:keys [output] :as paths}]
      (let [{:keys [valhalla]} (config/configuration)
            classpath (fixture/classpath-with valhalla output)]
        (when (compile-controlled valhalla paths)
          (let [compiler (fixture/compile-java
                          valhalla classpath output
                          "test/resources/non-nil-fields/ControlledJavaConsumer.java")]
            (is (= 0 (:exit compiler)) compiler)
            (is (= 0 (:result compiler)) compiler)
            (when (= 0 (:result compiler))
              (doseq [policy ["silent" "warn" "error"]
                      execution [[] ["-Xint"]]]
                (testing (pr-str [policy execution])
                  (let [{:keys [exit result err]}
                        (runner/run-jvm
                         (assoc (update valhalla :jvm-opts into execution)
                                :policy policy :classpath classpath
                                :form (pr-str (consumer-checks/java-consumer-form
                                               {:source "checked/controlled.clj"
                                                :owner "checked.controlled.ControlledHolder"
                                                :checked ["value"]}
                                               "fields.ControlledJavaConsumer"
                                               :requires ['value-type.impl.runtime]
                                               :wrap #(list 'with-redefs controlled-absence %)))))]
                    (is (= 0 exit) err)
                    (is (= {:source-absent? true :value? true :annotations [] :checks :passed}
                           result))
                    (is (= "" err))))))))))))
