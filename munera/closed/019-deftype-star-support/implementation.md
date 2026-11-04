# Implementation

## Design review

- 2026-11-03 architecture review: `ramora/META.md` and `doc/architecture.md`
  do not exist. The architecture sources are `ramora/INDEX.md`,
  `ramora/deftype-star-design.md` (D1–D7), `ramora/defvaluetype-design/`
  (decisions D1, D6, D9; fallback D10, D12, D13; assertions A-16, A-18) and
  `AGENTS.md`. The design agrees with them: it reuses the ordinary value path
  (install at expansion, `check-value-class`), one policy owner and a guarded
  untransformed fallback, and keeps baseline loading free of optional APIs.
- Context for later passes: under the existing contract, ordinary value
  definitions (no non-nil specifications) do not support separate expansion
  and evaluation; only the receiving macros do. D6 gives `defvaluetype*` no
  specifications, so "receiving compilation" in Scope and Acceptance 5 maps
  to the D1 expansion-time install and the guarded fallback. It does not map
  to a new receiving macro.
- no architectural review feedback
- ambiguity review added 5 new design steps
- inconsistency review added 2 new design steps
- Notes for the work on design-steps (2026-11-03, after `b09a3ca`):
  - Principles: scope stays frozen. Resolve each step with a statement in
    design.md, and change `ramora/deftype-star-design.md` (D1–D7, error table)
    in the same commit when the contract changes, so that the two stay equal.
    Reuse `defvaluetype` behaviour and error keys before you add new ones
    (minimal interface; AGENTS.md). ramora holds the contract; Munera holds
    task facts (`ramora/INDEX.md` documentation rules).
  - Existing behaviour that the steps refer to:
    `src/value_type/impl/fields.clj` (`non-nil?`: `false`/`nil` disabled,
    `:invalid-non-nil`, `:removed-nil-restricted` by presence);
    `src/value_type/core.clj` (`check-fields`, `check-value-class`,
    `:not-installed`/`:not-value-class`, ordinary value path);
    `src/value_type/impl/loader.clj` (`capture-loader`: in value output the
    companion transform does nothing when there are no specifications, so a
    removed-field check needs a new hook);
    `src/value_type/impl/value_backend.clj`, `src/value_type/impl/runtime.clj`
    (`guarded-deftype`);
    `ramora/defvaluetype-design/decisions/validation-and-errors.md` (D9:
    separate expansion and evaluation unsupported for ordinary definitions).
  - Compiler facts (Clojure 1.12 `Compiler.java`, `NewInstanceExpr`; from
    memory, not checked against bytes): the record constructors and `create`
    are emitted when there is at least one trailing hidden name
    (`altCtorDrops`). They always assume all four. Hidden names that are not
    trailing are ordinary fields. `getBasis` omits the hidden fields.
    `Compiler.emitValue` emits a record constant through `create` only when
    the instance is `IRecord`.
- Design steps 1–7 done (2026-11-03). design.md and ramora D2, D4, D6 and
  the error table changed in the same commit. Facts found, checked against
  Clojure 1.12.1 sources (`Compiler.java`, `LispReader.java`,
  `core_deftype.clj`):
  - Record constructors always push `null, null, 0, 0` (or `0, 0`) but drop
    only the counted trailing names. `ctorTypes()` gives `Object` for
    reference tags and the primitive type for primitive tags (`int` stays
    `int`). Hence the tag rules of D2.
  - `create` exists when the field count exceeds the hinted fields. It reads
    declared fields from the map, then calls the main constructor with
    `null, RT.seqOrElse(map), 0, 0`.
  - `emitValue` checks `IType` before `IRecord`. An `IRecord` constant is
    emitted as `create(PersistentArrayMap.create((Map) value))`, which only
    calls `entrySet`. `defrecord` classes implement `IRecord` and
    `java.util.Map`, not `IType`. Positional literals use
    `Reflector.invokeConstructor` by arity; map literals call `create`.
  - `tagOf` accepts a Symbol or String tag; other values give no tag.
  - closed-record (`~/projects/hugoduncan/closed-record/closed-record-master/src/closed_record/impl/jvm.clj`)
    uses untagged `__meta __extmap` and `^int ^:unsynchronized-mutable`
    hash fields. It must drop the mutable metadata to pass `:mutable-field`.
  - Existing `check-value-class` messages name "defvaluetype"; reuse for
    `defvaluetype*` keeps that text unless the plan changes it.
  - Existing tests to mirror: `test/runtime/value_type/core_test.clj`
    (`not-installed-test`, `not-value-class-test`),
    `test/compatibility/value_type/transfer_checks.clj` (receiving modes
    `:eval`, `:analysis`, `:source`).
  - Not changed (pre-existing): ramora Context still says the compiler gives
    the record shape when the last four fields are the hidden names; D2
    states the exact compiler rule.
- 2026-11-03 architecture review 2 (after `957c9a8`, design steps 1–7):
  no architectural review feedback. Facts for planning, checked in source:
  - `defvaluetype` runs `check-fields` (`:mutable-field`), then
    `fields/specifications` (`:nil-restricted`, `:non-nil`), then capability
    and policy. `defvaluetype*` puts `:record-shape` between the first two
    checks, so the design order agrees with the existing path.
  - `capture-loader` writes companion class files during AOT only when
    specifications exist. The removed-field check rejects companions and
    does not rewrite them, so value output of `defvaluetype*` needs no
    companion writes.
- ambiguity review 2 added 1 new design step. Checked and not filed:
  the order of the field metadata checks across fields. "Else" and "first such
  field" give a check per field, in field order, with `:nil-restricted` first
  in each field, as in `fields/non-nil?`. Also not filed: the `:class-name` of
  `:removed-field-access`. The error table says "with `:class-name`", which
  means the defined class, as for `:field-class-mismatch`.
- inconsistency review 2 added 2 new design steps
- Notes for the work on design-steps 8–10 (2026-11-03, after `773870d`):
  - Step 8 (non-symbol elements): the record-shape rule already leaves
    elements that are not symbols, and a `fields` value that is not a vector,
    to `deftype*`, and `check-fields` checks only vectors. If the metadata check
    uses the same rule, the three checks treat input the same way, and the
    behaviour stays the same as `fields/enabled-fields`. From memory (not
    checked): `NewInstanceExpr` casts `fields` to `IPersistentVector` and each
    element to `Symbol`, so the compiler fails with a `ClassCastException` in
    a `CompilerException`. If a test depends on that error, check it first.
  - Step 9 (commit reference): the current ramora text is from `f0d53ae`.
    `957c9a8` changed only design.md. No other file refers to `99da8ad`.
    Prefer no commit, because a commit becomes stale at the next contract edit.
  - Step 10 (ramora Context): change only the compiler sentence in
    `ramora/deftype-star-design.md` (Context and scope). Use the compiler facts
    above (`altCtorDrops` counts trailing hidden names; the generated code
    assumes all four). Do not repeat the D2 predicate in Context.
