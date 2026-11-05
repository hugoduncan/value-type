# Design steps

- [x] Architecture: make Acceptance 10 keep the ramora deftype* design in
  line with the new architecture, not only remove the Gap paragraph. The
  Embodiment element list now says `value-type.core` has "three declaration
  checks", names no receiving macro for `defvaluetype*`, and does not name
  the identity backend delegation rule or record value storage. "Qualities
  and risks — Observed" says "This evidence has no non-nil fields". D6 and
  D8 say "provisional, until the D7 non-nil gate passes". State that the
  task updates the Embodiment elements, the Observed quality with the
  non-nil gate evidence and limits, and the D6/D8 commitment per the gate
  result (or state explicitly that the commitment stays provisional, as D3
  did after the first gate).
- [x] Ambiguity: define how the receiving boundary gets the shape and which
  `defvaluetype*` declaration checks it repeats. "It carries the shape to
  the capture loader" permits two readings: the receiver derives the shape
  from the fields (and repeats `:mutable-field`, `:record-shape` and
  `:hidden-field`), or the expansion transports the shape as data, like the
  enforcement modes. For the second reading, state the error for an invalid
  transported shape. The `defvaluetype` receiving macros repeat only
  `:mutable-field`, and `install-receiving!` passes `:plain` always.
- [x] Ambiguity: state the exact construction paths that Acceptance 2 and 3
  cover, and how the record code constant path can show rejection.
  - The Contract lists record paths only. It does not name the main
    constructor called directly, reflective constructor calls (non-nil D4),
    or the paths of the plain shape (`deftype*` emits no `->Name` factory).
  - A compiled record constant comes from `eval` of a form that embeds an
    instance (see `spike/record-value-type/README.md`). An instance with
    `nil` in an enabled field cannot exist, so "record code constants reject
    `nil`" has no direct test. State the observable evidence (for example,
    emitted constants call `create`, which rejects `nil`), for the
    feasibility gate and for Acceptance 3.
- [x] Ambiguity: state whether a delegating constructor that writes an
  enabled field gives `:unsupported-shape`. The Contract says a delegating
  constructor "writes no enabled field", but does not say if this is a
  check of the identity backend or only a description of compiler output.
- [x] Ambiguity: state where the non-nil gate script goes. "Keep it
  runnable next to the existing gate" permits a change to
  `spike/record-value-type/gate.clj`, but
  `ramora/deftype-star-design/feasibility-gate.md` records the SHA-256 of
  that file as evidence of the D3 gate. State that the non-nil gate is a
  separate script (or that the change records a new hash and keeps the D3
  evidence valid).
- [x] Inconsistency: Acceptance 8 says the existing `defvaluetype*` tests
  pass, but Acceptance 5 removes `:non-nil-unsupported`, and existing tests
  assert it: `field-metadata-rejection-test` in
  `test/runtime/value_type/deftype_star_test.clj`, and the "declaration
  error" policy cell in `test/compatibility/value_type/runtime_test.clj`
  (`^:non-nil x` with no tag gives `:missing-tag` after D6). State that
  tests of the earlier D6 rejection are replaced by D6 tests, and that
  Acceptance 8 covers the other existing tests.
- [x] Inconsistency: the Contract (and the deftype* design and D6) say
  record literal forms reject `nil` "with `NullPointerException`", with no
  wrapping clause. The non-nil field design permits wrapping for
  reflection, and Clojure wraps reader errors: a constructor literal that
  throws in `load-string` gives `CompilerException` with the cause, while
  `read-string` gives the cause directly (checked on Clojure 1.12.6). State
  that compiler, reader and reflection wrapping can occur, and that the
  Acceptance 3 tests check the exception type in the cause chain.
- [x] Ambiguity: state the shape coverage of Acceptance 3, 6 and 7.
  Acceptance 2 and 3 name both shapes, but Acceptance 6 (transferred
  expansions, receiving errors, AOT) and 7 (fallback policies, Java 8, EA3
  preview off) name no shape. Thus plain-only tests satisfy them, and leave
  the new receiving behaviour unproven: the receiver derives `:record` from
  the fields and passes it to the capture loader (now always `:plain`), and
  the identity backend accepts delegating constructors on Java 8. Also,
  Acceptance 3 says "native and generated enforcement are both covered in
  value output", but not for which shape; only the spike gate then covers
  native restriction in record value output. State, for example, that
  Acceptance 6 covers the record shape in both representations (macroexpand,
  macroexpand-1 and AOT), Acceptance 7 covers both shapes, and Acceptance 3
  covers native and generated fields in the record shape.
