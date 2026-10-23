# Implementation notes

## 2026-10-23 — Architecture review

- No architectural review feedback. Scope is unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture
  context came from `AGENTS.md` and `ramora/defvaluetype-design.md`,
  including its context, decisions, embodiment and qualities sections.
- Decision D7 supports AOT output for consumer-defined types; it does
  not require AOT packaging of the library. The source-only JAR keeps
  that capability and the `core → loader → transform` boundary intact.

## 2026-10-23 — Ambiguity review

- No ambiguity review feedback. Scope is unchanged.
- Checked the referenced `closed-record/add-jar/build.clj`: `jar`
  returns `{:jar-file jar-file :version v}`. Git count validation trims
  stdout and requires `[0-9]+`; a nonzero exit or launch failure gives
  an error. These details resolve the design's reference-based contract.

## 2026-10-23 — Inconsistency review

- No inconsistency review feedback. Scope is unchanged.

## 2026-10-23 — Review handoff

- Relevant non-task files: `deps.edn` separates runtime dependencies
  from development aliases. `README.md` documents consumer JVM setup
  and the `target/object-size/` outputs affected by the clean command.

## 2026-10-23 — Planning and plan review

- Created `plan.md` and `steps.md` from the stable design. Implementation
  has not started. The four slices cover build entry points and license,
  build contract checks, isolated consumer use, and documentation with
  final verification.
- Reviewed the plan for ambiguity with `steps.md` held read-only.
  Clarified in the plan that the consumer resolves dependency coordinates
  from the generated POM without installing or publishing the library.
  No unresolved plan ambiguity remains.
- The consumer check must not reuse `run-in-new-jvm`: that helper adds
  the repository test classpath. Use an explicit artifact-only runtime
  classpath to prevent a false pass from checkout sources.
- Selected the reference tools.build version `0.10.10`, a separate
  `:build-test` alias and test directory, and explicit project-description
  wording. These are implementation choices left open by the design;
  none needs a user decision.

## 2026-10-23 — Design consistency check after planning

- Rechecked `design.md` internally and against the referenced build,
  `deps.edn`, README, and the architecture's D7 AOT decision. No internal
  or referenced-artifact inconsistency was found. The reference return
  map and decimal-count validation agree with the plan.
- Source-only library packaging does not conflict with AOT support for
  consumer-defined types. The clean warning matches the benchmark output
  location in README. The runtime dependency remains Clojure 1.12.6.
- No blocking or non-blocking design ambiguity remains. `design.md` is
  unchanged. All eight acceptance criteria have planned verification.
- Validation in this planning pass is document review and whitespace
  checking only. No build or library tests were run.

## 2026-10-23 — Shared plan-review handoff

- Review commits `68806f9` (ambiguity) and `9bc1107` (inconsistency)
  are empty: the existing notes already covered both outcomes.
  Neither pass added follow-up items; the absence of `design-steps.md`
  is intentional, not a missing review artifact.

## 2026-10-23 — Slice 1: build entry points and license

- Added tools.build 0.10.10 only in the `:build` alias. Runtime
  dependencies and library sources are unchanged. Git runs only in
  `jar`; validation occurs before target deletion. The runtime basis
  excludes user configuration and aliases.
- Copied the full Eclipse license text from
  `https://www.eclipse.org/org/documents/epl-2.0/EPL-2.0.txt` (276 lines,
  14,198 bytes); `cmp` against the downloaded file passed.
- On JDK `27-jep401ea3+1-1`, `clojure -T:build jar` passed. Direct
  `build/jar` invocation returned
  `{:jar-file "target/value-type-0.1.401.jar", :version "0.1.401"}`,
  matching the then-current Git count. Archive inspection found the
  three library sources, license, manifest and Maven metadata only.
  POM inspection confirmed coordinates, description, URL, SCM, license
  and the sole runtime dependency Clojure 1.12.6.
- In a temporary directory without Git metadata, both first and repeated
  `clojure -T:build clean` passed; `jar` failed with the expected Git
  diagnostic. `git diff --check` passed. Automated error, archive and
  consumer coverage remains in slices 2 and 3; no library tests ran.
- Preserved existing `target/object-size/` in
  `/tmp/value-type-target-backup.jNz4jz/` before building and restored it
  afterwards. Future checkout builds must preserve these outputs again.
- No design or approach change. Next slice should test the private
  `version-from-result` validator and real child processes, not mocks.

## 2026-10-23 — Slice 2a: error and clean contract checks

- Added the separate build-test alias and three tests. Fixtures use real
  temporary directories outside the checkout, unconditional cleanup,
  explicit child working directories and 60-second process limits.
  No mocks, library changes or runtime dependency changes were needed.
- Git launch failure is tested with an empty child PATH and an absolute
  Java executable. Clojure's default error printer shows the root
  IOException rather than the build diagnostic. The child harness now
  prints the caught exception message and exits nonzero, so the test
  checks the build's diagnostic directly.
- Verification on Valhalla JDK 27: `clojure -M:build-test -m scry.cli
  --dir test-build` exited 0: 3 tests, 19 assertions. `.scry-results/`
  was empty; `git diff --check` passed. Checkout target data was untouched.