- Design steps 8–10 done (2026-11-03). design.md, ramora D6 and ramora
  Context changed in the same commit. No `SCOPE_QUESTION:` items.
  - Checked in Clojure 1.12.6 `Compiler.java`: `DeftypeParser` casts
    `fields` to `IPersistentVector`, and `NewInstanceExpr.build` casts each
    element to `Symbol`. So both faults left to `deftype*` give a
    `ClassCastException`, also when the element carries `:non-nil`.
    `altCtorDrops` > 0 exactly when the last field has a hidden name.
  - All three declaration checks now examine only symbols in a `fields`
    vector (`mutable-fields`, `fields/enabled-fields` already do this). A
    shared helper for the three checks keeps them equal.
  - ramora Context now uses "record shape" only for D2's form; the
    compiler's wider trigger is called "record code".
- 2026-11-03 architecture review 3 (after `de466b1`, design steps 8–10):
  no architectural review feedback. The new symbol-in-vector rule for all
  three declaration checks agrees with `defvaluetype` (`check-fields`,
  `fields/enabled-fields`) and with D2/D6.
- ambiguity review 3 (after `3b2791f`): no ambiguity review feedback.
  Checked and not filed: duplicate or qualified hidden names (the in-place
  rule rejects them); the location of the retained D7 experiment (a plan
  choice); error message text and the `:class-name` form (reuse
  `value-type.core` behaviour).
- inconsistency review 3 (after `2dab9ed`): no inconsistency review feedback.
  Checked in source: the error data of design.md and the ramora error table
  agree with `check-fields`, `fields/non-nil?`, `check-value-class` and
  `transform/unexpected-constructor!` (all add `:class-name`). Existing
  `:unexpected-constructor` and `check-value-class` messages name the
  `deftype`/`defvaluetype` shape and "use Clojure 1.12"; the plan decides
  whether `defvaluetype*` needs other text (the design gives keys and data only).
- Handoff after design review 3 (2026-11-03, after `ddc35ca`): this slice
  added no design steps; all 10 are done. The next step is planning
  (`create-task-plan`), not design-step work. For planning:
  - Keep the frozen scope and the D3 feasibility gate first; keep design.md
    and `ramora/deftype-star-design.md` equal if the plan changes a contract.
  - The current transform input check is `constructor-difference` in
    `src/value_type/impl/transform.clj` (parts: count of constructors,
    parameters, super call, field writes). The record shape needs a new path
    there, not a wider rule for every class.
  - Model for the retained D7 experiment: `spike/value-array-semantics/`
    (README with commands) and its ramora evidence report
    (`ramora/value-type-array-design/semantic-verification.md`).
- Plan ambiguity review 1 (after `713e385`): added 1 design step (owner
  removed-field check point and its order with `:unexpected-constructor`).
  Why actionable: an owner method that reads `__hash` (for example `hasheq`)
  is the main access case, and the two check points give different errors
  and different code. Checked and not filed: how the shape reaches the
  loader (D4 and Embodiment say value-backend and loader "carry" it, so a
  new `install!` input from `defvaluetype*`, not detection from bytes);
  `:class-name` is `(str classname)` of the `deftype*` arguments; usage
  documentation goes in `README.md` and `doc/usage.md`, as for non-nil
  fields; the transferred `:source` mode already prints with `*print-meta*`,
  so `^int` tags survive (`transfer_checks.clj`).
- Plan inconsistency review 1 (after `26afd6a`): added 1 design step
  (stale task entry in `munera/plan.md`). Checked and not filed:
  - ramora Embodiment lists a guarded `deftype*` under both `value-type.core`
    and `value-type.impl.runtime`. The plan chooses runtime. Slice 6 rewrites
    Embodiment, so make it name one place there.
  - Slice 1 writes "the gate result" to a ramora evidence report.
    `ramora/INDEX.md` lets experiment reports keep methods, observations and
    evidence limits, but not delivery status. Put the pass/fail decision of
    the gate in implementation.md only.
- Notes for the work on plan-review design steps 11–12 (after `5db7fd4`):
  - Principles: these steps change plan.md and steps.md (and
    `munera/plan.md`), not the scope. If step 11 adds an error-order rule,
    also add it to design.md (Removed fields) and to ramora D4 and the error
    table in the same commit. Today neither file orders loader errors.
  - Step 11 facts (checked in source): in `capture-loader`
    (`src/value_type/impl/loader.clj`), the target branch runs
    `check-field-classes!`, then `(owner bs)`, then `writeClassFile` when
    `*compile-files*`, then `define-target-class`. `->value-class`
    (`src/value_type/impl/transform.clj`) throws `:unexpected-constructor`
    before it changes any bytes. `field-access/rewrite` keeps field
    references that are not in `storage`. With no specifications, owner
    methods that read `__hash` still read it after the transform. A check
    after the transform therefore sees every access except the removed
    main-constructor writes. It needs no constructor matching, and it fits
    between `(owner bs)` and `writeClassFile`, so AOT never writes rejected
    bytes. A check before the transform must copy the main-constructor
    matching of the new record path.
  - Step 12: change only the task 019 entry in `munera/plan.md`.
- Plan-review design steps 11–12 done (2026-11-03). plan.md (Removed-field
  check), steps.md Slice 4 and the task entry in `munera/plan.md` changed.
  - Step 11 choice: the owner removed-field check reads the transformed
    bytes, after `(owner bs)` and before `writeClassFile`. So
    `:unexpected-constructor` comes before `:removed-field-access`, and AOT
    writes no rejected bytes. design.md was read-only for this pass, so
    design.md and ramora D4 / error table do not state this order. The order
    is a plan choice that follows from the transform owning constructor-shape
    validation. A later review may file a design step to put it in the
    contract (change both files in one commit).
  - Crafted bytes for the Slice 4 order test (and Slice 3 constructor-set
    rejections): `deftype*` cannot emit an extra constructor. Build bytes
    with `java.lang.classfile`, as `super-before-write-class` in
    `test/runtime/value_type/impl/loader_test.clj` does, and pass them to the
    capture loader's `defineClass`.
  - No steps.md items were executed; these were plan edits only.
