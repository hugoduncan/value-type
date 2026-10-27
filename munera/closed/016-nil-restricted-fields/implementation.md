# Implementation notes

## 2026-10-27 — Task setup

The task follows the committed Ramora design and EA3 experiment in `3bf1c10`.
Unsupported native support remains a definition failure under every fallback
policy. The later discussion of constructor-check fallback did not change that
contract. No implementation work has started.

## 2026-10-27 — Architecture review

- No architectural review feedback. The frozen scope fits the native-field design,
  capability boundary, compiler conventions and fixed AOT representation.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context for
  later design-review turns came from `AGENTS.md`, `ramora/INDEX.md`, the
  nil-restricted field design and verification requirements, and the
  `defvaluetype` decisions, embodiment and controlled-fallback documents.

## 2026-10-27 — Ambiguity review

- No ambiguity review feedback.

## 2026-10-27 — Inconsistency review

- No inconsistency review feedback.

## 2026-10-27 — Review handoff

- Read the closing `LoadableDescriptors` warning in
  `spike/null-restricted-fields/report.md` against D5 in
  `ramora/nil-restricted-field-design.md`: the current decision requires a
  demonstrated loading need before adding that attribute. The warning alone
  does not establish a missing requirement.

## 2026-10-27 — Plan ambiguity review

- No new plan ambiguity review feedback.

## 2026-10-27 — Plan inconsistency review

- No new plan inconsistency review feedback.

## 2026-10-27 — Plan review handoff

- `test/compatibility/value_type/transfer_test.clj` produces ordinary fallback
  expansions on an incapable JVM. Reuse its process isolation, not that producer
  setup: an enabled nil-restricted declaration must fail there, so transfer tests
  need a capable producer before they can test receiving guards.

## 2026-10-27 — Slice 1: native capability

- Added baseline-safe `impl.field-capability/detect` and separate lazy
  `impl.runtime/field-capability`. No public declaration behavior changed.
  The probe uses UUID class names, concrete nested storage, strict initialization
  and the native annotation. Constructors contain no null checks. Valid values,
  field type, readback and native null rejection are required invariants.
- Only the existing value detector's missing-API/version results mean absence.
  No field-specific absence catch is justified by current evidence. Real invalid
  primitive restriction, invalid constructor slots, identity restriction and an
  unrestricted holder produce ClassFormatError, VerifyError,
  IncompatibleClassChangeError and IllegalStateException respectively.
- Fresh children verify lazy public loading and cached results on Java 8,
  preview-disabled EA3 and preview-enabled EA3. All child configurations omit
  exports. Tests use real emitted classes, not mocked capability results.
- Verification used Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla
  `27-jep401ea3+1-1`. Executables are the defaults in `jvm-config`.
  Commands from the repository root:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test`
    — exit 0; 2 tests, 8 assertions; no Scry failure files.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.runtime-test --namespace value-type.field-capability-test`
    — exit 0; 11 tests, 99 assertions; `.scry-results/` empty.
  - `bb clj-fmt:check` and `bb clj-kondo:lint` — exit 0; no lint warnings.
- Next: implement internal declaration normalization and eligibility. Do not
  enable public success until native transformation and compiler guards exist.
  Design is unchanged. Full policy, AOT and execution matrices remain in later
  slices; this probe result does not establish public field support.

## 2026-10-27 — Slice 2a: metadata normalization

- Added internal `impl.fields/restricted-fields`: returns enabled symbols in
  declaration order with metadata intact. No tag resolution, capability call,
  registry or public macro integration. Non-vectors and non-symbol entries remain
  the responsibility of `deftype`. Design is unchanged.
- Invalid metadata uses `:value-type/error :invalid-field`, `:class-name`,
  `:field`, `:reason :invalid-nil-restricted` and `:value`. The first invalid
  metadata value in field order wins. Integration must call this after existing
  mutable-field validation. Full eligibility precedence and resolved specification
  shape remain open checklist work, not a completed contract.
- Next slice: resolve explicit tags in the defining environment and validate
  eligibility. Reuse the selected symbols; do not strip their metadata or enable
  public construction before native integration.
- Verification: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  and `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  each exited 0 with 3 tests and 15 assertions; `.scry-results/` was empty.
  These use the same EA3 and Java 8 installations recorded in slice 1, without
  preview or exports. `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0.


## 2026-10-27 — Slice 2b: explicit tag resolution

- Added internal `impl.fields/resolve-tag` with an explicit defining namespace.
  It returns the resolved Class unchanged and uses Clojure compiler tag rules
  for imported names, qualified class names, Class objects, string class names,
  primitive tags and array tags. No public integration or eligibility check yet.
- Missing tags report `:missing-tag`; unresolved tags report `:unresolved-tag`.
  Both use `:value-type/error :invalid-field`, `:class-name`, `:field` and `:tag`.
  Only ClassNotFoundException is caught at this boundary; linkage errors remain
  errors. Full validation precedence and specification shape remain next work.
- A focused test exposed the separate Clojure array-tag resolution path
  (`String/1`); the resolver now follows that path as well. Primitive and array
  resolution is intentional: eligibility must reject these Classes next.
- Design is unchanged. Next slice must reject the enclosing binary name even
  when resolution finds an older definition, and test eligible external classes.
