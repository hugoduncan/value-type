(ns value-type.prose
  "Vale checks for the maintained prose scope."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [cheshire.core :as json]))

(def prose-paths ["README.md" "AGENTS.md" "doc/API.md"])

(defn parse-alerts
  "Parse Vale JSON and reject unexpected result or severity data."
  [output]
  (let [result (json/parse-string output true)]
    (when-not (and (map? result)
                   (every? vector? (vals result)))
      (throw (ex-info "Invalid Vale JSON result" {})))
    (let [alerts (into [] cat (vals result))]
      (when-not (every? #(and (map? %)
                              (#{"suggestion" "warning" "error"} (:Severity %)))
                        alerts)
        (throw (ex-info "Invalid Vale alert severity" {})))
      alerts)))

(defn check!
  "Sync project styles, report all alerts and fail on warnings or errors."
  [args]
  (when (seq args)
    (throw (ex-info "Usage: bb vale" {})))
  (doseq [path prose-paths]
    (when-not (fs/regular-file? path)
      (throw (ex-info "Missing Vale input" {:path path}))))
  (shell "vale" "--config=.vale.ini" "--no-global" "sync")
  (let [output (:out (apply shell {:out :string}
                            "vale" "--config=.vale.ini" "--no-global"
                            "--minAlertLevel=suggestion" "--output=JSON" "--no-exit"
                            prose-paths))
        alerts (parse-alerts output)]
    (println output)
    (when (some #(#{"warning" "error"} (:Severity %)) alerts)
      (throw (ex-info "Vale found warnings or errors" {})))))
