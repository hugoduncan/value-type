# Implementation

## Design review (architecture, 2026-11-06)

- `ramora/META.md` and `doc/architecture.md` do not exist. Architecture
  sources used: `ramora/INDEX.md`, D14 (`decisions/capability-report.md`),
  D17 and D18 (`decisions/probe-and-format.md`), D19 and D20
  (`decisions/compiler-boundary.md`), `embodiment/runtime.md` (dependency
  direction, boundaries, invariants).
- Constraints that the boundary and the AOT check must keep: `core` must not
  eagerly require `loader` or `transform`; `impl.runtime` requires no
  capability namespace; `impl.capability` and `impl.field-capability` resolve
  `impl.probe-builder` only through `requiring-resolve`.
- Current compiler coupling (commit `30c2d13e`): `DynamicClassLoader` in
  `impl.capability`, `impl.field-capability`, `impl.probe-builder`,
  `impl.loader`; `Compiler/LOADER` and `Compiler/writeClassFile` in
  `impl.loader`; `Compiler$HostExpr` and `Compiler/munge` in `impl.fields`.
- Precedent for an emitted capability guard: `value-receiving` emits
  `field-capability/require-field-capability!` and calls it at analysis.
- architectural review added 2 new design steps (1 `SCOPE_QUESTION:`)
- ambiguity review added 3 new design steps
- inconsistency review added 4 new design steps

## Notes for the design-steps slice

- Principles: edit only design.md, and keep the scope boundary unchanged.
  A human decides the `SCOPE_QUESTION:` step; do not resolve it by
  rewording the scope. Write in ASD-STE100 style. Ramora design docs state
  the design, not history (`ramora/INDEX.md`).
- Resolve these steps together, because they all concern the one emitted
  value-class check: architecture step 1 (key and owner), ambiguity step 2
  (position, reach, wrapper) and inconsistency step 2 (order with
  `require-field-capability!`). Ambiguity step 3 and the D17 part of
  inconsistency step 4 both affect the D17 text.
- Code to read: `src/value_type/core.clj` (`require-value-capability!`,
  `check-value-class`, `value-expansion`, `value-receiving`,
  `policy-expansion`, `not-installed-message`), `src/value_type/impl/runtime.clj`.
- Docs that the design must name: `ramora/defvaluetype-design/embodiment.md`
  (gap table), `.../decisions/compiler-and-capture.md` (D1 D3 D4),
  `.../decisions/validation-and-errors.md` (D9),
  `ramora/deftype-star-design/decisions/interface-and-shape.md` (deftype* D1),
  `doc/usage.md` (error list and "How you get the error"). `doc/API.md` is
  generated from docstrings (`bb api-docs`); do not edit it by hand.
- Test examples: `test/compatibility/value_type/aot_fixture.clj`,
  `deftype_star_aot_checks.clj`, `field_aot_checks.clj`,
  `deftype_star_transfer_test.clj`. A failed self-check is classified in
  `test/runtime/value_type/impl/capability_test.clj` with
  `support/constant-value-class`. No mechanism exists yet to force a failed
  self-check in a child AOT JVM.

## Design-steps slice (2026-11-06)

- `SCOPE_QUESTION:` (D19 and `impl.fields` `Compiler$HostExpr`/`munge`,
  `impl.loader` `Compiler/writeClassFile`) is deferred to the user. The
  boundary list in Scope is unchanged; Acceptance 1 names only
  `DynamicClassLoader`, `Compiler/LOADER` and the shape facts.
- Observed on Clojure 1.12.6 AOT, JDK 25 (scratch namespace, not in repo):
  - `require` of a saved namespace whose top-level form throws `ex-info`:
    chain `ExceptionInInitializerError` → `ExceptionInfo`; a second
    `require` gives `NoClassDefFoundError` → `ExceptionInInitializerError`.
    A form inside a function throws `ExceptionInfo` directly.
  - `-verbose:class`: the `deftype` class loads (from the top-level fn class)
    before the first form of the `let` runs. So the guard cannot stop class
    loading; design.md states this.
- Tests whose expectations change: `test/cross-jvm/value_type/value_class_switch_test.clj`
  `aot-native-field-off-test` (`:field-capability-unavailable` →
  `:value-capability-unavailable`) and `aot-value-output-off-test` (interim
  limit; now throws). Cross-JVM checks match the root failure
  (`registry/root-failure`), not the full chain.
- `core/error-message` `:not-value-class` text and the usage guide name
  `(eval (macroexpand form))` as a cause. After D20 that path installs at
  analysis, so this cause no longer applies; update both.
- No test matches the text "field capture" of `not-installed-message`.

## Design review pass 2 (architecture, 2026-11-06, after `413950e1`)

- no new architectural review feedback. The open `SCOPE_QUESTION:` step is
  not raised again.
- Planning implication: the boundary depends only on Clojure and loads on
  Java 8. Thus the boundary owns the capture proxy and the `Compiler/LOADER`
  binding, but takes the byte rewrite as a function argument. `impl.loader`
  supplies that function (transform, companion rewrite, AOT write). This
  keeps `core → loader → transform` lazy (fallback boundary in
  `embodiment/runtime.md`). The statement "`transform` has no Clojure compiler
  dependency" in `runtime.md` changes, because D19 makes the backends
  boundary users.
- ambiguity review pass 2 added 2 new design steps
  - Failed self-check in a child JVM: D14 makes the owner accessor
    `impl.capability/capability` the test replacement point. A child driver
    can `alter-var-root` it to a `:failed-self-check` result before `require`,
    with no production hook. `impl.field-capability` derives from the same
    accessor, so native-field output sees the same reason.
- inconsistency review pass 2: no new design steps; corrected the pass-2
  ambiguity step example to the D14 test replacement point

## Notes for the design-steps slice (pass 2)

- Open steps: the `SCOPE_QUESTION:` (human only; do not resolve), the failed
  self-check mechanism and the startup measurement outputs. Edit only
  design.md; keep Scope, Contract keys and the D17 commitment unchanged.
- Failed self-check: the child JVM runs a check form
  (`test/compatibility/value_type/jvm_runner.clj` `start-command`,
  `cell_harness.clj` `cell-child`). That form can replace the accessor
  before it requires the saved namespace. Thus no production hook or
  property is necessary; a hook would change the public surface (Scope does
  not include one). Example of child runtime options:
  `test/cross-jvm/value_type/value_class_switch_test.clj` (`switched`,
  `child`).
- Measurement outputs: the guard is emitted by each value receiving macro.
  Ordinary value output has the guard only after D20 is done. So the commit
  order is: boundary and D20 first, then the guard alone in one commit. The
  baseline is the parent of that commit (D20 receiving output without the
  guard). Baseline AOT output reads only `capability/value-predicate`
  (reflection), not the probe; the measured difference is the probe.

## Design-steps slice (pass 2, 2026-11-06)

- `SCOPE_QUESTION:` (D19 boundary and `impl.fields`/`impl.loader` compiler
  couplings) stays deferred to the user; scope unchanged.
- Failed self-check child check: check namespaces load no value-type
  namespace at their own load (`value_class_switch_checks.clj` docstring), so
  replace the accessor with `with-redefs-fn` on a resolved var after
  `require`. `aot-load` gives the root failure with `:value-type/error` and
  `:reason`. No build uses direct linking, so the emitted guard and
  `impl.field-capability/detect` read the accessor through its var. The value
  guard throws before `require-field-capability!`, so the field-capability
  delay is not forced. Existing unit tests use the same literal result map
  (`array_test.clj`, `field_capability_test.clj`).

