#!/usr/bin/env bash
set -euo pipefail
out=${1:-target/value-array-semantics/discovery}
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
JAVA8=${JAVA8:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java}
mkdir -p "$out"
JAVA_CMD="$JDK/bin/java" clojure -Spath > "$out/classpath.txt"
cp "$JDK/release" "$out/jdk-release.txt"
"$JDK/bin/javap" jdk.internal.value.ValueClass > "$out/signatures.txt"
unzip -p "$JDK/lib/src.zip" java.base/jdk/internal/value/ValueClass.java > "$out/ValueClass.java"
shasum -a 256 "$JDK/lib/src.zip" spike/value-array-semantics/discover.* spike/value-array-semantics/support/discover.clj > "$out/hashes.txt"
run() {
  local name=$1 java=$2
  shift 2
  local cmd=("$java" "$@" -cp "$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/discover.clj "$out/$name.edn")
  printf '%q ' "${cmd[@]}" > "$out/$name.command"
  printf '\n' >> "$out/$name.command"
  "$java" -version > "$out/$name.version" 2>&1
  local status=0
  "${cmd[@]}" > "$out/$name.log" 2>&1 || status=$?
  echo "$status" > "$out/$name.exit"
  return "$status"
}
run baseline "$JDK/bin/java" --enable-preview --add-exports=java.base/jdk.internal.value=ALL-UNNAMED
run no-exports "$JDK/bin/java" --enable-preview
run no-preview "$JDK/bin/java" --add-exports=java.base/jdk.internal.value=ALL-UNNAMED
run neither "$JDK/bin/java"
run java8 "$JAVA8"
