# Implementation

## Design review

- 2026-11-06 architecture review (design-review turn 1): added 1 design step.
  - Sources: `ramora/INDEX.md` (no `ramora/META.md` or
    `doc/architecture.md`), D10, D12, D14, D17, D18,
    `fallback/selected-detector.md`, `validation-and-errors.md`,
    `embodiment.md` gap table.
  - Fits: switch inside the `impl.capability` delay keeps D14 single owner
    and test replacement point; field and array capability already derive
    from `capability/capability`, so `:disabled` propagates without change;
    D12 and validation-and-errors already list `:disabled`.
  - Context for later turns: invalid-policy precedent is
    `IllegalArgumentException` from a delay in `impl.runtime` (same exception
    on each read). Under `off`, definitions that require native non-nil
    enforcement throw `:field-capability-unavailable` independent of policy
    (`require-field-capability!`); the "Contract" names only policy fallback.
- 2026-11-06 ambiguity review (design-review turn 2): added 3 design steps.
  - Checked, not ambiguous: under `off`, `:non-nil` fields select generated
    enforcement (`select-enforcement` sees capability unavailable), so
    `require-field-capability!` does not run; the turn-1 note about
    `:field-capability-unavailable` applies only when value capability is
    available.
- 2026-11-06 inconsistency review (design-review turn 3): added 1 design step.
- 2026-11-06 architecture review, round 2 (after design steps applied): no
  architectural review feedback.
  - Checked against D12 (`:disabled` is established absence; invalid value
    is an error), D14 (one delay owner, report shows only `:available?` and
    `:reason`, no partial report), D16 (`off` loads no probe builder), D17/D18
    (AOT interim limit only in usage guide and gap table) and
    `validation-and-errors.md` (lists the switch, states no order).
- 2026-11-06 ambiguity review, round 2: no ambiguity review feedback.
  - Code confirms: allocation `discover` returns the value-class result
    before it checks array APIs, so Java 8 under `off` gives `:disabled`, not
    `:missing-array-api`; `:array-unavailable` ex-data keeps `:reason` and
    `:detail`.
- 2026-11-06 inconsistency review, round 2: no inconsistency review
  feedback. Task 026 design and `munera/plan.md` match the AOT deferral;
  `enforce-fallback!` prefix and the invalid-policy message format match the
  design.

## Notes for addressing design-steps

- Principles: edit design.md in place (no amendment notes); keep scope frozen;
  ramora describes design, not status (status only in the embodiment gap
  table); prose in ASD-STE100 style (`bb vale`).
- Order step and invalid-value step interact: if the switch is read after the
  API checks, an invalid value does not throw on JVMs without the APIs.
  Decide the order first.
- AOT step: D18 in `probe-and-format.md` already states the target AOT
  behaviour (throw at initialization, D17); the design must state only the
  interim behaviour in this slice, not change D18.
- Files to read when deciding:
  - `src/value_type/impl/capability.clj` (`detect`, `unavailable`, delay)
  - `src/value_type/impl/runtime.clj` (invalid-policy precedent)
  - `src/value_type/impl/array.clj` (`require-allocation` message)
  - `src/value_type/core.clj` (`enforce-fallback!` gets `:detail`;
    `require-value-capability!`, `no-value-classes?` message)
  - `src/value_type/capability.clj` (`report` reason list)
  - `ramora/defvaluetype-design/fallback/selected-detector.md`
  - `ramora/defvaluetype-design/embodiment.md` (gap table rows D17, D18)
  - `doc/usage.md`

## Design steps addressed

- 2026-11-06 all 5 design steps applied to design.md (no `SCOPE_QUESTION:`
  items).
  - Order: switch read first (kept "Architecture"); `selected-detector.md`
    added to docs. Matrix JVM without the APIs is only `:java8`; Valhalla
    without preview has both APIs (`:unsupported-class-version`).
  - Existing behaviour for implementation:
    - `unavailable` in `impl.capability` takes a Throwable for `:detail`;
      `:disabled` needs a fixed detail string instead.
    - Field (`detect`) and allocation (`discover`) return the value-class
      result as-is when unavailable, so `:reason` and `:detail` propagate
      without change. `require-allocation` adds the detail only for
      `:failed-self-check`; extend that branch to `:disabled`.
    - Clojure delays cache a thrown exception; field and allocation delays
      then cache the same IAE object, so "same exception" holds everywhere.
    - Definitions read the capability before the policy
      (`policy-expansion`); `report` reads the policy first.
    - `make-array`/`into-array` call `require-allocation` before any
      argument check.
    - AOT value receiving output with native fields emits
      `require-field-capability!` at initialization; other AOT value output
      only calls `check-value-class` (`value-class?`, no capability read).
    - `require-value-capability!` uses `no-value-classes-message` when
      `isValue` is missing, so on Java 8 under `off` a value receiving
      form does not show the `:disabled` detail. Not in the contract.
    - Expected report rows: `test/compatibility/value_type/capability_rows.clj`;
      invalid-value child-test precedent:
      `test/cross-jvm/value_type/fallback_policy_test.clj`.

