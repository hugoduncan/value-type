# Implementation notes

## 2026-10-23 — Task creation

Created one task for the complete fallback contract in architecture
commit `660fe06`. Capability detection, application policy and AOT
namespace-load enforcement share one acceptance boundary.

No implementation or experiments have run for this task. The plan is
preliminary. Configuration details remain provisional; strict Error
retries and the absence of an extra fallback JDK minimum are confirmed
requirements.

## 2026-10-23 — Architecture review

- No architectural review feedback. Scope is unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Reviewed against
  `AGENTS.md` and the architecture linked from `ramora/INDEX.md`, including
  D10/D12/D13, runtime boundaries and source-only packaging.

## 2026-10-23 — Ambiguity review

- Ambiguity review added 1 new design step. Scope is unchanged.
- D9 permits expansion-only tooling on the value-class path. It does not
  establish when fallback diagnostics apply to that tooling. This affects
  observable policy behaviour, not just implementation placement.

## 2026-10-23 — Inconsistency review

- No new inconsistency review feedback. Scope is unchanged.

## 2026-10-23 — Design follow-up context

- Resolve diagnostic timing without treating a missing compiler loader
  binding as unavailable JVM capability. D9 separates these cases in
  `ramora/defvaluetype-design/decisions/validation-and-errors.md`.
- Check the selected timing against both compilation and namespace-load
  enforcement in `ramora/defvaluetype-design/embodiment/runtime.md` and
  `ramora/defvaluetype-design/fallback.md`. These are separate enforcement
  points, not alternative places to enforce policy.

## 2026-10-23 — Expansion timing follow-up

- Existing `not-installed-test` uses `support/on-raw-thread` to expand
  without a compiler-loader binding; `not-value-class-test` covers
  separate expansion and evaluation. Preserve these value-path checks.
- The compiler defines `deftype` classes during analysis, before runtime
  checks (`embodiment/runtime.md`). A runtime-only guard cannot prevent
  source class definition when a returned fallback expansion is compiled
  elsewhere. Include this case in the planned guard experiment.
- Design follow-up complete. No scope questions were present. No code or
  experiments ran; configuration and implementation decisions remain open.

## 2026-10-23 — Architecture review after expansion-timing change

- No new architectural review feedback. Compilation of returned fallback
  expansions preserves D13 application authority; expansion-only tooling
  on the value-class path retains D9 behaviour. Scope is unchanged.

## 2026-10-23 — Ambiguity review after expansion-timing change

- No new ambiguity review feedback. The completed timing follow-up resolves
  the earlier finding. Scope is unchanged.

## 2026-10-23 — Inconsistency review after expansion-timing change

- No new inconsistency review feedback. The revised timing contract agrees
  with the referenced architecture. Scope is unchanged.

## 2026-10-23 — Plan ambiguity review

- No new ambiguity review feedback. The preliminary plan explicitly gates
  implementation on the unresolved decisions; these are not new findings.
- For the planned test-support inspection: `test/value_type/test_support.clj`
  eagerly requires the loader and transformer. Its subprocess helper uses
  the host JDK, always enables preview and inherits checkout sources.
  It cannot serve the fallback matrix unchanged. The isolated consumer
  pattern is in `test-build/build_test.clj` and
  `mementum/knowledge/testing-library-artifacts.md`.

## 2026-10-23 — Plan inconsistency review

- No new inconsistency review feedback. The plan and steps agree with
  the design's decision gates and acceptance criteria, including the
  completed expansion-timing follow-up.

## 2026-10-23 — Verification handoff

- `test/value_type/environment_test.clj` requires value-class support and
  preview; `deps.edn` enables preview in `:test`. Keep these value-path
  checks distinct from fallback verification. Their failure on an
  incapable JVM is not evidence that fallback is broken.

## 2026-10-23 — Implementation pass: configuration gate

Read all task artifacts and the fallback architecture. No supplied actor-step
metadata overrides the task path or provides configuration approval. The
explicit pre-implementation approval gate remains unresolved. No code or
tests changed; no runtime verification ran.

<!-- IMPLEMENTATION_BLOCKER: START -->
- blocker: The public fallback configuration contract is provisional and requires user confirmation before implementation.
- required-human-action: Confirm or revise JVM property value-type.fallback, values silent/warn/error, default silent, read and validated once per loaded library runtime; invalid values fail and later changes are unsupported.
<!-- IMPLEMENTATION_BLOCKER: END -->

## 2026-10-23 — User approval of configuration

The user explicitly approved the public contract: JVM property
`value-type.fallback`; values `silent`, `warn`, `error`; default `silent`;
read and validated once per loaded library runtime; invalid values fail
clearly; later property changes are unsupported.

The configuration blocker recorded above is resolved. That block is a
historical record, not an active request for approval. The design, plan,
steps and architecture now reflect approval. Technical experiments and
plan finalization remain pending. No implementation or tests ran in this
approval update.

## 2026-10-23 — Architecture review after configuration approval

- No new architectural review feedback. The approved startup property and
  one-time validation fit D10 application authority and D13 state ownership.
  Scope is unchanged.

## 2026-10-23 — Ambiguity review after configuration approval

- No new ambiguity review feedback. Configuration is approved; capability
  detection, Error selection and test JDK selection remain explicit
  pre-implementation decisions, not unspecified requirements. Scope is
  unchanged.

## 2026-10-23 — Inconsistency review after configuration approval

- No new inconsistency review feedback. The design's approved configuration
  agrees with D10; its timing and AOT requirements preserve D9 and D13.
  Scope is unchanged.

## 2026-10-23 — Detailed planning and review

