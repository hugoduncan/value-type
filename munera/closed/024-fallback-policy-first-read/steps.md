# Steps: fallback policy at first read

## Slice 1: runtime policy owner

- [x] In `src/value_type/impl/runtime.clj`, replace the load-time
      `(def ^:private policy ...)` with a private delay that reads
      `value-type.fallback` (default `"silent"`), validates against
      `#{"silent" "warn" "error"}` with the current message, and returns
      the keyword `:silent`, `:warn` or `:error`.
- [x] Add public zero-arity accessor `fallback-policy` that derefs the
      delay, with a docstring that defines the policy in effect and the
      first-read rule.
- [x] Change `enforce-fallback!` to read `(fallback-policy)` and `case` on
      `:silent`/`:warn`/`:error`; messages and warn-once behaviour
      unchanged.
- [x] Update the docstrings of `enforce-fallback!` and
      `enforce-fixed-fallback!`: "initialized policy" → "policy in
      effect". The `guarded-deftype` and `guarded-deftype*` docstrings
      stay unchanged.
- [x] Update the `impl.runtime` ns docstring to describe first-read policy
      ownership.

## Slice 2: report

- [x] In `src/value_type/capability.clj`, require
      `value-type.impl.runtime`.
- [x] In `report`, read `(runtime/fallback-policy)` first, before any
      probe call, and add the value unchanged under `:fallback-policy`.
- [x] Rewrite the `report` docstring: five keys including
      `:fallback-policy` (`:silent`, `:warn` or `:error`), policy read
      before probes, invalid policy throws before probes, no partial
      report; remove "does not read the `value-type.fallback` policy".
- [x] Verify `value-type.capability` does not load `value-type.core` and
      contains no policy logic beyond the accessor read.

## Slice 3: tests

- [x] Rewrite `value-type.runtime-test/invalid-policy-report-test`
      (`:selected` child for each launch policy `""`, `"WARN"`,
      `"unknown"`). The form requires `value-type.capability`, calls
      `report` through `value-type.cell-registry/failed-call` with
      `value-type.cell-registry/thrown`, then reads the probe delays and
      the loaded namespaces. Expected map:
  - `:thrown`: `{:class "java.lang.IllegalArgumentException" :message
    <current message> :chain ["java.lang.IllegalArgumentException"]}`
    (plain, no wrapper).
  - `:probes`: `[false false false false]`, the `realized?` values of the
    private delays `value-type.impl.capability/detected-capability`,
    `value-type.impl.field-capability/detected-field-capability`,
    `value-type.impl.array/detected-allocation-capability` and
    `value-type.impl.array/detected-inspection-capability` (through
    `find-var`). This shows the throw before any probe.
  - `:loaded`: `'[value-type.impl.runtime]`, from
    `(filterv find-ns '[value-type.core value-type.impl.runtime])`:
    `value-type.core` is absent; `impl.runtime` is now loaded.
  Remove the `rows/report` expectation.
- [x] Update `value-type.capability-test/report-translation-test` to
      five-key report maps including `:fallback-policy`, using
      `with-redefs` of `impl.runtime/fallback-policy`.
- [x] Update `capability_matrix_checks` `owner-report` to compare `report`
      with the four owner accessors plus `fallback-policy`.
- [x] Update the exact report maps of
      `controlled_value_capability_test` (`:report`) and
      `build_test/isolated-consumer-test` (`consumer-reports`): add
      `:fallback-policy :silent` (both launch with policy `silent`).
- [x] Update the `api_docs_test` `report` strings: remove the "does not
      read" expectation; add expectations for the new `report` docstring
      text (Slice 2). The `defvaluetype` strings change in Slice 4.
- [x] Move the `value-type.runtime-test` tests that rely on load-time
      validation to first-read validation:
  - `invalid-policy-test` (launch policy `""`, `"WARN"`, `"unknown"`):
    the form requires `value-type.core`, `value-type.capability` and
    `value-type.impl.runtime`, then calls
    `(value-type.impl.runtime/fallback-policy)` through
    `cell-registry/failed-call` with `cell-registry/thrown`. Expected:
    `{:class "java.lang.IllegalArgumentException" :message <current
    message> :chain ["java.lang.IllegalArgumentException"]}`. A value
    result (not `:thrown`) shows that the requires succeed.
  - `policy-test`: call `(value-type.impl.runtime/fallback-policy)` once
    after the require and before `System/setProperty` `"invalid-later"`.
    Expectations stay unchanged. Without this read, `"invalid-later"` is
    the first read and `enforce-fallback!` throws
    `IllegalArgumentException`.
