# Plan

## Approach

Run the feasibility gate first (D7 non-nil gate). D6 and D8 are provisional
until it passes. If it fails, record the evidence and stop for a design
decision.

Then implement in dependency order: declaration validation, backends,
receiving boundary, tests, documentation. Each slice ends with bounded
evidence that maps to the acceptance criteria.

Key decisions, from the design and the implementation.md review notes:

- **Gate method.** The non-nil gate uses a spike-local transform, as the D3
  gate did, and records "production transform not covered" in its limits.
  `gate.clj` does not change, so the recorded D3 SHA-256 stays valid.
- **`:hidden-field` placement.** The D6 order puts `:hidden-field` between
  the metadata-key phase and the tag/type phase of
  `fields/specifications`. Put the check in `value-type.impl.fields`
  (which owns `record-hidden-fields`) or expose the two phases; `fields`
  stays the one owner of the non-nil declaration rules. The receiver repeat
  goes between the metadata checks and the modes check in
  `receiving-specifications`.
- **Record value storage reuses the plain path.**
  `transform/constructor-code` already takes a storage argument
  (`->record-value-class` passes nil). The value backend composes concrete
  storage, casts, native restriction and `nil` checks into the main
  constructor only, keeps D3 shape validation and the D4 removed-field
  check, leaves delegating constructors, `create` and constructor
  descriptors unchanged, and removes hash fields as now.
- **Identity delegation rule is shape-independent.** Detection: an
  `invokespecial <init>` with owner = target class on the receiver.
  `:unsupported-shape` for a delegating constructor that writes an enabled
  field, and for a constructor with a `NEW` of the target that also calls
  one of its constructors. Each other constructor writes each enabled field
  once. ASM only; baseline-safe.
- **Receiving boundary.** New `defvaluetype*` receiving macros in
  `value-type.core`, `value-field-deftype*` and `identity-field-deftype*`,
  carry `tagname classname modes fields & opts+specs` (the `defvaluetype`
  receivers carry `class-name modes name fields`). The expansion transports the form and
  fixed enforcement modes, not the shape; the receiver derives the shape
  with the `:record-shape` check and passes it to the capture loader
  (`install-receiving!` currently hard-codes `:plain`). Each receiver
  keeps the check order of its `defvaluetype` receiving macro
  (`value-field-deftype`, `identity-field-deftype` in
  `src/value_type/core.clj`), puts `:record-shape` directly after
  `:mutable-field` and `:hidden-field` directly after the metadata keys,
  then installs capture with the derived shape:
  - `value-field-deftype*`: `:mutable-field`, `:record-shape`, value
    capability (`:value-capability-unavailable`), metadata keys,
    `:hidden-field`, modes (`:invalid-field-enforcement`), tag and type
    (with native type eligibility), native field capability
    (`:field-capability-unavailable`).
  - `identity-field-deftype*`: `:mutable-field`, `:record-shape`,
    metadata keys, `:hidden-field`, modes, tag and type, fixed fallback
    policy (`runtime/enforce-fixed-fallback!`).
  No new receiving error; no second policy path. Add `:no-doc` overrides in
  `bb/value_type/api_docs.clj` so `defvaluetype*` stays the only public
  surface.
- **Backend tests before the receiving macros.** With enabled fields, the
  `defvaluetype*` expansion is always a receiving macro (ramora D1, D5), and
  these come in slice 5. Thus slices 3 and 4 test each backend through
  fixture macros that call `loader/install!` with specifications, the
  representation and the shape, then expand to `(let [] (deftype* …)
  <class>)`. Precedents: `test/compatibility/value_type/identity_backend/fixture.clj`
  (`:identity :plain`) and the `restricted-type`/`mixed-type` macros in
  `test/runtime/value_type/impl/loader_test.clj` (`:value :plain`). The
  internal delegation-rule tests go in
  `test/compatibility/value_type/identity_backend_test.clj`, so the
  fallback suite also runs them on Java 8. The public `defvaluetype*`
  tests of Acceptance 2 and 3 are in slice 5. The Java 8 and EA3
  preview-off evidence of Acceptance 7 is in slice 6: Java 8 reaches
  enabled-field identity output only through the identity receiving macro.
