# Design steps

- [x] Architecture: assign an owner for the native-field guard
  (`require-field-capability!`, error `:field-capability-unavailable`).
  It is neither policy nor warning state, so it must leave `impl.runtime`.
  Expansions and AOT artifacts emit calls to it. State the new owner, that
  `value-type.core` loads that owner eagerly, and that the error data and
  order do not change. Add the defvaluetype runtime embodiment
  (`embodiment/runtime.md` component table and dependency direction,
  `embodiment/runtime/fallback-interfaces.md`) to the Acceptance 7
  document updates.
- [x] Architecture: fallback Verification (D12) requires that public
  namespaces load from the packaged source JAR without optional APIs.
  Add an Acceptance item: the isolated-consumer build test loads
  `value-type.capability` from the JAR and calls `report` on a runtime
  without JEP 401 support.
- [x] Ambiguity: Acceptance 1 says "each require order" of three
  namespaces. State if this means all six permutations or only which
  namespace loads first. The current `array-capability/require-orders`
  axis lists all permutations of two namespaces, and each order is a
  one-cell child JVM. Six orders × five runtimes × three policies gives 90
  children. State also if `report` runs before or after the first
  definition or array call in each cell.
- [x] Ambiguity: define "identical" (Contract) and "agree" (Acceptance 3).
  Internal results carry `:detail`, `:cause` and Methods, so state that the
  comparison uses `:available?` and `:reason` only. Give the observable
  outcome for each key:
  - `:value-class`: the representation that `defvaluetype` selects.
  - `:native-non-nil-field`: how the test sees native enforcement, not
    generated enforcement.
  - `:array-allocation` and `:array-inspection`: success, or the
    `:array-unavailable` and `:array-inspection-unavailable` ex-data
    `:reason`.
- [x] Ambiguity: Acceptance 7 "short decision record for the report" does
  not give a location or an identifier. Name the file (a new ramora
  document, or a decision in an existing design such as the array or
  defvaluetype decisions) and its decision number.
- [x] Inconsistency: the Contract table claims to use the
  `array-capability/runtimes` labels. Acceptance 1 requires the table "for
  each runtime label". But `runtimes` has six labels, and the table omits
  `:no-flattening` (export plus `-XX:-UseArrayFlattening`). Add the
  `:no-flattening` row (expected: all available), or name the label subset
  that Acceptance 1 uses. The cell count in the require-order step changes
  to match.
- [x] Inconsistency: Qualities and risks says that controlled-capability
  tests "replace private cache vars in `impl.runtime`". They do not.
  `cell_harness/controlled-absence` uses `with-redefs` on the public
  accessor `impl.runtime/field-capability`. Other checks call
  `runtime/capability` (`expansion_checks`, `field_policy_checks`,
  `field_transfer_checks`, `identity_receiving_checks`, `runtime_test`), or
  deref the private `detected-field-capability` delay
  (`non_nil_fields_test`, `field_capability_test`). Correct the risk text.
  Name the replacement point that Acceptance 4 and 5 use, such as each
  owner's accessor var. Dependent probes must read the value-class result
  through that point, or the Acceptance 4 replacement has no effect on
  them.
- [x] Architecture: assertion A-34 in
  `ramora/defvaluetype-design/assertions/fallback-architecture.md` states
  that the capability/policy responsibility owns the cached capability
  (basis D13). After the owner move this is false: `impl.runtime` keeps
  only policy and the warning flag, and the probe namespaces own the
  caches. Acceptance 7 updates D13 but not A-34. Add A-34 to the
  Acceptance 7 document updates, so the assertion states the new owners.
- [x] Ambiguity: Acceptance 2 says only that the test "gets a report". It
  gives no pass criterion and no runtime. State the runtime label (or
  labels) of the child JVM. State the expected result, for example: the
  report equals the Contract row of that label, and the child does not
  load `value-type.core` or `impl.runtime` before `report` returns.
- [x] Ambiguity: Architecture "Dependencies" does not say if the edge list
  is complete. The current code also has `value-type.core →
  impl.fields` and `impl.fields → impl.capability`, which the list omits.
  State that the list gives only the capability-related edges, or list
  all edges between the named namespaces. Otherwise a reviewer can read
  the other requires as a violation of the design.
- [x] Inconsistency: the Qualities and risks list of tests that read
  capability through `impl.runtime` is incomplete. These tests also call
  `runtime/capability` or `runtime/field-capability`:
  `removed_field_test`, `deftype_star_test`, `deftype_star_record_test`.
  `field_capability_test` also calls `runtime/field-capability` and
  `runtime/require-field-capability!` (the guard that moves). Add them to
  the list, or state that the list gives examples and that Acceptance 5
  covers every reader in `test/`.