## Design review pass 3 (architecture, 2026-11-06, after `daa9b54d`)

- no new architectural review feedback. The open `SCOPE_QUESTION:` step is
  not raised again.
- The emitted guard refers to `value-type.impl.capability`. `core` already
  requires that namespace eagerly, so AOT output that loads `core` resolves
  the guard var. The guard adds no new load-order dependency.
- ambiguity review pass 3: no new ambiguity review feedback
- inconsistency review pass 3 added 1 new design step (Acceptance 1 and
  test code that binds `Compiler/LOADER` or constructs `DynamicClassLoader`)

## Notes for the design-steps slice (pass 3)

- Open steps: the `SCOPE_QUESTION:` (human only; do not resolve) and the
  Acceptance 1 limit. Edit only design.md; keep Scope unchanged. The test
  rewrite is not in Scope, so prefer to limit Acceptance 1 to `src`.
- Code use in `src` now: `impl/capability.clj`, `impl/field_capability.clj`,
  `impl/probe_builder.clj`, `impl/loader.clj`. `core.clj` names
  `Compiler/LOADER` only in text (the `macroexpanded-outside-compilation`
  message and the `install-receiving!` docstring). Thus state Acceptance 1 as
  code use (construct, bind, read), not as a text match: the receiving
  message in the Contract must keep the name.
- Test use (stays outside the boundary): `test/compatibility/value_type/`
  (capture, identity loader and receiving checks, `cell_harness.clj`),
  `test/runtime/value_type/` (`probe_test_support.clj`, `loader_test.clj`,
  `core_test.clj` and others), `test/cross-jvm/value_type/`. These bind
  `Compiler/LOADER` to simulate compilation, or make a fresh
  `DynamicClassLoader` for isolation. `dev/spike/` is outside all suites.

## Design-steps slice (pass 3, 2026-11-06)

- `SCOPE_QUESTION:` (D19 boundary and `impl.fields`/`impl.loader` compiler
  couplings) stays deferred to the user; scope unchanged.
- Acceptance 1 now covers code use in `src` only. Check with a search of
  `src` for `DynamicClassLoader.`, `(binding [Compiler/LOADER` and
  `Compiler/LOADER` reads, and ignore text in `core.clj` (lines with the
  `macroexpanded-outside-compilation` message and the `install-receiving!`
  docstring).
- Test API to keep or adapt: tests call `probe-builder/define-probe` and
  `probe-builder/define-probe-class` with their own `(DynamicClassLoader.)`
  as the first argument (`probe_test_support.clj`,
  `impl/field_capability_test.clj`, `impl/probe_builder_test.clj`). If the
  boundary takes over the define function, keep a loader parameter (or
  update these tests); tests stay free to construct the loader.

## Design review pass 4 (architecture, 2026-11-06, after `963115ec`)

- no new architectural review feedback. The open `SCOPE_QUESTION:` step is
  not raised again. The Acceptance 1 limit to `src` code agrees with D19
  (D19 names `loader`, the backends and the probes, not tests).
- ambiguity review pass 4: no new ambiguity review feedback
- inconsistency review pass 4 added 1 new design step (`:not-value-class`
  message and usage cause after D20; the pass-1 note "update both" was not
  carried into design.md). The other cause (nested `defvaluetype` in a
  method body) stays.

## Notes for the design-steps slice (pass 4)

- Open steps: the `SCOPE_QUESTION:` (human only; do not resolve) and the
  `:not-value-class` message step. Edit only design.md; keep Scope and the
  Contract keys unchanged. Keep the nested-`defvaluetype` cause and fix.
- Why the replay cause goes: `(eval (macroexpand form))` now gives the
  receiving macro form; `eval` analyses it under its own `LOADER`, so the
  receiving macro installs there and catches the class.
- Text with the replay cause: `src/value_type/core.clj` `error-message`
  (`:not-value-class` branch; its `:not-installed` branch has no caller once
  `check-value-class` loses `installed`), `doc/usage.md` error list
  (`:not-installed` "(at run time)" entry and `:not-value-class` causes),
  `ramora/defvaluetype-design/decisions/validation-and-errors.md` D9 (already
  in Acceptance 6), `ramora/deftype-star-design/context.md` line ~68.
  `doc/API.md` follows from docstrings (`bb api-docs`).
- `ramora/defvaluetype-design/verification.md` (`(eval (macroexpand form))`
  item) and `assertions.md` A-24 record spike observations of `install!`;
  they are evidence, not contract text.
- No test matches the `:not-value-class` message text (only a comment in
  `test/runtime/value_type/deftype_star_test.clj` line 179).

## Design-steps slice (pass 4, 2026-11-06)

- `SCOPE_QUESTION:` (D19 boundary and `impl.fields`/`impl.loader` compiler
  couplings) stays deferred to the user; scope unchanged.
- `:not-value-class` message: only the `case` text in `core/error-message`
  changes. The `no-value-classes?` branch stays, because `check-value-class`
  is public and a direct call can reach it; in an expansion the emitted
  value guard throws first on such a JVM. The `:not-installed` branch of
  `error-message` has no caller after D20; remove it.
- Text outside Acceptance 6 before this pass, now listed: deftype*
  `context.md` scope text, and the usage paragraph in "`deftype*` forms"
  (line ~598, "fails with `:not-installed` or `:not-value-class`").

## Design review pass 5 (architecture, 2026-11-06, after `7dcda386`)

- no new architectural review feedback. The open `SCOPE_QUESTION:` step is
  not raised again. The pass-4 change (`:not-value-class` text) stays in
  `core/error-message`, the owner of `check-value-class`; D9 owns the key.
- ambiguity review pass 5: no new ambiguity review feedback
- inconsistency review pass 5: no new inconsistency review feedback
  - Usage error-list text that changes (covered by Acceptance 6): the
    `:not-installed` "(at run time)" entry, the replay cause of
    `:not-value-class`, and `:value-capability-unavailable` described as
    receiving-boundary only (now also the emitted guard at evaluation).

## Notes for the design-steps slice (pass 5)

- Pass 5 added no steps. The only open step is the `SCOPE_QUESTION:`; a
  human decides it. Do not edit design.md to resolve it.
- If the human includes the other couplings in the boundary, change D19
  (`ramora/defvaluetype-design/decisions/compiler-boundary.md`), Scope,
  Acceptance 1 and the `embodiment/runtime.md` boundary row together. If they
  stay outside, state this in D19 and Acceptance 1. Either way the design
  needs no other change, so after that decision the next step is
  `create-task-plan`.

## Design review pass 6 (architecture, 2026-11-06, after `c392fc51`)

- no new architectural review feedback. Scope decision A agrees with D19
  (firm) and with the fallback boundary.
- Planning implication of decision A: `core` requires `impl.fields`
  eagerly, and `impl.fields` will require the boundary (type tags, `munge`).
  `impl.capability` and `impl.field-capability` will also require it (define
  function). Thus the fallback path loads the boundary eagerly. The rule
  "depends only on Clojure, loads on Java 8" is load-bearing: the boundary
  must not import `java.lang.classfile` or other optional JDK APIs, and must
  not require any value-type namespace
  (no edge to `impl.capability`, so no cycle with `impl.fields →
  impl.capability`). Add the boundary edges to the `runtime.md` dependency
  direction (Acceptance 6).
