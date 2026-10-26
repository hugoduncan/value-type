(ns ^{:clj-kondo/config '{:linters {:unresolved-namespace {:exclude [allocation]}}}} allocation)

;; Command-line entry point; helper loading does not run probes.
(load-file "spike/value-array-semantics/support/allocation.clj")
(allocation/run-probe! (first *command-line-args*))
