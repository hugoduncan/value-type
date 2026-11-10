# Implementation notes

## 2026-11-08 — Architecture review

- No architectural review feedback. Scope unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Review used
  `AGENTS.md`, the array design, decisions and embodiment linked from
  `ramora/INDEX.md`, and the capability-report, probe and runtime architecture
  documents. These sources are the architecture context for later review turns.

## 2026-11-08 — Ambiguity review

- Ambiguity review added 1 new design step. Scope unchanged.

## 2026-11-08 — Inconsistency review

- No new inconsistency review feedback. Scope unchanged.
- The linked JVM-boundary knowledge describes an allocation-free access
  check, not allocation-free discovery. The separate D17 allocation round
  trip remains required; these statements do not conflict.

## 2026-11-08 — Design follow-up handoff

- Use `dev/jvm/jvms.edn` as the source for JVM flag profiles when clarifying
  criterion 2; do not create a second configuration authority. See
  `doc/development/runtime.md` for explicit child flags and existing array
  matrices. The `:test` alias enables preview, so it is not suitable for
  preview-free controls.

## 2026-11-08 — JVM configuration follow-up

- `test/cross-jvm/value_type/array_capability.clj` already supplies the
  required runtime rows through `runtimes`: `:java8`, `:no-options`,
  `:export-only`, `:no-export` and `:export`. Its extra `:no-flattening`
  row is a separate layout control, not another preview/export combination.
- `value-type.aot-fixture/compile-producer` deletes fixture source after
  successful compilation; reuse this support for source-free load checks.
- Checked criterion 2 against the JVM facts and runtime guide. Scope is
  unchanged. No runtime tests were run for this documentation-only change.

## 2026-11-08 — Ambiguity re-review

- No new ambiguity review feedback. Scope unchanged.

## 2026-11-08 — Review handoff

- No design follow-ups remain open. In
  `ramora/value-type-array-design/decisions.md` (D3 state) and
  `ramora/value-type-array-design/embodiment.md`, cached reflective methods
  describe the implementation being replaced, not a constraint to retain.
  Update those descriptions without changing capability ownership or the
  separation of allocation and inspection.

## 2026-11-08 — Plan ambiguity review

- No new ambiguity review feedback on plan.md or steps.md.

## 2026-11-08 — Plan inconsistency review

- No new inconsistency review feedback on plan.md or steps.md.

## 2026-11-08 — Plan review handoff

- For fresh inspection-isolation checks, observe delay state before calling
  `value-type.capability/report` (`src/value_type/capability.clj`), which forces
  all capability results. Do not load `test/runtime/value_type/array_test_support.clj`
  first: its top-level value definitions trigger value-class discovery. Use an
  ordinary Object array for the initial observation.

## 2026-11-09 — Slice 1: reflective measurement baseline

- Production revision: `0472b9fae6b444627eec37db12d6bcab16050abc`.
  No production source changed. Added the two measurement scripts under
  `dev/bench/array-adapter/`. The runner validates counts and normalization
  against real operations, without mocks; refuses an existing output directory;
  and gives each child a 120-second bound with process-tree termination.
- Facts resolution verified Java 8 `1.8.0_492-b09` (`:plain`) and Valhalla
  `27-jep401ea3+1-1` (`:preview-export`). The latter uses the existing local
  installation through the verified environment override. `javac` and `javap`
  version checks passed. Host: macOS Darwin 27, ARM64; baseline Java is x64.
- Command (repository root):
  `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java bb dev/bench/array-adapter/run.clj munera/open/029-lazy-direct-call-array-adapter/evidence/reflective`.
  Exit 0; 15 fresh children, 145 validated records: 15 setup, 10 first-use,
  20 warmup and 100 measured warmed samples. Each warmed case has one second
  warmup and five 200-ms samples per fork. Exact child commands, classpath,
  runner digest, flags, builds, GC counts/times and samples are in
  `evidence/reflective/`; `summary.edn` retains medians and ranges.