- ambiguity review pass 6 added 1 new design step (class-definition limit
  in Acceptance 1)
  - `DynamicClassLoader` code use in `src` (commit `c392fc51`):
    construct in `impl.capability` (line ~96), `impl.field-capability`
    (~41), `impl.loader` (~46, and `proxy [DynamicClassLoader]` ~54);
    `.defineClass` in `impl.probe-builder` `define-probe` (~31) and
    `impl.loader` (~17, ~66); type hints in `impl.probe-builder` and
    `impl.loader`. No other `Compiler` code use in `src` beyond
    `impl.fields` and `impl.loader`; `core` names `Compiler/LOADER` and
    `CompilerException` only in text.
- inconsistency review pass 6 added 1 new design step (D19 and Architecture
  "refers"/"references" vs the Acceptance 1 code-in-`src` limit; D19 user
  list vs Concepts)

## Notes for the design-steps slice (pass 6)

- Open steps: the class-definition limit in Acceptance 1 (ambiguity) and
  D19 vs Acceptance 1 and the user list (inconsistency). Resolve them
  together: both define what "boundary use" means for a `src` search.
  Edit design.md and D19
  (`ramora/defvaluetype-design/decisions/compiler-boundary.md`); keep Scope
  (decision A) unchanged. ASD-STE100 style; ramora states the design, not
  history.
- Prefer one wording for code use in `src` in Concepts, Architecture,
  Acceptance 1 and D19. Text in docstrings and messages stays allowed;
  test and `dev` code stays outside.
- Define limit options. With the strict option, only the boundary calls
  `.defineClass`, subclasses or constructs `DynamicClassLoader`. Then
  `impl.probe-builder` keeps `probe-bytes` and can keep
  `define-probe-class` as a caller of the boundary define function.
  `define-probe` takes a loader argument from tests
  (`test/runtime/value_type/probe_test_support.clj`,
  `impl/field_capability_test.clj`, `impl/probe_builder_test.clj`), so the
  boundary define function keeps a loader parameter. Type hints alone
  are not a compiler coupling; state whether they are allowed.
- The capture proxy (`impl.loader` `capture-loader`) is "`Compiler/LOADER`
  capture" in Scope, so it is boundary content. The byte rewrite is passed
  in as a function (pass 2 note).
- D19 user list: Concepts is the authority (it was aligned in pass 1). The
  D19 gap row in `ramora/defvaluetype-design/embodiment.md` line 34 is
  rewritten later under Acceptance 6; do not edit it in this slice.
- After these steps, the next step is `create-task-plan`.

## Design-steps slice (pass 6, 2026-11-06)

- Scope decision A is in place; no `SCOPE_QUESTION:` step is open.
- "Compiler code use" is now defined once in Concepts; Architecture,
  Acceptance 1 and D19 use it. Strict option: in `src`, only the boundary
  imports, type-hints, constructs or subclasses `DynamicClassLoader`, and
  only it calls `.defineClass`. So `impl.probe-builder` drops its
  `DynamicClassLoader` import and hints; `impl.capability` and
  `impl.field-capability` get a new probe loader from a boundary function.
- Acceptance 1 search: `Compiler`, `DynamicClassLoader`, `.defineClass` in
  `src`, case-sensitive (`*compile-files*` does not match). Text matches
  that stay: `core.clj` (`Compiler/LOADER` message and `install-receiving!`
  docstring, `CompilerException` in a docstring), `impl.loader` `install!`
  docstring, `probe-builder/unique-probe-name` docstring.
- `UnsupportedClassVersionError` is a `LinkageError`. The probes catch it
  from the define call, so the boundary define function must not wrap
  `LinkageError`; `:jvm-rejected` stays in `impl.loader` (Architecture).
- D19 now has the code-use rule and the Concepts user list. The D19 gap row
  in `embodiment.md` stays for Acceptance 6. Next step: `create-task-plan`.

## Design review pass 7 (architecture, 2026-11-06, after `6adeb194`)

- no new architectural review feedback. A search of `src` finds compiler
  code use only in `impl.capability`, `impl.field-capability`,
  `impl.probe-builder`, `impl.loader` and `impl.fields`. The Architecture
  list gives each of these a path through the boundary.
- ambiguity review pass 7 added 1 new design step (form of the
  constructor shape, field order and record suffix in the boundary)
  - Why it is actionable: Acceptance 1 says the users read these facts
    "only from the compiler boundary", but no search can check this. Most
    facts are in `java.lang.classfile` code in `impl.transform`. A boundary
    that loads on Java 8 cannot hold that code. So the implementer must
    choose between data in the boundary and checks that stay in place. The
    identity backend uses `clojure.asm`, which the boundary can import (it
    is part of Clojure).
