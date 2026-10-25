# Implementation notes

## Architecture review — 2026-10-25

- No architectural review feedback. Scope unchanged; no design steps added.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context
  came from `AGENTS.md` and `ramora/defvaluetype-design.md`, including its
  purpose, qualities, fallback and runtime/artifact embodiment sections.
  These are the architecture sources for later design-review turns.

## Ambiguity review — 2026-10-25

- No ambiguity review feedback. Scope unchanged; no design steps added.

## Inconsistency review — 2026-10-25

- No inconsistency review feedback. Scope unchanged; no design steps added.
- At the pinned Scry commit, `.pre-commit-hooks/cljfmt-fix.sh` runs
  `git add` on changed files and suppresses staging errors with `|| true`.
  Do not copy that script unchanged: adapt it to the design's staging
  protection. This is an explicit reference adaptation, not a design conflict.

## Review handoff — 2026-10-25

- `src/value_type/core.clj` already has the macro's detailed contract and
  explicit `:arglists`; use these when checking generated API content.
- `build.clj` creates the artifact dependency basis with `:user nil` and
  `:aliases []`. Preserve this isolation when adding tooling aliases to
  `deps.edn`; the packaging contract is in
  `ramora/defvaluetype-design/embodiment/artifacts.md`.

## Planning and review — 2026-10-25

- Created `plan.md` and `steps.md` from the stable design. No implementation
  work started. The checklist has 38 unchecked actions in six slices.
- Selected concrete tool versions from local CLI output and the pinned
  Scry reference. Quickdoc's cached pinned source confirms `:no-doc`
  overrides and private-var exclusion. Linux installation and complete
  version compatibility remain implementation verification, not proven results.
- Plan ambiguity review treated `steps.md` as read-only; its SHA-256 stayed
  `4203e3a98d9a8d9c4e53e8b6da5d1895ee4e39509f7dc5941c9e00ecf9813819`.
  Clarified Vale JSON exit handling in the plan so suggestions can pass
  without hiding setup errors. No unresolved blocking plan ambiguity remains.
- Design consistency review compared the design internally and against the
  pinned Scry tasks, tools, hooks, workflow, Vale rules, generator and tests;
  also checked the current README, macro docstring/arglists, dependency aliases,
  build code and artifact architecture. No design inconsistency was found.
- Non-blocking design choices are resolved in the plan: exact tool pins,
  no automatic hook staging, and shared local/CI Vale severity handling.
  Scry's restaging hook and narrower lint scope are not copied unchanged;
  the design explicitly requires these adaptations. Design text is unchanged.
- Validation: checklist structure, document size and whitespace checks.
  Runtime and tooling tests were not run because this change is planning only.

## Shared plan-review — turn 1, 2026-10-25

- No new ambiguity review feedback on the plan and steps; no design steps
  added. `steps.md` is unchanged. Context for the later inconsistency turn
  includes the pinned Scry reference and Quickdoc source, current API contract,
  dependency aliases, hook behavior and artifact-test JVM requirements.

## Shared plan-review — turn 2, 2026-10-25

- No new inconsistency review feedback across the design, plan, steps and
  implementation notes. No design steps added; `steps.md` is unchanged.

## Review handoff — regression setup, 2026-10-25

- `test-fallback/value_type/jvm_config.clj` defines the accepted fallback
  test hosts and executable overrides. Consult it when verifying tooling-JVM
  isolation; the tooling JVM is not a supported host for that suite.

## Slice 1 — 2026-10-25

- Added pinned mise setup, development-only Quickdoc aliases and shared
  formatting/lint entry points. Runtime dependencies and all existing
  aliases are structurally unchanged (EDN comparison passed). No library
  code changed. Design scope is unchanged.
- Dependency imports resolve all eight aliases separately with `-Srepro
  -Spath -A:<alias>`; this computes classpaths without starting preview
  JVMs. Imports run before lint. clj-kondo uses `--repro` and the project
  config directory. cljfmt includes EDN explicitly (its default excludes
  EDN). Optional not-yet-created scope directories are filtered by existence.
- `mise install` passed on macOS arm64. Version checks passed: Babashka
  1.13.225, Clojure CLI 1.12.6.1673, clj-kondo 2026.04.15, cljfmt 0.16.4,
  Vale 3.17.1, pre-commit 4.6.0, Temurin 21.0.10+7; mise is 2026.4.11.
  Linux verification moves to slice 5; no Linux executor was used here.