- Median (range), ns/operation: allocation length 0: 98.09 (88.86–135.39);
  length 4: 123.51 (119.33–146.38); ordinary inspection: 100.73
  (88.89–122.42); restricted inspection: 92.48 (90.74–119.91).
  First allocation: 1.229 ms (1.033–1.344); first inspection: 0.449 ms
  (0.402–0.492). Setup: median 330.97 ms (322.59–390.95).
- Setup includes namespace load and value fixture definition, but excludes JVM
  startup. Thus first allocation includes array discovery and its self-check,
  not first value-class discovery. Inspection starts with ordinary arrays and
  no allocation discovery. Warmed runs vary seeds and array inputs. The volatile
  sink and per-call clock add overhead; short samples and fixed case order limit
  inference. Compare the same runner on the same host; no speed claim yet.
- Verification: `bb clj-fmt check dev/bench/array-adapter`,
  `clj-kondo --lint dev/bench/array-adapter` (zero warnings/errors), and
  `git diff --check` passed. Runtime suites were not needed for this
  development-only slice. Next: implement direct allocation (slice 2); preserve
  these samples and use the same measurement script for the final comparison.

## 2026-11-09 — Slice 2: direct allocation

- Allocation success now contains callable `:allocator` and `:predicate` values.
  Optional resolution and the round trip occur outside classified discovery
  catches. No caller data is cached. Inspection retains its reflective predicate
  locally until slice 3; it does not use the changed allocation wrapper.
- Existing real-probe tests cover constant-value rejection and throwing
  constructors. The native negative-length regression now also checks that the
  same successful capability result survives rejection. Existing controlled
  discovery tests remain controlled evidence, not proof of JVM support.
- EA3 `27-jep401ea3+1-1`, local executable
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, preview and exports:
  `JAVA_CMD=<executable> clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli`
  with `--namespace` for `value-type.impl.array-test`,
  `value-type.impl.probe-builder-test`, `value-type.array-test`,
  `value-type.array-layout-test` and `value-type.array-sequence-test` passed:
  33 tests, 1377 assertions, exit 0. A separate `value-type.array-info-test`
  run passed: 4 tests, 1314 assertions, exit 0; no Scry failure files.
- Compiled `value-type.impl.array-allocation` with `clojure -M -e`, explicit
  preview/export flags and `*compile-path*` set to
  `/tmp/value-type-029-allocation-classes`; exit 0, no reflection warnings.
  Formal retained bytecode verification remains slice 4.
- Targeted `bb clj-fmt check` and `clj-kondo --lint` on both changed source
  files and both changed test files passed (zero lint warnings/errors).
  `git diff --check` passed. Design unchanged. Full matrices, disabled
  flattening, source-free deployment and performance comparison remain pending.

## 2026-11-09 — Slice 3: direct inspection and initial isolation

- Inspection success now caches only `:inspect-array`; optional compilation is
  outside the classified-absence handler. Removed repeated reflective invocation
  and its exception wrapper. Design and public contracts are unchanged.
- Fresh children verify ordinary inspection leaves allocation and value-class
  delays unrealized and the allocation adapter unloaded, with preview on/off
  and `value-type.value-classes=off` on/off. Allocation-side isolation and the
  combined disabled-allocation/report checks remain unchecked for the next pass.
