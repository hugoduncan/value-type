# Release procedure

[Development guide](../development.md)

Run commands from the repository root with the pinned development tools.
Use Java 21 for release tooling. The runtime checks use the explicit JVMs in the
[runtime guide](runtime.md). Local builds need no publication credentials.

## Prepare and inspect

Use a clean `master` checkout with complete Git history. Commit factual notes
under exactly `## [Unreleased]` in `CHANGELOG.md` before preparation.
Dated headings must be `## [0.1.N] - YYYY-MM-DD`, with a positive N without
leading zeros and a valid date. Do not repeat versions or leave dated bodies
empty. The whole document is validated; comments and subheadings count as notes.

```sh
clojure -M:release -m value-type.release prepare
git show --stat HEAD
git show HEAD -- CHANGELOG.md
git status --short
```

Preparation moves nonempty Unreleased notes into the next commit-count version
with the UTC date. It leaves empty Unreleased and commits only CHANGELOG.md as
`Release vVERSION`. It checks the resulting count. It does not tag, push,
dispatch or publish. A failed local commit leaves changes for inspection;
there is no automatic reset. Inspect and resolve the cause before another attempt.

Push the inspected commit separately, when authorised:

```sh
git push origin master
```

Do not add commits between preparation and publication. Each commit changes the
version. If master advances, prepare matching notes for the new selected commit.

## Preview

In GitHub Actions, select the Release workflow on `master`. Keep `dry-run` true
(the default). The workflow checks out the dispatch SHA with complete history;
later changes to master do not change that checkout. Other branches are rejected.

Both modes run quality, tooling, release, value-path, fallback and isolated JAR
consumer tests before notes selection. Preview builds without Clojars secrets or
release writes. Its summary reports SHA, version, tag, notes source and whether
preparation is required. Nonempty Unreleased is a preview fallback only when no
matching version exists. Invalid or empty matching notes always fail.

For local notes inspection without publication:

```sh
notes_dir=$(mktemp -d)
clojure -M:release -m value-type.release notes \
  "$notes_dir/notes.md" "$notes_dir/outputs" --preview
cat "$notes_dir/outputs" "$notes_dir/notes.md"
clojure -T:build jar
```

Keep notes outside `target/`: builds delete that directory. Notes export needs
complete history. Local preview export does not run the workflow's checks.

## Publish

Only an authorised maintainer should dispatch with `dry-run` set to false.
Only the explicit boolean false permits publication. Inspect a successful preview
for the intended SHA first. Publication requires matching dated notes.

Repository secrets `CLOJARS_USERNAME` and `CLOJARS_PASSWORD` must contain the
Clojars account name and deployment token with permission for
`org.hugoduncan/value-type`. Configure them separately through approved repository
administration. Never put credentials in commands, files or logs. The workflow
uses its GitHub token for tag and Release writes; the job needs contents-write
permission. Secrets are limited to the steps that need them.

After checks and credential validation, publication creates and pushes `vVERSION`
at HEAD without force, builds and deploys through stock deps-deploy 0.2.2, then
creates the GitHub Release. It uses the returned JAR and POM paths as assets and
the selected notes as its body. It does not update master or edit existing Releases.

Build tooling exposes `clojure -T:build:deploy deploy` for deployment. This command
validates credentials that are not blank before building, builds HEAD and returns version,
JAR and POM paths. It does not perform workflow checks or create tags or Releases;
do not use it as an automatic recovery command.

## Failure and recovery

Publication is not atomic. A failure can leave a tag, Clojars files or a GitHub
Release. The job stops and reports failed steps. Inspect the job log, GitHub tags,
Releases and assets, and Clojars before deciding on another attempt. Preserve
Scry failure artifacts for test failures. Do not blindly rerun, force a tag,
clobber assets or assume a failed upload wrote nothing. There is no automatic
rollback, retry wrapper or resume procedure. Stock tools can retry internally.
Command success is not independent verification of remote files.

Local tests and workflow lint do not prove hosted permissions or publication.
Task acceptance does not authorise remote writes, secret configuration or a real
release. Hosted workflow execution and publication remain unverified.
