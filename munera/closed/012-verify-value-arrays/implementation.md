# Implementation notes

## Architecture review — design-review first turn

- No architectural review feedback. The frozen scope is unchanged; no design
  steps were added.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context
  came from `AGENTS.md`, `ramora/INDEX.md`, the linked macro purpose, context
  and fallback documents, and the proposed array design, decisions and
  verification documents. The typed-array report supplied existing evidence.

## Ambiguity review — design-review second turn

- No ambiguity review feedback. No design steps were added.

## Inconsistency review — design-review third turn

- No inconsistency review feedback. No design steps were added.

## Review handoff

- `ramora/value-type-array-design/verification.md` includes later public API
  checks, not just this task's JVM verification. Do not treat its full list
  as task 012 acceptance. Likewise, `spike/typed-array/README.md` describes
  historical benchmark checks, not requirements for the new semantic probes.

## Ambiguity review — plan-review first turn

- No new actionable ambiguity review feedback. No design steps were added;
  `steps.md` was read-only. Static review only; no runtime probes were run.

## Inconsistency review — plan-review second turn

- No new actionable inconsistency review feedback. No design steps were added;
  `steps.md` was read-only. Static review only; no runtime probes were run.

## Plan-review handoff

- For fresh-JVM controls, `deps.edn` adds preview through the `:test` and
  `:bench` aliases; do not inherit those JVM options in preview-off or Java 8
  runs. `test-fallback/value_type/jvm_runner.clj` shows explicit executable,
  classpath and JVM-option selection without adding preview. Use it as a
  reference, not a required dependency of the isolated probe.

## Slice 1 — 2026-10-26: discovery

- Primary executable: `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`,
  actual build `27-jep401ea3+1-1`. Control executable:
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  actual build `1.8.0_492-b09`. Both are available.
- Added the isolated discovery runner and EDN observation schema. Five fresh
  JVM runs passed process checks. Real result assertions checked three cases
  per run, access outcomes and Java 8 ClassNotFoundException chains. Formatting
  and lint passed. No shared dependency or production files changed.
- Retained discovery evidence in
  `ramora/value-type-array-design/evidence/discovery/`. Source archive hash and
  javap signatures are included. Matching bundled Java source is extracted by
  the runner; native implementation correspondence remains unverified.
- The selected allocator is `Object[] newNullRestrictedAtomicArray(Class, int,
  Object)`; no seed-free overload appears. Diagnostics `isFlatArray`,
  `isNullRestrictedArray`, and `isAtomicArray` each take Object and return boolean.
  The bundled source describes atomicity as a layout designed for atomic access.
- Discovery succeeds without exports, but invocation fails with
  IllegalAccessException. With exports, the Object-array diagnostic returns
  false even without preview. Do not use this query alone as an array capability
  check. Java 8 records API absence without optional imports.
- Next slice must add actual value fixtures and allocation/store cases; these
  discovery outcomes do not establish disabled-preview allocation behavior.

## Slice 2a — 2026-10-26: allocation and seeds

- Added a direct allocation probe with five payload variants across four value
  classes. All 59 observations passed coverage and outcome assertions on EA3
  with preview and exports. Evidence: `ramora/value-type-array-design/evidence/allocation/`.
  Every accepted array has its length, component and all initial slots checked.
  Formatting and lint passed; no production or dependency files changed.
- Lengths 0, 1 and 3 accept valid seeds, including values with null reference
  fields. Null and wrong-component seeds fail even at length zero. Negative
  lengths, identity components and primitive components also fail. All observed
  failures have InvocationTargetException → IllegalArgumentException chains.
- Important correction to the initial probe assertion: EA3 reports
  `long.class.isValue()` as true, but rejects allocation with a boxed long seed
  as a type mismatch. Value status is not allocation eligibility. The corrected
  real-JVM assertions passed; the initial failed assertion was not a JVM failure.
- Installed signatures and bundled Java declarations show only the three-argument
  selected atomic allocator, not a seed-free overload. This is not a claim about
  other allocation APIs. No public API semantics changed.
- Next slice: add store checks, the Java access helper and independent diagnostics.
  The present script intentionally requires preview; do not use it as the safe
  Java 8 or preview-off entry point. Matrix orchestration and repeated-run evidence
  remain slice 3 work. No flattening or atomicity result is claimed here.

## Slice 2b — 2026-10-26: Clojure and Java stores

- Added ordinary Java Object[] bytecode access and Clojure aget/aset checks.
  Each of 60 cases owns a fresh array. Both access paths check lengths and
  every slot before and after writes at lengths 1 and 3. Zero-length indexed
  access is intentionally excluded; allocation already covers empty arrays.
