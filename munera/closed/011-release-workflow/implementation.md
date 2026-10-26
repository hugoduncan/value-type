# Implementation notes

## 2026-10-26 — Task creation and orientation

- User requested a release task using the same implementation as closed-record
  task 016. Read working memory, task plan, Ramora index, packaging knowledge,
  the completed reference task and its build, release namespaces and workflow.
- Pinned the clean reference checkout at
  `ffe2ad4e4e5e1b9f66ab814d72989ca60fe584d3`. Reuse its final implementation,
  including review follow-ups; do not restore its abandoned task-015 framework.
- Current value-type has source-only packaging and quality CI, but no release
  support. Preserve its artifact identity and Clojure 1.12.6 dependency. Replace
  the reference's runtime matrix with value-type's Java 8/Valhalla tests and
  existing quality commands. Linux Valhalla provisioning needs verification
  during implementation; no hosted support is claimed by these task records.
- Created design, plan and unchecked implementation steps. No production code,
  workflow, dependency or changelog changed. No tests or publication ran.
- Task Markdown links and `git diff --check` passed. Each task file is below
  the 1,000-word document split threshold.

## 2026-10-26 — Architectural-fit review

- No architectural review feedback. Scope is unchanged; no design steps added.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context
  for later review turns is in `ramora/defvaluetype-design.md`, its purpose,
  qualities, fallback and embodiment sections, especially
  `ramora/defvaluetype-design/embodiment/artifacts.md`. Reviewed these with
  `AGENTS.md` and the runtime development guide; did not read task `plan.md`
  or `steps.md`.

## 2026-10-26 — Ambiguity review

- No ambiguity review feedback. Scope is unchanged; no design steps added.
- Targeted reads of the pinned reference's `.github/workflows/release.yml`
  and `README/releases.md` confirm that preview runs checks and tests before
  notes selection and its build. The reference resolves this detail without
  a new design decision.

## 2026-10-26 — Inconsistency review

- No inconsistency review feedback. Scope is unchanged; no design steps added.

## 2026-10-26 — Review handoff

- Verification references: `doc/development/runtime.md` gives explicit JVM
  selection and cross-JVM environment variables; `.github/workflows/ci.yml`
  gives current quality commands and action conventions.
- `mementum/knowledge/testing-library-artifacts.md` explains consumer isolation:
  a fresh JVM alone does not exclude checkout sources. Preserve the existing
  POM-derived runtime classpath and exact-JAR resource checks when adapting
  release verification.

## 2026-10-23 — Plan update and review

- Updated existing plan.md and steps.md from the stable design. No design or
  implementation code changed. Ordered six slices: reference/Linux JVMs,
  build/deploy, pure notes, Git/CLI, quality/workflow, documentation/acceptance.
- Checked the pinned reference with `git show`, including its design, build,
  notes/release implementation, test contracts, workflow, operator guidance,
  architecture and review fixes. Compared local build/dependency, runtime,
  quality, hook and packaging contracts. No blocking design ambiguity found.
- Reviewed plan ambiguity with steps.md as read-only context after drafting.
  No unresolved actionable finding. Explicit commands and JVM assignments
  prevent accidental inheritance of value-only tests by release tests.
- Reviewed design internal consistency and its agreement with referenced
  artifacts. No conflict found. Current local packaging docs describe the
  pre-release state; adding deployment is the intended change, not a conflict.
  The reference's preview runs checks before selection/build; retain that order.
- Linux archive URLs/checksums and execution remain unverified implementation
  details, not missing design decisions. Slice 1 records pins and verifies
  provisioning/capability or stops with a blocker. Hosted publication remains
  outside acceptance authority.
- Validation: local Markdown targets exist; `git diff --check` passed. Plan and
  steps remain below 1,000 words. No tests, provisioning, dispatch, push, secret
  configuration or publication ran during this planning-only pass.

## 2026-10-23 — Shared plan-review: ambiguity turn

- No new actionable ambiguity feedback. Checked plan.md and read-only steps.md
  against the pinned reference and local build, JVM, quality and test contracts.
  No design steps added; context is loaded for the inconsistency turn.

## 2026-10-23 — Shared plan-review: inconsistency turn