- inconsistency review pass 7: no new inconsistency review feedback
  - The D19 "Rejected" text (shape assumptions in `transform` and the
    identity backend) belongs to the pass-7 ambiguity step. It is not a
    separate step.
  - After implementation, the guard makes this sentence stale:
    `mementum/knowledge/controlled-fallback-boundaries.md` (~line 165,
    "output that calls only `check-value-class` reads `isValue`, not the
    cached result"). Acceptance 6 does not list this page. Update it with
    `mementum/state.md`, or in knowledge extraction.

## Notes for the design-steps slice (pass 7)

- Open step: the form of the shape facts in the boundary (pass-7
  ambiguity). Edit design.md (Scope wording only where it names the facts,
  Architecture, Acceptance 1) and D19
  (`ramora/defvaluetype-design/decisions/compiler-boundary.md`). D19
  "Rejected" ("shape assumptions in `loader`, `transform`, the identity
  backend and `core`") must agree with the chosen form. Keep decision A
  and "shape errors and their messages are unchanged".
- Limit: the boundary loads on Java 8 and requires no value-type namespace.
  It can import `clojure.asm`, but not `java.lang.classfile`. Thus the
  `impl.transform` matchers stay in `impl.transform`. A simple option: the
  boundary owns shape data, and the backends match bytes against that data.
- Constructor order in the class file is a format fact
  (`jvm-format/require-constructor-order!`, D15). It is not a compiler fact
  and stays in `impl.jvm-format`.
- Places where the shape facts are written into code now (commit `f5608dce`):
  - `impl.fields/record-hidden-fields` (the only data); read by `core`
    (`record-shape`, ~107-126: index n - 4 + k), `impl.transform`
    (~353-370, `removed-field-names`), `impl.value-backend` (~38, through
    `transform/removed-field-names`) and `impl.fields` (~177).
  - `impl.identity-backend/hidden-indexes` (~154) writes n - 4 and n - 3
    as literals instead of reading `record-hidden-fields`.
  - `impl.transform` `record-constructor-difference` (~398) hard-codes
    3 constructors with arities: declared, declared + 2, all.
    `expected-code` (~117) hard-codes the deftype constructor body: super
    call, then for each field in declared order a load and a putfield.
- A possible Acceptance 1 check for the shape facts: in `src`, the
  hidden-field names (`"__meta"` and the others) and the record arity
  offsets occur as code only in the boundary. Docstrings and messages are
  text and are excluded, as in the compiler code use rule. The `__meta`
  text in `impl.fields` (~26, the closed-check message) is text.
- Do not edit the D19 gap row in `embodiment.md` (line 34) or the
  "`transform` has no Clojure compiler dependency" sentence in
  `embodiment/runtime.md` in this slice. Acceptance 6 changes them during
  implementation.
- After this step, the next step is `create-task-plan`.

## Design-steps slice (pass 7, 2026-11-06)

- No `SCOPE_QUESTION:` step is open (decision A stays).
- Shape data form chosen: Clojure data in the boundary, bytecode checks
  stay in place (design.md Architecture "Shape data", D19). Precedent for a
  data row of facts: `impl.jvm-format` `format-row`
  (`:constructor-order`).
- `record-hidden-fields` gets a third element per entry (delegating
  argument: `nil` or `0`). Readers that destructure `[n tag]` (`core`
  `hidden-index`, `in-place?`, `hidden-fields-fix`; `transform`
  `hash-field-names`; `impl.fields` `check-hidden-fields!`) must accept it.
- Literals to replace with boundary data (commit `e05e2bc3`):
  `transform` ~102 (`"java/lang/Object"` `"()V"`), ~174 (`1`), ~406-419
  (`4`, `+ 2`, `3`); `identity-backend` ~158 (`n - 4`, `n - 3`); `core`
  ~119 (`n - 4 + k`). `delegating-code` emits `ACONST_NULL` and `ICONST_0`
  for the absent arguments; map them from the delegating argument.
- `identity-backend` `object-descriptor` stays: its other uses (~111,
  ~225) check non-nil field storage, not compiler shape.
- Next step: `create-task-plan`.

## Design review pass 8 (architecture, 2026-11-06, after `5517c2b7`)

- no new architectural review feedback. Shape data as Clojure data in the
  boundary, with the bytecode checks kept in `impl.transform` and
  `impl.identity-backend`, agrees with D19 (Java 8 load rule), D15 (value
  constructor order stays in `impl.jvm-format`) and the fallback boundary
  (the boundary has no optional JDK API, so an eager require from `core`
  and `impl.fields` is allowed).
- `impl.value-backend` uses the boundary only indirectly, through
  `transform/removed-field-names` and `fields/emitted-name` (`munge`). It
  needs no direct require of the boundary.
- ambiguity review pass 8: no new ambiguity review feedback
  - Field order is not a data key. Architecture states what the data holds
    (constructor count, super constructor). `expected-code` and the
    `parameters` check keep the declared order by structure, with no
    literal, so Acceptance 1 does not apply to them.
- inconsistency review pass 8: no new inconsistency review feedback
  - Checked against code (commit `23bcda5e`): the three record
    constructors (all, all - 2, all - 4) and the delegating arguments match
    `transform/record-constructor-difference`. In `src`, the hidden field
    names occur outside `impl.fields/record-hidden-fields` only as text
    (docstrings and messages, for example `identity-backend` ~177). Thus the
    Acceptance 1 search is satisfiable once the data moves.
  - `value-receiving` now emits `(check-value-class ~cls true)`; it loses
    the literal with `installed` (Scope).

## Notes after design review pass 8

- No design step is open: pass 8 added none (all three reviews). The design
  is stable. Next step: `create-task-plan` (write `plan.md` and `steps.md`).
- Planning: the design notes for the plan are spread over the earlier passes in
  this file. Read them before you write the plan: commit order for the startup
  measurement (pass 2), the failed self-check child (pass 2 slice), the
  Acceptance 1 search (pass 6 slice) and the literals to replace (pass 7 slice).

## Plan review (ambiguity, 2026-11-06, after `d7601e66`)

- ambiguity review added 3 new design steps (receiving macro for ordinary
  value definitions; slice-5 handling of `aot-value-output-off-test`;
  slice-7 build of the two outputs)
  - Not raised (implementer choice, low risk): the home of
    `no-value-classes-message` after the guard moves (`core/error-message`
    also reads it). `aot-native-field-invalid-test` (`OFF`) keeps its
    expectation: the value guard reads the switch first and throws the same
    `IllegalArgumentException`.
  - Candidate for the inconsistency turn: slice 2 "Replace shape literals"
    lists `transform` and `identity-backend` sites, but not `core/in-place?`
    (`(+ (- n 4) k)`), the user of the boundary index function (pass-7
    slice note).

## Plan review (inconsistency, 2026-11-06, after `992c801a`)

- inconsistency review added 1 new design step (`core/in-place?` index
  literal missing from slice 2)
  - For slice 8: `doc/API.md` renders only `defvaluetype`, `defvaluetype*`
    and the array and capability functions. `api_docs_test.clj` forbids
    the text `check-value-class` and `value-type.impl.` in it. So the
    `defvaluetype` docstrings must describe the emitted guard without
    naming `impl.capability/require-value-capability!` or
    `check-value-class`.

## Notes for the design-steps slice (plan review)

- Open steps: 4 plan steps (receiving macro, `aot-value-output-off-test`,
  slice-7 build, `core/in-place?`). They change plan.md and steps.md
  only; design.md is stable and Scope (decision A) stays.
  steps.md is a checklist: edit unticked items in place; do not add
  revision markers.
- Keep the slice order and the dedicated guard commit (slice 5) unchanged;
  the measurement baseline depends on it.
- Receiving macro: `policy-expansion` (`src/value_type/core.clj`) already
  has the receiving branch `(let [] ~(value-form modes))`; its docstring
  states why the `let` wraps the form. Modes `[]` pass
  `fields/receiving-specifications` when no field is enabled (count check
  0 = 0). Tests that name the old shape: `test/runtime/value_type/core_test.clj`
  (`check-value-class-test`, two arguments),
  `test/runtime/value_type/deftype_star_test.clj`
  (`value-expansion-receiving-test`),
  `test/compatibility/value_type/expansion_checks.clj`.
- `aot-value-output-off-test`: `test/cross-jvm/value_type/value_class_switch_test.clj`
  (~183, uses `fixture/source-free-consumers` and `checks/holder-consumer-form`);
  `aot-native-field-off-test` (~161) is the model for a keyed error
  expectation (`harness/keyed`, `:data`).
- Slice-7 build: `test/compatibility/value_type/aot_fixture.clj`
  (`compile-form`, `compile-clojure-child`, `classpath-with`) and
  `jvm_runner.clj` give the child JVM pattern. Benchmark precedent:
  `dev/bench/` (outside all suites).
- Next step after these steps: plan review again, then `implement-task`.

## Plan-steps slice (plan review, 2026-11-06)

- The 4 plan steps are resolved in plan.md and steps.md. design.md is not
  changed. The slice order and the dedicated slice-5 guard commit do not
  change.
- Ordinary value receiving: `value-receiving` already calls
  `require-value-capability!` at analysis. So after slice 4, a transferred
  ordinary expansion throws `:value-capability-unavailable` at analysis
  with no extra code (Acceptance 2). The receivers also repeat
  `check-fields` and `declared-shape`; this is harmless.
- `restricted.aot-fixture` (`test/cross-jvm/value_type/field_aot_test.clj`
  `restricted-source`) defines the ordinary `Leaf` before the native-field
  `Holder`. After slice 5, `Leaf`'s guard throws first. The key and reason
  of `aot-native-field-off-test` are the same, and the guard message has
  the switch detail. Before slice 5, `aot-value-output-off-test` still
  passes: receiving output without an emitted guard loads as value classes.
- Slice-7 classpath: the only dependency in `deps.edn` is Clojure, so
  `clojure -Spath` without `src` gives the Clojure jars. `dev/bench` is in
  `bb/value_type/quality.clj` `maintained-paths`. Lint and format apply to
  any `.clj` file there; a bash-only script needs no lint.

## Plan review pass 2 (ambiguity, 2026-11-06, after `2e994798`)

- ambiguity review added 1 new design step (slice-6 saved sources and
  check forms)
  - Not raised (implementer can find them; the reviewer checks them):
    - Slice 4 test homes. Fresh-JVM replay and transfer of an ordinary
      value expansion: `transfer-cells` with `config/value-producer` and
      enabled `false`, shapes `[:plain :record]`
      (`deftype_star_transfer_test.clj`, model
      `deftype*-closed-value-transfer-test`, which already has the
      `available?` branch), and `transfer_test.clj` for `defvaluetype`
      (now identity producer only). In-process tests that invert after
      D20: `core_test.clj` `not-installed-test` and
      `not-value-class-test`, `deftype_star_test.clj`
      `value-expansion-receiving-test` (both branches). Eval of a one-step
      expansion now installs under the `eval` `LOADER` binding.
    - Slice 3 "update D9/guard unit tests": no test calls the private
      `core/require-value-capability!` now. A new unit test in
      `impl/capability_test.clj` is optional.
  - Candidate for the inconsistency turn: design Architecture gives
    `impl.loader` the field class check and the `:jvm-rejected` error as
    functions passed to the boundary. plan.md and slice 1 name only the
    byte rewrite and the field class check. The boundary proxy defines
    the target class, so the `:jvm-rejected` wrap (now
    `loader/define-target-class`) must also be a callback or wrap the
    proxy call. Also, the backend `prepare` takes the delegate loader,
    which the boundary constructs: the rewrite argument must be a
    function of the delegate.

## Plan review pass 2 (inconsistency, 2026-11-06, after `e050fb53`)

- inconsistency review added 1 new design step (the `:jvm-rejected`
  callback and the delegate-loader argument of the rewrite are in design.md
  Architecture, but not in plan.md or slice 1; this is the candidate from
  the ambiguity pass)
  - The other plan and steps lists agree with design.md and the code at
    `e050fb53`: the `record-hidden-fields` readers (`core` `hidden-index`,
    `in-place?`, `hidden-fields-fix`, `record-shape`; `transform` ~353-363;
    `fields` ~177), the slice-5 and slice-6 split of Acceptance 3, the
    measurement method, and the Acceptance 6 document list.

## Notes for the plan-steps slice (plan review pass 2)

- Open steps: 2 plan steps (slice-6 sources and check forms; the
  `:jvm-rejected` callback). Edit plan.md and unticked steps.md items in
  place; design.md is stable. Keep the slice order and the dedicated
  slice-5 guard commit.
- Slice-6 check functions: put them in
  `test/compatibility/value_type/value_class_switch_checks.clj`. That
  namespace loads no value-type namespace at its own load, so require,
  then `resolve` (as `aot-load`). Resolve the accessor var after the
  `require`, then use `with-redefs-fn`. For the in-function check, invoke
  `install` twice (`registry/twice`). Narrow the root failure with
  `registry/root-failure` and `select-keys`
  (`test/compatibility/value_type/cell_registry.clj`).
  `function-aot-checks/invoke-install` stays as it is; identity and
  native function tests use it.
- Slice-6 runner: `fixture/source-free-consumers`
  (`test/compatibility/value_type/aot_fixture.clj` ~201) with
  `config/value-producer`, as `restricted-consumers` in
  `test/cross-jvm/value_type/value_class_switch_test.clj`. For the failed
  self-check, the receiver is `valhalla` without `switched`.
- Callback step: the current flow is in `src/value_type/impl/loader.clj`
  (`capture-loader`, `define-target-class`). Both backend `prepare`
  functions take `[target specifications delegate shape]`; the identity
  backend ignores the delegate. The companion define (non-target) has no
  `:jvm-rejected` wrap now; keep that. The probe callers catch a raw
  `LinkageError`, so the boundary define function stays unwrapped.

## Plan-steps slice (plan review pass 2, 2026-11-06)

- Both plan steps are resolved in plan.md and steps.md; design.md is not
  changed. Slice order and the dedicated slice-5 commit do not change.
- Slice 1: `capture-loader` calls backend `prepare` at construction
  (install time), not at the first define; keep that timing inside
  `compiler/install-capture!`. The companion AOT write is guarded by
  `(seq specifications)` now; the boundary cannot see specifications, so
  it gets `rewrite-companions?`.
- Slice 6: `function-source` and `identity-source` are public vars in
  `test/cross-jvm/value_type/public_field_aot_test.clj`;
  `value_class_switch_test.clj` already aliases it (`public-field-aot`)
  and `field-aot`. `^String ^:non-nil` gives generated (not native)
  enforcement in value output, so both `checked.aot-fixture` sources are
  "without native fields". The guard message reads
  `capability/value-predicate` (not the replaced accessor) for the
  no-value-classes text; on EA3 it is present, so the message has the
  `:detail` of the replaced result.

## Plan review pass 3 (ambiguity, 2026-11-07, after `bbb37289`)

- ambiguity review added 1 new design step (capture-loader entry point
  for tests after slice 1)
  - Why actionable: `install-capture!` as planned is the only boundary
    entry; it needs a `LOADER` binding and returns a boolean, so the
    five direct `#'loader/capture-loader` test sites lose their loader.
    No test reads `transform` shape functions, `record-hidden-fields` or
    private `core` vars, so slice 2 and slice 3 need no test entry point.
  - Checked, not raised: the slice-6 helpers (`registry/twice`,
    `outcome`, `root-failure`, `each-attempt`, `harness/entries`,
    `keyed`) exist; no test matches the literal expansion form, so
    slice 5 changes only the cross-JVM expectations it lists; the
    lazy-load tests (`capture_loading_test.clj`, `runtime_test.clj`)
    forbid only `loader`, `transform`, `value-backend` and
    `field-access`, so the eager boundary load passes.

## Plan review pass 3 (inconsistency, 2026-11-07, after `c3e243b6`)

- no inconsistency review feedback
  - Checked at `c3e243b6`: steps slice-2 line references in
    `impl.transform` (~102, ~174, ~393-419) match the code;
    `fields/direct-fix` is the Contract fix text of the receiving
    `:not-installed` message.

## Notes for the plan-steps slice (plan review pass 3)

- Open step: the capture-loader entry point for tests. Edit plan.md and
  unticked steps.md slice-1 items in place; design.md is stable. Keep
  the slice order and the dedicated slice-5 commit.
- Prefer the option that leaves the tests unchanged (Acceptance 5, and
  test code is outside Acceptance 1): a boundary constructor
  `[target parent prepare check define-target rewrite-companions?]` that
  returns the proxy; `install-capture!` reads `@Compiler/LOADER` as the
  parent, calls the constructor, then does `.set`. `impl.loader` keeps
  private `capture-loader [target parent specifications representation
  shape]` (builds the four functions, calls the constructor) and
  `install!` composes it.
- What the test sites need from the returned loader: `.defineClass`
  `[name bytes src]` on the proxy (the tests type-hint it
  `^DynamicClassLoader` themselves); the parent is a `ClassLoader` (context
  loader, `(DynamicClassLoader.)` or a class loader). Keep the timing:
  `prepare` runs at construction; `check` and the owner rewrite run at
  the target define, where `capture_failure_test.clj` and
  `restricted-class-mismatch-test` expect their errors.
- `link-test` passes `(DynamicClassLoader.)` as `delegate` to
  `define-target-class`; it still works after the type hint goes, because
  the boundary define function holds the hint.
- Next step after this slice: plan review again, then `implement-task`.

## Plan-steps slice (plan review pass 3, 2026-11-07)

- Capture-loader entry point resolved in plan.md and steps.md slice 1;
  design.md is not changed. Two boundary functions:
  `compiler/capture-loader` `[target parent prepare check define-target
  rewrite-companions?]` (no `LOADER` access) and
  `compiler/install-capture!` `[make-loader]` (function of the parent).
  `rewrite-companions?` is a boolean, the other three are functions.
- `loader/capture-loader` and `loader/define-target-class` keep their
  arguments, so no test changes. Every test site already hints the
  returned loader `^DynamicClassLoader` at its `.defineClass` call, so
  the dropped return hint in `impl.loader` adds no reflection.
- `stale-identity-specification-test` and `restricted-class-mismatch-test`
  pass `(byte-array 0)` as target bytes: `check` must run before `owner`
  in the proxy, or the rewrite fails first with a different error.
- Next step: plan review again, then `implement-task`.

## Plan review pass 4 (ambiguity, 2026-11-07, after `9f27f1a4`)

- ambiguity review added 1 new design step (hidden-field count literal 4
  in `core/record-shape`, `identity-backend/closed-checks` and
  `transform/record-constructor-difference`)
  - Why actionable: Architecture names all three functions as shape checks
    that read the shape data, and Acceptance 1 checks them by review. The
    slice-2 list names only positions (n - 4 + k, n - 4, n - 3) and
    arities. An implementer that follows the list keeps the count literal;
    a reviewer can then reject it.
  - Checked, not raised: the emitted guard takes the class-name string, as
    `field-capability/require-field-capability!` does; the slice-6 check
    functions can build on `aot-load` (it returns `:source-absent?` and
    `:load`); the slice-7 JDK default is the one in `jvm_config.clj` and
    `dev/bench/typed-array/run.sh`; the test suites are the `deps.edn`
    aliases.

## Plan review pass 4 (inconsistency, 2026-11-07, after `f89de381`)

- no inconsistency review feedback
  - Not raised: steps slice 2 lists the `record-hidden-fields` readers
    but not `transform/closed-field-names` (`mapv first`). The value does
    not change with a third element. Only the namespace prefix changes
    when the data moves, and the compiler reports it.

## Notes for the plan-steps slice (plan review pass 4)

- Open step: the hidden-field count literal 4 (slice 2). Edit plan.md and
  the unticked steps.md slice-2 items in place; design.md is stable. Keep
  the slice order and the dedicated slice-5 commit.
- Simple rule that agrees with design Architecture: every count `4` in
  the named shape checks comes from the boundary. Use `(count
  record-hidden-fields)` for the hidden-field count, or the main record
  constructor count (4) for record constructor arities. Docstrings that
  say "n - 4" are text; Acceptance 1 permits them.
- Sites (commit `9f27f1a4`):
  - `src/value_type/core.clj` ~143 `record-shape`: `(= 4 (count
    hidden))` (all hidden names found). ~119 `in-place?` is already in
    slice 2.
  - `src/value_type/impl/identity_backend.clj` ~173 `closed-checks`:
    `(< n 4)` stops a negative index in `hidden-indexes` (~158) before
    `aget`. Keep the guard; take the bound from the boundary.
  - `src/value_type/impl/transform.clj` ~406 `(< (count fields) 4)` and
    ~411 `(- (count fields) 4)` in `record-constructor-difference`; ~415
    `(+ 2 ...)` is the meta constructor arity (already in the step range).
- No test reads these functions directly. The shape error messages do
  not change.
- Next step after this slice: plan review again, then `implement-task`.

## Plan-steps slice (plan review pass 4, 2026-11-07)

- Hidden-field count step resolved in plan.md ("Rule for the slice-2
  literals") and steps.md slice 2; design.md is not changed.
- One rule: the hidden-field count is `(count
  compiler/record-hidden-fields)`; the record constructor data derives
  its main entry (4) from it. `record-constructor-difference` ~419
  (`3`) is the count of record constructor data entries.
- Kept as literals (not shape data): `transform/main-constructor-part`
  `(<= pos 2)` (positions 1-2 of `expected-code` are `aload_0` and the
  super call) and slot arithmetic.
- No design step is open. Next step: plan review again, then
  `implement-task`.

## Plan review pass 5 (ambiguity, 2026-11-07, after `348522f7`)

- no ambiguity review feedback
  - Not raised (implementer inference, low risk): `transform/delegating-code`
    takes the boolean `meta-extmap?` now. "Map `ACONST_NULL`/`ICONST_0`
    from the delegating argument" reads as: for each hidden field, a
    reference load when its index is below the hidden parameter count of
    the record constructor entry (0 or 2), else the constant of its
    delegating argument (`nil` → `ACONST_NULL`, `0` → `ICONST_0`). Thus
    the call sites pass the entry count in place of `false`/`true`. The
    slice-2 rule forbids only number literals, so a kept boolean also
    passes review.
  - Not raised: the form of the type tag wrappers (one function or three)
    and the home of the `*ns*` binding and the `ClassNotFoundException`
    catch in `fields` type resolution. Design: the boundary wraps the
    calls and does not change their results.

## Plan review pass 5 (inconsistency, 2026-11-07, after `ad938da3`)

- no inconsistency review feedback
  - Checked: the Acceptance 2, 3 and 6 lists against slices 4-8; the
    measurement method against slice 7; the `src` compiler code use
    (`capability`, `field_capability`, `probe_builder`, `loader`,
    `fields`) against slice 1. `transform` ~235 (`Object.<init>` through
    `ConstantDescs`) is emitted value-constructor code, not a shape check;
    it stays.

## Notes after plan review pass 5

- No design step is open (`design-steps.md` has no unticked item). The
  plan is stable. Next step: `implement-task`, from slice 1.
- Before slice 2, read the pass-5 ambiguity note on `delegating-code`. It
  gives the data-driven reading (hidden parameter count in place of
  `meta-extmap?`).

## Slice 1: compiler boundary (2026-11-07)

- New `value-type.impl.compiler`: `define-class [loader name bytes src]`,
  `probe-loader`, `capture-loader [target parent prepare check
  define-target rewrite-companions?]`, `install-capture! [make-loader]`,
  `resolve-tag [tag]` (caller keeps the `*ns*` binding and the
  `ClassNotFoundException` catch, in `fields/resolve-tag`), `munge-name`.
- `impl.loader`, `capability`, `field-capability`, `probe-builder`,
  `fields` have no `Compiler`/`DynamicClassLoader` import now; remaining
  `src` matches outside the boundary are docstring text only.
- Test sites unchanged; all suites green.
- Suite commands (local): runtime needs
  `JAVA_CMD=<jdk-27>/bin/java clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime`;
  baseline fallback/cross-jvm need Java 8
  (`/Library/Java/JavaVirtualMachines/temurin-8.jdk`), with
  `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` for cross-jvm.

## Slice 2: shape data in the boundary (2026-11-07)

- Boundary data in `impl.compiler`: `record-hidden-fields` entries
  `[name tag delegating-argument]`; `hidden-field-count`;
  `hidden-field-index [n k]`; `hidden-field-position [name]` (index or
  nil); `record-constructors` `[0 2 hidden-field-count]` (hidden
  parameter counts; main = count); `deftype-constructor`
  `{:count :super-owner :super-descriptor}`.
- `fields/record-hidden-fields` removed; readers use the boundary var.
  `core` now requires `impl.compiler` directly (no value-type deps, so
  lazy-load invariants hold).
- `transform/delegating-code` takes the hidden parameter count (pass-5
  reading); constant from a `{nil ACONST_NULL 0 ICONST_0}` map (a `case`
  on `nil`/`0` gives a hash-collision warning).
  `record-constructor-difference` groups constructors by each
  `record-constructors` entry; error parts unchanged.
- `identity-backend/hidden-indexes` resolves `__meta`/`__extmap` by name.
- New `test/runtime/value_type/impl/compiler_test.clj` (shape data).
  All suites green (runtime 187, fallback J8/J27 74, cross-jvm 112,
  tooling 11).

## Slice 3: guard move (2026-11-07)

- `impl.capability` now owns `require-value-capability!`,
  `no-value-classes?` and `no-value-classes-message` (public); `core`
  calls them (`error-message` too). Message and data unchanged.
- No test referenced the private core guard; new unit test
  `capability-test/require-value-capability-test` (redefs `capability`).
- Tooling suite command: `clojure -M:quickdoc:tooling-test -m scry.cli
  --dir test/tooling`; cross-jvm needs `JAVA_CMD=<java 8>` too.
  All suites green (runtime 188, fallback J8/J27 74, cross-jvm 112,
  tooling 11).

## Slice 4: D20 receiving expansion (2026-11-07)

- `policy-expansion` is `[shape validated value-form identity-form
  guarded]`; value branch always `(let [] ~(value-form modes))`.
  `value-expansion` and the `definition` arguments are gone.
- `check-value-class [cls]` (one argument), throws `:not-value-class`
  only; message helper `not-value-class-message` (no replay cause).
  `error-message` and `macroexpanded-outside-compilation` removed.
- `install-receiving!`/`not-installed-message` take no `kind`; message
  "class capture requires a compiler loader ... no Compiler/LOADER
  binding). Fix: evaluate the defvaluetype form directly."
- Public value expansion now keeps `:value-type/closed` in the receiver
  call (the receiver strips it). `closed-option-removal-test` checks only
  `deftype*`/`guarded-deftype*` calls.
- Tests: `core_test` `not-installed-test` (one step + macroexpand-all),
  `replayed-expansion-test` (in-process replay gives a value class);
  `deftype_star_test` `value-expansion-receiving-test` rewritten.
  Fresh-JVM replay/transfer: `transfer_test/ordinary-value-transfer-test`
  (value producer, consumed by `deftype-star-transfer-checks/consume-data`)
  and `deftype*-ordinary-value-transfer-test` (was closed-only; now all
  shapes). Incapable receivers expect `:value-capability-unavailable`.
- `aot-value-output-off-test` still passes unchanged; slice 5 rewrites it.
- All suites green (runtime 188, fallback J8/J27 75, cross-jvm 112,
  tooling 11).

## Slice 5: guard emission (2026-11-07)

- `core/value-receiving` emits `(capability/require-value-capability!
  "<class>")` as the first form of the `let`; `src` diff is that one
  line, so no other expansion output changes. The measurement baseline
  (slice 7) is the parent of this commit.
- `value_class_switch_test.clj`: new private `aot-consumers [ns-sym source
  source-path value policies expected]` (generalized
  `restricted-consumers`) and `disabled-failure`; slice 6 can reuse both.
  `consumer-aot-checks` require removed (unused now).
- Other AOT suites needed no change (guard is available on capable JVMs).
- All suites green (runtime 188, fallback J8/J27 75, cross-jvm 112,
  tooling 11); fallback runs with `clojure -M:fallback-test` (no `-m`).

## Slice 6: AOT guard tests (2026-11-07)

- `value_class_switch_test.clj`: new private `aot-run [ns-sym source
  source-path receiver policies check args expected]` (value producer,
  one receiver, check symbol + extra args, keyed expected map);
  `aot-consumers` now delegates to it.
- `value_class_switch_checks.clj`: private `narrowed-failure` shared by
  `aot-load`, new `aot-install` and `aot-load-failed-self-check`.
- The failed-self-check redef works because the guard reads
  `capability` through its var (slice 3).
- Cross-jvm suite green (114 tests).

## Slice 7: startup measurement (2026-11-07)

- Script: `dev/bench/aot-startup/run.sh e4987584` (guard commit; baseline
  parent 18d487f3). Clojure jars = `clojure -Spath` without `src`.
  Fixture `startup.fixture` with `(defvaluetype Point [x])`, written to
  `target/aot-startup/fixture/`. Check: guard output `Point` `.isValue` true.
- Result (ms, JVM start to end of `require`, 10 runs after 2 discarded,
  alternating): guard median 215 (min 214, max 220); baseline median 204
  (min 203, max 213). Added cost about 11 ms median.
- Context: first `impl.capability/capability` call, 10 fresh JVMs: median
  5.7 ms (min 5.5, max 5.8). The rest of the difference is likely the load
  of `impl.capability` and its probe code at namespace initialization.
- Limits: one machine (macOS, Apple silicon), one JVM build (EA3
  jdk-27), no class-data sharing control. D17 commitment unchanged; the
  user decides.

## Slice 8: docs and closure (2026-11-07)

- D19 users list: `impl.value-backend` removed; it does not require
  `impl.compiler`; design.md Concepts updated to match.
- Acceptance 1 search found one code use of hidden names outside the
  boundary: `identity-backend/hidden-indexes` used `["__meta" "__extmap"]`.
  Now it selects the `record-hidden-fields` entries with no tag. All other
  matches are text.
- Gap table: D17, D18, D19, D20 rows removed (realized).
- D17 records the measured AOT cost (~11 ms median); commitment unchanged.
- Tooling test `api-docs-test` checks the phrase "never selects fallback"
  in `doc/API.md` on one line; keep it unbroken when rewrapping the
  `defvaluetype*` docstring.
- Suite commands: runtime needs
  `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`; release
  needs `-M:release:release-test`; build `-M:build-test`.
- All suites green: runtime 188, fallback J8/J27 75, cross-jvm 114,
  tooling 11, build 9, release 12; lint, format, Vale, API check pass.

## Implementation review pass 1 (2026-11-07, after `5df42c76`)

- added 3 steps to be addressed

## Review follow-up 1 (2026-11-07)

- addressed 3 review steps.
- Guard position: `value-receiving-test/receiving-native-guard-order-test`
  expands `value-field-deftype` and `value-field-deftype*` with `[:native]`
  under a `Compiler/LOADER` binding; asserts the value-class guard, then the
  native-field guard, are the first forms. Fails when the order is swapped
  (checked).
- runtime.md: removed the left `impl.loader` cells; `impl.capability` row
  names `require-value-capability!` (D14). `impl.capability` and
  `impl.transform` namespace docstrings updated.
- Runtime 189, tooling 11 green; lint, format, Vale, API check pass.

## Implementation review pass 2 (2026-11-07, after `e1ccf25e`)

- no new review feedback

## Implementation test review (2026-11-07, after `a3889b7b`)

- added 3 steps to be addressed

## Review follow-up 2 (2026-11-07)

- addressed 3 review steps.
- `check-value-class-test`: where value classes exist, asserts the message
  names the class, the nested cause and the fix.
- `not-installed-enabled-field-test`: asserts `class-capture`.
- `impl/compiler_test.clj`: replaced with a check of the shape data against a
  real `defrecord` class (hidden field names, indexes and types; constructor
  parameter counts). Delegating arguments not checked (bytecode only; covered
  by the corpus).
- Runtime 189 green; lint, format pass.

## Implementation test review 2 (2026-11-07, after `a75af217`)

- added 1 step to be addressed

## Review follow-up 3 (2026-11-07)

- addressed 1 review step.
- `impl/compiler_test.clj`: compiles `ShapeRecord` with `*compile-files*` to a
  temporary directory and reads the instance field order and constructor
  parameter counts from the class bytes (`clojure.asm.ClassReader`). No
  reflection order dependency. Fails when `hidden-field-index` is wrong
  (checked).
- Runtime 189 green (EA JDK 27, `--add-exports
  java.base/jdk.internal.value=ALL-UNNAMED`); lint, format pass.

## Implementation test review 3 (2026-11-07, after `98f718b4`)

- added 1 step to be addressed

## Review follow-up 4 (2026-11-07)

- addressed 1 review step.
- `impl/compiler_test.clj` `record-class-bytes`: uses
  `support/with-temp-dir`; reads the bytes inside its body. The directory
  is deleted after the run.
- Runtime 189 green; lint, format pass.

## Implementation test review 4 (2026-11-07, after `9b3bea0f`)

- added 1 step to be addressed

## Review follow-up 5 (2026-11-07)

- addressed 1 review step.
- `core_test.clj` `not-installed-identity-receiving-test`: full expansion of
  `identity-field-deftype` (`[:generated]`, enabled `String` field, silent
  policy redef) on a raw thread throws `:not-installed` with the class name,
  `class-capture` and `direct-fix`.
- Runtime 190 green; lint, format pass.

## Implementation review 3 (2026-11-08, after `aef24b62`)

- added 1 step to be addressed

## Review follow-up 6 (2026-11-08)

- addressed 1 review step: corrected the AOT switch coverage text against
  `value_class_switch_test.clj`. `bb vale` passed; documentation-only change.

## Implementation test review 5 (2026-11-08, after `baf544e4`)

- added 1 step to be addressed

## Review follow-up 7 (2026-11-08)

- addressed 1 review step: AOT checks retain the outer exception class.
  Top-level output asserts `ExceptionInInitializerError`; both function
  invocations assert direct `ExceptionInfo`. Existing root-error and
  source-absence checks remain.
- Switch test namespace: 11 tests, 172 assertions passed. Lint and format
  checks passed.

## Implementation test review 6 (2026-11-08)

- added 1 step to be addressed

## Review follow-up 8 (2026-11-08)

- addressed 1 review step: compile-only transfer checks cover both expansion
  forms of `defvaluetype` and plain and record `defvaluetype*` on Java 8 and
  EA3 without preview. They assert `CompilerException` and immediate cause
  data without invoking the compiled function.
- New test: 44 assertions passed. Existing ordinary transfer tests: 238
  assertions passed on each of Java 8 and EA3 with preview off and on.
  Removing only the analysis guard made all 12 new receiver cases fail;
  restored the guard. Lint and format checks passed.

## Implementation test review 7 (2026-11-08, after `0704a209`)

- no new actionable test issues

## Documentation review (2026-11-08)

- added 2 steps to be addressed

## Documentation follow-up (2026-11-08)

- addressed 1 review step: README distinguishes new fallback definitions from
  fixed saved value output and states guard rejection phases and disabled reason.
  `bb vale` passed.

- addressed 1 review step: runtime examples use the four-argument `install!`
  call, define the shape values and pass `:plain` for ordinary `defvaluetype`.
  Checked against `impl.loader/install!`; `bb vale` passed.

## Documentation review 2 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 2 (2026-11-08)

- addressed 1 review step: corrected capture-loading boundaries in the three
  runtime pages against `policy-expansion`, `identity-receiving`, backend
  selection and the identity receiving checks. `bb vale` passed.

## Documentation review 3 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 3 (2026-11-08)

- addressed 1 review step: qualified D17 startup probing against capability
  detection, the receiving guard and the function-output switch test. Measured
  cost and commitment unchanged. `bb vale` passed.

## Documentation review 4 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 4 (2026-11-08)

- addressed 1 review step: usage and API docs now state the native-field
  guard phases, fixed enforcement and value-class guard precedence. Checked
  against both guards and the guard-order test. Regenerated `doc/API.md`;
  API check, Vale and format check passed.

## Documentation review 5 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 5 (2026-11-08)

- addressed 1 review step: corrected the tooling macroexpand risk mitigation
  against `policy-expansion`, `value-receiving`, `not-installed-test` and
  `replayed-expansion-test`. Kept fresh-receiver verification. `bb vale` passed.

## Documentation review 6 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 6 (2026-11-08)

- addressed 1 review step: corrected the guard error-data example with separate
  class-name and reason entries. Checked against `require-value-capability!`
  and the saved-output switch assertions. `bb vale` passed.

## Documentation review 7 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 7 (2026-11-08)

- addressed 1 review step: completed the loader error-data map with a
  class-name value. Checked against `define-target-class`, `jvm-rejected-test`
  and `link-test`. `bb vale` passed.

## Documentation review 8 (2026-11-08)

- added 1 step to be addressed

## Documentation follow-up 8 (2026-11-08)

- addressed 1 review step: the AOT-write invariant now names the compiler
  boundary and the write order. Checked against `compiler/capture-loader`,
  `aot-write-test` and `aot-write-before-define-test`. `bb vale` passed.

## Documentation review 9 (2026-11-08)

- no new actionable documentation issues

## Code-shaper review (2026-11-08)

- added 1 step to be addressed

## Code-shaper follow-up (2026-11-08)

- addressed 1 review step: benchmark exit cleanup preserves the failure status
  and removes only worktrees created by the run. Stale registrations at the two
  owned paths are removed before directory reset; physical paths match Git.
- `bash -n` passed. In a temporary repository, a forced Clojure compile failure
  returned 1 with no benchmark worktrees registered. A retry created both trees
  and completed the benchmark. Missing and existing stale registrations were
  cleared; an unrelated worktree remained. These runs test cleanup, not guard cost.

## Code-shaper review 2 (2026-11-08)

- no new actionable code-shaper issues

## Closure (2026-11-08)

Completed and closed at the user's request. All steps are checked. The
implementation review completed with no new actionable findings. Knowledge
extraction committed two new memories and updated two existing memories.
D17 remains provisional; the recorded AOT guard timing is bounded evidence.
No tests were rerun for this task-state change.