- EA3 `27-jep401ea3+1-1`, executable
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`: with `JAVA_CMD` and
  `VALUE_TYPE_VALHALLA_JAVA` set to that executable,
  `clojure -M:cross-jvm-test --namespace value-type.array-info-boundary-test`
  passed (3 tests, 172 assertions, exit 0). This includes disposable invalid
  inputs across the facts-selected runtime rows before shared native tests.
- With `JAVA_CMD` as above, export flag and `-M:test -m scry.cli --namespace
  value-type.array-info-test`, valid-array oracle comparisons passed with default
  flattening (4 tests, 1314 assertions) and `-J-XX:-UseArrayFlattening`
  (4 tests, 1437 assertions). Both exited 0. No final Scry failure files.
- Export-only `clojure -M -e` compilation of the inspection namespace to
  `/tmp/value-type-029-inspection-classes` passed without reflection warnings.
  Formal disassembly evidence remains slice 4. Targeted formatting and lint
  passed (zero warnings/errors); `git diff --check` passed.
- Initial test launches exposed a non-facts default parent JVM and two test
  construction errors (syntax-quoted private symbols and a missing harness
  `described` conversion). Corrected the launch and tests; no production
  workaround or new dependency. Next pass must retain remaining deployment,
  full regression and measurement gates rather than infer completion here.

## 2026-11-09 — Slice 3 completion: discovery and switch boundaries

- Added real fresh-process checks: successful allocation discovery (including
  its native self-check) leaves inspection unrealized and its adapter unloaded.
  Repeated discovery returns the same cached result. With value classes off,
  allocation rejects before input validation, the allocation adapter remains
  unloaded, and ordinary inspection and report entries agree, with preview
  both enabled and disabled. Observe isolation before report: report forces
  all capability results. No production or design changes were needed.
- Verification: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:cross-jvm-test --namespace value-type.array-info-boundary-test`
  passed: 5 tests, 184 assertions, exit 0, no Scry failure files. JVM rows
  use the existing facts configuration (EA3 `27-jep401ea3+1-1` and Java 8
  `1.8.0_492-b09`); no flags or dependency versions were added.
- Targeted `bb clj-fmt check` and `clj-kondo --lint` passed after formatting;
  lint has zero errors/warnings. `git diff --check` passed. Next slice is the
  compiled-bytecode and deployment boundary work; these tests do not prove
  AOT safety, JAR isolation, or direct bytecode calls.

## 2026-11-09 — Slice 4: compiled operation boundary

- Added `dev/bench/array-adapter/check-bytecode.clj`, a bounded real-tool check.
  It compiles both optional namespaces and the baseline boundary in a temporary
  directory, checks seven operation classes, and deletes temporary classes.
  Each of the five scoped JDK targets must occur on a direct invocation
  instruction. The inspection predicate helper is included. Whole operation
  classes (including forwarding methods) reject Method.invoke, Reflector and
  method-handle invocation. Discovery classes are deliberately outside this gate.
- Command: `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  bb dev/bench/array-adapter/check-bytecode.clj
  munera/open/029-lazy-direct-call-array-adapter/evidence/bytecode`.
  Exit 0, seven classes verified on facts-selected EA3 `27-jep401ea3+1-1`,
  export-only profile (no preview). No reflection warnings. `evidence/bytecode/`
  retains exact commands, environment, descriptors, disassembly and pass data.
- The initial runner attempt exposed Babashka syntax-quote resolution of
  `compile` to `user/compile`; explicitly qualifying `clojure.core/compile`
  fixed the runner. No production change or dependency was needed.
- Targeted `bb clj-fmt check` and `clj-kondo --lint` pass (zero lint warnings
  or errors); `git diff --check` passes. Design remains unchanged. Next slice
  needs fresh require/AOT and isolated JAR evidence; bytecode alone does not
  establish receiving-runtime safety. Measurements and full gates remain open.

## 2026-11-09 — Slice 4: fresh public loading

- Added `value-type.array-loading-test`: ten fresh children cover both public
  require orders on Java 8 plain and the four EA3 preview/export profiles.
  Require (including the report namespace) loads neither adapter. Discovery
  loads only available adapters; rejected public operations preserve that state
  and the classified reasons. Each child checks its actual arguments and build.
  Real JVM state and operations are used; no mocks or production changes.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:cross-jvm-test --namespace value-type.array-loading-test`.
  Exit 0: 1 test, 40 assertions; no Scry failure files. Facts builds remain
  Java 8 `1.8.0_492-b09` and EA3 `27-jep401ea3+1-1`.
- Targeted formatting and lint pass with zero warnings/errors; `git diff --check`
  passes. Design unchanged. Next slice needs require-only AOT producers and
  source-free consumers, including the plain-to-preview/export receiver case.
  Fresh source loading does not establish AOT or isolated JAR safety.


## 2026-11-09 — Slice 4: require-only application AOT

- Extended `array-loading-test` with five real producers and fresh source-free
  consumers: Java 8 plain and all four EA3 profiles. Compilation and loading
  leave both adapters unloaded; saved output contains no adapter classes.
  Consumers check absent application source and present compiled init resource,
  then verify discovery, unavailable-operation reasons and adapter state.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:cross-jvm-test --namespace value-type.array-loading-test`.
  Exit 0: 2 tests, 85 assertions; no Scry failure files. Consumers assert actual
  arguments and facts builds: Java 8 `1.8.0_492-b09`, EA3 `27-jep401ea3+1-1`.
  Targeted formatting and lint pass (zero warnings/errors); diff check passes.
