# Steps

## Slice 1: switch in impl.capability

- [x] Add the fixed `:disabled` detail string
      `Value classes are disabled by value-type.value-classes=off.` to
      `src/value_type/impl/capability.clj`.
- [x] Add a switch-read fn in `impl.capability` that reads
      `value-type.value-classes`: absent/`auto` → nil (continue), `off` →
      `{:available? false :reason :disabled :detail <fixed string>}`, other
      → throw `IllegalArgumentException` with message
      `Invalid value-type.value-classes: <pr-str of value>; expected auto or
      off` (case-sensitive comparison, no trim).
- [x] Call the switch check first in `detect`, before `required-apis` and
      the probe; under `off` no probe class is defined.
- [x] Update the `detect` docstring: the switch check runs first; `:disabled`
      is a permitted absence reason.
- [x] REPL-verify: with the property set via `with-redefs`-free means
      (`System/setProperty` in a fresh capability read through `detect`
      directly): `off` → `:disabled`; `auto`/absent → unchanged; `"OFF"` →
      IAE with the stated message; delay caches the exception (same object
      on second deref of a test delay).

## Slice 2: message propagation

- [x] Extend `require-allocation` in `src/value_type/impl/array.clj` to
      append `:detail` for `:disabled` as for `:failed-self-check`:
      `Value-array allocation is unavailable. <detail>`.
- [x] Verify (read, no change expected): `field_capability/detect` and
      `impl.array/discover` return the unavailable value-class result as-is,
      so `:reason :disabled` and `:detail` propagate.
- [x] Verify (read, no change expected): fallback passes `:detail` to
      `enforce-fallback!`, so the `warn` warning and the `error` Error text
      contain the `:disabled` detail after the prefix
      `value-type: identity deftype fallback: `.
- [x] Verify (read, no change expected):
      `require-field-capability!` message under `off` carries
      `:reason :disabled` in ex-data for AOT field-capability output.
- [x] Verify (read, no change expected): `array-info` does not read the
      value-class capability.

## Slice 3: tests

Layout, labels, fixtures and expected values: plan.md decision 5.

- [x] Add rows to `reasons` in
      `test/compatibility/value_type/capability_rows.clj`: `:export-off`
      and `:java8-off` (`:disabled` for `:value-class`,
      `:native-non-nil-field` and `:array-allocation`; `:array-inspection`
      nil for `:export-off`, `:missing-array-api` for `:java8-off`),
      `:export-auto` (as `:export`).
- [x] Move `expected-call` and `expected` from
      `test/cross-jvm/value_type/capability_matrix_test.clj` to
      `test/cross-jvm/value_type/capability_expectations.clj`;
      `expected` takes `[label policy context]`. `capability_matrix_test`
      passes `harness/rejection-context` as now; run it and confirm no
      change.
- [x] Create `test/compatibility/value_type/value_class_switch_checks.clj`
      and `test/cross-jvm/value_type/value_class_switch_test.clj`. Runtimes
      add `-Dvalue-type.value-classes=<value>` to `:jvm-opts`:
      `:export-off` and `:export-auto` from the `:export` runtime of
      `array-capability/runtimes`, `:java8-off` from baseline.
- [x] Add report children on `:export-off`, each policy, require order
      `[value-type.core value-type.array value-type.capability]`
      (`capability-matrix-checks/check`): report, final and owner rows
      `:disabled` for the three keys and `:array-inspection` available;
      `silent` and `warn` give an identity class; the `warn` warning line
      is the prefix and the `:disabled` detail; `error` gives the fallback
      Error with the `:disabled` detail; native field false (skipped under
      `error`); allocation `{:value-type/error :array-unavailable :reason
      :disabled}`.
- [x] Add the same report children on `:java8-off`: rows `:disabled` for
      the three keys (not `:missing-classfile-api` /
      `:missing-value-predicate`); `:array-inspection`
      `:missing-array-api`.
- [x] Add report children on `:export-auto`, each policy: the results of
      `:export` (value class, native field, allocation and inspection
      available).
- [x] Add message children on `:export-off` and `:java8-off`, policy
      `silent`: `make-array` and `into-array` each throw message
      `Value-array allocation is unavailable. Value classes are disabled by
      value-type.value-classes=off.` and ex-data
      `{:value-type/error :array-unavailable :reason :disabled :detail
      <the :disabled detail>}`.
