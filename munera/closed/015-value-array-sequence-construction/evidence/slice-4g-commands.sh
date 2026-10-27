#!/bin/bash
set -euo pipefail
export JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
export VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
export VALUE_TYPE_VALHALLA_JAVA=$JAVA_CMD
E=munera/open/015-value-array-sequence-construction/evidence
{ uname -a; "$JAVA_CMD" -version; "$VALUE_TYPE_BASELINE_JAVA" -version; } > "$E/slice-4g-environment.txt" 2>&1
clojure -M:build-test -e "(require '[scry.core :as s]) (let [r (s/run {:namespaces '[build-test]})] (spit \"$E/slice-4g-build.edn\" (pr-str {:java-version (System/getProperty \"java.runtime.version\") :clojure-version (clojure-version) :os (System/getProperty \"os.name\") :arch (System/getProperty \"os.arch\") :flags (vec (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))) :result r})) (prn (:summary r)) (shutdown-agents) (System/exit (if (:pass? r) 0 1)))"
clojure -M:build-test -m scry.cli --dir test-build
test -z "$(find .scry-results -name '*.edn' -print)"
echo 'build: structured and CLI exit zero; no failure EDN files'
echo COMPLETE
