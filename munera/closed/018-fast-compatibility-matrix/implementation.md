# Implementation

## Design review

- 2026-10-29 architecture review pass 1: `ramora/META.md` and `doc/architecture.md`
  do not exist. Architecture sources used: `AGENTS.md`, `ramora/INDEX.md`,
  `ramora/defvaluetype-design/fallback.md` (D10, D13, Verification),
  `ramora/non-nil-field-design/verification.md`, `doc/development/runtime.md`,
  and the mementum testing and fallback pages.
- Transfer consumers today reuse one producer expansion for all cells, with a
  fixed class name (`user.TransferParent`). Thus fresh consumer namespaces alone
  do not isolate transfer cells.
- Architectural review added 3 new design steps.
- 2026-10-29 ambiguity review pass 1: `:selected` users found by grep are
  `aot-test`, `transfer-test` and `runtime-test`. Namespaces with no `run-jvm`
  call are listed in design-steps. Only `array-info-boundary-test` and
  `array-sequence-boundary-test` set `:timeout-ms` explicitly (120000).
- Ambiguity review added 8 new design steps.
- 2026-10-29 inconsistency review pass 1: the Context claims about
  `value-type.impl.runtime` agree with the source. Policy is a namespace-load
  `def`. The warning prints to the current `*err*` binding. `aot_fixture` uses
  `Files/createTempDirectory`.
- Inconsistency review added 4 new design steps.
- 2026-10-29 architecture review pass 2: the revised design agrees with D11,
  D13 and the non-nil verification matrix. Grouping by child configuration
  keeps one policy for each JVM (D13 rejects a cross-loader registry).
  `ramora/META.md` and `doc/architecture.md` still do not exist.
- Architecture review pass 2 added 1 `SCOPE_QUESTION:` design step (release
  gate and quality-tool paths after the move to `test/cross-jvm/`). Do not raise
  other forms of this boundary concern again.
- 2026-10-29 ambiguity review pass 2: added 4 new design steps.
- 2026-10-29 inconsistency review pass 2: added 2 new design steps.
  - Checked with no finding: the capability cache and `warned?` names in
    "Shared runtime state" agree with `src/`. The "Static cache" heading exists.
    The 27 compatibility namespaces agree with "Test classes". `:baseline` and
    `:valhalla` get the parent `java.class.path`, so under `:cross-jvm-test`
    children see both test directories.
  - For acceptance 5 timeout: with Scope 4, a fixture child with n cells times
    out at `:timeout-ms` + 10 s × (n − 1). The fixture sleep must be longer.
