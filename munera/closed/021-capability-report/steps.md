# Steps: Public JVM capability report

## Slice 1: Owner accessors

- [x] In `impl.capability`, add a private delay `detected-capability` over
      `detect` and a public accessor `capability` that derefs it.
- [x] In `impl.field-capability`, add a private delay `detected-field-capability`
      and a public accessor `capability`; make the field probe read its
      value-class prerequisite through the var `impl.capability/capability`
      instead of calling `capability/detect`.
- [x] In `impl.array`, rename the private delays to
      `detected-allocation-capability` and `detected-inspection-capability`;
      add public accessors `allocation-capability` and
      `inspection-capability` over them; make `require-allocation` and
      `require-inspection` read the accessors; make allocation discovery
      read its prerequisite through `impl.capability/capability`.
- [x] Do not change `impl.runtime`, `value-type.core` or `test/`. Until
      Slices 2 and 3, the `impl.runtime` delays still call the probes, so a
      runtime can compute a result twice; the results are equal.
- [x] Confirm that every read of the four capability-result delays
      (`detected-capability`, `detected-field-capability`,
      `detected-allocation-capability`, `detected-inspection-capability`)
      goes through the owner accessor var, so `with-redefs` replaces the
      result for every reader. Only the accessor of the owner derefs its
      delay. The check does not cover `impl.capability/value-predicate`
      (a public Method delay, not a capability result, with no accessor):
      the derefs `@capability/value-predicate` in `impl.array/discover`
      (line 16) and `value-type.core` `no-value-classes?` (line 22) stay
      unchanged.
- [x] Run the code gate (plan "Test gates"): runtime suite, fallback
      suite on its three parents and cross-JVM suite (array matrices and
      field probe); commit.

## Slice 2: Value-class reader switch

- [x] Find every value-class reader under any alias (`runtime/`, `r/`
      or the full name) with the value-class grep:
      `grep -rlE 'impl\.runtime' src test | xargs grep -nE '/capability([^-]|$)'`.
      The first grep lists every file that loads `impl.runtime`; the
      second finds the accessor calls in them, also in quoted child forms.
      List all hits (the design list may be incomplete).
- [x] Switch `value-type.core` (`runtime/capability` at `core.clj` lines 29
      and 220) to `capability/capability`.
- [x] Migrate the `test/` readers to `impl.capability/capability`:
      `expansion_checks`, `field_policy_checks`, `field_transfer_checks`,
      `identity_receiving_checks`, `runtime_test` (three quoted child forms:
      `capability-test` lines 35–38, alias `r`, which keeps the `:cached`
      `identical?` check on two accessor calls; lines 107–109; lines
      302–303, alias `r`; the forms at lines 59 and 81 are policy checks
      and stay), `removed_field_test`, `deftype_star_test`,
      `deftype_star_record_test` (line 67).
- [x] Delete the value-class delay and the `capability` accessor from
      `impl.runtime`; remove its `impl.capability` require.
- [x] Run the code gate (plan "Test gates"); commit.

## Slice 3: Guard move and native-field reader switch

- [x] Find every native-field reader under any alias with the
      native-field grep:
      `grep -rlE 'impl\.runtime' src test | xargs grep -nE '/field-capability([^-]|$)|require-field-capability!|detected-field-capability'`.
      Run the value-class grep (Slice 2) again. List all hits.
- [x] Move `require-field-capability!` (same message, ex-data
      `:value-type/error :field-capability-unavailable`, `:class-name`,
      `:reason`, same check order) from `impl.runtime` to
      `impl.field-capability`, reading `impl.field-capability/capability`.
- [x] In `value-type.core`: add `impl.field-capability` to the requires;
      switch the reader at line 224 to `impl.field-capability/capability`;
      switch the guard call and emission at lines 189 and 192 to
      `value-type.impl.field-capability/require-field-capability!`.
- [x] Update `cell_harness/controlled-absence` to load
      `impl.field-capability` and redef `impl.field-capability/capability`;
      update its docstring. Its callers (`controlled_field_capability_test`,
      `function_redefinition_test`) need no change.
- [x] Migrate `deftype_star_record_test` (line 183) to
      `impl.field-capability/capability`.
