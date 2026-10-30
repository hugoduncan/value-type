# Implementation notes

## 2026-10-27 — Task creation

Created from the portable non-nil field design in commit `996cd56` and the request
to implement it. The current implementation remains native-only. No implementation
or feasibility experiment has run for this task. The baseline-safe bytecode backend
is the first technical decision to resolve with evidence.

## 2026-10-27 — Architectural review

- No architectural review feedback. Scope was kept fixed; the task's plan and
  steps were not reviewed.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context came
  from `AGENTS.md`, `ramora/INDEX.md` and its linked macro, non-nil field and
  fallback documents.

## 2026-10-27 — Ambiguity review

- No ambiguity review feedback.

## 2026-10-27 — Inconsistency review

- No inconsistency review feedback.

## 2026-10-27 — Review handoff

- `mementum/knowledge/controlled-fallback-boundaries.md` explains compiler-phase
  and loader constraints. Its “Native requirements need receiver-local compiler
  state” section records task 016 behavior; do not carry its unconditional native
  capability requirement into the portable contract.

## 2026-10-27 — Plan ambiguity review

- Reviewed `plan.md` and read-only `steps.md` against the design and referenced
  code, tests and documentation. No new actionable ambiguity feedback.

## 2026-10-27 — Plan inconsistency review

- Checked consistency across the task design, plan and read-only steps using
  the context loaded for the plan ambiguity review. No new actionable
  inconsistency feedback.

## 2026-10-27 — Plan review handoff

- `test/compatibility/value_type/field_capability_test.clj` combines probe tests
  with public native-only policy expectations. Preserve independent probe checks
  when replacing the public rejection cases with portable-field cases.
- `test/compatibility/value_type/field_aot_test.clj` has reusable source-removal
  and separate-consumer checks, but its producer uses strict policy and its Java
  compilation uses EA3 preview flags. Adapt both for identity-output coverage.


## 2026-10-27 — Slice 1 evidence inventory and toolchains

- Added `evidence-map.md` for acceptance 1–9, reusable fixtures and missing cases.
  No production behavior or dependency changed. The design remains unchanged.
- Verified Java and javac at both paths:
  - `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/`:
    Java `1.8.0_492-b09`, javac `1.8.0_492`.
  - `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/`:
    Java `27-jep401ea3+1-1`, javac `27-jep401ea3`.
  These match the existing child-runner defaults. Override with
  `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` on other hosts.
- Verification command:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:fallback-test --namespace value-type.fields-test --namespace value-type.field-capability-test`
  Parent had no preview or exports. Scry exited 0; 16 tests and 164 assertions
  passed, with no failure files in `.scry-results/`. Capability children use
  explicit Java 8 and EA3 preview-off/on configurations. This verifies the
  existing native contract only, not the proposed generated checks.
- Next: build the isolated public-declaration ASM experiment. Do not reuse the
  capture namespace unchanged: it eagerly requires optional transforms and creates
  ClassDesc values. Keep backend selection open until baseline execution/AOT and
  generated value-owner constructor gates pass. No access or human decision blocks
  this work.

## 2026-10-27 — Slice 1 identity backend experiment

- Added `spike/portable-fields/` without production or dependency changes.
  The experiment macro installs a real capture loader and calls public
  `defvaluetype` on baseline JVMs. It supports only String constraints and is
  not a candidate public API or a complete declaration validator.
- Bundled ASM preserves existing frames and maximum stack sizes. At each selected
  PUTFIELD it calls Java 7+ `Objects.requireNonNull`, then CHECKCAST, without
  branches or additional stack depth. It retypes storage and owner/companion
  references. Real factory, direct and reflective constructors enforce checks.
- Initial tests caught loss of the loader when the public fallback expansion
  emitted a top-level `do`. A `let` boundary around the experiment declaration
  keeps receiving analysis in the installed compiler binding. Production receiving
  expansion work must retain this boundary; installation alone is insufficient.
- Verification: `bash spike/portable-fields/verify.sh` exited 0. Java 8
  `1.8.0_492` and EA3 `27-jep401ea3` ran without preview or exports. Each source
  run passed 1 test / 13 assertions. A fresh Java 8 process passed the same
  13 assertions from AOT artifacts with the experiment source path absent.
  Scry failure directories were empty. Formatting passed; scoped lint passed
  with no errors or warnings (one redundant-coercion info).
- The fixture checks final String storage, Object reference constructor parameters,
  long/double slots, nullable controls, direct/reflective reads, owner methods and
  captured-receiver closures. The script rebuilds artifacts under ignored target/.
- Next: prove generated checks in value owners for identity tags and controlled
  native absence. This experiment does not establish value initialization safety,
  exact-Class resolution, transferred expansions, Java consumers or general
  production compiler-shape validation. Backend selection remains open. No human
  decision or external access blocks the next slice. Design scope is unchanged.

## 2026-10-27 — Slice 1 value-owner gate and backend selection

- Added an isolated value experiment that uses public representation selection and
  the existing value transformer, then retypes fields and adds requireNonNull and
  CHECKCAST before strict writes. No production code or dependency changed.
- String and already-defined value-class fields pass actual constructor verification.
  Native annotations are deliberately absent for both fields. This controls enforcement
  selection without mocks or probe replacement; it does not simulate a different JVM.
- Select bundled ASM for identity output and java.lang.classfile for value output.
  Branch-free generated checks can run before Object initialization. The experiment
  composes two transforms; production should emit checks in its constructor builder.
- `bash spike/portable-fields/verify.sh` exited 0 on the previously recorded Java 8
  and EA3 paths. Baseline source runs and source-free identity AOT each passed
  1 test / 13 assertions. EA3 preview-enabled `-Xmixed` and `-Xint` each passed
  1 test / 22 assertions, without exports. Scry failure directory was empty.
  Formatting passed; scoped lint reported no errors or warnings (two coercion infos).
- Value evidence includes final concrete fields, Object constructor parameters,
  long/double slots, nullable nested data, owner/companion reads and nil/type failures
  through factory, direct and reflective construction. No value AOT or Java consumer
  claim is made. Next slice: declaration validation and enforcement selection; retain
  independent native probe tests and add no experiment hooks to production.

## 2026-10-27 — Slice 2 metadata boundary

- Public declarations now select `:non-nil`. Presence of `:nil-restricted`
  throws structured `:invalid-field` with `:removed-nil-restricted`, even for
  false/nil or combined keys. Invalid new values use `:invalid-non-nil`.
  Mutable validation still runs first. Active fixtures migrated; historical
  evidence is unchanged. No dependency or design change.
- This is only the parsing step. Eligibility, capability policy and constructors
  remain native-only. Do not infer portable enforcement from the new spelling.
  Next: concrete-class eligibility and explicit per-field enforcement selection.
- EA3 commands with JAVA_CMD set to the recorded EA3 executable:
  `clojure -M:fallback-test --namespace value-type.fields-test --namespace value-type.field-capability-test`
  exited 0: 17 tests / 196 assertions. This includes real child JVM capability
  checks and public old-key rejection without mocks.
  `clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test --namespace value-type.field-eligibility-test`
  exited 0: 7 tests / 114 assertions, preview enabled, no exports.
  Both successful runs left no Scry failure files.
- Scoped `bb clj-fmt check` passed. Scoped clj-kondo passed with zero errors
  and warnings. An additional full `clojure -M:fallback-test` attempt exited
  137 without failure artifacts; it is not a passing suite result. Full
  compatibility verification remains required; no external blocker is established.


## 2026-10-27 — Slice 2 concrete validation boundary

- Added `validate-concrete-type`, independent of optional APIs. It preserves exact
  identity/value Classes and rejects primitive, array, interface, abstract and
  enclosing types. The existing native validator calls it, then retains its
  native-only guard. Public specifications do not yet accept identity tags; this
  avoids sending them to native-only output before generated checks exist.
- Tests use real imported/external JDK Classes and an ordinary fallback declaration,
  without mocks. No dependency or design-contract change. Next: integrate concrete
  validation with explicit enforcement selection, then generated constructors.
- Java 8 command (JAVA_CMD set to the recorded Java 8 path):
  `clojure -M:fallback-test --namespace value-type.fields-test` exited 0:
  14 tests / 117 assertions. EA3 command (JAVA_CMD set to the recorded EA3 path):
  `clojure -M:test -m scry.cli --namespace value-type.field-eligibility-test`
  exited 0: 3 tests / 54 assertions, preview enabled without exports.
  Both runs left no Scry failure files. Scoped formatting and clj-kondo passed
  with zero lint errors or warnings. Full policy and deployment matrices remain open.

## 2026-10-27 — Slice 2 specification modes

- Added `:enforcement` to ordered specifications. The four-argument form accepts
  native capability as explicit data; it selects native only for a real value
  Class with capability present. Other concrete Classes select generated checks.
  The three-argument public path still validates native eligibility. Do not wire
  the new arity into core until generated constructor backends are ready.
- Tests use real String, Date, library value and external value Classes. Controlled
  capability absence tests selection logic only, not a new JVM configuration.
  No dependency or design-contract change. Public portability remains unfinished.
- Java 8: `clojure -M:fallback-test --namespace value-type.fields-test`, with
  JAVA_CMD set to the recorded Java 8 executable, exited 0: 15 tests and 125
  assertions passed. EA3: `clojure -M:test -m scry.cli --namespace
  value-type.field-eligibility-test`, with JAVA_CMD set to the recorded EA3
  executable, exited 0: 4 tests and 60 assertions passed with preview, no exports.
  Both Scry failure directories were empty. Scoped formatting and clj-kondo
  passed with zero lint errors or warnings.

## 2026-10-27 — Value backend generated checks

- Added a fourth transformer argument: a set of emitted field names that use
  generated enforcement. Names must have concrete storage entries. Checks run
  before casts and strict writes; native annotations apply only to other selected
  storage fields. Existing transformer arities retain native behavior.
- This is a backend prerequisite for public mode selection, not public portability.
  The loader does not yet pass generated modes. No dependency or design-contract
  change. Next: wire specifications, test mixed modes, and integrate identity output.
- Real deftype bytes transformed and loaded on EA3 verify String storage, absent
  native annotations, valid construction, NullPointerException and ClassCastException.
  An existing private constructor-builder test needed its new argument; it now passes.
- With JAVA_CMD set to the recorded EA3 path, `clojure -M:test -m scry.cli
  --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test`
  exited 0: 23 tests / 214 assertions, preview enabled without exports.
  Scry failure directory was empty. Scoped formatting and clj-kondo passed with
  zero lint errors or warnings. Mixed enforcement and interpreter evidence remain open.


## 2026-10-27 — Value loader mode transport

- The capture loader now maps `:generated` specifications to munged field names
  and passes them to the value transformer. Exact-Class checks and companion
  rewriting are unchanged. Public declarations still use native-only selection;
  this does not enable identity tags at the public boundary. No dependency or
  design-contract change.
- A real compiler fixture verifies mixed native value storage and generated String
  storage, final fields, Object reference parameters, long/double slots, nullable
  controls and captured reads. Both constrained positions reject nil and wrong
  types through the actual reflective constructor, without mocks.
- With JAVA_CMD set to the recorded EA3 executable, both commands exited 0:
  `clojure -M:test -m scry.cli --namespace value-type.impl.loader-test --namespace value-type.impl.field-storage-test`
  and the same command with `-J-Xint` before `-M:test`. Each passed 16 tests / 73
  assertions with preview and no exports; both Scry failure directories were empty.
  Scoped formatting passed after correction. Scoped clj-kondo had zero errors or
  warnings (one redundant-coercion info).
- Next: isolate capture from optional APIs and integrate identity output before
  enabling portable public selection. AOT and transfer matrices remain unverified
  for generated modes.

## 2026-10-27 — Baseline-safe capture loading

- Moved optional value preparation into `impl.value-backend/prepare`. Capture
  resolves it at installation and receives owner/companion transform functions.
  Exact-Class checks, AOT writes and error propagation remain in capture. No
  dependency or design-contract change. Installation still selects value output;
  identity backend integration is the next prerequisite, not delivered behavior.
- Fresh real Java 8 and preview-disabled EA3 processes load public and capture
  namespaces without loading optional backend namespaces. No mocks are used.
- With JAVA_CMD set to the recorded Java 8 path, `clojure -M:fallback-test
  --namespace value-type.capture-loading-test` exited 0: 1 test / 4 assertions.
  With JAVA_CMD set to the recorded EA3 path, `clojure -M:test -m scry.cli
  --namespace value-type.impl.loader-test --namespace value-type.impl.field-storage-test`
  exited 0: 16 tests / 73 assertions, preview enabled without exports. Both
  Scry failure directories were empty. Scoped formatting passed after correction;
  scoped clj-kondo reported zero errors and warnings. Full matrices remain open.

## 2026-10-27 — Identity backend integration

- Added explicit `:identity` capture selection and a bundled-ASM backend. It retains
  frames and stack sizes, inserts branch-free null checks/casts before constructor
  writes, and rewrites owner/companion references. Native modes, non-final/static
  selected storage and writes outside owner constructors fail visibly. No dependency
  or design-contract change. Public selection remains native-only.
- Real compiler fixtures verify final String storage, Object constructor parameters,
  mixed long/double slots, nullable controls, captured reads and factory/direct/
  reflective failures. General missing-field/reference/constructor shape validation,
  production identity AOT and public receiving selection remain next.
- With JAVA_CMD set to the previously recorded Java 8 executable,
  `clojure -M:fallback-test --namespace value-type.identity-backend-test` exited 0:
  1 test / 13 assertions. EA3 without preview ran that namespace plus
  `--namespace value-type.capture-loading-test`: exit 0, 2 tests / 17 assertions.
  EA3 with preview ran `clojure -M:test -m scry.cli --namespace
  value-type.impl.loader-test`: exit 0, 15 tests / 67 assertions. An initial run
  found a private capture-loader test using the old arity; updated its explicit
  representation and reran successfully. Successful Scry runs left no failure files.
- Scoped formatting passed; scoped clj-kondo reported zero errors/warnings (two
  redundant-coercion infos). No mocks or optional APIs were added to identity output.


## 2026-10-27 — Identity storage and reference shape checks

- Identity transformation now rejects missing selected fields and references with
  non-Object descriptors or static opcodes. Both owner and companion transforms
  enforce the reference contract. Tests feed real ASM class bytes to the backend;
  no mocks, dependencies or design-contract changes. Constructor coverage validation
  remains the next backend step; this pass does not prove all compiler shapes.
- With JAVA_CMD set to the recorded Java 8 executable, `clojure -M:fallback-test
  --namespace value-type.identity-backend-test` exited 0: 2 tests / 20 assertions.
  EA3 without preview ran `clojure -J-Xmx256m -M:fallback-test --namespace
  value-type.identity-backend-test`: exit 0, 2 tests / 20 assertions, with an empty
  Scry failure directory. A preceding EA3 run also selecting capture-loading-test
  exited 137; that combined run is not passing evidence or a human blocker.
- Scoped `bb clj-fmt check` passed. Scoped clj-kondo reported zero errors and
  warnings (one existing redundant-coercion info). Public mode selection and the
  remaining runtime/deployment matrix are unchanged and incomplete.

## 2026-10-27 — Identity constructor coverage

- Identity owners now require a constructor and complete, single selected-field
  writes before each normal return. Branches, switches and exception handlers in
  constrained constructors fail visibly. This deliberately supports the compiler's
  straight-line shape rather than introducing control-flow analysis. Companion
  methods remain free to branch. No dependency or design-contract change.
- Real ASM bytes test absent constructors, absent/repeated writes, a branch and an
  additional unchecked constructor. Existing real compiler construction/read tests
  still pass. Public and receiving mode selection are the next integration work;
  this check does not establish the remaining deployment or public contract matrix.
- With JAVA_CMD set in turn to the recorded Java 8 and EA3 executables,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-backend-test`
  exited 0 on both, without preview or exports: 3 tests / 26 assertions each.
  Scry failure files were absent. Scoped formatting passed; clj-kondo reported
  zero errors/warnings and one existing redundant-coercion info. No mocks used.


## 2026-10-27 — Identity method and branch coverage

- Extended the real compiler fixture with a protocol, Object override and captured
  branch merges between concrete and nullable fields. Java 8 and preview-disabled
  EA3 verify and execute all paths with retained compiler stack maps. No mocks,
  production changes, dependencies or design-contract changes.
- With JAVA_CMD set in turn to the recorded Java 8 and EA3 executables,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-backend-test`
  exited 0 on both: 4 tests / 34 assertions each, without preview or exports.
  Both Scry failure directories were empty. Scoped formatting passed; clj-kondo
  reported zero errors/warnings and one existing redundant-coercion info.
- Next: public and receiving mode selection remain pending. These tests exercise
  explicit identity capture, not portable public declarations or value-backend
  branch-merge resolution. The remaining deployment and policy matrices stay open.

## 2026-10-27 — Mixed value companion branch coverage

- Extended the real mixed-mode compiler fixture with a multi-arity captured
  function and a nested closure. A branch merges generated String storage with
  nullable Object storage; the nested companion also reads native storage.
  Both branches execute correctly. No mocks, dependencies, production changes
  or design-contract changes. Public selection and general hierarchy cases remain
  open; this evidence is only for explicit capture installation.
- With JAVA_CMD set to the recorded EA3 executable, `clojure -J-Xmx256m
  -M:test -m scry.cli --namespace value-type.impl.loader-test` and the same
  command with `-J-Xint` exited 0: each passed 15 tests / 69 assertions, with
  preview enabled and no exports. Both Scry failure directories were empty.
  Scoped `bb clj-fmt check` passed; clj-kondo reported zero errors/warnings
  and one existing redundant-coercion info.
- Next slice: connect public/receiving mode selection or extend construction
  coverage. Do not treat these backend tests as public portability evidence.

## 2026-10-27 — Mixed value construction entry points

- Added real direct Clojure constructor and generated factory calls with runtime
  arguments. Both native and generated positions reject nil and wrong types;
  valid calls retain long/double slots and nullable nested data. No mocks,
  production changes, dependencies or design-contract changes.
- With JAVA_CMD set to the recorded EA3 executable, `clojure -J-Xmx256m
  -M:test -m scry.cli --namespace value-type.impl.loader-test` and the same
  command with `-J-Xint` exited 0: each passed 16 tests / 79 assertions,
  with preview enabled and no exports. Both Scry failure directories were empty.
  Scoped `bb clj-fmt check` passed; clj-kondo reported zero errors/warnings
  and two redundant-coercion infos.
- Next: these are explicit-capture backend tests, not public portability evidence.
  Public/receiving selection, shared public construction cases and separate Java
  consumers remain open. Existing reflective tests cover permitted wrapping;
  direct/factory failures propagate without InvocationTargetException.

## 2026-10-27 — Multiple identity constraints

- Added an explicit-capture identity fixture with String and Date constraints,
  separated by primitive slots. Direct, factory and reflective construction checks
  each constrained position for nil and wrong types. Valid calls preserve final
  concrete storage, order, long/double slots and a nullable control. No mocks,
  production changes, dependencies or design-contract changes.
- With JAVA_CMD set to the recorded Java 8 executable, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.identity-backend-test` exited 0:
  5 tests / 53 assertions. EA3 without preview ran the same command with
  `-J-Xint` and also passed 5 tests / 53 assertions. Both Scry failure directories
  were empty. Scoped formatting passed; clj-kondo reported zero errors/warnings
  and two redundant-coercion infos.
- Next: public/receiving mode selection remains pending. These tests prove multiple
  generated constraints only through explicit capture, not public portability or
  separate Java consumers. The design remains unchanged.

## 2026-10-27 — Production identity source-free AOT

- Added a real Java 8 producer using production identity capture. Its source is
  deleted and excluded before fresh Java 8 and EA3 preview-off/on consumers run.
  Saved owner and companion code retains final String storage, Object constructor
  parameters, mixed slots, nullable controls and captured reads. Direct, factory
  and reflective calls retain nil/type rejection. Consumer forms preserve metadata
  so direct field reads compile with their declared hints.
- No mocks, dependencies, production changes or design-contract changes. This is
  explicit-capture evidence only: public receiving policy, separate Java consumers
  and value-output deployment remain open. Next slices must not infer public
  portability or policy timing from this test.
- With JAVA_CMD set to the recorded Java 8 executable, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.identity-aot-test` exited 0: 1 test /
  12 assertions. Children use the recorded Java 8 and EA3 paths, without exports.
  Scry failure files were absent. Scoped `bb clj-fmt check` and clj-kondo passed
  with zero lint errors or warnings.

## 2026-10-27 — Separate Java identity consumer

- Java 8 javac compiles direct calls against saved production identity output after
  producer source deletion. Java 8 and EA3 preview off/on execute valid construction,
  nil/type rejection, concrete storage, mixed slots and captured companion reads.
  Object constructor parameters remain callable with wrong types from Java.
- No production, dependency or design changes. This is explicit-capture evidence;
  public selection and policy timing remain unverified. Next: connect public and
  receiving selection or add generated value-output Java consumers.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-aot-test`
  exited 0: 2 tests / 25 assertions; no Scry failure files. Children use configured
  Java 8 and EA3 executables with no exports. Scoped `bb clj-fmt check` and
  clj-kondo passed with zero lint errors or warnings. An initial EOF syntax error
  was repaired with clj-paren-repair before verification.

## 2026-10-27 — Separate Java generated-value consumer

- Added an EA3 producer with explicit generated-only value capture and a separate
  Java compilation after producer source deletion. The shared Java constructor
  fixture checks valid/nil/wrong-type inputs, concrete String storage, mixed slots,
  nullable data and companion reads. Consumers confirm value representation and
  no native field annotations in normal and interpreter execution, without exports.
- No production, dependency or design-contract changes. This proves saved generated
  value output, not public selection, receiving policy or native/mixed Java cases.
  The Java fixture name identifies its original identity test, but its checks are
  representation-independent and now run against both outputs.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.generated-value-aot-test`
  exited 0: 1 test / 10 assertions; Scry failure directory empty. Children use
  configured EA3 Java and its compiler with `--enable-preview --release 27` for
  compilation, and `--enable-preview` with/without `-Xint` for execution. Scoped
  `bb clj-fmt check` and clj-kondo passed with zero errors or warnings.

## 2026-10-27 — Separate Clojure generated-value consumers

- Extended the saved generated-value fixture with direct, factory and reflective
  Clojure construction after producer source deletion. Each entry rejects nil and
  wrong types; reflection unwraps InvocationTargetException. Final String storage,
  absent native annotations, mixed slots, nullable controls and companion reads pass.
- No production, dependency or design-contract changes. Explicit capture still
  bypasses public selection; receiving policy and native/mixed deployment remain
  open. Consumer forms preserve metadata for direct field reads.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.generated-value-aot-test`
  exited 0: 2 tests / 18 assertions; `.scry-results/` contained no failure files.
  EA3 children use preview without exports, in normal and interpreter execution.
  Scoped formatting passed after correction; clj-kondo reported zero errors and
  warnings. No mocks used.

## 2026-10-27 — Incompatible generated-value receivers

- Saved generated-only value output rejects Java 8 and preview-disabled EA3 with
  UnsupportedClassVersionError on two successive class loads under each fallback
  policy. Producer source is deleted and absent from the consumer classpath.
  No mocks, production changes, dependencies or design-contract changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.generated-value-aot-test`
  exited 0: 3 tests / 38 assertions. Scry failure directory was empty. Scoped
  `bb clj-fmt check` and clj-kondo passed with zero errors or warnings.
  Children use the previously recorded Java 8 and EA3 paths, without exports.
- Next: this checks direct class loading of explicit-capture artifacts only.
  It does not prove public receiving guards or native capability independence.
  Public selection and receiving-policy integration remain incomplete.

## 2026-10-27 — Identity attribute preservation

- Real ASM bytes now verify that identity transformation preserves runtime class,
  field and constructor annotations and declared constructor exceptions. The JVM
  loads the result; concrete storage and constructor nil rejection still pass.
  No mocks, production changes, dependencies or design-contract changes.
- With JAVA_CMD set in turn to the recorded Java 8 and EA3 executables,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-backend-test`
  passed 6 tests / 60 assertions on each, without preview or exports. Scry failure
  directories were empty. Scoped formatting passed; clj-kondo reported zero errors
  and warnings after renaming a proxy argument (two existing coercion infos).
- Next: value-backend attribute coverage and public/receiving selection remain
  open. This test uses explicit backend transformation, not public declarations.

## 2026-10-27 — Generated value storage annotations

- Extended real classfile storage tests to both enforcement modes. Generated mode
  retains visible/invisible annotation values and flags without NullRestricted;
  nullable storage is unchanged. No mocks, production changes, dependencies or
  design-contract changes. This is bytecode inspection, not whole-class loading.
- With JAVA_CMD set to the recorded EA3 executable, `clojure -J-Xmx256m
  -M:test -m scry.cli --namespace value-type.impl.field-storage-test` exited 0:
  1 test / 12 assertions, preview enabled without exports. Scry failure directory
  was empty. Scoped formatting and clj-kondo passed with zero errors or warnings.
- Next: whole-class value attribute checks and public/receiving selection remain
  open. Do not count this storage check as public portability evidence.

