(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [discover]}}}} discover)

;; Java 8-safe command-line entry point.
(load-file "spike/value-array-semantics/support/discover.clj")
(discover/run-probe! (first *command-line-args*))