Updated `plan.md` and `steps.md` into six ordered slices. Configuration and
phase timing are settled. Capability detection, concrete Error selection,
guard mechanics and actual JDK selection are experiment gates before
production changes, not unresolved requirements or new approval requests.

Reviewed the plan for ambiguity with `steps.md` read-only. Each slice has
concrete actions and observable results; all eight acceptance criteria have
planned coverage. No blocking ambiguity was found.

Checked design consistency against D8/D9, D10/D12/D13, runtime and artifact
embodiment, and open architecture risks. No internal or cross-document
conflict was found. Current value-only code and README describe the existing
implementation, not evidence against the intended fallback contract.
Historical configuration blocker notes remain superseded by user approval.

Planning only: no production code, experiments or tests ran. `git diff
--check` passed. The first implementation action remains boundary inspection,
followed by real-JVM experiments and selection of the technical details.

## 2026-10-23 — Shared plan-review: ambiguity turn

- Reviewed detailed plan `f56fce5`: no new actionable ambiguity feedback.

## 2026-10-23 — Shared plan-review: inconsistency turn

- Reviewed detailed plan `f56fce5`: no new actionable inconsistency feedback.

## 2026-10-23 — Shared review handoff

- The inconsistency turn reused the ambiguity turn's loaded context after
  confirming no task-file changes since `c834a7d`. These were static reviews,
  not new runtime evidence for the slice 1 experiment gates.

## 2026-10-23 — Boundary inspection and initial JVM matrix

- Dependency boundary: `core` requires `impl.loader`, which requires
  `impl.transform`. The transform namespace imports `java.lang.classfile`
  at load time. Core's reflective `Class.isValue` check therefore does not
  protect baseline loading. Loader installation occurs at macroexpansion;
  transformed class definition and AOT byte writing occur during analysis;
  `check-value-class` runs in the returned expansion.
- `test-support` eagerly requires the loader. Its child runner inherits the
  host Java executable, checkout classpath and preview option. Build consumers
  already use temporary projects and POM dependencies; preserve that isolation.
- Selected JVM paths and full runtime versions are in `plan.md`. Both execute
  Clojure 1.12.6. Java 8 works on this machine despite its x86_64 VM build.
- Added repeatable assertion-based experiment `spike/fallback/api-check.clj`.
  Resolve CP with `JAVA_CMD=<valhalla-java> clojure -Spath`; run
  `<java> [--enable-preview] -cp "$CP" clojure.main
  spike/fallback/api-check.clj '<expected-map>'`. All three processes exited 0. Java 8 returned
  `{:classfile-api false :is-value-method false}`; Valhalla returned
  `{:classfile-api true :is-value-method true}` both with and without preview.
- Next: API presence cannot detect disabled preview. Test a known-format
  definition/link probe, narrow absence classification, and the transferred
  expansion guard. No production code changed; Error selection and AOT
  phase evidence are still pending. No new dependencies or human blocker.

## 2026-10-23 — Fixed-format capability experiment

- Added `spike/fallback/format-check.clj`: a real isolated loader defines
  format 71.65535 bytes, invokes the constructor and checks `Class.isValue`.
  No production changes, optional namespace loads, mocks or new dependencies.
- Reproduce with the runtime CP from `JAVA_CMD=<valhalla-java> clojure -Spath`:
  `<java> [--enable-preview] -cp "$CP" clojure.main
  spike/fallback/format-check.clj '<expected>'`.
  Java 8 and Valhalla without preview expect
  `{:outcome :unsupported-version}`; Valhalla with preview expects
  `{:outcome :defined :value? true}`. All three assertions passed (exit 0).
- Java 8 reports maximum format 52.0. Valhalla without preview reports
  disabled preview for 71.65535. Both use `UnsupportedClassVersionError`.
  This is evidence for narrow known-probe rejection, not general error recovery.
- ASM's packed version requires `unchecked-int`; checked coercion initially
  overflowed before class definition. Corrected and reran all three cases.
- Next slice: extend the probe with a strict field and pre-super assignment;
  the empty class alone does not establish the transform's full format.
  Then test Error chains and transferred-expansion/AOT guards before selecting
  production interfaces. No human blocker.

## 2026-10-23 — Strict-field capability experiment

- Extended `spike/fallback/format-check.clj` with an ACC_STRICT_INIT final
  `long` field and assignment before `Object.<init>`. The real constructor
  receives 42; reflective field readback must return 42. No mocks, optional
  APIs for byte generation, production changes or dependencies were added.
- Used the same CP and three Java commands recorded above. Java 8 and
  preview-disabled Valhalla returned `:unsupported-version`; preview-enabled
  Valhalla returned `{:outcome :defined :value? true :field-value 42}`.
  All three assertion processes exited 0.
- Next: Error cause chains and compile/evaluation/AOT guards remain open.
  Final detector selection must address format alignment: the probe fixes
  version 71, while the production transform uses the running JDK's latest
  major version. The matrix establishes this EA build, not future releases.

## 2026-10-23 — Source Error experiment

- Added `spike/fallback/error-check.clj`, a real compiler experiment with
  assertion-based checks and structured EDN output. No mocks, production
  changes or dependencies. Plain `java.lang.Error` is the candidate; no
  custom compiled subtype is needed for these source paths.
- Resolve CP with `JAVA_CMD=<valhalla-java> clojure -Spath`; run
  `<java> [--enable-preview] -cp "$CP" clojure.main
  spike/fallback/error-check.clj`. Java 8, Valhalla with preview, and
  Valhalla without preview all passed (exit 0).