## After design review round 2

- No open design steps. The design is stable; the next step is planning
  (`plan.md`, `steps.md`).
- Files to read during planning, not listed above:
  - `src/value_type/impl/field_capability.clj` (`detect` returns the
    value-class result as-is; `require-field-capability!` message appends
    `:detail`)
  - `src/value_type/array.clj` (public `make-array`, `into-array`,
    `array-info`)
  - `doc/usage.md` capability-report section ("probe reasons", near
    `:failed-self-check`): place for `:disabled` and the switch
  - `ramora/defvaluetype-design/decisions/validation-and-errors.md` (D12
    detector sentence lists the switch, no order; edit only if the wording
    becomes false)

## Plan review

- 2026-11-06 ambiguity review (plan-review turn 1): added 5 design steps.
  - Context: `capability_rows/reasons` is keyed by runtime label;
    `array-capability/runtimes` feeds `capability_matrix_test`,
    `array_matrix_test` and `array_info_boundary_test`, so a new label there
    grows several matrices. `jvm_runner/start-command` passes `:jvm-opts` as
    separate process args (no shell), so ` off` and the empty string
    (`-Dvalue-type.value-classes=`) pass unchanged.
  - Checked, not ambiguous: plan decision 2 ("directly or a string-detail
    variant") is an internal choice with the same result.
- 2026-11-06 inconsistency review (plan-review turn 2): no inconsistency
  review feedback. The plan "Risks" claim that the checklist names each
  invalid-value entry point conflicts with steps.md (no AOT field init); the
  turn-1 design step already covers it.

## Notes for addressing plan-review design steps

- Targets: these steps change plan.md and steps.md. Change design.md only if
  a decision changes "Acceptance" (step 2: Acceptance 4; step 4:
  Acceptance 3). Review passes do not edit steps.md; the pass that addresses
  these steps does.
- Steps 1 and 2 interact: both select where the switch property enters the
  child JVM. Decide them together.
  - `jvm_runner/start-command` adds `:jvm-opts`, then `policy-option` from
    `:policy`. Pass the switch as an added `:jvm-opts` entry, or add a
    `policy-option`-like option.
  - Each label added to `array-capability/runtimes` adds 18 children to
    `capability_matrix_test` (3 policies × 3 require orders) and also grows
    `array_matrix_test`, `array_info_boundary_test` and
    `allocation-configurations`.
  - `capability_matrix_test/expected` sets the `error` context with
    `harness/rejection-context {:baseline? (= :java8 label)}`. On Java 8
    under `off` the context is the `:disabled` detail, not
    `unavailable-detail` (`java/lang/classfile/ClassFile`). A Java 8 `off`
    label needs its own context.
- Steps 3 and 4 (AOT):
  - Correction to step 3: native fields need no export
    (`capability_rows` `:no-export` has `:native-non-nil-field` available).
    `config/value-producer` (EA3, preview, policy `error`, no property) is
    a correct producer.
  - `aot-fixture/incompatible-consumers` is not reusable as-is: it expects
    `consumer-aot-checks/incompatible-result` (`:unsupported-class-version`).
    Use `aot-fixture/source-free-consumers` with a receiver of `valhalla`
    plus the switch option, and a new expected value
    (`:field-capability-unavailable`, `:reason :disabled`; for `OFF` the IAE
    in the cause chain). `consumer-aot-checks/strict-consumer` gives the
    load outcome and root failure.
  - Fixtures: native fields `restricted.aot-fixture`
    (`test/cross-jvm/value_type/field_aot_test.clj`); no native fields
    `checked.aot-fixture` with `consumer-aot-checks/string-holder`
    (`test/cross-jvm/value_type/generated_value_aot_test.clj`).
- Step 5: the docstring is in `src/value_type/capability.clj`; it already
  states the policy-before-probe order. Regenerate `doc/API.md` after it changes.

## Plan-review design steps addressed

- 2026-11-06 all 5 plan-review design steps applied to plan.md (decisions
  5 and 6) and steps.md (Slices 3 and 4). design.md not changed: the
  one-time `auto` run meets Acceptance 4 as written; the AOT invalid-value
  test is coverage beyond Acceptance 3.
- Existing behaviour found, not in plan.md:
  - `capability-matrix-checks/array-call` keeps only `:value-type/error`
    and `:reason`, so the `:disabled` message needs its own check.
  - `harness/expected-warning`: a string context must equal the whole
    warning line; `:any` matches exactly one line. A `warn` child writes
    one warning line.
  - The native-field AOT guard is a top-level form of the namespace init,
    before the definition (`core.clj` `value-receiving`). A second
    `require :reload` after a failed init can give another failure, so the
    new AOT check loads once (`consumer-aot-checks/strict-consumer` loads
    twice and keeps no ex-data).
  - `checked.aot-fixture` calls `loader/install!` and `deftype` directly,
    not `defvaluetype`; it reads no capability.
  - `build_test.clj` `consumer-reports` repeats four `capability_rows`
    rows; the new labels need no change there.

## Plan review, round 2

- 2026-11-06 ambiguity review (plan-review round 2, turn 1): added 2 design
  steps.
  - Step 1 basis: `jvm_runner/start-command` puts `:jvm-opts` before the
    policy option; HotSpot keeps the last `-D` of a property.
  - Checked, not actionable: the policy of the invalid-value children (the
    definition reads the capability before the policy, so the result does
    not change); the AOT failure `:chain` (the check returns it through
    `registry/thrown`; the implementation records the observed chain);
    `:warning` takes the context, and `harness/warning-line` adds the
    prefix.
- 2026-11-06 inconsistency review (plan-review round 2, turn 2): added 2
  design steps.
  - Step 1 basis: `defvaluetype` value output without native fields calls
    `check-value-class` at init (`core.clj` `value-expansion`,
    `value-receiving`). It reads `capability/value-class?` (the
    `isValue` predicate), not the cached capability, so under `off` the
    expected result stays `string-holder-result true`.

## Notes for addressing plan-review round 2 design steps

- Four open steps (two ambiguity, two inconsistency). Targets: plan.md
  decisions 5 and 6, and steps.md Slices 3 and 4. Change design.md only for
  the usage-guide step if the invalid-value failure joins the "Scope" docs
  list.
- `auto` run step: the simplest option that keeps the axis complete is to
  exclude `value-type.value-class-switch-test` (its `:export-auto` children
  already cover `auto`). Runtime tests that read JVM input arguments
  (`test/runtime/value_type/environment_test.clj`, `array_info_test.clj`,
  `array_layout_test.clj`) use `.contains`; an added `-D` does not change
  them.
- Invalid-value step, precedents:
  - `test/compatibility/value_type/fallback_policy_checks.clj`
    `invalid-report` (`caught`, `set-policy!`, `:second-identical?`,
    `:cause-identical?`); expected map in
    `test/cross-jvm/value_type/fallback_policy_test.clj`.
  - `test/compatibility/value_type/array_sequence_checks.clj`
    `unavailable-call` (nil component, `lazy-seq` effect counter,
    `registry/rejected-call` → `{:exception :data :message :cause}`).
  - `capability_matrix_checks/inspection` uses `(object-array 1)`.
- AOT step: a direct precedent exists:
  `public_field_aot_test.clj` `source-free-public-generated-value-test` (line
  104) compiles `identity-source` with `config/value-producer` and expects
  `string-holder-result true`. Use `config/value-producer` as producer (not
  plain `valhalla`) for consistency with the native-field AOT test. The
  fixture namespace is also `checked.aot-fixture`, so
  `consumer-aot-checks/string-holder` applies unchanged. `identity-source`
  is a public var; require it, do not copy it.

## Plan-review round 2 design steps addressed

- 2026-11-06 all 4 steps applied to plan.md (decisions 5 and 6) and
  steps.md (Slices 3 and 4). design.md not changed.
  - `auto` run: exclude the switch test namespace. Scry 0.1.61 CLI has no
    exclude flag; `--ns-pattern` is matched with `re-matches`, so a negative
    lookahead works (checked:
    `(?!value-type\.value-class-switch-test$).*-test` rejects the switch
    test and `-checks` namespaces, keeps other `-test` namespaces).
    `value-class-switch-checks` ends in `-checks`, so `:fallback-test`
    (`--dir test/compatibility`) does not discover it.
  - Invalid-value check: `registry/rejected-call` and
    `registry/guarded-call` catch only ExceptionInfo; the IAE needs a
    Throwable catch (`registry/outcome`, or `caught` as in
    `fallback-policy-checks`). `array-info` validates the argument before
    `require-inspection`, so a nil argument shows nothing.
  - Usage guide: the invalid-value failure was removed from steps.md, not
    added to design "Scope" (design.md is read-only for the plan profile).
    The `report` docstring (and `doc/API.md`) documents it.

## Plan review, round 3

- 2026-11-06 ambiguity review (plan-review round 3, turn 1): no ambiguity
  review feedback.
  - Checked, not actionable: the policy-first child check name and return
    shape (precedent `fallback-policy-checks/invalid-definition`:
    `registry/failed-call registry/thrown`); the message-child arguments
    and return shape (`require-allocation` runs before any argument
    check, so any arguments give the same result); the gap-table wording
    (D17 row already names the AOT initialization check).
  - Code confirms: the emitted init of native-field value output has only
    `require-field-capability!` (`require-value-capability!` runs at
    macroexpansion), so the AOT root failure is from the field capability;
    only `array-capability/expected-flags` compares child `:flags`, so the
    one-time `auto` edit needs no other expected-value change.
- 2026-11-06 inconsistency review (plan-review round 3, turn 2): no
  inconsistency review feedback.

## After plan review round 3

- No open design steps: round 3 added none, and all earlier steps are
  checked. The plan is stable; the next step is implementation (Slice 1).
- Test support files (paths not given elsewhere in the task):
  - `test/compatibility/value_type/cell_registry.clj`: `thrown`,
    `outcome`, `failed-call`, `invalid-policy-message`, `runtime-facts`.
  - `test/compatibility/value_type/cell_harness.clj`: `cell-child`,
    `failed-definition`, `expected-warning`, `rejection-context`.
  - `test/compatibility/value_type/aot_fixture.clj`:
    `source-free-consumers`, `consumer-child`.
  - `test/compatibility/value_type/jvm_config.clj`: `value-producer`,
    `policies`.
  - `test/compatibility/value_type/jvm_runner.clj`: `start-command`
    (the one-time `auto` edit).
  - `test/cross-jvm/value_type/array_capability.clj`: `runtimes`,
    `expected-flags` (the one-time `auto` edit).

## Slices 1 and 2 (2026-11-06)

- `impl.capability`: public `disabled-detail` (tests can reference it);
  private `switch` runs first in `detect` via `(or (switch) ...)`.
- `impl.array/require-allocation`: `:disabled` shares the
  `:failed-self-check` message branch (set membership).
- Verified by child JVMs: `off` on Java 8 → `:disabled` (not
  `:missing-value-predicate`); `off` on EA3 preview → report rows
  `:disabled` x3, inspection `:array-access-denied`; `OFF` → IAE, same
  object on second read (delay caches it); `auto` unchanged.
- Propagation needed no change (field `detect`, array `discover`, fallback
  `enforce-fallback! detail`, `require-field-capability!` reason).
- Runtime suite needs `JAVA_CMD=~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  (env vars unset here): 186 tests pass. fmt and kondo pass.
- Next: Slice 3 (tests).

## Slice 3 (2026-11-06)

- Tests: `test/cross-jvm/value_type/value_class_switch_test.clj` (7 tests,
  104 assertions, all pass); checks
  `test/compatibility/value_type/value_class_switch_checks.clj`; shared
  `value-type.capability-expectations` (`expected [label policy context]`).
- Off-label report children use `:warning disabled-detail` for all labels;
  harness checks it only for `warn` fallback cells.
- Observed AOT init chain (no CompilerException wrapper):
  `["java.lang.ExceptionInInitializerError" <root>]`. Native-field `off`
  root message: `Cannot define restricted.aot_fixture.Holder: native
  non-nil field enforcement is unavailable. <disabled detail>`.
- AOT tests reuse public vars `field-aot-test/restricted-source` and
  `public-field-aot-test/identity-source` (test ns requires test ns).
- One-time `auto` run (edit in `jvm_runner/start-command` and
  `array-capability/expected-flags`, reverted):
  - `JAVA_CMD=<java8> clojure -M:cross-jvm-test --ns-pattern
    '(?!value-type\.value-class-switch-test$).*-test'`: 103 tests pass.
  - `JAVA_CMD=<ea3> clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
    -J-Dvalue-type.value-classes=auto -M:test -m scry.cli --dir test/runtime`:
    186 pass. (`-M:test` alone has no main; it opens a REPL.)
  - `:fallback-test` with `-J-Dvalue-type.value-classes=auto`: Java 8 74
    pass; EA3 74 pass; EA3 `--enable-preview` 73 pass, 1 unknown
    (`identity-receiving-test/value-receiving-star-closed-option-order-test`).
    The unknown also occurs without the property: pre-existing, not from
    this task. Slice 5 must accept it or track it separately.
- Next: Slice 4 (docs).

## Slice 4 (2026-11-06)

- Docs done: `report` docstring (exact plan text), `doc/API.md`
  regenerated, usage guide section `### Value-class switch` under the
  fallback policy section plus a `:disabled` bullet in the probe-reason
  list, CHANGELOG entry, gap-table D18 row (only the AOT part remains,
  task 026), `selected-detector.md` (switch read first), `mementum/state.md`.
- `bb vale` and `bb api-docs --check` pass.
- Next: Slice 5 (full verification).

## Slice 5 (2026-11-06)

- All pass: runtime 186; fallback Java 8 74, EA3 74; cross-JVM 110 (incl.
  7 switch tests); build 9; tooling 11; release 12; fmt, kondo, vale,
  api-docs --check.
- EA3 `--enable-preview` fallback: 73 pass, 1 unknown
  (`identity-receiving-test/value-receiving-star-closed-option-order-test`,
  scry exit 1). It also occurs on `6975e057^` (before task 025): a
  pre-existing problem outside this task's scope. Track it separately.
- All steps done; ready for implementation review.

## Implementation review

- 2026-11-06 implementation review: added 4 steps.
- 2026-11-06 addressed 4 review steps: usage switch section moved after
  the fallback-policy text; `detect` docstring wrapped;
  `disabled-detail` private; allocation-message test selects off runtimes
  by label. Verified: switch test 7 pass, runtime 186 pass, fmt, kondo,
  vale, api-docs --check.
- 2026-11-06 implementation review, round 2: added 3 steps.
- 2026-11-06 addressed 3 review steps (round 2): `caught` moved to
  `value-type.cell-registry` (public), used by both check namespaces;
  `allocation-messages` uses `registry/rejected-call`, test expects its
  outcome map; usage `:disabled` moved out of the probe-reason list.
  Verified: switch, fallback-policy, cell-registry tests (14 pass), fmt,
  kondo, vale.
- 2026-11-06 implementation review, round 3: no implementation review
  feedback.
- 2026-11-06 test review: added 1 step.
- 2026-11-06 addressed 1 test review step: AOT tests no longer pin the
  cause chain (`aot-chain` removed; `aot-load` uses `registry/failure`).
  Verified: switch test 7 pass, fmt, kondo.
- 2026-11-06 test review, round 2: added 1 step.
- 2026-11-06 addressed 1 test review step (round 2): `switch-probe-test`
  runs `value-class-switch-checks/probe-loads` on `:export-off` and
  `:export-auto` with `-Xlog:class+load:file=<temp log>`; after `report`
  it counts log lines that name `CapabilityProbe`: off 0 (`:disabled`),
  auto 1. No production hook. Verified: switch test 8 pass, fmt, kondo.
- 2026-11-06 test review, round 3: added 1 step.
- 2026-11-06 addressed 1 test review step (round 3): `probe-loads`
  returns `:probe-loaded?` (a log line names `CapabilityProbe`); expected
  off `false`, auto `true`. Verified: switch test 8 pass, fmt, kondo.
- 2026-11-06 test review, round 4: no test review feedback.
- 2026-11-06 test review, round 5: added 1 step.
- 2026-11-06 addressed 1 test review step (round 5):
  `switch-fixed-read-test` runs `value-class-switch-checks/fixed-read` on
  `:export-off`: `report`, set the switch to `auto`, `report` again;
  expected `:disabled` at both reads. Verified: switch test 9 pass, fmt,
  kondo.
- 2026-11-06 test review, round 6: no test review feedback.
- 2026-11-06 docs review: added 2 steps.
- 2026-11-06 addressed 2 docs review steps: README names
  `value-type.value-classes=off` and lists the value-class switch in the
  usage-guide link; usage states the first read (first definition,
  `report`, `make-array`, `into-array`; top-level definition at load), the
  recommended JVM option and the `System/setProperty` limit. Verified: vale.
- 2026-11-06 docs review, round 2: added 1 step.
- 2026-11-06 addressed 1 docs review step (round 2): `defvaluetype`
  docstring adds "unless JVM property `value-type.value-classes` is
  `off`"; `make-array` docstring adds the switch requirement. Regenerated
  `doc/API.md`. Verified: vale, fmt, `bb api-docs --check`.
- 2026-11-06 docs review, round 3: added 3 steps.
- 2026-11-06 addressed 3 docs review steps (round 3): embodiment
  `runtime.md` `impl.capability` row adds the switch read (first) and
  D18; `artifacts.md` adds "`value-type.value-classes` not `off`";
  usage "AOT limits" links to the value-class switch limit. Verified: vale.
- 2026-11-06 docs review, round 4: added 1 step.
- 2026-11-06 addressed 1 docs review step (round 4): README introduction
  and "Value arrays" add the condition `value-type.value-classes` not
  `off`. Verified: vale.
- 2026-11-06 docs review, round 5: added 2 steps.
- 2026-11-06 addressed 2 docs review steps (round 5): README introduction
  and "Value arrays" paragraphs rewrapped (no text change);
  `doc/development/runtime.md` adds "Verify the value-class switch" (command,
  child groups with counts, guide link). Verified: vale.
- 2026-11-06 docs review, round 6: no docs review feedback.
- 2026-11-07 code-shaper review: added 4 steps.
- 2026-11-07 addressed 4 code-shaper review steps: switch checks call
  vars through `resolve`, share one private `require-public`, name
  `failure-of` in `invalid-value`, and use the now public
  `capability-matrix-checks/inspection`; switch test `runtimes` is a label
  map (selection by key), `invalid-value-test` destructures
  `{:keys [baseline valhalla]}` once, `child` docstring names `context`
  and the cell options. Verified: switch test 9 pass (116 assertions),
  fmt, kondo.
- 2026-11-07 code-shaper review, round 2: added 3 steps.
- 2026-11-07 addressed 3 code-shaper review steps (round 2):
  `invalid-value` `same?` is false when `report` does not throw;
  `value_class_switch_checks/switch-property` is private; the allocation
  and probe tests bind `(runtimes)` once. Verified: switch test 9 pass
  (116 assertions), fmt, kondo.
- 2026-11-07 code-shaper review, round 3: added 2 steps.
- 2026-11-07 addressed 2 code-shaper review steps (round 3):
  `impl.capability/switch` reads the property with default `auto` and
  maps it with `case`; `value_class_switch_checks` has one private
  `report` fn, and `invalid-value` calls `make-array` and `into-array`
  through `resolve` at the call site (no shadowing locals). Verified:
  switch test 9 pass (116 assertions); `impl.capability-test` and
  `capability-test` on EA3 6 pass; fmt, kondo.
- 2026-11-07 code-shaper review, round 4: added 2 steps.
- 2026-11-07 addressed 2 code-shaper review steps (round 4):
  `invalid-value` `:definition` uses `registry/outcome` with `failure-of`
  (bound in the `let`, so it runs before the array calls; a returning
  definition gives `:accepted`); the lazy-input fn is `make-input`.
  Verified: switch test 9 pass (116 assertions), fmt, kondo.
- 2026-11-07 code-shaper review, round 5: added 3 steps.
- 2026-11-07 addressed 3 code-shaper review steps (round 5):
  `aot-load` uses `registry/root-failure` with narrowed `:data`;
  `invalid-value` docstring names all returned keys; `switch-probe-test`
  iterates label and expected pairs (no branch). Verified: switch test 9
  pass (116 assertions), fmt, kondo.
- 2026-11-07 code-shaper review, round 6: no code-shaper review feedback.