- [x] Architecture: `doc/API.md` is generated by Quickdoc from the
  `:source-paths` in `bb/value_type/api_docs.clj` (now only `core.clj` and
  `array.clj`). Do not edit the generated file. Acceptance 7 names
  `doc/API.md`, and Acceptance 6 needs `bb api-docs --check`, but the check
  passes when `report` is absent. Name the generator change in the design:
  add `src/value_type/capability.clj` to the `:source-paths`, add
  `value-type.capability/report` to the public var list in
  `test/tooling/value_type/api_docs_test.clj`, and update the
  `embodiment/artifacts.md` "Development quality workflow" paragraph that
  names the documented sources and functions.
- [x] Ambiguity: the design does not say where the new tests run.
  Acceptance 1 says only "Cross-JVM tests", Acceptance 4 says only "a
  controlled test", and Acceptance 2 names a suite but no namespace.
  `doc/development/runtime.md` gives a run command with `--namespace`
  lists for each feature (for example "Verify array allocation" runs
  `value-type.array-matrix-test` under `:cross-jvm-test`), but Acceptance 7
  does not list it. For Acceptance 1, 2 and 4, name the test namespace, or
  state that the test goes in an existing namespace (for example
  `array-matrix-test`, `runtime-test` or
  `controlled-field-capability-test`). Give its alias and parent JVM.
  State if `doc/development/runtime.md` gets a new or changed run command.
