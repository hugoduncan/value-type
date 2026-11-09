(ns launcher-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]])
  (:import [java.lang.management ManagementFactory]))

(deftest launcher-configuration-test
  ;; Record the actual Scry JVM, even when its configuration is wrong.
  (let [expected (edn/read-string (slurp "expected.edn"))
        observed {:build (System/getProperty "java.runtime.version")
                  :mode (System/getProperty "value-type.value-classes")
                  :flags (set (.getInputArguments (ManagementFactory/getRuntimeMXBean)))}]
    (spit "observed.edn" (pr-str observed))
    (is (= (:build expected) (:build observed)))
    (is (= "auto" (:mode observed)))
    (doseq [flag (:flags expected)]
      (is (contains? (:flags observed) flag) flag))
    (when (:timeout? expected)
      ;; The marker is written by a real child only after it has started.
      (let [child (.start
                   (ProcessBuilder.
                    ^java.util.List
                    [(str (System/getProperty "java.home") "/bin/java")
                     "-cp" (System/getProperty "java.class.path")
                     "clojure.main" "-e"
                     "(spit \"timeout-ready\" \"ready\") @(promise)"]))]
        (.waitFor child)))
    (is (not (:fail? expected)) "Input requests a failing selected suite")))
