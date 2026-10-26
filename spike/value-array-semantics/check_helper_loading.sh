#!/usr/bin/env bash
# Real JVM checks: loading helpers must neither run probes nor write evidence.
set -euo pipefail
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
JAVA8=${JAVA8:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java}
classpath=$(JAVA_CMD="$JDK/bin/java" clojure -Spath)
repo=$PWD
classpath=$(bb -e '(require '"'"'[clojure.string :as str]) (println (str/join ":" (map #(.getCanonicalPath (java.io.File. %)) (str/split (first *command-line-args*) #":"))))' "$classpath")
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
# Resolve helper dependencies from the repository, but catch output in a clean cwd.
ln -s "$repo/spike" "$work/spike"
cd "$work"
check() {
  local java=$1 helper=$2
  shift 2
  for arguments in nil '["unexpected.edn"]'; do
    "$java" "$@" -Dvalue-type.fallback=error -cp "$classpath" clojure.main -e \
      "(binding [*command-line-args* $arguments]
         (assert (= \"\" (with-out-str (load-file \"$helper\")))))"
    [[ $(find . -type f | wc -l | tr -d ' ') == 0 ]]
  done
}
check "$JAVA8" spike/value-array-semantics/support/discover.clj
check "$JDK/bin/java" spike/value-array-semantics/support/discover.clj
check "$JDK/bin/java" spike/value-array-semantics/support/allocation.clj \
  --enable-preview --add-exports=java.base/jdk.internal.value=ALL-UNNAMED
echo 'Passed: helper loading with absent and sentinel CLI arguments (6 JVMs)'
