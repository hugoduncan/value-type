#!/bin/bash
set -euo pipefail
export VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
export VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
E=munera/open/015-value-array-sequence-construction/evidence
{
  uname -a
  "$VALUE_TYPE_BASELINE_JAVA" -version
  "$VALUE_TYPE_VALHALLA_JAVA" -version
} > "$E/slice-4f-environment.txt" 2>&1
for row in baseline preview-off preview-on; do
  flags=()
  export JAVA_CMD=$VALUE_TYPE_VALHALLA_JAVA
  if [ "$row" = baseline ]; then export JAVA_CMD=$VALUE_TYPE_BASELINE_JAVA; fi
  if [ "$row" = preview-on ]; then flags=(-J--enable-preview); fi
  clojure ${flags[@]+"${flags[@]}"} -Sdeps '{:aliases {:structured {:main-opts []}}}' -M:fallback-test:structured -e "(require '[scry.core :as s]) (let [r (s/run {:dirs [\"test-fallback\"]})] (spit \"$E/slice-4f-$row.edn\" (pr-str r)) (prn (:summary r)) (shutdown-agents) (System/exit (if (:pass? r) 0 1)))"
  clojure ${flags[@]+"${flags[@]}"} -M:fallback-test
  test -z "$(find .scry-results -name '*.edn' -print)"
  echo "$row: structured and CLI exit zero; no failure EDN files"
done
echo COMPLETE
