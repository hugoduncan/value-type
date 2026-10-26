# Release implementation plan

[design.md](design.md) owns scope and acceptance. Reuse closed-record task 016
at `ffe2ad4e4e5e1b9f66ab814d72989ca60fe584d3`. Adapt identity and verification;
do not import task-015 cleanup, its framework, or the Clojure-version matrix.

## Approach

### Reference and ownership

Read the pinned files with `git show COMMIT:PATH` in
`~/projects/hugoduncan/closed-record/closed-record-master`, not from an
unverified working tree:

- `build.clj`, `deps.edn`, and `test-build/build_test.clj`.
- `dev/release/closed_record/{notes,release}.clj` and
  `test-release/closed_record/{notes,release}_test.clj`.
- `.github/workflows/release.yml`, `README/releases.md`, and
  `ramora/release-workflow/`.
- `munera/closed/016-simple-release-workflow/implementation.md`, including
  shallow-history, valid-credential and changelog review fixes.

Build tooling owns packaging/deployment. Release tooling owns shared notes
validation, preparation and export. Git and gh own publication. Keep runtime
source, Clojure 1.12.6, source-only EPL-2.0 packaging and Git-count versions.

### Build and local release tooling

Extend `build.clj`: return the actual POM path; validate nonblank credentials
before build effects; build HEAD; load stock deps-deploy 0.2.2 lazily; call it
once; return artifact paths. Append optional GitHub outputs only after success.
Keep credential-free `jar` and `clean`, including their target-deletion behaviour.
Do not test live deployment or introduce service mocks.

Port namespaces to `value-type` in `dev/release/value_type/` and tests to
`test-release/value_type/`. Add tooling-only `:deploy`, `:release` and
`:release-test` aliases. Give release tests Scry 0.1.61 without `:test`, preview
flags or `:main-opts`. Preserve these interfaces:

- `clojure -M:release -m value-type.release prepare`
- `clojure -M:release -m value-type.release notes NOTES_FILE GITHUB_OUTPUT [--preview]`
- `clojure -T:build:deploy deploy`
- Build outputs: `version`, `jar-file`, `pom-file`.
- Notes outputs: `sha`, `version`, `tag`, `source`, `preparation-required`.

Retain the full shared line-based validator, explicit UTC stamping, resulting
commit-count check and CLI agent shutdown. Seed factual Unreleased notes only.
Use real temporary Git repositories on master, isolated from user configuration,
hooks and signing. Install failing hooks only in failure fixtures. Preserve
pre-existing output bytes on validation failure and local state after commit
failure. Keep fixtures and exported notes outside `target/`.

### Workflow and verification

Use Ubuntu 24.04 and current mise/action conventions. Provision explicit Linux
x86_64 Java 8 and Valhalla executables. Start with Temurin `1.8.0_492-b09` and
Valhalla `27-jep401ea3+1-1`; record download URLs, checksums and version output.
Do not silently substitute a newer EA format. Slice 1 verified both archives and the value-capability probe in an Ubuntu
24.04 amd64 container. Exact pins and reproduction are in
[linux-jvms.md](linux-jvms.md). Use these pins for workflow integration.

Use the pinned mise Java 21 JVM for quality, tooling, release, notes and deploy
commands. Set `JAVA_CMD` for each runtime command, plus
`VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` for cross-JVM tests.
Verification commands are:

| JVM | Command |
| --- | --- |
| Tooling | `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale`, `bb api-docs --check` |
| Tooling | `clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc` |
| Tooling | `clojure -M:release:release-test -m scry.cli --dir test-release` |
| Valhalla | `clojure -M:test -m scry.cli` |
| Java 8 | `clojure -M:fallback-test` |
| Valhalla | `clojure -M:fallback-test` |
| Valhalla | `clojure -J--enable-preview -M:fallback-test` |
| Valhalla | `clojure -M:build-test -m scry.cli --dir test-build` |

The test/build aliases already enable preview. Preserve both isolated JAR
consumers, their POM-derived classpaths and exact-JAR resource checks. Check CLI
exit status and `.scry-results` immediately after each suite; upload hidden
failure files before another suite can clear them. Do not run benchmarks.

Both preview and publication run all checks. Keep one fixed full-history
checkout of the master dispatch SHA, one contents-write job, a finite timeout,
non-cancelling release concurrency and no persisted checkout credentials.
Only typed false permits publication; other input values take the preview path.
Use step-scoped secrets and command-scoped Git authentication.

Select notes under RUNNER_TEMP. Publication order is credential validation,
normal tag creation/push, build/deploy, then ordinary gh Release creation using
returned artifact paths. Preserve EDN string quoting for `:github-output`.
Stop on failure; report failed steps and manual inspection instructions.

Extend quality paths, dependency imports, hook selectors and scope tests for
release tooling. Add focused release tests to ordinary read-only CI; do not add
the runtime matrix there. Add `doc/development/release.md`, linked from the
development guide; update runtime command docs, packaging architecture and
Ramora release documentation/index. Include new maintainer prose in Vale scope.

## Risks

- Linux x86_64 provisioning and capability passed under local emulation. Native
  hosted execution remains unverified; do not omit required workflow tests.
- Git-count versions require complete history. New commits after preparation
  require matching notes. Preparation assumes no concurrent local edits.
- Builds delete `target/`. Preserve notes and test evidence elsewhere.
- Publication is not atomic. Failures can leave tags or uploads. No outer
  retries, reset, rollback, resume or remote-absence protocol is permitted.
- Local tests/actionlint cannot prove hosted permissions or remote publication.
  Acceptance must not push, dispatch, configure secrets or publish.

## Slice order

1. Verify reference interfaces and Linux JVM provisioning; record pins or blocker.
2. Extend build/deploy contracts and isolated artifact/credential tests (complete).
3. Port pure notes validation, stamping and selection; seed CHANGELOG.md (complete).
4. Port preparation/export CLI and real Git/process tests (complete).
5. Integrate quality scopes, ordinary CI and the manual release workflow.
   Quality scopes, hook/scope probes and ordinary CI are complete (slice 5a).
   Manual workflow and actionlint for both workflows are complete (slice 5b).
   Full local runtime acceptance and operator documentation are complete in slice 6.
6. Align maintainer/architecture docs and run full local acceptance.
   Documentation, Vale scope and focused tooling acceptance are complete
   (slice 6a). Full runtime/build/release acceptance and local preview passed
   in slice 6b; see [acceptance.md](acceptance.md).

Each slice ends with its focused checks and recorded evidence in
implementation.md. Commit each completed slice. Full acceptance maps evidence
to all six design criteria and states hosted limits explicitly.