- [x] Add `value-type.fallback-policy-test`
      (`test/cross-jvm/value_type/fallback_policy_test.clj`) and its child
      checks `value-type.fallback-policy-checks`
      (`test/compatibility/value_type/fallback_policy_checks.clj`; baseline
      safe; loads no value-type namespace at its own load), in the pattern
      of `capability_matrix_test` and `capability_matrix_checks`. Each
      child is a `harness/cell-child` on a runtime of
      `config/configuration`.
- [x] Acceptance 1: for each runtime of `config/incompatible-receivers`
      (Java 8, preview off) and each policy `error`, `warn`, one child
      with launch `:policy nil`. The check requires `value-type.core`,
      sets the policy with `System/setProperty`, then defines ordinary
      types.
  - `error`: the definition fails with the fallback Error
    (`harness/failed-definition` of `harness/rejection-context`
    `{:baseline? (= baseline runtime)}`).
  - `warn`: the check defines two types inside
    `(binding [*err* (java.io.StringWriter.)] ...)` and returns the
    captured text. Expected: exactly one fallback warning line
    (`harness/warning-line` of the context on Java 8; on preview off the
    context holds a random class name, so match one line with
    `harness/warning-prefix`, as `cell_harness` `any-warning-line` does).
    The binding is necessary: the harness expects empty cell `*err*` in
    a child without launch policy `warn`.
- [x] Acceptance 2: for each runtime of `config/incompatible-receivers`,
      one child with launch `:policy nil`. The check requires
      `value-type.core` and `value-type.capability`; `(:fallback-policy
      (report))` is `:silent`; then it sets `error` with
      `System/setProperty`; a definition defines an identity class. The
      harness checks empty cell `*err*` and empty child stderr, so the
      fallback writes nothing to standard error.
- [x] Acceptance 4: launch `:policy "unknown"`.
  - Report children, one for each runtime of `config/receivers` (Java 8,
    preview off, preview on). The check runs in this order: require
    `value-type.core` and `value-type.capability` (loading succeeds);
    first `report`; `System/setProperty` `silent`; second `report`; one
    definition of an ordinary type. The first `report` throws plain
    `IllegalArgumentException` (`cell-registry/thrown`: current message,
    chain `["java.lang.IllegalArgumentException"]`). On preview on, the
    definition defines a value class
    (`consumer-aot-checks/value-class?`) and does not throw. The second
    `report` and the definition on the other runtimes: see the later-read
    step.
  - Definition children, one for each runtime of
    `config/incompatible-receivers`. The check requires `value-type.core`,
    then a definition makes the first read. Expected
    (`cell-registry/thrown`): current message, chain
    `["clojure.lang.Compiler$CompilerException"
    "java.lang.IllegalArgumentException"]`.
- [x] Later reads (in the acceptance 4 report children; compare inside the
      check, because the delay rethrows the cached instance):
  - The second `report` throws a throwable `identical?` to the first, on
    each runtime of `config/receivers`.
  - On each runtime of `config/incompatible-receivers`, the definition
    throws `Compiler$CompilerException` whose cause is `identical?` to the
    first throwable.
  The `silent` property fix thus has no effect.
- [x] Update `value-type.capability-matrix-test` to check
      `:fallback-policy` in each cell against the cell policy
      (acceptance 3): the expected `:report`, `:final` and `:owner` maps
      are `(assoc (rows/report label) :fallback-policy (keyword policy))`.
      `rows/report` stays unchanged.

## Slice 4: docs and design sources

- [x] Update the `defvaluetype` docstring: replace "before loading" and
      "read and validated once" with the first-read rule from Acceptance 6.
- [x] Update the README fallback text ("before the library loads") with
      the first-read rule; keep the JVM option as the recommended form.
- [x] Update the usage guide "Fallback policy" section (`doc/usage.md`):
      replace "before dependencies load, not in `-main`" (l.15), "read and
      validated once" and "fail at load time" (l.27-28) with the first-read
      rule. Keep the JVM option example (l.17-19).
- [x] Update the usage guide "Capability report" section (`doc/usage.md`
      l.308):
  - Intro (l.310-312): `report` also returns the fallback policy in
    effect.
  - Example result (l.316-320): add `:fallback-policy :silent`.
  - Key table (l.326-331): add a `:fallback-policy` row (the policy in
    effect: `:silent`, `:warn` or `:error`).
  - Remove "`report` does not read the `value-type.fallback` policy"
    (l.356). State instead: `report` reads the policy before it runs a
    probe; an invalid value makes `report` throw before any probe, with no
    partial report. The rest of l.356-359 (no `value-type.core` load, no
    JEP 401 needs, per-runtime cache) stays.