- Verification on the EA3 and Java 8 installations recorded above, without
  preview or exports:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - Both exited 0: 5 tests, 28 assertions each; `.scry-results/` empty after each.
  - `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0, without lint warnings.

## 2026-10-27 — Slice 2c: type eligibility

- Added internal `impl.fields/validate-type`, returning the exact eligible Class.
  Rejection order is enclosing binary name, primitive, array, interface, abstract,
  then non-value class. Reasons are `:enclosing-type`, `:primitive-type`,
  `:array-type`, `:interface-type`, `:abstract-type` and `:identity-type`.
  Errors retain `:value-type/error :invalid-field`, class, field and original tag.
  Lazy reflective `Class.isValue` lookup catches only NoSuchMethodException;
  native field capability remains a separate requirement.
- Real baseline Classes cover all rejection categories. A library value Class
  with nullable contents verifies acceptance and exact Class identity. Both an
  existing identity Class and value Class are rejected as enclosing replacements.
  Design is unchanged; public behavior is unchanged.
- Next: independently compiled external value fixture, ordered specifications,
  and full declaration precedence. No new dependencies or registry were added.
- Verification on the same Java 8 and EA3 builds recorded in slice 1:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - Both exited 0: 7 tests, 35 assertions each; no Scry failure files.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.field-eligibility-test`
    exited 0: 1 test, 2 assertions; no Scry failure files. Preview enabled,
    no exports. `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0.

## 2026-10-27 — Slice 2d: ordered specifications

- Added internal `impl.fields/specifications`, returning a vector of
  `{:field original-symbol :type exact-Class :descriptor JVM-descriptor}`.
  Symbols retain metadata. Disabled fields are omitted without tag resolution.
  The Java fixture compiles separately with EA3 javac and needs no registration.
- Declaration precedence is mutable-field validation (at integration), all
  restriction metadata in field order, then resolution and eligibility together
  for each enabled field in order. Error keys and reasons remain those recorded
  in slices 2a–2c; capability remains separate. Tests pin metadata-before-type
  and first-enabled-field precedence. No dependency or design change.
- Next finish the declaration regression checklist item, then connect the native
  path. This slice does not enable public restricted construction.
- Verification used the same EA3 and Java 8 builds as slice 1:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.field-eligibility-test`
    — exit 0; 2 tests, 7 assertions; no exports, empty `.scry-results/`.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
    — both exit 0; 8 tests, 39 assertions each; empty `.scry-results/`.
  - An initial reader error in the new test was repaired with `clj-paren-repair`
    before these successful runs. `bb clj-fmt:check` and `bb clj-kondo:lint`
    exited 0 with no lint warnings.

## 2026-10-27 — Slice 2e: declaration regressions

- Completed the remaining slice 2 tests. Mutable declarations retain ordered
  structured data and CompilerException → ExceptionInfo wrapping, including
  enabled, disabled and invalid restriction metadata. Disabled long and String
  fields retain long/Object storage, the original constructor signature and nil
  acceptance. Macroexpansion preserves unrelated metadata. No public integration,
  production change, dependency or design change.
- Next slice starts loader transport and native transformation. These tests do
  not establish enabled public field support; repeat validation precedence checks
  when connecting `fields/specifications` to the macro.
- Verification on the Java 8 and EA3 builds recorded in slice 1:
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.fields-test`
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J--enable-preview -M:fallback-test --namespace value-type.fields-test`
  - Each exited 0: 11 tests, 75 assertions; `.scry-results/` empty after each.
    No exports. `bb clj-fmt:check` and `bb clj-kondo:lint` passed after fixing
    formatting and an unused test expression; no lint warnings remain.

## 2026-10-27 — Slice 3a: storage emission

- Added `impl.field-storage/emit!`, an isolated class-file emitter. It changes
  the descriptor, merges the native annotation with runtime-visible annotations,
  and copies other field elements. The caller must supply strict initialization
  and rewrite constructors and accesses. It is deliberately not connected to the
  transform yet: storage-only integration would emit invalid classes.
- Byte-model tests preserve public/final flags, an existing visible annotation,
  an invisible annotation and an untouched nullable field. These tests do not
  claim native construction or complete attribute coverage. No dependencies or
  design changes. Loader transport remains the next integration action.
- Verification on the EA3 build recorded in slice 1, preview on, no exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.field-storage-test --namespace value-type.impl.transform-test`
  exited 0: 19 tests, 176 assertions; `.scry-results/` empty. Explicit List
  overloads removed initial reflection warnings. `bb clj-fmt:check` and
  `bb clj-kondo:lint` passed without warnings.

## 2026-10-27 — Slice 3b: isolated constructor casts

- Constructor emission accepts emitted field names mapped to ClassDesc values.
  It loads arguments from the original field model, casts selected references,
  then writes the concrete descriptor. The production transform still supplies
  an empty map. No public behavior, dependency or design contract changed.
- Byte-model tests verify unchanged constructor signatures, long/double slots,
  one selected cast and unchanged primitive writes. Existing constructor-shape
  rejection and ordinary round-trip tests pass. These checks do not establish
  native restricted construction. Next connect storage, constructor and access
  rewriting together; do not enable storage-only output or public success.
- Verification on the same EA3 build recorded in slice 1, preview on, no exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test`
  exited 0: 20 tests, 183 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed without warnings.
  An initial test reader error was repaired with `clj-paren-repair`; an explicit
  Consumer hint removed a reflection warning before final verification.

## 2026-10-27 — Slice 3c: isolated field-access rewriting

- Added `impl.field-access/rewrite`, a CodeTransform shared by owner and companion
  transforms. Inputs are the owner ClassDesc and emitted field-name-to-ClassDesc
  map. It changes only GETFIELD/PUTFIELD references with the matching owner,
  selected name and original Object descriptor. Already concrete references and
  unrelated references pass through. Constructor emission still owns write casts.
- Real byte-model tests cover owner and companion classes, reads, writes, nullable
  controls, another owner and empty specifications. They do not load these models
  or establish generated-class linkage. Next connect this transform with storage,
  constructor emission and loader transport. Public behavior and design are
  unchanged; no dependencies were added.