- [x] Inconsistency: the Contract ("Errors and order") calls
  `:removed-field-access` a "value transform" error. The Architecture section
  of design.md says the value backend owns the removed-field check, and the
  code agrees (`value-type.impl.value-backend`, after the transform). Ramora
  D4 says "the loader rejects". State one owner (the value backend) in the
  Contract, and use the reason form of the deftype* design table
  (`:invalid-field` `:reason :removed-field-access`).
- [x] Inconsistency: ramora D1 says `defvaluetype*` "installs the capture
  loader, and expands to `(let [] (deftype* …) (check-value-class <class>
  <installed>) <class>)`". With enabled fields, D5 (as changed by this task)
  and the design Architecture make the expansion a transferred receiving
  macro that installs capture during analysis, and fallback expansions are a
  guarded `deftype*` or a receiving identity macro. The design lists D4, D5,
  D6, D7 and D8 text and the D3 commitment as the ramora changes, but not
  D1. Add D1 to the ramora text that the task changes, so that D1 describes
  the expansion with enabled fields (or states the value expansion without
  enabled fields only, and refers to D5 for the others).
- [x] Ambiguity: state whether acceptance requires tests of the declaration
  checks that the `defvaluetype*` receiving macros repeat. The Architecture
  section fixes their order (`:record-shape` directly after
  `:mutable-field`, `:hidden-field` directly after the metadata keys), but
  Acceptance 5 covers only the errors "at macroexpansion, before
  representation policy", and Acceptance 6 says only "give its receiving
  errors" (the errors of `defvaluetype`). The `defvaluetype` receiving
  tests call the internal macros directly for receiving errors
  (`identity_receiving_test.clj`, `value_receiving_test.clj`), but no test
  covers their repeat of `:mutable-field`. State one: Acceptance 6 includes
  internal-form tests that the receiver gives `:record-shape` and
  `:hidden-field` in the stated order; or the repeats are defence only and
  have no separate test, as for `defvaluetype`.
- [x] Inconsistency: the Public contract of the deftype* design says
  "value-type does not validate constructor arguments" (from task 019, about
  the hash arguments). The next paragraph, D6, D8 and the design.md Contract
  make the main constructor check each enabled-field argument for `nil` and
  type. Change the sentence to state its real scope, for example
  "value-type does not validate the hash arguments", and mirror the change
  in design.md if it refers to it.
- [x] Plan ambiguity: state how slices 3 and 4 reach the backends before
  slice 5 exists, and where the public-form evidence of Acceptance 2, 3
  and 7 is made. With enabled fields, the `defvaluetype*` expansion is
  always a receiving macro (ramora D1, D5; `defvaluetype` routes all
  enabled-field forms through `value-field-deftype` or
  `identity-field-deftype`, also for direct expansion). The receiving
  macros come in slice 5, but slices 3 and 4 have runtime tests "through
  each contract construction path", and slice 4 runs the Java 8 and EA3
  preview-off evidence of Acceptance 7. Also, the slice 3 step "Enable the
  plain `defvaluetype*` value path with enabled fields" names no code
  change: the plain value backend already takes specifications, and the
  public path is the slice 5 expansion. State one: slices 3 and 4 test the
  backends through `loader/install!` fixture macros (precedent:
  `test/compatibility/value_type/identity_backend/fixture.clj`), and the
  public `defvaluetype*` tests of Acceptance 2, 3 and 7 move to slice 5 or
  later; or the receiving macros move before the backend slices. Remove or
  restate the "Enable" step to match.
- [x] Plan inconsistency: Acceptance 9 requires packaging and tooling
  checks, but plan slice 8 and steps slice 8 run only the runtime, fallback
  and cross-JVM suites and the four `bb` checks. The omitted suites check
  changes of this task: `-M:quickdoc:tooling-test`
  (`test/tooling/value_type/api_docs_test.clj` asserts the exact list of
  documented names, which proves the slice 5 `:no-doc` overrides, and
  asserts `defvaluetype*` docstring text that slice 7 changes);
  `-M:release:release-test` (validates the `CHANGELOG.md` entries that
  slice 7 edits); `-M:build-test` (packaging). Add these suites to slice 8,
  and record their commands and results per Acceptance 9.
