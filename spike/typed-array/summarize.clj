#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns typed-array-summary
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(defn median
  [xs]
  (nth (vec (sort xs)) (quot (count xs) 2)))

(defn validate-report!
  [{:keys [n configuration results] :as report}]
  (assert (= #{:identity :value :restricted} (set (map :kind results))))
  (assert (= 3 (count results)))
  (doseq [{:keys [kind flat? component-value? null-restricted? atomic?
                 array-bytes storage-bytes allocated-bytes-per-build sum
                 ns-per-element]} results]
    (let [flat-expected? (and (= kind :restricted) (= configuration "default"))
          payload (case kind :identity 24 :value 32 :restricted (if flat-expected? 8 32))
          expected-array (+ 16 (* n (if flat-expected? 8 4)))
          expected-storage (if flat-expected? expected-array (+ expected-array (* n payload)))]
      ;; These checks apply to the recorded VM setup, not arbitrary JVMs.
      (assert (= flat-expected? flat?))
      (assert (= (not= kind :identity) component-value?))
      (assert (= (= kind :restricted) null-restricted?))
      (assert atomic?)
      (assert (= (quot (* n (dec n)) 2) sum))
      (assert (= expected-array array-bytes))
      (assert (= expected-storage storage-bytes))
      (assert (<= storage-bytes allocated-bytes-per-build (+ storage-bytes 1024)))
      (assert (pos? ns-per-element))))
  report)

(let [[directory output] *command-line-args*
      files (filterv #(.endsWith (.getName ^java.io.File %) ".edn")
                     (vec (file-seq (io/file directory))))
      reports (mapv #(validate-report! (edn/read-string (slurp %))) files)
      groups (sort-by key (group-by (juxt :configuration :n) reports))
      rows (into []
                 (mapcat
                  (fn [[[configuration n] runs]]
                    (assert (= #{0 1 2} (set (map :rotation runs))))
                    (assert (= 3 (count runs)))
                    (mapv
                     (fn [kind]
                       (let [samples (mapv (fn [run]
                                             (first (filter #(= kind (:kind %)) (:results run))))
                                           (sort-by :rotation runs))]
                         {:configuration configuration :n n :kind kind
                          :storage-bytes (mapv :storage-bytes samples)
                          :allocated-bytes-per-build (mapv :allocated-bytes-per-build samples)
                          :ns-per-element (mapv :ns-per-element samples)
                          :median-ns-per-element (median (mapv :ns-per-element samples))}))
                     [:identity :value :restricted])))
                 groups)
      summary {:run-count (count reports) :rows rows}]
  (assert (seq reports))
  (with-open [writer (io/writer output)] (pprint/pprint summary writer))
  (prn summary))