- **Fixture macros (`deftype_star_record_test.clj`).** Each takes
  `tagname classname fields & opts+specs`, as `defvaluetype*`, so it can
  be the `definer` of `record-form` and also define plain forms directly.
  - Slice 3 and 4: one private helper takes the representation and the
    macro arguments. It derives the shape from the fields (`:record` when
    the fields end with the four hidden names of `record-tail`, else
    `:plain`). It gets the specifications from the `:non-nil` metadata
    with `fields/specifications`. For `:value` it selects modes with
    `fields/select-enforcement` and the thunk of `defvaluetype`
    (`#(:available? (runtime/field-capability))`); for `:identity` it
    keeps the `:generated` modes of `fields/specifications`. Then it calls
    `loader/install!` and returns `(let [] (deftype* tagname classname
    fields opts+specs) classname)`. Two thin macros call it:
    `value-installed*` (slice 3) and `identity-installed*` (slice 4). A
    native field needs a value-class tag, so the value tests first define
    a `defvaluetype` class for that tag.
  - Slice 5: the identity definer is `identity-receiving*`. It expands to
    `(vt/identity-field-deftype* tagname classname modes fields
    opts+specs)`, with one `:generated` mode for each field that
    `fields/enabled-fields` gives. The value definer is `vt/defvaluetype*`.
- **Transfer evidence (slice 5).** Extend the fallback-suite harness
  (`deftype_star_transfer_checks.clj`, `deftype_star_transfer_test.clj`),
  not a cross-JVM test. Each fallback-suite host is one receiver
  (`:selected`), and slice 6 and 8 run the suite on Java 8, EA3 preview off
  and EA3 preview on, so the three receivers are covered.
  - `produce` specs get a fourth element `enabled?`; with it, the first
    field is `^Long ^:non-nil x` in place of `x`. The new
    `consume-enabled` returns the `consume` keys, plus `:stored` (field
    `x` of the instance from argument 42) and `:rejections`
    (`consumer-aot-checks/checked-outcomes` for `nil` and `(Object.)`).
    It calls the one-argument constructor by reflection
    (`Reflector/invokeConstructor`): the main constructor in the plain
    shape, the declared-fields delegating constructor in the record shape.
    Failures give `registry/root-failure` (with `:data`).
  - The existing `deftype*-transferred-expansion-test` stays unchanged
    (Acceptance 8). Two new tests have the cells mode × expand × shape,
    each with enabled fields, and the policies `silent`, `warn` and
    `error`:
    - Identity producer (`config/identity-producer`: Java 8, `silent`;
      the expansion is `identity-field-deftype*` with `[:generated]`).
      `silent`, `warn`: `{:named? true :value? false :new-class? true
      :hash-fields <2 record, 0 plain> :stored 42 :rejections
      checks/checked-rejections}`. `error`:
      `(assoc harness/fallback-rejection :data nil)`. Each cell has
      `:fallback? true` and `:warning harness/fallback-context`, as the
      existing test.
    - Value producer (`config/value-producer`: EA3 preview, `error`; the
      expansion is `value-field-deftype*` with `[:native]`, as `Long` is a
      value class there). Receiver with capability (`:available?` of
      `config/configuration`), each policy: `{:named? true :value? true
      :new-class? true :hash-fields 0 :stored 42 :rejections
      checks/checked-rejections}`, no `:fallback?`. The record cell of this
      row is the evidence that the receiver passes `:record` to the
      capture loader. Receiver without capability, each policy: root
      failure with `:class "clojure.lang.ExceptionInfo"` and `:data`
      including `:value-type/error :value-capability-unavailable` and the
      cell `:class-name`; the message and `:reason` are not fixed.
- **AOT evidence (slice 5).** Keep the `Plain` and `Rec` fixtures of
  `deftype_star_aot_checks.clj` unchanged (Acceptance 8). Add to
  `fixture-source` `PlainNonNil` (`[^Long ^:non-nil x]`) and `RecNonNil`
  (the same field, then the record tail), and to `classes`
  `:plain-non-nil` (`[42]`) and `:record-non-nil` (`[42 nil nil]`). For
  the new classes, `facts` adds `:rejections`:
  `consumer-aot-checks/checked-outcomes` of the same constructor with
  `nil` and `(Object.)` for `x`. `expected` adds both entries, with
  `:rejections checks/checked-rejections` and the hash-field counts of
  the shape. In `deftype_star_aot_test.clj`: `identity-artifact-test` and
  `value-artifact-test` use the extended `expected` (under `error`, the
  identity load fails at the first form, as now);
  `incompatible-value-artifact-test` adds the owners
  `star.aot_fixture.PlainNonNil` and `star.aot_fixture.RecNonNil`.