- [x] Plan inconsistency: the `munera/plan.md` entry for task 020 says
  "Design only. Next: plan.md and steps.md", but both files exist. Change
  the entry to state that the plan exists, and that the next step is the
  plan review, then slice 1 (the feasibility gate).
- [x] Plan ambiguity: state where and with which checks the plan proves
  the Java 8 and EA3 preview-off part of Acceptance 7 ("identity output
  with generated checks works for both shapes"). The construction-path
  tests of slices 3 to 5 are in `test/runtime`, and the runtime suite runs
  only on Valhalla with preview (`deps.edn` `:test`,
  `doc/development/runtime.md`). The slice 6 step names "fallback suite,
  and cross-JVM cells" but no file, no shape fixture and no check. The
  Java 8 suites have no `IRecord` record helper (implementation.md,
  plan-review resolution). State one: extend the `deftype*-policy-test`
  plain and record cells in `test/compatibility/value_type/runtime_test.clj`
  (fallback suite: Java 8, EA3 preview off and on) with an enabled field,
  and check a valid store, `nil` → `NullPointerException` and wrong type →
  `ClassCastException` through the main constructor (record shape: also
  `create`); or name the cross-JVM test and cells. State whether
  cross-JVM cells are required in addition to the fallback suite.
- [x] Plan ambiguity: state the interface of the slice 3 fixture macro.
  `record-form` calls `(definer tagname classname fields :implements …)`,
  so the definer gets no representation, specifications or enforcement
  modes. But the plan uses one macro for `:value` (slice 3) and
  `:identity` (slice 4), and value record tests need one `:native` and one
  `:generated` field, while identity output takes only `:generated`
  (`identity-field-deftype` allows `#{:generated}`). State how the macro
  gets the representation (for example, two thin macros over one helper,
  or an option in the form) and where the specifications come from (from
  the `:non-nil` metadata through `fields/specifications` and
  `fields/select-enforcement`, with `:generated` forced for `:identity`;
  or literal specifications). Also state the slice 5 identity definer: the
  `defvaluetype*` identity receiving macro takes
  `tagname classname modes fields`, so `record-form` cannot call it
  directly.
- [x] Plan inconsistency: Acceptance 5 says that `:non-nil-unsupported`
  no longer exists, and the Contract gives the D6 error order. But the
  slice 7 docs steps name only "non-nil support, record behaviour" and the
  hash-argument sentence, and no step removes the old error text outside
  code and tests. The reason and the old order are still in the
  `defvaluetype*` docstring (`src/value_type/core.clj`: "Restrictions: …
  no `:non-nil`", the Errors list with `:non-nil-unsupported`, "Excluded:
  `:non-nil` fields"), `doc/usage.md` (~line 410–413, the error-order
  paragraph) and the generated `doc/API.md`. Add these to the slice 7
  docstring and `doc/usage.md` steps: replace the error list and the
  paragraph with the D6 order (`:mutable-field`, `:record-shape`,
  `:removed-nil-restricted`/`:invalid-non-nil`, `:hidden-field`, then the
  `defvaluetype` tag and type reasons), and remove the `:non-nil`
  restriction and exclusion. Add a slice 8 check that
  `git grep non-nil-unsupported -- ':!munera'` finds nothing outside the
  ramora decision history.
- [x] Plan ambiguity: state how the slice 5 transfer step gets the value
  representation and what it checks. The step extends
  `deftype_star_transfer_checks`/`deftype_star_transfer_test` "for both
  shapes in both representations", but that test produces expansions only
  with `config/identity-producer` (Java 8, `silent`), so each expansion is
  identity output, and the expected map fixes `:value? false`. `consume`
  gives only `:named? :value? :hash-fields :new-class?`, with no
  construction check. State one: add value-producer cells
  (`config/value-producer`, EA3 preview, `error`) to this fallback-suite
  test, or add a cross-JVM test after `test/cross-jvm/value_type/field_transfer_test.clj`.
  Also state the receivers and the expected outcome per receiver (value
  output with `:hash-fields 0` on a capable receiver; which `defvaluetype`
  receiving errors, for example `:value-capability-unavailable`, on the
  other receivers), whether the consume check proves enforcement (valid
  store, `nil` and wrong type), and that the slice 5 step "a received value
  record-shaped expansion has no hash fields" is one of these cells.
- [x] Plan ambiguity: state whether the slice 5 AOT step changes the
  existing `Plain` and `Rec` fixtures of `deftype_star_aot_checks` or adds
  new fixture classes with enabled fields. If it changes them, the AOT
  evidence for forms without enabled fields (Acceptance 8) goes away. Also
  state the facts that prove that AOT "fixes enforcement": `facts` gives
  only `:value? :stored :hash-fields`; add, for example, the root class of
  the cause chain for `nil` and wrong-type constructor calls, and the
  expected values for `checks/expected` in the identity, value and
  incompatible-receiver tests of
  `test/cross-jvm/value_type/deftype_star_aot_test.clj`.
- [x] Plan inconsistency: the plan ("Receiving boundary") and the slice 5
  step put all other receiving checks after `:hidden-field` ("then the
  existing receiving checks (modes, tag resolution)"). design.md
  Architecture puts only `:record-shape` and `:hidden-field` in new
  positions, and keeps the order of the `defvaluetype` receiving macros for
  the other checks. In `value-field-deftype` (`src/value_type/core.clj`),
  `require-value-capability!` (`:value-capability-unavailable`) runs after
  `check-fields` and before `fields/receiving-specifications` (metadata
  keys), and `runtime/require-field-capability!` runs after it. State the
  full order of `value-field-deftype*`: `:mutable-field`, `:record-shape`,
  value capability, metadata keys, `:hidden-field`, modes, tag and type,
  native field capability. State the `identity-field-deftype*` order
  (fixed fallback policy after the specifications, as in
  `identity-field-deftype`). Change the slice 5 step to match.
- [x] Plan ambiguity: state which `defvaluetype` receiving errors the
  slice 5 tests prove for the `defvaluetype*` receivers (Acceptance 6:
  "give its receiving errors"). The plan covers only
  `:value-capability-unavailable` (value-producer transfer cells on an
  incapable receiver) and the fixed fallback rejection under `error`. It
  names no test for `:invalid-field-enforcement` (for example
  `identity-field-deftype*` with `[:native]`, or a mode count that does
  not match the enabled fields), `:field-capability-unavailable` or
  `:not-installed` (`clojure.walk/macroexpand-all` of an enabled-field
  `defvaluetype*` form). Precedents: `receiving-identity-native-modes-test`
  (`test/compatibility/value_type/identity_receiving_test.clj`) and
  `not-installed-enabled-field-test` (`test/runtime/value_type/core_test.clj`).
  State one: a bounded set of internal-form cases (name the errors, the
  receiver and the file), or that the transfer cells are the full
  Acceptance 6 receiving-error evidence.
- [x] Plan ambiguity: state the file and suite of the slice 5
  internal-form receiving-order tests. The step says "per receiving macro"
  but names no file. `value-field-deftype*` checks value capability
  between `:record-shape` and the metadata keys, so on an incapable JVM
  (Java 8, EA3 preview off) its metadata/`:hidden-field` and later pairs
  give `:value-capability-unavailable`. The pairs hold only on a capable
  JVM. State, for example: `value-field-deftype*` cases in
  `test/runtime/value_type/value_receiving_test.clj` (runtime suite, EA3
  preview); `identity-field-deftype*` cases in
  `test/compatibility/value_type/identity_receiving_test.clj` (fallback
  suite, also Java 8).
- [x] Plan inconsistency: the slice 7 step "Update `CHANGELOG.md` and
  `mementum/state.md`" names no stale text. The docstring and
  `doc/usage.md` steps do name theirs. Both files state the old exclusion
  now: the `## [Unreleased]` `defvaluetype*` entry of `CHANGELOG.md`
  ("`:non-nil` fields are excluded.") and `mementum/state.md` (~line 23,
  "no `:non-nil` fields"). A new changelog bullet next to the old entry
  contradicts it. A note that `:non-nil-unsupported` is removed breaks the
  slice 8 check `git grep non-nil-unsupported -- ':!munera'`. Name both
  sentences in the step. State that slice 7 edits the existing unreleased
  `defvaluetype*` entry. `defvaluetype*` is not released, so the entry
  does not name the removed reason.
- [x] Plan inconsistency: the `munera/plan.md` entry for task 020 says
  "Slices 1–8 done … Next: implementation review". implementation.md and
  steps.md record that the implementation, test, docs and code-shaper
  reviews are done, and that the only open step is the code-shaper
  follow-up (`deftype*-policy-test` cells in
  `test/compatibility/value_type/runtime_test.clj`). Change the entry to
  state that the reviews are done, and that the next step is this
  code-shaper step, then close the task.