- Verification on the EA3 build recorded in slice 1, preview on, no exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.field-access-test --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test`
  exited 0: 21 tests, 187 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 without warnings.

## 2026-10-27 — Slice 3d: connected owner transformation

- `transform/->value-class` now accepts an optional emitted-name-to-ClassDesc map.
  It validates the original constructor, then composes strict initialization,
  concrete storage, casts and access rewriting in one class-file build. The
  one-argument entry point supplies an empty map. No public API or design change.
- An initial two-build implementation failed with `Unset fields mismatch`:
  strict field descriptors and constructor writes must agree before the class-file
  API generates stack maps. Composition fixes this without suppressing validation.
- Real loaded classes verify concrete reflected type, readback, protocol access,
  original Object constructor parameters, long/double slots, native null rejection
  and wrong-type casts. No mocks or internal exports. Companion loading, loader
  transport and public guards remain next work; this is not public field support.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test --namespace value-type.impl.field-access-test`
  exited 0: 22 tests, 192 assertions, no Scry failure files. Preview enabled,
  no exports. `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0, no warnings.

## 2026-10-27 — Slice 3e: loader transport

- `loader/install!` accepts ordered specifications as an optional second argument;
  its existing arity supplies an empty vector. The loader munges source field names
  and derives ClassDesc storage values from descriptors. Exact Classes remain in
  the captured specifications, without a registry. Before target definition it
  checks resolution through the delegate and reports `:invalid-field` with reason
  `:field-class-mismatch` if identity differs. Missing classes and unexpected
  loading errors remain errors. No public integration or design change.
- Real compiler-loader construction verifies a hyphenated restricted field,
  unchanged Object constructor parameters, nullable control, native null rejection
  and wrong-type casts. Existing loader cases verify ordinary definitions, AOT
  writes and errors. Adversarial class identity fixtures remain in slice 4.
- Next: rewrite generated companions and verify linkage; the loader currently
  transforms only the target. Do not enable public success before companion
  transport and capability guards are complete.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview enabled,
  no exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.loader-test --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test --namespace value-type.impl.field-access-test`
  exited 0: 33 tests, 232 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 with no warnings.

## 2026-10-27 — Slice 3f: captured companion references

- The loader now rewrites target-field references in all other captured classes
  when specifications are nonempty. These classes retain identity semantics.
  Runtime definition and AOT writes use the same rewritten bytes. Empty
  specifications keep the existing path. No dependency or design change.
- Real nested closures exercise restricted and nullable owner reads across both
  control-flow branches. The byte-model test also uses the new whole-class helper
  and retains unrelated-owner, unrelated-field and already-concrete controls.
- Next: public integration and receiving guards remain incomplete. Persisted AOT
  companions still need source-free execution in slice 4; runtime closure success
  is not evidence for that deployment contract. Exact-Class adversarial fixtures
  also remain open. The design contract is unchanged.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview on,
  without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.loader-test --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test --namespace value-type.impl.field-access-test`
  exited 0: 34 tests, 236 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed with no warnings after
  formatting the new fixture.

## 2026-10-27 — Slice 3g: composed-transform regression coverage

- Verified annotation merging through the complete transform, not only the
  isolated emitter. All five mixed fields retain their visible and invisible
  annotation attributes; unmarked primitive descriptors remain unchanged.
- The existing malformed-constructor corpus now compares ordinary and nonempty
  storage selections. Both retain the same structured errors and messages before
  emission. Together with existing loaded mixed-slot tests, this completes the
  storage and constructor integration checklist items. No production, dependency
  or design change was needed.
- Next: connect public declaration validation and capability guards. Internal
  transform coverage does not establish public support or source-free AOT support.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview on,
  without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.transform-test --namespace value-type.impl.field-storage-test --namespace value-type.impl.field-access-test --namespace value-type.impl.loader-test`
  exited 0: 35 tests, 260 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed without warnings.


## 2026-10-27 — Slice 3h: native requirement guard

- Added baseline-safe `runtime/require-field-capability!`. Unsupported capability
  throws ExceptionInfo with `:value-type/error :field-capability-unavailable`,
  `:class-name` and the detector's `:reason`. It returns nil on support and does
  not catch probe defects or apply ordinary fallback policy. No dependency or
  design change.
- Fresh JVM tests cover all nine Java 8 / EA3 preview-off / EA3 preview-on and
  silent / warn / error combinations, without exports. Each calls the guard
  twice and checks that no identity-fallback warning occurs.
- Next: place this guard at public selection and receiving phases. This internal
  test does not establish macroexpansion transfer, AOT enforcement or enabled
  public construction. The existing public checklist items remain open.
- Verification on the Java 8 and EA3 builds recorded in slice 1:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test`
  exited 0: 3 tests, 35 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0, with no warnings.


## 2026-10-27 — Slice 3i: receiving-phase guard

- Added internal `runtime/guarded-fields`: checks native support during macro
  analysis and emits the same check before its body at evaluation. It does not
  install a loader or choose representation. Public integration must retain this
  macro in both macroexpand entry-point results. No design or dependency change.
- Real children cover discarded macroexpand-1, analysis inside an uninvoked
  function, and evaluation, twice each, across all nine JVM/policy combinations.
  This establishes the internal guard, not public transfer or AOT enforcement.
- Next: connect declaration validation, selection guard and loader transport in
  the public macro while retaining receiving checks. Public integration, public
  construction and deployment tests remain open.
- Verification on Java 8 `1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1`,
  Clojure 1.12.6, without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test`
  exited 0: 4 tests, 62 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 without warnings.

## 2026-10-27 — Slice 3j: public integration

- Public macro now validates restriction metadata, requires native capability,
  resolves specifications and passes them to the capture loader. Mutable fields
  still win validation precedence; capability precedes type eligibility on
  unsupported JVMs. No dependency or design-contract change.
- Keep the receiving guard inside `let`, not top-level `do`: the latter caused
  Compiler.eval to replace the loader binding and define an identity class.
  Both macroexpand entry points retain the receiving macro. Transfer verification
  remains open; selection tests alone do not prove transfer safety.