- Real integration checks: copy-config exited 0 both normally and with
  empty `CLJ_CONFIG`, then again after deleting project imports. Imported
  babashka/fs, babashka/sci and org.hugoduncan/criterium configuration.
  Maven/git caches were warm; fully cold download verification remains
  in slice 6. No dependency references the local Scry checkout.
- Explicit formatting fix and check of `bb`, `deps.edn`, `bb.edn` exited
  0. `git diff --check` passed. Generated imports are ignored. Existing
  lint baseline correctly exits 1 (126 errors, 1 warning); slice 2 must
  add macro analysis and fix findings before this gate can pass.
- Babashka task namespace requirements must be inside `:tasks :requires`;
  the reference's top-level `:requires` did not resolve task aliases.
  Corrected and verified through real task execution. No mocks used.

## Slice 2a — 2026-10-25

- Added only the `defvaluetype` → `deftype` lint-as mapping. No analysis
  hook or suppression is needed for its fields, methods or constructor.
  Runtime source and design contract are unchanged.
- Added real-process lint tests under `test-quickdoc`: valid field/method
  bindings and constructor calls pass; an unresolved method-body symbol
  produces exactly one unresolved-symbol finding and a nonzero exit.
  Input uses stdin and cache is disabled; no fixture files remain.
- `mise exec -- clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli
  --dir test-quickdoc` passed (1 test, 4 assertions, exit 0, no failure EDN).
  Explicit formatting fix/check passed for both new files.
- Project lint still fails as expected: 35 errors and 12 warnings after
  the mapping (previously 126 errors and 1 warning). Next: remaining
  benchmark/test macros, custom assertion syntax and newly visible warnings,
  then project formatting and defect probes. No baseline completion claimed.

## Slice 2b — 2026-10-25

- Added a repository-only `with-temp-dir` analysis hook: its single-name
  binding vector becomes a lexical `let` for lint analysis. A plain `let`
  mapping cannot model the missing initializer. No runtime code or design
  contract changed.
- Real-linter tests verify the directory binding, unresolved body symbols
  and use outside the lexical scope. Focused Scry CLI passed: 2 tests,
  10 assertions, exit 0; `.scry-results` contains no failure files.
- Explicit formatting fix/check passed for the hook, lint config and
  tooling tests. `git diff --check` passed. Project lint still fails as
  expected with 32 errors and 12 warnings; benchmark macros, custom
  assertions and baseline cleanup remain. No full regression was run:
  this slice changes lint analysis only.

## Slice 2c — 2026-10-25

- Added repository-only `defpair` analysis as two `deftype` forms with
  implicit `x` and `y` fields. Both generated constructors are registered;
  method bodies retain diagnostics. No runtime or design contract changed.
- Real-linter tests cover protocol methods, field bindings, both constructors,
  unresolved method symbols and wrong constructor arity. Focused Scry CLI
  passed: 3 tests, 18 assertions, exit 0; no failure EDN files.
- Explicit formatting fix/check and `git diff --check` passed. Project lint
  still fails as expected (15 errors, 12 warnings). Next: `defpass`, custom
  assertions and baseline cleanup. Full runtime regression remains in slice 6.

## Slice 2d — 2026-10-25

- Added repository-only `defpass` analysis. Generated functions retain
  one-array or two-array arity, implicit element bindings and expression
  checks. Type substitution includes `T.`, `T` and metadata type hints;
  the first project lint run exposed the metadata case, now covered.
- Real-linter tests cover both generated functions, field and constructor
  expressions, unresolved symbols and wrong arities. Focused Scry CLI
  passed: 4 tests, 30 assertions, exit 0; no failure EDN files.
- Explicit formatting check and `git diff --check` passed. Project lint
  still fails as expected: 2 errors and 12 warnings, all in the compatibility
  tests. Custom assertions and baseline cleanup are next. Runtime source,
  design contract and dependencies are unchanged; full regression stays in
  slice 6.

## Slice 2e — 2026-10-25

- Custom assertion dispatch symbols are not vars. Added unresolved-symbol
  exclusions only within `clojure.test/is` in the compatibility namespace.
  Argument expressions and calls outside `is` retain diagnostics. No hook,
  runtime change, dependency change or design deviation was needed.
