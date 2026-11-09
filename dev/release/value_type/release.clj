(ns value-type.release
  "Local release preparation. Run from the repository root without concurrent edits."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [value-type.notes :as notes]
            [value-type.readme :as readme])
  (:import [java.time LocalDate ZoneOffset]))

(defn- git [directory & args]
  (let [{:keys [exit out err]} (apply shell/sh "git" (concat args [:dir directory]))]
    (when-not (zero? exit)
      (throw (ex-info (str "Git failed; inspect local state before retrying: " err)
                      {:exit exit :args args})))
    (str/trim out)))

(defn- commit-count [directory]
  (Long/parseLong (git directory "rev-list" "--count" "HEAD")))

(defn prepare
  "Stamp and commit README.md and CHANGELOG.md on clean master with full history.
   Failures after writing leave local changes for inspection. Return version and SHA."
  [directory]
  (when-not (= "master" (git directory "symbolic-ref" "--short" "HEAD"))
    (throw (ex-info "Preparation requires master" {})))
  (when-not (= "false" (git directory "rev-parse" "--is-shallow-repository"))
    (throw (ex-info "Preparation requires complete history" {})))
  (when-not (str/blank? (git directory "status" "--porcelain" "--untracked-files=all"))
    (throw (ex-info "Preparation requires a clean worktree and index" {})))
  (let [original-head (git directory "rev-parse" "HEAD")
        version (str "0.1." (inc (commit-count directory)))
        changelog (io/file directory "CHANGELOG.md")
        stamped (notes/stamp (slurp changelog) version (LocalDate/now ZoneOffset/UTC))
        readme-file (io/file directory "README.md")
        rendered (readme/render (slurp readme-file) version)]
    (spit changelog stamped)
    (spit readme-file rendered)
    (git directory "add" "--" "CHANGELOG.md" "README.md")
    (git directory "commit" "--only" "-m" (str "Release v" version) "--" "CHANGELOG.md" "README.md")
    (when-not (= version (str "0.1." (commit-count directory)))
      (throw (ex-info "Release commit count changed; inspect local state" {:version version})))
    (when-not (= original-head (git directory "show" "-s" "--format=%P" "HEAD"))
      (throw (ex-info "Release history changed; inspect local state" {:version version})))
    (let [paths (set (str/split (git directory "diff" "--name-only" "--no-renames" "-z"
                                     original-head "HEAD" "--") #"\u0000"))]
      (when-not (= #{"README.md" "CHANGELOG.md"} paths)
        (throw (ex-info "Release changed paths differ; inspect local state"
                        {:version version :paths paths}))))
    (when-not (str/blank? (git directory "status" "--porcelain" "--untracked-files=all"))
      (throw (ex-info "Release worktree or index changed; inspect local state" {:version version})))
    (doseq [[path expected] [["README.md" rendered] ["CHANGELOG.md" stamped]]]
      (let [{:keys [exit out]} (shell/sh "git" "show" (str "HEAD:" path) :dir directory)]
        (when-not (and (zero? exit) (= expected out))
          (throw (ex-info "Release committed document changed; inspect local state"
                          {:version version :path path})))))
    {:version version :sha (git directory "rev-parse" "HEAD")}))

(defn write-notes
  "Write validated HEAD notes and append GitHub outputs. Preview permits fallback.
   Output files should be outside target/, which packaging deletes."
  [directory notes-file github-output preview?]
  (when-not (= "false" (git directory "rev-parse" "--is-shallow-repository"))
    (throw (ex-info "Notes selection requires complete history" {})))
  (let [version (str "0.1." (commit-count directory))
        selected (notes/select-notes (slurp (io/file directory "CHANGELOG.md")) version preview?)
        result {:sha (git directory "rev-parse" "HEAD")
                :version version :tag (str "v" version)
                :source (:source selected)
                :preparation-required (:preparation-required? selected)}]
    (spit notes-file (:body selected))
    (spit github-output
          (apply str (mapv (fn [key] (str (name key) "=" (get result key) "\n"))
                           [:sha :version :tag :source :preparation-required]))
          :append true)
    result))

(defn -main
  "Prepare locally or export notes; never tag, push, or publish.
   Usage: prepare | notes NOTES_FILE GITHUB_OUTPUT [--preview]."
  [& args]
  (try
    (cond
      (= ["prepare"] (vec args)) (prn (prepare "."))
      (and (= "notes" (first args))
           (or (= 3 (count args))
               (and (= 4 (count args)) (= "--preview" (last args)))))
      (prn (write-notes "." (second args) (nth args 2) (= "--preview" (last args))))
      :else (throw (ex-info "Usage: prepare | notes NOTES_FILE GITHUB_OUTPUT [--preview]" {})))
    (finally (shutdown-agents))))