- Valid replacements pass; null stores throw NullPointerException and wrong
  types throw ArrayStoreException with no reflection wrapper. Rejected writes
  preserve all slots. Reference replacements cross null/non-null field values;
  zero-field replacement correctly cannot show a payload change.
- `bash spike/value-array-semantics/stores.sh` passed 59 allocation and 60
  store observations on EA3. Formatting and lint passed (zero warnings).
  Evidence is in `ramora/value-type-array-design/evidence/stores/`; allocation.edn
  supplies the shared process environment. No production or dependency changes.
- The first run caught an invalid default-package import form; corrected before
  retained runs. Diagnostics, fresh-JVM matrix and final report remain open.
  Design scope and public API proposals did not change.

## Slice 2c — 2026-10-26: diagnostics

- Added 69 independent array queries, plus six isolated invalid-input queries.
  Array checks and 59 allocation checks passed. Each null query causes SIGSEGV
  (exit 134); each String query returns false. Retained repeat-run evidence:
  `ramora/value-type-array-design/evidence/diagnostics/`. Fatal logs replace
  result EDN for crashed processes. This is a JVM finding, not a human blocker.
- The first combined run crashed before writing results. Invalid inputs now run
  separately. Primitive long arrays return false for all three diagnostics;
  corrected the initial atomicity assertion accordingly. Array validation does
  not require flattening. Formatting and lint passed with zero warnings.
- Next slice must integrate fatal outcomes into the bounded matrix validator
  and preserve query isolation. Do not map null to false or to an exception.
  No production, dependency or public contract changes. Design scope is unchanged;
  the report must explain why public input validation cannot delegate null safely.

## Slice 3a — 2026-10-26: isolated process records

- Added `validate_isolated.clj` and integrated it into the diagnostic runner.
  Six process records now distinguish `:fatal` with SIGSEGV from returned false.
  The validator requires query-entry evidence, environment, exact case identity,
  expected exit and fatal logs, and rejects stale result files for fatal cases.
- Two real-JVM diagnostic runs passed (59 allocation, 69 array queries and six
  isolated outcomes each). The final run uses the corrected underscore filename.
  Formatting and lint passed. A copied evidence set with exit changed to zero
  failed validation as required. Retained final evidence and negative-check log:
  `ramora/value-type-array-design/evidence/isolated-validation/`.
- The matrix runner must consume these fatal observations separately. This slice
  does not cover the startup matrix or flattening-disabled semantics. Production
  and shared dependencies are unchanged. Pre-existing worktree edits were preserved.
- Whitespace review: authored files pass. The retained raw JVM fatal logs and
  shell command captures contain trailing spaces; these are preserved as raw
  evidence. Full-diff whitespace validation therefore reports those files.

## Slice 3b — 2026-10-26: flattening comparison

- Added `semantics.sh` and a state-based EDN comparison. Two unchanged-source
  runs passed for default and disabled array flattening. Each configuration
  passed 59 allocation, 60 store, 69 array-query and six isolated-query checks
  (allocation also runs before diagnostics). Comparison reports matched across
  repeats. Retained final output: `evidence/layout-comparison/` under the array design.
- Contents, exception classes, null restriction and atomic diagnostics agree.
  All disabled-layout flat queries return false. Store exception messages differ;
  comparison excludes only cause messages, while raw EDN preserves them. The
  first comparator caught this difference; it was investigated before rerunning.
- Null still crashes all three queries with flattening disabled. No native cause
  is inferred. Formatting, lint and shell syntax checks passed. No production or
  shared dependencies changed. Generated classes were excluded from retention.
- Next slice: integrate startup controls with aggregate coverage and explicit
  unresolved dependent cases. Full-matrix repetition and final report remain open.

## Slice 3c — 2026-10-26: startup controls

- Five real-JVM controls now separate discovery, strict value creation and
  zero-length allocation. Preview permits value creation without exports;
  allocator access then throws IllegalAccessException. Without preview, strict
  fixture creation fails; dependent cases are explicitly unresolved. This is
  not evidence of a direct allocator rejection without preview.
- Two unchanged-source runs passed and produced identical validation summaries.
  A copied nonzero-exit result failed validation. Formatting, lint (zero warnings)
  and shell syntax checks passed. Evidence: `evidence/startup/` under the array
  design. No production or shared dependency changes; design scope unchanged.
- Next: combine startup coverage with semantic and fatal records, repeat the
  full matrix, then write the report and update architecture gates. Existing
  uncommitted design/orchestration edits were preserved, not included in this commit.
- Authored files pass whitespace checks. Raw command captures and the negative
  validator log retain original trailing whitespace, as in earlier evidence.

## Slice 3d — 2026-10-26: aggregate matrix

- Added `matrix.sh` and `validate_matrix.clj`, using real process records and
  existing validators. Two final full runs passed with identical `validation.edn`
  and source hashes. Each layout checks 59 allocation, 60 store, 69 array-query
  and six isolated outcomes; diagnostics also repeats allocation checks.
