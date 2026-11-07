# Plan: value-class switch

## Approach

Put the switch inside `value-type.impl.capability/detect`, before the API
checks and the probe. The cached delay (`detected-capability`) then owns the
switch result, and all readers (field capability, allocation, `report`,
definitions, AOT field init) get the result through the existing derivation
with no reader change.

Key decisions:

1. Read `System/getProperty "value-type.value-classes"` inside `detect`,
   as the first check. Map:
   - absent or `auto` → continue to `required-apis` and the probe.
   - `off` → return `{:available? false :reason :disabled :detail <fixed
     string>}`. No probe class is defined. The result is the same on every
     JVM.
   - any other value → throw `IllegalArgumentException` with message
     `Invalid value-type.value-classes: <pr-str>; expected auto or off`.
     The Clojure delay caches the thrown exception, so each later read
     throws the same exception object (precedent: invalid policy in
     `impl.runtime`). Field and allocation delays cache the same object.
2. `:disabled` needs a fixed detail string
   (`Value classes are disabled by value-type.value-classes=off.`); the
   existing `unavailable` helper takes a Throwable, so add the `:disabled`
   result directly (or a string-detail variant), do not force an exception.
3. Propagation is free: `field_capability/detect` and `impl.array/discover`
   return the value-class result as-is when it is unavailable, so
   `:reason :disabled` and the `:detail` flow to both. Fallback already
   passes `:detail` to `enforce-fallback!`, so `warn` and `error` text
   follow.
4. One message change outside `impl.capability`:
   `impl.array/require-allocation` appends the `:detail` only for
   `:failed-self-check`; extend that branch to include `:disabled`, giving
   `Value-array allocation is unavailable. <detail>`.
