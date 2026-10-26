#!/usr/bin/env bash
set -euo pipefail
out=${1:-target/value-array-semantics/diagnostics}
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
flags=(--enable-preview --add-exports=java.base/jdk.internal.value=ALL-UNNAMED)
case ${ARRAY_FLATTENING:-default} in
  default) ;;
  disabled) flags+=(-XX:-UseArrayFlattening) ;;
  *) echo "ARRAY_FLATTENING must be default or disabled" >&2; exit 1 ;;
esac
mkdir -p "$out/classes"
JAVA_CMD="$JDK/bin/java" clojure -Spath > "$out/classpath.txt"
cmd=("$JDK/bin/java" "${flags[@]}" -cp "$out/classes:$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/diagnostics.clj "$out/allocation.edn" "$out/diagnostics.edn")
printf '%q ' "${cmd[@]}" > "$out/run.command"
printf '\n' >> "$out/run.command"
status=0
"${cmd[@]}" > "$out/run.log" 2>&1 || status=$?
echo "$status" > "$out/run.exit"
shasum -a 256 spike/value-array-semantics/{allocation.clj,support/allocation.clj,diagnostics.clj,diagnostics.sh} > "$out/hashes.txt"
if [[ "$status" != 0 ]]; then exit "$status"; fi
for input in null non-array; do
  for query in isFlatArray isNullRestrictedArray isAtomicArray; do
    dir="$out/$input-$query"
    mkdir -p "$dir"
    isolated=("$JDK/bin/java" "-XX:ErrorFile=$dir/hs_err.log" "${flags[@]}" -cp "$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/diagnostics.clj "$dir/allocation.edn" "$dir/result.edn" "$input" "$query")
    printf '%q ' "${isolated[@]}" > "$dir/run.command"
    printf '\n' >> "$dir/run.command"
    status=0
    "${isolated[@]}" > "$dir/run.log" 2>&1 || status=$?
    echo "$status" > "$dir/run.exit"
    if [[ "$input" == null ]]; then
      [[ "$status" == 134 && -s "$dir/hs_err.log" ]]
      grep -q 'SIGSEGV' "$dir/hs_err.log"
    else
      [[ "$status" == 0 && -s "$dir/result.edn" ]]
    fi
  done
done
"$JDK/bin/java" -cp "$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/record_isolated.clj "$out"
"$JDK/bin/java" -cp "$(cat "$out/classpath.txt")" clojure.main spike/value-array-semantics/validate_isolated.clj "$out" > "$out/validation.log" 2>&1
shasum -a 256 spike/value-array-semantics/{validate_isolated,record_isolated,isolated_evidence}.clj >> "$out/hashes.txt"
