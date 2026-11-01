(ns value-type.aot-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner])
  (:import (java.nio.file Files)))

(defn- consumer
  "Return the consumer child (aot-fixture/consumer-child; rule 5 for the
   strict retry in the same cell) that evaluates call, an aot-checks call
   form, with its namespace loaded (registry/check-form), in a cell with
   context. fixture-ns is the namespace of the artifact's AOT compile child."
  [fixture-ns jvm classpath policy context call expected & {:keys [fallback?]}]
  (fixture/consumer-child fixture-ns jvm classpath policy (apply registry/check-form call) expected
                          :context [context] :fallback? fallback? :warning harness/fallback-context))

(defn- producer-child
  "Return the AOT producer child of runtime jvm (with :policy) that
   compiles the fixture with compile-fixture into output."
  [jvm source output]
  (assoc jvm :kind :aot-compile
         :classpath (fixture/classpath-with jvm source)
         :form (pr-str (registry/check-form 'value-type.aot-checks/compile-fixture
                                            (.getPath output) false))))

(defn- produced?
  "Assert under the support label \"producer\" that a run-jvm result exits
   0; return true on exit 0."
  [result]
  (testing "producer"
    (harness/assert-run result {:exit 0})))

(defn- produce
  "Run the producer-child (same arguments) with support assertions; return
   the run-jvm result on exit 0, else nil."
  [jvm source output]
  (let [result (runner/run-jvm (producer-child jvm source output))]
    (when (produced? result)
      result)))

(defn- with-fixtures
  "Call f with a vector of n with-fixture path maps of fixture-ns."
  [n fixture-ns source-text f]
  (if (zero? n)
    (f [])
    (fixture/with-fixture
      fixture-ns source-text
      (fn [paths]
        (with-fixtures (dec n) fixture-ns source-text #(f (into [paths] %)))))))

(deftest fallback-aot-compilation-test
  ;; Real incapable compilers emit identity classes, or reject before type output.
  ;; One compile child for each policy (rule 2); no artifact is loaded.
  ;; Each policy compiles into its own output.
  (let [{:keys [selected] :as configuration} (config/configuration)
        context (harness/rejection-context configuration)]
    (fixture/with-fixture
      'fallback.aot-fixture fixture/top-level-source
      (fn [{:keys [source root]}]
        (harness/run-cells
         (for [policy config/policies
               :let [output (io/file root (str "classes-" policy))
                     strict? (= "error" policy)]]
           (do
             (.mkdirs output)
             ;; Deliberately omit preview, including when the host enables it.
             (harness/cell-child
              (assoc selected :jvm-opts [] :policy policy
                     :classpath (fixture/classpath-with selected source)
                     :fixture-ns 'fallback.aot-fixture)
              ;; The warning context depends on the parent runtime.
              [{:context [policy]
                :form (registry/check-form 'value-type.aot-checks/compile-fixture
                                           (.getPath output) strict?)
                :expected (if strict?
                            (harness/described
                             (harness/entries
                              (registry/each-attempt
                               (harness/failed-definition context
                                                          {:emitted? (harness/fixed false)}))))
                            (merge {:emitted? true :identity? true :stored 42}
                                   registry/new-definition))
                :fallback? true
                :warning :any}]))))))))

(deftest source-free-fallback-aot-loading-test
  ;; Compiled identity output obeys receiving policy, even on a capable JVM.
  ;; Both producers run in one run-jvms call, then all consumers in one call.
  (let [{:keys [baseline selected baseline?]} (config/configuration)
        producer-policies ["silent" "warn"]]
    (with-fixtures
      (count producer-policies) 'fallback.aot-fixture fixture/top-level-source
      (fn [fixtures]
        (let [results (runner/run-jvms
                       (for [[producer-policy {:keys [source output]}]
                             (map vector producer-policies fixtures)]
                         (producer-child (assoc baseline :policy producer-policy) source output)))
              produced (doall
                        (for [[producer-policy paths result]
                              (map vector producer-policies fixtures results)
                              :when (produced? result)]
                          [producer-policy paths]))]
          (doseq [[_ {:keys [fixture]}] produced]
            ;; Delete source as well as excluding it from the consumer classpath.
            (Files/delete (.toPath fixture)))
          (harness/run-cells
           (for [[producer-policy {:keys [output]}] produced
                 policy config/policies]
             (consumer 'fallback.aot-fixture selected (fixture/classpath-with selected output) policy
                       (str producer-policy " artifact / " policy " consumer")
                       (list 'value-type.aot-checks/load-fixture (= "error" policy))
                       (if (= "error" policy)
                         [(assoc harness/fallback-rejection
                                 :error? true :chain harness/direct-rejection-chain)
                          (if baseline?
                            {:error? true :class "java.lang.NoClassDefFoundError"
                             :message "Could not initialize class fallback.aot_fixture__init"
                             :chain ["java.lang.NoClassDefFoundError"]}
                            {:error? true :class "java.lang.ExceptionInInitializerError"
                             :message (str "Exception java.lang.Error: "
                                           (:message harness/fallback-rejection)
                                           " [in thread \"main\"]")
                             :chain ["java.lang.NoClassDefFoundError"
                                     "java.lang.ExceptionInInitializerError"]})]
                         {:stored 42 :identity? true :binary-name? true})
                       :fallback? true))))))))

(deftest source-free-value-aot-loading-test
  ;; A value artifact remains a value artifact; fallback cannot repair its format.
  (let [{:keys [selected available?] :as configuration} (config/configuration)]
    (fixture/with-fixture
      'fallback.aot-fixture fixture/top-level-source
      (fn [{:keys [source output fixture]}]
        (when-let [producer (produce (config/value-producer configuration) source output)]
          (testing "producer"
            (harness/assert-run producer
                                {:result (merge {:emitted? true :identity? false :stored 42}
                                                registry/new-definition)}))
          (Files/delete (.toPath fixture))
          (harness/run-cells
           (for [policy config/policies]
             (consumer 'fallback.aot-fixture selected (fixture/classpath-with selected output)
                       policy policy
                       '(value-type.aot-checks/load-value-fixture)
                       (if available?
                         {:value? true :stored 42}
                         (harness/described
                          (harness/including
                           {:chain (harness/contains
                                    ["java.lang.UnsupportedClassVersionError"])})))))))))))

(deftest source-free-runtime-only-fallback-test
  ;; Loading succeeds before repeated calls enforce policy without compiler analysis.
  (let [{:keys [selected] :as configuration} (config/configuration)]
    (fixture/source-free-consumers
     'fallback.runtime-fixture
     "(ns fallback.runtime-fixture (:require [value-type.core :refer [defvaluetype]]))\n(defn define-type [] (defvaluetype RuntimeIdentity [x]))\n"
     (config/identity-producer configuration)
     [selected] config/policies
     (registry/check-form 'value-type.aot-checks/invoke-runtime-fixture)
     (fn [policy]
       {:loaded? true
        :calls (registry/each-attempt (if (= "error" policy)
                                        (assoc harness/fallback-rejection
                                               :chain harness/direct-rejection-chain)
                                        {:stored 42 :identity? true :factory-class? true}))})
     :context-of (fn [_ policy] [policy])
     :fallback? true :warning harness/fallback-context)))