5. Test layout.
   - Property: each child gets `-Dvalue-type.value-classes=<value>` as an
     added `:jvm-opts` entry. `jvm_runner` does not change.
     `start-command` passes each entry as one process argument, so ` off`
     and the empty value pass unchanged.
   - New runtime labels. They run only in the new switch test. They do not
     join `array-capability/runtimes`, so the existing capability, array
     and array-info matrices do not grow. Their rows go to
     `capability_rows/reasons`
     ([value-class native-non-nil-field array-allocation array-inspection]):

     | Label | Runtime | Added option | Reasons |
     |---|---|---|---|
     | `:export-off` | `:export` of `array-capability/runtimes` (EA3, preview, export) | `=off` | `:disabled` `:disabled` `:disabled` nil |
     | `:java8-off` | baseline (Java 8) | `=off` | `:disabled` `:disabled` `:disabled` `:missing-array-api` |
     | `:export-auto` | `:export` | `=auto` | nil nil nil nil (as `:export`) |

   - Files: `test/cross-jvm/value_type/value_class_switch_test.clj`
     (`value-type.value-class-switch-test`) and the baseline-safe checks
     `test/compatibility/value_type/value_class_switch_checks.clj`
     (`value-type.value-class-switch-checks`; loads no value-type
     namespace before the cell requires it). All switch children, the AOT
     children included, are in `value-type.value-class-switch-test`. Each
     cell that makes the first capability read has its own child
     (`doc/development/runtime.md`, isolation rule 5).
   - Report children: for each new label and each policy, a one-cell child
     of `capability-matrix-checks/check` with the require order
     `[value-type.core value-type.array value-type.capability]` (the
     definition makes the first capability read). Move `expected-call` and
     `expected` from `capability_matrix_test.clj` to the new shared
     namespace `test/cross-jvm/value_type/capability_expectations.clj`
     (`value-type.capability-expectations`). `expected` takes
     `[label policy context]`. `capability_matrix_test` passes its current
     context (`harness/rejection-context`); its results do not change.
     The switch test passes the `:disabled` detail for the off labels, with
     `:fallback? true` and `:warning` the `:disabled` detail (the exact
     warning line). `:export-auto` cells have `:fallback? false`.
   - Message children: on `:export-off` and on `:java8-off`, policy
     `silent`, a one-cell child of `value-class-switch-checks` that calls
     `make-array` and `into-array` and returns the message and ex-data of
     each. Expected for each: message `Value-array allocation is
     unavailable. Value classes are disabled by value-type.value-classes=off.`;
     ex-data `{:value-type/error :array-unavailable :reason :disabled
     :detail <the :disabled detail>}`.
   - Invalid-value children: values `OFF`, ` off` and the empty string, on
     `:no-export` (EA3, preview) and on baseline. One child for each runtime
     and value. The check `value-class-switch-checks/invalid-value`
     (precedent: `fallback-policy-checks/invalid-report`) does these
     steps in this order:
     1. Require `value-type.core`, `value-type.array` and
        `value-type.capability` (loading succeeds).
     2. Call `report`; keep the failure (the first failure).
     3. Set `value-type.value-classes` to `auto` with `System/setProperty`.
     4. Call `report` again.
     5. Define an ordinary type (`registry/outcome`).
     6. Call `(make-array nil -1 nil)`
        (precedent: `array-matrix-checks`).
     7. Call `(into-array nil input)` and `(into-array nil nil input)`.
        `input` is a `lazy-seq` that increments an effect counter
        (precedent: `array-sequence-checks/unavailable-call`).
     8. Call `(array-info (object-array 1))`
        (precedent: `capability-matrix-checks/inspection`). Return
        `:inspected`, or the `:value-type/error` and `:reason` of an
        `:array-inspection-unavailable` ExceptionInfo. Other exceptions
        propagate, so an IAE fails the child.

     Catch Throwable at steps 2 and 4 to 7 (`registry/rejected-call` catches
     only ExceptionInfo). Expected result of each child:

     ```clojure
     {:thrown {:class "java.lang.IllegalArgumentException"
               :message (str "Invalid value-type.value-classes: "
                             (pr-str value) "; expected auto or off")
               :chain ["java.lang.IllegalArgumentException"]}
      :second-identical? true
      :definition {:class "clojure.lang.Compiler$CompilerException"
                   :cause-identical? true}
      :make-array-identical? true
      :into-array-identical? [true true]
      :effects 0
      :inspection {:value-type/error :array-inspection-unavailable
                   :reason :array-access-denied}} ; baseline: :missing-array-api
     ```

     Each `-identical?` compares with the first failure (the definition: its
     direct cause). The policy-first child: baseline, policy `unknown` and
     value `OFF`; `report` throws the invalid-policy exception
     (`registry/invalid-policy-message`).
   - `auto` regression (Acceptance 4):
     - Permanent: the `:export-auto` report children.
     - One time, before Slice 5: add `-Dvalue-type.value-classes=auto`
       to each child in an uncommitted edit. Append it after the policy
       option in `jvm_runner/start-command`, and append it the same way in
       `array-capability/expected-flags` (the array and array-info matrices
       compare the child JVM input arguments). Run `:cross-jvm-test`
       without `value-type.value-class-switch-test`: its children put a
       switch value in `:jvm-opts`, before the policy option, and the JVM
       uses the last `-D` of a property, so they would run with `auto`.
       Its `:export-auto` children already cover `auto`. Scry has no
       exclude option; use
       `--ns-pattern '(?!value-type\.value-class-switch-test$).*-test'`
       (the default pattern is `.*-test$`). Run `:test` and each
       `:fallback-test` parent of `doc/development/runtime.md` with
       `-J-Dvalue-type.value-classes=auto`. Revert the edit. Record the commands and results in
       implementation.md. The existing matrices without the property run
       in Slice 5.
   - AOT children (a separate compile phase and run phase):
     - Compile on EA3 with preview and no switch property, so the output
       has value classes (and native fields for `restricted.aot-fixture`).
       Do not compile under `off`: the definitions then fall back
       (`select-enforcement` selects generated enforcement) and the output
       has identity classes, so the run phase tests nothing.
     - Run in a source-free consumer on `valhalla` (EA3, preview) with the
       added switch option (`aot-fixture/source-free-consumers`).
     - Native fields: fixture `restricted.aot-fixture` (`restricted-source`
       in `field_aot_test.clj`), producer `config/value-producer` (no
       export needed). Run with `off` under each policy. Do not use
       `aot-fixture/incompatible-consumers`: it expects
       `:unsupported-class-version`. A new check in
       `value-class-switch-checks` requires the saved namespace once and
       returns source absence and the load outcome: the root class, root
       message, cause chain (`registry/thrown`) and the root ex-data keys
       `:value-type/error` and `:reason`. Expected: root
       `clojure.lang.ExceptionInfo`, data
       `{:value-type/error :field-capability-unavailable :reason :disabled}`,
       message contains the `:disabled` detail.
     - No native fields: fixture `checked.aot-fixture` from
       `identity-source` in `public_field_aot_test.clj` (a `defvaluetype`
       with a generated `^String ^:non-nil` field; require the public var,
       do not copy it). Producer `config/value-producer`, as in
       `public-field-aot-test/source-free-public-generated-value-test`, so
       the output has value classes and calls `check-value-class` at
       initialization. Run with `off` under policy `error`. Check
       `consumer-aot-checks/holder-consumer-form` with
       `consumer-aot-checks/string-holder`; expected
       `(consumer-aot-checks/string-holder-result true)` (value classes).
     - Invalid value at AOT initialization: fixture
       `restricted.aot-fixture`, as for native fields, run with `OFF` under
       policy `error`. Expected root `java.lang.IllegalArgumentException`
       with the stated message. This entry point is in the design
       "Contract" but not in Acceptance 3; the plan adds the test.