- [x] Update the `api_docs_test` `defvaluetype` strings to the new
      docstring text. Keep the current strings that the new text still
      contains.
- [x] Regenerate `doc/API.md` (`bb api-docs`).
- [x] Update `CHANGELOG.md` with the first-read policy and the
      `:fallback-policy` report key.
- [x] Update `ramora/defvaluetype-design/embodiment/runtime.md`: component
      rows for `value-type.capability` and `impl.runtime`, dependency
      direction (`value-type.capability → impl.runtime`), and the
      "initialized policy" text in the fallback boundary and state
      sections.
- [x] Update assertion A-34 in
      `ramora/defvaluetype-design/assertions/fallback-architecture.md` to
      first-read wording.
- [x] Update `doc/development/runtime.md`:
  - Add a "Verify the fallback policy" section after "Verify the capability
    report", in the same pattern: the focused command
    `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test
    --namespace value-type.fallback-policy-test` and a coverage text. The
    text names the 11 children: a policy set after load applies (Java 8 and
    preview off, `error` and `warn`: 4); a property change after a `report`
    call has no effect (Java 8 and preview off: 2); an invalid launch policy
    throws at the first read, `report` (Java 8, preview off, preview on: 3)
    or a definition (Java 8 and preview off: 2), and each later read throws
    the same exception. Link the usage guide "Fallback policy" section for
    the public contract.
  - "Verify the capability report" (l.423-452): "Each report must equal the
    expected row" becomes: each report must equal the expected row plus
    `:fallback-policy` of the cell policy.
  - "Child JVMs" isolation rule 5 (l.110-112): add "the first policy read"
    to the first-event evidence list.
  - Rule 2 "Fallback policy" stays unchanged: it is the launch property.
    Cells that set the property inside the child make the first read, so
    rule 5 already gives them their own child. The `runtime_test.clj` l.12
    comment ("isolation rule 2") stays true.
- [x] Remove D10 and D14 from the embodiment gap table.
- [x] Update `mementum/state.md`.

## Slice 5: verification

- [x] Run the runtime, fallback, cross-JVM, build and tooling suites;
      all pass (acceptance 5).
- [x] Run `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and
      `bb api-docs --check`; all pass.

## Implementation review follow-ups

- [x] `test/cross-jvm/value_type/fallback_policy_test.clj`: replace the
      `baseline?` and `label` helpers (label from `(seq (:jvm-opts runtime))`)
      with explicit runtime/label pairs destructured from
      `config/configuration` (`baseline`, `preview-off`, `valhalla`), as
      `field_capability_test`, `loader_policy_test` and `array_capability` do.
- [x] `fallback_policy_test` `child`: remove `:fallback? true` and
      `:warning :any`. No child launches with policy `warn`, so
      `cell_harness/expected-err` does not use them; they mislead.
- [x] Reflow the paragraphs that this task edited and left with broken
      wraps: `doc/usage.md` capability report intro (l.322-323) and the
      "reads the `value-type.fallback` policy" paragraph ("returned. Loading");
      `doc/development/runtime.md` isolation rule 5 ("namespace. Such a cell");
      `mementum/state.md` `report` entry; `embodiment/runtime.md` dependency
      direction; the `report` docstring line "Each capability value is ..."
      (83 columns). Then run `bb api-docs` and `bb vale`.
- [x] Reflow two more paragraphs that this task edited and left with
      broken wraps: `doc/usage.md` capability report "Each capability value
      is ..." paragraph (l.347, 85 columns), and the `embodiment/runtime.md`
      state list item "Cached capability results owned by the probe
      namespaces ..." (short line "owned by `impl.runtime`" before
      "(D13)."). Then run `bb vale`.
- [x] Update the D14 commitment evidence list in
      `ramora/defvaluetype-design/decisions/capability-report.md` (l.39-44).
      It names the tests that verify the report, but not the new tests of
      `:fallback-policy` and the policy read before the probes: add
      `report-policy-before-probes-test` (to the `value-type.capability-test`
      list) and `value-type.fallback-policy-test`. Then run `bb vale`.
- [x] `README.md` l.163: "The startup property `value-type.fallback`" is
      stale under first-read semantics (a `System/setProperty` call after
      startup applies if no policy read occurred). Remove "startup", as
      this task did in `mementum/state.md`. Then run `bb vale`.

## Test review follow-ups

- [x] `test/runtime/value_type/capability_test.clj`: remove
      `report-policy-before-probes-test`. It injects behaviour (a spy atom
      that records probe calls), not state. The child test
      `value-type.runtime-test/invalid-policy-report-test` already shows the
      throw before any probe through the probe delay state (`:probes`).
      Remove the test name from the D14 commitment evidence in
      `ramora/defvaluetype-design/decisions/capability-report.md`. Then run
      the runtime suite and `bb vale`.
- [x] `value-type.runtime-test/invalid-policy-report-test`: the `:loaded`
      expectation `'[value-type.impl.runtime]` asserts an implementation
      decision (the `value-type.capability → impl.runtime` dependency). The
      Contract only says that `report` does not load `value-type.core`.
      Check only `value-type.core` (for example
      `:core-loaded? (some? (find-ns 'value-type.core))`, expected `false`).