## 2026-10-27 — Native probe independence

- Added a real probe-bytecode test and loaded the same bytes on EA3. The holder
  calls only Object initialization, accepts valid native storage and rejects nil.
  No mocks, production changes, dependencies or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.impl.field-capability-test`
  exited 0: 1 test / 3 assertions, preview enabled without exports. An initial
  classfile accessor error was corrected before the passing run.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var value-type.field-capability-test/capability-matrix-test`
  exited 0: 1 test / 6 assertions. Fresh Java 8 and EA3 preview-off/on children
  confirm lazy detection and cached capability results. Both Scry failure directories
  were empty. Scoped formatting and clj-kondo passed with zero errors or warnings.
- Next: retain this native-only probe when public selection gains generated modes.
  This does not complete public selection or receiving-policy integration.

## 2026-10-27 — Portable specification validation

- Added no-mock specification tests for invalid tags, metadata precedence and removed
  metadata, with both explicit capability inputs. Disabled and plain unresolved hints
  are ignored; real fallen-back Classes retain exact identity, metadata and descriptors.
  No production, dependency or design-contract changes. Public integration remains next.
- With JAVA_CMD set in turn to the recorded Java 8 and EA3 executables,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.fields-test`
  exited 0 on both without preview: 17 tests / 153 assertions each. Scry failure
  directories were empty. Scoped formatting and clj-kondo passed with zero errors
  and warnings. Explicit capability inputs test selection, not new JVM support.

## 2026-10-27 — Identity AOT policy matrix

- Extended source-free Clojure identity consumers to silent, warn and error on
  Java 8 and EA3 preview off/on. Equal inputs still produce unequal identity
  objects; saved fields have no native annotations. Constructor checks and
  companion reads remain fixed. No mocks, dependencies or production changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-aot-test`
  exited 0: 2 tests / 43 assertions; no Scry failure files. Scoped formatting
  and clj-kondo passed with zero errors or warnings. Children use the recorded
  Java 8 and EA3 toolchains without exports.
- These explicit-capture artifacts contain no public representation-policy guard.
  Strict-policy success here is not evidence that public guarded definitions may
  bypass strict policy. Public selection and receiving timing remain next; the
  design contract is unchanged.

## 2026-10-27 — Identity invalid storage and write coverage

- Real ASM bytes verify structured rejection of non-final, static and pretyped
  selected storage, plus writes from owner methods and companions. No mocks,
  production changes, dependencies or design-contract changes. Public selection
  and public defect propagation remain open; backend rejection is not that evidence.
- With JAVA_CMD set to the recorded Java 8 and EA3 executables in turn,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-backend-test`
  exited 0 on both without preview or exports: 7 tests / 72 assertions each.
  Both Scry failure directories were empty. Scoped formatting passed; clj-kondo
  reported zero errors/warnings and two existing coercion infos. An initial
  unmatched delimiter was repaired with clj-paren-repair before passing verification.
- Next: connect public and receiving selection. This slice closes specific backend
  rejection gaps only; the design and remaining deployment matrix are unchanged.

## 2026-10-27 — Generated-value AOT policy matrix

- Extended separate Java and Clojure source-free consumers to silent, warn and
  error in normal and interpreter execution. Generated checks remain fixed and
  stderr remains empty. No production, dependency or design-contract changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.generated-value-aot-test`
  exited 0: 3 tests / 62 assertions. Scry failure files were absent. Scoped
  formatting and clj-kondo passed with zero errors or warnings. An initial
  unresolved test binding was corrected before the passing run.
- Next: explicit capture bypasses public guards. These results do not establish
  public selection, receiving-policy timing or native-capability absence. The
  design remains unchanged; public integration is still required.

## 2026-10-27 — Nested identity constraint

- Added a real explicit-capture identity owner whose constrained child is another
  transformed identity class. Nullable child data remains valid. Factory, direct
  and reflective constructors retain exact storage Class, captured reads and
  nil/wrong-type rejection. No mocks, dependencies or production changes.
- With JAVA_CMD set to the recorded Java 8 executable, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.identity-backend-test` exited 0:
  8 tests / 95 assertions. The recorded EA3 executable passed the same command
  with `-J-Xint`, without preview or exports. Both failure directories were empty.
  Scoped formatting and clj-kondo passed with zero errors/warnings (two existing
  coercion infos), after qualifying the fixture Class for lint.
- Next: this is backend evidence, not public nested declaration support. Public
  selection and receiving policy remain incomplete. The design contract is unchanged.

## 2026-10-27 — Identity capture Class mismatch

- Real sibling loaders define distinct Classes with the same binary name. Identity
  capture rejects the stale specification with `:field-class-mismatch` before
  reading owner bytes. No mocks, production changes, dependencies or design changes.
- With JAVA_CMD set in turn to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.identity-loader-test` exited 0 on both:
  1 test / 3 assertions each, without preview or exports. Both Scry failure
  directories were empty. Scoped formatting and clj-kondo passed with zero errors
  or warnings. An initial unmatched delimiter was repaired with clj-paren-repair.
- Next: public mode selection and receiving integration remain open. This checks
  the identity capture boundary only, not transferred public declarations or
  successful redefinition. The design contract is unchanged.

## 2026-10-27 — Fixed receiving specification modes

- Added `receiving-specifications`: resolve receiver-local concrete Classes, then
  apply an ordered vector of fixed modes. Invalid mode/count transport and native
  selections for identity Classes fail rather than reselecting enforcement. The
  caller still owns representation/capability guards and capture installation.
  No dependencies or design-contract changes. Public integration remains open.
- Real namespace imports verify exact local Class, descriptor and metadata, disabled
  unresolved tags and invalid transport. No mocks. This is a receiving prerequisite,
  not transferred-public-expansion evidence or native receiver capability evidence.
- With JAVA_CMD set in turn to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.fields-test` exited 0 on both without
  preview or exports: 18 tests / 169 assertions each. Both Scry failure directories
  were empty. Scoped formatting and clj-kondo passed with zero errors/warnings.
- Next: carry the selected mode vector through the receiving macro, check fixed
  representation and required native capability, and install the matching backend.

## 2026-10-27 — Fixed identity receiving boundary

- Added `identity-field-deftype` as an internal receiving macro. It resolves local
  Classes with fixed modes, permits only generated identity enforcement, and fails
  if capture cannot install. Fixed fallback policy runs during analysis and emitted
  evaluation. This prevents unchecked output or representation upgrades. No dependency
  or design-contract change; public selection still uses the existing native path.
- Real fresh namespaces import Date and compile transported boundary forms through
  eval and function analysis/invocation. Final concrete storage, nullable controls
  and reflective nil/type rejection pass. No mocks. Public transferred expansions,
  warning/strict timing and AOT policy integration remain unverified and are next.
- With JAVA_CMD set in turn to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, `clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.identity-receiving-test` exited 0 on both
  without preview or exports: 1 test / 12 assertions each. Both Scry failure
  directories were empty. Scoped `bb clj-fmt check` and clj-kondo passed with zero
  errors or warnings. The design required no change.

## 2026-10-27 — Fixed identity receiving policy

- Added fresh-process policy tests for the internal identity receiving boundary.
  Java 8 and EA3 preview off/on retain String storage through function analysis
  and repeated invocation. Warn emits once across two definitions; strict rejects
  both analysis attempts with CompilerException wrapping Error, including on the
  capable JVM. No mocks, production changes, dependencies or design changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-receiving-test`
  exited 0: 2 tests / 39 assertions. Children use the configured Java 8 and EA3
  toolchains without exports. No Scry failure files. Scoped formatting and
  clj-kondo passed with zero errors or warnings.
- Next: connect public selection to receiving boundaries. This test does not prove
  public portable declarations, concurrent warning scope or source-free AOT timing.
  The design contract remains unchanged.

## 2026-10-27 — Fixed value receiving boundary

- Added internal `value-field-deftype`: resolves local Classes with fixed modes,
  requires value capability, conditionally requires native capability, and requires
  capture installation. Unsupported receivers report `:value-capability-unavailable`
  rather than selecting identity. No dependency or design-contract changes.
- Real Date fields retain concrete final storage and no native annotations through
  eval and function analysis; constructors reject nil and wrong types. Java 8 and
  preview-disabled EA3 reject transferred fixed value forms under all policies,
  without warnings. No mocks. Native/mixed receiving and public dispatch remain next.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` passed 1 test / 16 assertions each, with
  preview and no exports. With JAVA_CMD set to the recorded Java 8 executable,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-receiving-test`
  passed 3 tests / 57 assertions. All runs exited 0; failure directories were empty.
  Scoped formatting and clj-kondo passed with zero errors or warnings.

## 2026-10-27 — Native and mixed value receiving

- Fixed native/native and native/generated mode vectors now have real receiving
  tests through eval and function analysis. Both fields use the same receiver-local
  value Class: generated mode remains generated even when native enforcement is
  eligible. Concrete final storage, annotation selection, captured companion reads,
  nullable child data and nil/type rejection at each position pass. No mocks,
  dependencies, production changes or design-contract changes.
- On EA3, with JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 2 tests, 64 assertions.
  Preview was enabled, with no exports. Scry failure directories were empty.
  Scoped formatting (after correction) and clj-kondo passed.
- Next: connect public mode selection to both fixed receiving boundaries. These
  internal-boundary tests do not prove public expansion transfer or AOT policy.

## 2026-10-27 — Receiving source loading

- Extended internal receiving tests with `load-string` of forms printed with
  metadata. Local Date/Child resolution, final storage and nil/type rejection pass
  for identity-generated and value-generated/native/mixed fields. Mixed companions
  retain captured reads. No mocks, production changes, dependencies or design changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var value-type.identity-receiving-test/receiving-identity-fields-test`
  exited 0: 1 test / 18 assertions. The same command with
  JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java also passed without preview.
- With the EA3 JAVA_CMD, `clojure -J-Xmx256m -M:test -m scry.cli --namespace
  value-type.value-receiving-test` and the same command with `-J-Xint` each exited 0:
  2 tests / 96 assertions. Preview enabled; no exports. Each Scry failure directory
  was empty. Scoped formatting and clj-kondo passed with zero errors or warnings.
- Next: connect public selection to the fixed receiving boundaries. These tests
  transport internal forms, not public macroexpand or macroexpand-1 results.
  Public transfer, receiving policy and source-free public AOT remain open.

## 2026-10-27 — Generated-value receiving policies

- Fresh EA3 receivers now verify String fields under silent, warn and error: value
  representation, concrete storage, absent native annotations, valid construction
  and nil/wrong-type rejection, with empty stderr. No mocks, production changes,
  dependencies or design-contract changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-receiving-test`
  exited 0: 4 tests / 72 assertions; no Scry failure files. Children use the
  configured Java 8 and EA3 paths, without exports. Scoped formatting and clj-kondo
  passed with zero errors or warnings. A child-form delimiter error was repaired
  with clj-paren-repair before the passing run.
- Next: connect public selection to receiving boundaries. This verifies native
  ineligibility of String, not controlled native capability absence or public
  policy timing. The existing design needs no change.

## 2026-10-27 — Receiver import replacement

- Real sibling loaders define same-name Classes. Replacing the receiver import
  changes the exact Class in fixed generated specifications without changing the
  descriptor or enforcement. No mocks, production changes, dependencies or design
  changes. This checks resolution only, not owner redefinition or public transfer.
- With JAVA_CMD set to the recorded Java 8 and EA3 executables in turn,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.identity-loader-test`
  exited 0 on both without preview or exports: 2 tests / 10 assertions each.
  Both Scry failure directories were empty. Scoped formatting passed after automatic
  correction; clj-kondo reported zero errors and warnings.
- Next: public mode selection and successful receiving owner compilation remain
  open. Do not treat this specification test as completion of acceptance 6.

## 2026-10-27 — Receiving owner redefinition

- Real deftype child redefinition followed by fixed identity owner reanalysis retains
  separate exact storage Classes. Both owners accept their own child and reject nil
  and the same-name child from the other loader. No mocks, production changes,
  dependencies or design-contract changes. Public transfer remains unverified.
- With JAVA_CMD set in turn to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, `clojure -J-Xmx256m
  -M:fallback-test --var value-type.identity-receiving-test/receiving-redefined-child-test`
  exited 0 on both without preview or exports: 1 test / 10 assertions each.
  Both Scry failure directories were empty. Scoped formatting and clj-kondo passed
  with zero errors or warnings.
- Next: connect public mode selection and test public expansion transfer. This is
  successful internal receiving compilation evidence, not completion of acceptance 6.

## 2026-10-27 — Value receiving owner redefinition

- Extended exact-Class redefinition coverage to fixed value receiving in native
  and generated modes. Each old/new owner retains its own child Class and rejects
  nil and the other definition's child. Real classes and constructors only; no
  mocks, dependency changes or design changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 3 tests / 116 assertions.
  EA3 preview enabled, no exports; Scry failure directories empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed with no warnings or errors.
- Next: connect public mode selection. These internal receiving checks do not
  establish public transfer, discarded expansion policy or source-free AOT timing.

## 2026-10-27 — Identity receiving companion reads

- Extended the real receiving fixture with IDeref returning a multi-arity captured
  function and nested closure. Concrete Date and nullable Object branch reads retain
  their values through eval, function analysis and metadata-preserving source loading.
  No mocks, production changes, dependencies or design-contract changes.
- With JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var value-type.identity-receiving-test/receiving-identity-fields-test`
  exited 0: 1 test / 27 assertions. With
  JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java, the same command
  with `-J-Xint` also passed. Neither run enabled preview or exports; both Scry
  failure directories were empty. Scoped formatting passed after correction;
  scoped clj-kondo reported zero errors and warnings.
- Next: connect public selection to receiving boundaries. These internal forms do
  not establish public expansion transfer or source-free public AOT policy timing.

## 2026-10-27 — Identity receiving construction entries

- Extended the fixed identity receiving fixture to direct Clojure constructors and
  factories, alongside reflection, through eval, function analysis and source loading.
  Valid values retain identity; nil/type failures propagate directly except for the
  permitted reflection wrapper. No mocks, production changes, dependencies or design
  changes. Public mode selection remains the next integration step.
- With JAVA_CMD set to the recorded Java 8 executable, `clojure -J-Xmx256m
  -M:fallback-test --var value-type.identity-receiving-test/receiving-identity-fields-test`
  exited 0: 1 test / 48 assertions. The recorded EA3 executable passed the same
  command with `-J-Xint`, without preview or exports. Both Scry failure directories
  were empty. Scoped formatting and clj-kondo passed with zero errors or warnings.
  A rejected replacement form was repaired with clj-paren-repair before use.

## 2026-10-27 — Native/mixed receiving construction entries

- Extended fixed native/mixed value receiving tests to direct constructors and
  factories alongside reflection, through eval, function analysis and source loading.
  Each constrained position rejects nil and wrong types; valid calls retain nullable
  child data and companion reads. Only reflection unwraps InvocationTargetException.
  No mocks, production changes, dependencies or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 3 tests / 182 assertions.
  Preview enabled, no exports; both Scry failure directories empty. Scoped
  `bb clj-fmt check` and clj-kondo passed with zero errors or warnings.
- Next: public selection and public expansion transfer remain open. This extends
  internal receiving evidence only; it does not establish public portability.

## 2026-10-27 — Generated-only receiving construction entries

- Extended fixed generated-only value receiving to direct constructors and factories
  alongside reflection through eval, function analysis and source loading. A concrete
  Date field accepts valid input and rejects nil/wrong types; only reflection unwraps
  InvocationTargetException. No mocks, production changes, dependencies or design
  changes. Public selection remains open; this is internal-boundary evidence only.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 3 tests / 203 assertions.
  EA3 preview enabled, no exports; both Scry failure directories were empty.
  Scoped `bb clj-fmt check` and `clj-kondo --lint` passed without errors or warnings.
- Next: connect public mode selection and verify public expansion transfer. These
  tests do not establish public portability or source-free public AOT policy timing.

## 2026-10-27 — Public portable selection

- Public declarations now validate concrete tags before capability selection and carry
  fixed mode vectors to both receiving boundaries. Identity output always uses generated
  checks; value output selects native only when eligible and available. Unconstrained
  dispatch is unchanged. No mocks, dependencies or design-contract changes.
- Public policy tests now expect portable Long fields, warn-once identity selection and
  repeated strict rejection. String value fields pass all policies without warnings.
  Both public expansion entry points pass baseline eval, function analysis and source
  loading with direct/factory/reflective checks and companion reads. These same-namespace
  expansion tests do not replace cross-process transferred-public-form coverage.
- Java 8: `clojure -J-Xmx256m -M:fallback-test --namespace
  value-type.field-capability-test` passed 5 tests / 89 assertions; the same command
  selecting `value-type.identity-receiving-test` passed 5 tests / 208 assertions.
  Children use configured Java 8 and EA3 preview-off/on toolchains without exports.
- EA3 preview: `clojure -J-Xmx256m -M:test -m scry.cli --namespace
  value-type.field-eligibility-test --namespace value-type.restricted-fields-test`
  passed 8 tests / 117 assertions. EA3 without preview: `clojure -J-Xmx256m
  -J-Xint -M:fallback-test --var value-type.identity-receiving-test/receiving-identity-fields-test`
  passed 1 test / 144 assertions. JAVA_CMD used the previously recorded executable
  for each run. All exited 0; Scry failure files were absent. Scoped formatting
  and clj-kondo passed with no errors or warnings.
- Next: public value transfer, nested public cases and source-free guarded AOT remain
  open. Public docstring updated, but generated API and usage/embodiment still need
  synchronization. Full suites have not run; remaining native-only expectations need
  review. The design remains unchanged.

## 2026-10-27 — Public generated-value expansion

- Extended generated-value receiving tests to both public expansion entry points,
  retaining the internal control. Eval, function analysis and metadata-preserving
  source loading preserve final Date storage, nullable controls and direct/factory/
  reflective nil and wrong-type rejection. No mocks, production changes, dependencies
  or design changes. Expansion and analysis share a namespace; this is not evidence
  for cross-process transfer or native/mixed public expansion.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 3 tests / 293 assertions.
  EA3 preview enabled, no exports; both Scry failure directories were empty.
  Scoped `bb clj-fmt check` and `clj-kondo --lint` passed without errors or warnings.
- Next: transfer public expansions between isolated producers and receivers, then
  verify fixed modes and guarded AOT policy timing. Existing acceptance items stay
  open until that wider evidence is available.

## 2026-10-27 — Public native and mixed receiving expansions

- Extended the receiving fixture to public macroexpand and macroexpand-1. Public mixed
  declarations use Child/String tags to select native/generated enforcement without
  mocks; internal fixed-mode controls retain Child/Child. Wrong-type inputs now use
  Object so they are invalid for both tags. Constructors, exact final storage, native
  annotations, nullable child data and captured reads pass through eval, function
  analysis and metadata-preserving source loading. No production, dependency or
  design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:test -m scry.cli --namespace value-type.value-receiving-test`
  and the same command with `-J-Xint` each exited 0: 3 tests / 569 assertions.
  EA3 preview enabled, no exports. Scry failure files absent. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without errors or warnings.
- Next slice: producer and receiver still share a namespace and process. These tests
  do not prove cross-process public transfer, incompatible receivers or guarded AOT
  policy timing. Keep those acceptance items open.

## 2026-10-27 — Public expansion redefinition

- Both public expansion entry points now have same-name child redefinition coverage.
  Reusing the selected expansion resolves the new receiver-local Class; each old/new
  owner accepts its own child and rejects nil and the other child. Identity output
  uses generated checks; value output selects native checks. Internal generated-value
  controls remain. No mocks, production changes, dependencies or design changes.
- Java 8 ran `clojure -J-Xmx256m -M:fallback-test --var
  value-type.identity-receiving-test/receiving-redefined-child-test`: 1 test / 30
  assertions. Preview-disabled EA3 passed the same command with `-J-Xint`.
  EA3 ran `clojure -J-Xmx256m -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-redefined-child-test`, with and without
  `-J-Xint`: each passed 1 test / 40 assertions, preview enabled, no exports.
  JAVA_CMD used the recorded Java 8 and EA3 executable paths. All exits were 0;
  each Scry failure directory was empty. Scoped formatting and lint passed.
- Next: these are same-process public expansions. Cross-process transfer, sibling
  loader public cases and source-free guarded AOT still need evidence. Acceptance 6
  remains open; no design-contract change is needed.

## 2026-10-27 — Cross-process public value transfer

- Extended existing isolated public transfer fixtures to native Long and generated
  String fields. Both expansion entry points retain concrete storage, native
  annotation choice, factory checks and repeated receiver-local installation through
  eval, source loading and function analysis/invocation under all three policies.
  No mocks, dependencies, production changes or design changes.
- Replaced stale native-only rejection expectations. Structured Scry failures showed
  that incompatible receivers reject native Long during fixed tag validation
  (`:identity-type`), before the representation guard. Generated String reaches
  `:value-capability-unavailable`. Both reject repeatedly without warnings or an
  installed owner on Java 8 and preview-disabled EA3.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 2 tests / 172 assertions; `.scry-results` was empty. Child producers and
  supported receivers use EA3 preview; incompatible children use Java 8 and EA3
  without preview. Scoped clj-kondo and `bb clj-fmt check` passed.
- Next: identity and mixed cross-process transfer, direct/reflective transfer
  construction, interpreter transfer and source-free guarded AOT remain separate
  evidence gaps. This slice does not close acceptance 6 or the full runtime matrix.

## 2026-10-27 — Public transfer construction entries

- Cross-process native Long and generated String transfer now checks direct Clojure
  and reflective constructors alongside factories, after each receiver installation.
  Valid inputs retain the installed Class and field value; nil and wrong types fail.
  Only reflection unwraps InvocationTargetException. No mocks, production changes,
  dependencies or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 2 tests / 172 assertions. `.scry-results` contained no failure files.
  Child supported receivers use EA3 preview under all policies; incompatible receivers
  use Java 8 and preview-disabled EA3. Scoped formatting and clj-kondo passed.
- Next: identity/mixed transfer, interpreter transfer and source-free public guarded
  AOT remain open. This slice extends construction evidence, not the full acceptance 6.

## 2026-10-27 — Interpreter public value transfer

- Extended the isolated supported-transfer matrix with receiver `-Xint`, retaining
  preview. Native Long and generated String expansions pass both public expansion
  entry points, eval/source/function analysis, every policy and all three construction
  entries. Producers remain in normal execution. No mocks, production changes,
  dependencies or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 2 tests / 280 assertions; `.scry-results` contained no failure files.
  Supported children use EA3 preview, without exports. Incompatible receiver checks
  still use Java 8 and preview-disabled EA3. Scoped `bb clj-fmt check` and
  `clj-kondo --lint` passed with zero errors or warnings.
- Next: identity/mixed transfer and source-free public guarded AOT remain open.
  This closes the native/generated-value interpreter transfer gap only, not the full
  runtime matrix or acceptance 6.

## 2026-10-27 — Public identity transfer

- Java 8-produced public expansions now pass fresh Java 8 and EA3 preview-off/on
  receivers under silent and warn. Both expansion entry points retain generated
  String storage and direct/factory/reflective rejection through eval, source loading
  and function analysis/invocation. Repeated installations share one warning. Shared
  consumer checks now distinguish identity from value equality. No mocks, production,
  dependency or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 3 tests / 389 assertions; Scry failure directory empty. Scoped formatting
  and clj-kondo passed with zero errors or warnings. An initial warning assertion
  used an incorrect prefix; structured results identified it before correction.
- Next: strict and interpreter identity transfer, mixed transfer and source-free
  public guarded AOT remain open. No internal exports were used. These results do
  not complete acceptance 6 or the full runtime matrix.

## 2026-10-27 — Interpreter public identity transfer

- Extended the existing isolated identity transfer matrix with receiver `-Xint`.
  Java 8-produced expansions retain generated String checks, identity equality and
  warn-once behavior on Java 8 and EA3 preview off/on under silent and warn. Both
  expansion entry points, all receiving paths and direct/factory/reflective
  constructors pass. No mocks, production changes, dependencies or design changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 3 tests / 497 assertions. Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed with zero errors or warnings.
  Child runtimes use their configured preview flags and no internal exports.
- Next: strict identity rejection, mixed transfer and source-free public guarded
  AOT remain open. This closes only the identity interpreter transfer gap; it does
  not complete acceptance 6 or the full runtime matrix.

## 2026-10-27 — Strict public identity transfer

- Java 8-produced public identity expansions now have strict-policy rejection
  evidence on Java 8 and EA3 preview off/on, with normal and interpreter execution.
  Both expansion entry points reject twice through eval, source loading and function
  analysis. The receiver installs no owner and emits no warning. Failure observations
  now include the root exception class to distinguish policy Error from unrelated
  failures. No mocks, production changes, dependencies or design changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 4 tests / 528 assertions; `.scry-results` had no failure files.
  Children use configured Java 8 and EA3 preview off/on without internal exports.
  Scoped `bb clj-fmt check` and `clj-kondo --lint` passed with no errors or warnings.
- Next: mixed public transfer and source-free public guarded AOT remain open.
  Strict rejection here is receiving analysis, not AOT guard timing. Acceptance 6
  and the full runtime matrix remain incomplete.

## 2026-10-27 — Interpreter incompatible value transfer

- Extended incompatible public transfer to interpreter execution on Java 8 and
  preview-disabled EA3. Both expansion entry points reject native and generated
  fixed value modes repeatedly through eval, source loading and function analysis
  under every policy, without an owner or warning. No mocks, production changes,
  dependencies or design changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var
  value-type.field-transfer-test/unsupported-public-transfer-test` exited 0:
  1 test / 122 assertions. Scry failure directory empty. Scoped `bb clj-fmt check`
  and `clj-kondo --lint` passed without errors or warnings. Child receivers use
  configured Java 8 and EA3 without preview or internal exports.