- [x] Add the check `value-class-switch-checks/invalid-value` and
      invalid-value children (`OFF`, ` off`, empty string) on `:no-export`
      (EA3, preview) and on baseline. Call order, arguments and expected
      map: plan.md decision 5. Namespace loading succeeds; `report` throws
      the stated IAE at first read and the identical exception at a later
      read after the property is set to `auto`; a definition throws
      `Compiler$CompilerException` with the identical IAE as direct cause;
      `make-array` (`nil -1 nil`) and both `into-array` arities (nil
      component, lazy input) throw the identical IAE, with 0 input effects;
      `array-info` of `(object-array 1)` does not throw it and gives
      `:array-inspection-unavailable` with `:array-access-denied`
      (`:no-export`) or `:missing-array-api` (baseline).
- [x] Add a policy-first child: baseline, policy `unknown`, value `OFF`;
      `report` throws the invalid-policy exception
      (`registry/invalid-policy-message "unknown"`). Use a new check, not
      `fallback-policy-checks/invalid-report`: its definition reads the
      capability first and throws the switch exception.
- [x] Add an AOT check in `value-class-switch-checks`: require the saved
      namespace once; return source absence and the load outcome (root
      class, root message, cause chain, root ex-data keys
      `:value-type/error` and `:reason`), or `:accepted`.
- [x] Add an AOT test: compile `restricted.aot-fixture` with
      `config/value-producer` (EA3, preview, no switch property); run
      source-free on `valhalla` with `off` under each policy. Expected:
      root `clojure.lang.ExceptionInfo`, data
      `{:value-type/error :field-capability-unavailable :reason :disabled}`,
      message contains the `:disabled` detail.
- [x] Add an AOT test: compile `checked.aot-fixture` from
      `public-field-aot-test/identity-source` (a `defvaluetype`; require
      the var) with `config/value-producer` (no switch property); run
      source-free on `valhalla` with `off` under
      policy `error`; `consumer-aot-checks/holder-consumer-form` of
      `consumer-aot-checks/string-holder` gives
      `(consumer-aot-checks/string-holder-result true)` (value classes;
      interim limit until task 026).
- [x] Add an AOT test for an invalid value: compile
      `restricted.aot-fixture` as above; run source-free on `valhalla`
      with `OFF` under policy `error`. Expected root
      `java.lang.IllegalArgumentException` with the stated message.
- [x] One-time `auto` run of the existing matrices (Acceptance 4): in an
      uncommitted edit, append `-Dvalue-type.value-classes=auto` after the
      policy option in `jvm_runner/start-command` and in
      `array-capability/expected-flags`. Run `:cross-jvm-test` without
      `value-type.value-class-switch-test`
      (`--ns-pattern '(?!value-type\.value-class-switch-test$).*-test'`;
      its children set their own switch value, which the appended option
      would override). Run `:test` and each `:fallback-test` parent of
      `doc/development/runtime.md` with
      `-J-Dvalue-type.value-classes=auto`. Revert the edit. Record the
      commands and results in implementation.md.

## Slice 4: docs and design artifacts

- [x] Update the `report` docstring in `src/value_type/capability.clj`
      as plan.md decision 6 states: `:disabled` at the end of the
      `:value-class` reason list; the `value-type.value-classes` paragraph
      after the policy paragraph (values, `off` result, invalid-value
      IllegalArgumentException after the policy read, first read fixes the
      result).
- [x] Regenerate `doc/API.md` (`bb api-docs`).
- [x] Update `doc/usage.md`: the property, values `auto`/`off`, purpose
      (configuration-only return to identity semantics after a JVM defect),
      the `:disabled` reason near `:failed-self-check`, advice to use `off`
      with `silent` or `warn` (under `error` each definition throws), and
      the AOT limit of this slice until task 026 (plan.md decision 6; the
      `report` docstring documents the invalid-value failure).
- [x] Update `CHANGELOG.md`.
- [x] Update `ramora/defvaluetype-design/embodiment.md` gap table: D18
      embodied except the AOT initialization check (task 026).
- [x] Update `ramora/defvaluetype-design/fallback/selected-detector.md`:
      the switch check runs first, before the API checks and the probe.
- [x] Update `mementum/state.md`.

## Slice 5: verification

- [x] Run all runtime, fallback, cross-JVM, build and tooling suites; all
      pass.
- [x] `bb clj-fmt:check` passes.
- [x] `bb clj-kondo:lint` passes.
- [x] `bb vale` passes.
- [x] `bb api-docs --check` passes.

## Implementation review

