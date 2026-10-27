(ns value-type.field-transfer-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(deftest unsupported-public-transfer-test
  ;; A capable producer cannot grant native-field permission to a receiver.
  (let [{:keys [baseline valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [[configuration reason]
              [[baseline :missing-classfile-api]
               [(assoc valhalla :jvm-opts []) :unsupported-class-version]]
              policy ["silent" "warn" "error"]]
        (testing (pr-str [(:java configuration) policy])
          (let [consumer (runner/run-jvm
                          (assoc configuration :policy policy :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume "
                                      (pr-str (:result producer)) "))")))
                expected {:data {:value-type/error :field-capability-unavailable
                                 :class-name "user.TransferredField"
                                 :reason reason}
                          :absent? true}]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (doseq [mode [:eval :source :analysis]]
              (is (= (vec (repeat 2 (vec (repeat 2 expected))))
                     (get (:result consumer) mode)) consumer))))))))

(deftest supported-public-transfer-test
  ;; Receiving compilation must install its own native transformation.
  (let [{:keys [valhalla]} (config/configuration)
        producer (runner/run-jvm
                  (assoc valhalla :form
                         "(require '[value-type.field-transfer-checks :as c]) (prn (c/produce))"))]
    (is (= 0 (:exit producer)) producer)
    (when (= 0 (:exit producer))
      (doseq [policy ["silent" "warn" "error"]
              mode [:eval :source :analysis]
              [expansion serialized] (map-indexed vector (:result producer))]
        (testing (pr-str [policy mode expansion])
          ;; Each mode/expansion pair starts without a class or factory.
          (let [consumer (runner/run-jvm
                          (assoc valhalla :policy policy :form
                                 (str "(require '[value-type.field-transfer-checks :as c]) (prn (c/consume-supported "
                                      (pr-str serialized) " " (pr-str mode) "))")))
                expected {:new-class? true :factory-class? true
                          :field 42 :type "java.lang.Long"
                          :nil :rejected :wrong :rejected}]
            (is (= 0 (:exit consumer)) consumer)
            (is (= "" (:err consumer)) consumer)
            (is (= {:initially-absent? true :attempts [expected expected]}
                   (:result consumer)) consumer)))))))