- Next: mixed public transfer and source-free guarded AOT remain open. This adds
  incompatible-receiver interpreter evidence only, not full acceptance 6 or 8.

## 2026-10-27 — Mixed public transfer

- Added isolated mixed Long/String public transfer using real native eligibility.
  Both expansion entry points retain native/generated annotation choices, concrete
  storage and direct/factory/reflective nil/type rejection at each position through
  eval, source loading and function analysis/invocation. Only reflection unwraps
  InvocationTargetException. No mocks, production changes, dependencies or design
  changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var
  value-type.field-transfer-test/mixed-public-transfer-test` exited 0: 1 test /
  109 assertions. Scry failure directory empty. EA3 child receivers use preview,
  every policy and normal/interpreter execution without exports. Scoped clj-kondo
  passed without errors or warnings; scoped formatting passed after correction.
- Next: mixed repeated installation and incompatible receivers remain untested by
  this fixture. Source-free public guarded AOT and full regression verification
  remain open. This does not complete acceptance 6 or 8.

## 2026-10-27 — Incompatible mixed public transfer

- Mixed Long/String public expansions reject twice on Java 8 and preview-disabled
  EA3 through eval, source loading and function analysis. Both expansion entry points,
  all policies and normal/interpreter execution retain fixed selection without an
  owner or warning. Long fails native eligibility before the representation guard;
  failure order is not contractual. No mocks, production or dependency changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var
  value-type.field-transfer-test/unsupported-mixed-public-transfer-test` exited 0:
  1 test / 61 assertions. Scry failure directory empty. Scoped `bb clj-fmt check`
  and `clj-kondo --lint` passed without errors or warnings. Child receivers use
  configured Java 8 and EA3 without preview or internal exports.
- Next: repeated supported mixed installation and source-free guarded AOT remain
  open. The design contract is unchanged; this does not complete acceptance 6 or 8.

## 2026-10-27 — Repeated supported mixed transfer

- Mixed public transfer now installs each expansion twice in the same receiver.
  Each installation returns a distinct Class and retains native Long/generated String
  storage and direct/factory/reflective checks at both positions. Both expansion
  entry points and all three receiving paths pass. No mocks, production changes,
  dependencies or design-contract changes.
- With JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java,
  `clojure -J-Xmx256m -M:fallback-test --var
  value-type.field-transfer-test/mixed-public-transfer-test` exited 0: 1 test /
  109 assertions. Scry failure directory empty. EA3 children use preview, every
  policy and normal/interpreter execution without exports. Scoped formatting and
  clj-kondo passed with zero errors or warnings.
- Next: source-free public guarded AOT and full regression verification remain
  open. Repeated installation does not complete acceptance 6 or 8; the design
  needs no change.

## 2026-10-27 — Public identity AOT, permitted receivers

- Added a public defvaluetype producer that reuses the identity AOT consumer contract.
  Java 8-produced String constraints retain final concrete storage, identity equality,
  mixed slots, captured reads and all three Clojure constructor entries after source
  deletion. Java 8 and EA3 preview off/on pass silent and warn; warn emits one fixed
  fallback warning. No production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.public-field-aot-test` exited 0: 1 test,
  22 assertions; `.scry-results` empty. Scoped `bb clj-fmt check` and `clj-kondo
  --lint` passed with no warnings or errors. Children use configured runtimes without
  internal exports. Tests use real compilation and fresh receivers, not mocks.
- Next: strict receiving guards, function-contained timing, repeated loading,
  interpreter execution and separate public Java consumers. Other enforcement modes
  and full regression verification remain open; this does not complete acceptance 7.

## 2026-10-27 — Interpreter public identity AOT

- Extended permitted source-free public identity AOT receivers with `-Xint` on Java 8
  and EA3 preview off/on. Concrete storage, identity equality, mixed slots, companion
  reads and direct/factory/reflective checks pass under silent and warn. Producers
  remain in normal execution. No mocks, production, dependency or design changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --namespace value-type.public-field-aot-test` exited 0: 1 test,
  40 assertions; `.scry-results` empty. Scoped `bb clj-fmt check` and clj-kondo passed
  with no warnings or errors. Children use configured runtimes without exports.
- Next: strict receiving guards, function-contained timing, repeated loading and
  separate public Java consumers remain open. Interpreter evidence does not establish
  those policy timing contracts or complete acceptance 7.

## 2026-10-27 — Public identity AOT Java consumer

- Reused the separately compiled Java constructor fixture with the public identity
  producer. Source-free Java 8 and EA3 preview off/on retain concrete String storage,
  mixed slots, nullable fields, companion reads and nil/wrong-type rejection under
  every policy in normal/interpreter execution. Direct class access does not evaluate
  namespace guards: strict policy permits this access and warn emits no warning.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/java-public-identity-consumer`
  exited 0: 1 test / 58 assertions. Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed with no warnings or errors.
  Real Java compilation and fresh JVM consumers use no mocks or internal exports.
- Next: strict guarded namespace loading, function-contained timing and repeated
  loading remain open. No production, dependency or design-contract changes.

## 2026-10-27 — Strict public identity AOT loading

- Source-free public identity output now rejects strict namespace loading on Java 8
  and EA3 preview off/on in normal/interpreter execution. The root failure is Error,
  the factory is not bound, and stderr is empty. Real compilation and fresh JVMs use
  no mocks or internal exports. No production, dependency or design changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/strict-public-identity-aot-test`
  exited 0: 1 test / 20 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without warnings or errors.
- Next: repeated namespace loading and function-contained guard timing remain open.
  This checks first guarded loading only; direct Java access remains separately
  permitted. Acceptance 7 and the full regression matrix remain incomplete.

## 2026-10-27 — Repeated strict public identity AOT loading

- Strict source-free namespace loading now retries with `:reload` in the same JVM.
  The first load fails with policy Error; the second remains rejected with an unbound
  factory. Failed JVM initialization is sticky: Java 8 reports NoClassDefFoundError;
  EA3's root cause is ExceptionInInitializerError. Initial structured failures exposed
  this wrapper difference; tests now check it without requiring policy reevaluation.
  No mocks, production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/strict-public-identity-aot-test`
  exited 0: 1 test / 32 assertions. Java 8 and EA3 preview off/on children pass normal
  and interpreter execution without exports or warnings. Scry failure directory empty;
  scoped formatting and clj-kondo passed without warnings or errors.
- Next: permitted repeated loading and function-contained policy timing remain open.
  A failed top-level initializer cannot establish function invocation retry behavior.

## 2026-10-27 — Permitted public identity AOT reload

- Source-free public identity consumers now run the shared constructor/read contract
  before and after namespace reload. Java 8 and EA3 preview off/on pass silent/warn
  in normal/interpreter execution; warn remains once. No mocks, production changes,
  dependencies or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/source-free-public-identity-test`
  exited 0: 1 test / 40 assertions. Scry failure directory empty; scoped formatting
  and clj-kondo passed with zero warnings/errors. Children use configured runtimes
  without exports. An initial delimiter error was repaired with clj-paren-repair;
  structured failures then identified premature alias resolution in the wrapper.
  Evaluating the metadata-preserved contract form defers analysis until require runs.
- Next: function-contained policy timing remains unverified. Reload coverage does not
  establish invocation-time guards or complete acceptance 7. The design is unchanged.

## 2026-10-27 — Function-contained public identity AOT

- Source-free public identity function definitions load without warnings under every
  policy. Repeated invocation retains concrete String storage and factory nil/type
  rejection under silent/warn; strict invocation repeatedly throws Error, including
  on capable receivers. Warn remains once. No mocks, production changes, dependencies
  or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/function-public-identity-aot-test`
  exited 0: 1 test / 56 assertions. Scry failure directory empty. Java 8 and EA3
  preview off/on children pass normal/interpreter execution without exports. Scoped
  clj-kondo passed without warnings/errors; formatting passed after correction.
- Next: public value-generated/native/mixed source-free AOT and full regression
  verification remain open. This verifies identity invocation timing, not completion
  of acceptance 7 or the full runtime matrix.

## 2026-10-27 — Public generated-value AOT

- Added a strict-policy EA3 public producer with source deleted before separate
  Clojure consumption. Shared generated-value checks retain final String storage,
  absent native annotations, mixed slots, companion reads and direct/factory/reflective
  nil/type rejection under all policies and normal/interpreter execution. No mocks,
  production changes, dependencies or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/source-free-public-generated-value-test`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without warnings or errors.
  EA3 children enable preview without internal exports.
- Next: separate public generated-value Java consumers, incompatible receivers and
  native/mixed AOT remain open. String proves native ineligibility, not controlled
  native capability absence. This does not complete acceptance 7 or 8.

## 2026-10-27 — Public generated-value Java consumer

- Reused the separate Java constructor fixture with the strict-policy public value
  producer. Source-free EA3 consumers retain concrete String storage, absent native
  annotations, mixed slots, nullable controls, companion reads and nil/type rejection
  under every policy in normal/interpreter execution. No mocks, production changes,
  dependencies or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/java-public-generated-value-consumer`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed with no warnings or errors.
  Java compilation uses `--enable-preview --release 27`; receivers use preview
  without internal exports.
- Next: incompatible public generated-value receivers, native/mixed AOT and full
  regression verification remain open. This does not complete acceptance 7 or 8.

## 2026-10-27 — Incompatible public generated-value AOT

- Public strict-policy EA3 output now rejects two successive direct class loads on
  Java 8 and preview-disabled EA3 under every policy in normal/interpreter execution.
  Producer source is deleted and absent from consumer classpaths. Both attempts report
  UnsupportedClassVersionError without warnings. Real compilation and fresh JVMs use
  no mocks or internal exports; no production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/incompatible-public-generated-value-consumer`
  exited 0: 1 test / 38 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without warnings or errors.
- Next: public native/mixed AOT and full regression verification remain open. Direct
  class loading checks fixed bytecode compatibility, not guarded namespace timing.
  Acceptance 7 and 8 remain incomplete.

## 2026-10-27 — Public generated-value AOT reload

- Extended the source-free public generated-value consumer to run its shared contract
  before and after namespace reload. EA3 retains concrete storage, constructor checks
  and companion reads under every policy in normal/interpreter execution, without
  warnings. Real compilation and fresh receivers use no mocks or internal exports.
  No production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/source-free-public-generated-value-test`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without warnings or errors.
- Next: native/mixed public AOT and generated-value function-contained timing remain
  open. Reload evidence does not complete acceptance 7 or the full runtime matrix.

## 2026-10-27 — Function-contained public generated-value AOT

- Added real public EA3 compilation followed by source deletion and fresh consumers.
  Namespace loading and repeated function invocation stay silent under all policies;
  value representation, String storage, absent native annotations and factory nil/type
  rejection remain fixed. No mocks, production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-field-aot-test/function-public-generated-value-aot-test`
  exited 0: 1 test / 20 assertions; Scry failure directory empty. EA3 children use
  preview with normal/interpreter execution and no exports. Scoped clj-kondo passed
  without warnings/errors; scoped formatting passed after automatic correction.
- Next: native/mixed public AOT and final regressions remain open. This checks factory
  construction within function-contained definitions; broader construction entries
  remain covered by the separate top-level generated-value fixtures.

## 2026-10-27 — Public native AOT Clojure consumers

- Added a public Long producer and fresh source-free Clojure receivers. Direct,
  factory and reflective constructors retain nil/type rejection, concrete final
  storage, NullRestricted, long/double slots, nullable controls and captured reads
  before and after reload. Reflection alone unwraps InvocationTargetException.
  No mocks, production changes, dependencies or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-native-field-aot-test/source-free-public-native-value-test`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. Fresh EA3 receivers
  use every policy and normal/interpreter execution with preview and no exports.
  Scoped formatting passed after automatic correction; clj-kondo reported no
  warnings or errors.
- Next: reuse the native fixture for separate Java consumers; mixed public AOT,
  incompatible native receivers and final regression verification remain open.
  This slice does not complete acceptance 7 or 8.

## 2026-10-27 — Public native AOT Java consumer

- Added a separately compiled Java consumer of the public Long producer. Source-free
  EA3 receivers retain concrete storage, native annotation, mixed slots, nullable
  controls, companion reads and nil/type rejection under every policy in normal and
  interpreter execution. No mocks, production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-native-field-aot-test/java-public-native-value-consumer`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. Scoped
  `bb clj-fmt check` and `clj-kondo --lint` passed without warnings or errors.
  Java compilation uses `--enable-preview --release 27`; receivers enable preview
  without internal exports.
- Next: incompatible native receivers, native function-contained timing, mixed public
  AOT and full regressions remain open. Direct Java access does not establish guarded
  namespace timing. Acceptance 7 and 8 remain incomplete; design.md needs no change.

## 2026-10-27 — Incompatible public native AOT

- Public native Long artifacts reject two direct class loads on Java 8 and
  preview-disabled EA3 under every policy in normal/interpreter execution. Source
  is deleted before consumption. Both attempts report UnsupportedClassVersionError
  without warnings. No mocks, production, dependency or design-contract changes.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-native-field-aot-test/incompatible-public-native-value-consumer`
  exited 0: 1 test / 38 assertions; Scry failure directory empty. Scoped clj-kondo
  passed without warnings/errors; formatting passed after automatic correction.
  Real EA3 compilation enables preview; incompatible receivers use no preview or
  internal exports.
- Next: native function-contained timing, mixed public AOT and final regressions.
  Direct class rejection does not establish namespace guard timing. Acceptance 7
  and 8 remain incomplete; design.md needs no change.

## 2026-10-27 — Native function-contained public AOT

- Added a source-free public Long definition inside a function. Namespace loading
  and repeated invocation stay silent under every policy. Value representation,
  concrete Long storage, native annotation and factory nil/type rejection remain
  fixed. No mocks, production changes or dependencies. The design contract is
  unchanged; design.md required no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-native-field-aot-test/function-public-native-value-aot-test`
  exited 0: 1 test / 20 assertions; Scry failure directory empty. EA3 children use
  preview in normal/interpreter execution without exports. Scoped clj-kondo passed
  with no warnings/errors; scoped formatting passed after automatic correction.
- Next: mixed public AOT and final regressions remain open. This slice checks factory
  construction inside function-contained definitions; top-level native fixtures
  cover direct Clojure, reflective and separate Java construction. Acceptance 7
  and 8 remain incomplete.

## 2026-10-27 — Mixed public AOT Clojure consumers

- Added a public mixed Long/String producer. Source-free direct, factory and reflective
  constructors check each constrained position before and after namespace reload.
  Final concrete storage, per-field native annotation selection, long/double slots,
  nullable data and companion reads remain fixed. Reflection alone unwraps constructor
  exceptions. No mocks, production changes, dependencies or design-contract changes;
  design.md required no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-mixed-field-aot-test/source-free-public-mixed-value-test`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. EA3 receivers use
  every policy in normal/interpreter execution with preview and no exports. Scoped
  clj-kondo passed without warnings/errors; formatting passed after automatic correction.
- Next: mixed Java consumers, incompatible receivers and function-contained timing.
  Full regressions and acceptance 7/8 remain incomplete.

## 2026-10-27 — Incompatible public mixed AOT

- Public mixed Long/String artifacts reject two direct class loads on Java 8 and
  preview-disabled EA3 under every policy in normal/interpreter execution. Source
  is deleted before consumption. Both attempts report UnsupportedClassVersionError
  without warnings. No mocks, production changes or dependencies. The design contract
  is unchanged; design.md required no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-mixed-field-aot-test/incompatible-public-mixed-value-consumer`
  exited 0: 1 test / 38 assertions; Scry failure directory empty. Scoped clj-kondo
  passed without warnings/errors; formatting passed after automatic correction.
  Real EA3 compilation enables preview; incompatible receivers use no preview or
  internal exports.
- Next: mixed Java consumers, mixed function-contained timing and final regressions.
  Direct class rejection does not establish namespace guard timing. Acceptance 7
  and 8 remain incomplete.

## 2026-10-27 — Separate Java consumers of public mixed AOT

- Added a separately compiled Java consumer of the public Long/String artifact.
  It checks concrete final storage and native annotation selection for both fields,
  each constrained position's nil/type rejection, mixed slots and companion reads.
  Source is deleted before Java compilation and execution. No mocks, production
  changes or dependencies. The design contract is unchanged; design.md needs no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-mixed-field-aot-test/java-public-mixed-value-consumer`
  exited 0: 1 test / 22 assertions; Scry failure directory empty. EA3 consumers use
  preview under every policy in normal/interpreter execution without exports.
  Scoped clj-kondo and `bb clj-fmt check` passed without findings.
- Next: mixed function-contained timing and final regressions. This test uses direct
  Java class access, not namespace policy guards. Acceptance 7/8 remain incomplete.

## 2026-10-27 — Mixed function-contained public AOT

- Source-free public mixed definitions now load and execute twice without warnings
  under every policy. Long retains native annotation; String retains generated checks.
  Factory construction rejects nil and wrong types at each position. No mocks,
  production changes or dependencies. The design contract is unchanged; design.md
  needs no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J-Xmx256m
  -M:fallback-test --var value-type.public-mixed-field-aot-test/function-public-mixed-value-aot-test`
  exited 0: 1 test / 20 assertions; Scry failure directory empty. Fresh EA3 consumers
  use preview in normal/interpreter execution without exports. Scoped clj-kondo
  passed without warnings/errors; formatting passed after automatic correction.
- Next: final regressions and acceptance audit remain open. Function-contained tests
  check factory construction; top-level fixtures cover direct Clojure, reflective and
  separate Java construction. This slice does not complete acceptance 7 or 8.

## 2026-10-27 — Native field flattening regression matrix

- Ran existing public native construction, mixed primitive slots, methods and
  companion checks with backend loader, transformation, storage and access tests.
  All four controls pass; no test or production changes were needed. No mocks,
  dependencies or design changes. design.md remains the acceptance contract.
- Command for each combination: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -J-XX:<sign>UseFieldFlattening <mode> -M:test -m scry.cli
  --namespace value-type.restricted-fields-test --namespace value-type.impl.loader-test
  --namespace value-type.impl.transform-test --namespace value-type.impl.field-access-test
  --namespace value-type.impl.field-storage-test`, with `<sign>` set to `+` and `-`,
  and `<mode>` omitted or set to `-J-Xint`. The alias enables preview; no exports.
  EA3 `27-jep401ea3+1-1`, Clojure 1.12.6. Each command exited 0 with 44 tests,
  363 passing assertions, zero failures/errors and an empty `.scry-results/`.
  Each result was checked before the next run replaced the failure directory.
- Next: generated semantic controls, packaged-source consumers, full regression
  suites and documentation. These controls establish native semantics, not flat
  layout, performance, new JVM support or completion of acceptance 8.

## 2026-10-27 — Generated receiving execution controls

- Ran real receiving compilation and construction, without mocks or changed
  dependencies. Existing tests cover both public expansion entry points, eval,
  function analysis and metadata-preserving source loading. Identity tests also
  cover captured reads; both representations cover exact-Class redefinition.
  No production or test changes were needed. design.md remains unchanged.
- For Java 8 (`/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`)
  and EA3 (`$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`), ran
  `JAVA_CMD=<java> clojure <mode> -M:fallback-test
  --var value-type.identity-receiving-test/receiving-identity-fields-test
  --var value-type.identity-receiving-test/receiving-redefined-child-test`.
  `<mode>` was omitted or `-J-Xint`; preview and exports were absent.
  All four commands exited 0: 2 tests / 174 assertions each.
- On EA3 ran `JAVA_CMD=<ea3-java> clojure <mode> -M:test -m scry.cli
  --namespace value-type.value-receiving-test` in both modes. The alias supplies
  preview, not exports. Both commands exited 0: 3 tests / 589 assertions each.
  This namespace covers generated-only, native and mixed receiving, plus child
  redefinition. Each of the six runs had zero failures/errors; checked the empty
  Scry failure directory before the next run replaced it.
- Next: all-policy matrix audit, packaged-source consumers, full regression suites
  and documentation. These focused default-policy controls do not complete
  acceptance 8, prove controlled native absence or establish new JVM support.

## 2026-10-27 — Full runtime regression execution controls

- Ran all runtime tests against real EA3 compilation and execution. No failures
  required code or test changes; no mocks or dependencies were added. The design
  contract is unchanged, so design.md needs no edit.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure <mode> -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
  -M:test -m scry.cli --dir test/runtime`, with `<mode>` omitted and then
  `-J-Xint`. EA3 `27-jep401ea3+1-1`, Clojure 1.12.6; the alias enables preview.
  Each command exited 0: 113 tests / 3770 assertions, zero failures or errors.
  Checked that `.scry-results/` was empty after each run, before replacement.
- Next: compatibility and packaged-source suites, documentation and acceptance
  audit remain open. The export is needed by array tests; these runs do not
  replace field-only no-export controls, all-policy tests or controlled native
  absence evidence. No new JVM support or performance claim follows.

## 2026-10-27 — Packaged-source non-nil consumers

- Extended the JAR-only consumer with nested Point/String constraints. Direct and
  factory construction retain concrete storage and reject nil/wrong types at each
  position. Real dependency resolution and fresh JVMs use no mocks. No production,
  dependency or design-contract changes; design.md needs no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:build-test -m scry.cli --dir test/build` exited 0: 9 tests / 110 assertions;
  `.scry-results/` empty. Consumers use Java 8 and EA3 preview off/on, silent policy,
  with only artifact/runtime JARs. Array controls retain their explicit export.
  Scoped clj-kondo passed without warnings/errors; formatting passed after correction.
- Next: compatibility/tooling suites, documentation and acceptance audit remain open.
  This verifies packaged source, not source-free AOT, all-policy packaging or native
  annotation selection. The existing AOT and field-only controls remain necessary.

## 2026-10-27 — Tooling regressions and generated API

- The tooling suite found that Quickdoc exposed the new internal identity/value
  receiving macros. Added no-doc overrides beside the existing native boundary
  override and regenerated doc/API.md. The existing public API boundary test now
  passes. No production behavior, dependencies or design-contract changes; design.md
  needs no edit. Tests use the real generator, not mocks.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:quickdoc:tooling-test -m scry.cli --dir test/tooling` exited 0: 11 tests /
  217 assertions; Scry failure directory empty. The initial tooling-only command
  lacked the bb source path; include :quickdoc for this suite. Structured Scry
  failure data identified the exposed macros before correction.
- `bb api-docs`, `bb api-docs --check`, scoped `bb clj-fmt check` and scoped
  clj-kondo all passed, with zero lint warnings/errors. Next: compatibility suites,
  usage/runtime and architecture documentation, then acceptance audit. Generated
  API synchronization alone does not complete acceptance 9.

## 2026-10-27 — Release regressions

- Ran the real release suite without mocks. No production, test, dependency or
  design-contract changes were needed; design.md remains unchanged.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:release:release-test -m scry.cli --dir test/release` exited 0: 12 tests /
  123 assertions, zero failures/errors; `.scry-results/` contained no files.
  No preview or internal exports were enabled. The initial `:release-test`-only
  invocation ran no tests because release implementation namespaces were absent;
  include `:release` to supply `dev/release`.
- Next: compatibility suites, usage/runtime and architecture documentation, quality
  checks and acceptance audit remain open. Release tooling tests do not verify live
  publication or complete acceptance 9.

## 2026-10-27 — Declaration and capability compatibility controls

- Preview-enabled execution exposed a test assumption: the public nested fixture
  is a value class there, not a fallen-back identity class. Expected enforcement
  now uses the independent JVM configuration and explicit native-capability input.
  Exact Class, descriptor and metadata assertions remain. No production changes,
  mocks, dependencies or design-contract changes were needed.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.fields-test --namespace value-type.field-capability-test` with
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and no
  flags, then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java` without and
  with `-J--enable-preview`. Each final run exited 0: 23 tests / 258 assertions,
  zero failures/errors; checked empty `.scry-results/` before the next run.
  Capability children use their own explicit flags; no exports were added.
- Scoped `bb clj-fmt check` and `clj-kondo --lint` passed for the changed test.
  Next: remaining compatibility namespaces, documentation and acceptance audit.
  These focused runs do not complete full compatibility or controlled-absence
  acceptance. The initial preview failure was a test expectation, not a blocker.

## 2026-10-27 — Runtime-policy compatibility regressions

- Ran existing state-based tests against real child JVMs, without mocks. No
  production, test, dependency or design-contract changes were needed; design.md
  remains unchanged. These controls protect ordinary definitions alongside the
  new constrained paths; they do not prove constrained-field warning scope.
- Command: `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.runtime-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, first
  without flags, then with `-J--enable-preview`. No internal exports were used.