- 2026-10-29 architecture review pass 3: no architectural review feedback.
  The pass-2 changes (child kinds, one artifact for each AOT cell child, retry
  in one cell, label record) agree with D11, D13 (one warning for each library
  runtime; retry `NoClassDefFoundError`) and non-nil verification ("consume
  them without source in fresh processes"). The open `SCOPE_QUESTION:` is not
  raised again.
- 2026-10-29 ambiguity review pass 3: no ambiguity review feedback.
  - Checked with no finding: `controlled-field-capability-test` and
    `function-redefinition-test` cells replace
    `value-type.impl.runtime/field-capability` with `with-redefs`. This is a
    cell input, not child configuration: the child-count bound has no term
    for it, and cells run one at a time, so the root is restored before the
    next cell. Fixture sources are in temporary directories, not on the
    parent class path, so rule 4 holds under `:cross-jvm-test`.
- 2026-10-29 architecture review pass 4: design unchanged since pass 3. Added
  1 new design step: per-cell `Compiler/LOADER` binding.
  - Source of the finding: `ramora/defvaluetype-design/risks-and-evolution.md`
    risk table ("fresh receivers"). Pass 3 checked only D11, D13 and the
    non-nil matrix, not the risk table.
  - Transfer receivers use `eval` and `load-string`. These push their own
    `LOADER` frame, so the leak affects cells that call `macroexpand` in the
    child, not the transferred `evaluate` path. A child is one `-e` form
    (`jvm-runner` line 29).
- 2026-10-29 ambiguity review pass 4: added 1 new design step (invalid
  concurrency limit).
  - Checked with no finding: cross-JVM tests read no parent-derived
    `jvm-config` key (`:available?`, `:baseline?`) in the parent. Thus their
    first-record labels and cell counts are equal under the three parents, and
    acceptance 2 can compare with any of them.
- 2026-10-29 inconsistency review pass 4: no inconsistency review feedback.
  - Checked with no finding: non-nil verification permits "at most one
    fallback warning per runtime" (agrees with Scope 3). Task 017 acceptance 8
    names no process count. Ramora A-36 (`aot-test/source-free-runtime-only-fallback-test`)
    makes several function calls in one cell. `doc/development/runtime.md`
    has "Verified fallback matrix" with three commands, and the 120-second text
    at the sequence and diagnostics sections. Children require
    `value-type.core` inside the cell code, so library-runtime load errors
    (`runtime-test/invalid-policy-test`) are cell results, not child-level
    stderr.
  - Doc fact for Scope 6: the focused non-nil command mixes cross-JVM
    namespaces with `fields-test` (in-process) in one `:fallback-test` call.
    Split it, or run `fields-test` under `:cross-jvm-test` by `--namespace`
    (not checked with Scry).
- 2026-10-29 architecture review pass 5: no architectural review feedback.
  The open `SCOPE_QUESTION:` is not raised again.
  - Checked with no finding: each Ramora "fresh JVM/process/receiver"
    requirement maps to a rule. Array D3 and verification item 6 (exports,
    preview, flattening) → rule 1. Non-nil "isolated processes" per JVM ×
    policy → rules 1–2. Non-nil "fresh processes" for source-free AOT →
    rules 4–5. Risk-table "fresh receivers" → per-cell `LOADER` frame.
    `install!` changes only the bound `LOADER`; `warned?` is the only atom
    in `src/`.
  - A warn value cell after a fallback cell in one child has a trivially true
    "no warning" check. Not a gap: its exact value Class check and the
    error-policy success cell show that no fallback occurred.
- 2026-10-29 ambiguity review pass 5: no ambiguity review feedback.
  - Checked with no finding: `run-jvm` takes only `:java`, `:classpath`,
    `:jvm-opts`, `:policy`, `:form` and `:timeout-ms`, so the Concepts list of
    child configuration is complete. No code in `src/` or
    `test/compatibility` writes to `System/out` or `System/err` directly, so
    per-cell `*out*`/`*err*` bindings capture all cell text.

## Notes for the design-steps work (after design-review pass 1)

- Principles:
  - Scope is frozen. Fix each design step in Isolation rules, Decisions,
    Invariants or Acceptance. Do not move the scope boundary.
  - Design steps that touch one another: transfer isolation (arch 1),
    warn-guard evidence (arch 2), the warn expectation change (ambiguity 3) and
    the runtime-state invariant (inconsistency 3). Write one model of shared
    runtime state, then update all four sections from it.
  - Run-once mechanism (arch 3) and acceptance 3 (inconsistency 4): select the
    mechanism first. The acceptance text depends on it.
  - Children return data and the parent asserts. A failure is a root class and
    message (`consumer-aot-checks/failure`), not a class only.
  - Expectations come from the explicit JVM configuration, not from what a child
    detects (`mementum/knowledge/controlled-fallback-boundaries.md`).
- Facts from the code:
  - `run-jvm` reads only the last `stdout` line as EDN. It merges no stderr into
    the result. The parent checks process `:err` itself.
  - `child-transfer-checks/consume` looks up `->TransferChild` and the builders
    by the current `*ns*` name. A namespace for each cell must also hold the
    receiver child definition.
  - `transfer-test` and `aot-test` mix `:baseline` producers with `:selected`
    consumers. A split by parent cuts through these tests.
- Project files:
  - `test/compatibility/value_type/{jvm_runner,jvm_config,aot_fixture}.clj`
  - `test/compatibility/value_type/child_transfer_{test,checks}.clj` (example of
    a large matrix)
  - `src/value_type/impl/runtime.clj` (`policy`, `warned?`, capability delays)
  - `doc/development/runtime.md` ("Verified fallback matrix", array sections
    with the 120 s text, focused non-nil commands)
  - `ramora/defvaluetype-design/fallback.md`,
    `ramora/non-nil-field-design/verification.md`
  - `mementum/knowledge/testing-clojure-generated-classes.md`,
    `mementum/memories/{repeated-definitions-must-prove-new-class-identity,child-jvm-checks-return-data,scry-pass-true-with-zero-tests}.md`

## Design-steps work (pass 1)

- 2026-10-29: all 15 design steps done in `design.md`. No `SCOPE_QUESTION:`
  items.
- Test classes were found per `deftest` by reading the forms, not by grep.
  A grep for `selected` gives false matches (prose such as "selected storage").
  The table in `design.md` is the reference.
- Scry 0.1.61 facts:
  - `--namespace` for a namespace that is not on the class path gives
    `:scry.cli/runner-error` and exit 1 (checked on 2026-10-29).
  - Assertion events carry `:contexts`. `scry.core/run` with `:var` result
    scope gives `:assertions` for each Var. Use this for the label record.
  - Scry reports only a total `:duration-ms`. Namespace time needs its own
    measurement.
- Current labels use `(pr-str [(:java runtime) ...])`, so they hold absolute
  JDK paths. Labels are thus specific to one machine.
- Warn-guard matches: `child-transfer-test/strict-receiver-child-replacement-test`
  has a matching error cell for each warn cell of
  `receiver-child-replacement-test`. Other tests with warn fallback cells are
  not checked yet.
- Files that name the suite or the test directories (update for
  `test/cross-jvm/`): `deps.edn`, `bb/value_type/quality.clj` (lint paths,
  aliases), `test/tooling/{ci-failures,hooks}.sh` (directory lists),
  `ramora/defvaluetype-design/{assertions/fallback-architecture,embodiment/artifacts}.md`,
  `spike/portable-fields/verify.sh`. `.github/workflows/release.yml` runs the
  three `:fallback-test` commands. Without a change, CI does not run the
  cross-JVM suite. Scope 6 does not name this file; ask the user before the
  plan changes it.
- The default limit (4) and the timeout of 10 s for each added cell are not
  measured. If measurement shows a problem, change `design.md`.
- After the work, update
  `mementum/memories/repeated-definitions-must-prove-new-class-identity.md`.
  The design replaces its fresh-JVM rule.

## Notes for the design-steps work (after design-review pass 2)

- Principles:
  - The `SCOPE_QUESTION:` item waits for a human. Do not edit Scope for it.
    The other pass-2 steps do not depend on it. If the human accepts, the file
    list is in "Design-steps work (pass 1)" above.
  - Decide these together. They all change rules 4–5 and the acceptance 2
    bound: the child-count formula (ambiguity 1), the AOT classpath
    (ambiguity 2) and the AOT retry (inconsistency 1). Write the AOT consumer
    model once, then update the rules, Scope 3 and acceptance 2 from it.
  - Warn-guard match (ambiguity 3): define it from the transfer rule (one
    expansion for each consumer cell), not against that rule.
  - Label record (inconsistency 2): the record source is Scry assertion events
    (`:var`, `:contexts`). Define the cell count and the labels of assertions
    that belong to no cell from those events.
  - "Do not decrease" applies to Error forms. Today AOT checks compare the full
    `:chain` of class names. Root class and message alone accept more, so keep
    the chain where it exists.
- Facts from the code:
  - `aot-checks/load-fixture` under strict policy makes 2 attempts in one
    child (`:reload`), so initial and retry are one cell today. It asserts that
    the source `fallback/aot_fixture.clj` is absent from the classpath.
  - All `with-fixture` artifacts in `aot-test` use the namespace
    `fallback.aot-fixture`. If AOT consumers share a child, each artifact
    needs its own namespace name.
  - `compile-producer` asserts exit, empty stderr and `:compiled` in the parent
    outside a cell `testing` context. These are assertions that belong to no
    cell.
  - `compile-java` starts a `run-jvm` child that runs javac in process through
    `ToolProvider`. It does not start a grandchild process.
- Project files: `test/compatibility/value_type/{aot_fixture,aot_checks,aot_test}.clj`,
  `.github/workflows/release.yml`, `ramora/release-workflow.md` ("Workflow
  flow"), `bb/value_type/quality.clj`.

## Design-steps work (pass 2)

- 2026-10-29: 6 pass-2 design steps done in `design.md`. The
  `SCOPE_QUESTION:` step (release gate and quality-tool paths for
  `test/cross-jvm/`) stays unchecked. It is deferred to the user. Scope is not
  changed.
- Consequence of rules 4 and 5: an AOT cell child holds one artifact, and
  each cell that loads it is a first initialization. Thus AOT tests keep one
  cell for each child. Their gain comes from parallel children and the
  run-once cross-JVM suite, not from grouping.
- Producers today: each transfer test already starts one producer, and each
  AOT test compiles one fixture (`field-aot-test/separately-compiled-restricted-field-consumer-test`
  compiles two). Thus `producer(T)`, `aot(T)` and `java(T)` equal the current
  counts.
- Today one cell child runs one cell, also in the `transfer-test` concurrent
  tests (one `testing` context for each child). The label for the first
  record: Scry sets `:contexts` to `(vec (reverse clojure.test/*testing-contexts*))`
  (scry 0.1.61). A recorder at child start gets the same value.
- Some current labels hold cells and support assertions together: for example,
  `public-field-aot-test` calls `compile-producer` in the same `testing`
  context as all its consumer cells (12 cells, 1 label).

## Notes for the design-steps work (after design-review pass 4)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The two pass-4 steps do not depend on it.
  - `LOADER` step (arch 4): extend the existing model of shared state. Do not
    add a new isolation mechanism. Fresh namespace + unique class name + own
    `LOADER` frame is one isolation unit for each cell. Update "Shared runtime
    state", the "isolation unit" decision, the definition-state invariant and
    the Scope 6 doc list (`risks-and-evolution.md` "fresh receivers") together.
  - Invalid-limit step (ambiguity 4): select the check point so that the
    stated result ("stops the run") is true. Scry facts are known only for a
    `--namespace` that is not on the class path (runner error, exit 1). The
    Scry result for an exception at namespace load is not checked.
- Facts from the code:
  - `install!` replaces the bound `LOADER` value with `.set`. It does nothing
    when `LOADER` is unbound. `eval` and `load-string` push a new frame for
    each top-level form. In `(eval (macroexpand form))`, `macroexpand` runs in
    the outer frame, before `eval` pushes its frame.
  - A child is one `clojure.main -e` form, thus one outer frame for all cells.
  - Child cells that call `macroexpand`/`macroexpand-1` directly:
    `expansion-checks` (used by `runtime-test`) and
    `controlled-field-capability-test`.
- Project files: `src/value_type/impl/loader.clj` (`install!`),
  `test/compatibility/value_type/{jvm_runner,expansion_checks,controlled_field_capability_test}.clj`,
  `ramora/defvaluetype-design/risks-and-evolution.md` (risk table),
  `mementum/memories/clojure-compiler-loader-hook.md`.

## Design-steps work (pass 4)

- 2026-10-29: 2 pass-4 design steps done in `design.md` (per-cell binding
  frame; invalid `VALUE_TYPE_TEST_JVM_LIMIT`). The pass-2 `SCOPE_QUESTION:`
  step stays unchecked and deferred to the user. Scope is not changed.
- Scry 0.1.61 facts (checked 2026-10-29 with a scratch project):
  - `--dir` loads only test namespaces. A support namespace loads only when a
    loaded test namespace requires it.
  - An exception at load of a required support namespace gives "No tests
    run", `:scry.cli/runner-error` and exit 1. No other test runs.
  - `--namespace` of a namespace that does not require the failing support
    namespace passes.
- The 6 in-process namespaces do not require `jvm-runner`.
  `runtime-test`, `aot-test`, `transfer-test` and `aot-fixture` do. Thus each
  `:fallback-test` command after the move still loads `jvm-runner`.
- Clojure 1.12.6 facts for the per-cell frame: `clojure.main/null-opt` wraps
  `-e` in `clojure.main/with-bindings` (it binds `*ns*`,
  `*warn-on-reflection*`, `*print-namespace-maps*` true, `*compile-path*` from
  the system property, `*1`–`*3`, `*e` nil, and others). Per-cell bindings
  take the value at cell start, not these defaults. `RT/makeClassLoader`
  gives a `DynamicClassLoader` whose parent is the bound `LOADER` value.
  `Var.set` sets the innermost thread binding.
- Children inherit the parent environment (`ProcessBuilder`). A child that
  loads `jvm-runner` also reads the limit; this is harmless because an
  invalid value stops the parent first.
- 2026-10-29 inconsistency review pass 5: added 1 new design step (nested
  case labels in `loader-policy-test`).
  - Found with an awk scan for nested `testing` forms in each `deftest`.
    `loader-policy-test` is the only test where one child gives results for
    more than one nested label. The other nested labels in cross-JVM tests
    (`field-aot-test`, `generated-value-aot-test`, the `public-*-aot-test`
    namespaces) start one child each. The outer label holds only compile
    support assertions.

## Notes for the design-steps work (after design-review pass 5)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The pass-5 step does not depend on it.
  - Fix the pass-5 step only in Concepts ("Cell", "Support assertion") and
    Invariants (label record, label mapping). One counting rule must hold for
    the first record and the record after the work.
  - Options for the nested case labels:
    - (a) The nested labels are part of the cell of the outer (child-start)
      label. Then state that an assertion in a nested context of a cell label
      is a cell assertion, not a support assertion, and that a label can have
      cell count 0 without being a support label.
    - (b) Each nested label is its own cell (count 1 for each child). Then the
      outer label holds only child-level assertions (exit, case count), which
      are support assertions. The recorder must attribute the child start to
      the nested labels, not to the outer label.
    (a) changes less; (b) matches the "Cell" concept. Select one.
- Facts from the code:
  - `identity-loader-checks/public-stale-sibling` returns a map of 6 cases
    (`[expansion analysis]`). Each case already uses a `gensym` class name
    and a `gensym` namespace, and calls `macroexpand`/`macroexpand-1` itself
    in the child's top-level frame (no own `LOADER` binding today).
  - `identity-loader-test` (in-process) runs the same 6 cases with the same
    nested labels in the parent. Only the cross-JVM copy is affected.
- Project files: `test/compatibility/value_type/{loader_policy_test,identity_loader_checks,identity_loader_test}.clj`.

## Design-steps work (pass 5)

- 2026-10-29: the pass-5 design step is done in `design.md` (Concepts
  "Support assertion"; Invariants, first record). Option (b) selected: each
  nested case label is its own cell. Option (a) gives one cell more than one
  label, and count-0 labels that are not support labels. The pass-2
  `SCOPE_QUESTION:` step stays unchecked and deferred to the user. Scope is
  not changed.
- Corrects the pass-2 note "Today one cell child runs one cell":
  `loader-policy-test` children run 6 cells each.
- First record for `fresh-policy-loader-rejection-test`: 14 start labels
  with count 0 (exit and result-count support assertions), and 14 × 6 case
  labels with count 1. Its child count does not change with grouping (one
  child for each configuration today). After the work, each of its 6 cases
  needs its own `LOADER` frame (today they share the child's top-level frame).
- 2026-10-29 architecture review pass 6: no architectural review feedback.
  The open `SCOPE_QUESTION:` is not raised again.
  - Checked with no finding: the pass-5 change (each nested case label of
    `fresh-policy-loader-rejection-test` is a cell) is label accounting only.
    It agrees with the per-cell `LOADER` frame (each case calls `macroexpand`
    itself). D11 Verification (`fallback.md`) and array verification item 6
    map to rules 1–2 and 4–5 and Scope 3. The `artifacts.md` line
    "`test/compatibility/...`: explicit fresh-JVM ... tests" must change under
    Scope 6 (new suite, grouped children). Its text must obey
    `ramora/INDEX.md` "Documentation rules" (no history).
- 2026-10-29 ambiguity review pass 6: added 1 new design step (cell labels in
  the record after the work).
  - Source: the pass-5 nested-label rule classifies any label under a start
    label that has child-result assertions as a cell. A grouped child adds the
    Scope 2 child-level stderr label, which fits that rule but is a support
    assertion.
- 2026-10-29 inconsistency review pass 6: no inconsistency review feedback.
  - Checked with no finding: `loader-policy-test` has 14 children
    ((2 + 2 + 3) policies × 2 execution modes), each returns 6 cases, and the
    exit and count assertions are under the start label (lines 20–29).
    Referenced paths and headings in `design.md` exist.

## Notes for the design-steps work (after design-review pass 6)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The pass-6 step does not depend on it.
  - Fix the pass-6 step only in Invariants (label record, "after the work")
    and, if a label form is needed, in Scope 2. Keep the first-record method
    as it is. Acceptance 2 compares the two records, so both must give a count
    from the same Scry events (`:var`, `:contexts`).
  - The method must handle both child shapes after the work:
    - grouped child: exit, result count and child-level stderr are support
      assertions;
    - one-cell child (rules 4–5, for example AOT children): exit and the
      current process-stderr checks stay under the cell label as cell
      assertions (Concepts "Support assertion": "when the child runs more than
      one cell"; Scope 2: "can keep its current checks").
  - Options:
    - (a) First-record method, and exclude support labels. Then Scope 2 must
      give the child-level stderr label a form that the record can detect
      (for example, a fixed last context).
    - (b) The parent records each label that it asserts from a cell result.
      This is test support (in scope). It needs no label form, but it is a
      second method; state that the first record cannot use it (the serial
      runner has no recorder).
- Facts from the code:
  - `loader-policy-test` lines 20–29: start label `(pr-str [java jvm-opts
    policy execution])`, nested label `(pr-str case-key)`. The `err` in
    `(is (= 0 exit) err)` is a message, not a stderr assertion.
- Project files: `test/compatibility/value_type/{jvm_runner,loader_policy_test}.clj`.

## Design-steps work (pass 6)

- 2026-10-29: the pass-6 design step is done in `design.md` (Invariants,
  "after the work"). Option (b) selected: the cell-result assert function
  records each cell label. Option (a) fails when a grouped child starts
  outside its cell labels (for example, a batch start at the test root
  context): the start label then does not identify the child's cells. The
  pass-2 `SCOPE_QUESTION:` step stays unchecked and deferred to the user.
  Scope is not changed.
- Scry 0.1.61 facts (`scry/capture.clj`): the event `:var` comes from Scry's
  own `:begin-test-var` frames, not from `clojure.test/*testing-vars*`. The
  event `:contexts` is `(vec (reverse clojure.test/*testing-contexts*))`.
  A recorder that reads the Var from `*testing-vars*` must give the same Var
  as Scry; check this in the plan.
- Implementation consequence: all cell assertions in grouped children go
  through one test support function (assert and record). Inline `is` on a
  child result is a support assertion, so it must not use a cell label.
- 2026-10-29 architecture review pass 7: no architectural review feedback.
  The open `SCOPE_QUESTION:` is not raised again.
  - Checked with no finding: the pass-6 change (the cell-result function
    records cell labels) is test support only. It agrees with "The parent
    keeps the assertions" and `child-jvm-checks-return-data`. Scry labels
    and failure form do not change.
- 2026-10-29 ambiguity review pass 7: added 1 new design step (stderr checks
  of rule-5 children).
  - Source: the pass-6 text "also makes the current process-stderr checks"
    against Scope 2 "child-level stderr must be empty, under all policies".
    Rule-5 warn children today assert the warning text in process `:err`.
- 2026-10-29 inconsistency review pass 7: no inconsistency review feedback.
  The rule-5 stderr conflict is filed once, as ambiguity pass 7.
  - Checked with no finding: no cell-result function exists today
    (`jvm-runner`, `jvm-config` and `aot-fixture` define only `run-jvm`,
    `configuration` and the fixture functions). "21 test namespaces" in
    Context equals the classified `run-jvm` users.

## Notes for the design-steps work (after design-review pass 7)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The pass-7 step does not depend on it.
  - Fix the pass-7 step in Scope 2 and Invariants ("after the work") only,
    with the same words in both. Remove "can": the plan must not choose.
  - Keep "Expected results do not decrease". A warning check that moves from
    process `:err` to the cell result must keep its exact text and count.
  - Keep one support/cell split: after the work a label has support
    assertions or cells, not both. If a rule-5 child also gets the
    child-level stderr assertion, its label differs from the cell label.
  - Options:
    - (a) Rule-5 children use the per-cell `*out*`/`*err*` bindings like
      other children. The warning is in the cell result; the child-level
      empty-stderr support assertion applies to every child. One model, but
      each current process-`:err` check changes to a cell-result check.
    - (b) Rule-5 children have no per-cell bindings. Process stderr is the
      cell's stderr, checked under the cell label by the cell-result
      function; no child-level stderr support assertion for them. Fewer
      check changes, but two child shapes.
- Facts from the code:
  - The warning prints to the current `*err*` binding
    (`src/value_type/impl/runtime.clj` lines 32–33), so a per-cell binding
    captures it.
  - Process-`:err` warning checks in rule-5 children:
    `transfer-test/concurrent-warning-scope-test` line 53; `aot-test`
    lines 35–36, 80, 145 (and `= ""` at 38, 111). 13 test namespaces check
    `:err` (largest: `runtime-test` 17, `public-field-aot-test` 10).
- Project files: `test/compatibility/value_type/{transfer_test,aot_test,aot_checks,jvm_runner}.clj`,
  `src/value_type/impl/runtime.clj`.

## Design-steps work (pass 7)

- 2026-10-29: the pass-7 design step is done in `design.md` (Scope 2;
  Invariants, "after the work"). Option (a) selected: every cell child,
  also a rule-5 child, uses per-cell `*out*`/`*err*` writers and gets the
  child-level empty-stderr support assertion. One child shape. The pass-2
  `SCOPE_QUESTION:` step stays unchecked and deferred to the user. Scope is
  not changed.
- Corrects the pass-5 note "per-cell `*out*`/`*err*` bindings capture all
  cell text": a thread that does not convey bindings writes to the root
  `*err*` (process stderr). `expansion-checks/on-thread` uses a raw `Thread`
  (no `bound-fn`, so that `Compiler/LOADER` stays unbound). Under warn,
  `runtime-test/fallback-expansion-policy-test` gets its one warning on that
  thread (`expansion-attempts false` runs first) and counts it in process
  `:err` (line 136). `constrained-discarded-with-loader` uses the same
  thread. Fix in the plan: bind only `*out*`/`*err*` on that thread
  (Scope 2). `warning-checks` threads use `bound-fn` or `future`; they
  convey the cell writers already.
- 2026-10-29 architecture review pass 8: no architectural review feedback.
  The open `SCOPE_QUESTION:` is not raised again.
  - Checked with no finding: the pass-7 change (per-cell `*out*`/`*err*`
    writers in every cell child, rule 5 included). D11 "Print one warning to
    standard error" is met through `*err*` (`enforce-fallback!` prints to the
    current `*err*`; its root is process stderr). Empty child-level stderr
    keeps D11 "No output in silent mode" at process level. D13 concurrent
    warning evidence holds: `warning-checks` threads use `bound-fn` or
    `future`, so they convey the cell writers. Binding only `*out*`/`*err*`
    on the raw `expansion-checks/on-thread` thread leaves `Compiler/LOADER`
    unbound (a new frame merges only the thread's empty prior bindings).
- 2026-10-29 ambiguity review pass 8: added 1 new design step (expected
  `*out*`/`*err*` text of each cell).
  - Source: the pass-7 text "the function checks the `*out*` and `*err*`
    text" with no general expected value. Warn tests with no `:err` check
    today (grep of `"warn"` against `:err`): `array-info-boundary-test`,
    `array-matrix-test`, `array-sequence-boundary-test`,
    `field-capability-test`, `function-redefinition-test`,
    `loader-policy-test`. No test reads `:out` today.
- 2026-10-29 inconsistency review pass 8: added 1 new design step
  (temporary artifact path in the Scope 2 child-level stderr label).
  - Checked with no finding: the one-cell timeout (formula with n = 1),
    the one-cell exit assertion (Concepts and Invariants), and acceptance 5
    against "Failures stay visible". The acceptance 5 tests assert returned
    results, not through the cell-result function, so their assertions are
    support assertions with new labels.

## Notes for the design-steps work (after design-review pass 8)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The pass-8 steps do not depend on it.
  - Expected `*out*`/`*err*` text (ambiguity 8): fix it in Scope 2, Scope 3
    and Invariants ("after the work") with one rule. Keep "Expected results
    do not decrease": a new expectation can be stricter than today (today
    many children have unchecked text), never weaker. A stricter check on a
    test that checks nothing today is a changed expectation; record it as
    the Invariants require.
  - For a current child that splits into more than one cell, the Scope 3
    warn rule (first fallback cell) already places the warning. Only `""`
    expectations need a rule for each split cell.
  - Stderr label (inconsistency 8): fix it in Scope 2 only. A label must be
    the same in each run and at each limit. Labels can hold absolute JDK
    paths (stable on one machine), but no temporary path.
- Facts from the code:
  - Child forms today print their result with `prn` as the last stdout line
    (for example `aot-fixture/reload-form` line 123, `compile-producer`
    `(prn :compiled)`). Check namespaces (`*_checks.clj`) print nothing else.
    In a grouped child, a cell returns its value; a `prn` in a cell goes to
    the cell `*out*`. Thus the `*out*` rule must allow for this or the
    cell forms must stop printing.
  - `aot-fixture/with-fixture` takes `fixture-ns`. All `aot-test` artifacts
    use `fallback.aot-fixture`, so the fixture name does not separate them.
    This is permitted for a support label (labels can repeat).
- Project files: `test/compatibility/value_type/{aot_fixture,jvm_runner,transfer_test,field_capability_test,loader_policy_test}.clj`,
  `src/value_type/impl/runtime.clj` (`enforce-fallback!`).

## Design-steps work (pass 8)

- 2026-10-29: 2 pass-8 design steps done in `design.md` (Scope 2
  "Expected cell text" and child-level stderr label; Scope 3 silent;
  Concepts "Fallback cell"; Invariants). The pass-2 `SCOPE_QUESTION:` step
  stays unchecked and deferred to the user. Scope is not changed.
- Current `:err` check forms (for the change record): exact text with
  `"\n"` (`aot-test`, `public-field-aot-test`, `transfer-test`,
  `runtime-test/policy-test`); `(count (re-seq ...)) = 1`
  (`child-transfer-test`, `field-transfer-test`, `runtime-test`); one line
  plus `starts-with?` (`aot-test` line 35). The new one-line rule is
  stricter than the `re-seq` count form.
- `runtime-test/policy-test` calls `enforce-fallback! "test"` directly. It
  is the only direct call in `test/`. Thus "Fallback cell" now names a
  direct call.
- Default policy is `"silent"` (`runtime.clj` `policy`). The `nil` policy
  children of `runtime-test` are silent cells.
- Cell forms that print today and must return their value after the work:
  `consumer-aot-checks/*-form` and `function-aot-checks` wrap the call in
  `prn`; `aot-fixture/reload-form` reads the consumer result with
  `with-out-str`.
- Risk for the plan: tests with no `:err` check today (see ambiguity pass 8
  list) can write other text, for example Clojure "already refers to"
  warnings. Not checked. The new rule fails them. Fix this in the test
  code, not in `src/`.
- 2026-10-29 architecture review pass 9: no architectural review feedback.
  The open `SCOPE_QUESTION:` is not raised again.
  - Checked with no finding: the pass-8 changes. The "Expected cell text"
    rule agrees with D10 (`warn`: one warning to standard error; `silent`:
    no message; default `silent`, as Scope 3 now states) and D11
    Verification ("No output in silent mode; one warning"). The library
    writes the warning only to `*err*` (`enforce-fallback!` binds `*out*`
    to `*err*`), so `*out*`: empty holds for library code. A direct
    `enforce-fallback!` call as a fallback cell agrees with D10 (direct
    calls throw plain `Error`). D13 "warn on actual fallback, not merely
    on requiring the public namespace" keeps its evidence: cells require
    `value-type.core` inside cell code, so a warning at require goes to
    the `*err*` of a cell that expects none, or past warn-once.
- 2026-10-29 ambiguity review pass 9: added 1 new design step (warning
  context of the first fallback cell of a split child).
  - Source: Scope 2 "Expected cell text" ties the context to "the current
    check of the cell", but split children have no current check of their
    own. Exact-context checks today: `transfer-test` lines 33, 53, 83;
    `aot-test` 78, 143; `public-field-aot-test` 36, 128;
    `runtime-test/policy-test` 42 (`test`). Prefix-only or `re-seq` forms:
    `aot-test` 35–37, `child-transfer-test`, `field-transfer-test`,
    `runtime-test` (other tests).
- 2026-10-29 inconsistency review pass 9: added 1 new design step (label
  mapping for a split child). The split-child warning context is filed once,
  as ambiguity pass 9.
  - Checked with no finding: the pass-8 Scope 2 text agrees with the current
    `:err` checks. All non-empty expectations are the fallback warning, and
    only on warn children. `runtime-test/fallback-expansion-warning-timing-test`
    expects child `:err` `""` and checks its warning in text that
    `expansion-checks` captures itself, which agrees with "Text that a cell
    captures itself is not cell text". `controlled-field-capability-test`
    line 127 checks producer stderr, so it is a support assertion. Scope 2
    covers only cell children.

## Notes for the design-steps work (after design-review pass 9)

- Principles:
  - Scope is frozen. The pass-2 `SCOPE_QUESTION:` still waits for a human.
    The pass-9 steps do not depend on it.
  - Decide the two pass-9 steps together. Both are about split children.
    Select the split model first (mapping, inconsistency 9). The expected
    warning context (ambiguity 9) then follows from it:
    - split is k cells → mapping lets one label map to k cell labels (sum
      ≥ count), and the first fallback cell needs a stated context;
    - split is one cell with one label → no mapping change, and the cell
      keeps the current child check unchanged. But then a grouped child
      holds a multi-case cell, and the "one cell per label" rules must still
      hold.
  - Keep "Expected results do not decrease". The context in the current
    exact-text checks must stay checked. Prefix only is weaker.
  - Fix in Scope 2 ("Expected cell text") and Invariants ("Label mapping",
    label-change record) only, with the same words in both.
- Facts from the code:
  - `transfer-checks/consume` returns `{mode [form0 form1]}`. Each form is
    2 attempts in one namespace (`transfer.<mode><index>`). Thus the
    natural split is 4 cells (mode × form). The 2 attempts are a repeated
    strict attempt inside one cell (as with the AOT retry under rule 5), not
    2 cells.
  - Every fixed fallback expansion or artifact warns with the context
    `fixed fallback representation` (`runtime/enforce-fixed-fallback!`).
    Source definitions with no value support use a detector context, so the
    "kind of fallback" option needs a rule for each kind.
  - Other split candidates have the same shape: `transfer-test` lines 60–112
    (one `testing` per policy/execution/runtime, result over serialized
    forms), and `concurrent-warning-scope-test` (8 concurrent definitions,
    one label). Classify each one with the selected model.
- Project files: `test/compatibility/value_type/{transfer_test,transfer_checks,warning_checks}.clj`,
  `src/value_type/impl/runtime.clj` (`enforce-fallback!`,
  `enforce-fixed-fallback!`).

## Design-steps work (pass 9)

- 2026-10-29: 2 pass-9 design steps done in `design.md` (Scope 2
  "Expected cell text"; Invariants "Label mapping" and the change record).
  Split model: a split is k cells. Its label maps to all k cell labels
  (target counts add up to at least the cell count). The first fallback
  cell keeps the context of the current child check. The pass-2
  `SCOPE_QUESTION:` step stays unchecked and deferred to the user. Scope is
  not changed.
- Facts for the plan:
  - `transfer-test/constrained-concurrent-warning-scope-test` (lines
    69–73): the serialized-form loop is outside `testing`, so each first
    record label has 2 cell children (cell count 2).
  - `concurrent-warning-scope-test` and the constrained variants: the 8
    concurrent definitions are the evidence (one warning for concurrent
    namespaces). Do not split them; one child result is one cell (rule 5).
  - Change record column is now "target labels", not "new labels". "New
    label" means a label after the work that is no target.
- 2026-10-29 architecture review pass 10: added 1 new design step (release
  preserve step for `:cross-jvm-test`).
  - Scope: the pass-2 `SCOPE_QUESTION:` is decided (Scope 7). This step
    does not change the boundary; it applies the release-workflow rule
    inside Scope 7.
  - Checked with no finding: no test in `test/release` or `test/tooling`
    reads `release.yml`, so Scope 7 changes no test of an out-of-scope suite.
    `jvm-config/configuration` accepts only the three documented parents, so
    the owner parent works in the release job (`VALUE_TYPE_BASELINE_JAVA`,
    `VALUE_TYPE_VALHALLA_JAVA` are set there). `ci.yml` runs no fallback
    suite. The pass-9 split model (k cells, first fallback cell keeps the
    current context) agrees with D11.
- 2026-10-29 ambiguity review pass 10: no ambiguity review feedback.
  - Checked with no finding: Scope 7 paths. `.pre-commit-config.yaml` file
    patterns match all of `test/`, so the hooks need no change.
    `ci-failures.sh` (missing-scope loop) and `hooks.sh` (probe loop) each
    hold one directory list. No `test/tooling/value_type` test or
    `quality-checks.md` text names `test/compatibility` or the alias list.
    `spike/portable-fields/verify.sh` uses `:fallback-test` only for a spike
    namespace and its classpath; the move does not change it.
- 2026-10-29 inconsistency review pass 10: no inconsistency review feedback.
  - Checked with no finding: Scope 7, "Out of scope" (`src/` line and suite
    line), the Scope 7 decision and acceptance 8 agree. `test/tooling` is not
    an excluded suite. `doc/development/release.md` ("Preview") says
    "fallback ... tests" with no suite list, so it stays true after the move.
    The pass-9 split text agrees with Scope 3 warn and with the first-record
    rule (a split is a change after the work only).

## Notes for the design-steps work (after design-review pass 10)

- Principles:
  - Scope is frozen. Scope 7 is decided. Fix the pass-10 step in Scope 7
    (`release.yml` item) and acceptance 8 only, with the same words in both.
  - Copy the form of the current suite steps. Do not add a new mechanism.
- Facts from the code (`.github/workflows/release.yml`):
  - Each suite is two steps: `Test <x>` with `id: <x>`, then
    `Preserve <x> failures` with `id: preserve-<x>-failures`,
    `if: ${{ failure() && steps.<x>.outcome == 'failure' }}`,
    `actions/upload-artifact@v4`, `name: release-scry-<x>`,
    `path: .scry-results/`, `include-hidden-files: true`,
    `if-no-files-found: warn`. Ids today: `fallback-java8`,
    `fallback-preview-off`, `fallback-preview-on`.
  - The `Report failure` step lists failed step ids from `toJSON(steps)`, so
    a new step id needs no change there.
  - Job `timeout-minutes: 60`. Today the three fallback commands take about
    80 min locally; after the work the target is at most 25% of that.
  - `ramora/release-workflow.md` "Workflow flow" already states the preserve
    rule for each suite. Only its suite list needs the cross-JVM tests.
- Project files: `.github/workflows/release.yml`, `ramora/release-workflow.md`,
  `doc/development/release.md` ("Preview").

## Design-steps work (pass 10)

- 2026-10-29: the pass-10 design step is done in `design.md` (Scope 7
  `release.yml` item; acceptance 8). Names follow the current pattern: step
  id `cross-jvm`, preserve id `preserve-cross-jvm-failures`, artifact
  `release-scry-cross-jvm`. No open `SCOPE_QUESTION:` step remains.
- For the plan: put the `cross-jvm` step pair next to the three
  `fallback-*` pairs, before `Test build` (each preserve step must run
  before the next suite clears `.scry-results/`). Check that the job
  `timeout-minutes: 60` covers the four commands after the work.
- 2026-10-29 architecture review pass 11: added 1 new design step
  (`quality-checks.md` "six test directories" text).
  - Checked with no finding: the pass-10 change (`cross-jvm` step and
    `preserve-cross-jvm-failures`) agrees with `ramora/release-workflow.md`
    "Workflow flow" (runtime JVMs selected explicitly, preserve for each
    suite) and the `release.yml` step form. `test/` has six directories
    today; `test/cross-jvm` makes seven.
- 2026-10-29 ambiguity review pass 11: no ambiguity review feedback.
  - Checked with no finding: `design.md` is unchanged since pass 10. The
    pass-10 Scope 7 text gives the step id, preserve id, condition, path and
    artifact name; step name and position are free, because the preserve
    step directly follows its suite step. Producer children have no cells,
    so the Scope 4 timeout gives them the explicit `:timeout-ms`.
- 2026-10-29 inconsistency review pass 11: no inconsistency review feedback.
  - Checked with no finding: acceptance 8 names the three quality files
    that hold a `test/compatibility` path today (`quality.clj`
    `maintained-paths`, `ci-failures.sh` line 46, `hooks.sh` line 81).
    `deps.edn` `:fallback-test` gives its `--dir` in `:main-opts`, so the
    Scope 5 `:cross-jvm-test` alias can take the same form.

## Notes for the design-steps work (after design-review pass 11)

- Principles:
  - Scope is frozen. Fix the pass-11 step in Scope 7 and acceptance 8 only,
    with the same words in both. It names one more file inside Scope 7; it
    does not move the boundary.
  - Prefer text with no count ("all test directories listed in the test
    layout"). Then a later suite needs no edit here.
  - `quality-checks.md` is in the Vale prose gate (development guide linked
    document). Keep the text in simplified technical English.
- Facts from the code:
  - The line-64 sentence also lists non-test paths (`bench/`,
    `dev/release/`, `bb/`, `.clj-kondo/hooks/`, `build.clj`, `bb.edn`,
    `deps.edn`). Only the test-directory count changes.
  - The test layout table in `doc/development/runtime.md` ("Test layout")
    has six rows today, including `test/resources/`. Scope 6 adds
    `test/cross-jvm/`.
- Project files: `doc/development/quality-checks.md` ("Quality checks",
  line 64), `doc/development/runtime.md` ("Test layout"),
  `ramora/defvaluetype-design/embodiment/artifacts.md` ("Development quality
  workflow"), `bb/value_type/quality.clj` (`maintained-paths`).

## Design-steps work (pass 11)

- 2026-10-29: the pass-11 design step is done in `design.md` (Scope 7 new
  `quality-checks.md` item; acceptance 8). The rule is "no count of test
  directories", so a later suite needs no edit there. No open
  `SCOPE_QUESTION:` step remains.
- For the plan: edit only the test-directory phrase of
  `quality-checks.md` line 64. It is in the Vale prose gate, so run
  `bb vale` after the edit. `quality.clj` `maintained-paths` lists each
  test directory explicitly (also `test/resources`, which is not a class
  path root); add `test/cross-jvm` there, and `:cross-jvm-test` to
  `dependency-aliases`. `project-paths!` throws if a listed path is
  missing, so add the path in the same commit that creates
  `test/cross-jvm/`.
- 2026-10-29 architecture review pass 12: no architectural review feedback.
  - Checked with no finding: the pass-11 change. "All test directories
    listed in the test layout" agrees with `maintained-paths` (the
    `runtime.md` test layout rows equal its test paths, `test/resources`
    included) after Scope 6 adds `test/cross-jvm/`. Two class path roots
    for `:cross-jvm-test` have a precedent (`:build-test` adds `.` and
    `test/build`). No Ramora or tooling file names the cross-JVM tests of
    `identity-receiving-test` (only closed task 017 notes).
- 2026-10-29 ambiguity review pass 12: no ambiguity review feedback.
  - Checked with no finding: the pass-11 Scope 7 `quality-checks.md` item.
    Only line 64 holds a test-directory count. The file has no alias list
    (line 47 says "each alias basis" with no names), so `:cross-jvm-test`
    needs no text there. The `artifacts.md` sentence in that item is a
    reference, not a change: its "Development quality workflow" text names
    no lint path list (only `test/tooling/` as the place of the tooling tests).
- 2026-10-29 inconsistency review pass 12: no inconsistency review feedback.
  - Checked with no finding: the pass-11 `quality-checks.md` item agrees
    with the Scope 7 lead sentence, acceptance 8, the "Out of scope" `src/`
    line (documentation) and Scope 6 (its "test layout" link target).
    `test/tooling/value_type/prose_test.clj` and `bb/value_type/prose.clj`
    list `quality-checks.md` and `runtime.md` as Vale files only; no test
    reads their text.

## Notes after design-review pass 12

- Pass 12 (architecture, ambiguity, inconsistency) added no design steps.
  `design-steps.md` has 33 checked items and none open. The design review has
  converged. The next step is `plan.md` and `steps.md` (not yet present).
- For the plan: the "For the plan" bullets in the "Design-steps work"
  sections above (passes 1–11) hold the code facts and file paths. Read them
  before you write `plan.md`. Do not derive them again.

## Plan review

- 2026-10-29 ambiguity review pass 1 (plan): added 5 new design steps
  (record sources, namespace time method, parallel call, cell description,
  open names).
  - Scry 0.1.61 source read from the local jar
    (`~/.m2/repository/org/hugoduncan/scry/0.1.61`): `scry/clojure_test.clj`
    `run` and `test-vars-with-output-owners`, `scry/cli/results.clj`.
  - Checked with no finding: Slice 1 cell counts of
    `fresh-policy-loader-rejection-test` (14 start labels 0, case labels 1)
    agree with the Invariants. The Slice 2 timeout rule agrees with Scope 4.
    The Slice 7 `release.yml` pair agrees with Scope 7 (each step sets
    `JAVA_CMD` itself, so any documented parent can own the cross-JVM step).
- 2026-10-29 inconsistency review pass 1 (plan): added 1 new design step
  (cell-result function for every cell child, not only grouped children).
  - Slice 7 lists `ramora/defvaluetype-design/assertions/fallback-architecture.md`
    from the pass-1 file list. That list came from a grep match on the test
    name `source-free-runtime-only-fallback-test` (line 25). The file names
    no suite, directory or fresh-JVM rule, so it needs no edit unless other
    text in it is false after the work. `git grep` for `test/compatibility`
    or `fallback-test` outside `munera/` and `mementum/` finds no file that
    the steps miss.

## Notes for the plan-steps work (after plan-review pass 1)

- Principles:
  - `design.md` is stable. Fix the 6 plan-review steps in `plan.md` and
    `steps.md`. Change `design.md` only if a step shows a real design gap.
  - Decide in this order: the parallel call and the cell description (the
    runner and harness API) first. Then the record method (record sources,
    child count, namespace time), which hooks into that API. Then the
    every-cell-child rule (inconsistency 1), which uses the same API.
  - Use one record method before and after the work where possible
    (acceptance 2 compares the two). The first record runs on the unchanged
    serial runner, so its hooks must not need the new runner.
  - Children return data and the parent asserts
    (`mementum/memories/child-jvm-checks-return-data.md`). Do not move `is`
    into worker threads.
- Scry 0.1.61 facts (jar `~/.m2/repository/org/hugoduncan/scry/0.1.61`,
  files `scry/clojure_test.clj`, `scry/capture.clj`, `scry/cli/results.clj`):
  - `run` binds `test/*testing-contexts*` and `*testing-vars*` to `(list)`,
    and `with-redefs` `test/report` (root), `test/test-var`, `test-vars`,
    `run-tests`. The `test/test-var` replacement closes over the root value
    found at run start. Thus a root wrapper installed earlier (for example
    when a test namespace or `jvm-runner` loads) runs for each Var.
  - The capture context is the dynamic Var `scry.capture/*capture-context*`.
    An `is` on a thread that does not convey bindings records nothing, and
    does not fail. `future` and `bound-fn` convey it.
  - `progress-callback` (option of `scry.core/run`) gets each Var entry in
    order, after the Var runs. This is a possible label source for an
    in-process run. The CLI does not expose it to tests.
- Project files: `test/compatibility/value_type/{jvm_runner,child_transfer_test,aot_test,aot_fixture,consumer_aot_checks}.clj`,
  `deps.edn` (`:fallback-test`), `.github/workflows/release.yml`
  (`JAVA_CMD` for each step).

## Plan-steps work (pass 1)

- 2026-10-29: the 6 plan-review steps are done in `plan.md` and `steps.md`.
  `design.md` is unchanged. Decisions: a permanent recorder
  `value-type.label-record`, active only with `VALUE_TYPE_TEST_RECORD`
  (replaces the removed temporary recorder and the `report`
  `:begin-test-ns` wrapper); `run-jvm` option `:kind` for the child kind;
  `jvm-runner/run-jvms` for parallel children; `value-type.cell-harness`
  (`cell-child`, `assert-child`) for every cell child; owner parent Java 8;
  new namespace `value-type.identity-receiving-cross-jvm-test`.
- Facts checked (Scry 0.1.61 jar, Clojure 1.12.6 jar):
  - `scry/clojure_test.clj` `resolve-vars` requires all namespaces in the
    `let` of `run`, before the `with-redefs`. The `test-var` replacement
    takes the root value at that point. Thus a root wrapper that is installed
    at namespace load runs for each Var.
  - `clojure.test/report` is `^:dynamic` (`clojure/test.clj` line 324). A
    `binding` in the `test-var` wrapper is innermost, so it sees each
    assertion event before Scry's capture.
  - No test in `test/compatibility` calls `run-jvm` from a `future` or a
    thread, and no namespace uses `use-fixtures`.
- For the `:kind` step (Slice 1): producer forms do not all call a function
  named `produce` (for example `field-transfer-test` line 11,
  `transfer-test`). AOT compile children are also started by direct
  `run-jvm` calls (`aot-test` lines 48, 89, 121; result `:compiled`). Read
  each `run-jvm` caller; do not classify by grep only.
- The first record now runs at the recorder commit, not at the commit
  before it. That commit changes no child, assertion or runner behavior.
- 2026-10-29 ambiguity review pass 2 (plan): added 4 new design steps
  (Slice 6 cell-failure data, kind of an AOT compile that no child loads,
  record event order, Slice 7 Ramora edits).
  - Checked with no finding: no cross-JVM test reads `:selected`,
    `:available?` or `:baseline?` of the parent (only child-side forms), so
    the three first-record runs can give equal cross-JVM labels.
    `fields-test` does not require `jvm-runner` (Slice 2 invalid-limit check
    holds). `run-check` is used only by the five cross-JVM tests of
    `identity-receiving-test`. `compile-producer` calls `compile-clojure`, so
    two `aot-fixture` functions call `run-jvm`.
- 2026-10-29 inconsistency review pass 2 (plan): added 2 new design steps
  (unreadable child result against the per-cell failure Invariants; all
  focused cross-JVM commands in `runtime.md`).
  - Checked with no finding: `warning-checks/concurrent-definitions` uses
    `bound-fn` and `concurrent-rejections` uses `future`, so their worker
    threads already get the cell `*out*`/`*err*`. Only `expansion-checks/on-thread`
    needs the Slice 3 change. Its `bound?` path also binds `Compiler/LOADER`
    on purpose; keep it. "Bind only `*out*`/`*err*`" means no `bound-fn`.

## Notes for the plan-steps work (after plan-review pass 2)

- Principles:
  - `design.md` is stable. Fix the 6 pass-2 steps in `plan.md` and
    `steps.md` only. The kind step has a design answer already (Concepts:
    "A child that compiles as its checked case is a cell child"), so it needs
    no design change.
  - Decide the two `assert-child` steps together: the cell-outcome data for
    Slice 6, and the unreadable-result case. Both define the input and the
    verdict of the cell-result function. One pure function from the
    `cell-child` map and the `run-jvm` result to the outcome of each cell
    lets `assert-child` make its `is` from it, and lets Slice 6 test the
    verdicts with no suite failure. Children still return data. No `is`
    runs on a worker thread.
  - If Slice 6 tests make no `cell!` records, their labels are support
    labels (inconsistency review pass 8 note). Keep this unless a step
    changes it explicitly.
  - Do not change the behavior of the first record. The event-order fix is a
    record-format change only (Slice 1).
- Facts from the code:
  - `run-jvm` reads `:result` only on exit 0. Empty stdout gives `nil` (no
    throw), because of `some->`. An invalid last line throws from
    `edn/read-string`. In a `future`, `deref` rethrows it in an
    `ExecutionException`.
  - `field-aot-test/persisted-restricted-field-output-test` (lines 21–40)
    uses the local `compile-producer` (line 15) with `:after`
    `c/inspect-output`. It starts no later child. It is the only `:after`
    caller of that kind. The other `:after` caller (line 112, compile of
    `restricted.consumer`) is an AOT compile child: a later child loads its
    output, so the `:source-absent?` check is a support assertion.
    `controlled-field-capability-test` uses only `:wrap`.
  - `runtime.md`: the receiving command (lines 120–123) runs in a loop over
    each parent. After the split, its cross-JVM part runs once. The
    "120-second timeout" text is at lines 218 and 255.
  - `artifacts.md` lines 25–26 are the layout entry that changes.
- Project files: `test/compatibility/value_type/{jvm_runner,aot_fixture,field_aot_test}.clj`,
  `doc/development/runtime.md`, `ramora/defvaluetype-design/embodiment/artifacts.md`,
  `ramora/INDEX.md` ("Documentation rules").

## Plan-steps work (pass 2)

- 2026-10-29: the 6 plan-review pass-2 steps are done in `plan.md` and
  `steps.md`. `design.md` is unchanged. Decisions:
  - `cell-harness/cell-outcomes`: pure outcome data for each cell;
    `assert-child` reports from it with `clojure.test/do-report`. Slice 6
    tests assert on this data, make no `cell!` record (support labels).
  - `:kind` option on `compile-clojure`/`compile-producer` (default
    `:aot-compile`). Checked-case compiles are `:cell`:
    `field-aot-test/persisted-restricted-field-output-test` and
    `aot-test/fallback-aot-compilation-test`. After the work both are
    one-cell children whose cell compiles (new `aot-fixture/compile-form`).
  - Record event order: `:assertion-index` in each `:children` entry.
  - Unreadable last stdout line: `:cell` child gives `:read-error`, no
    throw; producer and compile children throw after the call returns.
- Facts checked: every current `compile-java` output is loaded by a later
  child (`generated-value-aot-test`, `public-field-aot-test`,
  `identity-aot-test`, `field-aot-test`, `public-native-field-aot-test`,
  `public-mixed-field-aot-test`, `controlled-field-capability-test`).
  `persisted-restricted-field-output-test` is the only `compile-producer`
  call whose output no later child loads. `aot-test` direct compile calls:
  line 18 (cell), lines 48, 89, 121 (AOT compile).
- `runtime.md` focused cross-JVM commands: lines 83, 120–121, 136, 182,
  213, 250; "fresh JVMs" text at line 185. `artifacts.md` lines 25–26 is
  the only Ramora layout entry that changes. `fallback-architecture.md`
  needs no edit.
- Open risk: `compile` under the per-cell binding frame is not verified
  (`plan.md` Risks).
- 2026-10-29 ambiguity review pass 3 (plan): added 3 new design steps
  (new-Class check method and scope, Slice 6 failing-cell output and cell
  result entry form, Slice 6 Scope 3 test method).
  - Checked with no finding: the pass-2 `:kind` list. `compile-producer`
    callers other than `persisted-restricted-field-output-test` have a
    later child that loads the output (`field-aot-test` line 112 included).
    `jvm-config` children get the parent `java.class.path`, so under
    `:cross-jvm-test` they also get `test/cross-jvm`; check namespaces can
    stay in `test/compatibility`. The `fallback-aot-compilation-test` cell
    keeps its current `selected` configuration with `:jvm-opts []`. Thus
    each of its cells is a fallback cell under every parent.
- 2026-10-29 inconsistency review pass 3 (plan): added 1 new design step
  (the "every cell child" rule against the Slice 6 fixture children).
  - Checked with no finding: on a crash, `cell-outcomes` gives one failing
    `:result` check and no separate exit check for a one-cell child. The
    Invariant "makes the exit assertion ... under the cell label" still
    holds in effect: the failure is under the cell label, and its message
    shows the exit code. A `git grep` for `test/compatibility` or
    `fallback-test` outside `munera/` and `mementum/` finds no file that the
    steps miss (`spike/portable-fields/verify.sh` needs no change, as before).

## Notes for the plan-steps work (after plan-review pass 3)

- Principles:
  - `design.md` is stable. Fix the 4 pass-3 steps in `plan.md` and
    `steps.md` only.
  - Decide in this order. First the form of one cell result entry: the
    new-Class checks and the Slice 6 assertions both read it. Then the
    new-Class method. Then the two Slice 6 steps together (Scope 3 test
    method, "every cell child" exception). Both steps decide if a Slice 6
    test calls `assert-child`.
  - Keep `:expected` simple for the Slice 4 conversions. If the entry is a
    map (value, `:out`, `:err`, or `:thrown`), `:expected` applies to the
    value or the `:thrown` map. Text goes only to the `:out`/`:err` checks.
  - The new-Class check needs state for the whole child, kept on the child
    side (for example a `cell-harness` registry of the Classes that earlier
    cells returned). Cell code reports the check in its value. Do not make
    the check pass only for cells that define no type: state which cells
    are exempt (AOT consumers that load saved classes).
  - The Slice 6 fixture children have empty child-level stderr. Thus the
    Scope 2 support assertion can stay in each Slice 6 test without suite
    failure.
- Facts from the code (`test/compatibility/value_type/`):
  - Current identity checks: `:factory?` compares the class name
    (`aot_checks.clj` lines 41, 64) or the Class (`aot_checks.clj` line 94,
    `transfer_checks.clj` line 26, `warning_checks.clj` line 22).
    `field_transfer_checks.clj` lines 75–85 already checks `:new-class?`
    (`identical?` to the previous attempt in the same cell) and `:class?`
    for each construction. Use it as the model for the cross-cell form.
  - `aot_checks/compile-fixture` strict branch already returns `:mapped?`
    and `:loadable?` (absence of the rejected type) for each attempt.
- Project files: `test/compatibility/value_type/{aot_checks,transfer_checks,warning_checks,field_transfer_checks}.clj`.

## Plan-steps work (pass 3)

- 2026-10-29: the 4 plan-review pass-3 steps are done in `plan.md` and
  `steps.md`. `design.md` is unchanged. Decisions:
  - Cell result entry: `{:value v :out o :err e}` or `{:thrown {:class
    :message :chain} :out o :err e}`. `:expected` applies to `v` or
    `{:thrown ...}` only.
  - New-Class checks: child-side `value-type.cell-registry` (`new-class!`,
    JVM-wide synchronized identity set). Cell code reports `:new-class?`
    in its value; no harness check. Calls added per namespace in Slice 4.
    No check in AOT consumer cells and compile cells.
  - `:failure` holds the cell's own `:out`/`:err`/`:thrown` when the child
    gives an entry for the cell; else the process stdout/stderr.
  - Slice 6 tests (Scope 3 and runner outcomes) use `cell-child` and
    `run-jvms`, assert inline, no `assert-child`, no `cell!`; each calls
    the new public `assert-child-stderr`. The Scope 3 test states each
    cell entry itself, then checks `cell-outcomes` agrees.
- Facts for implementation:
  - Only `identity-loader-checks` defines types and is shared across test
    namespaces (with in-process `identity-loader-test`, via
    `public-stale-sibling-expected`). `consumer-aot-checks` (14 users)
    `eval`s only `fn` forms, no types.
  - An AOT-compiled function that contains `deftype` returns the same Class
    on each call (`aot-checks/invoke-runtime-fixture` calls it twice), so a
    new-Class check there fails on correct behavior.
  - `run-jvm` reads the stdout/stderr files after `destroyForcibly`, so a
    timed-out child keeps text that it flushed before the kill.
  - Fixed names in check code collide in a grouped child, for example
    `transfer-checks/consume` (`transfer.<mode><index>` namespaces) and
    `array-matrix-checks` (`MatrixPoint`, unique only if defined in the cell
    namespace). Fix them in the Slice 4 conversion.
- 2026-10-29 ambiguity review pass 4 (plan): added 1 new design step
  (new-Class and absence keys against the current result shapes).
  - Checked by a run (Java 25, no preview, `error` consumer of a silent
    `macroexpand` expansion): a rejected `(eval (list 'fn [] expansion))`
    and a call of it leave `user.Transferred` unloadable through
    `RT/baseLoader`. Thus the plan `:absent?` form also holds for
    function-contained (`:analysis`) transfer rejections; only AOT cells
    need the exemption.
  - `source-checks` defines `Ordinary` and `Portable` at namespace top
    level. Its only caller is the one-cell
    `runtime-test/fallback-source-compatibility-test`, so its fixed names
    do not collide; its `new-class!` calls go after those top-level forms.
  - `expansion-checks/attempt` binds `*ns*` to `value-type.expansion-checks`,
    not to the cell namespace. Its type names are fixed per label, so it is
    one more fixed-name case for the Slice 4 conversion.
- 2026-10-29 inconsistency review pass 4 (plan): added 2 new design steps
  (compile-cell new-Class exemption against the plan scope rule and the
  design; `:absent?` model `transfer-checks/absent?` checks no factory).
  - Checked with no finding: `constrained-discarded-with-loader` calls
    `on-thread`, so the Slice 3 `on-thread` change covers both names in
    that step.

## Notes for the plan-steps work (after plan-review pass 4)

- Principles:
  - `design.md` is stable. Fix the 3 pass-4 steps in `plan.md` and
    `steps.md` only. Keeping the compile-cell exemption is the one choice
    that also needs a deviation record in `implementation.md`.
  - Decide the 3 steps together. They all set the form of the new-Class
    and absence report: first the parts of `:absent?`, then the key form
    for each definition (and the current absence keys), then the compile
    cells.
  - Prefer one key form for each definition map. Record each rename or
    stronger check as a changed expectation (Invariants). Do not keep two
    keys that check the same thing.
  - The check must not fail on correct behavior. The AOT consumer exemption
    stays.
- Facts from the code (`test/compatibility/value_type/`):
  - Current code already has the full absence model in two places:
    `warning_checks.clj` `concurrent-rejections` (`:factory-absent?` by
    `ns-resolve '->Transferred`, `:owner-absent?` by `Class/forName` with
    `RT/baseLoader`), and `expansion_checks.clj` lines 121–122
    (`:owner-absent?` = import and class name, `:factory-absent?` =
    `->Name`). Use one of them as the model, not `transfer-checks/absent?`.
  - `aot_checks.clj` `compile-fixture`: the strict branch compiles twice
    in one cell (`:chain`, `:emitted?`, `:mapped?`, `:loadable?`); the
    non-strict branch compiles once and compares `:factory?` by class
    name. After `compile`, `(ns-resolve fixture 'AotIdentity)` gives the
    Class for a `new-class!` call.
  - `consumer_aot_checks.clj` `builders` constructs the owner through the
    factory, `new` and reflection. `child-transfer-checks/consume` uses
    it, but checks only `:retained?`, not the Class of the result.

## Plan-steps work (pass 4)

- 2026-10-29: the 3 plan-review pass-4 steps are done in `plan.md`
  ("New-Class checks", "Conversion order", Risks) and `steps.md` (Slices 3
  and 4). `design.md` is unchanged. Decisions:
  - `cell-registry/absent?` (import, `->Name`, class name through
    `RT/baseLoader`) is the one absence key `:absent?`. All current boolean
    absence keys move to it; `transfer-checks/absent?` changes to it.
  - `:factory?` = import resolves to the defined Class and `->Name`
    constructs exactly that Class. It replaces presence checks
    `:absent? false`, because it implies the import.
  - Multi-definition cells without a map per definition get `:definitions`
    (simple type name → definition map).
  - Compile cells make the new-Class check (option 1 of the step); no
    deviation record is needed. Only AOT consumer cells are exempt.
- Facts for Slice 4:
  - `aot-test` line 31 and `field-aot-test` lines 33–40 hold the compile-cell
    expectations that change.
  - Absence expectations to rewrite: `child-transfer-test` 97–98,
    `transfer-test` 25 and 110–111, `field-transfer-test` 27, 121, 178,
    `runtime-test` 72, 103, 105, 121, 131, 159, 182, 207, 213; presence
    `:absent? false` at `runtime-test` 135 and 162.
  - Not changed: `identity-receiving-checks/strict-nested` `:owner`/`:factory`
    (resolved values of expansions); `:source-absent?` keys (fixture source
    absence); `field-transfer-checks` `:initially-absent?` (state before the
    first attempt, not a rejection); `runtime-test` line 88 `:absent` (a
    lookup value, not a key).
- 2026-10-29 ambiguity review pass 5 (plan): added 2 new design steps
  (unique class names where check code binds a fixed `*ns*`; placement of
  non-map repeated attempts).
  - Collision found by reading the `doseq` axes: in
    `public-field-evaluation-policy-test`, `tag` is a cell input, not child
    configuration. Cross-JVM `identity-receiving-test` tests run one cell
    for each configuration, so `identity-receiving-checks` fixed names do
    not collide today.
- 2026-10-29 inconsistency review pass 5 (plan): added 1 new design step
  (`public-definition-test` `Sample` keys: `:definitions` against the
  in-place `:absent` rename).
  - Checked with no finding: the plan claim that
    `identity-loader-checks/public-stale-sibling` is the only shared check
    function that defines types holds. A `grep` for `transfer-checks` also
    matches `child-transfer-checks` and `field-transfer-checks`, but only
    `transfer-test` calls `value-type.transfer-checks`.

## Notes for the plan-steps work (after plan-review pass 5)

- Principles:
  - `design.md` is stable. Fix the 3 pass-5 steps in `plan.md` and
    `steps.md` only.
  - Decide in this order. First the class-name rule (ambiguity 1): it sets
    the namespace that `absent?` and `:factory?` use. Then one placement
    rule for attempt and outcome values that are not maps (ambiguity 2).
    Apply it to `public-definition-test` (inconsistency 1). One rule must
    cover both cases.
  - Prefer the smallest rule: check code defines in `*ns*` at cell start
    (the cell namespace), or makes its fixed namespace name from it. Do not
    add a second naming mechanism.
  - Do not add `:absent?` to the shared `consumer-aot-checks/failure`. AOT
    consumer cells use it too, and they are exempt. Add the key in the
    caller (`field-policy-checks/attempts`, the inline `catch` in
    `public-definition-test`).
  - Each moved or renamed key is a changed expectation (Invariants).
- Facts from the code (`test/compatibility/value_type/`):
  - Fixed-`*ns*` sites: `field_policy_checks.clj` line 32,
    `identity_receiving_checks.clj` lines 15 and 21, `expansion_checks.clj`
    lines 45, 67, 95, 106, 135. Fixed `create-ns`: `transfer_checks.clj`
    line 43, `warning_checks.clj` lines 9 and 66. `gensym` namespaces
    (no change): `identity_loader_checks.clj`, the in-process tests.
  - A collision today needs two cells of one child that run the same
    code. Found: `field-capability-test/public-field-evaluation-policy-test`
    (axis `tag`). `field-policy-checks/expansion` (`public-field-policy-test`)
    only expands, so it defines nothing. The `warning-checks` users are the
    `transfer-test` concurrent tests (rule 5, one cell for each child).
  - `field-capability-test` builds the expected rejection in the parent
    with `checks/policy-rejection` (`{:class :message}`). Success attempts
    are vectors of 3 construction maps.
  - `runtime-test/public-definition-test` and `public-require-test` loop
    over policy with no `testing`. Thus each has one first-record label
    with cell count 3. After the work it maps to 3 cell labels.
- Project files: `test/compatibility/value_type/{field_policy_checks,field_capability_test,consumer_aot_checks,runtime_test,expansion_checks,identity_receiving_checks,transfer_checks,warning_checks}.clj`.

## Plan-steps work (pass 5)

- 2026-10-29: the 3 plan-review pass-5 steps are done in `plan.md` ("Cell
  harness" child form and "Class names"; "New-Class checks" "Key
  placement" and "Current absence keys") and `steps.md` (Slices 3 and 4).
  `design.md` is unchanged. Decisions:
  - Class names: cell code defines in the cell namespace or in
    `<cell namespace>.<suffix>`. Check code with a fixed `*ns*` or fixed
    `create-ns` reads `*ns*` once on the cell thread. Applied uniformly,
    also where no collision exists today (`identity-receiving-checks`).
    The harness refers `clojure.core` in each cell namespace, because
    `transfer-checks` and `warning-checks` evaluate `let` forms in the
    namespace where they define.
  - A non-map attempt or outcome value of one definition becomes its
    definition map: `field-policy-checks/evaluation` attempts
    (`:constructions`), `public-definition-test` `:outcome` (`:field`,
    `:equal?`). `:definitions` is only for cells with more than one
    definition and no map for each.
- Facts checked:
  - A namespace named `cell.0` works for `deftype` (class `cell.0.Foo`
    loads through `RT/baseLoader`) and `ClassDesc/of "cell.0.Foo"`
    accepts the name (JDK 25).
  - Collisions in a grouped child without the rule:
    `field-capability-test/public-field-evaluation-policy-test` (tag),
    `runtime-test/constrained-discarded-expansion-test` (entry point ×
    loader binding), `transfer-test/constrained-concurrent-strict-test`
    (serialized form, `strict.worker<index>`).
- Open for the harness (not in a plan step yet):
  `transfer-test/constrained-concurrent-strict-test` calls
  `(shutdown-agents)` in its child form today (`transfer_test.clj` line
  107), because `future` threads of `concurrent-rejections` are not
  daemon threads and delay JVM exit. In a grouped child, a cell must not
  call it (later `future` calls fail). The child form must call
  `shutdown-agents` once, after it prints the results.

## Plan reviews (pass 6)

- 2026-10-29 ambiguity review pass 6 (plan): added 1 new design step
  (harness child-form `shutdown-agents` rule; promotes the pass-5 "Open
  for the harness" note into a plan step).
  - Checked with no finding: `:read-error` covers unprintable cell values;
    `cell-outcomes` warn `*err*` rule covers `:warning` `:any`;
    semaphore/timeout interaction and `:fixture-ns` label flow are stated.
- 2026-10-29 inconsistency review pass 6 (plan): no new design steps.
  - Checked with no finding: Scope 3 Error forms vs the `:thrown` map and
    Slice 6 `:rejection`; one-cell exit check vs the crash `:no-result`
    rule; `persisted-restricted-field-output-test` `:kind :cell` (Slice 1)
    vs no `compile-producer` (Slice 4) is temporal, not a conflict; Slice 6
    fixture-children exception matches "Record after the work".

## Notes for the plan-steps work (after plan-review pass 6)

- Principles:
  - `design.md` is stable. Fix the 1 pass-6 step in `plan.md` ("Cell
    harness" child form) and `steps.md` (Slice 3 child-form step, Slice 4
    `transfer-test` item) only.
  - Put the exit rule in the harness, not in cell forms: no cell form may
    call `shutdown-agents` (later cells' `future` calls fail after pool
    shutdown).
  - The call must come after the EDN result line is printed and flushed;
    the parent reads the last stdout line, so order matters.
- Facts for the decision:
  - An unconditional `shutdown-agents` after the print is safe in children
    whose cells use no agents (the call is idempotent and cheap), so no
    per-child flag is needed.
  - Without the call, the JVM waits for the agent pool idle timeout
    (~60 s per `clojure.core/shutdown-agents` docs) only when a cell used
    `future`/agents; with the timeout rule this can push a grouped child
    past its bound.
  - Today only `transfer-test` child forms call `shutdown-agents`
    (`transfer_test.clj` line 107); `warning-checks` is the only check
    namespace that starts `future` threads.
- Project files: `test/compatibility/value_type/transfer_test.clj`,
  `test/compatibility/value_type/warning_checks.clj`.

## Plan-steps work (pass 6)

- 2026-10-29: resolved the pass-6 `shutdown-agents` step. Rule now in
  `plan.md` "Cell harness" (child form): print EDN result line, flush
  stdout, call `shutdown-agents` exactly once, exit; no cell form calls
  it. `steps.md` updated: Slice 3 child-form step carries the rule; the
  Slice 4 `transfer-test` item removes the `(shutdown-agents)` call from
  the `constrained-concurrent-strict-test` child form. The pass-5 "Open
  for the harness" note above is now covered by plan steps.
- All design-steps.md items are now checked. No unchecked plan-review
  follow-ups remain; the task is ready for implementation (Slice 1).

## Plan reviews (pass 7)

- 2026-10-29 ambiguity review pass 7 (plan): no ambiguity review feedback.
- 2026-10-29 inconsistency review pass 7 (plan): no inconsistency review feedback.
  - Checked with no finding: constrained-concurrent-warning-scope-test
    first-record count 2 vs the "no cell label above 1" after-record check
    (the pass-9 split model and "sum >=" mapping cover it); Slice 1 :kind
    vs Slice 4 conversions; limit-1 commands vs "Owner parent"; one-cell
    exit check vs multi-cell support assertions.

## Notes after plan-review pass 7

- Both pass-7 reviews added no design steps: no plan-steps work remains.
  All design-steps.md items stay checked; the task is ready for Slice 1.
- Plan review has converged (passes 6 and 7 clean). Later reviews should
  prefer targeted checks of code facts over full re-reads; the pass-7
  spot-checks confirmed the plan's code references
  (test/compatibility/value_type/{transfer_test,aot_test,jvm_runner}.clj).
- Principle for implementation slices: the split-label rule (pass-9 model,
  "sum >=" mapping) is what keeps first-record labels with count > 1
  (for example constrained-concurrent-warning-scope-test) consistent with
  the after-record "no cell label above 1" check; conversions must give
  each cell of such a child a distinct :context.

## Slice 1 — recorder and first record (implementation pass 1)

- Machine: Apple M5 Max (Mac17,7), 18 CPUs, macOS 27.0.1.
- JDK builds: Java 8 `Temurin 1.8.0_492-b09`
  (`~/.local/share/mise/installs/java/temurin-8/bin/java`); EA3
  `27-jep401ea3+1-1` (`~/.local/share/mise/installs/java/openjdk-27.0.0-jep401ea3+1-1/bin/java`).
  `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA` point at these;
  they are not set in the default shell environment.
- First-record commit: `e61905d`. The record ran in a detached worktree at
  that commit (`git worktree add /tmp/vt-first e61905d`), so later edits did
  not change the measured code. Do the same for the after record if work
  continues in parallel.
- Totals (`/usr/bin/time -p` real, all exit 0): `fallback-java8` 1559.49 s,
  `fallback-preview-off` 1245.62 s, `fallback-preview-on` 1255.23 s; sum
  4060.34 s. 25% target for the after record (same three commands plus
  `cross-jvm`): ≤ 1015 s.
- Child starts: 1013 for each command (all kinds).
- Full summaries (`summary :start-labels`, labels with cell counts):
  `first-record/<command>.edn` in this task directory.
- Recorder notes: Var symbols are `ns/name`; labels are
  `[var-symbol contexts-vector]`. `summary` returns
  `{ns {:ms :children {[test kind] {:count :configurations}} :labels}}`.
  The `:start-labels` method for `loader-policy-test` gives 14 start labels
  with count 0 and 84 nested labels with count 1.
- Scry check: `scry.core/run` with a single `:namespaces` entry gives
  per-assertion detail (multi-namespace scope is compact: no
  `:assertions`). Labels and types were equal to the record on
  `loader-policy-test`.

### First-record per-namespace summary

### fallback-java8
| namespace | ms | children (cell/producer/aot/java) | labels | cells |
|---|---|---|---|---|
| value-type.aot-test | 59754 | 15/0/4/0 | 18 | 15 |
| value-type.array-boundary-test | 3879 | 2/0/0/0 | 1 | 2 |
| value-type.array-info-boundary-test | 37593 | 39/0/0/0 | 39 | 39 |
| value-type.array-matrix-test | 39414 | 36/0/0/0 | 36 | 36 |
| value-type.array-sequence-boundary-test | 38477 | 36/0/0/0 | 36 | 36 |
| value-type.capture-failure-test | 9 | 0/0/0/0 | 1 | 0 |
| value-type.capture-loading-test | 4266 | 2/0/0/0 | 2 | 2 |
| value-type.child-transfer-test | 268410 | 180/4/0/0 | 94 | 180 |
| value-type.controlled-field-capability-test | 56218 | 54/1/2/1 | 39 | 54 |
| value-type.field-aot-test | 16402 | 10/0/5/1 | 11 | 10 |
| value-type.field-capability-test | 92021 | 63/0/0/0 | 63 | 63 |
| value-type.field-transfer-test | 351272 | 222/8/0/0 | 180 | 222 |
| value-type.fields-test | 179 | 0/0/0/0 | 82 | 0 |
| value-type.function-redefinition-test | 6996 | 6/0/0/0 | 6 | 6 |
| value-type.generated-value-aot-test | 22270 | 18/0/3/1 | 9 | 18 |
| value-type.identity-aot-test | 26005 | 12/0/2/1 | 2 | 12 |
| value-type.identity-backend-test | 160 | 0/0/0/0 | 20 | 0 |
| value-type.identity-loader-test | 78 | 0/0/0/0 | 9 | 0 |
| value-type.identity-receiving-test | 53271 | 30/0/0/0 | 57 | 30 |
| value-type.isolated-sibling-loader-test | 200 | 0/0/0/0 | 4 | 0 |
| value-type.loader-policy-test | 22897 | 14/0/0/0 | 98 | 84 |
| value-type.public-field-annotations-test | 76 | 0/0/0/0 | 6 | 0 |
| value-type.public-field-aot-test | 127728 | 86/0/10/2 | 16 | 86 |
| value-type.public-mixed-field-aot-test | 36807 | 32/0/5/1 | 11 | 32 |
| value-type.public-native-field-aot-test | 34687 | 30/0/4/1 | 10 | 30 |
| value-type.runtime-test | 146008 | 41/0/0/0 | 35 | 41 |
| value-type.transfer-test | 96420 | 29/0/0/0 | 19 | 29 |
Total children: 1013
### fallback-preview-off
| namespace | ms | children (cell/producer/aot/java) | labels | cells |
|---|---|---|---|---|
| value-type.aot-test | 15675 | 15/0/4/0 | 18 | 15 |
| value-type.array-boundary-test | 4042 | 2/0/0/0 | 1 | 2 |
| value-type.array-info-boundary-test | 34097 | 39/0/0/0 | 39 | 39 |
| value-type.array-matrix-test | 34895 | 36/0/0/0 | 36 | 36 |
| value-type.array-sequence-boundary-test | 34368 | 36/0/0/0 | 36 | 36 |
| value-type.capture-failure-test | 1 | 0/0/0/0 | 1 | 0 |
| value-type.capture-loading-test | 3716 | 2/0/0/0 | 2 | 2 |
| value-type.child-transfer-test | 258045 | 180/4/0/0 | 94 | 180 |
| value-type.controlled-field-capability-test | 52113 | 54/1/2/1 | 39 | 54 |
| value-type.field-aot-test | 15099 | 10/0/5/1 | 11 | 10 |
| value-type.field-capability-test | 88686 | 63/0/0/0 | 63 | 63 |
| value-type.field-transfer-test | 338699 | 222/8/0/0 | 180 | 222 |
| value-type.fields-test | 19 | 0/0/0/0 | 82 | 0 |
| value-type.function-redefinition-test | 6876 | 6/0/0/0 | 6 | 6 |
| value-type.generated-value-aot-test | 22459 | 18/0/3/1 | 9 | 18 |
| value-type.identity-aot-test | 25506 | 12/0/2/1 | 2 | 12 |
| value-type.identity-backend-test | 6 | 0/0/0/0 | 20 | 0 |
| value-type.identity-loader-test | 11 | 0/0/0/0 | 9 | 0 |
| value-type.identity-receiving-test | 50943 | 30/0/0/0 | 57 | 30 |
| value-type.isolated-sibling-loader-test | 26 | 0/0/0/0 | 4 | 0 |
| value-type.loader-policy-test | 21480 | 14/0/0/0 | 98 | 84 |
| value-type.public-field-annotations-test | 16 | 0/0/0/0 | 6 | 0 |
| value-type.public-field-aot-test | 119468 | 86/0/10/2 | 16 | 86 |
| value-type.public-mixed-field-aot-test | 34808 | 32/0/5/1 | 11 | 32 |
| value-type.public-native-field-aot-test | 32171 | 30/0/4/1 | 10 | 30 |
| value-type.runtime-test | 14805 | 41/0/0/0 | 35 | 41 |
| value-type.transfer-test | 34227 | 29/0/0/0 | 19 | 29 |
Total children: 1013
### fallback-preview-on
| namespace | ms | children (cell/producer/aot/java) | labels | cells |
|---|---|---|---|---|
| value-type.aot-test | 16599 | 15/0/4/0 | 18 | 15 |
| value-type.array-boundary-test | 4018 | 2/0/0/0 | 1 | 2 |
| value-type.array-info-boundary-test | 35351 | 39/0/0/0 | 39 | 39 |
| value-type.array-matrix-test | 34668 | 36/0/0/0 | 36 | 36 |
| value-type.array-sequence-boundary-test | 36156 | 36/0/0/0 | 36 | 36 |
| value-type.capture-failure-test | 1 | 0/0/0/0 | 1 | 0 |
| value-type.capture-loading-test | 3482 | 2/0/0/0 | 2 | 2 |
| value-type.child-transfer-test | 260402 | 180/4/0/0 | 94 | 180 |
| value-type.controlled-field-capability-test | 52039 | 54/1/2/1 | 39 | 54 |
| value-type.field-aot-test | 15319 | 10/0/5/1 | 11 | 10 |
| value-type.field-capability-test | 93149 | 63/0/0/0 | 63 | 63 |
| value-type.field-transfer-test | 329979 | 222/8/0/0 | 180 | 222 |
| value-type.fields-test | 28 | 0/0/0/0 | 82 | 0 |
| value-type.function-redefinition-test | 6706 | 6/0/0/0 | 6 | 6 |
| value-type.generated-value-aot-test | 21308 | 18/0/3/1 | 9 | 18 |
| value-type.identity-aot-test | 26451 | 12/0/2/1 | 2 | 12 |
| value-type.identity-backend-test | 8 | 0/0/0/0 | 20 | 0 |
| value-type.identity-loader-test | 15 | 0/0/0/0 | 9 | 0 |
| value-type.identity-receiving-test | 49956 | 30/0/0/0 | 57 | 30 |
| value-type.isolated-sibling-loader-test | 20 | 0/0/0/0 | 4 | 0 |
| value-type.loader-policy-test | 22842 | 14/0/0/0 | 98 | 84 |
| value-type.public-field-annotations-test | 19 | 0/0/0/0 | 6 | 0 |
| value-type.public-field-aot-test | 119804 | 86/0/10/2 | 16 | 86 |
| value-type.public-mixed-field-aot-test | 32925 | 32/0/5/1 | 11 | 32 |
| value-type.public-native-field-aot-test | 31467 | 30/0/4/1 | 10 | 30 |
| value-type.runtime-test | 15553 | 41/0/0/0 | 35 | 41 |
| value-type.transfer-test | 34813 | 29/0/0/0 | 19 | 29 |
Total children: 1013

## Slices 2–3 — runner and harness (implementation pass 1)

- `jvm-runner`: `limit` (public Var), one shared `Semaphore`; `run-jvms`
  derefs futures in input order, then throws for a non-`:cell` child with
  `:read-error`. Non-zero exit gives `:result nil`. `run-jvm` docstring
  states the read and timeout rules.
- `aot-fixture`: `compile-form` returns the data form (callers `pr-str`);
  `compile-clojure-child` / `compile-java-child` return option maps;
  `compile-clojure` / `compile-java` run them.
- `cell-harness`: `cell-child` keeps `:cells` and `:configuration` in the
  option map (extra keys are ignored by the runner). Cell `:form` is data
  (printed with `*print-meta*`) or a source string; the child evaluates it
  with `load-string` in the cell frame, so symbols resolve in the cell
  namespace. Top-level `LOADER` is the bound value, else `RT/baseLoader`;
  each cell gets `(DynamicClassLoader. top-loader)` and all thread bindings
  captured at child top level. Added `run-cells` (run-jvms + assert-child
  for each child) for conversions.
- REPL check (Java 8): 3-cell `warn` child — first cell `:err` is
  `value-type: identity deftype fallback: java/lang/classfile/ClassFile\n`
  (Java 8 context is the missing class, not "fixed fallback
  representation"), later cells `""`; `error` child — each cell throws with
  chain `[Compiler$CompilerException java.lang.Error]`; `silent` — each
  `{:new-class? true :factory? true}`.
- Not yet done: Slice 2 verification runs (limit 1 vs default, invalid
  limit with Scry) and Slice 3 "verify with one converted test".

## Slice 4 — child-transfer-test (implementation pass 2)

- `cell-registry/defined` (new): `(defined ns simple-name class & factory-args)`
  → `{:new-class? :factory?}`; calls `new-class!` once. Use it for every
  successful definition in later conversions.
- Producer: `child-transfer-checks/produce [native? specs]`, spec
  `["cell.<i>" :macroexpand|:macroexpand-1]`; it expands in namespace
  `cell.<i>` = consumer cell namespace, so the expansion class name is
  `cell.<i>.TransferParent` and `absent?` with its default class name fits.
  One producer per test gives 6 expansions shared by all children of the
  test (different JVMs; unique within a child).
- Cells: 6 per child (mode × expand). Label mapping: old label
  `(pr-str [... mode])` → 2 cells `[old-label "macroexpand"]`,
  `[old-label "macroexpand-1"]`. 248 labels, max cell count 1, 180 cells.
- Changed expectations: `consume` result gains `:definitions {TransferChild
  {:new-class? true :factory? true} TransferParent {...}}`;
  `reject-replaced-child` result changes from attempt vector to
  `{:definitions {TransferChild ...} :attempts [...]}`, each attempt
  `:owner-absent?`/`:factory-absent?` → `:absent?` (adds class-name part);
  warn `*err*` check changes from count of warning lines to exact line
  `value-type: identity deftype fallback: fixed fallback representation`
  on the first cell, `""` after (all three runtimes). Producer
  `:err ""` check added for the baseline producers (support label
  `"producer"`).
- Warn-guard: each warn cell has the matching error cell in
  `strict-receiver-child-replacement-test` (same runtimes, execution,
  producer, form, mode). No rule-5 exception.
- Children: 30 cell (12+6+6+6) + 4 producer; namespace 25 s (first record
  260 s). Limit 1 vs 4: equal `:assertions` label vectors.
- Verification needs `JAVA_CMD` = Java 8 path; `VALUE_TYPE_BASELINE_JAVA`
  is unset in this shell, `jvm-config` default path is used.

## Slice 4 — field-transfer-test (implementation pass 3)

- `field-transfer-checks`: `produce [tag specs]` and `produce-mixed [specs]`
  take specs `[namespace-name expand]` and expand in that namespace (the
  cell namespace), so class names are `cell.<i>.TransferredField`.
  `consume [expansions mode]` (one mode per cell) returns `{:capability
  :attempts [[a a] [a a]]}`; was a map of all modes.
- Finding: `:analysis` as `((eval (fn [] expansion)))` gives a stale import
  on the second definition in one cell (`:factory? false`): the call runs
  outside the eval `LOADER` frame, and `Class/forName` through the cell
  loader returns the first Class (plain `deftype` does the same; Clojure
  behavior, not `src/`). The old test did not check the import. Fix in test
  code: `:analysis` evaluates `((fn [] expansion))` with `eval`, so the
  definition and call run in one eval frame. Apply this to other
  converted checks that define twice through a function call.
- Cells and labels (old label kept as first context):
  - unsupported-public: child per [runtime exec policy] (12); cells tag × mode,
    context `[old (str tag) (name mode)]` (old label had 2 starts, one per tag).
  - supported-public: child per [exec policy] (6); 12 cells tag × mode ×
    expansion, context `[old (str tag)]`.
  - identity-public: child per [runtime exec policy] (12); 6 cells, context = old label.
  - strict-identity: child per [runtime exec] (6); cells per mode, `[old (name mode)]`.
  - mixed-public: child per [exec policy] (6); 6 cells, context = old label.
  - unsupported-mixed: child per [runtime exec policy] (12); cells per mode.
- Changed expectations: attempts gain `:factory?`; `:new-class?` by
  `new-class!` (was not-identical to previous attempt); `:absent?` and
  `:initially-absent?` by `cell-registry/absent?` (adds `->Name` and class
  name); unavailable class name is `cell.<i>.<Name>` (was `user.<Name>`);
  identity warn `*err*`: exact line `fixed fallback representation` on the
  first cell, `""` after (was count 1 per child); producer `:err ""` added.
- Warn-guard: identity warn cells match strict-identity error cells (same
  runtimes, exec, baseline producer, modes). Unsupported warn cells are not
  fallback cells (`:err ""`). No rule-5 exception.
- Record: 426 labels, max cell count 1, 306 cells; limit 1 vs 4 equal
  `:assertions` label vectors.

## Slice 4 — public-field-aot-test (implementation pass 4)

- Shared form API change: `consumer-aot-checks/*-form` and
  `function-aot-checks/invoke-install-form` return value forms (no `prn`);
  `aot-fixture/reload-form` returns a data form with value
  `{:before v :after v}` (was printing source read by `with-out-str`).
  New `aot-fixture/printed form` gives `(prn (eval 'form))` source for
  unconverted `run-jvm` callers (`eval` keeps top-level `do` require
  order; a plain `(prn (do (require ..) ..))` fails to resolve vars).
  Unconverted callers (identity-aot, generated-value-aot, public-mixed,
  public-native, field-aot, controlled-field-capability) use `printed`;
  all pass under Java 8 parent. Converting them: drop `printed`.
- Each consumer is a one-cell child (rule 4); `run-cells` runs all
  children of a test in parallel. Strict retry stays in one cell (rule 5).
- Labels: old label + context `(pr-str [java jvm-opts policy])` per cell;
  incompatible test keeps `"<java> / <policy>"` then that context.
  Compile assertions under `[old "producer"]`, javac under `[old "javac"]`
  (support labels). Persisted-output inspections are one-cell children.
- Changed expectations: warn `*err*` exact line on the cell (same text);
  strict-test checks become one `:expected` predicate (`strict-loads?`);
  persisted inspection checks become `persisted-output?`. No new-Class
  keys (AOT consumer cells are exempt).
- Warn-guard: warn cells of `source-free-public-identity-test` and
  `function-public-identity-aot-test` match error cells of
  `strict-public-identity-aot-test` / the function test `error` cells.
- Record: 194 labels, 86 cells, max cell count 1; namespace 75 s.

## Slice 4 — array-boundary, capture-loading, array-sequence-boundary (implementation pass 5)

- All three: each child is a one-cell child (rule 4: namespace load and
  require order are process-wide). Child counts unchanged: 2, 2, 36.
- Labels: `array-boundary-test` gains context `(pr-str [java jvm-opts])`
  (old: no context). `capture-loading-test` context `java` (unchanged).
  `array-sequence-boundary-test` context unchanged.
- Changed expectations: inline `is` checks become one `:expected`
  predicate per cell; "no `fallback` in err" becomes exact empty cell
  `*err*` (stronger); `:exit` becomes the cell exit check.
- `array-sequence-checks/check`: supported rows add
  `:definitions {SequencePoint (cell-registry/defined ...)}`; expected
  `{:new-class? true :factory? true}`; unsupported rows have no key.
- Verified under Java 8 parent: 10, 10, 180 assertions pass.
- Remaining in this step: `array-info-boundary-test`, `array-matrix-test`,
  `controlled-field-capability-test`, `field-aot-test`,
  `field-capability-test`, `function-redefinition-test`,
  `generated-value-aot-test`, `identity-aot-test`, `loader-policy-test`,
  `public-mixed-field-aot-test`, `public-native-field-aot-test`.

## Slice 4 — array-info-boundary-test (implementation pass 6)

- Each child is a one-cell child (rule 4: require order and policy are
  process-wide). Child count unchanged: 36 + 3 = 39 (was 39).
- Cell forms are data forms (syntax-quoted control form). Each call is in a
  later form of a top-level `do`, after its `require`; `load-string`
  evaluates the `do` form by form, so no `resolve` is necessary (review
  pass 9 removed it).
- Labels: `safe-inspection-boundary-test` context
  `(pr-str [label policy require-order])` and
  `export-only-capabilities-test` context `policy` (both unchanged).
- Changed expectations: inline `is` checks become one `:expected`
  predicate (or `=` map) per cell; "no `fallback` in err" becomes exact
  empty cell `*err*` (stronger); exit becomes the cell exit check. No type
  definitions, so no new-Class keys.
- Verify: export `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA`
  first (paths in "Slice 1"); `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"` with
  the variable unset runs the parent on the default `java`, and
  `jvm-config/configuration` throws.
- Verified under Java 8 parent: 195 assertions pass.

## Slice 4 — array-matrix-test (implementation pass 7)

- Each child is a one-cell child (rule 4: require order and policy are
  process-wide). Child count unchanged: 36.
- Labels: context `(pr-str [jvm-opts reason policy require-order])`
  (unchanged).
- Changed expectations: inline `is` checks become one `:expected`
  predicate per cell; "no `fallback` in err" becomes exact empty cell
  `*err*` (stronger); exit becomes the cell exit check.
- `array-matrix-checks/allocation`: the supported-row outcome map is the
  definition map of `MatrixPoint` (`cell-registry/defined` keys merged in);
  expected `:new-class? true :factory? true` added.
- Verified under Java 8 parent: 180 assertions pass.

## Slice 4 — controlled-field-capability-test (implementation pass 8)

- Children: construction 6 (2 cells each; was 6 children, 2 results each);
  receiving 18 → 6 (6 cells each) + 1 producer; AOT 6 and Java AOT 6
  one-cell children (rule 4: saved class loads once per JVM) + 2 aot + 1
  java. Total cell children 36 → 24.
- Labels: construction `[(pr-str [policy execution])]` → that context plus
  `"macroexpand-1"` / `"macroexpand"`; receiving
  `[(pr-str [policy execution analysis])]` → plus the expansion name.
  AOT labels unchanged.
- Producer expands each receiving expansion in namespace `cell.<i>` (the
  consumer cell namespace), so each cell has its own class name.
- Changed expectations: construction and receiving cells add
  `:new-class? true :factory? true` (`cell-registry/defined`, factory arg
  `(Long/valueOf 7)`); "no `fallback` in err" becomes exact empty cell
  `*err*` (stronger); the producer err check becomes `= ""`. AOT cells: no
  new-Class keys.
- Warn guard: no cell is a fallback cell (controlled absence keeps value
  storage), so no warn match needed.
- Verified under Java 8 parent: 253 assertions pass, 16 s.

## Slice 4 — function-redefinition-test (implementation pass 9)

- Children: 6 (policy × execution, process-wide), unchanged; each child
  now has 2 cells (deftype, defvaluetype), each cell defines twice.
- Labels: `[(pr-str [:controlled-capability policy execution])]` → that
  context plus `"deftype"` / `"defvaluetype"`.
- Changed expectations: each check map adds `:new-class? true`
  (`cell-registry/new-class!` on the factory result Class); exit and
  `err` checks become cell exit/`*err*` checks.
- Deviation: no `:factory?` key. The second definition through function
  analysis leaves the namespace import `RepeatedHolder` mapped to the
  first Class, so `cell-registry/defined` gives `:factory? false`. The
  owner is the factory result Class, so factory identity holds by
  construction. Expected behaviour of Clojure, not a defect.
- Verified under Java 8 parent: 54 assertions pass, 12 s (before 18
  assertions, 12 s).

## Slice 4 — identity-receiving-test cross-JVM tests (implementation pass 10)

- Children: 30 one-cell children, unchanged (each test has one cell per
  configuration; grouping unit is one test). Labels unchanged.
- `identity-receiving-checks`: each check reads `*ns*` once (cell
  namespace `cell.0`); `ns-name*` and literal class names removed. New
  private `define` gives a definition map (success + `cell-registry/defined`)
  or the root failure with `:absent?`.
- Private `run-check` removed; Slice 5 moves `check-cell`,
  `run-check-cells`, `cell-ns`, `defined` instead.
- Changed expectations:
  - `receiving-identity-policy-test`: each holder adds `:new-class? true
    :factory? true`; error rejection adds `:absent? true`; warning check is
    exact cell `*err*` (one line, context `fixed fallback representation`).
  - `incompatible-value-receiving-test`: class name `cell.0.Holder`
    (from result `:class-name`); `:outcome` failure adds `:absent? true`;
    `:expected` is a predicate.
  - `generated-value-receiving-policy-test`: adds `:definition`.
  - `strict-nested-public-declaration-test`: adds `:child` definition map;
    `:owner`/`:factory` unchanged (plan); predicate expected.
  - `nested-public-warning-scope-test`: result is `{:child ... :declarations
    ...}`; each parent declaration adds `:definition` (redefinition by
    top-level eval keeps factory identity); `:type` `cell.0.Child`; warn
    cell `:warning :any` (detail differs per JVM), exact one line.
- Warn guard: no grouped warn cell (one-cell children).
- Verified under Java 8 parent: 829 assertions pass, 27 s.

## Slice 4 — public-native / public-mixed field AOT tests (implementation pass 11)

- Each consumer and each inspection is a one-cell child (rule 4); child
  counts unchanged (native: 1 producer per test, 1 javac, 6/6/12/6 cells;
  mixed: same plus 2 inspection cells). `printed` no longer used by them.
- Labels: same scheme as `public-field-aot-test` (old label + context
  `(pr-str [java jvm-opts policy])`; incompatible keeps
  `"<java> / <policy>"` first; producer/javac support labels). Mixed
  inspection cells: context `[field]` (old: no context; 2 children shared
  the label).
- Changed expectations: exit/err/result `is` checks become cell checks;
  mixed inspection checks become one `persisted-output?` predicate. No
  new-Class keys (AOT consumer cells exempt). No warn fallback cells.
- Verified under Java 8 parent: native 168, mixed 182 assertions pass
  (19 s each).

## Slice 4 — identity-aot / generated-value-aot tests (implementation pass 12)

- Each consumer is a one-cell child (rule 4); child counts unchanged
  (identity: 9 + 3 cells, 2 producers, 1 javac; generated: 6 + 6 + 6
  cells, 3 producers, 1 javac). `printed` no longer used by them.
- Labels: old label + context `(pr-str [java jvm-opts policy])`;
  `java-identity-backend-consumer` has no policy, context
  `(pr-str [java jvm-opts])`; incompatible keeps `"<java> / <policy>"`
  first; producer/javac support labels. Producers keep no policy
  (unchanged from before).
- Changed expectations: exit/err/result `is` checks become cell checks.
  No new-Class keys (AOT consumer cells exempt). No warn fallback cells.
- Verified under Java 8 parent: identity 70, generated 104 assertions
  pass (23 s, 15 s).

## Slice 4 — field-aot-test (implementation pass 13)

- `persisted-restricted-field-output-test`: one-cell child (policy
  `error`, classpath = parent + fixture source dir); cell form is a `do`:
  require `field-aot-checks`/`cell-registry`, `aot-fixture/compile-form`,
  then `{:output ... :definitions {Leaf (defined ...) Holder (defined ...)}}`
  (`cell-registry/defined`, classes via `ns-resolve` at run time).
  `:expected` = `persisted-output?` predicate. No `compile-producer`;
  source not deleted. Label unchanged (outer `testing`, `:context []`).
- Other tests: each consumer one-cell child (rule 4); context
  `(pr-str [java jvm-opts policy])`; incompatible keeps
  `"<java> / <policy>"` first; producer under support label `producer`
  (new), javac under `javac` (new). Consumer compile of
  `separately-compiled-...` stays an inline `:aot-compile` child.
- Changed expectations: exit/err/result `is` become cell checks; persisted
  output gains `:definitions` new-Class keys (Leaf, Holder). No warn
  fallback cells.
- Children: 1 cell + 4 producers + 1 consumer aot + 1 javac + 1+1+1+6
  consumer cells. Verified under Java 8 parent: 73 assertions pass.

## Slice 4 — field-capability-test (implementation pass 14)

- Children: capability 3 (rule 5: first detection), malformed 6,
  required 9, policy 9: one-cell each; evaluation 18 children (config ×
  policy × execution) with 2 cells (tag). Total 63 → 45. 37 s (first
  record 92 s).
- Labels unchanged (each old label is one cell context).
- `field-policy-checks/evaluation` reads `*ns*` once (cell namespace; the
  fixed `binding *ns*` is gone). An attempt is the definition map
  `{:constructions [...] :new-class? :factory?}` or the root failure with
  `:absent?`. Top-level `eval` redefinition keeps `:factory? true`.
- `required-capability-policy-matrix-test` sets the policy with `:policy`
  (was a `-D` JVM option); same child command.
- Changed expectations: inline `is` become one `:expected` predicate per
  cell (`policy-expected`, includes the old `(seq detail)` check); warn
  count check becomes exact one-line cell `*err*` (`:warning :any`) on the
  first fallback cell, `""` on the second (String) cell; "no fallback in
  err" becomes exact empty `*err*`; evaluation attempts add
  `:new-class?`/`:factory?`, error attempts add `:absent? true`.
- Warn guard: the warn String cell matches the error String cell of the
  same configuration and execution. No rule-5 exception besides
  capability-matrix.
- Verified under Java 8 parent: 297 assertions pass.

## Slice 4 — loader-policy-test (implementation pass 15)

- Children: 14 configuration children, 6 cells each (one per
  `[expansion analysis]` case; context `[start-label (pr-str case-key)]`,
  labels unchanged). Start labels now hold only support assertions.
- New warn-guard children (design "Warn-guard evidence"): Java 8 and EA3
  preview-off grouped warn cells had no matching error cell. Added 4
  error-policy children (`baseline`/`preview-off` × execution), new start
  labels `(pr-str [java jvm-opts "error" execution])`, 6 cells each,
  `stale-sibling-strict-case`; expected: root `java.lang.Error` with the
  fallback prefix and `Holder` absent. The guard fires already at
  `macroexpand` (whole case throws), so no import rejections in that result.
- `identity-loader-checks`: `stale-sibling-case` is public,
  `[receiver expansion analysis]`; sibling class binary name is
  `<receiver>.Child<gensym>` (class-name rule); caller owns receiver
  (`public-stale-sibling` keeps gensym ns + `remove-ns` for the in-process
  `identity-loader-test`). `reject` `:absent?` now uses
  `cell-registry/absent?` (stronger: adds class name). The case result
  adds `:new-class? true :factory? true` (`cell-registry/defined`, Class =
  evaluated value when a Class, else resolved owner). Changed expectation
  of `public-stale-sibling-expected` (both callers, one commit).
- Warn cells: Java 8 warning context `java/lang/classfile/ClassFile`
  (exact); preview-off message holds a random probe class name → `:any`.
  Old test did not check stderr; now exact cell `*err*`.
- Verified under Java 8 parent: 378 assertions pass, 18 children, 108 cells
  (each count 1), namespace 12.6 s (first record 22.9 s);
  `identity-loader-test` 16 pass.

## Slice 4 — transfer-test (implementation pass 16)

- `transfer-checks/produce [form print-meta? specs]` (shared by all four
  tests; spec `["cell.<i>" expand]`, expansion class `cell.<i>.Transferred`).
  `consume [serialized mode]`: one cell = one mode and one expansion, two
  attempts in the cell namespace.
- `warning-checks/concurrent-definitions` and `concurrent-rejections` take
  `[serialized class-name]`; worker namespaces are
  `<cell ns>.warning.worker<i>` / `<cell ns>.strict.worker<i>`.
  `(shutdown-agents)` removed from the strict child form (harness calls it).
- Children: transferred 3 × 4 cells; concurrent-warning 2 one-cell
  (rule 5: warning per fresh runtime); constrained-warning 8 × 2 cells
  (rule 5: runtime index axis, 2 runtimes per exec × policy);
  strict 2 × 2 cells; 4 producers (one per test, baseline, `:producer`).
- Labels: transferred `[policy]` → `[policy mode expand]` (1 → 4);
  constrained-warning and strict old label + expand name (count 2 → 2
  cells); concurrent-warning unchanged. Producer support label `producer`.
- Changed expectations: success attempts and worker types add
  `:new-class? :factory?` (`cell-registry/defined`; old `:factory?` was
  class equality only); `:absent?` by `cell-registry/absent?` (adds
  `->Transferred`); strict `:owner-absent?`/`:factory-absent?` → `:absent?`;
  warn `*err*` exact line on the first fallback cell, `""` after;
  concurrent-warning producer gains `:err ""`.
- Warn guard: transferred warn cells match the error child of the same
  test; constrained-warning warn cells match strict-test error cells (same
  selected JVM, exec options, producer form, serialized form, `eval`).
- Verified: 153 assertions pass under Java 8, EA3 preview off and on;
  Java 8 run 51 s (first record 96 s).

## Slice 4 — aot-test (implementation pass 17)

- Children: 15 cell children (one cell each, rule 4; strict load retry
  stays in one cell, rule 5) + 4 producers (`:aot-compile`), unchanged count.
  `fallback-aot-compilation-test` children are compile cells that run in
  parallel: one shared fixture source, one output dir per policy
  (`classes-<policy>` under the fixture root).
- `aot-checks/compile-fixture` now requires `value-type.cell-registry`.
  Strict attempts: `:mapped?`/`:loadable?` → `:absent?`
  (`registry/absent?` on `(create-ns 'fallback.aot-fixture)`, adds the
  factory part). Non-strict: `:factory?` (class name) → `registry/defined`
  (`:new-class? :factory?`). The `source-free-value-aot-loading-test`
  producer uses the same function, so its expected result gains
  `:new-class? true` (changed expectation).
- AOT consumer cells (`load-fixture`, `load-value-fixture`,
  `invoke-runtime-fixture`): keys unchanged, no new-Class check.
- Labels: cell contexts keep the old `testing` strings. Producer
  assertions move under the new support label `producer`.
- Changed expectations: compile warn cell `*err*` is one warning line with
  context `:any` (parent-dependent; was line count + prefix); consumer warn
  cells exact line `fixed fallback representation` (as before); other cells
  `""`; `runtime-only` producer now uses `compile-producer` (adds empty
  stderr check and `:compiled`).
- Compile in a cell (risk "Compile in a cell"): works for this fixture in
  all three parents.
- Verified: 83 assertions pass under Java 8, EA3 preview off and on;
  Java 8 wall 47 s (first record namespace 59.8 s).

## Slice 4 — runtime-test (implementation pass 18)

- Children (Java 8): 41 → 29 cell children, no producers. One child for
  each policy (rule 2); `constrained-discarded-expansion-test` 4 cells and
  `constrained-validation-before-policy-test` 2 cells for each policy child.
  Rule-5 exceptions: `capability-test` (1 child, first capability
  detection; one cell anyway); `fallback-expansion-warning-timing-test`
  (4 children, 1 configuration: each cell needs the first warning of a fresh
  runtime). Its warning goes to the check's own `*err*` binding, so its
  cells are not `:fallback?` and expect empty cell `*err*`.
- Label mapping: `[]` → `["policy <p>"]` for `invalid-policy-test`
  (`(pr-str p)`), `public-require-test`, `public-definition-test`; other
  labels unchanged (`capability-test`, `fallback-source-compatibility-test`,
  `source-field-validation-test` keep `[]`, one cell each).
- Check code: `expansion-checks` reads `*ns*` once (before `on-thread`);
  private `absent?` removed → `cell-registry/absent?`; `attempt` takes
  `cell-ns` and `evaluate?`. `source-checks/observations` takes the type
  name; `validation-checks` defines in `*ns*`.
- Changed expectations: `:owner-absent?`/`:factory-absent?` → `:absent?`
  (constrained tests); `:absent? false` → `:new-class? true :factory? true`
  (`:evaluated`, `:source`, timing `:evaluated`); `public-definition-test`
  `:outcome` per plan; `source-checks` `:factory` → `:new-class?
  :factory?`; `check-fields` successes gain `:new-class? :factory?`,
  `:absent` → `:absent?`; `invalid-forms` entries change from the class
  name to `{:class c :absent? true}`; warn cells: `policy-test` exact line
  `test`, others one line `:any` on the first fallback cell; all cells now
  check empty `*err*` otherwise (was unchecked in `invalid-policy-test`,
  `constrained-discarded` non-warn only).
- Verified: 187 assertions pass under Java 8, EA3 preview off and on;
  Java 8 wall 65 s (first record namespace 146 s).

## Slice 4 — child-count verification (implementation pass 19)

- Mid record: Java 8 `:fallback-test`, default limit, at 335df47;
  `VALUE_TYPE_TEST_RECORD=target/test-record/mid/fallback-java8.edn`;
  119 tests pass; real 435 s (first record 1559 s). Not the after record:
  Slice 5 moves namespaces, so Slice 8 takes it again.
- Columns: "cell configs" = distinct cell-child configurations summed over
  the tests of the namespace; "max" = largest cell count of one label
  (≤ 1 for all).

| namespace | ms | children (cell/producer/aot/java) | cell configs | first cell children | max cell count |
|---|---|---|---|---|---|
| value-type.aot-test | 37241 | 15/0/4/0 | 15 | 15 | 1 |
| value-type.array-boundary-test | 3962 | 2/0/0/0 | 2 | 2 | 1 |
| value-type.array-info-boundary-test | 14030 | 39/0/0/0 | 21 | 39 | 1 |
| value-type.array-matrix-test | 12445 | 36/0/0/0 | 18 | 36 | 1 |
| value-type.array-sequence-boundary-test | 12478 | 36/0/0/0 | 18 | 36 | 1 |
| value-type.capture-failure-test | 13 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.capture-loading-test | 3032 | 2/0/0/0 | 2 | 2 | 1 |
| value-type.child-transfer-test | 24019 | 30/4/0/0 | 30 | 180 | 1 |
| value-type.controlled-field-capability-test | 11360 | 24/1/2/1 | 24 | 54 | 1 |
| value-type.field-aot-test | 8606 | 10/0/5/1 | 10 | 10 | 1 |
| value-type.field-capability-test | 23684 | 45/0/0/0 | 45 | 63 | 1 |
| value-type.field-transfer-test | 44023 | 54/8/0/0 | 54 | 222 | 1 |
| value-type.fields-test | 143 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.function-redefinition-test | 2480 | 6/0/0/0 | 6 | 6 | 1 |
| value-type.generated-value-aot-test | 9633 | 18/0/3/1 | 18 | 18 | 1 |
| value-type.identity-aot-test | 17631 | 12/0/2/1 | 12 | 12 | 1 |
| value-type.identity-backend-test | 42 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.identity-loader-test | 108 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.identity-receiving-test | 22744 | 30/0/0/0 | 30 | 30 | 1 |
| value-type.isolated-sibling-loader-test | 103 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.loader-policy-test | 10878 | 18/0/0/0 | 18 | 14 | 1 |
| value-type.public-field-annotations-test | 71 | 0/0/0/0 | 0 | 0 | 0 |
| value-type.public-field-aot-test | 59765 | 86/0/10/2 | 86 | 86 | 1 |
| value-type.public-mixed-field-aot-test | 14152 | 32/0/5/1 | 31 | 32 | 1 |
| value-type.public-native-field-aot-test | 13236 | 30/0/4/1 | 30 | 30 | 1 |
| value-type.runtime-test | 43949 | 29/0/0/0 | 26 | 41 | 1 |
| value-type.transfer-test | 32974 | 15/4/0/0 | 10 | 29 | 1 |

- Bound check: cell children = cell configs, except where earlier passes
  record rule-4/rule-5 exceptions: array-info (39 vs 21), array-matrix and
  array-sequence (36 vs 18), public-mixed-field-aot (32 vs 31), all rule 4
  (one artifact or require order for each child); runtime-test (29 vs 26,
  warning-timing rule 5); transfer-test (15 vs 10, concurrent warning-scope
  rule 5). Producer/aot/java counts equal the first record, except
  transfer-test producers 0 → 4 (see pass 16) and loader-policy 14 → 18
  (4 new error-policy warn-guard children, pass 15).

## Pass 20 — Slice 5 cross-JVM suite split

- 17 cross-JVM namespaces `git mv` to `test/cross-jvm/value_type/`
  (names unchanged). Shared support (`jvm_runner`, `cell_harness`,
  `*_checks`, `jvm_config`, `aot_fixture`, `label_record`) stays in
  `test/compatibility`; `:cross-jvm-test` adds both paths.
- New `value-type.identity-receiving-cross-jvm-test`: 5 tests moved with the
  helpers `check-cell`, `run-check-cells`, `cell-ns`, `defined`. Label map:
  `value-type.identity-receiving-test/<name>` →
  `value-type.identity-receiving-cross-jvm-test/<name>` for
  `receiving-identity-policy-test`, `incompatible-value-receiving-test`,
  `generated-value-receiving-policy-test`,
  `strict-nested-public-declaration-test`,
  `nested-public-warning-scope-test`. In-process tests stay.
- Fixed a pre-existing cljfmt error in `aot_checks.clj` (from pass 17).
- Shell note: `VALUE_TYPE_BASELINE_JAVA`/`VALUE_TYPE_VALHALLA_JAVA` are not
  set by mise; export them (paths in "Slice 1") before runs, else
  `jvm-config` falls back to default paths that do not exist here.
- Times (Java 8 parent, limit 4): `:cross-jvm-test` 309 s;
  `:fallback-test` 111 s.

## Pass 21 — Slice 6 permanent runner tests

- New `test/cross-jvm/value_type/cell_runner_test.clj`
  (`value-type.cell-runner-test`): `scope-3-policy-rules-test`,
  `failing-cell-outcome-test`, `crashing-child-outcome-test`,
  `timed-out-child-outcome-test`. 40 assertions pass, Java 8 parent.
- Java 8 source-definition fallback context is
  `java/lang/classfile/ClassFile` (the missing ClassFile API class), so the
  warn line and the error root message are
  `value-type: identity deftype fallback: java/lang/classfile/ClassFile`.
- Fixture cells: `:expected :ok` for outcome fixtures (only data is
  checked); `eval` of a fallback `defvaluetype` returns the Class, given
  to `cell-registry/defined`.
- Next: Slice 7 (docs, release.yml); Slice 8 records must include this
  namespace in the `:cross-jvm-test` count (new labels, cell count 0).

## Pass 22 — Slice 7 documentation and release tooling

- `runtime.md`: new "Child JVMs" subsection (limit variable, timeout rule,
  isolation rules 1–5, warn-guard, `VALUE_TYPE_TEST_RECORD`); focused
  cross-JVM commands now `:cross-jvm-test` under `$VALUE_TYPE_BASELINE_JAVA`;
  "120-second timeout" text refers to "Child JVMs".
- Vale rejects "classpath" in prose; use "class path".
- `release.yml`: `cross-jvm` + `preserve-cross-jvm-failures` before
  `Test build`; timeout 60 min kept. actionlint not installed locally;
  not run.
- Next: Slice 8 (after record, acceptance 2/3/7, mementum memory).

## Pass 23 — Slice 8 after record and acceptance

- After record at `4701304` (detached worktree `/tmp/vt-after`), same
  machine and JDK builds as the first record, default limit 4. Per-namespace
  summaries (`summary :cells`): `after-record/<command>.edn`.
- Totals (`/usr/bin/time -p` real, all exit 0, 0 failures):
  `fallback-java8` 156.00 s, `fallback-preview-off` 44.86 s,
  `fallback-preview-on` 45.39 s, `cross-jvm` 409.83 s; sum 656.08 s =
  16.2% of 4060.34 s (target ≤ 1015 s). Acceptance 2 time holds.
- Limit 1 (`VALUE_TYPE_TEST_JVM_LIMIT=1`): `fallback-java8` 258.87 s,
  `cross-jvm` 810.35 s, both exit 0; `:assertions` label vectors equal the
  limit-4 records (2052 and 4552 entries). Acceptance 3 holds.
- Max cell count of one label: 1 in all four records.
- Label mapping (first-record label → same label or extensions, ns rename for
  the 5 moved `identity-receiving-test` Vars, after fallback ∪ cross-jvm):
  all hold except 2 `fields-test/public-imported-concrete-field-test`
  labels per command. Those contexts hold `gensym` names (different in each
  run) and have cell count 0 (in-process test). Not a defect.
- Child bounds: same counts as the pass-19 mid record table (rule-4/rule-5
  exceptions unchanged); `cell-runner-test` adds 6 one-cell children.
- Acceptance 7: no task-018 commit touches `src/` (`src/` diffs since
  `6fa2ef9` are task-017 commits).
- Memory `repeated-definitions-must-prove-new-class-identity` updated to the
  cell model.
- Check script used: `label_record/summary` plus a prefix mapping; rerun
  pattern in this entry if a record is retaken.

## Implementation review (pass 1)

- Added 4 steps to be addressed (steps.md "Implementation review (pass 1)").

## Review pass 1 fixes

- Addressed 4 review steps.
- Correction to pass 19: the array require-order exceptions are rule 5,
  not rule 4. Rule 4 is the source-free AOT artifact rule only.
- Isolation exceptions (children above distinct configurations, or a cell
  that keeps its own child):

| test | children / configs | rule | reason |
|---|---|---|---|
| `array-info-boundary-test/safe-inspection-boundary-test` | 36 / 18 | 5 | require order is process-wide; one child for each order |
| `array-matrix-test/configuration-matrix-test` | 36 / 18 | 5 | require order (design example) |
| `array-sequence-boundary-test/sequence-configuration-matrix-test` | 36 / 18 | 5 | require order |
| `runtime-test/fallback-expansion-warning-timing-test` | 4 / 1 | 5 | each cell needs the first warning of a fresh runtime |
| `runtime-test/capability-test` | 1 / 1 | 5 | first capability detection |
| `field-capability-test/capability-matrix-test` | 1 per config | 5 | first field-capability detection |
| `transfer-test/concurrent-warning-scope-test` | 2 / 1 | 5 | warning scope across runtimes |
| `transfer-test/constrained-concurrent-warning-scope-test` | 8 / 4 | 5 | runtime index axis (warning scope) |

- In-cell retries (`aot-test` and `public-field-aot-test/strict-public-identity-aot-test`
  strict load retry) stay in one cell; no extra child.
- `array-boundary-test`, `capture-loading-test`: children differ by
  configuration (rule 1); no exception.
- `public-mixed-field-aot-test/persisted-public-mixed-output-test`: the 2
  inspection cells share 1 child (was 2). Cell labels unchanged; support
  labels gain the `"child"` context. Assertions 4552 → 4551.
- New shared support: `aot-fixture/consumer-child` (one-cell source-free
  AOT consumer, rule 4) and `aot-fixture/produce` (support label
  `producer`). `aot-test/consumer` delegates to `consumer-child`.
  `public-field-aot-test` inspection builds its child with `cell-child`.
- `cell-harness` exposes `cell-ns-name`, `warning-prefix`,
  `policy-rejection`, `fallback-rejection`; the child form uses the same
  prefix. `policy-rejection`/`fallback-rejection` move from
  `consumer-aot-checks` (loaded in children, so it must not require the
  harness).
- Verified: `:cross-jvm-test` (Java 8 parent) 4551 assertions, 65 tests,
  exit 0; `:fallback-test` Java 8, EA3 preview off, EA3 preview on: 2052
  assertions each, exit 0. Positive cell labels of the cross-JVM record
  equal the after record in every namespace. `bb clj-fmt:fix`,
  `bb clj-kondo:lint`: 0 warnings.

## Implementation review (pass 2)

- Added 4 steps to be addressed (steps.md "Implementation review (pass 2)").

## Review pass 2 fixes

- Addressed 4 review steps.
- Removed unused `aot-fixture/printed`.
- Rule citations: `aot-test/fallback-aot-compilation-test` compile
  children are rule 2 (no artifact load); controlled AOT and Java AOT
  children are rules 1, 2 (policy × execution) with one artifact (rule 4);
  `consumer-child` docstring: rule 4 artifact, first load rule 5.
- `:fixture-ns` added; changed support labels (the `"child stderr"`
  context last element `nil` → fixture namespace):
  - `aot-test` `source-free-fallback-aot-loading-test`,
    `source-free-value-aot-loading-test`: `fallback.aot-fixture`;
    `source-free-runtime-only-fallback-test`: `fallback.runtime-fixture`.
  - `controlled-field-capability-test`
    `controlled-native-absence-aot-test`,
    `controlled-native-absence-java-aot-test`: `checked.controlled`.
- `doc/development/runtime.md` "Child JVMs": matching error cell is in the
  same test namespace; it gives guard evidence for the form and mode of the
  warn cell.
- Verified: `aot-test` under Java 8, EA3 preview off, EA3 preview on (83
  assertions each, exit 0); `controlled-field-capability-test` under
  `:cross-jvm-test` (253 assertions, exit 0). `bb clj-fmt:fix`,
  `bb clj-kondo:lint` (0 warnings), `bb vale` clean.

## Implementation review (pass 3)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 3)").

## Review pass 3 fixes

- Addressed 2 review steps.
- Changed expectation, `loader-policy-test/fresh-policy-loader-rejection-test`
  error-policy cells: `strict-rejection?` → `(strict-rejection warning)`.
  It compares the full `:chain`
  `["clojure.lang.Compiler$CompilerException" "java.lang.Error"]` and root
  class `java.lang.Error`. Baseline: exact message
  `warning-prefix` + `java/lang/classfile/ClassFile`. Preview off: message
  prefix only (random probe class name).
- `mementum/state.md`: cell model, `VALUE_TYPE_TEST_JVM_LIMIT`, about
  11 minutes for the four commands at limit 4, `:cross-jvm-test` suite.
- Verified: `loader-policy-test` under `:cross-jvm-test` (Java 8 parent),
  378 assertions, exit 0. `bb clj-fmt:check`, `bb clj-kondo:lint`
  (0 warnings), `bb vale` clean.

## Implementation review (pass 4)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 4)").

## Review pass 4 fixes

- Addressed 2 review steps.
- `field-transfer-checks/produce` takes specs `[tag namespace-name expand]`.
  `unsupported-public-transfer-test` and `supported-public-transfer-test`
  start one producer each (was 2: one for each tag). `field-transfer-test`
  producers 8 → 6, one for each test, equal to the distinct producer
  configurations. Labels unchanged.
- Correction to pass 19: "Producer/aot/java counts equal the first record"
  compared with the first record, not with the distinct configurations.
  `field-transfer-test` had 8 producers for 6 producer configurations
  (bound exceeded); now 6.
- `value-type.cell-registry/thrown` gives `{:class :message :chain}`;
  `cell-harness/child-form`, `identity-loader-checks/stale-sibling-strict-case`
  (`root-chain` removed) and `cell-runner-test` `fallback-cell-form` call it.
  Result form unchanged.
- Verified (Java 8 parent): `:cross-jvm-test` `field-transfer-test`,
  `cell-runner-test`, `loader-policy-test` 1510 assertions pass; record
  producer kinds 1 for each `field-transfer-test` test.
  `:fallback-test` `identity-loader-test` 16 pass. `bb clj-fmt:check`,
  `bb clj-kondo:lint` (0 warnings) clean.

## Implementation review (pass 5)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 5)").

## Review pass 5 fixes

- Step 1 (strict Error form):
  - `cell-harness/rejection?` (chain, context, thrown map): root
    `java.lang.Error`, chain equal, message `warning-prefix` + context
    exact, or the prefix only when context is `:any`.
    `cell-harness/rejections` gives an `:expected` predicate: each result
    map that satisfies `rejection?` becomes `{:rejected? true ...}` before
    `=`. `loader-policy-test/strict-rejection` uses `rejection?`.
  - Checks return `cell-registry/thrown` (with `:absent?`, `:emitted?` or
    `:error?` where they had them). Removed: `aot-checks/cause-chain`, the
    chain loops of `transfer-checks/attempt`, `expansion-checks/attempt`,
    the `runtime-test` `public-definition-test` form;
    `expansion-checks/discarded` uses `thrown`.
  - Changed expectations (chains unchanged):
    - `runtime-test/public-definition-test`, `fallback-expansion-policy-test`
      error cells: message `warning-prefix` + `java/lang/classfile/ClassFile`
      on Java 8; prefix only on EA3 preview off (random probe class).
    - `runtime-test/invalid-policy-test`: the cell throws;
      `:expected {:thrown {:class "java.lang.IllegalArgumentException"
      :message ...}}` (was a `[class message]` value from a root loop).
    - `transfer-test/transferred-expansion-test` error cells:
      `fallback-rejection` (`fixed fallback representation`).
    - `aot-test/fallback-aot-compilation-test` strict compile: Java 8
      exact (`java/lang/classfile/ClassFile`); EA3 prefix only (no
      preview: random probe class).
    - `aot-test/source-free-fallback-aot-loading-test`: first attempt
      `fallback-rejection`; retry Java 8 `NoClassDefFoundError` "Could not
      initialize class fallback.aot_fixture__init"; EA3 root
      `ExceptionInInitializerError` "Exception java.lang.Error: " +
      fallback message + ` [in thread "main"]`.
    - `aot-test/source-free-runtime-only-fallback-test` strict calls:
      `fallback-rejection`.
    - `aot-test/source-free-value-aot-loading-test`: result is
      `registry/thrown`; predicate on `:chain` unchanged.
  - Verified: `runtime-test`, `transfer-test`, `aot-test` under
    `:fallback-test` on Java 8, EA3 preview off, EA3 preview on (423 pass);
    `loader-policy-test` under `:cross-jvm-test` (378 pass);
    `bb clj-fmt:check`, `bb clj-kondo:lint` clean.
- Step 2 (after record retaken at `b03b1fb`; later commits change task
  files only): same machine and JDK builds, raw records in
  `target/test-record/after5/`; summaries replace `after-record/*.edn`.
  - Limit 4 (`/usr/bin/time -p` real, all exit 0, 0 failures):
    `fallback-java8` 133.85 s, `fallback-preview-off` 40.06 s,
    `fallback-preview-on` 44.40 s, `cross-jvm` 375.59 s; sum 593.90 s =
    14.6% of 4060.34 s (≤ 1015 s). Acceptance 2 time holds.
  - Limit 1: `fallback-java8` 242.39 s, `cross-jvm` 766.90 s, exit 0;
    `:assertions` label vectors equal limit 4 (2052, 4547). Acceptance 3
    holds.
  - Max cell count of one label: 1 in all four records.
  - Child bound: no `[test kind]` above its distinct configurations except
    the rule-5 table (review pass 1). `field-transfer-test` producers 1
    for each test; `persisted-public-mixed-output-test` 1 cell child.
  - Label mapping: all first labels map, except 2
    `fields-test/public-imported-concrete-field-test` labels per command
    (gensym contexts, cell count 0; known, pass 23).
  - Check script: `check_record.clj` (task dir); usage in its header.
    It writes the summaries and prints the bound, mapping and limit
    findings. It replaces the pass-23 ad hoc "prefix mapping".
- Addressed 2 review steps.

## Implementation review (pass 6)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 6)").

## Review pass 6 fixes

- Step 1: `cell-registry/root` (root `Throwable`); `thrown` uses the same
  chain. `consumer-aot-checks/failure` is
  `(select-keys (registry/thrown e) [:class :message])`;
  `identity-receiving-checks/root-failure` and `expansion-checks` use
  `registry/root`; `expansion-checks/root` removed. Result forms unchanged.
- Step 2: `cell-harness/unavailable-detail` (constant) and
  `cell-harness/rejection-context` (configuration → detail on Java 8, else
  `:any`). Used in `aot-test`, `runtime-test` (private copy removed),
  `cell-runner-test` (private copy removed), `loader-policy-test` (2
  places). Labels and expectations unchanged.
- Verified: `:fallback-test` on Java 8, EA3 preview off, EA3 preview on
  (2052 pass each); `:cross-jvm-test` on Java 8 (4547 pass); assertion
  counts equal the pass-5 record. `bb clj-fmt:check`, `bb clj-kondo:lint`
  clean.
- Addressed 2 review steps.

## Implementation review (pass 7)

- Added 1 step to be addressed (steps.md "Implementation review (pass 7)").

## Review pass 7 fixes

- `cell-harness/check-form` (check is a namespace-qualified symbol: require
  its namespace, then call it) and `cell-harness/produce` (runtime + form:
  `run-jvm` `:kind :producer`, form printed with metadata as cell forms
  are; exit 0 and empty `:err` under `"producer"`; `:result` on exit 0,
  else nil). Used in `transfer-test`, `child-transfer-test`,
  `field-transfer-test`, `controlled-field-capability-test`; `aot-test`
  `checks-form` removed (`consumer` takes an `aot-checks` call form).
  Local `check-form`/`produce` copies removed.
- Changed support label: `controlled-field-capability-test/
  controlled-native-absence-receiving-test` producer assertions (exit,
  `:err`, expansion count) move from context `[]` to `["producer"]`; the
  exit and `:err` messages are now the producer result map. No other label
  or child count changes (`child-transfer-test`, `field-transfer-test`,
  `controlled-field-capability-test` summaries equal `after-record/` but
  for that label).
- Discovery: since pass 16, `transfer-test` printed the producer form
  without `*print-meta*`, so `constrained-form` lost `^Long ^:non-nil`
  (the pre-018 string form kept them). `cell-harness/produce` prints with
  metadata, so the constrained tests expand the constrained definition
  again. Expectations unchanged; they pass.
- Verified: `:fallback-test --namespace value-type.transfer-test
  --namespace value-type.aot-test` on Java 8, EA3 preview off, EA3
  preview on (236 pass each); `:cross-jvm-test` on Java 8 for the three
  cross-JVM namespaces (1983 pass). `bb clj-fmt:check`, `bb clj-kondo:lint`
  clean.
- Addressed 1 review step.

## Implementation review (pass 8)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 8)").

## Review pass 8 fixes

- `cell-harness/check-form` replaces the require-and-call cell forms in
  `array-matrix-test` (local `check-form` and its `resolve` removed),
  `array-sequence-boundary-test`, `identity-receiving-cross-jvm-test`
  (`check-cell`), `loader-policy-test` (`case-form`),
  `public-field-aot-test` and `public-mixed-field-aot-test` (inspection),
  `runtime-test` (`check-expansions` in two tests,
  `constrained-discarded-with-loader`). `runtime-test`
  `constrained-validation-before-policy-test` (two calls) unchanged.
  Labels and expectations unchanged.
- Changed expectation: `warning-checks/define-types` adds
  `:nil-construction` to each definition map: `:accepted` when the factory
  takes `nil`, else the root failure class. `transfer-test`
  `concurrent-warning-scope-test` (unconstrained `identity-form`) expects
  `:accepted`; `constrained-concurrent-warning-scope-test` expects
  `"java.lang.NullPointerException"` (`Objects.requireNonNull` of the
  `^:non-nil` field). Checked: with the constrained producer printed
  without metadata, `constrained-concurrent-warning-scope-test` fails
  (20 failures). `constrained-concurrent-strict-test` shares the same
  producer call; its rejection does not depend on the constraint.
- Verified: `:fallback-test --namespace value-type.transfer-test
  --namespace value-type.runtime-test` on Java 8, EA3 preview off, EA3
  preview on (340 pass each); `:cross-jvm-test` on Java 8 for the six
  changed cross-JVM namespaces (1543 pass). `bb clj-fmt:check`,
  `bb clj-kondo:lint` clean.
- Addressed 2 review steps.

## Implementation review (pass 9)

- Added 2 steps to be addressed (steps.md "Implementation review (pass 9)").

## Review pass 9 fixes

- `field-capability-test` `policy-cell` takes `check & args` and uses
  `cell-harness/check-form`. `array-info-boundary-test` `control-form` and
  `export-only-form` call `value-type.array` and
  `value-type.array-info-observation` directly (no `resolve`). Labels and
  expectations unchanged.
- Verified: `:cross-jvm-test` on Java 8 for both namespaces (492 pass).
  `bb clj-fmt:check`, `bb clj-kondo:lint` clean.
- Addressed 2 review steps.

## Implementation review (pass 10)

- Added 3 steps to be addressed (steps.md "Implementation review (pass 10)").

## Review pass 10 fixes

- Step 1: `aot-fixture/javac` (run `compile-java` under the support label
  `"javac"`; assert exit 0 and javac exit 0; return true when both pass).
  Used in `field-aot-test`, `generated-value-aot-test`, `identity-aot-test`,
  `public-field-aot-test` (private `compile-java` removed),
  `public-mixed-field-aot-test`, `public-native-field-aot-test`. Labels
  unchanged.
- Step 2: changed support labels in `controlled-field-capability-test`:
  - `controlled-native-absence-aot-test` and
    `controlled-native-absence-java-aot-test`: `compile-controlled`
    assertions (exit, `:err`, `:compiled`, source deletion) `[]` →
    `["producer"]`;
  - `controlled-native-absence-java-aot-test`: `ControlledJavaConsumer`
    javac assertions (exit, result) `[]` → `["javac"]`.
- Step 3: after record retaken at `e5ed659` (last code change; later
  commits change task files only). Correction: the pass-5 note "later
  commits change task files only" did not hold; passes 6–10 changed test
  code. Same machine and JDK builds; raw records in
  `target/test-record/after10/` and `after10-limit1/`; summaries replace
  `after-record/*.edn`.
  - Limit 4 (`/usr/bin/time -p` real, all exit 0, 0 failures):
    `fallback-java8` 115.63 s, `fallback-preview-off` 37.16 s,
    `fallback-preview-on` 39.77 s, `cross-jvm` 328.76 s; sum 521.32 s =
    12.8% of 4060.34 s (≤ 1015 s). Acceptance 2 time holds.
  - Limit 1: `fallback-java8` 238.38 s, `cross-jvm` 746.25 s, exit 0;
    `:assertions` label vectors equal limit 4 (2052, 4547). Acceptance 3
    holds. (A first limit-1 `cross-jvm` run ended by an external SIGTERM
    after 270 s, exit 143; the rerun is the record.)
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding; label mapping: only the known 2
    `fields-test/public-imported-concrete-field-test` gensym labels per
    command (cell count 0).
- Addressed 3 review steps.

## Implementation review (pass 11)

- Added 1 step to be addressed (steps.md "Implementation review (pass 11)").

## Review pass 11 fixes

- Step 1 (`15bf0ab`): `aot-test` private `producer-child` (option map) and
  `with-fixtures` (n nested `with-fixture`); `produce` runs
  `producer-child`. `source-free-fallback-aot-loading-test` opens 2
  fixtures, runs the `silent` and `warn` producers in one `run-jvms` call,
  asserts exit 0 under `"producer"` after the call, deletes the source of
  each passing producer, then runs the 6 consumer children in one
  `run-cells` call. Label multiset, child counts (2 `:aot-compile`, 6
  `:cell`) and expectations unchanged in all three parents; the label order
  changes (both producer assertions before the consumer cells).
- Times (`aot-test` Var `:ms`, full `:fallback-test` at limit 4, after10 →
  after11): test Java 8 14342 → 11673, preview off 7701 → 5075, preview on
  8143 → 5002; namespace Java 8 29494 → 27610, preview off 12775 → 11051,
  preview on 13400 → 11080. Limit 1 Java 8: test 26399 → 26396 (serial).
- After record retaken for the three `:fallback-test` commands at
  `15bf0ab` (raw: `target/test-record/after11/`, `after11-limit1/`);
  `cross-jvm` records copied from after10 (no cross-JVM code change).
  `/usr/bin/time -p` real, exit 0, 0 failures: `fallback-java8` 120.34 s,
  `fallback-preview-off` 37.06 s, `fallback-preview-on` 39.49 s,
  `cross-jvm` 328.76 s (after10); sum 525.65 s = 12.9% of 4060.34 s.
  Limit 1 `fallback-java8` 223.12 s. `check_record.clj`: max cell count 1;
  no bound finding; only the known 2 `fields-test` gensym labels per
  command; limit-1 = limit-4 `:assertions` labels (2052, 4547).
  `after-record/fallback-*.edn` replaced.
- Addressed 1 review step.

## Implementation review (pass 12)

- No new issues.

## Implementation test review (pass 1)

- Added 1 step to be addressed.

## Implementation test review (pass 1) fixes

- `cell-runner-test/failing-cell-outcome-test` selects the `:result` check
  by `:check`, not by position. `--namespace value-type.cell-runner-test`
  under `:cross-jvm-test` (Java 8): 40 assertions pass. Labels unchanged.
- Addressed 1 review step.

## Implementation test review (pass 2)

- Added 1 step to be addressed.

## Implementation test review (pass 2) fixes

- `cell-harness/described` attaches a data description to a cell
  `:expected` predicate (metadata); `report-check` reports it as
  `:expected`, else the value as is. `rejections` describes itself as
  `{:expected :chain :context}`. `runtime-test` inline predicates
  (`capability-test`, `fallback-expansion-warning-timing-test`,
  `constrained-discarded-expansion-test`,
  `constrained-validation-before-policy-test`) are described; result-derived
  parts are symbols (`<detail>`, `<non-blank>`, `<after-expansion>`).
  `(partial = :value-mode)` becomes `:value-mode` (same result).
- REPL check: failing described predicates report the description; an
  undescribed fn reports as before. `runtime-test` (Java 8, EA3: 187 pass),
  `aot-test` (Java 8: 83 pass); fmt and kondo clean. Pass/fail results and
  labels unchanged.
- Addressed 1 review step.

## Implementation test review (pass 3)

- Added 1 step to be addressed.

## Implementation test review (pass 3) fixes

- `cell-runner-test/unreadable-result-outcome-test`: two cells (`:ok`,
  `(Object.)`); asserts `:exit` 0, string `:read-error`, no `:result` key,
  and for each outcome `:pass? false`, `[[:result :no-result]]`, and a
  `:failure` with `:exit` 0 and the process `:out`, `:err` and
  `:read-error`. New labels only.
- `--namespace value-type.cell-runner-test` under `:cross-jvm-test`
  (Java 8): 56 assertions pass (40 → 56). fmt and kondo clean.
- Addressed 1 review step.

## Implementation test review (pass 4)

- No new issues.

## Implementation test review (pass 5)

- Added 2 steps to be addressed.

## Implementation test review (pass 5) fixes

- `value-type.cell-outcomes-test` (`test/cross-jvm/`, in-process, no child
  JVM): failing `:err` check (warn first fallback cell with no warning, two
  warning lines, warning on a later cell, `:any` warning without
  `warning-prefix`; `error` and `silent` cells with `*err*` text), failing
  `:out` check, failing `:result` check (other `:thrown` message, `:value`
  in place of `:thrown`, throwing predicate), `rejection?` false with `:any`
  for a message without `warning-prefix` and for another chain. 17
  assertions pass.
- Deviation: the review asked for a failing `:exit` check (one-cell child,
  exit not 0, with an entry). `cell-outcomes` reads entries only on exit 0
  (plan "Cell outcomes": exit not 0 gives one `:no-result` check), so the
  `:exit` check cannot fail. The test shows the plan result (`:no-result`,
  `:failure` `:exit` 1) and a passing `:exit` check on exit 0.
- `value-type.cell-registry-test` (in-process): `new-class!` false for a
  Class of an earlier call, a symbol and nil; `absent?` false for an
  import, a factory and a loadable class name; `defined` gives `:factory?
  false` when `->Sample` gives an instance of another record. 10 assertions
  pass.
- fmt and kondo clean.
- Addressed 2 review steps.

## Implementation test review (pass 6)

- Added 2 steps to be addressed.

## Implementation test review (pass 6) fixes

- `cell-harness/cell-outcomes`: one-cell `:exit` check removed;
  `cell-outcomes-test/one-cell-exit-test` "exit 0" case removed;
  `plan.md` "Cell outcomes", "Cell-result function" and "Conversion order"
  no longer name a cell exit check.
- Assertion counts (limit 4, Java 8 parent for `:cross-jvm-test`):
  `:cross-jvm-test` 4590 → 4222 (−370 one-cell `:exit` checks, −1 removed
  case, +3 new case); `:fallback-test` 2052 → 2012 (−40) under Java 8, EA3
  preview off and on. All four commands exit 0. Labels unchanged.
- `cell-registry-test/defined-test` "import gives another class": defines
  `Sample` twice, then interns the first `->Sample` again, so only the
  import gives another Class (the review form also changes the factory,
  so it does not isolate the import part). Mutation check: with the import
  part removed from `defined`, the case fails. 13 assertions pass.
- `after-record/` not retaken: this pass removes assertions only (no child,
  cell or label change).
- fmt and kondo clean.
- Addressed 2 review steps.

## Implementation test review (pass 7)

- Added 3 steps to be addressed.

## Implementation test review (pass 7) fixes

- `cell-harness/cell-child` throws (`ex-info`, cell `:context`) when a cell
  `:expected` is a fn with no `described` description. `plan.md` "Cell
  harness" `:expected` states the rule.
- Described predicates: the review list, plus three it did not name
  (found by the new throw and by grep): `public-field-aot-test/strict-loads?`,
  `identity-receiving-cross-jvm-test` `incompatible-value-receiving-test`
  and `strict-nested-public-declaration-test` inline fns, and the
  `aot-test/source-free-fallback-aot-loading-test` retry fn. A description
  is data: the expected value, with `'<detail>` for a runtime detail and
  `[x :+]` for "one or more x". Predicate logic unchanged.
- `cell-outcomes-test`: `one-cell-exit-test` → `no-result-test` (plus
  short-result and non-vector cases); `rejection-test` "exact context";
  new `assert-child-report-test` (binds `clojure.test/report`) and
  `undescribed-predicate-test`.
- Verify (limit 4, all exit 0, 0 failures): `:cross-jvm-test` (Java 8
  parent) 4238 assertions, 76 tests, 318.75 s; `:fallback-test` 2012
  assertions under Java 8 (133.01 s), EA3 preview off (40.72 s) and on
  (43.26 s).
- `after-record/` not retaken: new labels only; no child, cell or existing
  label change.
- fmt and kondo clean.
- Addressed 3 review steps.

## Implementation test review (pass 8)

- Added 4 steps to be addressed.

## Implementation test review (pass 8) fixes

- `assert-child-report-test` "child result count": asserts the fail
  contexts (`"child"` support context, then `"report count 1"`) and the
  `:no-result` fail; no `is` source forms.
- `cell-runner-test/unreadable-producer-result-test`: `run-jvms` of a
  `:cell` child then a `:producer` child that prints `#object[...]`;
  `ex-data` has `:index 1`, `:exit 0`, a string `:read-error`.
- `jvm-runner/read-limit` is public; `jvm-runner/wait-ms` (pure, option
  map) is the wait time of `execute`. New in-process
  `value-type.jvm-runner-test` (`test/cross-jvm/`).
- Description notation, stated in the `cell-harness/described` docstring:
  `<detail>` (runtime value or text), `(contains x ...)`, `(repeat n x)`
  (`n` `:+` is one or more), `(starts-with s)`, `#{x ...}` (one of).
  Aligned: `'<non-blank>`, `:present`, `:any` → `'<detail>`;
  `[x :+]` → `(repeat :+ x)`; `[:* x :*]` → `(contains x)`;
  `{:count ... :each/:rejection ...}` → `(repeat n x)`; `:message-names`
  → `:message (contains ...)`; glob strings → `(starts-with s)`;
  `(one-warning p)` and `'<after-expansion>` → `(repeat 1 p)`. New
  `harness/rejection-description` (context `:any` → `<detail>`), used by
  `rejections` (description: expected with each `:rejected? true` map
  expanded) and `loader-policy-test/strict-rejection`. Predicates
  unchanged.
- Verify (limit 4, all exit 0, 0 failures): `:cross-jvm-test` (Java 8
  parent) 4258 assertions (+20: 17 runner rules, 2 producer, 1 child
  count), 79 tests, 327 s; `:fallback-test` 2012 assertions under Java 8
  (111 s), EA3 preview off (33 s) and on (34 s).
- `after-record/` not retaken: new labels only; no child, cell or existing
  label change.
- fmt and kondo clean.
- Addressed 4 review steps.

## Implementation test review (pass 9)

- Added 2 steps to be addressed.

## Implementation test review (pass 9) fixes

- `jvm-runner/read-limit`: parses with `bigint`; a value above
  `Integer/MAX_VALUE` throws the same `ex-info` (variable and value).
  `read-limit-test`: valid `"2147483647"`; invalid `"2147483648"`,
  `"99999999999999999999"`.
- `cell-outcomes-test/warn-err-check-test`: cells `[{} {:fallback? true}]`;
  warning on cell 1 passes both; warning on cell 0 fails `:err` of both.
- Verify: `jvm-runner-test` and `cell-outcomes-test` 58 assertions pass;
  fmt and kondo clean. New labels only; `after-record/` not retaken.
- Addressed 2 review steps.

## Implementation test review (pass 10)

- Added 2 steps to be addressed.

## Implementation test review (pass 10) fixes

- `cell-outcomes-test/cell-child-wait-test`: `runner/wait-ms` of a
  `cell-child` is 22000 for `:timeout-ms` 2000 and 3 cells, 120000 for
  one cell with no `:timeout-ms`.
- `value-type.label-record-test` (`test/cross-jvm/`): hand-made record in
  a temporary file; asserts `:children` count and configurations for each
  `[test kind]`, namespace `:ms` sums, `:cells` counts (0 with no
  `cell!`), `:start-labels` counts (nested labels, else start label).
- Verify: both namespaces 44 assertions pass; fmt and kondo clean. New
  labels only; `after-record/` not retaken.
- Addressed 2 review steps.

## Implementation test review (pass 11)

- Added 3 steps to be addressed.

## Implementation test review (pass 11) fixes

- `cell-runner-test/cell-isolation-test`: one baseline silent child, two
  cells, same form. Each cell reads its state, then interns `leaked` and
  sets `*warn-on-reflection*`. Asserts namespaces `cell.0`/`cell.1`,
  loader chains of equal depth, distinct cell loaders (chain index 2),
  equal chains above, and `:leaked false`, `:warn-on-reflection false`.
  Deviations: `load-string` and the eval of each top-level form bind a
  fresh `LOADER`, so `@LOADER` is not the cell loader; the test compares
  the parent chain. A top-level `do` adds one level, so both cells use one
  form. `intern` replaces `def`: `def` interns at compile time, before the
  read.
- `cell-registry-test/absent-test` "default class name of a namespace
  name with -": under Scry the test thread has no `LOADER` binding, so
  `RT/baseLoader` does not find an eval-defined class and the case gives
  true. The case binds `LOADER` to a `DynamicClassLoader`, as a cell does.
  Contrast: the unmunged class name gives true.
- `doc/development/runtime.md` "Child JVMs": in-process support test
  command (4 namespaces, 6 s); `cell-runner-test` adds the child-JVM cases.
- Verify: `cell-registry-test`, `cell-runner-test` 82 assertions pass;
  fmt, kondo, vale clean. New labels only; `after-record/` not retaken.
- Addressed 3 review steps.

## Implementation test review (pass 12)

- Added 3 steps to be addressed.

## Implementation test review (pass 12) fixes

- `consumer-aot-checks/unavailable-description` (beside
  `unavailable-failure`): failure map, `:reason <detail>`, `:absent? true`,
  `:message (contains ...)` with the `unavailable-message?` names (private
  `unavailable-names`) and extra names. Used in `field-transfer-test` and
  `identity-receiving-cross-jvm-test`. `array-sequence-boundary-test`
  description: `:definitions :absent` removed.
- `function-redefinition-test/repeated-definition-cell`: one quoted form;
  `cell-form` of both declarations printed equal before and after.
- `cell-outcomes-test/warn-err-check-test`: `:any` one line passes, two
  lines fail `:err`.
- Verify: the 5 touched namespaces under `:cross-jvm-test` (Java 8),
  1449 assertions pass, 78 s; fmt, kondo clean. New labels only;
  `after-record/` not retaken.
- Addressed 3 review steps.

## Implementation test review (pass 13)

- No new issues found.

## Implementation docs review (pass 1)

- Added 3 steps to be addressed.

## Implementation docs review (pass 1) fixes

- `release.md`: release suite list names cross-JVM tests.
- `runtime.md` "Verified fallback matrix": owner-parent command uses
  `<java8-home>/bin/java`; focused `:cross-jvm-test` commands need
  `VALUE_TYPE_BASELINE_JAVA` set (else `java` on `PATH`).
- `runtime.md` "Child JVMs": limit at most 2147483647; invalid value gives
  Scry runner error, no tests, exit 1 (checked: limit `0`, Java 8).
- `bb vale` clean.
- Addressed 3 review steps.

## Implementation docs review (pass 2)

- No new issues found.

## Code-shaper review (pass 1)

- Added 3 steps to be addressed.

## Code-shaper review (pass 1) fixes

- `cell-harness/cell-specs` (`[(cell-ns-name i) x]`); used in
  `transfer-test`, `child-transfer-test`,
  `controlled-field-capability-test`, `field-transfer-test`.
  `field-transfer-test`: `cell-expansions` (3 `subvec` copies) and local
  `produce`.
- `cell-harness/source-rejection-chain`; used in `runtime-test`,
  `loader-policy-test`, `aot-test`, `transfer-test`.
  `cell-outcomes-test` keeps its literal (tests the harness).
- `aot-test/producer-child`: form from `harness/check-form` (printed by
  `clojure.main -e`); `produced?` is the one producer support assertion.
- Verify: 4 touched cross-JVM namespaces (Java 8) 2349 assertions pass;
  `transfer-test`, `runtime-test`, `aot-test` under Java 8, Valhalla and
  Valhalla preview 383 assertions pass each; fmt, kondo clean. Labels
  unchanged; `after-record/` not retaken.
- Addressed 3 review steps.

## Code-shaper review (pass 2)

- Added 4 steps to be addressed.

## Code-shaper review (pass 2) fixes

- `cell-harness/fallback-context`; `fallback-rejection` uses it; local
  copies in `aot-test`, `transfer-test`, `public-field-aot-test`,
  `child-transfer-test`, `identity-receiving-cross-jvm-test`,
  `field-transfer-test` removed.
- `cell-registry/class-name` (namespace or name, `namespace-munge`); used in
  `absent?`, `identity-receiving-checks`, `warning-checks`,
  `transfer-test`, `field-transfer-test`, and `identity-loader-checks`
  (same rule, not listed).
- `aot-test/produced?` asserts under `"producer"`; `produce` gives nil on
  exit not 0; `source-free-value-aot-loading-test` uses `when-let`.
- `field-transfer-test/each-expansion` uses `cell-ns-name`.
- Verify: `:cross-jvm-test` (Java 8) 4288 assertions pass; `:fallback-test`
  under Java 8, Valhalla and Valhalla preview 2012 assertions pass each;
  fmt, kondo clean. Labels unchanged; `after-record/` not retaken.
- Addressed 4 review steps.

## Code-shaper review (pass 3)

- Added 4 steps to be addressed.

## Code-shaper review (pass 3) fixes

- `jvm-config/configuration` gives `:preview-off` (`:valhalla` with
  `:jvm-opts []`); 23 copies in 15 namespaces and the local
  `loader-policy-test` `preview-off` removed.
- `jvm-config/policies`, `jvm-config/executions` and
  `jvm-config/child-configuration` (runtime, execution, policy); all full
  axis literals and `(assoc (update ... :jvm-opts into ...) :policy ...)`
  copies use them (`transfer-test` threading forms too). `aot-test/policies`
  removed; `child-transfer-test/consumer-child` takes the configuration.
  Subset literals (`["silent" "warn"]`) and fixed options (exports,
  `-XX:-UseArrayFlattening`) kept.
- `aot-fixture/produce` passes `compile-producer` options;
  `aot-test/source-free-runtime-only-fallback-test` and
  `controlled-field-capability-test/compile-controlled` use it
  (`compile-controlled` now returns a boolean; callers use `when`).
- `registry/class-name` in `identity-loader-checks/reject`,
  `identity-receiving-cross-jvm-test` (2), `identity-receiving-test` (5).
  `test/runtime/value_type/value_receiving_test.clj` copies kept (runtime
  suite; not listed, no `cell-registry`).
- Verify: `:cross-jvm-test` (Java 8) 4288 assertions pass; `:fallback-test`
  under Java 8, Valhalla and Valhalla preview 2012 assertions pass each;
  fmt, kondo clean. Labels unchanged; `after-record/` not retaken.
- Addressed 4 review steps.

## Code-shaper review (pass 4)

- Added 5 steps to be addressed.

## Code-shaper review (pass 4) fixes

- `jvm-config/with-execution` (runtime, execution); `child-configuration`
  uses it. The `interpreter? [false true]` and
  `[["--enable-preview"] ["--enable-preview" "-Xint"]]` axes in
  `public-field-aot-test`, `public-native-field-aot-test`,
  `public-mixed-field-aot-test` and `generated-value-aot-test` use it with
  `config/executions`; local `value-options` removed. `:jvm-opts` vectors
  unchanged.
- `aot-fixture/incompatible-consumer-child` (fixture-ns, jvm, classpath,
  policy, spec): context prefix `"<java> / <policy>"`; five callers use it.
- `field-transfer-checks` and `controlled-field-capability-test`
  `holder-check` use `consumer-aot-checks/builders` and `outcome`; local
  `reflective` and the `InvocationTargetException` unwrap removed.
  Construction order is now direct, factory, reflective (entries equal).
  `field-transfer-checks/outcome` maps the expected exception class name to
  `:rejected`; another failure gives its class name in place of a throw.
  `holder-check` takes the namespace name.
- Controlled AOT cells use `fixture/consumer-child`; the Java AOT test
  joins compile and javac in one `when`.
- `compile-producer` passes only present `:wrap`, `:after`, `:kind`.
- Verify: `:cross-jvm-test` (Java 8) 4288 assertions pass; `:fallback-test`
  under Java 8, Valhalla and Valhalla preview 2012 assertions pass each;
  fmt, kondo clean. Labels unchanged; `after-record/` not retaken.
- Addressed 5 review steps.

## Code-shaper review (pass 5)

- Added 6 steps to be addressed.

## Code-shaper review (pass 5) fixes

- `jvm-config/receivers` (baseline, preview off, preview on) and
  `jvm-config/incompatible-receivers` (baseline, preview off) replace the
  receiver literals; `identity-aot-test/receivers` removed;
  `runtime-test/policy-test` uses `(cons nil config/policies)`.
- `aot-fixture/incompatible-consumers` (fixture-ns, source, producer,
  receivers, executions, spec) holds the five incompatible-receiver AOT
  bodies. Each producer configuration is kept (`generated-value-aot-test`
  without policy, the others `"error"`); tests without an execution axis
  give `[[]]`.
- `transfer-test/concurrent-form` and `concurrent-cells` build the
  concurrent cell forms and cells.
- `child-transfer-test/consumer-child` takes `check`, `args` and
  `expected`; no symbol test.
- `identity-loader-checks/public-stale-sibling` builds its constructions
  with `consumer-aot-checks/builders` (map, order free).
- `controlled-field-capability-test/controlled-aot-consumers` holds the
  frame of the two controlled AOT tests.
- Verify: `:cross-jvm-test` (Java 8) 4288 assertions pass; `:fallback-test`
  under Java 8, Valhalla and Valhalla preview 2012 assertions pass each;
  fmt, kondo clean. Labels unchanged; `after-record/` not retaken.
- Addressed 6 review steps.

## Code-shaper review (pass 6)

- Added 4 steps to be addressed.

## Code-shaper review (pass 6) fixes

- `aot-fixture/with-produced` (fixture-ns, source, producer, f, produce
  options) holds the produced-artifact frame of the listed tests, of
  `incompatible-consumers`, `controlled-aot-consumers` (with `:wrap`; local
  `compile-controlled` removed) and of
  `aot-test/source-free-runtime-only-fallback-test` (same frame, not
  listed). `field-aot-test/persisted-restricted-field-output-test`
  compiles in its cell (no producer): `with-fixture` kept. Consumer loops
  use `:let [jvm (config/with-execution runtime execution)]` and
  `(classpath-with jvm output)`; javac tests keep one `classpath`.
- `consumer-aot-checks/java-consumer-result` replaces 8 result literals.
- `aot-fixture/persisted-output?` (storage, descriptor) and
  `aot-fixture/inspection-cell`. `field-aot-test` composes the predicate
  under `:output`; its description is built with the new public
  `cell-harness/description` (was private `reported-expected`), so it is
  unchanged.
- `cell-registry`: parameters `ns-or-name`, `binary-name`.
- Verify: `:cross-jvm-test` (Java 8) 4288 assertions pass; `:fallback-test`
  under Java 8, Valhalla and Valhalla preview 2012 assertions pass each;
  fmt, kondo clean. Labels unchanged; `after-record/` not retaken.
- Addressed 4 review steps.

## Code-shaper review (pass 7)

- Added 3 steps to be addressed.

## Code-shaper review (pass 7) fixes

- `jvm-config/with-executions` (runtime in each of `executions`). The 9
  `for [execution ... :let [jvm ...]]` loops on `valhalla` use it;
  `public-field-aot-test` `receivers` and `value-receivers` removed
  (`(mapcat config/with-executions (config/receivers configuration))`
  inline). `controlled-aot-consumers` keeps `execution`: its cell context
  `(pr-str [policy execution])` needs the execution value, and the jvm
  holds only the joined `:jvm-opts`. Labels unchanged.
- `public-field-aot-test/strict-loads?`: one `let` of the first load and
  the retry classes for description and predicate.
- `holder-check` uses `consumer-aot-checks/annotation-names`.
- Verify: `:cross-jvm-test` (Java 8) of the 5 changed namespaces, 1004
  assertions pass; fmt, kondo clean. `after-record/` not retaken.
- Addressed 3 review steps.

## Code-shaper review (pass 8)

- Added 3 steps to be addressed.

## Code-shaper review (pass 8) fixes

- `aot-fixture/source-free-consumers` (fixture-ns, source text, producer,
  jvms, policies, form, expected of policy, consumer-child options,
  `:java [source & javac-options]`). javac runs on the producer without
  `:policy` (the jvm of each former call). 16 tests use it:
  `public-field-aot-test` (7), `public-native-field-aot-test` (3),
  `public-mixed-field-aot-test` (3), `generated-value-aot-test` (2),
  `identity-aot-test` (2), `field-aot-test/java-restricted-field-consumer-test`.
  `identity-aot-test/java-identity-backend-consumer` uses policies `[nil]`.
  `incompatible-consumers` kept: its context depends on jvm and policy,
  which the consumer-child options cannot give.
- `field-transfer-test`: local `configurations` removed; inline
  `for [runtime execution policy :let [configuration ...]]`.
- `cell-harness/produce` returns `:result` only on exit 0 and empty
  stderr; docstring updated.
- Verify: `:cross-jvm-test` (Java 8) of 10 changed namespaces, 3006
  assertions pass; `:fallback-test` `transfer-test` 151 pass; fmt, kondo
  clean. `after-record/` not retaken.
- Addressed 3 review steps.

## Code-shaper review (pass 9)

- Added 3 steps to be addressed.

## Code-shaper review (pass 9) fixes

- `aot-fixture/source-free-consumers` options `:context-of` (fn of jvm
  and policy → cell context) and `:produce` (map of `produce` options).
  `incompatible-consumers`, `controlled-aot-consumers` and
  `aot-test/source-free-runtime-only-fallback-test` use it;
  `incompatible-consumer-child` removed (no other caller); `aot-test`
  passes `[selected]` as jvms.
- Child order change: `controlled-aot-consumers` now loops jvm (execution)
  outer, policy inner (was policy outer). Context `(pr-str [policy
  execution])` unchanged: execution is the `:jvm-opts` of the jvm after the
  `valhalla` options. Labels and child counts unchanged.
- `field-aot-test/source-free-restricted-field-loading-test` uses
  `source-free-consumers` (`[valhalla] ["error"]`).
- `holder-check` reads `:value?`/`:eligible?` with
  `consumer-aot-checks/value-class?`.
- Verify: `:cross-jvm-test` (Java 8) of the 6 changed namespaces, 1067
  assertions pass; `:fallback-test` `aot-test` on Java 8, EA3, EA3 preview
  (68 pass each); fmt, kondo clean. `after-record/` not retaken.
- Addressed 3 review steps.

## Code-shaper review (pass 10)

- Added 5 steps to be addressed.

## Code-shaper review (pass 10) fixes

- `controlled-aot-consumers`: `execution-of` is `(zipmap jvms
  config/executions)` of the jvms it makes; no inverse of
  `with-execution`. Context and child order unchanged.
- `aot-fixture`: `compile-producer`, `source-free-consumers` (consumer-child
  options, `(:produce options)`) pass option maps (Clojure 1.12 trailing
  map); `mapcat identity` removed.
- `incompatible-consumers` takes jvms; callers pass
  `(config/incompatible-receivers configuration)` (2) or
  `(mapcat config/with-executions ...)` (3); same child order.
- `field-aot-test/separately-compiled-restricted-field-consumer-test`:
  `artifact` runtime bound once, used for `compile-producer` and
  `classpath-with`.
- After record retaken at `41ebc3a` (last code change). Raw:
  `target/test-record/after12/`, `after12-limit1/`; summaries replace
  `after-record/*.edn`. `VALUE_TYPE_BASELINE_JAVA`,
  `VALUE_TYPE_VALHALLA_JAVA` and `JAVA_CMD` are the mise paths
  (`temurin-8`, `openjdk-27.0.0-jep401ea3+1-1`): labels hold the Java path,
  so other paths of the same builds give false mapping findings (a first
  run with the `jvm-config` default paths did; discarded).
  - Limit 4 (`/usr/bin/time -p` real, exit 0, 0 failures):
    `fallback-java8` 109.66 s, `fallback-preview-off` 34.45 s,
    `fallback-preview-on` 34.82 s, `cross-jvm` 335.77 s; sum 514.70 s =
    12.7% of 4060.34 s. Acceptance 2 time holds.
  - Limit 1: `fallback-java8` 229.23 s, `cross-jvm` 734.39 s, exit 0;
    `:assertions` label vectors equal limit 4 (2012, 4288). Acceptance 3
    holds.
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding; label mapping: only the known 2
    `fields-test/public-imported-concrete-field-test` gensym labels per
    fallback command.
- Verify: fmt, kondo clean.
- Addressed 5 review steps.

## Implementation review (pass 13)

- No new issues.

## Implementation test review (pass 14)

- No new issues found.

## Implementation test review (pass 15)

- Added 1 step to be addressed.

## Test review pass 15 fixes

- `cell-outcomes-test/rejections-test`: nested match true; false for
  message without `warning-prefix`, other chain, other non-rejection key;
  description holds `rejection-description` keys.
- New `value-type.aot-fixture-test/persisted-output-test` (in-process):
  true case; false for other storage, empty `:companion-references`,
  other descriptor. Added to the in-process command in
  `doc/development/runtime.md` "Child JVMs".
- In-process command: 16 tests, 93 assertions, 0 failures. fmt, kondo,
  vale clean.
- Addressed 1 review step.

## Implementation test review (pass 16)

- Added 1 step to be addressed.

## Test review pass 16 fixes

- `cell-outcomes-test` docstring: cell outcomes, `assert-child` report
  translation, `cell-child` options, rejection predicates; lines wrapped.
- `rejection-test` uses `harness/source-rejection-chain`.
- `cell-outcomes-test`: 9 tests, 44 assertions, 0 failures. fmt, kondo
  clean.
- Addressed 1 review step.

## Implementation test review (pass 17)

- No new issues found.

## Implementation docs review (pass 3)

- No new issues found.

## Code-shaper review (pass 11)

- Added 3 steps to be addressed.

## Code-shaper review (pass 11) fixes

- `cell-harness/produce`, `aot-fixture/compile-producer`, `aot-fixture/javac`:
  verdict is `(every? true? [(is ...) ...])`; each `is` runs once, no copy
  of the checks. `aot-test/produced?` returns its `is`.
- `cell-harness/runtime-context` (runtime, parts): 15 copies of
  `(pr-str [(:java x) (:jvm-opts x) ...])` removed; `consumer-child`
  default context uses it. Labels unchanged.
- `aot-test/producer-child`, `produce`: no `classpath` parameter; use
  `(fixture/classpath-with jvm source)`.
- Verify (mise Java 8 parent): `:cross-jvm-test` 84 tests, 4297
  assertions, 0 failures; `:fallback-test --namespace value-type.aot-test`
  68 assertions, 0 failures. fmt, kondo clean. After record not retaken
  (labels unchanged).
- Addressed 3 review steps.

## Code-shaper review (pass 12)

- Added 3 steps to be addressed.

## Code-shaper review (pass 12) fixes

- `value-type.array-capability` (`test/cross-jvm`): `export-option`,
  `allocation-configurations` (six rows), `expected-flags`,
  `rejection-description`, nil-safe `rejection?`. Used by
  `array-matrix-test`, `array-sequence-boundary-test`,
  `array-boundary-test` and `array-info-boundary-test`; local `exports`,
  `export-option` and `configurations` removed. Each description/predicate
  pair binds `flags` once.
- `array-boundary-test`: the cause column is removed from its rows; the
  cause comes from the reason (same values). Description key order of the
  rejection maps changed (map content unchanged).
- `safe-inspection-boundary-test` keeps its own rows: other order and
  inspection reasons; it uses only `export-option` and `expected-flags`.
- `field-transfer-test`: labels use the bound `policy` in the four loops
  that bind it (two listed, plus `supported-` and `mixed-public-transfer-test`).
- Verify (mise Java 8 parent): `:cross-jvm-test` of the 5 changed
  namespaces, 11 tests, 1544 assertions, 0 failures. fmt, kondo clean.
- Addressed 3 review steps.

## Code-shaper review (pass 13)

- Added 4 steps to be addressed.

## Code-shaper review (pass 13) fixes

- `consumer-aot-checks/unavailable-outcome?` (class name, capability,
  extra names, outcome); `unavailable-description` takes extra names as a
  vector. Used by `field-transfer-test/unavailable` (shape check
  `[2 2]`, then each attempt) and `incompatible-value-receiving-test`
  (`:let` of `extra-names`). `unavailable-message?` had no other caller;
  removed.
- `field-capability-test/policy-expected`: local `attempts-of`.
- `jvm-config/value-producer`, `identity-producer`, `receiver-jvms`,
  `incompatible-receiver-jvms`. Consumer maps with extra keys
  (`:fixture-ns`, `:classpath`) keep their `assoc`.
- `consumer-aot-checks/checked-rejections`; mixed tests use
  `(into checked-rejections checked-rejections)`, controlled test
  `(into [7] checked-rejections)`.
- Verify (mise Java 8 parent): `:cross-jvm-test` 84 tests, 4297
  assertions, 0 failures (360 s). fmt, kondo clean.
- Addressed 4 review steps.

## Code-shaper review (pass 14)

- Added 3 steps to be addressed.

## Code-shaper review (pass 14) fixes

- `transfer-test/produce` and `aot-test/source-free-runtime-only-fallback-test`
  use `config/identity-producer`; `classpath` binding removed (baseline and
  selected classpath are both `java.class.path`).
- `consumer-aot-checks/native-annotations`; 8 copies removed. Descriptor
  form (`Ljdk/internal/vm/annotation/NullRestricted;`) kept: other shape.
- Private `consumer-aot-checks/call-form`; holder, incompatible and strict
  forms use it. Before/after forms equal in a REPL. `java-consumer-form`
  kept (extra requires, `:wrap`).
- Verify (mise paths): `:cross-jvm-test` of 4 changed namespaces, 18 tests,
  990 assertions, 0 failures; `:fallback-test` of `aot-test` and
  `transfer-test` on Java 8 and Valhalla, 8 tests, 219 assertions each,
  0 failures. fmt, kondo clean.
- Addressed 3 review steps.

## Code-shaper review (pass 15)

- Added 7 steps to be addressed.

## Code-shaper review (pass 15) fixes

- `transfer-checks/serialize` (expand keyword, `:print-meta?` default
  true, `:prepare`) and `transfer-checks/evaluate` (`:eval`, `:source`,
  `:analysis`). `:analysis` calls the function inside its eval (the
  field-transfer form); child-transfer and controlled cells used an outside
  call. `controlled-expansions` calls `transfer-checks/produce`; the
  `VALUE-DEFINITION` placeholder and `clojure.walk` removed.
- Renamed label part: `controlled-native-absence-receiving-test` mode
  `:function` → `:analysis` (12 labels in `after-record/cross-jvm.edn`
  still give `:function`; record not retaken).
- `consumer-aot-checks/checked-outcomes`, `root-failure`, `field-facts`;
  `builders` takes `ns-or-name` (`(str ns-or-name)`). Field-transfer
  constructions give `:rejections` (mixed: per field, nil then wrong) in
  place of `:nil`/`:wrong`.
- Verify (mise paths): `:cross-jvm-test` 84 tests, 4297 assertions, 0
  failures; `:fallback-test` of `runtime-test`, `transfer-test`,
  `identity-loader-test` on Java 8, preview off and preview on, 18 tests,
  331 assertions each, 0 failures. fmt, kondo clean.
- Addressed 7 review steps.

## Code-shaper review (pass 16)

- Added 4 steps to be addressed.

## Code-shaper review (pass 16) fixes

- `field-policy-checks`: alias `registry`; caught Throwable `thrown`.
- `cell-registry/twice` (`[(f) (f)]`, eager, in order): 18 copies in 13
  namespaces (`transfer-checks`, `field-transfer-checks` (3),
  `field-policy-checks`, `identity-loader-checks`, `consumer-aot-checks`
  (2), `aot-checks` (3), `child-transfer-checks` (`[0 1]`),
  `warning-checks`, `function-aot-checks`, `array-matrix-checks`,
  `array-sequence-checks`, and the child forms of `runtime-test/policy-test`
  and `array-boundary-test`, which now require `value-type.cell-registry`).
  Kept: `expansion-checks` (uses the index in type names),
  `array-info-boundary-test` (2, not listed; child forms observe require
  order), `test/resources/build/isolated-consumer.clj` (runs without test
  paths).
- `cell-harness/controlled-absence`; local def in
  `controlled-field-capability-test` removed;
  `function-redefinition-test` template uses a `CONTROLLED-ABSENCE`
  placeholder that `cell-form` replaces. Old and new cell forms equal
  (REPL; only the `#()` gensym differs).
- After record retaken at `8292c19` (last code change). Raw:
  `target/test-record/after13/`, `after13-limit1/`; summaries replace
  `after-record/*.edn`. Mise paths (`temurin-8`,
  `openjdk-27.0.0-jep401ea3+1-1`) as at pass 10.
  - Limit 4 (`/usr/bin/time -p` real, exit 0, 0 failures):
    `fallback-java8` 118.04 s, `fallback-preview-off` 39.93 s,
    `fallback-preview-on` 37.28 s, `cross-jvm` 348.76 s; sum 543.01 s =
    13.4% of 4060.34 s. Acceptance 2 time holds (≤ 25%).
  - Limit 1: `fallback-java8` 228.64 s, `cross-jvm` 776.86 s, exit 0;
    `:assertions` label vectors equal limit 4 (2012, 4297). Acceptance 3
    holds.
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding. Label mapping, 8 per fallback command: the known 2
    `fields-test/public-imported-concrete-field-test` gensym labels, and 6
    first-record `controlled-native-absence-receiving-test` labels with
    `:function` (pass 15 rename to `:analysis`; the after labels give
    `:analysis`, 12 in `cross-jvm.edn`).
- Verify: fmt, kondo clean.
- Addressed 4 review steps.

## Code-shaper review (pass 17)

- Added 7 steps to be addressed.

## Code-shaper review (pass 17) fixes

- `transfer-checks/expanders` public; new `transfer-checks/evaluate-form`
  (`:eval`, `:source` printed with metadata, `:analysis` call inside the
  eval); `evaluate` uses it (`:source` loads the text).
  `identity-loader-checks` uses both; local `expansions` and `evaluate`
  removed; `reject` uses `evaluate-form` (the `:analysis` call runs only
  when the compile does not throw).
- Renamed label part: `loader-policy-test/fresh-policy-loader-rejection-test`
  mode `:function` → `:analysis` (36 labels in `after-record/cross-jvm.edn`
  still give `:function`; record not retaken).
- Caught Throwable `thrown` in the listed namespaces, and in
  `array-matrix-checks` and `array-sequence-checks` (not listed).
  `aot-checks/compile-fixture` let local `thrown` → `result` (it held the
  map, and the catch now binds `thrown`).
- `aot-checks/load-value-fixture` uses `consumer-checks/value-class?`.
- `receiving-definition` removed; `transfer-test/expands`;
  `aot-test` `producer-child`/`produce` take a runtime with `:policy`;
  `produce-mixed` uses `transfer/produce`.
- Verify (mise paths): `:fallback-test` on Java 8, preview off, preview
  on: 58 tests, 2012 assertions each, 0 failures; `:cross-jvm-test` 84
  tests, 4297 assertions, 0 failures. fmt, kondo clean.
- Addressed 7 review steps.

## Code-shaper review (pass 18)

- Added 4 steps to be addressed.

## Code-shaper review (pass 18) fixes

- `transfer-checks/modes` `[:eval :analysis :source]`,
  `transfer-checks/expands` `[:macroexpand :macroexpand-1]`; `expanders`
  is defined from `expands`. Used in `identity-loader-checks`,
  `loader-policy-test`, `transfer-test`, `field-transfer-test`,
  `child-transfer-test`, `controlled-field-capability-test` (also the
  definition loop: `(symbol (name expand))`); locals removed. Label sets
  unchanged. Changed cell order (cells in one child; child order
  unchanged):
  - modes `:source :analysis` → `:analysis :source`:
    `field-transfer-test` (all six tests), `child-transfer-test` (all
    four tests);
  - expands `:macroexpand-1 :macroexpand` → `:macroexpand :macroexpand-1`:
    `controlled-native-absence-construction-test`,
    `controlled-native-absence-receiving-test`.
- `identity-receiving-test`: `transfer/evaluate-form`, `transfer/expanders`
  (`:internal` local), `checks/builders` by name. Renamed label part:
  `receiving-identity-fields-test` mode `:function` → `:analysis`.
  Assertion count unchanged (2012).
- Docstrings wrapped: `identity-loader-checks/stale-sibling-case`,
  `loader-policy-test/strict-rejection`.
- After record retaken at `691584f` (last code change). Raw:
  `target/test-record/after14/`, `after14-limit1/`; summaries replace
  `after-record/*.edn`. Mise paths as at pass 10.
  - Limit 4 (`/usr/bin/time -p` real, exit 0, 0 failures):
    `fallback-java8` 122.17 s, `fallback-preview-off` 37.35 s,
    `fallback-preview-on` 38.94 s, `cross-jvm` 368.74 s; sum 567.20 s =
    14.0% of 4060.34 s. Acceptance 2 time holds (≤ 25%).
  - Limit 1: `fallback-java8` 225.29 s, `cross-jvm` 2345.31 s (3× the
    pass-16 776.86 s; cause not found, no test failed), exit 0;
    `:assertions` label vectors equal limit 4 (2012, 4297). Acceptance 3
    holds.
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding. Label mapping, 46 per fallback command: the known 2
    `fields-test/public-imported-concrete-field-test` gensym labels, and
    44 first-record labels with `:function` (renamed to `:analysis`):
    `controlled-native-absence-receiving-test` 6 (pass 15),
    `loader-policy-test/fresh-policy-loader-rejection-test` 28 and
    `identity-loader-test/public-stale-sibling-class-test` 2 (pass 17),
    `identity-receiving-test/receiving-identity-fields-test` 8 (pass 18).
    Each after label gives `:analysis`.
- Verify: fmt, kondo clean (0 errors, 0 warnings).
- Addressed 4 review steps.

## Code-shaper review (pass 19)

- Added 4 steps to be addressed.

## Code-shaper review (pass 19) fixes

- Shadowing removed: `cell-harness/described` parameter `data`; private
  `cell-harness/check` → `check-result`;
  `consumer-aot-checks/unavailable-outcome?` parameter `result`;
  `cell-registry/thrown` local `root-cause`;
  `controlled-aot-consumers` parameter `consumer-expected`.
- `identity-receiving-test`: invalid values paired with
  `checks/checked-rejections`, read with `checks/outcome` (one `is` each);
  `InvocationTargetException` import removed; `args` bound once (also for
  the equal `[input "label" nil 7 2.5]` expectations; optional loop uses
  `(assoc args 2 optional)`); native-modes catch local `thrown`.
- `controlled-native-absence-construction-test`: child form calls
  `(value-type.transfer-checks/expanders expand)`.
- Record run at `b2c7cf7` (code commit; mise paths as at pass 10). Raw:
  `target/test-record/after15/`, `after15-limit1/`. All six runs exit 0,
  0 failures; assertions 2012 (`fallback-java8`), 4297 (`cross-jvm`).
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding; label mapping findings 46 per fallback command, the same set
    as at `691584f`.
  - Summaries without `:ms` equal `after-record/*.edn` (labels, cell
    counts, children), except the 2 known
    `fields-test/public-imported-concrete-field-test` gensym labels per
    fallback command. Record not replaced.
  - Limit 4 real: `fallback-java8` 135.49 s, `fallback-preview-off`
    43.74 s, `fallback-preview-on` 94.58 s, `cross-jvm` 932.57 s; sum
    1206.38 s = 29.7% of 4060.34 s. Higher than at `691584f` (567.20 s);
    no code cause found (no new child, same labels); machine load not
    controlled. The record of acceptance 2 time stays `691584f`. Limit 1:
    `fallback-java8` 343.72 s, `cross-jvm` 1072.68 s.
  - Finding (acceptance 3 order): `check_record.clj` gives limit-1 =
    limit-4 `:assertions` labels false for both commands. The label
    sequence of each Var is equal (except the gensym labels); the
    namespace order differs. Cause: Scry 0.1.61
    `scry/clojure_test.clj` runs `(group-by (comp :ns meta) vars)`; with
    more than 8 namespaces the map is a hash map keyed by `Namespace`
    (identity hash), so the namespace order can change in each JVM run.
    Earlier records (after13 vs after14) also show different namespace
    orders. Not changed in this pass.
- Verify: fmt, kondo clean (0 errors, 0 warnings).
- Addressed 4 review steps.

## Code-shaper review (pass 20)

- Added 8 steps to be addressed.

## Code-shaper review (pass 20) fixes

- `array-capability/require-orders`, `repeated?`; used in the three array
  matrix tests and the four two-outcome checks.
- `array-info-boundary-test`: private `supported-info`, `invalid-outcome`,
  `unavailable-outcome`, `runtime-prefix`; the predicate reads the runtime
  with `str/starts-with?` (was a regex of the same prefix). Outcome
  predicates compare the map without `:message` (child outcomes always
  have the four keys). `array-sequence-boundary-test`: private
  `definitions`.
- `cell-registry/twice` in `field-capability-test` and
  `array-info-boundary-test` (2).
- `runtime-test/fallback-expansion-policy-test`: `expansions`,
  `definitions` bound in the `:let`.
- `warning-checks/worker-count` (8); private `run-workers` (threads with
  `bound-fn`, ready latch with timeout, start latch; results in index
  order; rethrows a worker failure). `concurrent-rejections` used `future`
  with a start promise; now `run-workers`. `transfer-test/concurrent-expected`.
- Names: `runtimes` → `configuration`, one runtime `runtime`,
  `execution-options` → `execution` (`identity-receiving-cross-jvm-test`,
  `field-transfer-test`, `transfer-test`). A child configuration local is
  `child` (`field-transfer-test`, `run-check-cells`), so it does not hide
  the configuration map.
- Transfer axis values: `mode`, `expand`, `expand-index`
  (`identity-loader-checks`, `controlled-field-capability-test`,
  `field-transfer-test`; also `loader-policy-test`,
  `identity-receiving-test`). The expander fn local is `expander`.
- `consumer-aot-checks/source-absent?`; used in `consumer-aot-checks` (4),
  `function-aot-checks`, `field-aot-test` (3), `saved-cell`, `aot-checks`
  (3, in `assert`). Unused `clojure.java.io` requires removed.
- Run at `762590d` (code commit; mise paths as at pass 10). Raw:
  `target/test-record/after16/`. All four limit-4 runs exit 0, 0 failures;
  assertions 2012 (each fallback command), 4297 (`cross-jvm`). Limit 1 not
  run (no child order or label change).
  - `check_record.clj`: max cell count 1; no bound finding; 46 label
    mapping findings per fallback command, the same set as at `691584f`.
  - Summaries without `:ms` equal `after-record/*.edn`, except the known
    `fields-test` gensym labels. Record not replaced.
  - `/usr/bin/time -p` real is not valid in this run: the machine
    suspended (wall clock 1077.87 s, 1009.36 s, 38.06 s, 3938.22 s). Var
    time sums (`:ms`): 109.0 s, 38.9 s, 36.3 s, 331.1 s.
- Verify: fmt, kondo clean (0 errors, 0 warnings).
- Addressed 8 review steps.

## Implementation review (pass 12)

- Added 4 steps to be addressed.

## Implementation review (pass 12) fixes

- Acceptance 3 order: the namespace order of the `:assertions` vector is
  a Scry 0.1.61 property, not a runner property (`group-by (comp :ns
  meta)` gives a hash map keyed by `Namespace`, identity hash). The
  runner fixes the order inside a namespace (assertions on the test
  thread, in input order). `check_record.clj` now checks acceptance 3 for
  each namespace: equal namespace sets and equal label vectors of each
  namespace at limit 1 and limit 4.
- `fields-test/public-imported-concrete-field-test`: fixed contexts for
  the gensym child tags; class names stay gensym. Label changes:
  `["NullableChild<n>"]` → `["NullableChild"]`;
  `["imported_fields_<m>.NullableChild<n>"]` →
  `["imported_fields.NullableChild"]`.
- `check_record.clj` maps first labels before the match:
  `mode-renamed-vars` (4 Vars, `:function` → `:analysis`, passes 15, 17,
  18) and `fixed-tags` (the 2 gensym tags above), beside `moved-vars`.
  Header comment updated. A finding is a new deviation only.
- Final after record at `1be42d8` (last code commit; mise paths as at
  pass 10; no other test load). Raw: `target/test-record/after17/`,
  `after17-limit1/`; summaries replace `after-record/*.edn`. All six runs
  exit 0, 0 failures; assertions 2012 (each fallback command), 4297
  (`cross-jvm`).
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 113.93 s,
    `fallback-preview-off` 38.45 s, `fallback-preview-on` 42.05 s,
    `cross-jvm` 354.11 s; sum 548.54 s = 13.5% of 4060.34 s. Acceptance 2
    time holds (≤ 25%); no profile needed.
  - Limit 1: `fallback-java8` 226.96 s, `cross-jvm` 757.12 s.
  - `check_record.clj`: max cell count 1 in all four records; no bound
    finding; 0 label mapping findings for each fallback command (904
    first labels each); limit-1 = limit-4 label vectors of each namespace
    true for both commands (10 and 24 namespaces). Acceptance 3 holds.
- Verify: fmt, kondo clean (0 errors, 0 warnings).
- Addressed 4 review steps.

## Implementation review (pass 13)

- Added 2 steps to be addressed. `check_record.clj` on
  `target/test-record/after17` gives the recorded pass 12 results.

## Implementation review (pass 13) fixes

- `mementum/state.md`: suite time "about 11 minutes" → "about 9 minutes"
  (548.54 s at `1be42d8`).
- Blocked: acceptance 3 reading step (Scope 4, acceptance 3, plan). It
  needs a user decision (accept the per-namespace reading), and
  `design.md` is read-only in this pass. `plan.md` is not changed before
  the decision, so that plan and design agree.
- Addressed 1 review step; 1 blocked.

## Implementation review (pass 14)

- No new steps. No code change since pass 13. The pass 13 acceptance 3
  reading step is still open: it needs a user decision.

## Implementation test review (pass 18)

- No new issues found.

## Implementation test review (pass 19)

- Added 1 step to be addressed.

## Implementation test review (pass 19) fixes

- New in-process namespace `value-type.unavailable-predicates-test`:
  true and false cases of `array-capability/rejection?` and
  `consumer-aot-checks/unavailable-outcome?`; 2 tests, 13 assertions.
  Added to the in-process command in `doc/development/runtime.md`.
- Still blocked: pass 13 acceptance 3 reading step. It needs a user
  decision (accept the per-namespace reading).
- Verify: in-process command 106 assertions pass; fmt, kondo clean.
- Addressed 1 review step; 1 blocked.

## Implementation test review (pass 20)

- Added 2 steps to be addressed.

## Implementation test review (pass 20) fixes

- `unavailable-predicates-test`: false cases for an unavailable message
  without the class name and without `--enable-preview`, and an array
  message without `--enable-preview` (private `without`). In-process:
  2 tests, 16 assertions pass.
- `cross-jvm` after record retaken at `67a57ec` (mise paths as at pass
  10). Raw: `target/test-record/after18/`, `after18-limit1/` (fallback
  records copied from `after17`; no fallback code change). Exit 0, 0
  failures, 4313 assertions, 86 tests, 25 namespaces. Limit 4 348.67 s;
  limit 1 778.59 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 label
    mapping findings; limit-1 = limit-4 label vectors of each namespace
    true (10 and 25 namespaces). Acceptance 3 holds (per-namespace
    reading). Fallback summaries unchanged.
- Still blocked: pass 13 acceptance 3 reading step. It needs a user
  decision (accept the per-namespace reading).
- Addressed 2 review steps; 1 blocked.

## Implementation test review (pass 21)

- No new issues found.

## Implementation docs review (pass 4)

- No new issues found.

## Code-shaper review (pass 21)

- Added 3 steps to be addressed.

## Implementation review (pass 15)

- No new steps. Open: pass 13 acceptance 3 reading step (needs a user
  decision) and the 3 code-shaper pass 21 steps.

## Implementation test review (pass 22)

- No new issues found.

## Implementation test review (pass 23)

- No new issues found.

## Implementation docs review (pass 5)

- No new issues found.

## Code-shaper review (pass 22)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision) and the 3 code-shaper pass 21 steps.

## Code-shaper review (passes 21, 22) fixes

- `check_record.clj`: `moved-vars` maps first Var → after Var;
  `context-renames` maps a Var → context functions (`analysis-mode`,
  `fixed-tag`); `rename` applies each mapping. Output equal on `after17`
  and `after18`.
- `isolated-sibling-loader-test/public-isolated-sibling-construction-test`
  uses `transfer/expands`, `transfer/expanders`, `transfer/modes` and
  `transfer/evaluate-form`. Label change: `["isolated sibling through
  :function"]` → `["isolated sibling through :analysis"]`. Order
  unchanged (`:eval :analysis :source`; `:macroexpand :macroexpand-1`).
  Var added to `context-renames`.
- `jvm-runner/policy-option`; `array-capability` uses it and
  `message-names?` with private `rejection-names`.
- After record at `766e3c2` (mise paths as at pass 10). Raw:
  `target/test-record/after19/`, `after19-limit1/`. All six runs exit 0,
  0 failures; 2012 assertions (each fallback command), 4313 (`cross-jvm`).
  Limit 4: java8 109.34 s, preview-off 33.45 s, preview-on 34.86 s,
  cross-jvm 357.81 s; total 535.46 s = 13.2% of 4060.34 s. Limit 1:
  java8 234.01 s, cross-jvm 745.68 s. `check_record.clj`: max cell count
  1; no bound finding; 0 label mapping findings; limit-1 = limit-4 label
  vectors of each namespace true (10 and 25 namespaces). Acceptance 3
  holds (per-namespace reading). `after-record/*.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step. It needs a user
  decision (accept the per-namespace reading).
- Addressed 5 review steps; 1 blocked.

## Code-shaper review (pass 23)

- Added 4 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 23 fixes

- `aot-test/source-free-value-aot-loading-test`: `chain-class` bound once;
  predicate returns a boolean.
- `aot-fixture/inspection-child` (jvm, output, `[context field expected]`
  cells) used in `public-field-aot-test/inspection` and
  `public-mixed-field-aot-test`; `field-aot-test` uses
  `config/value-producer` twice.
- `jvm-runner`: private `kind`; `label-record`: private `var-ns`.
  `check_record.clj` output on `after19` equal before and after.
- Verify (mise paths, limit 4): three fallback commands 2012 assertions,
  `cross-jvm` 4313 assertions; all exit 0, 0 failures. Labels unchanged;
  record kept.
- Still blocked: pass 13 acceptance 3 reading step (needs a user
  decision: accept the per-namespace reading).
- Addressed 4 review steps; 1 blocked.

## Code-shaper review (pass 24)

- Added 4 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 24 fixes

- `consumer-aot-checks/constructions` (`[:direct :factory :reflective]`,
  the order of `builders`) and `each-construction`; 3 `zipmap` and 13
  `(vec (repeat 3 x))` copies replaced.
- `function-redefinition-test`: `cell-registry/twice`; context part bound
  once in the `for` `:let`.
- `cell-harness/controlled-absence` is a function of a body form: it
  loads `value-type.impl.runtime`, then wraps body in `with-redefs`. Used
  in 5 places of `controlled-field-capability-test` (also
  `controlled-expansions`) and through a `(CONTROLLED body)` placeholder
  in `function-redefinition-test`. The `:requires` option of
  `java-consumer-form` had no other caller; removed.
- Docstrings over 80 characters wrapped (8 places).
- Verify (mise paths, limit 4; raw `target/test-record/after20/`): all
  four commands exit 0, 0 failures; 2012 assertions (each fallback
  command), 4313 (`cross-jvm`). `check_record.clj` (with `after19-limit1`):
  0 mapping findings; label vectors of each namespace equal. Summaries
  differ from `after-record/*.edn` (`766e3c2`) only in times; record kept.
- Still blocked: pass 13 acceptance 3 reading step (needs a user
  decision: accept the per-namespace reading).
- Addressed 4 review steps; 1 blocked.

## Code-shaper review (pass 25)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 25 fixes

- `cell-registry/attempt-count` (2) and `each-attempt`; `twice` runs
  `attempt-count` attempts. Expected attempts use `each-attempt` (15
  places), descriptions `(list 'repeat registry/attempt-count x)` (5),
  `array-capability/repeated?` and `field-transfer-test/unavailable` check
  the count. `expansion-checks` indexed attempts use the count. Not the
  attempt axis (kept): `identity-receiving-cross-jvm-test/receiving-policy`
  `[expected expected]` (FirstHolder, SecondHolder),
  `transfer-test/concurrent-defined` (two types), `runtime-test`
  `:invalid` (two invalid forms). Expand axes: `(count transfer/expands)`.
- `transfer/expands` with `transfer/expanders` in `expansion-checks`
  (also `discarded`, `invalid-constrained-discarded`),
  `identity-receiving-checks/nested` (expander called with `*ns*` bound to
  the cell namespace, not through `eval`), `public-field-annotations-test`;
  locals `expander`.
  - Changed label part (`runtime-test` `fallback-expansion-warning-timing-test`,
    `constrained-discarded-expansion-test`,
    `constrained-validation-before-policy-test`): entry point `:full` →
    `:macroexpand`, `:one` → `:macroexpand-1`. Child order unchanged.
    Mapping: `check_record.clj` `expand-keyword` in `context-renames`.
  - Changed generated type names (not labels): `expansion-attempts`
    `Full<bound?><i>`/`One<bound?><i>` → `Expand<expand-index><bound?><i>`.
- `function-redefinition-test`: template `repeated-definition-body` (only
  `DECLARATION`); `cell-form` builds `do`, requires and
  `harness/controlled-absence` with `walk/postwalk-replace`. Forms equal
  data (REPL, modulo `#()` gensym).
- Verify at the pass 25 commit (mise paths; raw `target/test-record/after21/`,
  `after21-limit1/`): all exit 0, 0 failures. Limit 4: `fallback-java8`
  122.12 s, `fallback-preview-off` 35.23 s, `fallback-preview-on` 38.68 s,
  `cross-jvm` 355.82 s; sum 551.85 s = 13.6% of 4060.34 s. Limit 1:
  `fallback-java8` 224.80 s, `cross-jvm` 747.46 s. `check_record.clj`:
  max cell count 1, no bound finding, 0 mapping findings; label vectors
  of each namespace equal at limit 1 and 4 (2012, 4313). Labels changed,
  so `after-record/*.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step (needs a user
  decision: accept the per-namespace reading).
- Addressed 3 review steps; 1 blocked.

## Code-shaper review (pass 26)

- Added 3 steps to be addressed.

## Code-shaper review (pass 26) fixes

- `transfer-checks/each-expand`; used in `runtime-test`
  `fallback-expansion-policy-test`, `field-transfer-test` (`attempts-of`,
  `unavailable` count check), `identity-receiving-cross-jvm-test`
  `strict-nested-public-declaration-test`. `cell-expansions` uses
  `(count transfer/expands)`. The `unavailable` description keeps
  `(list 'repeat (count transfer/expands) ...)` (description text
  unchanged). Parent axis: public `identity-receiving-checks/parent-types`,
  not `parents`: that name shadows `clojure.core/parents`, and the child
  writes the replace warning to stderr (5 `identity-receiving-cross-jvm-test`
  tests failed on the stderr check).
- `cell-registry/attempts` (f of the attempt index); `twice` uses it;
  private `expansion-checks/attempts` removed.
  `function-redefinition-test` `:distinct-classes?` uses
  `(apply distinct? (map :owner definitions))`.
- `identity-loader-checks`: expected `:construction` is
  `(zipmap consumer-checks/constructions (repeat {:stored? true
  :rejections checked-rejections}))`; each construction gives `:stored?`
  and `checked-outcomes` of the stale child; private `outcome` removed.
- Verify at the pass 26 code (mise paths; raw `target/test-record/after22/`):
  all exit 0, 0 failures (2012, 2012, 2012, 4313). Limit 4: `fallback-java8`
  118.16 s, `fallback-preview-off` 35.15 s, `fallback-preview-on` 36.55 s,
  `cross-jvm` 332.91 s. `check_record.clj`: max cell count 1, 0 mapping
  findings; summaries differ from `after-record/*.edn` in times only, so
  the record is kept.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 3 review steps; 1 blocked.

## Code-shaper review (pass 27)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper review (pass 27) fixes

- `consumer-aot-checks/incompatible-result`: `:loads
  (registry/each-attempt :unsupported-class-version)`; "each load".
- `controlled-field-capability-test/holder-check` and
  `function-redefinition-test/repeated-definition-body`: `:constructions`,
  one `{:stored :rejections}` for each construction; one literal map in the
  merge; deftype rejections `(mapv (constantly :accepted)
  checks/checked-rejections)`.
- Key names: `:construction` → `:constructions` (`field-transfer-checks`
  `consume-supported`, `consume-mixed`; `identity-loader-checks`);
  stored value `:field` → `:stored` (`consume-supported`), and
  `consume-mixed` `:fields` → `:stored` (the stored values, same rule);
  `field-policy-checks/evaluation` gives `:type` once in the attempt map.
- Verify (mise paths, limit 4; raw `target/test-record/after23/`): all exit
  0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8` 120.70 s,
  `fallback-preview-off` 38.34 s, `fallback-preview-on` 39.17 s,
  `cross-jvm` 355.29 s. `check_record.clj`: max cell count 1, 0 mapping
  findings; summaries differ from `after-record/*.edn` in times only, so
  the record is kept.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 3 review steps; 1 blocked.

## Code-shaper review (pass 28)

- Added 7 steps to be addressed.

## Code-shaper review (pass 28) fixes

- Aliases: `checks` only for `consumer-aot-checks`; `function-checks`
  (`public-field-aot-test`), `loader-checks` (`identity-loader-test`,
  `loader-policy-test`), `capability` (`unavailable-predicates-test`);
  `runtime-test` child requires `validation`, `expansion`.
- `consumer-aot-checks/construction-results` (ns-or-name, owner, field,
  valid, wrong; `:facts` maps the valid instance to a merged map) in
  `field-policy-checks`, `identity-receiving-checks/generated-value`,
  `controlled-field-capability-test/holder-check`,
  `function-redefinition-test`, `field-transfer-checks/consume-supported`
  (`:class?` through `:facts`). Builder entries named `build` (also
  `consume-mixed`, `identity-loader-checks`, `identity-receiving-test`).
- Field facts: `:type` from `field-facts`; `:storage` → `:type`
  (`receiving-policy`, `generated-value`), `consume-mixed` `:types` →
  `:type`. `generated-value` merges `field-facts`, so its expected adds
  `:final? true` (a fact of the shared shape). Stored-input fact
  `:stored-input?` (was `:stored?`, `:retained?`, `:input?`).
- `:constructions` is a vector of entries in each check.
  `identity-loader-checks/stale-sibling-case` (was a map keyed by
  construction) and `nested-warning-scope` (was `:rejections`, one outcome
  pair for each construction). `nested-warning-scope` entries are
  `{:stored-input? :rejections}` for each construction; the factory-only
  `:stored` class name and `:optional` are removed: `:stored-input?` true
  states that the stored value is the input `(->Child nil)`.
- `identity-receiving-checks/holder-types`; the test uses one private
  `each-type` (type axis, expected) for the holder and parent axes.
- `cell-registry/new-definition` in the 22 expected values
  (`cell-registry-test` literals kept).
- `call-form` takes an args vector and `:wrap`; the four `-form` functions
  use it.
- `bb clj-fmt:fix` also reformatted one line of `expansion-checks`.
- Verify (mise paths, limit 4; raw `target/test-record/after24/`): all exit
  0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8` 121.84 s,
  `fallback-preview-off` 38.86 s, `fallback-preview-on` 39.33 s,
  `cross-jvm` 355.51 s. `check_record.clj`: max cell count 1, 0 mapping
  findings; summaries differ from `after-record/*.edn` in times only, so
  the record is kept. fmt, kondo clean.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 7 review steps; 1 blocked.

## Code-shaper review (pass 29)

- Added 7 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 29 fixes

- `cell-registry/check-form` (moved from `cell-harness`; child-safe).
  `cell-harness` callers use `registry/check-form`; `consumer-aot-checks`
  `call-form` removed (the four `-form` functions call `check-form`);
  `java-consumer-form` has no `:wrap`; `invoke-install-form` uses
  `check-form`.
  - Snag: `controlled-absence` compiles its body as one form, so the
    `require` of `check-form` in the body runs after the call symbol is
    resolved (`ClassNotFoundException value-type.consumer-aot-checks`,
    6 failures in `controlled-native-absence-java-aot-test`). The test
    requires `value-type.consumer-aot-checks` before the wrapped form, as
    the other `controlled-absence` callers do; the docstring states the
    rule (`ca56c92`).
- `function-aot-checks/inspect-holder` merges `field-facts` with `:stored`
  (was `:value`); expected values add `:final? true`.
- `consumer-aot-checks/input-results` in `child-transfer-checks/consume`,
  `nested-warning-scope`, `stale-sibling-case`;
  `public-stale-sibling-expected` merges `registry/new-definition`.
- `:field` → `:stored` (`transfer-checks`, `warning-checks`, `aot-checks`,
  `field-aot-test`; also the `runtime-test` source cell);
  `cell-registry/identity?` (factory, args) in `transfer-checks`,
  `field-transfer-checks`, `warning-checks`, `aot-checks` (3).
  `aot-checks` `load-fixture` `:factory?` → `:binary-name?`,
  `invoke-runtime-fixture` `:factory?` → `:factory-class?`.
  `source-checks` `:unequal` kept (not listed; another fact).
- `field-aot-test/source-free-consumer`: entries take `[x]`;
  `checked-outcomes`.
- `warning-checks/identity-types`; `concurrent-defined` uses its count.
- `field-policy-checks/policy-attempts`.
- Verify (mise paths, limit 4; raw `target/test-record/after25/`): fallback
  at `a29e52d`, `cross-jvm` rerun at `ca56c92` (later change is in
  `controlled-field-capability-test` and a `cell-harness` docstring only).
  All exit 0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8`
  122.20 s, `fallback-preview-off` 36.74 s, `fallback-preview-on` 39.97 s,
  `cross-jvm` 359.97 s. `check_record.clj` (with `after21-limit1`): max
  cell count 1, 0 mapping findings, limit-1 = limit-4 labels of each
  namespace; summaries differ from `after-record/*.edn` in times only, so
  the record is kept. fmt, kondo clean.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 7 review steps; 1 blocked.

## Code-shaper review (pass 30)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 30 fixes

- `cell-harness/controlled-absence` takes `[requires body]`: one top-level
  `require` of `value-type.impl.runtime` and requires, then `with-redefs`.
  Caller rule removed from the docstring. 5 callers; `:produce` `:wrap` is
  `(partial harness/controlled-absence [])`.
- `validation-checks/invalid-fields`; `expansion-checks/invalid-declarations`
  (metadata, reason). `runtime-test` requires both (aliases `validation`,
  `expansion`, as the child forms).
- Private `expansion-checks/discarded-expansion`; `runtime-test` private
  `discarded-expansion`, `returned-definition`.
- Verify (mise paths, limit 4; raw `target/test-record/after26/`): all exit
  0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8` 132.40 s,
  `fallback-preview-off` 36.26 s, `fallback-preview-on` 38.23 s,
  `cross-jvm` 366.82 s. `check_record.clj` (with `after21-limit1`): max cell
  count 1, 0 mapping findings, limit-1 = limit-4 labels of each namespace;
  summaries differ from `after-record/*.edn` in times only; record kept.
  fmt, kondo clean.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 3 review steps; 1 blocked.

## Code-shaper review (pass 31)

- Added 4 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Code-shaper pass 31 fixes

- `cell-registry/factory-symbol` (simple name; namespace and simple name);
  used in `cell-registry`, `warning-checks`, `identity-receiving-checks`,
  `consumer-aot-checks` (2) and `fields-test` (7).
- `jvm-config/baseline-version-prefix`, `valhalla-version-prefix`,
  `preview-option`; used in `configuration` and
  `array-info-boundary-test/runtime-prefix`.
- `runtime-test` private `optional-loaded-form`; `capability-test` and
  `public-require-test` forms built with `list` (equal data).
- `cell-harness/err-matches?` uses `Pattern/quote`; public
  `cell-harness/occurrences` (text, s; nil as ""), used in the
  `fallback-expansion-warning-timing-test` predicate.
- Verify (mise paths, limit 4; raw `target/test-record/after27/`): all exit
  0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8` 171.70 s,
  `fallback-preview-off` 52.72 s, `fallback-preview-on` 53.06 s,
  `cross-jvm` 358.65 s (timing not a record run). `check_record.clj` (with
  `after21-limit1`): max cell count 1, 0 mapping findings, limit-1 =
  limit-4 labels of each namespace; summaries differ from
  `after-record/*.edn` in times only; record kept. fmt, kondo clean.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 4 review steps; 1 blocked.

## Implementation review (pass 16)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Implementation review pass 16 fixes

- `cell-registry/each` (axis, expected): expected once for each entry of
  axis. `each-attempt` (`(range attempt-count)`), `each-construction` and
  `each-expand` are defined with it; it replaces the private
  `identity-receiving-cross-jvm-test/each-type` (3) and the inline copies
  in `runtime-test/source-field-validation-test` and
  `transfer-test/concurrent-defined`.
- `cell-registry` namespace docstring and plan "New-Class checks" state
  the namespace role: child-safe cell support (new-Class and absence
  checks, attempt axis, axis expected values, identity fact, factory
  symbols, failure maps, definition map, child call forms). No move.
- Verify (mise paths, limit 4; raw `target/test-record/after28/`): all exit
  0, 0 failures (2012, 2012, 2012, 4313). `fallback-java8` 118.69 s,
  `fallback-preview-off` 38.65 s, `fallback-preview-on` 39.53 s,
  `cross-jvm` 365.80 s (timing not a record run). `check_record.clj` (with
  `after21-limit1`): max cell count 1, 0 mapping findings, limit-1 =
  limit-4 labels of each namespace; summaries differ from
  `after-record/*.edn` in times only; record kept. fmt, kondo clean.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 2 review steps; 1 blocked.

## Implementation review (pass 17)

- No new steps. Open: pass 13 acceptance 3 reading step (needs a user
  decision).

## Implementation test review (pass 24)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Implementation test review (pass 24) fixes

- `identity-receiving-checks/nested`: each expansion runs in
  `with-bindings` of `*ns*` and a new `Compiler/LOADER`
  (`RT/makeClassLoader`), as eval of a top-level form gives. The capture
  loader of an expansion stays in that frame. Docstring states the rule.
- Verify: `identity-receiving-cross-jvm-test` (mise paths) 120 passed,
  0 failed; fmt, kondo clean. Labels unchanged.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision:
  accept the per-namespace reading).
- Addressed 1 review step; 1 blocked.

## Implementation test review (pass 25)

- No new issues found.

## Implementation test review (pass 26)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  (needs a user decision).

## Implementation test review (pass 26) fixes

- `identity-receiving-checks/nested-warning-scope` gives `:cell-loader?`
  (cell `Compiler/LOADER` identical after `(expansion)`);
  `nested-public-warning-scope-test` expects `true`. Verify (mise Java 8
  and EA3 paths): 120 passed, 0 failed; fmt, kondo clean. Labels unchanged.
- Blocked (pass 26 step): with the `Compiler/LOADER` frame removed from
  `nested`, the test still passes (120 passed). Cause: the fallback
  `defvaluetype` expansion is `(let [] (identity-field-deftype ...))`;
  `macroexpand` stops at `let*`, `macroexpand-1` at `let`. Thus no
  expansion installs a capture loader; `identity-field-deftype` installs
  it during analysis of the `eval` of the form, in the `eval` frame.
  Probe (bound LOADER, both expanders): LOADER unchanged. The pass 24
  finding premise does not hold for this cell (incompatible receivers
  only). Decision needed: keep `:cell-loader?` as a guard, or remove it
  and the pass 24 frame.
- Still blocked: pass 13 acceptance 3 reading step (needs a user decision).
- Addressed 0 review steps; 2 blocked.

## Implementation test review (pass 27)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 27) fixes

- `identity-loader-checks/cases` (expands by modes, in order) in
  `public-stale-sibling` and `loader-policy-test`;
  `public-stale-sibling-class-test` checks the key set, not 6.
- `array-info-boundary-test`: private `invalid-inputs`, `valid-input`;
  description with `registry/each`, predicate with `(count
  invalid-inputs)` and `split-at`. Control forms equal data (modulo
  gensym), checked for each require order.
- `cell-harness/detail?` (non-blank string) in `runtime-test`,
  `field-capability-test`, `identity-receiving-cross-jvm-test`.
- Verify (mise Java 8): cross-jvm (loader-policy, array-info-boundary,
  field-capability, identity-receiving-cross-jvm) 924 passed; fallback
  (identity-loader, runtime) 180 passed; 0 failed. fmt, kondo clean.
  Labels unchanged.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 28)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 28) fixes

- `identity-receiving-test`: `checks/checked-outcomes` with one-argument
  builds in place of the positional input pairs; quoted `specs` binding
  builds `form` and `public-form` with `list*` (no `(drop 5 form)`).
- `function-aot-checks/invoke-install-form` takes a spec (`:checked`,
  `:valid`, `:invalid`); callers pass `consumer-spec` or
  `checks/string-holder`; private `rejections` in
  `public-mixed-field-aot-test`. Child forms equal data.
- Verify (mise paths): `identity-receiving-test` Java 8 and EA3 553
  passed each; cross-jvm (public-field-aot, public-native-field-aot,
  public-mixed-field-aot) 677 passed; 0 failed. fmt clean; kondo 0
  warnings. Labels unchanged.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 29)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 29) fixes

- `function-aot-checks`: `invoke-install` and `invoke-install-form` take
  a spec; `inspect-holder` constructs through `:arguments`. Each function
  test has a private `function-spec` (`:arguments` of its function
  Holder). Child forms change (the whole spec).
- `consumer-aot-checks/invalid-arguments` (valid, one wrong value for each
  checked field) in `string-holder` and the native and mixed
  `consumer-spec`; mixed `rejections` from `(:checked consumer-spec)`.
  Invalid vectors equal data.
- `identity-receiving-test/receiving-identity-fields-test`:
  `non-nil-positions` and `optional-position` from `fields`.
- Verify (mise paths): `identity-receiving-test` Java 8 and EA3 553
  passed each; cross-jvm (public-field-aot, public-native-field-aot,
  public-mixed-field-aot) 677 passed; 0 failed. fmt clean; kondo 0
  warnings. Labels unchanged.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 30)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 30) fixes

- `consumer-aot-checks/spec-rejections` (checked-rejections for each
  `:checked` field) in `string-holder-result`, native `consumer-result`
  and install result, `public-field-aot-test` install result; private
  mixed `rejections` removed.
- `consumer-aot-checks/string-holder-fields`; private `fields` in the
  native and mixed tests; `function-aot-checks/holder-invocation` (spec,
  fields, value?) gives the install `:fields` with `:stored` from
  `(zipmap :checked :valid)`. Private `string-holder` →
  `function-holder` in `public-field-aot-test`.
- `identity-receiving-test/receiving-identity-fields-test`: `ctor` is
  `(first (.getConstructors owner))`.
- Verify (mise paths): `identity-receiving-test` Java 8 and EA3 553
  passed each; cross-jvm (public-field-aot, public-native-field-aot,
  public-mixed-field-aot) 677 passed; 0 failed. fmt clean; kondo 0
  warnings. Labels unchanged.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 31)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 31) fixes

- `consumer-aot-checks/holder-result` takes `spec fields value? call`
  (the `holder-invocation` order); `:read`, checked `:stored` and
  `:rejections` from the spec; `unchecked-stored` bound once.
- `function-aot-checks/invoke-install`, `inspect-holder` read `:source`,
  `:ns`, `:owner` of the spec (factory by `registry/factory-symbol`).
- `aot-fixture/descriptor`; persisted annotation vectors in
  `public-mixed-field-aot-test` and `field-aot-test` from
  `checks/native-annotations`.
- Verify (mise paths): cross-jvm (public-field-aot, public-native-field-aot,
  public-mixed-field-aot, generated-value-aot, identity-aot, field-aot)
  29 tests, 884 passed, 0 failed. fmt clean; kondo 0 warnings.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 32)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 32) fixes

- In-process expand contexts (`c6e1033`): inner `(testing (pr-str
  expand))` in `identity-receiving-test/receiving-identity-fields-test`,
  `public-field-annotations-test` and the five `fields-test` Vars;
  `receiving-redefined-child-test` adds `(pr-str expand mode)` (for
  example `":internal :eval"`), then `"first-owner"`/`"second-owner"`.
  `fields-test` uses `transfer/expands` with `transfer/expanders` (order
  now `:macroexpand`, `:macroexpand-1`).
  - Label change: each old label gains the expand context. In
    `receiving-identity-fields-test` (above `"direct"`/`"factory"`/
    `"reflective"`) and `public-field-annotations-test` (above "owner and
    ordinary ...") it is inserted, not appended. Mapping:
    `check_record.clj` `inserted-contexts` (match ignores the expand
    contexts for these 2 Vars).
- `fields-test/public-imported-concrete-field-test`: `checks/builders`,
  stored value and `checked-outcomes` for each construction (direct
  added; assertion count unchanged, 8 for each tag and expand).
- Verify: focused `fields-test`, `identity-receiving-test`,
  `public-field-annotations-test` Java 8 and EA3 1208 passed each; kondo
  0 warnings; fmt clean.
- After record at `c6e1033` (mise paths; `target/test-record/run-p32.sh`;
  raw `target/test-record/after29/`, `after29-limit1/`). All six runs exit
  0, 0 failures. Assertions 1886 (each fallback command; 2012 before:
  pass 28 one `is` for each `checked-outcomes`), 4313 (`cross-jvm`); tests
  58, 86.
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 110.36 s,
    `fallback-preview-off` 34.69 s, `fallback-preview-on` 36.03 s,
    `cross-jvm` 341.64 s; sum 522.72 s = 12.9% of 4060.34 s. Acceptance 2
    time holds.
  - Limit 1: `fallback-java8` 225.46 s, `cross-jvm` 750.54 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings (904 first labels each fallback command); limit-1 = limit-4
    label vectors of each namespace true (10 and 25 namespaces).
    Acceptance 3 holds (per-namespace reading). Labels changed, so
    `after-record/*.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation test review (pass 33)

- Added 2 steps to be addressed.

## Implementation test review pass 33 fixes

- `isolated-sibling-loader-test`: inner `(testing (str loader-name " "
  (pr-str expand)))` (for example `"left :macroexpand"`) below
  `"isolated sibling through <mode>"`. Label change: each old label gains
  one appended context (`check_record.clj` mapping rule holds).
  `verify-owner` uses `checks/input-results` (receiver, owner); expected
  `(checks/each-construction {:stored-input? true :rejections
  checks/checked-rejections})`. `checks/builders` resolves the owner:
  `verify-owner` runs in the `Compiler/LOADER` and `*ns*` bindings of the
  case.
- `fields-test/public-imported-concrete-field-test`: `checks/input-results`
  in one `is`.
- Verify: focused `fields-test`, `isolated-sibling-loader-test` Java 8,
  EA3 preview off and on: 516 passed each; kondo 0 warnings; fmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps; 2 blocked.

## Implementation test review (pass 34)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review pass 34 fixes

- `identity-backend-test`: private `fixture-ns`, `named-builders` (pairs
  of `checks/constructions` and `checks/builders`). Rejections:
  `checked-outcomes` of `(fn [[x]] (build (assoc args i x)))` for each
  construction (and each checked field of `Pair`; position from the field
  names). Stored-value checks for each construction. Nil rejection in
  `unrelated-attributes-survive`: `checks/outcome`, expected `(first
  checks/checked-rejections)`. `InvocationTargetException` import removed.
  - Label change: below the old contexts, an appended `(pr-str
    construction)` (`Pair` rejections: `(pr-str construction field-name)`,
    for example `":direct \"left\""`). Each old label is a prefix of its new
    labels (`check_record.clj` mapping rule holds).
- `fields-test/disabled-declaration-behavior-test`: `checks/builders` (all
  three constructions; direct added). Label change: the Var label (no
  context) gains `(pr-str restriction)`, then `(pr-str text
  construction)` (for example `"nil :direct"`); appended.
- Verify: focused `fields-test`, `identity-backend-test` Java 8, EA3
  preview off and on: 576 passed each; kondo 0 warnings; fmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps; 2 blocked.

## Implementation test review (pass 35)

- Added 4 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review pass 35 fixes

- `consumer-aot-checks/named-builders` (ns-or-name, owner): pairs of
  `constructions` and `builders`. Used in `identity-backend-test` (private
  copy removed), `fields-test/disabled-declaration-behavior-test` and
  `identity-receiving-test/receiving-identity-fields-test`.
  - Label change (`identity-receiving-test`): the construction context
    `(name entry)` (`"direct"`) becomes `(pr-str construction)`
    (`":direct"`); not a prefix extension. Correction (implementation
    review pass 18): the first record has the old labels, so
    `check_record.clj` needs a context rename (`construction-keyword`). The rejections gain an appended `(pr-str (name field))`
    (for example `":direct \"required\""`).
- `identity-backend-test`: one `shape-failure` (ex-data with `:message`);
  local `failure` removed. `transforms` iterated as entries.
  - Label change (appended): `(pr-str transform-key instruction)` (for
    example `":owner GETFIELD"`), `(pr-str transform-key)` for the write
    loop, storage cases `"not final"`, `"static"`, `"String typed"`,
    constructor cases `"no constructor"`, `"no write"`, `"two writes"`,
    `"branch"`, `"extra constructor"`.
- `public-field-annotations-test`: appended `(testing field-name)` in both
  field loops (for example `"count_value"`, `"required0"`).
- `identity-loader-checks/class-bytes` (binary name) and `instance`
  (Class); `named-class` and `stale-sibling-case` use them;
  `isolated-sibling-loader-test/child-bytes` removed.
- Verify: focused `fields-test`, `identity-backend-test`,
  `identity-receiving-test`, `public-field-annotations-test`,
  `isolated-sibling-loader-test`, `identity-loader-test` Java 8, EA3
  preview off and on: 1326 passed each; `:cross-jvm-test`
  `loader-policy-test` 378 passed; kondo 0 warnings; fmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 4 review steps; 2 blocked.

## Implementation review (pass 18)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation review pass 18 fixes

- Field-name context: the raw field name in its own `testing`, below the
  construction context. Label change: `identity-backend-test/multiple-identity-constraints`
  `":direct \"left\""` → `":direct"` `"left"`;
  `identity-receiving-test/receiving-identity-fields-test`
  `"\"required\""` → `"required"`; `public-field-annotations-test`
  unchanged (raw name). The changed labels are not in the first record, so
  no `check_record.clj` mapping is needed for them.
- `check_record.clj`: `construction-keyword` (`"direct"` → `":direct"`,
  also `factory`, `reflective`) in `context-renames` of
  `receiving-identity-fields-test`, after `analysis-mode`; header updated.
  Without it: 18 mapping findings for each fallback command; with it: 0.
- After record at `0d2ce00` (`target/test-record/run-p33.sh`; raw
  `target/test-record/after30/`, `after30-limit1/`). All six runs exit 0,
  0 failures. Assertions 1716 (each fallback command; 1886 before: passes
  33–35 check constructions in one `is`), 4313 (`cross-jvm`); tests 58,
  86.
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 116.68 s,
    `fallback-preview-off` 37.38 s, `fallback-preview-on` 37.36 s,
    `cross-jvm` 358.34 s; sum 549.76 s = 13.5% of 4060.34 s. Acceptance 2
    time holds.
  - Limit 1: `fallback-java8` 233.77 s, `cross-jvm` 761.28 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings (904 first labels each fallback command); limit-1 = limit-4
    label vectors of each namespace true (10 and 25 namespaces).
    Acceptance 3 holds (per-namespace reading). `after-record/*.edn`
    replaced.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps; 2 blocked.

## Implementation review (pass 19)

- No new steps. Open: pass 13 acceptance 3 reading step and pass 26 step
  (need user decisions).

## Implementation test review (pass 36)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Test review pass 36 follow-up

- `cell-runner-test/child-limit-test`: `(inc runner/limit)` baseline
  children of `:kind :producer` (a `:cell` kind would add cell starts to
  the record), each sleeps `sleep-ms` 6000 and prints `[start end]`;
  `:timeout-ms` = `sleep-ms` + `start-ms` 6000. JVM start alone is about
  2700 ms (Java 8, cross-jvm classpath); 4000 + 3000 timed out at limit 4
  and 1, so the bounds are 6000 + 6000. Assert: each exit 0; maximum
  overlap of the intervals ≤ `runner/limit`. New label
  `[value-type.cell-runner-test/child-limit-test ["child limit"]]`.
- Verify (mise Java 8): limit 4 and limit 1, 2 passed each (25.0 s,
  23.6 s real). Mutations of `jvm-runner/execute`, limit 1: permit
  released after process start → overlap check fails; wait clock started
  before `acquire` → last child `:timeout`, exit check fails. Restored.
- Addressed 1 review step. Still blocked: pass 13 acceptance 3 reading
  step and pass 26 step (need user decisions).

## Implementation test review (pass 37)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Test review pass 37 follow-up

- `child-limit-test` kind: `:cell` (default kind). Each child run is the
  checked case and returns one result, as a child that compiles as its
  checked case is a cell child (design Concepts). It makes no transfer
  expansions, so `:producer` was not true. A `:cell` child with an
  unreadable line gives `:read-error`, not a throw, so the test also
  checks that each `:result` is a vector. Reason in the test comment.
- Isolation exception (added to the table of review pass 1 and to
  `check_record.clj` `rule-5-exceptions`):

| test | children / configs | rule | reason |
|---|---|---|---|
| `cell-runner-test/child-limit-test` | `n + 1` / 1, `n = (max limit 4)` (5 / 1 at limit 1 to 4) | 5 | the evidence is concurrency of children in one parent; it needs more than `limit` processes at one time (implementation review pass 22: `n` independent of the limit at limit 4 or less) |

- `cross-jvm` after record at `9cb4f7b` (`target/test-record/run-p37.sh`;
  raw `target/test-record/after31/`, `after31-limit1/`; fallback records
  copied from `after30`, no fallback change since `0d2ce00`). Both runs
  exit 0, 0 failures; assertions 4316 (4313 + 3), tests 87.
  - `/usr/bin/time -p` real: limit 4 385.09 s, limit 1 795.32 s. Sum of
    the four limit-4 commands 576.51 s = 14.2% of 4060.34 s. Acceptance 2
    time holds.
  - `check_record.clj`: max cell count 1; no bound finding
    (`[child-limit-test :cell]` count 5); 0 mapping findings; limit-1 =
    limit-4 label vectors of each namespace true (10 and 25 namespaces).
    Acceptance 3 holds (per-namespace reading).
    `after-record/cross-jvm.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 38)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 38) — fixes

- `cell-runner-test/child-limit-test`: `harness/assert-child-stderr` for
  each child and its result, before the `"child limit"` context. Equal
  children give one label (`child-context` of `"child stderr"`). New
  label only; `after-record/` not retaken.
- Verify: `child-limit-test` (Java 8, limit 4) 8 assertions pass (5 + 3);
  kondo, cljfmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 39)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 39) — fixes

- `cell-runner-test/child-limit-test`: two `run-jvms` calls in `future`s
  (1 child, `runner/limit` children); overlap of all results asserted
  `(= runner/limit ...)`. No label change (same contexts; same child
  count, 5 at limit 4).
- Mutations (Java 8, limit 4), each 1 assertion failed, then restored:
  `permits` `(Semaphore. 1)`; `(mapv execute option-maps)`; a
  `Semaphore` for each `run-jvms` call.
- Verify: limit 4 8 passed; limit 1 5 passed; kondo, cljfmt clean.
  `after-record/` not retaken (no label change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 40)

- No new issues found.

## Implementation test review (pass 41)

- Added 1 step to be addressed.

## Implementation test review (pass 41) — fixes

- `cell-runner-test/unreadable-producer-result-test`: producer at index 0,
  then a `:cell` child that sleeps `late-cell-ms` (3000). Asserts elapsed
  ≥ 3000 ms, `ex-data` `{:index 0 :exit 0 :err ""}`, string
  `:read-error`, `:out` contains `#object[java.lang.Object`. Same
  context; no label change; same child count.
- Mutation (Java 8, limit 4): throw in the deref loop of `run-jvms` →
  1 assertion failed (`(<= 3000 2144)`); restored.
- Verify: `cell-runner-test` (Java 8, limit 4) 77 passed; kondo, cljfmt
  clean. `after-record/` not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 42)

- Added 2 steps to be addressed.

## Implementation test review (pass 42) — fixes

- `cell-runner-test/unreadable-producer-result-test`: the cell child
  sleeps `late-cell-ms`, then writes a temp file; the `catch` reads
  `(.exists file)` at the throw and asserts it true; the `finally` deletes
  the file. The `try` gives `::no-throw` when `run-jvms` does not throw.
  The elapsed-time assertion is removed. Same context; no label change.
- Mutation (Java 8, limit 4): throw in the deref loop of `run-jvms` →
  1 assertion failed (`(true? written?)`); restored.
- `str/includes?` in `cell-runner-test/assert-no-result` and
  `jvm-runner-test/read-limit-test` (no regex of literal text).
- Verify: `cell-runner-test` and `jvm-runner-test` (Java 8, limit 4) 101
  passed; kondo, cljfmt clean. `after-record/` not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 43)

- Added 2 steps to be addressed.

## Implementation test review (pass 43) — fixes

- `jvm-runner/read-result` public (docstring);
  `jvm-runner-test/read-result-test`: empty stdout, last line, unreadable
  line, exit 3, `:timeout`. New label.
- `cell-runner-test/assert-no-result` takes child and run; each failure
  equals `(select-keys run [:exit :out :err :read-error])`. Crashing,
  unreadable and timed-out tests check the run facts once (exit, marker
  in `:out`, `:err` `""`, string `:read-error`). No label change.
- Verify: `cell-runner-test` and `jvm-runner-test` (Java 8, limit 4) 100
  passed; kondo, cljfmt clean. `after-record/` not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 44)

- Added 1 step to be addressed.

## Implementation review (pass 20)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading
  step, pass 26 step (need user decisions) and pass 44 step.

## Implementation test review (pass 44), implementation review (pass 20) — fixes

- `jvm-runner/read-failure` public (pure: option maps, results → ex-info
  data or nil); `run-jvms` uses it. `jvm-runner-test/read-failure-test`:
  `:producer`, `:aot-compile`, `:java-compile` throw data; `:cell` and no
  `:kind` nil; first index of two. New labels.
- `run-jvms` derefs every future first (catches `ExecutionException`),
  then throws ex-info `{:index i}` of the first failed child with the
  cause, then applies `read-failure`. Docstring states the rule.
  `cell-runner-test/failed-start-child-test`: missing `:java` at index 0,
  cell child at index 1 sleeps `late-cell-ms` then writes a file; file
  exists at the throw. Mutation (`mapv deref` without catch): the test
  errors. New labels; one extra baseline `:cell` start (no record retaken).
- `doc/development/runtime.md` lists the unreadable producer, the failed
  start and the child limit.
- Verify: `jvm-runner-test` and `cell-runner-test` (Java 8, limit 4) 112
  passed; kondo, cljfmt clean. `after-record/` not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps.

## Implementation review (pass 21)

- Added 2 steps to be addressed.

## Implementation review (pass 21) — fixes

- `cell-runner-test/failed-start-child-test`: `missing` is
  `outcome-child` of `(assoc baseline :java ...)` (has a `:form`); assert
  `(instance? java.io.IOException (ex-cause e))`. Labels unchanged.
  Focused `cell-runner-test` (Java 8, limit 4): 75 passed.
- After record at `1be68f9` (`target/test-record/run-p45.sh`; raw
  `after32/`, `after32-limit1/`). All six runs exit 0, 0 failures.
  Assertions 1716 (each fallback command), 4336 (`cross-jvm`, limit 4),
  4333 (`cross-jvm`, limit 1); tests 58, 90.
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 112.69 s,
    `fallback-preview-off` 35.01 s, `fallback-preview-on` 36.40 s,
    `cross-jvm` 377.94 s; sum 562.04 s = 13.8% of 4060.34 s. Acceptance 2
    time holds.
  - Limit 1: `fallback-java8` 233.60 s, `cross-jvm` 768.62 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings (904 first labels each fallback command). `after-record/*.edn`
    replaced.
  - Acceptance 3 does not hold for `cross-jvm`: `fallback-java8` limit-1 =
    limit-4 label vectors true (10 namespaces); `cross-jvm` false, only
    `value-type.cell-runner-test` (75 at limit 4, 72 at limit 1). Cause:
    pass 38 added `harness/assert-child-stderr` for each of the
    `(inc runner/limit)` children of `child-limit-test`, so its
    `"child stderr"` assertion count depends on the limit. Design Scope 2
    (one stderr assertion for each cell child) and acceptance 3 (same labels
    at each limit) conflict for this test. Needs a decision (for example
    one assertion of all child stderr values, or an acceptance 3 exception
    for `child-limit-test`).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation review (pass 22)

- Added 1 step to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Implementation review (pass 22) — fixes

- `cell-runner-test/child-limit-test`: private `limit-count` = `(max
  runner/limit (runner/read-limit nil))`; two `run-jvms` calls of 1 and
  `limit-count` children; exit 0 for each of the `(inc limit-count)`
  children; `(= runner/limit (max-overlap ...))`. Child count 5 at limit 1
  to 4; no label change. Test comment and exception row updated (`n + 1` /
  1, `n = (max limit 4)`); `rule-5-exceptions` unchanged (5).
- Focused (Java 8): limit 4 and limit 1, 8 assertions pass each.
  Mutations at limit 4, each 1 assertion failed, then restored: `permits`
  `(Semaphore. 1)` (overlap 1); `run-jvms` without `future` (overlap 2);
  a `Semaphore` for each call (overlap 5). kondo, cljfmt clean.
- `cross-jvm` after record at `ea25e65` (`target/test-record/run-p46.sh`;
  raw `after33/`, `after33-limit1/`; fallback records copied from
  `after32`, no fallback change since `1be68f9`). Both runs exit 0, 0
  failures; 4336 assertions at limit 4 and limit 1; 90 tests.
  - `/usr/bin/time -p` real: limit 4 362.28 s, limit 1 790.57 s. Sum with
    the `after32` fallback times 546.38 s = 13.5% of 4060.34 s.
    Acceptance 2 time holds.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings. Limit-1 = limit-4 label vectors of each namespace: true for
    `fallback-java8` (10) and `cross-jvm` (25). Acceptance 3 holds.
    `after-record/cross-jvm.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation review (pass 23)

- No new steps. Open: pass 13 and pass 26 steps (need user decisions).

## Implementation test review (pass 45)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 45) fix

- `timed-out-child-outcome-test`: cell 0 prints `marker`, sleeps
  `past-timeout-ms` (4000 ms from cell start), prints `late-marker`, then
  sleeps 60 s. New `is` of `late-marker` in `:out` under the same label
  "timed-out child" (one more assertion; same child count; no label
  change). The `cross-jvm` after record (`ea25e65`) is not retaken.
- Focused (Java 8, `target/test-record/focus-p47.sh`): limit 4 and limit
  1, 11 assertions pass each. Mutation `wait (:timeout-ms options
  120000)` in `execute`: 2 assertions fail (both markers absent; the
  child is stopped at 2000 ms), then restored. kondo, cljfmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 46)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 46) fix

- `cell-runner-test/limit-variable-load-test`: one baseline `:cell` child
  through `run-jvms` (`harness/run-cells`), two cells. Each cell starts a
  grandchild JVM (cell `java.home`/`bin/java`, cell `java.class.path`,
  `VALUE_TYPE_TEST_JVM_LIMIT` set, `VALUE_TYPE_TEST_RECORD` removed) that
  requires `value-type.jvm-runner` and prints `limit`. `"2"` →
  `{:exit 0 :out "2" :err ""}`; `"0"` → nonzero exit, `:out ""`, `:err`
  contains `VALUE_TYPE_TEST_JVM_LIMIT` and `"\"0\""`. Grandchildren are
  not runner children (not recorded, no permit). New labels: child stderr,
  child, `[:limit-load "2"]`, `[:limit-load "0"]` (9 assertions).
- Focused (Java 8, `target/test-record/focus-p48.sh`): limit 4 and limit
  1, 9 assertions pass each. Mutation `limit` = `(read-limit nil)`: 2
  assertions fail (both grandchildren print 4), then restored. kondo,
  cljfmt clean. `doc/development/runtime.md` list names the case. The
  `cross-jvm` after record (`ea25e65`) is not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 47)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 47) fix

- `cell-runner-test/limit-load-form` keeps the `.waitFor` result
  (`finished?`) and gives `:exit :timeout` when it is false; docstring
  states it. Focused (Java 8, limit 4): 9 assertions pass. kondo, cljfmt
  clean. No label change.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 48)

- No new issues found.

## Implementation test review (pass 49)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 49) fix

- `cross-jvm` after record at `5cb0462` (`target/test-record/run-p49.sh`;
  raw `after34/`, `after34-limit1/`; fallback records copied from
  `after32`, no fallback change since `1be68f9`). Both runs exit 0, 0
  failures; 4346 assertions at limit 4 and limit 1; 91 tests.
  - `/usr/bin/time -p` real: limit 4 374.03 s, limit 1 791.26 s. Sum with
    the `after32` fallback times 558.13 s = 13.7% of 4060.34 s.
    Acceptance 2 time holds.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings. Limit-1 = limit-4 label vectors of each namespace: true for
    `fallback-java8` (10) and `cross-jvm` (25). Acceptance 3 holds.
    `after-record/cross-jvm.edn` replaced.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 50)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 50) fix

- `cell-runner-test`: private `late-cell-throw` (baseline, function of
  the temp file that gives the index-0 child) gives `{:data :written?
  :cause}` or `::no-throw`; `unreadable-producer-result-test` and
  `failed-start-child-test` use it. `limit-variable-load-test` binds
  `valid` beside `invalid`. Labels and expectations unchanged.
- Focused (Java 8, `target/test-record/focus-p50.sh`): the three tests,
  18 assertions pass at limit 4 and limit 1. kondo, cljfmt clean.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 51)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 51) fix

- `jvm-runner`: public `thrown-failure` (outcomes → `[index thrown]` of
  the first `:thrown`, or nil); `run-jvms` uses it.
  `jvm-runner-test/thrown-failure-test` (new labels, 2 assertions).
- Focused (Java 8): `jvm-runner-test` 40 assertions pass at limit 4 and
  limit 1; `failed-start-child-test` passes. Mutation `last` for `first`
  fails `thrown-failure-test`; restored. kondo, cljfmt clean. After
  record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 52)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 52) fix

- `cell-runner-test/timed-out-child-outcome-test`: cell 0 writes a
  deleted temp file after its 60000 ms sleep; `(false? written?)` read at
  the return of `run-jvms` (file deleted after). Same label, one more
  assertion.
- Focused (Java 8, `target/test-record/focus-p52.sh`): 12 assertions pass
  at limit 4 and limit 1. Mutation (no `.destroyForcibly` in the
  `when-not finished?` branch of `execute`) fails `(false? written?)`;
  restored. kondo, cljfmt clean. After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 53)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 53) fix

- `cross-jvm` after record at `4482293` (`target/test-record/run-p53.sh`;
  raw `after35/`, `after35-limit1/`; fallback records copied from
  `after34`, no fallback change since `1be68f9`). Both runs exit 0, 0
  failures; 4349 assertions at limit 4 and limit 1; 92 tests.
  - `/usr/bin/time -p` real: limit 4 371.75 s, limit 1 800.46 s. Sum with
    the `after32` fallback times 555.85 s = 13.7% of 4060.34 s.
    Acceptance 2 time holds.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings. Limit-1 = limit-4 label vectors of each namespace: true for
    `fallback-java8` (10) and `cross-jvm` (25). Acceptance 3 holds.
    `after-record/cross-jvm.edn` replaced (has `thrown-failure-test`).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 54)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 54) fix

- Public `jvm-runner/with-output-files` (temp dir, `stdout`, `stderr`; f
  of the two files; the three removed in `finally`); `execute` uses it.
  `jvm-runner-test/with-output-files-test`: after return and after throw
  (throw propagates), the three files do not exist. New labels only.
- `cell-outcomes-test/assert-child-report-test`: `str/starts-with?`,
  `str/includes?`; no `re-find` left in `test/cross-jvm`.
- Focused (Java 8, `target/test-record/focus-p54.sh`): 104 assertions
  pass at limit 4 and limit 1. Mutation (no removal) fails both checks;
  restored. kondo, cljfmt clean. After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 55)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 55) fix

- `cell-runner-test/limit-load-form`: the cell form requires
  `value-type.jvm-runner` (the cell child does not load it) and calls
  `with-output-files`; the local `vt-limit-` directory and `.delete`
  removal removed; docstring updated. Labels and expectations unchanged.
- Focused (Java 8, `target/test-record/focus-p55.sh`):
  `limit-variable-load-test` 9 assertions pass at limit 4 and limit 1.
  kondo, cljfmt clean. After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation test review (pass 56)

- Added 2 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 56) fix

- New `value-type.temp-dir/with-temp-dir` (test/compatibility; does not
  load `jvm-runner`): create a temp directory, call f of it, remove the
  tree with `Files/deleteIfExists` in `finally`. Used in
  `jvm-runner/with-output-files`, `aot-fixture/with-fixture`,
  `isolated-sibling-loader-test`, `cell-runner-test/late-cell-throw` and
  `timed-out-child-outcome-test` (the written file is a path in the
  directory). Labels and expectations unchanged.
- `run-jvms`: the ex-data of each throw has `:results` (input order; nil
  for a child that throws). plan "Runner" and docstring state it.
  `late-cell-throw` gives `:cell` and `:late`; `unreadable-producer-result-test`
  and `failed-start-child-test` assert `assert-child-stderr` and exit 0 of
  the late child. Label change: new `"child stderr"` label of the late
  child (fixture context) only; `failed-start-child-test` data check is
  `{:index 0 :results [nil late]}`.
- Focused (Java 8, `target/test-record/focus-p56.sh`: `jvm-runner-test`,
  `isolated-sibling-loader-test`, `aot-test`, four `cell-runner-test`
  tests): 207 assertions pass at limit 4 and limit 1; no temp directory
  left. kondo, cljfmt clean. After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Implementation test review (pass 57)

- Added 3 steps to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 57) fix

- `cell-runner-test/late-cell-throw` gives `{:late-child :late-run
  :thrown}`: the late child, its result (return value of `run-jvms`, or
  `:results` of the ex-data), and `::no-throw` or `{:data :written?
  :cause}`. Both tests check the throw before `assert-child-stderr`, so a
  missing throw fails first and the stderr label does not change. Labels
  and expectations unchanged. Focused (`target/test-record/focus-p57.sh`):
  13 assertions pass.
- After record at `5264320` (`target/test-record/run-p57.sh`; raw
  `after36/`, `after36-limit1/`). All six runs exit 0, 0 failures.
  Assertions 1716 (each fallback command), 4357 (`cross-jvm`, limit 4 and
  limit 1); tests 58, 93.
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 109.98 s,
    `fallback-preview-off` 35.52 s, `fallback-preview-on` 35.23 s,
    `cross-jvm` 382.23 s; sum 562.96 s = 13.9% of 4060.34 s. Acceptance 2
    time holds.
  - Limit 1: `fallback-java8` 228.31 s, `cross-jvm` 808.28 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings (904 first labels each fallback command). Limit-1 = limit-4
    label vectors of each namespace: true for `fallback-java8` (10) and
    `cross-jvm` (25). Acceptance 3 holds.
  - `after-record/cross-jvm.edn` replaced (has `with-output-files-test`
    and the late child `"child stderr"` labels). Fallback summaries differ
    in times only; kept.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 3 review steps.

## Implementation test review (pass 58)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).
- Pass 58 step: `label-record-test/summary` writes `record.edn` in
  `(temp/with-temp-dir "vt-label-record-" ...)`; `File` import removed.
  Prefixes now `vt-` + kebab-case name + `-`: `vt-jvm-`, `vt-aot-`,
  `vt-siblings-` (was `value-type-siblings`), `vt-late-cell-` (was
  `late-cell`), `vt-timed-out-cell-` (was `timed-out-cell`),
  `vt-label-record-`. Labels and expectations unchanged. Focused
  (`target/test-record/focus-p58.sh`: `label-record-test`,
  `isolated-sibling-loader-test`, three `cell-runner-test` Vars): 93
  assertions pass. After record not retaken (prefix and temp-file changes
  only; no label, child or assertion change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation review (pass 24)

- Added 1 step to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).
- Pass 24 step: plan "Runner" states the child-throw rule of `run-jvms`
  (first such child by `thrown-failure`; ex-info `:index`, `:results` nil
  for each child that throws; the exception as cause; checked before the
  read rule). "nil for a child that throws" moved from the read-failure
  rule. Code, labels and expectations unchanged.
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 1 review step.

## Implementation review (pass 25)

- No new steps. Open: pass 13 and pass 26 steps (need user decisions).

## Implementation test review (pass 59)

- No new issues found.

## Implementation test review (pass 60)

- No new issues found.

## Implementation docs review (pass 6)

- No new issues found.

## Code-shaper review (pass 32)

- Added 4 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 32) fixes

- `jvm-runner/await-exit` (exit value, or `:timeout` after stop) and
  private `stop`; `execute` and `limit-load-form` use them.
- `thrown-failure` gives `{:index :thrown}`; `read-failure` and
  `thrown-failure` share private `first-indexed`; both throws build the
  ex-data with `(assoc ... :results results)`.
- `jvm-runner/limit-variable`; `read-limit` message bound from
  `Integer/MAX_VALUE` (same text).
- `jvm-runner/run-facts`; used in `read-failure`, `cell-harness`
  (`assert-child`, `cell-outcomes`) and `cell-runner-test` (3 places).
- Verify: focused `jvm-runner-test` (44), `cell-runner-test` (90),
  `cell-outcomes-test` (44) pass on Java 8; cljfmt and clj-kondo clean.
  After record not retaken (no label, child or assertion change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 4 review steps.

## Code-shaper review (pass 33)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 33) fixes

- `(pr-str (runner/run-facts r))` is the failure message in
  `child-limit-test`, `cell-harness/produce`, `aot-fixture/compile-producer`,
  `aot-fixture/javac` and `aot-test/produced?`.
- Public `label-record/record-variable`; `record-path` and
  `cell-runner-test/limit-load-form` use it (docstring names it).
- Verify: focused `cell-runner-test`, `jvm-runner-test`,
  `cell-outcomes-test`, `label-record-test` (185),
  `public-native-field-aot-test`, `field-transfer-test`,
  `generated-value-aot-test` (1316) on Java 8; fallback `aot-test` (68);
  cljfmt and clj-kondo clean. After record not retaken (no label, child or
  assertion change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 34)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 34) fixes

- Public `cell-harness/assert-run` (run, map of expected run values; one
  `is` for each key, message `run-facts`; true when all pass). Used in
  `cell-harness/produce`, `cell-harness/assert-child`,
  `aot-fixture/compile-producer`, `aot-fixture/javac`,
  `aot-test/produced?`, `cell-runner-test/scope-3-policy-rules-test` and
  `cell-isolation-test`. The `is` form is `(= v (get run k))`; labels and
  counts unchanged.
- Public `jvm-runner/run-command` (command, env change map with nil to
  remove, wait ms): `with-output-files`, start, `await-exit`, stop in a
  `finally`; gives `{:exit :out :err}`. `execute` and
  `cell-runner-test/limit-load-form` use it; `stop` stays private.
  The grandchild now also stops a live process when the wait throws.
- Verify (Java 8 parent): focused `cell-runner-test`, `jvm-runner-test`,
  `cell-outcomes-test`, `label-record-test` (185);
  `public-native-field-aot-test`, `field-transfer-test`,
  `generated-value-aot-test` (1316); fallback `aot-test` (68). cljfmt and
  clj-kondo clean. After record not retaken (no label, child or
  assertion change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 35)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 35) fixes

- `harness/assert-run` replaces the `is` forms on run keys in
  `aot-test/source-free-value-aot-loading-test`, `field-aot-test`, and
  `cell-runner-test` (`crashing-child-outcome-test`,
  `unreadable-result-outcome-test`, `timed-out-child-outcome-test`,
  `unreadable-producer-result-test`, `failed-start-child-test`,
  `child-limit-test`). `aot-test` no longer refers `is`.
- Count change: `child-limit-test` had one `is` of all exits; now it has
  one `is` for each child (`(inc limit-count)`, 5 at limit 1 and 4). Label
  `["child limit"]` unchanged; the count is the same at limit 1 and 4, so
  acceptance 3 holds.
- Private `aot-fixture/compiled` (`:compiled`) is the one source of the
  default `:after` and the expected `:result` of `compile-producer`.
- Verify (Java 8 parent): `:cross-jvm-test` `cell-runner-test`,
  `field-aot-test` (157); `generated-value-aot-test` (86); fallback
  `aot-test` (68). cljfmt and clj-kondo clean. After record not retaken
  (`child-limit-test` count +4 in `cross-jvm`; no label or child change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 36)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 36) fixes

- `harness/assert-child-stderr` is `(assert-run run {:err ""})` in its
  `"child stderr"` context: the failure message is the run facts.
  `:err ""` removed from the `assert-run` maps of
  `crashing-child-outcome-test`, `unreadable-result-outcome-test` and
  `timed-out-child-outcome-test`.
- Count change: −1 in each of the three tests (the same at limit 1 and
  4); `cell-runner-test` 94 → 91 assertions. Labels unchanged; acceptance
  3 holds.
- Public `jvm-config/current-runtime` (`{:java :classpath}` of the current
  JVM). `configuration` uses it for `:selected` and the `classpath`
  local; the `limit-load-form` grandchild requires `value-type.jvm-config`
  and uses it.
- Verify (Java 8 parent): `:cross-jvm-test` `cell-runner-test` (91);
  fallback `aot-test` (68). cljfmt and clj-kondo clean. After record not
  retaken (−3 assertions in `cross-jvm`; no label or child change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 37)

- Added 4 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 37) fixes

- Child-safe `cell-registry/runtime-facts` (require-order →
  `{:runtime :policy :flags :require-order}`, `:runtime` from
  `java.version`) and `cell-registry/rejection` (exception →
  `{:exception :data :message :cause}`). Used in
  `array-matrix-checks/check`, `array-sequence-checks/check`, their
  `unavailable-call`, and `array-info-boundary-test/control-form`. The
  `:runtime` of the matrix and sequence children changes from
  `java.runtime.version` to `java.version`; the parent checks only that
  it is not empty.
- Public `array-capability/runtime-expected` (`[description runtime?]`,
  configuration, policy, require-order → `[description predicate]` of the
  four keys). Used in `array-matrix-test/check-expected`,
  `array-sequence-boundary-test/expected` and
  `array-info-boundary-test/control-expected`.
- `config/preview-option` in `aot-fixture/compile-java-child`,
  `array-capability/rejection-names` and
  `consumer-aot-checks/unavailable-names` (now requires `jvm-config`).
- `limit-load-form` requires `clojure.string` and
  `value-type.label-record`; the grandchild form is data
  (`~(pr-str '(do ...))`).
- Verify (Java 8 parent): `:cross-jvm-test` `array-matrix-test`,
  `array-sequence-boundary-test`, `array-info-boundary-test`,
  `cell-runner-test` (535); `unavailable-predicates-test`,
  `identity-receiving-cross-jvm-test`, `aot-fixture-test` (140);
  `field-aot-test` (63); fallback `aot-test` (68). cljfmt and clj-kondo
  clean. After record not retaken (no label, child or count change).
- Still blocked: pass 13 acceptance 3 reading step and pass 26 step (need
  user decisions).
- Addressed 4 review steps.

## Code-shaper review (pass 38)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 38) fixes

- `['<detail> harness/detail?]` in `array-matrix-test/check-expected` and
  `array-sequence-boundary-test/expected`.
- Public `array-capability/export-name`, used in `rejection-names`.
  `array-info-boundary-test` binds private `unavailable-names` and uses
  it in `control-expected` (`list* 'contains`) and `array-outcome?`
  (`message-names?`).
- `array-info-boundary-test/control-form` is one syntax-quoted form.
- Verify (Java 8 parent): `:cross-jvm-test` `array-matrix-test`,
  `array-sequence-boundary-test`, `array-info-boundary-test` (444).
  cljfmt and clj-kondo clean. After record not retaken (no label, child
  or count change).
- Still blocked: pass 13 and pass 26 steps (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 39)

- Added 5 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 39) fixes

- Public `array-capability/runtimes` (`[label runtime]`, allocation order)
  and `runtime`. `allocation-configurations` maps a private
  `allocation-reasons`. `array-info-boundary-test` keeps its row order
  (`inspection-rows`, runtime by label), so the report order is unchanged.
- `:exception` in `capability/rejection-description` and `rejection?`.
  The other users of `rejection?` had no `:exception`:
  `array-boundary-test/control-form` now gives `registry/rejection` (its
  description adds `:exception`), and the `unavailable-predicates-test`
  fixture adds it. No new false case (labels unchanged).
- `export-only-capabilities-test`: allocation by `registry/rejection`;
  `described` expected from `rejection-description` and `rejection?`.
  Private `inspect-form` used in `control-form` and `export-only-form`.
- Child-safe `cell-registry/jvm-facts`; `jvm-config` requires
  `cell-registry`.
- `assert-child` count assertion has the `run-facts` message.
- Verify (Java 8 parent): `:cross-jvm-test` `unavailable-predicates-test`,
  `cell-registry-test`, `array-boundary-test`, `array-info-boundary-test`,
  `array-matrix-test`, `array-sequence-boundary-test` (483); fallback
  `aot-test` (68). cljfmt and clj-kondo clean. After record not retaken
  (no label, child or count change).
- Still blocked: pass 13 and pass 26 steps (need user decisions).
- Addressed 5 review steps.

## Code-shaper review (pass 40)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 40) fixes

- Child-safe `cell-registry/rejected-call` (f): `(rejection e)` with
  `:status :rejected` for an `ExceptionInfo`, else `{:status
  :unexpected-success}`. Used in `array-matrix-checks`,
  `array-sequence-checks` (`assoc` of `:effects` after the call),
  `array-boundary-test/control-form` and
  `array-info-boundary-test/export-only-form`. Expectation change: the
  rejections of `array-boundary-test` and `export-only-capabilities-test`
  add `:status :rejected`; their unexpected success is now `{:status
  :unexpected-success}` (was the `make-array` value, and
  `:unexpected-success`). Labels unchanged.
- Public `array-capability/repeated-rejection` (reason, extra):
  `[description predicate]` of `attempt-count` equal rejections with
  `:status :rejected` and extra. Used in the four places.
- Public `array-capability/rejection-exception`; used in
  `array-info-boundary-test` (`invalid-outcome`, `unavailable-outcome`)
  and the `unavailable-predicates-test` fixture.
- Verify (Java 8 parent): `:cross-jvm-test` `unavailable-predicates-test`,
  `array-boundary-test`, `array-info-boundary-test`, `array-matrix-test`,
  `array-sequence-boundary-test` (468), `cell-registry-test` (15). cljfmt
  and clj-kondo clean. After record not retaken (no label, child or count
  change).
- Still blocked: pass 13 and pass 26 steps (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 41)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 41) fixes

- Public `array-capability/allocation-children` (check, expected).
  expected is a function of reason only (both tests read only reason), and
  gives `[description predicate]` of the result keys beside the runtime
  facts; `allocation-children` adds the `['<detail> harness/detail?]`
  runtime facts, so the pair is written once. Used in `array-matrix-test`
  and `array-sequence-boundary-test`.
- Child-safe `cell-registry/guarded-call` (f, success): `(success value)`,
  or `(rejection e)` with `:status :rejected` for an `ExceptionInfo`.
  `rejected-call` is `guarded-call` with a constant `{:status
  :unexpected-success}`. Used in `array-info-boundary-test/control-form`.
  Expectation change: the invalid-input and inspection rejections of
  `safe-inspection-boundary-test` add `:status :rejected`. Labels
  unchanged. `array-matrix-checks/check` uses `(registry/twice
  unavailable-call)`.
- Public `cell-harness/repeated` (n, `[description predicate]`). Used in
  `capability/repeated-rejection` (with `repeated?`), `control-expected`
  (with `repeated?`; private `invalid-expected`, `unavailable-expected`,
  `valid-expected`; `array-outcome?` and `unavailable-outcome` removed)
  and `field-transfer-test/unavailable` (nested).
- Verify (Java 8 parent): `:cross-jvm-test` `array-matrix-test`,
  `array-sequence-boundary-test`, `array-info-boundary-test`,
  `field-transfer-test`, `unavailable-predicates-test`,
  `array-boundary-test`, `cell-registry-test`: 1575 assertions, 0
  failures. cljfmt and clj-kondo clean. After record not retaken (no
  label, child or count change).
- Still blocked: pass 13 and pass 26 steps (need user decisions).
- Addressed 3 review steps.

## Implementation review (pass 26)

- Added 1 step to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Implementation review (pass 26) fix

- After record at `d108b6d` (`target/test-record/run-p59.sh`; raw
  `after37/`, `after37-limit1/`). All six runs exit 0, 0 failures.
  Assertions 1716 (each fallback command), 4358 (`cross-jvm`, limit 4 and
  limit 1; 4357 at `5264320`, +4 pass 35, −3 pass 36); tests 58, 93.
  - Limit 4 (`/usr/bin/time -p` real): `fallback-java8` 115.23 s,
    `fallback-preview-off` 35.11 s, `fallback-preview-on` 37.21 s,
    `cross-jvm` 391.80 s; sum 579.35 s = 14.3% of 4060.34 s. Acceptance 2
    time holds.
  - Limit 1: `fallback-java8` 238.75 s, `cross-jvm` 831.55 s.
  - `check_record.clj`: max cell count 1; no bound finding; 0 mapping
    findings (904 first labels each fallback command). Limit-1 = limit-4
    label vectors of each namespace: true for `fallback-java8` (10) and
    `cross-jvm` (25). Acceptance 3 holds.
  - `after-record/*.edn` kept: the four summaries differ in `:ms` only
    (the summary has no assertion count).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Implementation review (pass 27)

- Added 1 step to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Implementation review (pass 27) fix

- `array-capability/repeated?` renamed `equal-outcomes?`; it checks only
  that the outcomes are equal (true when empty). `harness/repeated` is the
  one count rule. `repeated-rejection` and `control-expected` check
  `equal-outcomes?` after the `harness/repeated` predicates.
  `control-expected` binds `native` once.
- Verify (Java 8 parent): focused `array-info-boundary-test`,
  `array-matrix-test`, `array-sequence-boundary-test`,
  `array-boundary-test`, `unavailable-predicates-test` (468, 0 failures).
  cljfmt and clj-kondo clean. After record not retaken (no label, child
  or assertion change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Implementation review (pass 28)

- No new steps. Open: pass 13 and pass 26 steps (need user decisions).

## Implementation test review (pass 61)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 61) fix

- `jvm-runner-test` and `cell-runner-test` each have a private literal
  `limit-variable` (`"VALUE_TYPE_TEST_JVM_LIMIT"`) with a comment that the
  name is the design Scope 4 contract. `read-limit-test`,
  `limit-variable-load-test` (`texts`) and `limit-load-form` (grandchild
  environment key) use it. `jvm-runner/limit-variable` stays in the
  runner.
- Mutation `jvm-runner/limit-variable` → `"VT_LIMIT"`: `read-limit-test`
  and `limit-variable-load-test` fail (8 failures); restored.
- Verify (Java 8 parent): focused `jvm-runner-test`, `cell-runner-test`
  (135, 0 failures). cljfmt and clj-kondo clean. After record not retaken
  (no label, child or assertion change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Implementation test review (pass 62)

- Added 1 step to be addressed. Open: pass 13 acceptance 3 reading step
  and pass 26 step (need user decisions).

## Implementation test review (pass 62) fix

- `cell-outcomes-test/assert-child-report-test`: case "child stderr"
  (one cell, run `:err "x"`): one `:fail`, context `["child stderr" ...]`,
  message contains `:err "x"`. Case "child exit" (two cells, run `:exit
  1`): fail contexts are `["child" ...]`, `"report exit 0"`, `"report
  exit 1"`. Deviation: not exactly one `:fail`; `cell-outcomes` gives
  `:no-result` for each cell of a nonzero exit. The `"child"` context is
  the grouped exit assertion only (the result count passes).
- Mutations: `assert-child-stderr` → `(assert-run run (select-keys run
  [:err]))` fails "child stderr"; grouped `(assert-run run {:exit 0})`
  removed fails "child exit"; restored.
- Verify (Java 8 and Java 25 parent): focused `cell-outcomes-test` (47,
  0 failures). cljfmt and clj-kondo clean. After record not retaken
  (new labels only).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Implementation test review (pass 63)

- No new issues found.

## Implementation test review (pass 64)

- No new issues found.

## Implementation docs review (pass 7)

- No new issues found.

## Code-shaper review (pass 42)

- Added 1 step to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 42) follow-up

- Public child-safe `cell-registry/policy-property`
  (`"value-type.fallback"`). `runtime-facts` reads it; `jvm-runner`
  requires `cell-registry` and builds `policy-option` from it. Docstrings
  of `runtime-facts`, `policy-option` and `run-jvm` updated.
- Verify (Java 8 parent): focused `jvm-runner-test`, `cell-runner-test`,
  `array-info-boundary-test`, `array-matrix-test` (435, 0 failures).
  cljfmt and clj-kondo clean. After record not retaken (no label, child
  or assertion change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Code-shaper review (pass 43)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 43) follow-up

- Child-safe `cell-registry/failed-definition` (`thrown` with
  `:absent?`), used in `transfer-checks/attempt`,
  `expansion-checks/attempt`, `runtime-test/public-definition-test`,
  `cell-runner-test/fallback-cell-form` and
  `identity-loader-checks/stale-sibling-strict-case`. Expectation shape
  change: the last two give the flat map `{:class :message :chain
  :absent?}`, not `{:absent? :rejection {...}}`.
  `loader-policy-test/strict-rejection` is `(harness/rejections chain
  warning {:rejected? true :absent? true})`; `scope-3-policy-rules-test`
  expects `(assoc (harness/policy-rejection detail) :chain
  harness/source-rejection-chain :absent? true)`.
- `harness/warning-line` public; used in `scope-3-policy-rules-test`.
- `cell-ns-name` (and private prefix) moved to `cell-registry`; the child
  form calls `value-type.cell-registry/cell-ns-name`; callers use
  `registry/cell-ns-name`; `harness/cell-ns-name` removed.
- Verify (Java 8 parent): `:cross-jvm-test` focused `cell-runner-test`,
  `loader-policy-test`, `cell-registry-test`, `cell-outcomes-test`,
  `field-transfer-test`, `identity-receiving-cross-jvm-test` (1743, 0
  failures), `child-transfer-test`, `controlled-field-capability-test`
  (879, 0); `:fallback-test` `runtime-test`, `transfer-test` (315, 0).
  cljfmt and clj-kondo clean. After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 44)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper pass 44 fixes

- `runtime-test/policy-test`: form is syntax-quoted; it sets
  `registry/policy-property` and gives `registry/failure` of the Error.
  Expectation shape change: `{:class :message}`
  (`harness/policy-rejection context`), not `[class message]`. Context
  bound once.
- `failure` and `root-failure` moved from `consumer-aot-checks` to
  `cell-registry`; callers updated; `warning-checks` no longer requires
  `consumer-aot-checks`. `expansion-checks/discarded` uses
  `registry/failure`.
- `registry/failed-definition` has an optional first argument, the
  failure function (default `thrown`). Reason: `incompatible-value` uses
  `define` too, and `checks/unavailable-outcome?` needs `:data` (the
  reason), so one fixed `thrown` shape is not sufficient. `define` takes
  the failure function: `receiving-policy` gives `registry/thrown`,
  `incompatible-value` gives `registry/root-failure`.
  `field-policy-checks` also uses `failed-definition` (with
  `registry/failure`). Expectation change:
  `receiving-identity-policy-test` strict expects `:chain
  harness/source-rejection-chain` in place of `:data nil`.
- Public `harness/direct-rejection-chain`; used in
  `aot-test/source-free-fallback-aot-loading-test` and
  `source-free-runtime-only-fallback-test`; `rejection?` `:any` class
  from `policy-rejection`.
- Verify: Java 8 parent `:cross-jvm-test` focused
  `identity-receiving-cross-jvm-test`, `field-transfer-test`,
  `child-transfer-test`, `public-field-aot-test`,
  `controlled-field-capability-test`, `cell-outcomes-test`,
  `unavailable-predicates-test`, `loader-policy-test`, `cell-runner-test`
  (3011, 0 failures), `field-capability-test`,
  `public-native-field-aot-test`, `public-mixed-field-aot-test` (559, 0),
  `cell-registry-test` (15, 0); `:fallback-test` `runtime-test`,
  `aot-test`, `transfer-test`, `fields-test`, `identity-receiving-test`
  (1409, 0); JEP 401 EA parent, preview off and on, `runtime-test`,
  `aot-test` (232, 0 each). cljfmt and clj-kondo clean. After record not
  retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 45)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 45) fix

- Child-safe `registry/root-data` (root ex-data); `root-failure` uses it.
- Child-safe `registry/outcome` (failure-of, f, optional success; default
  success `:accepted`). Used in `function-aot-checks/invoke-install`,
  `child-transfer-checks/reject-replaced-child`, `warning-checks`
  (`nil-construction`, `concurrent-rejections`), `consumer-aot-checks`
  (`outcome`, `strict-consumer`), `field-policy-checks` (`policy-attempts`
  with optional failure-of and success; `evaluation` has no inner `try`),
  `expansion-checks` (`attempt`, `discarded`,
  `invalid-constrained-discarded` with `root-data`) and
  `validation-checks`. Catches of one Throwable class only
  (`java-consumer`, `incompatible-consumer`) stay: other failures must
  propagate.
- `validation-checks`: `check-fields` failure is `failed-definition` of
  `root-failure` merged with `:key :enabled?`; `invalid-forms` failure is
  `failed-definition` of `failure`. `invalid-fields` is pairs of field
  vector and a message text.
- Expectation changes: `source-field-validation-test` expects `:class
  "clojure.lang.ExceptionInfo" :message :data {:value-type/error
  :mutable-field :class-name :fields [x]}` in place of `:error
  :mutable-field`, and `:message` for each invalid form. The class name
  (a gensym) and the message text are not fixed: the expected is
  `described` (private `fixed-entry`, `failed-entry`, `entries`); the
  message must name the class name and `"mutable fields [x]"`, or the
  `invalid-fields` text. `constrained-discarded-expansion-test` and
  `constrained-validation-before-policy-test` expect `:accepted` in place
  of `:returned`. Labels and assertion counts unchanged.
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); JEP 401 EA parent `:fallback-test` preview
  off and on (1716, 0 each). cljfmt and clj-kondo clean. After record not
  retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 46)

- Added 2 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 46) fix

- `registry/outcome` replaces the `try`/`catch Throwable` in
  `identity-receiving-checks` (`define`, `strict-nested`; `attempt`
  removed), `transfer-checks/attempt`, `field-transfer-checks/attempt`,
  `identity-loader-checks/stale-sibling-strict-case`, `aot-checks` (four
  places), the child forms of `runtime-test/public-definition-test` and
  `cell-runner-test/fallback-cell-form`, and the `outcome` local of
  `field-capability-test` (the form now requires `cell-registry`). Stay:
  worker catches that pass the Throwable on (`warning-checks`,
  `expansion-checks`), the `cell-harness` cell catch (`{:thrown ...}`),
  the predicate guard of `result-matches?` and `registry/defined`.
- Public `harness/fixed` and `harness/entries` give `[description
  predicate]`; `source-field-validation-test` builds its `described`
  expected from the two `entries` pairs.
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); EA3 parent `:fallback-test` preview off and
  on (1716, 0 each). cljfmt and clj-kondo clean. After record not
  retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 47)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 47) fix

- Child-safe `registry/unexpected-success` (`{:status
  :unexpected-success}`) and `registry/failed-call`; used in
  `field-transfer-checks/attempt`, the `outcome` local of
  `field-capability-test` and `identity-loader-checks/stale-sibling-strict-case`
  (no case result in the map); `rejected-call` uses the value. Only
  failure outcomes are expected, so expectations do not change.
- Public `harness/detailed` (present?, expected-of): `:detail` is
  `<detail>` (`detail?`) when present?, else nil; the rest of the map
  equals `(expected-of detail)`. `runtime-test` constrained-discarded and
  constrained-validation now describe and check `:detail`: present when
  the capability is not available, else nil. `field-capability-test`
  gives present? = reason; the predicate now checks nil detail with no
  reason. The predicate compares the full map (no extra keys).
- Public `harness/keyed` (map of key to `[description predicate]`); used in
  `source-field-validation-test`, `field-aot-test/persisted-output?`,
  `public-field-aot-test/strict-loads?` (`:loads` is `entries`: exactly two
  loads, as `twice` gives) and `array-info-boundary-test/control-expected`
  (`:outcomes` is `entries` of private `equal-repeated`).
  `array-capability/runtime-expected` gives the keyed pairs map;
  `allocation-children` applies `keyed`.
- Not changed: `identity-receiving-cross-jvm-test/incompatible-value`. Its
  `:outcome` check takes the observed `:capability` (reason of
  `unavailable-outcome?`), and `keyed` checks each key alone. A fixed
  expected reason would change the expectation.
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); EA3 parent `:fallback-test` preview off and
  on (1716, 0 each). cljfmt and clj-kondo clean. After record not
  retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 48)

- Added 3 steps to be addressed. Open: pass 13 and pass 26 steps (need
  user decisions).

## Code-shaper review (pass 48) fix

- `allocation-children`: `expected` gives a map of key to `[description
  predicate]`; the child expected is `(keyed (merge (runtime-expected ...)
  (expected reason)))`. `array-matrix-test/outcomes-expected`,
  `array-sequence-boundary-test/expected` and
  `field-transfer-test/attempts-of` use `keyed`, `entries` and `fixed`.
- Description change: the rejected branch of
  `array-sequence-boundary-test/expected` now describes `:definitions nil`
  (`(fixed nil)`); the predicate checks a nil value, not an absent key.
  The child gives no `:definitions` there, so the result does not change.
- Public `capability/equal-repeated`; `repeated-rejection` is built from
  it; `array-info-boundary-test` uses it; `equal-outcomes?` is private.
- `harness/described` takes one `[data f]` argument; `apply` removed from
  the nine callers; two-argument callers (`rejections`,
  `allocation-children`, `cell-outcomes-test`, others) give a pair;
  `export-only-expected` uses `keyed` (same checks).
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); EA3 parent `:fallback-test` preview off and
  on (1716, 0 each). cljfmt and clj-kondo clean. After record not
  retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 49)

- Added 2 steps to be addressed.

## Code-shaper review (pass 49) fix

- Public `cell-harness` pair functions: `contains` (substrings of a
  string, or items of a collection), `starts-with`, `detail` (default
  `detail?`; a value predicate for other details), `with-entry` (a map
  equal to a fixed map, with one key checked by a pair), and `:+` (one or
  more) in `repeated`. Used in `runtime-test` (`capability-test` by
  `keyed`; `failed-entry` by `with-entry` and `contains`),
  `array-info-boundary-test` (`unavailable-expected`, `starts-with`
  runtime), `cell-runner-test/limit-variable-load-test` (`keyed`; `:exit`
  is a non-zero integer `detail`), `aot-test` (`keyed` `:chain`),
  `aot-fixture/persisted-output?` and `capability/allocation-children`.
- Rejection rules are one pair function each: `harness/rejection`
  (replaces `rejection-description` and `rejection?`),
  `capability/rejection` (replaces `rejection-description` and
  `rejection?`; optional extra entries, used by `repeated-rejection`), and
  `value-capability/unavailable` (replaces
  `consumer-aot-checks/unavailable-description` and
  `unavailable-outcome?`). `cell-outcomes-test` and
  `unavailable-predicates-test` use `second` of the pair.
- `value-capability` is a new parent namespace (`test/cross-jvm`):
  `unavailable-failure` and `unavailable` moved from
  `consumer-aot-checks`. Reason: the pair needs `cell-harness`, and
  consumer children load `consumer-aot-checks`; a child must not load
  `cell-harness` (start cost). All users are cross-JVM tests.
- Description change: `(harness/rejection chain :any)` describes
  `:message` as `(starts-with "value-type: identity deftype fallback: ")`,
  not the string `"value-type: identity deftype fallback: <detail>"`. Other
  descriptions are unchanged. Predicates are unchanged, except that a
  non-string where a string is expected now gives false, not an
  exception.
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); EA3 parent `:fallback-test` preview off and
  on (1716, 0 each); focused `cell-outcomes-test` and
  `unavailable-predicates-test` (63, 0). cljfmt and clj-kondo clean.
  After record not retaken.
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 2 review steps.

## Code-shaper review (pass 50)

- Added 3 steps to be addressed.

## Code-shaper pass 50 fix

- `cell-harness/observed` (key path, `[description predicate]` of the
  observed value, function of the value to a pair) is the one rule of an
  expectation that depends on an observed runtime value. `detailed` is
  built from it with `(detail)` or `(fixed nil)`. Used in
  `runtime-test/failed-entry` (`[:data :class-name]`),
  `identity-receiving-cross-jvm-test/incompatible-value-receiving-test`
  and `field-transfer-test/unavailable` (both `[:capability :reason]`).
- Predicate changes: `failed-entry` checks `:class-name` by `detail?`
  (non-blank string), not `string?`. The two `:capability` users check
  that `:reason` is a keyword (`(detail keyword?)`); `field-transfer-test`
  had no check. `incompatible-value` checks `:capability` by `keyed`
  `{:available? (fixed false)}` (same as `(false? (:available? ...))`).
  Descriptions unchanged.
- `cell-harness/repeated-text` gives the string form `(repeat n text)`
  (n or `:+`); `occurrences` is private.
  `fallback-expansion-warning-timing-test` uses `keyed` and `fixed`, with
  the cross-key check in one `and`.
- `cell-harness/including` gives the new description form `(including m)`
  (named in the `described` docstring). Used for `:native` of
  `array-info-boundary-test/control-expected` (`native-expected` inlined;
  used once) and `{:thrown (including ...)}` of
  `runtime-test/invalid-policy-test` and `cell-outcomes-test`
  `result-check-test`. The `{:thrown}` branch of `result-matches?` is
  removed. Description changes: `:native` is `(including {...})`, not the
  bare map; the invalid-policy expected is a `described` predicate with
  description `{:thrown (including {...})}`, not the bare map.
- Verify: Java 8 parent `:fallback-test` (1716, 0 failures) and
  `:cross-jvm-test` (4361, 0); EA3 parent `:fallback-test` preview off and
  on (1716, 0 each). REPL check of the failure side of `observed`,
  `repeated-text`, `including` and `detailed`. cljfmt and clj-kondo clean.
  After record not retaken (no label, child or count change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 3 review steps.

## Code-shaper review (pass 51)

- Added 2 steps to be addressed.

## Code-shaper pass 51 fix

- `cell-harness/keyed` is closed: the predicate is true only for a map
  with exactly the keys of pairs (an absent key fails, also when its pair
  accepts nil). `including` takes a map of key to `[description
  predicate]` (open; description `(including {k description})`); each
  key must be present. `with-entry` is `(keyed (assoc (update-vals m
  fixed) k entry))`. The `described` docstring states that a bare map is
  an exact map, and names `(warning-line <detail>)`.
- `including` in place of `keyed` where the result has other keys:
  `aot-test` `{:chain ...}`, `identity-receiving-cross-jvm-test`
  `:capability`, `field-transfer-test/unavailable` and `attempts-of`
  (result has `:capability`), `public-field-aot-test/strict-loads?` retry
  load (has `:message`). Former fixed-map `including` users give
  `(update-vals m fixed)`.
- Description changes: these places show `(including {...})`, not a bare
  map. `array-capability/rejection` `:data` now is closed: `:detail
  <detail>` is added except for `:array-access-denied` (new private
  `rejection-data`); predicate checks it. `array-sequence-boundary-test`
  rejected branch: `:definitions` key removed from the expected (the
  child gives no key; pass 48 described `:definitions nil`).
- `expected-err` gives a described pair: `(fixed "")`, `(fixed
  (warning-line w))`, or private `any-warning-line` (description
  `(warning-line <detail>)`, same regex). `:out` uses `(fixed "")`. Both
  are checked by `result-matches?`; `err-matches?` removed. A failure
  report of a `:warning :any` cell gives `:expected (warning-line
  <detail>)`, not `[:any]`.
- New in-process cases: `cell-outcomes-test` "any warning description",
  "fixed warning description" and `map-pair-test`;
  `unavailable-predicates-test` `:detail` cases (fixture data has
  `:detail` unless access denied).
- Verify: Java 8 parent `:fallback-test` (1716, 0) and `:cross-jvm-test`
  (4376, 0; +15 in-process assertions); EA3 parent `:fallback-test`
  preview off and on (1716, 0 each). cljfmt and clj-kondo clean. After
  record not retaken (no label or child change; in-process cases only).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 2 review steps.

## Implementation review (pass 28)

- Added 1 step to be addressed.

## Implementation review pass 28 fix

- `harness/rejected` (chain, context, pairs): `keyed` of the private
  `rejection-pairs` (`:class :message :chain`) merged with pairs.
  `harness/rejection` is `keyed` of the same pairs (fixed context: same
  description map, exact-map predicate as before).
- Users: `aot-test/fallback-aot-compilation-test` (`entries` of
  `each-attempt`), `runtime-test/public-definition-test` and
  `fallback-expansion-policy-test` (private `strict-rejected`; `keyed`,
  `entries`, `fixed`), `loader-policy-test/strict-rejection`.
  `rejections` and its `clojure.walk` require removed;
  `cell-outcomes-test/rejections-test` is `rejected-test` (+1 assertion:
  other key).
- Description change: none (same vectors and maps; rejection keys
  inline). Predicate change: each rejection is checked at its position,
  not at any map with `:chain`.
- Verify: Java 8 parent `:fallback-test` (1716, 0) and `:cross-jvm-test`
  (4377, 0); EA3 parent `:fallback-test` preview off and on (1716, 0
  each). cljfmt and clj-kondo clean. After record not retaken (no label
  or child change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Implementation review (pass 29)

- Added 2 steps to be addressed.

## Implementation review pass 29 fix

- Public `harness/failed-definition` (context, pairs): `rejected` of
  `source-rejection-chain` and context, pairs with `:absent? (fixed true)`.
  Used in `runtime-test` (2), `loader-policy-test`,
  `aot-test/fallback-aot-compilation-test` and
  `transfer-test/transferred-expansion-test`; private `strict-rejected`
  and `strict-rejection` removed. `scope-3-policy-rules-test` keeps its
  stated entries.
- Description change: `transferred-expansion-test` is now `described` of
  `entries` (was a fixed vector); the description vector has the same maps.
  Predicate change: there, each attempt is checked at its position by
  `keyed` (exact keys), not by `=`. Others: none.
- `harness/rejection` removed; `cell-outcomes-test/rejection-test` uses
  `(rejected chain context {})`; namespace docstring updated.
- Verify: Java 8 parent `:fallback-test` (1716, 0) and `:cross-jvm-test`
  (4377, 0); EA3 parent `:fallback-test` preview off and on (1716, 0
  each). cljfmt and clj-kondo clean. After record not retaken (no label
  or child change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 2 review steps.

## Implementation review (pass 30)

- Added 1 step to be addressed.

## Implementation review pass 30 fix

- `identity-receiving-cross-jvm-test/receiving-identity-policy-test`:
  expected is `described` of `entries` of `registry/each`; `error` is
  `(harness/failed-definition harness/fallback-context {})`, other
  policies `harness/fixed`.
- Description change: a vector of the same maps (was a fixed vector).
  Predicate change: each holder result is checked at its position by
  `keyed` (exact keys) for `error`, by `=` for the others.
- Verify: Java 8 parent `:cross-jvm-test --namespace
  value-type.identity-receiving-cross-jvm-test` (120, 0; same count as
  before). cljfmt and clj-kondo clean. After record not retaken (no label
  or child change).
- Still blocked: pass 13 acceptance 3 reading step and test review pass
  26 step (need user decisions).
- Addressed 1 review step.

## Closure — 2026-11-01

Closed at the user’s request. This is an administrative closure, not a
claim that all acceptance and review items passed.

Two review items remain unchecked in `steps.md`:

- Implementation review pass 13: agreement on per-namespace label order
  instead of full-report order is not recorded in the design and plan.
- Test review pass 26: the loader-isolation check still passes when the
  expansion frame is removed; the required negative check is not proved.

Moved the task to `munera/closed/` and updated the record-check script’s
path. `munera/plan.md` had no task entry to remove. Existing verification
records are unchanged. No tests were rerun for closure.
