#!/usr/bin/env bash
# Narrow integration tests using real Git, pre-commit, cljfmt and clj-kondo.
set -euo pipefail
root=$(git rev-parse --show-toplevel)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
git archive HEAD | tar -x -C "$tmp"
cp "$root/.pre-commit-config.yaml" "$tmp/"
mkdir -p "$tmp/.pre-commit-hooks"
cp "$root/.pre-commit-hooks/"*.sh "$tmp/.pre-commit-hooks/"
cd "$tmp"
export MISE_TRUSTED_CONFIG_PATHS="$tmp"
git init -q
git add .
git -c user.name=Test -c user.email=test@example.invalid commit -qm baseline
mise exec -- pre-commit validate-config

run_hook() {
  mise exec -- pre-commit run "$@" > "$tmp/hook.log" 2>&1
}
expect_failure() {
  if run_hook "$@"; then
    cat "$tmp/hook.log"
    echo 'Expected hook failure' >&2
    exit 1
  fi
}
index_tree() { git write-tree; }

# Clean maintained files pass both hooks.
run_hook --all-files || { cat "$tmp/hook.log"; exit 1; }
git diff --exit-code

# Formatter edits block the commit without changing the staged version.
printf '(ns hook-probe)\n(defn probe []\n(+ 1 2))\n' > test/hook_probe.clj
git add test/hook_probe.clj
before=$(index_tree)
expect_failure cljfmt-fix
test "$before" = "$(index_tree)"
git diff --quiet -- test/hook_probe.clj && exit 1
mise exec -- bb clj-fmt check test/hook_probe.clj

# A clean staged file and a separate unstaged hunk survive stash/restore.
git add test/hook_probe.clj
printf '\n;; unstaged note\n' >> test/hook_probe.clj
printf '\nUnrelated local edit.\n' >> README.md
cp test/hook_probe.clj "$tmp/probe.expected"
cp README.md "$tmp/readme.expected"
before=$(index_tree)
run_hook cljfmt-fix || { cat "$tmp/hook.log"; exit 1; }
test "$before" = "$(index_tree)"
cmp test/hook_probe.clj "$tmp/probe.expected"
cmp README.md "$tmp/readme.expected"

# Conflicting unstaged edits must also survive a formatter failure.
printf '(ns hook-probe)\n(defn probe []\n(+ 1 2))\n' > test/hook_probe.clj
git add test/hook_probe.clj
printf '(ns hook-probe)\n(defn probe []\n(+ 1 3))\n' > test/hook_probe.clj
cp test/hook_probe.clj "$tmp/probe.expected"
before=$(index_tree)
expect_failure cljfmt-fix
test "$before" = "$(index_tree)"
cmp test/hook_probe.clj "$tmp/probe.expected"
cmp README.md "$tmp/readme.expected"

# Real lint errors fail without modifying staged or working content.
printf '(ns hook-probe)\n(missing-hook-symbol)\n' > test/hook_probe.clj
git add test/hook_probe.clj
cp test/hook_probe.clj "$tmp/probe.expected"
before=$(index_tree)
expect_failure clj-kondo-lint
grep -q 'missing-hook-symbol' "$tmp/hook.log"
test "$before" = "$(index_tree)"
cmp test/hook_probe.clj "$tmp/probe.expected"
cmp README.md "$tmp/readme.expected"

# Historical and generated paths do not trigger either hook.
for path in spike/excluded.clj target/excluded.clj .clj-kondo/imports/excluded.clj; do
  run_hook --files "$path"
  test "$(grep -c 'Skipped' "$tmp/hook.log")" = 2
done
# Owned lint config triggers lint even when no source file is selected.
expect_failure clj-kondo-lint --files .clj-kondo/config.edn
grep -q 'missing-hook-symbol' "$tmp/hook.log"
# A warning alone must block the lint hook without staging or rewriting files.
printf '(ns hook-probe)\n(defn probe [unused] 1)\n' > test/hook_probe.clj
git add test/hook_probe.clj
cp test/hook_probe.clj "$tmp/probe.expected"
before=$(index_tree)
mise exec -- clj-kondo --repro --config-dir .clj-kondo --fail-level error \
  --config '{:output {:format :edn}}' --lint test/hook_probe.clj > "$tmp/lint.edn"
mise exec -- bb -e '(let [result (read-string (slurp "lint.edn"))]
  (assert (zero? (get-in result [:summary :error])))
  (assert (pos? (get-in result [:summary :warning]))))'
expect_failure clj-kondo-lint
grep -q 'warning:.*unused' "$tmp/hook.log"
test "$before" = "$(index_tree)"
cmp test/hook_probe.clj "$tmp/probe.expected"
cmp README.md "$tmp/readme.expected"
echo 'Hook integration checks passed.'
