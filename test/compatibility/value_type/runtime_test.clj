(ns value-type.runtime-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(defn- run
  [policy form]
  (runner/run-jvm
   (assoc (:selected (config/configuration)) :policy policy :form form)))

(deftest capability-test
  ;; Use actual JVM APIs and class definition, not simulated capability results.
  (let [result (run nil
                    "(require '[value-type.impl.runtime :as r]) (let [a (r/capability)] (prn {:capability a :cached (identical? a (r/capability)) :optional-loaded (boolean (some find-ns '[value-type.impl.loader value-type.impl.transform]))}))")
        {:keys [available? unavailable-reason]} (config/configuration)
        capability (get-in result [:result :capability])]
    (is (= 0 (:exit result)) result)
    (is (= "" (:err result)))
    (is (true? (get-in result [:result :cached])))
    (is (false? (get-in result [:result :optional-loaded])))
    (if available?
      (is (= {:available? true} capability))
      (do (is (false? (:available? capability)))
          (is (= unavailable-reason
                 (:reason capability)))
          (is (not (str/blank? (:detail capability))))))))

(deftest policy-test
  ;; Independent processes own configuration; later property changes have no effect.
  (doseq [policy [nil "silent" "warn" "error"]]
    (testing (str "policy " policy)
      (let [result (run policy
                        "(require '[value-type.impl.runtime :as r]) (System/setProperty \"value-type.fallback\" \"invalid-later\") (prn (mapv (fn [_] (try (r/enforce-fallback! \"test\") :allowed (catch Error e [(.getName (class e)) (.getMessage e)]))) (range 2)))")]
        (is (= 0 (:exit result)) result)
        (if (= "error" policy)
          (is (= (vec (repeat 2 ["java.lang.Error" "value-type: identity deftype fallback: test"]))
                 (:result result)))
          (is (= [:allowed :allowed] (:result result))))
        (is (= (if (= "warn" policy)
                 "value-type: identity deftype fallback: test\n" "")
               (:err result)))))))

(deftest invalid-policy-test
  ;; Invalid startup values fail clearly rather than silently selecting a policy.
  (doseq [policy ["" "WARN" "unknown"]]
    (let [result (run policy
                      "(try (require 'value-type.impl.runtime) (prn :unexpected) (catch Throwable e (prn (loop [cause e] (if-let [next (.getCause cause)] (recur next) [(.getName (class cause)) (.getMessage cause)])))))")]
      (is (= 0 (:exit result)) result)
      (is (= ["java.lang.IllegalArgumentException"
              (str "Invalid value-type.fallback: " (pr-str policy) "; expected silent, warn or error")]
             (:result result))))))

(deftest public-require-test
  ;; Public loading must not load optional implementation APIs or warn.
  (doseq [policy ["silent" "warn" "error"]]
    (let [result (run policy
                      "(require 'value-type.core) (prn (boolean (some find-ns '[value-type.impl.loader value-type.impl.transform])))")]
      (is (= [0 false ""] ((juxt :exit :result :err) result)) result))))

(deftest public-definition-test
  ;; Real source compilation selects representation without weakening strict policy.
  (doseq [policy ["silent" "warn" "error"]]
    (let [result (run policy
                      "(require '[value-type.core :as v] '[value-type.impl.runtime :as r]) (let [available? (:available? (r/capability))] (prn {:available? available? :outcome (try (eval '(v/defvaluetype Sample [x])) (eval '(let [a (->Sample 42) b (->Sample 42)] [(= Sample (class a)) (.-x a) (= a b)])) (catch Throwable e (loop [cause e chain []] (let [chain (conj chain (.getName (class cause)))] (if-let [next (.getCause cause)] (recur next chain) {:chain chain :absent (nil? (resolve 'Sample))})))))}))")
          {:keys [available?]} (config/configuration)]
      (is (= 0 (:exit result)) result)
      (is (= available? (get-in result [:result :available?])) result)
      (is (= (if (and (not available?) (= "error" policy))
               {:chain ["clojure.lang.Compiler$CompilerException" "java.lang.Error"]
                :absent true}
               [true 42 available?])
             (get-in result [:result :outcome])) result)
      (if (and (not available?) (= "warn" policy))
        (is (= 1 (count (re-seq #"value-type: identity deftype fallback:" (:err result)))) result)
        (is (= "" (:err result)) result)))))

(deftest fallback-source-compatibility-test
  ;; Compare real identity types; capable JVMs must not be forced into fallback.
  (let [result (run "silent"
                    "(require '[value-type.impl.runtime :as r]) (prn (if (:available? (r/capability)) :value-mode (do (require 'value-type.source-checks) ((resolve 'value-type.source-checks/compatibility)))))")
        observed (:result result)
        {:keys [available?]} (config/configuration)
        expected {:factory true :fields ["long" "java.lang.Object"]
                  :protocol 42 :interface "sample" :method "sample:42"
                  :same true :distinct true :unequal true :hash true
                  :clojure-hash true :lookup :found :other-lookup :absent}]
    (is (= 0 (:exit result)) result)
    (is (= "" (:err result)) result)
    (if available?
      (is (= :value-mode observed) result)
      (is (= {:ordinary expected :portable expected
              :hinted-field 42 :constructor true :import true}
             observed) result))))

(deftest source-field-validation-test
  ;; Mutable fields fail in either representation; false metadata remains legal.
  (let [result (run "silent"
                    "(require '[value-type.validation-checks :as checks]) (prn {:fields (checks/check-fields) :invalid (checks/invalid-forms)})")]
    (is (= 0 (:exit result)) result)
    (is (= "" (:err result)) result)
    (is (= [{:key :volatile-mutable :enabled? true :error :mutable-field :absent true}
            {:key :volatile-mutable :enabled? false :final? true}
            {:key :unsynchronized-mutable :enabled? true :error :mutable-field :absent true}
            {:key :unsynchronized-mutable :enabled? false :final? true}]
           (get-in result [:result :fields])) result)
    (is (= ["java.lang.AssertionError" "java.lang.AssertionError"]
           (get-in result [:result :invalid])) result)))

(deftest fallback-expansion-policy-test
  ;; Discarded expansions consume warning state; caught strict errors grant no permission.
  (doseq [policy ["silent" "warn" "error"]]
    (testing policy
      (let [result (run policy
                        "(require '[value-type.expansion-checks :as checks]) (prn (checks/check-expansions))")
            observed (:result result)
            {:keys [available?]} (config/configuration)
            strict? (= "error" policy)
            rejected {:chain ["clojure.lang.Compiler$CompilerException" "java.lang.Error"]
                      :absent? true}]
        (is (= 0 (:exit result)) result)
        (if available?
          (do
            (is (= :value-mode observed) result)
            (is (= "" (:err result)) result))
          (do
            (doseq [mode [:bound :unbound]]
              (is (= (vec (repeat 2 (vec (repeat 2 (if strict? rejected
                                                       {:returned? true :absent? true})))))
                     (get observed mode)) result))
            (doseq [phase [:evaluated :source]]
              (is (= (vec (repeat 2 (if strict? rejected
                                        {:returned? true :absent? false})))
                     (get observed phase)) result))
            (is (= (if (= "warn" policy) 1 0)
                   (count (re-seq #"value-type: identity deftype fallback:" (:err result))))
                result)
            (when-not (= "warn" policy)
              (is (= "" (:err result)) result))))))))

(deftest fallback-expansion-warning-timing-test
  ;; Fresh warning state prevents earlier expansions from masking delayed warnings.
  (doseq [entry-point [:full :one]
          bound? [false true]]
    (testing (str entry-point " loader-bound=" bound?)
      (let [result (run "warn"
                        (str "(require '[value-type.expansion-checks :as checks]) "
                             "(prn (checks/check-expansions " entry-point " " bound? "))"))
            observed (:result result)
            {:keys [available?]} (config/configuration)]
        (is (= 0 (:exit result)) result)
        (is (= "" (:err result)) result)
        (if available?
          (is (= :value-mode observed) result)
          (do
            (is (= {:returned? true :absent? true} (:expanded observed)) result)
            (is (= 1 (count (re-seq #"value-type: identity deftype fallback:"
                                    (or (:after-expansion observed) "")))) result)
            (is (= {:returned? true :absent? false} (:evaluated observed)) result)
            (is (= (:after-expansion observed) (:after-evaluation observed)) result)))))))
