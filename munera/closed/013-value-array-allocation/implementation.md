# Implementation notes

## Design review — Architecture turn

- No architectural review feedback. Scope is unchanged; no design steps added.
- `ramora/META.md` and `doc/architecture.md` are absent. Reviewed against
  `AGENTS.md` and the architecture sources linked from `ramora/INDEX.md`:
  the array design, decisions, verification and implementation gates, plus
  the macro design and controlled-fallback boundary. Array decision D3 keeps
  allocation independent of diagnostic availability; preserve this boundary
  when task 014 extends the adapter.

## Design review — Ambiguity turn

- No ambiguity review feedback. Scope is unchanged; no design steps added.

## Design review — Inconsistency turn

- No inconsistency review feedback. Scope is unchanged; no design steps added.

## Review handoff

- The native allocation exception chains in
  `ramora/value-type-array-design/semantic-verification.md` (section 2) are
  evidence, not the public error contract. Use the failure rules in
  `ramora/value-type-array-design/decisions.md` for public validation. This
  distinction was intentional and was not classified as an inconsistency.

## Planning review — plan creation

- Created `plan.md` and `steps.md`; implementation has not started.
- Reviewed the plan for ambiguity with `steps.md` held read-only. Each slice
  has concrete actions and verification. The plan defines capability-first
  error precedence, separate allocation discovery/access, test placement,
  the six-configuration/three-policy matrix and isolated JAR consumers.
  No unresolved blocking ambiguity was found.
- Reviewed `design.md` for internal inconsistency and against the linked
  architecture, decisions, task 012 design, semantic report and implementation
  gates. No inconsistency was found. Broader architecture checks for diagnostics
  and sequence realization belong to tasks 014 and 015, not this task.
- Non-blocking design choices were resolved in the plan: error precedence and
  the smallest adapter strategy. Exact native eligibility for untested classes
  remains a stated limit, not a promise or an implementation blocker.
- Static verification only: local plan links and whitespace checked. Runtime
  tests were not run because this change adds planning records, not code.

## Shared plan-review — Ambiguity turn

- No new actionable ambiguity feedback after checking the plan and read-only
  steps against the referenced architecture, evidence, capability detector,
  JVM runners, packaged-consumer tests and documentation tooling. The prior
  planning review still applies; no design steps added. Static review only.

## Shared plan-review — Inconsistency turn

- No new actionable inconsistency feedback across the task files; no design
  steps added. Static review only; `steps.md` was read-only.

## Slice 1 — 2026-10-28

- Added the lazy adapter and explicit-seed public allocator. Access is checked
  with `MethodHandles.lookup().unreflect`, without allocating a probe array or
  changing module access. Diagnostics are not prerequisites.
- Capability discovery catches only missing classes, missing methods and denied
  access. The existing class detector supplies its known prerequisite failures.
  That detector provides detail, not a Throwable; array access failures retain
  their cause. Invocation unwraps `InvocationTargetException`; native rejection,
  unexpected failures and JVM errors propagate. No caller array or seed is cached.
- Pure checked-length tests cover the maximum integer without allocating it.
  Public tests cover ordered validation and one-long allocations at 0 and 1.
- Next: broaden native fixtures and selected-slot Clojure/Java access (slice 2).
  Supported library test commands now need the explicit internal export. The
  complete six-configuration/policy matrix remains in slice 3.
