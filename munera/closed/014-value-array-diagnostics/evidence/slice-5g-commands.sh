#!/bin/bash
set -e
export VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
export VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
E=munera/open/014-value-array-diagnostics/evidence
for row in baseline preview-off preview-on; do
  flags=()
  export JAVA_CMD=$VALUE_TYPE_VALHALLA_JAVA
  if [ "$row" = baseline ]; then export JAVA_CMD=$VALUE_TYPE_BASELINE_JAVA; fi
  if [ "$row" = preview-on ]; then flags=(-J--enable-preview); fi
  clojure "${flags[@]}" -Sdeps '{:aliases {:verify {:extra-paths ["test-fallback"] :extra-deps {org.hugoduncan/scry {:mvn/version "0.1.61"}}}}}' -M:verify -e "(require '[scry.core :as s]) (let [r (s/run {:namespaces '[value-type.runtime-test value-type.aot-test value-type.transfer-test]})] (spit \"$E/slice-5g-$row.edn\" (pr-str r)) (prn (:summary r)) (shutdown-agents) (System/exit (if (:pass? r) 0 1)))"
  clojure "${flags[@]}" -M:fallback-test --namespace value-type.runtime-test --namespace value-type.aot-test --namespace value-type.transfer-test
  test -z "$(find .scry-results -name '*.edn' -print)"
done
export JAVA_CMD=$VALUE_TYPE_VALHALLA_JAVA
clojure -M:build-test -e "(require '[scry.core :as s]) (let [r (s/run {:namespaces '[build-test]})] (spit \"$E/slice-5g-build.edn\" (pr-str r)) (prn (:summary r)) (shutdown-agents) (System/exit (if (:pass? r) 0 1)))"
clojure -M:build-test -m scry.cli --namespace build-test
test -z "$(find .scry-results -name '*.edn' -print)"
echo COMPLETE