- No production, dependency or design changes. A delimiter error in the new
  test was repaired with `clj-paren-repair` before verification. Next slice must
  add plain-producer to preview/export-consumer repeated operations; matching
  profile AOT alone does not prove that receiver capability selection works.
  JAR isolation, broader suites and measurements remain unchecked.

## 2026-11-09 — Slice 4: receiving-runtime AOT operations

- Added a plain EA3 require-only producer and preview/export source-free
  consumer. The consumer checks compiled application presence, source absence,
  unloaded adapters before operations, and three rounds of allocation, both
  sequence arities and ordinary/restricted inspection. Reports and actual JVM
  arguments agree with receiver support. No production or design change.
- Capture adapter state in a let before operations: map entry evaluation order
  must not define this observation. Initial test failures exposed this ordering
  error and an incorrect test arity; both were corrected without product changes.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:cross-jvm-test --namespace value-type.array-loading-test`.
  Exit 0: 3 tests, 93 assertions; no Scry failure files. Facts builds remain
  Java 8 `1.8.0_492-b09` and EA3 `27-jep401ea3+1-1`.
- Targeted formatting, lint and diff checks pass. Next slice needs broader
  array/report/switch regression and isolated JAR consumers. This evidence does
  not establish JAR isolation or performance; those checklist items remain open.

## 2026-11-09 — Slice 4: focused cross-JVM regression

- Ran the next unchecked regression gate on the Java 8 parent. All seven
  selected namespaces passed: 23 tests, 961 assertions, exit 0, no Scry
  failure files. Real children cover the array profiles, report policies,
  value-class switch, fresh require and source-free AOT boundaries.
- [Verification record](evidence/cross-jvm-regression/verification.md) retains
  the exact command, revision, builds, coverage and recording limits. The
  label record contains 239 child starts and 227 cells. Runtime/argument
  agreement is asserted by the tests; the recorder retains hashes, not raw
  argument vectors. No source, tests, dependencies or design changes were needed.
- Next slice: extend isolated JAR consumers, then run the build suite. Do not
  treat this focused pass as the full cross-JVM or final acceptance gate.
  Measurements, architecture updates and final quality checks remain open.


## 2026-11-09 — Slice 4: isolated JAR consumers

- Extended the real JAR consumer: both optional resources must come from the
  artifact; public requires load neither adapter; unavailable allocation leaves
  its adapter unloaded; export-only inspection loads only its own adapter.
  Supported consumers repeat allocation, mutation/rejection, both sequence
  arities and ordinary/restricted diagnostics before report discovery.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:build-test -m scry.cli --dir test/build`.
  Exit 0: 9 tests, 116 assertions; no Scry failure files. Facts-selected
  consumers use Java 8 `1.8.0_492-b09` plain and EA3 `27-jep401ea3+1-1`
  plain, export-only and preview/export. Profiles supply flags; all consumers
  also use silent fallback. No checkout source is on their classpaths.
- Artifact: temporary `org.hugoduncan/value-type` version `0.1.1`,
  `target/value-type-0.1.1.jar`, built from parent revision
  `86c347319c73ecdda154ec38dd76079b5142bcd1` (only tests/docs changed).
  The build suite checks exact source-only entries and source bytes. Temporary
  artifacts are deleted by the harness; no artifact digest was retained.
- Targeted formatting and lint pass (zero warnings/errors). A delimiter error
  was fixed with `clj-paren-repair`. An initial alias-only launch opened a REPL
  and ran no tests; only the explicit Scry command above is verification.
  No production, dependency or design change. Next slice is the direct-run
  measurement comparison, followed by full regression and documentation gates.