- Both macroexpand entry points, eval of the macro, and load-string expose
  `[Compiler$CompilerException, java.lang.Error]`; a direct runtime call
  through eval exposes `[java.lang.Error]`. Every operation was repeated
  after catching its first failure. The rejected type was neither mapped
  in the namespace nor loadable through the compiler's base loader.
- Next: this proves rejection at initial macroexpansion only. It does not
  prove guards on transferred expansions or AOT initialization. Keep the
  broader Error/phase checklist items open until those experiments pass.

## 2026-10-23 — Transferred-expansion analysis guard

- Added baseline-safe experiment files `spike/fallback/phase_guard.clj` and
  `spike/fallback/transfer-check.clj`. A `do` barrier preserves the nested
  guard macro in both macroexpand results. Without that barrier, full
  macroexpand would remove the receiving compiler's policy check.
- Reproduction: use the runtime CP from `JAVA_CMD=<valhalla-java> clojure
  -Spath`, append `:spike`, then run Java 8 with `clojure.main
  spike/fallback/transfer-check.clj produce <temp-file>`. Run `consume
  <temp-file>` in fresh JVMs with `-Dvalue-type.fallback=silent` and `error`
  on each selected configuration. All seven processes exited 0.
- Silent consumers created identity objects with working fields/factories.
  Strict consumers rejected eval and source loading twice each, with no
  resolvable or loadable rejected class. This includes capable Valhalla:
  the transferred representation does not bypass receiving policy.
- Next: verify AOT initialization and its Error chain, plus runtime-only
  enforcement. The prototype intentionally has no warning implementation;
  it is not production policy state. No dependencies or production changes.


## 2026-10-23 — AOT initialization guard experiment

- Added `spike/fallback/aot-check.clj`. Compile a temporary namespace
  `fallback.aot-fixture` requiring `fallback.phase-guard` and defining
  `(fallback.phase-guard/fallback-type AotIdentity [x])` on Java 8.
  Use runtime CP plus `spike` and temporary source for `compile <output-dir>`;
  use runtime CP plus `spike` and output only for `load <output-dir>`.
  The load mode asserts the fixture source resource is absent.
- Seven processes passed: one silent compiler and silent/error consumers on
  Java 8 and Valhalla with preview enabled and disabled. Silent objects retain
  field 42 and identity inequality. No production changes or dependencies.
- Important discovery: first strict AOT load exposes `[java.lang.Error]`.
  Retry exposes `[java.lang.NoClassDefFoundError]` on Java 8 and
  `[java.lang.NoClassDefFoundError, java.lang.ExceptionInInitializerError]`
  on Valhalla. Initial assertions expected the original Error in every cause
  chain; corrected them to assert the observed JVM initialization contract.
  The retry still throws a Java Error, but does not execute policy again.
- Next: runtime-only evaluation and strict AOT compilation remain unverified.
  Warning state and final detector/format alignment are still open. No human
  decision is needed; do not mistake JVM failed-initializer state for a policy
  bypass or promise a stable concrete Error across AOT retries.

## 2026-10-23 — Strict AOT compilation experiment

- Extended `spike/fallback/aot-check.clj` compile mode: strict compilation
  rejects twice with `[Compiler$CompilerException, java.lang.Error]`.
  After each attempt the type has no class file, namespace mapping or
  loadable class. Silent compilation still emits the identity class.
- Six fresh processes passed (exit 0): silent/error compilation on Java 8
  and Valhalla with preview enabled and disabled. Used the same temporary
  fixture and runtime CP plus `spike` and source directory as the prior AOT
  experiment; pass `-Dvalue-type.fallback=error` for strict compilation.
  Each process had a separate output directory; temporary files were removed.
- No production changes, mocks or new dependencies. Next: runtime-only
  evaluation still needs proof separate from analysis. Detector/format
  alignment and final interfaces remain open; this does not finish slice 1.


## 2026-10-23 — Runtime-only guard experiment

- Added `spike/fallback/runtime-check.clj`. Compile a temporary namespace
  `fallback.runtime-fixture` requiring `fallback.phase-guard`, with
  `(defn define-type [] (fallback.phase-guard/fallback-type RuntimeIdentity [x]))`.
  Java 8 silent compilation uses runtime CP plus `spike` and temporary source,
  with script arguments `compile <output-dir>`. Consumers use runtime CP plus
  `spike` and output only, with script argument `invoke`.
- Seven processes passed: one compiler and silent/error consumers on Java 8
  and Valhalla with preview on/off. The script checks source absence. Loading
  succeeds under strict policy because the guard is inside the function;
  both subsequent calls throw plain Error. Silent calls preserve fields,
  factory and identity inequality. No mocks or dependencies were added.
- Phase experiments now support plain Error and the nested analysis/runtime
  guard. Runtime checks do not promise absence of an AOT-defined class.
  Next slice must resolve detector/transform format alignment and finalize
  architecture/interfaces. No production code changed or human blocker arose.


## 2026-10-23 — Running-JDK probe format

- Added optional `latest` mode to `spike/fallback/format-check.clj`. It uses
  reflective ClassFile.latestMajorVersion after the caller selects that mode;
  default mode remains baseline-safe and fixed at 71.65535.
- Resolve CP with `JAVA_CMD=<valhalla-java> clojure -Spath`. Run
  `<valhalla-java> [--enable-preview] -cp "$CP" clojure.main
  spike/fallback/format-check.clj '<expected-map>' latest`.
  Preview on returned `{:outcome :defined :value? true :field-value 42}`;
  preview off returned `{:outcome :unsupported-version}`. Java 8's default
  mode also retained its version rejection. All three processes exited 0.
