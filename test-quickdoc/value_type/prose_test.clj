(ns value-type.prose-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest prose-command-test
  ;; Real Vale rules isolate severity and scope from downloaded style changes.
  (let [dir (.toFile (Files/createTempDirectory "value-type-prose-" (make-array FileAttribute 0)))
        put! (fn [path text]
               (let [file (io/file dir path)]
                 (io/make-parents file)
                 (spit file text)))
        run! #(shell/sh "bb" "vale" :dir dir)
        config "StylesPath = styles\nMinAlertLevel = suggestion\n[*.md]\nBasedOnStyles = Probe\nBlockIgnores = (?m)^λ[^\\n]*\n"]
    (try
      (doseq [path ["bb.edn" "bb/value_type/quality.clj" "bb/value_type/prose.clj"]]
        (let [destination (io/file dir path)]
          (io/make-parents destination)
          (io/copy (io/file path) destination)))
      (put! ".vale.ini" config)
      (doseq [severity ["suggestion" "warning" "error"]]
        (put! (str "styles/Probe/" severity ".yml")
              (str "extends: existence\nmessage: 'Found " severity " marker.'\nlevel: "
                   severity "\ntokens:\n  - '" severity "marker'\n")))
      (doseq [path ["README.md" "AGENTS.md" "doc/API.md" "doc/usage.md" "doc/development.md"
                    "doc/development/quality-checks.md" "doc/development/runtime.md"]]
        (put! path "Clean prose.\n"))
      (put! "outside.md" "errormarker\n")
      (testing "suggestions are reported but do not fail; other files are excluded"
        (put! "README.md" "suggestionmarker\n")
        (let [result (run!)]
          (is (zero? (:exit result)) (pr-str result))
          (is (str/includes? (:out result) "suggestionmarker"))))
      (testing "warnings and errors fail on each scoped path without edits"
        (doseq [path ["README.md" "AGENTS.md" "doc/API.md" "doc/usage.md" "doc/development.md"
                      "doc/development/quality-checks.md" "doc/development/runtime.md"]
                severity ["warning" "error"]]
          (let [text (str severity "marker\n")]
            (put! path text)
            (let [result (run!)]
              (is (pos? (:exit result)) (pr-str result))
              (is (str/includes? (:out result) (str severity "marker")))
              (is (= text (slurp (io/file dir path)))))
            (put! path "Clean prose.\n"))))
      (testing "lambda instructions and fenced code remain protected"
        (let [text "λ errormarker\n\n```clojure\nwarningmarker\n```\n"]
          (put! "AGENTS.md" text)
          (let [result (run!)]
            (is (zero? (:exit result)) (pr-str result)))
          (is (= text (slurp (io/file dir "AGENTS.md"))))))
      (testing "missing styles and invalid configuration fail"
        (doseq [text [(str/replace config "BasedOnStyles = Probe" "BasedOnStyles = Missing")
                      "not valid ini [\n"]]
          (put! ".vale.ini" text)
          (let [result (run!)]
            (is (pos? (:exit result)) (pr-str result)))))
      (testing "missing scoped input fails"
        (put! ".vale.ini" config)
        (io/delete-file (io/file dir "doc/API.md"))
        (let [result (run!)]
          (is (pos? (:exit result)) (pr-str result))
          (is (str/includes? (:err result) "Missing Vale input"))))
      (finally
        (doseq [file (reverse (file-seq dir))]
          (io/delete-file file))))))

(deftest invalid-vale-output-test
  ;; Exercise the parser with data, without replacing the Vale process.
  (doseq [output ["not json" "[]" "{\"file\":{}}"
                  "{\"file\":[{\"Severity\":\"unknown\"}]}"]]
    (let [result (shell/sh "bb" "-cp" "bb" "-e"
                           (str "(require '[value-type.prose :as prose])"
                                "(prose/parse-alerts " (pr-str output) ")"))]
      (is (pos? (:exit result)) (pr-str result)))))

(deftest owned-prose-protection-test
  ;; Keep production exclusions and vocabulary so configuration regressions fail.
  (let [dir (.toFile (Files/createTempDirectory "value-type-owned-prose-" (make-array FileAttribute 0)))
        protected "λ This is definately incorrect.\n\n```clojure\nThis is definately incorrect.\n```\n"
        agents (io/file dir "AGENTS.md")]
    (try
      (doseq [path ["bb.edn" "bb/value_type/quality.clj" "bb/value_type/prose.clj"
                    ".vale.ini" "styles/config/vocabularies/ValueType/accept.txt"]]
        (let [destination (io/file dir path)]
          (io/make-parents destination)
          (io/copy (io/file path) destination)))
      (doseq [path ["README.md" "doc/API.md" "doc/usage.md" "doc/development.md"
                    "doc/development/quality-checks.md" "doc/development/runtime.md"]]
        (let [file (io/file dir path)]
          (io/make-parents file)
          (spit file "Clean prose.\n")))
      (doseq [[text pass?] [[protected true]
                            [(str protected "\nThis is definately incorrect.\n") false]]]
        (spit agents text :encoding "UTF-8")
        (let [before (vec (Files/readAllBytes (.toPath agents)))
              result (shell/sh "bb" "vale" :dir dir)]
          (is (= pass? (zero? (:exit result))) (pr-str result))
          (when-not pass?
            (is (str/includes? (:out result) "definately") (pr-str result)))
          (is (= before (vec (Files/readAllBytes (.toPath agents)))))))
      (finally
        (doseq [file (reverse (file-seq dir))]
          (io/delete-file file))))))