- All three CLI runs exited 0 with 9 tests each: Java 8 and preview-disabled
  EA3 each passed 91 assertions; preview-enabled EA3 passed 66. Checked empty
  `.scry-results/` after each run before the next run replaced it. The lower
  preview count follows the explicit value-mode branches in fallback-only tests.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  Do not mark the full compatibility suite or all-policy non-nil matrix complete
  from these ordinary-definition controls.

## 2026-10-27 — Identity backend compatibility regressions

- Existing real-bytecode tests pass for capture loading, identity constructors,
  companion reads, attributes, unsupported shapes and exact-Class loader checks.
  No mocks, code changes, dependencies or design-contract changes were needed;
  design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.capture-loading-test --namespace value-type.identity-backend-test
  --namespace value-type.identity-loader-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. No internal exports were used.
  Each run exited 0: 11 tests / 109 assertions, zero failures/errors. Checked
  the empty `.scry-results/` after each run before replacement.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  Backend shape rejection is not full public-path defect-propagation evidence.
  Capture-loading children explicitly use Java 8 and preview-disabled EA3,
  independent of the parent flags; these runs do not complete the full matrix.

## 2026-10-27 — Array boundary compatibility regressions

- Existing real-JVM array boundary, inspection and sequence tests pass unchanged.
  No mocks, production changes, dependencies or design-contract changes were needed;
  design.md remains unchanged. These controls protect the excluded array API boundary.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.array-boundary-test --namespace value-type.array-info-boundary-test
  --namespace value-type.array-sequence-boundary-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 4 tests / 2599
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run.
  Children retain their explicit policy, preview and array-export configurations.
- The first combined shell call exceeded its tool timeout; its process continued.
  Waited for completion, then repeated the matrix with sufficient time to capture
  each exit status. No timeout was counted as passing evidence or a human blocker.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  These array controls do not complete portable-field acceptance or full compatibility.

## 2026-10-27 — Ordinary transfer compatibility regressions

- Ran existing independent-producer/consumer and concurrent-namespace tests without
  mocks. No code, dependency or design-contract changes were needed; design.md
  remains unchanged. These ordinary-field controls preserve fixed identity output,
  receiving policy and warning scope, not constrained-field concurrency evidence.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.transfer-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. All three runs exited 0: 2 tests /
  21 assertions, zero failures/errors. Checked the empty `.scry-results/` after
  each run before replacement. No internal exports were added.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  Do not infer constrained-field concurrent warning coverage or full compatibility
  completion from these ordinary-definition controls.

## 2026-10-27 — Ordinary AOT compatibility regressions

- Existing real-JVM tests pass for fallback compilation, source-free identity/value
  loading and function-contained policy timing. No mocks, code changes, dependencies
  or design-contract changes were needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 4 tests / 54
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. Children retain their explicit producer/consumer flags.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  These ordinary-field controls do not complete constrained-field AOT acceptance
  or full compatibility verification.

## 2026-10-27 — Complete identity receiving compatibility regressions

- Existing real-JVM tests pass unchanged. They cover local receiving paths and
  child redefinition, fixed identity policy, incompatible value receivers and
  public generated-value enforcement under all policies. No mocks, dependencies
  or design-contract changes were needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.identity-receiving-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 5 tests / 228
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. No internal exports were added.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  Child processes retain their explicit flags, independent of parent preview.
  This is not controlled native-absence or constrained concurrent-warning evidence.

## 2026-10-27 — Complete identity backend AOT compatibility regressions

- Existing real-JVM tests pass unchanged, using source-free Clojure and separately
  compiled Java consumers. No mocks, code changes, dependencies or design-contract
  changes were needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.identity-aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 2 tests / 43
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. Child processes retain their explicit JVM flags.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  These fixtures install capture explicitly; do not count this run as public
  guarded-form policy evidence or full compatibility completion.

## 2026-10-27 — Complete generated-value backend AOT compatibility regressions

- Existing real-JVM tests pass unchanged. No mocks, code changes, dependencies or
  design-contract changes were needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.generated-value-aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 3 tests / 62
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. Child JVMs retain their explicit flags and policy matrices.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  These explicit-capture fixtures cover source-free Java/Clojure construction and
  incompatible artifact rejection, not public receiving guards or controlled native
  absence. Parent JVM variations do not imply additional child configurations.

## 2026-10-27 — Native field AOT compatibility regressions

- Existing real-JVM tests pass unchanged. Saved native descriptors, constructor
  signatures and companion references pass inspection. Source-free Clojure and
  separately compiled Clojure/Java consumers retain checks and reads; incompatible
  receivers reject saved value output. No mocks, code changes, dependencies or
  design-contract changes were needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.field-aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 5 tests / 48
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. No internal exports were added.
- Next: remaining compatibility namespaces, documentation and acceptance audit.
  Producers and supported consumers explicitly use EA3 preview with strict policy;
  incompatible children cover all policies. Host variations do not expand child
  coverage or establish controlled native absence or full compatibility completion.

## 2026-10-27 — Complete public field-transfer compatibility regressions

- Existing real-JVM tests pass unchanged; no mocks, dependencies or code changes
  were needed. The design contract remains unchanged, so design.md is unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.field-transfer-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 6 tests / 758
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. No internal exports were added.
- Next: remaining public AOT compatibility namespaces, documentation and acceptance
  audit. This namespace covers independent public expansion receivers, repeated
  installation/rejection and constructor checks with explicit child policies and
  normal/interpreter modes. Host variations do not add child configurations.
  Controlled native absence and the full compatibility suite remain unverified.

## 2026-10-27 — Complete public identity/generated-value AOT regressions

- Existing real-JVM tests pass unchanged; no mocks, code changes or dependencies
  were needed. No design-contract change was needed; design.md remains unchanged.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.public-field-aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 8 tests / 288
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. No internal exports were added.
- Next: public native/mixed AOT namespace regressions, documentation and acceptance
  audit. These public fixtures cover source-free Clojure/Java consumers, namespace
  reload, function-contained policy timing and incompatible generated-value output.
  Child policies and normal/interpreter modes are explicit; host variations do not
  add child configurations or establish controlled native absence.

## 2026-10-27 — Complete public native/mixed AOT regressions

- Existing real-JVM tests pass unchanged; no mocks, code changes or dependencies
  were needed. The design contract is unchanged; design.md needs no change.
- Ran `JAVA_CMD=<java> clojure <flags> -M:fallback-test --namespace
  value-type.public-native-field-aot-test --namespace
  value-type.public-mixed-field-aot-test`. Java 8 used
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` without
  flags; EA3 used `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, without
  flags and then with `-J--enable-preview`. Each run exited 0: 8 tests / 204
  assertions, zero failures/errors. Checked empty `.scry-results/` after each run
  before replacement. No internal exports were added.
- Next: audit remaining compatibility coverage, update documentation and complete
  acceptance verification. These tests cover source-free Clojure/Java consumers,
  reload, function-contained timing and incompatible receivers. Child policies and
  normal/interpreter modes are explicit; host variations do not add child cases
  or establish controlled native absence. Do not mark full acceptance complete.

## 2026-10-27 — Portable-field usage contract

- Replaced native-only usage claims with the delivered representation/enforcement
  split, fixed receiving modes, AOT guard timing and constructor guarantees.
  Documented removed-key rejection and excluded constructor-bypassing paths.
  No production code, dependencies or design contract changed; design.md is unchanged.
- Verification: `bb vale` exited 0 with an empty alert map after replacing the
  unrecognized spelling “APIs”. `git diff --check` passed. The initial targeted
  `bb vale doc/usage.md` call was rejected because the task accepts no arguments;
  reran the complete maintained-prose check. No runtime tests were needed for this
  prose-only slice; existing real-JVM evidence remains in earlier entries.
- Next: README, runtime instructions and embodiment still need alignment. Do not
  infer matrix completion from this documentation update; controlled native absence,
  remaining coverage audit and final quality/acceptance checks remain open.

## 2026-10-27 — Portable-field README

- Replaced native-only field and fallback claims with the portable contract and
  repaired the usage-guide anchor. Kept native array requirements separate. No
  code, dependencies or design-contract changes; design.md remains unchanged.
- `bb vale` exited 0 with an empty alert map; `git diff --check` passed. This
  prose-only slice required no new runtime tests or mocks. Existing real-JVM
  evidence remains applicable; no additional JVM support is claimed.
- Next: runtime instructions and architecture embodiment, then remaining coverage
  and acceptance checks. README alignment does not complete acceptance 9.

## 2026-10-27 — Portable-field runtime instructions

- Replaced native-only packaging claims and the obsolete usage anchor. Documented
  representation/enforcement outcomes, public AOT namespaces and normal/interpreter
  receiving commands. Retained child-JVM, controlled-capability and silent-only
  packaging evidence limits. No code, dependencies or design-contract changes;
  design.md remains unchanged.
- Verification: `bb vale` exited 0 with an empty alert map; `git diff --check`
  passed. Checked command namespace names against the test tree. Runtime suites
  were not rerun for this prose-only slice; no tests or mocks were added.
- Next: align architecture embodiment, then complete remaining coverage and quality
  audits. The new command examples do not establish additional matrix results.

## 2026-10-27 — Portable-field architecture embodiment

- Replaced native-only embodiment claims with the two production backends, fixed
  receiving modes and AOT policy boundaries. Linked current evidence and retained
  historical experiments unchanged. Updated the architecture index and project
  state. No code, dependency or design-contract changes; design.md needs no edit.
- `bb vale` exited 0 with an empty alert map; `git diff --check` passed. This
  prose-only slice adds no tests or mocks and requires no runtime rerun. Existing
  real-JVM evidence remains bounded by each recorded child configuration.
- Next: coverage and final quality/acceptance audits. Controlled public native
  absence and constrained concurrent-warning coverage remain explicit gaps; this
  documentation update does not complete acceptance 9.

## 2026-10-27 — Repository quality verification

- Ran `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and
  `bb api-docs --check`; each exited 0. Formatting is current, lint reports
  zero errors/warnings, Vale returns an empty alert map, and generated API output
  matches its source. Lint reports four informational redundant double coercions
  in existing backend/loader tests; no code changes were required.
- Host: OpenJDK 25.0.2, Babashka 1.13.225; no preview or export flags.
  `git diff --check` also passes. No runtime tests or mocks were added: this
  slice verifies repository quality, not constructor or JVM behavior.
- The design contract and implementation approach are unchanged; design.md needs
  no edit. Next: audit outstanding coverage, especially controlled public native
  absence and constrained concurrent warnings. Keep final acceptance open; these
  commands do not complete the runtime/policy matrix or full-suite evidence.

## 2026-10-27 — Constrained concurrent warning scope

- Added real-JVM coverage for generated Long constraints through both public
  expansion entry points. Eight synchronized namespace workers each install two
  types; two independent runtimes per entry point and execution mode each emit
  exactly one warning. Identity equality, factory Class and field reads remain fixed.
- The shared warning fixture must rename the fixed binary owner string along with
  the declaration symbol. The initial run failed with ClassNotFoundException because
  the ordinary-field fixture renamed only the symbol; corrected the fixture, not
  production code. No mocks, dependencies or API changes. The design is unchanged.
- Verification: `clojure -Spath -M:fallback-test > /tmp/vt017-cp`, then each explicit
  JVM with `-cp "$(cat /tmp/vt017-cp)" clojure.main -m scry.cli --namespace
  value-type.transfer-test`: Java 8 at
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`, and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java` without/with
  `--enable-preview`. Each exits 0: 3 tests, 47 passing assertions, no failures or
  errors; `.scry-results` is empty. Child receivers additionally cover `-Xint`.
  `bb clj-fmt:check` and `git diff --check` pass.
- Next: controlled public native absence and remaining acceptance audit. This test
  covers warn policy and successful construction, not concurrent strict rejection
  or additional nil/type outcomes; existing constructor tests remain separate.

## 2026-10-27 — Concurrent constrained strict rejection

- Added real receiving JVM tests for both public expansion entry points. Eight
  concurrent namespaces each reject the fixed identity expansion twice with policy
  Error, no owner and no factory. No warnings, mocks, production changes or new
  dependencies. The design contract is unchanged; design.md needs no edit.
- Used `clojure -Spath -M:fallback-test > /tmp/vt017-cp`, then explicit Java 8
  (`/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`) and EA3
  (`$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`) with `-cp
  "$(cat /tmp/vt017-cp)" clojure.main -m scry.cli --var
  value-type.transfer-test/constrained-concurrent-strict-test`. EA3 ran without
  and with `--enable-preview`. Each exited 0: 1 test / 14 assertions, empty
  `.scry-results`. Child receivers also cover `-Xint`; no internal exports.
  Scoped formatting and clj-kondo pass with zero warnings/errors. An initial
  delimiter error required repair and correction of the expected result form.
- Next: controlled public native absence and acceptance audit. Concurrent strict
  rejection does not establish successful constructor checks or new JVM support.

## 2026-10-27 — Partial AOT acceptance reconciliation

- Audited public identity/generated/native/mixed AOT fixtures against prior namespace
  results and marked four matching deployment items complete. Added a scoped audit
  with source deletion, construction entries, policy boundaries and evidence limits.
  Corrected the stale design context that called all production integration unverified;
  the acceptance contract is unchanged. No code, tests, mocks or dependencies changed.
- Next: controlled native absence remains distinct from String ineligibility.
  Do not close the combined generated-only native-requirement item from incompatible
  class rejection alone. Saved-bytecode and remaining acceptance audits remain open.
- Verification: `bb vale` exited 0 with an empty alert map; `git diff --check`
  passed. Reviewed fixture source and existing structured-run records; no runtime
  rerun was needed for this checklist/documentation-only slice.

## 2026-10-27 — Documentation acceptance reconciliation

- Audited README, usage, core docstring and generated API against the portable
  contract. Marked the two matching documentation items complete; no production
  or test changes were needed. The design contract and plan approach are unchanged.
- Verification: `bb api-docs --check`, `bb vale` and `git diff --check` exit 0.
  Vale returns an empty alert map. No runtime evidence is added by these checks.
- Next: controlled native absence, saved-bytecode coverage and remaining acceptance
  audits stay open. Documentation completion does not close acceptance 9, which
  also requires regression evidence. See documentation-audit.md for scope.

## 2026-10-27 — Saved-bytecode coverage audit

- Separated native saved-file inspection from source-free execution in all four
  modes. Existing generated consumers verify storage and reads by execution, not
  direct instruction inspection. Keep the combined persistence item open; next
  add generated identity/value and mixed saved-file inspection as specified in
  saved-bytecode-audit.md. No production, tests, dependencies or design-contract
  changes were needed; design.md remains unchanged.
- Verification: reviewed the real AOT fixtures and inspector against recorded
  namespace results. `bb vale` exited 0 with an empty alert map;
  `git diff --check` passed. This audit adds no runtime evidence and uses no mocks.
  Controlled native absence and the remaining acceptance audit remain open.

## 2026-10-27 — Saved public identity bytecode

- Added direct inspection of public Java 8 compiler output in a separate EA3
  process. String storage, absent native annotations, Object constructor parameters
  and non-empty concrete companion references pass. The inspector now accepts owner
  and field names; its native fixture entry point remains unchanged. No production,
  dependency or design-contract changes; design.md needs no edit. No mocks.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var value-type.public-field-aot-test/persisted-public-identity-output-test
  --var value-type.field-aot-test/persisted-restricted-field-output-test` exits 0:
  2 tests / 12 assertions; `.scry-results` is empty. Scoped formatting and lint
  pass without warnings or errors. Children use explicit JVMs without exports.
- Next: generated-value and mixed saved-file inspection. EA3 parses baseline bytes
  without loading the saved Classes; this does not add baseline execution evidence
  or replace source-free constructor tests. Controlled native absence remains open.

## 2026-10-27 — Saved public generated-value bytecode

- Added inspection of actual public String-constrained value compiler output under
  strict policy. A separate EA3 process parses saved files without loading them.
  Concrete storage, absent native annotations, Object constructor parameters and
  non-empty concrete companion references pass. No mocks, production changes or
  dependencies. The design contract remains unchanged; design.md needs no edit.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var value-type.public-field-aot-test/persisted-public-generated-value-output-test
  --var value-type.public-field-aot-test/persisted-public-identity-output-test
  --var value-type.field-aot-test/persisted-restricted-field-output-test` exits 0:
  3 tests / 19 assertions; `.scry-results` is empty. Scoped `bb clj-fmt check`
  and `clj-kondo --lint` pass with zero warnings/errors. `bb vale` returns an
  empty alert map; `git diff --check` passes.
- Next: inspect both mixed fields and their companion references. The combined
  persistence item remains open. These checks do not replace source-free execution
  or establish controlled native absence; that acceptance gap remains separate.

## 2026-10-27 — Saved public mixed bytecode

- Added inspection of actual public mixed Long/String compiler output under strict
  policy. Separate EA3 processes parse both saved field descriptors, native annotation
  selection, Object constructor parameters and non-empty companion references. No
  mocks, production changes or dependencies. The design contract remains unchanged;
  design.md needs no edit. The combined persistence checklist item is complete.
- `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var value-type.public-mixed-field-aot-test/persisted-public-mixed-output-test
  --var value-type.public-field-aot-test/persisted-public-generated-value-output-test
  --var value-type.public-field-aot-test/persisted-public-identity-output-test
  --var value-type.field-aot-test/persisted-restricted-field-output-test` exits 0:
  4 tests / 31 assertions; `.scry-results` is empty. Scoped formatting and lint
  pass with zero warnings/errors.
- Next: controlled native absence and the remaining acceptance audit. Inspection
  verifies saved descriptors and signatures, not constructor execution; retain the
  separate source-free consumer evidence. Whole-class attributes remain separate.

## 2026-10-27 — Baseline public declaration errors

- Added real public macroexpansion tests for missing, forward, unresolved, primitive,
  interface, abstract and array tags. Both expansion entry points retain structured
  field context and leave owner and factory unresolved. No mocks, production changes
  or dependencies. The design contract remains unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set first to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, and again with the latter
  plus `-J--enable-preview`. Each exits 0: 19 tests / 225 assertions; no failures
  or errors. `.scry-results` is empty. Scoped lint passes with zero errors/warnings.
  Scoped formatting passes after applying the formatter's indentation correction.
- Next: controlled native absence and remaining acceptance gaps. This slice extends
  public validation evidence, not capability or all-policy coverage. Existing broad
  declaration checklist items remain open because their other cases need audit.

## 2026-10-27 — Public metadata rejection

- Added public expansion tests for seven invalid non-nil values and removed-key
  true/false/nil presence alongside valid non-nil metadata. Both entry points retain
  structured field/value context and leave owner and factory unresolved. No mocks,
  production changes or dependencies. The design contract is unchanged; design.md
  needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 20 tests / 305 assertions, no failures or
  errors. Checked empty `.scry-results` after each run. Scoped formatting and lint
  passed with zero warnings/errors.
- Next: mutable and disabled-field public cases, controlled native absence and
  remaining acceptance audit. Metadata rejection does not establish policy coverage.

## 2026-10-27 — Public mutable-field rejection

- Extended the real public mutable-field test to both expansion entry points.
  Both mutable keys reject enabled, disabled and invalid non-nil metadata before
  field validation, with structured mutable-field context and no installed owner
  or factory. No mocks, production changes or dependencies. The design contract
  is unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 20 tests / 361 assertions, no failures or
  errors. `.scry-results` was empty after each run. Scoped `bb clj-fmt check`
  and `clj-kondo --lint` passed with zero warnings/errors.
- Next: audit disabled-field and eligible-tag public coverage, then controlled
  native absence and remaining acceptance gaps. Mutable rejection does not
  establish enforcement or policy coverage.

## 2026-10-27 — Disabled-field constructor controls

- Extended public disabled-field tests to absent, false and nil metadata. Factories
  and reflective constructors accept nil, String and non-String arguments while
  storage remains long/Object. No mocks, production changes or dependencies.
  The design contract is unchanged; design.md needs no edit.
- An initial attempt to evaluate pre-expanded ordinary definitions failed on EA3
  preview with structured `:not-installed` data. Ordinary definitions require
  compilation-local capture; restored direct public evaluation. This slice does
  not claim transferred-expansion coverage.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each final run exited 0: 20 tests / 396 assertions, no
  failures/errors; checked empty `.scry-results` after each run. Scoped formatting
  and lint pass with zero warnings/errors.
- Next: eligible-tag coverage and controlled native absence, then remaining acceptance
  audit. These ordinary-field controls do not complete all-policy acceptance.

## 2026-10-27 — Public recursive replacement rejection

- Added public enclosing-type validation through both expansion entry points.
  Rejection retains the existing exact Class and factory; that factory still
  accepts nil for the original ordinary field. This differs from first-definition
  rejection, where no owner exists. No mocks, production changes or dependencies.
  The design contract is unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 21 tests / 408 assertions, no failures or
  errors. Checked empty `.scry-results` after each run. Scoped formatting and lint
  passed with zero warnings/errors; `git diff --check` passed.
- Next: eligible-tag public coverage and controlled native absence, then remaining
  acceptance audit. Recursive replacement validation does not prove successful
  redefinition with a new constrained Class or all-policy coverage.

## 2026-10-27 — Public imported external fields

- Added real public receiving tests for imported Date and fully qualified Date tags
  through both expansion entry points. Exact final storage and reference retention
  pass; factories and reflective constructors reject nil/wrong types. Only reflection
  unwraps InvocationTargetException. No mocks, production changes or dependencies.
  The design contract is unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 22 tests / 440 assertions, zero failures
  or errors. Checked empty `.scry-results` after each run. Scoped formatting and
  lint pass with zero warnings/errors after automatic indentation correction.
- Next: audit aliased and nested public tags, controlled native absence and remaining
  acceptance gaps. These default-policy tests do not complete the policy matrix.

## 2026-10-27 — Public nested external class tags

- Extended imported and fully qualified public tag tests to the concrete Java nested
  class AbstractMap$SimpleEntry. Both expansion entry points preserve exact final
  storage and valid references; factories and reflective constructors reject nil
  and wrong types. No mocks, production changes or dependencies. The design contract
  is unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 22 tests / 472 assertions, no failures or
  errors. Checked empty `.scry-results` after each run. Scoped formatting and lint
  pass with zero warnings/errors.
- Next: aliased tags, nested defvaluetype coverage and controlled native absence
  still need audit. A nested Java class does not establish nested defvaluetype or
  all-policy acceptance.

## 2026-10-27 — Public nested nullable child tags

- Extended the public concrete-tag fixture with a real defvaluetype child containing
  nil. Local and binary-name tags retain exact final storage through both expansion
  entry points; factories and reflective constructors reject nil/wrong types. No
  mocks, production changes or dependencies. The design contract is unchanged;
  design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 22 tests / 505 assertions, zero failures
  or errors. Checked empty `.scry-results` after each run. Scoped formatting and
  lint pass with zero warnings/errors; no internal exports were used.
- Next: aliased tags and controlled native absence, then remaining acceptance audit.
  These default-policy cases do not establish all-policy nested construction or
  direct Java construction; existing deployment evidence remains separate.

## 2026-10-27 — Public class aliases

- Extended real public concrete-tag tests with Class import aliases for external
  Date and a defvaluetype child containing nil. Both expansion entry points retain
  exact final storage and factory/reflective nil/type checks. These are Class
  aliases, not namespace aliases. No mocks, production changes or dependencies.
  The design contract is unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.fields-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each final run exited 0: 22 tests / 537 assertions, no
  failures or errors. Checked empty `.scry-results` after each run. Scoped
  formatting and lint pass with zero warnings/errors; no internal exports.
- Next: reconcile declaration acceptance and verify controlled native absence.
  Alias tests use default policy; they do not complete the all-policy matrix.

## 2026-10-27 — Declaration acceptance reconciliation

- Audited the existing public and specification fixtures and completed the two
  declaration integration checklist items. See declaration-acceptance-audit.md
  for exact coverage and limits. No test additions were needed for these items;
  no production changes, mocks or dependencies. Design.md remains accurate.
- Reran `clojure -M:fallback-test --namespace value-type.fields-test` with
  `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. All exited 0: 22 tests / 537 assertions each, no failures
  or errors. `.scry-results` contained no files after each run.
- Next: controlled native absence and remaining policy/runtime acceptance.
  Default-policy declaration evidence does not complete those items. Unrelated
  metadata retention is not whole-class attribute preservation.
- Documentation verification: `bb vale` and `git diff --check` pass.

## 2026-10-27 — Separate Java constructor acceptance

- Reconciled the slice 3 Java constructor item with four existing public AOT tests;
  no new tests, mocks, dependencies or production changes were needed. The AOT
  audit records exact consumer coverage and limits. Design.md remains accurate.