- Real-linter tests passed through the focused Scry CLI: 5 tests, 42
  assertions, exit 0, no failure EDN. Fixture filenames must match the
  namespace when testing namespace-scoped configuration.
- Explicit formatting check and `git diff --check` passed. Project lint
  now reports 0 errors and 12 warnings; it still fails as required.
  Next: intentional protocol-test exceptions, unused bindings, explicit
  test imports and project formatting. Full regression stays in slice 6.

## Slice 2f — 2026-10-25

- Applied project formatting to nine maintained files. `git diff -w
  --exit-code` passed before record updates: all code changes are whitespace
  only. No excluded paths changed. Design and dependencies are unchanged.
- Project fix/check and explicit-path check passed. Focused Scry CLI passed
  (5 tests, 42 assertions, exit 0); no failure EDN files. Runtime regression
  remains in slice 6.
- In a disposable archived checkout, a malformed-indent fixture caused the
  project check to fail and retained its SHA-256. Explicit fix then check
  passed. The first probe stopped at mise trust; the successful retry trusted
  only the disposable checkout through `MISE_TRUSTED_CONFIG_PATHS`.
- Next: resolve the 12 previously recorded lint warnings and finish the
  combined lint/configuration and defect checks. No lint completion claimed.

## Slice 2g — 2026-10-25

- Resolved the 12 lint warnings with explicit test imports, unused receiver
  names and form-local exceptions for five upstream partial-protocol types.
  HasMarkers also needs a method-local exception: clj-kondo associates its
  method with a following marker protocol. No global linter was disabled.
  Runtime behavior and the design contract are unchanged.
- Project lint and formatting check passed (zero lint findings). The affected
  compatibility namespace passed on preview-enabled Valhalla: 21 tests, 78
  assertions. Focused tooling tests passed: 5 tests, 42 assertions. Both CLI
  exits were zero and both runs left no Scry failure EDN.
- In a disposable archived checkout with these edits, an unresolved-symbol
  probe failed project lint without changing its bytes; removing it restored
  success. A formatting probe failed project check without changing its bytes;
  explicit fix/check and project fix/check then passed. Dependency imports
  were prepared by the lint task in that checkout.
- Slice 2 is complete. Next is the Quickdoc generator and its content/process
  tests. Full runtime, fallback and build regression remains in slice 6.

## Slice 3a — 2026-10-25

- Added Quickdoc generation from core only, with a no-doc override for the
  public expansion helper. No runtime source, dependency or design-contract
  change. The generated reference links to README and master source lines.
- File operations take an explicit path. Freshness compares exact UTF-8
  bytes and does not write. Tests use real temporary files and rendered
  Markdown: exact macro surface, arguments, policy, AOT boundaries, mutable
  fields, navigation and repeatability. No mocks or full-document snapshots.
- Focused Scry CLI on mise's non-preview tooling JVM passed: 7 tests,
  74 assertions, exit 0, no failure EDN. Project formatting, project lint
  (zero findings), and `bb api-docs --check` passed. Generation succeeded.
- Next slice must add real CLI failure tests for missing/stale output and
  invalid arguments, including useful diagnostics and unchanged output.
  File-level tests already cover missing/stale and repeated generation;
  the process-test checklist item remains open. Vale has not run yet.

## Slice 3b — 2026-10-25

- Added real `bb api-docs` process tests in a temporary project with the
  actual task, generator, dependency configuration and core source. Tests
  cover missing/stale output, invalid/extra arguments, repair diagnostics,
  unchanged bytes and modification times, successful repair and two identical
  generations. Temporary files and CLI caches are removed after the test.
- Focused Scry CLI on the non-preview tooling JVM passed: 8 tests,
  98 assertions, exit 0; no failure EDN files. Project formatting, lint
  (zero findings), API freshness and `git diff --check` passed.
- Slice 3 is complete. No production code, dependencies or design contract
  changed. Next: Vale configuration and real severity/setup failure probes.
  Process tests use the installed Babashka/Clojure tools and warm dependency
  caches; cold setup remains in slice 6.

## Slice 4a — 2026-10-25

- Added the pinned proselint configuration and ValueType vocabulary. Lambda
  exclusions match only lines that start with λ; Markdown code protection
  remains active. Corrected Clojure capitalization in README. No source,
  API, dependency or design-contract change.
