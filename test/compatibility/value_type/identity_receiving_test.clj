(ns value-type.identity-receiving-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.core]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]
            [value-type.message-checks :refer [message-names?]])
  (:import (java.lang.reflect InvocationTargetException Modifier)))

(defn- run-check
  "Run the identity-receiving-checks function check in configuration with
   execution options and policy. Check exit; return :result and :err."
  [configuration execution-options policy check]
  (let [{:keys [exit] :as run}
        (runner/run-jvm
         (assoc (update configuration :jvm-opts into execution-options)
                :policy policy
                :form (pr-str (list 'do '(require 'value-type.identity-receiving-checks)
                                    (list 'prn (list (symbol "value-type.identity-receiving-checks"
                                                             (name check))))))))]
    (is (= 0 exit) (:err run))
    run))

(deftest receiving-identity-fields-test
  ;; Analyze transported data in a fresh namespace with a receiver-local import.
  (doseq [analysis [:eval :function :source]
          expansion [:internal :macroexpand :macroexpand-1]
          nested? [false true]]
    (testing (str "fixed generated identity receiving via " analysis " nested? " nested?)
      (let [receiver (create-ns (gensym "identity.receiver"))
            binary-name (str (ns-name receiver) ".Holder")]
        (try
          (binding [*ns* receiver]
            (clojure.core/refer 'clojure.core)
            (import 'java.util.Date)
            (eval '(defprotocol FieldReads (read-fields [this])))
            (let [child-class (when nested?
                                (eval '(value-type.core/defvaluetype Child [optional])))
                  required-class (or child-class java.util.Date)
                  fields [(with-meta 'required {:tag (if nested? 'Child 'Date)
                                                :non-nil true})
                          (with-meta 'label {:tag 'String :non-nil true})
                          'optional (with-meta 'count {:tag 'long})
                          (with-meta 'ratio {:tag 'double})]
                  form (list 'value-type.core/identity-field-deftype
                             binary-name [:generated :generated] 'Holder fields
                             'FieldReads
                             '(read-fields [_] [required label optional count ratio])
                             'java.util.concurrent.Callable
                             '(call [_] [required label optional count ratio])
                             'Object
                             '(toString [_] (pr-str [required label optional count ratio]))
                             'clojure.lang.IDeref
                             '(deref [_]
                                     (fn
                                       ([] [required label optional count ratio])
                                       ([selected?]
                                        (fn [] (if selected? required optional))))))
                  public-form (list* 'value-type.core/defvaluetype
                                     'Holder fields (drop 5 form))
                  selected (case expansion
                             :internal form
                             :macroexpand (macroexpand public-form)
                             :macroexpand-1 (macroexpand-1 public-form))
                  ^Class owner (case analysis
                                 :function ((eval (list 'fn [] selected)))
                                 :source (load-string
                                          (binding [*print-meta* true]
                                            (pr-str selected)))
                                 :eval (eval selected))
                  field (.getField owner "required")
                  ctor (.getConstructor owner (into-array Class [Object Object Object Long/TYPE Double/TYPE]))
                  input (if nested?
                          ((ns-resolve receiver '->Child) nil)
                          (java.util.Date. 0))
                  instance (.newInstance ctor (object-array [input "label" nil (long 7) (double 2.5)]))]
              (is (= [input "label" nil 7 2.5]
                     ((ns-resolve receiver 'read-fields) instance)))
              (is (= (pr-str [input "label" nil 7 2.5]) (.toString instance)))
              (is (identical? required-class (.getType field)))
              (when nested?
                (is (nil? (.get (.getField ^Class child-class "optional") input))))
              (is (Modifier/isFinal (.getModifiers field)))
              (is (identical? String (.getType (.getField owner "label"))))
              (is (Modifier/isFinal (.getModifiers (.getField owner "label"))))
              (let [read-direct (eval '(fn [^Holder holder]
                                         [(.-required holder) (.-label holder)
                                          (.-optional holder) (.-count holder)
                                          (.-ratio holder)]))]
                (is (= [input "label" nil 7 2.5] (read-direct instance))))
              (is (identical? input (.get field instance)))
              (is (nil? (.get (.getField owner "optional") instance)))
              (doseq [optional [nil "present"]]
                (let [holder (.newInstance ctor (object-array [input "label" optional (long 7) (double 2.5)]))
                      read-field (.deref ^clojure.lang.IDeref holder)]
                  (is (= [input "label" optional 7 2.5] (read-field)))
                  (is (= [input "label" optional 7 2.5]
                         (.call ^java.util.concurrent.Callable holder)))
                  (is (identical? input ((read-field true))))
                  (is (identical? optional ((read-field false))))))
              (doseq [[entry construct]
                      [[:direct (eval '(fn [required label optional count ratio]
                                         (new Holder required label optional count ratio)))]
                       [:factory (ns-resolve receiver '->Holder)]
                       [:reflective (fn [& arguments]
                                      (try
                                        (.newInstance ctor (object-array arguments))
                                        (catch InvocationTargetException error
                                          (throw (.getCause error)))))]]]
                (testing (name entry)
                  (is (= [input "label" nil 7 2.5]
                         ((.deref ^clojure.lang.IDeref
                           (construct input "label" nil (long 7) (double 2.5))))))
                  (doseq [position [0 1]
                          [invalid expected] [[nil NullPointerException]
                                              [42 ClassCastException]]]
                    (is (= expected
                           (try
                             (apply construct (assoc [input "label" nil (long 7) (double 2.5)]
                                                     position invalid))
                             nil
                             (catch Exception error
                               (class error))))))))))
          (finally (remove-ns (ns-name receiver))))))))

(deftest receiving-identity-policy-test
  ;; Fixed identity analysis must not upgrade on a capable receiving JVM.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [configuration [baseline (assoc valhalla :jvm-opts []) valhalla]
            policy ["silent" "warn" "error"]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy])
        (let [{:keys [result err]} (run-check configuration [] policy 'receiving-policy)
              expected (if (= "error" policy)
                         (assoc checks/fallback-rejection :data nil)
                         {:same-class? true :storage "java.lang.String"})]
          (is (= [expected expected] result))
          (is (= (if (= "warn" policy) 1 0)
                 (count (re-seq #"identity deftype fallback" err))) err))))))

(deftest incompatible-value-receiving-test
  ;; A fixed value expansion must reject baseline receivers, not select identity.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [configuration [baseline (assoc valhalla :jvm-opts [])]
            policy ["silent" "warn" "error"]]
      (testing (pr-str [(:java configuration) policy])
        (let [{:keys [result err]} (run-check configuration [] policy 'incompatible-value)
              {:keys [capability outcome]} result]
          (is (false? (:available? capability)) result)
          (is (= (checks/unavailable-failure
                  "value_type.identity_receiving_checks.Holder" capability)
                 (dissoc outcome :message)))
          (is (checks/unavailable-message?
               "value_type.identity_receiving_checks.Holder" outcome)
              outcome)
          (when (= baseline configuration)
            (is (message-names? (:message outcome)
                                ["no value classes" "Use a JEP 401 JDK"])
                outcome))
          (is (empty? err) err))))))

(deftest generated-value-receiving-policy-test
  ;; Identity-class field tags must not trigger identity representation policy.
  (let [{:keys [valhalla]} (config/configuration)]
    (doseq [policy ["silent" "warn" "error"]]
      (testing (str "generated value enforcement under " policy)
        (let [{:keys [result err]} (run-check valhalla [] policy 'generated-value)]
          (is (= {:value? true
                  :storage "java.lang.String"
                  :annotations []
                  :constructions (vec (repeat 3 {:stored "valid"
                                                 :rejections ["java.lang.NullPointerException"
                                                              "java.lang.ClassCastException"]}))}
                 result))
          (is (empty? err) err))))))

(deftest receiving-redefined-child-test
  ;; Reanalysis must use the replacement Class, not a cached same-name Class.
  (doseq [expansion [:internal :macroexpand :macroexpand-1]
          analysis [:eval :function :source]]
    (let [receiver (create-ns (gensym "identity.redefinition"))
          binary-name (str (ns-name receiver) ".Holder")]
      (try
        (binding [*ns* receiver]
          (clojure.core/refer 'clojure.core)
          (let [original (eval '(deftype Child []))
                old-child (.newInstance ^Class original)
                fields [(with-meta 'required {:tag 'Child :non-nil true})]
                declaration (list 'value-type.core/defvaluetype 'Holder fields)
                form (case expansion
                       :internal (list 'value-type.core/identity-field-deftype
                                       binary-name [:generated] 'Holder fields)
                       :macroexpand (macroexpand declaration)
                       :macroexpand-1 (macroexpand-1 declaration))
                analyze (fn []
                          (case analysis
                            :eval (eval form)
                            :function ((eval (list 'fn [] form)))
                            :source (load-string
                                     (binding [*print-meta* true]
                                       (pr-str form)))))
                first-owner (analyze)
                replacement (eval '(deftype Child []))
                new-child (.newInstance ^Class replacement)
                second-owner (analyze)]
            (is (not (identical? original replacement)))
            (is (not (identical? first-owner second-owner)))
            (doseq [[^Class owner expected valid stale]
                    [[first-owner original old-child new-child]
                     [second-owner replacement new-child old-child]]]
              (let [field (.getField owner "required")
                    ctor (.getConstructor owner (into-array Class [Object]))
                    instance (.newInstance ctor (object-array [valid]))]
                (is (identical? expected (.getType field)))
                (is (identical? valid (.get field instance)))
                (doseq [[invalid failure] [[nil NullPointerException]
                                           [stale ClassCastException]]]
                  (is (= failure
                         (try
                           (.newInstance ctor (object-array [invalid]))
                           nil
                           (catch InvocationTargetException error
                             (class (.getCause error)))))))))))
        (finally (remove-ns (ns-name receiver)))))))

(deftest strict-nested-public-declaration-test
  ;; A valid concrete child must not bypass strict representation policy.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [configuration [baseline (assoc valhalla :jvm-opts [])]
            execution-options [[] ["-Xint"]]]
      (testing (pr-str [(:java configuration) execution-options])
        (let [{:keys [result err]} (run-check configuration execution-options "error"
                                              'strict-nested)
              rejected {:outcome (assoc (checks/policy-rejection (:detail result)) :data nil)
                        :owner nil :factory nil}]
          (is (seq (:detail result)) result)
          (is (= [[rejected rejected] [rejected rejected]] (:declarations result)))
          (is (empty? err) err))))))

(deftest nested-public-warning-scope-test
  ;; Nested declarations share runtime warning state, not per-type warning state.
  (let [{:keys [baseline valhalla]} (config/configuration)
        child "value_type.identity_receiving_checks.Child"]
    (doseq [configuration [baseline (assoc valhalla :jvm-opts [])]
            execution-options [[] ["-Xint"]]
            policy ["silent" "warn"]]
      (testing (pr-str [(:java configuration) execution-options policy])
        (let [{:keys [result err]} (run-check configuration execution-options policy
                                              'nested-warning-scope)
              declaration {:type child :stored child :input? true :optional nil
                           :rejections (vec (repeat 3 ["java.lang.NullPointerException"
                                                       "java.lang.ClassCastException"]))}]
          (is (= [[declaration declaration] [declaration declaration]] result))
          (is (= (if (= "warn" policy) 1 0)
                 (count (re-seq #"identity deftype fallback" err))) err)
          (when (= "silent" policy)
            (is (empty? err) err)))))))

(deftest receiving-identity-native-modes-test
  ;; Identity output rejects native modes before tag resolution and validation.
  (let [receiver (create-ns (gensym "identity.native"))]
    (try
      (binding [*ns* receiver]
        (clojure.core/refer 'clojure.core)
        (eval '(deftype Child []))
        (doseq [tag '[String Child]]
          (testing (str tag)
            (let [form (list 'value-type.core/identity-field-deftype
                             (str (ns-name receiver) ".Holder") [:native] 'Holder
                             [(with-meta 'value {:tag tag :non-nil true})])
                  cause (try (macroexpand-1 form)
                             (catch clojure.lang.Compiler$CompilerException e
                               (.getCause e)))]
              (is (= {:value-type/error :invalid-field-enforcement
                      :class-name (str (ns-name receiver) ".Holder")
                      :modes [:native]}
                     (ex-data cause)))
              (is (message-names? cause [(str (ns-name receiver) ".Holder")
                                         "[:native]"
                                         "evaluate the defvaluetype form directly"])
                  (ex-message cause))))))
      (finally (remove-ns (ns-name receiver))))))