- Public tests cover both syntax forms, both restricted positions, nested nullable
  contents, direct/factory/reflective construction, concrete reflected types and
  Object constructor signatures. The initial test reader error was fixed with
  clj-paren-repair. Usage, docstring and generated API now describe the feature.
- Verification used Clojure 1.12.6 and the Java 8/EA3 builds recorded in slice 1:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test --namespace value-type.impl.loader-test`
    — exit 0, 13 tests, 66 assertions, preview on without exports.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test --namespace value-type.fields-test`
    — exit 0, 16 tests, 164 assertions; includes nine public selection/policy
    configurations and repeated attempts. Both runs left `.scry-results/` empty.
  - `bb api-docs`, `bb api-docs --check`, `bb clj-fmt:check` and
    `bb clj-kondo:lint` exited 0, no lint warnings.
- Next: public transfer guards, mixed primitive fields and receiving/deployment
  cases. Do not infer source-free AOT or execution-matrix acceptance from this pass.

## 2026-10-27 — Slice 3k: public mixed-field verification

- Added a public fixture with restricted first/last fields, long/double arguments
  and an ordinary hinted nullable field. Direct constructors, factories and
  reflection preserve argument order, reject nil/wrong types at both restricted
  positions and retain nullable nested contents. Direct and reflective reads agree;
  storage is concrete only for restricted references and constructor references
  remain Object. No production, dependency or design-contract change was needed.
- Next: public macroexpansion transfer and receiving guards still need verification;
  these runtime checks do not establish AOT or the semantic execution matrix.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview on,
  without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test --namespace value-type.impl.loader-test`
  exited 0: 14 tests, 92 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed without warnings after
  formatting the fixture with `bb clj-fmt fix`.

## 2026-10-27 — Public methods and branch-merge resolution

- Added public protocol, Supplier, Object override and escaping nested-function
  fixtures. Eight branch combinations cover restricted and nullable reads.
- The fixture exposed a real class-file stack-map failure: its default hierarchy
  resolver could not resolve a dynamically defined Leaf at a branch merge.
  Owner and companion transforms now accept an optional class loader; the capture
  loader passes its delegate. Existing transform arities retain their defaults.
  No dependency or design-contract change. This is not AOT or identity-boundary
  evidence; those checks and public transfer verification remain open.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview on,
  without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test --namespace value-type.impl.loader-test --namespace value-type.impl.transform-test --namespace value-type.impl.field-access-test`
  exited 0: 38 tests, 314 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 without warnings.

## 2026-10-27 — Unsupported public expansion transfer

- Added a real preview-enabled producer for both public expansion entry points.
  Serialization retains field metadata. Fresh unsupported receivers test eval,
  source loading and analysis inside an uninvoked function, with repeated attempts
  under silent, warn and error. All reject with native capability data before
  installing the factory, without fallback warnings. No production, dependency
  or design-contract change. Supported receiving execution remains next work;
  these results do not prove loader installation or AOT behavior.
- Verification on the Java 8 and EA3 builds recorded in slice 1, without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-transfer-test`
  exited 0: 1 test, 31 assertions; `.scry-results/` empty. An initial malformed
  fixture binding was corrected before this run. `bb clj-fmt:check` and
  `bb clj-kondo:lint` exited 0 without warnings.

## 2026-10-27 — Field semantic execution matrix

- Ran the existing public construction, mixed-slot, method and companion tests
  together with loader, transform, field-access and storage tests in four fresh
  JVMs. No mocks, exports, production changes or new dependencies. Existing tests
  cover this execution-only action; no test changes or design changes were needed.
- Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6. Exact command from the repository
  root (each run exited 0; 39 tests and 320 assertions passed; checked that
  `.scry-results/` contained no files after each run):

  ```bash
  export JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
  for flatten in + -; do
    for mode in normal interpreter; do
      flags=()
      if [ "$mode" = interpreter ]; then flags+=(-J-Xint); fi
      clojure "-J-XX:${flatten}UseFieldFlattening" "${flags[@]}" \
        -M:test -m scry.cli \
        --namespace value-type.restricted-fields-test \
        --namespace value-type.impl.loader-test \
        --namespace value-type.impl.transform-test \
        --namespace value-type.impl.field-access-test \
        --namespace value-type.impl.field-storage-test
      test -z "$(find .scry-results -type f -print)"
    done
  done
  ```

- The `:test` alias supplies preview. Results establish semantic agreement, not
  flattened storage or performance. Next slice still needs supported expansion
  receiving execution, followed by identity and source-free deployment checks.

## 2026-10-27 — Existing compatibility regression suites

- Ran the existing deftype/core corpus and complete compatibility suite with real
  JVMs. No production changes, new tests or dependencies were needed for this
  verification-only action. Design is unchanged. Existing AOT tests do not
  establish restricted-field AOT acceptance; new deployment fixtures remain open.
- Clojure 1.12.6, Temurin `1.8.0_492-b09`, Valhalla
  `27-jep401ea3+1-1`. Commands from the repository root:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.deftype-compat-test --namespace value-type.core-test`
    — exit 0; 33 tests, 124 assertions.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test`
    — exit 0; 37 tests, 3572 assertions.
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test`
    — exit 0; 37 tests, 3572 assertions.
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -J--enable-preview -M:fallback-test`
    — exit 0; 37 tests, 3547 assertions.
- Checked `.scry-results/` after every command; all were empty. Parent commands
  add no exports; compatibility children select their own flags. Next slice
  still needs supported expansion receiving behavior, class identity and
  restricted-field source-free deployment checks. No human blocker is known.

## 2026-10-27 — Restricted field Class identity

- Added real same-name/different-loader redefinition tests. Old and new holders
  retain their selected nested Class and reject the other Class's instance with
  ClassCastException. A stale loader specification reports the exact structured
  `:field-class-mismatch` error before transformation (the supplied target bytes
  are empty). No production, dependency or design change was needed.
- Verification on Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6, preview enabled,
  without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:test -m scry.cli --namespace value-type.impl.loader-test --namespace value-type.restricted-fields-test`
  exited 0: 18 tests, 115 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 without warnings.
