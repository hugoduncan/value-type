;; Add spike/ to the runtime classpath. Producer and consumer use separate JVMs.
(ns fallback.transfer-check
  (:require [clojure.edn :as edn]
            [fallback.phase-guard :as guard]))

(defn cause-chain
  "Returns exception class names from outer exception to root cause."
  [failure]
  (loop [current failure result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result (.getName (class current))))
      result)))

(defn class-present?
  "Checks class visibility through the compiler loader."
  [class-name]
  (try
    (Class/forName class-name false (clojure.lang.RT/baseLoader))
    true
    (catch ClassNotFoundException _ false)))

(let [[mode path] *command-line-args*]
  (case mode
    "produce"
    (let [form '(fallback.phase-guard/fallback-type Transferred [x])
          expansion (macroexpand form)]
      ;; The do barrier preserves the analysis guard for a receiving compiler.
      (assert (= expansion (macroexpand-1 form)))
      (spit path (pr-str expansion))
      (prn {:produced expansion}))

    "consume"
    (let [expansion (edn/read-string (slurp path))
          operations {:eval #(eval expansion)
                      :source #(load-string (pr-str expansion))}]
      (if (= "error" guard/policy)
        (let [results
              (into {}
                    (map (fn [[phase operation]]
                           [phase
                            (mapv (fn [_]
                                    (let [chain (try
                                                  (operation)
                                                  []
                                                  (catch Throwable failure
                                                    (cause-chain failure)))]
                                      (assert (= ["clojure.lang.Compiler$CompilerException"
                                                  "java.lang.Error"]
                                                 chain)
                                              (pr-str chain))
                                      (assert (nil? (ns-resolve *ns* 'Transferred)))
                                      (assert (not (class-present?
                                                    "fallback.transfer_check.Transferred")))
                                      chain))
                                  [0 1])]))
                    operations)]
          (prn {:policy guard/policy :results results :class-present false}))
        (let [type-class (eval expansion)
              factory (ns-resolve *ns* '->Transferred)
              first-instance (factory 42)
              second-instance (factory 42)]
          (assert (= type-class (class first-instance)))
          (assert (= 42 (.-x first-instance)))
          (assert (not= first-instance second-instance))
          (prn {:policy guard/policy :class-present true :identity true}))))))
