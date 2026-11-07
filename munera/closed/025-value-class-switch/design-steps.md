# Design steps

- [x] Align the switch-check order with the selected detector.
  `ramora/defvaluetype-design/fallback/selected-detector.md` reads the switch
  after the ClassFile and `Class.isValue` API checks. The design ("Architecture")
  runs the switch check before the API checks. The order changes observable
  results on JVMs without the APIs (Java 8, JDK 24-26): the `off` reason
  (`:disabled` or `:missing-classfile-api`/`:missing-value-predicate`) and
  whether an invalid value throws there. Select one order. State its result on
  a JVM without the APIs in "Contract" and cover it in "Acceptance". If the
  design keeps "before the API checks", add `selected-detector.md` to the
  documentation updates.
- [x] Define the invalid-value contract exactly. "Contract" gives no exception
  class, no match rule and no repeat behaviour. State: the exception class
  (precedent: `impl.runtime` throws `IllegalArgumentException` for
  `value-type.fallback`); exact, case-sensitive match of `auto` and `off`
  (`OFF`, ` off` and the empty string are invalid); the same exception at each
  later read; the entry points that surface it (definitions, `make-array`,
  `into-array`, `report`; `array-info` does not read the value-class
  capability).
- [x] Specify the `:disabled` messages. Fallback passes the capability
  `:detail` to `enforce-fallback!`, so it is the `warn` warning text and the
  `error` Error text. `impl.array/require-allocation` now says allocation
  "requires a supported Valhalla JVM, --enable-preview and --add-exports",
  which is false under `off`. State the `:detail` text and the
  `:array-unavailable` message for `:disabled`; each names
  `value-type.value-classes=off`.
- [x] State the effect of `off` on value-class AOT output in this slice.
  The AOT check is task 026, so compiled value classes still load as value
  classes under `off`. Say so in "Contract" and say whether the usage guide
  documents this limit until 026. Also make "Docs: ... the reason" exact
  (the `:disabled` keyword, the rationale, or both).
- [x] Fix "the three derived keys" in "Acceptance" 1. "Scope" derives only
  two keys (`:native-non-nil-field`, `:array-allocation`) from
  `:value-class`; "Contract" names the three keys. Write "`:value-class`,
  `:native-non-nil-field` and `:array-allocation`".

## Plan ambiguity review

- [x] Name the `off` report-row labels and where they run. Slice 3 adds
  `:disabled` rows to `capability_rows.clj`, but `reasons` is keyed by
  runtime label, and `array-capability/runtimes` enumerates labels that the
  capability, array and array-info matrices iterate. State the new labels
  (for example `:export-off`, `:java8-off`) and their JVM options. State if
  they join `array-capability/runtimes`, which multiplies the existing
  matrices (policies × require orders), or run only in the new `off` child
  tests.
- [x] State how the `auto` regression runs. Slice 3 and Acceptance 4 say
  "Run the existing matrices with `auto` and with no property". The harness
  has no option that adds the property to every child. Select one: a
  permanent `auto` axis (which matrices), a one-time run with a temporary
  option (record the result in implementation.md), or a small `auto` child
  test that compares the report and one definition with the no-property row.
- [x] State the property of each AOT phase. The two Slice 3 AOT child tests
  say "run under `off`" only. If compilation also runs under `off`,
  `select-enforcement` selects generated enforcement and the output is
  identity, so neither test can pass. State: compile on EA3 with preview and
  export and no property (value output, native fields); load and run with
  `off`. Name the fixtures to reuse (`restricted.aot-fixture` in
  `field_aot_test.clj`, precedent `incompatible-restricted-field-consumer-test`;
  a fixture without native fields for the second test).
- [x] Decide test coverage of the AOT field-initialization entry point for
  an invalid value. The design "Contract" lists it, and the plan "Risks"
  says "the checklist names each entry point", but the Slice 3 invalid-value
  step and Acceptance 3 omit it. Add a step (EA3 with preview, compile with
  no property, run with `OFF`: the IAE is in the cause chain) or state in the
  plan that the contract is verified by reading only.
- [x] Make the `report` docstring update exact. Slice 4 adds `:disabled` to
  the reason list only. The docstring documents the invalid-policy
  `IllegalArgumentException` and that a probe defect propagates. State if
  it also names `value-type.value-classes` and the invalid-value
  `IllegalArgumentException` (thrown after the policy read).

## Plan ambiguity review, round 2

- [x] Keep the one-time `auto` run from changing the switch children. The
  edit appends `-Dvalue-type.value-classes=auto` after the policy option to
  each child. The switch and AOT children put `=off`, `=OFF`, ` off` or the
  empty value in `:jvm-opts`, before the policy option. The JVM uses the
  last `-D` of a property, so these children run with `auto` and fail. The
  step is the last item of Slice 3, after the switch test exists, and runs
  all of `:cross-jvm-test`. Select one: run the one-time edit before
  `value_class_switch_test.clj` exists, exclude
  `value-type.value-class-switch-test` from that run, or append the option
  only to children without a `value-type.value-classes` entry (and the same
  rule in `array-capability/expected-flags`).
- [x] Make the invalid-value check and its expected result exact.
  - Property fix: the design says "a later property fix has no effect". The
    step says only "the identical exception at a later read". State if the
    check sets `value-type.value-classes` to `auto` before the later read
    (precedent: `fallback-policy-checks/invalid-report` sets `silent`
    between the reads).
  - Identity: state if the definition cause, `make-array` and `into-array`
    must give the same object as the first `report` failure
    (`:cause-identical?` precedent) or only the same class and message.
  - Arguments: state the arguments that show "before argument checks and
    input realization" (precedent: `array-sequence-checks/unavailable-call`:
    nil component, lazy input with an effect counter; expected effects 0).
  - `array-info`: a nil argument throws `:invalid-array` before any
    capability read, so the check then shows nothing. State the argument
    (for example `(object-array 1)`) and the expected outcome on each
    runtime (`:array-inspection-unavailable` with `:array-access-denied` on
    `:no-export` and `:missing-array-api` on baseline).

## Plan inconsistency review, round 2

- [x] Use `defvaluetype` output in the AOT test without native fields. The
  design "Contract" says other value-class AOT output (output of
  `defvaluetype`) loads and runs as value classes under `off`. Plan
  decision 5 and the Slice 3 step use `checked.aot-fixture` of
  `generated_value_aot_test.clj`. That fixture calls `loader/install!` and
  `deftype` directly. It has no `defvaluetype` and no `check-value-class`
  init call, so the test does not show the contract case. Use
  `identity-source` of `public_field_aot_test.clj` (a `defvaluetype` with a
  generated `^String ^:non-nil` field). Compile it on `valhalla` so the
  output has value classes and calls `check-value-class` at init. The spec
  (`consumer-aot-checks/string-holder`) and the expected result
  (`string-holder-result true`) do not change. Change plan.md and
  steps.md.
- [x] Align the usage-guide contents. The Slice 4 usage step includes "the
  invalid-value failure". Plan decision 6 and the design "Scope" docs list
  do not include it. Add it to both, or remove it from steps.md.