- With `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:fallback-test --var value-type.public-mixed-field-aot-test/java-public-mixed-value-consumer`:
  exit 0, 1 test / 22 assertions. Then ran the same command with three `--var`
  selectors: `value-type.public-field-aot-test/java-public-identity-consumer`,
  `value-type.public-field-aot-test/java-public-generated-value-consumer` and
  `value-type.public-native-field-aot-test/java-public-native-value-consumer`:
  exit 0, 3 tests / 102 assertions. Both runs had zero failures/errors and empty
  `.scry-results`. Children select explicit JVMs, policies and execution modes.
- Next: controlled native absence and remaining public construction/read audits.
  Direct Java access does not execute Clojure namespace policy guards; strict
  identity Java construction is not strict namespace-loading evidence.

- Documentation verification: `bb vale` and `git diff --check` pass.

## 2026-10-27 — Public receiving acceptance reconciliation

- Audited the six real-JVM public transfer tests and completed the first three
  slice 4 items. See receiving-acceptance-audit.md for coverage and limits. No
  production changes, new tests, mocks or dependencies were needed. Design.md
  remains accurate; the implementation approach is unchanged.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --namespace value-type.field-transfer-test`. Scry reports
  6 tests / 758 assertions, zero failures/errors; `.scry-results` is empty.
  The host used no preview or export flags. Child JVMs select their own explicit
  configurations. The background shell did not retain the process exit code;
  the completed Scry summary and empty failure directory are the run evidence.
- Next: controlled native absence and remaining policy/read acceptance. Rejection
  fixtures check owner absence, not factory absence. Do not infer discarded-form
  timing, child Class redefinition or source-free AOT from this transfer audit.
- Documentation verification: `bb vale` and `git diff --check` pass.

## 2026-10-27 — Discarded constrained expansion policy

- Added real fresh-JVM tests for both expansion entry points using String constraints.
  Returned forms are discarded; owners and factories remain absent. Baseline warn
  emits once across two types; strict rejects twice. Capable runtimes allow every
  policy without warnings. No mocks, production changes or dependencies. The design
  contract remains unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --var
  value-type.runtime-test/constrained-discarded-expansion-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exits 0: 1 test, respectively 22, 22 and 24 assertions,
  zero failures/errors. Checked empty `.scry-results` after each run. Scoped lint
  and formatting pass with zero warnings/errors; no internal exports were used.
- Next: validation/policy precedence and explicit loader-binding cases remain for
  discarded constrained forms. This test uses String ineligibility, not controlled
  native absence, and does not establish constructor execution or enforcement modes.

## 2026-10-27 — Discarded constrained loader bindings

- Extended the existing public discarded-form test with fresh threads that assert
  bound or unbound Compiler/LOADER state. All policies and both expansion entry
  points retain owner/factory absence, immediate warning and repeated strict
  rejection. No returned form is evaluated; no mocks or production changes.
  The design contract remains accurate and unchanged.
- Ran `clojure -M:fallback-test --var
  value-type.runtime-test/constrained-discarded-expansion-test` with `JAVA_CMD`
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 1 test with 44, 44 and 48 assertions,
  respectively; no failures/errors and empty `.scry-results` after each run.
- Scoped clj-kondo passes with zero errors/warnings. Corrected formatter indentation
  and reran scoped formatting successfully. Next: validation/policy precedence;
  loader-binding evidence does not prove constructor execution or native absence.

## 2026-10-27 — Discarded validation before policy

- Added fresh-JVM public checks for invalid metadata, removed-key presence, missing,
  unresolved and interface tags through both expansion entry points under every
  policy. Structured declaration errors precede strict fallback errors; invalid
  forms emit no warnings and install no owners or factories. Subsequent valid
  discarded forms still warn once or reject strict fallback. No production changes,
  mocks or dependencies. The design contract remains accurate and unchanged.
- Ran `clojure -M:fallback-test --var
  value-type.runtime-test/constrained-validation-before-policy-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then the latter with
  `-J--enable-preview`. Each exited 0: 1 test / 24 assertions, no failures/errors,
  empty `.scry-results`. Scoped lint passes with zero errors/warnings; scoped
  formatting passes after indentation correction.
- Combined with prior explicit loader-binding and discarded-policy runs, this closes
  the discarded-expansion item. Next: controlled native absence and remaining
  construction/read acceptance. These tests do not execute returned constructors.

## 2026-10-27 — Isolated receiving-policy audit

- Extended the real concurrent constrained receiving test to silent policy, using
  the same two independent runtimes, both expansion entry points and normal/interpreter
  execution as warn. No production changes, mocks or dependencies. Added the missing
  intention comment to the strict test. The design contract is unchanged;
  design.md needs no edit.
- Ran `clojure -M:fallback-test --namespace value-type.transfer-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  then `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, and the latter with
  `-J--enable-preview`. Each exited 0: 4 tests / 85 assertions, no failures/errors,
  empty `.scry-results`. Scoped `bb clj-fmt check` and clj-kondo pass with zero
  lint warnings/errors.
- The receiving audit combines this warning-scope evidence with existing public
  field-transfer policy checks to close the slice 4 isolated-policy item. Concurrent
  successful construction is not nil/type rejection evidence. Next: controlled
  native absence, exact child Class identity and remaining construction/read audits.

## 2026-10-27 — Public generated-value child redefinition

- Extended the existing receiving redefinition test with public macroexpand and
  macroexpand-1 cases whose children are real identity deftypes. This selects
  generated enforcement without changing capability probes. Both old and new
  owners retain their exact child Class, accept the matching instance and reject
  nil or the other definition's instance through reflective constructors.
- Ran `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
  clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-redefined-child-test`, then the same
  command with `-J-Xint`. Both exit 0: 1 test / 60 assertions, no failures/errors;
  `.scry-results` is empty after each. Scoped `bb clj-fmt check` and clj-kondo
  pass with zero lint errors/warnings. No production or dependency changes.
- The design contract remains unchanged. This is local public reanalysis evidence,
  not cross-process child replacement or controlled native absence. Those cases
  remain open for the broader exact-Class acceptance item.

## 2026-10-27 — Packaged-source acceptance reconciliation

- Audited artifact-only consumers and completed the slice 6 source-JAR item. See
  packaging-acceptance-audit.md for classpath isolation, public loading, constructor
  and array coverage. No new tests, mocks, dependencies or production changes were
  needed. The design contract remains accurate; design.md needs no edit.
- Ran `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure
  -M:build-test -m scry.cli --dir test/build`: exit 0, 9 tests / 110 assertions,
  zero failures/errors; `.scry-results` empty. Child configurations remain explicit.
- Next: controlled native absence and remaining construction/read acceptance.
  Packaging uses silent policy and source JARs, not source-free AOT. The capable
  consumer retains array exports; do not count it as field-only no-export evidence.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Release and tooling regressions

- Ran with `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"`:
  `clojure -M:release:release-test -m scry.cli --dir test/release` and
  `clojure -M:quickdoc:tooling-test -m scry.cli --dir test/tooling`.
  Both exited 0 without preview or export flags. Release passes 12 tests /
  123 assertions; tooling passes 11 tests / 217 assertions. Both have zero
  failures/errors; checked empty `.scry-results` before the next run.
- Existing tests pass unchanged; no mocks, dependencies or production changes
  were needed. The design contract remains accurate; design.md needs no edit.
- Next: full runtime and compatibility suites, controlled native absence and
  remaining acceptance gaps. Tooling checks do not establish constructor behavior
  or new JVM support. The combined suite checklist item remains open.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Current full runtime regressions

- Ran `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
  clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
  -M:test -m scry.cli --dir test/runtime`, then the same command with `-J-Xint`.
  Both exited 0: 113 tests / 3790 assertions, zero failures/errors. Checked empty
  `.scry-results` after each run before replacement. The test alias enables preview;
  the export supports array tests, not a field-only no-export claim.
- Existing real-JVM tests pass unchanged. No mocks, production changes, dependencies
  or new tests were needed. The design contract remains accurate and unchanged.
- Next: full compatibility verification, controlled native absence and remaining
  construction/read acceptance. These complete runtime runs do not close the combined
  regression item or add baseline runtime-suite support.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Full compatibility regressions

- Ran `clojure -M:fallback-test` with `JAVA_CMD` set first to
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, then to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`.
  Ran EA3 again with `clojure -J--enable-preview -M:fallback-test`.
  Java 8 and preview-disabled EA3 each pass 102 tests / 5901 assertions;
  preview-enabled EA3 passes 102 tests / 5880 assertions. All CLI exits are 0
  with zero failures/errors. Copied each empty `.scry-results` directory and
  its exit status to `/tmp/task017-compat/` before the next run.
- These complete compatibility runs complement the recorded full runtime, build,
  release and tooling runs. Child JVM policies and execution flags remain explicit;
  changing the host does not expand child coverage. No mocks, production changes,
  dependencies or new tests were needed. The design contract remains accurate;
  design.md is unchanged.
- Next: controlled native absence and remaining construction/read acceptance.
  Full regression success does not close those matrix or acceptance gaps.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public receiving mixed primitive slots

- Extended `receiving-native-and-mixed-fields-test` with long/double fields after
  the reference arguments. Real direct, factory and reflective constructors now
  verify five-argument order and captured reads alongside per-position nil/type
  rejection. Native/native and native/generated cases retain nullable child data
  and a nullable owner field. No mocks or production changes were needed.
- With `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"`, ran
  `clojure -M:test -m scry.cli --var value-type.value-receiving-test/receiving-native-and-mixed-fields-test`:
  exit 0, 1 test / 414 assertions. Then ran `clojure -J-Xint -M:test -m scry.cli
  --namespace value-type.value-receiving-test`: exit 0, 3 tests / 609 assertions.
  Both failure directories were empty. The alias enables preview; no exports
  were used. `bb clj-fmt check test/runtime/value_type/value_receiving_test.clj`
  passes.
- Next: complete public identity/multiple-constraint and broader read acceptance.
  This slice does not establish controlled native absence or close the combined
  construction checklist items. The design contract remains accurate and unchanged.

## 2026-10-27 — Public identity multiple constraints and slots

- Extended the existing receiving fixture with a second generated String constraint
  and long/double slots. Both public expansion entry points and the internal control
  retain concrete final storage, argument order, captured reads and per-position
  nil/type rejection through direct, factory and reflective constructors. No mocks,
  production changes or dependencies. The design remains accurate and unchanged.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`. All four runs exited 0: 1 test / 216 assertions, zero failures/errors;
  checked empty `.scry-results` after each run. No preview or export flags.
- Scoped formatting passes after automatic indentation correction. Scoped clj-kondo
  reports zero errors/warnings and three informational redundant double coercions.
  Next: nested nullable-child and broader public read acceptance. This slice does
  not establish all-policy nested behavior or controlled native absence.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public identity nullable children

- Extended the existing receiving fixture with a real defvaluetype child containing
  nil. Both public expansion entry points and the internal control retain exact
  child storage, captured reads, multiple constraints and primitive slots through
  direct, factory and reflective constructors. No mocks, production changes or
  dependencies. The design contract remains accurate; design.md needs no edit.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`. All four runs exited 0: 1 test / 441 assertions, zero failures/errors;
  checked empty `.scry-results` after each. No preview or export flags.
- Scoped formatting passes; scoped lint reports zero errors/warnings and three
  existing informational double coercions. Next: all-policy nested behavior,
  broader read acceptance and controlled native absence. Default-policy local
  receiving tests do not complete those acceptance items.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Direct public receiving field reads

- Added type-hinted Clojure readers compiled after receiving analysis to the
  identity and native/mixed fixtures. Readers access all five fields directly,
  including concrete references, nullable storage and long/double slots. Both
  public expansion entry points and all receiving paths pass. No mocks,
  production changes or dependencies. The design remains accurate and unchanged.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`: four passing runs, 1 test / 459 assertions each.
- With the EA3 path, ran `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`: two passing runs, 1 test / 432 assertions each. The test
  alias enables preview; no internal exports were used. All six exits were 0;
  checked empty `.scry-results` after each run before replacement.
- Scoped formatting passes. Scoped lint has zero errors/warnings and three
  existing informational double-coercion findings. Next: broader public
  method/protocol coverage and controlled native absence. Direct-read evidence
  does not close the combined read acceptance item or the all-policy matrix.

## 2026-10-27 — Public receiving Object overrides

- Added Object toString overrides that read all five fields in the identity and
  native/mixed receiving fixtures. Both public expansion entry points and all
  three analysis paths retain constrained references, nullable data and primitive
  slots. No mocks, production changes or dependencies. The design remains accurate
  and unchanged.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`: four runs, each exit 0, 1 test / 477 passing assertions.
- With the EA3 path, ran `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`: both exit 0, 1 test / 450 passing assertions. The alias
  enables preview; no internal exports. Checked empty `.scry-results` after each
  of the six runs. Scoped formatting and lint pass with zero errors/warnings;
  lint retains three existing informational double-coercion findings.
- Next: public protocol and broader control-flow coverage, then controlled native
  absence. Object method reads do not complete the combined read acceptance item
  or the all-policy matrix. Generated-only value method coverage remains separate.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public identity receiving protocol reads

- Added a real receiver-local protocol to the identity receiving fixture. Dispatch
  reads both constrained fields, nullable storage and long/double slots through
  both public expansion entry points and all three analysis paths, with external
  and nullable-child tags. No mocks, production changes or dependencies. The design
  remains accurate and unchanged; design.md needs no edit.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`: four runs, each exit 0, 1 test / 495 passing assertions. No preview
  or export flags; checked empty `.scry-results` after each run.
- Scoped formatting and lint pass with zero errors/warnings; lint retains three
  existing informational double-coercion findings. Next: value protocol reads and
  broader control-flow acceptance, then controlled native absence. This slice does
  not close the combined read acceptance item or establish all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public native/mixed receiving protocol reads

- Added a real receiver-local protocol to the native/mixed receiving fixture.
  Dispatch reads both constrained fields, nullable storage and long/double slots
  through both public expansion entry points and all three analysis paths. Internal
  fixed-mode controls also pass. No mocks, production changes or dependencies.
  The design remains accurate and unchanged; design.md needs no edit.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`. Both exits were 0, each with 1 test / 468 passing assertions.
  The alias enables preview; no internal exports. Checked empty `.scry-results`
  after each run before replacement.
- Scoped formatting and lint pass with zero errors/warnings. Next: generated-only
  value protocol reads and broader control-flow acceptance, then controlled native
  absence. This slice does not close the combined read acceptance item or establish
  all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public generated-only value protocol reads

- Added a real receiver-local protocol to the generated-only value receiving
  fixture. Dispatch reads required Date and nullable storage through both public
  expansion entry points and all three analysis paths. The internal fixed-mode
  control also passes. No mocks, production changes or dependencies. The design
  contract remains accurate and unchanged; design.md needs no edit.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-value-fields-test`, without and with
  `-J-Xint`. Both exited 0: 1 test / 144 passing assertions, zero failures/errors.
  Checked empty `.scry-results` after each run. The alias enables preview; no
  internal exports. Scoped formatting and lint pass with zero errors/warnings.
- Next: broader control-flow acceptance and controlled native absence. Protocol
  reads do not complete the combined read item or establish all-policy coverage.

## 2026-10-27 — Generated-only value nested closure reads

- Extended the real generated-only receiving fixture with IDeref returning a
  branch-selecting closure, which returns a second closure. Both branch choices
  preserve Date and nullable references with nil and String nullable inputs.
  Both public expansion entry points and the internal control use all three
  analysis paths. No mocks, production changes or dependencies. The design
  contract remains accurate; design.md needs no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-value-fields-test`, without and with
  `-J-Xint`. Both exited 0 with 1 test / 180 passing assertions and empty
  `.scry-results`. Preview comes from the alias; no internal exports.
- Scoped cljfmt and clj-kondo checks pass with zero lint errors or warnings.
  Next: broader public read acceptance and controlled native absence. This
  fixture does not establish all-policy coverage or close the combined read item.

## 2026-10-27 — Identity nested closure nullable reads

- Extended the identity receiving fixture to execute both nested closure branches
  with nil and String optional data. Assertions preserve reference identity for
  both selected fields, including nested nullable children. Internal controls and
  both public expansion entry points run through all three analysis paths. No
  mocks, production changes or dependencies. The design contract remains accurate;
  design.md needs no change.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`. All four runs exited 0: 1 test / 549 passing assertions each, with
  empty `.scry-results` checked after each run. No preview or export flags.
- Scoped cljfmt and clj-kondo checks pass with zero errors/warnings. Lint reports
  four informational double-coercion findings. Next: broader public read acceptance
  and controlled native absence. This does not close the combined read item or
  establish all-policy coverage.

## 2026-10-27 — Native/mixed nested closure nullable reads

- Extended the native/mixed receiving fixture with a multi-arity closure and a
  nested closure after branch selection between native and nullable fields. Nil
  and String nullable inputs retain both constrained fields and long/double slots.
  Internal controls and both public expansion entry points use all three analysis
  paths. No mocks, production changes or dependencies. The design contract remains
  accurate; design.md needs no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`. Both exited 0: 1 test / 594 passing assertions each. Checked
  empty `.scry-results` after each run. Preview comes from the alias; no exports.
- Scoped cljfmt and clj-kondo checks pass with zero errors/warnings;
  `git diff --check` passes. Next: remaining public read acceptance and controlled
  native absence. This does not establish all-policy coverage or complete the
  combined read acceptance item.

## 2026-10-27 — Generated-only value direct reads and Object overrides

- Extended the generated-only receiving fixture with a type-hinted direct reader
  and toString override. Both read required Date and nullable storage with nil and
  String optional inputs. Internal controls and both public expansion entry points
  use all three analysis paths. No mocks, production changes or dependencies.
  The design contract remains accurate; design.md needs no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-value-fields-test`, without and with
  `-J-Xint`. Both exited 0: 1 test / 252 passing assertions each. Checked empty
  `.scry-results` after each run. Preview comes from the alias; no exports.
- Scoped cljfmt and clj-kondo checks pass with zero errors/warnings;
  `git diff --check` passes. Next: combined public read acceptance audit and
  controlled native absence. These runs do not establish all-policy coverage.

## 2026-10-27 — Explicit Java interface receiving reads

- Added Callable to the three receiving fixtures. Direct interface dispatch reads
  all fields with nil and non-nil nullable inputs. Both public expansion entry
  points and internal controls use eval, function analysis and source loading.
  This complements protocol, Object and closure reads without mocks, production
  changes or dependencies. Reviewed design.md; its contract needs no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-value-fields-test --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`. Each exited 0: 2 tests / 954 passing assertions. Preview
  comes from the alias; no exports. Checked empty `.scry-results` after each run.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and the EA3 path above, each without and with `-J-Xint`. All four runs exited 0:
  1 test / 585 passing assertions, with empty `.scry-results` after each run.
  No preview or exports.
- Scoped cljfmt and clj-kondo pass with zero errors/warnings; four existing
  informational double-coercion findings remain. Next: reconcile combined read
  acceptance and complete controlled native absence. These focused runs do not
  establish all-policy coverage or replace final regressions.

## 2026-10-27 — Public read acceptance reconciliation

- Completed the combined public read checklist item after auditing real identity,
  generated-value and native/mixed fixtures. See public-read-acceptance-audit.md
  for exact scope and limits. No new tests, mocks, dependencies or production
  changes were needed. Reviewed design.md; its contract remains accurate.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each without and with
  `-J-Xint`: four exits 0, each 1 test / 585 passing assertions.
- With the EA3 path, ran `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-value-fields-test --var
  value-type.value-receiving-test/receiving-native-and-mixed-fields-test`, without
  and with `-J-Xint`: two exits 0, each 2 tests / 954 passing assertions. Preview
  comes from the alias; no exports. Copied each empty `.scry-results` directory
  to `/tmp/task017-read-audit/` before the next run; all six had no failures/errors.
- Next: controlled native absence and separate construction, hierarchy and attribute
  acceptance. Read completion does not establish all-policy nested construction
  or complete acceptance 4 or 8.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public constrained runtime annotations

- Added public-field-annotations-test using real public expansion and evaluation,
  with String and Long constraints. Deprecated survives on the owner, field and
  toString method; reflective invocation still reads the concrete field. No mocks,
  production changes or dependencies. Reviewed design.md; no contract change.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`. Repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 20 passing assertions
  each. Preserved empty failure directories in `/tmp/task017-annotations/`.
- Scoped `bb clj-fmt check` and `clj-kondo --lint` pass with zero errors/warnings.
  This covers runtime annotations through public receiving compilation, not all
  classfile attributes, invisible annotations, AOT attributes or unsupported-shape
  propagation. Those checks and controlled native absence remain open.

## 2026-10-27 — Public annotation interpreter verification

- Ran `clojure -J-Xint -J-Dvalue-type.fallback=silent -M:fallback-test
  --namespace value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`, then
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`. Repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 20 assertions each,
  zero failures/errors. Copied empty failure directories and command logs to
  `/tmp/task017-annotations-xint/` before subsequent runs. No internal exports.
- Existing real public compilation tests pass unchanged; no mocks, production
  changes or dependencies. Reviewed design.md; no contract change is needed.
- Next: whole-class and invisible attributes, unsupported-shape propagation and
  controlled native absence remain open. Interpreter annotation evidence does not
  close the combined attribute item or establish all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Mixed public field annotations

- Extended the real public annotation fixture with a Long/String owner and exact
  reflective field reads. Preview-enabled EA3 exercises native/generated fields
  together; baseline configurations exercise two generated fields. No mocks,
  dependencies or production changes. Reviewed design.md; no contract change.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`. Repeated EA3 with
  `-J--enable-preview`, and all three configurations with `-J-Xint`.
  Six exits 0, each 1 test / 42 assertions, no failures/errors. Logs and copied
  empty Scry failure directories are in `/tmp/task017-mixed-annotations/`.
- Scoped cljfmt and clj-kondo pass with zero errors/warnings. Next: invisible and
  whole-class attributes, unsupported-shape propagation and controlled native
  absence remain open. This check does not close the combined attribute item.

## 2026-10-27 — Public method annotation values

- Extended the public annotation fixture with the JDK DescriptorKey annotation.
  Its string value survives on toString through both expansion entry points for
  generated, native and mixed fields. This checks annotation payloads rather than
  presence alone. No mocks, production changes or dependencies. Reviewed design.md;
  its contract remains accurate and unchanged.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 48 assertions each,
  zero failures/errors. Logs and empty failure directories are retained in
  `/tmp/task017-annotation-values/`. No internal exports.
- Scoped formatting and lint pass with zero errors/warnings; `git diff --check`
  passes. Next: invisible and whole-class attributes, unsupported-shape propagation
  and controlled native absence. This does not close the combined attribute item.

## 2026-10-27 — Public annotation payload interpreter verification

- Ran `clojure -J-Xint -J-Dvalue-type.fallback=silent -M:fallback-test
  --namespace value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 48 assertions each,
  zero failures/errors. Logs and copied empty Scry failure directories are in
  `/tmp/task017-annotation-values-xint/`. No internal exports.
- Existing real public compilation tests pass unchanged; no mocks, dependencies
  or production changes. Reviewed design.md; no contract change is needed.
- Next: invisible and whole-class attributes, unsupported-shape propagation and
  controlled native absence remain open. This does not close the combined
  attribute item or establish all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public class annotation array values

- Added SupportedOptions to the real public annotation fixture. Reflection checks
  both strings in order after owner transformation. No mocks, dependencies or
  production changes. Reviewed design.md; its contract needs no change.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 54 assertions each,
  zero failures/errors. Logs and empty Scry failure directories are retained in
  `/tmp/task017-array-annotations/`. No internal exports.
- Next: invisible and whole-class attributes, unsupported-shape propagation and
  controlled native absence remain open. Array payload evidence is limited to
  normal execution and silent policy; it does not close the combined attribute item.
- Scoped cljfmt and clj-kondo pass with zero errors/warnings. `bb vale` exits 0
  with an empty alert map; `git diff --check` passes.

## 2026-10-27 — Public class annotation array interpreter verification

- Ran `clojure -J-Xint -J-Dvalue-type.fallback=silent -M:fallback-test
  --namespace value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 54 assertions each,
  zero failures/errors. Logs and copied empty Scry failure directories are in
  `/tmp/task017-array-annotations-xint/`. No internal exports.
- Existing real public compilation tests pass unchanged. No mocks, dependencies
  or production changes. Reviewed design.md; no contract change is needed.
- Next: invisible and whole-class attributes, unsupported-shape propagation and
  controlled native absence remain open. This does not close the combined
  attribute item or establish all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public class annotation enum values

- Added SupportedSourceVersion to the real public annotation fixture. Reflection
  verifies RELEASE_8 after String, Long and mixed owner transformation through
  both expansion entry points. No mocks, dependencies or production changes.
  Reviewed design.md; no contract change is needed.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 60 assertions each,
  zero failures/errors. Logs and empty Scry failure directories are retained in
  `/tmp/task017-enum-annotations/`. No internal exports.
- Scoped cljfmt and clj-kondo pass with zero errors/warnings. Next: interpreter
  enum payload coverage, invisible and whole-class attributes, unsupported-shape
  propagation and controlled native absence. This does not close the combined
  attribute item or establish all-policy coverage.

## 2026-10-27 — Public class annotation enum interpreter verification

- Ran `clojure -J-Xint -J-Dvalue-type.fallback=silent -M:fallback-test
  --namespace value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. All three exits were 0: 1 test / 60 assertions each,
  zero failures/errors. Logs, exit codes and copied empty Scry failure directories
  are in `/tmp/task017-enum-annotations-xint/`. No internal exports.
