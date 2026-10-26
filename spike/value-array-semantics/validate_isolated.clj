(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [isolated-evidence]}}}} validate-isolated
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(load-file "spike/value-array-semantics/isolated_evidence.clj")

(let [root (first *command-line-args*)
      retained (edn/read-string (slurp (io/file root "isolated.edn")))]
  (assert (= (isolated-evidence/record root) retained)
          "Isolated records differ from raw process evidence")
  (println "Validated six isolated diagnostic outcomes"))
