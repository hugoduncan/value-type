(ns value-type.quality
  "Shared scope and process entry points for development checks."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [clojure.string :as str]))

(def maintained-paths
  ["src" "test" "test-build" "test-fallback" "bench" "build.clj"
   "bb" "test-quickdoc" "bb.edn" "deps.edn" ".clj-kondo/hooks"])

(def dependency-aliases
  [:test :build :build-test :fallback-test :bench :object-size
   :quickdoc :quickdoc-test])

(defn- project-paths!
  []
  (let [missing (filterv (complement fs/exists?) maintained-paths)]
    (when (seq missing)
      (throw (ex-info (str "Missing maintained scope paths: " (str/join ", " missing))
                      {:missing-paths missing})))
    maintained-paths))

(defn format!
  "Check or fix explicit paths, or the maintained project paths."
  [args]
  (let [[operation & paths] args]
    (when-not (#{"check" "fix"} operation)
      (throw (ex-info "Usage: bb clj-fmt check|fix [PATHS...]" {})))
    (apply shell "mise" "exec" "--" "cljfmt" operation
           "--file-pattern" "\\.(?:clj[csx]?|bb|edn)$"
           (if (seq paths) paths (project-paths!)))))

(defn copy-config!
  "Resolve each dependency basis separately and import its lint config."
  []
  (let [classpaths (mapv (fn [alias]
                           (str/trim
                            (:out (shell {:out :string}
                                         "clojure" "-Srepro" "-Spath"
                                         (str "-A" alias)))))
                         dependency-aliases)]
    (shell "mise" "exec" "--" "clj-kondo"
           "--repro" "--config-dir" ".clj-kondo"
           "--lint" (str/join (System/getProperty "path.separator") classpaths)
           "--dependencies" "--copy-configs" "--skip-lint")))

(defn lint!
  "Prepare dependency configuration and fail on code warnings or errors."
  []
  (let [paths (project-paths!)]
    (copy-config!)
    (apply shell "mise" "exec" "--" "clj-kondo"
           "--repro" "--config-dir" ".clj-kondo"
           "--fail-level" "warning" "--lint"
           paths)))
