(ns value-type.forced-jit-runner
  "Babashka entry point for the forced-JIT runtime tier."
  (:refer-clojure :exclude [run!])
  (:require [babashka.process :as process]
            [clojure.string :as str]
            [value-type.forced-jit :as jit]
            [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts]))

(defn run!
  "Run only runtime tests, report duration and preserve the Scry exit status."
  []
  (let [environment (into {} (System/getenv))
        configuration (jit/configuration!
                       (facts/read-facts)
                       (executable/platform (System/getProperty "os.name")
                                            (System/getProperty "os.arch"))
                       environment
                       (executable/default-root (System/getProperty "user.home")))
        classpath (str/trim
                   (:out (process/shell {:out :string
                                         :extra-env {"JAVA_CMD" (:java configuration)}}
                                        "clojure" "-Srepro" "-Spath" "-A:test")))
        start (System/nanoTime)]
    (prn configuration)
    (println "Forced compilation applies only to the Scry parent; runtime children remain normal.")
    (let [result (apply process/shell {:continue true} (jit/command configuration classpath))]
      (prn {:exit (:exit result)
            :elapsed-seconds (/ (- (System/nanoTime) start) 1e9)})
      (System/exit (:exit result)))))
