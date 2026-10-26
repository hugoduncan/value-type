#!/usr/bin/env bash
set -euo pipefail
out=${1:-target/value-array-semantics/stores}
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
flags=(--enable-preview --add-exports=java.base/jdk.internal.value=ALL-UNNAMED)
case ${ARRAY_FLATTENING:-default} in
  default) ;;
  disabled) flags+=(-XX:-UseArrayFlattening) ;;
  *) echo "ARRAY_FLATTENING must be default or disabled" >&2; exit 1 ;;
esac
mkdir -p "$out/classes"
JAVA_CMD="$JDK/bin/java" clojure -Spath > "$out/classpath.txt"
"$JDK/bin/javac" -d "$out/classes" spike/value-array-semantics/ArrayAccess.java > "$out/compile.log" 2>&1
cmd=("$JDK/bin/java" "${flags[@]}" -cp "$out/classes:$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/stores.clj "$out/allocation.edn" "$out/stores.edn")
printf '%q ' "${cmd[@]}" > "$out/run.command"
printf '\n' >> "$out/run.command"
status=0
"${cmd[@]}" > "$out/run.log" 2>&1 || status=$?
echo "$status" > "$out/run.exit"
shasum -a 256 spike/value-array-semantics/{allocation.clj,support/allocation.clj,stores.clj,stores.sh,ArrayAccess.java} > "$out/hashes.txt"
exit "$status"