- Existing real public compilation tests pass unchanged. No mocks, dependencies
  or production changes. Reviewed design.md; no contract change is needed.
- Next: invisible and whole-class attributes, unsupported-shape propagation and
  controlled native absence. This does not close the combined attribute item or
  establish all-policy coverage.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Public method annotation boolean values

- Added Transient with explicit false to the public annotation fixture. Its default
  is true, so reflection detects a lost payload as well as a lost annotation.
  Both expansion entry points cover String, Long and mixed owners. No mocks,
  dependencies or production changes. Reviewed design.md; no contract change.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. Repeated all three with `-J-Xint`. All six exits were 0:
  1 test / 66 assertions each. Copied empty failure directories and logs to
  `/tmp/task017-boolean-annotations/`. No internal exports.
- Scoped lint passes with zero errors/warnings; formatting passes after automatic
  indentation correction. Next: invisible and whole-class attributes, unsupported
  shape propagation and controlled native absence. This does not close the
  combined attribute item or establish all-policy coverage.

## 2026-10-27 — Ordinary nullable field annotations

- Extended the public annotation fixture with an annotated ordinary field beside
  String, Long and mixed constraints. Both expansion entry points retain Deprecated,
  Object storage and nil acceptance. No mocks, dependencies or production changes.
  Reviewed design.md; its contract remains accurate and unchanged.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. Repeated all three with `-J-Xint`. All six exits were 0:
  1 test / 84 assertions each, zero failures/errors. Logs and copied empty Scry
  failure directories are in `/tmp/task017-nullable-annotations/`. No exports.
- Scoped formatting and lint pass with zero errors/warnings. Next: invisible and
  whole-class attributes, unsupported-shape propagation and controlled native
  absence. This does not close the combined attribute item or all-policy coverage.

## 2026-10-27 — Ordinary primitive field annotations

- Extended the real public annotation fixture with annotated long/double fields.
  Reflection checks Deprecated, primitive storage types and distinct values beside
  String, Long and mixed constraints through both expansion entry points. No mocks,
  dependencies or production changes. Reviewed design.md; no contract change.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --namespace
  value-type.public-field-annotations-test` with JAVA_CMD set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java` and
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`; repeated EA3 with
  `-J--enable-preview`. Repeated all three with `-J-Xint`. All six final exits
  were 0: 1 test / 120 assertions each. Checked empty `.scry-results` after each
  run; logs are in `/tmp/task017-primitive-annotations/`. No internal exports.
- Scoped formatting and lint pass with zero errors/warnings. Next: whole-class
  attributes, unsupported-shape propagation and controlled native absence remain
  open. This does not close the combined attribute item or all-policy coverage.

## 2026-10-27 — Shared public constructor acceptance audit

- Reconciled the slice 3 shared construction item with existing real receiving tests;
  see constructor-acceptance-audit.md. The same outcome tables cover all three
  Clojure constructor entries and both constrained positions where present. Only
  reflective calls unwrap InvocationTargetException. No tests, dependencies or
  production code changed. Reviewed design.md; no contract change is needed.
- Ran `clojure -J-Dvalue-type.fallback=silent -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each normally and with
  `-J-Xint`: four exits 0, each 1 test / 585 assertions.
- On EA3, ran `clojure -J-Dvalue-type.fallback=silent -M:test -m scry.cli
  --var value-type.value-receiving-test/receiving-value-fields-test
  --var value-type.value-receiving-test/receiving-native-and-mixed-fields-test`,
  normally and with `-J-Xint`: two exits 0, each 2 tests / 954 assertions.
  The test alias supplies preview; no internal exports. An initial runtime command
  omitted `-m scry.cli` and failed before tests; the corrected commands above pass.
- Logs and copied empty Scry failure directories are in
  `/tmp/task017-constructor-audit/`. All-policy nested construction, controlled
  native absence and attribute/shape acceptance remain open. Do not treat this
  focused audit as completion of the broader construction matrix.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — All-policy value receiving verification

- Ran the existing real generated-only and native/mixed receiving fixtures under
  silent, warn and error. Both public expansion entry points and internal controls
  cover eval, source loading and function analysis. Direct, factory and reflective
  construction retain per-position nil/type rejection, nested nullable children,
  exact storage and long/double slots. No mocks, dependencies or code changes.
  Reviewed design.md; its contract needs no change.
- Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -J-Dvalue-type.fallback=POLICY -M:test -m scry.cli
  --var value-type.value-receiving-test/receiving-value-fields-test
  --var value-type.value-receiving-test/receiving-native-and-mixed-fields-test`.
  Repeated each policy with `-J-Xint`. Preview comes from the alias; no exports.
  All six exits were 0, each 2 tests / 954 assertions, zero failures/errors.
  Copied empty Scry failure directories, logs and exit codes to
  `/tmp/task017-value-policies/` before subsequent runs.
- Next: baseline all-policy nested construction, controlled native absence and
  remaining attribute/shape acceptance. These real-JVM runs do not prove controlled
  native absence, warning capture or explicit flattening configurations. Keep the
  broader acceptance items open.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Baseline warn nested receiving verification

- Ran `clojure -J-Dvalue-type.fallback=warn -M:fallback-test --var
  value-type.identity-receiving-test/receiving-identity-fields-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each normally and with
  `-J-Xint`. All four exits were 0: 1 test / 585 assertions each, zero failures
  or errors. Logs, exit codes and copied empty Scry failure directories are in
  `/tmp/task017-identity-warn/`. No preview or internal exports.
- Existing real receiving tests cover nested nullable children, multiple constraints,
  primitive slots and direct/factory/reflective construction without mocks or new
  dependencies. Reviewed design.md; its contract remains accurate and unchanged.
- Next: reconcile strict nested rejection and warning scope, then controlled native
  absence and attribute/shape acceptance. These runs do not assert warning counts
  or prove strict rejection; the broad policy matrix remains open.

- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Strict nested public parent rejection

- Added a fresh-JVM test with a real identity deftype child and constrained parent.
  Using deftype isolates parent policy from child declaration rejection. Both public
  expansion entry points reject repeated attempts, leave parent and factory absent,
  and emit no warning. No mocks, dependencies or production changes. Reviewed
  design.md; its contract needs no change.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var
  value-type.identity-receiving-test/strict-nested-public-declaration-test`:
  exit 0, 1 test / 12 assertions, zero failures/errors; `.scry-results` is empty.
  Children run Java 8 and preview-disabled EA3, normally and with `-Xint`, without
  exports. Scoped formatting and lint pass with zero errors/warnings; four existing
  informational double-coercion findings remain.
- Next: warning-scope reconciliation and controlled native absence. This test does
  not prove nested defvaluetype creation under strict policy or complete the matrix.

## 2026-10-27 — Nested public warning scope

- Added a real child-JVM test for nested public declarations under silent and warn.
  The child is a defvaluetype with nullable data; repeated parent declarations use
  both expansion entry points. Exact child storage, retained references and factory
  nil/type rejection pass. Warn is counted across child and all parents in each
  runtime, not independently per definition. No mocks or production changes.
  Reviewed design.md; no contract change is needed.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var
  value-type.identity-receiving-test/nested-public-warning-scope-test`:
  exit 0, 1 test / 28 assertions, zero failures/errors; `.scry-results` is empty.
  Eight child processes cover Java 8 and preview-disabled EA3, silent/warn and
  normal/interpreter execution, without exports.
- Scoped cljfmt and clj-kondo pass (zero errors/warnings; four existing informational
  double-coercion findings). `git diff --check` passes. Next: controlled native
  absence and remaining attribute/shape checks. This narrow factory test supplements,
  but does not replace, the direct/reflective construction evidence.

## 2026-10-27 — Public field-position construction audit

- Reconciled the public construction checklist item with existing receiving fixtures,
  recorded all-policy runs and isolated nested policy checks. The constructor audit
  separates successful construction from strict baseline rejection and controlled
  native absence. No code, dependencies or new fixtures were needed. Reviewed
  design.md; its contract is unchanged.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var
  value-type.identity-receiving-test/strict-nested-public-declaration-test --var
  value-type.identity-receiving-test/nested-public-warning-scope-test`:
  exit 0, 2 tests / 40 assertions, zero failures/errors; `.scry-results` is empty.
  Real children cover Java 8 and preview-disabled EA3 in normal/interpreter
  execution. No mocks or internal exports.
- Next: controlled native absence and remaining attribute/shape checks. Closing
  the field-position item does not close the capability matrix or acceptance 2–4.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Generated value attribute preservation

- Extended the composed-transform fixture to run native and generated modes over
  the same real class bytes. SourceFile/SourceDebugExtension and visible/invisible
  field payloads survive; only native storage receives NullRestricted. Ordinary
  primitive fields retain descriptors and annotations. No mocks, production changes
  or dependencies. Reviewed design.md; its contract remains unchanged.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:test -m scry.cli --var
  value-type.impl.transform-test/integrated-storage-attributes-test`, then the same
  command with `-J-Xint`: each exits 0 with 1 test / 24 assertions and zero
  failures/errors. Both failure directories are empty. No internal exports.
- Scoped `bb clj-fmt fix test/runtime/value_type/impl/transform_test.clj` and
  `git diff --check` pass. Next: whole-class attributes, unsupported-shape propagation
  and controlled native absence. This test inspects bytes; it does not execute the
  synthetic storage.Value constructor or complete the combined attribute item.

## 2026-10-27 — Composed class annotation preservation

- Added real class-byte checks for visible and invisible class annotations through
  native and generated value transformation. String, boolean and integer payloads
  survive. Generalized the test comparison helper from FieldModel to AttributedElement;
  existing field checks still pass. No mocks, production changes or dependencies.
  Reviewed design.md; no contract change is needed.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:test -m scry.cli --var
  value-type.impl.transform-test/integrated-class-annotations-test --var
  value-type.impl.transform-test/integrated-storage-attributes-test`, then the same
  command with `-J-Xint`. Each exits 0: 2 tests / 28 assertions, zero failures/errors.
  Failure directories are empty. No internal exports.
- Scoped cljfmt and clj-kondo pass with zero lint errors/warnings; `git diff --check`
  passes. Next: other class attributes, unsupported-shape propagation and controlled
  native absence. These checks inspect bytes, not synthetic constructor execution;
  the combined attribute and final acceptance items remain open.

## 2026-10-27 — Composed method attribute preservation

- Added a separate focused namespace to avoid enlarging the transform test file.
  Real composed transforms preserve visible/invisible method annotation payloads
  and declared IOException on every method, including constructors, in native and
  generated modes. No mocks, production changes or dependencies. Reviewed design.md;
  its metadata-preservation contract needs no change.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:test -m scry.cli --namespace value-type.impl.method-attributes-test`, then
  the same command with `-J-Xint`. Each exits 0: 1 test / 28 assertions, zero
  failures/errors. Both `.scry-results` directories are empty. No internal exports.
- Scoped cljfmt and clj-kondo pass with zero lint errors/warnings;
  `git diff --check` passes. Next: remaining attributes, unsupported-shape
  propagation and controlled native absence. This is bytecode inspection, not
  execution of the synthetic storage.Value constructor or complete acceptance.

## 2026-10-27 — Constructor parameter metadata

- Added real-byte MethodParameters checks for native and generated value transforms.
  Five arguments include long/double slots, an unnamed parameter and distinct
  final/synthetic/mandated flags. Parameter order is independent of local slot width.
  No mocks, production changes or dependencies. Reviewed design.md; the existing
  unrelated-metadata contract needs no change.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:test -m scry.cli --namespace value-type.impl.method-attributes-test`, then
  the same command with `-J-Xint`. Each exits 0: 2 tests / 32 assertions,
  zero failures/errors; both failure directories are empty. No internal exports.
  Scoped cljfmt, clj-kondo and `git diff --check` pass.
- This is constructor metadata inspection, not synthetic constructor execution.
  Remaining attributes, unsupported-shape propagation and controlled native absence
  still need verification; the combined acceptance item stays open.

## 2026-10-27 — Method Deprecated marker preservation

- Extended the real composed-transform fixture with Deprecated marker attributes
  on methods and constructors, separate from runtime annotations. Native and
  generated modes retain exactly one marker per method. No mocks, production
  changes or dependencies. Reviewed design.md; its contract needs no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --namespace value-type.impl.method-attributes-test`,
  then the same command with `-J-Xint`. Each exited 0: 2 tests / 40 assertions,
  zero failures/errors; checked empty `.scry-results` after each run. Preview comes
  from the alias; no internal exports. Scoped cljfmt and clj-kondo pass with zero
  errors/warnings; `git diff --check` passes.
- This is bytecode inspection, not execution of synthetic constructors. Remaining
  attributes, unsupported-shape propagation and controlled native absence remain
  open; the combined acceptance item is not complete.

## 2026-10-27 — Ordered exception declarations

- Extended the composed method fixture from one declared exception to two distinct
  exceptions. Exact vector comparison checks both preservation and order on all
  methods, including constructors, in native and generated modes. No mocks,
  production changes or dependencies. Reviewed design.md; its metadata contract
  is unchanged.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --namespace value-type.impl.method-attributes-test`
  and the same command with `-J-Xint`. Each exited 0: 2 tests / 40 assertions,
  zero failures/errors; both failure directories were empty. Scoped cljfmt,
  clj-kondo and `git diff --check` pass.
- This checks declared exception metadata, not thrown-exception behavior or
  synthetic constructor execution. Remaining attributes, unsupported-shape
  propagation and controlled native absence remain open.

## 2026-10-27 — Consolidated acceptance audit

- Added acceptance-audit.md to map criteria 1–9 to the existing evidence and
  explicit gaps. Reconciled two stale public receiving/AOT checklist items.
  Completing the audit does not complete acceptance. No production code, tests,
  dependencies or public contract changed; existing tests suffice for this audit.
- Next slice: prioritize controlled native absence, then exact-Class reconciliation,
  failure propagation and the complete matrix. More isolated annotation payload
  checks do not address these gaps. No external blocker was found.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --namespace value-type.impl.method-attributes-test`
  and the same command with `-J-Xint`. Each exited 0: 2 tests / 40 assertions,
  zero failures/errors. Checked empty `.scry-results` after each run. These are
  real-byte inspection checks, not synthetic constructor execution. `bb vale`
  exits 0 with an empty alert map; `git diff --check` passes.

## 2026-10-27 — Identity redefinition across receiving paths

- Extended the real child-redefinition fixture to function analysis and
  metadata-preserving source loading, alongside eval. Each path reanalyzes the
  same expansion after child replacement. Both owners retain their exact field
  Class, accept their child and reject nil and the other child. No mocks,
  dependencies or production changes. Reviewed design.md; no contract change.
- Controlled native absence needs further design of the test setup: runtime caches
  a zero-argument probe, so it has no explicit capability argument to supply.
  Rather than substitute a mock or add a test-only production path, this slice
  advances the next independent exact-Class gap. This is not a human blocker.
- Ran `clojure -M:fallback-test --var
  value-type.identity-receiving-test/receiving-redefined-child-test` with JAVA_CMD
  set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each normally and
  with `-J-Xint`, without preview or exports. All four runs exit 0: 1 test /
  90 assertions, no failures/errors; checked empty `.scry-results` after each.
- Scoped cljfmt passes. Next: public sibling-loader compilation and cross-process
  replacement remain distinct from same-namespace redefinition; do not mark the
  combined exact-Class item complete from this evidence.

## 2026-10-27 — Value redefinition across receiving paths

- Extended the real value child-redefinition fixture to function analysis and
  metadata-preserving source loading. Both public expansion entry points and native/
  generated internal controls reanalyze the same expansion after child replacement.
  Both owners retain exact field Classes, accept their child and reject nil and the
  other child. No mocks, production changes or dependencies. Reviewed design.md;
  its contract remains accurate and unchanged.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:test -m scry.cli --var
  value-type.value-receiving-test/receiving-redefined-child-test`, then repeated
  with `-J-Xint`. Both exited 0: 1 test / 180 passing assertions, zero failures/errors.
  Checked empty `.scry-results` after each run. Preview comes from the alias;
  no internal exports. Scoped cljfmt and clj-kondo pass with zero errors/warnings.
- Next: public sibling-loader compilation and cross-process replacement remain
  separate exact-Class gaps. Controlled native absence also remains open. These
  checks do not complete the combined exact-Class item or the capability matrix.

## 2026-10-27 — Public sibling-loader mismatch rejection

- Added a real-loader public rejection control through macroexpand/macroexpand-1
  and eval, function analysis and metadata-preserving source loading. Structured
  field-class-mismatch errors remain visible; no owner or factory is installed.
  No production changes, mocks or dependencies. Reviewed design.md; the exact-Class
  and visible-failure contracts remain unchanged.
- Attempted successful sibling compilation first. DynamicClassLoader's shared
  name cache resolves the replacement Class rather than the imported original;
  compiler/context loader bindings did not make that fixture isolated. Retained
  this as a rejection control instead of weakening the expected Class contract.
  Next slice needs isolated name resolution for the successful sibling case.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test`
  with JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`. All four exited 0: 3 tests / 34 assertions, zero failures/errors.
  Checked empty `.scry-results` after each run. No preview or exports.
- Controlled native absence, successful sibling compilation, cross-process child
  replacement and the combined failure/matrix audits remain open.

## 2026-10-27 — Recovery after sibling import rejection

- Extended the real public mismatch test to replace the stale import and analyze
  the same expansion again. Eval, function analysis and source loading install an
  owner with the exact replacement field Class and a factory through both expansion
  entry points. A rejected attempt does not prevent corrected receiving analysis.
  No mocks, production changes or dependencies. Reviewed design.md; no contract change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`: all four exited 0, each 3 tests / 52 assertions, with empty
  `.scry-results`. No preview or exports. Removed a redundant do reported by lint;
  repeated the normal EA3 run with the same result. Scoped formatting and lint pass.
- This verifies recovery and exact field storage, not successful construction with
  isolated sibling loaders. That success case, controlled native absence and the
  broader acceptance gaps remain open.

## 2026-10-27 — Construction after sibling import recovery

- Added real default constructors to the sibling child fixture. After import
  correction, the recovered public factory retains the replacement instance and
  rejects nil and the original-loader instance through both expansion entry points
  and all three analysis paths. No mocks, production changes or dependencies.
  Reviewed design.md; the existing contract needs no change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`. All four exits were 0: 3 tests / 70 assertions, no failures/errors;
  checked empty `.scry-results` after each run. No preview or internal exports.
  Scoped cljfmt and clj-kondo pass with zero errors/warnings.
- Next: this proves construction after recovery, not simultaneous isolated sibling
  compilation. That case, controlled native absence and broader acceptance gaps
  remain open.

## 2026-10-27 — Reflective construction after sibling import recovery

- Extended the real-loader recovery test to call the Object constructor directly
  through reflection. It retains the replacement instance and rejects nil and the
  original-loader instance. Only InvocationTargetException is unwrapped. No mocks,
  production changes or dependencies. Reviewed design.md; no contract change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`. All four exits were 0: 3 tests / 88 assertions each, with empty
  `.scry-results`. No preview or exports. Scoped cljfmt and clj-kondo pass.
- This closes the factory-only limit for recovered reflective construction, not
  simultaneous isolated sibling compilation. Controlled native absence and the
  other acceptance-audit gaps remain open.

## 2026-10-27 — Direct construction after sibling import recovery

- Added a compiled direct Clojure constructor call after import correction. Both
  expansion entry points and eval/function/source receiving paths retain the exact
  replacement instance and reject nil and original-loader instances. No mocks,
  production changes or dependencies. Reviewed design.md; no contract change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`. All four exits were 0: 3 tests / 106 assertions each. Checked empty
  `.scry-results` after each run. No preview or exports. Logs are in
  `/tmp/task017-direct-recovery/`. Scoped cljfmt and clj-kondo pass.
- Recovery now covers direct, factory and reflective constructor entries. It does
  not prove successful simultaneous isolated sibling compilation. Controlled
  native absence and the other acceptance-audit gaps remain open.

## 2026-10-27 — Generated field constructor policy matrix

- Extended public-field-evaluation-policy-test with String and explicit normal/
  interpreter child execution. Real constructors retain typed values and reject
  nil/wrong types; repeated strict baseline attempts fail and warning counts remain
  scoped to the child runtime. Long remains the existing comparison case. No mocks,
  dependencies or production changes. Reviewed design.md; no contract change.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var
  value-type.field-capability-test/public-field-evaluation-policy-test`: exit 0,
  1 test / 108 assertions, zero failures/errors; `.scry-results` is empty.
  Children cover Java 8 and EA3 preview off/on, all policies and both execution
  modes. Log: `/tmp/task017-field-policy.log`. Scoped cljfmt passes.
- Next: String proves native ineligibility, not controlled native absence. This
  focused factory test does not close the full constructor or capability matrix.

## 2026-10-27 — Public constructor entry policy agreement

- Extended the String/Long public policy fixture to compile a direct constructor
  and invoke the Object constructor reflectively beside the factory. All three
  share expected concrete storage, valid values and nil/type rejection. Only the
  reflective invocation wrapper is unwrapped. No mocks, production changes or
  dependencies. Reviewed design.md; its contract needs no change.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var
  value-type.field-capability-test/public-field-evaluation-policy-test`: exit 0,
  1 test / 108 assertions, zero failures/errors; `.scry-results` is empty.
  Real children cover Java 8 and EA3 preview off/on, all policies and normal/
  interpreter execution. Log: `/tmp/task017-constructor-policy.log`.
  Scoped cljfmt and clj-kondo pass with zero errors/warnings; diff checks pass.
- Next: controlled native absence remains distinct from String ineligibility.
  Strict baseline declarations still reject before construction. This focused
  matrix does not complete the remaining exact-Class and failure-propagation gaps.

## 2026-10-27 — Malformed native-probe policy controls

- Extended the existing real-byte probe control to silent/warn/error and normal/
  interpreter child execution, with an explicit no-warning assertion. No mocks,
  production changes or dependencies. Reviewed design.md; its contract is unchanged.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --var value-type.field-capability-test/malformed-probe-test`:
  exit 0, 1 test / 18 assertions; `.scry-results` is empty. Log:
  `/tmp/task017-probe-policy.log`. Scoped cljfmt and clj-kondo pass.
- These checks call probe emission/enforcement helpers directly. They prove the
  four expected error classes with real JVM verification, not propagation through
  public macro selection. That audit and controlled native absence remain open.
  The current native detector has no explicit capability input; no test-only
  production switch or replacement probe was introduced.

## 2026-10-27 — Approved capability-test exception

The user approved `with-redefs` of `value-type.impl.runtime/field-capability` for
controlled native-capability absence. Updated design, plan, checklist and acceptance
audit to permit this narrow stub while keeping compilation and construction real.
Use isolated processes and compile inside the override. No production refactor or
test-only switch is required. This resolves the testing-method restriction, not the
acceptance gap: no new test has run as part of this task-document update.

## 2026-10-27 — Controlled native absence: public construction

- Added an isolated child-JVM fixture using only the approved field-capability
  override. Long remains a real eligible value Class. Both expansion entry points
  compile inside the override; factory, direct and reflective constructors retain
  final Long storage, accept 7 and reject nil/wrong types. Owners remain value
  classes without native annotations or fallback warnings. No production change
  or dependency change. The design contract is unchanged.
- Verification: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:fallback-test --namespace value-type.controlled-field-capability-test`
  exits 0: 1 test / 18 assertions, empty `.scry-results`. Children cover all three
  policies, normal/interpreter execution, preview enabled and no internal exports.
  Log: `/tmp/task017-controlled.log`. Scoped cljfmt and clj-kondo pass.
- Next: independent receiving and source-free AOT under controlled absence remain
  open. This is controlled-capability evidence, not support for another JVM.
  Print quoted fixture forms with metadata enabled or field constraints are lost.

## 2026-10-27 — Controlled native absence: independent receiving

- A strict-policy producer serializes both public expansions with metadata. Fresh
  consumers read and compile them inside the approved accessor override, under
  every policy and normal/interpreter execution. Real value owners retain final
  Long storage, no native annotation, constructor checks and no fallback warning.
  Reused the construction outcomes without new production paths or dependencies.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --namespace value-type.controlled-field-capability-test`:
  exit 0, 2 tests / 38 assertions, empty `.scry-results`. Log:
  `/tmp/task017-controlled-receiving.log`. Scoped cljfmt and clj-kondo pass with
  zero errors/warnings. No internal exports.
- This verifies transferred expansion evaluation, not source-free AOT loading.
  Next slice must build and consume fixed generated artifacts without source;
  keep the approved override local to isolated processes where required.

## 2026-10-27 — Controlled native absence: source-free Clojure AOT

- A strict producer compiles a public Long constraint inside the approved accessor
  override. After source deletion, isolated consumers load the saved owner under
  all policies, normally and with the interpreter. Final Long storage, value
  representation, absent native annotations and all three constructor outcomes
  remain fixed without fallback warnings. No production or dependency changes.
