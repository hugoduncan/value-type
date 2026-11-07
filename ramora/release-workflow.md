# Release workflow

## Ownership and boundaries

`build.clj` owns source-only packaging, credential validation and deployment.
The tooling-only `:deploy` alias selects stock deps-deploy 0.2.2. Runtime sources,
dependencies, EPL-2.0 metadata and consumer requirements are separate from
release tooling.
`jar` and `deploy` return `:version`, `:jar-file` and `:pom-file`; deployment can
append these fields to a GitHub output file after success.

`dev/release/value_type/notes.clj` owns whole-document line-based validation,
stamping and note selection. Body Markdown is opaque. Section headings in column
one are not hidden by fences or comments. Exactly one Unreleased precedes all
dated sections; dated bodies must be nonempty. Versions and calendar dates are
validated, but need not be ordered. Duplicate versions are rejected.

`dev/release/value_type/release.clj` owns local Git preparation and notes export.
Preparation requires clean master and complete history, stamps the next count
with a UTC date, commits only the changelog and checks the resulting version.
Export requires complete history and validates before writing notes or appending
outputs: `sha`, `version`, `tag`, `source`, `preparation-required`. Only preview
can use Unreleased when matching notes are absent. Invalid matching notes cannot
fall back. Partial local failures remain inspectable without reset.

Git and GitHub CLI own tag and Release publication. See the
[operator guide](../doc/development/release.md) for commands and recovery.

## Workflow flow

`.github/workflows/release.yml` is manual only. One contents-write job uses a
single full-history checkout of the master dispatch SHA with persisted
credentials disabled. It has a finite timeout and one release concurrency group
with cancellation disabled. Maintainer-controlled master code is trusted.

Both modes run quality and tooling checks, value tests on Valhalla, Java 8 and
Valhalla preview-off/on fallback tests, cross-JVM tests, isolated JAR consumers and release tests.
Tooling runs on Java 21; runtime JVMs come from the supported-JVM facts file
([D21](defvaluetype-design/decisions/verification-and-release.md)), with the test
tiers of D22. Consumers install the release tags that this workflow creates
(D24), not `master`. Build tests run
on Valhalla and launch both consumer JVMs. Each suite preserves Scry failures
before another suite can clear them. No benchmarks run.

Only typed false publishes. Preview selects notes, builds and reports without
publication secrets or release writes. Publication requires matching notes, then
validates credentials, creates/pushes the tag, builds/deploys and creates the
Release with returned asset paths. Notes stay outside target/. Secrets are
step-scoped; Git authentication is command-scoped. Failure stops later writes.
No outer retries, rollback, automatic resume or remote-absence protocol exists.

## Embodiment

### Verification limits

Pure tests cover notes; isolated real Git/process tests cover preparation,
selection, output preservation and partial failures. Credential tests reject
missing or blank values before effects and validate good values without uploads.
Artifact tests retain POM-derived classpaths and exact-JAR resource checks.

Not yet implemented: D21 (the workflow names the JVM URLs and SHA-256 values
directly), D22 and D24 (the README installs from a clone).

Hosted execution, permissions and live publication remain unverified.
Local emulation and actionlint do not prove these properties. Tests perform
no remote writes. Command success does not independently verify remote files.
Linux archive pins and capability evidence are in the
[JVM verification record](../munera/closed/011-release-workflow/linux-jvms.md).