- Retained final evidence: `ramora/value-type-array-design/evidence/matrix/`.
  Generated classes are omitted; commands retain their original target paths.
  Removing a store result from a copied evidence set failed aggregate validation.
  Formatting, lint (zero warnings), shell syntax and authored whitespace checks
  passed. Raw process captures retain original whitespace.
- No production or shared dependency changes. Pre-existing design/orchestration
  edits remain untouched. Scope is unchanged. Next slice must write the report,
  explain the null crashes and diagnostic limits, and update architecture gates;
  matrix completion alone does not complete task acceptance.

## Slice 4a — 2026-10-26: reproduction instructions

- Revised the probe README with explicit runtime paths, fresh-output commands,
  matrix coverage, retained validation and fatal-query safety instructions.
  Commands match the existing matrix runners and completed slice 3d runs.
- Revalidated retained matrix evidence with `bb .../validate_matrix.clj`
  (exit zero); shell syntax and README relative-link checks passed. No JVM
  experiments were rerun for this documentation-only slice. No production,
  test or dependency changes. Authored diff whitespace checks passed.
- New runs hash the revised README; historical hashes must remain unchanged.
  Next: write the six-question report, update evidence-backed design gates,
  add index/design links and map all acceptance criteria. Scope is unchanged.

## Slice 4b — 2026-10-26: semantic report

- Added the six-question report with case IDs, retained evidence and bundled
  source links. It separates primitive value status from eligibility, native
  false results from public validation, and fatal null queries from exceptions.
- Retained matrix validation passed (exit zero); report relative links and
  full worktree diff whitespace checks passed. No JVM probes were rerun and
  no tests, production code or shared dependencies changed.
- Design scope is unchanged. Next slice must update architecture gates and
  links, then map acceptance. Existing uncommitted design, orchestration and
  isolated-evidence edits were preserved and excluded from this slice commit.

## Slice 4c — 2026-10-26: design gates and acceptance

- Updated architecture assertions from direct evidence. Empty seeded allocation
  and native stores are observed, not assumed. Null diagnostics must be guarded;
  primitive value status is not eligibility. Public contracts remain proposed.
  Remaining public checks and unresolved mechanisms are in
  `ramora/value-type-array-design/implementation-gates.md`.
- Retained matrix validation passed (exit 0). Local architecture Markdown links
  and anchors and `git diff --check` passed. No JVM rerun, test/dependency change,
  production change or historical measurement change in this slice. Pre-existing
  worktree edits remain outside this commit, including task design and raw evidence.
- Acceptance mapping (paths below are relative to the repository):
  1. Commands: `spike/value-array-semantics/README.md`; provenance, flags, EDN
     and raw logs: `ramora/value-type-array-design/evidence/matrix/`.
  2. All six questions: `ramora/value-type-array-design/semantic-verification.md`,
     with case/source links and explicit unresolved mechanisms.
  3. Fresh-JVM controls: matrix `startup/validation.edn`; both layouts:
     `semantics/`. Missing fixture prerequisites remain unresolved, not passed.
  4. Seed/length coverage: each layout's `stores/allocation.edn`; replacement
     and rejected-write contents: `stores/stores.edn`. No exhaustion allocations.
  5. Report sections 3, 5 and 6 separate status, eligibility, diagnostics and
     atomicity; historical performance claims remain scoped and unchanged.
  6. Architecture decisions, verification assertions and implementation gates
     now match observations. No remaining direct-JVM blocker within task scope.
  7. Design and Ramora index link report, instructions and evidence. Link and
     whitespace checks passed; shared configuration and production are unchanged.
- Next review should assess the completed evidence and proposed-contract boundary,
  not require the later public API tests. Task remains open for review; all
  implementation checklist items are complete.

## Implementation review — 2026-10-26

- Added 2 steps to be addressed.

## Review follow-up 1 — 2026-10-26

- Addressed 1 review step. Shared fixture-free assertions now check required IDs,
  operations, inputs and semantic outcomes; startup checks enforce runtime and flags.
- Retained matrix and five copy-based checks passed (unchanged, false diagnostic,
  renamed ID, wrong flags, wrong runtime). Formatting and lint passed. No JVM rerun.

## Review follow-up 2 — 2026-10-26

- Addressed the remaining review step (2 total). Record creation is separate
  from validation. Matrix fatal-log references are now directory-relative;
  raw evidence and observations are unchanged. Validation compares existing
  isolated records with records derived from raw process evidence.
- All six copy-based checks passed, including corrupted isolated records.
  SHA-256 checks confirmed unchanged evidence bytes on success and failure.
  Formatting, lint, shell syntax and whitespace checks passed. No JVM rerun;
  production code, shared dependencies and task design are unchanged.