- Plan ambiguity review 2 (after `a11241c`): added 1 design step (AOT
  statement of the removed-field check). Why actionable: Clojure 1.12
  `ObjExpr.compile` calls `writeClassFile` with the original bytes, and only
  then `getCompiledClass` calls the loader `defineClass`. A Slice 4 or
  Slice 5 test through `deftype*` under `*compile-files*` that asserts "no
  class file" fails. Checked and not filed: the delegating constructors call
  `invokespecial` on the class itself (`objtype`), not on the compile stub,
  so D3 matching by owner is exact; the spike transform is spike-local code
  (the capture loader selects backends by fixed symbols, so the spike cannot
  plug in a transform through `install!`); the evidence report path and the
  test file locations are implementation choices.
- Plan inconsistency review 2 (after `e1fb605`): added 1 design step
  (removed-field scan location and baseline loading). Why actionable:
  `loader.clj` imports only `clojure.lang` and loads on Java 8 for identity
  definitions with non-nil fields; `value-backend/prepare` loads only for
  value output. If the owner check is composed into `:owner` after
  `->value-class`, it keeps the plan's check point (after the transform,
  before `writeClassFile`) with no change to `capture-loader`. Checked and
  not filed: the documented caller obligations differ in wording (plan.md
  lists three, steps.md four, ramora also lists the tag rule); the tag rule
  is enforced by `:record-shape`, so Slice 6 can take the ramora list.
- Notes for the work on plan-review-2 design steps 13–14 (after `a560ad4`):
  - Principles: plan edits only (plan.md, steps.md); scope stays frozen.
    Neither step changes the design.md or ramora contract: design.md says
    only "the loader checks each class when it defines it", and ramora
    Embodiment already names value-backend and loader together. If the
    wording of D4 changes, change design.md and ramora in one commit.
  - Step 13 (AOT files): the compiler writes at the end of
    `ObjExpr.compile`, before `getCompiledClass` calls the loader. Existing
    rule: defvaluetype D7 in
    `ramora/defvaluetype-design/decisions/compiler-and-transform.md`. A
    compiler-path test can instead assert that the file bytes on disk are
    the untransformed bytes (an identity class with both hash fields).
  - Step 14 (scan location): existing Java 8 evidence that `loader.clj`
    loads on baseline: `test/compatibility/value_type/identity_loader_test.clj`
    (requires `value-type.impl.loader`, runs in the compatibility suite on
    Java 8; JVM selection in `test/compatibility/value_type/jvm_config.clj`).
    Keep `java.lang.classfile` imports only in namespaces that load after
    value output is chosen (`value_backend.clj`, `transform.clj`,
    `field_access.clj`). If the scan goes into `:owner`/`:companion`, the
    plan text "needs a new loader hook" and the risk "Loader hook scope"
    become stale; update them in the same edit.
- Plan-review-2 design steps 13–14 done (2026-11-03). plan.md (Removed-field
  check, Risks, Slice order) and steps.md Slices 3–4 changed; no steps.md
  items executed; design.md and ramora unchanged (D4 wording and ramora
  Embodiment already fit).
  - Scan location: `value-type.impl.value-backend`, composed into `:owner`
    after `->value-class` and into `:companion`. `loader.clj` only carries
    the shape (new last argument of `install!`/`capture-loader`, passed to
    `value-backend/prepare` only); `identity-backend/prepare` unchanged, as
    ramora Embodiment says ("The identity backend does not change").
  - Arity change hits direct callers in `test/`:
    `test/runtime/value_type/test_support.clj`,
    `test/runtime/value_type/impl/loader_test.clj`,
    `test/compatibility/value_type/{capture_failure_test,identity_loader_test}.clj`,
    `test/compatibility/value_type/identity_backend/fixture.clj`,
    `test/cross-jvm/value_type/{generated_value_aot_test,identity_aot_test}.clj`
    (`capture_loading_test.clj` only checks `install!` is callable). Spikes
    have their own loaders.
  - Existing baseline guard: `runtime_test.clj` `optional-loaded-form`
    (checks `value-type.impl.loader`/`transform` not loaded after requiring
    `value-type.core`); the new Slice 4 check extends this idea to requiring
    the loader itself.
- Plan ambiguity review 3 (after `690246d`): added 1 design step (fallback
  expansion return value). Why actionable: checked in Clojure 1.12.4
  `Compiler.java`, `NewInstanceExpr.eval` returns null and `emit` pushes
  `ACONST_NULL` for `deftype*`; `deftype` returns the class only because
  `core_deftype.clj` appends `~classname` after `(import ~classname)`.
  Checked and not filed: "verification enabled" in Slice 1 (the JVM verifies
  classes of non-boot loaders by default, and running a constructor requires
  verification); the main constructor is unique by parameter count, also with
  zero declared fields (`()`, `(Object Object)`, `(Object Object int int)`);
  the receiving macros `value-field-deftype`/`identity-field-deftype` pass
  `:plain` through `install-capture!`, covered by "`defvaluetype` passes
  `:plain`".
- Plan inconsistency review 3 (after `974b7d3`): added 1 design step
  (ramora "as `deftype*` does" return claim). Checked and not filed:
  `munera/plan.md` still names Slice 1 as next; open design steps come first
  in the lifecycle, so the entry stays correct.
- Notes for the work on plan-review-3 design steps 15–16 (after `fecd0a4`):
  - Principles: resolve both steps in one commit. Step 15 edits plan.md
    and steps.md Slice 2 only. Step 16 edits only the ramora Public contract
    sentence. design.md already says "evaluates to the defined Class" and
    does not change. Scope stays frozen.
  - Facts: `deftype` returns the class only because `core_deftype.clj`
    (`deftype` macro) appends `(import ~classname)` and `~classname` after
    `deftype*`. The `deftype*` call defines the class during analysis, so a
    fully qualified class symbol after it in the same expansion resolves with
    no `import`. `defvaluetype*` needs no `import`: `deftype*` callers such as
    closed-record emit their own.
  - Options for step 15: (a) the new runtime macro emits
    `(do (enforce-fixed-fallback!) (deftype* …) <classname>)`, so it returns
    the Class the same way as `guarded-deftype`; (b) `value-type.core` adds
    the class symbol after the guarded call, as its value expansion does.
    (a) puts the return next to the guard and keeps the core expansion
    simple. Models: `guarded-deftype` in `src/value_type/impl/runtime.clj`;
    the fallback branch of `defvaluetype` in `src/value_type/core.clj`.
  - The second `deftype*` argument (`classname`) can carry metadata
    (`emit-deftype*` copies it from `cname`). Use `(str classname)` for
    `:class-name`/`install!` and `(symbol (str classname))` for `<class>`.