- Verification: EA3 `27-jep401ea3+1-1`, Clojure 1.12.6, macOS aarch64.
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test
  -m scry.cli --namespace value-type.array-test`: exit 0; 3 tests,
  71 assertions; no failure EDN files.
- With the same JAVA_CMD, `clojure -M:fallback-test --namespace
  value-type.array-boundary-test`: exit 0; 1 test, 28 assertions; no failure
  EDN files. Children use Java 8 and preview-enabled EA3 without exports.
  Repeated calls, precedence, causes and startup guidance passed.
- Java 8 namespace-loading check returned `:array-capability-realized? false`.
  Focused `bb clj-fmt check` passed; `bb clj-kondo:lint` passed with zero
  warnings/errors. Full regression and acceptance runs remain pending.

## Slice 2 — 2026-10-28

- Added all four fixture shapes at lengths 0, 1 and 3, including null/non-null
  reference payloads. Both access paths check each of three selected indices
  with a fresh array per accepted, null or wrong-type store. Native exception
  classes and all slots are checked. Zero-field values have no distinct replacement.
- Java fixtures compile with the running JDK into cleaned temporary directories;
  the independent Java value class allocates without macro registration.
  Test method handles preserve native exceptions. They need explicit int indices
  and caller lookup (public lookup does not use the unnamed-module export).
- Restriction and atomicity checks use only non-null arrays. The two-long
  non-flat layout check is separate from semantic checks. No production changes.
- Verification: EA3 `27-jep401ea3+1-1`, Clojure 1.12.6, macOS aarch64.
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test
  -m scry.cli --namespace value-type.array-test`: exit 0, 9 tests,
  398 assertions, no failure EDN files. Focused formatting and full clj-kondo
  checks passed (zero warnings/errors). Full regression remains for acceptance.
- Next: slice 3 configuration/policy matrix. Semantic tests require the matching
  JDK compiler as well as preview and exports; Java sources are test-only.

## Slice 3 — 2026-10-28

- Added baseline-safe child checks and 36 fresh processes per matrix run:
  six startup configurations × three fallback policies × two require orders.
  Child EDN includes runtime, flags, policy, order and allocation/error results.
  Unsupported rows never define value fixtures. Repeated failures preserve
  capability-first reasons and causes; supported calls return independent arrays.
- Layout checks are separate from semantic checks. The one-long fixture is
  flat with flattening enabled and non-flat when disabled on EA3. Both runs
  exercise all existing Clojure/Java selected-slot, fixture and restriction tests.
  No production change or design deviation was needed.
- Verification: macOS aarch64, Clojure 1.12.6, Temurin 1.8.0_492-b09 and
  Valhalla 27-jep401ea3+1-1. Executables use the existing runner defaults.
  All commands below exited 0 with no `.scry-results/*.edn` failure files:
  - EA3 `clojure -M:fallback-test --namespace value-type.array-matrix-test`:
    1 test, 612 assertions.
  - EA3 `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
    -J-XX:+UseArrayFlattening -M:test -m scry.cli
    --namespace value-type.array-test --namespace value-type.array-layout-test`:
    10 tests, 399 assertions. Repeated with `-J-XX:-UseArrayFlattening`:
    10 tests, 399 assertions. The test alias adds preview.
  - `clojure -M:fallback-test` with Java 8 and EA3 selected by `JAVA_CMD`:
    17 tests, 806 assertions each. EA3 `clojure -J--enable-preview
    -M:fallback-test`: 17 tests, 781 assertions. Each full suite reruns the matrix.
  - Focused `bb clj-fmt check` and full `bb clj-kondo:lint` passed,
    with zero lint warnings/errors.
- Next: slice 4 must extend isolated JAR consumers, not reuse checkout paths.
  Matrix checks are test-only; no Java fixture or new dependency enters the JAR.


## Slice 4 — 2026-10-28

- Extended the existing isolated macro consumer rather than adding checkout
  paths. Both array resources must resolve from the exact built JAR. Its
  classpath contains only that artifact and dependencies from its packaged POM.
- Supported EA3 checks initial contents, component, length, Clojure stores and
  reads, Java reflective reads, and native null/type rejection with unchanged
  contents. Java 8 and preview-disabled EA3 repeat unavailable calls before
  defining any fallback fixture. Existing macro assertions remain intact.
- Source payload checks explicitly require both array namespaces. Exact archive
  membership excludes tests, Java fixtures and compiled classes.
