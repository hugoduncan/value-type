(ns value-type.release-test
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [value-type.notes :as notes]
            [value-type.readme :as readme]
            [value-type.readme-test :as readme-test]
            [value-type.release :as release])
  (:import [java.nio.file Files]
           [java.time LocalDate ZoneOffset]))

(def initial "## [Unreleased]\n\nNew feature\n\n## [0.1.9] - 2025-01-01\nPrior notes\n")

(defn- git [dir & args]
  (let [{:keys [exit out err]} (apply shell/sh "git" (concat args [:dir dir]))]
    (when-not (zero? exit) (throw (ex-info err {:args args :exit exit})))
    (str/trim out)))

(defn- with-project [f]
  (let [root (.toFile (Files/createTempDirectory "release-test-" (make-array java.nio.file.attribute.FileAttribute 0)))
        project (io/file root "project")
        config (io/file root "gitconfig")
        hooks (io/file root "hooks")
        env (into {} (remove (fn [[k _]] (str/starts-with? k "GIT_"))) (System/getenv))]
    (.mkdir project)
    (.mkdir hooks)
    (spit config (str "[user]\n name = Release Test\n email = release@example.invalid\n"
                      "[commit]\n gpgSign = false\n[core]\n hooksPath = " hooks "\n"))
    (try
      (binding [shell/*sh-env* (assoc env "GIT_CONFIG_NOSYSTEM" "1" "GIT_CONFIG_GLOBAL" (str config))]
        (git project "init" "-b" "master")
        (spit (io/file project "CHANGELOG.md") initial)
        (spit (io/file project "README.md") readme-test/initial)
        (git project "add" ".")
        (git project "commit" "-m" "Initial")
        (f project root hooks))
      (finally
        (doseq [file (reverse (file-seq root))] (io/delete-file file true))))))

(deftest preparation-test
  ;; Preparation makes exactly one local two-file commit and no remote or tag changes.
  (with-project
    (fn [project root _]
      (let [remote (io/file root "remote.git")
            before-date (str (LocalDate/now ZoneOffset/UTC))]
        (git root "init" "--bare" (str remote))
        (git project "remote" "add" "origin" (str remote))
        (git project "push" "origin" "master")
        (let [remote-before (git remote "rev-parse" "master")
              result (release/prepare project)
              sections (:sections (notes/parse (slurp (io/file project "CHANGELOG.md"))))]
          (is (= "0.1.2" (:version result)))
          (is (= (readme/render readme-test/initial (:version result))
                 (:out (shell/sh "git" "show" "HEAD:README.md" :dir project))))
          (is (= (:sha result) (git project "rev-parse" "HEAD")))
          (is (= "2" (git project "rev-list" "--count" "HEAD")))
          (is (= "Release v0.1.2" (git project "log" "-1" "--format=%s")))
          (is (= "CHANGELOG.md\nREADME.md" (git project "diff-tree" "--no-commit-id" "--name-only" "-r" "HEAD")))
          (is (contains? (set [before-date (str (LocalDate/now ZoneOffset/UTC))]) (:date (second sections))))
          (is (str/blank? (:body (first sections))))
          (is (= "Prior notes\n" (:body (last sections))))
          (is (= "" (git project "status" "--porcelain")))
          (is (= "" (git project "tag")))
          (is (= remote-before (git remote "rev-parse" "master"))))))))

(deftest rejected-preparation-test
  ;; Guard and validation failures must preserve HEAD, index, and changelog bytes.
  (doseq [scenario [:staged :unstaged :untracked :branch :detached :invalid :duplicate :readme-missing :readme-duplicate :shallow]]
    (testing (name scenario)
      (with-project
        (fn [project root _]
          (case scenario
            :staged (do (spit (io/file project "extra") "change") (git project "add" "extra"))
            :unstaged (spit (io/file project "CHANGELOG.md") "changed" :append true)
            :untracked (spit (io/file project "extra") "change")
            :branch (git project "checkout" "-b" "other")
            :detached (git project "checkout" "--detach")
            :invalid (do (spit (io/file project "CHANGELOG.md") "invalid")
                         (git project "commit" "-am" "Invalid notes"))
            :duplicate (do (spit (io/file project "CHANGELOG.md")
                                 (str initial "\n## [0.1.3] - 2025-01-01\nAlready present\n"))
                           (git project "commit" "-am" "Duplicate next version"))
            :readme-missing (do (spit (io/file project "README.md") "No markers")
                                (git project "commit" "-am" "Invalid README"))
            :readme-duplicate (do (spit (io/file project "README.md")
                                        (str readme-test/initial "<!-- release-install:start -->"))
                                  (git project "commit" "-am" "Duplicate README marker"))
            :shallow nil)
          (let [directory (if (= scenario :shallow)
                            (let [clone (io/file root "shallow")]
                              (git root "clone" "--depth=1" (str "file://" (.getAbsolutePath project)) (str clone))
                              clone)
                            project)
                file (io/file directory "CHANGELOG.md")
                before [(git directory "rev-parse" "HEAD") (git directory "status" "--porcelain") (slurp file) (slurp (io/file directory "README.md"))]]
            (is (thrown? Exception (release/prepare directory)))
            (is (= before [(git directory "rev-parse" "HEAD") (git directory "status" "--porcelain") (slurp file) (slurp (io/file directory "README.md"))]))))))))

(deftest failed-commit-test
  ;; A real rejecting hook leaves the prepared changes available for inspection.
  (with-project
    (fn [project _ hooks]
      (let [head (git project "rev-parse" "HEAD")
            hook (io/file hooks "pre-commit")]
        (spit hook "#!/bin/sh\nexit 1\n")
        (.setExecutable hook true)
        (is (thrown? clojure.lang.ExceptionInfo (release/prepare project)))
        (is (= head (git project "rev-parse" "HEAD")))
        (is (= (readme/render readme-test/initial "0.1.2")
               (slurp (io/file project "README.md"))))
        (is (not (str/blank? (git project "status" "--porcelain"))))
        (is (= "0.1.2" (:source (notes/select-notes (slurp (io/file project "CHANGELOG.md")) "0.1.2" false))))))))

(defn- cli [project & args]
  (let [classpath (str/join java.io.File/pathSeparator
                            (mapv #(.getAbsolutePath (io/file %))
                                  (str/split (System/getProperty "java.class.path")
                                             (re-pattern java.io.File/pathSeparator))))]
    (apply shell/sh (str (System/getProperty "java.home") "/bin/java")
           "-cp" classpath "clojure.main" "-m" "value-type.release"
           (concat args [:dir project]))))

(deftest preparation-cli-test
  ;; Real CLI processes report success, argument errors, and retained commit failures.
  (with-project
    (fn [project _ hooks]
      (doseq [args [[] ["prepare" "extra"] ["notes"] ["notes" "a" "b" "false"]]]
        (let [result (apply cli project args)]
          (is (not (zero? (:exit result))))
          (is (str/includes? (:err result) "Usage:"))))
      (is (= "1" (git project "rev-list" "--count" "HEAD")))
      (let [hook (io/file hooks "pre-commit")]
        (spit hook "#!/bin/sh\nexit 1\n")
        (.setExecutable hook true)
        (let [result (cli project "prepare")]
          (is (not (zero? (:exit result))))
          (is (str/includes? (:err result) "inspect local state")))
        (is (= "1" (git project "rev-list" "--count" "HEAD")))
        (is (str/includes? (slurp (io/file project "CHANGELOG.md")) "## [0.1.2]")))))
  (with-project
    (fn [project _ _]
      (is (zero? (:exit (cli project "prepare"))))
      (is (= "Release v0.1.2" (git project "log" "-1" "--format=%s")))
      (is (= "" (git project "status" "--porcelain"))))))

(deftest post-commit-count-mismatch-test
  ;; A hook-created extra commit must fail preparation without undoing local state.
  (with-project
    (fn [project root hooks]
      (let [remote (io/file root "remote.git")
            hook (io/file hooks "post-commit")
            before-date (str (LocalDate/now ZoneOffset/UTC))]
        (git root "init" "--bare" (str remote))
        (git project "remote" "add" "origin" (str remote))
        (git project "push" "origin" "master")
        (let [remote-before (git remote "show-ref")]
          (spit hook (str "#!/bin/sh\nset -eu\nrm -- \"$0\"\n"
                          "git -c core.hooksPath=/dev/null commit --allow-empty -m 'Hook commit'\n"))
          (.setExecutable hook true)
          (let [result (cli project "prepare")
                changelog (slurp (io/file project "CHANGELOG.md"))
                sections (:sections (notes/parse changelog))]
            (is (not (zero? (:exit result))))
            (is (str/includes? (:err result) "Release commit count changed; inspect local state"))
            (is (= "3" (git project "rev-list" "--count" "HEAD")))
            (is (= (git project "hash-object" "README.md")
                   (git project "rev-parse" "HEAD:README.md")
                   (git project "rev-parse" "HEAD^:README.md")))
            (is (= (readme/render readme-test/initial "0.1.2")
                   (slurp (io/file project "README.md"))))
            (is (= "Hook commit\nRelease v0.1.2\nInitial"
                   (git project "log" "--format=%s")))
            (is (= (git project "hash-object" "CHANGELOG.md")
                   (git project "rev-parse" "HEAD:CHANGELOG.md")
                   (git project "rev-parse" "HEAD^:CHANGELOG.md")))
            (is (str/blank? (:body (first sections))))
            (is (= "0.1.2" (:source (notes/select-notes changelog "0.1.2" false))))
            (is (contains? (set [before-date (str (LocalDate/now ZoneOffset/UTC))])
                           (:date (second sections))))
            (is (= "" (git project "status" "--porcelain")))
            (is (= "" (git project "tag")))
            (is (= remote-before (git remote "show-ref")))))))))

(deftest notes-cli-test
  ;; Preview fallback is explicit; prepared notes export exact bodies and step outputs.
  (with-project
    (fn [project root _]
      (let [body (io/file root "notes.md")
            output (io/file root "outputs")
            run-notes (fn [& options]
                        (apply cli project "notes" (str body) (str output) options))]
        (is (not (zero? (:exit (run-notes)))))
        (is (not (.exists body)))
        (is (not (.exists output)))
        (is (zero? (:exit (run-notes "--preview"))))
        (is (= (:body (notes/select-notes initial "0.1.1" true)) (slurp body)))
        (is (= (str "sha=" (git project "rev-parse" "HEAD")
                    "\nversion=0.1.1\ntag=v0.1.1\nsource=Unreleased\npreparation-required=true\n")
               (slurp output)))
        (is (zero? (:exit (cli project "prepare"))))
        (spit output "existing=value\n")
        (is (zero? (:exit (run-notes))))
        (is (= (:body (notes/select-notes (slurp (io/file project "CHANGELOG.md")) "0.1.2" false))
               (slurp body)))
        (is (= (str "existing=value\nsha=" (git project "rev-parse" "HEAD")
                    "\nversion=0.1.2\ntag=v0.1.2\nsource=0.1.2\npreparation-required=false\n")
               (slurp output)))
        (spit (io/file project "CHANGELOG.md") "## [Unreleased]\nPreview\n## [0.1.2] - 2025-01-01\n")
        (spit body "unchanged")
        (spit output "unchanged")
        (doseq [options [[] ["--preview"]]]
          (testing (str "invalid matching notes with options " options)
            (is (not (zero? (:exit (apply run-notes options)))))
            (is (= "unchanged" (slurp body) (slurp output)))))))))

(deftest shallow-notes-cli-test
  ;; A truncated count must not export release metadata in either mode.
  (with-project
    (fn [project root _]
      (let [clone (io/file root "shallow")
            body (io/file root "notes.md")
            output (io/file root "outputs")]
        (spit (io/file project "CHANGELOG.md")
              (str initial "\n## [0.1.1] - 2025-01-01\nValid truncated-count notes\n"))
        (git project "commit" "-am" "Add matching notes")
        (git root "clone" "--depth=1" (str "file://" (.getAbsolutePath project)) (str clone))
        (is (= "true" (git clone "rev-parse" "--is-shallow-repository")))
        (is (= "1" (git clone "rev-list" "--count" "HEAD")))
        (doseq [options [[] ["--preview"]]]
          (testing (str "shallow notes with options " options)
            (spit body "existing notes\n")
            (spit output "existing=value\n")
            (let [result (apply cli clone "notes" (str body) (str output) options)]
              (is (not (zero? (:exit result))))
              (is (str/includes? (:err result) "requires complete history")))
            (is (= "existing notes\n" (slurp body)))
            (is (= "existing=value\n" (slurp output)))))))))

(deftest replacement-preparation-test
  ;; A later preparation replaces coordinates in the same commit as its notes.
  (with-project
    (fn [project root _]
      (let [remote (io/file root "remote.git")]
        (git root "init" "--bare" (str remote))
        (git project "remote" "add" "origin" (str remote))
        (release/prepare project)
        (git project "tag" "prior")
        (git project "push" "origin" "master" "--tags")
        (spit (io/file project "CHANGELOG.md")
              (str/replace (slurp (io/file project "CHANGELOG.md"))
                           "## [Unreleased]" "## [Unreleased]\n\nAnother feature"))
        (git project "commit" "-am" "Next feature")
        (let [refs (git remote "show-ref")
              tags (git project "show-ref" "--tags")
              result (release/prepare project)
              committed (:out (shell/sh "git" "show" "HEAD:README.md" :dir project))
              changelog (:out (shell/sh "git" "show" "HEAD:CHANGELOG.md" :dir project))]
          (is (= "0.1.4" (:version result)))
          (is (= "4" (git project "rev-list" "--count" "HEAD")))
          (is (= (:sha result) (git project "rev-parse" "HEAD")))
          (is (= (:version result) (:source (notes/select-notes changelog (:version result) false))))
          (is (= (readme/render readme-test/initial (:version result)) committed))
          (is (str/includes? committed "Use them only after this\nversion is published"))
          (is (str/includes? committed "Development only: clone and :local/root"))
          (is (not (str/includes? committed "0.1.2")))
          (is (= "CHANGELOG.md\nREADME.md"
                 (git project "diff-tree" "--no-commit-id" "--name-only" "-r" "HEAD")))
          (is (= "" (git project "status" "--porcelain")))
          (is (= tags (git project "show-ref" "--tags")))
          (is (= refs (git remote "show-ref"))))))))

(deftest post-commit-document-postconditions-test
  ;; Successful hooks must not hide dirty output or amended release coordinates.
  (doseq [[label commands message dirty?]
          [["worktree" "printf '\nHook output\n' >> README.md\n"
            "Release worktree or index changed" true]
           ["index" "printf '\nHook output\n' >> README.md\ngit add README.md\n"
            "Release worktree or index changed" true]
           ["amended coordinates"
            (str "sed 's/0.1.2/9.9.9/g' README.md > changed\nmv changed README.md\n"
                 "git add README.md\ngit -c core.hooksPath=/dev/null commit --amend --no-edit\n")
            "Release committed document changed" false]]]
    (testing label
      (with-project
        (fn [project _ hooks]
          (let [hook (io/file hooks "post-commit")]
            (spit hook (str "#!/bin/sh\nset -eu\n" commands))
            (.setExecutable hook true)
            (is (thrown-with-msg? clojure.lang.ExceptionInfo (re-pattern message)
                                  (release/prepare project)))
            (is (= "2" (git project "rev-list" "--count" "HEAD")))
            (is (= dirty? (not (str/blank? (git project "status" "--porcelain")))))
            (if dirty?
              (is (str/includes? (slurp (io/file project "README.md")) "Hook output"))
              (is (str/includes? (git project "show" "HEAD:README.md") "9.9.9")))
            (is (= "" (git project "tag")))
            (is (= "" (git project "remote")))))))))

(deftest post-commit-history-postconditions-test
  ;; Hooks cannot add paths or replace ancestry while preserving count and documents.
  (doseq [[label commands message]
          [["extra path"
            (str "printf 'Hook output\n' > unrelated.txt\ngit add unrelated.txt\n"
                 "git -c core.hooksPath=/dev/null commit --amend --no-edit\n")
            "Release changed paths differ"]
           ["replaced parent"
            (str "parent=$(printf 'Replacement root\n' | git commit-tree HEAD^: )\n"
                 "commit=$(printf 'Replacement release\n' | git commit-tree HEAD: -p \"$parent\")\n"
                 "git update-ref HEAD \"$commit\"\n")
            "Release history changed"]]]
    (testing label
      (with-project
        (fn [project _ hooks]
          (let [original-head (git project "rev-parse" "HEAD")
                hook (io/file hooks "post-commit")]
            (spit hook (str "#!/bin/sh\nset -eu\n" commands))
            (.setExecutable hook true)
            (is (thrown-with-msg? clojure.lang.ExceptionInfo (re-pattern message)
                                  (release/prepare project)))
            (is (= "2" (git project "rev-list" "--count" "HEAD")))
            (is (= "" (git project "status" "--porcelain")))
            (is (= (readme/render readme-test/initial "0.1.2")
                   (:out (shell/sh "git" "show" "HEAD:README.md" :dir project))))
            (is (str/includes? (git project "show" "HEAD:CHANGELOG.md") "[0.1.2]"))
            (if (= label "extra path")
              (do
                (is (= original-head (git project "rev-parse" "HEAD^")))
                (is (= "Hook output" (git project "show" "HEAD:unrelated.txt"))))
              (is (not= original-head (git project "rev-parse" "HEAD^"))))
            (is (= "" (git project "tag")))
            (is (= "" (git project "remote")))))))))