- Select the running-JDK version rule for the probe, matching the existing
  transformer. No transformer change or fixed Java 27 restriction is needed.
  Unknown format/verification failures must still propagate. Required API
  checks, absence classification and state-owner interfaces remain to finalize.
  No production changes, mocks or dependencies were added.

## 2026-10-23 — Slice 1 decisions complete

- Selected baseline-safe capability detector plus runtime state owner and
  expansion-visible guard interfaces in the plan and runtime architecture.
  Absence catches are scoped to ClassFile lookup, isValue lookup and known
  probe version rejection. Later missing API members and invariant failures
  remain errors; no broad linkage recovery or message parsing is allowed.
- Production probes need unique binary names: DynamicClassLoader has a shared
  class cache. The one-process prototypes did not test repeated probe names.
- Re-ran api-check on Java 8 and both Valhalla modes, plus latest-format checks
  on both Valhalla modes, using the recorded runtime CP commands. All five
  processes exited 0 with the expected maps. No mocks or dependencies added.
- Slice 1 is complete. Next slice adds baseline-safe process verification;
  warning state and full syntax remain production-test obligations. No
  production code changed in this pass.

## 2026-10-23 — Slice 2 isolated verification support

- Added `value-type.jvm-runner` under `test-fallback`, with explicit Java,
  classpath, options and policy. It never adds preview or checkout paths.
  Output goes to temporary files to avoid pipe deadlock and reader-thread
  cleanup. Timeout terminates and waits for the child; all completion paths
  delete output files. Non-zero exits do not parse stale stdout.
- Added `:fallback-test`, selecting only `test-fallback` through Scry. No new
  dependency version or production code changed; existing `:test` is intact.
- Ran `JAVA_CMD=<java> clojure -M:fallback-test` on the recorded Java 8 and
  Valhalla executables, then Valhalla with `-J--enable-preview`. Each run
  exited 0: 4 tests, 15 assertions; no Scry failure files. Real child JVMs
  cover configuration, separate stderr, EDN, non-zero exit, timeout, malformed
  output and cleanup (including failed launch). Children do not inherit the
  host preview option or load loader/transform namespaces.
- Next slice can use this runner for fresh-runtime policy and capability
  tests. `:result` is last-line EDN only on success; `:exit :timeout` is not a
  policy failure. Production fallback acceptance remains open.

## 2026-10-23 — Slice 3 capability and policy state

- Added baseline-safe `impl.capability/detect` and `impl.runtime` state owner.
  The detector uses unique probe names and the running ClassFile major version.
  Only the selected lookup/version exceptions return absence; constructor,
  predicate and field-read failures propagate. No optional namespaces load.
- Policy initializes once, capability is delayed and cached, and warning state
  uses compare-and-set. Strict direct calls throw plain Error on each attempt.
  These are internal interfaces; the public macro still uses its old path.
- Added fresh-process tests for real capability, cached result identity, no
  optional namespace loading, all policies, repeated strict calls, one warning,
  require-only silence, invalid values and ignored later property changes.
  No mocks or dependencies added. Concurrent warnings remain a later test.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test` on Java 8 and
  Valhalla preview off: each exited 0, 8 tests / 43 assertions. Valhalla with
  `-J--enable-preview` exited 0, 8 tests / 41 assertions. Scry failure files
  were absent. clj-kondo on the three added files: no errors or warnings.
- Next: integrate core's lazy value dependency and guarded fallback expansion.
  Requiring the public namespace on Java 8 is not yet supported by this slice.

## 2026-10-23 — Slice 3 public source integration

- Core now resolves the loader only after positive capability detection.
  Fallback retains an outer do and the runtime-owned analysis macro; its
  emitted runtime check uses fixed-representation context. No dependencies
  added. Basic source definitions retain the class result, factory and field.
- Fresh-process public require tests confirm silence and no optional namespace
  loading for all policies. Source tests cover all policies on Java 8 and
  Valhalla preview off/on: identity on incapable runtimes, value equality on
  capable runtimes, and compiler-wrapped Error with no namespace type mapping
  for strict fallback. Warn emits once across selection and evaluation.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test` on Java 8 and
  Valhalla preview off; each passed 10 tests / 55 assertions. Valhalla with
  `-J--enable-preview` passed 10 / 53. Existing core tests passed 12 / 46 via
  `clojure -M:test -m scry.cli --namespace value-type.core-test`. All exits 0;
  no Scry failure files. Changed Clojure files lint without warnings/errors.
- Next: full source compatibility, mutable metadata and macroexpansion/retry
  coverage remain unchecked. Production transferred-expansion and AOT tests
  are still required; prototype evidence is not acceptance coverage.

## 2026-10-23 — Slice 3 source compatibility and validation

- Added real compiler fixtures under `test-fallback`. Fallback observations
  match ordinary deftype for factories, imports, constructors, primitive and
  reference hints, protocols, interfaces, methods, identity equality and hash
  behaviour. Hash tests do not assume distinct objects have distinct hashes.
- Both mutable metadata keys reject true values with `:mutable-field` and no
  type mapping. False values produce final fields in both representations.
  Reserved fields and non-vector fields retain AssertionError rejection.
  No production changes or dependencies were needed.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test` on Java 8 and
  Valhalla preview off: each passed 12 tests / 64 assertions. Valhalla with
  `-J--enable-preview` passed 12 / 59. All exits were 0; no Scry failure
  files. Lint passed with defvaluetype mapped to deftype via `:lint-as`.
- Next: macroexpand entry points with/without loader bindings, discarded
  expansions, evaluation and strict retries remain open. These fixtures do
  not force fallback on capable JVMs; identity checks run only when incapable.

## 2026-10-23 — Slice 3 macroexpansion and strict retries

- Added real compiler checks for both macroexpand entry points on a raw thread
  without a compiler-loader binding and with an explicit loader binding.
  Discarded expansions leave types unmapped and unloadable; later evaluation
  and source definition share the same warning state. Strict attempts repeat
  after caught errors and leave no mapped or loadable type. Their cause chain
  is Compiler$CompilerException followed by java.lang.Error.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test` on Java 8 and
  Valhalla preview off: each passed 13 tests / 84 assertions. Valhalla with
  `-J--enable-preview` passed 13 / 65. All exits 0; no Scry failure files.
  clj-kondo passed both changed Clojure files without warnings or errors.
