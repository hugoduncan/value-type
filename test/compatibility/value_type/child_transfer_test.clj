(ns value-type.child-transfer-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.consumer-aot-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(defn- check-form
  "Return a child form that prints (child-transfer-checks/check & args)."
  [check & args]
  (pr-str (list 'do
                '(require 'value-type.child-transfer-checks)
                (list 'prn (list* (symbol "value-type.child-transfer-checks" (name check))
                                  args)))))

(defn- consumed
  "Return the expected consume result of a value? parent with annotations."
  [value? annotations]
  {:child-fields ["receiver" "replacement"] :exact-class? true
   :value? value? :final? true :annotations annotations
   :constructions (vec (repeat 3 {:retained? true
                                  :rejections ["java.lang.NullPointerException"
                                               "java.lang.ClassCastException"]}))})

(deftest receiver-child-replacement-test
  ;; Serialized field tags resolve to the receiver Class, not producer storage.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm (assoc baseline :form (check-form 'produce)))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
              policy ["silent" "warn"]
              execution [[] ["-Xint"]]
              mode [:eval :source :analysis]
              serialized (:result producer)]
        (testing (pr-str [(:java runtime) (:jvm-opts runtime) policy execution mode])
          (let [consumer (runner/run-jvm
                          (assoc runtime :policy policy
                                 :jvm-opts (into (:jvm-opts runtime) execution)
                                 :form (check-form 'consume serialized mode)))]
            (is (= 0 (:exit consumer)) consumer)
            (if (= "warn" policy)
              (is (= 1 (count (re-seq #"value-type: identity deftype fallback:"
                                      (:err consumer)))) consumer)
              (is (= "" (:err consumer)) consumer))
            (is (= (consumed false []) (:result consumer)) consumer)))))))

(defn- value-parent-replacement
  "Check that a value parent produced against a native? child resolves the
   receiver child under every policy, with the expected annotations."
  [native? annotations]
  (let [{:keys [valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :policy "error" :form (check-form 'produce native?)))]
    (is (= 0 (:exit producer)) producer)
    (is (= "" (:err producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [policy ["silent" "warn" "error"]
              execution [[] ["-Xint"]]
              mode [:eval :source :analysis]
              serialized (:result producer)]
        (testing (pr-str [native? policy execution mode])
          (let [consumer (runner/run-jvm
                          (assoc valhalla :policy policy
                                 :jvm-opts (into (:jvm-opts valhalla) execution)
                                 :form (check-form 'consume serialized mode native?)))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (is (= (consumed true annotations) (:result consumer)) consumer)))))))

(deftest value-receiver-child-replacement-test
  ;; A fixed value parent resolves an identity child locally under every policy.
  (value-parent-replacement false []))

(deftest native-receiver-child-replacement-test
  ;; Native storage resolves the replacement value child under every policy.
  (value-parent-replacement true ["jdk.internal.vm.annotation.NullRestricted"]))

(deftest strict-receiver-child-replacement-test
  ;; Replacement must not let a fixed identity expansion bypass strict policy.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm (assoc baseline :form (check-form 'produce)))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [runtime [baseline (assoc valhalla :jvm-opts []) valhalla]
              execution [[] ["-Xint"]]
              mode [:eval :source :analysis]
              serialized (:result producer)]
        (testing (pr-str [(:java runtime) (:jvm-opts runtime) execution mode])
          (let [consumer (runner/run-jvm
                          (assoc runtime :policy "error"
                                 :jvm-opts (into (:jvm-opts runtime) execution)
                                 :form (check-form 'reject-replaced-child serialized mode)))]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (is (= (vec (repeat 2 {:outcome checks/fallback-rejection
                                   :child-retained? true
                                   :owner-absent? true
                                   :factory-absent? true}))
                   (:result consumer)) consumer)))))))