6. Docs after the behaviour is in place: `report` docstring (then regenerate
   `doc/API.md`), usage guide (`:disabled` reason near `:failed-self-check`,
   the property, its values and purpose, the `error`-policy interaction,
   the AOT limit until task 026; not the invalid-value failure, which the
   `report` docstring documents), `CHANGELOG.md`, embodiment gap table (D18 except AOT),
   `fallback/selected-detector.md` (switch read first), `mementum/state.md`.
   The `report` docstring change is exact:
   - Add `:disabled` at the end of the `:value-class` reason list. The
     `:native-non-nil-field` and `:array-allocation` lists include it
     through "the value-class reasons".
   - Add this paragraph after the policy paragraph:
     "The report reads the `value-type.value-classes` property with the
     value-class capability, after the policy. Valid values are `auto` and
     `off`; no property is `auto`. `off` gives `:disabled` for
     `:value-class`, `:native-non-nil-field` and `:array-allocation`, and
     runs no value-class probe. An invalid value throws
     IllegalArgumentException; no partial report is returned. The first
     read fixes the result; later property changes have no effect."
   - Keep the other text. Fix only the wording that `bb vale` rejects.

## Risks

- Delay exception caching is the mechanism for "same exception at each
  read"; a refactor to a memoized fn would break it. Keep the delay.
- Invalid-value entry-point coverage is wide (report, definition,
  make-array, into-array, AOT field init, array-info non-throwing); missing
  one gives a silent contract gap. The checklist names each entry point.
- Child-test matrix growth: the new labels stay out of
  `array-capability/runtimes`; the switch test adds about 25 children.
- The one-time `auto` run uses an uncommitted harness edit. Revert it
  before the commit; the record in implementation.md is the evidence.
- Under `off` on Java 8, `require-value-capability!` in a value receiving
  form uses `no-value-classes-message`, not the `:disabled` detail. Not in
  the contract; do not test for the detail there.
- `report` reads policy before capability (task 024); keep that order so
  invalid policy wins over invalid switch value.

## Slice order

1. Switch in `impl.capability`: property read, validation, `:disabled`
   result, fixed detail string, order before API checks.
2. Message propagation: `require-allocation` `:disabled` branch; verify
   field and allocation derivation and `enforce-fallback!` text.
3. Cross-JVM and compatibility tests (decision 5): new report rows and
   labels, `off` and `auto` report children, message children,
   invalid-value children, AOT children, the one-time `auto` run of the
   existing matrices.
4. Docs and design artifacts: docstring, API.md, usage guide, CHANGELOG,
   gap table, selected-detector.md, state.md.
5. Full verification: all suites, `bb clj-fmt:check`, `bb clj-kondo:lint`,
   `bb vale`, `bb api-docs --check`.
