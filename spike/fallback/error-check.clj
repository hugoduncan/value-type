;; Run on the runtime-only classpath, with or without preview.
(ns fallback.error-check)

(defn reject!
  "Rejects each experimental fallback attempt with a Java Error."
  []
  (throw (Error. "Fallback rejected by application policy")))

(defmacro rejected-type
  "Rejects before returning a deftype expansion."
  [type-name]
  (reject!)
  `(deftype ~type-name []))

(defn cause-chain
  "Returns exception class names, from the outer exception to its cause."
  [failure]
  (loop [current failure result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result (.getName (class current))))
      result)))

(defn failure-chain
  "Runs a real compiler operation and returns its failure chain."
  [operation]
  (try
    (operation)
    (throw (ex-info "Operation unexpectedly succeeded" {}))
    (catch Throwable failure
      (let [chain (cause-chain failure)]
        (assert (= "java.lang.Error" (peek chain)) (pr-str chain))
        chain))))

(defn class-present?
  "Checks whether the compiler can resolve the rejected type."
  [class-name]
  (try
    (Class/forName class-name false (clojure.lang.RT/baseLoader))
    true
    (catch ClassNotFoundException _ false)))

;; Each operation is repeated after the first Error has been caught.
(let [form '(fallback.error-check/rejected-type Rejected)
      operations {:macroexpand #(macroexpand form)
                  :macroexpand-1 #(macroexpand-1 form)
                  :eval #(eval form)
                  :source #(load-string
                            "(fallback.error-check/rejected-type Rejected)")
                  :runtime #(eval '(fallback.error-check/reject!))}
      results (into {}
                    (map (fn [[phase operation]]
                           [phase [(failure-chain operation)
                                   (failure-chain operation)]]))
                    operations)
      compiler-chain ["clojure.lang.Compiler$CompilerException"
                      "java.lang.Error"]]
  (doseq [phase [:macroexpand :macroexpand-1 :eval :source]]
    (assert (= [compiler-chain compiler-chain] (get results phase))
            (pr-str [phase (get results phase)])))
  (assert (= [["java.lang.Error"] ["java.lang.Error"]]
             (:runtime results)))
  (assert (nil? (ns-resolve *ns* 'Rejected)))
  (assert (not (class-present? "fallback.error_check.Rejected")))
  (prn {:java-version (System/getProperty "java.runtime.version")
        :results results
        :rejected-class-present false}))