- Ran `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure
  -M:fallback-test --namespace value-type.controlled-field-capability-test`:
  exit 0, 3 tests / 60 assertions, empty `.scry-results`. Log:
  `/tmp/task017-controlled-aot.log`. Scoped cljfmt and clj-kondo pass with zero
  errors/warnings; `git diff --check` passes. Child JVMs use preview, no exports.
- Next: separate Java AOT consumers under controlled absence remain open. This
  fixture reuses constructor outcomes but loads saved classes instead of expanding
  declarations; it does not prove Java consumption or establish new JVM support.

## 2026-10-27 — Controlled native absence: separate Java AOT

- Added a separately compiled Java consumer of the public generated Long artifact.
  Source is deleted before Java compilation and excluded from consumer classpaths.
  Direct Java constructors retain values and reject null/wrong types; reflection
  confirms final Long storage, value representation and no native annotation.
  The approved accessor override surrounds Java invocation in each isolated child.
  No production or dependency changes; the design contract is unchanged.
- Verification with `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`:
  `clojure -M:fallback-test --namespace value-type.controlled-field-capability-test`
  exits 0: 4 tests / 84 assertions, empty `.scry-results`. The new focused var
  passes 24 assertions. Logs: `/tmp/task017-controlled-java-suite.log` and
  `/tmp/task017-controlled-java.log`. Scoped cljfmt, clj-kondo and diff checks pass.
  Children cover all policies and normal/interpreter execution without exports.
- Next: reconcile the combined controlled-capability acceptance items, then address
  exact-Class and public failure-propagation gaps. Java direct access does not load
  Clojure namespace policy guards; existing Clojure AOT checks cover that boundary.
  This controlled experiment does not establish support for another JVM.

## 2026-10-27 — Controlled-capability acceptance reconciliation

- Audited all four controlled tests against the approved exception and AOT evidence.
  Closed the controlled-absence and combined incompatible/native-requirement items;
  the separate selection matrix item stays open pending its full reconciliation.
  Added a focused audit and synchronized current design/plan/acceptance references.
  No code, dependency or contract changes; existing tests cover this slice.
- Reran the complete controlled namespace with the documented EA3 JAVA_CMD and
  `clojure -M:fallback-test --namespace value-type.controlled-field-capability-test`:
  exit 0, 4 tests / 84 assertions, empty `.scry-results`.
  Log: `/tmp/task017-controlled-audit.log`.
- Next: successful isolated sibling-loader compilation and public failure propagation
  remain open. Do not infer all receiving analysis paths from this eval-only
  controlled transfer fixture, or new JVM support from the accessor override.

## 2026-10-27 — Controlled receiving analysis paths

- Extended transferred public expansions to function analysis and metadata-preserving
  source loading under every policy and execution mode. Only the approved capability
  accessor is replaced. No production or dependency changes; design.md remains valid.
- Each expansion now runs in a separate consumer. The initial shared consumer failed
  function-path field reads after same-name owner redefinition (IllegalArgumentException).
  Isolation removes that unrelated redefinition from this boundary test; it does not
  prove same-name redefinition correctness.
- With the documented EA3 JAVA_CMD, `clojure -M:fallback-test --namespace
  value-type.controlled-field-capability-test` exits 0: 4 tests / 174 assertions,
  no failures/errors and empty `.scry-results`. Log: /tmp/task017-controlled-paths.log.
- Next: exact-Class isolation and public failure propagation remain open. The observed
  repeated function-definition case needs a separate focused check before it can be
  classified as a fixture limit or production defect.

## 2026-10-27 — Repeated function definition diagnosis

- Ordinary deftype reproduces the stale namespace Class import after a second
  function-contained definition. Both factories construct their new Class. The
  earlier field/instance mismatch came from reflecting with the stale import,
  not lost generated storage. Added a real compilation control for ordinary and
  controlled generated definitions; each instance retains its value and field type.
  Only the previously approved capability accessor is replaced. No production or
  dependency changes; the design contract is unchanged.
- With the documented EA3 JAVA_CMD, `clojure -M:fallback-test --namespace
  value-type.function-redefinition-test` exits 0: 1 test / 18 assertions, empty
  `.scry-results`. Children cover all policies, normal/interpreter execution and
  preview without exports. Log: /tmp/task017-function-redefinition.log. Scoped
  cljfmt and clj-kondo pass with zero errors/warnings; diff checks pass.
- Next: successful isolated sibling compilation, cross-process child replacement
  and public failure propagation remain open. This diagnosis does not establish
  that namespace imports change during function invocation; the test uses actual
  instance Classes and keeps independent transfer consumers isolated.

## 2026-10-27 — Silent public loader mismatch failure

- Captured stderr around real receiving analysis of stale sibling imports. Both
  public expansion entry points and all three analysis paths retain the structured
  mismatch error without warnings or owner/factory installation. No mocks,
  production changes or dependencies. Reviewed design.md; no contract change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to the documented Java 8 and EA3 paths, normally and with `-J-Xint`.
  All four exits were 0: 3 tests / 112 assertions; checked empty `.scry-results`
  after each. No preview or exports. Scoped cljfmt and clj-kondo pass.
- Next: this checks stderr during receiving analysis, not selection-time warnings
  or every-policy unexpected probe/transform failures. Successful isolated sibling
  compilation and cross-process child replacement remain open.

## 2026-10-27 — Repeated public loader rejection

- Reanalyze the same stale sibling-import expansion twice before correcting its
  import. Both attempts preserve structured mismatch errors, empty stderr and absent
  owner/factory bindings. Existing direct, factory and reflective recovery checks
  still pass. No mocks, production changes or dependencies. Reviewed design.md;
  its exact-Class and visible-failure contract needs no change.
- Ran `clojure -M:fallback-test --namespace value-type.identity-loader-test` with
  JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, normally and with
  `-J-Xint`. Four exits 0: 3 tests / 136 assertions each; all failure directories
  empty. No preview or exports. Logs: `/tmp/task017-repeat-rejection/`.
  Scoped cljfmt and clj-kondo pass with zero errors/warnings.
- Next: repeated rejection is not successful isolated sibling compilation or an
  all-policy probe/transform failure audit. Those acceptance gaps remain open.

## 2026-10-27 — Successful isolated sibling compilation

- Added real URLClassLoader siblings backed by temporary class files, outside the
  DynamicClassLoader shared name cache. Bind the compiler loader to each sibling
  before public expansion and receiving analysis. Both sibling owners retain the
  exact imported Class; factory, direct and reflective constructors accept their
  own child and reject nil and the other sibling. Temporary files/loaders are cleaned
  up. No mocks, production changes, dependencies or contract changes.
- `clojure -M:fallback-test --namespace value-type.isolated-sibling-loader-test`
  passes with JAVA_CMD set to `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`
  and `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, each normally and
  with `-J-Xint`: 1 test / 133 assertions per run, exits 0 and empty failure
  directories. No preview or exports. Additional run logs:
  `/tmp/task017-isolated-siblings/`. Scoped formatting and clj-kondo pass.
- Next: this closes successful baseline sibling construction, not cross-process
  child replacement, the general hierarchy audit or public failure propagation.

## 2026-10-27 — Isolated siblings in generated value owners

- Extended the real sibling fixture to assert final fields and absent annotations.
  Preview-enabled EA3 strict policy accepts these identity child tags with generated
  enforcement through both public expansion entry points and all receiving paths.
  No mocks, production changes, dependencies or contract changes.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -J--enable-preview -J-Dvalue-type.fallback=error -M:fallback-test
  --namespace value-type.isolated-sibling-loader-test`, then repeated with `-J-Xint`.
  Both exits were 0: 1 test / 157 assertions, empty `.scry-results`. Logs:
  `/tmp/task017-sibling-value/`. Java 8 silent normal regression also exits 0 with
  157 assertions and empty failure results. No internal exports. Scoped cljfmt
  and clj-kondo pass with zero errors/warnings.
- Next: cross-process child replacement and general hierarchy/failure audits remain
  open. This fixture uses identity child Classes, not native value child siblings.

## 2026-10-27 — Cross-process receiver child replacement

- Added a real producer/consumer fixture. Producer and receiver define the same
  child name with different fields; transferred parent storage resolves to the
  receiver Class. Factory, direct and reflective constructors retain that child
  and reject nil/wrong types. Both expansion entries and eval/function/source
  receiving paths pass. No mocks, production changes or dependencies.
- With the documented EA3 JAVA_CMD, `clojure -M:fallback-test --namespace
  value-type.child-transfer-test` exits 0: 1 test / 109 assertions; failure results
  are empty. Children cover Java 8 and EA3 preview off/on, normal/interpreter,
  silent policy. Log: /tmp/task017-child-transfer.log. Scoped cljfmt and clj-kondo
  pass. An initial delimiter error was repaired with clj-paren-repair before runs.
- This tests baseline-selected identity output, not value-selected replacement or
  all policies. Keep the combined exact-Class item open and reconcile those limits
  next. The design contract is unchanged.

## 2026-10-27 — Value-selected cross-process child replacement

- Added strict EA3 production with independent all-policy receivers. The child is
  an identity deftype with a different receiver shape, so parent value representation
  and generated enforcement are checked separately. Both expansions and all three
  analysis paths retain final exact child storage without native annotations and
  enforce direct/factory/reflective constructor checks. No mocks, dependencies,
  production changes or design-contract changes.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:fallback-test --namespace value-type.child-transfer-test`: exit 0,
  2 tests / 219 assertions, empty `.scry-results`. Children explicitly cover normal
  and interpreter execution without exports. Log: `/tmp/task017-value-child-transfer.log`.
  Scoped cljfmt and clj-kondo pass with zero errors/warnings.
- Next: this is generated value-parent transfer, not native child transfer. Baseline
  warn/strict replacement and the combined exact-Class audit remain open.

## 2026-10-27 — Native cross-process child replacement

- Extended the shared transfer fixture with real public value child declarations.
  Producer and receiver use different child shapes. Native parent storage resolves
  to the receiver Class, remains final and retains NullRestricted. All constructor
  entries retain valid children and reject nil/wrong types. No mocks, dependencies
  or production changes; the design contract is unchanged.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`,
  `clojure -M:fallback-test --namespace value-type.child-transfer-test` exits 0:
  3 tests / 329 assertions, empty `.scry-results`. Children explicitly cover all
  policies, both expansion entries and all receiving paths in normal/interpreter
  execution without exports. Log: `/tmp/task017-native-child-transfer.log`.
- Next: reconcile baseline warn/strict child replacement and the exact-Class audit.
  Native transfer does not establish native sibling-loader construction.

## 2026-10-27 — Warn cross-process child replacement

- Extended the baseline-selected transfer fixture to warn alongside silent. Each
  isolated receiver emits one fallback warning while all three constructor entries
  retain the receiver-local child and reject nil/wrong types. Both expansions and
  eval/function/source paths run on Java 8 and EA3 preview off/on, normally and with
  the interpreter. No mocks, production changes, dependencies or contract changes.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:fallback-test --var
  value-type.child-transfer-test/receiver-child-replacement-test`: exit 0,
  1 test / 217 assertions, empty `.scry-results`. Log: /tmp/task017-child-warn.log.
  Scoped cljfmt and clj-kondo pass with zero errors/warnings; diff checks pass.
- Next: strict receiving rejection remains distinct from successful warn construction.
  The combined exact-Class audit and public failure-propagation gaps remain open.

## 2026-10-27 — Strict cross-process child replacement

- Added real isolated receiving rejection after replacement of the producer child.
  Both public expansions reject twice through eval, function analysis and source
  loading. The replacement child remains installed; parent and factory stay absent
  with empty stderr. No mocks, dependencies or production changes. Reviewed design.md;
  its fixed-representation and strict-policy contracts need no change.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:fallback-test --var
  value-type.child-transfer-test/strict-receiver-child-replacement-test`: exit 0,
  1 test / 109 assertions, empty `.scry-results`. Real children cover Java 8 and
  EA3 preview off/on, normal/interpreter execution without exports. Log:
  `/tmp/task017-child-strict.log`. Scoped cljfmt, clj-kondo and diff checks pass.
- Next: reconcile the combined exact-Class audit and then public failure propagation.
  Strict rejection is not successful construction and does not close those items.

## 2026-10-27 — Exact-Class acceptance reconciliation

- Added exact-class-audit.md and closed the focused Class identity item plus both
  transfer reconciliation follow-ups. Real receiving, redefinition, isolated sibling
  and cross-process replacement tests cover the stated boundaries. No production,
  test, dependency or contract changes were needed. Native value-child siblings
  and every-policy loader combinations remain limits of the separate matrix audit.
- With JAVA_CMD set to `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, ran
  `clojure -M:fallback-test --namespace value-type.child-transfer-test`: exit 0,
  4 tests / 546 passing assertions, empty `.scry-results`. Real child JVMs cover
  the documented policies and normal/interpreter paths without exports. Log:
  `/tmp/task017-exact-class-audit.log`.
- Next: audit public failure propagation, general hierarchy/unsupported shapes and
  the complete matrix. Strict baseline transfer is rejection evidence, not successful
  construction. Do not reopen resolved Class identity gaps from older progress notes.
- Documentation verification: `bb vale` exits 0 with an empty alert map;
  `git diff --check` passes.

## 2026-10-27 — Failure-propagation evidence audit

- Added failure-propagation-audit.md with explicit helper/public boundaries and
  concrete follow-ups. Source inspection found narrow capability catches and no
  weaker-output retry in capture; target LinkageError is intentionally wrapped
  with its cause. This is not public fault-execution evidence. Reviewed design.md;
  no contract, production, dependency or test changes are needed for this audit.
- EA3 preview-disabled host: malformed probe control passes 1 test / 18 assertions;
  identity backend/loader namespaces pass 11 tests / 231 assertions. Both CLI
  exits are 0 with empty failure directories. Commands, logs and child runtime
  limits are in the audit. Var selectors take precedence over namespace selectors;
  the namespaces were therefore run separately.
- Next: fresh-policy public loader rejection and real capture failure propagation.
  Keep strict policy rejection separate from a reached loader failure. The combined
  failure-propagation item remains open; do not replace it with helper coverage.

## 2026-10-27 — Fresh-policy public loader rejection

- Added isolated execution of the real stale-import rejection/recovery test under
  baseline silent/warn and preview-enabled silent/warn/error, normal/interpreter.
  Strict baseline is excluded because policy rejects before the loader boundary.
  No mocks, production changes or dependencies. Reviewed design.md; unchanged.
- Documented EA3 JAVA_CMD with `clojure -M:fallback-test --namespace
  value-type.loader-policy-test` exits 0: 1 test / 42 assertions; failure results
  empty. Log: /tmp/task017-loader-policy.log. Scoped cljfmt and clj-kondo pass.
- Next: real capture failures and remaining public probe/hierarchy/matrix evidence.
  This is exact-Class rejection, not malformed bytecode or probe failure coverage.

## 2026-10-27 — Unsupported identity capture input

- Added a narrow real-byte capture test for missing selected storage. Repeated
  rejection retains `:unsupported-shape` and class lookup fails after each attempt.
  This bypasses public selection deliberately; do not count it as public or
  value-backend evidence. No production, API or dependency changes. Reviewed
  design.md; the contract remains unchanged.
- Six focused CLI runs pass (1 test / 5 assertions each): Java 8 and EA3 preview
  off/on, normal/interpreter. Exit codes are 0; failure directories are empty.
  Scoped formatting/lint pass. Commands and limits are in the failure audit.
- Next: value capture failure and public reachable-shape reconciliation. The
  combined failure-propagation item remains open.

## 2026-10-27 — User acceptance and bounded completion

The user instructed: "accept as sufficient. create the blocker list". Accepted
existing combined evidence for criteria 1–8 and delivered documentation. Replaced
the accumulated plan/checklist with a completion plan and mapped the accepted
criteria in acceptance-audit.md. Git preserves the previous checklist and plan.

blockers.md is now the authoritative remaining-work list. No demonstrated
functional defect is recorded as a blocker. B1 requires final delivered-tree
regression and quality validation; no tests were run in this documentation update.
Historical audit follow-ups and exhaustive permutations do not block completion.
A concrete failed check or demonstrated contract defect must be identified before
further implementation work is added. After B1 passes, report completion and close.

## 2026-10-28 — Final validation slice

- Tested unchanged code/test revision `3d19f26fc77bf9ca958fb54cafe4a0b19552ccbd`.
  Design reviewed; no contract, production, dependency or test changes were needed.
  Existing real integration tests cover this validation slice.
- JAVA_CMD was `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`.
  `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test
  -m scry.cli --dir test/runtime` passes normally and with `-J-Xint`: each exit 0,
  116 tests / 4372 passing assertions. Initial runs omitted the documented array
  export and exited 1; preserved EDN identifies array access denial. Corrected
  commands pass without a code repair. Non-nil fields do not require this export.
- `clojure -Srepro -M:build-test -m scry.cli --dir test/build`: exit 0,
  9 tests / 110 assertions. With `-M:release:release-test --dir test/release`:
  exit 0, 12 tests / 123 assertions. With `-M:quickdoc:tooling-test
  --dir test/tooling`: exit 0, 11 tests / 217 assertions. Each uses `-m scry.cli`.
  Copied empty failure directories after each run, before the next run.
- `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale`, `bb api-docs --check`
  all exit 0. Logs and copied Scry results are in `/tmp/task017-final/`.
- Java 8 `clojure -M:fallback-test` reports 118 tests / 7727 passing assertions
  and an empty failure directory. Its serial launcher was stopped before process
  completion; the test process completed, but its exit code was not retained.
  Do not count this as final CLI verification. No test processes were left running.
- Next: run exit-verified compatibility suites on Java 8 and EA3 preview off/on.
  B1 remains open. Reuse the passing checks above because subsequent edits are
  documentation-only; rerun documentation checks. No human blocker was found.


## 2026-10-28 — Final compatibility validation and completion

- Tested HEAD `b6348d83f73d59125620a00af929df67c95c145c`; code/tests remain
  unchanged from `3d19f26fc77bf9ca958fb54cafe4a0b19552ccbd`. Reused the prior
  runtime normal/interpreter, build, release, tooling and quality results.
  Reviewed design.md; no contract change, implementation repair or new test needed.
- Ran `clojure -M:fallback-test` with JAVA_CMD set first to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`, then
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`. Both exit 0: 118 tests /
  7727 assertions each. Ran `clojure -J--enable-preview -M:fallback-test` with
  the EA3 JAVA_CMD: exit 0, 118 tests / 7706 assertions. No internal exports.
- Copied each empty `.scry-results` directory before the next run. Logs, exit-code
  files and copied results are in `/tmp/task017-finish/`, named java8, ea3-off
  and ea3-on. These final process exits replace the earlier unretained Java 8 exit.
- B1 is complete. Close the task without reopening historical exploratory gaps.
  Accepted evidence limits remain unchanged; no additional JVM support is claimed.
- After completion edits, `bb vale` exits 0 with an empty alert map; staged and
  unstaged `git diff --check` pass. No source or test files changed in this pass.

## Implementation review

- Added 5 review follow-up steps. Task is in `munera/closed/`, not `munera/open/`; left in place.

## 2026-10-28 — Review follow-ups

- Addressed 5 review steps. Removed `core/native-field-deftype`, its API-doc
  override and the 3-arity `fields/specifications`; tests use the 4-arity form.
  `capability/value-predicate` and `capability/value-class?` are the one cached
  `Class::isValue` lookup, used by core, fields, array allocation and probes.
  Array inspection keeps its signature-checking lookup.
- `loader/define-value-class` renamed to `define-target-class`; `:jvm-rejected`
  message now says "Cannot define class". Messages say "non-nil" instead of "nil-restricted".
- `identity-backend/rewrite`: `proxy-super` reflected, so overrides call the
  next visitor (`writer`/`delegate`) directly. ASM super methods only forward.
  `*warn-on-reflection*` is set in capability, fields, runtime, field-capability
  and identity-backend; loading gives no warnings.
- Verified: runtime suite (EA3, preview, jdk.internal.value export) 116 tests /
  4372 assertions, exit 0. Compatibility: Java 8 and EA3 preview-off 7731 assertions,
  EA3 preview-on 7710; all pass. Build 110 and tooling suites pass; fmt, kondo,
  api-docs --check, diff --check pass. The bb vale sync step failed on a network
  timeout; vale on the prose paths found no alerts.

## Implementation review (2)

- Added 3 review follow-up steps.

## 2026-10-28 — Review follow-ups (2)

- Addressed 3 review steps. Removed the enforcement check from
  `identity-backend/prepare`; `core/identity-field-deftype` is its only guarded
  caller and throws `:invalid-field-enforcement`. The `defvaluetype` Errors
  section lists `:value-capability-unavailable` and `:invalid-field-enforcement`;
  doc/API.md regenerated. `value-backend` sets `*warn-on-reflection*`; `runtime`
  aliases `field-capability` as `field-capability`.
- Verified: runtime suite (EA3, preview, export) 116 tests / 4372 assertions, no
  reflection warnings. Compatibility: Java 8 and EA3 preview-off 7731 assertions,
  EA3 preview-on 7710; all pass. fmt, kondo, api-docs --check, diff --check pass.

## Implementation review (3)

- Added 3 review follow-up steps.

## 2026-10-28 — Review follow-ups (3)

- Addressed 3 review steps. `capability/probe-bytes` (descriptor, restricted?)
  and `capability/latest-major-version` are the one probe emitter and
  major-version lookup; `field-capability` uses them. `fields/select-enforcement`
  derives native modes from validated specifications; `defvaluetype` resolves
  each tag once, before the native field probe. The API-doc test checks
  `value-field-deftype` and `identity-field-deftype`. doc/API.md regenerated
  (source line link).
- Verified: runtime suite (EA3, preview, export) 116 tests / 4372 assertions.
  Compatibility: Java 8 and EA3 7731 assertions each; tooling 218 assertions;
  all pass. fmt, kondo, api-docs --check, diff --check pass.

## Implementation review (4)

- Added 2 review follow-up steps.

- Addressed 2 review steps. `fields/specifications` takes no capability argument
  and returns `:generated` specs; `core` and `receiving-specifications` call it
  without `false`. Tests that check native selection call `select-enforcement`;
  error and disabled-field tests drop the capability loop (13 fewer assertions).
  `capability/required-apis` returns only `:predicate`.
- Verified: runtime suite (EA3, preview, export) 116 tests / 4372 assertions.
  Compatibility: Java 8 and EA3 118 tests / 7718 assertions each. fmt, kondo,
  api-docs --check, diff --check pass.

## Implementation review (5)

- Added 2 review follow-up steps.

- Addressed 2 review steps. `field-storage/emit!` has only the 4-arity; its
  docstring describes `native?`. `runtime.md` shows the 4-arity `->value-class`
  with `generated-fields`, the 3-arity `install!` with representation, the
  `:enforcement` key, and the delegate path through `value-backend/prepare`.
- Verified: runtime suite (EA3, preview, export) 116 tests / 4372 assertions.
  fmt, kondo, api-docs --check, diff --check pass. Plain vale on `runtime.md`
  gives the same 7 alerts before and after the change. `bb vale` failed at sync.

## Implementation review (6)

- Added 3 review follow-up steps.

- Addressed 3 review steps. CHANGELOG describes `:non-nil`, identity fallback
  with generated checks, the removed `:nil-restricted` key and links
  `#portable-non-nil-fields`. `defvaluetype` probes native field capability only
  when a validated `:type` is a value class (`native-probe-scope-test`).
  `field-storage/emit!` omits an empty `RuntimeVisibleAnnotations` attribute
  (`generated-storage-without-annotations-has-no-attribute`). Both new tests fail
  without the fix.
- Verified: runtime (EA3, preview, export) 118 tests / 4378 assertions;
  compatibility Java 8 and EA3 118 tests / 7718 assertions each; tooling 218;
  release 123. fmt, kondo, diff --check pass; api-docs --check passes with
  `JAVA_CMD` set to EA3.

## Implementation review (7)

- Added 2 review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

- Addressed 2 review steps. `native-probe-scope-test` has no stub. It runs in a
  fresh child JVM and checks `realized?` of `runtime/detected-field-capability`:
  false after an invalid definition and a String-tagged `:non-nil` field, true
  after a value-class-tagged field. The `defvaluetype` Errors list names
  `:field-capability-unavailable` as a receiving-boundary error; local prose no
  longer implies it. doc/API.md regenerated.
- Verified: runtime (EA3, export) 118 tests / 4376 assertions. fmt, kondo,
  api-docs --check, diff --check pass.

## Implementation review (8)

- Added 2 review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (8) addressed

- Addressed 2 review steps.
- `value-field-deftype` checks value capability before `receiving-specifications`.
  Transferred value expansions on JVMs without value classes now report
  `:value-capability-unavailable`. Updated Long and mixed expectations in
  `field_transfer_test` (the mixed test had the same cause).
- `defvaluetype` Errors list has `:invalid-field`; the header states compiler
  wrapping. Removed the unclear prose sentence. doc/API.md regenerated.
- Verified: runtime (EA3, export) 118 tests / 4376 assertions. fallback-test
  Java 8 118/7718, EA3 off 118/7718, EA3 on 118/7697; all exit 0. fmt, kondo,
  api-docs --check, diff --check pass.

