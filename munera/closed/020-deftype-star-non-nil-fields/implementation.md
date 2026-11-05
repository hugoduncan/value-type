# Implementation

## Design review — architecture (2026-11-03)

- architectural review added 1 new design step
- `ramora/META.md` and `doc/architecture.md` do not exist. Sources used:
  `AGENTS.md`, `ramora/INDEX.md` (documentation rules), the deftype* and
  non-nil field designs and their decisions, and the current code.
- Fit confirmed against code: `transform/constructor-code` already takes a
  storage argument (`->record-value-class` passes nil), so record storage
  reuses the plain path; `identity-backend` ignores shape and checks writes
  only at `putfield`, so D8 needs only delegation detection at `RETURN`;
  `loader/install!` already carries shape; `install-receiving!` hard-codes
  `:plain`, the only receiving change for shape.
- `fields/specifications` runs metadata checks for all fields, then tag and
  type checks, in one call (`field-specifications` is private). The D6 order
  puts `:hidden-field` between the two phases, so the hidden check must go
  in `fields` (which already owns `record-hidden-fields`) or `fields` must
  expose the phases. Not a design misfit; a plan decision.

## Design review — ambiguity (2026-11-03)

- ambiguity review added 4 new design steps

## Design review — inconsistency (2026-11-03)

- inconsistency review added 2 new design steps

## Notes for addressing design-steps

- Principles: resolve each step by editing the design text (and the ramora
  text it implements), not by changing scope. The task design implements the
  ramora deftype* design and its decisions, so keep `design.md`,
  `ramora/deftype-star-design.md` and `ramora/deftype-star-design/decisions.md`
  (D4–D8) consistent with each other in one edit. Ramora sections other than
  Embodiment state design without status or history (`ramora/INDEX.md`).
  Prefer the minimal answer: for example, derive the shape from the fields
  rather than sending it as data.
- Precedent: D3 is still "provisional, until D7 passes" although the task
  019 gate passed. Choose one rule for commitments that apply to D3, D6
  and D8 alike.
- Code locations: `src/value_type/core.clj` (`check-field-metadata`,
  `value-field-deftype`, `identity-field-deftype`, `install-receiving!`,
  `defvaluetype*`); `src/value_type/impl/{fields,identity_backend,value_backend,transform,loader,runtime}.clj`.
- Evidence and tests: `spike/record-value-type/{gate.clj,README.md}`,
  `ramora/deftype-star-design/feasibility-gate.md` (records the gate.clj
  hash), `ramora/deftype-star-design/evidence/`; the tests named in
  design-steps; non-nil wrapping clause in `ramora/non-nil-field-design.md`
  (Public contract) and D4 in `ramora/non-nil-field-design/decisions.md`.

## Design-step resolution (2026-11-03)

- All 7 design steps resolved; no `SCOPE_QUESTION:` items. Mirrored into
  ramora: Public contract (caller `assoc`, wrapping), D5 (receiver derives
  shape), D6 (caller `assoc`, wrapping), D7 (constant evidence), D8
  (delegating write → `:unsupported-shape`). Embodiment and commitments
  change at implementation (Acceptance 10), not now.
- Probed on Clojure 1.12.6 with a plain `deftype*` record fixture:
  `Compiler$ObjExpr.emitValue` checks `IType` before `IRecord`, so a fixture
  that implements `IType` is emitted as a main-constructor call, not
  `create`. Keep record fixtures `IRecord`-only. Cause chains seen:
  constant → `ExceptionInInitializerError`; `load-string` literal →
  `CompilerException`; `read-string`, `create` and
  `Reflector/invokeConstructor` → unwrapped (Reflector unwraps
  `InvocationTargetException`).
- `deftype_star_record_test.clj` `record-form` merges `__extmap` last in
  `entrySet`, so `(construct c [valid … nil {:a nil}])` gives the
  nil-in-map-view constant fixture without new fixture code.
- Identity backend rejects jumps, switches and try blocks in constructors;
  compiler delegating constructors have none. Delegation detection needs an
  `invokespecial <init>` with owner = target; `NEW` of the target in a
  constructor is the ambiguous case D8 rejects.
- Receiver order hint: `fields/receiving-specifications` runs metadata
  checks, then the modes check, then tag resolution. `:hidden-field` must
  go between metadata and modes (design: directly after metadata keys).

## Design review — architecture, pass 2 (2026-11-03)

- no architectural review feedback
- Revised design checked against the ramora deftype* design (D3–D8), non-nil
  D3–D5 and `src/value_type/core.clj`: receiver-derived shape, the
  shape-independent ASM delegation rule and the commitment rule fit.
- Public surface: the existing receiving macros are hidden from
  `doc/API.md` by `:no-doc` overrides in `bb/value_type/api_docs.clj`. New
  `defvaluetype*` receiving macros need the same override, so that
  `defvaluetype*` stays the only new public surface and `bb api-docs --check`
  passes.

## Design review — ambiguity, pass 2 (2026-11-03)

- ambiguity review added 1 new design step
- Gate bytes: the D3 gate used a spike-local transform, not the production
  path (`feasibility-gate.md` Limits). The non-nil gate runs before D6/D8
  exist, so it can do the same; then record "production transform not
  covered" in its limits. Not filed: the design leaves the method to the
  gate record.

## Design review — inconsistency, pass 2 (2026-11-03)

- inconsistency review added 2 new design steps

## Notes for addressing pass-2 design-steps

- Principles: as in the pass-1 notes (edit design text and mirror ramora in
  one edit; no scope change). For the coverage step, name a minimal set of
  shape × representation cells, so that each acceptance criterion stays
  bounded evidence. Do not require the full path × mode × shape matrix.
- Coverage step, existing harnesses to extend:
  `test/compatibility/value_type/{deftype_star_transfer_checks,deftype_star_transfer_test,deftype_star_aot_checks,field_transfer_checks,field_aot_checks,field_policy_checks,identity_receiving_checks}.clj`,
  `test/runtime/value_type/{deftype_star_test,deftype_star_record_test,non_nil_fields_test,value_receiving_test}.clj`.
- `:removed-field-access` step: change the Contract wording in design.md,
  ramora D4 ("the loader rejects") in
  `ramora/deftype-star-design/decisions.md`; the Embodiment element
  `value-type.impl.value-backend` already names the right owner. Code:
  `src/value_type/impl/value_backend.clj`.
