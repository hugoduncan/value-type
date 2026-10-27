(ns value-type.field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(deftest capability-matrix-test
  ;; Require remains lazy; explicit child configurations determine expectations.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration)])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc configuration :form
                      (str "(require 'value-type.core 'value-type.impl.runtime) "
                           "(let [cache @#'value-type.impl.runtime/detected-field-capability "
                           "      lazy? (not (realized? cache)) "
                           "      result (value-type.impl.runtime/field-capability)] "
                           "  (prn {:lazy? lazy? :capability (dissoc result :detail) "
                           "        :cached? (identical? result (value-type.impl.runtime/field-capability))}))")))]
          (is (= 0 exit) err)
          (is (= {:lazy? true :cached? true
                  :capability (if reason {:available? false :reason reason}
                                  {:available? true})}
                 result)))))))

(deftest malformed-probe-test
  ;; Real malformed classes and an unrestricted holder must remain errors.
  (let [{:keys [valhalla]} (config/configuration)
        {:keys [exit result err]}
        (runner/run-jvm
         (assoc valhalla :form
                (str
                 "(require 'value-type.impl.field-capability) "
                 "(let [emit @#'value-type.impl.field-capability/probe-bytes "
                 "      verify @#'value-type.impl.field-capability/verify-enforcement "
                 "      major (java.lang.classfile.ClassFile/latestMajorVersion) "
                 "      loader (clojure.lang.DynamicClassLoader.) "
                 "      define (fn [name descriptor restricted?] "
                 "               (.defineClass loader name (emit major name descriptor restricted?) nil)) "
                 "      outcome (fn [f] (try (f) :unexpected-success "
                 "                         (catch Throwable e (symbol (.getName (class e)))))) "
                 "      leaf (define \"FieldProbeLeaf\" \"J\" false) "
                 "      holder (define \"FieldProbeNullable\" \"LFieldProbeLeaf;\" false)] "
                 "  (prn [(outcome #(verify leaf holder)) "
                 "        (outcome #(define \"FieldProbePrimitive\" \"J\" true)) "
                 "        (outcome #(let [c (define \"FieldProbeBadSlots\" \"I\" false)] "
                 "                    (.getConstructors c))) "
                 "        (outcome #(let [c (define \"FieldProbeIdentity\" \"Ljava/lang/String;\" true)] "
                 "                    (.getConstructors c))) ]))")))]
    (is (= 0 exit) err)
    (is (= '[java.lang.IllegalStateException
             java.lang.ClassFormatError
             java.lang.VerifyError
             java.lang.IncompatibleClassChangeError]
           result))))

(deftest required-capability-policy-matrix-test
  ;; Native requirements never warn, fall back, or become successful on retry.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]
            policy ["silent" "warn" "error"]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (-> configuration
                   (update :jvm-opts conj (str "-Dvalue-type.fallback=" policy))
                   (assoc :form
                          (str
                           "(require 'value-type.impl.runtime) "
                           "(let [attempt (fn [] "
                           "  (try (value-type.impl.runtime/require-field-capability! \"RequiredHolder\") "
                           "       :supported "
                           "       (catch clojure.lang.ExceptionInfo e (ex-data e))))] "
                           "  (prn [(attempt) (attempt)]))"))))
              expected (if reason
                         {:value-type/error :field-capability-unavailable
                          :class-name "RequiredHolder"
                          :reason reason}
                         :supported)]
          (is (= 0 exit) err)
          (is (= [expected expected] result))
          (is (not (.contains ^String err "identity deftype fallback")) err))))))

(deftest public-field-policy-test
  ;; Public selection rejects unavailable native support before ordinary policy.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]
            policy ["silent" "warn" "error"]]
      (let [{:keys [exit result err]}
            (runner/run-jvm
             (-> configuration
                 (update :jvm-opts conj (str "-Dvalue-type.fallback=" policy))
                 (assoc :form
                        (str
                         "(require 'value-type.core) "
                         "(let [form '(value-type.core/defvaluetype RequiredPublic "
                         "             [^Long ^:nil-restricted value]) "
                         "      attempt (fn [] (try (macroexpand-1 form) :expanded "
                         "        (catch Throwable e (loop [cause e] "
                         "          (if-let [nested (ex-cause cause)] (recur nested) "
                         "            (ex-data cause))))))] "
                         "  (prn [(attempt) (attempt)]))"))))
            expected (if reason
                       {:value-type/error :field-capability-unavailable
                        :class-name "user.RequiredPublic" :reason reason}
                       :expanded)]
        (is (= 0 exit) err)
        (is (= [expected expected] result))
        (is (not (.contains ^String err "identity deftype fallback")) err)))))

(deftest public-field-evaluation-policy-test
  ;; Native construction must not depend on the ordinary fallback policy.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]
            policy ["silent" "warn" "error"]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc configuration :policy policy :form
                      (str
                       "(require 'value-type.core) "
                       "(let [attempt (fn [] (try "
                       "  (eval '(value-type.core/defvaluetype EvaluatedField "
                       "           [^Long ^:nil-restricted value])) "
                       "  (let [factory (resolve '->EvaluatedField) "
                       "        instance (factory (Long/valueOf 7)) "
                       "        field (.getField (class instance) \"value\") "
                       "        rejects (fn [value] (try (factory value) :accepted "
                       "                  (catch Throwable e (.getName (class e)))))] "
                       "    [(.getName (.getType field)) (.get field instance) "
                       "     (rejects nil) (rejects \"wrong\")]) "
                       "  (catch Throwable e (loop [cause e] "
                       "    (if-let [nested (ex-cause cause)] (recur nested) "
                       "      (ex-data cause))))))] "
                       "  (prn [(attempt) (attempt)]))")))
              expected (if reason
                         {:value-type/error :field-capability-unavailable
                          :class-name "user.EvaluatedField" :reason reason}
                         ["java.lang.Long" 7 "java.lang.NullPointerException"
                          "java.lang.ClassCastException"])]
          (is (= 0 exit) err)
          (is (= [expected expected] result))
          (is (= "" err) err))))))