- Next slice still needs supported expansion receiving execution and source-free
  AOT consumers. Runtime Class identity coverage does not establish deployment
  acceptance. No human blocker is known.

## 2026-10-27 — Public evaluation policy matrix

- Added real public definition/evaluation tests in all nine JVM/policy cases,
  with two attempts each. Supported cases verify concrete Long storage, readback,
  native NullPointerException and ClassCastException. Unsupported cases verify
  structured capability rejection. All child stderr is empty. No production,
  dependency or design change was needed.
- Verification on the Java 8 and EA3 builds recorded in slice 1, without exports:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test`
  exited 0: 6 tests, 116 assertions; `.scry-results/` empty.
  `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0 without warnings.
- Next: supported transferred expansions still need a receiving-loader contract;
  ordinary public evaluation does not establish transfer or source-free AOT.

## 2026-10-27 — Supported expansion transfer

- Fresh supported receivers exposed `:not-value-class`: producer loader state
  cannot cross serialization. Added `core/native-field-deftype`, an internal
  receiving macro that checks capability, resolves exact field Classes and installs
  the receiver's capture loader before emitting the guarded definition. The outer
  let remains necessary. No dependency or design-contract change.
- Both expansion entry points now pass eval, metadata-preserving source loading,
  and function analysis followed by invocation, twice under all three policies.
  Native Long storage, reads, nil rejection and wrong-type rejection pass without
  exports. Existing unsupported transfer tests still reject repeatedly.
- Verification used Clojure 1.12.6 and the Java 8/EA3 installations recorded above.
  With `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`:
  - `clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test --namespace value-type.impl.loader-test`
    — exit 0; 18 tests, 115 assertions.
  - `clojure -M:fallback-test --namespace value-type.field-capability-test --namespace value-type.fields-test --namespace value-type.field-transfer-test`
    — exit 0; 19 tests, 235 assertions before adding supported function analysis.
  - `clojure -M:fallback-test --namespace value-type.field-transfer-test`
    — final exit 0; 2 tests, 47 assertions including supported function analysis.
  - All successful runs left `.scry-results/` empty. Initial failing evidence was
    retained at `/tmp/016-transfer-failure.edn` before rerunning.
  - `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb api-docs` and
    `bb api-docs --check` passed; no lint warnings.
- Next: restricted-field AOT producer bytes and source-free consumers. Transfer
  success does not establish deployment acceptance. No human blocker is known.

## 2026-10-27 — Persisted restricted-field AOT producer

- Added a real EA3 AOT fixture and byte inspection in an EA3-only helper loaded
  by the child process. Baseline test discovery remains safe. Saved owner bytes
  retain concrete storage, NullRestricted and Object/Object/long/double constructor
  parameters; saved closure bytes contain concrete owner-field references.
- Bare field symbols capture values rather than read owner fields in companions.
  The fixture now uses explicit receiver access to test persisted rewriting. The
  initial non-vacuity failure is retained at `/tmp/016-aot-initial.edn`.
- No production, dependency or design change. Next: delete producer source and
  execute these artifacts in fresh Clojure consumers, then separately compiled
  Clojure/Java consumers and incompatible JVMs. Byte inspection is not deployment
  acceptance.
- Clojure 1.12.6, Valhalla `27-jep401ea3+1-1`, Temurin `1.8.0_492-b09`:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - Both exited 0: 1 test, 5 assertions; no Scry failure files. Producer uses
    preview without exports. `bb clj-fmt:check` and `bb clj-kondo:lint` passed.


## 2026-10-27 — Source-free restricted-field execution

- Added a fresh EA3 consumer after deleting producer source and excluding its
  directory. The receiver asserts source-resource absence. Direct, factory and
  reflective construction retain mixed-slot values, nullable controls, direct and
  reflective reads, and both persisted companion branches. All three paths reject
  nil and wrong types; reflection retains its normal exception wrapper.
- No production, dependency or design change. Next: separately compiled Clojure
  and Java consumers, then incompatible JVMs. This test uses runtime Clojure
  analysis of consumer code; it does not establish separately compiled consumption.
- Clojure 1.12.6, Valhalla `27-jep401ea3+1-1`, Temurin `1.8.0_492-b09`:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - Both exited 0: 2 tests, 11 assertions; `.scry-results/` empty after each.
    Producer and consumer use preview without exports. `bb clj-fmt:check` and
    `bb clj-kondo:lint` passed without warnings.


## 2026-10-27 — Separately compiled Clojure consumer

- Added a three-process deployment test: producer compilation, separate consumer
  compilation against source-free artifacts, then execution with both sources
  deleted and excluded. The compiled function verifies direct/factory/reflective
  construction, concrete reads, mixed slots, companions and nil/type rejection.
- Consumer namespace aliases must exist during compilation; a require inside its
  function does not establish them. Initial failure evidence is retained at
  `/tmp/016-consumer-initial.edn`. No production, dependency or design change.
- Next: matching EA3 javac consumer and incompatible-JVM rejection. This slice
  does not establish those contracts.
- Clojure 1.12.6, Valhalla `27-jep401ea3+1-1`, Temurin `1.8.0_492-b09`:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - Both exited 0: 3 tests, 19 assertions; `.scry-results/` empty after each.
    All deployment children use preview without exports. `bb clj-fmt:check` and
    `bb clj-kondo:lint` passed without warnings.


## 2026-10-27 — Direct Java AOT consumer

- Added a Java fixture compiled with the selected EA3 JDK compiler through
  ToolProvider in an isolated process. Direct calls retain Object constructor
  parameters, concrete field reads, nullable contents and mixed long/double slots.
  A fresh receiver verifies native NullPointerException and ClassCastException.
  Producer source is deleted and excluded before compilation and execution.