- Real Vale sync and direct JSON checks of README, AGENTS and API passed
  (empty alerts, exit 0). Disposable-project probes verified lambda/code
  protection, spelling detection in ordinary AGENTS prose and unchanged
  input. Missing styles still return nonzero with `--no-exit`.
- Vale vocabulary entries enforce case: listing both Clojure and clojure
  made the latter win. Keep the canonical Clojure spelling only.
- API freshness and `git diff --check` passed. No Clojure code changed, so
  focused generator and runtime suites were not rerun. Next: implement
  `bb vale` with exact three-file scope, JSON validation and severity exit
  handling; add persistent real-process tests. Direct checks do not yet
  establish the shared command contract or complete slice 4.

## Slice 4b — 2026-10-25

- Added `bb vale` with task-local namespace loading, fixed three-file scope,
  explicit project config and no global config. Sync/process errors propagate;
  JSON shape and severity validation fail closed. Suggestions are printed as
  JSON but do not fail. Missing scoped files and extra arguments fail.
- Real Vale tests use small local rules in a temporary project, not mocked
  processes or network style fixtures. They verify all three severities, each
  scoped file, excluded files, lambda/code protection, unchanged input, missing
  styles, invalid config and missing input. Separate data tests reject invalid
  JSON and unknown severity. Production style sync passed on the baseline.
- Focused Scry CLI passed on the tooling JVM: 10 tests, 128 assertions, exit
  0, no failure EDN. Project formatting, lint (zero findings), Vale and API
  freshness passed. No further prose edits or regeneration were needed.
- Design contract, runtime code and dependencies are unchanged. Next: safe
  hooks and CI, including Linux installation verification. Full runtime
  regression remains in slice 6.

## Slice 5a — 2026-10-25

- Added local formatting-fix and project-lint hooks. File filters cover
  maintained Clojure/EDN paths and owned lint configuration; imports and
  historical paths do not trigger hooks. Scripts never stage files. The
  formatter exits without changes when no filenames are supplied.
- `bash test-tooling/hooks.sh` passed (exit 0) using real tools in an
  archived temporary Git repository. It checks clean files, formatting
  failure/repair, lint failure, partial staging, conflicting unstaged
  changes, unrelated edits, excluded paths and owned-config triggers.
  Index trees and file bytes confirm staging and restoration safety.
- No runtime code, dependencies or design contract changed. The shell
  integration test is separate from focused Clojure tests. CI, Linux tool
  installation and all slice 6 acceptance checks remain unverified.

## Slice 5b — 2026-10-25

- Added the pull-request/master quality workflow with read-only permissions,
  pinned mise bootstrap, uncached tool installation, version output, dependency
  imports and all five gates. An always-run final check detects tracked-file
  edits even after gate failure. No repair command runs in CI.
- Plan adaptation: mise installs both Java and Clojure from `mise.toml`;
  separate setup actions would duplicate installation and introduce PATH
  precedence risk. Design, runtime code and dependencies are unchanged.
- actionlint 1.7.7 passed. All five gate commands passed locally under mise:
  formatting, zero lint findings, Vale, freshness and 10 tooling tests with
  128 assertions. Scry exited zero with no failure EDN. `git diff --exit-code`
  confirmed checks left existing tracked files unchanged.
- This host is macOS; no Linux execution is claimed. Next: run the workflow
  on Linux and finish controlled CI failure probes, then slice 6. No remote
  workflow was dispatched or branch pushed during this pass.

## Slice 5c — 2026-10-25

- Added `bash test-tooling/ci-failures.sh`: real CI commands run in an
  archived disposable Git checkout. A passing baseline precedes injected
  formatting, lint, prose, stale/missing API, assertion and setup defects.
  Each failure must preserve the index tree and tracked working files.
  The formatter repair path also passes. No mocks or runtime changes.
- Verification passed on macOS (exit 0): all seven failure probes, all five
  baseline gates, and focused Scry tests (10 tests, 128 assertions). The
  deliberately failed test must produce its named Scry EDN artifact.
- Probe logs must be excluded from the disposable index; the first run
  exposed this test-harness issue, corrected before the passing run.
- Design scope is unchanged. Linux verification is not claimed. Podman has
  a running Linux VM available for the next slice; investigate that route
  before treating Linux access as blocked. Documentation, cold setup and
  the full runtime matrix remain in slice 6.

## Slice 5d — 2026-10-25