- Plan-review-3 design steps 15–16 done (2026-11-03). plan.md (Expansion
  mirrors `defvaluetype`), steps.md Slice 2 and the ramora Public contract
  return sentence changed; design.md unchanged; no steps.md items executed.
  - Choice: option (a). New `runtime/guarded-deftype*` emits
    `(do (enforce-fixed-fallback!) (deftype* …) <class>)`; the core
    fallback expansion adds nothing after it.
  - Checked on Clojure 1.12.6 (`clojure -M -e`): a bare `deftype*` returns
    nil; `(do (deftype* user/Foo user.Foo …) user.Foo)` and the `let []`
    form with record-shaped fields return the class with no `import`.
  - Still open for Slice 6: ramora Embodiment lists a guarded `deftype*`
    under both `value-type.core` and `value-type.impl.runtime`; the plan
    puts it in runtime only.
- Plan ambiguity review 4 (after `4a99c8c`): added 1 design step (transform
  interface for the shape). Why actionable: `->value-class` has about 17
  direct 3-argument calls in `transform_test.clj` and
  `method_attributes_test.clj`; the plan lists caller updates only for
  `install!`/`capture-loader`. Spikes that call `->value-class`
  (`portable-fields`, `null-restricted-fields`) are historical and already
  use old arities (`verify.sh` runs at `6318c36`), so they do not constrain
  the choice. Checked and not filed: the shape passed for a non-vector
  `fields` (the `deftype*` analysis fails, so it has no effect); new message
  text for `:record-shape`, `:non-nil-unsupported` and
  `:removed-field-access` (keys and data are fixed; text is an
  implementation choice); the location of the "identity fallback does not
  check" test (the runtime suite cannot select fallback on EA3 preview on, so
  it goes in the compatibility suite).
- Plan inconsistency review 4 (after `74806ca`): no inconsistency review
  feedback. Checked and not filed: ramora D7 asks the spike to read "a record
  literal"; design.md and steps.md Slice 1 ask for both forms (stricter, so
  no conflict). Slice 2 wires the value path before Slice 3 adds the shape
  argument to `install!`; until Slice 3, record-shaped value output gives
  `:unexpected-constructor`, so Slice 2 tests of the value path use plain forms.
- Notes for the work on plan-review-4 design step 17 (after `1c4f42b`):
  - Principles: plan edits only (plan.md, steps.md Slice 3); scope stays
    frozen; design.md and ramora stay unchanged (ramora Embodiment says only
    that value-backend "carries the shape"). `AGENTS.md` permits a change
    that breaks old callers, so choose the simplest interface, not the one
    that keeps old arities.
  - Facts (checked in source): production callers of `->value-class` are
    `value_backend.clj` (`:owner`) and the `install!` docstring in
    `loader.clj`, which names it. Test callers: 13 in
    `test/runtime/value_type/impl/transform_test.clj`, 2 in
    `test/runtime/value_type/impl/method_attributes_test.clj`. `prepare` takes
    `[target specifications delegate]`; its only caller is `capture-loader`.
  - Options: (a) `prepare` takes the shape as a new last argument and selects
    a new `transform/->record-value-class` for `:record`; `->value-class` and
    its test calls stay. (b) `->value-class` takes the shape as a 4th
    argument; all 15 test calls change. (a) leaves the plain path and its
    tests as they are and matches "a record path, not a wider rule".
  - Shared-code hazard for either option: `value-class-transform` in
    `src/value_type/impl/transform.clj` applies `constructor-transform` to
    every constructor and `strict-init-transform` to every instance field.
    The record path must rewrite only the main constructor and drop the two
    hash fields, so it needs its own class transform or a selector argument.
- Plan-review-4 design step 17 done (2026-11-03). plan.md (Transform via a
  new record path: Interface, `prepare`, Callers; ->record-value-class in
  Removed-field check) and steps.md Slices 3–4 changed; design.md and ramora
  unchanged; no steps.md items executed.
  - Choice: option (a). New `transform/->record-value-class [bs
    class-loader]`, no `storage` (empty under D6); `value-backend/prepare
    [target specifications delegate shape]`. `->value-class` and its 15 test
    calls stay.
  - `capture-loader` today calls both backends through one
    `requiring-resolve` call with the same three arguments; passing the
    shape to the value backend only means that call branches by
    representation (`identity-backend/prepare [target specifications
    _delegate]` stays).
  - Steps.md Slice 3 now says kept instance fields get `ACC_STRICT_INIT`
    (the old "kept fields unchanged" conflicted with value-class rules).
- Plan ambiguity review 5 (after `6fdfde2`): added 1 design step (check of
  the main-constructor body in the record path). Why actionable: the record
  path rewrites the whole main-constructor body; ramora D3 and the plan check
  only the delegating constructors, so an implementation can drop other code
  in the body with no error. The Clojure main constructor of a record shape
  has the same code as a plain `deftype` constructor (`Object.<init>`, then
  one `putfield` per field), so `expected-code` with all fields fits.
  Checked and not filed: kept-field slots (hash parameters are last, so
  `field-slots` of the kept fields gives the original slots); the receiving
  macros reach `install!` through `install-capture!`, which takes the shape
  too.
- Plan inconsistency review 5 (after `808ac38`): no inconsistency review
  feedback. Checked and not filed: steps.md Slice 6 says "Observed
  qualities", but the ramora section is "Qualities and risks" (the same
  section); ramora Embodiment says value-backend and loader carry "the
  removed field names", but the plan carries only the shape (Slice 6
  rewrites Embodiment); the plan "Callers that change" list omits
  `value-type.core` (`install-capture!`), which the `:plain` statement
  covers. The test files and the alias that the steps name exist.
- Notes for the work on plan-review-5 design step 18 (after `4eb0e7a`):
  - Principles: plan edits only (plan.md Transform section and Risks
    "Compiler-bytecode coupling", steps.md Slice 3); scope stays frozen.
    design.md already asks for "exact constructor-shape validation" and does
    not change. If ramora D3 gets a main-constructor body rule, change
    design.md in the same commit only if its text then disagrees.
  - Reuse in `src/value_type/impl/transform.clj`: `expected-code [owner
    fields]`, `first-difference`, `debug-element?` and the `part` mapping of
    `constructor-difference` (position ≤ 2 → `super call`, else `field
    writes`). For the record path, pass all instance fields of the input
    bytes (hash fields included): the input still declares them and its main
    constructor writes them.
  - Test models in `test/runtime/value_type/impl/transform_test.clj`:
    `check-error`/`check-built-error` (assert key, `:class-name` and the
    `part` in the message) and the `build-class` crafted classes
    (`write-before-super-class`, `no-write-class`, `swapped-writes-class`).
    `defrecord-error-test` compiles a real `defrecord` with
    `compile-fixture`; that gives real record-shaped bytes for
    `->record-value-class` tests (its hash fields are not final, which the
    transform drops anyway).
