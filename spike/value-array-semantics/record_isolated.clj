(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [isolated-evidence]}}}} record-isolated
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]))

(load-file "spike/value-array-semantics/isolated_evidence.clj")

(let [root (first *command-line-args*)]
  (spit (io/file root "isolated.edn")
        (with-out-str (pprint/pprint (isolated-evidence/record root)))))
