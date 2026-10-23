#!/usr/bin/env bash
# Explicit native-agent integration checks; run from the repository root.
set -euo pipefail
root=$PWD
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
ln -s "$root/deps.edn" "$work/deps.edn"
for dir in src bench test; do ln -s "$root/$dir" "$work/$dir"; done
cd "$work"
mkdir -p target/object-size
printf keep >target/object-size/keep.txt
for file in results.edn raw.edn stdout.log stderr.log; do
  printf stale >"target/object-size/$file"
done
if JAVA_CMD="$work/missing-java" bash bench/object-size.sh; then
  echo 'Setup failure returned success' >&2; exit 1
fi
[[ ! -e target/object-size/results.edn && ! -e target/object-size/raw.edn ]]
[[ $(cat target/object-size/keep.txt) == keep ]]
! grep -q stale target/object-size/{stdout,stderr}.log
# A real measurement entry without the required agent must fail.
if clojure -M:object-size -m value-type.object-size >"$work/no-agent.log" 2>&1; then
  echo 'Measurement without agent returned success' >&2; exit 1
fi
[[ ! -e target/object-size/results.edn ]]
bash bench/object-size.sh
[[ -s target/object-size/results.edn && -s target/object-size/raw.edn ]]
OBJECT_SIZE_EVIDENCE_DIR="$work/target/object-size" \
  clojure -M:test:object-size -m scry.cli \
  --namespace value-type.object-size-evidence-test
[[ -z $(find .scry-results -name '*.edn' -print) ]]
[[ $(cat target/object-size/keep.txt) == keep ]]
echo 'Object-size integration checks passed'