- [x] `value-type.runtime-test/invalid-policy-test`: it has two concerns,
      and one duplicates `invalid-policy-report-test`. Its accessor call
      (`value-type.impl.runtime/fallback-policy`) asserts the same message
      and chain for the same three launch values that
      `invalid-policy-report-test` asserts through the public `report`. Keep
      only the load contract: with an invalid launch policy, require
      `value-type.core`, `value-type.capability` and
      `value-type.impl.runtime`, and expect a value result (for example
      `:loaded`). Remove the accessor call and the `:thrown` expectation;
      update the comment. Then run `value-type.runtime-test`.

## Code-shaper review follow-ups

- [x] `test/cross-jvm/value_type/fallback_policy_test.clj`: the tests
      derive expectations from string label comparisons
      (`(= "java8" label)`, `(= "preview-on" label)`) and then branch on
      the derived value (`(= :any context)`). Put the expected data in the
      runtime rows, as `loader_policy_test` does: for example
      `[runtime label warning-context]` for the incompatible receivers and
      `[runtime label definition]` (`:value` or the CompilerException map)
      for the receivers. Remove the label comparisons. Then run
      `value-type.fallback-policy-test`.
- [x] `test/compatibility/value_type/fallback_policy_checks.clj`:
  - `set-after-load` branches on policy and returns two shapes (captured
    `*err*` text for `warn`, a definition outcome for `error`). Split it
    into two checks with one result shape each (for example
    `set-after-load-error` and `set-after-load-warn`) and update the
    callers in `fallback_policy_test`.
  - `invalid-report` repeats the `:value`/`:identity` mapping of
    `definition` in a nested `if`. Extract one helper (for example
    `representation` of a Class) and use it in both.
  Then run `value-type.fallback-policy-test`, fmt and kondo.

## Code-shaper review pass 2 follow-ups

- [x] `fallback_policy_test` `incompatible-receivers`: the warning column
      repeats the `cell_harness` `expected-err` branch (`:any` →
      `any-warning-line`, else `(fixed (warning-line c))`). It is a function
      of the context column.
  - In `cell_harness`, extract that branch into one public function (for
    example `expected-warning` of a context) and use it in `expected-err`.
    Make `any-warning-line` private again.
  - In `fallback_policy_test`, keep the context column, remove the warning
    column, and use `(harness/expected-warning context)` in
    `set-after-load-test`.
  Then run `value-type.fallback-policy-test`, fmt and kondo.
- [x] The invalid-policy message is built in two test files:
      `runtime_test` `invalid-policy-report-test` and
      `fallback_policy_test` `invalid-message`. Each copies the format of
      `impl.runtime`. Add one helper (for example
      `cell-registry/invalid-policy-message` of a policy string) and use it
      in both. Then run `value-type.runtime-test`,
      `value-type.fallback-policy-test`, fmt and kondo.

## Code-shaper review pass 3 follow-ups

- [x] `fallback_policy_test` `child`: remove the `args` parameter. Every
      caller passes `[]`, and no check takes arguments. Use
      `(registry/check-form check)` and update the callers. Then run
      `value-type.fallback-policy-test`, fmt and kondo.
- [x] `test/compatibility/value_type/cell_registry.clj` ns docstring: this
      task added "the invalid-policy message" to a line and left it at 96
      columns. Reflow that paragraph to the width of the other lines.
      Then run fmt.

## Code-shaper review pass 4 follow-ups

- [x] `fallback_policy_checks/invalid-report`: the `:definition` value
      does not use the outcome idiom of `definition` in the same file. It
      catches with `caught`, discards the Class that `define` returns,
      gets it again with `(resolve 'InvalidPoint)` (which depends on
      `*ns*`), and leaves a 93-column line. Use
      `(registry/outcome failure-of #(define 'InvalidPoint) representation)`,
      where `failure-of` returns `{:class ... :cause-identical? ...}`.
      Then run `value-type.fallback-policy-test`, fmt and kondo.