- The design is unchanged. Next: complete slice 2 with a real committed
  Git fixture, archive byte checks, parsed Maven metadata and user-deps
  isolation. The child harness currently uses the build-test classpath;
  the later consumer check must instead resolve an isolated classpath.

## 2026-10-23 — Slice 2b: archive and metadata contract

- Added a real one-commit Git fixture. The test checks the returned map,
  exact archive payload, source/license bytes, stale target deletion,
  parsed POM fields and Maven properties. No production changes.
- A child CLJ_CONFIG points to an extra, unavailable user dependency.
  The child verifies that tools.deps finds that configuration before
  building; the build succeeds and declares only project runtime deps.
- Verification on Valhalla JDK 27: `clojure -M:build-test -m scry.cli
  --dir test-build` passed (4 tests, 44 assertions); `.scry-results/`
  is empty. Checkout target data was untouched. Design is unchanged.
- Next: reuse `pom-dependencies` for the isolated consumer fixture, but
  do not use `build-process` for the consumer: it inherits the test
  classpath. Consumer classpath must contain only artifact/runtime JARs.

## 2026-10-23 — Slice 3: isolated consumer

- Added a real consumer test using dependencies read from the packaged
  POM. Its basis disables root/user configuration, has no source paths
  or aliases, and supplies only runtime JARs plus the built artifact.
  The child verifies the core resource's JAR URL against that artifact,
  constructs two instances, checks four field values and `Class.isValue`.
- The consumer uses the existing bounded process helper, not the inherited
  build classpath. The artifact path is passed as a JVM property because
  trailing arguments are not available to `clojure.main -e` as expected.
- Verification: on Valhalla JDK `27-jep401ea3+1-1`,
  `clojure -M:build-test -m scry.cli --dir test-build` exited 0
  (5 tests, 49 assertions); no Scry failure files remain. Checkout target
  output was not changed. Design and runtime dependencies are unchanged.
- Next: README and final root-command/library-suite verification in slice 4.

## 2026-10-23 — Slice 4: documentation and final verification

- README now covers local build/clean commands, return values, Git count
  limits, source-only contents, consumer requirements, separate build tests
  and EPL-2.0. No design or approach change; no new tests were needed for
  this documentation-only slice. Existing real-process checks cover the
  contract without mocks.
- On Valhalla `27-jep401ea3+1-1`, root `clojure -T:build clean` passed
  twice, then `clojure -T:build jar` passed. Direct `build/jar` invocation
  returned `{:jar-file "target/value-type-0.1.405.jar" :version "0.1.405"}`.
  The artifact filename and parsed POM matched the then-current Git count.
  A later commit changes that count; rebuild to get the new version.
- Preserved target output in the temporary directory named by
  `/tmp/value-type-slice4-backup-path`; restored `target/object-size/`.
- `clojure -M:build-test -m scry.cli --dir test-build`: exit 0,
  5 tests, 49 assertions. `clojure -M:test -m scry.cli`: exit 0,
  64 tests, 332 assertions. `.scry-results/` was empty after each run.
- Acceptance evidence: criteria 1–3 and 5–6 have automated build checks
  plus root command/archive verification; criterion 4 has the isolated
  consumer check; criterion 7 has both passing suites; criterion 8 has
  the README. Library sources and runtime dependencies are unchanged;
  no install or publish command was added. All slices are ready for review.

## 2026-10-23 — Implementation review

- No new actionable issues. Build checks: 5 tests, 49 assertions;
  library checks: 64 tests, 332 assertions. Both passed on Valhalla
  JDK 27; no Scry failure files remain.

## 2026-10-23 — Test review

- Added 2 steps to be addressed.

## 2026-10-23 — Review follow-up: CLI commands

- Addressed 1 review step. Real CLI checks use a temporary project and
  empty user configuration. They check the JAR and repeated clean without
  Git metadata. Checkout target output is untouched.
- Valhalla JDK 27 build suite passed: 6 tests, 56 assertions; exit 0,
  no Scry failure files.

## 2026-10-23 — Review follow-up: input preservation

- Addressed 1 review step. Snapshot fixture source paths and bytes plus
  deps.edn before jar and each clean; compare after each command.
- Valhalla JDK 27 build suite passed: 6 tests, 59 assertions; exit 0,
  no Scry failure files. Both review steps are complete. No production
  code or design change; the library suite was not rerun.

## 2026-10-23 — Test re-review

- No new actionable issues. Build suite passed: 6 tests, 59 assertions.

## 2026-10-23 — Documentation review

- Added 1 step to be addressed.

## 2026-10-23 — Documentation review follow-up

- Addressed 1 review step: added Ramora packaging guidance, artifact
  entries and index/README links. Checked local link targets and
  `git diff --check`. Documentation only; no tests rerun.

## 2026-10-23 — Documentation re-review

- No new actionable issues.

## 2026-10-23 — Code-shaper review

- No new actionable issues. Build suite passed: 6 tests, 59 assertions.

## 2026-10-23 — Closure

- Closed as completed at the user's request. All implementation and
  review steps are complete; no actionable review issues remain.
- Knowledge extraction completed in `5282c0a` and created
  `mementum/knowledge/testing-library-artifacts.md`.
- Moved the task to `munera/closed/008-jar-build` and removed it from
  the task index. Updated working memory to record completion.
- Closure changes task records only. No tests were rerun.