- [x] `doc/usage.md`: move `### Value-class switch` after the last
      fallback-policy paragraph (before `## AOT limits`). Now it splits
      the fallback-policy text: the paragraphs from "All policies use value
      classes when supported" to "Both representations reject mutable
      fields" are under the switch heading, and "All policies ..." has no
      blank line after the "Limit:" paragraph, so Markdown joins them.
- [x] `src/value_type/impl/capability.clj` `detect` docstring: wrap the
      110-character line ("IllegalArgumentException. Only the switch, ...")
      to the width of the other docstring lines.
- [x] `impl.capability/disabled-detail` is public, but no namespace other
      than `impl.capability` uses it (the switch test keeps its own literal
      to pin the contract text). Make it private, or reference it from
      the tests; do not keep unused public surface.
- [x] `value_class_switch_test/switch-allocation-message-test`: select the
      off runtimes by label (`:export-off`, `:java8-off`), not by
      `(take 2 (runtimes))`, which depends on the row order of `runtimes`.

## Implementation review, round 2

- [x] `value_class_switch_checks/caught` is a verbatim copy of the private
      `fallback_policy_checks/caught`. Move it to `value-type.cell-registry`
      as a public fn and use it from both check namespaces.
- [x] `value_class_switch_checks/rejection` re-implements
      `registry/rejected-call` (with `registry/rejection`), which already
      returns `:message` and `:data` of the ExceptionInfo. Use
      `registry/rejected-call` in `allocation-messages` and change
      `allocation-rejection` in `value_class_switch_test` to its outcome
      map (`:status :rejected`, `:exception`, `:cause nil`).
- [x] `doc/usage.md` "Capability report": the list starts with "The probe
      reasons below are classified absences", but the new `:disabled`
      bullet says "No probe runs". Move the `:disabled` bullet out of the
      probe-reason list (for example, one sentence after the list that
      links to the switch section), or change the lead sentence so that it
      includes the switch reason.

## Test review

- [x] `value_class_switch_test` AOT tests: do not pin the full cause chain
      (`aot-chain`, `[ExceptionInInitializerError ExceptionInfo]` and the
      IAE variant). The design states only that the failure is "in the
      cause chain", and `:class`, `:message` and `:data` already check the
      root. The fixed chain fails if the initialization wrapping changes
      (for example the AOT check of task 026). Remove the `:chain` key
      from the expected `:load` maps of `aot-native-field-off-test` and
      `aot-native-field-invalid-test` (and from `aot-load`, if no other
      test uses it).

## Test review, round 2

- [x] No test covers "`off` defines no probe class" (design "Scope" and
      "Architecture"; `report` docstring "runs no value-class probe"). On
      EA3 with preview, `:disabled` rows also result if the probe runs and
      `:disabled` replaces its result; the `:java8-off` rows show only that
      the switch runs before the API checks. Add an `:export-off` child
      check that observes the probe from outside production code (for
      example `-Xlog:class+load` or `-verbose:class` output of the child
      JVM after `report`: no `CapabilityProbe` class loads), and the same
      check on `:export-auto` to show that the observation detects a probe.
      Add no production hook.

## Test review, round 3

- [x] `value_class_switch_test/switch-probe-test`: the `:export-auto`
      control expects `:probe-loads 1`. The design requires only that a
      probe runs under `auto` (the control shows that the log detects a
      probe). An exact count fails if the probe loads more than one class
      whose name contains `CapabilityProbe`, an implementation decision.
      Make `probe-loads` return `:probe-loaded?` (`pos?` of the count) and
      expect `false` for `:export-off` and `true` for `:export-auto`.

## Test review, round 5

