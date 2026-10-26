# 011 Release workflow

## Intent and reference

Provide a local release preparation command and a manual release workflow for
`org.hugoduncan/value-type`. Reuse the implementation completed by closed-record
[task 016](../../../../../closed-record/closed-record-master/munera/closed/016-simple-release-workflow/design.md),
not a new release framework.

The reference repository is
`~/projects/hugoduncan/closed-record/closed-record-master`, at commit
`ffe2ad4e4e5e1b9f66ab814d72989ca60fe584d3`. Its task, code, tests, workflow and
release documentation define the reuse baseline. Adapt project identity and
verification to value-type. Do not inherit closed-record task 015, its cleanup
work, or its Clojure-version matrix.

## Context and ownership

The existing build creates a source-only EPL-2.0 JAR with version
`0.1.<git rev-list --count HEAD>`. Preserve library behaviour, runtime dependencies,
packaging and version rules. Build tooling owns packaging and deployment; small
release tooling owns changelog validation and local preparation; Git and GitHub
CLI own tag and GitHub Release publication. All release dependencies remain
outside the runtime artifact.

## Required behaviour

### Build and deployment

Retain credential-free local JAR builds. Deployment checks nonblank
`CLOJARS_USERNAME` and `CLOJARS_PASSWORD` before building, builds HEAD, calls stock
`slipset/deps-deploy` 0.2.2 once for that JAR/POM, and returns version and artifact
paths. The GitHub assets use those returned paths. Rebuilding after verification
is permitted, as in the reference. Never print credentials.

### Changelog and preparation

Create factual Unreleased notes without invented past releases. Preserve the
reference's shared, line-based validation contract:

- A section starts in column one with `##`, but not `###`. Only exact
  `## [Unreleased]` and `## [VERSION] - YYYY-MM-DD` lines are valid, without
  trailing spaces. VERSION is `0.1.N`, with a positive decimal N and no leading
  zeros. Dates must be valid calendar dates.
- Require exactly one Unreleased section before all dated sections. Zero dated
  sections are valid. Ignore preamble. Bodies end at the next section or EOF;
  fences and comments do not hide headings. Body Markdown is opaque.
- Nonempty means at least one non-whitespace character. Comments and subheadings
  count. Reject repeated Unreleased or version headings, regardless of date.
- Validate the entire document. Every dated body must be nonempty. Unreleased
  can be empty unless selected. No date or version ordering is required.

Preparation requires clean master and complete history. It moves nonempty
Unreleased notes to `0.1.<HEAD count + 1>` with the UTC date, leaves empty
Unreleased, preserves prior entries, and commits only CHANGELOG.md as
`Release vVERSION`. Reject invalid notes or an existing next version before
modification. Check the resulting commit-count version. Never tag, push,
dispatch or publish. Leave partial local failures for inspection without reset.

Publication selects the section matching the checked-out commit's version.
Preview may use nonempty Unreleased only when that section is absent, and must
report preparation required. Invalid or empty matching notes never permit
fallback. Notes export requires complete history.

### Manual workflow

Use one job and one full-history checkout of the master dispatch SHA. Reject
other branches. Later master advancement must not change the selected commit.
Use only `workflow_dispatch`, with boolean `dry-run` defaulting to true. Only
explicit typed false permits publication. Preview builds and reports SHA,
version, tag, notes source and preparation status without release writes or
Clojars secrets.

Use Ubuntu, existing development tool/action conventions, a finite timeout, and
one release concurrency group with cancellation disabled. Disable persisted
checkout credentials. Keep the reference's single contents-write job and
step-scoped secrets; trust maintainer-controlled master code.

Before publication, pass value-type quality checks, tooling tests, value-path
runtime tests, the Java 8/Valhalla preview-off/preview-on fallback matrix, isolated
JAR consumer tests on both JVM paths, and release tests. Select JVMs explicitly;
Temurin 21 alone cannot verify value classes. Preserve Scry failure artifacts.
Do not run benchmarks.

After checks and matching notes: validate credentials, create and push
`vVERSION` at HEAD without force, build/deploy, then use ordinary
`gh release create --verify-tag --notes-file` with explicit repository, title,
and the deployed JAR/POM. Keep notes outside `target/`, which builds delete.
Do not update master, generate notes, edit existing Releases or clobber assets.

Stop after failure. Add no outer retries, rollback, automatic resume, remote
absence proof or custom transport. Accept stock tool retries and cleanup.
Publication is not atomic. Report the failed step and require manual GitHub and
Clojars inspection before retrying. Command success is not independent remote
file verification.

## Acceptance

1. Reference behaviour and review fixes are retained with value-type names;
   runtime source, dependencies, packaging and version rules remain unchanged.
2. Pure notes tests and isolated real Git/process tests cover preparation,
   rejection, preview selection, output preservation and partial failures.
   Credential tests cover valid and absent/blank values without uploads.
3. Quality checks, all specified runtime/build/tooling/release suites and
   actionlint pass. Record commands, JVM versions, results and hosted limits.
4. Workflow inspection confirms fixed checkout, typed gates, secret placement,
   checks before publication, artifact-path reuse and fail-stop ordering.
5. Maintainer documentation covers prepare, inspect, separate push, preview,
   publish, credentials and manual recovery. Architecture and command docs agree.
6. Acceptance makes no remote writes, configures no secrets and publishes no
   release. Hosted publication remains unverified unless separately authorised.

## Boundaries

Out of scope: automatic releases, new artifacts, signing, dependency upgrades
except release tooling, runtime changes, branch protection, secret configuration,
shared cross-project tooling, transport hardening and real publication.