- No new actionable inconsistency feedback across task files. Used the context
  loaded in the ambiguity turn; steps.md is unchanged. No design steps added.

## 2026-10-23 — Plan-review handoff

- JVM selection detail: `test-build/build_test.clj` uses the test JVM's
  `java.home` for its value-path consumer, not `VALUE_TYPE_VALHALLA_JAVA`.
  That override is used by `test-fallback/value_type/jvm_config.clj`.
  Keep the build suite itself on Valhalla; setting both cross-JVM variables
  does not replace that requirement.

## 2026-10-26 — Slice 1: reference and Linux JVMs

- Compared pinned reference build, aliases, notes/CLI, tests, workflow, operator
  guide and release architecture with local build and quality conventions.
  Port `closed-record` namespaces/paths to `value-type`; retain local artifact
  metadata, base Clojure 1.12.6, preview-aware build tests and isolated consumers.
  Use local mise/action pins and quality scope/hook machinery, not the reference
  setup actions or Clojure-version matrix. Keep release tests separate from
  the value-only `:test` alias.
- Retain reference review fixes: shallow notes export rejects before output
  writes in both modes; valid credentials have a pure no-upload test; factual
  Unreleased notes include release tooling. Keep shared notes validation,
  CLI agent shutdown, returned deployment paths and typed-false gates.
- Both Linux archives passed checksum, extraction and version checks. The real
  preview-enabled capability probe returned `{:available? true}`; combined
  command exit 0. See [linux-jvms.md](linux-jvms.md) for pins, reproduction,
  output and emulation limits. Local mount/classpath errors were resolved;
  no blocker remains. No runtime suite, benchmark or remote write ran.
- Next: slice 2 build/deploy contracts. No design change was needed. Updated
  plan, checklist and working memory; runtime files and dependencies unchanged.

## 2026-10-26 — Slice 2: build and deployment

- Ported the pinned reference's credential validator, output writer and stock
  deploy call. `jar` now returns `:pom-file` as well as version/JAR path.
  Only the tooling `:deploy` alias adds deps-deploy 0.2.2; runtime dependencies
  and packaging are unchanged. Design is unchanged.
- Tests use real child processes for each absent/empty/blank credential and
  pure validation for valid credentials. They preserve a target sentinel and
  pre-existing output bytes, and check secret-free errors. No upload is called.
  The process helper now accepts nil environment values to remove inherited
  credentials. Artifact checks compare the returned POM with packaged bytes
  and add release-tooling exclusion fixtures; both isolated consumers remain.
- Verification: `JAVA_CMD=<Valhalla>/bin/java` with both consumer variables,
  `clojure -M:build-test -m scry.cli --dir test-build`: exit 0, 9 tests,
  96 assertions, no Scry failure files. JVMs: macOS Valhalla
  `27-jep401ea3+1-1` and Temurin `1.8.0_492-b09`.
  `mise exec -- bb clj-fmt:check` and `mise exec -- bb clj-kondo:lint`
  passed (zero lint warnings/errors). Formatting was applied to the test file.
- Next slice adds notes and release aliases. Quality dependency imports for
  new aliases remain in slice 5. Local return-value documentation is updated;
  operator deployment guidance remains in slice 6. Hosted execution and live
  deployment are unverified. No remote writes or benchmarks ran.
- Final prose/whitespace checks: `mise exec -- bb vale` and
  `git diff --check` passed.

## 2026-10-26 — Slice 3: pure changelog operations

- Ported the pinned notes implementation and tests to `value-type.notes`.
  Added tests for column-one boundaries, unrestricted date/version order and
  stamping validation. Tests use pure inputs and outputs, without mocks.
  Design is unchanged; no Git preparation or export CLI exists yet.
- Added composable release aliases with Scry 0.1.61 and no preview flags or
  runtime test discovery. Seeded factual Unreleased notes only. Preview
  selection of the repository changelog returned source `Unreleased` and
  preparation required. Later slices must add notes for their completed work.
- Verification on Temurin `21.0.10+7-LTS`: `mise exec -- clojure
  -M:release:release-test -m scry.cli --dir test-release` exited 0:
  5 tests, 41 assertions, no Scry failure files. Explicit formatting and
  clj-kondo checks for the new directories passed, as did maintained formatting,
  maintained lint and `git diff --check`. Quality-scope integration remains
  slice 5; the existing maintained scope does not yet include release files.