- No production changes or dependencies. Capable JVMs skip these identity
  checks rather than force fallback. Next: D9 tooling and defect regressions,
  then production transfer/AOT coverage. Warning concurrency remains open.

## 2026-10-23 — Slice 3 capable-JVM regressions

- Ran the existing core, loader and transform suites in fresh Valhalla
  `27-jep401ea3+1-1` processes under silent, warn and error. Each passed
  40 tests / 251 assertions; all exits 0 and no Scry failure files. Command:
  `JAVA_CMD=<selected-valhalla-java> clojure -J-Dvalue-type.fallback=<policy>
  -M:test -m scry.cli --namespace value-type.core-test
  --namespace value-type.impl.loader-test --namespace value-type.impl.transform-test`.
- Existing tests cover missing loader bindings, separate expansion/evaluation,
  actual ClassFormatError and VerifyError rejection, and invalid constructor
  transforms. These remain errors under every policy; no mocks, production
  changes or new tests were needed for this regression slice. Loader defect
  fixtures exercise the loader directly, not invalid public mutable forms,
  which core correctly rejects before loading.
- Slice 3 is complete. Next: test production transferred expansions in fresh
  receiving runtimes; then warning concurrency and source-free AOT fixtures.
  Prototype phase evidence is not a substitute for those acceptance tests.

## 2026-10-23 — Slice 4 production expansion transfer

- Added real Java 8 production expansion serialization and fresh-process
  consumers. Both macroexpand entry points retain receiving guards through
  eval and source loading. Silent/warn consumers retain identity semantics,
  fields and factories even on capable Valhalla. Strict retries leave no
  mapped or loadable type; chains are Compiler$CompilerException → Error.
- Warn consumers emit exactly one fixed-representation diagnostic across
  all attempts and namespaces. This is sequential coverage, not concurrency
  coverage. No production changes, mocks or dependencies were needed.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.transfer-test` on Java 8 and Valhalla preview off; repeated on
  Valhalla with `-J--enable-preview`. Each passed 1 test / 14 assertions,
  exit 0; no Scry failure files. clj-kondo reported no errors or warnings.
- The producer defaults to the recorded Java 8 executable; CI can set
  VALUE_TYPE_BASELINE_JAVA explicitly. Missing executables fail, not skip.
  Next: synchronized warning tests and production source-free AOT coverage.

## 2026-10-23 — Slice 4 concurrent warning scope

- Added eight latch-synchronized compiler workers, each evaluating two types
  in its own namespace. They consume a real Java 8 fallback expansion, so the
  same test exercises fixed identity representation on capable Valhalla too.
  Each fresh warn consumer emits exactly one complete stderr diagnostic and
  returns all 16 working identity factories and fields. Two independent
  consumers per matrix entry establish separate warning state.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.transfer-test` on Java 8 and Valhalla preview off, then Valhalla
  with `-J--enable-preview`: each passed 2 tests / 21 assertions, exit 0.
  No Scry failure files; clj-kondo passed both changed Clojure files.
- No production changes, mocks, sleeps or dependencies. This covers shared
  analysis/evaluation warning state, not AOT initialization. Next: production
  fallback AOT compilation and source-free receiving-policy checks.


## 2026-10-23 — Slice 4 production AOT compilation

- Added temporary production macro fixtures and real fresh-JVM compilation
  checks. Silent/warn emit a working identity class and factory; warn emits
  one diagnostic. Strict compilation rejects twice with CompilerException →
  Error and no emitted, mapped or loadable type. Temporary files are removed.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.aot-test` on Java 8 and Valhalla preview off, then Valhalla with
  `-J--enable-preview`. Each passed 1 test / 10 assertions, exit 0; no Scry
  failure files. clj-kondo passed both new files without errors or warnings.
- Children intentionally omit preview: both host Valhalla runs exercise an
  incapable compiler. No production changes, mocks or dependencies added.
  Next: reuse compiled output in source-free receiving runtimes under all
  policies; compilation checks alone do not establish AOT load enforcement.

## 2026-10-23 — Slice 4 source-free fallback AOT loading

- Added fresh consumers for silent/warn Java 8 production artifacts. Fixture
  source is deleted and excluded from the consumer classpath; the consumer
  also asserts resource absence. Silent/warn loads preserve identity, fields
  and factories. Repeated warn loads emit one fixed-representation diagnostic.
- Strict first load throws Error; retry throws NoClassDefFoundError, with an
  ExceptionInInitializerError cause on Valhalla and no cause on Java 8. This
  verifies production guards, not just the earlier prototype.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.aot-test` on Java 8 and Valhalla preview off, then Valhalla with
  `-J--enable-preview`. Each passed 2 tests / 32 assertions, exit 0. No Scry
  failure files remained; clj-kondo passed both changed files.
- No production changes, mocks or dependencies. Next: value-class AOT
  constraints and direct-class-load guard limits, then isolated JAR consumers.


## 2026-10-23 — Slice 4 value AOT constraints

