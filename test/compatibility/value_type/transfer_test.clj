(ns value-type.transfer-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(defn- run
  [configuration policy form]
  (runner/run-jvm (assoc configuration :policy policy :form form)))

(deftest transferred-expansion-test
  ;; A real incapable producer grants no permission to independent consumers.
  (let [{:keys [baseline selected]} (config/configuration)
        producer (run baseline "silent"
                      "(require '[value-type.transfer-checks :as c]) (prn (c/produce))")]
    (is (= 0 (:exit producer)) producer)
    (is (= "" (:err producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [policy ["silent" "warn" "error"]]
        (testing policy
          (let [consumer (run selected policy
                              (str "(require '[value-type.transfer-checks :as c]) (prn (c/consume "
                                   (pr-str (:result producer)) "))"))
                expected (if (= "error" policy)
                           {:absent? true
                            :chain ["clojure.lang.Compiler$CompilerException" "java.lang.Error"]}
                           {:field 42 :identity? true :factory? true})]
            (is (= 0 (:exit consumer)) consumer)
            (doseq [mode [:eval :source]]
              (is (= (vec (repeat 2 (vec (repeat 2 expected))))
                     (get (:result consumer) mode)) consumer))
            (is (= (if (= "warn" policy)
                     "value-type: identity deftype fallback: fixed fallback representation\n"
                     "")
                   (:err consumer)) consumer)))))))

(deftest concurrent-warning-scope-test
  ;; Concurrent namespaces share one warning; a new runtime gets its own warning.
  (let [{:keys [baseline selected]} (config/configuration)
        producer (run baseline "silent"
                      "(require '[value-type.transfer-checks :as c]) (prn (c/produce))")]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime-index (range 2)]
        (testing (str "independent runtime " runtime-index)
          (let [consumer (run selected "warn"
                              (str "(require '[value-type.warning-checks :as c]) "
                                   "(prn (c/concurrent-definitions "
                                   (pr-str (first (:result producer))) "))"))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= (vec (repeat 8 (vec (repeat 2 {:field 42 :identity? true :factory? true}))))
                   (:result consumer)) consumer)
            (is (= "value-type: identity deftype fallback: fixed fallback representation\n"
                   (:err consumer)) consumer)))))))

(deftest constrained-concurrent-warning-scope-test
  ;; Concurrent fixed fields stay silent or share one warning per receiving runtime.
  (let [{:keys [baseline selected]} (config/configuration)
        producer (run baseline "silent"
                      (str "(require 'value-type.core) "
                           "(binding [*print-meta* true] "
                           "(prn (mapv (fn [expand] "
                           "(pr-str (expand '(value-type.core/defvaluetype "
                           "Transferred [^Long ^:non-nil x])))) "
                           "[macroexpand macroexpand-1])))"))]
    (is (= 0 (:exit producer)) producer)
    (is (= "" (:err producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [serialized (:result producer)
              execution-options [[] ["-Xint"]]
              runtime-index (range 2)
              policy ["silent" "warn"]]
        (testing (str policy " execution " execution-options " runtime " runtime-index)
          (let [consumer (run (update selected :jvm-opts into execution-options)
                              policy
                              (str "(require '[value-type.warning-checks :as c]) "
                                   "(prn (c/concurrent-definitions "
                                   (pr-str serialized) "))"))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= (vec (repeat 8 (vec (repeat 2 {:field 42 :identity? true :factory? true}))))
                   (:result consumer)) consumer)
            (is (= (if (= "warn" policy)
                     "value-type: identity deftype fallback: fixed fallback representation\n"
                     "")
                   (:err consumer)) consumer)))))))

(deftest constrained-concurrent-strict-test
  ;; Strict concurrent analysis repeatedly rejects without installing definitions.
  (let [{:keys [baseline selected]} (config/configuration)
        producer (run baseline "silent"
                      (str "(require 'value-type.core) "
                           "(binding [*print-meta* true] "
                           "(prn (mapv (fn [expand] "
                           "(pr-str (expand '(value-type.core/defvaluetype "
                           "Transferred [^Long ^:non-nil x])))) "
                           "[macroexpand macroexpand-1])))"))]
    (is (= 0 (:exit producer)) producer)
    (is (= "" (:err producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [serialized (:result producer)
              execution-options [[] ["-Xint"]]]
        (testing (str "strict concurrent receiving " execution-options)
          (let [consumer (run (update selected :jvm-opts into execution-options)
                              "error"
                              (str "(require '[value-type.warning-checks :as c]) "
                                   "(try (prn (c/concurrent-rejections "
                                   (pr-str serialized) ")) (finally (shutdown-agents)))"))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= (vec (repeat 8 (vec (repeat 2 {:outcome checks/fallback-rejection
                                                  :factory-absent? true
                                                  :owner-absent? true}))))
                   (:result consumer)) consumer)
            (is (= "" (:err consumer)) consumer)))))))
