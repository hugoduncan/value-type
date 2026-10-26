#!/usr/bin/env bash
set -euo pipefail
out=${1:-target/value-array-semantics/semantics}
# Refuse old output: stale results must not count as current observations.
if [[ -e "$out" ]]; then
  echo "Output already exists: $out" >&2
  exit 1
fi
mkdir -p "$out"
for layout in default disabled; do
  ARRAY_FLATTENING="$layout" bash spike/value-array-semantics/stores.sh "$out/$layout/stores"
  ARRAY_FLATTENING="$layout" bash spike/value-array-semantics/diagnostics.sh "$out/$layout/diagnostics"
done
bb spike/value-array-semantics/compare_semantics.clj "$out" > "$out/comparison.log" 2>&1
shasum -a 256 spike/value-array-semantics/*.* spike/value-array-semantics/support/*.clj > "$out/source-hashes.txt"
