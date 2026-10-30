(ns value-type.field-transfer-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(deftest unsupported-public-transfer-test
  ;; Neither native nor generated checks permit a value representation downgrade.
  (doseq [tag ['Long 'String]]
    (let [{:keys [baseline valhalla]} (config/configuration)
          producer (runner/run-jvm
                    (assoc valhalla :form
                           (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce '" tag "))")))]
      (is (= 0 (:exit producer)) producer)
      (when (= 0 (:exit producer))
        (doseq [runtime [baseline (assoc valhalla :jvm-opts [])]
                execution-options [[] ["-Xint"]]
                :let [configuration (update runtime :jvm-opts into execution-options)]
                policy ["silent" "warn" "error"]]
          (testing (pr-str [(:java configuration) (:jvm-opts configuration) policy])
            (let [consumer (runner/run-jvm
                            (assoc configuration :policy policy :form
                                   (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume "
                                        (pr-str (:result producer)) "))")))
                  expected (assoc (checks/unavailable-failure
                                   "user.TransferredField" (:capability (:result consumer)))
                                  :absent? true)]
              (is (= 0 (:exit consumer)) consumer)
              (is (= "" (:err consumer)) consumer)
              (doseq [mode [:eval :source :analysis]
                      :let [outcomes (get (:result consumer) mode)]]
                (is (= (vec (repeat 2 (vec (repeat 2 expected))))
                       (mapv #(mapv (fn [o] (dissoc o :message)) %) outcomes))
                    consumer)
                (is (every? #(checks/unavailable-message? "user.TransferredField" %)
                            (apply concat outcomes))
                    consumer)))))))))

(deftest supported-public-transfer-test
  ;; Receiving compilation installs fixed native or generated enforcement.
  (doseq [tag ['Long 'String]]
    (let [{:keys [valhalla]} (config/configuration)
          producer (runner/run-jvm
                    (assoc valhalla :form
                           (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce '" tag "))")))]
      (is (= 0 (:exit producer)) producer)
      (when (= 0 (:exit producer))
        (doseq [execution-options [[] ["-Xint"]]
                policy ["silent" "warn" "error"]
                mode [:eval :source :analysis]
                [expansion serialized] (map-indexed vector (:result producer))]
          (testing (pr-str [execution-options policy mode expansion])
          ;; Each mode/expansion pair starts without a class or factory.
            (let [consumer (runner/run-jvm
                            (assoc valhalla
                                   :jvm-opts (into (:jvm-opts valhalla) execution-options)
                                   :policy policy :form
                                   (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume-supported "
                                        (pr-str serialized) " " (pr-str mode) "))")))
                  expected {:new-class? true
                            :identity? false
                            :type (if (= tag 'String) "java.lang.String" "java.lang.Long")
                            :native? (= tag 'Long)
                            :construction
                            (vec (repeat 3 {:class? true
                                            :field (if (= tag 'String) "valid" 42)
                                            :nil :rejected :wrong :rejected}))}]
              (is (= 0 (:exit consumer)) consumer)
              (is (= "" (:err consumer)) consumer)
              (is (= {:initially-absent? true :attempts [expected expected]}
                     (:result consumer)) consumer))))))))

(deftest identity-public-transfer-test
  ;; Baseline-selected identity fields must not upgrade on a capable receiver.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc baseline :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce 'String))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
              execution-options [[] ["-Xint"]]
              :let [configuration (update runtime :jvm-opts into execution-options)]
              policy ["silent" "warn"]
              mode [:eval :source :analysis]
              [expansion serialized] (map-indexed vector (:result producer))]
        (testing (pr-str [(:java configuration) (:jvm-opts configuration)
                          policy mode expansion])
          (let [consumer (runner/run-jvm
                          (assoc configuration :policy policy :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume-supported "
                                      (pr-str serialized) " " (pr-str mode) "))")))
                expected {:new-class? true :identity? true :type "java.lang.String" :native? false
                          :construction
                          (vec (repeat 3 {:class? true :field "valid"
                                          :nil :rejected :wrong :rejected}))}]
            (is (= 0 (:exit consumer)) consumer)
            (is (= {:initially-absent? true :attempts [expected expected]}
                   (:result consumer)) consumer)
            (if (= policy "silent")
              (is (= "" (:err consumer)) consumer)
              (is (= 1 (count (re-seq #"value-type: identity deftype fallback:" (:err consumer)))) consumer))))))))

(deftest strict-identity-public-transfer-test
  ;; Strict receivers reject fixed identity output even when value output is available.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc baseline :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce 'String))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
              execution-options [[] ["-Xint"]]]
        (testing (pr-str [(:java runtime) (:jvm-opts runtime) execution-options])
          (let [consumer (runner/run-jvm
                          (assoc runtime
                                 :jvm-opts (into (:jvm-opts runtime) execution-options)
                                 :policy "error" :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume "
                                      (pr-str (:result producer)) "))")))
                expected (assoc checks/fallback-rejection :data nil :absent? true)]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (doseq [mode [:eval :source :analysis]]
              (is (= (vec (repeat 2 (vec (repeat 2 expected))))
                     (get (:result consumer) mode)) consumer))))))))

(deftest mixed-public-transfer-test
  ;; A fresh receiver must preserve both enforcement modes in one public owner.
  (let [{:keys [valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce-mixed))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [execution-options [[] ["-Xint"]]
              policy ["silent" "warn" "error"]
              mode [:eval :source :analysis]
              [expansion serialized] (map-indexed vector (:result producer))]
        (testing (pr-str [execution-options policy mode expansion])
          (let [consumer (runner/run-jvm
                          (assoc valhalla
                                 :jvm-opts (into (:jvm-opts valhalla) execution-options)
                                 :policy policy :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume-mixed "
                                      (pr-str serialized) " " (pr-str mode) "))")))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (is (= (vec (repeat 2 {:new-class? true
                                   :types ["java.lang.Long" "java.lang.String"]
                                   :native [true false]
                                   :construction
                                   (vec (repeat 3 {:class? true :fields [42 "valid"]
                                                   :nil [:rejected :rejected]
                                                   :wrong [:rejected :rejected]}))}))
                   (:result consumer)) consumer)))))))

(deftest unsupported-mixed-public-transfer-test
  ;; Mixed enforcement must not downgrade or install an unchecked owner.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce-mixed))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime [baseline (assoc valhalla :jvm-opts [])]
              execution-options [[] ["-Xint"]]
              policy ["silent" "warn" "error"]]
        (testing (pr-str [(:java runtime) execution-options policy])
          (let [consumer (runner/run-jvm
                          (assoc runtime
                                 :jvm-opts (into (:jvm-opts runtime) execution-options)
                                 :policy policy :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume "
                                      (pr-str (:result producer)) "))")))
                expected (assoc (checks/unavailable-failure
                                 "user.TransferredMixed" (:capability (:result consumer)))
                                :absent? true)]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (doseq [mode [:eval :source :analysis]
                    :let [outcomes (get (:result consumer) mode)]]
              (is (= (vec (repeat 2 (vec (repeat 2 expected))))
                     (mapv #(mapv (fn [o] (dissoc o :message)) %) outcomes))
                  consumer)
              (is (every? #(checks/unavailable-message? "user.TransferredMixed" %)
                          (apply concat outcomes))
                  consumer))))))))