- **Receiving order and errors (slice 5).** Internal-form tests call a
  receiver with `macroexpand-1` and read the cause of the
  `Compiler$CompilerException` (precedent:
  `receiving-identity-native-modes-test` in
  `test/compatibility/value_type/identity_receiving_test.clj`). They assert
  `:data`. Message assertions name only the class name and the modes, not
  the `fields/direct-fix` text ("evaluate the defvaluetype form
  directly"), which names `defvaluetype`; error text does not change.
  - Files: `value-field-deftype*` cases go in
    `test/runtime/value_type/value_receiving_test.clj` (runtime suite, EA3
    preview). The runtime suite is always capable, so the value capability
    check passes and each pair gives the first error.
    `identity-field-deftype*` cases go in
    `test/compatibility/value_type/identity_receiving_test.clj` (fallback
    suite: Java 8, EA3 preview off and on).
  - Order: per receiver, the four adjacent pairs `:mutable-field` →
    `:record-shape`, `:record-shape` → metadata keys, metadata keys →
    `:hidden-field`, `:hidden-field` → tag and type.
  - Receiving errors of Acceptance 6, one case each:
    - `:invalid-field-enforcement`: `identity-field-deftype*` with
      `[:native]` (in `identity_receiving_test.clj`), and
      `value-field-deftype*` with `[]` for one enabled field (in
      `value_receiving_test.clj`). Data: `{:value-type/error
      :invalid-field-enforcement :class-name <cn> :modes <modes>}`.
    - `:not-installed`: `clojure.walk/macroexpand-all` on a raw thread
      (`support/on-raw-thread`) of a public record-shaped `defvaluetype*`
      form with `^String ^:non-nil a`, in
      `test/runtime/value_type/deftype_star_test.clj` (precedent:
      `not-installed-enabled-field-test` in
      `test/runtime/value_type/core_test.clj`). The result is a
      `Compiler$CompilerException`; its cause has the data
      `{:value-type/error :not-installed :class-name <cn>}`.
    - `:value-capability-unavailable`: the value-producer transfer cells
      on a receiver without capability ("Transfer evidence").
    - Fixed fallback rejection: the identity-producer transfer cells
      under `error` ("Transfer evidence").
    - `:field-capability-unavailable`: no test. `field-capability/detect`
      gives unavailable only when value capability is unavailable, and
      `value-field-deftype*` checks value capability first, so no JVM
      reaches this error. The `defvaluetype` receiver has no test of it
      either. `test/cross-jvm/value_type/field_capability_test.clj`
      covers `runtime/require-field-capability!`.
- **Acceptance 7 evidence (slice 6).** Add two cells, "plain non-nil" and
  "record non-nil", to `deftype*-policy-test` in
  `test/compatibility/value_type/runtime_test.clj`. Keep the existing
  cells without enabled fields (Acceptance 8). The new cells declare
  `^Long ^:non-nil x` (the record cell then adds the record tail) and
  evaluate public `defvaluetype*` under `silent`, `warn` and `error`. The
  fallback suite runs them on Java 8 and on EA3 with preview off (identity
  output with generated checks), and on EA3 with preview on (value
  output). Thus the cells also give the fallback-policy evidence of
  Acceptance 7. An option of `deftype*-form` adds these outcome keys:
  - `:nil` and `:wrong-type`: the root class name of the cause chain
    (`registry/failed-call` with `(:class (registry/thrown e))`) of
    `(new cn nil)` and `(new cn "s")`. `(new cn …)` with one argument is
    the main constructor of the plain shape. In the record shape it is the
    declared-fields delegating constructor, which calls the main
    constructor.
  - Record cell only: `:create-stored`, `:create-nil` and
    `:create-wrong-type`, the same checks through `create`. `deftype*`
    emits `create` for the record shape also without `:implements`
    (checked on Clojure 1.12.6).
  - Expected: `:stored 42`, `:create-stored 42`,
    `"java.lang.NullPointerException"` and
    `"java.lang.ClassCastException"`; under `error` on a JVM without
    capability, the failed definition, as for the existing cells.
  Cross-JVM cells are not required for Acceptance 7. Slice 8 runs the
  cross-JVM suite as regression; it also runs the slice 5 AOT fixtures
  (`test/cross-jvm/value_type/deftype_star_aot_test.clj` uses
  `deftype_star_aot_checks`).
- **Test fixtures.** Record fixtures stay `IRecord`-only (an `IType`
  implementer is emitted as a main-constructor call, not `create`). The
  nil-in-map-view constant uses `(construct c [valid … nil {:a nil}])` of
  `deftype_star_record_test.clj`, no new fixture code. Tests find exception
  types in the cause chain. Extend existing harnesses
  (`deftype_star_transfer_*`, `deftype_star_aot_checks`, `field_*`,
  `non_nil_fields_test`, `deftype_star_record_test`) instead of new ones.
  Receiving-order fixtures carry valid enforcement modes; the
  metadata/`:hidden-field` pair needs the metadata error on a later field
  (for example `^:non-nil __meta` with `^{:non-nil :x} __hash`).
- **Ramora growth.** `deftype-star-design.md` and `decisions.md` already
  exceed the 1000-word split rule. Put new Embodiment detail in a linked
  file under `ramora/deftype-star-design/`, as
  `ramora/defvaluetype-design/embodiment/` does.
- **Commitments.** One rule: provisional "until" a gate passes becomes
  `firm` with retained evidence. D6, D8 and D3 (task 019 gate) become
  `firm` after the gate.

## Risks

- The feasibility gate can fail on EA3 with preview and `-Xverify:all`
  (native storage in a record-shaped value class). Mitigation: gate first;
  on failure, record evidence and stop.
- The `fields` phase split can disturb the existing `defvaluetype` error
  order. Mitigation: keep `fields` the one owner; existing non-nil and
  fields tests must stay green (Acceptance 8).
- Delegation detection could misclassify compiler-emitted code. Evidence so
  far: compiler delegating constructors contain no jumps, switches or try
  blocks, and no Clojure-emitted constructor contains a `NEW` of the
  target. Mitigation: internal tests for each D8 rejection case.
- Fallback-suite and cross-JVM evidence (Java 8, EA3 preview off/on)
  needs the configured toolchains; record limits in implementation.md if an environment is
  unavailable.

## Slice order

1. **Gate.** `spike/record-value-type/non-nil-gate.clj`, README command,
   `ramora/deftype-star-design/non-nil-gate.md`, raw EDN evidence.
   (Acceptance 1.)
2. **Declaration validation.** `fields` phase split, `:hidden-field`,
   D6 error order in `value-type.core`, removal of
   `:non-nil-unsupported`, D6 test replacements. (Acceptance 5 declaration
   part, 8.)
3. **Value backend.** Record main-constructor storage and checks;
   `value_backend/prepare` reads specifications for `:record`; fixture-macro
   tests for both shapes; bytecode inspection tests. The plain value path
   already takes specifications; slice 3 only tests it with a plain
   `deftype*` class. (Acceptance 4 value part; backend evidence for 2
   and 3.)
4. **Identity backend.** Delegation rule, generated checks for both
   shapes; fixture-macro tests; bytecode inspection tests. (Acceptance 4
   identity part, 5 identity backend part; backend evidence for 2 and 3.)
5. **Receiving, public forms and AOT.** `defvaluetype*` receiving macros,
   shape-derived capture install, public `defvaluetype*` construction-path
   tests, transfer and AOT tests, receiving-order and receiving-error
   internal tests, `:no-doc` overrides. (Acceptance 2, 3, 6.)
6. **Fallback policies and baseline JVMs.** The "plain non-nil" and
   "record non-nil" cells of `deftype*-policy-test`: `silent`/`warn`/`error`
   with enabled fields, both shapes; identity output with generated checks
   for both shapes on Java 8 and on EA3 with preview off. (Acceptance 7.)
7. **Docs and commitments.** Docstring, `doc/API.md`, `doc/usage.md`,
   `CHANGELOG.md`, `mementum/state.md`, ramora Embodiment (linked file),
   non-nil embodiment, commitments to `firm`; `api_docs_test.clj`
   docstring assertions in the same slice if the docstring text that they
   assert changes. The docstring and `doc/usage.md` replace the old error
   list and the `:non-nil` restriction and exclusion with the D6 order
   (Acceptance 5, 10). `CHANGELOG.md`: edit the existing `## [Unreleased]`
   `defvaluetype*` bullet in place; replace "`:non-nil` fields are
   excluded." with the non-nil support, record behaviour and evidence
   limits; add no separate bullet. `defvaluetype*` is not released, so the
   entry does not name the removed reason (this also keeps the slice 8
   `git grep non-nil-unsupported` check clean). `mementum/state.md`:
   replace "no `:non-nil` fields" in the `defvaluetype*` bullet.
8. **Quality and regression.** Runtime, fallback, cross-JVM, tooling,
   release and build suites; `bb api-docs --check`, `bb clj-fmt:check`,
   `bb clj-kondo:lint`, `bb vale`; `git grep non-nil-unsupported --
   ':!munera'` finds no match (Acceptance 5); record commands, results and
   limits in implementation.md. (Acceptance 5, 8–9.) The tooling suite
   (`test/tooling/value_type/api_docs_test.clj`) asserts the exact list of
   documented names, which proves the slice 5 `:no-doc` overrides, and
   `defvaluetype*` docstring text that slice 7 changes. The release suite
   validates the `CHANGELOG.md` entries of slice 7. The build suite checks
   packaging.
