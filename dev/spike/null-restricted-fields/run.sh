#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
: "${JAVA_CMD:?Set JAVA_CMD to the EA3 java executable}"
export JAVA_CMD
extra='{:paths ["src" "dev/spike/null-restricted-fields/src"]}'
probe=dev/spike/null-restricted-fields
classes=$(mktemp -d "${TMPDIR:-/tmp}/field-probe.XXXXXX")
trap 'rm -rf "$classes"' EXIT

run() { timeout 60 "$@"; }
check() {
  run clojure -J--enable-preview "$@" -Sdeps "$extra" -M "$probe/check.clj" diagnostics
}
exports=(-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
         -J--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED)

# Core semantics need neither internal-package export.
run clojure -J--enable-preview -Sdeps "$extra" -M "$probe/check.clj"
check "${exports[@]}"
check -J-XX:-UseFieldFlattening "${exports[@]}"
check -J-Xint "${exports[@]}"

for row in 'hint unused' \
           'annotation java.lang.IncompatibleClassChangeError' \
           'string java.lang.IncompatibleClassChangeError' \
           'primitive java.lang.ClassFormatError' \
           'recursive java.lang.ClassCircularityError'; do
  read -r mode expected <<< "$row"
  run clojure -J--enable-preview -Sdeps "$extra" -M "$probe/boundary.clj" "$mode" "$expected"
done
run clojure -Sdeps "$extra" -M "$probe/boundary.clj" annotation java.lang.ClassFormatError

FIELD_PROBE_CLASSES="$classes" run clojure -J--enable-preview -Sdeps "$extra" -M -e \
  '(binding [*compile-path* (System/getenv "FIELD_PROBE_CLASSES")] (compile (quote field-probe.fixtures)))'
classpath=$(clojure -Spath)
# Deliberately omit the spike source path from the consumer.
run "$JAVA_CMD" --enable-preview -cp "$classes:$classpath" clojure.main "$probe/check.clj"
echo 'PASS: null-restricted field experiment'
