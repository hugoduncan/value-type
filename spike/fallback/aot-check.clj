;; Add spike/ and the temporary source/output directory to the classpath.
(ns fallback.aot-check
  (:require [clojure.java.io :as io]
            [fallback.phase-guard :as guard]))

(defn cause-chain
  "Returns exception class names from outer exception to root cause."
  [failure]
  (loop [current failure result []]
    (if current
      (recur (.getCause ^Throwable current)
             (conj result (.getName (class current))))
      result)))

(let [[mode directory] *command-line-args*
      fixture 'fallback.aot-fixture
      class-file (io/file directory "fallback/aot_fixture/AotIdentity.class")]
  (case mode
    "compile"
    (binding [*compile-path* directory]
      (if (= "error" guard/policy)
        (let [chains
              (mapv (fn [_]
                      (let [chain (try
                                    (compile fixture)
                                    []
                                    (catch Throwable failure
                                      (cause-chain failure)))]
                        (assert (= ["clojure.lang.Compiler$CompilerException"
                                    "java.lang.Error"] chain)
                                (pr-str chain))
                        (assert (not (.exists class-file)))
                        (assert (nil? (ns-resolve fixture 'AotIdentity)))
                        (assert (try
                                  (Class/forName "fallback.aot_fixture.AotIdentity"
                                                 false
                                                 (clojure.lang.RT/baseLoader))
                                  false
                                  (catch ClassNotFoundException _ true)))
                        chain))
                    [0 1])]
          (prn {:compiled false :policy guard/policy :chains chains
                :rejected-class-present false}))
        (do
          (compile fixture)
          (assert (.isFile class-file))
          (prn {:compiled true :policy guard/policy}))))

    "load"
    (do
      ;; No consumer source may mask a missing or ineffective AOT guard.
      (assert (nil? (io/resource "fallback/aot_fixture.clj")))
      (if (= "error" guard/policy)
        (let [chains
              (mapv (fn [_]
                      (let [chain (try
                                    (require fixture :reload)
                                    []
                                    (catch Throwable failure
                                      (cause-chain failure)))]
                        (assert (if (zero? _)
                                  (= ["java.lang.Error"] chain)
                                  (contains? #{["java.lang.NoClassDefFoundError"]
                                               ["java.lang.NoClassDefFoundError"
                                                "java.lang.ExceptionInInitializerError"]}
                                             chain))
                                (pr-str chain))
                        chain))
                    [0 1])]
          (prn {:policy guard/policy :chains chains}))
        (do
          (require fixture)
          (let [factory (ns-resolve fixture '->AotIdentity)
                first-instance (factory 42)
                second-instance (factory 42)]
            (assert (= 42 (.-x first-instance)))
            (assert (not= first-instance second-instance))
            (prn {:policy guard/policy :identity true :field-value 42})))))))
