(ns value-type.object-size-integration-test
  (:require
   [clojure.test :refer [deftest is]]
   [value-type.object-size :as size]))

(deftest environment-integration-test
  ;; Explicit integration check extracts the real agent and reads JVM state.
  (let [environment (#'size/environment-data (#'size/read-environment-state))]
    (is (= "0.5.251-ALPHA" (:criterium environment)))
    (is (= (clojure-version) (:clojure environment)))
    (is (= (System/getProperty "java.vm.version")
           (get-in environment [:system "java.vm.version"])))
    (is (re-matches #"[0-9a-f]{64}" (get-in environment [:agent :sha256])))
    (is (= 4 (count (:vm-options environment))))
    (is (= {:value :unknown :reason "VM option unavailable"}
           (#'size/vm-option "NotARealVMOption")))))
