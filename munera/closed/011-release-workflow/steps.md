# Implementation steps

Follow [design.md](design.md) and [plan.md](plan.md). Record commands, decisions,
results and blockers in implementation.md. Commit each completed slice.
No real push, dispatch, secret configuration or publication is authorised.

## Slice 1 — Reference and Linux JVM provisioning

- [x] Compare the pinned reference files with local build, aliases and quality conventions; record the port map and retained review fixes.
- [x] Resolve Linux x86_64 Java 8 and Valhalla archives; record exact versions, URLs, checksums and extraction paths.
- [x] Verify checksum-based installation and `java -version` for both archives on compatible Linux; stop and record a blocker if provisioning cannot be verified.
- [x] Run a preview-enabled value-capability probe on the provisioned Valhalla JVM; record the result and executable paths for cross-JVM tests.

## Slice 2 — Build and deployment

- [x] Add the tooling-only `:deploy` alias with stock deps-deploy 0.2.2, leaving base dependencies unchanged.
- [x] Extend `jar` to return its actual POM path without changing packaging, cleanup or version rules.
- [x] Port credential validation and `deploy`: validate before building, build HEAD, call stock deploy once and return the artifact map.
- [x] Port optional GitHub output appending for successful deployment using `version`, `jar-file` and `pom-file`.
- [x] Extend artifact tests to verify returned paths, POM metadata and exclusion of release tooling while retaining input-preservation and both isolated consumers.
- [x] Port absent/empty/blank credential process tests that verify failure before build effects, secret-free errors and unchanged output files.
- [x] Port the pure valid-credential test and output-writer test without making any upload call.
- [x] Run the plan's build suite on Valhalla with both consumer executables configured; inspect exit status and Scry failure files.

## Slice 3 — Pure changelog operations

- [x] Add composable `:release` and `:release-test` aliases with separate paths and Scry, without runtime discovery or preview requirements.
- [x] Port `value-type.notes` with shared whole-document parsing, explicit-date stamping and preview-aware selection.
- [x] Port pure tests for exact headings, versions, calendar dates, section order, duplicates, opaque bodies and nonempty-body rules.
- [x] Verify stamping preserves prior entries and rejects empty Unreleased or an existing next version before producing output.
- [x] Verify matching-note precedence, preview-only Unreleased fallback and rejection of invalid or empty matching notes.
- [x] Create CHANGELOG.md with factual Unreleased library and release-tooling notes; validate it without inventing dated releases.
- [x] Run the focused release suite on the tooling JVM; inspect exit status and Scry failure files.

## Slice 4 — Preparation and export CLI

- [x] Port `value-type.release` preparation with clean-master/full-history guards, UTC stamping, changelog-only commit and resulting count check.
- [x] Port notes export and CLI dispatch with preserved output keys, validation before file writes and agent shutdown.
- [x] Port isolated real Git/process fixtures with explicit master, private Git configuration and cleanup outside target/.
- [x] Verify preparation success: exact commit message, only CHANGELOG.md committed, empty Unreleased, correct UTC date/version and unchanged tags/local remote.
- [x] Verify dirty, detached, wrong-branch, shallow, invalid-note and duplicate-next-version rejection without input changes.
- [x] Verify a rejecting local commit hook leaves inspectable partial state and a nonzero CLI exit without reset.
- [x] Verify CLI argument rejection, matching/preview note selection, output append semantics and normal process termination.
- [x] Verify invalid notes and shallow history preserve pre-existing notes/output bytes in both publication and preview modes.
- [x] Run the complete focused release suite on the tooling JVM; inspect exit status and Scry failure files.

## Slice 5 — Quality and workflow integration

- [x] Extend maintained paths and dependency imports in `bb/value_type/quality.clj` for release source, tests and aliases.
- [x] Extend hook selectors and tooling scope tests; verify release files receive the same checks without automatic staging.
- [x] Add focused release tests to ordinary CI while retaining read-only checks and unchanged tracked-file verification.
- [x] Add the single-job manual workflow with master guard, fixed full-history dispatch SHA, default preview, timeout, concurrency and disabled credential persistence.
- [x] Add pinned tooling and verified JVM provisioning with explicit per-command JVM selection and cross-JVM variables.
- [x] Add every quality/tooling/runtime/fallback/build/release command from plan.md before notes selection in both modes, with immediate per-suite hidden failure uploads.
- [x] Add RUNNER_TEMP notes selection, preview build and SHA/version/tag/source/preparation reporting without publication secrets.
- [x] Add typed-false credential checks, tag creation/push, deploy and gh Release steps in fail-stop order using scoped secrets and returned artifact paths.
- [x] Add failure reporting that names failed steps and requires manual GitHub/Clojars inspection without retry or rollback.
- [x] Run actionlint on both workflows and inspect typed true/false/missing/null/string input gates, fixed checkout, secret scope and publication ordering.

## Slice 6 — Documentation and acceptance

- [x] Add linked `doc/development/release.md` instructions for prepare, inspect, separate push, preview, publish, credentials and manual recovery.
- [x] Update runtime build return-value documentation and packaging architecture to include deployment without changing consumer requirements.
- [x] Add linked Ramora release architecture with ownership, contracts, flow and verification limits; exclude reference task-015 history.
- [x] Add the release guide and CHANGELOG.md to Vale scope and adjust tooling scope tests.
- [x] Run all quality and tooling commands in plan.md; verify tracked files remain unchanged by checks.
- [x] Run the value-path suite and each of the three fallback configurations with explicit JVM selection.
- [x] Run build tests with both isolated consumer JVMs and release tests on the tooling JVM.
- [x] Verify a credential-free local JAR build and local preview export with notes outside target/; do not invoke deployment.
- [x] Record per-suite exit/failure-file results, JVM versions, actionlint and workflow inspection against all six acceptance criteria.
- [x] Check Markdown links and whitespace; record hosted limits and confirm no remote writes, secret configuration or benchmarks occurred.

## Implementation review follow-up

- [x] Update `doc/development/quality-checks.md` to include `dev/release/` and `test-release/` in the formatting/lint scope and the focused release suite in the ordinary CI description. The current scope list omits both maintained paths, and the CI paragraph does not mention release tests. Match `bb/value_type/quality.clj` and `.github/workflows/ci.yml`; keep the runtime-matrix exclusion explicit.

## Test review follow-up

- [x] Add a real Git/process test for preparation's post-commit count mismatch in `test-release/value_type/release_test.clj`. Current tests cover the expected count and a rejected commit, but none reaches the mismatch guard in `release/prepare`. Use an isolated, one-shot post-commit hook that creates an extra local commit with hooks disabled for that nested commit. Assert a nonzero CLI exit with the count-mismatch diagnostic, retained commits and stamped changelog, and no tag or remote change. The test must fail if the resulting-count check is removed; use no process mocks or production test options.