- Plan-review-5 design step 18 done (2026-11-03). plan.md (Transform:
  main-constructor body; Risks; Slice order) and steps.md Slice 3 (one
  validation item, one rejection test) changed; design.md and ramora
  unchanged; no steps.md items executed.
  - Choice: reuse `expected-code` over all input instance fields (hash
    fields included) with `first-difference`; parts `super call` /
    `field writes`, as in `constructor-difference`.
  - `expected-code`, `first-difference`, `debug-element?` are private in
    `transform.clj`; `->record-value-class` lives in the same namespace, so
    no visibility change. `check-error` in `transform_test.clj` calls
    `->value-class` (two storages) through `thrown-data`; record tests
    need their own thrower.
  - Left open (implementation choice): message part for a wrong constructor
    set or delegating body, and check order among set, main body and
    delegating bodies (all give `:unexpected-constructor`). ramora D3 does
    not state the main-body check; Slice 6 or a later review may add it.
- Plan ambiguity review 6 (after `808ac38` + step 18 edits): no ambiguity
  review feedback. Checked and not filed: the Slice 2 value expansion's
  `installed` is the `install-capture!` return, as in the existing
  `defvaluetype` value branch (`src/value_type/core.clj`); the delegating
  constants `null, null, 0, 0` / `meta, extmap, 0, 0` match the compiler's
  record constructors for all field counts (main ctor unique by descriptor);
  message text for the record-path rejections stays an implementation
  choice, as recorded after step 18.
- Plan inconsistency review 6: no inconsistency review feedback. Checked and
  not filed: the two Slice 4 AOT assertions (crafted bytes → no file;
  compiler path → untransformed file) agree with step 13 and with each
  other; the expansion forms, error order, transform interface and scan
  location are stated identically in plan.md and steps.md after steps 11–18.
- Handoff after plan review 6 (2026-11-03, after `7d38dd1`): no design steps
  added; all 18 are done and no design-step work remains. The next work is
  steps.md Slice 1 (D3/D7 feasibility gate). For that slice: model
  `spike/value-array-semantics/` and its evidence report
  `ramora/value-type-array-design/semantic-verification.md`; the spike
  transform is spike-local (the capture loader selects backends by fixed
  symbols); put the gate pass/fail decision in implementation.md only, not
  in the ramora report (`ramora/INDEX.md` documentation rules).
- Slice 1 done (2026-11-03): D3/D7 gate PASSED on EA3 preview with
  `-Xverify:all`. Spike `spike/record-value-type/gate.clj` (README with
  command); report `ramora/deftype-star-design/feasibility-gate.md`, raw
  result `ramora/deftype-star-design/evidence/gate-ea3-preview.edn`. All
  12 cases pass: value class, hash fields gone, 3 constructors, main and both
  delegating constructors, `create`, map/positional literals, compiled
  constant, `withMeta`, `=`. Facts for later slices:
  - Value-class delegating `this(…)` constructors verify unchanged; only the
    main body needs rewriting (kept-field writes from slots over all fields,
    then `Object.<init>`). Fixture uses `^long`/`^double` fields, so wide
    slots are proven.
  - Inside `deftype*` method bodies, refer to the own class by its simple
    name (`(new Rec …)`); `gate.Rec` and `gate/Rec` do not resolve there
    (compile stub). Slice 5 test forms need this.
  - The spike capture: a macro sets `Compiler/LOADER` to a
    `DynamicClassLoader` proxy at expansion, then expands to
    `(let [] (deftype* …) <class>)`; this is the production shape too.
  - Preview off: the JVM rejects the preview class version (not a gate case).
- Slice 2 done (2026-11-03): `value-type.core/defvaluetype*` and
  `runtime/guarded-deftype*`. Facts for later slices:
  - `record-shape` returns `:plain`/`:record` (bound as `_shape` in the
    macro, unused); Slice 3 passes it to `install-capture!`/`install!`.
    Until then the value path calls `install!` with `[] :value` and the
    plain transform, so a record form on a capable JVM fails with
    `:unexpected-constructor`. Plain value output works (tested).
  - `field-symbols` is the shared symbols-in-vector selector;
    `mutable-fields` now filters symbols first (same behaviour).
  - Record-shape `:field` keeps its metadata (the symbol from the form).
  - Tests: `test/runtime/value_type/deftype_star_test.clj` (validation,
    order, guard, plain value); compat
    `runtime-test/public-deftype*-definition-test` (policies; passed on
    Java 8, EA3 preview off, EA3 preview on). Macroexpansion errors arrive
    wrapped in `CompilerException`; tests read the cause chain.
  - Full runtime suite on EA3 preview: 126 tests, 4414 assertions pass.
- Slice 3 done (2026-11-03): `transform/->record-value-class [bs loader]`;
  shape carried through `install!`/`capture-loader` (new last arg) to
  `value-backend/prepare [target specs delegate shape]`. Facts:
  - Record check order: `count of constructors` (≠3; also <4 fields gives
    `parameters`), `parameters` (main/short/meta descriptors not each
    present once), `delegating constructor` (new message part; body not
    `aload0, args, (aconst_null×2 | meta, extmap), iconst_0×2,
    invokespecial main, return`), then main body via `expected-code` over
    all fields (`super call`/`field writes`).
  - Plain `deftype` bytes given to the record path fail with
    `count of constructors`.
  - `:record` `:companion` is `identity` for now; Slice 4 replaces it with
    the removed-field scan and adds the scan to `:owner` after the transform.
  - `identity-backend/prepare` unchanged; `capture-loader` calls each
    backend with its own arity (`:value` gets the shape).
  - `defrecord` bytes have the same constructor shape, so the record
    path accepts them (not used, but handy as a fixture source).
  - Tests: transform_test `record-*` (structure, round trip for zero /
    reference / long+double fields, built-class rejections);
    deftype_star_test `record-value-definition-test` (end to end on EA3).
  - Verified: runtime suite EA3 preview (needs
    `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`) 132 tests
    / 4486 pass; fallback-test Java 8 and EA3 59/1728 pass; cross-jvm-test
    EA3 94/4377 pass; clj-kondo clean; cljfmt applied.
