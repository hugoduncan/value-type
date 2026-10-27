# State

## Project

value-type: a Clojure library. It supplies the `defvaluetype` macro.
The macro creates JEP 401 value objects
(https://openjdk.org/jeps/401). The macro is as compatible with
`deftype` as possible.

## Status

- The repository is initialised with ramora, mementum and munera.
- The design is in `ramora/defvaluetype-design.md` (status: transform, loader and macro implemented and tested).
  Approach: expand to `deftype`, catch the bytes with a capture loader
  in the thread-bound `Compiler/LOADER`, change them into a value class
  with `java.lang.classfile`.
- The design assumptions are verified on Valhalla EA
  `openjdk-27-jep401ea3+1-1` (JDK at
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). Results:
  `ramora/defvaluetype-design/verification.md`. Working spike code:
  `spike/valhalla/`.
- The project scaffold is done (`munera/closed/001-scaffold-project`): `deps.edn`
  (aliases `:test`, `:nrepl`), environment test `value-type.environment-test`, README
  "Development" section. Select the JDK with `JAVA_CMD`.
- Tests: `clojure -M:test -m scry.cli`. REPL: `clojure -M:test:nrepl`.
- The transform is done (`munera/closed/002-value-class-transform`):
  `value-type.impl.transform/->value-class` (bytes → bytes, with the
  D6 constructor check), tests in `value-type.impl.transform-test`.
- The loader is done (`munera/closed/003-capture-loader`):
  `value-type.impl.loader/install!` (call at macroexpansion time),
  tests in `value-type.impl.loader-test`.
- The macro is done (`munera/closed/004-defvaluetype-macro`, JDK 25
  run done): `value-type.core/defvaluetype` and
  `check-value-class`, tests in `value-type.core-test`. Shared test
  helpers and the `vt-type` fixture: `value-type.test-support`. README has
  "Usage" and "Differences from `deftype`".
- The compatibility corpus is done (`munera/closed/005-deftype-compat-tests`,
  no `src/` change): `value-type.deftype-compat-test` (the `deftype`
  forms of the Clojure 1.12.6 test suite, with the AOT checks CLJ-979
  and CLJ-1208). No form is excluded. The new-JVM helper is
  `value-type.test-support/run-in-new-jvm` (`dir` on the test
  classpath; returns the EDN of the last `prn`).
- The method-call benchmark is done
  (`munera/closed/006-method-call-benchmark`, no `src/` change):
  `value-type.bench` in `bench/`, run with
  `clojure -M:bench -m value-type.bench`. Results in
  `ramora/defvaluetype-design/benchmark.md`: `interface` and
  `protocol` ratios 1.14 (slower) and 1.00 (equal), respectively;
  A-21 is `contested`, A-28 records the ratios.
  Traps: one captured value object lets the JIT fold the call
  (measure over many instances); criterium's
  `:stats :stats :elapsed-time :median` is per batch (divide by
  `:batch-size`).

- The object-size comparison is done
  (`munera/closed/007-object-size-comparison`). Criterium
  `0.5.251-ALPHA` uses the packaged native agent via `extract-agent!`
  and `-agentpath` on Valhalla/macOS aarch64. For 0–3 primitive `long`
  fields, shallow sizes are `[16 24 32 40]` bytes for `deftype` and
  `[16 32 40 48]` for `defvaluetype`. Evidence is preserved in
  `ramora/defvaluetype-design/object-size/`. These results apply only
  to the recorded VM setup. A follow-up investigation identified the cause:
  a VM-injected 1-byte null marker plus 7 bytes of alignment padding.
  Disabling `UseNullableNonAtomicValueFlattening` removes the difference
  for these cases. Agent-free VM layouts, fresh Criterium measurements and
  plain Java instrumentation controls agree. See
  `ramora/defvaluetype-design/object-size-cause.md`; reproduction is in
  `spike/object-size/`. Production code is unchanged.
  Reference-field follow-up: 1–3 `Object` fields also show +8 bytes with
  defaults, but one reference retains its atomic nullable layout when only
  non-atomic nullable flattening is disabled. Disabling both nullable
  options removes all three differences. Null/non-null fields agree;
  Clojure and Java controls agree. Details and retained layouts:
  `ramora/defvaluetype-design/object-size-references.md`.
  The original task implementation and all reviews are complete.
  Knowledge extraction updated `mementum/knowledge/benchmarking-value-types.md`
  in commit `faeb15b`.

- The JAR build is done (`munera/closed/008-jar-build`). Use
  `clojure -T:build jar` for a source-only `org.hugoduncan/value-type`
  artifact with version `0.1.<git commit count>` and EPL-2.0 metadata.
  Both `jar` and `clean` delete `target/`, including benchmark output.
  Value-class consumers need a supported Valhalla JDK with
  `--enable-preview`; task 009 adds ordinary-JVM fallback.
  Installation and publication are out of scope. All reviews and knowledge
  extraction are complete. Artifact-test lessons are in
  `mementum/knowledge/testing-library-artifacts.md` (commit `5282c0a`).

- Controlled fallback is complete (`munera/closed/009-controlled-deftype-fallback`).
  Startup property `value-type.fallback` selects `silent` (default), `warn`
  or `error`; it is validated once per loaded library runtime. Invalid
  values fail; later changes are unsupported. Capability detection and
  lazy dependencies allow fallback without optional JDK APIs.
- Fallback uses ordinary `deftype` semantics. Warn emits once per library
  runtime; strict attempts throw Java `Error`. Policy applies at expansion,
  compilation and evaluation. AOT representation is fixed: top-level forms
  enforce policy during initialization; forms inside functions enforce it
  on invocation. Direct class access bypasses these guards. Failed AOT
  initialization can produce `NoClassDefFoundError` on retries.
- Fallback adds no JDK minimum beyond supported Clojure. Verified coverage
  is Java 8 (`1.8.0_492-b09`) and Valhalla `27-jep401ea3+1-1` with preview
  off/on, not future JVM formats. Use
  `JAVA_CMD=<java> clojure [-J--enable-preview] -M:fallback-test`.
- Implementation review and knowledge extraction are complete. Lessons:
  `mementum/knowledge/controlled-fallback-boundaries.md` (7251bcd),
  `java-lang-classfile.md` (030aee5) and
  `testing-library-artifacts.md` (854e76d). Detailed decisions and evidence
  remain in task 009's implementation notes and the fallback architecture.

- Development quality checks are complete
  (`munera/closed/010-development-quality-checks`). Pinned mise tools and
  Babashka tasks cover cljfmt, clj-kondo, Vale and Quickdoc. Pre-commit hooks
  never stage edits; quality CI checks do not repair files. Setup, scopes
  and commands are in `doc/development.md` and its linked guides, with
  architecture notes in the Ramora quality-workflow documentation.
  All implementation and review follow-ups are complete. Knowledge extraction
  added `mementum/knowledge/verifying-development-quality-checks.md`
  (`481c676`). Hosted GitHub Actions execution remains unverified; cold Linux
  checks passed in an Ubuntu 24.04 arm64 container.

- Release tooling is complete
  ([task 011](../munera/closed/011-release-workflow/design.md)). Local preparation
  stamps and commits changelog notes; the manual workflow defaults to preview.
  Publication uses stock deps-deploy 0.2.2 and Git/gh, with typed-false gates,
  step-scoped secrets and explicit Java 8/Valhalla verification. Operator guide:
  `doc/development/release.md`; architecture: `ramora/release-workflow.md`.
  All implementation and review follow-ups are complete. Knowledge extraction
  added `mementum/knowledge/release-identity-and-failure-boundaries.md`
  (`e398ff3`) and updated artifact-test JVM guidance (`90f6eba`). The task is
  closed at the user's request. Hosted Actions and live publication remain
  unverified; no project push, dispatch, secret setup or publication occurred.

## Next

- Task 013 (value-array allocation) is closed at the user's request after
  implementation, final acceptance, review follow-ups and knowledge extraction.
  Artifacts: `munera/closed/013-value-array-allocation/`. The public explicit-seed
  allocator is implemented; capability, component, length and seed checks run
  in that order. Native semantics, fresh-JVM controls, packaged consumers and
  quality checks passed within the recorded EA3/Java 8 scope.
- Tasks 014 (diagnostics) and 015 (sequence construction) remain design-only
  and need execution plans. Both depend on completed task 013; 015 does not
  depend on 014. Task 012 is also closed. Older slice and review notes below
  are historical progress, not current task status. Closure changed records
  only; no runtime tests were rerun.
- Proposed array API design is in `ramora/value-type-array-design.md`.
  Closed task `munera/closed/012-verify-value-arrays/` verified direct JVM
  allocation, seeds, stores, diagnostics and startup controls on EA3.
  Null native diagnostic inputs crash that build; public validation must
  reject them before invocation. Public allocation is implemented; diagnostics
  and sequence construction remain unimplemented.
  Knowledge extraction is in `mementum/knowledge/value-array-jvm-boundaries.md`
  and `mementum/knowledge/validating-retained-jvm-evidence.md`.
- Typed-array investigation (2026-10-26): ordinary one-long typed value
  arrays are not flat on EA3. Internal null-restricted atomic arrays are
  flat: at 1,048,576 elements, storage is 8 MiB versus 28 MiB for identity
  arrays and 36 MiB for ordinary value arrays. Current-thread allocation
  is about 8/28/36 bytes per element. Disabling array flattening removes
  the benefit. Six successful extended timing runs give median traversal
  times 0.239 ns/element flat, 0.305 identity, 0.360 ordinary value (about
  22% less time flat versus identity). Short screening runs had warnings;
  the extended runs did not. One interrupted trial was excluded and rerun.
  Report:
  `ramora/defvaluetype-design/typed-array.md`; probe: `spike/typed-array/`.
  This does not establish a benefit for ordinary Clojure collection storage
  or wider value classes. Production code is unchanged.
- Interface-call slowdown investigated (2026-10-24 UTC): no stable penalty
  in 16 fresh JVMs with alternating order, two lengths and Java controls.
  Clojure median ratios were 1.028 / 1.008; Java 1.015 / 0.998. Paired main
  C2 loop bytes matched in separate diagnostic runs. The unchanged full
  benchmark also passed its equality and scaling bands. Historical 1.14
  cause remains unknown; do not treat it as a general JEP 401 penalty.
  Report and retained evidence: `ramora/defvaluetype-design/interface-call.md`.
  Reproduction: `spike/interface-call/`. Production code unchanged.
- Other adjacent task (not yet created): nested `defvaluetype` in a method
  body on the value-class path (needs a loader change).

## Related

- `ramora/INDEX.md`
- `munera/plan.md`
- `ramora/defvaluetype-design/fallback.md`
- `mementum/knowledge/controlled-fallback-boundaries.md`

## Latest verification

Task 011 is closed after implementation, review follow-ups and knowledge
extraction. Full local acceptance passed: library 64 tests/332 assertions;
fallback Java 8 and Valhalla preview off 15/166 each, preview on 15/141;
build 9/96 with both isolated consumer JVMs; tooling 11/172; release 11/112.
Quality checks, local preview/build and actionlint passed. Linux x86_64 JVM
archives and the value-capability probe passed in emulated Ubuntu 24.04.
Detailed evidence: `munera/closed/011-release-workflow/acceptance.md` and
`linux-jvms.md` in the same directory.

The later count-guard test and mutation check passed; the final release suite
passed 12 tests/123 assertions, exit 0, with no Scry failure files. All final
reviews found no new actionable issues. They did not repeat the full matrix.
Closure changed records only; no tests were rerun. Hosted execution, permissions
and publication remain unverified.

## Documentation layout

Development instructions moved from README to `doc/development.md`, with
short guides in `doc/development/quality-checks.md` and
`doc/development/runtime.md`. README retains a link. Vale covers all three
new documents; maintained architecture links point to the new headings.
Verification after the move: formatting, lint, Vale, API freshness, local
Markdown links/anchors and whitespace checks passed. Focused tooling tests
passed (11 tests, 153 assertions, no Scry failure files). Runtime tests were
not rerun; runtime code did not change.

README now introduces the problem, experimental status, quick start,
limitations and AI usage. Detailed fallback, AOT and error guidance is in
`doc/usage.md`, linked from README and the generated API reference and
included in Vale scope. Formatting, lint, Vale, API freshness, local links
and whitespace checks passed. Focused tooling tests passed (11 tests,
160 assertions, no Scry failure files). Quick-start forms passed on Valhalla
with preview and on the tooling JVM with fallback. Full runtime suites were
not rerun. The user's staged readme-writing skill was left unchanged.

## Task 011 test review

The test review follow-up is complete. A real CLI test uses a one-shot
post-commit hook to verify count-mismatch failure and retained local state.
Removing the count guard failed the exit-status and diagnostic assertions.
With the guard restored, release tests passed: 12 tests, 123 assertions,
exit 0, no Scry failure files. Formatting, lint and whitespace checks passed.
Runtime/build suites were not repeated. No network writes or publication ran.

Task 011 test re-review found no new actionable issues. The completed count-guard
follow-up remains covered. Focused release tests passed again: 12 tests,
123 assertions, exit 0, no Scry failure files. Build/runtime suites and hosted
publication were not rerun. No new checklist items were needed.

Task 011 documentation review found no new actionable issues. Release commands,
operator guidance, changelog and architecture agree with the code and workflow.
No follow-up steps were added. This was a static review; tests and hosted
publication were not run.

Task 011 code-shape review found no new actionable issues. Reviewed release
parsing, preparation/export, build/deployment, workflow wiring and their tests
and guides. No follow-up steps were needed. This was a static review; test
suites and hosted publication were not run.

## Task 012 implementation

Slice 1 is complete. Discovery evidence is in
`ramora/value-type-array-design/evidence/discovery/`. Diagnostic access needs
exports but the Object-array query does not need preview. Allocation and
store semantics remain untested; slice 2 is next.

Task 012 slice 2a: allocation and initial-content checks passed (59 observations)
on EA3. Evidence is in `ramora/value-type-array-design/evidence/allocation/`.
Valid seeds work at length zero; null and wrong seeds do not. Primitive long
reports value status true but allocation rejects the boxed seed. Stores, Java
access, diagnostics and the full fresh-JVM matrix remain open.

Task 012 slice 2b: 60 real-JVM Clojure/Java store checks passed, plus the
59 allocation checks. Null and wrong-type stores reject without changing
contents. Evidence: `ramora/value-type-array-design/evidence/stores/`.
Diagnostics are next; the full JVM matrix and report remain open.

Task 012 slice 2c: array diagnostics passed (69 observations plus 59 allocation
checks). All three null queries crash EA3 with SIGSEGV; separate JVMs retain
fatal logs. String and primitive long-array queries return false for all three
methods. Evidence: `ramora/value-type-array-design/evidence/diagnostics/`.
Slice 3 must model fatal outcomes separately; the full matrix and report remain open.

Task 012 slice 3a adds validated `:fatal` process records for the six isolated
queries. Two diagnostic runs passed; a wrong-exit evidence mutation failed.
Evidence: `ramora/value-type-array-design/evidence/isolated-validation/`.
The full startup/flattening matrix and final report remain open.

Task 012 slice 3b: default and disabled-flattening semantic suites passed twice.
Contents, store exception classes and non-layout diagnostics agree; exception
messages differ and are retained. Null diagnostic crashes persist with flattening
disabled. Evidence: `ramora/value-type-array-design/evidence/layout-comparison/`.
Startup controls, aggregate matrix validation and the final report remain open.

Task 012 slice 3c: five startup controls passed twice with identical validation
summaries. Strict value creation rejects disabled preview and Java 8; allocation
without exports rejects access. Evidence: array-design `evidence/startup/`.
Aggregate matrix integration and the final report remain open.

Task 012 slice 3d: the aggregate startup/semantic matrix passed twice with
identical summaries and source hashes. Missing-result validation failed as
expected. Retained evidence: array-design `evidence/matrix/`. Slice 3 is complete;
the explanatory report, architecture gates and acceptance mapping remain open.

Task 012 slice 4a: reproduction README revised and retained matrix revalidated.
No new JVM run. Report, design gates, index links and acceptance mapping remain.

Task 012 slice 4b: six-question semantic report is written at
`ramora/value-type-array-design/semantic-verification.md`. Retained matrix
validation and report links pass; no JVM rerun. Architecture gates, index
links and acceptance mapping remain. Report commit: `3f78aaf`.

Task 012 slice 4c completes design gates, index links and acceptance mapping.
All implementation checklist items are done; the task remains open for review.
EA3 null diagnostics require public validation; primitive value status is not
allocation eligibility. Public API implementation remains separate. Retained
matrix validation and local links/anchors passed; no new JVM run in this slice.

Task 012 review follow-up 1: retained validation now checks required case contracts
and startup runtime/flags. Five copy-based checks pass; read-only validation is next.

Task 012 review follow-up 2 completes read-only evidence validation. Creation is
separate; matrix fatal-log references are portable. All six copy-based checks
pass and confirm unchanged evidence bytes. No JVM experiments were rerun.

Task 012 test review adds one open follow-up for selected-slot store checks.
Current valid writes replace all slots before checking; rejected writes stop at
index zero. The six retained-evidence regression checks passed. No JVM probes
were rerun. See the task checklist for the required coverage.

Task 012 selected-slot review follow-up is complete. Each index is tested on a
fresh array through Clojure and Java access, with all slots checked after each
accepted or rejected write. Two final full matrices passed 120 store cases per
layout with identical summaries and source hashes. Six evidence regression
checks passed. Current evidence: array-design `evidence/matrix-selected-slots/`.
Production code and historical evidence are unchanged.

Task 012 isolated-process configuration follow-up is complete. Aggregate
validation checks runtime and layout flags for all 12 isolated processes.
All 55 copy-based checks passed and preserved evidence bytes; formatting and
lint passed. No JVM probes were rerun; retained evidence is unchanged.

Task 012 test review found one open startup-status validation gap. All 55
existing copy-based checks passed, but a baseline allocation changed to
unresolved still passed aggregate validation. The task checklist records the
follow-up. No JVM probes were rerun.

Task 012 startup-status review follow-up is complete. Baseline allocation now
requires pass status and the expected value. All 57 copy-based checks passed
and preserved evidence bytes, including unresolved and rejected status changes.
Formatting and lint passed. No JVM probes were rerun; retained evidence and
production code are unchanged.

Task 012 current-evidence link follow-up is complete. The Ramora index, array
design and verification page now link to `evidence/matrix-selected-slots/`,
consistent with the probe README and semantic report. Linked paths and retained
matrix validation passed. Historical evidence is unchanged; no JVM probes ran.

Task 012 test review found one open signature-validation gap. All 57 existing
copy-based checks passed, but empty discovered signature lists in all four
Valhalla startup records still passed aggregate validation. The task checklist
records the follow-up. No JVM probes were rerun.

Task 012 signature-validation review follow-up is complete. Startup validation
requires allocator and diagnostic parameter/return types and rejects a seed-free
atomic overload without excluding unrelated methods. All 117 copy-based checks
passed and preserved evidence bytes. Formatting and lint passed. No JVM probes
were rerun; production code and retained evidence are unchanged.

Task 012 documentation review follow-ups are complete. The probe README now
describes per-file environment provenance and optional unresolved-case inputs.
The Unreleased note links the commands and warns about isolated JVM crashes;
no public array API was added. Retained matrix validation passed. No JVM probes
were rerun. Both review checklist items are complete.

Task 012 code-shape follow-up is complete. Reusable allocation and discovery
helpers load without probe execution or file writes. Explicit entry-point calls
preserve allocation-first checks and independent Java 8-safe discovery. Six
helper-loading JVMs, standalone discovery, the full matrix and 117 evidence
regression checks passed. Historical evidence and production code are unchanged.


Task 013 final acceptance passed. Implementation is complete; task remains open
for review. The task-local acceptance record and evidence retain the full EA3,
Java 8, fallback, packaging and tooling results. No production code changed in
this pass. Diagnostics and sequence construction remain tasks 014 and 015.

Task 013 implementation review found two open follow-ups: the release runtime
command lacks the required array export, and two architecture sections retain
pending acceptance claims. See the task checklist. Focused array/layout tests
passed with exports (10 tests, 399 assertions); the no-export reproduction
failed as expected. Full acceptance and hosted Actions were not rerun.

Task 013 release-command review follow-up is complete. The runtime command now
exports the internal value API; unsupported controls are unchanged. Local EA3
library tests passed (74 tests, 731 assertions, exit 0, no Scry failure files),
as did actionlint 1.7.12. Architecture status reconciliation remains open.
Hosted Actions and publication were not run.

Task 013 architecture-status review follow-up is complete. Both stale acceptance
claims now link the completed acceptance record. Diagnostics and sequence
construction remain pending. Maintained architecture status, changed-page link
targets, Vale and whitespace checks passed. Both implementation-review steps
are complete; task 013 remains open.

Task 013 implementation re-review found no new actionable issues. Both prior
follow-ups are complete; no checklist items were added. Focused array/layout
checks passed (10 tests, 399 assertions), as did the 36-process startup matrix
(1 test, 612 assertions). Both CLI runs exited 0 with no Scry failure files.
Packaging and documentation were reviewed statically; full acceptance and
hosted Actions were not rerun. Task 013 remains open.

Task 013 test review added two open follow-ups; see the task checklist.
Focused array/layout tests passed (10 tests, 399 assertions), and the
36-process matrix passed (1 test, 612 assertions). Both CLI runs exited 0
with no Scry failure files. Packaging and API-generation coverage were
reviewed statically; full acceptance was not rerun. Production code and
tests are unchanged.

Task 013 native-failure test review follow-up is complete. The real EA3
allocator rejects negative lengths with IllegalArgumentException. The test
passes (1 test, 3 assertions) and fails when cause unwrapping is removed.
Production code is unchanged. Validation-context coverage remains open.

Task 013 validation-context test review follow-up is complete. Both test-review
steps are done. Array/layout tests passed on EA3 with preview and exports:
11 tests, 447 assertions, exit 0, no Scry failure files. Formatting and lint
passed. Production code is unchanged; full acceptance was not repeated.

Task 013 test re-review found no new actionable issues. Both test-review
follow-ups are covered; no checklist items were added. Focused array/layout
checks passed (11 tests, 447 assertions), and the 36-process startup matrix
passed (1 test, 612 assertions). Both CLI runs exited 0 with no Scry failure
files. Packaging and API-generation coverage were reviewed statically;
full acceptance was not repeated. Production code and tests are unchanged.

Task 013 documentation review added two open follow-ups for the artifact
architecture page. See the task checklist. Static review checked the usage,
API, runtime, release and array architecture documents against source and tests.
No runtime tests were run; production code and user documentation are unchanged.

Task 013 API-source documentation follow-up is complete. API freshness passed.
Direct artifact-page Vale found four existing errors outside the changed text.
The packaging-requirements documentation follow-up remains open.

Task 013 documentation review follow-ups are complete. Artifact architecture
now records both API sources, array startup requirements and packaged-consumer
coverage. API freshness, page links/anchors, changed-text and project Vale, and
whitespace checks passed. Three unrelated existing page-level Vale errors remain.
No runtime tests were repeated; production code and tests are unchanged.

Task 013 documentation re-review found no new actionable issues. Both prior
follow-ups are complete; no checklist items were added. Static checks compared
allocation guides, API, runtime/release instructions and architecture with code
and tests. API freshness and whitespace checks passed. Runtime tests were not
repeated.

Task 013 code-shape review added one open follow-up for shared JVM configuration
in the boundary test; see the task checklist. Static review covered allocation,
native and fresh-JVM tests, packaged consumers, API tooling and related guides.
Runtime tests were not repeated. Production code and tests are unchanged.

Task 013 code-shape review follow-up is complete. The boundary test uses the
shared JVM configuration and retains its local expectations. Boundary and
36-process matrix tests passed with explicit executable overrides: 2 tests,
640 assertions, exit 0, no Scry failure files. Focused formatting, project lint
and whitespace checks passed. Production code is unchanged; full acceptance
was not repeated. No review follow-up remains unchecked; task 013 stays open.

Task 013 code-shape re-review found no new actionable issues. The shared JVM
configuration follow-up is complete; no checklist items were added. Static
review covered the allocator, native and fresh-JVM tests, packaged consumers,
API tooling and related documentation. Whitespace checks passed. Runtime
tests were not repeated; production code and tests are unchanged.
