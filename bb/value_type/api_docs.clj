(ns value-type.api-docs
  "Generate the committed public API reference."
  (:require
   [clojure.java.io :as io]
   [quickdoc.api :as quickdoc])
  (:import
   (java.nio.file Files)
   (java.nio.charset StandardCharsets)
   (java.util Arrays)))

(def output-path "doc/API.md")

(def intro
  (str "# value-type API reference\n\n"
       "This file is generated from source docstrings with Quickdoc. "
       "Do not edit `doc/API.md` by hand.\n\n"
       "See the [README](../README.md) for installation, examples and tested JVMs.\n\n"
       "See the [usage guide](usage.md) for detailed behavior and error handling.\n\n"
       "Regenerate from the repository root with `bb api-docs`. "
       "Check without writing with `bb api-docs --check`.\n\n"))

(def quickdoc-options
  {:outfile false
   :github/repo "https://github.com/hugoduncan/value-type"
   :git/branch "master"
   :source-paths ["src/value_type/core.clj" "src/value_type/array.clj"]
   :overrides {'value-type.core {'check-value-class {:no-doc true}}}})

(defn generated-markdown
  "Return the public API reference as deterministic Markdown."
  []
  (str intro (:markdown (quickdoc/quickdoc quickdoc-options))))

(defn write-doc!
  "Write the generated reference to path as UTF-8."
  [path]
  (io/make-parents path)
  (spit path (generated-markdown) :encoding "UTF-8"))

(defn current-doc?
  "Return true when path contains exactly the generated UTF-8 bytes."
  [path]
  (let [file (io/file path)
        expected (.getBytes ^String (generated-markdown) StandardCharsets/UTF_8)]
    (and (.isFile file)
         (Arrays/equals expected (Files/readAllBytes (.toPath file))))))

(defn -main
  "Generate the reference, or check it without writing with --check."
  [& args]
  (case (vec args)
    [] (write-doc! output-path)
    ["--check"]
    (when-not (current-doc? output-path)
      (binding [*out* *err*]
        (println "doc/API.md is missing or stale. Run `bb api-docs` and commit the result."))
      (System/exit 1))
    (do
      (binding [*out* *err*]
        (println "Usage: bb api-docs [--check]"))
      (System/exit 2))))
