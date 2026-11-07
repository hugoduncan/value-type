(ns value-type.fallback-policy-test
  "First policy read: a policy set after load applies, a change after the
   first read has no effect, and an invalid value throws at the first read."
  (:require [clojure.test :refer [deftest]]
            [value-type.cell-harness :as harness]
            [value-type.cell-registry :as registry]
            [value-type.jvm-config :as config]))

(def ^:private invalid-message
  (registry/invalid-policy-message "unknown"))

(defn- child
  "Return a one-cell child of runtime with launch policy, the check call
   and expected."
  [runtime policy context check expected]
  (harness/cell-child
   (assoc runtime :policy policy)
   [{:context [context]
     :form (registry/check-form check)
     :expected expected}]))

(def ^:private compiler-failure
  "Expected definition of an invalid-report check on a runtime without
   value capability: CompilerException with the first failure as its
   cause."
  {:class "clojure.lang.Compiler$CompilerException"
   :cause-identical? true})

(defn- receivers
  "Return the [runtime label definition] rows of the receiver runtimes.
   definition is the expected definition of an invalid-report check."
  []
  (let [{:keys [baseline preview-off valhalla]} (config/configuration)]
    [[baseline "java8" compiler-failure]
     [preview-off "preview-off" compiler-failure]
     [valhalla "preview-on" :value]]))

(defn- incompatible-receivers
  "Return the [runtime label context] rows of the receiver runtimes without
   value capability. context is the fallback context."
  []
  (let [{:keys [baseline preview-off]} (config/configuration)]
    [[baseline "java8" harness/unavailable-detail]
     [preview-off "preview-off" :any]]))

(deftest set-after-load-test
  ;; Acceptance 1: a policy set with System/setProperty after load applies.
  (harness/run-cells
   (for [[runtime label context] (incompatible-receivers)
         [policy check expected]
         [["error" 'value-type.fallback-policy-checks/set-after-load-error
           (harness/failed-definition context {})]
          ["warn" 'value-type.fallback-policy-checks/set-after-load-warn
           (harness/expected-warning context)]]]
     (child runtime nil (str label " set " policy) check
            (harness/described expected)))))

(deftest change-after-report-test
  ;; Acceptance 2: after report shows :silent, setting error has no effect.
  ;; The harness checks that the fallback writes nothing to *err* or stderr.
  (harness/run-cells
   (for [[runtime label] (incompatible-receivers)]
     (child runtime nil (str label " report then error")
            'value-type.fallback-policy-checks/report-then-set
            {:fallback-policy :silent :definition :identity}))))

(deftest invalid-report-test
  ;; Acceptance 4 and later reads: report throws at the first read; each
  ;; later read throws the identical exception; a property fix has no effect.
  (harness/run-cells
   (for [[runtime label definition] (receivers)]
     (child runtime "unknown" (str label " invalid report")
            'value-type.fallback-policy-checks/invalid-report
            {:thrown {:class "java.lang.IllegalArgumentException"
                      :message invalid-message
                      :chain ["java.lang.IllegalArgumentException"]}
             :second-identical? true
             :definition definition}))))

(deftest invalid-definition-test
  ;; Acceptance 4: on a runtime without value-class support, a definition
  ;; makes the first read and throws the wrapped policy exception.
  (harness/run-cells
   (for [[runtime label] (incompatible-receivers)]
     (child runtime "unknown" (str label " invalid definition")
            'value-type.fallback-policy-checks/invalid-definition
            {:class "java.lang.IllegalArgumentException"
             :message invalid-message
             :chain ["clojure.lang.Compiler$CompilerException"
                     "java.lang.IllegalArgumentException"]}))))
