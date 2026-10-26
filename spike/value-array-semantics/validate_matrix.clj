(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [semantic-checks]}}}} validate-matrix
  (:require [babashka.process :as process]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(load-file "spike/value-array-semantics/semantic_checks.clj")

(defn read-result [path]
  (edn/read-string (slurp path)))

(defn validate-process!
  "Run an existing evidence validator and require successful completion."
  [script root]
  (let [{:keys [exit out err]} @(process/process ["bb" (str "spike/value-array-semantics/" script) root]
                                                 {:out :string :err :string})]
    (assert (zero? exit) (str script " failed: " err))
    out))

(defn validate-environment!
  "Require the EA3 runtime and flags for a matrix layout."
  [layout environment]
  (let [flags (set (:jvm-flags environment))]
    (assert (= "27-jep401ea3+1-1" (:java-version environment)))
    (assert (contains? flags "--enable-preview"))
    (assert (contains? flags "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED"))
    (assert (= (= "disabled" layout) (contains? flags "-XX:-UseArrayFlattening")))))

(defn semantic-record
  "Check retained process coverage and collect path-independent observations."
  [root layout suite file expected-count]
  (let [directory (str root "/semantics/" layout "/" suite)
        result (read-result (str directory "/" file ".edn"))
        environment (:environment (read-result (str directory "/allocation.edn")))
        cases (:cases result)]
    (assert (= "0" (str/trim (slurp (str directory "/run.exit")))))
    (assert (= expected-count (count cases) (count (set (mapv :id cases)))))
    (assert (every? #(contains? #{:pass :rejection} (:status %)) cases))
    ((case file
       "allocation" semantic-checks/allocation!
       "stores" semantic-checks/stores!
       "diagnostics" semantic-checks/diagnostics!) cases)
    (validate-environment! layout environment)
    {:layout layout :suite suite :phase file :cases cases}))

(let [root (first *command-line-args*)
      startup (edn/read-string (validate-process! "validate_startup.clj" (str root "/startup")))
      comparison (edn/read-string (validate-process! "compare_semantics.clj" (str root "/semantics")))
      semantics (into [] (for [layout ["default" "disabled"]
                               [suite file n] [["stores" "allocation" 59]
                                               ["stores" "stores" 120]
                                               ["diagnostics" "allocation" 59]
                                               ["diagnostics" "diagnostics" 69]]]
                           (semantic-record root layout suite file n)))
      isolated (mapv (fn [layout]
                       (let [directory (str root "/semantics/" layout "/diagnostics")]
                         (validate-process! "validate_isolated.clj" directory)
                         (doseq [record (:cases (read-result (str directory "/isolated.edn")))]
                           (validate-environment! layout (:environment record)))
                         {:layout layout
                          :cases (mapv #(select-keys % [:id :inputs :status :signal :exit :value])
                                       (:cases (read-result (str directory "/isolated.edn"))))}))
                     ["default" "disabled"])]
  ;; Retain unresolved startup observations even when another process covers the
  ;; baseline semantics. A failed fixture is not a native allocator rejection.
  (prn {:status :pass :startup startup :comparison comparison
        :semantics semantics :isolated isolated}))
