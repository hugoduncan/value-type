(ns value-type.capture-loading-test
  (:require [clojure.test :refer [deftest is testing]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(deftest baseline-capture-loading-test
  ;; A fresh process proves loading capture support does not load optional APIs.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [jvm [baseline (assoc valhalla :jvm-opts [])]]
      (testing (:java jvm)
        (let [result (runner/run-jvm
                      (assoc jvm :form
                             "(do
                                (require 'value-type.core 'value-type.impl.loader)
                                (prn {:install-callable? (ifn? (deref (resolve 'value-type.impl.loader/install!)))
                                      :optional-loaded? (boolean
                                                         (some find-ns
                                                               '[value-type.impl.value-backend
                                                                 value-type.impl.transform
                                                                 value-type.impl.field-access]))}))"))]
          (is (= 0 (:exit result)) (pr-str result))
          (is (= {:install-callable? true :optional-loaded? false}
                 (:result result))))))))
