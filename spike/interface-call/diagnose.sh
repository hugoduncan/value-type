#!/usr/bin/env bash
set -euo pipefail
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
out=${1:-target/interface-call}
cp="$(cat "$out/classpath.txt"):$out/classes"
for mode in clojure java; do
  "$JDK/bin/java" --enable-preview -XX:+UnlockExperimentalVMOptions \
    '-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume' \
    -XX:+UnlockDiagnosticVMOptions -XX:+LogCompilation \
    "-XX:LogFile=$out/$mode-compilation.xml" \
    '-XX:CompileCommand=print,value_type.bench$interface_dt::invokeStatic' \
    '-XX:CompileCommand=print,value_type.bench$interface_vt::invokeStatic' \
    '-XX:CompileCommand=print,investigation.InterfaceControl::identityPass' \
    '-XX:CompileCommand=print,investigation.InterfaceControl::valuePass' \
    -cp "$cp" clojure.main spike/interface-call/run.clj \
    "$mode" 1024 dt-first "$out/$mode-diagnostic.edn" \
    > "$out/$mode-assembly.log" 2>&1
done
