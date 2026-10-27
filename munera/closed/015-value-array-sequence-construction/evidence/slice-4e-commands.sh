#!/bin/bash
set -euo pipefail
export JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
E=munera/open/015-value-array-sequence-construction/evidence
{ uname -a; "$JAVA_CMD" -version; } > "$E/slice-4e-environment.txt" 2>&1
for row in library enabled disabled; do
  flags=(-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED)
  selectors=(--dir test)
  scope='{:dirs ["test"]}'
  if [ "$row" != library ]; then
    scope="{:namespaces '[value-type.array-sequence-test value-type.array-test value-type.array-layout-test]}"
    selectors=(--namespace value-type.array-sequence-test --namespace value-type.array-test --namespace value-type.array-layout-test)
    if [ "$row" = enabled ]; then flags+=(-J-XX:+UseArrayFlattening); else flags+=(-J-XX:-UseArrayFlattening); fi
  fi
  clojure "${flags[@]}" -M:test -e "(require '[scry.core :as s]) (let [r (s/run $scope)] (spit \"$E/slice-4e-$row.edn\" (pr-str {:java-version (System/getProperty \"java.runtime.version\") :clojure-version (clojure-version) :os (System/getProperty \"os.name\") :arch (System/getProperty \"os.arch\") :flags (vec (.getInputArguments (java.lang.management.ManagementFactory/getRuntimeMXBean))) :result r})) (prn (:summary r)) (shutdown-agents) (System/exit (if (:pass? r) 0 1)))"
  clojure "${flags[@]}" -M:test -m scry.cli "${selectors[@]}"
  test -z "$(find .scry-results -name '*.edn' -print)"
  echo "$row: structured and CLI exit zero; no failure EDN files"
done
echo COMPLETE