- [x] Migrate cross-JVM `field_capability_test`
      (`test/cross-jvm/value_type/field_capability_test.clj`):
  - `capability-matrix-test` (lines 27–32): require `value-type.core` and
    `value-type.impl.field-capability`; read the owner delay
    `impl.field-capability/detected-field-capability` and the owner
    accessor `impl.field-capability/capability`.
  - `required-capability-policy-matrix-test` (lines 85–92): the child
    form requires `value-type.core` (not `impl.field-capability` only),
    then calls `value-type.impl.field-capability/require-field-capability!`.
    `value-type.core` loads `impl.runtime` (policy) and
    `impl.field-capability` (guard), as an expansion or AOT artifact does,
    and reads no capability result. So each child initializes its policy,
    and the policy axis keeps its evidence: the guard never warns, falls
    back or succeeds on retry under each policy. Keep the test comment
    (line 72) and the expected values.
- [x] Migrate the `non_nil_fields_test` delay read to
      `impl.field-capability/detected-field-capability`.
- [x] There is no checked-in AOT output; AOT tests compile in the test
      run. Confirm that no fixture embeds the old guard symbol.
- [x] Delete the field delay, the `field-capability` accessor and the old
      guard from `impl.runtime`; remove its `impl.field-capability` require.
- [x] Run the code gate (plan "Test gates"; the cross-JVM suite includes
      `controlled-field-capability-test`); commit.

## Slice 4: Runtime cleanup check (Acceptance 5)

- [x] Update the `impl.runtime` namespace docstring: policy, warning state
      and fallback guards; no capability cache.
- [x] Run the value-class grep (Slice 2) and the native-field grep
      (Slice 3). Verify that each hit resolves to `impl.capability` or
      `impl.field-capability` (read the alias of each hit), so no file in
      `src/` or `test/` reads capability or calls the guard through
      `impl.runtime`.
- [x] Verify the require graph matches the design edge list and has no
      cycles.
- [x] Run the full gate (plan "Test gates": the five Acceptance 6
      suites); all migrated tests pass with the same evidence; commit.

## Slice 5: Public facade

- [x] Create `src/value_type/capability.clj` requiring only
      `impl.capability`, `impl.field-capability`, `impl.array`.
- [x] Implement `report`: no args; map of `:value-class`,
      `:native-non-nil-field`, `:array-allocation`, `:array-inspection` to
      `select-keys` of each accessor result over `[:available? :reason]`;
      probe defects propagate, no partial report.
- [x] Write the `report` docstring per Contract (keys, reasons, EDN-printable).
- [x] Verify loading the namespace and calling `report` never reads
      `value-type.fallback` or loads `impl.runtime`, and loads on Java 8
      without preview/export/classfile API.
- [x] Commit.

## Slice 6: New tests

- [x] Create `test/cross-jvm/value_type/capability_matrix_test.clj` under the
      `:cross-jvm-test` alias (Java 8 parent, pattern of
      `array_matrix_test.clj`): 6 runtime labels × 3 policies × 3 require
      orders (rotations of capability/core/array), one child JVM per cell.
- [x] Per cell, first require `value-type.capability`, `value-type.core`
      and `value-type.array` in the rotation order of the cell (pattern:
      `array_matrix_checks/check`, `(run! require require-order)`; pass the
      order as a quoted argument).
- [x] Per cell: assert the report equals the Contract table row. In the
      order that starts with `value-type.capability`, call `report` after
      all three requires and before the first definition or array call.
      In the other two orders, make the Acceptance 3 definition and array
      calls, then call `report`. In all orders, the Acceptance 3
      comparisons (next item) run after the first `report` call. Call
      `report` again last and assert equality.
- [x] Per cell, Acceptance 3 agreement checks: ordinary `defvaluetype`
      outcome vs `:value-class`; `:non-nil`-field annotation presence vs
      `:native-non-nil-field` (skip when `:value-class` unavailable under
      `error`); `make-array` vs `:array-allocation` reason; `array-info` vs
      `:array-inspection` reason; contract identity vs owner accessor
      `select-keys`.
