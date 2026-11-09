(ns value-type.forced-jit
  "Facts-selected, parent-only forced compilation configuration."
  (:require [value-type.jvm-executable :as executable]
            [value-type.jvm-facts :as facts]))

(def compilation-flags
  ["-Xcomp" "-Xbatch" "-XX:-TieredCompilation"])

(defn check-environment!
  "Reject inherited JVM options, including whitespace-only values."
  [environment]
  (doseq [variable ["JAVA_TOOL_OPTIONS" "JDK_JAVA_OPTIONS" "_JAVA_OPTIONS"]]
    (when (seq (get environment variable))
      (throw (ex-info "Forced JIT rejects inherited JVM options"
                      {:variable variable}))))
  environment)

(defn configuration!
  "Select and verify the candidate EA and its parent-only launch flags."
  [jvm-facts platform environment root]
  (check-environment! environment)
  (let [selection (facts/select jvm-facts :valhalla platform :preview-export)]
    {:java (executable/resolve! selection environment root)
     :build (:build selection)
     :parent-flags (into (:jvm-opts selection)
                         (concat ["-Dvalue-type.value-classes=auto"] compilation-flags))
     :child-flags ["--enable-preview"]}))

(defn command
  "Build a direct Java command so compilation flags cannot reach children."
  [{:keys [java parent-flags]} classpath]
  (into [java] (concat parent-flags
                       ["-cp" classpath "clojure.main" "-m" "scry.cli"
                        "--dir" "test/runtime"])))
