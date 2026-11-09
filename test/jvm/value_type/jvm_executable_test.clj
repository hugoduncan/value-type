(ns value-type.jvm-executable-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts]))

(deftest path-selection-test
  ;; Overrides select paths, not flags or build identities.
  (let [selection (facts/select (facts/read-facts) :baseline :linux-x64 :plain)
        root (executable/default-root "/home/test")]
    (testing "provisioned paths separate role, build and platform"
      (is (= (str root "/baseline/" (:build selection) "/linux-x64/bin/java")
             (executable/candidate selection {} root))))
    (testing "only the role's nonblank override takes precedence"
      (is (= "/custom/java"
             (executable/candidate selection {"VALUE_TYPE_BASELINE_JAVA" "/custom/java"} root)))
      (is (= (executable/candidate selection {} root)
             (executable/candidate selection {"VALUE_TYPE_BASELINE_JAVA" " "
                                              "JAVA_CMD" "/other/java"} root))))))

(deftest platform-selection-test
  ;; Unsupported architectures must not silently download another platform.
  (doseq [architecture ["aarch64" "arm64"]]
    (is (= :linux-aarch64 (executable/platform "Linux" architecture))))
  (is (= :linux-x64 (executable/platform "Linux" "amd64")))
  (is (= :macos-aarch64 (executable/platform "Mac OS X" "aarch64")))
  (is (= :macos-aarch64 (executable/platform "Mac OS X" "x86_64")))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unsupported JVM host"
                        (executable/platform "Linux" "riscv64"))))

(deftest executable-verification-test
  ;; Exercise the real parent JVM executable, including mismatch rejection.
  (let [java (str (io/file (System/getProperty "java.home") "bin" "java"))
        selection (assoc (facts/select (facts/read-facts) :baseline :linux-x64 :plain)
                         :build (System/getProperty "java.runtime.version"))]
    (testing "the exact runtime build is accepted through an override"
      (is (= java (executable/resolve! selection {"VALUE_TYPE_BASELINE_JAVA" java} "/unused"))))
    (testing "a different expected build is rejected"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"build identity mismatch"
                            (executable/verify! (assoc selection :build "not-this-build") java))))
    (testing "missing executables fail before process execution"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing or non-executable"
                            (executable/verify! selection (str java "/missing")))))))

(deftest verification-timeout-test
  ;; A real stalled executable must stop before verification returns an error.
  (let [script (java.io.File/createTempFile "stalled-java-" ".sh")
        selection {:role :baseline :platform :linux-x64 :build "expected-build"}]
    (try
      ;; Only shell builtins: the recorded PID is the verification process itself.
      (spit script "#!/bin/sh\necho $$\necho startup-stalled >&2\nwhile :; do :; done\n")
      (is (.setExecutable script true))
      (let [path (.getAbsolutePath script)
            error (try (executable/verify! selection path 1000)
                       nil
                       (catch clojure.lang.ExceptionInfo e e))
            data (ex-data error)
            pid (some-> (:out data) str/trim)]
        (is (= "JVM build verification timed out" (some-> error ex-message)))
        (is (= (assoc selection :java path)
               (select-keys data [:role :platform :build :java])))
        (is (= 1000 (:timeout-ms data)))
        (is (true? (:terminated? data)) (pr-str data))
        (is (= "startup-stalled\n" (:err data)))
        (is (boolean (and pid (re-matches #"[0-9]+" pid))) (pr-str data))
        (when (and pid (re-matches #"[0-9]+" pid))
          (is (not (zero? (:exit (shell/sh "kill" "-0" pid))))
              (pr-str data))))
      (finally (.delete script)))))
