;; Compile the fixture under silent policy, then run source-free consumers.
(ns fallback.runtime-check
  (:require [clojure.java.io :as io]
            [fallback.phase-guard :as guard]))

(let [[mode output] *command-line-args*]
  (case mode
    "compile"
    (binding [*compile-path* output]
      (assert (= "silent" guard/policy))
      (compile 'fallback.runtime-fixture)
      (prn {:compiled true}))

    "invoke"
    (do
      (assert (nil? (io/resource "fallback/runtime_fixture.clj")))
      ;; Loading succeeds: the guard is inside a function, not at top level.
      (require 'fallback.runtime-fixture)
      (let [operation (ns-resolve 'fallback.runtime-fixture 'define-type)
            results
            (mapv
             (fn [_]
               (if (= "error" guard/policy)
                 (let [failure (try
                                 (operation)
                                 nil
                                 (catch Throwable failure failure))]
                   (assert (= Error (class failure)) (pr-str failure))
                   (assert (= "Fallback rejected by application policy"
                              (.getMessage ^Throwable failure)))
                   {:error (.getName (class failure))})
                 (let [type-class (operation)
                       factory (ns-resolve 'fallback.runtime-fixture
                                           '->RuntimeIdentity)
                       instance (factory 42)]
                   (assert (= type-class (class instance)))
                   (assert (= 42 (.-x instance)))
                   (assert (not= instance (factory 42)))
                   {:identity true})))
             [0 1])]
        (prn {:policy guard/policy :loaded true :calls results})))))