- Verification: macOS aarch64, Clojure 1.12.6, Valhalla 27-jep401ea3+1-1,
  Temurin 1.8.0_492-b09. Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
  clojure -M:build-test -m scry.cli --dir test-build`. Exit 0, 9 tests,
  101 assertions, no failure EDN files. Supported child flags are preview and
  the internal-value export; both control children omit them.
  Focused formatting and full clj-kondo checks passed with no warnings/errors.
- Next: slice 5 documentation and full acceptance. No production code,
  dependency or public contract changed in this slice.

## Slice 5a — 2026-10-28

- Added the explicit-seed usage guide and README link. No API or contract
  change. The guide separates native store exceptions from ordered library
  validation, and documents slot ownership, flags and unsupported features.
- Ran the guide's forms with assertions for length, initial contents and the
  selected-slot update on EA3 `27-jep401ea3+1-1`, Clojure 1.12.6, with
  `-J--enable-preview -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
  All passed, exit 0. `mise exec -- bb vale` and `git diff --check` passed.
  The README allocation anchor and usage-guide relative links resolve.
- Documentation-only slice; no new test code or runtime suite run. Existing
  real-JVM semantic tests cover the example contract. Next: Quickdoc source
  paths/content tests, runtime commands, architecture/release status, then
  the full acceptance matrix. Do not mark final acceptance complete from
  this example check.

## Slice 5b — 2026-10-28

- Quickdoc now includes the allocator. Rendered-output tests cover its signature,
  flags, validation order, seed requirement and ownership limits. The isolated
  CLI fixture copies the array source too; no optional JVM API is loaded.
- Marked startup flags as code in the source docstring after Vale treated the
  internal package name as prose. No runtime behavior or contract changed.
- On the mise tooling JVM (Temurin 21), API generation/freshness, formatting,
  clj-kondo (zero warnings/errors), Vale and whitespace checks passed. Command:
  `mise exec -- clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc`.
  Exit 0; 11 tests, 182 assertions; no Scry failure files. Tests use real
  generation and files, not mocks. Runtime suites were not repeated.
- Next: runtime test commands, release/architecture status, then full acceptance.

## Slice 5c — 2026-10-28

- Runtime guidance now supplies the explicit export for library tests and the
  REPL, matching-compiler requirements, the isolated array matrix command and
  both flattening controls. Baseline commands remain preview-free. Build
  guidance separates supported allocation from unsupported loading controls.
- Verified the documented disabled-flattening command on EA3
  `27-jep401ea3+1-1`, Clojure 1.12.6, macOS aarch64: exit 0, 10 tests,
  399 assertions. The documented matrix command with explicit EA3 and Temurin
  `1.8.0_492-b09` executable paths passed: exit 0, 1 test, 612 assertions.
  Each CLI run left no Scry failure files. Vale and `git diff --check` passed;
  the new usage link resolves to `value-array-allocation`.
- Documentation only; no test or runtime code changed. Full library, fallback
  and packaging acceptance has not been repeated in this slice. Next update
  release/architecture status, then retain the full acceptance matrix.

## Slice 5d — 2026-10-28

- Release notes and architecture now separate the implemented allocator from
  pending diagnostics and sequence construction. The remaining gates retain
  native diagnostic safety constraints; public allocation assertions now cite
  task 013 tests instead of only direct-JVM probes.
- No API, runtime code, dependency or test changed. Existing real-JVM coverage
  supports these status updates; no new runtime results are claimed.
- Verification: `mise exec -- bb vale` and `git diff --check` passed. All 52
  relative link targets in changed documents exist; the allocation-guide
  anchor resolves. Runtime suites were not repeated in this documentation slice.
- Next: run and retain the combined library, matrix, fallback, build and
  tooling results before writing `acceptance.md`. Prior slice results are
  not a replacement for that final checklist. No blocker is known.

## Slice 5e — Final acceptance

- Completed the combined acceptance runs without code or contract changes.
  Exact environments, commands, CLI summaries, exit codes and checked failure
  file sets are retained in `acceptance.md` and `evidence/`.
- All runtime and quality checks passed. No new tests were needed: existing
  real-JVM tests cover all seven design criteria. No blocker was found.
- Next review should use the bounded EA3 matrix in the acceptance record;
  completion does not extend support to other JVMs or implement tasks 014/015.