- Slice 4 done (2026-11-03): removed-field scan in
  `value-type.impl.value-backend` (`check-removed-fields`, private). Facts:
  - `:record` `:owner` = scan after `->record-value-class`; `:companion` =
    scan only (bytes unchanged). Scan parses with plain `ClassFile/of`
    (no loader needed) and matches `getfield`/`putfield` with owner
    `ClassDesc/of target` and name `__hash`/`__hasheq`.
  - Message text: "Invalid access to removed field <class>/<field>: value
    output of defvaluetype* removes __hash and __hasheq; compute hashes
    from entries." Data per design.
  - A fn class in a method body that reads `(.-__hasheq ^Name this)` is a
    companion access; the owner in that case has no hash access. A
    closure over `__hash` by name is an owner read (captured at fn
    construction).
  - Tests: `test/runtime/value_type/impl/removed_field_test.clj` (owner,
    both-fields, companion, AOT untransformed file, error order via
    `#'loader/capture-loader` with crafted extra `(I)V` constructor);
    compat `runtime_test` `deftype*-removed-field-check-test` (silent/warn;
    value → `:removed-field-access`, fallback → defined) and
    `loader-require-test`.
  - Verified: runtime suite EA3 preview 136 / 4496 pass; fallback-test
    Java 8, EA3 preview off, EA3 preview on 61 / 1740 pass; cross-jvm-test
    EA3 preview 94 / 4377 pass; clj-kondo 0 errors/warnings; cljfmt fix.
- Slice 5a done (2026-11-03): `test/runtime/value_type/deftype_star_record_test.clj`
  builds record forms by function (`record-form definer classname declared`)
  and runs each under `runtime/guarded-deftype*` (identity) and, when
  capable, `vt/defvaluetype*` (value). Facts:
  - `getBasis` returns the declared fields only (hidden fields excluded),
    in both representations.
  - The compiler-generated `create` casts `^int` fields to `Integer`, so a
    map literal with a Long for an `^int` field fails in both
    representations (Clojure behaviour, not ours). Mixed variant therefore
    uses `^long`, `^double` and a reference field only.
  - Identity hash fields: `int`, final, hold 0.
  - Plain form: `IType` constant compiles and evaluates in both outputs.
  - Verified: runtime suite EA3 preview 138 / 4615 pass; clj-kondo clean.
  - Remaining Slice 5: policy, receiving, AOT, cross-JVM matrix, regression.
- Slice 5b (2026-11-03): policy and receiving tests.
  - `runtime_test/deftype*-policy-test`: one child per policy, cells plain,
    record, then `defvaluetype` (shares the once-per-runtime warning), then
    a `:non-nil` declaration error (`:non-nil-unsupported` under every
    policy, no fallback, no warning). Passed on Java 8, EA3 preview off/on.
  - `deftype_star_transfer_test.clj` (+ `_checks`): baseline producer
    expands both shapes with `macroexpand`/`macroexpand-1`; class name is
    `cell.i.Transferred` (built per spec, since `defvaluetype*` takes the
    class name explicitly). Receivers × `:eval :analysis :source` × policy:
    identity class with 2 hash fields (record) or policy rejection.
    Passed on all three JVMs.
  - Value receiving lives in the runtime suite
    (`value-expansion-receiving-test`, both shapes): raw-thread
    `macroexpand` → `:not-installed`; `(eval (macroexpand …))` →
    `:not-value-class`.
- Slice 5c (2026-11-03): AOT tests and full verification.
  - `test/cross-jvm/value_type/deftype_star_aot_test.clj` with
    `test/compatibility/value_type/deftype_star_aot_checks.clj` (fixture
    `star.aot-fixture`, one plain + one record `defvaluetype*`):
    baseline-produced artifact on baseline/preview off/preview on × policy
    → identity (record keeps 2 hash fields), `error` → fixed fallback
    rejection on load, `warn` → one warning; EA3-produced artifact on
    preview on (normal, `-Xint`) × policy → value, no hash fields;
    baseline and preview off reject both value classes with
    `UnsupportedClassVersionError` under every policy. 124 assertions pass.
  - Matrix (JAVA_HOME selects the parent; EA3 = `~/.local/jdks/jdk-27.jdk`,
    Java 8 = temurin-8):
    - `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime` (EA3): 139 / 4621 pass.
    - `clojure -M:fallback-test` Java 8, EA3 off, EA3 `-J--enable-preview`: 63 / 1904 pass each.
    - `clojure -J--enable-preview -M:cross-jvm-test` (EA3): 97 / 4501 pass.
    - `-M:build-test` 9 / 110 pass; `-M:release:release-test` 12 / 123 pass.
    - `bb clj-fmt:check`, `bb clj-kondo:lint` (0 errors / 0 warnings), `bb vale` pass.
  - Expected failures, left to Slice 6 (new public var not yet documented):
    `bb api-docs --check` (doc/API.md stale) and tooling
    `api_docs_test/public-reference-test` (public list lacks
    `defvaluetype*`); tooling otherwise 10 / 217 pass.
  - Limit: interpreter (`-Xint`) runs only for the value artifact consumers.
- Slice 6 (2026-11-03): documentation and embodiment.
  - `doc/usage.md` section "`deftype*` forms": syntax, shapes, caller
    obligations, fallback, exclusions, evidence limits.
  - Docstring gained exclusions and evidence limits; `doc/API.md`
    regenerated. `api_docs_test/public-reference-test` lists
    `defvaluetype*` and checks key phrases (docstring writes
    `:reason :record-shape` in one code span).
  - `ramora/deftype-star-design.md` Embodiment and qualities describe
    delivered behaviour; no contract change, so design.md is unchanged.
  - `mementum/state.md` names the new API.
  - Verified: `bb api-docs --check`; `clojure -Srepro -M:quickdoc:tooling-test
    -m scry.cli --dir test/tooling` 11 / 224 pass; `bb clj-fmt:check`,
    `bb clj-kondo:lint` (0/0), `bb vale` pass. Only docstring text changed in
    `src/`, so Slice 5c runtime/cross-JVM records stand.
- Implementation review 1 (after `3d34bad`): added 4 steps. Checked and
  not filed: runtime suite on EA3 preview passes (139 / 4621); clj-kondo
  0 / 0; record-shape `:field` selection, delegating-constructor slots and
  the scan location match design and plan.
- Review 1 follow-up (2026-11-03): addressed 4 review steps.
  - `field-symbols` returns `[index symbol]` pairs and is the selector of
    `mutable-fields`, `record-shape` and `check-field-metadata`.
  - `defvaluetype*` docstring lists value-output errors
    (`:unexpected-constructor`, `:removed-field-access`); `doc/API.md`
    regenerated; phrases added to `public-reference-test`.
  - `transform/hash-field-names` (vector, report order) is the one
    definition; `value-backend` uses it.
  - `record-constructor-difference` returns `[part main-descriptor]`;
    `->record-value-class` reuses the descriptor.
  - Verified: runtime (EA3) 139 / 4621; tooling 11 / 226; fallback Java 8
    63 / 1904; `bb api-docs --check`, clj-fmt, clj-kondo (0/0), vale pass.
