(ns value-type.function-redefinition-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private repeated-function-definition
  "Child program: under controlled native absence, evaluate each declaration
   twice through function analysis. Inspect each owner immediately after its
   definition: value?, stored value, field type, final modifier, native
   annotations and the nil and wrong-type outcomes of each construction entry
   (direct, factory, reflective)."
  '(do
     (require 'value-type.consumer-aot-checks 'value-type.core
              'value-type.impl.runtime)
     (with-redefs [value-type.impl.runtime/field-capability
                   (constantly {:available? false :reason :controlled-absence})]
       (let [define (fn [declaration]
                      ((eval (list 'fn [] declaration)))
                      (let [instance ((resolve '->RepeatedHolder) (Long/valueOf 7))
                            owner (class instance)
                            field (.getField owner "value")]
                        {:owner owner
                         :check
                         {:value? (value-type.consumer-aot-checks/value-class? owner)
                          :stored (.get field instance)
                          :type (.getName (.getType field))
                          :final? (java.lang.reflect.Modifier/isFinal
                                   (.getModifiers field))
                          :annotations (value-type.consumer-aot-checks/annotation-names
                                        field)
                          :rejections (mapv (fn [build]
                                              [(value-type.consumer-aot-checks/outcome #(build [nil]))
                                               (value-type.consumer-aot-checks/outcome #(build ["wrong"]))])
                                            (value-type.consumer-aot-checks/builders
                                             (str (ns-name *ns*)) owner))}}))]
         (prn
          (mapv (fn [declaration]
                  (let [definitions [(define declaration) (define declaration)]]
                    {:distinct-classes? (not (identical? (:owner (first definitions))
                                                         (:owner (second definitions))))
                     :checks (mapv :check definitions)}))
                ['(clojure.core/deftype RepeatedHolder [value])
                 '(value-type.core/defvaluetype RepeatedHolder [^Long ^:non-nil value])]))))))

(defn- expected
  "Return the expected result of one declaration defined twice."
  [check]
  {:distinct-classes? true :checks [check check]})

(deftest controlled-capability-repeated-definition-test
  ;; Controlled capability (acceptance 8): only the native capability accessor
  ;; is replaced. Same-name redefinition keeps value representation and
  ;; generated checks. Fields are read from the constructed Class, not a stale
  ;; namespace import.
  (let [{:keys [valhalla]} (config/configuration)]
    (doseq [policy ["silent" "warn" "error"]
            execution [[] ["-Xint"]]]
      (testing (pr-str [:controlled-capability policy execution])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc (update valhalla :jvm-opts into execution)
                      :policy policy
                      :form (binding [*print-meta* true]
                              (pr-str repeated-function-definition))))]
          (is (= 0 exit) err)
          (is (= [(expected {:value? false :stored 7 :type "java.lang.Object"
                             :final? true :annotations []
                             :rejections (vec (repeat 3 [:accepted :accepted]))})
                  (expected {:value? true :stored 7 :type "java.lang.Long"
                             :final? true :annotations []
                             :rejections (vec (repeat 3 ["java.lang.NullPointerException"
                                                         "java.lang.ClassCastException"]))})]
                 result))
          (is (= "" err)))))))