## Implementation review (9)

- Added 2 review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (9) addressed

- Addressed 2 review steps.
- `nil-restricted-field-design/embodiment.md`: replaced "Final acceptance remains
  open" with the accepted limits; task links use `munera/closed/`; usage anchor
  is `#portable-non-nil-fields`.
- `defvaluetype-design/embodiment/runtime.md`: State names the
  `capability/value-predicate` cache. Boundaries also stated that `core`
  resolves `Class::isValue`; corrected (core uses `capability/value-class?`).
- `transform/->value-class` docstring: storage-types give concrete storage;
  generated-fields get `requireNonNull` checks and no native annotation.
- Verified: fmt, kondo, api-docs --check, diff --check pass.

## Implementation review (10)

- Added 2 review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (10) addressed

- Addressed 2 review steps.
- `identity-field-deftype` checks that `modes` is a vector of `:generated` before
  `receiving-specifications`. `[:native]` with String or identity-class tags gives
  `:invalid-field-enforcement`. Removed the later redundant check. Added
  `identity_receiving_test/receiving-identity-native-modes-test`.
- `defvaluetype` Errors list names loader errors: `:invalid-field`
  `:field-class-mismatch`, `:jvm-rejected`, `:unexpected-constructor` (value) and
  `:unsupported-shape` (identity). `identity-backend/rewrite` errors now carry
  `:class-name`, as the Errors header states. doc/API.md regenerated.
- Verified: runtime (EA3, export) 118 tests / 4376, exit 0. fallback-test Java 8
  119/7723 exit 0; EA3 off 119/7723, EA3 on 119/7702, 0 failures/errors.
  fmt, kondo, api-docs --check pass.

## Implementation review (11)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (11) addressed

- Addressed 1 review step. Task is in `munera/closed/`, not `munera/open/`.
- `identity-backend/rewrite`: each `unsupported!` data map is on its own line.
  Every `unsupported!` line is ≤ 90 columns.
- Verified: runtime (EA3, export) 118 tests / 4376, exit 0. fallback-test Java 8
  119/7723 exit 0. fmt, kondo, diff --check pass.

## Implementation review (12)

- No new follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups addressed

- Addressed 2 test review steps. Task is in `munera/closed/`, not `munera/open/`.
- New `identity_loader_checks.clj`: `public-stale-sibling` returns data per
  [expansion analysis] case; `public-stale-sibling-expected` is the contract.
  `identity_loader_test` and each `loader_policy_test` child JVM assert this data.
  The child JVMs do not run tests through `scry.core/run`.
- `transform/->value-class` has only the 4-arity. Tests pass `{}`, `nil`, `#{}`
  explicitly. Docstring and `ramora/.../embodiment/runtime.md` updated. Spike code
  is not changed.
- Verified: runtime (EA3, export) 118 / 4376, exit 0. fallback-test Java 8
  summary 119 / 7674, 0 failed (background run; exit code not captured). EA3 preview identity-loader + loader-policy 4 / 129, exit 0.
  fmt, kondo, diff --check pass.

## Test review (2)

- No new follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (3)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (3) addressed

- Addressed 2 test review steps. Task is in `munera/closed/`, not `munera/open/`.
- `controlled_field_capability_test`: no `walk/postwalk-replace`. `construction-check`
  builds the child program from explicit definition forms and a factory name.
  `expected`, `controlled-absence` and `holder-definition` are defined once.
- `aot_fixture`: new `compile-clojure` and `compile-java`. `compile-java` uses
  `--release (.feature (Runtime/version))` of the child JVM. The five task AOT
  tests and `field_aot_test` use them; no test hard-codes `--release 27`.
- Verified: the 6 changed namespaces, EA3 preview and Java 8 runners: each
  31 tests / 803 assertions, exit 0. fmt, kondo pass.

## Test review (4)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (4) addressed

- Addressed 2 test review steps. Task is in `munera/closed/`, not `munera/open/`.
- New baseline-safe `function_aot_checks`: `invoke-install` returns source absence,
  loading output and, per invocation, Holder facts (value?, field type, annotations,
  value, rejection classes) or the install failure class. Inspection failures
  propagate. `invoke-install-form` builds the child program; `install-result` builds
  the expected map. The four function AOT tests use them; no `assert` in child
  programs; no single-element runtime `doseq`. Invalid inputs are printable data
  (`42`, `"wrong"`, `:wrong`), not `(Object.)`.
- `controlled-native-absence-aot-test`: one `saved-definition`.
- Verified: the 4 changed namespaces on EA3 preview, EA3 off and Java 8 runners:
  each 23 tests / 693 assertions, exit 0. fmt, kondo pass.

## Test review (5)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (5) addressed

- Addressed 2 test review steps. Task is in `munera/closed/`, not `munera/open/`.
- `aot_fixture/reload-form`: the child evaluates the consumer form, reloads the
  fixture namespace, evaluates again and prints `{:before … :after …}` (EDN of
  the last output line of each run). No `assert` in the child. The four
  source-free reload tests compare both values with the expected result.
- `aot_fixture/compile-java` takes `:baseline? true`: no preview or release
  options. `java-public-identity-consumer` uses it with the Java 8 javac.
- Verified: the 3 changed test namespaces on EA3 preview and Java 8 runners:
  each 19 tests / 518 assertions, exit 0. fmt, kondo pass.

## Test review (6)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (6) addressed

- Addressed 2 test review steps. Task is in `munera/closed/`, not `munera/open/`.
- New baseline-safe `consumer_aot_checks`. `holder-consumer` takes a spec (source,
  ns, owner, checked fields, argument fn, valid and invalid values). It returns
  source absence, value?, checked field facts (type, final?, annotations) and, per
  direct, factory and reflective construction: stored values, compiled reads,
  Callable result, equality of two valid instances and rejection classes.
  `holder-consumer-form` builds the child form; `holder-result` and
  `string-holder-result` build the expected map. The four source-free Clojure
  consumer forms are removed; no `assert` in these child programs.
- `controlled_field_capability_test`: `saved-definition` has no `assert`.
  `construction-check :source` adds `:source-absent?` to each result; `run-check`
  compares the full expected result vector.
- `aot_fixture/java-consumer-form` (spec, consumer class, optional `:requires`
  and `:wrap`) prints `consumer-aot-checks/java-consumer`: source absence, value?,
  first checked field annotations and `:checks` (`:passed` or failure class and
  message). The seven namespaces use it and compare the data in the parent.
  `:requires` is necessary: `with-redefs` of a var compiles only after its
  namespace is loaded by a preceding top-level form.
- Verified: the 7 changed namespaces on EA3 preview, EA3 off and Java 8 runners:
  each 33 tests / 846 assertions, exit 0. fmt, kondo pass.

## Test review (7)

- Added 3 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (7) — addressed

- `field_aot_test/source-free-consumer` is a compiled let form that returns
  source absence and, per construction entry, reads, reflective field value,
  Supplier results and rejections; the leaf is reported as `:leaf`. The loading
  and separately compiled tests compare it with `source-free-result`. Producer
  and consumer source absence are returned data, not `assert`.
- `consumer-aot-checks/incompatible-consumer` (source absence and two load
  outcomes) replaces both incompatible consumer copies; five tests use it. No
  test requires `generated-value-aot-test`. `strict-public-identity-aot-test`
  returns `:source-absent?` with its `:loads`.
- One `value-class?`, `outcome` and `annotation-names` in `consumer_aot_checks`;
  `function_aot_checks` uses them. `java-consumer-form` moved next to
  `java-consumer`. All form builders return forms; callers `pr-str`.
- Verified: the 7 changed namespaces on EA3 preview, EA3 off and Java 8 runners:
  each 33 tests / 853 assertions, exit 0. fmt, kondo pass.
- addressed 3 review steps

## Test review (8)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (8) — addressed

- `aot_fixture/compile-producer` (jvm, with-fixture paths, ns, optional
  `:wrap`/`:after`) compiles, asserts exit 0, empty stderr and `:compiled`
  (with `:after` the caller checks `:result`), deletes the source, asserts it is
  absent, and returns the result only on success. All producer compiles in the
  7 namespaces use it, including the `identity_aot` hand-written compile and the
  separately compiled `restricted.consumer`. `classpath-with` is public and
  replaces the repeated classpath `str`. `identity_aot` uses
  `compile-java :baseline? true`.
- `consumer-aot-checks/strict-consumer` replaces the strict string program; it
  returns source absence and per load the root class, message and
  `:factory-bound?`. `failure` returns root class and message;
  `fallback-rejection` is the fixed fallback Error. The first strict load and the
  `"error"` install invocations of `function-public-identity-aot-test` compare
  against it.
- Verified: the 7 namespaces on EA3 preview, EA3 off and Java 8 runners: each
  33 tests / 899 assertions, exit 0. fmt, kondo pass.
- addressed 2 review steps

## Test review (9)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (9) — addressed

- Put each `classpath` binding on its own line in the 5 listed `let` forms.
  Whitespace-only change. fmt and kondo pass.
- addressed 1 review step

## Test review (10)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (10) — addressed

- New `field_policy_checks`: `expansion` and `evaluation` replace the string
  programs of the two `field_capability_test` policy tests. They reuse
  `consumer_aot_checks/builders` (now public), `outcome` and `failure`; they
  return the capability `:detail` and two attempts. A strict attempt is the root
  class and message; the parent compares it with
  `consumer-aot-checks/policy-rejection` of the detail (`fallback-rejection`
  now uses it). `public-field-policy-test` has `testing` per configuration and policy.
- `expansion_checks`: one `on-thread` runner (bound/unbound `LOADER`) serves
  `warning-timing`, both `check-expansions` arities and
  `constrained-discarded-with-loader`. It returns the observed
  `:loader-bound?`; the `assert`s are removed and `runtime_test` checks it.
  One `root` helper replaces the two root-cause loops. `constrained-discarded`
  returns `:detail` and per attempt the root class and message; `runtime_test`
  asserts the policy rejection message.
- Verified: `field-capability-test` and `runtime-test` on Java 8, EA3 off and
  EA3 preview runners: exit 0, 16 tests each (394/394/366 assertions). fmt, kondo pass.
- addressed 2 review steps

## Test review (11)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (11) — addressed

- `field_transfer_checks/attempt` returns `checks/failure` (root class and
  message) with `:data` and `:absent?`. The strict transfer test asserts
  `fallback-rejection`; the unsupported tests assert the ExceptionInfo message.
- New `identity_receiving_checks`: `receiving-policy`, `incompatible-value`,
  `generated-value`, `strict-nested` and `nested-warning-scope` replace the five
  string programs. They evaluate in their own namespace (binary names are
  munged: `value_type.identity_receiving_checks.*`) and reuse `builders`,
  `outcome`, `failure`, `annotation-names` and `value-class?`. Strict results
  are compared with `fallback-rejection` (fixed identity output) or
  `policy-rejection` of the returned capability detail (nested public
  declaration). `nested-warning-scope` returns field type, stored class,
  optional value and per-construction rejection classes.
- Verified: `identity-receiving-test` and `field-transfer-test` on EA3 preview,
  EA3 off and Java 8 runners: exit 0, 14 tests / 1533 assertions each. fmt, kondo pass.
- addressed 2 review steps

## Test review (12)

- Added 2 test review follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (12)

- `warning_checks/concurrent-rejections` and `child_transfer_checks/reject-replaced-child`
  return `:outcome` (`:accepted` or `consumer-aot-checks/failure`); tests compare
  with `fallback-rejection`.
- New `child_transfer_checks.clj` holds `produce`, `consume` and
  `reject-replaced-child`; child JVMs no longer require the test namespace.
  `consume` returns child field names, `value?`, `final?`, annotation names and
  per-construction `outcome` rejections. The value and native receiver tests
  share one helper; native annotations are exactly `NullRestricted`.
- Verified: `child-transfer-test` and `transfer-test` on EA3 preview, EA3 off and
  Java 8 runners: exit 0, 8 tests / 631 assertions each. fmt, kondo pass.
- addressed 2 review steps

## Implementation review (13)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (13)

- `spike/portable-fields/verify.sh` now runs in a temporary detached git worktree
  at `6318c36` and removes it on exit. Header comment and
  `ramora/nil-restricted-field-design/embodiment.md` state that the experiment
  does not run against the current tree.
- Verified: `verify.sh` exits 0 (baseline, EA3, fresh AOT consumer, fmt, kondo,
  EA3 `-Xmixed`/`-Xint` value experiment: 22 assertions each).
- addressed 1 review step

## Implementation review (14)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (14)

- Rewrote `mementum/state.md` (5471 → 604 words): current behavior, evidence
  limits, links to acceptance audit and implementation record. Removed stale
  "remains open" statements.
- addressed 1 review step

## Implementation review (15)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (15)

- Enabled-field `:not-installed` at macroexpansion: documented in the
  `defvaluetype` docstring, `doc/usage.md` and `doc/API.md`. Added
  `not-installed-enabled-field-test` (raw thread, `macroexpand-all`, no stub).
  Verified on JDK 25 (identity receiving path). Not run on Valhalla here (no
  Valhalla JDK available); the value receiving path throws the same error
  from the same `install!` check.
- addressed 1 review step

## Implementation review (16)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (16)

- Receiving-boundary messages in `core` now name the class and the fix.
  `direct-fix` text is shared by `error-message`, both `:not-installed`
  messages and `:invalid-field-enforcement`.
- `:value-capability-unavailable` uses `no-value-classes-message` only when
  `Class::isValue` is missing. Valhalla without preview has `Class::isValue`,
  so that text would be false there; it gives "Use --enable-preview." instead.
  Shared expectation: `consumer-aot-checks/unavailable-message`.
- Message assertions added: `core-test/not-installed-enabled-field-test`,
  `identity-receiving-test/receiving-identity-native-modes-test`.
- Verified: runtime suite on EA3 (119 tests, 0 failures); the 4 affected
  compatibility tests on a Java 8 host (205 assertions, 0 failures); fmt, kondo.
- addressed 1 review step

## Implementation review (17)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (17)

- `value-field-deftype` reads `:reason` and `:detail` from `(runtime/capability)`.
  Message: `no-value-classes-message` for `:missing-value-predicate`, else
  `:detail` (as `enforce-fallback!`). Ex-data adds `:reason`. On Java 8 the
  reason is `:missing-classfile-api`, so the message gives the detail.
- `consumer-aot-checks/unavailable-message` replaced by `unavailable-failure`
  (class, message, data from a capability). `field-transfer-checks/consume`
  and `identity-receiving-checks/incompatible-value` now report `:capability`.
- Verified: runtime suite on EA3 (119 tests, 0 failures); the 3 affected
  compatibility tests on a Java 8 host (207 assertions, 0 failures); fmt, kondo.
- addressed 1 review step

## Implementation review (18)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (18)

- `value-field-deftype` uses `no-value-classes-message` for
  `:missing-classfile-api` and `:missing-value-predicate`; `:detail` otherwise.
  Java 8–23 lack both the ClassFile API and `Class::isValue`.
- `identity-receiving-test/incompatible-value-receiving-test` asserts the
  literal message on the baseline receiver, independent of the helper.
- Verified: runtime suite on EA3 with `--add-exports` (119 tests, 0 failures);
  the 3 affected compatibility tests on a Java 8 host (210 assertions,
  0 failures); fmt, kondo.
- addressed 1 review step

## Implementation review (19)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (19)

- `bb api-docs` regenerated doc/API.md (source link `#L134-L261`).
  `bb api-docs --check` passes. No `src` change in this pass.
- addressed 1 review step

## Implementation review (20)

- Added 1 review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review follow-ups (20)

- `fields/invalid-enforcement!` (with `fields/direct-fix`) throws
  `:invalid-field-enforcement` for `receiving-specifications` and
  `identity-field-deftype`: "Cannot define class C: <expected>, got <modes>.
  Fix: evaluate the defvaluetype form directly."
- `loader/capture-loader` mismatch names class, field, type and the fix
  (redefine both in the same loader, or reload both).
- `identity-backend/rewrite` `unsupported!`: "Cannot add generated field checks
  to C: unsupported shape (<part>). ... Fix: use Clojure 1.12." Companion
  rewrites name the owner class.
- Message assertions: `fields-test`, `identity-backend-test`,
  `identity-loader-test`, `capture-failure-test`, runtime `loader-test`.
- Verified: the 5 affected compatibility namespaces on Java 8 and EA3 preview
  (42 tests, 1447 assertions, 0 failures); runtime suite on EA3 with
  `--add-exports` (119 tests, 0 failures); fmt, kondo; `bb api-docs` regenerated
  doc/API.md (`#L130-L257`), `--check` passes.
- addressed 1 review step

## Implementation review (21)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (13)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (13)

- addressed 1 review step
- Two `message-names?` copies: `test/runtime` and `test/compatibility` are on
  separate classpaths (`:test`, `:fallback-test`).
- Valhalla 27 with preview off gives `:unsupported-class-version` with a detail
  that contains "--enable-preview". The expected message parts need no branch
  on `:reason`.

## Test review (14)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (15)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review (1)

- Added 3 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (1)

- addressed 3 review steps. `bb vale` passes. The controlled-capability command passes on EA3.

## Documentation review (2)

- Added 1 documentation follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (2)

- addressed 1 review step. `bb vale` and `bb api-docs --check` pass.

## Documentation review (3)

- Added 2 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (3)

- addressed 2 review steps. `bb vale` passes.

## Documentation review (4)

- Added 3 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (4)

- addressed 3 review steps. `bb vale` and `bb api-docs --check` pass.

## Documentation review (5)

- Added 2 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (5)

- addressed 2 review steps. `bb vale` passes.

## Documentation review (6)

- Added 1 documentation follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (6)

- addressed 1 review step. `bb api-docs --check` and `bb vale` pass.

## Documentation review (7)

- Added 3 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (7)

- addressed 2 review steps; 1 partly done. `bb api-docs --check` and `bb vale` pass.
- Blocked: rename step stays open. This task's `design.md` links still name
  `ramora/nil-restricted-field-design`; `design.md` was read-only in this pass.
  Fix: replace the three links in `design.md` with `non-nil-field-design`.

## Documentation review (8)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review (1)

- Added 4 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (1)

- addressed 4 review steps. Fallback tests pass on Java 8 and EA3 (preview
  off/on); runtime tests pass on EA3. `clj-fmt`, `clj-kondo`,
  `bb api-docs --check` and `bb vale` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (2)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (2)

- Addressed 3 code shape review steps. On EA3: runtime suite (with
  exports) 120 tests and compatibility suite 119 tests pass; `bb api-docs --check`,
  `bb clj-fmt:check` and `bb clj-kondo:lint` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (3)

- Added 1 code shape follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (3)

- Addressed 1 code shape review step. On EA3: runtime suite (with exports)
  120 tests and compatibility suite 119 tests pass; `bb api-docs --check`,
  `bb clj-fmt:check` and `bb clj-kondo:lint` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (4)

- Added 2 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (4)

- Addressed 2 code shape review steps. On EA3: runtime suite (with exports)
  119 tests and compatibility suite 119 tests pass; `bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb api-docs --check` and `bb vale` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (5)

- Added 2 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (5)

- Addressed 2 code shape review steps. On EA3: runtime suite (with exports)
  119 tests and compatibility suite 119 tests pass; `bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb api-docs --check` and `bb vale` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (6)

- Added 2 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (6)

- Addressed 2 code shape review steps. On EA3: runtime suite (with exports)
  119 tests and compatibility suite 119 tests pass; `bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb api-docs --check` and `bb vale` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (7)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (7)

- Addressed 3 code shape review steps. On EA3: runtime suite (with exports)
  119 tests and compatibility suite (preview) 119 tests pass; compatibility
  suite passes on Java 8. `bb clj-fmt:check`, `bb clj-kondo:lint`,
  `bb api-docs --check` and `bb vale` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (8)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (8)

- Addressed 3 code shape review steps. Compatibility suite (no preview)
  119 tests pass on Java 8 and EA3; runtime suite (with exports) 119 tests
  pass on EA3. `bb clj-fmt:check` and `bb clj-kondo:lint` pass.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (9)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (9)

- Addressed 3 code shape review steps. On EA3: runtime suite (with exports)
  119 tests pass. On EA3 and Java 8 (no preview): compatibility namespaces
  `fields-test`, `identity-backend-test`, `identity-loader-test`,
  `field-capability-test` and `controlled-field-capability-test` pass; the
  full compatibility suite was not run. `bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb api-docs --check` and `bb vale` pass. doc/API.md
  regenerated (stale source line link).
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Code shape review (10)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (10)

- Addressed 3 code shape review steps. On EA3: runtime suite (with exports)
  119 tests and compatibility suite 119 tests pass; compatibility suite
  passes on Java 8. `bb clj-fmt:check`, `bb clj-kondo:lint`,
  `bb api-docs --check` and `bb vale` pass. doc/API.md
  regenerated (stale source line link).
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Implementation review (22)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (16)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (16)

- Addressed 1 test review step: removed the trailing blank line in
  `transform_test.clj`. `bb clj-fmt:check` passes.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Test review (17)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (17)

- Addressed 1 test review step: relabelled and completed the repeated
  definition test as controlled-capability evidence; recorded in the
  controlled-capability audit. `--namespace value-type.function-redefinition-test`
  exits 0: 1 test / 18 assertions. `bb clj-fmt:check` passes.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Test review (18)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (18)

- Addressed 1 test review step: the repeated definition test checks final
  storage and native annotations. EA3 preview `--namespace
  value-type.function-redefinition-test` exits 0: 1 test / 18 assertions.
  `bb clj-fmt:check` passes.
- Still blocked: Documentation review (7) rename step. This task's `design.md`
  links name `ramora/nil-restricted-field-design`; `design.md` is read-only.

## Test review (19)

- No new test review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (20)

- No new test review steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review (9)

- Added 2 documentation follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review follow-ups (9)

- Addressed 2 review steps. Still blocked: Documentation review (7) rename
  step; this task's `design.md` links `ramora/nil-restricted-field-design` and
  `design.md` is read-only.

## Documentation review (10)

- No new documentation review steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review (11)

- Added 2 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review follow-ups (11)

- Addressed 2 review steps. `bb clj-fmt:check`, `bb clj-kondo:lint` and
  `bb api-docs --check` pass (doc/API.md source link regenerated). Affected
  fallback namespaces pass on Java 8 and EA3 (preview off and on); runtime
  tests pass on EA3 with preview.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Code shape review (12)

- Added 3 code shape follow-up steps. Task is in `munera/closed/`, not `munera/open/`.

## Implementation review (23)

- No new review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (21)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Review follow-ups (code shape 12, test 21)

- Addressed 4 review steps. `bb clj-fmt:check`, `bb clj-kondo:lint` and
  `bb api-docs --check` pass. Compatibility suite passes on EA3; field
  namespaces pass on Java 8 and EA3 (preview off and on); runtime suite
  (with exports) passes on EA3 with preview.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Test review (22)

- Added 1 test review follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Test review follow-ups (22)

- Addressed 1 review step. `bb clj-fmt:check` and `bb clj-kondo:lint` pass.
  `fields-test` and `identity-receiving-test` pass on Java 8 and EA3
  (preview off and on).
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Test review (23)

- No new test review steps. Task is in `munera/closed/`, not `munera/open/`.

## Test review (24)

- No new test review steps. Task is in `munera/closed/`, not `munera/open/`.

## Documentation review (11)

- Added 1 documentation follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Documentation follow-up pass (11)

- addressed 1 review step: `runtime.md` `install!` comment matches code.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Documentation review (12)

- No new documentation review steps. Task is in `munera/closed/`, not `munera/open/`.

## Code shape review (13)

- Added 1 code shape follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Code shape follow-up pass (13)

- addressed 1 review step: `transform/constructor-code` tests `storage-type`
  once. Runtime suite (EA3, preview) and field transfer, mixed AOT and
  controlled-capability tests pass.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Code shape review (14)

- Added 1 code shape follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Code shape follow-up pass (14)

- addressed 1 review step: `core` shares the `:not-installed` cause text in
  one private constant. Runtime suite (EA3, preview, exports), `clj-fmt` and
  `clj-kondo` pass.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Code shape review (15)

- Added 1 code shape follow-up step. Task is in `munera/closed/`, not `munera/open/`.

## Code shape follow-up pass (15)

- addressed 1 review step: `fields` and `capability` use imported class
  names. Runtime suite (EA3, preview, exports), `clj-fmt` and `clj-kondo` pass.
- Still blocked: Documentation review (7) rename step; this task's `design.md`
  links `ramora/nil-restricted-field-design` and `design.md` is read-only.

## Code shape review (16)

- No new code shape review steps. Task is in `munera/closed/`, not `munera/open/`.

## Closure (2026-10-29)

- Documentation review (7) rename step done: `design.md` links now name
  `ramora/non-nil-field-design{.md,/decisions.md,/verification.md}`; all
  targets exist. `git grep nil-restricted-field-design` matches only task 016
  and history notes in this task. No open steps remain. The task stays in
  `munera/closed/`; `munera/plan.md` has no reference to it.