## 2026-11-09 — Slice 5: measurement comparison

- Ran the unchanged runner on direct revision
  `5143b4feb20a05f0dc71ab13b09b56775d851fb6`: exit 0, 145 validated records
  across 15 fresh children. Environment, classpath, flags and runner digest
  match the reflective baseline. Raw evidence is in `evidence/direct/`;
  commands, medians, ranges and limits are in `evidence/comparison.md`.
- First-use medians regress by about 3.09 ms (allocation) and 3.83 ms
  (inspection). Ten real fresh-process preloaded controls isolate optional
  source loading as the supported explanation; all exit 0. Preserve lazy
  loading: moving this cost to public require would violate the design.
  All warmed direct samples are below the corresponding baseline range.
  No API, dependency, production or design changes were needed.
- The existing runner validates sample counts and normalization with real
  operations; no mocks or new tests were needed for this measurement slice.
  `git diff --check` passed. Full runtime, cross-JVM, fallback, forced-JIT,
  smoke, quality and architecture gates remain unchecked. The next slice
  should run the full runtime suite and both flattening controls; do not infer
  final acceptance from benchmark success.


## 2026-11-09 — Slice 5: runtime regression

- Full runtime suite passes: 191 tests, 5234 assertions. Explicit array
  allocation, layout, sequence and diagnostic controls pass with flattening
  enabled (26 tests, 2665 assertions) and disabled (26 tests, 2788 assertions).
  All three Scry CLI exits are 0; all failure directories were empty and copied
  before the next run. [Verification record](evidence/runtime-regression/verification.md)
  retains commands, revision, build, logs, exit statuses and coverage limits.
- No production, test, dependency or design change was needed. Existing real
  array tests cover this slice; no mocks were added. Plan and checklist now
  distinguish this completed gate from the remaining regression gates.
- Next slice: full cross-JVM suite on Java 8, then the three fallback parents.
  Forced-JIT, smoke, architecture, quality and final acceptance audit remain
  open. Runtime success alone does not complete criterion 6.


## 2026-11-09 — Slice 5: full compatibility regression

- Full cross-JVM suite passes under Java 8: 121 tests, 5226 assertions.
  Each fallback parent (Java 8, EA3 preview off, EA3 preview on) passes
  78 tests and 2636 assertions. All four exits are 0; Scry failure directories
  were empty and copied before the next run. The [verification record](evidence/full-matrix/verification.md)
  retains commands, revision, builds, logs, status and evidence limits.
- No production, test, dependency or design changes were needed. Existing
  real-process tests cover this gate; controlled-capability cases remain
  separate from support evidence. Plan, checklist and current state agree.
- Next slice: forced-JIT and ordinary-JDK smoke. Normal child success does
  not establish forced compilation. Architecture updates, quality checks
  and the final acceptance audit remain open.

## 2026-11-09 — Slice 5: forced-JIT and smoke gates

- Forced-JIT runtime passes: 191 tests, 5238 assertions, exit 0. Ordinary-JDK
  smoke passes: 3 tests, 45 assertions, exit 0. Both Scry failure directories
  were empty and copied before another run. The [verification record](evidence/jit-smoke/verification.md)
  retains commands, builds, revision, flags, logs, status and coverage limits.
- Forced compilation applies only to the EA3 runtime parent, including AOT
  production. Runtime children remain normal preview-only processes. Ordinary
  JDK 25 coverage is startup, policy and same-JDK AOT smoke, not a full matrix.
- Existing real-process tests cover this slice; no new mocks, tests, production
  changes or dependencies were needed. Design is unchanged. Next slice needs
  array architecture updates, quality gates and the final acceptance audit.


## 2026-11-09 — Slice 5: architecture documentation

- Updated array decisions and embodiment to separate baseline-safe discovery
  from the two optional direct-call namespaces. Documented cached function
  keys, receiving-runtime selection, independent discovery, direct self-check
  and native failure propagation. Public contracts and task design are unchanged.
- Compared the documents with the current implementation and retained bytecode,
  deployment and measurement evidence. Added the bounded measurement result
  and first-use compilation cost without a general speed claim. Project state,
  plan and checklist now agree. `git diff --check` passes.