- [x] No test covers the fixed result of a valid value (design "Scope":
      read once per loaded library runtime; `report` docstring: "The first
      read fixes the result; later property changes have no effect").
      `invalid-value-test` covers only the invalid value. Add a child check
      on `:export-off` that calls `report`, sets the switch to `auto` with
      `System/setProperty`, and calls `report` again; expect `:disabled`
      for `:value-class` at both reads. A probe that reads the property at
      each call passes all current tests.

## Docs review

- [x] `README.md` "Important behavior and limitations": the README
      documents `value-type.fallback` but not the new user-facing property
      `value-type.value-classes`. Add one sentence that names the property
      and `off` (return to identity semantics with no code change), and
      add "value-class switch" to the list in the usage-guide link sentence
      ("field comparison rules, fallback policy, AOT limits and error
      handling").
- [x] `doc/usage.md` "Value-class switch": the section says only "The
      library reads the property once, with the value-class capability".
      State when the first read occurs (the first definition, `report`,
      `make-array` or `into-array` call; a namespace with a top-level
      definition reads it when it loads), that a `System/setProperty` call
      applies only before that read, and that the JVM option is the
      recommended form, as the fallback-policy text does for
      `value-type.fallback`.

## Docs review, round 2

- [x] `defvaluetype` docstring (`src/value_type/core.clj`): "Uses value
      classes on a supported JEP 401 JDK with `--enable-preview`. Otherwise
      uses identity storage" is false under `value-type.value-classes=off`
      (a supported JDK with preview then uses identity storage). Add the
      switch condition, for example "unless `value-type.value-classes` is
      `off`", next to the `value-type.fallback` text. The `make-array`
      docstring (`src/value_type/array.clj`) lists the requirements
      ("Requires a supported Valhalla JVM, `--enable-preview` and
      `--add-exports=...`"); add that the switch is not `off`. Regenerate
      `doc/API.md` (`bb api-docs`); `bb vale` passes.

## Docs review, round 3

- [x] `ramora/defvaluetype-design/embodiment/runtime.md` "Components": the
      `value-type.impl.capability` row lists the API checks, probe builder,
      classification and cache, but not the `value-type.value-classes`
      switch that `detect` now reads first, and its decisions column omits
      D18. Add the switch read (first, before the API checks) to the
      responsibility and D18 to the decisions.
- [x] `ramora/defvaluetype-design/embodiment/artifacts.md` (consumer
      requirements paragraph): "Value-class creation needs a supported JEP
      401 Valhalla JDK with `--enable-preview`" omits the switch condition
      that the `defvaluetype` and `make-array` docstrings now state. Add
      "and `value-type.value-classes` not `off`".
- [x] `doc/usage.md` "AOT limits": the section states the AOT behaviour of
      value-class output but does not name the interim switch limit (most
      saved value output ignores `off` until task 026). Add one sentence
      that links to the [value-class switch](#value-class-switch) limit, so
      a reader of the AOT section finds it.

## Docs review, round 4

- [x] `README.md`: two statements omit the switch condition that the
      `defvaluetype` and `make-array` docstrings and `artifacts.md` now
      state. The introduction says "It uses value classes on a supported
      Valhalla JVM and ordinary identity classes otherwise"; under
      `value-type.value-classes=off` a supported JVM uses identity classes.
      "Value arrays" says "Array construction needs a supported Valhalla
      JVM, preview and access to an internal JVM package"; it also needs
      the switch not `off`. Add the condition to both (for example "unless
      `value-type.value-classes` is `off`"). `bb vale` passes.

## Docs review, round 5

- [x] `README.md` lines 7 and 107 (116 and 110 characters, from the round 4
      edit): rewrap the introduction paragraph and the "Value arrays"
      paragraph to the width of the other README prose lines (about 80).
      Do not change the text. `bb vale` passes.
- [x] `doc/development/runtime.md`: each cross-JVM matrix has a "Verify ..."
      section (for example "Verify the capability report" and "Verify the
      fallback policy", with the command, the child count and a link to the
      usage contract), but `value-type.value-class-switch-test` has none.
      Add a "Verify the value-class switch" section after "Verify the
      fallback policy": the command
      (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test
      --namespace value-type.value-class-switch-test`), what the children
      cover (`:export-off`, `:java8-off` and `:export-auto` report children,
      allocation messages, invalid values, the policy-first child, probe
      loads, the fixed read and the AOT children) and a link to the
      [value-class switch](../usage.md#value-class-switch) guide. State
      the child count only if the test gives a fixed count.

## Code-shaper review

- [x] `value_class_switch_checks`: make the idioms agree with the sibling
      check namespaces. Remove `public-fn` and call vars as
      `capability-matrix-checks` does (`((resolve 'value-type.array/make-array) ...)`).
      Replace the five copies of
      `(require 'value-type.core 'value-type.array 'value-type.capability)`
      with one private fn. In `invalid-value`, replace the
      `(some-> definition (as-> t {...}))` form with a named local fn, as
      `fallback-policy-checks/invalid-report` does (`failure-of`).
- [x] `value_class_switch_checks/inspection` repeats
      `capability-matrix-checks/inspection` (`array-call` of `array-info` on
      `(object-array 1)`, `:inspected` or the `:value-type/error` and
      `:reason` of `:array-inspection-unavailable`). Make the
      `capability-matrix-checks` fn public and use it; remove the copy.
- [x] `value_class_switch_test/invalid-value-test`: the local `valhalla` is
      `(array-capability/runtime (config/configuration) :no-export)`, which
      is the `:valhalla` runtime of the configuration, and the test reads
      `config/configuration` twice. Read it once and destructure
      `{:keys [baseline valhalla]}`, as `restricted-consumers` and
      `aot-value-output-off-test` do.
- [x] `value_class_switch_test/runtimes` returns `[label runtime]` rows, and
      four tests select rows with `:when` on the label
      (`switch-fixed-read-test` uses `for` to select one runtime). Return a
      map of label to runtime (insertion order kept); select by key. Also
      make the `child` docstring name the `context` argument and the cell
      options.

## Code-shaper review, round 2

- [x] `value_class_switch_checks/invalid-value`: `same?` is
      `#(identical? first-failure %)`, so when `report` does not throw,
      each later call that also returns gives `true` (`identical? nil nil`)
      and the `-identical?` keys claim a shared failure that does not exist.
      Make `same?` false when `first-failure` is nil (for example
      `(and first-failure (identical? first-failure %))`).
- [x] `value_class_switch_checks/switch-property` is public, but no other
      namespace uses it (the switch test writes the property name as a
      literal). Make it private, as `impl.capability` does for its
      `switch-property` and as the round 1 review did for
      `disabled-detail`.
- [x] `value_class_switch_test`: `switch-allocation-message-test` and
      `switch-probe-test` call `(runtimes)` (and so
      `config/configuration`) in the `:let` of each `for` iteration. Bind
      `(runtimes)` once outside the `for`, as `invalid-value-test` binds
      `config/configuration` once.

## Code-shaper review, round 3

- [x] `impl.capability/switch`: make the read agree with its precedent
      `impl.runtime` `policy`, which reads the property with a default
      (`(System/getProperty "value-type.fallback" "silent")`) and maps it
      with `case`. Read `(System/getProperty switch-property "auto")` and
      use `case` (`"auto"` nil, `"off"` the `:disabled` result, default
      throw); remove the `nil?` branch and the `cond`.
- [x] `value_class_switch_checks`: four fns resolve
      `value-type.capability/report` in a local (`probe-loads`,
      `fixed-read`, `invalid-value`, `policy-first`). Add one private
      `report` fn, as `fallback-policy-checks` does, and use it. In
      `invalid-value`, the locals `make-array` and `into-array` shadow
      `clojure.core` fns; call the vars through `resolve` at the call
      site, as `capability-matrix-checks` does, or give the locals names
      that do not shadow.

## Code-shaper review, round 4

- [x] `value_class_switch_checks/invalid-value` `:definition`: use
      `registry/outcome` with `failure-of`, as its precedent
      `fallback-policy-checks/invalid-report` does, not
      `(some-> (registry/caught ...) failure-of)`. Now a definition that
      returns gives `nil`, which hides what it defined; `outcome` gives
      `:accepted` (or a `representation`-style success fn). The expected
      map does not change.
- [x] `value_class_switch_checks/invalid-value`: the local `input` is a fn
      that returns a new lazy seq, but the precedent
      `array-sequence-checks/unavailable-call` binds `input` to the lazy
      seq itself. Give the fn a name that states that it makes a seq (for
      example `make-input`), so `(input)` does not read as a seq call.

## Code-shaper review, round 5

- [x] `value_class_switch_checks/aot-load`: the failure fn
      `(assoc (registry/failure t) :data (select-keys (registry/root-data t) ...))`
      re-implements `registry/root-failure` (`failure` with `:data`
      `root-data`). Use `registry/root-failure` and narrow its `:data`
      (for example `(update (registry/root-failure t) :data select-keys
      [:value-type/error :reason])`).
- [x] `value_class_switch_checks/invalid-value` docstring: the return
      description names the first failure, the later failures and the
      definition outcome, but not `:effects` (the input realization count)
      or `:inspection` (the `capability-matrix-checks/inspection` result).
      Name all returned keys.
- [x] `value_class_switch_test/switch-probe-test`: the expected map is
      selected with `(if (= :export-off label) ...)` inside the `for`.
      Iterate label and expected pairs as data, as `invalid-value-test`
      does with `[label runtime inspection-reason]` (for example
      `[[:export-off {:reason :disabled :probe-loaded? false}]
      [:export-auto {:reason nil :probe-loaded? true}]]`), and remove the
      branch.