- Compile the production fixture under strict policy on preview-enabled
  Valhalla, then delete its source. Fresh consumers under all three policies
  retain value representation on that JVM. Java 8 and preview-disabled
  Valhalla report UnsupportedClassVersionError, without fallback diagnostics.
  The optional producer override is VALUE_TYPE_VALHALLA_JAVA.
- Namespace guards do not intercept direct class loads that bypass namespace
  initialization. They cannot convert an AOT class or guarantee that the JVM
  has not loaded a type before namespace rejection. These remain scope limits,
  not guarantees established by namespace-loading tests.
- Ran `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.aot-test` on Java 8 and Valhalla preview off, then Valhalla with
  `-J--enable-preview`: each passed 3 tests / 43 assertions, exit 0. No Scry
  failure files; clj-kondo reported no errors or warnings in both changed files.
- No production changes, mocks or dependencies. Slice 4 is complete.
  Next: isolated source-JAR consumers and full regression verification.


## 2026-10-23 — Slice 5 isolated source-JAR consumers

- Extended the real artifact consumer to Java 8 without preview, using
  VALUE_TYPE_BASELINE_JAVA or the recorded baseline executable. Both consumers
  use only the artifact and POM-resolved runtime JARs and assert that the public
  source resource belongs to that artifact. Fields and factories work in both
  modes; equal copies compare unequal on Java 8 and equal on Valhalla.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:build-test -m scry.cli --namespace build-test`: exit 0, 6 tests /
  63 assertions. No Scry failure files. Source-only archive and runtime POM
  checks pass, including the added production namespaces. clj-kondo passed.
- No production changes, mocks or dependencies. Next: full fallback matrix
  and existing value-path regressions, then documentation and acceptance review.


## 2026-10-23 — Slice 5 full regression verification

- Ran the explicit fallback entry point with the selected Java 8
  `1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1` executables:
  `JAVA_CMD=<java> clojure -M:fallback-test`, adding
  `-J--enable-preview` for the capable Valhalla run. Results: 18 tests /
  148 assertions on Java 8 and preview-disabled Valhalla; 18 / 129 on
  preview-enabled Valhalla. Each process exited 0; each Scry failure
  directory was empty. The capable run has fewer assertions because
  incapable-only source checks do not force fallback.
- On the selected Valhalla executable, ran `clojure -M:test -m scry.cli
  --dir test`: 64 tests / 332 assertions passed. Ran
  `clojure -M:build-test -m scry.cli --namespace build-test`: 6 tests /
  63 assertions passed. Both aliases enable preview. Both exits were 0;
  both Scry failure directories were empty.
- No code, test or dependency changes were needed. Existing real-JVM tests
  meet this verification slice; no mocks were added. Slice 5 is complete.
  Next: update public documentation and architecture, map all eight
  acceptance criteria to evidence, then complete the final review.


## 2026-10-23 — Slice 6 public documentation

- Corrected stale README claims that all consumers need Valhalla and that
  unavailable preview causes a user-class loader error. Scoped the example
  and value semantics to the value path. Documented startup policy, shared
  warning state, discarded expansions, strict cause chains and AOT limits.
- Expanded the public macro docstring without changing executable forms.
  No tests or dependencies were added: existing real-JVM coverage already
  verifies these contracts. Architecture evidence mapping remains next.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:test -m scry.cli --namespace value-type.core-test`: exit 0, 12 tests /
  46 assertions passed, no Scry failure files. `git diff --check` passed.


## 2026-10-23 — Slice 6 architecture alignment

- Replaced intended/unimplemented fallback claims with the implemented lazy
  boundary, narrow detector, shared policy state and phase guards. Scoped
  assertions to the recorded JVM matrix; the general baseline remains a
  constraint, not an exhaustive support claim. Recorded AOT retry limits.
- Updated runtime, artifact, decision and risk documents. Split long detail
  sections into linked files to keep architecture pages below 1000 words.
- Documentation-only pass: checked descriptions against production source
  and recorded real-JVM tests. No executable forms, dependencies or tests
  changed; no runtime tests rerun. `git diff --check` passed.
- Next: map all eight acceptance criteria to specific evidence, then perform
  final implementation review. This pass does not claim that review is done.

## 2026-10-23 — Slice 6 acceptance mapping and final review

Evidence below refers to the recorded Slice 3–5 runs above, not new matrix
runs in this pass. All eight criteria have evidence on the selected JVMs.

1. **Value path:** `runtime-test/public-definition-test` exercises all policies;
   Slice 3 core/loader/transform runs passed 40 tests / 251 assertions per
   policy. Slice 5 full library regressions, including compatibility, passed
   64 / 332 on preview-enabled Valhalla.
2. **Capability and configuration:** `runtime-test/capability-test`,
   `policy-test`, `invalid-policy-test` and `public-definition-test` cover
   capability, default, all policies and immutable startup configuration.
   Slice 5 fallback matrix passed 18 tests / 148, 148, 129 assertions on
   Java 8 and Valhalla preview off/on respectively.
3. **Diagnostics and expansion timing:** `public-require-test`,
   `fallback-expansion-policy-test`, `transfer-test/transferred-expansion-test`
   and `concurrent-warning-scope-test` cover require-only silence, both
   expansion entry points, bound/unbound loaders, discarded expansions,
   later evaluation, receiving policy and concurrent namespace warning scope.
   Included in the same passing matrix.
4. **Strict retries:** expansion and transfer tests check repeated compiler
   rejection and absent types. `policy-test` checks repeated direct Error.
   `aot-test` records first-load Error and retry NoClassDefFoundError.
   Compiler operations retain CompilerException → Error. Matrix passed.