- No production, dependency or design change. Next: incompatible-JVM AOT rejection
  under all policies, then documentation and final verification. Java compiler
  preview notes are permitted; receiver stderr must be empty. No exports are used.
- Clojure 1.12.6, Valhalla `27-jep401ea3+1-1`, Temurin `1.8.0_492-b09`:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - Both exited 0: 4 tests, 27 assertions; `.scry-results/` empty after each.
    `bb clj-fmt:check` and `bb clj-kondo:lint` passed without lint warnings.

## 2026-10-27 — Incompatible restricted-field AOT receivers

- Added fresh Java 8 and preview-disabled EA3 receivers under silent, warn and
  error. Each requires the public library safely, asserts producer-source absence
  and rejects direct Holder loading twice with UnsupportedClassVersionError.
  Empty stderr confirms no identity-fallback warning. No mocks, exports,
  production changes, dependencies or design changes.
- Clojure 1.12.6, Valhalla `27-jep401ea3+1-1`, Temurin `1.8.0_492-b09`:
  - `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-aot-test`
  - Both exited 0: 5 tests, 48 assertions; `.scry-results/` empty after each.
    `bb clj-fmt:check` and `bb clj-kondo:lint` passed without warnings.
- Next: documentation and final verification. These results cover incompatible
  loading, not future JVM formats or storage layout. No human blocker is known.

## 2026-10-27 — Public documentation and API verification

- Expanded usage and macro documentation for eligibility, structured errors,
  receiving checks, constructor signatures and source-free AOT limits. No runtime
  or design-contract change; existing behavior tests cover these claims.
- The full tooling test exposed the internal receiving macro in generated public
  API output. Added a Quickdoc exclusion, consistent with check-value-class.
  Existing public-reference-test verifies the exact public surface without mocks.
  Initial failure evidence is retained at /tmp/016-doc-failure.edn.
- With JAVA_CMD unset (local OpenJDK 25.0.2+10-69, Clojure 1.12.6),
  `bb api-docs`, `bb api-docs --check`, `bb vale`, `bb clj-fmt:check` and
  `bb clj-kondo:lint` passed after prose and formatting corrections.
  `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir test/tooling`
  exited 0: 11 tests, 217 assertions; .scry-results contained no files.
- Next: runtime-guide commands and Ramora embodiment, then the final separate
  runtime, compatibility and build suites and acceptance summary. No human
  blocker is known. This slice does not claim final task acceptance.

## 2026-10-27 — Runtime guidance and design embodiment

- Added reproducible no-export field matrix and source-free consumer commands.
  Ramora now separates implemented boundaries and semantic evidence from the
  prototype's layout observations. Removed stale plan handoffs and working-state
  claims. The design contract is unchanged; no code, tests or dependencies changed.
- Verified the documented focused command on Clojure 1.12.6 and the Java 8/EA3
  builds recorded above:
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java clojure -M:fallback-test --namespace value-type.field-capability-test --namespace value-type.fields-test --namespace value-type.field-transfer-test --namespace value-type.field-aot-test`
  exited 0: 24 tests, 286 assertions; `.scry-results/` empty. `bb vale` passed.
  The four-way semantic command retains the previously verified configuration;
  it was not rerun in this documentation slice.
- Next: run the separate full runtime, compatibility, build and tooling suites,
  then consolidate acceptance evidence for criteria 1–8. Do not infer final
  acceptance from this focused verification. No human blocker is known.

## 2026-10-27 — Final verification and acceptance

- No production changes, new tests or dependencies were needed. Existing real-JVM
  tests cover the completed design. No design or approach change was required.
- Final separate suites used Clojure 1.12.6. Set
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  for the following commands (Valhalla `27-jep401ea3+1-1`):
  - `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime`
    — exit 0; 104 tests, 3096 assertions.
  - `clojure -M:fallback-test`
    — exit 0; 44 tests, 3663 assertions. Child processes select Java 8
    `1.8.0_492-b09` and EA3 with explicit policies and preview settings.
  - `clojure -M:build-test -m scry.cli --dir test/build`
    — exit 0; 9 tests, 107 assertions.
- With `JAVA_CMD` unset, OpenJDK `25.0.2+10-69`:
  - `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir test/tooling`
    — exit 0; 11 tests, 217 assertions.
  - `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and
    `bb api-docs --check` — all exit 0; no lint warnings.
- Inspected `.scry-results/` after each completed suite; no failure files.
  The first compatibility tool call exceeded its 200-second wait while its JVM
  continued. Waited for that process to end, then reran with a 1200-second
  allowance to obtain a confirmed exit status. This was not a test failure or
  a capability absence; no blocker remains.

### Acceptance evidence, criteria 1–8

1. Met: declaration regressions and public construction tests cover both metadata
   forms, enabled/disabled values and structured invalid metadata. See slices 2e
   and 3j and the final runtime and compatibility suites above.
2. Met: field eligibility and declaration tests cover rejected tags, mutable
   declarations and independently compiled external values. See slices 1–2 and
   the final runtime and compatibility suites.
3. Met: public mixed-field verification covers direct, factory and reflective
   calls, both restricted positions, two-slot primitives, nullable nested contents,
   native null rejection and type casts. See slice 3k and final runtime suite.
4. Met: public methods and branch-merge tests, loader tests and transform tests
   cover methods, closures, concrete reads, retained attributes and ordinary
   descriptors. The final runtime suite includes the compatibility corpus.
5. Met: capability, guard and public policy matrices cover safe loading, native
   enforcement, repeated rejection and visible unexpected errors. See slices
   1, 3h–3j, public evaluation policy matrix and final compatibility suite.
6. Met: supported/unsupported expansion transfer, exact-Class redefinition and
   source-free AOT tests cover receiving phases, separately compiled Clojure,
   direct Java and incompatible receivers. See their dated entries above;
   final runtime and compatibility suites include these tests.
