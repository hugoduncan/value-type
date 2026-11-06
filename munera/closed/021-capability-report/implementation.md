# Implementation

## Design review

- Architecture review (2026-11-04): added 2 design steps.
  - Note: `ramora/META.md` and `doc/architecture.md` do not exist. The
    architecture sources are `ramora/INDEX.md`, fallback D12/D13
    (`ramora/defvaluetype-design/fallback.md`), array D2/D3 and the
    defvaluetype runtime embodiment.
  - Guard step rationale: `runtime/require-field-capability!` appears in
    emitted expansions (`core.clj` `value-receiving`). If it moves, the
    expansion symbol changes, and AOT artifacts need the new owner loaded
    by `value-type.core`. Natural owner: `impl.field-capability`.
  - Fit checked, no finding: allocation uses the value-class result only as
    a prerequisite, not as proof of array support (D3). Inspection stays
    independent. `value-type.capability` does not read policy (D12).
    `impl.capability`, `impl.field-capability` and `impl.array` load on
    Java 8.
  - Tooling: `test/tooling/value_type/api_docs_test.clj` checks the exact
    list of public vars. Add `value-type.capability/report` to it.
- Architecture review, pass 2 (2026-11-04): added 1 design step (A-34).
  - Checked, no finding: the guard move, the dependency graph (matches
    current `:require` forms; `impl.runtime` loses its two probe
    requires), D3 adapter ownership, and `purpose.md` "State" (it names no
    owner, so it stays true).
- Architecture review, pass 3 (2026-11-04): added 1 design step (API doc
  generator).
  - Checked, no finding: array embodiment ("caches capability"; no shared
    array cache) stays true; `fallback/selected-detector.md` names only
    `detect`, which does not change; the guard in an `impl.` namespace
    matches `guarded-deftype` in `impl.runtime`.
- Architecture review, pass 4 (2026-11-04): no architectural review
  feedback.
  - Checked against code: `core.clj` reads and emits only the `runtime/`
    vars that the design moves; `impl.field-capability/detect` and
    `impl.array/discover` call `capability/detect` directly (the design
    changes this to the accessor).
  - For the D13 update (Acceptance 7): D13 names one owner in both
    "Choice" and "Rationale" ("one small state owner is enough"). Update
    both.
- Ambiguity review (2026-11-04): added 3 design steps.
  - Require-order cost: child JVMs run at `VALUE_TYPE_TEST_JVM_LIMIT`
    (default 4). The current four matrix commands take about 9 minutes, so
    90 more one-cell children are a large cost.
- Ambiguity review, pass 2 (2026-11-04): added 2 design steps.
  - Checked, no finding: `isolated-consumer-test` option sets match the
    `:export`, `:no-options` and `:export-only` matrix runtimes. Loading
    `value-type.core` reads no capability result (only the
    `value-predicate` delay), so the Acceptance 4 "before any read"
    condition is reachable.
- Ambiguity review, pass 3 (2026-11-04): added 1 design step (test
  location and run command).
- Ambiguity review, pass 4 (2026-11-04): added 1 design step (D12 and
  non-nil embodiment targets in Acceptance 7).
  - Cache statements in ramora now: fallback D13 (single owner),
    array D3 "State" (adapter owns delayed results; stays true),
    `fallback.md` "Strict retries" ("Cache capability"; names no owner).
- Inconsistency review (2026-11-04): added 2 design steps.
- Inconsistency review, pass 2 (2026-11-04): added 1 design step.
  - Find readers with: `grep -rn "runtime/capability\|runtime/field-capability\|require-field-capability\|detected-field-capability" test`.
  - Name clash, no finding: `impl.array` already has private delays
    `allocation-capability` and `inspection-capability`. These are the
    accessor names in the design, so the delays need new names. Tests
    that deref the delays by var name must follow the rename (Acceptance 5).
- Inconsistency review, pass 3 (2026-11-04): no inconsistency review
  feedback.
  - Checked against artifacts: Contract table rows match
    `array_capability/allocation-reasons` and `array_info_boundary_test`
    inspection rows; guard ex-data matches `impl.runtime`; current
    `core.clj` reads and emits only the `runtime/` vars the design moves.