- Verified Linux installation in a new Podman Ubuntu 24.04 arm64 container,
  using `git archive HEAD`, no host cache mounts and no user configuration.
  Installed curl, Git, CA certificates, unzip, xz, Python/venv and build tools;
  bootstrapped mise with `MISE_VERSION=v2026.4.11`, trusted only `/work`, then
  ran `mise install`. All eight version reports match the plan.
- Ran the workflow's dependency preparation and five gate commands under
  `mise exec`: all exited zero. Tooling tests passed (10 tests, 128 assertions),
  with no Scry failure EDN. Imports, Maven/git dependencies and Vale styles
  downloaded from cold caches. `git diff --exit-code HEAD --` passed.
- Linux `bash test-tooling/ci-failures.sh` also passed all seven defect probes
  and tracked-file checks. Its first invocation lacked mise itself on PATH;
  adding `/root/.local/bin` fixed the container setup without code changes.
  The initial baseline and repeated tooling suite both passed.
- This verifies Linux arm64 commands, not the hosted x86_64 Actions runner
  or action orchestration. Prior actionlint validation remains applicable;
  workflow content is unchanged. Design and implementation need no changes.
  Next: README setup instructions, fresh-checkout acceptance and the runtime,
  fallback and build matrix in slice 6. No new tests were needed: existing
  real-process tests cover this verification slice.

## Slice 6a — 2026-10-25

- Added README setup, tool/runtime JVM separation, all task commands,
  scopes, generated-file rules, hook setup and edit/index behavior. Use
  mise activation or `mise exec`; unset `JAVA_CMD` for tooling. Added only
  tool names and Git/setup terms to the Vale vocabulary after real findings.
- All five quality gates passed under `mise exec` (exit 0): format, lint,
  Vale, API freshness and focused Scry tests (10 tests, 128 assertions;
  no failure EDN). No runtime code or dependency changes. Existing real-tool
  tests cover the unchanged behavior; no new test was needed for these docs.
- Design remains unchanged. Next: verify the README procedure in a fresh
  checkout, repeat deterministic generation/file-safety probes, and run the
  library/fallback/build matrix before final acceptance. Earlier Linux setup
  evidence does not replace verification of the newly written procedure.

## Slice 6b — 2026-10-25

- Full runtime regression passed after baseline formatting/lint edits. Used
  Temurin `1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1`, with explicit
  `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` executable paths.
  `JAVA_CMD` selected each host; no tooling alias was composed into these runs.
- Commands and results (all exit 0; no Scry failure EDN after each run):
  - Valhalla `clojure -M:test -m scry.cli`: 64 tests, 332 assertions.
  - Java 8 `clojure -M:fallback-test`: 15 tests, 166 assertions.
  - Valhalla `clojure -M:fallback-test`: 15 tests, 166 assertions.
  - Valhalla `clojure -J--enable-preview -M:fallback-test`: 15 tests,
    141 assertions.
  - Valhalla `clojure -M:build-test -m scry.cli --dir test-build`:
    6 tests, 63 assertions, including isolated artifact consumers.
- Copied existing `target/` to `/tmp/value-type-010-regression/target-backup`
  before build checks to protect local measurement output. Logs are in that
  temporary directory. `git diff --exit-code HEAD --` passed after all tests.
- No code, dependency or design-contract change was needed; existing tests
  cover this verification slice. Next: fresh-checkout README verification,
  final file-safety/determinism probes and acceptance evidence mapping.


## Slice 6c — Final acceptance, 2026-10-25

- Followed README setup in a fresh `git clone --no-local` of `12b8f7e`
  on macOS arm64, with empty `CLJ_CONFIG`, unset `JAVA_CMD`, no project
  caches/imports/styles, and `mise trust` then `mise install`. Host tools
  and Maven/git caches were warm; cold global installation evidence remains
  slice 5d. README prerequisites were already installed. No setup fix needed.
- All documented quality commands passed under `mise exec`, including
  dependency preparation, explicit/project format check and fix, lint,
  Vale, API generation/check, focused tests and pre-commit install/run.
  Focused tests: 10 tests, 128 assertions, exit 0, no Scry failure EDN.
- Missing/stale API checks failed without creating or changing output.
  Repair and two successive generations matched the original committed
  bytes. Both real-tool shell suites passed, including all seven CI defect
  probes and hook index protection. Check-only commands left tracked files
  unchanged; final `git diff --exit-code HEAD --` passed. No mocks used.
