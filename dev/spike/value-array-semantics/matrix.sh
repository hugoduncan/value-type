#!/usr/bin/env bash
set -euo pipefail
out=${1:-target/value-array-semantics/matrix}
[[ ! -e "$out" ]] || { echo "Output already exists: $out" >&2; exit 1; }
mkdir -p "$out"
shasum -a 256 dev/spike/value-array-semantics/*.* dev/spike/value-array-semantics/support/*.clj > "$out/source-hashes.txt"
bash dev/spike/value-array-semantics/startup.sh "$out/startup"
bash dev/spike/value-array-semantics/semantics.sh "$out/semantics"
bb dev/spike/value-array-semantics/validate_matrix.clj "$out" > "$out/validation.edn"
shasum -a 256 dev/spike/value-array-semantics/*.* dev/spike/value-array-semantics/support/*.clj > "$out/source-hashes-after.txt"
cmp "$out/source-hashes.txt" "$out/source-hashes-after.txt"
