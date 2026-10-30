# Completion steps

The user accepts the existing evidence as sufficient. The
[blocker list](blockers.md) is the authoritative remaining-work list. Earlier
fine-grained test follow-ups are not additional completion requirements; Git and
implementation.md preserve the execution history.

- [x] Implement the portable non-nil contract, both enforcement backends and policy selection.
- [x] Verify declarations, constructors, reads, compilation, Class identity and AOT.
- [x] Verify controlled native absence with the approved accessor override.
- [x] Accept combined public/backend/source-inspection evidence for criteria 1–8.
- [x] Deliver usage/API documentation and record bounded verification evidence.
- [x] Reconcile acceptance and remove open-ended test expansion as a blocker.
- [x] Validate full runtime normal/interpreter, build, release, tooling and quality checks.
- [x] Complete B1: final runtime, compatibility, build, release, tooling and quality validation.
- [x] Record final results, report implementation complete and close the task.

Do not reopen completed items because old progress notes say that work remains.
Only a concrete failed final check or a newly demonstrated contract defect can
justify further implementation within this task.

## Implementation review follow-ups

- [x] Remove dead `core/native-field-deftype` and the native-only 3-arity of
      `fields/specifications`; no source or receiving expansion uses them. Move
      the affected compatibility tests to the 4-arity or `receiving-specifications`.
      Update the stale `native-field-deftype` gap reference in
      `ramora/defvaluetype-design/decisions/validation-and-errors.md`.
