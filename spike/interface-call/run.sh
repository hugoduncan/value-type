#!/usr/bin/env bash
set -euo pipefail
# Run from the repository root. Keep timing JVMs sequential.
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
out=${1:-target/interface-call}
mkdir -p "$out/classes" "$out/results"
"$JDK/bin/javac" --enable-preview --release 27 -d "$out/classes" spike/interface-call/InterfaceControl.java
JAVA_CMD="$JDK/bin/java" clojure -Spath -M:bench > "$out/classpath.txt"
cp="$(cat "$out/classpath.txt"):$out/classes"
"$JDK/bin/java" -version 2> "$out/java-version.txt"
for n in 1024 2048; do
  for trial in 1 2 3 4; do
    if (( trial % 2 )); then order=dt-first; modes='clojure java';
    else order=vt-first; modes='java clojure'; fi
    for mode in $modes; do
      name="$mode-$n-$trial-$order"
      echo "START $name $(date -u +%FT%TZ)"
      "$JDK/bin/java" --enable-preview -XX:+UnlockExperimentalVMOptions \
        '-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume' \
        -cp "$cp" clojure.main spike/interface-call/run.clj \
        "$mode" "$n" "$order" "$out/results/$name.edn" \
        > "$out/results/$name.log" 2>&1
      echo "DONE $name $(date -u +%FT%TZ)"
    done
  done
done