## Implementation review — follow-ups

- Added 2 steps to be addressed.

## Review follow-up 1 — 2026-10-28

- Addressed the release runtime export step. Unsupported-runtime controls are unchanged.
- Corrected workflow command passed locally on EA3: 74 tests, 731 assertions,
  exit 0, no Scry failure files. Actionlint 1.7.12 and whitespace checks passed.
  Hosted Actions and publication were not run.

## Review follow-up 2 — 2026-10-28

- Addressed the architecture status step. Both stale sections now link completed
  acceptance; diagnostics and sequence construction remain pending. No other
  contradictory allocation-status claim was found in maintained architecture pages.
- Changed-page relative link targets, Vale and whitespace checks passed.
  Documentation only; no additional runtime run. Both review steps are complete.

## Implementation re-review — 2026-10-28

- No new actionable issues.

## Test review — 2026-10-28

- Added 2 steps to be addressed.

## Test review follow-up 1 — 2026-10-28

- Addressed 1 review step. EA3 rejects negative native lengths with
  `IllegalArgumentException`; subsequent public allocation succeeds.
- Focused Scry test passed: 1 test, 3 assertions, exit 0, no failure files.
  Removing cause unwrapping failed the exception-class assertion (exit 1);
  restored production code and reran successfully. Formatting passed.

## Test review follow-up 2 — 2026-10-28

- Addressed 1 review step. Validation tests now check component, length and
  reason where relevant, distinguish null and incompatible seeds, and reject
  invented index data or slot/index messages at lengths zero and one.
- EA3 with preview and exports: array and layout Scry tests passed
  (11 tests, 447 assertions, exit 0, no failure files). Formatting, lint and
  whitespace checks passed. Full acceptance was not repeated.

## Test re-review — 2026-10-28

- No new actionable issues.

## Test review verification — additional pass

- Prior review result unchanged; no steps added. Repeated focused array/layout
  tests (11 tests, 447 assertions) and the 36-process matrix (1 test,
  612 assertions): exit 0, no Scry failure files. Full acceptance not repeated.

## Documentation review — 2026-10-28

- Added 2 steps to be addressed.

## Documentation review follow-up 1

- Addressed 1 review step. API sources and public entries now match the
  generator, generated reference and content tests; exclusions remain explicit.
- API freshness and whitespace checks passed. Direct page Vale check found
  four existing errors outside the changed paragraph. Runtime tests were not run.

## Documentation review follow-up 2

- Addressed 1 review step. Packaging now separates class/fallback requirements
  from array exports and no-fallback behavior, and describes the existing
  isolated consumer coverage with current guide links.
- Page links/anchors, changed-text Vale, project Vale and whitespace checks
  passed. Three unrelated existing page-level Vale errors remain. Runtime
  tests were not repeated; no code or test changed. Both review steps are done.

## Documentation re-review

- No new actionable issues.

## Code-shape review

- Added 1 step to be addressed.

- Code-shape review follow-up: addressed 1 review step. The boundary test now
  uses shared baseline and Valhalla configurations; expected reasons, causes
  and assertions remain local. Verification: set `VALUE_TYPE_BASELINE_JAVA`
  to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `VALUE_TYPE_VALHALLA_JAVA` to
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then ran
  `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test --namespace value-type.array-boundary-test --namespace value-type.array-matrix-test`.
  Scry passed 2 tests and 640 assertions, exit 0, no failure files.
  Focused formatting, project lint and whitespace checks passed.
  Production code is unchanged; full acceptance was not repeated.

## Code-shape re-review

- No new actionable issues.

## Closure — user request

- Closed after implementation, final acceptance, review follow-ups and knowledge
  extraction. All checklist items are complete. Moved the task to
  `munera/closed/`, removed it from the active plan and updated incoming links
  and working memory.
- Closure changes records only. Runtime tests and hosted workflows were not
  rerun. Recorded JVM scope and verification limits remain. Diagnostics and
  sequence construction belong to tasks 014 and 015.
