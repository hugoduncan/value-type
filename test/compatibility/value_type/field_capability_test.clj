(ns value-type.field-capability-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
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
  ;; Policy must not weaken real linkage, verification or enforcement errors.
  (let [{:keys [valhalla]} (config/configuration)]
    (doseq [policy ["silent" "warn" "error"]
            execution [[] ["-Xint"]]]
      (testing (pr-str [policy execution])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc (update valhalla :jvm-opts into execution) :policy policy :form
                      (str
                       "(require 'value-type.impl.field-capability) "
                       "(let [emit value-type.impl.capability/probe-bytes "
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
                 result))
          (is (not (.contains ^String err "identity deftype fallback")) err))))))

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

(defn- policy-attempts
  "Run form in configuration with execution and policy. Check exit and the
   warning count; return the attempts and the strict rejection."
  [configuration execution policy reason form]
  (let [{:keys [exit result err]}
        (runner/run-jvm
         (assoc (update configuration :jvm-opts into execution) :policy policy
                :form (pr-str (list 'do '(require 'value-type.field-policy-checks) (list 'prn form)))))]
    (is (= 0 exit) err)
    (is (= (if (and reason (= "warn" policy)) 1 0)
           (count (re-seq #"identity deftype fallback" err))) err)
    (when reason (is (seq (:detail result)) result))
    {:attempts (:attempts result)
     :rejection (checks/policy-rejection (:detail result))}))

(deftest public-field-policy-test
  ;; Representation policy is independent of field enforcement.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]
            policy ["silent" "warn" "error"]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy])
        (let [{:keys [attempts rejection]}
              (policy-attempts configuration [] policy reason
                               '(value-type.field-policy-checks/expansion))
              expected (if (and reason (= "error" policy)) rejection :expanded)]
          (is (= [expected expected] attempts)))))))

(deftest public-field-evaluation-policy-test
  ;; Public constructors retain checks in both representations.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason]
            [[baseline :missing-classfile-api]
             [(assoc valhalla :jvm-opts []) :unsupported-class-version]
             [valhalla nil]]
            policy ["silent" "warn" "error"]
            execution [[] ["-Xint"]]
            [tag valid invalid expected-type]
            [['Long 7 "wrong" "java.lang.Long"]
             ['String "retained" 7 "java.lang.String"]]]
      (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy execution tag])
        (let [{:keys [attempts rejection]}
              (policy-attempts configuration execution policy reason
                               (list 'value-type.field-policy-checks/evaluation
                                     (list 'quote tag) valid invalid))
              expected (if (and reason (= "error" policy))
                         rejection
                         (vec (repeat 3 {:type expected-type :stored valid
                                         :rejections ["java.lang.NullPointerException"
                                                      "java.lang.ClassCastException"]})))]
          (is (= [expected expected] attempts)))))))
