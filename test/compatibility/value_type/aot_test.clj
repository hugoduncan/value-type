(ns value-type.aot-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.aot-fixture :as fixture]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner])
  (:import (java.nio.file Files)))

(deftest fallback-aot-compilation-test
  ;; Real incapable compilers emit identity classes, or reject before type output.
  (let [{:keys [selected]} (config/configuration)]
    (doseq [policy ["silent" "warn" "error"]]
      (testing policy
        (fixture/with-fixture
          'fallback.aot-fixture fixture/top-level-source
          (fn [{:keys [source output]}]
            ;; Deliberately omit preview, including when the host enables it.
            (let [result (runner/run-jvm
                          (assoc selected :jvm-opts [] :policy policy
                                 :classpath (str (:classpath selected)
                                                 java.io.File/pathSeparator source)
                                 :form (str "(require '[value-type.aot-checks :as c]) "
                                            "(prn (c/compile-fixture "
                                            (pr-str (.getPath output)) " "
                                            (= "error" policy) "))")))]
              (is (= 0 (:exit result)) result)
              (is (= (if (= "error" policy)
                       (vec (repeat 2 {:chain ["clojure.lang.Compiler$CompilerException"
                                               "java.lang.Error"]
                                       :emitted? false :mapped? false :loadable? false}))
                       {:emitted? true :identity? true :field 42 :factory? true})
                     (:result result)) result)
              (if (= "warn" policy)
                (do
                  (is (= 1 (count (str/split-lines (:err result)))) result)
                  (is (str/starts-with? (:err result)
                                        "value-type: identity deftype fallback: ") result))
                (is (= "" (:err result)) result)))))))))

(deftest source-free-fallback-aot-loading-test
  ;; Compiled identity output obeys receiving policy, even on a capable JVM.
  (let [{:keys [baseline selected baseline?]} (config/configuration)
        classpath (:classpath selected)]
    (doseq [producer-policy ["silent" "warn"]]
      (fixture/with-fixture
        'fallback.aot-fixture fixture/top-level-source
        (fn [{:keys [source output fixture]}]
          (let [producer (runner/run-jvm
                          (assoc baseline :policy producer-policy
                                 :classpath (str classpath java.io.File/pathSeparator source)
                                 :form (str "(require '[value-type.aot-checks :as c]) "
                                            "(prn (c/compile-fixture "
                                            (pr-str (.getPath output)) " false))")))]
            (is (= 0 (:exit producer)) producer)
            (when (= 0 (:exit producer))
              ;; Delete source as well as excluding it from the consumer classpath.
              (Files/delete (.toPath fixture))
              (doseq [policy ["silent" "warn" "error"]]
                (testing (str producer-policy " artifact / " policy " consumer")
                  (let [consumer (runner/run-jvm
                                  (assoc selected :policy policy
                                         :classpath (str classpath java.io.File/pathSeparator output)
                                         :form (str "(require '[value-type.aot-checks :as c]) "
                                                    "(prn (c/load-fixture " (= "error" policy) "))")))]
                    (is (= 0 (:exit consumer)) consumer)
                    (if (= "error" policy)
                      (let [[initial retry] (:result consumer)]
                        (is (= {:error? true :chain ["java.lang.Error"]} initial) consumer)
                        (is (= {:error? true
                                :chain (if baseline?
                                         ["java.lang.NoClassDefFoundError"]
                                         ["java.lang.NoClassDefFoundError"
                                          "java.lang.ExceptionInInitializerError"])}
                               retry) consumer))
                      (is (= {:field 42 :identity? true :factory? true}
                             (:result consumer)) consumer))
                    (is (= (if (= "warn" policy)
                             "value-type: identity deftype fallback: fixed fallback representation\n"
                             "")
                           (:err consumer)) consumer)))))))))))

(deftest source-free-value-aot-loading-test
  ;; A value artifact remains a value artifact; fallback cannot repair its format.
  (let [{:keys [valhalla selected available?]} (config/configuration)
        classpath (:classpath selected)]
    (fixture/with-fixture
      'fallback.aot-fixture fixture/top-level-source
      (fn [{:keys [source output fixture]}]
        (let [producer (runner/run-jvm
                        (assoc valhalla :policy "error"
                               :classpath (str classpath java.io.File/pathSeparator source)
                               :form (str "(require '[value-type.aot-checks :as c]) "
                                          "(prn (c/compile-fixture "
                                          (pr-str (.getPath output)) " false))")))]
          (is (= 0 (:exit producer)) producer)
          (is (= {:emitted? true :identity? false :field 42 :factory? true}
                 (:result producer)) producer)
          (when (= 0 (:exit producer))
            (Files/delete (.toPath fixture))
            (doseq [policy ["silent" "warn" "error"]]
              (testing policy
                (let [consumer (runner/run-jvm
                                (assoc selected :policy policy
                                       :classpath (str classpath java.io.File/pathSeparator output)
                                       :form "(require '[value-type.aot-checks :as c]) (prn (c/load-value-fixture))"))]
                  (is (= 0 (:exit consumer)) consumer)
                  (if available?
                    (is (= {:value? true :field 42} (:result consumer)) consumer)
                    (is (some #{"java.lang.UnsupportedClassVersionError"}
                              (get-in consumer [:result :chain])) consumer))
                  (is (= "" (:err consumer)) consumer))))))))))

(deftest source-free-runtime-only-fallback-test
  ;; Loading succeeds before repeated calls enforce policy without compiler analysis.
  (let [{:keys [baseline selected]} (config/configuration)
        classpath (:classpath selected)]
    (fixture/with-fixture
      'fallback.runtime-fixture
      "(ns fallback.runtime-fixture (:require [value-type.core :refer [defvaluetype]]))\n(defn define-type [] (defvaluetype RuntimeIdentity [x]))\n"
      (fn [{:keys [source output fixture]}]
        (let [producer (runner/run-jvm
                        (assoc baseline :policy "silent"
                               :classpath (str classpath java.io.File/pathSeparator source)
                               :form (str "(binding [*compile-path* " (pr-str (.getPath output))
                                          "] (compile 'fallback.runtime-fixture)) (prn :compiled)")))]
          (is (= 0 (:exit producer)) producer)
          (is (= :compiled (:result producer)) producer)
          (when (= 0 (:exit producer))
            (Files/delete (.toPath fixture))
            (doseq [policy ["silent" "warn" "error"]]
              (testing policy
                (let [consumer (runner/run-jvm
                                (assoc selected :policy policy
                                       :classpath (str classpath java.io.File/pathSeparator output)
                                       :form "(require '[value-type.aot-checks :as c]) (prn (c/invoke-runtime-fixture))"))]
                  (is (= 0 (:exit consumer)) consumer)
                  (is (= {:loaded? true
                          :calls (vec (repeat 2 (if (= "error" policy)
                                                  {:chain ["java.lang.Error"]}
                                                  {:field 42 :identity? true :factory? true})))}
                         (:result consumer)) consumer)
                  (is (= (if (= "warn" policy)
                           "value-type: identity deftype fallback: fixed fallback representation\n"
                           "")
                         (:err consumer)) consumer))))))))))
