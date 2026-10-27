(ns value-type.api-docs-test
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [value-type.api-docs :as docs])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest public-reference-test
  ;; Check the rendered contract, not a snapshot of the whole document.
  (let [markdown (docs/generated-markdown)]
    (testing "only the public macro and array functions are documented"
      (is (= ["value-type.array/array-info" "value-type.array/make-array"
              "value-type.core/defvaluetype"]
             (mapv second (re-seq #"## <a name=\"([^\"]+)\"" markdown))))
      (doseq [internal ["check-value-class" "is-value-method" "mutable-fields"
                        "value-type.impl." "checked-length"]]
        (is (not (str/includes? markdown internal)) internal)))
    (testing "arguments and representation boundaries remain explicit"
      (doseq [text ["(defvaluetype name [& fields] & opts+specs)"
                    "Macro." "JEP 401 JDK with `--enable-preview`"
                    "`value-type.fallback`" "silent (default), warn (once per"
                    "error (reject with Java Error)" "AOT fixes representation"
                    "policy on invocation" "direct class access"
                    "Value artifacts do not convert" "no mutable fields"
                    "`:volatile-mutable`" "`:unsynchronized-mutable`"]]
        (is (str/includes? markdown text) text)))
    (testing "allocation arguments and experimental limits remain explicit"
      (doseq [text ["(make-array component length initial)"
                    "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"
                    "component, length and initial value in that order"
                    "even at length zero" "Native allocation failures propagate"
                    "caller owns the mutable slots" "Flattening is not guaranteed"
                    "safe publication or compound-update safety"
                    "https://github.com/hugoduncan/value-type/blob/master/src/value_type/array.clj#L"]]
        (is (str/includes? markdown text) text)))
    (testing "inspection domain, errors and limits remain explicit"
      (doseq [text ["(array-info array)" "ordinary and restricted reference arrays"
                    "including empty arrays" "runtime Class as :component-class"
                    ":component-value?" ":null-restricted?" ":atomic?" ":flat?"
                    ":value-type/error :invalid-array before capability"
                    "Arrays whose components are arrays are accepted"
                    "reference-array inspection does not require preview"
                    ":value-type/error :array-inspection-unavailable"
                    "Returns all five properties or fails" "unknown facts are not false"
                    "Native failures propagate" "does not change or retain the array"
                    "compound-update safety or caller synchronization"]]
        (is (str/includes? markdown text) text)))
    (testing "generation instructions and navigation are present"
      (doseq [text ["generated from source docstrings" "Do not edit"
                    "[README](../README.md)" "[usage guide](usage.md)" "`bb api-docs`"
                    "`bb api-docs --check`"
                    "https://github.com/hugoduncan/value-type/blob/master/src/value_type/core.clj#L"]]
        (is (str/includes? markdown text) text)))
    (is (= markdown (docs/generated-markdown)))))

(deftest file-check-test
  ;; Use real files to verify byte comparison and non-writing checks.
  (let [dir (.toFile (Files/createTempDirectory "value-type-api-" (make-array FileAttribute 0)))
        file (io/file dir "API.md")]
    (try
      (testing "missing output remains missing"
        (is (false? (docs/current-doc? file)))
        (is (not (.exists file))))
      (testing "stale output remains unchanged"
        (spit file "stale\r\n" :encoding "UTF-8")
        (is (false? (docs/current-doc? file)))
        (is (= "stale\r\n" (slurp file :encoding "UTF-8"))))
      (testing "generation is repeatable and check does not write"
        (docs/write-doc! file)
        (let [first-bytes (vec (Files/readAllBytes (.toPath file)))
              modified (.lastModified file)]
          (is (true? (docs/current-doc? file)))
          (is (= modified (.lastModified file)))
          (docs/write-doc! file)
          (is (= first-bytes (vec (Files/readAllBytes (.toPath file)))))))
      (finally
        (io/delete-file file true)
        (io/delete-file dir)))))

(deftest command-line-test
  ;; Exercise the documented Babashka entry point in an isolated project.
  (let [dir (.toFile (Files/createTempDirectory "value-type-api-cli-" (make-array FileAttribute 0)))
        output (io/file dir "doc/API.md")
        run! (fn [& args]
               (apply shell/sh "bb" "api-docs" (concat args [:dir dir])))
        bytes #(vec (Files/readAllBytes (.toPath output)))]
    (try
      (doseq [path ["deps.edn" "bb.edn" "bb/value_type/quality.clj"
                    "bb/value_type/api_docs.clj" "src/value_type/core.clj"
                    "src/value_type/array.clj"]]
        (let [destination (io/file dir path)]
          (io/make-parents destination)
          (io/copy (io/file path) destination)))
      (testing "missing output fails with a repair command and creates no file"
        (let [{:keys [exit err] :as result} (run! "--check")]
          (is (pos? exit) (pr-str result))
          (is (str/includes? err "missing or stale") err)
          (is (str/includes? err "bb api-docs") err)
          (is (not (.exists output)))))
      (io/make-parents output)
      (spit output "stale é\r\n" :encoding "UTF-8")
      (let [original (bytes)
            modified (.lastModified output)]
        (testing "stale output fails without changing bytes or modification time"
          (let [{:keys [exit err] :as result} (run! "--check")]
            (is (pos? exit) (pr-str result))
            (is (str/includes? err "missing or stale") err)
            (is (str/includes? err "bb api-docs") err)
            (is (= original (bytes)))
            (is (= modified (.lastModified output)))))
        (testing "unknown or extra arguments fail without writing"
          (doseq [args [["--unknown"] ["--check" "extra"]]]
            (let [{:keys [exit err] :as result} (apply run! args)]
              (is (pos? exit) (pr-str result))
              (is (str/includes? err "Usage: bb api-docs [--check]") err)
              (is (= original (bytes)))
              (is (= modified (.lastModified output)))))))
      (testing "generation repairs output and produces identical bytes twice"
        (let [result (run!)]
          (is (zero? (:exit result)) (pr-str result)))
        (is (= (docs/generated-markdown) (slurp output :encoding "UTF-8")))
        (let [generated (bytes)
              modified (.lastModified output)
              checked (run! "--check")]
          (is (zero? (:exit checked)) (pr-str checked))
          (is (= generated (bytes)))
          (is (= modified (.lastModified output)))
          (let [result (run!)]
            (is (zero? (:exit result)) (pr-str result)))
          (is (= generated (bytes)))))
      (finally
        (doseq [file (reverse (file-seq dir))]
          (io/delete-file file))))))
