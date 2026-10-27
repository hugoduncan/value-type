(ns value-type.array-boundary-test
  (:require [clojure.test :refer [deftest is]]
            [value-type.jvm-config :as config]
            [value-type.jvm-runner :as runner]))

(def ^:private control-form
  "(require 'value-type.array 'value-type.core)
   (prn (mapv (fn [_]
                (try (value-type.array/make-array nil -1 nil)
                     (catch clojure.lang.ExceptionInfo e
                       {:data (ex-data e) :message (ex-message e)
                        :cause (some-> e ex-cause class .getName)})))
              (range 2)))")

(deftest unavailable-boundary-test
  ;; Real child JVMs prove safe loading and capability-first repeated failures.
  (let [{:keys [baseline valhalla]} (config/configuration)]
    (doseq [[configuration reason cause]
            [[baseline :missing-classfile-api nil]
             [valhalla :array-access-denied "java.lang.IllegalAccessException"]]]
      (let [{:keys [exit result err]}
            (runner/run-jvm (assoc configuration :form control-form))]
        (is (= 0 exit) err)
        (is (= 2 (count result)))
        (is (= (first result) (second result)))
        (doseq [outcome result]
          (is (= :array-unavailable (get-in outcome [:data :value-type/error])))
          (is (= reason (get-in outcome [:data :reason])))
          (is (= cause (:cause outcome)))
          (is (re-find #"--enable-preview" (:message outcome)))
          (is (re-find #"--add-exports" (:message outcome))))
        (is (not (re-find #"(?i)fallback" err)))))))