- Next: reuse the reference Git/CLI layer and real isolated process tests.
  Runtime source/dependencies are unchanged. No remote writes, deployment,
  benchmarks or hosted execution ran.

## 2026-10-26 — Slice 4: preparation and export CLI

- Ported the pinned Git/CLI implementation and real temporary-repository tests
  to value-type. Retained private Git configuration, hook/signing isolation,
  complete-history guards, UTC dates and CLI agent shutdown. Design is unchanged.
- Extended invalid matching-note tests to check both publication and preview
  export modes. Both preserve existing notes and output bytes. A real rejecting
  commit hook leaves the stamped changelog for inspection; no reset occurs.
- Verification on mise Temurin 21: `mise exec -- clojure
  -M:release:release-test -m scry.cli --dir test-release` exited 0: 11 tests,
  112 assertions, no Scry failure files. Explicit `bb clj-fmt check dev/release
  test-release` and `clj-kondo --lint dev/release test-release` passed.
- Next slice must add these paths to maintained quality scopes and integrate
  CI/workflow commands. Tests use local temporary bare remotes only; no project
  tags, network pushes, deployment, dispatch or benchmarks ran. Runtime source
  and dependencies are unchanged. Hosted execution remains unverified.
- Maintained formatting, lint, Vale and `git diff --check` also passed. JVM:
  Temurin `21.0.10+7-LTS`. Maintained scope expansion remains slice 5.

## 2026-10-26 — Slice 5a: quality scopes and ordinary CI

- Added release source/tests to maintained formatting/lint and both hook
  selectors; dependency imports now include deploy and both release aliases.
  Design is unchanged. Ordinary read-only CI runs focused release tests and
  preserves hidden Scry failures before the next suite can clear them.
- Real Git/pre-commit probes cover formatting repair and lint rejection in
  both release trees, with unchanged index checks. Missing-scope probes now
  cover both trees. Probe fixtures copy the current quality config, so checks
  verify this pass before commit rather than only the previous HEAD.
- Verification on mise Temurin 21.0.10+7-LTS: formatting, lint (zero warnings
  or errors), Vale, API freshness, hooks.sh and ci-failures.sh passed. Focused
  tooling tests passed (11 tests/160 assertions); release tests passed
  (11/112). Both CLI exits were 0 with no Scry failure files. Whitespace passed.
- Next: slice 5b manual release workflow, then actionlint on both workflows;
  actionlint is not on PATH yet. Runtime suites remain for full acceptance.
  No runtime changes, remote writes, secrets, benchmarks or publication ran.
  Hosted Actions execution remains unverified.

## 2026-10-26 — Slice 5b: manual release workflow

- Reused the pinned reference publication flow with local action/mise conventions
  and checksum-pinned Java 8/Valhalla archives. Tooling uses explicit mise Java;
  runtime commands override JAVA_CMD. Build tests run on Valhalla, not Java 21.
  No design change was needed. Runtime source and dependencies are unchanged.
- Both modes run all planned checks before notes selection. Each suite has an
  immediate, hidden-file-aware failure upload. Notes stay under RUNNER_TEMP.
  Step IDs support the failure report, including setup and publication failures.
- Inspection: one full-history checkout uses github.sha, never moving master;
  branch guard, disabled persisted credentials, 60-minute timeout and shared
  non-cancelling concurrency are present. JSON boolean false alone publishes;
  true, missing/null and string values select preview. Clojars secrets occur
  only in gated validation/deploy steps. Default success gates stop publication
  after failure. Order remains credentials, tag/push, deploy, gh release; assets
  use deployed output paths. No rollback, outer retry or remote proof was added.
- Verification: actionlint 1.7.7 passed for both workflows. Maintained formatting
  and lint passed (zero warnings/errors). Focused release CLI on mise Temurin
  21.0.10+7-LTS exited 0: 11 tests, 112 assertions, no Scry failure files.
  No source-text tests or service mocks were added for workflow wiring.