7. Met: the Field semantic execution matrix entry records the exact four-way
   command and 39 tests/320 assertions per run, all successful without exports.
   No production code changed after that matrix except the receiving macro;
   supported transfer and final suites separately verify that receiving path.
   These results establish semantics, not flat storage or performance.
8. Met: usage, generated API, runtime guide and Ramora embodiment are current.
   Final quality, runtime, compatibility, build and tooling checks all pass.

All eight criteria have evidence. No implementation follow-up or human blocker
is known. Review can use this summary and the earlier focused commands; no future
JVM format, layout or performance guarantee is implied. The task remains in the
open directory for the separate review/lifecycle step.

## 2026-10-27 — Implementation review

- Added 3 steps to be addressed.

## 2026-10-27 — Review follow-up 1

- Removed selection-time loader installation for enabled fields; receiving installation and selection validation remain.
- EA3, no exports: `clojure -M:test -m scry.cli --namespace value-type.restricted-fields-test` passed (4 tests, 60 assertions); `clojure -M:fallback-test --namespace value-type.field-transfer-test --namespace value-type.field-aot-test` passed (7 tests, 95 assertions). Both exited 0; no Scry failure files.

## 2026-10-27 — Review follow-up 2

- Removed unused `guarded-fields` and its standalone process matrix. Public selection, analysis, evaluation and repeated rejection tests remain.
- EA3: `clojure -M:fallback-test --namespace value-type.field-capability-test --namespace value-type.field-transfer-test` exited 0 (7 tests, 136 assertions); no Scry failure files. Children cover Java 8 and EA3 preview off/on under all policies.

## 2026-10-27 — Review follow-up 3

- Extended the external Java fixture test through public construction with a compiler loader that can load the fixture. Verified exact reflected Class, readback, nullable contents, NullPointerException and ClassCastException.
- EA3, no exports: `clojure -M:test -m scry.cli --namespace value-type.field-eligibility-test` exited 0 (2 tests, 12 assertions); no Scry failure files. `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0.
- Addressed 3 review steps. No blockers remain.

## 2026-10-27 — Follow-up implementation review

- No new actionable issues found. Focused EA3 runtime checks passed: 43 tests,
  343 assertions. Field compatibility, transfer and AOT checks passed: 23 tests,
  259 assertions. Both commands exited 0; no Scry failure files. No exports used.

## 2026-10-27 — Test review

- Added 2 steps to be addressed.

## 2026-10-27 — Test review follow-up 1

- Preserved metadata in unsupported source transfer, as in the supported receiver.
- EA3: `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:fallback-test --namespace value-type.field-transfer-test` passed (2 tests, 47 assertions), exit 0, no Scry failure files. Both expansions and repeated attempts pass across Java 8 and preview-disabled EA3 under all policies; supported receivers also pass.
- Addressed 1 review step.

## 2026-10-27 — Test review follow-up 2

- Changed `BehavioralFields/field-reader` to read restricted and nullable fields through captured `this` inside the nested function. Existing assertions cover escaping functions, both branches, nil/non-nil payloads and nullable controls without a closure-layout requirement.
- Ran the exact four-way Bash matrix in `doc/development/runtime.md` with `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"` (EA3). All four runs passed: 41 tests, 331 assertions each, exit 0, no Scry failure files. Preview enabled; no exports. No layout or performance claim.
- `bb clj-fmt:check` and `bb clj-kondo:lint` exited 0.
- Addressed both test-review steps. No blockers remain.

## 2026-10-27 — Follow-up test review

- Added 1 step to be addressed.

## 2026-10-27 — Follow-up test review completion

- Addressed 1 review step. Annotation frequencies preserve content and count checks without order requirements. Constructor checks retain signatures and concrete writes; `native-storage-round-trip` retains mixed-slot readback and native nil/type rejection.
- Ran the four-way field semantic matrix from `doc/development/runtime.md` with `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"` (EA3), without exports. Each run passed: 41 tests, 327 assertions, exit 0, no Scry failure files.
- `bb clj-fmt:check` and `bb clj-kondo:lint` passed. No blockers remain.

## 2026-10-27 — Public declaration test review

- Added 1 step to be addressed.

## 2026-10-27 — Public declaration review completion

- Addressed 1 review step. Public macroexpansion tests cover all requested rejection
  categories, existing enclosing Class, compiler cause chains and competing fields.
  No production or design change was needed. No blockers remain.
- EA3, preview enabled, no exports:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:test -m scry.cli --namespace value-type.field-eligibility-test`
  exited 0: 3 tests, 54 assertions; no Scry failure files.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed after removing an unused
  test expression flagged by lint.

## 2026-10-27 — Final task test review

- No new actionable issues. Focused EA3 runtime tests passed (44 tests, 381
  assertions); field compatibility, transfer and AOT tests passed (23 tests,
  259 assertions). Both commands exited 0 with no Scry failure files; no exports.

## 2026-10-27 — Annotation-value test review

- Added 1 step to be addressed.

## 2026-10-27 — Annotation-value review completion

- Addressed 1 review step. Both fixtures now contain non-default string, boolean
  and integer annotation values. Assertions compare types and named values without
  annotation order constraints; native annotations and unmarked controls remain.
- EA3, preview enabled, no exports:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:test -m scry.cli --namespace value-type.impl.field-storage-test --namespace value-type.impl.transform-test`
  exited 0: 22 tests, 208 assertions; no Scry failure files.
- `bb clj-fmt:check` and `bb clj-kondo:lint` passed. No blockers remain.

## 2026-10-27 — Transfer isolation test review

- Added 1 step to be addressed.


## 2026-10-27 — Transfer isolation review completion

- Addressed 1 review step. Each supported policy/mode/expansion case uses a fresh
  JVM. Both attempts must return distinct Classes, and each factory must construct
  the returned Class. Native nil/type rejection and unsupported cases remain.
