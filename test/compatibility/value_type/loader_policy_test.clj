(ns value-type.loader-policy-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.identity-loader-checks :as checks]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private public-loader-check
  '(do
     (require 'value-type.identity-loader-checks)
     (prn (value-type.identity-loader-checks/public-stale-sibling))))

(deftest fresh-policy-loader-rejection-test
  ;; Real stale imports must fail even when strict policy permits value owners.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[runtime policies] [[baseline ["silent" "warn"]]
                                [(assoc valhalla :jvm-opts []) ["silent" "warn"]]
                                [valhalla ["silent" "warn" "error"]]]
            policy policies
            execution [[] ["-Xint"]]]
      (testing (pr-str [(:java runtime) (:jvm-opts runtime) policy execution])
        (let [{:keys [exit result err]}
              (runner/run-jvm
               (assoc (update runtime :jvm-opts into execution)
                      :policy policy :form (pr-str public-loader-check)))]
          (is (= 0 exit) err)
          (is (= 6 (count result)) (pr-str result))
          (doseq [[case-key case-result] result]
            (testing (pr-str case-key)
              (is (= checks/public-stale-sibling-expected case-result)))))))))
