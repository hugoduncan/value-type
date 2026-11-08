#!/usr/bin/env bash
# Measure the start cost of the emitted value-class guard in AOT output.
# Argument: the guard commit; the baseline is its parent. Run from the
# repository root. Output: target/aot-startup/ (not committed).
set -euo pipefail
guard=$(git rev-parse "${1:?usage: run.sh <guard-commit>}")
base=$(git rev-parse "$guard^")
JDK=${JDK:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home}
java="$JDK/bin/java"
out=$(pwd -P)/target/aot-startup
# Remove registrations left by older runs before deleting their directories.
for name in guard base; do
  tree="$out/trees/$name"
  if git worktree list --porcelain | grep -Fx "worktree $tree" > /dev/null; then
    git worktree remove --force "$tree"
  fi
done
created=""
cleanup() {
  status=$?
  trap - EXIT
  for name in $created; do
    git worktree remove --force "$out/trees/$name" || true
  done
  exit "$status"
}
trap cleanup EXIT
rm -rf "$out"
mkdir -p "$out/trees" "$out/fixture/startup" "$out/lib" "$out/results"
cat > "$out/fixture/startup/fixture.clj" <<'EOF'
(ns startup.fixture
  (:require [value-type.core :refer [defvaluetype]]))

(defvaluetype Point [x])
EOF
jars=$(clojure -Spath | tr ':' '\n' | grep -v '^src$' | paste -sd: -)
for name in guard base; do
  rev=${!name}
  tree="$out/trees/$name"
  git worktree add --detach "$tree" "$rev" > /dev/null
  created="$created $name"
  mkdir -p "$out/$name"
  "$java" --enable-preview -cp "$jars:$tree/src:$out/fixture" clojure.main -e \
    "(binding [*compile-path* \"$out/$name\"] (compile 'startup.fixture))"
done
cp -R "$out/guard/value_type" "$out/lib/"
rm -rf "$out/guard/value_type" "$out/base/value_type"
for name in $created; do git worktree remove --force "$out/trees/$name"; done
created=""
run() {
  "$java" --enable-preview -cp "$jars:$out/lib:$out/$1" clojure.main -e \
    "(require 'startup.fixture)
     (println (- (System/currentTimeMillis)
                 (.getStartTime (java.lang.management.ManagementFactory/getRuntimeMXBean))))"
}
for i in 1 2; do run guard > /dev/null; run base > /dev/null; done
for i in $(seq 10); do
  run guard >> "$out/results/guard.txt"
  run base >> "$out/results/base.txt"
done
for i in $(seq 10); do
  "$java" --enable-preview -cp "$jars:$out/lib" clojure.main -e \
    "(require 'value-type.impl.capability)
     (let [t (System/nanoTime)]
       (value-type.impl.capability/capability)
       (println (/ (- (System/nanoTime) t) 1e6)))" >> "$out/results/capability.txt"
done
stats() {
  sort -g "$1" | awk '{a[NR]=$1} END {printf "median %s min %s max %s\n",
    (NR % 2 ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2), a[1], a[NR]}'
}
for f in guard base capability; do echo "$f: $(stats "$out/results/$f.txt")"; done \
  | tee "$out/results/summary.txt"
