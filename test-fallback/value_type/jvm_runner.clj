(ns value-type.jvm-runner
  "Baseline-safe process support for isolated runtime tests."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.util List)
           (java.util.concurrent TimeUnit)))

(defn run-jvm
  "Run a Clojure source string in an explicitly selected JVM and classpath.

   Supply :java, :classpath and :form. Optional :jvm-opts defaults to [],
   :policy sets value-type.fallback, and :timeout-ms defaults to 120000.
   No preview flag or parent classpath is added. Return :exit, :out, :err
   and :result (the last stdout line as EDN on exit 0). Timeout uses
   :exit :timeout. Invalid success EDN throws. Temporary output files are
   removed and a timed-out child is stopped before returning."
  [{:keys [java classpath form jvm-opts policy timeout-ms]
    :or {jvm-opts [] timeout-ms 120000}}]
  (let [dir (.toFile (Files/createTempDirectory
                      "vt-jvm-" (make-array FileAttribute 0)))
        out-file (io/file dir "stdout")
        err-file (io/file dir "stderr")
        command (into [java]
                      (concat jvm-opts
                              (when policy [(str "-Dvalue-type.fallback=" policy)])
                              ["-cp" classpath "clojure.main" "-e" form]))]
    (try
      (let [process (.start (doto (ProcessBuilder. ^List command)
                              (.redirectOutput out-file)
                              (.redirectError err-file)))]
        (try
          (let [finished? (.waitFor process timeout-ms TimeUnit/MILLISECONDS)]
            (when-not finished?
              (.destroyForcibly process)
              (.waitFor process))
            (let [out (slurp out-file)
                  err (slurp err-file)
                  exit (if finished? (.exitValue process) :timeout)]
              {:exit exit :out out :err err
               :result (when (= 0 exit)
                         (some-> out str/split-lines last edn/read-string))}))
          (finally
            (when (.isAlive process)
              (.destroyForcibly process)
              (.waitFor process)))))
      (finally
        (run! #(Files/deleteIfExists (.toPath %)) [out-file err-file dir])))))