- Next slice must finish operator/architecture docs and full local acceptance.
  This pass did not repeat runtime suites or archive provisioning. Hosted Actions
  and publication remain unverified. No remote writes, secret configuration,
  deployment or benchmarks ran.


## 2026-10-26 — Slice 6a: release documentation and prose scope

- Added linked operator and Ramora release guides. Documented separate prepare,
  inspect, push, preview and publication, credential ownership, partial failure
  inspection and hosted limits. Packaging/runtime and quality docs now agree.
  Design is unchanged; no runtime or workflow code changed.
- Vale now checks the release guide and changelog. Real Vale process tests
  verify both new paths reject warnings/errors without editing files. Added
  Clojars and boolean to project vocabulary; fixed prose found by the first run.
- Verification on Temurin 21.0.10+7-LTS: `mise exec -- bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check` passed. A repeated
  check run preserved the tracked diff. `mise exec -- clojure -Srepro
  -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc` exited 0:
  11 tests, 172 assertions, no Scry failure files. Local Markdown link targets
  and `git diff --check` passed.
- Next slice: full value/fallback/build/release acceptance, credential-free JAR
  and local preview export, then acceptance mapping. Do not run preparation on
  this checkout for acceptance: it creates a release commit. Keep exported notes
  outside target/. Hosted execution and publication remain unverified. No remote
  writes, secrets, deployment or benchmarks ran.

## 2026-10-26 — Slice 6b: final local acceptance

- Full JVM matrix, isolated build consumers, release and tooling suites passed;
  commands, versions, exit/failure-file checks and all six acceptance criteria
  are in [acceptance.md](acceptance.md). The initial combined fallback run hit
  a tool timeout; a longer allowance completed all configurations successfully.
- Credential-free preview/build passed for HEAD version 0.1.98 with Unreleased
  notes and preparation required. Preserved existing target research output
  outside the checkout before the destructive build. Notes survived the build.
- Repeated quality checks and actionlint passed without tracked-file changes.
  No code/test changes or design deviations were needed. All checklist items
  are complete; next is implementation review, not publication. Hosted Actions
  and remote publication remain unverified. No remote writes, secret setup,
  deployment or benchmarks ran.

## 2026-10-23 — Implementation review

- Added 1 step to be addressed.

## 2026-10-23 — Review follow-up

- Addressed 1 review step: aligned the quality guide with maintained release
  paths and ordinary CI release tests; kept the runtime-matrix exclusion.
  Checked against quality.clj and ci.yml. Vale and `git diff --check` passed.
  Documentation only; no test suites or remote operations ran.

## 2026-10-23 — Implementation re-review

- No new actionable findings. Release tests passed: 11 tests, 112 assertions;
  exit 0, no Scry failure files. Full runtime matrix was not repeated.

## 2026-10-23 — Test review

- Added 1 step to be addressed.

## 2026-10-26 — Test review follow-up

- Addressed 1 review step: added a real CLI test with a one-shot post-commit
  hook. It checks count-mismatch failure, retained commits and stamped bytes,
  and unchanged tags and local bare remote refs. No mocks or production changes.
- Removing the count guard made the new test fail on exit status and diagnostic;
  restored the guard. Release suite passed: 12 tests, 123 assertions, exit 0,
  no Scry failure files. Formatting, lint and whitespace checks passed.
  Runtime/build suites were not repeated; no network writes or publication ran.

## 2026-10-23 — Test re-review

- No new actionable findings.

## 2026-10-23 — Documentation review

- No new actionable documentation findings.

## 2026-10-23 — Code-shape review

- No new actionable code-shape findings.


## 2026-10-26 — Closure

- Closed at the user's request after implementation, review follow-ups and
  knowledge extraction. All implementation checklist items are complete;
  final reviews record no new actionable findings.
- Knowledge extraction created release identity/failure guidance (`e398ff3`)
  and updated artifact-test JVM guidance (`90f6eba`).
- Moved the task to munera/closed, removed it from the orchestration plan and
  updated links and working memory. Historical acceptance evidence is retained.
- Closure changes records only. No test suite, push, dispatch, secret setup,
  deployment or publication ran. Hosted execution and publication remain
  unverified.
- Validation: local Markdown file targets and `git diff --check` passed;
  no references to the former open-task path remain.