- Compare returned Classes, not namespace imports: function-contained redefinition
  can leave the import pointing to the earlier Class while returning a new Class.
- EA3, no exports:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:fallback-test --namespace value-type.field-transfer-test`
  passed: 2 tests, 86 assertions, exit 0, no Scry failure files. Covers both expansion
  forms, three receiver paths, two attempts and all policies; unsupported receivers
  use Java 8 and preview-disabled EA3.
- `bb clj-fmt:check` and `bb clj-kondo:lint` passed. No blockers remain.

## 2026-10-27 — Transfer isolation follow-up test review

- No new actionable issues. Focused EA3 runtime checks passed (44 tests, 381
  assertions); field compatibility, transfer and AOT checks passed (23 tests,
  298 assertions). Both exited 0 with no Scry failure files; no exports.

## 2026-10-27 — Documentation review

- Added 4 steps to be addressed.

## 2026-10-27 — Documentation follow-up 1

- Added the README field example and scoped fallback guidance. Checked the example against the usage contract; `bb vale` passed.

## 2026-10-27 — Documentation follow-up 2

- Added the Unreleased field contract and experimental limits; scoped the older fallback entry. `bb vale` passed.

## 2026-10-27 — Documentation follow-up 3

- Scoped ordinary fallback and updated runtime architecture, interfaces and invariants. Checked against `native-field-deftype`, `capture-loader` and the loader-aware transform; `bb vale` passed.

## 2026-10-27 — Documentation follow-up 4

- Scoped usage and macro fallback/error guidance; documented receiver-local restricted expansion support and regenerated the API. No runtime behavior changed.
- EA3, no exports: `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:fallback-test --namespace value-type.field-capability-test --namespace value-type.field-transfer-test` exited 0: 7 tests, 175 assertions; no Scry failure files.
- `bb api-docs`, `bb api-docs --check`, `bb vale`, `bb clj-fmt:check` and `bb clj-kondo:lint` passed. Fixed two prose spelling alerts with code markup before final checks.
- Addressed 4 documentation review steps. No blockers remain.

## 2026-10-27 — Follow-up documentation review

- Added 2 steps to be addressed.

## 2026-10-27 — Design scope documentation follow-up

- Addressed 1 review step. Purpose, qualities and local JAR guidance distinguish
  eligible native fields from general non-null references and ordinary fallback.
- Checked against `defvaluetype` and the field capability tests. `bb vale` passed.

## 2026-10-27 — Compiler decision documentation follow-up

- Addressed 1 review step. D1–D6, D9 and tooling risks distinguish ordinary
  selection from restricted receiving installation and field transformation.
- Checked against `native-field-deftype`, `capture-loader`, storage/access and
  constructor transforms, and supported transfer tests. `bb vale` passed.
- Both follow-up documentation review steps are complete. No blockers remain.

## 2026-10-27 — Qualities documentation review

- Added 1 step to be addressed.

## 2026-10-27 — Qualities documentation follow-up

- Addressed 1 review step. Scoped unchanged method bytecode and benchmark evidence
  to ordinary definitions; separated restricted-field behavior from performance.
- Checked against `field-access/rewrite`, the composed transform, companion
  rewriting and native field evidence limits. `git diff --check` passed.
  Documentation only; no runtime tests needed. No blockers remain.

## 2026-10-27 — Final documentation review

- No new actionable issues. `bb api-docs --check` passed.

## 2026-10-27 — Code-shaper review

- Added 2 steps to be addressed.

## 2026-10-27 — Code-shaper follow-up 1

- Addressed 1 review step. Owner and companion transforms share optional-loader
  ClassFile configuration behind the optional JDK boundary; nil retains JDK defaults.
- EA3, preview without exports: runtime restricted-fields, loader, transform and
  field-access namespaces passed (40 tests, 321 assertions). Java 8 parent:
  field-aot and field-capability namespaces passed (10 tests, 137 assertions),
  including source-free consumers and safe public loading. All Scry commands
  exited 0; no failure files. `bb clj-fmt:check` and `bb clj-kondo:lint` passed.

## 2026-10-27 — Code-shaper follow-up 2

- Addressed 1 review step. A local helper owns producer compilation and the
  exit/stderr success gate. Tests retain separate artifacts, explicit source
  deletion and consumer assertions; byte inspection still precedes deletion.
- EA3: `clojure -M:fallback-test --namespace value-type.field-aot-test`
  passed (5 tests, 48 assertions), exit 0, no Scry failure files.
  `bb clj-fmt:check` and `bb clj-kondo:lint` passed.
- Both code-shaper review steps are complete. No blockers remain.

## 2026-10-27 — Follow-up code-shaper review

- Added 1 step to be addressed.

## 2026-10-27 — Follow-up code-shaper completion

- Addressed 1 review step. Shared annotation contents and occurrence counts in
  `test/runtime/value_type/annotation_support.clj`; kept fixtures and expected
  values local. Baseline compatibility helpers are unchanged.
- EA3, preview without exports:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" clojure -M:test -m scry.cli --namespace value-type.impl.field-storage-test --namespace value-type.impl.transform-test`
  passed (22 tests, 208 assertions), exit 0, no Scry failure files.
  `bb clj-fmt:check`, `bb clj-kondo:lint` and `git diff --check` passed.
  No blockers remain.

## 2026-10-27 — Final code-shaper review

- No new actionable issues. Focused EA3 runtime checks passed: 44 tests,
  381 assertions, exit 0, no Scry failure files; no exports.

## Closure — after knowledge extraction commit 2e7bc76

Closed at the user's request after implementation, successful implementation
review and knowledge extraction. All checklist items are complete. Moved the task
to munera/closed and removed it from the active plan. No tests were rerun for this
administrative closure; verification evidence remains in the preceding records.
