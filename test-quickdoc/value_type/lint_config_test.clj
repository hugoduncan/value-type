(ns value-type.lint-config-test
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.test :refer [deftest is testing]]))

(defn- lint [body & [namespace-name]]
  (let [{:keys [exit out err]}
        (shell/sh "mise" "exec" "--" "clj-kondo"
                  "--repro" "--config-dir" ".clj-kondo"
                  "--cache" "false" "--fail-level" "warning"
                  "--config" "{:output {:format :edn}}"
                  "--filename" (if namespace-name
                                 "value_type/deftype_compat_test.clj"
                                 "lint_fixture.clj") "--lint" "-"
                  :in (str "(ns " (or namespace-name "lint-fixture")
                           " (:require [value-type.core :as vt]))\n"
                           body))]
    {:exit exit :result (edn/read-string out) :err err}))

(deftest deftype-compatible-analysis
  ;; Exercise the real linter, without loading preview-only classes.
  (testing "fields, method arguments and the generated constructor are known"
    (let [result (lint (str "(vt/defvaluetype Point [x] Object "
                            "(equals [_ other] (= x other)))\n"
                            "(def point (->Point 1))"))]
      (is (zero? (:exit result)) (pr-str result))
      (is (empty? (get-in result [:result :findings])))))
  (testing "method bodies retain unresolved-symbol checks"
    (let [result (lint (str "(vt/defvaluetype Point [x] Object "
                            "(equals [_ other] (= missing x other)))"))]
      (is (pos? (:exit result)) (pr-str result))
      (is (= [:unresolved-symbol]
             (mapv :type (get-in result [:result :findings])))))))

(deftest temporary-directory-analysis
  ;; Check lexical scope and body diagnostics with the real linter.
  (testing "the directory name is bound within the body"
    (let [result (lint (str "(vt/defvaluetype Point [])\n"
                            "(require '[value-type.test-support :refer [with-temp-dir]])\n"
                            "(with-temp-dir [dir] (str dir))"))]
      (is (zero? (:exit result)) (pr-str result))
      (is (empty? (get-in result [:result :findings])))))
  (testing "unresolved body symbols and escaped bindings still fail"
    (doseq [body ["(with-temp-dir [dir] (str dir missing))"
                  "(with-temp-dir [dir] (str dir)) (str dir)"]]
      (let [result (lint (str "(vt/defvaluetype Point [])\n"
                              "(require '[value-type.test-support :refer [with-temp-dir]])\n"
                              body))]
        (is (pos? (:exit result)) (pr-str result))
        (is (= [:unresolved-symbol]
               (mapv :type (get-in result [:result :findings]))))))))

(deftest benchmark-type-pair-analysis
  ;; Analyze actual benchmark macro syntax without running the benchmark.
  (testing "both types expose fields, methods and two-argument constructors"
    (let [result (lint (str "(vt/defvaluetype Point [])\n"
                            "(require '[value-type.bench :refer [defpair]])\n"
                            "(defprotocol Norm (norm [this]))\n"
                            "(defpair Dt Vt Norm (norm [_] (+ x y)))\n"
                            "(def pair [(->Dt 1 2) (->Vt 3 4)])"))]
      (is (zero? (:exit result)) (pr-str result))
      (is (empty? (get-in result [:result :findings])))))
  (testing "method bodies and constructor arity remain checked"
    (doseq [[body finding]
            [["(defpair Dt Vt Object (toString [_] (str missing x y)))"
              :unresolved-symbol]
             ["(defpair Dt Vt) (->Dt 1)" :invalid-arity]
             ["(defpair Dt Vt) (->Vt 1)" :invalid-arity]]]
      (let [result (lint (str "(vt/defvaluetype Point [])\n"
                              "(require '[value-type.bench :refer [defpair]])\n"
                              body))]
        (is (pos? (:exit result)) (pr-str result))
        (is (= #{finding}
               (into #{} (map :type) (get-in result [:result :findings])))
            (pr-str result))))))

(deftest benchmark-pass-analysis
  ;; Keep generated function arities and expression diagnostics visible.
  (let [prefix (str "(vt/defvaluetype Point [])\n"
                    "(require '[value-type.bench :refer [defpass]])\n"
                    "(deftype Dt [x y]) (deftype Vt [x y])\n")]
    (testing "one-array and two-array passes define both functions"
      (doseq [body ["(defpass (.-x ^T e) fd Dt fv Vt) (fd nil) (fv nil)"
                    "(defpass (instance? T e) fd Dt fv Vt) (fd nil) (fv nil)"
                    "(defpass (.-x ^T (T. e e)) fd Dt fv Vt) (fd nil) (fv nil)"
                    "(defpass (if (identical? e e2) 1 0) fd Dt fv Vt) (fd nil nil) (fv nil nil)"]]
        (let [result (lint (str prefix body))]
          (is (zero? (:exit result)) (pr-str result))
          (is (empty? (get-in result [:result :findings]))))))
    (testing "unresolved expressions and wrong generated arities fail"
      (doseq [[body finding]
              [["(defpass (+ e missing) fd Dt fv Vt)" :unresolved-symbol]
               ["(defpass e fd Dt fv Vt) (fd nil nil)" :invalid-arity]
               ["(defpass (= e e2) fd Dt fv Vt) (fv nil)" :invalid-arity]]]
        (let [result (lint (str prefix body))]
          (is (pos? (:exit result)) (pr-str result))
          (is (= #{finding}
                 (into #{} (map :type) (get-in result [:result :findings])))
              (pr-str result)))))))

(deftest custom-assertion-analysis
  ;; Only the corpus assertion dispatch symbols are exempt, not their bodies.
  (doseq [assertion ["fails-with-cause?" "thrown-with-cause-msg?"]]
    (let [prefix (str "(vt/defvaluetype Point [])\n"
                      "(ns value-type.deftype-compat-test "
                      "(:require [clojure.test :refer [is]]))\n")
          form (str "(" assertion " Exception #\"expected\" ")]
      (testing "custom assertion arguments are analyzed"
        (let [result (lint (str prefix "(is " form "(str 1)))")
                           "value-type.deftype-compat-test")]
          (is (zero? (:exit result)) (pr-str result))
          (is (empty? (get-in result [:result :findings]))))
        (let [result (lint (str prefix "(is " form "missing))")
                           "value-type.deftype-compat-test")]
          (is (pos? (:exit result)) (pr-str result))
          (is (= [:unresolved-symbol]
                 (mapv :type (get-in result [:result :findings]))))))
      (testing "the symbol is not exempt outside is"
        (let [result (lint (str prefix "(is true) " form "(str 1))")
                           "value-type.deftype-compat-test")]
          (is (pos? (:exit result)) (pr-str result))
          (is (= [:unresolved-symbol]
                 (mapv :type (get-in result [:result :findings])))))))))