- Implementation review 2 (after `ac19b4e`): added 2 steps.
- Review 2 follow-up (2026-11-03): addressed 2 review steps.
  - `check-removed-fields` gives `:field` as a symbol; test expectations
    updated.
  - `field-name`, `parameter-descriptors` and `code-difference` moved
    before the input check; `constructor-difference`, `putfield?`,
    `constructor-code` and `storage-transform` use them; `main-params`
    removed.
  - Verified (EA3): runtime 139 / 4621; fallback (preview on) 63 / 1904;
    clj-fmt, clj-kondo (0/0) pass.
- Implementation review 3 (after `e94c597`): added 1 step.
- Review 3 follow-up (2026-11-03): addressed 1 review step.
  - `value-class-transform [keep-field? ctor-code]` holds the version,
    flags, field and constructor branches for both paths;
    `constructor-transform` takes the code `Consumer`.
    `record-value-class-transform` keeps non-hash fields and gives code
    only for the main constructor.
  - Verified (EA3): runtime 139 / 4621; cross-jvm 97 / 4501; clj-fmt,
    clj-kondo (0/0) pass.
- Implementation review 4 (after `dc4d552`): added 2 steps.
- Review 4 follow-up (2026-11-03): addressed 2 review steps.
  - `constructor-code [this fields keep-field? storage]` is the one
    emitter; `record-constructor-code` removed. `argument-slots` gives the
    field slots and the next free slot; `field-slots` and `delegating-code`
    use it.
  - `test_support` holds `record-tail` and `ex-info-data` (cause-chain
    `ex-data`); the four test namespaces use them.
  - Verified (EA3): runtime 139 / 4621; fallback (preview on) 63 / 1904;
    cross-jvm 97 / 4501; clj-fmt, clj-kondo (0/0) pass.
- Implementation review 5 (after `562cc2a`): no review feedback.
- Test review 1 (after `0a38aba`): added 4 steps.
- Test review 1 follow-up (2026-11-03): addressed 4 review steps.
  - Removed `plain-value-definition-test`, `record-value-definition-test`,
    `guarded-deftype*-test` and `public-deftype*-definition-test`; the
    class-name fact moved to `deftype_star_record_test`, `:new-class?` and
    `:stored` to the `deftype*-policy-test` cells.
  - Accept tests assert `:no-error`; `unchecked-shapes-test` also shows
    that evaluation fails with no `value-type` error data.
  - `test_support/thrown-data` is the one throw-capture helper.
  - transform_test: wrong `(I)V` descriptor gives `parameters`; other code
    in either delegating constructor gives `delegating constructor`.
  - Verified (EA3): runtime 136 / 4630; fallback Java 8 and EA3 preview on
    62 / 1892; clj-fmt, clj-kondo (0/0), vale pass.
- Test review 2 (after `ee3413a`): added 1 step.
- Test review 2 follow-up (2026-11-03): addressed 1 review step.
  - `removed_field_test/companion-bytes-test`: companion bytes built with
    `java.lang.classfile` and defined through `#'loader/capture-loader`
    (`:value :record`); `putfield` of `__hash` and of `__hasheq` on the
    target give `:removed-field-access` with that `:field`; `getfield` of
    `__hash` on another class is accepted.
  - Verified (EA3): runtime 137 / 4633; clj-fmt, clj-kondo (0/0) pass.
- Test review 3 (after `81b8351`): added 1 step.
- Test review 3 follow-up (2026-11-03): addressed 1 review step.
  - transform_test `record-round-trip-test` passes hash arguments
    `(int 7) (int 8)`; the kept fields stay the same.
  - Verified (EA3): transform_test 28 / 302; clj-fmt, clj-kondo (0/0) pass.
- Test review 4 (after `5a5a10c`): added 2 steps.
- Test review 4 follow-up (2026-11-03): addressed 2 review steps.
  - `accepted-shapes-test`: record shape with `{:tag nil}` on `__meta`
    and `__extmap` gives `:no-error`.
  - transform_test: `record-thrown-data` and `check-record-error`
    removed; `thrown-data [f bs]` and `check-error [failure bs …]` serve
    both paths (`value-class-failure`, `record-failure`).
  - Verified (EA3): transform_test + deftype_star_test 34 / 339;
    clj-fmt, clj-kondo (0/0) pass.
- Test review 5 (after `dbe5203`): added 2 steps.
- Test review 5 follow-up (2026-11-03): addressed 2 review steps.
  - runtime_test "declaration error" and `deftype*-removed-field-check-test`
    use `cell-registry/outcome` with `root-data` and assert the full error
    data (`:value-type/error`, `:class-name`, `:reason`, `:field`); the
    removed-field cell uses the fixed class `cell.HashRead`.
  - removed_field_test: `record-form` takes an optional namespace;
    `class-file` is the one class-file path helper.
  - Verified: removed_field_test (EA3) 5 / 13; fallback-test
    `value-type.runtime-test` Java 8, EA3 off, EA3 preview on 14 / 221
    each; clj-fmt, clj-kondo (0/0) pass.
- Test review 6 (after `dab6c93`): added 2 steps.
- Test review 6 follow-up (2026-11-03): addressed 2 review steps.
  - runtime_test "record" cell declares `x` and expects `:stored 42`
    (declared-fields constructor); `deftype*-form` always reads `x`.
  - `value-type.cell-registry/hash-field-count` (child-safe, Java 8) is the
    one hash-field count; used by `deftype_star_aot_checks`,
    `deftype_star_transfer_checks` and the `deftype*-form` child form.
  - Verified: fallback-test Java 8, EA3 off, EA3 preview on 62 / 1892
    each; cross-jvm-test (EA3 preview) 97 / 4501; clj-fmt, clj-kondo
    (0/0) pass.
- Test review 7 (after `e508785`): no test review feedback.
- Test review 8 (after `65b8777`): no test review feedback.
- Docs review 1 (after `32eba3f`): added 4 steps.
- Docs review 1 follow-up (2026-11-03): addressed 4 review steps.
  - `CHANGELOG.md` `[Unreleased]`: `defvaluetype*` entry, linked to
    `doc/usage.md#deftype-forms`.
  - `doc/usage.md` (`deftype*` forms): order is `:mutable-field`,
    `:record-shape`, field metadata.
  - `README.md`: one statement on `defvaluetype*` for `deftype*` emitters.
  - `ramora/deftype-star-design.md` (Context and scope): the one-constructor
    limit names the `defvaluetype` path (`->value-class`) and the identity
    backend; the record path is separate.
  - Verified: `bb vale` (no alerts); tooling 11 / 226; release 12 / 123.
