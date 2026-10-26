#!/usr/bin/env bash
# Exercise CI command failures with real tools in an isolated Git checkout.
set -euo pipefail
root=$(git rev-parse --show-toplevel)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git archive HEAD | tar -x -C "$tmp"
cp "$root/bb/value_type/quality.clj" "$tmp/bb/value_type/"
cd "$tmp"
export MISE_TRUSTED_CONFIG_PATHS="$tmp"
git init -q
printf '/probe.log\n' >> .git/info/exclude
git add .
git -c user.name=Test -c user.email=test@example.invalid commit -qm baseline

expect_failure() {
  local label=$1
  shift
  # Include the injected defect in the comparison baseline, including deletions.
  git add -A
  local before
  before=$(git write-tree)
  if mise exec -- "$@" > "$tmp/probe.log" 2>&1; then
    cat "$tmp/probe.log"
    echo "Expected failure: $label" >&2
    exit 1
  fi
  git diff --exit-code
  test "$before" = "$(git write-tree)"
  echo "Verified failure and tracked-file preservation: $label"
}
reset_probe() {
  git reset --hard -q HEAD
}

# Start with a passing baseline so setup errors cannot stand in for defects.
mise exec -- bb clj-kondo:copy-config
mise exec -- bb clj-fmt:check
mise exec -- bb clj-kondo:lint
mise exec -- bb vale
mise exec -- bb api-docs --check
mise exec -- clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc
git diff --exit-code HEAD --

# Required directories and files must not silently disappear from project scope.
for path in test-build build.clj dev/release test-release; do
  rm -rf "$path"
  for task in clj-fmt:check clj-fmt:fix clj-kondo:lint; do
    expect_failure "missing-scope-$path-$task" bb "$task"
    grep -q 'Missing maintained scope paths:' "$tmp/probe.log"
    grep -Fq "$path" "$tmp/probe.log"
  done
  # Explicit formatting does not require the complete project scope.
  mise exec -- bb clj-fmt check src
  reset_probe
done

printf '(ns ci-probe)\n(defn probe []\n(+ 1 2))\n' > test/ci_probe.clj
expect_failure formatting bb clj-fmt:check
mise exec -- bb clj-fmt fix test/ci_probe.clj
mise exec -- bb clj-fmt check test/ci_probe.clj
reset_probe

printf '(ns ci-probe)\n(missing-ci-symbol)\n' > test/ci_probe.clj
expect_failure lint bb clj-kondo:lint
grep -q missing-ci-symbol "$tmp/probe.log"
reset_probe

# Valid code with only an unused-binding warning must still block CI.
printf '(ns ci-probe)\n(defn probe [unused] 1)\n' > test/ci_probe.clj
mise exec -- clj-kondo --repro --config-dir .clj-kondo --fail-level error \
  --config '{:output {:format :edn}}' --lint test/ci_probe.clj > "$tmp/lint.edn"
mise exec -- bb -e '(let [result (read-string (slurp "lint.edn"))]
  (assert (zero? (get-in result [:summary :error])))
  (assert (pos? (get-in result [:summary :warning]))))'
rm "$tmp/lint.edn"
expect_failure warning-only-lint bb clj-kondo:lint
grep -q 'warning:.*unused' "$tmp/probe.log"
reset_probe

printf '\nThis is definately incorrect.\n' >> README.md
expect_failure prose bb vale
grep -qi definately "$tmp/probe.log"
reset_probe

printf '\nStale output.\n' >> doc/API.md
expect_failure stale-api bb api-docs --check
reset_probe
rm doc/API.md
expect_failure missing-api bb api-docs --check
reset_probe

# Remove a required notice from the generator; keep its contract tests unchanged.
mise exec -- bb -e '(require (quote [clojure.string :as str]))
  (let [path "bb/value_type/api_docs.clj"
        original (slurp path)
        defective (str/replace original "Do not edit" "Edit")]
    (assert (not= original defective))
    (spit path defective))'
expect_failure focused-tests clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc
# Inspect assertion data, not a runner-specific artifact filename.
mise exec -- bb -e '(require (quote [babashka.fs :as fs]))
  (assert (some #(clojure.string/includes? (slurp (str %)) "Do not edit")
                (fs/glob ".scry-results" "*.edn")))'
reset_probe

# Invalid project configuration must fail, not silently skip prose checking.
printf '\n[*.md]\nBasedOnStyles = MissingQualityStyle\n' > .vale.ini
expect_failure setup bb vale
reset_probe
echo 'CI failure probes passed.'
