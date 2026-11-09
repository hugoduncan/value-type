(ns value-type.forced-jit-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [value-type.forced-jit :as jit]
            [value-type.jvm-facts :as facts]
            [value-type.jvm-executable :as executable])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [java.util.concurrent TimeUnit]))

(deftest inherited-options-test
  ;; Global JVM options would also change child controls.
  (doseq [variable ["JAVA_TOOL_OPTIONS" "JDK_JAVA_OPTIONS" "_JAVA_OPTIONS"]]
    (testing variable
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"inherited JVM options"
                            (jit/check-environment! {variable "-Xcomp"})))
      (is (thrown? clojure.lang.ExceptionInfo
                   (jit/check-environment! {variable " "})))
      (is (= {variable ""} (jit/check-environment! {variable ""}))))))

(deftest candidate-selection-test
  ;; Real executable verification follows supplied facts, not a built-in EA pin.
  (let [build (System/getProperty "java.runtime.version")
        java (str (io/file (System/getProperty "java.home") "bin" "java"))
        candidate (mapv #(if (= :valhalla (:role %)) (assoc % :build build) %)
                        (facts/read-facts))
        environment {"VALUE_TYPE_VALHALLA_JAVA" java}
        configuration (jit/configuration! candidate :linux-x64 environment "/unused")]
    (is (= build (:build configuration)))
    (is (= java (:java configuration)))
    (is (= ["--enable-preview"] (:child-flags configuration)))
    (is (= ["--enable-preview" "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"
            "-Dvalue-type.value-classes=auto" "-Xcomp" "-Xbatch" "-XX:-TieredCompilation"]
           (:parent-flags configuration)))
    (is (= (into [java] (concat (:parent-flags configuration)
                                ["-cp" "classpath" "clojure.main" "-m" "scry.cli"
                                 "--dir" "test/runtime"]))
           (jit/command configuration "classpath")))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"build identity mismatch"
                          (jit/configuration!
                           (mapv #(assoc % :build "wrong-build") candidate)
                           :linux-x64 environment "/unused")))))

(defn- launch!
  [directory java]
  (let [command ["bb" "--config" "bb.edn" "--classpath"
                 (str (.getCanonicalPath (io/file "bb")) java.io.File/pathSeparator
                      (.getCanonicalPath (io/file "dev/jvm"))) "-e"
                 "(require '[value-type.forced-jit-runner :as runner]) (runner/run!)"]
        command-file (io/file directory "command.edn")
        result-file (io/file directory "result.edn")
        log (io/file directory "launcher.log")
        builder (ProcessBuilder.
                 ^java.util.List
                 ["bb" "--config" "bb.edn"
                  (.getCanonicalPath (io/file "test-resources/forced-jit/supervise.clj"))
                  (.getPath command-file) (.getPath result-file)])]
    (spit command-file (pr-str command))
    (io/delete-file result-file true)
    (.directory builder directory)
    (.put (.environment builder) "VALUE_TYPE_VALHALLA_JAVA" java)
    (.redirectErrorStream builder true)
    (.redirectOutput builder log)
    (let [process (.start builder)]
      ;; Babashka owns ProcessHandle use; this test also runs on Java 8.
      (when-not (.waitFor process 210 TimeUnit/SECONDS)
        (throw (ex-info "Process supervisor timed out; retained project"
                        {:directory (.getPath directory) :log (slurp log)})))
      (let [result (when (.isFile result-file) (edn/read-string (slurp result-file)))]
        (when-not (and (zero? (.exitValue process))
                       (seq (:processes result))
                       (every? (comp false? :alive?) (:processes result)))
          (throw (ex-info "Process cleanup failed; retained project"
                          {:directory (.getPath directory) :result result :log (slurp log)})))
        (assoc result :log (slurp log))))))

(deftest launcher-process-test
  ;; Run the real launcher and Scry against isolated passing and failing inputs.
  (let [entries (facts/read-facts)
        selection (facts/select entries :valhalla
                                (executable/platform (System/getProperty "os.name")
                                                     (System/getProperty "os.arch"))
                                :preview-export)
        java (executable/resolve! selection (into {} (System/getenv))
                                  (executable/default-root (System/getProperty "user.home")))
        directory (.toFile (Files/createTempDirectory "forced-jit-launcher-"
                                                      (make-array FileAttribute 0)))
        expected {:build (:build selection)
                  :flags #{"--enable-preview"
                           "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"
                           "-Dvalue-type.value-classes=auto"
                           "-Xcomp" "-Xbatch" "-XX:-TieredCompilation"}}
        project (edn/read-string (slurp "deps.edn"))
        completed? (atom false)]
    (try
      (.mkdirs (io/file directory "dev/jvm"))
      (.mkdirs (io/file directory "test/runtime"))
      (.mkdirs (io/file directory "test/other"))
      (spit (io/file directory "dev/jvm/jvms.edn") (pr-str entries))
      (spit (io/file directory "bb.edn")
            (pr-str {}))
      (spit (io/file directory "deps.edn")
            (pr-str {:paths ["test/other"] :deps (:deps project)
                     :aliases {:test (get-in project [:aliases :test])}}))
      (io/copy (io/file "test-resources/forced-jit/launcher_test.clj")
               (io/file directory "test/runtime/launcher_test.clj"))
      (io/copy (io/file "test-resources/forced-jit/out_of_tier_test.clj")
               (io/file directory "test/other/out_of_tier_test.clj"))
      (doseq [mode [:pass :fail :timeout]]
        (testing (name mode)
          (spit (io/file directory "expected.edn") (pr-str (assoc expected :fail? (= :fail mode) :timeout? (= :timeout mode))))
          (io/delete-file (io/file directory "observed.edn") true)
          (io/delete-file (io/file directory "out-of-tier-executed") true)
          (let [{:keys [exit log timed-out? processes]} (launch! directory java)
                observed-file (io/file directory "observed.edn")]
            (if (= :timeout mode)
              (do
                (is (true? timed-out?) log)
                (is (<= 3 (count processes)) (pr-str processes))
                (is (every? (comp false? :alive?) processes) (pr-str processes)))
              (do
                (is (not timed-out?) log)
                (is (= (= :pass mode) (= 0 exit)) log)))
            (is (not (.exists (io/file directory "out-of-tier-executed")))
                "Forced-JIT discovery must not execute tests outside test/runtime")
            (is (.isFile observed-file) log)
            (when (.isFile observed-file)
              (let [observed (edn/read-string (slurp observed-file))]
                (is (= (:build expected) (:build observed)))
                (is (= "auto" (:mode observed)))
                (is (every? (:flags observed) (:flags expected)) (pr-str observed)))))))
      (reset! completed? true)
      (finally
        ;; Keep output and project files when cleanup or execution throws.
        (when @completed?
          (doseq [file (reverse (file-seq directory))]
            (io/delete-file file true)))))))