- This slice changes documentation only; no tests or dependencies changed.
  Existing real-JVM evidence remains the verification basis. Next slice must run
  the five quality commands and tooling suite listed in steps.md, then audit
  all six acceptance criteria. No final acceptance is claimed here.

## 2026-11-09 — Final quality gates and acceptance audit

- Full-scope lint found collisions between standalone scripts in `user` that
  targeted lint had missed. Added distinct script namespaces and the existing
  benchmark convention for file-name lint exclusions. No production, public API,
  dependency or design changes. The original benchmark comparison remains valid;
  its runner bytes are recorded with that evidence.
- All five quality commands pass; tooling passes 11 tests and 245 assertions,
  exit 0 with no Scry failure files. Real script rechecks pass seven bytecode
  classes and 145 measurement records. Commands, initial failures, final logs
  and copied recheck data: [quality verification](evidence/quality/verification.md).
  No new mocks or tests were needed for this script-isolation correction.
- Acceptance 1: `evidence/bytecode/` and the quality recheck identify all five
  JDK targets in seven compiled classes, without reflective or method-handle
  dispatch in operation bodies. Discovery reflection is intentionally retained.
- Acceptance 2: fresh require and require-only AOT tests cover Java 8 plain
  and all four EA3 profiles. Source-free cross-profile receiving operations,
  unloaded optional adapters, export-only inspection and the disabled-value
  switch pass. See slice 3/4 notes and the focused/full cross-JVM records.
- Acceptance 3: runtime regression, both flattening controls and full cross-JVM
  evidence cover public semantics, validation, reports, native rejection followed
  by valid allocation, direct self-check and independent discovery. Controlled
  capability tests remain distinct from real support evidence.
- Acceptance 4: isolated JAR build suite passes 9 tests and 116 assertions;
  repeated supported operations and unsupported loading use artifact resources,
  not checkout source. Slice 4 records version, parent revision, builds and
  commands. Temporary JAR bytes were checked by the suite; no digest was retained.
- Acceptance 5: `evidence/comparison.md` and reflective/direct raw samples
  establish the bounded comparison. Fresh preloaded controls explain the repeatable
  first-use regression as lazy source compilation. Warmed improvements are local
  observations, not a general speed guarantee.
- Acceptance 6: array architecture and project state match the implementation.
  Runtime, cross-JVM, fallback, build, forced-JIT, smoke and quality gates pass.
  Forced compilation covers only the runtime parent; ordinary JDK coverage is
  smoke-only. Child arguments are asserted, but the focused recorder retains
  hashes rather than raw vectors. These recording limits do not change the
  tested contracts. `git diff --check` passes.
- No implementation checklist items remain. The next pass is review, not another
  implementation slice. Keep task closure and knowledge extraction separate.

## 2026-11-09 — Implementation review

- Added 1 step to be addressed.

## 2026-11-09 — Implementation review follow-up

- Addressed 1 review step: removed the stale quality/acceptance status from
  the array embodiment. Measurement limits remain unchanged. Checked agreement
  with the final acceptance audit, quality evidence, task plan and project state.
  `git diff --check` passes. Documentation only; no runtime tests were run.

## 2026-11-09 — Implementation re-review

- No new actionable issues. Bytecode recheck passed for seven operation classes;
  focused array tests passed (16 tests, 458 assertions, exit 0; no Scry failure files).

## 2026-11-09 — Test review

- Added 1 step to be addressed.

## 2026-11-08 — Test review follow-up

- Addressed 1 review step. Added `array-adapter-failure-test`: isolated source
  copies with absent adapters, real compiler errors, and AOT adapters whose
  class-file major version is incompatible. Both discovery accessors and public
  operations run in separate fresh EA3 preview/export processes. Tests verify
  API/access prerequisites, the expected escaping cause, and no public
  capability-error translation. These damaged-artifact controls are not
  supported-JVM evidence. No production code changed.