- Verification script reached `ACCEPTANCE_PASSED`; logs and disposable clone
  are at `/tmp/value-type-010-acceptance.jAooGw`. These are temporary evidence,
  not a dependency. No code, dependency or design change was needed.

### Acceptance evidence

1. Setup and commands: slice 6c fresh clone and empty user configuration;
   slice 5d cold Linux tools/dependencies. All baseline gates pass.
2. Defect detection and format repair: slices 2g, 4b and 5c; both shell
   integration suites repeated successfully in slice 6c.
3. API file safety and determinism: real-process tests in slice 3b and
   explicit missing/stale/repair/byte-comparison probes in slice 6c.
4. API surface, prose and links: focused content tests from slice 3a,
   included in the passing 10-test suite in slice 6c.
5. Hook and CI scope/safety: slices 5a–5d and repeated shell probes in 6c.
   Workflow syntax passed actionlint. Linux arm64 commands passed; no hosted
   Actions run or hosted x86_64 execution is claimed.
6. Development-only dependencies: slice 1 basis comparison and slice 6b
   library, three fallback configurations and build/consumer regression.
7. Contributor documentation: slice 6a README, verified without corrections
   in slice 6c. Design and approach remain unchanged.

All checklist items are complete. No implementation blocker or follow-up
was found. Keep the task open for implementation review; no closure is claimed.

## Implementation review — 2026-10-25

- No new actionable issues found; no follow-up steps added.

## Test review — 2026-10-25

- Added 3 steps to be addressed.

- 2026-10-24 review follow-up 1: Added owned Vale configuration/vocabulary protection coverage. Lambda and fenced-code inputs pass; ordinary AGENTS prose fails; bytes remain unchanged. Focused prose tests passed (3 tests, 35 assertions); no Scry failure files.
- 2026-10-24 review follow-up 2: Replaced synthetic assertion with a missing generator notice. Existing tests reject the defect; failure artifacts are found without a fixed filename. CI failure probes pass with tracked-file/index preservation; baseline 11 tests, 133 assertions.
- 2026-10-24 review follow-up 3: Added unused-binding warning probes through project lint and the lint hook. Real clj-kondo EDN confirms warnings and zero errors. Both shell suites pass; failed checks preserve file/index content. Addressed all 3 review steps.

## Test re-review — 2026-10-24

- No new steps. Focused tests passed (11 tests, 133 assertions); both shell integration suites passed.

## Documentation review — 2026-10-24

- Added 1 step to be addressed.

- 2026-10-24 documentation follow-up: addressed 1 review step. Added the
  Ramora quality workflow and index link. Checked commands and boundaries
  against source configuration and README; local links/anchors and
  `git diff --check` pass. Documentation only; runtime tests not rerun.

## Documentation re-review — 2026-10-25

- No new actionable issues. API freshness and Vale checks passed.

## Code-shape review — 2026-10-25

- Added 2 steps to be addressed.

- Code-shape follow-up 1: added shared scope validation before formatting or lint setup. Disposable CI probes remove a directory and a file; check, fix and lint fail with path diagnostics and preserve tracked files/index. Explicit formatting still passes. Formatting, lint and the full CI probe suite passed (working-tree snapshot; focused tests 11/133).

- Code-shape follow-up 2: extracted private pure type substitution with explicit type/form inputs. Real-linter coverage passes for `T` (added explicit case), `T.`, `^T`, both arities and retained diagnostics. Formatting and lint pass; focused Scry suite passes (11 tests, 135 assertions, exit 0, no failure EDN). Addressed both code-shape review steps; no blockers. Runtime code is unchanged; runtime suites were not rerun.

## Code-shape re-review — 2026-10-25

- Both follow-ups are resolved; no new steps.

## Closure — 2026-10-25

- Closed at the user's request after implementation, all review follow-ups
  and knowledge extraction completed. No unchecked steps or blockers remain.
- Final code-shape re-review: `e42350e`. Extracted knowledge:
  `mementum/knowledge/verifying-development-quality-checks.md` (`481c676`).
- Moved the task to `munera/closed/010-development-quality-checks` and
  removed it from the scheduled plan. Updated project state.
- Closure changes records only; no tests were rerun. Existing verification
  limits remain: no hosted Actions run, and no runtime regression rerun
  during the final tooling-only reviews.
