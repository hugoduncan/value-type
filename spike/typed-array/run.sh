#!/usr/bin/env bash
# Run timing JVMs sequentially from the repository root.
set -euo pipefail
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
out=${1:-target/typed-array}
mkdir -p "$out/classes" "$out/results"
"$JDK/bin/javac" --add-exports java.base/jdk.internal.value=ALL-UNNAMED \
  -d "$out/classes" spike/typed-array/Probe.java
printf 'Premain-Class: investigation.Probe\n\n' > "$out/manifest.mf"
"$JDK/bin/jar" --create --file "$out/probe.jar" --manifest "$out/manifest.mf" -C "$out/classes" .
JAVA_CMD="$JDK/bin/java" clojure -Spath -M:bench > "$out/classpath.txt"
cp="$(cat "$out/classpath.txt"):$out/classes"
cp "$JDK/release" "$out/jdk-release.txt"
"$JDK/bin/java" --enable-preview -XX:+UnlockDiagnosticVMOptions -XX:+PrintFlagsFinal -version \
  > "$out/vm-flags.log" 2>&1
for n in ${SIZES:-65536 1048576}; do
  for trial in ${TRIALS:-0 1 2}; do
    if (( trial % 2 )); then modes='no-flattening default'; else modes='default no-flattening'; fi
    for mode in $modes; do
      flags=(--enable-preview)
      if [[ "$mode" = no-flattening ]]; then flags+=(-XX:-UseArrayFlattening); fi
      name="$mode-$n-$trial"
      echo "START $name $(date -u +%FT%TZ)"
      "$JDK/bin/java" "${flags[@]}" -Xms512m -Xmx2g \
        --add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
        -javaagent:"$out/probe.jar" -XX:+UnlockExperimentalVMOptions \
        '-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume' \
        -cp "$cp" clojure.main spike/typed-array/run.clj "$n" "$trial" "$mode" \
        "$out/results/$name.edn" > "$out/results/$name.log" 2>&1
      echo "DONE $name $(date -u +%FT%TZ)"
    done
  done
done