## Implementation re-review — 2026-10-26

- No new actionable issues.

## Test review — 2026-10-26

- Added 1 step to be addressed.

## Test review follow-up — 2026-10-26

- Addressed 1 review step. Each selected index now has an independent valid,
  null and wrong-type write, with all slots checked after each attempt.
- Both final matrix runs passed 120 store observations per layout and produced
  identical summaries and source hashes. Six read-only evidence regression
  checks, formatting and lint passed. Expected isolated null-query crashes
  remain validated. Retained evidence: `evidence/matrix-selected-slots/` under
  the array design; earlier evidence is unchanged. Production code is unchanged.

## Test re-review — 2026-10-26

- No new actionable issues. All six retained-evidence checks passed; JVM probes
  were not rerun.

## Test review: isolated process configuration — 2026-10-26

- Added 1 step to be addressed.

## Isolated-process review follow-up — 2026-10-26

- Addressed 1 review step. Aggregate validation now enforces EA3, preview,
  exports and layout flags for all 12 isolated diagnostic processes.
- All 55 copy-based checks passed, including substituted layout evidence and
  independent runtime/flag mutations. Evidence hashes remain unchanged on
  success and failure. Formatting, lint and whitespace checks passed.
- No JVM probes were rerun. Production code and retained evidence are unchanged.

## Test review: startup allocation status — 2026-10-26

- Added 1 step to be addressed.

## Startup-status review follow-up — 2026-10-26

- Addressed 1 review step. Baseline allocation now requires `:pass` and the
  expected value. The new unresolved-status regression failed before the fix.
- All 57 copy-based checks passed, including unresolved and rejected baseline
  allocation with unchanged values. Evidence hashes remain unchanged on success
  and failure. Formatting, lint and whitespace checks passed. No JVM probes
  were rerun; production code and retained evidence are unchanged.

## Test review: current evidence links — 2026-10-26

- Added 1 step to be addressed.

## Current-evidence links follow-up — 2026-10-26

- Addressed 1 review step. All three entry points now link to the selected-slot
  matrix, consistent with the probe README and semantic report. Linked paths
  and retained-matrix validation passed; whitespace checks passed. Historical
  evidence is unchanged. No JVM probes were rerun.

## Test review: discovered signatures — 2026-10-26

- Added 1 step to be addressed.

## Signature-validation review follow-up — 2026-10-26

- Addressed 1 review step. Startup validation requires allocator and diagnostic
  parameter/return types and rejects the seed-free atomic overload. Unrelated
  API methods remain allowed. The empty-signature regression failed before the fix.
- All 117 copy-based checks passed, with unchanged evidence hashes on success
  and failure. Formatting, lint and whitespace checks passed. No JVM probes
  were rerun; production code and retained evidence are unchanged.

## Test review after signature validation — 2026-10-26

- No new actionable issues. Rechecked the completed signature follow-up:
  all 117 retained-evidence checks passed. JVM probes were not rerun.

## Documentation review — 2026-10-26

- Added 2 steps to be addressed.

## Documentation format follow-up — 2026-10-26

- Addressed 1 review step. README now describes environment locations and
  optional startup inputs. Checked writers and retained selected-slot EDN;
  retained matrix validation and whitespace checks passed. No JVM probes ran.

## Documentation changelog follow-up — 2026-10-26

- Addressed 1 review step (2 documentation steps total). Added the Unreleased
  probe/validation note, public API boundary and isolated-crash warning.
  README link and whitespace checks passed. No JVM probes ran.

## Documentation re-review — 2026-10-26

- No new actionable issues. Retained matrix validation and local link checks
  passed. JVM probes were not rerun.

## Code-shape review — 2026-10-26

- Added 1 step to be addressed.

- 2026-10-27: addressed 1 code-shape review step. Moved reusable allocation and discovery helpers to `support/`; entry points now pass output paths explicitly. Preserved allocation-first checks, independent discovery and Java 8 startup. Helper-loading checks passed in 6 fresh JVMs; standalone discovery and the full matrix passed (`target/value-array-semantics/helper-separation-verified/`). All 117 read-only evidence regression checks, formatting, lint and whitespace checks passed. Historical evidence and production code are unchanged.

## Code-shape re-review — 2026-10-27

- No new actionable issues after helper separation. All 117 retained-evidence
  checks passed; JVM probes were not rerun.

## Closure — user request

- Closed after verification, review follow-ups and knowledge extraction.
  All checklist items are complete. Moved the task to `munera/closed/` and
  removed it from the active plan; updated incoming links and working memory.
- Closure changes records only. No JVM probes or runtime tests were rerun.
  Existing EA3 scope limits remain; public API implementation is covered by
  tasks 013–015, not this closed verification task.
