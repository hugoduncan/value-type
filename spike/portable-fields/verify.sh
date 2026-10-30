#!/usr/bin/env bash
# Historical experiment. It ran at commit 6318c36 and does not run against the
# current tree: `value_experiment.clj` calls the removed 3-arity
# `transform/->value-class` and installs a `Compiler/LOADER` proxy that current
# `defvaluetype` capture bypasses. This script runs in a temporary git worktree
# at that commit.
set -euo pipefail
commit=6318c36
repo=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
worktree=$(mktemp -d)/value-type-$commit
git -C "$repo" worktree add --detach "$worktree" "$commit" >/dev/null
trap 'git -C "$repo" worktree remove --force "$worktree"' EXIT
cd "$worktree"
baseline=${VALUE_TYPE_BASELINE_JAVA:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java}
valhalla=${VALUE_TYPE_VALHALLA_JAVA:-$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java}
paths='{:paths ["src" "spike/portable-fields"]}'
for java in "$baseline" "$valhalla"; do
  JAVA_CMD="$java" clojure -Sdeps "$paths" -M:fallback-test \
    --namespace portable-fields.experiment-test
done
mkdir -p target/portable-fields
JAVA_CMD="$baseline" clojure -Sdeps "$paths" -M -e \
  '(binding [*compile-path* "target/portable-fields"] (compile (quote portable-fields.fixture)) (compile (quote portable-fields.experiment-test)))'
classpath=$(JAVA_CMD="$baseline" clojure -Spath -M:fallback-test)
# The fresh consumer has compiled artifacts, but no experiment source path.
"$baseline" -cp "target/portable-fields:$classpath" clojure.main -m scry.cli \
  --namespace portable-fields.experiment-test
cljfmt check spike/portable-fields
clj-kondo --lint spike/portable-fields --config \
  '{:lint-as {portable-fields.experiment/checked-identity clojure.core/deftype
              portable-fields.value-experiment/checked-value clojure.core/deftype}}'
# Controlled generated-only value fields: no native annotation or probe override.
for execution in -Xmixed -Xint; do
  JAVA_CMD="$valhalla" clojure -Sdeps "$paths" -J--enable-preview -J"$execution" \
    -M:fallback-test --namespace portable-fields.value-experiment-test
done