- D1 step: precedent wording in `ramora/defvaluetype-design.md` (Context:
  "For non-nil fields, receiving analysis installs a local capture loader
  for the selected backend"). D1 also omits the existing fallback expansion
  (`runtime/guarded-deftype*`), so a general wording is better than an extra
  expansion literal. Current expansions: `defvaluetype*` and
  `value-expansion` in `src/value_type/core.clj`.

## Pass-2 design-step resolution (2026-11-03)

- All 3 pass-2 steps resolved; no `SCOPE_QUESTION:` items. Mirrored into
  ramora: D1 (expansion per enabled fields, refers to D5), D4 (value backend
  owns the removed-field check), D7 (fallback tests for both shapes).
- Shape-coverage harnesses already have both shapes, without enabled fields:
  `deftype_star_transfer_test` cells are mode × expand × shape;
  `deftype_star_aot_checks` fixture defines `Plain` and `Rec`. Add enabled
  fields there instead of new harnesses.
- Acceptance 6 evidence: if the receiver passed `:plain` for a record form,
  `->value-class` (one constructor only) gives `:unexpected-constructor`. So
  a received value record with no hash fields proves the `:record` shape.

## Design review — architecture, pass 3 (2026-11-03)

- no architectural review feedback
- Pass-2 resolutions fit: ramora D1 (expansion per enabled fields, refers to
  D5), D4 (value backend owns `:removed-field-access`) and D7 (fallback for
  both shapes) agree with design.md Architecture and with D5/D8.
- Not filed (doc organization, not design fit): `ramora/deftype-star-design.md`
  (1450 words) and `decisions.md` (2026 words) exceed the ramora 1000-word
  split rule, from before this task. Acceptance 10 adds Embodiment text. Plan
  hint: put new Embodiment detail in a linked file, as
  `ramora/defvaluetype-design/embodiment/` does, instead of growing the file.

## Design review — ambiguity, pass 3 (2026-11-03)

- ambiguity review added 1 new design step
- Checked and not filed: `create` with an absent key (ramora D6: gives nil,
  then `NullPointerException`); D8 "creates an instance … and calls one of
  its constructors" means a `NEW` of the target in a constructor, because
  `new Target(…)` always calls `<init>`. The current identity backend
  accepts that case, but no Clojure-emitted constructor contains it.

## Design review — inconsistency, pass 3 (2026-11-03)

- inconsistency review added 1 new design step

## Notes for addressing pass-3 design-steps

- Principles: as in the pass-1 notes. Edit the design text, and change the
  ramora text in the same edit. Do not change scope. Choose the minimal
  answer that gives bounded evidence.
- Receiving-test step: the `defvaluetype` receiving repeat of
  `:mutable-field` is `check-fields` (`src/value_type/core.clj`) at the start
  of `value-field-deftype` and `identity-field-deftype`. No test covers it.
  Precedent for a test that calls an internal macro directly:
  `receiving-identity-native-modes-test` in
  `test/compatibility/value_type/identity_receiving_test.clj`. It calls
  `macroexpand-1` on the internal form and gets the cause from the
  `Compiler$CompilerException`. If you choose tests, two cases (one for
  `:record-shape`, one for `:hidden-field` with an earlier metadata error)
  give the order evidence. Put the choice in Acceptance 5 or 6, and in
  ramora D7 if it adds tests.
- Hash-argument step: the sentence is in three places:
  `ramora/deftype-star-design.md` (Public contract), the `defvaluetype*`
  docstring (`src/value_type/core.clj`) and the generated `doc/API.md`.
  design.md does not contain it. Change the ramora text now. The docstring
  and `doc/API.md` change at implementation (Acceptance 10, then
  `bb api-docs`). Optionally, name the docstring sentence in Acceptance 10.

## Pass-3 design-step resolution (2026-11-03)

- Both pass-3 steps resolved; no `SCOPE_QUESTION:` items.
- Receiving repeats: chose tests (Acceptance 6, ramora D7), not
  defence-only. Four adjacent-pair cases per receiving macro. Fixtures must
  carry valid enforcement modes: in the receiver the modes check
  (`:invalid-field-enforcement`) runs between `:hidden-field` and tag
  resolution, so invalid modes mask the `:hidden-field`/tag pair. Declared
  fields always precede hidden fields, so the metadata/`:hidden-field` pair
  proves category order only if the metadata error is on a later field
  (for example `^:non-nil __meta` with `^{:non-nil :x} __hash`).
- Hash-argument sentence: ramora Public contract changed now. Still old at
  implementation: `defvaluetype*` docstring (`src/value_type/core.clj`),
  `doc/usage.md` (wrapped over two lines, ~line 427) and generated
  `doc/API.md` (regenerate with `bb api-docs`).

## Design review — architecture, pass 4 (2026-11-03)

- no architectural review feedback
- Pass-3 resolutions fit: internal-form tests of the `defvaluetype*`
  receiving macros follow the `identity_receiving_test.clj` precedent; the
  hash-argument scope sentence agrees with D6/D8 main-constructor checks.

## Design review — ambiguity, pass 4 (2026-11-03)

- no ambiguity review feedback
- Checked and not filed: `:hidden-field` with several hidden fields marked
  `:non-nil true` gives no explicit `:field` rule; use field order, as the
  other declaration checks do. Hidden names occur only in record shape
  (D2 gives `:record-shape` first), so the check never sees a plain form.
- Receiving macro arity: `defvaluetype*` takes `tagname classname fields`;
  the `defvaluetype` receivers take `class-name modes name fields`. The new
  receivers carry `tagname` and `classname` (plan detail).

## Design review — inconsistency, pass 4 (2026-11-03)

- no inconsistency review feedback
- Correction to the ambiguity pass-4 note: ramora D6 already fixes the
  `:hidden-field` `:field` as the first such field in field order.

## Notes after design review, pass 4

- Pass 4 added no design steps; all items in `design-steps.md` are checked.
  The design review has converged. Next step: create `plan.md` and `steps.md`.
- Plan hints are spread over earlier notes. Use them, do not re-derive:
  `fields` phase split for `:hidden-field` (architecture pass 1);
  `:no-doc` override in `bb/value_type/api_docs.clj` (architecture pass 2);
  ramora split rule for new Embodiment text (architecture pass 3);
  receiving-test fixtures and the remaining old docstring and usage
  sentences (pass-3 resolution); receiver arity (ambiguity pass 4).

## Plan review — ambiguity (2026-11-03)

- ambiguity review added 1 new design step
- Checked and not filed: after D6, `:removed-nil-restricted` for
  `defvaluetype*` comes from `fields/non-nil?` ("use :non-nil instead."),
  not the `check-field-metadata` text ("defvaluetype* does not support
  it"). This follows from "fields stays the one owner"; no test asserts the
  old text. The identity "on the receiver" detection needs no stack
  analysis: D8 rejects any constructor with a `NEW` of the target, so a
  target `invokespecial <init>` in a constructor without that `NEW` is a
  delegation.

## Plan review — inconsistency (2026-11-03)

- inconsistency review added 2 new design steps
- Checked and not filed: Acceptance 10 says the Embodiment "elements name"
  the new parts, and the plan puts detail in a linked file. Both hold if the
  Embodiment names each element in brief and links the detail.

## Notes for addressing plan-review design-steps

- Principles: these steps are about the plan, not the design. Resolve them
  in `plan.md`, `steps.md` and `munera/plan.md`. Do not change design.md
  scope or acceptance. Keep the gate first and the dependency order. Choose
  the minimal answer that keeps each slice's evidence bounded.
- Backend-test step: the minimal option keeps the slice order. Slices 3 and
  4 test the backends through `loader/install!` with specifications and
  shape. Then slice 5 and later run the public `defvaluetype*` tests for
  Acceptance 2, 3 and 7, including the Java 8 run (Java 8 reaches
  enabled-field identity output only through the fallback receiving
  macro). Precedents: `test/compatibility/value_type/identity_backend/fixture.clj`
  (`:identity :plain` fixture macros), `test/runtime/value_type/impl/loader_test.clj`
  (value install with specifications) and
  `test/cross-jvm/value_type/identity_receiving_cross_jvm_test.clj`.
  `value_backend/prepare` docstring says that `:record` ignores
  specifications; slice 3 changes this.
- Suites step: get the commands from `doc/development/runtime.md` (build
  test ~line 457; tooling uses `-M:quickdoc:tooling-test`, release uses
  `-M:release:release-test`, as `mementum/state.md` says). Slice 7 changes
  docstring text that `api_docs_test.clj` asserts (lines ~30–36). Keep
  that text, or update the test in the same slice.

## Plan-review step resolution (2026-11-03)

- All 3 plan-review steps resolved in `plan.md`, `steps.md` and
  `munera/plan.md`; design.md unchanged. Slice order kept; slices 5 and 6
  renamed ("Receiving, public forms and AOT", "Fallback and cross-JVM").
- Backend slices: one fixture macro in `deftype_star_record_test.clj`
  (`loader/install!` + `deftype*`) is the `record-form` `definer` for both
  backends. `record-form` and `construct` are private to that file, and
  `test/compatibility` has no full `IRecord` record helper, so
  construction-path tests stay in `test/runtime`. Delegation-rule tests
  stay in `identity_backend_test.clj` (fallback suite, Java 8).
- `api_docs_test.clj` does not assert the "does not validate constructor
  arguments" sentence or any non-nil text now; slice 7 changes it only if
  edited docstring text is asserted. No release test mentions
  `defvaluetype*`.
- Suite commands come from `doc/development/runtime.md` (runtime, fallback,
  cross-JVM, build) and `doc/development/quality-checks.md` (tooling with
  `-Srepro`, release); aliases in `deps.edn`.

## Plan review — ambiguity, pass 2 (2026-11-03)

- ambiguity review added 2 new design steps
- Checked and not filed: slice 2 leaves "split or expose" the `fields`
  phases open; both meet the design, so the choice is an implementation
  detail. Note: `:hidden-field` applies only to `defvaluetype*`, so a check
  inside `fields/specifications` needs a flag or shape argument.
- Java 8 step: `deftype*-policy-test` (`runtime_test.clj`) already has
  plain and record cells with `:stored 42` on all fallback-suite JVMs; it
  is the cheapest place for the Acceptance 7 identity evidence.

## Plan review — inconsistency, pass 2 (2026-11-03)

- inconsistency review added 1 new design step
- Checked and not filed: plan slice 8 says the tooling suite asserts
  docstring text "that slice 7 changes". The asserted `defvaluetype*`
  phrases (`api_docs_test.clj` ~lines 24–37: hash-argument obligations,
  `:record-shape`, `:removed-field-access`, "serialization guarantees")
  stay. Keep them when the docstring is rewritten.

## Notes for addressing plan-review pass-2 design-steps

- Principles: as in the earlier plan-review notes. Resolve in `plan.md`,
  `steps.md` (and `munera/plan.md` if the next step changes). Do not change
  the design.md scope or acceptance. Choose one option per step; do not
  leave "or" in the plan. Keep each slice's evidence bounded.
- Java 8 step: `deftype*-form` (`test/compatibility/value_type/runtime_test.clj`
  ~line 131) builds the cell and reads `:stored` from `(new cn 42)`. For the
  record cell, that is the declared-fields delegating constructor, so it
  already goes through the main-constructor checks. The record cell has no
  `:implements`; check whether `create` is emitted before you name it as a
  path. Rejection needs new outcome keys (for example, the exception class
  from the cause chain for `nil` and wrong type); `cell-registry/outcome`
  is the error-capture precedent.
- Fixture step: `fields/select-enforcement` (`src/value_type/impl/fields.clj`)
  gives `:native` only for value-class field types, when the thunk returns
  true. A native field in the value fixture needs a value-class tag (a
  `defvaluetype` class defined first). Identity fixtures must pass a thunk
  that returns false, or set `:generated`.
- Docs step: the docstring lines are in `src/value_type/core.clj`
  (`defvaluetype*`, ~lines 371–395); the usage paragraph is
  `doc/usage.md` ~lines 409–413. Today, `non-nil-unsupported` also occurs
  in `ramora/deftype-star-design.md` ~line 185 (the Gap paragraph that
  slice 7 removes) and in the tests that slice 2 replaces. After slice 7
  the grep must find nothing outside `munera/`.

## Plan-review pass-2 step resolution (2026-11-03)

- All 3 pass-2 plan-review steps resolved in `plan.md` and `steps.md`;
  design.md and `munera/plan.md` unchanged. Slice 6 renamed "Fallback
  policies and baseline JVMs".
- Names chosen in the plan (not yet in code): receivers
  `value-field-deftype*`/`identity-field-deftype*`; test macros
  `value-installed*`, `identity-installed*`, `identity-receiving*` in
  `deftype_star_record_test.clj`.
- Probed on Clojure 1.12.6 (JDK 25): `deftype*` with the record tail and
  `:implements []` emits `public static create(IPersistentMap)`, so the
  `deftype*-policy-test` record cell can check `create` with no interface.
- Deftype fields with a non-primitive tag have `Object` constructor
  parameters, so `(new cn "s")` compiles and the generated cast gives the
  `ClassCastException`.
- `cell_registry.clj`: `failed-call` gives `{:status :unexpected-success}`
  when the call returns; `thrown` `:class` is the root of the cause chain.
- Cross-JVM suite runs `deftype_star_aot_test.clj` over
  `deftype_star_aot_checks`; no other cross-JVM test covers `defvaluetype*`.
- Other stale text for slice 7: `doc/usage.md` ~line 431 ("fallback
  expansion is guarded `deftype*` output with no transform") and line 437
  ("Exclusions: `:non-nil` fields"). Now `non-nil-unsupported` occurs
  outside `munera/` only in code, the tests that slice 2 replaces,
  `doc/usage.md`, `doc/API.md` and the ramora Gap paragraph.

## Plan review — ambiguity, pass 3 (2026-11-03)

- ambiguity review added 2 new design steps
- Transfer step context: `config/configuration` always carries the
  `:valhalla` runtime, so the fallback suite can use
  `config/value-producer` on every host. The `defvaluetype` value-transfer
  precedent is the cross-JVM `field_transfer_test.clj` with
  `field_transfer_checks/consume-supported` (construction results).
- Checked and not filed: between slices 2 and 5, a valid enabled-field
  `defvaluetype*` form expands through the current paths (no
  specifications), so it defines a class without checks. Transient; no
  existing test asserts it. Do not release between these slices.
- Slice 3 note: record tests `eval` forms, and `fields/resolve-tag` uses
  `*ns*` at expansion. Under the test runner `*ns*` can differ from the test
  namespace, so give a value-class field tag as a fully qualified class
  symbol.

## Plan review — inconsistency, pass 3 (2026-11-03)

- inconsistency review added 1 new design step
- The value-capability order matters only on an incapable receiver: the
  slice 5 internal-form tests run on a capable JVM, so their adjacent pairs
  hold for both orders. An internal `value-field-deftype*` form with a
  metadata error, received on Java 8, shows the difference
  (`:value-capability-unavailable` first).
- Checked and not filed: the Contract `assoc` path has no `record-form`
  method. A caller `assoc` (as `defrecord`) calls the declared-fields +
  `__meta` + `__extmap` delegating constructor, so the delegating-constructor
  tests cover it. `munera/plan.md` and the slice 8 commands agree with
  `doc/development/runtime.md`.

## Notes for addressing plan-review pass-3 design-steps

- Principles: as in the earlier plan-review notes. Resolve in `plan.md`
  and `steps.md` only; design.md and ramora D5 ("repeats the declaration
  checks, in the D6 order") already agree, so they do not change. Choose one
  option per step. Keep the existing cells and fixtures without enabled
  fields (Acceptance 8).
- Transfer step: `transfer/modes` (`:eval :analysis :source`) and
  `transfer/expands` are in `test/compatibility/value_type/transfer_checks.clj`.
  The fallback-suite receiver is `:selected` (the host JVM). Each host run
  gives one receiver, so value-producer cells there need an expected value
  per `available?`. Incapable-receiver expectation precedent: `unavailable`
  in `test/cross-jvm/value_type/field_transfer_test.clj`
  (`value-capability/unavailable`). Do not run cross-JVM namespaces with
  `:fallback-test` (`doc/development/runtime.md` ~line 86).
- AOT step: the helpers are in `test/compatibility/value_type/aot_fixture.clj`
  (`source-free-consumers`, `incompatible-consumers`, one owner per call).
  `incompatible-value-artifact-test` loops over a literal owner list; new
  classes need new entries there. Enforcement-fact precedent:
  `consumer_aot_checks/checked-outcomes` and `checked-rejections`
  (unwraps `InvocationTargetException`).
- Order step: `require-value-capability!` (~line 24) and
  `value-field-deftype` / `identity-field-deftype` (~lines 199–229) in
  `src/value_type/core.clj`.

## Plan-review pass-3 step resolution (2026-11-03)

- All 3 pass-3 plan-review steps resolved in `plan.md` and `steps.md`;
  design.md and `munera/plan.md` unchanged. New plan bullets: "Transfer
  evidence (slice 5)", "AOT evidence (slice 5)"; "Receiving boundary" now
  gives the full check order of each receiver.
- Transfer: chose the fallback suite, not cross-JVM. A fallback-suite test
  can start a Valhalla producer child (precedent:
  `test/compatibility/value_type/aot_test.clj` with `config/value-producer`).
  `value-type.value-capability` is in `test/cross-jvm` and is not on the
  `:fallback-test` classpath, so the incapable-receiver expectation is
  written in place (class and `:data`, not the message).
- Only enabled-field cells use the value producer: without enabled fields
  the value expansion installs capture at macroexpansion and is not a
  transferable receiving form.
- Enforcement facts in slice 5 use `consumer-aot-checks/checked-outcomes`
  and `checked-rejections` (precedent in the transfer and AOT harnesses);
  slice 6 keeps its `:nil`/`:wrong-type` keys in `runtime_test.clj`.
- Receiver order source: `fields/receiving-specifications` runs metadata
  (`enabled-fields`), modes, then tag/type for all fields, then native type
  eligibility per field. `value-field-deftype` checks value capability
  before it, native field capability after it.

## Plan review — ambiguity, pass 4 (2026-11-03)

- ambiguity review added 2 new design steps
- Checked and not filed: the slice 4 D8 rejection cases cannot come from
  `deftype*` source; use hand-built ASM bytes as `constructor-bytes` and
  `constructor-coverage` in `identity_backend_test.clj` do (the existing
  "extra constructor" case, a `()V` that calls `Object.<init>`, stays a
  rejection under D8). "Remove the hard-coded `:plain` … for this path"
  means a shape argument for `install-receiving!`; the `defvaluetype`
  receivers pass `:plain`.

## Plan review — inconsistency, pass 4 (2026-11-03)

- inconsistency review added 1 new design step
- Checked and not filed: `munera/plan.md` (next: plan review, then
  slice 1) and the slice 8 commands agree with `doc/development/runtime.md`.
  All present `non-nil-unsupported` matches outside `munera/` are in files
  that slices 2 and 7 change. The plan's value thunk
  `#(:available? (runtime/field-capability))` leaves out the `available?`
  conjunct of `defvaluetype`. The runtime suite is always capable, so the
  result is the same.

## Notes for addressing plan-review pass-4 design-steps

- Principles: as in the earlier plan-review notes. Resolve the steps in
  `plan.md` and `steps.md` only. design.md, ramora and `munera/plan.md`
  do not change. Choose one option per step and do not leave "or" in the
  plan. Keep each slice's evidence bounded: one case per receiving error,
  not a matrix over receivers, shapes and expansions.
- Receiving-errors step: `fields/receiving-specifications`
  (`src/value_type/impl/fields.clj`) gives `:invalid-field-enforcement`
  for a non-vector, a wrong count or a mode outside the permitted set. Do
  this check before tag resolution. `install-receiving!` (`core.clj`)
  gives `:not-installed`. Both messages end with `fields/direct-fix`
  ("evaluate the defvaluetype form directly"). For the
  `defvaluetype*` receivers, this names the wrong macro. Do not change
  the shared text: the design excludes changes to error text. Tests
  assert `:data`, and assert the message only for names that stay
  correct. Precedent for `:not-installed` with `defvaluetype*`:
  `value-expansion-receiving-test`
  (`test/runtime/value_type/deftype_star_test.clj`, value expansion
  without enabled fields).
- File step: `value_receiving_test.clj` is in the runtime suite (EA3
  preview, always capable). `identity_receiving_test.clj` is in the
  fallback suite (Java 8 and EA3).
- Changelog step: `defvaluetype*` is only in `## [Unreleased]` of
  `CHANGELOG.md`, so edit that bullet in place. The release suite
  (`test/release/value_type/notes_test.clj`) checks the section format,
  not the bullet text.

## Plan-review pass-4 step resolution (2026-11-03)

- All 3 pass-4 plan-review steps resolved in `plan.md` and `steps.md`;
  design.md, ramora and `munera/plan.md` unchanged. New plan bullet:
  "Receiving order and errors (slice 5)"; slice 7 names the stale
  `CHANGELOG.md` and `mementum/state.md` sentences.
- `:field-capability-unavailable` is unreachable through
  `value-field-deftype*`: `field-capability/detect` calls
  `capability/detect` (the same probe as `runtime/capability`) and, when
  available, its probe returns `{:available? true}` or throws. Thus no
  receiver test; do not try to build a JVM configuration for it.
- `fields/receiving-specifications` checks modes as vector, count of
  enabled fields and permitted set, all before tag resolution; `[]` for
  one enabled field is the minimal wrong-count case.
- `not-installed-message` and `macroexpanded-outside-compilation`
  (`src/value_type/core.clj`) also name "defvaluetype"; `:not-installed`
  tests assert `:data` and the class name only.

## Plan review — ambiguity, pass 5 (2026-11-03)

- no ambiguity review feedback
- Checked and not filed: these are implementation details, and each option
  meets the design.
  - Byte source for the slice 3 and 4 inspection tests. Value: call
    `transform/->record-value-class` on compiled fixture bytes, as
    `record-structure-test` (`test/runtime/value_type/impl/transform_test.clj`)
    does. That test already checks unchanged descriptors, delegating code and
    `create`. Identity: use the `:owner` transform of
    `identity-backend/prepare` with ASM. A generated check is
    `Objects.requireNonNull` and then `checkcast`.
  - The slice 3 step puts the companion rewrite in `->record-value-class`.
    The `:record` companion transform is in `value_backend/prepare`, and
    now it only does `check-removed-fields`. It also needs
    `field-access/rewrite-class` with the storage map, as in `:plain`.
  - The slice 3 and 4 fixture-macro tests stay after slice 5 as backend
    evidence. Slice 5 adds the public-form tests and does not replace them.

## Plan review — inconsistency, pass 5 (2026-11-03)

- no inconsistency review feedback
- Checked and not filed: the plan's transfer expectations agree with
  `cell_registry.clj`. `registry/root-failure` is `registry/failure`
  (`:class`, `:message`) plus `:data`, so the identity `error` cell expects
  `(assoc harness/fallback-rejection :data nil)` (the policy `Error` has no
  ex-data). `registry/failure` contains `:message`, so the value-producer
  incapable-receiver cells cannot use exact equality. Compare `:class` and
  the `:data` keys only, as the plan says ("message … not fixed").

## Notes after plan review, pass 5

- Pass 5 added no design steps. `design-steps.md` has no open item, so there
  is nothing to address. The plan review has converged. Next step: slice 1
  (the gate). Update the `munera/plan.md` entry when slice 1 starts.
- Slice 1 precedents: `spike/record-value-type/gate.clj` (method),
  `ramora/deftype-star-design/feasibility-gate.md` (record layout: method,
  results, limits, SHA-256) and
  `ramora/deftype-star-design/evidence/gate-ea3-preview.edn` (raw EDN form).
- Pass 5 notes give the byte sources for the slice 3 and 4 inspection tests,
  the `:record` companion rewrite location, and how to compare the slice 5
  transfer cells. Use them; do not re-derive.

## Slice 1: non-nil gate (2026-11-03)

- Gate passed on EA3 preview `-Xverify:all`: `:failed []`, 60 cases.
  Record: `ramora/deftype-star-design/non-nil-gate.md`; raw:
  `evidence/non-nil-gate-ea3-preview.edn`; SHA-256 of `non-nil-gate.clj`
  in the record. `gate.clj` unchanged (hash still `42d2284d…`).
- Command needs `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`:
  the native field tag `Pt` comes from `defvaluetype` (only the record
  transform is spike-local).
- Record literals do not evaluate elements; a value-class element needs
  `#=(ns.Pt. 7)` (read-eval). Slice 3 literal tests with a native field
  need the same, or use the compiled-constant path.
- No `NullRestricted` needs a `LoadableDescriptors` attribute here; the
  plain production `field-storage/emit!` approach worked unchanged for the
  record main constructor. Untagged `__meta`/`__extmap` are `Object`.
- Native nil: checkcast passes nil, the JVM `putfield` on the
  `NullRestricted` field throws NPE. Generated: `requireNonNull` first.
- Next: slice 2 (declaration validation). D6/D8/D3 go `firm` in slice 7.

## Slice 2: declaration validation (2026-11-03)

- Chose an option, not a phase split: `fields/specifications` and
  `fields/receiving-specifications` take an optional final map
  `{:hidden-field? true}`. The `:hidden-field` check (private
  `check-hidden-fields!`) runs on the enabled fields after the metadata
  keys and before modes / tag resolution. Name match ignores namespace.
  Slice 5 receivers pass the same option.
- `core/check-field-metadata` removed; `defvaluetype*` calls
  `(fields/specifications *ns* class-name fields {:hidden-field? true})`
  after `record-shape`. The result is discarded now; slices 3/5 use it.
  Transient: a valid enabled-field form defines a class with no checks
  until slice 5. Do not release in between.
- `:removed-nil-restricted` now has the `fields` message ("use :non-nil
  instead."). `defvaluetype*` docstring still lists `:non-nil-unsupported`;
  slice 7 rewrites it.
- `:enclosing-type` has no `defvaluetype*` declaration test: the tag must
  resolve to an existing class of the same name, which the macroexpansion
  fixture cannot give; `fields_test.clj` covers it.
- Verified: runtime suite (EA3 preview) 137/137; fallback suite Java 8 and
  EA3 62/62; `clj-fmt:check`, `clj-kondo:lint` clean.

## Slice 3: value backend (2026-11-03)

- API: `transform/->record-value-class [bs storage class-loader]` (new
  `storage` arg, same map as `->value-class`). Composes like
  `->value-class`: record transform → `storage-transform` →
  `field-access/rewrite` on all method bodies. Delegating ctors have no
  field access, so they stay byte-identical.
- `value_backend/prepare` builds `storage` for both shapes; `:record`
  companion = field rewrite, then `check-removed-fields`.
- Test macro `value-installed*` (`deftype_star_record_test.clj`) must be
  public: tests `eval` forms in another `*ns*`, and a private macro var
  fails `isMacro`. Slice 4 `identity-installed*`: reuse `installed*`.
- Value tag `NnPt` (`vt/defvaluetype`) in the test ns; literal paths use
  `#=(value_type.deftype_star_record_test.NnPt. 7)`. A string literal
  for the native field gives `ClassCastException` through both literal
  paths.
- Constant path: `construct` with extmap `{:p .. :s ..}` overrides the
  declared values in `entrySet`, so `create` gets the bad value.
- Bytecode test: `record-storage-structure-test` (`transform_test.clj`):
  descriptors, `NullRestricted` on native field only, 1 `requireNonNull`,
  2 `checkcast`, ctor descriptors and delegating code unchanged.
- Pitfall: `JAVA_CMD` does not persist across shell calls; without it the
  suite runs on Java 21 and value checks fail misleadingly.
- Verified: runtime suite (EA3 preview) 140/140; fmt, kondo clean.

## Slice 4: identity backend (2026-11-03)

- Delegation rule in `identity-backend/method-visitor`: flags
  `delegates?` (`INVOKESPECIAL owner.<init>`) and `creates?` (`NEW owner`),
  checked at the constructor `RETURN`. Order: both → "non-nil constructor
  both creates and calls the target"; delegating with writes → "delegating
  non-nil constructor writes a field"; else the old complete-writes rule.
  No receiver stack tracking: `NEW` + call is rejected instead (D8).
- Evidence: `delegating-record-constructors` (fallback suite, real
  `defrecord` fixture `Rec` in `identity_backend/fixture.clj`, `:identity
  :record`), `constructor-delegation-rule` (ASM bytes: accept + 2
  rejections), `identity-{record,plain}-non-nil-test` through
  `identity-installed*` (runtime suite), `identity-record-storage-structure-test`
  (`transform_test.clj`).
- Pitfall: ASM rebuilds the constant pool, so delegating-constructor code
  differs in raw bytes; compare resolved instruction strings.
- Pitfall: `checks/checked-outcomes` calls the builder with a vector `[x]`.
- Verified: runtime suite (EA3 preview) 143/143; fallback suite Java 8,
  EA3, EA3 preview 64/64 each; fmt, kondo clean.
- EA3 java: `~/.local/share/mise/installs/java/openjdk-27.0.0-jep401ea3+1-1/bin/java`;
  Java 8: `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`.

## Slice 5, pass 1: receivers and public forms (2026-11-03)

- `value-field-deftype*` / `identity-field-deftype*` (`core.clj`) take
  `tagname classname modes fields & opts+specs`; call
  `fields/receiving-specifications` with `{:hidden-field? true}`.
  `install-receiving!` gained a final `shape` arg; `defvaluetype`
  receivers pass `:plain`.
- `defvaluetype*` now selects enforcement as `defvaluetype` does; with
  enabled fields it expands to `(let [] (<receiver>* …))`, else the old
  value expansion / `guarded-deftype*`.
- Tests: the four backend tests in `deftype_star_record_test.clj` now share
  `check-record-non-nil` / `check-plain-non-nil` / `define-non-nil`;
  public-form tests reuse them (`vt/defvaluetype*`, `identity-receiving*`).
  Order/error tests: `receiving-star-*` in `value_receiving_test.clj` and
  `identity_receiving_test.clj`; `not-installed-enabled-field-test` in
  `deftype_star_test.clj`.
- Verified: runtime 150/150; fallback Java 8, EA3, EA3 preview 66/66;
  fmt, kondo clean.
- Remaining in slice 5: transfer harness/tests, AOT fixtures.

## Slice 5, pass 2: transfer evidence (2026-11-03)

- `deftype_star_transfer_checks/produce` specs are now
  `[ns expand shape enabled?]`; the old test passes `false`.
  `consume-enabled` uses `Reflector/invokeConstructor` and
  `registry/root-failure`, so the `error` identity cell expects
  `(assoc harness/fallback-rejection :data nil)`.
- New tests `deftype*-enabled-identity-transfer-test` and
  `deftype*-enabled-value-transfer-test`. The no-capability value cells use
  a described `repeated`/`including` predicate (message not fixed).
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68; fmt, kondo clean.
- Remaining in slice 5: AOT fixtures `PlainNonNil`/`RecNonNil`.

## Slice 5, pass 3: AOT evidence (2026-11-03)

- `deftype_star_aot_checks`: `PlainNonNil`/`RecNonNil` appended to
  `fixture-source` (after `Plain`/`Rec`, so the `error` identity load still
  fails at the first form). `facts` takes the `classes` key; checked keys
  add `:rejections` via `checks/checked-outcomes` on the same reflective
  constructor with `x` replaced. `expected` covers all four keys.
- `deftype_star_aot_test`: only the owner list changed; artifact tests pick
  up the extended `expected`.
- Run one cross-JVM ns: `clojure -M:cross-jvm-test --namespace <ns>`.
- Verified: `deftype-star-aot-test` (Java 8 host) 3/3, 180 assertions;
  fallback suite Java 8 68/68; fmt, kondo clean. Slice 5 complete.
- Next: slice 6.

## Slice 6: fallback policies and baseline JVMs (2026-11-03)

- `runtime_test.clj`: `deftype*-form` has a 3-arity with a `checks`
  child-form map merged into the outcome; `non-nil-checks [record?]` gives
  `:nil`/`:wrong-type` (and `:create-*` via `(. cn create {:x …})`).
  Shared `non-nil-field`, `record-tail`, `rejected-checks`; the old record
  cell now uses `record-tail` (same fields).
- On EA3 preview `Long` is a value class, so the cells get `:native`;
  NPE/CCE are the same root classes as the identity output.
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68 each; a
  deliberately wrong `:stored` failed on Java 8 (cells run); fmt, kondo
  clean.
- Next: slice 7 (docs and commitments).

## Slice 7: docs and commitments (2026-11-03)

- Docstring, `doc/usage.md`, `CHANGELOG.md` (bullet edited in place),
  `mementum/state.md`, `doc/API.md` regenerated. Docstring and usage also
  name `:unsupported-shape` (D8) for identity output.
- Ramora: Embodiment rewritten, detail in new
  `ramora/deftype-star-design/embodiment.md`; non-nil embodiment names
  `defvaluetype*`; D3, D6, D8 `firm`.
- Pitfall: `api_docs_test` matches phrases on one line
  ("serialization guarantees"); keep asserted phrases unwrapped.
- Verified: tooling 11/11, release 12/12, `api-docs --check`, fmt, kondo,
  vale clean. No `api_docs_test` change needed.
- Next: slice 8 (full regression).

## Slice 8: quality and regression (2026-11-03)

- Runtime (EA3 preview) 150/150; fallback Java 8, EA3, EA3 preview 68/68
  each; cross-JVM (Java 8 host) 97/97; tooling 11/11; release 12/12;
  build (EA3) 9/9. Commands as in steps.md slice 8.
- `bb api-docs --check`, `clj-fmt:check`, `clj-kondo:lint` (0 errors, 0
  warnings; one pre-existing info), `vale` clean.
- `git grep non-nil-unsupported -- ':!munera'`: no match.
- Limits: EA3 is the only Valhalla build tested; gate limits as in
  `ramora/deftype-star-design/non-nil-gate.md`.
- All slices done. Next: implementation review.

## Implementation review (2026-11-03)

- added 2 steps to be addressed

## Review follow-ups (2026-11-03)

- addressed 2 review steps: private `value-receiving` and
  `identity-receiving` helpers (`core.clj`) take shape, options and
  definition; the four receiving macros call them, check order unchanged.
  Docstring: `:unsupported-shape` on its own identity output line;
  `doc/API.md` regenerated.
- Verified: runtime (EA3 preview) 150/150; fallback Java 8, EA3, EA3
  preview 68/68 each; cross-JVM 97/97; tooling 11/11; `api-docs --check`,
  fmt, kondo, vale clean.

## Implementation review, pass 2 (2026-11-03)

- added 1 step to be addressed

## Review follow-ups, pass 2 (2026-11-03)

- addressed 1 review step: private `policy-expansion` (`core.clj`) owns
  capability, enforcement selection, modes and the four-way branch;
  `defvaluetype` and `defvaluetype*` validate first, then call it with
  per-macro receiving form functions, definition and guarded form.
- Verified: runtime (EA3 preview) 150/150; fallback Java 8, EA3, EA3
  preview 68/68 each; cross-JVM 97/97; `doc/API.md` regenerated
  (source line links); `api-docs --check`, fmt, kondo
  clean.

## Implementation review, pass 3 (2026-11-03)

- added 3 steps to be addressed

## Review follow-ups, pass 3 (2026-11-03)

- addressed 3 review steps: private `with-storage` (`transform.clj`)
  composes constructor transform, storage and field-access rewrite for
  `->value-class` and `->record-value-class`; private `evaluate-twice`
  (`deftype_star_transfer_checks.clj`) takes the failure function and
  extra facts for `consume` and `consume-enabled`; public
  `cell-registry/record-tail` replaces `record-tail`, `star-tail` and the
  inline tail of `fields`.
- Verified: runtime (EA3 preview) 150/150; fallback Java 8, EA3, EA3
  preview 68/68 each; cross-JVM 97/97; fmt, kondo clean.

## Implementation review, pass 4 (2026-11-03)

- added 1 step to be addressed

## Review follow-ups, pass 4 (2026-11-03)

- addressed 1 review step: private `main-descriptor`, `method-elements`,
  `field-types`, `null-check?` and `ctor-code-by-descriptor` (optional
  code function) in `transform_test.clj`; the three record structure
  tests use them.
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Implementation review, pass 5 (2026-11-03)

- added 3 steps to be addressed

## Review follow-ups, pass 5 (2026-11-03)

- addressed 3 review steps: `constructor-bytes` takes an optional second
  constructor body; private `super-call`; `delegation-bytes` removed
  (`identity_backend_test.clj`). Private `root-class` builds the
  `failed-call` child forms of `non-nil-checks`; the "record non-nil" cell
  gets `:create-nil`/`:create-wrong-type` by `set/rename-keys` of
  `rejected-checks` (`runtime_test.clj`). `nn` takes one argument
  (`value_receiving_test.clj`).
- Verified: runtime (EA3 preview) 150/150; fallback Java 8, EA3, EA3
  preview 68/68 each; fmt, kondo clean.

## Implementation review, pass 6 (2026-11-03)

- added 1 step to be addressed

## Review follow-ups, pass 6 (2026-11-03)

- addressed 1 review step: `installed*` (`deftype_star_record_test.clj`)
  gets the shape from `@#'vt/record-shape`; name comparison removed.
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Implementation review, pass 7 (2026-11-03)

- added 1 step to be addressed

## Review follow-ups, pass 7 (2026-11-03)

- addressed 1 review step: private `transfer-cells [producer enabled?
  consume expected & cell-keys]` owns the producer, spec, policy loop and
  cell construction (`deftype_star_transfer_test.clj`); `fallback-keys`
  and `identity-output` hold the shared cell keys and identity facts; the
  three transfer tests call it.
- Verified: fallback Java 8, EA3, EA3 preview 68/68 each; fmt, kondo clean.

## Implementation review, pass 8 (2026-11-03)

- added 2 steps to be addressed

## Review follow-ups, pass 8 (2026-11-03)

- addressed 2 review steps: private `direct-constructor` compiles
  `(new cn …)` per arity; record paths add `:declared-direct`,
  `:meta-extmap-direct`, `:main-direct`; plain check runs `:reflection`
  and `:direct` (valid, `nil`, wrong type). Namespace docstring names the
  non-nil fixture macros, `vt/defvaluetype*` and `identity-receiving*`;
  `check-record-non-nil` counts hash fields with `field-names`
  (`deftype_star_record_test.clj`).
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Implementation review, pass 9 (2026-11-03)

- no review feedback

## Test review (2026-11-03)

- added 1 step to be addressed

## Test review follow-ups (2026-11-03)

- addressed 1 review step: private `check-enforcement` (with
  `null-restricted?`) asserts `NullRestricted` on `p` only in value output
  and on neither field in identity output; `check-record-non-nil` and
  `check-plain-non-nil` call it (`deftype_star_record_test.clj`).
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Test review, pass 2 (2026-11-03)

- added 1 step to be addressed

## Test review follow-ups, pass 2 (2026-11-03)

- addressed 1 review step: `record-form` takes optional `:interfaces`
  and `:methods`; the non-nil record fixture implements `NnReader`, whose
  `reader` returns a closure that reads `(.-p this)`, `(.-s this)` or the
  nullable `__extmap` through the captured receiver; `check-record-non-nil`
  calls both branches on each construction path (all four record
  definers, value output included) (`deftype_star_record_test.clj`).
- Non-vacuity: with the `:record` companion rewrite removed in
  `value_backend/prepare`, the two value record tests error (20
  assertions); a bare `[p s]` closure did not fail (it captures values,
  see memory `companion-field-tests-need-captured-receivers`).
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Test review, pass 3 (2026-11-03)

- added 2 steps to be addressed

## Test review follow-ups, pass 3 (2026-11-03)

- addressed 2 review steps: `check-record-non-nil` tests a wrong-type
  native field through both literal paths (guard removed; slice 3 note
  corrected); `removed_field_test.clj` adds an owner (`__hash`) and a
  companion (`__hasheq`) case with `^String ^:non-nil a` (non-empty
  storage), both `:removed-field-access`.
- Verified: runtime (EA3 preview) 150/150; fmt, kondo clean.

## Test review, pass 4 (2026-11-03)

- no test review feedback

## Test review, pass 5 (2026-11-03)

- added 1 step to be addressed

## Test review follow-ups, pass 5 (2026-11-03)

- addressed 1 review step: `consumer-aot-checks` gains public
  `native-field?` (moved from `field_transfer_checks.clj`) and
  `enforcement-facts` (`:native?`, `:type`); `consume-enabled` and the
  AOT `facts` of enabled fixtures add them for `x`; `expected` and the
  enabled transfer cells expect value `:native? true`, identity
  `:native? false`, `:type "java.lang.Long"`.
- Non-vacuity: value cell expecting `:native? false` fails on EA3 preview
  (only that receiver is capable; without preview the cells give
  `:value-capability-unavailable`).
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68 each; cross-JVM
  97/97; fmt, kondo clean.

## Test review, pass 6 (2026-11-03)

- added 2 steps to be addressed

## Test review follow-ups, pass 6 (2026-11-03)

- addressed 2 review steps: `consumer-aot-checks` gains public
  `enabled-facts` (field x, build fn: `enforcement-facts`, `:stored`,
  `:rejections`) and `enabled-expected` (`value?`); AOT `facts`/`expected`,
  `consume-enabled` and both enabled transfer tests use them.
  `enforcement-facts` takes `:type` from `field-facts`;
  `consume-supported` merges `enforcement-facts`.
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68 each; cross-JVM
  97/97; fmt, kondo clean.

## Test review, pass 7 (2026-11-03)

- added 1 step to be addressed

## Test review follow-ups, pass 7 (2026-11-03)

- addressed 1 review step: `deftype_star_record_test.clj` replaces
  `rejects?` with `rejection` (expected class, else `:returned` or the
  cause-chain classes) and `rejections` (map `:nil-p :nil-s :wrong-p
  :wrong-s`); each record and plain path compares it with
  `expected-rejections`.
- Verified: runtime suite on EA3 150/150 tests, 4896 assertions; fmt,
  kondo clean.

## Test review, pass 8 (2026-11-03)

- added 2 steps to be addressed

## Test review follow-ups, pass 8 (2026-11-03)

- addressed 2 review steps: `deftype_star_record_test.clj` adds
  `check-field-types` (`p` → `pt-tag`, `s` → `String`), called from
  `check-record-non-nil` and `check-plain-non-nil`; renames
  `root-cause-classes` → `cause-chain-classes`.
- Verified: runtime suite on EA3 150/150 tests, 4900 assertions; fmt,
  kondo clean.

## Test review, pass 9 (2026-11-03)

- added 2 steps to be addressed

## Test review follow-ups, pass 9 (2026-11-03)

- addressed 2 review steps: `declaration-error-test` gives one assertion
  per tag and type reason (`select-keys` of the data against
  `field-error`) in its own context; `transform_test.clj` adds
  `code-elements`, used by `method-elements`, `null-checks` and
  `instructions`.
- Verified: runtime suite on EA3 (preview) 150/150 tests, 4888 assertions
  (12 fewer: one per reason and shape); fmt, kondo clean.

## Test review, pass 10 (2026-11-03)

- added 2 steps to be addressed

## Test review follow-ups, pass 10 (2026-11-03)

- addressed 2 review steps: `declaration-order-test` and both
  `receiving-star-order-test` compare `error-keys` (`:value-type/error`
  and `:reason`, or `:no-error`) with one expected map per case
  (`test-support/error-keys`; a private copy in
  `identity_receiving_test.clj`, as the fallback suite does not see
  `test/runtime`); `non-nil-record-paths` comments the `:constant` path.
- Verified: runtime suite on EA3 (preview) 150/150 tests, 4888
  assertions; fallback suite on EA3 preview and Java 8 68/68 tests, 2171
  assertions; fmt, kondo clean.

## Test review, pass 11 (2026-11-03)

- no new steps

## Docs review (2026-11-03)

- added 2 steps to be addressed

## Docs review follow-ups (2026-11-03)

- addressed 2 review steps: `doc/usage.md` and the `defvaluetype*`
  docstring state that the compiler, class initialization and reflection
  can wrap the rejections (find them in the cause chain); `doc/usage.md`
  record caller obligations add "supply each enabled field" (`create` from a
  partial map, constructor-based `assoc`). `doc/API.md` regenerated.
- Verified: `bb api-docs --check`, `bb clj-fmt:check`, `bb vale`; tooling
  suite 11/11 tests, 226 assertions.

## Docs review, pass 2 (2026-11-03)

- added 1 step to be addressed

## Docs review follow-ups, pass 2 (2026-11-03)

- addressed 1 review step: `doc/development/runtime.md` "Verify `deftype*`
  forms" adds `value-receiving-test` and `impl.transform-test` (EA3 preview
  run), `identity-receiving-test` and `identity-backend-test` (three-JVM
  fallback loop); coverage names non-nil fields, receiving order, the D8
  delegation rule and the `PlainNonNil`/`RecNonNil` fixtures; Limits names
  the non-nil gate and its `--add-exports` need.
- Verified: EA3 preview run 57/57 tests, 1867 assertions (no exports);
  fallback loop on Java 8, EA3, EA3 preview 32/32 tests, 1275 assertions
  each; `bb vale` clean. AOT command unchanged, not rerun.

## Docs review, pass 3 (2026-11-03)

- added 2 steps to be addressed

## Docs review follow-ups, pass 3 (2026-11-03)

- addressed 2 review steps: `doc/usage.md` and the `defvaluetype*`
  docstring limit the "evaluated apart from its macroexpansion" failure to
  forms without enabled fields; with enabled fields, separate `eval` works
  and only `clojure.walk/macroexpand-all` gives `:not-installed` (usage links
  the receiving boundary paragraph in "Differences from `deftype`").
  `non-nil-gate.md` Limits name the runtime and fallback suites, not the task.
  `doc/API.md` regenerated.
- Verified: `bb api-docs --check`, `bb clj-fmt:check`, `bb vale`; tooling
  suite 11/11 tests, 226 assertions.

## Docs review, pass 4 (2026-11-03)

- no docs review feedback

## Code-shaper review (2026-11-03)

- added 1 step to be addressed

## Design review — architecture, pass 5 (2026-11-04)

- no architectural review feedback
- design.md is unchanged since the pass-3 resolution (`18be65f`). Ramora
  D3, D6 and D8 are now `firm`; D1, D4, D5 and D7 agree with the
  Architecture section. `ramora/META.md` and `doc/architecture.md` do not
  exist; the sources are `AGENTS.md`, `ramora/INDEX.md` and the deftype*
  design.

## Design review — ambiguity, pass 5 (2026-11-04)

- no ambiguity review feedback

## Design review — inconsistency, pass 5 (2026-11-04)

- no inconsistency review feedback
- Checked against the implemented artifacts: ramora D4, D6 (order, record
  behaviour, wrapping), gate files and README command, and the
  hash-argument sentence (docstring, `doc/usage.md`, `doc/API.md`, ramora)
  agree with design.md. Not filed: "Problem and context" uses "now" for
  the state before the task; it is the problem statement, not a contract.

## Notes after design review, pass 5

- Pass 5 (architecture, ambiguity, inconsistency) added no design steps;
  `design-steps.md` has no open item, so there is nothing to address.
  Do not edit design.md or ramora for this pass.
- The open work is the code-shaper step in `steps.md` (`deftype*-policy-test`
  cells, `test/compatibility/value_type/runtime_test.clj`). That file has
  uncommitted changes in the working tree from that step; the review
  commits left it out. Verify with the fallback suite (Java 8, EA3, EA3
  preview) before you commit it.

## Plan review — ambiguity, pass 6 (2026-11-04)

- no ambiguity review feedback
- Scope: plan.md is unchanged since the pass-4 resolution (`e93bf7f`).
  Since pass 5, steps.md only added review follow-ups. The one open item
  is the code-shaper step. Not filed: the step does not say if the local
  function gets the shape from a flag or from the fields. Both ways meet
  the step. The working tree change uses a `record?` flag.

## Plan review — inconsistency, pass 6 (2026-11-04)

- inconsistency review added 1 new design step (`munera/plan.md` entry is
  stale)
- Checked and not filed: the plan text of the fixture-macro shape
  ("`record-tail` names"), `consume-enabled` and the AOT `facts` keys are
  older than the review follow-ups that changed them (`core/record-shape`,
  `:native?`/`:type`, shared enabled-field facts). steps.md records each
  change, and the approach is the same, so plan.md does not need an edit.

## Notes for addressing plan-review pass-6 design-steps

- Edit only the task 020 entry in `munera/plan.md` (path and first bullet
  stay). Do not edit design.md, plan.md or steps.md for this step.
- The entry must say what is true when you commit it. If the code-shaper
  step is already done (the working tree change to
  `test/compatibility/value_type/runtime_test.clj` has been checked on the
  fallback suite for Java 8, EA3 and EA3 preview, and committed), say
  "next: close the task", not the code-shaper step.
- Do not include `runtime_test.clj` in the commit for this step, unless the
  same commit also completes and ticks the code-shaper step.
- Precedent: the pass-1 plan-review step changed the same entry (see the
  ticked `munera/plan.md` item in design-steps.md).

## Plan-review pass-6 step resolution (2026-11-04)

- Resolved the one pass-6 step: only the task 020 entry of `munera/plan.md`
  changed (reviews done; next: code-shaper step, then close). design.md,
  plan.md and steps.md unchanged.
- The code-shaper step stays open. The uncommitted `runtime_test.clj`
  change (local `definition` fn, `record?` flag gives `:hash-fields`) is
  not in this commit and is not verified yet. When you complete that step,
  run the fallback suite on Java 8, EA3 and EA3 preview, tick it, and
  change the `munera/plan.md` entry to "next: close the task".

## Plan review — ambiguity, pass 7 (2026-11-04)

- no ambiguity review feedback

## Plan review — inconsistency, pass 7 (2026-11-04)

- no inconsistency review feedback

## Notes after plan review, pass 7

- Pass 7 added no design steps, so there is nothing to address. Do not edit
  design.md, plan.md, steps.md or the `munera/plan.md` entry for this pass;
  the entry is still correct.

## Code-shaper follow-up (2026-11-04)

- `deftype*-policy-test` (`runtime_test.clj`): local `definition [policy
  label class-name fields record? checks extra]`; `record?` adds
  `registry/record-tail` and gives `:hash-fields` 2 only on identity output.
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68 tests, 2171
  assertions each; fmt, kondo clean. No open step remains; next: close task.

## Implementation review, pass 10 (2026-11-04)

- added 1 step to be addressed

## Implementation review follow-ups, pass 10 (2026-11-04)

- addressed 1 review step: `deftype*-form` (`runtime_test.clj`) has one
  arity `[simple-name fields checks]`.
- Verified: fallback suite Java 8, EA3, EA3 preview 68/68 tests, 2171
  assertions each; fmt, kondo clean. `munera/plan.md` entry ("Next: close
  the task") is correct; unchanged.

## Implementation review, pass 11 (2026-11-04)

- no implementation review feedback

## Test review, pass 12 (2026-11-04)

- no new steps

## Test review, pass 13 (2026-11-04)

- no new steps

## Docs review, pass 5 (2026-11-04)

- no docs review feedback

## Code-shaper review, pass 2 (2026-11-04)

- no code-shaper review feedback

## Closure (2026-11-04)

- Implementation review complete (pass 11, `93c0cd8`); later test, docs and
  code-shaper reviews gave no feedback. Knowledge extracted (`da27263`).
- Task moved to `munera/closed/`; entry removed from `munera/plan.md`.
- Open notes: EA3 is the only tested Valhalla build. The D7 gate used a
  spike-local transform. `:field-capability-unavailable` has no test because
  no JVM reaches it through `value-field-deftype*`. The shared receiving
  error text still says "evaluate the defvaluetype form directly".
