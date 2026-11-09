(ns out-of-tier-test
  (:require [clojure.test :refer [deftest is]]))

(deftest excluded-tier-test
  ;; Broad discovery must be visible even when another selected test fails.
  (spit "out-of-tier-executed" "executed")
  (is false "Forced-JIT discovery must exclude other test tiers"))