- Verification: Java 8 parent (`1.8.0_492-b09`), EA3 child
  (`27-jep401ea3+1-1`). Set `JAVA_CMD` to the cached baseline executable and
  `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`;
  ran `clojure -M:cross-jvm-test --namespace value-type.array-adapter-failure-test
  --namespace value-type.array-loading-test`: exit 0, 4 tests, 141 assertions,
  no failures or errors; `.scry-results/` empty. The first attempt lacked the
  Valhalla override and stopped at executable selection; no test assertions ran.
- `bb clj-fmt check test/cross-jvm/value_type/array_adapter_failure_test.clj`,
  `bb clj-kondo:lint` and `git diff --check` pass (lint: no errors or warnings).

## 2026-11-08 — Test re-review

- No new actionable issues. Focused failure, loading and discovery-boundary
  tests pass: 9 tests, 325 assertions, exit 0; `.scry-results/` is empty.

## 2026-11-08 — Test-shaper review

- Added 1 step to be addressed.

## 2026-11-08 — Observation-order review follow-up

- Addressed 1 review step. Bound adapter state before AOT loading discovery;
  bound repeated operations, post-operation state and report in that order.
  Result-map entry order no longer controls these observations.
- Ran `clojure -M:cross-jvm-test --namespace value-type.array-loading-test`
  with the cached Java 8 parent (`1.8.0_492-b09`) and
  `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  (EA3 `27-jep401ea3+1-1`). All existing profiles passed: 3 tests,
  93 assertions, exit 0; no Scry failure files. A temporary permutation of
  the result-map entries also passed with the same counts; restored the
  maintained entry order afterward.
- File formatting, `bb clj-kondo:lint` and `git diff --check` pass.

## 2026-11-08 — Test-shaper review after observation-order correction

- No additional follow-up steps.

## 2026-11-08 — Documentation review

- Added 2 steps to be addressed.

## 2026-11-08 — User-guide review follow-up

- Addressed 1 review step: documented lazy adapter compilation, report-triggered
  discovery, failure propagation and bounded first-use cost in both user guides.
  Checked against discovery code, loading/failure tests and comparison evidence.
  `bb vale` and `git diff --check` pass. Documentation only; no runtime tests run.

## 2026-11-08 — Changelog review follow-up

- Addressed 1 review step: added the lazy direct-call array change under
  Unreleased, including first-discovery compilation cost and unchanged public
  contracts. No speed claim. `bb vale` and `git diff --check` pass.
  Documentation only; no runtime tests run.

## 2026-11-08 — Documentation re-review

- No new actionable documentation issues.

## 2026-11-08 — Code-shaper review

- Added 1 step to be addressed.

## 2026-11-08 — Access-check review follow-up

- Addressed 1 review step. Access checks return only status on success;
  helper names describe checks and selected-state locals describe operations.
  Preserved API/signature/access checks, classified failures, independent
  discovery and optional resolution outside classification catches.
- Java 8 parent (`1.8.0_492-b09`), EA3 children (`27-jep401ea3+1-1`):
  `clojure -M:cross-jvm-test` with namespaces `value-type.array-adapter-failure-test`,
  `value-type.array-loading-test`, `value-type.array-info-boundary-test`,
  `value-type.array-boundary-test` and `value-type.array-sequence-boundary-test`
  passed: 11 tests, 477 assertions, exit 0, no Scry failure files.
  Used the cached baseline `JAVA_CMD` and local EA3 `VALUE_TYPE_VALHALLA_JAVA`.
- EA3 parent, preview and exports: `clojure
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli`
  with namespaces `value-type.impl.array-test`, `value-type.array-test`,
  `value-type.array-sequence-test`, `value-type.array-info-test` and
  `value-type.array-layout-test` passed: 32 tests, 2676 assertions, exit 0,
  no Scry failure files.
- Changed-file formatting, `bb clj-kondo:lint` and `git diff --check` pass.

## 2026-11-08 — Code-shaper re-review

- No new actionable issues.

## 2026-11-10 — Closure

Closed as completed at the user's request. Implementation, review follow-ups
and knowledge extraction are complete. All implementation checklist items are
checked. The recorded verification and measurement limits still apply.
Historical commands retain their original open-task paths.
