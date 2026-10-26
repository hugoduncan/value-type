(ns value-type.notes
  "Shared changelog validation and pure release-note operations."
  (:require [clojure.string :as str])
  (:import [java.time LocalDate]))

(defn- reject [message]
  (throw (ex-info message {})))

(defn- heading [line]
  (if (= line "## [Unreleased]")
    {:version "Unreleased"}
    (if-let [[_ version date] (re-matches #"## \[(0\.1\.[1-9][0-9]*)\] - ([0-9]{4}-[0-9]{2}-[0-9]{2})" line)]
      (do (LocalDate/parse date)
          {:version version :date date})
      (reject (str "Invalid changelog heading: " line)))))

(defn parse
  "Validate the entire changelog. Return preamble and ordered sections.
   Section bodies retain their original text; Markdown is opaque."
  [text]
  (let [lines (str/split text #"\n" -1)
        starts (into [] (keep-indexed (fn [i line]
                                        (when (and (str/starts-with? line "##")
                                                   (not (str/starts-with? line "###")))
                                          i))) lines)
        sections (mapv (fn [start end]
                         (assoc (heading (nth lines start))
                                :body (str/join "\n" (subvec lines (inc start) end))))
                       starts (conj (subvec starts (min 1 (count starts))) (count lines)))
        versions (mapv :version sections)]
    (when-not (= "Unreleased" (first versions))
      (reject "Unreleased must be the first section"))
    (when-not (= (count versions) (count (set versions)))
      (reject "Duplicate changelog section"))
    (doseq [{:keys [version body]} sections]
      (when (and (not= version "Unreleased") (str/blank? body))
        (reject (str "Empty changelog section: " version))))
    {:preamble (str/join "\n" (subvec lines 0 (first starts)))
     :sections sections}))

(defn select-notes
  "Select validated version notes. Preview alone permits Unreleased fallback."
  [text version preview?]
  (let [{:keys [sections]} (parse text)
        matching (some #(when (= version (:version %)) %) sections)
        selected (or matching (when preview? (first sections)))]
    (when-not selected
      (reject (str "No notes for " version)))
    (when (str/blank? (:body selected))
      (reject "Selected notes are empty"))
    {:body (:body selected)
     :source (:version selected)
     :preparation-required? (nil? matching)}))

(defn stamp
  "Move Unreleased into version with an explicit LocalDate; preserve prior entries."
  [text version date]
  (let [{:keys [preamble sections]} (parse text)
        release-heading (str "## [" version "] - " date)
        _ (heading release-heading)
        body (:body (first sections))]
    (when (some #(= version (:version %)) sections)
      (reject (str "Version already exists: " version)))
    (when (str/blank? body)
      (reject "Unreleased notes are empty"))
    (str preamble "\n## [Unreleased]\n\n" release-heading "\n" body
         (apply str (mapv (fn [{:keys [version date body]}]
                            (str "\n## [" version "] - " date "\n" body))
                          (subvec sections 1))))))