- [x] Ambiguity: two Acceptance 7 document updates name no target
  statement, and the current text has no cache-owner statement to change.
  - "Update fallback D12 ... for cache ownership": D12
    (`ramora/defvaluetype-design/fallback.md`) does not mention a cache or
    its owner. Only D13 ("Choice" and "Rationale") does. State the D12
    change (for example, the Boundary sentence "the public namespace ...
    must load" now covers three public namespaces), or remove D12 from
    the list.
  - "Update the non-nil embodiment for the field cache owner": name the
    file (`ramora/non-nil-field-design/embodiment.md`?). Its
    "Implementation" section says only "Native capability is probed
    lazily" and names no owner. State the text to add or change, or remove
    the item.
- [x] Ambiguity: Acceptance 7 says the fallback "Verification" item
  "Public namespace and packaged source JAR load without optional APIs"
  "covers `value-type.capability` (evidence: Acceptance 8)". It does not
  say if the item text changes. The D12 "Boundary" entry beside it gives
  an edit ("names the public namespaces ..."), but this entry gives no
  verb. State one: change the item to name the public namespaces (as in
  D12 "Boundary"), or keep the text unchanged and remove the entry from
  the update list, because the singular "Public namespace" already reads
  as a general rule.
- [x] Plan ambiguity: plan.md "Key decisions" says the require order "only
  changes when the cell calls `report` relative to the definition and
  array checks". One reading is that the cell requires the namespaces in
  a fixed order and the order sets only the `report` timing. That
  conflicts with Acceptance 1, where each rotation loads a different
  namespace first. The steps.md Slice 6 items name the axis but do not
  say that the cell requires the namespaces in that order. State that each
  cell first requires `value-type.capability`, `value-type.core` and
  `value-type.array` in its rotation order, as in the
  `array-capability/require-orders` cells. State that the order also sets
  the `report` timing. For the order that starts with
  `value-type.capability`, state if `report` runs before the other two
  requires or after all three.
- [x] Plan inconsistency: the slice order conflicts with the test gates and
  with the plan "Risks" text. "Hidden readers" says to migrate the tests
  before the runtime accessors are deleted. But the plan "Slice order" and
  steps.md delete them in Slice 3 and migrate the tests in Slice 4. Slices
  2 and 3 each end with a suite run, and these runs cannot pass:
  - Slice 2: the moved guard reads `impl.field-capability/capability`,
    but `cell_harness/controlled-absence` still replaces
    `impl.runtime/field-capability` until Slice 4. The receiving-guard
    cases in `controlled_field_capability_test` then do not see the
    replacement.
  - Slice 3: tests still call `runtime/capability`,
    `runtime/field-capability` and the old guard, which no longer exist.
  Choose one order and make plan.md and steps.md agree. For example,
  migrate each test reader in the same slice as the src change that it
  depends on, or move the test migration before the runtime deletion.
- [x] Plan inconsistency: the steps.md Slice 7 item for
  `embodiment/artifacts.md` says "for the owners and guard move". Acceptance
  7 names four other `artifacts.md` parts: the source list, the
  `test/cross-jvm/` entry that names the matrices, the "Development quality
  workflow" paragraph (API doc sources and documented functions) and the
  isolated source-JAR consumer results (Acceptance 8). Make the plan and
  steps name these four parts.
- [x] Plan ambiguity: the steps.md slice gates use suite names that are
  not project suites, and the names change between slices. Slice 1 says
  "unit and compatibility", Slice 2 says "unit, compatibility and
  cross-JVM", and Slice 3 says "runtime, fallback and cross-JVM". Slice 4
  and Slice 6 say only "full test suites" or "existing suites". The
  project suites are aliases with fixed parent JVMs
  (`doc/development/runtime.md`): `:test` (`test/runtime`, EA3 with
  preview and the export), `:fallback-test` (three parents: Java 8, EA3,
  EA3 with preview), `:cross-jvm-test` (Java 8 parent), `:build-test` and
  `:tooling-test`. Also, Slice 1 changes `impl.array` allocation discovery
  and the field probe prerequisite, but its gate does not include
  `:cross-jvm-test`. The array matrix and the cross-JVM field-capability
  tests are in that suite. For each slice gate, name the aliases and
  parent JVMs, use one name for each suite, and state if Slice 1 runs
  `:cross-jvm-test`. Make "full" in Slice 4 agree with the Acceptance 6
  suite list.
- [x] Plan ambiguity: steps.md Slice 2 limits the `runtime_test`
  migration to "lines 107–109 only; the other `impl.runtime` loads are
  policy checks". That is not true. Two more child forms read the value-class
  result through `impl.runtime` with the alias `r`:
  `capability-test` (lines 35–38, `(r/capability)` and the
  `:cached` identity check) and the form at lines 302–303
  (`(:available? (r/capability))`). The Slice 2 grep patterns
  (`runtime/capability`, `value-type.impl.runtime/capability`) do not
  match `r/capability`, so the hidden-reader grep does not find them.
  Acceptance 5 requires that they move, and the A-34 note in
  implementation.md says that `capability-test` moves to
  `impl.capability/capability`. In steps.md Slice 2, list all three
  `runtime_test` readers (lines 35–38, 107–109, 302–303), and make the
  grep find alias reads (for example, also grep for
  `/capability)` and `/field-capability)` in files that require
  `impl.runtime`). Use the same grep in Slice 3 and the Slice 4 check.
- [x] Plan inconsistency: steps.md Slice 6 says that in the two orders
  that do not start with `value-type.capability`, the cell calls `report`
  "after the Acceptance 3 checks". But the next Slice 6 item defines the
  "Acceptance 3 agreement checks" as comparisons of report results with
  outcomes, plus the Contract identity check (report value vs owner
  accessor). These checks need the report, so they cannot all run before
  it. Design Acceptance 1 and plan "Key decisions" say "after the
  Acceptance 3 definition and array calls". Change the steps.md text to
  that wording: the cell makes the definition and array calls, then calls
  `report`, then compares.
- [x] Plan ambiguity: steps.md Slice 3 says "Migrate cross-JVM
  `field_capability_test` to ... the guard in `impl.field-capability`",
  but it does not give the child require set of
  `required-capability-policy-matrix-test`. Now its child form requires
  only `value-type.impl.runtime`, so each policy is initialized when the
  guard loads. The test claims "Native requirements never warn, fall back,
  or become successful on retry" under each policy. If the migration only
  replaces the require with `impl.field-capability`, no child loads the
  policy, and the policy axis gives no evidence (Acceptance 5: "the same
  evidence"). State the require set of that child form. For example,
  require `value-type.core`, which loads `impl.runtime` and
  `impl.field-capability` as an expansion or AOT artifact does. Or state
  that the policy axis now shows that the guard does not depend on policy.
- [x] Plan ambiguity: the steps.md Slice 1 item "Confirm all accessor
  reads go through vars (no direct deref of another owner's delay)" does
  not say which delays it covers. Two current `src/` readers deref a
  delay of `impl.capability` directly: `impl.array/discover`
  (`@capability/value-predicate`, line 16) and `value-type.core`
  `no-value-classes?` (line 22). `value-predicate` is a public Method
  delay, not one of the four capability results, and the design gives it
  no accessor. Read literally, the confirm check fails on unchanged code,
  or it asks for a change that is not in scope. State that the check
  covers only the four capability-result delays (`detected-capability`,
  `detected-field-capability`, `detected-allocation-capability`,
  `detected-inspection-capability`), and that the `value-predicate`
  derefs stay as they are.
