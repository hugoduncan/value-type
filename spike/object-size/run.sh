#!/usr/bin/env bash
# Run from the repository root. Requires the JEP 401 EA3 JDK.
set -euo pipefail
export JAVA_CMD=${JAVA_CMD:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java}
jdk=$(cd "$(dirname "$JAVA_CMD")/.." && pwd)
out=target/object-size-investigation
mkdir -p "$out/classes" "$out/default" "$out/no-nullable-non-atomic"
cp "$jdk/release" "$out/jdk-release.txt"
"$JAVA_CMD" --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version \
  > "$out/preview-flags.log" 2>&1
"$jdk/bin/javac" --enable-preview --release 27 -d "$out/classes" spike/object-size/SizeControl.java
printf 'Premain-Class: investigation.SizeControl\n\n' > "$out/manifest.mf"
"$jdk/bin/jar" --create --file "$out/size-agent.jar" --manifest "$out/manifest.mf" -C "$out/classes" .
agent_file=$(mktemp)
trap 'rm -f "$agent_file"' EXIT
CRITERIUM_AGENT_PATH_FILE="$agent_file" clojure -M:object-size -e '
(require (quote [criterium.agent :as agent]))
(spit (System/getenv "CRITERIUM_AGENT_PATH_FILE") (agent/extract-agent!))'
agent_path=$(cat "$agent_file")
[[ "$agent_path" = /* && -f "$agent_path" ]]
for mode in default no-nullable-non-atomic no-nullable-layouts; do
  mkdir -p "$out/$mode"
  flags=(--enable-preview)
  clj_flags=(-J--enable-preview)
  if [[ "$mode" != default ]]; then
    flags+=(-XX:-UseNullableNonAtomicValueFlattening)
    clj_flags+=(-J-XX:-UseNullableNonAtomicValueFlattening)
  fi
  if [[ "$mode" = no-nullable-layouts ]]; then
    flags+=(-XX:-UseNullableValueFlattening)
    clj_flags+=(-J-XX:-UseNullableValueFlattening)
  fi
  "$JAVA_CMD" --enable-preview "${flags[@]}" \
    -XX:+UnlockDiagnosticVMOptions -XX:+PrintFieldLayout \
    -javaagent:"$out/size-agent.jar" -cp "$out/classes" investigation.SizeControl \
    > "$out/$mode/java.log" 2>&1
  clojure "${clj_flags[@]}" "-J-javaagent:$out/size-agent.jar" \
    -J-XX:+UnlockDiagnosticVMOptions -J-XX:+PrintFieldLayout \
    -Sdeps "{:paths [\"src\" \"$out/classes\"]}" \
    -M:object-size spike/object-size/references.clj > "$out/$mode/references.log" 2>&1
  awk '/^Layout of class object_size_references\/(Identity|Value)Ref[1-3]@/ {keep=1}
       keep {print} keep && /^---$/ {keep=0} /^REFERENCE-SIZE / {print}' \
    "$out/$mode/references.log" > "$out/$mode/reference-layout-and-sizes.txt"
  # Layout run has no measurement agent attached.
  clojure "${clj_flags[@]}" -J-XX:+UnlockDiagnosticVMOptions -J-XX:+PrintFieldLayout \
    -M:object-size -e "(require 'value-type.object-size)" > "$out/$mode/clojure-layout.log" 2>&1
  mkdir -p target/object-size
  clojure "${clj_flags[@]}" "-J-agentpath:$agent_path" -M:object-size -m value-type.object-size \
    > "$out/$mode/stdout.log" 2> "$out/$mode/stderr.log"
  cp target/object-size/{raw,results}.edn "$out/$mode/"
  # Preserve complete target-class blocks, excluding compiler stubs and helpers.
  awk '/^Layout of class value_type\/object_size\/(Identity|Value)[0-3]@/ {keep=1}
       keep {print} keep && /^---$/ {keep=0}' \
    "$out/$mode/clojure-layout.log" > "$out/$mode/clojure-layout.txt"
  awk '/^Layout of class investigation\/SizeControl\$(Identity|Value)(Ref)?[0-3]@/ {keep=1}
       keep {print} keep && /^---$/ {keep=0} /^SIZE / {print}' \
    "$out/$mode/java.log" > "$out/$mode/java-layout-and-sizes.txt"
  cat "$out/$mode/stdout.log"
  grep '^SIZE ' "$out/$mode/java.log"
done