- Inconsistency review, pass 4 (2026-11-04): no inconsistency review
  feedback.
  - The two delay-reading tests use the delay only for `realized?`; they
    read results through the accessor. So Acceptance 5 ("read the owner's
    private delay") does not conflict with "every reader must read through
    the accessor".

## Notes for the design-steps slice

- Principles:
  - Scope is frozen. Resolve each step inside the current Scope.
  - Edit design.md in place, with no revision markers (munera evolution).
    Write in ASD-STE100. Keep no backward compatibility.
  - Do not change probe logic, reasons, error data or error order.
- Step interactions:
  - The `:no-flattening` row and the require-order step together set the
    matrix size. Decide them together.
  - The guard-owner step and the Acceptance 4/5 replacement-point step
    together set the accessor surface of each owner. Use one accessor per
    owner for the guard, `report` and test replacement.
- Clojure `delay` caches a thrown exception and throws it again on each
  later deref. A probe defect therefore propagates the same way from every
  reader (D12).
- Paths:
  - Matrix axes and reasons:
    `test/cross-jvm/value_type/array_capability.clj` (`runtimes`,
    `require-orders`, `allocation-reasons`).
  - Controlled absence: `test/compatibility/value_type/cell_harness.clj`
    (`controlled-absence`).
  - Packaged-JAR consumer: `test/resources/build/isolated-consumer.clj`
    and `test/build/build_test.clj` (`isolated-consumer-test`).
  - Docs that name `impl.runtime` as the capability owner and must change
    with the owner move:
    - `ramora/defvaluetype-design/embodiment/runtime.md`
    - `ramora/defvaluetype-design/embodiment/runtime/fallback-interfaces.md`
    - `ramora/defvaluetype-design/embodiment/artifacts.md`
    - `ramora/defvaluetype-design/decisions/validation-and-errors.md`
    - `ramora/deftype-star-design.md`
  - Existing decision-number ranges for the decision-record step: array
    D1–D5 (`ramora/value-type-array-design/decisions.md`) and defvaluetype
    D1–D13 (`ramora/defvaluetype-design/decisions.md`).

## Design-steps slice (2026-11-04)

- All 7 design steps applied to design.md. No `SCOPE_QUESTION:` items.
- Decisions taken in the slice (the design states them; rationale here):
  - Require orders: 3 rotations, not 6 permutations. The probe namespaces
    load once, on the first require, so only the first namespace changes
    which code loads them. Rotations put each namespace in each position
    once. 54 children, not 90.
  - Report timing: the order that starts with `value-type.capability`
    covers "report probes first"; the other two orders cover "definitions
    probe first, report reads the cache". This avoids a second axis.
  - Decision record: D14 in defvaluetype decisions, because D12/D13 there
    are the closest constraints. Array decisions keep D1–D5.
  - New acceptance item is 8, not inserted, so references to Acceptance
    1–7 stay valid.
- Existing behaviour to know:
  - Native enforcement needs a value-class tag. A `^String ^:non-nil`
    field does not read the field probe (`non_nil_fields_test`
    `native-probe-scope-test`: `after-identity` is false). Hence the
    Acceptance 3 field check tags with the `:value-class` check class.
  - The `:value-class` reason has no public definition outcome. Only
    transferred value expansions throw `:value-capability-unavailable`
    with `:reason` (`core/require-value-capability!`). The accessor
    identity check in Acceptance 3 covers the reason.
  - `require-allocation` ex-data is the cached result minus `:cause` and
    `:available?`, so a controlled replacement reason (Acceptance 4)
    appears unchanged as `:reason`.
  - `isolated-consumer-test` runs the consumer on the build JVM
    `java.home` (Valhalla) with three option sets and on Java 8
    (`VALUE_TYPE_BASELINE_JAVA`). Add `value_type/capability.clj` to
    `check-resource-origins!` in `test/resources/build/isolated-consumer.clj`.
  - `api_docs_test` excludes `value-type.impl.` vars, so the new owner
    accessors do not enter the public var list.
  - `ramora/deftype-star-design.md` names `impl.runtime` only for
    `guarded-deftype*`, which stays. It needs no change.

## Notes for the design-steps slice, pass 2

- 4 open steps: A-34 (architecture), Acceptance 2 criterion and
  Dependencies-list completeness (ambiguity), risk test list
  (inconsistency). The pass-1 principles above still apply.
- Each step adds a document or test to the design. None adds behaviour.
  Keep the 54-child matrix unchanged.
- A-34 step: also check the A-29–A-37 evidence paragraph in the same file.
  It names `value-type.runtime-test` for capability. Acceptance 5 moves
  those tests to the owner accessors.
- Acceptance 2 step: the existing invalid-policy pattern is
  `invalid-policy-test` in `test/compatibility/value_type/runtime_test.clj`
  (child JVM, invalid `value-type.fallback`). Re-use it for the report check.
- Dependencies step: the current requires among the named namespaces also
  include `value-type.core → impl.fields` and `impl.fields → impl.capability`.
  `impl.fields` reads only `capability/value-class?`, not a cached result.

## Design-steps slice, pass 2 (2026-11-04)

- All 4 open design steps applied to design.md. No `SCOPE_QUESTION:` items.
- Acceptance 2 runtime: `runtime_test` children use
  `(:selected (config/configuration))`, the parent JVM with only the
  preview option, never export. So the three `:fallback-test` parents map
  to `:java8`, `:no-options` and `:no-export`.
- Child JVMs of `cell_harness` load only `value-type.cell-registry`
  (clojure.core only). Neither `value-type.core` nor `impl.runtime` loads
  unless the cell form requires it, so the Acceptance 2 `find-ns` check is
  sound. `impl.capability`, `impl.field-capability` and `impl.array` have
  no `requiring-resolve` or dynamic `require`.
- Two files are named `field_capability_test`: `test/cross-jvm/...` reads
  `impl.runtime` (delay, accessor, guard); `test/runtime/value_type/impl/...`
  does not.
- A-34 evidence paragraph names `value-type.runtime-test` for capability.
  `capability-test` stays in that namespace and moves to
  `impl.capability/capability`, so the paragraph stays true.

## Notes for the design-steps slice, pass 3

- 2 open steps: API doc generator (architecture) and test location
  (ambiguity). Both add artifacts to the design. Neither changes the
  Contract, the 54-child matrix or the scope.
- API doc step:
  - `bb/value_type/api_docs.clj`: `:source-paths` (line 26). `:overrides`
    applies only to `value-type.core`. The `intro` prose names no
    namespace, so it does not change.
  - `doc/development/quality-checks.md` (lines 74–76) says: fix API prose
    in docstrings or `api_docs.clj`, never in `doc/API.md`. It names no
    source file, so it does not change.
  - `embodiment/artifacts.md` lines 51–58 name the two source files and
    the documented functions.
- Test-location step:
  - `doc/development/runtime.md`: "Verified fallback matrix" (line 59;
    `:fallback-test` on three parents, `:cross-jvm-test` once under
    Java 8) and "Verify array allocation" (line 297; runs
    `value-type.array-matrix-test`). Do not run cross-JVM namespaces with
    `:fallback-test`.
  - Candidate hosts: `test/cross-jvm/value_type/array_matrix_test.clj`
    (six runtimes; uses `array-capability/require-orders`),
    `test/cross-jvm/value_type/controlled_field_capability_test.clj`
    (uses `cell_harness/controlled-absence`; run command at
    `runtime.md` line 208), `test/compatibility/value_type/runtime_test.clj`
    (`invalid-policy-test`).
  - If a new namespace or command is chosen, add `doc/development/runtime.md`
    to the Acceptance 7 document list.

## Design-steps slice, pass 3 (2026-11-04)

- Both open design steps applied to design.md. No `SCOPE_QUESTION:` items.
- Test hosts chosen: new `value-type.capability-matrix-test` (Acceptance 1,
  3) and new `value-type.controlled-value-capability-test` (Acceptance 4),
  both `:cross-jvm-test` with the Java 8 parent; Acceptance 2 is a new
  `deftest` in `value-type.runtime-test`.
  - Not `array-matrix-test`: its require-order axis has two namespaces;
    the new axis has three rotations.
  - Not `controlled-field-capability-test`: `runtime.md` places its command
    under "Verify portable non-nil fields" and says it "replaces only the
    native capability result". A value-class replacement there would make
    that text false.
- Existing behaviour to know:
  - `:cross-jvm-test` adds `test/compatibility` to the class path, so child
    check helpers can go there (pattern: `array_matrix_checks.clj`).
  - `cell_harness/controlled-absence` hard-codes the replaced var. The
    Acceptance 4 test needs its own `with-redefs` of
    `impl.capability/capability`, or a parameterized harness function.
  - `ramora/non-nil-field-design/embodiment.md` (lines 56–64) names
    `controlled-field-capability-test`; it stays true.

## Notes for the design-steps slice, pass 4

- 1 open step (ambiguity: D12 and non-nil embodiment targets in
  Acceptance 7). The pass-1 principles still apply. It changes only the
  Acceptance 7 document list. Do not change the Contract, the matrix or
  the scope. Removing a document item is allowed, because Scope says only
  "Documentation ... updates (see Acceptance)".
- D12 (`ramora/defvaluetype-design/fallback.md`, "D12. Capability
  boundary"):
  - The Boundary bullet says "the public namespace and fallback path must
    load without JEP 401, preview flags or `java.lang.classfile`".
  - The shared "Verification" list (D11) says "Public namespace and
    packaged source JAR load without optional APIs". Acceptance 8 is the
    evidence for `value-type.capability`.
  - D12 says nothing about cache ownership. D13 does, in "Choice" and
    "Rationale".
- Non-nil embodiment (`ramora/non-nil-field-design/embodiment.md`,
  "Implementation"): the only probe statement is "Native capability is
  probed lazily." There is a wording pattern for owner text in array D3
  "State" (`ramora/value-type-array-design/decisions.md`): "the adapter
  owns delayed capability results ..., scoped to one loaded library
  runtime".
- Singular "public namespace" also appears in `ramora/non-nil-field-design.md`
  (line 76) and `non-nil-field-design/verification.md` (line 65). There it
  means `value-type.core`, so it stays true. No step for these.

## Design-steps slice, pass 4 (2026-11-04)

- The open design step applied to design.md (Acceptance 7 targets in
  `fallback.md` and the non-nil embodiment). No `SCOPE_QUESTION:` items.
- D12 Boundary names `value-type.core` and `value-type.capability` only.
  `value-type.array` load safety stays with array D3 ("Constraint:
  namespace loading must work on the library's fallback JVMs"), so D12
  does not repeat it.
- The fallback "Verification" list is headed "D11 requires ...", not D12.
  The design names it by heading ("Verification"), not by decision.
- Exact source text to replace: `fallback.md` D12 "Boundary" bullet
  ("the public namespace and fallback path must load ..."), D13 "Choice"
  ("the policy/capability responsibility owns cached capability, ...")
  and "Rationale" ("one small state owner is enough"); non-nil
  `embodiment.md` line 13 ("Native capability is probed lazily.").

## Design review, pass 5

- Architecture review, pass 5 (2026-11-04): no architectural review
  feedback.
  - Checked the pass-4 Acceptance 7 targets against `fallback.md`: the
    D12 "Boundary" sentence and the D13 "Choice"/"Rationale" text exist
    as quoted. D14 in `decisions.md` with a `decisions/` details file
    follows the D1–D13 index-plus-details pattern.
- Ambiguity review, pass 5 (2026-11-04): added 1 design step (fallback
  "Verification" item: edit or no edit).
  - Item text now (`fallback.md` line 115, under "D11 requires isolated
    JVM tests:"): "Public namespace and packaged source JAR load without
    optional APIs."
- Inconsistency review, pass 5 (2026-11-04): no inconsistency review
  feedback.
  - Checked: the probe namespaces require only `impl.capability`, so
    Acceptance 2 (`impl.runtime` not loaded) holds. The guard in
    `impl.runtime` uses only `field-capability` and `ex-info`, so it
    moves with no other `impl.runtime` dependency.

## Notes for the design-steps slice, pass 5

- 1 open step (ambiguity: fallback "Verification" item, edit or no edit).
  The pass-1 principles still apply. Change only that Acceptance 7 entry.
  Do not change the Contract, the matrix or the scope.
- Either choice is valid. Acceptance 8 gives the evidence in both cases.
  If the entry keeps an edit, use the D12 "Boundary" entry as the wording
  pattern, so that the two entries name the same public namespaces.
- Other singular "public namespace" text in `fallback.md` stays true, so
  it needs no entry: D13 "Choice" line 88 ("not merely on requiring the
  public namespace") is about the warning from `value-type.core`.

## Design-steps slice, pass 5 (2026-11-04)

- The open design step applied to design.md. No `SCOPE_QUESTION:` items.
- Chose an edit, not removal: the fallback "Verification" item becomes
  the exact sentence in Acceptance 7. It names the same two public
  namespaces as the D12 "Boundary" entry, so the two entries agree.
- The item is the only occurrence of that sentence in `ramora/` and
  `doc/` (`fallback.md` line 115). No other document repeats it.

## Design review, pass 6

- Architecture review, pass 6 (2026-11-04): no architectural review
  feedback.
  - Checked, no finding: array embodiment diagram
    (`caller → value-type.array → impl.array`) shows the array API path
    only; array D3 "State"/"Boundary" (updated in Acceptance 7) covers the
    report as a reader. No assertion row names `value-type.array`, so the
    new public namespace needs no new assertion row.
  - Implementation hint: the `ramora/INDEX.md` "Usage guide" entry lists
    the `doc/usage.md` topics. Add "capability report" to it when the
    INDEX link for D14 is added.
- Ambiguity review, pass 6 (2026-11-04): no ambiguity review feedback.
  - Acceptance 4 macro timing is not a design gap: `with-redefs` acts at
    run time, but `defvaluetype` reads capability at expansion. Follow
    `cell_harness/controlled-absence`: require in a separate top-level
    form, and compile the definition inside the redefinition scope (for
    example with `eval`).
- Inconsistency review, pass 6 (2026-11-04): no inconsistency review
  feedback.
  - Checked against artifacts: `isolated-consumer-test` option sets and
    `check-resource-origins!` path list; `runtime.md` sections "Verify
    array diagnostics" and the "Test layout" `test/cross-jvm/` row;
    `invalid-policy-test` values; `consumer_aot_checks/native-field?`;
    next decision number D14; fallback.md D12/D13/Verification text.

## Notes after design review, pass 6

- No open design steps. All three pass-6 reviews gave no feedback. The
  design is stable for `create-task-plan`.
- Do not reopen settled choices from passes 1–5 in planning: 3 rotations
  (54 children), D14 in defvaluetype decisions, new test namespaces, one
  accessor per owner, the guard in `impl.field-capability`.

## Plan review

- Plan ambiguity review (2026-11-04): added 1 design step (require-order
  semantics in plan "Key decisions").
  - Checked, no finding:
    - Slice 2 "Regenerate or verify AOT fixtures": there is no checked-in
      AOT output. The only test that names the guard symbol is the
      cross-JVM `field_capability_test`, which Slice 4 migrates.
    - Slice 1 `impl.array` accessor names: these clash with the current
      private delays (see the inconsistency review, pass 2). No test
      derefs the array delays or a `detected-capability` delay, so their
      new names are free.
  - Implementation hint: three suites assert Contract rows: the matrix
    (`:cross-jvm-test`), `runtime_test` (`:fallback-test`) and the
    isolated consumer (`:build-test`, paths `.` and `test/build`). A
    shared table in `test/compatibility` is visible to the first two
    only. The build test needs its own copy of the rows or a different
    way to share them.
- Plan inconsistency review (2026-11-04): added 2 design steps (slice
  order and test gates; `artifacts.md` parts in Slice 7).
  - Slice-order evidence: `cell_harness/controlled-absence` hard-codes
    `value-type.impl.runtime/field-capability` in its `with-redefs`. It
    is used five times in `controlled_field_capability_test` and named by
    `function_redefinition_test`.
  - Checked, no finding: the plan and steps agree with the design on the
    accessor names, the private delay `detected-field-capability`, the
    require edges, the Acceptance 2 `find-ns` check, the Acceptance 4
    child (`:export`, `silent`) and the Acceptance 7 ramora targets other
    than `artifacts.md`.

## Notes for the design-steps slice (plan review)

- 3 open steps. All three change only plan.md and steps.md. design.md is
  stable: do not change the Contract, the 54-child matrix, the scope or
  the settled choices of passes 1–6.
- Slice-order step:
  - Principle: each slice ends with suites that pass. A test reader moves
    in the same slice as the src change that it depends on.
  - All field-capability readers must switch together, in one slice: the
    guard, `core.clj` (`runtime/field-capability` at line 224; guard calls
    at lines 189 and 192) and the `with-redefs` target in
    `cell_harness/controlled-absence`. If only some switch, the redefined
    accessor and the accessor that `core` reads differ, and
    `controlled_field_capability_test` fails.
  - Temporary delegation from the `impl.runtime` accessors to the owner
    accessors helps only if `with-redefs` targets the owner var. A
    redefinition of the delegating var does not reach readers of the owner
    var.
  - `runtime_test` forms at lines 35, 59, 81 and 302 load `impl.runtime`
    only for policy checks. They need no migration. Only lines 107–109
    read capability.
- Require-order step: the existing pattern is
  `test/compatibility/value_type/array_matrix_checks.clj` line 36
  (`(run! require require-order)` as the first action of the cell check),
  and `array-capability/allocation-children` passes the order as a quoted
  argument.

## Design-steps slice, plan review (2026-11-04)

- All 3 open plan-review steps applied to plan.md and steps.md. design.md
  unchanged.
- Slice order: Slices 1–4 are now owner accessors (`impl.runtime`
  unchanged) → value-class reader switch → guard move and native-field
  reader switch → cleanup check. Slice numbers 5–7 stay, so earlier notes
  that name Slices 6 and 7 stay valid. Earlier notes that name "Slice 4
  migrates" refer to the old order; test readers now move in Slices 2 and 3.
- Slice 1 leaves a temporary double computation: the `impl.runtime`
  delays still call `capability/detect` and `field-capability/detect`
  until Slices 2 and 3. Results are equal, so the suites pass.
- Capability-first order: `report` runs after all three requires. No
  top-level form in `core.clj`, `array.clj`, `impl/array.clj` or the probe
  namespaces derefs a capability delay (only `def`s of delays), so loading
  reads none of the four results.
- New private delay names chosen in steps.md: `impl.capability`
  `detected-capability`; `impl.array` `detected-allocation-capability`,
  `detected-inspection-capability`.
- `embodiment/artifacts.md` targets: source list lines 20–23 (lists
  neither `impl/field_capability.clj` nor `capability.clj` now); API doc
  paragraph lines 51–58; consumer results from line 108 ("Isolated
  source-JAR consumers passed in four configurations").

## Plan review, pass 2

- Plan ambiguity review, pass 2 (2026-11-04): added 1 design step (slice
  test gates: suite names, aliases, parent JVMs).
  - Gate evidence: `:test` runs only on EA3 with preview. The six-runtime
    array matrix (`array-matrix-test`, allocation prerequisite) and the
    cross-JVM `field_capability_test` (field probe) run only in
    `:cross-jvm-test`.
  - Checked, no finding: `cell_harness/controlled-absence` with empty
    requires (`controlled_field_capability_test` line 138, AOT `:wrap`)
    does not need `impl.runtime` from the harness. The wrapped `compile`
    loads `value-type.core`, which requires `impl.runtime`. So the Slice 3
    require swap to `impl.field-capability` is safe. No test derefs the
    `impl.array` delays by name, so the Slice 1 rename is free.
- Plan inconsistency review, pass 2 (2026-11-04): no inconsistency review
  feedback.
  - Not findings:
    - The Slice 4 require-graph check comes before Slice 5 creates
      `value-type.capability`. In Slice 4, check the edges that exist.
      Slice 5 adds a leaf with no reverse edges, so it cannot add a cycle.
    - `embodiment/artifacts.md` calls `test/runtime` "unit" tests. So
      "unit" in the Slice 1/2 gates means `:test`. The open gate-name
      design step can use this.

## Notes for the design-steps slice (plan review, pass 2)

- 1 open step: slice test-gate names. Change only the gate items in
  steps.md and the plan.md text that names suites. Do not change
  design.md, the Contract, the 54-child matrix or the slice order.
- Principle: use the commands in `doc/development/runtime.md` as they are.
  Do not invent commands. Give one name to each suite and use it in
  every slice.
- Suite commands now (`deps.edn` aliases):
  - `:test`: `runtime.md` "Run the tests" (line 48). The alias adds
    `--enable-preview` only. The full suite also needs
    `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` (arrays).
  - `:fallback-test` (three parents) and `:cross-jvm-test` (Java 8 parent):
    `runtime.md` "Verified fallback matrix" (line 59).
  - `:build-test`: `runtime.md` "Run the build tests" (line 464).
  - `:tooling-test`: `doc/development/quality-checks.md` line 44
    (`-M:quickdoc:tooling-test`).
  - `:release-test` exists. Acceptance 6 does not list it.
- Each scry run replaces `.scry-results/`. Keep failure files before the
  next run (`runtime.md` line 55).
- Suggested scope: Slice 1 changes `impl.array` and the field probe. Use
  `:test`, `:fallback-test` and `:cross-jvm-test`, as in Slice 2.

## Design-steps slice, plan review pass 2 (2026-11-04)

- The open gate-name step applied: plan.md "Test gates" names five suites
  (alias, parent JVM, command source) and two gates. Code gate (runtime,
  fallback ×3 parents, cross-JVM) ends Slices 1–3; full gate (code gate +
  build + tooling = Acceptance 6) ends Slices 4, 6, 7. Slice 5 has no gate.
- New-test homes by suite: matrix and controlled test → cross-JVM suite;
  Acceptance 2 (`runtime_test`, `test/compatibility`) → fallback suite;
  Acceptance 8 → build suite.
- Tooling suite runs with `JAVA_CMD` unset (`quality-checks.md` lines
  37–44). `:build-test` alias adds `--enable-preview` to the parent; the
  consumers get their own flags.

## Plan review, pass 3

- Plan ambiguity review, pass 3 (2026-11-04): added 1 design step
  (`runtime_test` readers through the alias `r`).
  - Correction: the earlier note "`runtime_test` forms at lines 35, 59,
    81 and 302 load `impl.runtime` only for policy checks" is wrong for
    lines 35 and 302. Both read `(r/capability)`. Lines 59 and 81 are
    policy checks.
  - Grep for all readers: `grep -rn "impl.runtime" test`, then read each
    alias. Quoted child forms fail in the child at run time, not at
    compile time, so the fallback suite (code gate) is the backstop.
  - Hint: after Slice 2/3, most migrated test files use `impl.runtime`
    for nothing else (only `deftype_star_record_test` keeps
    `runtime/guarded-deftype*`). Remove the unused requires, or
    `bb clj-kondo:lint` can fail (Acceptance 6).
- Plan inconsistency review, pass 3 (2026-11-04): added 1 design step
  (Slice 6 `report` timing wording in steps.md).
  - Checked, no finding: plan/steps/design agree on accessor and delay
    names, slice order, gates, the Acceptance 7 document list and the
    Acceptance 2/4/8 runtimes. The tooling suite stays green in Slice 6:
    `public-reference-test` reads the generated markdown from
    `:source-paths`, which gets `capability.clj` only in Slice 7.
  - Slice 7 hint: `command-line-test` (`api_docs_test.clj` line 112)
    copies the `:source-paths` files into a temporary project. Add
    `src/value_type/capability.clj` to that list, so that the copy
    matches the generator configuration.

## Notes for the design-steps slice (plan review, pass 3)

- 2 open steps. Both change only steps.md (and plan.md "Risks" if the
  grep text is repeated there). Do not change design.md, the Contract,
  the 54-child matrix, the slice order or the gates.
- `runtime_test` readers step:
  - File: `test/compatibility/value_type/runtime_test.clj`. The capability
    reads are in quoted child forms: `capability-test` (lines 33–47) and
    the form at lines 300–303, both with the alias `r`.
  - Evidence to keep: `capability-test` checks `:cached` by `identical?`
    on two accessor calls and `:optional-loaded` false. The migrated form
    requires `impl.capability` and calls its `capability` twice.
  - Principle: a grep that finds readers must not depend on the alias.
    Every current test file that requires `impl.runtime` is listed by
    `grep -rln "impl.runtime" test`.
- Report timing step: change only the Slice 6 sentence. Keep the design
  wording "definition and array calls". The two following Slice 6 items
  (agreement checks, identity check) stay as they are; they compare after
  `report`.

## Design-steps slice, plan review pass 3 (2026-11-04)

- Both open plan-review steps applied to steps.md (and plan.md "Hidden
  readers"). design.md unchanged.
- Alias-independent greps, checked on the current tree:
  - The value-class grep finds 16 hits: `core.clj` 29, 220 and all
    Slice 2 test readers, including `runtime_test` 36, 38, 109, 303.
  - The native-field grep finds `runtime.clj` (delay, guard), `core.clj`
    189, 192, 224, `cell_harness` 58, 66 (docstring and `with-redefs`
    vector), `deftype_star_record_test` 183, `non_nil_fields_test` 212
    and cross-JVM `field_capability_test` 28–32, 88.
  - After migration, files that still load `impl.runtime` (`core.clj`,
    `deftype_star_record_test`, `runtime_test` policy forms) give hits for
    the owner names (`capability/capability`,
    `impl.field-capability/...`). Those hits are correct. So Slice 4
    reads the alias of each hit; "no hits" is not the pass criterion.
- `runtime_test` line 302 form also loads `value-type.source-checks` in
  the identity branch. Only the `(r/capability)` read moves.

## Plan review, pass 4

- Plan ambiguity review, pass 4 (2026-11-04): added 1 design step
  (child require set of `required-capability-policy-matrix-test`).
  - Evidence: cross-JVM `field_capability_test` line 86 child form is
    `(require 'value-type.impl.runtime)` and then calls the guard.
    `impl.field-capability` requires only `impl.capability`, so after the
    move the guard namespace never reads `value-type.fallback`.
  - Slice 6 hint: `cell_harness` checks `*err*` per cell. In a `warn`
    child, the first cell with `:fallback? true` must give the warning
    line of its `:warning` context (or `:any`); all other cells must give
    "". So matrix cells on `:java8`, `:no-options` and `:export-only`
    under `warn` need `:fallback? true` and a `:warning`. One warning per
    child (atomic warning flag), so the second (`:non-nil`)
    `defvaluetype` gives no second line.
  - Checked, no finding: the Acceptance 2 child label comes from
    `jvm-config/configuration` (`:baseline?`, `:available?`); no build
    uses direct linking, so `with-redefs` of the accessor vars reaches
    every reader.
- Plan inconsistency review, pass 4 (2026-11-04): no inconsistency
  review feedback.
  - Checked on the current tree: the Slice 2 and Slice 3 greps give
    exactly the readers that steps.md lists. `function_redefinition_test`
    calls `controlled-absence` and names no `impl.runtime` var, so it
    needs no change. Only the two delays call `capability/detect`, and
    the probes call it directly, as the design states. `impl.capability`
    and `impl.field-capability` have no `capability` or `detected-*`
    vars yet, so the new names are free.

## Notes for the design-steps slice (plan review, pass 4)

- 1 open step: the child require set of
  `required-capability-policy-matrix-test`. Change only the steps.md
  Slice 3 item for cross-JVM `field_capability_test`. Do not change
  design.md, the Contract, the 54-child matrix, the slice order or the
  gates.
- Principle: keep the evidence of the test comment (line 72, "Native
  requirements never warn, fall back, or become successful on retry")
  under each policy (Acceptance 5). If the policy axis stays, a loaded
  namespace must initialize the policy.
- File: `test/cross-jvm/value_type/field_capability_test.clj`.
  - Lines 71–92: the child form is `(require 'value-type.impl.runtime)`,
    then `cell-registry/twice` on the guard. The child preloads
    `value-type.cell-registry` (`cell_harness` line 287). The cells have
    no `:fallback?`, so `*err*` must be "" under `warn`.
  - Option A, require `value-type.core`: after Slice 3 it loads
    `impl.runtime` (policy) and `impl.field-capability` (guard), as for
    an expansion or AOT artifact. Loading it reads no capability result.
  - Option B, require only `impl.field-capability`: then change the test
    comment, because the axis shows only that the guard reads no policy.
  - Same file, line 27 (`capability-matrix-test`): it requires
    `value-type.core` and `impl.runtime` for the laziness check. Its
    migration (`value-type.core` and `impl.field-capability`) is already
    clear; use it for consistency with Option A.

## Design-steps slice, plan review pass 4 (2026-11-04)

- The open plan-review step applied (Option A): the
  `required-capability-policy-matrix-test` child requires
  `value-type.core`, then calls the moved guard. steps.md Slice 3 splits
  the cross-JVM `field_capability_test` item into its two child forms,
  plus a separate `non_nil_fields_test` item; plan.md Slice 3 states the
  rule. design.md unchanged.
- Why it holds: loading `value-type.core` initializes the policy
  (`impl.runtime` `policy` def throws on an invalid value at load) and
  emits no warning (`enforce-fallback!` runs only at expansion or
  evaluation). So under `warn` the cells keep `*err*` "" with no
  `:fallback?`.
- `malformed-probe-test` (same file, line 48) already requires
  `impl.field-capability` and reads no `impl.runtime` var; it needs no
  change.

## Plan review, pass 5

- Plan ambiguity review, pass 5 (2026-11-04): added 1 design step
  (scope of the Slice 1 "no direct deref" check).
  - Why actionable: `@capability/value-predicate` in `impl.array` line 16
    and `core.clj` line 22 is a direct deref of an `impl.capability`
    delay. It is a Method lookup, not a capability result, so the
    Acceptance 4 replacement does not need it: with an unavailable
    `impl.capability/capability`, `discover` returns before line 16.
    A `with-redefs` of `impl.capability/capability` does not change
    `value-predicate`, so `no-value-classes?` still gives the real JVM
    answer in the `require-value-capability!` message.
  - Checked, no finding: `core.clj` reader lines 29, 220, 224 and guard
    lines 189, 192 are current. In `impl.field-capability`, a local var
    `capability` and the alias `capability` (for `impl.capability`) can
    both exist; `capability/capability` and `capability` resolve to
    different vars.
- Plan inconsistency review, pass 5 (2026-11-04): no inconsistency
  review feedback.
  - Checked, no finding: no file in `test/build`, `test/resources`,
    `test/tooling`, `bb` or `dev` reads `impl.runtime`, a probe namespace
    or a capability delay. So the build and tooling suites, which run
    only in the full gate, cannot hide a missed reader from Slices 2–3.
    No test uses `with-redefs` on `impl.capability/detect`, so the Slice 1
    switch of the field probe to the accessor breaks no redefinition.

## Notes for the design-steps slice (plan review, pass 5)

- 1 open step: scope of the Slice 1 "no direct deref" check. Change only
  that steps.md Slice 1 item (and the plan.md Slice 1 text, if it repeats
  the rule). Do not change design.md, the Contract, the 54-child matrix,
  the slice order or the gates.
- Principle: the check protects the `with-redefs` replacement point
  (design "Accessors"). It covers only the four capability-result
  delays. `value-predicate` stays shared and has no accessor: adding one
  is new surface and not in scope.
- Paths: `src/value_type/impl/capability.clj` lines 10–15
  (`value-predicate`, a public delay with a docstring; its reader
  `value-class?` is in the same file). Other readers of the delay are
  `src/value_type/impl/array.clj` line 16 and `src/value_type/core.clj`
  line 22. No test reads it.

## Design-steps slice (plan review, pass 5) — 2026-11-04

- steps.md Slice 1 "confirm" item now names the four capability-result
  delays and excludes `value-predicate`. plan.md does not state the rule,
  so it is unchanged.
- Name clash for the Slice 1 check: `impl.runtime` has its own private
  delays with the same names, `detected-capability` (line 16) and
  `detected-field-capability` (line 62), deref'd only by its own
  accessors (lines 22, 67). A grep for the delay names hits them. They
  are not owner delays; Slices 2 and 3 delete them.
- `impl.array` `require-allocation` (line 32) and `require-inspection`
  (line 90) deref the delays directly now; Slice 1 switches them to the
  accessors.

## Plan review, pass 5 (continued)

- Plan ambiguity review, pass 6 (2026-11-04): no ambiguity review
  feedback.
  - Verified on the current tree: the Slice 2 value-class grep and the
    Slice 3 native-field grep return exactly the readers that steps.md
    lists, including the alias reads (`r/capability` at `runtime_test`
    36, 38, 303). The line references in steps.md (`core.clj` 29, 189,
    192, 220, 224; `cell_harness` 58, 66; cross-JVM
    `field_capability_test` 28–32, 88; `non_nil_fields_test` 212;
    `deftype_star_record_test` 183) match the files. The `impl.array`
    private delays are named `allocation-capability` (line 27) and
    `inspection-capability` (line 85), as the Slice 1 rename assumes.
- Plan inconsistency review, pass 6 (2026-11-04): no inconsistency
  review feedback.
  - Cross-checked design/plan/steps: matrix shape and report timing,
    slice order and gates, per-slice reader migration and deletion
    order (the impl.runtime field delay keeps its probe require until
    Slice 3), accessor/delay/guard names, the `value-predicate`
    exclusion, Acceptance 2/4/8 details and the Acceptance 7 document
    targets all agree.

## Notes after plan review, pass 6

- No open design steps. Both pass-6 plan reviews gave no feedback, so
  plan.md and steps.md are stable for implementation.
- The steps.md line references and greps were verified against the
  working tree at commit 6d2948c4. If implementation starts after other
  commits touch `src/value_type/core.clj`, `src/value_type/impl/*.clj` or
  the listed test files, re-run the Slice 2/3 greps instead of trusting
  the line numbers.
- Do not reopen settled choices in implementation: 3 rotations (54
  children), one accessor per owner, guard in `impl.field-capability`,
  Option A child require set (`value-type.core`) in
  `required-capability-policy-matrix-test`, gates as named in plan
  "Test gates".

## Slice 1: Owner accessors (2026-11-04)

- Owner accessors added: `impl.capability/capability`,
  `impl.field-capability/capability`, `impl.array/allocation-capability`,
  `impl.array/inspection-capability` (zero-arity fns over private
  `detected-*` delays). Field probe and allocation discovery read
  `capability/capability`; `require-allocation`/`require-inspection` read
  the array accessors.
- `impl.runtime` still has its own delays (deleted in Slices 2/3).
- Code gate passed: runtime 152 tests, fallback ×3 parents 68 tests each,
  cross-JVM 97 tests. JDKs at jvm_config defaults
  (`~/.local/jdks/jdk-27.jdk`, temurin-8); env vars not needed locally.

## Slice 2: Value-class reader switch (2026-11-04)

- Grep hits matched the steps.md list exactly; all switched to
  `impl.capability/capability`. Test files that used `impl.runtime` only for
  `capability` now require `impl.capability :as capability` instead.
- `runtime_test` quoted child forms (lines 35, 107, 302) now require
  `value-type.impl.capability` (alias `r` kept), not `impl.runtime`.
- `impl.runtime` now requires only `impl.field-capability` (removed in Slice 3).
- Code gate passed: runtime 152, fallback ×3 68 each, cross-JVM 97.

## Slice 3: Guard move and native-field reader switch (2026-11-04)

- Grep hits matched the steps.md list; no extra readers. No fixture embeds
  the old guard symbol.
- `require-field-capability!` now lives in `impl.field-capability` (body
  unchanged, reads `capability`). `value-type.core` aliases it as
  `field-capability`; syntax-quote emits
  `value-type.impl.field-capability/require-field-capability!`.
- `impl.runtime` has no requires now; docstring already updated to
  "policy, warning state and fallback guards" (Slice 4 docstring item is
  effectively done; verify only).
- `deftype_star_record_test` gained a `field-capability` alias (still needs
  `runtime` for `guarded-deftype*`).
- Code gate passed: runtime 152, fallback ×3 68 each, cross-JVM 97.

## Slice 4: Runtime cleanup check (2026-11-04)

- No code change. `impl.runtime` docstring already correct (Slice 3);
  `impl.runtime` has no requires.
- Greps: every hit resolves to an owner. `core.clj` 30, 221 →
  `impl.capability`; 190, 193, 225 → `impl.field-capability`;
  `runtime_test` alias `r` (lines 36, 38, 303) → `impl.capability`;
  `deftype_star_record_test` 68, 184 → owner aliases; delay reads in
  `non_nil_fields_test` 212 and cross-JVM `field_capability_test` 28, 88
  name `impl.field-capability`. `runtime_test` 59, 81 load `impl.runtime`
  for policy only.
- Require graph matches design: core → {capability, field-capability,
  fields, runtime}; field-capability, array, fields → capability;
  array (public) → impl.array. Acyclic.
- Full gate passed: runtime 152, fallback ×3 68 each, cross-JVM 97,
  build 9, tooling 11. Baseline java:
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`.

## Slice 5: Public facade (2026-11-04)

- `value-type.capability/report` added; `select-keys [:available? :reason]`
  per accessor, so available entries are `{:available? true}` (no `:reason`).
- Manual check: Java 8 with `-Dvalue-type.fallback=bogus` → Contract `:java8`
  row; `impl.runtime` and `value-type.core` not loaded. EA JDK with preview +
  export → all available. kondo/cljfmt clean. No gate (per plan).

## Slice 6: New tests (2026-11-04)

- Shared Contract rows: `test/compatibility/value_type/capability_rows.clj`
  (`rows/report label`), used by the matrix and `runtime_test`. The build
  suite cannot see `test/compatibility`, so `build_test` keeps its own
  4-row copy (`consumer-reports`).
- Matrix child checks: `test/compatibility/value_type/capability_matrix_checks.clj`
  (`check require-order`). It returns `{:report :final :identity?
  :definition :native :allocation :inspection}`; the parent compares to a
  fixed map from the row and policy. Outcome encoding: `:definition`
  `:value|:identity|:rejected`; `:native` boolean or `:skipped`;
  `:allocation` `:allocated` or `[:array-unavailable reason]`;
  `:inspection` `:inspected` or `[:array-inspection-unavailable reason]`.
  Cells with unavailable `:value-class` are `:fallback? true :warning :any`.
- Rotations are local to `capability_matrix_test` (not in
  `array-capability`).
- Acceptance 2: `runtime_test/invalid-policy-report-test`; label from
  `jvm-config/configuration` (`:java8`, `:no-export` when available, else
  `:no-options`).
- Acceptance 4: `make-array Object 1 nil` is enough; the capability check
  runs before component validation.
- Acceptance 8: consumer prints `:report`; `value_type/capability.clj`
  added to `check-resource-origins!`.
- Full gate (JDKs at jvm_config defaults): runtime 152 tests/4900 asserts;
  fallback ×3 parents 69 tests/2183 each; cross-JVM 99 tests/4777, 446 s
  with default `VALUE_TYPE_TEST_JVM_LIMIT`; capability matrix alone 216
  asserts; build 9/111; tooling 11/226. fmt and kondo clean.

## Slice 7: Documentation and quality gates (2026-11-04)

- `public-reference-test` gained `value-type.capability/report` and a
  report-text check; `command-line-test` copies `capability.clj`.
- `doc/usage.md` "Capability report" sits before "Portable non-nil fields";
  example is the `:no-export` Contract row.
- D14 details: `ramora/defvaluetype-design/decisions/capability-report.md`;
  INDEX links it and the usage entry names the report.
- A-34 subject is now "probe namespaces; impl.runtime".
- Gates: api-docs --check, cljfmt, kondo (0/0), vale clean. Full gate:
  runtime 152, fallback ×3 69 each, cross-JVM 99, build 9, tooling 11/235.
- All slices done; task ready for implementation review.

## Implementation review (2026-11-04)

- Added 2 steps to be addressed.

## Review follow-ups (2026-11-04)

- Addressed 2 review steps: `public-reference-test` text names macro, array
  and capability functions; `artifacts.md` function list fixed (one "and")
  and edited lines rewrapped to 78 columns.
- Gates: tooling 11/235, cljfmt, kondo (0/0), vale clean.

## Implementation review, pass 2 (2026-11-04)

- Added 1 step to be addressed.

## Review follow-ups, pass 2 (2026-11-04)

- Addressed 1 review step: isolated-consumer paragraph in `artifacts.md`
  rewrapped to 78 columns; resource list reads "the core, both array and the
  capability source resources".

## Implementation review, pass 3 (2026-11-04)

- Added 3 steps to be addressed.

## Review follow-ups, pass 3 (2026-11-04)

- Addressed 3 review steps: `report` docstring and `doc/usage.md` state that
  each value equals the `:available?` and `:reason` entries of the cached
  result (`doc/API.md` regenerated); knowledge page names
  `impl.field-capability` owner vars; `deftype_star_record_test` requires
  sorted.
- Gates: api-docs --check, cljfmt, kondo (0/0), vale clean; runtime 152.

## Implementation review, pass 4 (2026-11-04)

- Added 2 steps to be addressed.

## Review follow-ups, pass 4 (2026-11-04)

- Addressed 2 review steps: rewrapped the `report` docstring (`doc/API.md`
  regenerated), `doc/usage.md` and the knowledge page to 78 columns;
  `mementum/state.md` states that `report` returns the `:available?` and
  `:reason` entries of the cached results.
- Gates: api-docs --check, cljfmt, kondo (0/0), vale clean; tooling 11/235.

## Implementation review, pass 5 (2026-11-04)

- Added 1 step to be addressed.

## Review follow-ups, pass 5 (2026-11-04)

- Addressed 1 review step: rewrapped the edited ramora prose lines in
  `validation-and-errors.md`, `fallback.md` (D13 "Rationale") and
  `embodiment/runtime.md` to 78 columns. The "Dependency direction"
  paragraph is rewrapped in full, because its last two lines (already over
  120 columns) share a line with the edited text.
- Gates: vale clean.

## Implementation review, pass 6 (2026-11-04)

- Added 1 step to be addressed.

## Review follow-ups, pass 6 (2026-11-04)

- Addressed 1 review step: `doc/development/runtime.md` "Verify the
  capability report" states that the report (`:value-class`,
  `:native-non-nil-field`, `:array-allocation`), `make-array` and the
  definition read the replaced result, and `:array-inspection` stays
  available.
- Gates: vale clean.

## Implementation review, pass 7 (2026-11-04)

- Added 2 steps to be addressed.

## Review follow-ups, pass 7 (2026-11-04)

- Addressed 2 review steps: `impl.array` `allocation-capability` and
  `inspection-capability` docstrings state the owner-accessor replacement
  point; `cell_harness/controlled-absence` docstring rewrapped to 78 columns.
- Gates: clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Implementation review, pass 8 (2026-11-04)

- Added 1 step to be addressed.

## Review follow-ups, pass 8 (2026-11-04)

- Addressed 1 review step: `capability_matrix_checks/definition` returns
  `registry/failed-definition` of `CapPoint` on a throw (via
  `registry/outcome`); `capability_matrix_test/expected` is one described
  `harness/keyed` value, with `harness/failed-definition` and
  `harness/rejection-context` (`:baseline?` from label `:java8`) for the
  `error` rows. Removed `policy-rejection?` and its prefix literal.
- Gates: `value-type.capability-matrix-test` (Java 8 parent) 216 passed;
  clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Implementation review, pass 9 (2026-11-04)

- No implementation review feedback.

## Test review (2026-11-04)

- No test review feedback.

## Test review, pass 2 (2026-11-04)

- Added 1 step to be addressed.

## Test review follow-ups, pass 2 (2026-11-04)

- Addressed 1 review step: `capability_matrix_checks/check` returns
  `:owner (owner-report)` in place of `:identity?`;
  `capability_matrix_test/expected` gives `:owner (harness/fixed report)`.
  A failure now shows the owner accessor result next to the Contract row.
- Gates: `value-type.capability-matrix-test` (Java 8 parent) 216 passed;
  clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Test review, pass 3 (2026-11-04)

- Added 1 step to be addressed.

## Test review follow-ups, pass 3 (2026-11-04)

- Addressed 1 review step: `public-reference-test` "capability report
  keys and reasons" now checks the six Contract reason keywords
  (`:missing-classfile-api`, `:missing-value-predicate`,
  `:unsupported-class-version`, `:missing-array-api`,
  `:missing-array-signature`, `:array-access-denied`). In `doc/API.md`
  each occurs only in the `report` docstring.
- Gates: tooling suite 241 assertions passed; clj-fmt:check clean;
  clj-kondo:lint 0 errors, 0 warnings.

## Test review, pass 4 (2026-11-04)

- Added 2 steps to be addressed.

## Test review follow-ups, pass 4 (2026-11-04)

- Addressed 2 review steps: `runtime_test` child forms of
  `capability-test` and `fallback-source-compatibility-test` alias
  `impl.capability` as `capability`; `public-reference-test` checks the
  full sentence "does not read the `value-type.fallback` policy".
- Gates: fallback suite `value-type.runtime-test` on Java 8, EA3 and EA3
  with preview, 251 assertions passed each; tooling suite 241 assertions
  passed; clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Test review, pass 5 (2026-11-04)

- Added 1 step to be addressed.

## Test review follow-ups, pass 5 (2026-11-04)

- Addressed 1 review step: new runtime-suite namespace
  `test/runtime/value_type/capability_test.clj`.
  `report-translation-test` replaces the four owner accessors with
  results that also hold `:value-predicate` (a Method), `:detail`,
  `:cause` or `:methods`, and checks that `report` gives exactly the four
  keys with only `:available?` and `:reason`.
  `report-defect-propagation-test` replaces
  `impl.array/inspection-capability` with a function that throws, and
  checks that `report` throws the identical Throwable.
- Gates: `value-type.capability-test` 2 assertions passed (`:test`, JDK 25;
  the tests replace all probe results, so they need no Valhalla);
  clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.
- Limit: no Valhalla JDK in this environment (`VALUE_TYPE_VALHALLA_JAVA`
  unset), so the full `test/runtime` suite was not run.

## Test review, pass 6 (2026-11-04)

- No test review feedback.

## Docs review (2026-11-04)

- Added 3 steps to be addressed.

## Docs review follow-ups (2026-11-04)

- Addressed 3 review steps: `README.md` names the capability report
  (Documentation entry, link under "JDK requirement");
  `doc/development/runtime.md` "Run the build tests" describes the
  Acceptance 8 `report` check; "Verify array allocation" names
  `value-type.core` and `value-type.array`. `bb vale` clean.

## Docs review, pass 2 (2026-11-04)

- Added 2 steps to be addressed.

## Docs review follow-ups, pass 2 (2026-11-04)

- Addressed 2 review steps: D14 "Commitment" lists
  `value-type.capability-test`; `doc/development/runtime.md` "Run the
  build tests" and "Verify array allocation" rewrapped to 78 columns.
  `bb vale` clean.

## Docs review, pass 3 (2026-11-04)

- Added 1 step to be addressed.

## Docs review follow-ups, pass 3 (2026-11-04)

- Addressed 1 review step: D14 "Ownership" names the fallback guards
  (`enforce-fallback!`, `guarded-deftype`, `guarded-deftype*`) as
  `impl.runtime` ownership.

## Docs review, pass 4 (2026-11-04)

- Added 1 step to be addressed.

## Docs review follow-ups, pass 4 (2026-11-04)

- Addressed 1 review step: `mementum/state.md` and
  `embodiment/artifacts.md` source list name the fallback guards
  (`enforce-fallback!`, `guarded-deftype`, `guarded-deftype*`) as
  `impl.runtime` ownership.

## Docs review, pass 5 (2026-11-04)

- Added 1 step to be addressed.

## Docs review follow-ups, pass 5 (2026-11-04)

- Addressed 1 review step: `embodiment/artifacts.md` "Development quality
  workflow" names the `defvaluetype` and `defvaluetype*` macros. Lines at
  most 78 columns; `bb vale` clean.

## Docs review, pass 6 (2026-11-04)

- Added 2 steps to be addressed.

## Docs review follow-ups, pass 6 (2026-11-04)

- Addressed 2 review steps: `doc/usage.md` "Portable non-nil fields" names
  `value-type.core`; `doc/development/runtime.md` "Verify the capability
  report" links to the public contract. `bb vale` clean.

## Docs review, pass 7 (2026-11-04)

- No docs review feedback.

## Code-shaper review (2026-11-04)

- Added 1 step to be addressed.

## Code-shaper review follow-ups (2026-11-04)

- Addressed 1 review step: `value-type.capability` and
  `value-type.capability-test` alias `impl.array` as `adapter`;
  `capability-test` aliases `impl.capability` as `capability` and refers
  `report`. Gates: runtime suite (EA3, `JAVA_CMD`) 154 tests, 4902
  assertions passed; `bb clj-fmt:check`, `bb clj-kondo:lint` (0 errors,
  0 warnings) and `bb api-docs --check` clean. Limit: the change renames
  aliases only, so the child-JVM suites did not run again.

## Code-shaper review, pass 2 (2026-11-04)

- Added 1 step to be addressed.

## Code-shaper review follow-ups, pass 2 (2026-11-04)

- Addressed 1 review step: `capability_matrix_checks` returns the array
  error as `(select-keys (ex-data e) [:value-type/error :reason])`;
  `capability_matrix_test/expected` gives the same map. Renamed
  `capability_matrix_checks/outcome` to `array-call` and
  `capability_matrix_test/outcome` to `expected-call`. The outcome
  encoding note (Slice 6) is not restated by a later entry, so it stays.
- Gates: `value-type.capability-matrix-test` (Java 8 parent) 216 passed;
  clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Code-shaper review, pass 3 (2026-11-04)

- Added 2 steps to be addressed.

## Code-shaper review follow-ups, pass 3 (2026-11-04)

- Addressed 2 review steps: `report-translation-test` replaces the owner
  results with the real shapes (value class and field unavailable with
  `:detail`; allocation with `:allocator` and `:predicate`; inspection
  with the four Method keys; allocation unavailable with `:cause`).
  Renamed `first?` to `report-first?` in `capability_matrix_checks/check`;
  the docstring already agrees.
- Gates: runtime suite (EA3) 154 tests, 4903 assertions passed;
  `value-type.capability-matrix-test` (Java 8 parent) 216 passed;
  clj-fmt:check clean; clj-kondo:lint 0 errors, 0 warnings.

## Code-shaper review, pass 4 (2026-11-04)

- Added 1 step to be addressed.

## Code-shaper review follow-ups, pass 4 (2026-11-04)

- Addressed 1 review step: `array_capability` requires
  `value-type.capability-rows` and derives the allocation reason with a
  private `allocation-reason`
  (`(:reason (:array-allocation (rows/report label)))`). Removed
  `allocation-reasons`. The `build_test/consumer-reports` copy stays.
- Gates: `value-type.array-matrix-test`,
  `value-type.array-sequence-boundary-test` and
  `value-type.capability-matrix-test` (`:cross-jvm-test`, Java 8 parent)
  3 tests, 504 assertions passed; clj-fmt:check clean; clj-kondo:lint
  0 errors, 0 warnings.

## Code-shaper review, pass 5 (2026-11-04)

- Added 1 step to be addressed.

## Code-shaper review follow-ups, pass 5 (2026-11-04)

- Addressed 1 review step: `array_info_boundary_test` requires
  `value-type.capability-rows`. `inspection-rows` is now a function that
  derives `supported?` and `reason` from `(:array-inspection (rows/report
  label))` over the labels of `array-capability/runtimes`; a local
  `inspection-causes` map gives the cause class name per reason.
  `export-only-reason` derives from `(:array-allocation (rows/report
  :export-only))`.
- Gates: `value-type.array-info-boundary-test` (`:cross-jvm-test`, Java 8
  parent) 2 tests, 156 assertions passed; clj-fmt:check clean;
  clj-kondo:lint 0 errors, 0 warnings.

## Code-shaper review, pass 6 (2026-11-04)

- Added 2 steps to be addressed.

## Code-shaper review follow-ups, pass 6 (2026-11-04)

- Addressed 2 review steps: `report-translation-test` now replaces the
  owner results with two reachable states, `:export-only` (one unavailable
  value-class result with `:detail` for the first three keys; inspection
  available with the four Method keys) and `:no-export` (value class and
  field available; allocation and inspection `:array-access-denied` with
  `:cause`). `capability_matrix_test/expected` destructures
  `native-non-nil-field` with the other report keys.
- Gates: `value-type.capability-test` (`:test`) 2 tests, 3 assertions
  passed; `value-type.capability-matrix-test` (`:cross-jvm-test`, Java 8
  parent) 1 test, 216 assertions passed; clj-fmt:check clean;
  clj-kondo:lint 0 errors, 0 warnings.

## Code-shaper review, pass 7 (2026-11-04)

- No code-shaper review feedback.