5. **Compatibility and defect visibility:** `fallback-source-compatibility-test`
   compares actual deftype and fallback factories, hints, imports, methods,
   protocols, fields, identity and hashes. `source-field-validation-test`
   checks both mutable keys and false metadata. Slice 3 D9 and real
   loader/transform defect regressions passed under all policies; failures
   do not enter a fallback retry path.
6. **AOT:** all three `aot-test` tests cover identity compilation, strict
   pre-output rejection, source-free fallback receivers and value-artifact
   constraints. Slice 4 passed 3 tests / 43 assertions on each JVM setting;
   Slice 5 includes these checks. Direct class access remains outside guards.
7. **Packaging:** `build-test/isolated-consumer-test` uses the exact source
   JAR and POM-resolved runtime JARs on Java 8 and capable Valhalla.
   `source-jar-contract-test` verifies archive contents. Slice 5 build suite
   passed 6 tests / 63 assertions.
8. **Documentation:** README, core macro docstring, fallback decisions,
   runtime/artifact descriptions, risks and assertions state the implemented
   contract. Coverage is explicitly Java 8 and the selected Java 27 EA,
   not every Clojure JVM or future EA format.

Final review checked production selection and catch boundaries, shared state,
phase guards, real-JVM test assertions, packaging isolation and documentation
against the task contract. No actionable behavior gap was found. No new tests
or dependencies were needed. The optional lazy resolution is a dependency
boundary, not a cycle workaround; capability absence catches are required by
the contract and remain narrow.

Verification this pass: core regressions on selected preview-enabled Valhalla
passed 12 tests / 46 assertions, exit 0, with no Scry failure files. Plain
clj-kondo cannot infer the custom type macro; rerunning with
`--config '{:lint-as {value-type.core/defvaluetype clojure.core/deftype}}'`
on `src test-fallback` passed with zero errors and warnings. No suppression
of actual findings was needed. `git diff --check` passed.

Implementation is complete. No missing acceptance evidence or human blocker
was identified. Keep the task open for the external review/closure workflow;
no representation or policy contract changed in this pass.

## 2026-10-23 — Implementation review

- Added 2 steps to be addressed.

- 2026-10-23: Addressed review documentation step. D9 now describes the
  production capability boundary; README limits preview AOT constraints to
  value output. Checked against implementation; `git diff --check` passed.

- 2026-10-23: Addressed the runtime-only AOT review step (2 review steps
  addressed in total). Added `source-free-runtime-only-fallback-test` and
  `invoke-runtime-fixture`. A Java 8 silent producer compiles the production
  macro inside a function; fixture source is deleted before fresh consumers
  load it. All policies load successfully. Two strict calls each return the
  exact `["java.lang.Error"]` cause chain; silent/warn calls preserve fields,
  factory and identity behavior. Warn emits one fixed-representation warning.
  No production code changed.
  Verification: `JAVA_CMD=<java> clojure -M:fallback-test --namespace
  value-type.aot-test`, with `-J--enable-preview` for the preview-enabled run.
  Used the plan's Temurin 8 and Valhalla 27 executables. Each of the three
  configurations passed 4 tests / 54 assertions, exit 0; `.scry-results`
  contained no files after each run. `git diff --check` passed.

## 2026-10-23 — Follow-up implementation review

- Verified both completed review steps; no new actionable findings.

## 2026-10-23 — Test review

- Added 2 steps to be addressed.

- 2026-10-23: Addressed test-review step 1. Removed the four JVM-runner
  tests; retained runner support unchanged. Production feature tests verify
  process isolation and policy diagnostics. `JAVA_CMD=<selected-java> clojure
  -M:fallback-test` passed on Java 8 and Valhalla preview off/on (add
  `-J--enable-preview` for on): 15 tests, 144/144/125 assertions. All exits
  were 0; `.scry-results` was empty after each run.

- 2026-10-23: Addressed test-review step 2 (2 review steps addressed).
  Compatibility expectations now use the selected host JVM configuration,
  not the child result shape. Capable Valhalla requires `:value-mode`;
  incapable entries require the complete ordinary/portable comparison map.
  Nil, scalars and incomplete maps cannot skip assertions. No mocks or
  production changes. `JAVA_CMD=<selected-java> clojure -M:fallback-test
  --namespace value-type.runtime-test` passed on Java 8 and Valhalla preview
  off/on (add `-J--enable-preview` for on): 9 tests, 67/67/51 assertions.
  All exits were 0; `.scry-results` was empty after each run. Lint and
  `git diff --check` passed.

## 2026-10-23 — Follow-up test review

- Added 1 step to be addressed.

- 2026-10-23: Addressed 1 review step. Fresh warn consumers now capture
  diagnostics after each expansion entry point with bound/unbound loaders,
  before evaluation. Each requires one warning and no mapped or loadable type;
  evaluation of the returned expansion defines the type without another warning.
  No production changes or mocks. Runtime tests passed on Java 8 and Valhalla
  preview off/on: 10 tests, 91/91/63 assertions. Used the plan's executables
  with `clojure -M:fallback-test --namespace value-type.runtime-test`
  (add `-J--enable-preview` for on). All exits were 0 and `.scry-results`
  was empty after each run. Lint and `git diff --check` passed.

## 2026-10-23 — Warning-timing test review

- No new actionable findings. Runtime tests passed on Java 8 and Valhalla
  preview off/on: 10 tests, 91/91/63 assertions; all exits 0, no Scry failure files.

## 2026-10-23 — Test economy and evidence review

- Added 2 steps to be addressed.