- [x] Correct stale docstrings: `fields/validate-type` ("until generated
      enforcement is integrated"); `loader` ns, `install!` and `define-value-class`
      (describe value output and `transform/->value-class` only, but identity
      output also uses them; `:jvm-rejected` message says "value class").
- [x] Replace removed `nil-restricted` wording in user-visible error messages:
      `fields/resolve-tag` ("Cannot resolve nil-restricted field type") and
      `runtime/require-field-capability!` ("native nil-restricted fields").
- [x] Reuse one cached `Class::isValue` lookup. `fields/value-predicate` adds a
      fourth copy next to `core/is-value-method`, `capability` and `array`;
      `specifications` also repeats the predicate invocation inline.
- [x] Set `*warn-on-reflection*` in `identity-backend`, `fields`, `runtime` and
      `field-capability` like the other impl namespaces. Type-hint `delegate` as
      `MethodVisitor` in `identity-backend/rewrite`: calls on it reflect per
      bytecode instruction of every captured class.

## Implementation review follow-ups (2)

- [x] Align the identity-backend enforcement error. `identity-backend/prepare`
      throws `:invalid-field` with `:reason :invalid-enforcement`; `core`,
      `fields/receiving-specifications` and `transform/->value-class` use
      `:value-type/error :invalid-field-enforcement` for the same condition.
      Use the shared key, or remove the check if `identity-field-deftype`
      is its only guarded caller.
- [x] List the receiving-boundary errors in the `defvaluetype` docstring
      Errors section: `:value-capability-unavailable` (transferred value
      expansion on a JVM without value classes) and `:invalid-field-enforcement`.
      The docstring says receiving checks apply to transferred expansions,
      but it does not name these keys. Regenerate doc/API.md.
- [x] Set `*warn-on-reflection*` in `value-backend` like the other impl
      namespaces. In `runtime`, rename the `fields` alias of
      `value-type.impl.field-capability` (for example `field-capability`);
      `fields` is the conventional alias of `value-type.impl.fields`.

## Implementation review follow-ups (3)

- [x] Share the probe class emitter. `capability/probe-bytes` gives the same
      bytes as `field-capability/probe-bytes` with descriptor `"J"` and no
      restriction. Both namespaces also resolve `ClassFile/latestMajorVersion`
      by reflection. Keep one emitter and one major-version lookup in `capability`.
- [x] Resolve each enabled field tag once in `defvaluetype`. With value
      capability, `field-specs/specifications` runs with `false`, then again
      with the native capability result; only `:enforcement` changes. Derive
      the native modes from the validated specifications. Keep definition
      errors before the native field probe.
- [x] Update the internal-name list in `api_docs_test/public-reference-test`.
      `is-value-method` no longer exists, so its check always passes. Check
      `value-field-deftype` and `identity-field-deftype` instead.

## Implementation review follow-ups (4)

- [x] Remove the `native-capable?` parameter of `fields/specifications`. After
      `select-enforcement` was extracted, `defvaluetype` and
      `receiving-specifications` pass only `false`; only tests pass `true`.
      Return `:generated` specs; tests that check native selection call
      `select-enforcement`. Update the docstring.
- [x] Remove the unused `:classfile` key from `capability/required-apis`.
      `probe` reads only `:predicate`; `latest-major-version` resolves
      `ClassFile` again.

## Implementation review follow-ups (5)

- [x] Remove the 3-arity of `field-storage/emit!`. `transform/storage-transform`
      and `field_storage_test` pass `native?` explicitly; no caller uses the
      default. Update the docstring.
- [x] Update the stale API sketch in `ramora/defvaluetype-design/embodiment/runtime.md`.
      Show the 4-arity `->value-class` with `generated-fields` and the 3-arity
      `install!` with representation (`:value` or `:identity`). Specifications
      also carry `:enforcement`. The loader passes its delegate through
      `value-backend/prepare` to the 4-arity, not the three-argument arity.

## Implementation review follow-ups (6)

- [x] Update the stale CHANGELOG `[Unreleased]` field entry. It describes opt-in
      `:nil-restricted` metadata, value-class-only tags and rejection of unavailable
      native support under every policy. It links the missing anchor
      `doc/usage.md#native-nil-restricted-fields`. The `defvaluetype` entry says
      fallback applies only "without enabled nil-restricted fields". Describe
      `:non-nil`, identity fallback with generated checks and the removed key;
      link `#portable-non-nil-fields`.
- [x] Probe native field capability only when native enforcement is possible.
      `defvaluetype` calls `runtime/field-capability` for every enabled field set
      on a value-capable JVM, also when no tag is a value class. An unexpected probe
      defect then fails declarations that need only generated checks. Call the probe
      only when some validated `:type` is a value class; keep definition errors first.
- [x] Emit no empty `RuntimeVisibleAnnotations` attribute in `field-storage/emit!`.
      Generated fields with no source annotations get an empty attribute. Add the
      attribute only when `annotations` is not empty. Assert attribute absence
      in `field_storage_test` for a generated field without annotations.

## Implementation review follow-ups (7)

- [x] Replace the in-process probe stub in
      `restricted_fields_test/native-probe-scope-test`. It redefines
      `runtime/field-capability` to throw a simulated defect in the shared test
      process. The approved exception permits only an unavailable result, in
      isolated child JVMs. Use a real check instead: in a fresh child JVM, define
      a String-tagged `:non-nil` field and assert that
      `runtime/detected-field-capability` is not realized; then define a
      value-class-tagged field and assert that it is realized. Keep the
      definition-error-first case without a stub.
- [x] Document `:field-capability-unavailable` in the `defvaluetype` Errors list
      as a receiving-boundary error: a transferred expansion with fixed `:native`
      modes on a JVM without native field support. The current prose ("unavailable
      support reports ...") implies that local declarations can raise it, which
      contradicts "missing native support alone does not ... fail". Regenerate
      doc/API.md.

## Implementation review follow-ups (8)

- [x] Check representation before field resolution in `core/value-field-deftype`.
      `receiving-specifications` runs first, so a transferred native Long expansion
      on a JVM without value classes reports `:invalid-field` `:identity-type`,
      not the documented `:value-capability-unavailable`. Move the
      `runtime/capability` check before `receiving-specifications`. Update the
      Long expectation in `field_transfer_test/unsupported-public-transfer-test`.
- [x] Add `:invalid-field` (macroexpansion; data `:field`, `:reason`) to the
      `defvaluetype` Errors list; it is the main field error but is absent. Remove
      the prose sentence "Invalid fields report :invalid-field ...; it includes
      :class-name ..." (unclear "it"; the Errors header gives the common keys).
      Regenerate doc/API.md.

## Implementation review follow-ups (9)

- [x] Update `ramora/nil-restricted-field-design/embodiment.md`. The paragraph
      "Final acceptance remains open: ..." contradicts the accepted, closed task;
      remove it or state the accepted limits. Its task links point to
      `munera/open/017-...`; use `munera/closed/`. The usage link anchor
      `#non-nil-fields` does not exist; use `#portable-non-nil-fields`.
- [x] Correct stale implementation descriptions. In
      `ramora/defvaluetype-design/embodiment/runtime.md` (State), the
      `Class::isValue` lookup cache is `capability/value-predicate`, not a delay
      in `core`. In the `transform/->value-class` docstring, storage-types are
      not only "for native restriction", and "Selected fields use concrete storage,
      native restriction and constructor casts" omits that `generated-fields`
      get `requireNonNull` checks and no native annotation.

## Implementation review follow-ups (10)

- [x] Check identity modes before field resolution in `core/identity-field-deftype`.
      `receiving-specifications` runs first and calls `validate-type` for each
      `:native` mode, so a String tag (or any tag on a baseline JVM) reports
      `:invalid-field` `:identity-type`, not the documented
      `:invalid-field-enforcement`. Verified on JDK 25: `[:native]` with a String tag
      gives `:identity-type`. Check that `modes` are all `:generated` before
      `receiving-specifications`, as review (8) did for value capability. Add a
      test for `[:native]` with identity and external tags.
- [x] Complete the loader errors in the `defvaluetype` Errors list. Identity output
      throws `:unsupported-shape` (`identity-backend/rewrite`), and both backends
      throw `:invalid-field` `:field-class-mismatch` (`loader/capture-loader`); the
      list names only `:jvm-rejected` and `:unexpected-constructor`. Name them, or
      use `:unexpected-constructor` for the identity constructor-shape errors so that
      both backends report one key. Regenerate doc/API.md.

## Implementation review follow-ups (11)

- [x] Wrap the `unsupported!` calls in `identity-backend/rewrite`. Review (10)
      put each call and its data map on one line (lines 25, 48, 67, 74–80; up to
      131 columns). The rest of the namespace wraps near 90 columns. Put the data
      map on its own line, as the replaced `ex-info` forms did.

## Test review follow-ups

- [x] Remove the test-covers-test check in `loader_policy_test`. Its child JVM
      runs `identity-loader-test/public-stale-sibling-class-test` through
      `scry.core/run` and asserts the test summary. Move the check body to a
      shared `*_checks.clj` function, as `field_transfer_checks` does. Call it
      from the in-process test and from each child JVM; assert its result data.
- [x] Remove the test-only arities of `transform/->value-class`. After the task,
      `value-backend/prepare` calls only the 4-arity; the 1-, 2- and 3-arity
      forms have only test callers (`transform_test`, `method_attributes_test`).
      Keep one arity; tests pass `storage-types`, `class-loader` and
      `generated-fields` explicitly. Update the docstring ("Optional ...").

## Test review follow-ups (3)

- [x] Remove the `walk/postwalk-replace` rewrite of `controlled-construction` in
      `controlled_field_capability_test`. The receiving and AOT tests replace exact
      subforms of the quoted program. A replacement that does not match fails
      silently: receiving cases then all use plain `eval`, and the AOT consumer
      defines `ControlledHolder` from source, not from saved classes. Make the check
      program a function of its definition form and factory lookup. Define the
      shared `expected` map once.
- [x] Share the AOT producer and Java consumer compilation ceremony. Five task tests
      (`controlled_field_capability`, `generated_value_aot`, `public_native_field_aot`,
      `public_mixed_field_aot`, `public_field_aot`) and `field_aot_test` build the
      same `compile` and `javax.tools` form strings. Each one hard-codes
      `--release 27`. Add helpers to `aot_fixture`; derive the release from the
      child JVM feature version.

## Test review follow-ups (4)

- [x] Return data from the function AOT consumer programs; do not `assert` inside
      them. `function-public-identity-aot-test`, `function-public-generated-value-aot-test`
      (`public_field_aot_test`), `function-public-native-value-aot-test` and
      `function-public-mixed-value-aot-test` put each `assert` inside the outer
      `(catch Throwable failure (.getName (class failure)))`. A failed check then
      gives only `"java.lang.AssertionError"` and does not identify the check.
      Return the inspected facts (value?, annotations, type, stored value,
      construction outcomes) and compare them with an expected map in the parent.
      The four string programs are near copies; build them from one shared form
      or `*_checks.clj` function. Remove the single-element `(doseq [runtime [valhalla]`.
- [x] Use one `saved-definition` in `controlled-native-absence-aot-test`.
      `[saved-definition saved-definition]` repeats a no-op `require`, so the second
      result adds no signal. It is a residue of the removed `[macroexpand-1
      macroexpand]` rewrite.

## Test review follow-ups (5)

- [x] Return data from the reload comparison; do not `assert` in the child.
      Four source-free tests (`public_field_aot_test` lines 43 and 200,
      `public_native_field_aot_test` 75, `public_mixed_field_aot_test` 81) wrap
      the consumer form in the same syntax-quoted `before#`/`after#` program.
      A mismatch gives only `Assert failed: (= before__N__auto__ after__N__auto__)`,
      without the values. Put one reload wrapper in a shared helper that prints
      `{:before … :after …}`. In the parent, check that both are equal to the
      expected result.
- [x] Use `fixture/compile-java` in `public_field_aot_test/java-public-identity-consumer`.
      It still builds the `javax.tools` form string by hand. `compile-java` always
      passes `--enable-preview --release`, which Java 8 javac rejects. Add a
      baseline option (no preview or release flags) and use it in this test.

## Test review follow-ups (6)

- [x] Return data from the source-free Clojure consumer forms; do not `assert` in
      the child. `identity_aot_test/consumer-form` and `clojure-consumer-form` in
      `generated_value_aot_test`, `public_native_field_aot_test` and
      `public_mixed_field_aot_test` assert source absence, value?, field type,
      final modifier, annotations and stored values. A failure gives only
      `Assert failed: <form>`, without the actual value. The identity form then
      prints the constants `:source-absent? true :reads? true`. The four forms are
      near copies that differ by field type, value and annotation. Build them from
      one shared form, as `function_aot_checks/invoke-install-form` does; return
      the inspected facts and compare them with an expected map in the parent.
      Apply the same change to the source `assert` in
      `controlled_field_capability_test/saved-definition`.
- [x] Share the Java consumer child program. Seven namespaces (`identity_aot`,
      `field_aot`, `public_field_aot`, `generated_value_aot`, `public_native_field_aot`,
      `public_mixed_field_aot`, `controlled_field_capability`) hand-write a string or
      list that asserts source absence (and value? or annotations), calls
      `fields.*JavaConsumer/runChecks` and prints `:java-checks-passed`. Add one
      `aot_fixture` helper that takes the consumer class and an optional wrap
      (for the controlled `with-redefs`). It returns data: source absence, value?,
      `required` annotations and the `runChecks` outcome (`:passed` or the
      exception class and message). Compare that data in the parent.

## Test review follow-ups (7)

- [x] Return data from the `field_aot_test` source-free consumers; do not `assert`
      in the child. Review (6) omitted this namespace. `source-free-consumer` asserts
      reads, stored values and the Supplier result, then prints the constants
      `:source-absent? true :reads? true`. `separately-compiled-restricted-field-consumer-test`
      also asserts source absence in the compile `:wrap` and in the consumer form.
      Use `consumer-aot-checks/holder-consumer` (add an optional read of the
      Supplier result if needed) or return the inspected facts. Compare them with
      an expected map in the parent.
- [x] Share the incompatible-receiver consumer and remove the test-namespace
      dependency. `generated_value_aot_test/incompatible-consumer-form` and
      `field_aot_test/incompatible-consumer` are copies that differ by source and
      owner; both `assert` source absence in the child. `public_field_aot_test`,
      `public_native_field_aot_test` and `public_mixed_field_aot_test` require the
      test namespace `generated-value-aot-test` only for this form. Add one
      data-returning function to `consumer_aot_checks` (source absence and the two
      load outcomes); call it from the five tests. Apply the same change to the
      source `assert` in `public_field_aot_test/strict-public-identity-aot-test`.
- [x] Consolidate the child check helpers. `value-class?` and `outcome` are copied
      in `function_aot_checks` and `consumer_aot_checks`; the two `outcome` copies
      differ (only one removes `InvocationTargetException`). Keep one copy. The form
      builders also differ: `invoke-install-form` and `aot_fixture/java-consumer-form`
      return strings, `holder-consumer-form` returns a form. `java-consumer-form`
      is in `aot_fixture`, away from `java-consumer`. Put each builder next to its
      check function and use one return type.

## Test review follow-ups (8)

- [x] Share the source-free producer ceremony. About 20 AOT tests (`field_aot`,
      `public_field_aot`, `public_native_field_aot`, `public_mixed_field_aot`,
      `generated_value_aot`, `identity_aot`, `controlled_field_capability`) repeat:
      `compile-clojure`, producer checks, `when` gate, `Files/delete` of the fixture
      and `(is (not (.exists fixture)))`. The producer checks differ: some check
      `:exit`, `:err` and `:compiled`; others check only `:exit`
      (for example `java-public-identity-consumer`, `strict-public-identity-aot-test`,
      the function AOT tests). Only `field_aot_test` has a `compile-producer` helper.
      Add one `aot_fixture` helper that compiles, checks all three, deletes the source
      and returns the result when it passes. Make `classpath-with` public and use it
      for the repeated `(str (:classpath jvm) File/pathSeparator output)`.
- [x] Return the policy rejection message from the strict identity AOT checks.
      `public_field_aot_test/strict-public-identity-aot-test` uses a hand-written
      string program, unlike the other consumers, and compares only the root class
      `"java.lang.Error"`. `function-public-identity-aot-test` under `"error"`
      also compares only that class (`checks/outcome`). Any other plain `Error`
      passes. Move the strict program to a `*_checks.clj` function. Return the root
      class and message of the first load. Assert the message is the fixed fallback
      policy rejection, as `runtime_test` does.

## Test review follow-ups (9)

- [x] Split the merged `let` bindings from review (8). In five tests the
      `classpath` binding is on the `config/configuration` line, after 14 spaces:
      `generated_value_aot_test` line 26, `public_field_aot_test` lines 48 and 161,
      `public_mixed_field_aot_test` line 82, `public_native_field_aot_test` line 54.
      cljfmt does not detect this. Put each `classpath` binding on its own line.

## Test review follow-ups (10)

- [x] Move the string programs of `field_capability_test/public-field-policy-test`
      and `public-field-evaluation-policy-test` to a `*_checks.clj` function, as
      the AOT consumers do. They are concatenated strings that copy the
      direct/factory/reflective construction and rejection logic of
      `consumer_aot_checks`. Both map any plain `java.lang.Error` root to
      `:strict-error`, so any other `Error` passes. Return the root class and
      message; assert the fixed fallback policy rejection, as review (8) did.
      Add `testing` with configuration and policy to `public-field-policy-test`;
      a failure does not identify the case now.
- [x] Share one thread runner in `expansion_checks`. `constrained-discarded-with-loader`
      is the third copy of the bound/unbound `Thread` + `promise` + `LOADER`
      ceremony (after `warning-timing` and the 0-arity `check-expansions`). Its
      precondition `assert` gives only `AssertionError` in the child; return the
      observed `thread-bound?` as data and check it in the parent. `constrained-discarded`
      and `invalid-constrained-discarded` copy the root-cause loop; use one helper.
      The strict outcome of `constrained-discarded` is only `"java.lang.Error"`;
      return and assert the root message as well (`runtime_test`
      `constrained-discarded-expansion-test`, `constrained-validation-before-policy-test`).

## Test review follow-ups (11)

- [x] Assert the strict policy rejection message in the remaining identity
      policy tests, as reviews (8) and (10) did. Each one accepts any plain
      `java.lang.Error`: `field_transfer_test/strict-identity-public-transfer-test`
      (`field_transfer_checks/consume` returns only `:error-class`),
      `identity_receiving_test/receiving-identity-policy-test` (`:strict-error?`)
      and `strict-nested-public-declaration-test` (`rejected?`). Return the root
      class and message (`consumer-aot-checks/failure`); compare with
      `policy-rejection` of the capability detail, or `fallback-rejection` for
      fixed identity output.
- [x] Move the five string programs of `identity_receiving_test`
      (`receiving-identity-policy`, `incompatible-value-receiving`,
      `generated-value-receiving-policy`, `strict-nested-public-declaration`,
      `nested-public-warning-scope`) to an `identity_receiving_checks.clj`, as
      review (10) did for `field_capability_test`. Return inspected facts, not
      booleans: `nested-public-warning-scope` returns tuples of `identical?`,
      `nil?` and `try ... true` results, so a failure does not show the actual
      type, value or exception. Reuse `consumer-aot-checks/builders` and `outcome`.

## Test review follow-ups (12)

- [x] Assert the strict policy rejection message in the two remaining task
      strict-receiving tests, as reviews (8), (10) and (11) did. Each one accepts
      any plain `java.lang.Error`:
      `transfer_test/constrained-concurrent-strict-test`
      (`warning_checks/concurrent-rejections` returns only `:cause`) and
      `child_transfer_test/strict-receiver-child-replacement-test`
      (`reject-replaced-child` returns only `:error-class`). Return
      `consumer-aot-checks/failure`; compare with `fallback-rejection`.
- [x] Move the child check functions of `child_transfer_test` (`produce`,
      `consume`, `reject-replaced-child`) to a `child_transfer_checks.clj`, as
      review (7) did for `generated_value_aot_test`. Child JVMs now require the
      test namespace `value-type.child-transfer-test`. Replace the two string
      programs of `value-receiver-child-replacement-test` and
      `native-receiver-child-replacement-test`, which add `:value?`, `:final?`
      and `:generated?`/`:native?` booleans, with one data-returning check. Reuse
      `consumer-aot-checks/builders`, `outcome`, `value-class?` and
      `annotation-names`: return annotation names and per-construction rejection
      classes, not `:rejected` from a `catch` of the expected exception type only.

## Implementation review follow-ups (13)

- [x] Make the portable experiment reproducible or mark it non-runnable.
      `spike/portable-fields/verify.sh` runs against current `src`, but
      `value_experiment.clj` calls the removed 3-arity `transform/->value-class`
      and installs its own `Compiler/LOADER` proxy, which `defvaluetype` capture
      now bypasses. On EA3 preview, `portable-fields.value-experiment-test` fails
      (`generated-value-contract`: the value-tagged field has `@NullRestricted`).
      Either run the script against the original commit `6318c36` (for example
      a `git worktree`), or state in the script and in
      `ramora/nil-restricted-field-design/embodiment.md` that it ran at
      `6318c36` and does not run against the current tree.
      Done: `verify.sh` runs in a temporary git worktree at `6318c36`; script
      header and `embodiment.md` state it does not run against the current tree.

## Implementation review follow-ups (14)

- [x] Replace the stale task 017 progress history in `mementum/state.md`. Plan
      step 4 says to record verified current behavior, not more progress history.
      The file has 5471 words, and many statements contradict the closed task,
      for example "The acceptance audit remains open" (line 176), "controlled
      native absence and final acceptance remain open" (lines 185, 540) and
      "Controlled native absence remains separate acceptance work" (line 161).
      Keep a short summary of current behavior and evidence limits, with links
      to `acceptance-audit.md` and `implementation.md`. Git keeps the history.
      Done: `state.md` rewritten as a 604-word current-behavior summary with
      evidence limits and links to `acceptance-audit.md` and `implementation.md`.

## Implementation review follow-ups (15)

- [x] Document and test the enabled-field `:not-installed` error. The receiving
      macros `core/value-field-deftype` and `identity-field-deftype` throw
      `:not-installed` at macroexpansion when `Compiler/LOADER` has no thread
      binding. Verified on JDK 25: `clojure.walk/macroexpand-all` of a
      `^String ^:non-nil` declaration in an unbound thread gives a
      `CompilerException` (`:macro-syntax-check`) with this cause. The
      `defvaluetype` Errors list and `doc/usage.md` name `:not-installed` only for
      ordinary definitions at run time. Add the enabled-field case
      (macroexpansion; full expansion without a compiler loader binding, for
      example `macroexpand-all`). Add a test with no stub. Regenerate doc/API.md.
      Done: `defvaluetype` Errors list and `doc/usage.md` name the
      macroexpansion `:not-installed` case; `doc/API.md` regenerated.
      `core-test/not-installed-enabled-field-test` runs `macroexpand-all` on a
      raw thread, with no stub. Passes on JDK 25 (identity path).

## Implementation review follow-ups (16)

- [x] Make the receiving-boundary error messages in `core` name the class and
      the fix, as `check-value-class` and `require-field-capability!` do.
      `value-field-deftype` and `identity-field-deftype` throw
      "Fixed value representation is unavailable", "Value field capture requires
      a compiler loader", "Identity field capture requires a compiler loader"
      and "Identity fields require generated enforcement". None names
      `class-name`. The two `:not-installed` messages do not give the documented
      fix ("Evaluate the defvaluetype form directly"); share the existing
      `:not-installed` fix text from `error-message`. Reuse
      `no-value-classes-message` for `:value-capability-unavailable`. Update the
      message expectations in `field_transfer_test` and `identity_receiving_test`.
      Done: all four messages name the class and the fix. `:not-installed`
      and `:invalid-field-enforcement` share `direct-fix` with
      `error-message`. `:value-capability-unavailable` reuses
      `no-value-classes-message` when `Class::isValue` is missing; on a JEP 401
      JVM without preview it says "Use --enable-preview." Tests updated.

## Implementation review follow-ups (17)

- [x] Derive the `:value-capability-unavailable` message from the capability
      result, as `enforce-fallback!` and `require-field-capability!` do.
      `core/value-field-deftype` ignores `(runtime/capability)` `:reason` and
      `:detail`. It checks `capability/value-predicate` again and adds the fixed
      text "The JVM cannot define value classes. Use --enable-preview." for every
      other reason. Use `no-value-classes-message` for `:missing-value-predicate`,
      else append `:detail`. Add `:reason` to the ex-data, as
      `:field-capability-unavailable` does, and name it in the `defvaluetype`
      Errors list. Regenerate doc/API.md. In the tests, derive the expected
      message from the receiver capability (as `policy-rejection` does), not from
      the copied text in `consumer-aot-checks/unavailable-message`.
      Done: message and ex-data `:reason` come from `(runtime/capability)`.
      `consumer-aot-checks/unavailable-failure` derives the expected failure
      from the capability that each consumer reports. doc/API.md regenerated.

## Implementation review follow-ups (18)

- [x] Restore the fix text in the `:value-capability-unavailable` message on
      JVMs without the ClassFile API. Review (17) appends `:detail` for every
      reason except `:missing-value-predicate`. On Java 8–23 the reason is
      `:missing-classfile-api` and `:detail` is only the class name. Verified on
      JDK 21: "Cannot define value class user.H: fixed value representation is
      unavailable. java/lang/classfile/ClassFile". The message names no fix,
      which review (16) requires. These JVMs also lack `Class::isValue`. Use
      `no-value-classes-message` for both `:missing-classfile-api` and
      `:missing-value-predicate`; append `:detail` only for other reasons.
      `consumer-aot-checks/unavailable-failure` copies the production
      conditional, so it cannot find this defect. In the baseline case, assert
      the literal expected message.
      Done: `no-value-classes-message` for `:missing-classfile-api` and
      `:missing-value-predicate`; `:detail` otherwise. `unavailable-failure`
      matches. `incompatible-value-receiving-test` asserts the literal baseline
      message.

## Implementation review follow-ups (19)

- [x] Regenerate doc/API.md. Review (18) added one line to `core/value-field-deftype`.
      The `defvaluetype` source link is now stale (`core.clj#L133-L260`, expected
      `#L134-L261`). `bb api-docs --check` fails: "doc/API.md is missing or stale".
      Run `bb api-docs`. Include `bb api-docs --check` in the follow-up verification
      for each change to `src`.
      Done: `bb api-docs` updated the source link to `#L134-L261`;
      `bb api-docs --check` passes.

## Implementation review follow-ups (20)

- [x] Make the remaining documented errors outside `core` name the class and
      the fix, as review (16) did for the receiving boundary in `core`.
      These messages name no class and no fix:
      - `fields/receiving-specifications`: "Invalid fixed field enforcement
        modes" (`:invalid-field-enforcement`). `identity-field-deftype` gives
        the same key with class, modes and `direct-fix`; use one message shape
        for both.
      - `loader/capture-loader`: "Restricted field type resolves to a different
        Class" (`:invalid-field` `:field-class-mismatch`). Name the class, the
        field and the fix (redefine the field type and the dependent
        definition in the same loader, or reload both).
      - `identity-backend/rewrite`: the `unsupported!` messages
        (`:unsupported-shape`), for example "Missing constrained field
        storage". `transform/unexpected-constructor!` names the class, the
        part, the cause and the fix (use Clojure 1.12); do the same.
      Assert the messages in the existing tests for these errors. Run
      `bb api-docs --check` if `src` docstrings change.
      Done: `fields/invalid-enforcement!` gives one message shape for both
      receiving checks; loader and identity-backend messages name class,
      field or part, cause and fix. Tests assert the messages. doc/API.md
      regenerated.

## Test review follow-ups (13)

- [x] Do not assert the full error message text. The design states "Error text
      ... not contractual". Reviews (16)–(20) added full-string equality, which
      fails on any change to the wording:
      `identity_backend_test/shape-message`, `capture_failure_test`,
      `fields_test` (invalid mode transport), `identity_loader_test`,
      runtime `loader_test`, `identity_receiving_test`
      (`receiving-identity-native-modes-test` and the baseline literal in
      `incompatible-value-receiving-test`). `consumer-aot-checks/unavailable-failure`
      also copies the production `case` on `:reason`, so it gives the production
      result again and cannot find a defect in it. Use one shared helper, the
      same as `core_test/message-names?`. Assert that the message contains the
      class name, the specific part (shape part, field, modes) and the fix text
      (for example "Use Clojure 1.12", "--enable-preview", "evaluate the
      defvaluetype form directly"). Keep the exact `ex-data` assertions,
      including `:reason`.
      Done: one `message-names?` helper per test tree (runtime
      `test-support`, compatibility `message-checks`); test trees have no shared
      classpath. All listed sites assert class, specific part and fix text.
      `unavailable-failure` has no `:message` and no `case`;
      `unavailable-message?` asserts class, cause and `--enable-preview`. Both
      unavailable reasons name `--enable-preview`.

## Test review follow-ups (16)

- [x] Remove the trailing blank line at the end of
      `test/runtime/value_type/impl/transform_test.clj`. Code shape review
      follow-ups added it after the last form. cljfmt does not detect this.
      Done: trailing blank line removed.

## Documentation review follow-ups (1)

- [x] Remove the stale status in `doc/development/runtime.md`, "Verify portable
      non-nil fields": "The final task 017 acceptance audit remains open."
      The task is closed and the user accepted the evidence. State the accepted
      limits or remove the sentence.
      Done: sentence removed; section states the accepted limits (silent-only
      packaging, child-JVM matrix scope).
- [x] Add the controlled-capability command to `doc/development/runtime.md`,
      "Verify portable non-nil fields". The section says to keep controlled
      native absence separate, but it gives no command for
      `value-type.controlled-field-capability-test`. `doc/usage.md` sends readers
      to the runtime instructions for these commands. Give the EA3 command
      (`clojure -M:fallback-test --namespace value-type.controlled-field-capability-test`)
      and label it controlled-capability evidence, not a supported JVM configuration.
      Done: command added with that label. Run on EA3: 4 tests, 175 assertions pass.
- [x] Correct stale Ramora status for non-nil fields:
      - `ramora/INDEX.md`: "target `:non-nil` constraint ... remaining verification
        limits". The constraint is implemented; say "accepted evidence limits".
      - `ramora/nil-restricted-field-design.md` (Embodiment): the embodiment file
        "records verified behavior and remaining acceptance gaps". It records
        accepted limits, not acceptance gaps.
      - `ramora/nil-restricted-field-design/verification.md` (Evidence boundary):
        "The [native evidence](embodiment.md) covers the existing implementation
        only. It does not establish the baseline backend, new keyword or portable
        enforcement." `embodiment.md` now records evidence for all three. Link the
        historical native experiment (`spike/null-restricted-fields/report.md`)
        instead, or restate the boundary.
      Done: INDEX and design say "accepted evidence limits"; the evidence boundary
      links the native experiment report and points to embodiment for current evidence.

## Documentation review follow-ups (2)

- [x] Complete the error list in `doc/usage.md`, "Differences from `deftype`".
      "Field and value-path errors" names only `:mutable-field`, `:not-installed`,
      `:not-value-class`, `:jvm-rejected` and `:unexpected-constructor`. The
      `defvaluetype` docstring (doc/API.md) also names `:invalid-field`
      (macroexpansion, and loader `:field-class-mismatch`),
      `:value-capability-unavailable`, `:field-capability-unavailable`,
      `:invalid-field-enforcement` (receiving boundary) and `:unsupported-shape`
      (loader; identity output). Add them, or link the API Errors list. Also:
      - `:jvm-rejected` says "on the selected value path"; `loader/install!`
        throws it for identity output too.
      - `:unexpected-constructor`: say value output only, as the docstring does.
      - "How you get the error" does not say how the field and receiving-boundary
        errors arrive (`CompilerException` cause).
      Done: all ten error keys listed, with a link to the API Errors list.
      `:jvm-rejected` covers value and identity output; `:unexpected-constructor`
      is value output only. "How you get the error" covers field,
      receiving-boundary and loader errors.

## Documentation review follow-ups (3)

- [x] Remove the stale "implementation gap" statements in Ramora. The parent
      [field design](../../../ramora/nil-restricted-field-design.md) (Embodiment)
      now records the implemented contract and accepted evidence limits, but
      these pointers to it still describe a gap or a target:
      - `ramora/defvaluetype-design.md` line 40: "for the target contract and its
        implementation gap".
      - `ramora/defvaluetype-design/fallback.md` line 6: "The field design's
        embodiment section records the current native-only implementation gap."
        This contradicts the first paragraph of the same file.
      - `ramora/nil-restricted-field-design/decisions.md` (D1) line 10: "lists the
        implementation gap".
      - `ramora/defvaluetype-design/qualities.md` line 24: "The field design
        separates the target from existing native-only evidence."
      - `ramora/defvaluetype-design/embodiment.md` line 11: "remaining
        verification gaps"; say "accepted evidence limits", as review (1) did
        for `ramora/INDEX.md`.
      Done: all five pointers say "implemented contract" or "accepted evidence
      limits".
- [x] Replace the old "restrictions" term in `doc/usage.md` line 94
      (`:not-value-class` causes): "An ordinary definition without enabled
      restrictions". Say "without enabled non-nil fields", as the
      `:not-installed` item does. Wrap the line near 80 columns like the rest
      of the list.
      Done: "without enabled non-nil fields"; item wrapped near 70 columns.

## Documentation review follow-ups (4)

- [x] Correct the stale public interface in
      `ramora/defvaluetype-design/embodiment/runtime.md` (Interfaces, Public):
      - "The fallback path, only for definitions without enabled restrictions,
        returns an ordinary identity class." Definitions with enabled non-nil
        fields also fall back to identity classes with generated checks.
      - "Enabled fields require an explicit concrete value-class tag." Tags can
        name any already defined concrete class, including identity and external
        classes.
      - The Errors set names only `:mutable-field`, `:not-installed`,
        `:not-value-class`, `:jvm-rejected` and `:unexpected-constructor`. Add
        `:invalid-field`, `:value-capability-unavailable`,
        `:field-capability-unavailable`, `:invalid-field-enforcement` and
        `:unsupported-shape`, or link the `defvaluetype` Errors list.
      Done: fallback covers both cases; tags name any defined concrete class;
      all ten error keys listed with a link to the API Errors list.
- [x] Update D9 in `ramora/defvaluetype-design/decisions/validation-and-errors.md`.
      "Rejected: throw `:not-installed` at macroexpansion" is true only for
      ordinary definitions. Enabled-field receiving macros throw `:not-installed`
      at macroexpansion without a compiler loader binding (review (15)). State
      both cases. Replace the old term "enabled restrictions" with "enabled
      non-nil fields" here, in `embodiment/runtime.md` (lines 19, 39, 93),
      `qualities.md` (line 13) and `decisions/compiler-and-transform.md` (line 4).
      Done: D9 states both cases; "enabled restrictions" and "Restricted
      expansions" replaced in the four files.
- [x] Document the `:invalid-field` `:reason` values. `doc/usage.md` and the
      `defvaluetype` Errors list give `:reason` but no values; the array errors
      list theirs. Declarations give `:removed-nil-restricted`, `:invalid-non-nil`
      (with `:value`), `:missing-tag`, `:unresolved-tag`, `:enclosing-type`,
      `:primitive-type`, `:array-type`, `:interface-type` and `:abstract-type`
      (with `:tag`). The loader gives `:field-class-mismatch`; receiving native
      modes give `:identity-type`. Add them to `doc/usage.md` (Portable non-nil
      fields). Regenerate doc/API.md if the docstring changes; run
      `bb api-docs --check` and `bb vale`.
      Done: reason list in `doc/usage.md`; docstring unchanged. Both checks pass.

## Documentation review follow-ups (5)

- [x] Remove the stale provisional identity-backend statements in Ramora. The
      [field embodiment](../../../ramora/nil-restricted-field-design/embodiment.md)
      records that identity output uses bundled ASM and that the feasibility
      experiment ran, but these files still describe an open choice:
      - `ramora/nil-restricted-field-design/decisions.md` (D3) lines 31–32:
        "Clojure's bundled ASM is the first candidate, not a verified selection.
        Backend choice is provisional until a baseline-JVM experiment proves ...".
        State the decision: use bundled ASM.
      - `ramora/defvaluetype-design/decisions/compiler-and-transform.md` (D5)
        line 101: "its concrete library choice remains provisional".
      - `ramora/defvaluetype-design/risks-and-evolution.md` line 60: "the baseline
        backend remains a feasibility risk, not an established capability".
      - `ramora/defvaluetype-design/embodiment/runtime.md` line 163: "remaining
        verification limits"; say "accepted evidence limits", as reviews (1)
        and (3) did.
      Done: D3 and D5 state bundled ASM; risk line cites accepted evidence
      limits; runtime.md says "accepted evidence limits".
- [x] Record the controlled native-absence evidence in
      `ramora/nil-restricted-field-design/embodiment.md` (Verified behavior and
      limits). The section lists the real-JVM evidence only. Design acceptance 8
      requires controlled capability cases labelled separately from real JVM
      evidence, and `doc/development/runtime.md` gives the
      `value-type.controlled-field-capability-test` command. State that this
      test replaces only the native capability result in child JVMs, that it
      covers generated checks in value owners with no fallback warning under
      every policy, and that it is controlled-capability evidence, not JVM
      support. Link `controlled-capability-audit.md` in the closed task.
      Done: separate controlled-capability paragraph with audit and runtime
      command links.

## Documentation review follow-ups (6)

- [x] Replace the old "restricted"/"restriction" term for non-nil fields. Native
      restriction is now one enforcement mode; generated checks are not a
      restriction. Review (4) replaced "enabled restrictions", but these remain:
      - `defvaluetype` docstring (`src/value_type/core.clj` lines 165, 176):
        "Only true enables restriction" and "Restricted storage". Say "the
        constraint" and "Non-nil field storage". Regenerate doc/API.md.
      - `ramora/defvaluetype-design.md` line 38 ("Restricted receiving analysis").
      - `ramora/defvaluetype-design/decisions/compiler-and-transform.md` lines
        4, 25, 48, 61, 76 ("Restricted expansions", missed by review (4)), 99,
        110, 125 and 146.
      - `ramora/defvaluetype-design/fallback.md` line 62,
        `qualities.md` lines 13 and 26, `risks-and-evolution.md` line 34 and
        `purpose.md` line 80.
      Say "non-nil fields", "definitions with enabled non-nil fields" or
      "constrained fields". Keep "native restriction" where it names the native
      mode, and keep the array and `:null-restricted?` terms. Wrap lines 48–49
      and 110–111 of `compiler-and-transform.md` near 80 columns. Run
      `bb api-docs --check` and `bb vale`.
      Done: docstring says "the constraint" and "Non-nil field storage";
      doc/API.md regenerated. All listed Ramora lines now say "non-nil fields"
      or "definitions with enabled non-nil fields"; touched paragraphs wrapped
      near 80 columns. Array, `:null-restricted?`, `NullRestricted` and
      native-mode terms kept. Both checks pass.

## Documentation review follow-ups (7)

- [x] Rename the Ramora field design to match the `:non-nil` name. The title is
      "Non-nil field design", but the file and directory still use the removed
      key: `ramora/nil-restricted-field-design.md` and
      `ramora/nil-restricted-field-design/`. Use `git mv` to
      `ramora/non-nil-field-design.md` and `ramora/non-nil-field-design/`.
      Update all links: `ramora/INDEX.md`, `ramora/defvaluetype-design.md`
      and its sections (`purpose.md`, `qualities.md`, `fallback.md`,
      `risks-and-evolution.md`, `embodiment.md`, `embodiment/runtime.md`,
      `decisions/compiler-and-transform.md`,
      `decisions/validation-and-errors.md`), the field design's own `Parent:`
      links, `munera/open/018-fast-compatibility-matrix/design.md` and the
      links in this task's `design.md`. Run `bb vale` and check that no link
      to the old path remains (`git grep nil-restricted-field-design`).
      Partly done: `git mv` to `ramora/non-nil-field-design{.md,/}`; links
      updated in Ramora, `mementum/state.md` and task 018. Closed task 016 and
      history notes keep the old path. `bb vale` passes.
      Done at closure: this task's `design.md` links name
      `ramora/non-nil-field-design`; all three targets exist.
- [x] Reflow the "Non-nil fields" paragraph of the `defvaluetype` docstring
      (`src/value_type/core.clj`). It has short broken lines
      ("Missing/unresolved tags,", "Value AOT artifacts require") and lines
      longer than 80 columns ("constructor checks. Missing native support
      alone does not invoke fallback policy. No", "retain receiver-local loader
      installation ..."). doc/API.md shows the same ragged text. Wrap near
      78 columns like the rest of the docstring; do not change the content.
      Regenerate doc/API.md; run `bb api-docs --check` and `bb vale`.
      Done: wrapped at 78 columns, content unchanged; doc/API.md regenerated.
- [x] Add non-nil fields to the Usage guide entry in `ramora/INDEX.md`. It says
      "fallback, AOT limits, value semantics and error handling", but
      `doc/usage.md` also has "Portable non-nil fields" (and the value-array
      sections). Name them so that the index matches the guide.
      Done: entry names value arrays and portable non-nil fields.

## Code shape review follow-ups (1)

- [x] Replace the old "restricted" term in code names and messages. It names
      enabled non-nil fields, not the native mode:
      - `fields/restricted-fields`: rename (for example `enabled-fields`);
        update the callers in `fields/specifications` and `fields_test`.
      - `field-capability/verify-enforcement`: "Restricted-field probe ...".
      - Docstrings of `field-access/rewrite-class` ("restricted fields") and
        `loader/install!` ("restricted target fields").
      - Test names: `restricted_fields_test` and `test/resources/nil-restricted-fields/`.
      Keep `:nil-restricted`, `NullRestricted` and native-mode terms. In
      `runtime`, alias `value-type.impl.capability` as `capability`, not
      `detector`, as `core`, `fields`, `field-capability` and `array` do.
      Done: `fields/enabled-fields`; probe messages say "Non-nil field probe";
      docstrings say "non-nil fields"; `non_nil_fields_test`; resources moved
      into the existing `test/resources/non-nil-fields/`; `capability` alias.
- [x] Derive the emitted field name once. `identity-backend/prepare` and
      `value-backend/prepare` (twice) compute `(Compiler/munge (name field))`
      from each specification. Add the emitted name to each specification in
      `fields/specifications` (or one shared helper) and use it in both backends.
      Done: shared helper `fields/emitted-name`; both backends use it.
- [x] Share the fixed fallback context. The literal "fixed fallback
      representation" occurs four times: `core/identity-field-deftype` (analysis
      and expansion) and `runtime/guarded-deftype` (analysis and expansion).
      Add one `runtime` function (for example `enforce-fixed-fallback!`) and
      call it at all four sites.
      Done: `runtime/enforce-fixed-fallback!` at all four sites.
- [x] Make the `transform/->value-class` precondition consistent. It throws
      `:invalid-field-enforcement` with no `:class-name`, no fix and the message
      "Generated checks require concrete storage". The `defvaluetype` Errors
      list documents this key as a receiving-boundary error with `:class-name`
      and `:modes`. `value-backend/prepare` derives both arguments from the same
      specifications, so public input cannot reach it. Throw an internal
      `IllegalArgumentException` (programmer error), and add a `transform_test`
      case that passes a generated field with no storage type.
      Done: `IllegalArgumentException` names the missing fields; docstring
      updated; `transform_test/generated-field-without-storage`.

## Code shape review follow-ups (2)

- [x] Share the receiving install check in `core`. `value-field-deftype` and
      `identity-field-deftype` repeat the same block: resolve
      `loader/install!`, call it, and throw `:not-installed` with
      `not-installed-message`. Add one private function (for example
      `install-receiving! [kind class-name specifications representation]`)
      and call it from both macros.
      Done: private `core/install-receiving!`, called from both macros.
- [x] Use one name for each field concept:
      - `core` aliases `value-type.impl.fields` as `field-specs`; the backends
        and tests use `fields`. Use `fields`. Remove the private `core/direct-fix`
        alias; call `fields/direct-fix`.
      - `identity-backend/rewrite` `unsupported!` messages say "constrained"
        (8 sites) for non-nil fields. Say "non-nil". Update the message
        assertions in the tests that match these texts.
      - `fields/validate-type` checks native eligibility only. Rename it to
        `validate-native-type`, as `validate-concrete-type`; update
        `receiving-specifications`, `fields_test` and `field_eligibility_test`.
      Done: `fields` alias, no `core/direct-fix`; 8 messages say "non-nil",
      test assertions updated; `fields/validate-native-type`.
- [x] Extract the exact-Class check from `loader/capture-loader`. The proxy
      `defineClass` mixes the field Class identity check (flow control and
      error data) with transform, write and define. Move the `doseq` to one
      private function (for example `check-field-classes! [target delegate
      specifications]`) and call it before `owner`.
      Done: private `loader/check-field-classes!`.

## Code shape review follow-ups (3)

- [x] Extract the value capability guard from `core/value-field-deftype`. The
      macro body inlines a 12-line check of `(runtime/capability)` that builds the
      `:value-capability-unavailable` message and throws. The native guard is one
      call, `runtime/require-field-capability!`, and `identity-field-deftype` uses
      one call for each check. Add one private function (for example
      `require-value-capability! [class-name]`, next to `no-value-classes-message`)
      and call it from the macro. Keep the message, ex-data and check order
      (before `receiving-specifications`). Run `bb api-docs --check`.
      Done: private `core/require-value-capability!`, called before
      `receiving-specifications`; doc/API.md regenerated (source link shift).

## Code shape review follow-ups (4)

- [x] Pass one field map to `transform/->value-class`. `storage-types` and
      `generated-fields` are parallel structures keyed by emitted name; the
      `IllegalArgumentException` precondition only enforces that they agree.
      `value-backend/prepare` derives both from the same specifications, and
      `storage-transform` and `constructor-code` look up both. Pass one map
      from emitted name to `{:storage-type ClassDesc :native? boolean}` (or the
      specification), so that a generated field without storage cannot occur.
      Remove the precondition and `transform_test/generated-field-without-storage`;
      `field-access/rewrite` can take the map keys and storage types. Update the
      docstring and the `transform_test` and `method_attributes_test` callers.
      Done: `->value-class` takes `[bs storage class-loader]`; `storage` maps
      emitted names to `{:storage-type :native?}`. Precondition and its test
      removed. Ramora `embodiment/runtime.md` updated.
- [x] Keep one arity of `loader/install!`. The 1-arity has only test callers
      (`loader_test`, `test_support`); the 2-arity has one caller,
      `defvaluetype`, which passes empty specifications and the default
      representation. Reviews (5) and test review (1) removed such defaults
      from `field-storage/emit!` and `transform/->value-class`. Keep the
      3-arity; pass `[] :value` explicitly. Update the docstring ("Optional",
      "default") and the test callers.
      Done: `install!` has one 3-arity; `defvaluetype` and test callers pass
      `[] :value`.

## Code shape review follow-ups (5)

- [x] Keep one arity of `field-access/rewrite-class`. The 3-arity has one
      test caller (`field_access_test/selected-instance-references`); the
      4-arity has one caller, `value-backend/prepare`. Review (4) removed such
      defaults from `loader/install!`. Keep the 4-arity; pass `nil` as
      class-loader in the test. Update the docstring ("Optional class-loader").
      Done: one 4-arity; test passes `nil`; docstring updated.
- [x] Pass a `ClassDesc` to `field-storage/emit!`. `transform/storage-transform`
      has a `ClassDesc`, converts it with `.descriptorString`, and `emit!`
      converts it back with `ClassDesc/ofDescriptor`. Take `^ClassDesc
      storage-type`; pass it directly. Update `field_storage_test` (two
      callers) and the docstring.
      Done: `emit!` takes `^ClassDesc storage-type`; `storage-transform` passes
      it directly; tests and docstring updated.

## Code shape review follow-ups (6)

- [x] Use one field-storage map shape for owner and companion rewrites.
      `value-backend/prepare` builds `storage` and also `storage-types`
      (`update-vals storage :storage-type`). `transform/->value-class` makes
      the same projection again for `field-access/rewrite`. Make
      `field-access/rewrite` and `rewrite-class` take `storage` (emitted name
      to `{:storage-type :native?}`) and read `:storage-type`. Pass `storage`
      from both callers; remove the two projections. Update the docstrings and
      `field_access_test`.
      Done: `rewrite` and `rewrite-class` take `storage` and read
      `:storage-type`; both projections removed.
- [x] Name the `Objects.requireNonNull` method type in `transform`.
      `constructor-code` builds it inline on a 134-column line (line 153).
      Add a private constant next to `ACC_STRICT_INIT`, as
      `field-storage/null-restricted` does, and use it. Also wrap the other
      task lines longer than 90 columns: `field_capability.clj` line 43,
      `field_storage.clj` line 29 and `loader.clj` line 102.
      Done: private `transform/require-non-null`; the three lines wrapped.

## Code shape review follow-ups (7)

- [x] Remove `:descriptor` from field specifications. It is derived from
      `:type` in `fields/specifications`, so a specification can hold a
      descriptor that does not agree with its Class. Each backend parses it
      back: `value-backend/prepare` calls `ClassDesc/ofDescriptor`;
      `identity-backend/rewrite` gets the `checkcast` internal name with
      `(subs concrete 1 (dec (count concrete)))`. Derive both from `:type` in
      the backends: `clojure.asm.Type/getDescriptor` and `getInternalName` in
      `identity-backend`; `(ClassDesc/ofDescriptor (.descriptorString type))`
      in `value-backend`. Update the `loader/install!` docstring and the test
      specifications that give `:descriptor` (some, for example in
      `identity_backend_test`, give no `:type`).
      Done: specifications have no `:descriptor`. `identity-backend` maps
      emitted names to `clojure.asm.Type` (descriptor, `checkcast` internal
      name); `value-backend` uses `.descriptorString`. Docstring, tests and
      Ramora updated.
- [x] Separate validation from selection in `fields/enabled-fields`. The
      `filter` function nests two throws (removed `:nil-restricted`, invalid
      `:non-nil`) inside the selection. Add one private function (for example
      `non-nil? [class-name field]`) that validates the metadata of one field
      and returns `(true? value)`; make `enabled-fields`
      `(filterv #(and (symbol? %) (non-nil? class-name %)) fields)` when
      `fields` is a vector. Keep the messages and ex-data.
      Done: private `fields/non-nil?`; `enabled-fields` uses `filterv`.
- [x] Wrap the task lines longer than 90 columns that review (6) did not
      list: `identity_backend.clj` line 82 and `core.clj` line 262.

## Code shape review follow-ups (8)

- [x] Use one "no value classes" condition in `core`. `error-message` tests
      `(nil? @capability/value-predicate)`; `require-value-capability!` tests
      `(#{:missing-classfile-api :missing-value-predicate} reason)`. Both select
      `no-value-classes-message`. Add one private predicate (for example
      `no-value-classes?`) and use it at both sites. Keep the messages and
      ex-data; run the message tests on Java 8 and EA3 (preview off).
      Done: private `core/no-value-classes?` (`Class::isValue` missing) at
      both sites.
- [x] Look up the field type once in `identity-backend/rewrite` `visitField`.
      It computes `(and owner? (contains? types field))` twice and
      `(when owner? (get types field))` once more. Bind
      `^Type type (when owner? (get types field))` once; use it for `seen`,
      the storage check and the descriptor. Keep the messages and ex-data.
      Done: one `type` binding in `visitField`.
- [x] Remove the stale reference to descriptors in the `loader/capture-loader`
      docstring ("descriptors alone lose identity"). Specifications have no
      `:descriptor` since review (7). State that the exact `:type` Classes
      serve `check-field-classes!`.
      Done.

## Code shape review follow-ups (9)

- [x] Move the native enforcement selection out of `defvaluetype`. The macro
      body inlines `(if (and available? (some #(capability/value-class? (:type %))
      validated)) (fields/select-enforcement validated (:available?
      (runtime/field-capability))) validated)`. The `some` test repeats the
      `value-class?` test of `select-enforcement`; it only defers the native
      probe until a value-class-typed field exists. Make `select-enforcement`
      take a capability thunk (or add one `runtime` function, for example
      `select-enforcement [specs]`) that probes only when a value-class field
      exists, and call it once. Keep definition errors before the native probe;
      update `fields_test` and `field_eligibility_test`.
      Done: `select-enforcement` takes a capability thunk and calls it only
      when a value-class spec exists; `defvaluetype` calls it once. Tests pass
      thunks; `portable-specifications-test` checks that no probe occurs.
- [x] Bind the enabled field names once in `identity-backend/rewrite`.
      `(set (keys types))` is computed in `visitEnd` (twice, with the `seen`
      check) and in `visitInsn` at each `RETURN`. Bind `field-names` with
      `seen`. Name the `"Ljava/lang/Object;"` descriptor (used in `visitField`
      and `visitFieldInsn`) once, for example `(Type/getDescriptor Object)`.
      Done: `field-names` bound with `seen`; private `object-descriptor`.
- [x] Name the `java.util.Objects` ClassDesc in `transform`. `constructor-code`
      calls `(ClassDesc/of "java.util.Objects")` for each generated field.
      Add a private constant next to `require-non-null` and use it.
      Done: private `objects-class`.

## Code shape review follow-ups (10)

- [x] Name the `Objects.requireNonNull` owner and descriptor in
      `identity-backend`, as review (9) did for `transform`. `visitFieldInsn`
      (line 99) inlines `"java/util/Objects"` and
      `"(Ljava/lang/Object;)Ljava/lang/Object;"`. Add private constants next to
      `object-descriptor` (for example `(Type/getInternalName Objects)` and
      `(Type/getMethodDescriptor (Type/getType Object) (into-array Type [(Type/getType Object)]))`)
      and use them.
      Done: private `objects-owner` and `require-non-null-descriptor`.
- [x] Extract the method visitor from `identity-backend/rewrite`. The function
      is about 90 lines with two nested proxies: class checks (`visitEnd`,
      `visitField`) and constructor checks and field-reference rewriting
      (`visitMethod` proxy). Move the `MethodVisitor` proxy to one private
      function (for example `method-visitor [delegate owner types owner? method
      desc constructor? unsupported!]`) and call it from `visitMethod`. Keep
      the messages, ex-data and check order.
      Done: private `method-visitor`; `visitMethod` counts constructors and
      calls it.
- [x] Resolve `loader/install!` at one site in `core`. `install-receiving!`
      (line 100) and the ordinary value branch of `defvaluetype` (line 255) each
      call `(requiring-resolve 'value-type.impl.loader/install!)`. The lazy
      resolution is a design constraint (`core` must not eagerly require
      `loader`). Add one private function (for example `install-capture!
      [class-name specifications representation]`) that returns the install
      result; call it from `install-receiving!` and `defvaluetype`.
      Done: private `install-capture!`.

## Test review follow-ups (17)

- [x] Label and complete `function_redefinition_test/repeated-function-definition-test`.
      It replaces `runtime/field-capability` with `with-redefs`, but its name and
      `testing` label (`[policy execution]`) do not identify it as
      controlled-capability evidence (acceptance 8). It defines the same-name
      owner twice under controlled absence, which the
      [controlled-capability audit](controlled-capability-audit.md) excludes, but it
      checks only distinct classes, stored values and field types. It does not show
      that each redefined owner keeps value representation and generated checks.
      Return `value?` and the nil and wrong-type construction outcomes of each
      owner (`consumer-aot-checks/builders`, `outcome`), and compare them in the
      parent. The `[macroexpand-1 macroexpand]` pair is the residue that review (4)
      removed elsewhere; evaluate the same declaration twice. Label the test as
      controlled capability and record it in the audit.
      Done: renamed `controlled-capability-repeated-definition-test`; label
      `[:controlled-capability policy execution]`; same declaration evaluated
      twice; each owner returns `value?`, stored value, type and nil/wrong-type
      outcomes of all three entries; recorded in the audit.

## Test review follow-ups (18)

- [x] Complete the storage check in
      `function_redefinition_test/controlled-capability-repeated-definition-test`.
      The [controlled-capability audit](controlled-capability-audit.md) row
      "Same-name redefinition" claims final Long storage, but the test checks only
      the field type. It also does not check that the field has no native
      annotations. Native enforcement also throws NullPointerException and
      ClassCastException, so the current outcomes do not show generated checks.
      Add `:final?` (`Modifier/isFinal`) and `:annotations`
      (`consumer-aot-checks/annotation-names`) to each `:check`, as
      `controlled_field_capability_test` does. Expect `true` and `[]` for the
      defvaluetype owner.
      Done: each `:check` returns `:final?` and `:annotations`; both owners
      expect `true` and `[]`. Audit row updated.

## Documentation review follow-ups (9)

- [x] Document the second controlled-capability test. Test review follow-ups
      (17) relabelled
      `function_redefinition_test/controlled-capability-repeated-definition-test`
      as controlled-capability evidence (the audit row "Same-name
      redefinition"). The user-facing docs still name only
      `value-type.controlled-field-capability-test`:
      - `doc/development/runtime.md`, "Run the controlled-capability check
        separately": add `--namespace value-type.function-redefinition-test`
        to the command.
      - `ramora/non-nil-field-design/embodiment.md`, controlled-capability
        paragraph: name the redefinition test and add same-name redefinition
        to the covered cases.
      Run `bb vale`.
      Done: command runs both namespaces; embodiment names the redefinition
      test and same-name redefinition. `bb vale` passes.
- [x] Make `ramora/defvaluetype-design/embodiment/runtime.md` match the
      code shape changes:
      - Data flow, line 129: `loader: bytes' := transform/->value-class bytes`
        uses the removed one-argument arity. Show the storage and delegate
        arguments that `value-backend/prepare` supplies, as line 119 shows
        `(loader/install! "ns.Name" [] :value)`.
      - Internal interfaces, line 83 (107 columns): wrap the
        `install!` specifications comment near 80 columns like the rest
        of the block.
      Run `bb vale`.
      Done: data flow shows `->value-class bytes storage delegate` with a
      `value-backend/prepare` note; specification comment wrapped near 64
      columns. `bb vale` passes.

## Code shape review follow-ups (11)

- [x] Validate receiving modes at one site. `core/identity-field-deftype`
      checks `(every? #{:generated} modes)` and calls the public
      `fields/invalid-enforcement!`; `fields/receiving-specifications` then
      checks the vector shape and `#{:native :generated}` again. Give
      `receiving-specifications` the permitted modes (for example `#{:generated}`
      for identity, `#{:native :generated}` for value) and name them in the one
      message. Remove the check from the macro; make `invalid-enforcement!`
      private. Keep `:invalid-field-enforcement`, `:class-name` and `:modes`.
      Add or update a test for identity `[:native]` modes.
      Done: `receiving-specifications` takes `permitted` and checks modes
      before tag resolution; the message names the permitted modes.
      `invalid-enforcement!` is private; the macro check is removed.
      `fields_test` adds an identity `[:native]` case.
- [x] Remove `owner?` from `identity-backend/method-visitor`. Its only use,
      `(and owner? (= method "<init>"))` in `visitFieldInsn`, is equal to
      `constructor?` there: a matched `concrete` type means `types` is not
      empty. Use `constructor?`; remove the parameter and the argument in
      `rewrite`. Keep the messages and ex-data.
      Done: `visitFieldInsn` uses `constructor?`; parameter removed.

## Code shape review follow-ups (12)

- [x] Find the enabled fields once in `fields/receiving-specifications`. It
      calls `enabled-fields` for the mode count, then `specifications` calls
      `enabled-fields` again, so the `:non-nil` metadata is validated twice.
      Add one private function (for example `field-specifications
      [defining-ns class-name enabled]`) that resolves and validates a vector of
      enabled field symbols. `specifications` calls it with `(enabled-fields
      ...)`; `receiving-specifications` binds `enabled` once, checks the modes
      with `(count enabled)` before tag resolution, then calls it with `enabled`.
      Keep the messages, ex-data and check order.
      Done: private `field-specifications`; `receiving-specifications` binds
      `enabled` once.
- [x] Correct the `identity-backend/prepare` docstring: "`core/identity-field-deftype`
      rejects other enforcement modes." Since review (11),
      `fields/receiving-specifications` rejects them (permitted `#{:generated}`).
      Done.
- [x] Use one test for native enforcement. `value-backend/prepare` sets
      `:native? (not= :generated enforcement)`, and the `loader/install!`
      docstring says "other modes retain native enforcement". `core` and
      `fields` test `(= :native ...)`, and modes are now validated as
      `:native` or `:generated`. Use `(= :native enforcement)` in
      `value-backend/prepare`; in the `install!` docstring, say that
      `:native` selects native enforcement. Run `bb api-docs --check`.
      Done: `bb api-docs --check` passes.

## Test review follow-ups (21)

- [x] Do not assert the permitted-mode wording in `fields_test`
      (`receiving-specifications` invalid modes). Code shape review (11) added
      the fragments `":generated or :native"` and `":generated mode"`. They fix
      the join word, the sort order and the word "mode", which the design states
      are not contractual (test review (13)). Assert each permitted mode as a
      separate fragment: `":generated"` and `":native"` for value output,
      `":generated"` for identity output (the rejected `[:native]` already
      contains `":native"`).
      Done.

## Test review follow-ups (22)

- [x] Test that invalid modes are rejected before tag resolution. The
      `fields/receiving-specifications` docstring states this order, and code
      shape review (12) kept it, but each enabled tag in
      `fields_test/receiving-specifications-test` and
      `identity_receiving_test/receiving-identity-native-modes-test` resolves
      (`Date`, `String`, `Child`). The comment of the identity test claims
      "before tag resolution", but the test does not show it. In
      `receiving-specifications-test`, add an enabled field with an unresolved
      tag (for example `^{:tag Missing :non-nil true}`) and invalid modes for
      both permitted sets. Expect `:invalid-field-enforcement`, not
      `:invalid-field` `:unresolved-tag`.
      Done: `receiving-specifications-test` adds an unresolved enabled tag
      with invalid modes for both permitted sets (expects
      `:invalid-field-enforcement`) and a valid-mode control (expects
      `:invalid-field` `:unresolved-tag`).

## Documentation review follow-ups (11)

- [x] Match the `install!` comment in
      `ramora/defvaluetype-design/embodiment/runtime.md` (Internal interfaces,
      lines 84–85) to the code. It says ":generated selects constructor checks;
      other modes retain native enforcement." Code shape review (12) changed the
      `loader/install!` docstring and `value-backend/prepare` to
      `(= :native enforcement)`. Say ":native selects native enforcement".
      Run `bb vale`.
      Done: comment says ":native selects native enforcement". `bb vale`
      passes.

## Code shape review follow-ups (13)

- [x] Test `storage-type` once in `transform/constructor-code`. The loop body
      tests it three times: for `requireNonNull`, for `checkcast` and for the
      `putfield` descriptor. `native?` has a meaning only with storage. Use one
      `when storage-type` block that emits `requireNonNull` when not `native?`,
      then `checkcast`. Keep the instruction order and the `putfield`
      descriptor.
      Done: one `when storage-type` block; `requireNonNull` under `when-not
      native?`, then `checkcast`. Order and `putfield` descriptor unchanged.

## Code shape review follow-ups (14)

- [x] Share the `:not-installed` cause text in `core`. `error-message`
      (`:not-installed`) and `not-installed-message` each give the literal
      "The defvaluetype form was macroexpanded outside compilation (no
      Compiler/LOADER binding)", as "fixed fallback representation" was
      repeated before review (1). Add one private constant (for example
      `macroexpanded-outside-compilation`) and use it in both messages. Keep
      the messages, ex-data and `fields/direct-fix`. Run `bb api-docs --check`.
      Done: private `macroexpanded-outside-compilation` used by both
      messages. Message text, ex-data and `fields/direct-fix` unchanged.
      `doc/API.md` regenerated (source line link only); `bb api-docs --check`
      passes.

## Code shape review follow-ups (15)

- [x] Use imported class names in the task namespaces, as the other task
      namespaces do. `fields/resolve-tag` writes
      `clojure.lang.Compiler$HostExpr` three times and
      `fields/validate-concrete-type` writes `java.lang.reflect.Modifier`,
      but `fields` imports only `(clojure.lang Compiler)`. Import
      `Compiler$HostExpr` and `java.lang.reflect.Modifier`; use the short
      names. `capability/probe` hints `^java.lang.reflect.Method`, but the
      task added the `Method` import for `value-class?`; hint `^Method`.
      Keep behavior. Run `bb clj-kondo:lint` and `bb clj-fmt:check`.
      Done: `fields` imports `Compiler$HostExpr` and `Modifier` and uses the
      short names; `capability/probe` hints `^Method`. Lint, format and the
      runtime suite (EA3, preview, exports) pass.
