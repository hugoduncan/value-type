---
title: Preserve release identity and test failure boundaries
status: active
category: release-engineering
tags: [git, versioning, release, integration-tests, github-actions]
related: [mementum/knowledge/testing-library-artifacts.md, mementum/knowledge/verifying-development-quality-checks.md, ramora/release-workflow.md]
depends-on: []
---

# Preserve release identity and test failure boundaries

Source: task `011-release-workflow`, its design, plan, steps and implementation
notes. Evidence: preparation/export `499aa5f`, deployment guards `0acef33`,
workflow `0589c16`, and count-guard test `8425b9a`.

## Commit-count versions bind notes and artifacts to one commit

The version is `0.1.<git rev-list --count HEAD>`. Complete history is part of
release correctness, not only a checkout performance choice. Both preparation
and notes export reject shallow history before file changes.

Preparation predicts one new commit, stamps that version, then checks the
actual count after committing. A successful Git command does not prove that
only one commit was made: a post-commit hook can create another commit.
Keep the resulting-count guard. A later commit also changes the build version;
previously prepared notes do not identify the new artifact.

The workflow checks out the full-history dispatch SHA, not moving master.
Notes, version, tag and build must refer to that selected commit. Preview may
use Unreleased notes only when the matching version section is absent; an
invalid or empty matching section must fail, not fall back.

## Exercise failures on both sides of the commit

A rejecting pre-commit hook tests failure before commit completion. It cannot
test a post-commit invariant. Use a disposable real Git repository and a
one-shot post-commit hook that makes an extra empty commit. Disable hooks for
the nested commit to prevent recursion.

The CLI test must assert a nonzero exit and the count-mismatch diagnostic,
retained commits and stamped notes, and unchanged tags and local bare-remote
refs. Removing the guard must make the test fail. Task 011 verified this
mutation; a success-path test alone had left the guard untested.

Isolate fixtures from inherited Git configuration, signing and hooks. Test
validation failures for unchanged input/output bytes. After a write or commit
failure, check the retained local state rather than expecting automatic reset.
These are different failure contracts.

## Test credential rejection without publication

Use real child processes for absent, empty and whitespace-only credentials.
Remove inherited environment values explicitly for the absent case. Preserve
a sentinel under `target/` and existing output bytes to prove rejection occurs
before build cleanup or output writes. Assert that diagnostics name the missing
variable but do not print the supplied secret. Test valid credentials through
the pure validator, without calling deployment.

## Publication is not a transaction

Only explicit typed boolean false permits publication:
`toJSON(inputs['dry-run']) == 'false'`. True, absent/null and string values
remain preview. Keep the same gate on each write step and its secrets.

The publication order is credential validation, tag/push, build/deploy, then
GitHub Release creation with the returned artifact paths. A failure can leave
remote state. Stop and require manual GitHub/Clojars inspection; do not add an
outer retry, rollback or automatic resume. Command success is not independent
verification of remote files.

Local process tests, Linux provisioning and actionlint do not prove hosted
permissions or publication. Task 011 established local behavior only; hosted
Actions and remote publication remained unverified.