- Docs review 2 (after `ddd9ee4`): added 1 step.
- Docs review 2 follow-up (2026-11-03): addressed 1 review step.
  - `doc/development/runtime.md`: section "Verify `deftype*` forms" with
    focused commands, limits and a link to the spike gate command.
  - `value-type.deftype-star-transfer-test` is in `test/compatibility/`; the
    guide runs it with `:fallback-test` on the three JVMs (the step named
    `:cross-jvm-test`).
  - Verified the documented commands: runtime (EA3 preview) 13 / 177;
    fallback-test `runtime-test` + `deftype-star-transfer-test` Java 8, EA3
    off, EA3 preview on 15 / 340 each; cross-jvm `deftype-star-aot-test`
    3 / 124; `bb vale` (no alerts); tooling 11 / 226.
- Docs review 3 (after `42c836c`): no docs review feedback.
- Code-shaper review 1 (after `ac05ed1`): added 1 step.
- Code-shaper review 1 follow-up (2026-11-03): addressed 1 review step.
  - `record-constructor-difference` gives `["parameters" nil]` for fewer
    than four instance fields; `->record-value-class` makes one difference
    call, as `->value-class` does.
  - Added transform_test case: `:no-fields` and `:branch` bytes give
    `parameters` from `->record-value-class`.
  - Verified (EA3 preview): runtime suite 137 / 4634 before the new case;
    transform, removed-field, deftype-star and deftype-star-record tests
    41 / 489 after it; clj-fmt, clj-kondo (0/0) pass.
- Code-shaper review 2 (after `1911f8e`): added 1 step.
- Code-shaper review 2 follow-up (2026-11-03): addressed 1 review step.
  - `value-backend/prepare`: `storage` binding moved into the `:plain`
    branch; `:record` reads no specifications.
  - Verified (EA3 preview): runtime suite 137 / 4644; clj-fmt, clj-kondo
    (0/0) pass.
- Code-shaper review 3 (after `7af7383`): added 2 steps.
- Code-shaper review 3 follow-up (2026-11-03): addressed 2 review steps.
  - `transform/invoke-init?` takes owner and descriptor and checks
    `(not (.isInterface i))`; `object-init?` and the delegating-constructor
    main call check use it; `invoke-main?` removed. The main call check now
    also rejects an interface `invokespecial` (compiler never emits one).
  - `Instruction` moved into the `java.lang.classfile` import list.
  - Verified (EA3 preview): runtime suite 137 / 4644; clj-fmt, clj-kondo
    (0/0) pass.
- Code-shaper review 4 (after `d43db7d`): added 2 steps.
- Code-shaper review 4 follow-up (2026-11-03): addressed 2 review steps.
  - `transform/main-constructor-part` and `field-descriptors` serve
    `constructor-difference` and `record-constructor-difference`.
  - `unexpected-constructor!` takes the `ClassModel` and computes
    `:class-name`; the `entry` bindings of both entry points are removed.
  - Verified (EA3 preview, `--add-exports=java.base/jdk.internal.value`):
    runtime suite 137 / 4644; clj-fmt, clj-kondo (0/0) pass.
- Code-shaper review 5 (after `2a76eab`): added 2 steps.
- Code-shaper review 5 follow-up (2026-11-03): addressed 2 review steps.
  - `fields/removed-nil-restricted!` (public, baseline-safe) builds the
    `:removed-nil-restricted` error; `fields/non-nil?` and
    `core/check-field-metadata` pass only their fix text.
  - `field-access/owner-field-access?` serves `field-access/rewrite` and
    `value-backend/removed-field-accesses`; the scan uses the now public
    `transform/hash-field?`; `Opcode` import removed from `value-backend`.
  - Verified: runtime suite (EA3 preview) 137 / 4644; `:fallback-test`
    Java 8 and EA3 preview 62 / 1892 each; clj-fmt, clj-kondo (0/0) pass.
- Code-shaper review 6 (after `df8a041`): added 2 steps.
- Code-shaper review 6 follow-up (2026-11-03): addressed 2 review steps.
  - `core/hidden-fields` holds each hidden name with its required tag;
    `hidden-index` and `in-place?` read it.
  - `:removed-nil-restricted` (defvaluetype*) and `:removed-field-access`
    messages end with `Fix: …`.
  - Verified: runtime suite (EA3 preview) 137 / 4644; `:fallback-test`
    Java 8 and EA3 preview 62 / 1892 each; clj-fmt, clj-kondo (0/0) pass.
- Code-shaper review 7 (after `3fd1fb9`): added 2 steps.
- Code-shaper review 7 follow-up (2026-11-03): addressed 2 review steps.
  - `core/value-expansion` installs capture and emits the value expansion
    for `defvaluetype` (`:plain`) and `defvaluetype*`; `check-value-class`
    docstring names both macros; `doc/API.md` regenerated.
  - `value-backend/removed-field-accesses` parses with
    `(class-file/configured nil)`; `ClassFile` import removed.
  - Verified: runtime suite (EA3 preview) 137 / 4644; `:fallback-test`
    Java 8, EA3 off and EA3 preview 62 / 1892 each; clj-fmt, clj-kondo
    (0/0), `bb api-docs --check` pass.
- Code-shaper review 8 (after `b07facb`): added 1 step.
- Code-shaper review 8 follow-up (2026-11-03): addressed 1 review step.
  - `fields/record-hidden-fields` (baseline-safe) holds the hidden names
    with tags; `core/hidden-index` and `in-place?` read it;
    `transform/hash-field-names` takes its `int`-tagged entries (`__hash`
    first). `doc/API.md` regenerated (source line links only).
  - Verified: runtime suite (EA3 preview) 137 / 4644; `:fallback-test`
    Java 8 and EA3 preview 62 / 1892 each; clj-fmt, clj-kondo (0/0),
    `bb api-docs --check` pass.
- Code-shaper review 9 (after `ed854cb`): added 3 steps.
- Code-shaper review 9 follow-up (2026-11-03): addressed 3 review steps.
  - `core/record-shape` accepts on `(and (= 4 (count hidden)) (empty?
    misplaced))`.
  - `transform/record-constructor-difference` binds `mains`, `shorts`,
    `metas` once.
  - `transform` ns docstring names `->record-value-class`;
    `core/check-fields` docstring names `deftype*`. `doc/API.md`
    regenerated (source line links only).
  - Verified: runtime suite (EA3 preview) 137 / 4644; `:fallback-test`
    Java 8 and EA3 preview 62 / 1892 each; clj-fmt, clj-kondo (0/0),
    `bb api-docs --check` pass.
- Code-shaper review 10 (after `e9f5d2a`): no new steps.
- Closure (2026-11-03): implementation review complete; knowledge extracted
  (`cb630c5`). Task moved to `munera/closed/`; entry removed from
  `munera/plan.md`. Open notes: the error order (`:unexpected-constructor`
  before `:removed-field-access`) and the main-constructor body check are
  plan choices and are not stated in `design.md` or ramora D3/D4.