- 2026-10-23: Addressed 1 review step. Removed redundant internal require-only
  coverage; public require coverage remains unchanged. Runtime suite passed
  with the plan’s Java executables and `clojure -M:fallback-test --namespace
  value-type.runtime-test` (add `-J--enable-preview` for preview on): Java 8
  and Valhalla preview off/on, 9 tests, 88/88/60 assertions. All exits 0;
  no Scry failure files. `git diff --check` passed.

- 2026-10-23: Addressed the remaining review step (2 addressed in this pass).
  Architecture index now states implemented fallback and AOT guards, links
  production evidence, and separates tested JVM coverage from the general
  fallback baseline. Checked against fallback.md and recorded task results;
  `git diff --check` passed. Documentation only; no additional tests run.

## 2026-10-23 — Expansion-policy test review

- Added 1 step to be addressed.

- 2026-10-23: Addressed 1 review step. Expansion-policy expectations now
  use the host JVM configuration, not the child result. Capable runs require
  `:value-mode`; incapable runs require all bound/unbound and evaluation/source
  phase results under each policy. No production changes or mocks. Runtime
  tests passed on the plan’s Java 8 and Valhalla preview off/on executables:
  `clojure -M:fallback-test --namespace value-type.runtime-test` (add
  `-J--enable-preview` for on), 9 tests, 88/88/63 assertions. All exits 0;
  no Scry failure files. Lint and `git diff --check` passed.

## 2026-10-23 — Public-definition test review

- Added 1 step to be addressed.

- 2026-10-23: Addressed 1 review step. Public-definition expectations now
  use the selected host JVM configuration and check the child capability
  against it. Representation, strict rejection and diagnostics no longer
  depend on the reported capability. Existing fresh processes are unchanged.
  Runtime suite passed on the plan's Java 8 and Valhalla preview off/on:
  `JAVA_CMD=<selected-java> clojure -M:fallback-test --namespace
  value-type.runtime-test` (add `-J--enable-preview` for on), 9 tests,
  91/91/66 assertions. All exits 0; no Scry failure files. Lint and
  `git diff --check` passed. No production changes.

## 2026-10-23 — Public-definition follow-up test review

- No new actionable findings.

## 2026-10-23 — Documentation review

- Added 2 steps to be addressed.

- 2026-10-23: Addressed 1 documentation review step. Qualities now links
  implemented fallback evidence and separates tested coverage from the general
  baseline. Checked against fallback.md and README; `git diff --check` passed.
  Documentation only; no runtime tests rerun.

- 2026-10-23: Addressed 1 documentation review step. Runtime boundaries now
  state the shared DynamicClassLoader and Class.isValue dependencies, while
  retaining loader ownership of Compiler/LOADER and Compiler/writeClassFile.
  Checked against capability.clj, core.clj and loader.clj; `git diff --check`
  passed. Documentation only; no runtime tests rerun.

## 2026-10-23 — AOT documentation review

- Added 1 step to be addressed.

- 2026-10-23: Addressed 1 review step. README, D13, runtime embodiment and
  public macro docstring now distinguish top-level AOT initialization from
  function invocation. Retained direct-class-access and failed-initializer
  limits. Checked against guarded-deftype, source-free-runtime-only-fallback-test
  and invoke-runtime-fixture; `git diff --check` passed. Documentation only;
  no runtime tests rerun.

## 2026-10-23 — Architecture summary documentation review

- Added 1 step to be addressed.

- 2026-10-23: Addressed 1 review step. Four architecture summaries now state
  AOT policy timing at form evaluation, with direct-access and failed-initializer
  limits. Checked against D13, source-free-runtime-only-fallback-test and
  invoke-runtime-fixture; `git diff --check` passed. Documentation only;
  no runtime tests rerun.

## 2026-10-23 — Architecture summary documentation follow-up review

- No new actionable findings.

## 2026-10-23 — Code-shaper review

- Added 2 steps to be addressed.

- 2026-10-23: addressed code-shaper review step 1. Added baseline-safe
  `jvm-config/configuration`; all three suites use its explicit runner maps
  and independent expectations. Unknown host configurations fail clearly.
  Incapable AOT compilation still removes preview explicitly. Full Scry
  fallback matrix passed: Java 8 and Valhalla preview off/on, 15 tests with
  166/166/141 assertions; all exits 0, no failure EDN files. Command:
  `JAVA_CMD=<java> clojure [-J--enable-preview] -M:fallback-test`.
  Lint and diff checks passed.

- 2026-10-23: addressed code-shaper review step 2. Extracted baseline-safe
  AOT fixture creation and finally cleanup into `aot-fixture/with-fixture`.
  Policies, source deletion, consumer classpaths and assertions stay in each
  scenario. Shared top-level source replaces three copies. Existing AOT
  suite passed on Java 8 and Valhalla preview off/on: 4 tests / 54 assertions
  each, exits 0, no Scry failure EDN files. Command: `JAVA_CMD=<java> clojure
  [-J--enable-preview] -M:fallback-test --namespace value-type.aot-test`.
  Lint and diff checks passed. Both review steps are complete.

## 2026-10-23 — Code-shaper follow-up review

- No new actionable findings after review of both completed refactors.

## 2026-10-23 — Closure

Closed as complete at the user's request. Implementation and all review
follow-ups are complete; all checklist items are checked. Final code-shape
review found no new actionable issues (3fb99b1).

Knowledge extraction created `controlled-fallback-boundaries.md` (7251bcd)
and updated `java-lang-classfile.md` (030aee5) and
`testing-library-artifacts.md` (854e76d) under `mementum/knowledge/`.

Closure changes task location, active-plan membership, the evidence link
and working state only. No runtime tests were rerun. Recorded verification
remains in the entries above.