- [x] Add the Acceptance 2 `deftest` to
      `test/compatibility/value_type/runtime_test.clj`: one child per invalid
      policy (`""`, `"WARN"`, `"unknown"`), child loads
      `value-type.capability`, calls `report`, asserts the Contract row for
      the parent's label and that `find-ns` returns nil for
      `value-type.core` and `value-type.impl.runtime`.
- [x] Create `test/cross-jvm/value_type/controlled_value_capability_test.clj`:
      EA3 `:export` child under `silent`; `with-redefs` on
      `impl.capability/capability` before any capability read; assert report
      values, `make-array` `:array-unavailable` reason, identity-class
      `defvaluetype`, and `:array-inspection` still available.
- [x] Extend `build_test/isolated-consumer-test`: load `value-type.capability`
      from the packaged JAR (resource-origin check), call `report` in the
      four runtimes, assert the Contract rows (Acceptance 8).
- [x] Run the full gate (plan "Test gates"; it includes the new tests:
      matrix and controlled test in the cross-JVM suite, Acceptance 2 in the
      fallback suite, Acceptance 8 in the build suite); record commands,
      results and limits in `implementation.md`; commit.

## Slice 7: Documentation and quality gates

- [x] Add `src/value_type/capability.clj` to `quickdoc-options`
      `:source-paths` in `bb/value_type/api_docs.clj`; run `bb api-docs`.
- [x] Add `value-type.capability/report` to `public-reference-test` in
      `test/tooling/value_type/api_docs_test.clj`.
- [x] Add a "Capability report" section to `doc/usage.md`.
- [x] In `doc/development/runtime.md`: add "Verify the capability report"
      after "Verify array diagnostics" with the two `:cross-jvm-test`
      commands (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"`); add the capability
      matrix to the `test/cross-jvm/` row of the test-layout table.
- [x] Add the `CHANGELOG.md` entry; update `mementum/state.md`.
- [x] Update `ramora/defvaluetype-design/fallback.md`: D12 "Boundary" names
      `value-type.core` and `value-type.capability`; "Verification" item
      renamed per design; D13 "Choice"/"Rationale" state the cache owners.
- [x] Update A-34 in
      `ramora/defvaluetype-design/assertions/fallback-architecture.md` with
      the new owners.
- [x] Update array D3 to name the report as a reader of adapter results.
- [x] Update `ramora/non-nil-field-design/embodiment.md` "Implementation"
      with the owner, prerequisite read and guard ownership text.
- [x] Update `embodiment/runtime.md` (component table and dependency
      direction), `embodiment/runtime/fallback-interfaces.md` and
      `decisions/validation-and-errors.md` (cache owner) for the owners and
      guard move.
- [x] Update `embodiment/artifacts.md`:
  - the source list ("Project artifacts" `src/` entries, lines 20–23):
    add `src/value_type/capability.clj` and
    `src/value_type/impl/field_capability.clj`; the entry for
    `impl/capability.clj` and `impl/runtime.clj` states that the probe
    namespaces own the capability results and `impl.runtime` owns policy;
  - the `test/cross-jvm/` entry, which names the matrices: add the
    capability matrix;
  - the "Development quality workflow" paragraph: name
    `src/value_type/capability.clj` as an API doc source and
    `value-type.capability/report` as a documented function;
  - the "Isolated source-JAR consumers passed in four configurations"
    results: add the `report` check and the `value-type.capability`
    resource origin (Acceptance 8).
- [x] Add D14 "Public capability report" to
      `ramora/defvaluetype-design/decisions.md`; create
      `ramora/defvaluetype-design/decisions/capability-report.md`; link it
      from `ramora/INDEX.md`.
- [x] Run `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint`,
      `bb vale` and the full gate (plan "Test gates"); record evidence in
      `implementation.md`; commit.

## Implementation review follow-ups

- [x] In `test/tooling/value_type/api_docs_test.clj` (`public-reference-test`),
      change the `testing` text "only the public macro and array functions
      are documented". The list now includes `value-type.capability/report`,
      so the text is false.
- [x] In `ramora/defvaluetype-design/embodiment/artifacts.md`, "Development
      quality workflow" paragraph: correct the function list (now "the
      `make-array`, `into-array` and `array-info` and `report` functions",
      with two "and"), and rewrap the edited lines of that paragraph and of
      the `test/cross-jvm/` entry to the file's line width.
- [x] In `ramora/defvaluetype-design/embodiment/artifacts.md`, isolated
      source-JAR consumer results (the paragraph after "All three
      unsupported-allocation configurations"): rewrap the edited lines
      (one is 113 columns) and fix the resource list "the core, both array
      and the `value-type.capability` source resources" (for example "the
      core, both array and the capability source resources").

## Implementation review follow-ups, pass 3

- [x] In the `value-type.capability/report` docstring
      (`src/value_type/capability.clj`, "Each value equals the cached
      result ...") and in `doc/usage.md` "Capability report" ("Each value
      is the same cached result ..."), state the Contract: each value
      equals the `:available?` and `:reason` entries of the cached result.
      The cached results also hold `:detail`, `:cause` or Methods, so the
      current text is false. Run `bb api-docs` to regenerate `doc/API.md`.
- [x] `mementum/knowledge/controlled-fallback-boundaries.md` (lines
      124–128) names the removed vars `runtime/detected-field-capability`
      and `runtime/field-capability`. Change them to the owner names
      `impl.field-capability/detected-field-capability` and
      `impl.field-capability/capability`.
- [x] In `test/runtime/value_type/deftype_star_record_test.clj`, move the
      new `[value-type.impl.field-capability :as field-capability]`
      require before `value-type.impl.fields`, so the `value-type.impl.*`
      requires stay in alphabetical order.

## Implementation review follow-ups, pass 4

- [x] Rewrap the lines that the pass-3 follow-ups made too long, to the
      line width of each file:
  - `src/value_type/capability.clj` line 27 (`report` docstring, 118
    columns); then run `bb api-docs` to regenerate `doc/API.md`.
  - `doc/usage.md` line 336 ("Capability report", 116 columns).
  - `mementum/knowledge/controlled-fallback-boundaries.md` line 129
    (84 columns).
- [x] `mementum/state.md` says that `report` "returns the four cached
      capability results". The cached results also hold `:detail`,
      `:cause` or Methods, so the text is false (same issue as the pass-3
      docstring step). State that `report` returns the `:available?` and
      `:reason` entries of the four cached capability results.

## Implementation review follow-ups, pass 5

- [x] Rewrap the ramora prose lines that this task edited and that exceed
      the paragraph width of each file (about 78 columns). Table rows stay
      as they are:
  - `ramora/defvaluetype-design/decisions/validation-and-errors.md`
    line 50 ("caches the result (`capability`); ...", 88 columns).
  - `ramora/defvaluetype-design/fallback.md` line 107 (D13 "Rationale",
    82 columns).
  - `ramora/defvaluetype-design/embodiment/runtime.md`: the "Dependency
    direction" paragraph (line 84 is 84 columns; the line that ends
    "Only the available" is short) and the "Core reads the owner
    capability accessors ..." line (82 columns).

## Implementation review follow-ups, pass 6

- [x] In `doc/development/runtime.md` "Verify the capability report"
      (lines 436–438), correct the controlled-check text. It says that
      "the report, allocation, definitions and inspection all read the
      replaced result". Inspection does not read the value-class result;
      the test checks that `:array-inspection` stays available. State
      that the report (`:value-class`, `:native-non-nil-field`,
      `:array-allocation`), `make-array` and the definition read the
      replaced result, and that `:array-inspection` stays available.

## Implementation review follow-ups, pass 7

- [x] In `src/value_type/impl/array.clj`, make the docstrings of
      `allocation-capability` and `inspection-capability` follow the
      owner-accessor pattern of `impl.capability/capability` and
      `impl.field-capability/capability`: add "Every reader reads through
      this var; tests replace it with `with-redefs`." All four accessors
      are the replacement point (design "Accessors", D14 "Ownership").
- [x] In `test/compatibility/value_type/cell_harness.clj`, rewrap the
      `controlled-absence` docstring. The edited first line ("Return a
      form that loads value-type.impl.field-capability and the namespaces
      of") is 82 columns; the other docstring lines are at most 78.

## Implementation review follow-ups, pass 8

- [x] In `test/compatibility/value_type/capability_matrix_checks.clj`,
      `definition` classifies the strict policy rejection in the child
      with a new local `policy-rejection?`. It copies the literal
      `cell-harness/warning-prefix` and does not check the cause chain,
      the context or that `CapPoint` stays absent. Use the existing
      pattern of the other child checks: on a throw, return
      `registry/failed-definition` (with `registry/thrown`) for
      `CapPoint`, and in `capability_matrix_test` `expected` give the
      `error` policy rows with `harness/failed-definition` and
      `harness/rejection-context` (as `runtime_test` lines 126 and 370
      do). Use `harness/keyed` and `harness/fixed` for the other keys, so
      the cell result stays one described expected value. Then remove
      `policy-rejection?` and its prefix literal.

## Test review follow-ups

- [x] In `test/compatibility/value_type/capability_matrix_checks.clj`,
      `check` returns `:identity?` (a boolean from
      `(= first-report (owner-report))`). On a failure, the cell gives only
      "expected true, actual false" and does not show the owner value or
      the key that differs. Return the owner report (for example
      `:owner (owner-report)`), and in `capability_matrix_test/expected`
      give it as `(harness/fixed report)`. Then a failure shows the owner
      accessor result next to the Contract row. Update the `check`
      docstring and the outcome encoding note if it names `:identity?`.

## Test review follow-ups, pass 3

- [x] In `test/tooling/value_type/api_docs_test.clj` (`public-reference-test`),
      the `testing` text "capability report keys and reasons remain
      explicit" names reasons, but the list checks no reason keyword. If
      the reason list leaves the `report` docstring, the test still
      passes. Add the reason keywords of the Contract
      (`:missing-classfile-api`, `:missing-value-predicate`,
      `:unsupported-class-version`; the value-class reasons occur in
      `doc/API.md` only in `report`), so the test checks the public
      reason contract.

## Test review follow-ups, pass 4

- [x] In `test/compatibility/value_type/runtime_test.clj`, the quoted
      child forms of `capability-test` (line 36) and
      `fallback-source-compatibility-test` (line 303) require
      `value-type.impl.capability` with the alias `r`. `r` reads as
      `impl.runtime`, and every other test file aliases
      `impl.capability` as `capability`. Change the alias in both forms
      to `capability` (`capability/capability`).
- [x] In `test/tooling/value_type/api_docs_test.clj`
      (`public-reference-test`, "capability report keys and reasons
      remain explicit"), the text "does not read the" also matches a
      docstring that names no policy. Check the full sentence
      "does not read the `value-type.fallback` policy" (`doc/API.md`
      line 138), so the test checks the policy-independence contract.

## Test review follow-ups, pass 5

- [x] No test checks the Contract "a probe defect propagates and no
      partial report is returned", and the default runtime suite (`:test`)
      has no test of `value-type.capability/report`: all report evidence
      is in child-JVM tests (fallback, cross-JVM, build suites). Add a
      runtime-suite namespace, for example
      `test/runtime/value_type/capability_test.clj`, with:
  - one `deftest` for translation: `with-redefs` of the four owner
    accessors to results that also hold `:detail`, `:cause` or a Method;
    assert that `report` gives exactly the four keys, each with only the
    `:available?` and `:reason` entries;
  - one `deftest` for defect propagation: `with-redefs` of one owner
    accessor (for example `impl.array/inspection-capability`) to a
    function that throws; assert that `report` throws the identical
    Throwable.

## Docs review follow-ups

- [x] `README.md` does not name the capability report. The "Documentation"
      entry says the API reference has "macro syntax and array function
      contracts"; it now also has `value-type.capability/report`. Update
      that entry, and add one sentence (for example under "JDK
      requirement") that links to
      [Capability report](doc/usage.md#capability-report), so that users
      can find how to check a deployment.
- [x] `doc/development/runtime.md` "Run the build tests" describes each
      isolated consumer check, but not the Acceptance 8 check: each
      consumer loads `value-type.capability` from the JAR and its `report`
      equals the expected row (`:java8`, `:no-options`, `:export-only`,
      `:export`). Add this to the paragraph.
- [x] `doc/development/runtime.md` "Verify array allocation" (line 311)
      says "both public namespace require orders". There are now three
      public namespaces, and the capability matrix uses three orders.
      Name the two namespaces (`value-type.core` and `value-type.array`)
      so that the text is not ambiguous.

## Docs review follow-ups, pass 2

- [x] D14 "Commitment" in
      `ramora/defvaluetype-design/decisions/capability-report.md` lists the
      tests that verify the report, but not the runtime-suite
      `value-type.capability-test` (`report-translation-test`,
      `report-defect-propagation-test`). That namespace is the evidence
      for "contains no Methods, Classes or Throwables" and for the
      "Failure" entry. Add it to the list.
- [x] `doc/development/runtime.md` paragraphs edited in the docs
      follow-ups are not wrapped. "Run the build tests" has a 124-column
      line ("storage and direct/factory constructor checks on Java 8 ...");
      "Verify array allocation" has a short line ("preview or exports.
      Unsupported configurations"). Rewrap the edited lines to 78 columns.

## Docs review follow-ups, pass 3

- [x] D14 "Ownership" in
      `ramora/defvaluetype-design/decisions/capability-report.md` says
      "`impl.runtime` owns only the initialized policy and the warning
      flag (D13)". `impl.runtime` also owns the fallback guards
      (`enforce-fallback!`, `guarded-deftype`, `guarded-deftype*`), as the
      design Scope, the `impl.runtime` docstring and the
      `embodiment/runtime.md` namespace table state. Add the fallback
      guards to the sentence, so that D14 agrees with them.

## Docs review follow-ups, pass 4

- [x] `mementum/state.md` says "`impl.runtime` owns only policy and the
      warning flag", and `ramora/defvaluetype-design/embodiment/artifacts.md`
      (source list, lines 25–27) says "`impl.runtime` owns the policy and
      the warning flag". `impl.runtime` also owns the fallback guards
      (`enforce-fallback!`, `guarded-deftype`, `guarded-deftype*`), as D14
      "Ownership" and the `embodiment/runtime.md` namespace table state.
      Add the fallback guards to both sentences, so that they agree with
      D14. Wrap the edited lines to 78 columns.

## Docs review follow-ups, pass 5

- [x] `ramora/defvaluetype-design/embodiment/artifacts.md` "Development
      quality workflow" paragraph (lines 56–63) lists the documented vars
      as the `value-type.core/defvaluetype` macro and the array and
      capability functions. `doc/API.md` also documents the
      `value-type.core/defvaluetype*` macro, and `public-reference-test`
      lists it. Name both macros, so that the list agrees with the
      generated reference. Wrap the edited lines to 78 columns.

## Docs review follow-ups, pass 6

- [x] `doc/usage.md` "Portable non-nil fields" (line 387) says "Requiring
      the public namespace is safe on Java 8 and preview-disabled EA3".
      There are now three public namespaces, and the docs follow-ups made
      the same change in `doc/development/runtime.md` "Verify array
      allocation". Name `value-type.core`, so that the text is not
      ambiguous. Wrap the edited lines to the paragraph width.
- [x] `doc/development/runtime.md` "Verify the capability report" does not
      link to the public contract. Each other "Verify ..." section for a
      public function ends with "See the [... guide](../usage.md#...) for
      the public contract." Add "See the
      [capability report guide](../usage.md#capability-report) for the
      public contract."

## Code-shaper review follow-ups

- [x] Make the aliases of the new code agree with the project aliases.
      `src/value_type/capability.clj` and
      `test/runtime/value_type/capability_test.clj` alias
      `value-type.impl.array` as `array`. `src/value_type/array.clj` and
      `array_test` alias it as `adapter`, and the runtime tests use
      `array` for the public `value-type.array`. `capability_test` also
      aliases `value-type.impl.capability` as `value-capability`, but all
      other files alias it as `capability` (test review pass 4 set this);
      there, `capability` is the public `value-type.capability`. In both
      files, alias `impl.array` as `adapter`. In `capability_test`, alias
      `impl.capability` as `capability` and refer the public function
      (for example `[value-type.capability :refer [report]]`).

## Code-shaper review follow-ups, pass 2

- [x] Make the array error outcome shape of the capability matrix agree
      with the project shape. `capability_matrix_checks/outcome` returns
      a vector `[error reason]` (for example
      `[:array-unavailable :missing-array-api]`), but
      `controlled_value_capability_test`, `array_capability` and
      `unavailable_predicates_test` give the error as the ex-data map
      `{:value-type/error error :reason reason}`. Return
      `(select-keys (ex-data e) [:value-type/error :reason])` in
      `capability_matrix_checks`, and give the same map in
      `capability_matrix_test/expected`. Also rename the two private
      `outcome` functions (`capability_matrix_checks/outcome` runs an
      array call; `capability_matrix_test/outcome` builds an expected
      value); `capability_matrix_checks` also calls `registry/outcome`,
      which has a third meaning. Update the outcome encoding note in
      `implementation.md` only if a later entry restates it.

## Code-shaper review follow-ups, pass 3

- [x] In `test/runtime/value_type/capability_test.clj`
      (`report-translation-test`), make the replaced owner results use the
      real internal shapes. The test uses the keys `:value-predicate`
      (value class) and `:methods` (inspection), which no owner result
      has. The value-class probe gives `{:available? true}` or an
      unavailable result with `:detail`; `impl.array/discover` gives
      `:allocator` and `:predicate`; `discover-inspection` gives
      `:predicate`, `:null-restricted?`, `:atomic?` and `:flat?`. Use these
      keys (for example an available allocation result with `:allocator`
      and `:predicate`, and an available inspection result with the four
      Method keys), so the test shows that `report` removes the data that
      the owners hold.
- [x] In `test/compatibility/value_type/capability_matrix_checks.clj`
      `check`, `first?` (the order starts with `value-type.capability`)
      and `first-report` (the first `report` call) use "first" with two
      meanings. Rename `first?` (for example `report-first?`) and keep
      the docstring in agreement.

## Code-shaper review follow-ups, pass 4

- [x] Give the Contract allocation column one source.
      `test/compatibility/value_type/capability_rows.clj` (`reasons`) and
      `test/cross-jvm/value_type/array_capability.clj`
      (`allocation-reasons`) both map each runtime label to the
      allocation reason. If one table changes, the other does not.
      `array_capability` can load `test/compatibility` (it requires
      `cell-harness`), so derive the reason from the rows, for example
      `(:reason (:array-allocation (rows/report label)))`, and remove
      `allocation-reasons`. The `build_test/consumer-reports` copy stays
      (the build suite cannot load `test/compatibility`).

## Code-shaper review follow-ups, pass 5

- [x] Give the Contract inspection column one source, as pass 4 did for
      the allocation column. In
      `test/cross-jvm/value_type/array_info_boundary_test.clj`,
      `inspection-rows` maps each runtime label to `supported?` and the
      inspection reason, and `export-only-reason` repeats the
      `:export-only` allocation reason. Both copy
      `value-type.capability-rows`. Derive them from the rows, for example
      `(:array-inspection (rows/report label))` for `supported?` and
      `reason`, and `(:reason (:array-allocation (rows/report
      :export-only)))`. Keep only the cause class name per label in the
      local table (or derive it from the reason, as
      `array_capability` does for `:array-access-denied`).

## Code-shaper review follow-ups, pass 6

- [x] In `test/runtime/value_type/capability_test.clj`
      (`report-translation-test`), make the replaced owner results a state
      that the probes can give. The fixture gives `:value-class`
      `:unsupported-class-version`, `:native-non-nil-field`
      `:missing-classfile-api` and `:array-allocation` available. The
      field and allocation probes return the value-class result when it
      is unavailable, and the Contract says that a field or allocation
      result can be available only when `:value-class` is available. Use
      two reachable states, for example the `:export-only` row (the same
      unavailable value-class result with `:detail` for the first three
      keys; inspection available with the four Method keys) and the
      `:no-export` row (value class and field available; allocation and
      inspection unavailable `:array-access-denied` with `:cause`).
- [x] In `test/cross-jvm/value_type/capability_matrix_test.clj`
      `expected`, the destructuring names three of the four report keys,
      and the `:native` entry reads `(:native-non-nil-field report)`.
      Destructure `native-non-nil-field` with the other keys.
