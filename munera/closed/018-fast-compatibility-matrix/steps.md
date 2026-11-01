# Steps — 018 fast compatibility matrix

Closed at the user’s request on 2026-11-01. The two unchecked review items
remain unresolved; see the closure note in `implementation.md`.

## Slice 1 — recorder and first record

- [x] Record the machine (model, CPU count, OS) and the JDK builds of the
  three parents in `implementation.md`.
  - done; see implementation.md "Slice 1".
- [x] Add `value-type.label-record`
  (`test/compatibility/value_type/label_record.clj`) per `plan.md`
  "Recorder": active only when `VALUE_TYPE_TEST_RECORD` is set; `test-var`
  root wrapper (`*var*`, `report` binding, Var time, record file written
  after each Var); `child-start!` (each `:children` entry has its
  `:assertion-index`); `cell!`; `summary` with methods `:start-labels` (uses
  `:assertion-index` to find the events of each child start) and `:cells`.
  - e61905d.
- [x] In `jvm-runner`: require `label-record`; `run-jvm` calls
  `child-start!` with its `:kind` (default `:cell`) on the calling thread.
  Set `:kind` per `plan.md` "Recorder" (child kind): `compile-clojure` and
  `compile-producer` take a `:kind` option (default `:aot-compile`);
  `field-aot-test/persisted-restricted-field-output-test` passes
  `:kind :cell`; `compile-java` uses `:java-compile`; each direct producer
  `run-jvm` call gets `:producer` and each direct AOT compile call gets
  `:aot-compile` (`aot-test` lines 48, 89, 121);
  `aot-test/fallback-aot-compilation-test` keeps `:cell`. Read each
  `run-jvm` caller; do not classify by grep only. No other change.
  - e61905d.
- [x] Verify on one namespace that the record labels equal the Scry
  `:var`/`:contexts` of the same run (`scry.core/run` with `:var` result
  scope), and that `child-transfer-test/receiver-child-replacement-test`
  records 1 producer and 72 cell children. Commit; record the SHA in
  `implementation.md` as the first-record commit.
  - e61905d is the first-record commit; scry labels = record labels on `loader-policy-test`; child-transfer: 72 cell + 1 producer.
- [x] Run the three documented `:fallback-test` commands (Java 8, EA3
  preview off, EA3 preview on) at that commit, each with
  `VALUE_TYPE_TEST_RECORD=target/test-record/first/<step-id>.edn` and
  `/usr/bin/time -p`.
- [x] Write the first record into `implementation.md` from
  `summary :start-labels`: for each namespace the child count, namespace time
  and label set with cell counts (nested case labels of
  `loader-policy-test/fresh-policy-loader-rejection-test` count 1 each; its 14
  start labels count 0); plus the total time.
  - done in worktree at e61905d; times in implementation.md.
  - tables in implementation.md; full label sets in `first-record/`.

## Slice 2 — runner

- [x] In `test/compatibility/value_type/jvm_runner.clj`, read and validate
  `VALUE_TYPE_TEST_JVM_LIMIT` once at namespace load: positive decimal
  integer, default 4, invalid value throws with the variable name and value in
  the message.
  - a047460.
- [x] Add `run-jvms` per `plan.md` "Runner": vector of option maps in, results
  in the same order; `child-start!` on the calling thread for each map; each
  child on a `future` that holds a permit of one shared `Semaphore` (limit
  permits) from process start until its output is read. `run-jvm` becomes
  `run-jvms` of one map. Java 8 APIs only.
  - a047460.
- [x] Apply the read rule (`plan.md` "Runner"): on exit 0 with an unreadable
  last stdout line, a `:cell` child gives `:exit`, `:out`, `:err`,
  `:read-error` and no `:result`, with no throw; `run-jvms` returns all
  results of the call. A producer or compile child keeps the throw (an
  `ex-info` with child index, `:exit`, `:out`, `:err` and read error), raised
  after all children of the call return. Update the `run-jvm` docstring.
  - a047460; REPL-checked.
- [x] Apply the timeout rule: from process start (permit wait excluded),
  `:timeout-ms` (default 120000) plus 10000 × (`:cell-count` − 1);
  `:cell-count` defaults to 1.
  - a047460.
- [x] Add `aot-fixture` functions that return the option map of each compile
  child, so a test can pass more than one compile child to `run-jvms`.
  - a047460: `compile-clojure-child`, `compile-java-child`, `compile-form` (data form).
- [x] Verify: `:fallback-test` under Java 8 with limit 1 and with the default
  limit gives equal `:assertions` label vectors in the record; an invalid
  limit value makes Scry report `:scry.cli/runner-error` and exit 1; a
  `--namespace value-type.fields-test` run with an invalid value passes; at
  the REPL, a `:cell` child whose last line is `#object[...]` returns
  `:read-error`, and a `:producer` child with the same line throws.
  - done: invalid limit gives `:scry.cli/runner-error` exit 1; `--namespace value-type.fields-test` with an invalid value passes; REPL `:read-error`/producer throw checked. Limit-1 vs limit-4 `:assertions` labels equal on converted `child-transfer-test`. Full `:fallback-test` and `:cross-jvm-test` limit-1 vs default: equal (Slice 8).

## Slice 3 — cell harness

- [x] Add `value-type.cell-harness`
  (`test/compatibility/value_type/cell_harness.clj`) with `cell-child`: takes
  a configuration (`:java`, `:classpath`, `:jvm-opts`, `:policy`, optional
  `:timeout-ms` and `:fixture-ns`) and cells (`:context`, `:form`,
  `:expected`, `:fallback?`, `:warning`; `plan.md` "Cell harness"); returns a
  `run-jvm` option map with `:kind :cell`, `:cell-count` and the child form.
  - 1c28523; also `run-cells` (run-jvms + assert-child).
- [x] Add `value-type.cell-registry`
  (`test/compatibility/value_type/cell_registry.clj`; `plan.md` "New-Class
  checks"): `clojure.core` only, Java 8 APIs; one `defonce` synchronized
  identity set of Classes; `new-class!` returns true only for a Class that no
  earlier call in the JVM got, and adds it; `absent?` (namespace, simple
  name, optional binary class name) returns true only when the import,
  `->Name` and the class name (`Class/forName` with `RT/baseLoader`) are
  all absent.
  - 1c28523.
- [x] Child form: require `value-type.cell-registry` at top level; cells in
  input order; per cell a fresh namespace (`cell.<index>`, `create-ns`,
  `clojure.core` referred), a new binding
  frame (`Compiler/LOADER` = fresh `RT/makeClassLoader` with parent =
  top-level `LOADER` value; `clojure.main/with-bindings` Vars at cell-start
  values; `*ns*` = the cell namespace), own `*out*`/`*err*` writers,
  `Throwable` captured, so one failing cell does not stop the others; last
  stdout line is the EDN vector of cell result entries (`plan.md` "Cell
  result entry": `{:value v :out o :err e}` or `{:thrown {:class :message
  :chain} :out o :err e}`, text written before a throw included); after the
  print and a stdout flush, the child form calls `shutdown-agents` exactly
  once and exits; no cell form calls it (`plan.md` "Cell harness").
  - 1c28523; REPL-checked warn/error/silent 3-cell children on Java 8.
- [x] Add `cell-outcomes` (`plan.md` "Cell outcomes"): pure function of the
  `cell-child` map and the `run-jvm` result; one outcome per cell in input
  order (`:context`, `:pass?`, `:checks`, `:failure`). Checks: the expected
  result against the cell result (`:value`, or `{:thrown ...}`; `=`,
  predicate, or the stated keys of an expected `:thrown` map), `*out*`
  empty, the expected `*err*` (first `:fallback?` cell of a `warn` child:
  one warning line with its `:warning` context; all other cells: empty)
  and, for a one-cell child, exit. A crash, timeout, `:read-error` or
  missing entry gives the cell only one failing `:result` check (`:actual
  :no-result`). `:failure` of a failing cell: child `:exit`, plus the
  entry `:out`, `:err` and `:thrown` when the child gives an entry for the
  cell, else the process stdout, stderr and `:read-error`.
  - 1c28523.
- [x] Add `assert-child` (the cell-result function): on the test thread, in
  input order; for each cell under its label one `cell!` record, then one
  `clojure.test/do-report` (`:pass` or `:fail`, `:expected`, `:actual`,
  `:message` with the `:failure` text) for each check of its
  `cell-outcomes` outcome.
  - 1c28523.
- [x] In `assert-child`, add the support assertions: empty child-level stderr
  for every cell child in the context
  `(pr-str ["child stderr" java jvm-opts policy fixture-ns])`, made by the
  public function `assert-child-stderr` (Slice 6 tests also call it); for a
  child with more than one cell, exit 0 and result count in the context
  `(pr-str ["child" java jvm-opts policy fixture-ns])`. No classpath or
  temporary path in a context.
  - 1c28523.
- [x] Change `expansion-checks/on-thread` (and
  `constrained-discarded-with-loader`) raw threads to bind only
  `*out*`/`*err*` to the cell writers.
  - 1c28523.
- [x] Verify the harness with one converted test before mass conversion.
  - `child-transfer-test`: 638 assertions pass, see implementation.md.

## Slice 4 — test conversion

Every cell child of the converted tests — grouped, one-cell (for example
each AOT cell child) or kept under rule 5 — is built with `cell-child`, run
with `run-jvms` and asserted with `assert-child`. No inline `is` on a cell
result; a current process-`:err` check becomes the `*err*` check of its cell.
Producer and compile children carry `:kind` and keep inline support
assertions. The Slice 6 fixture children are the only other users of
`cell-child` (`plan.md` "Slice 6 tests").

For each namespace: group cells per the isolation rules, change `prn`-ing cell
forms to return values, add the new-Class checks (`plan.md` "New-Class
checks": a `new-class!` call for each successful type definition that a
cell evaluates, compile cells included; `:new-class?` and `:factory?`
(import and factory identity), or `:absent?` from `cell-registry/absent?`,
in the definition map ("Key placement": the current map of each definition
or attempt; else a non-map attempt or outcome value becomes the definition
map, with `:absent?` added to its failure map by the catching check code;
else `:definitions` by simple type name; one place for each definition) and
in `:expected`;
current absence keys (`:owner-absent?`/`:factory-absent?`, `:absent`,
`:mapped?`/`:loadable?`, `:absent?`) become `:absent?`, and presence checks
`:absent? false` become `:new-class? true :factory? true` ("Current absence
keys"); none in AOT consumer cells; a check function shared with another
namespace, such as `identity-loader-checks/public-stale-sibling`, changes
with all of its callers in one commit; record each added, renamed or
stronger key as a changed expectation of each test), apply the class-name
rule (`plan.md` "Class names": cell code defines in the cell namespace or in
`<cell namespace>.<suffix>`; check code with a fixed `*ns*` binding or a
fixed `create-ns` reads `*ns*` once on the cell thread and uses it; before
the commit, `git grep -n -e 'binding \[\*ns\*' -e 'the-ns' -e 'create-ns'`
on the check code of the namespace; an uncovered case stops the conversion
and is recorded in `implementation.md`), apply the Scope 2 text
expectations, verify warn-guard matches (each grouped warn fallback cell
has a matching error cell in the same namespace, or gets a new label, or
keeps its own child under rule 5 with a code comment naming the rule), and
record in `implementation.md` the label
mapping, each changed expectation, and each rule-5 exception (test, child
count, rule, reason).

- [x] Convert `child-transfer-test` (producer gives one expansion per consumer
  cell, each in its own producer namespace; 72 → bounded by distinct child
  configurations).
  - 180 cell children → 30; 4 producers; 25 s vs 260 s.
- [x] Convert `field-transfer-test`.
  - 222 cell children → 54; 8 producers; 58 s vs 351 s.
- [x] Convert `public-field-aot-test` (split the 12-cells-1-label context;
  compile-producer assertions become support assertions).
  - 86 one-cell children (unchanged, rule 4) + 10 aot + 2 java; 75 s vs ~120 s.
- [x] Convert the remaining cross-JVM namespaces: `array-boundary-test`,
  `array-info-boundary-test`, `array-matrix-test` (require-order cells stay
  rule 5), `array-sequence-boundary-test`, `capture-loading-test`,
  `controlled-field-capability-test`, `field-aot-test`
  (`persisted-restricted-field-output-test` becomes a one-cell child whose
  cell compiles with `aot-fixture/compile-form` and returns `{:output
  (c/inspect-output ...) :definitions {Leaf ... Holder ...}}` with the
  new-Class checks of both types; no `compile-producer`; `plan.md`
  "Conversion order"),
  `field-capability-test` (`field-policy-checks/evaluation` defines in the
  cell namespace; each attempt is a definition map: `{:constructions [...]
  :new-class? true :factory? true}` or the failure map with `:absent?`;
  `:expected` predicate per `plan.md` "Key placement"),
  `function-redefinition-test`,
  `generated-value-aot-test`, `identity-aot-test`, `loader-policy-test`
  (per-case `LOADER` frames; case labels are cells, start labels support),
  `public-mixed-field-aot-test`, `public-native-field-aot-test`.
  - done (pass 5): `array-boundary-test`, `capture-loading-test`,
    `array-sequence-boundary-test` (one-cell children, rule 4).
  - done (pass 6): `array-info-boundary-test` (42 one-cell children,
    rule 4).
  - done (pass 7): `array-matrix-test` (36 one-cell children, rule 4).
  - done (pass 8): `controlled-field-capability-test` (cell children 36 → 24).
  - done (pass 9): `function-redefinition-test` (6 children, 2 cells each).
  - done (pass 11): `public-native-field-aot-test`,
    `public-mixed-field-aot-test` (one-cell children, rule 4).
  - done (pass 12): `identity-aot-test`, `generated-value-aot-test`
    (one-cell children, rule 4).
  - done (pass 13): `field-aot-test` (one-cell children, rule 4;
    persisted-output cell compiles).
  - done (pass 14): `field-capability-test` (cell children 63 → 45).
  - done (pass 15): `loader-policy-test` (14 children × 6 cells + 4 new
    error-policy warn-guard children).
- [x] Convert the cross-JVM tests of `identity-receiving-test`
  (`receiving-identity-policy-test`, `incompatible-value-receiving-test`,
  `generated-value-receiving-policy-test`,
  `strict-nested-public-declaration-test`,
  `nested-public-warning-scope-test`; `identity-receiving-checks` uses the
  cell namespace in place of `ns-name*` and the literal class name).
  - done (pass 10): 30 one-cell children (one per configuration, rule 1:
    grouping unit is one test); `run-check` replaced by `check-cell`.
- [x] Convert `transfer-test` (splits per pass 9: `transferred-expansion-test`
  1 → 4 cells, first fallback cell keeps context `fixed fallback
  representation`; `transfer-checks/consume` takes one mode and one
  expansion and defines in the cell namespace; `warning-checks` worker
  namespaces get the cell namespace prefix, and `define-types` replaces the
  class name of its expansion, given by the test; remove the
  `(shutdown-agents)` call from the `constrained-concurrent-strict-test`
  child form — the harness child form makes the one call;
  `concurrent-warning-scope-test` cells stay rule 5;
  `constrained-concurrent-warning-scope-test` start labels have cell count 2).
  - done (pass 16): cell children 29 → 15 (3 + 2 + 8 + 2); 4 producers.
- [x] Convert `aot-test` (`fallback-aot-compilation-test` is a one-cell
  compile child per `plan.md` "Conversion order"; `aot-checks/compile-fixture`
  gives `:absent?` for each strict attempt and `:new-class?`/`:factory?`
  (Class identity) for the non-strict result; one artifact per cell
  child, rule 4; AOT retry is one cell, rule 5; `reload-form` /
  `consumer-aot-checks` stop printing; moved process-`:err` checks keep
  exact text and Error cause chains).
  - done (pass 17): 15 one-cell children + 4 producers.
- [x] Convert `runtime-test` (`policy-test` direct `enforce-fallback!` call is
  a fallback cell; `public-definition-test` `:outcome` is the definition map
  of `Sample`: `{:field 42 :equal? available? :new-class? true :factory?
  true}`, or `{:chain [...] :absent? true}` under `error` with no value
  support; `expansion-checks` defines in the cell namespace, read before
  `on-thread`; `fallback-expansion-policy-test` warning moves to the
  thread's bound cell `*err*`; nil-policy children are silent cells).
- [x] Verify each converted namespace against the child-count bound
  `cell + producer + aot + java + exceptions` and record the counts.
  - pass 19: Java 8 mid record, 435 s; table in implementation.md.

## Slice 5 — cross-JVM suite split

- [x] Create `test/cross-jvm/`, `git mv` the cross-JVM namespaces from
  `test/compatibility` (names unchanged), and move the cross-JVM tests of
  `identity-receiving-test` and its private `run-check` helper into
  `value-type.identity-receiving-cross-jvm-test`
  (`test/cross-jvm/value_type/identity_receiving_cross_jvm_test.clj`; Var
  names unchanged). Record the label mapping
  `value-type.identity-receiving-test/<name>` →
  `value-type.identity-receiving-cross-jvm-test/<name>`.
  - pass 20; helpers `check-cell`, `run-check-cells`, `cell-ns`, `defined`
    moved too (`run-check` was already replaced in pass 10).
- [x] Add the `:cross-jvm-test` alias to `deps.edn`: `--dir test/cross-jvm`,
  class path includes `test/cross-jvm` and `test/compatibility`. Owner
  parent: Java 8 (`plan.md` "Owner parent").
- [x] In the same commit: add `test/cross-jvm` to `quality.clj`
  `maintained-paths` and `:cross-jvm-test` to its `dependency-aliases`; add
  `test/cross-jvm` to the directory lists in `test/tooling/ci-failures.sh`
  and `test/tooling/hooks.sh`.
- [x] Verify: `:cross-jvm-test` under Java 8 exits 0; a per-parent
  `:fallback-test` run reports no cross-JVM namespace; `--namespace` of a
  cross-JVM namespace under `:fallback-test` gives a Scry runner error and
  exit 1; `bb` quality checks lint `test/cross-jvm`.
  - pass 20: `:cross-jvm-test` 4512 assertions, 61 tests, 309 s, exit 0;
    Java 8 `:fallback-test` 2052 assertions, 58 tests, 111 s, exit 0;
    `--namespace value-type.array-boundary-test` under `:fallback-test`:
    `:scry.cli/runner-error`, exit 1; `bb clj-fmt:check` and
    `bb clj-kondo:lint` clean.

## Slice 6 — permanent runner tests (in test/cross-jvm/, :baseline children)

Each test follows `plan.md` "Slice 6 tests": fixture children built with
`cell-child` and run with `run-jvms`; no `assert-child`, no `cell!` record;
inline assertions on the returned data (support labels, cell count 0, new
labels); `assert-child-stderr` once for each fixture child.

- [x] Add a test showing each Scope 3 rule in a child with more than one
  fallback cell: `warn`, `error` and `silent` children of three or more
  source-definition cells. The test states the expected entry of each cell
  and asserts on the returned entries: warn-once (first cell `:err` is the
  one warning line, later cells `""`), repeated strict rejection (each cell
  `:value` `{:absent? true :rejection r}`, `r` with root class
  `java.lang.Error`, root message and the `Compiler$CompilerException` cause
  chain), silent (each cell `:err` `""`); warn and silent cells give
  `{:new-class? true :factory? true}`. It also asserts that `cell-outcomes`
  gives `:pass? true` for each cell.
- [x] Add runner-outcome tests that assert on `cell-outcomes` data:
  - failing cell: middle cell of three prints a marker to `*out*` and
    `*err*`, then throws; its outcome has `:pass? false`, `:result` check
    `:actual` `{:thrown ...}` with the fixture class and message, and
    `:failure` with `:exit` 0, `:out` and `:err` equal to the marker text
    and the `:thrown` map; the other cells have `:pass? true`, `:failure`
    nil;
  - crashing child: second of two cells writes and flushes a marker line to
    `System/out`, then `(System/exit 3)`; each cell `:pass? false`, one
    `:result` check `:actual :no-result`, `:failure` with `:exit` 3, `:out`
    containing the marker, `:err` `""`;
  - timed-out child: two cells, `:timeout-ms` 2000; first cell writes and
    flushes a marker line to `System/out`, then sleeps past
    2000 + 10000 × (2 − 1) ms; each cell `:pass? false`, one `:result` check
    `:actual :no-result`, `:failure` with `:exit :timeout`, `:out`
    containing the marker, `:err` `""`.
  - pass 21: `value-type.cell-runner-test` (4 tests, 40 assertions pass).

## Slice 7 — documentation and release tooling

- [x] Update `doc/development/runtime.md`: commands ("Verified fallback
  matrix" gains the `:cross-jvm-test` command), test layout (add
  `test/cross-jvm/`), concurrency variable, the timeout rule (replace the
  "120-second timeout" text), owner parent (Java 8), isolation rules,
  `VALUE_TYPE_TEST_RECORD`. Each focused `:fallback-test` command that runs
  a cross-JVM namespace changes to `:cross-jvm-test` under the owner parent
  (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"`):
  - non-nil fields (line 83): `fields-test` stays under `:fallback-test`
    (EA3 parent); the other six namespaces go to one `:cross-jvm-test`
    command;
  - receiving (lines 120–121): `identity-receiving-test` stays in the
    parent loop; one `:cross-jvm-test` command after the loop runs
    `value-type.identity-receiving-cross-jvm-test`;
  - controlled capability (line 136: `controlled-field-capability-test`,
    `function-redefinition-test`), array allocation (line 182:
    `array-matrix-test`), sequence construction (line 213:
    `array-sequence-boundary-test`) and array info (line 250:
    `array-info-boundary-test`): the alias and `JAVA_CMD` change;
  - line 185 ("This test starts fresh JVMs for six configurations"):
    state the six configurations without "fresh JVMs", and refer to the
    isolation rules for the children that run the cells.
- [x] Update `ramora/defvaluetype-design/risks-and-evolution.md` (risk
  "`macroexpand` from tooling installs a loader"): a fresh receiver is a new
  JVM or a cell with its own `LOADER` binding.
- [x] Update the other Ramora verification/embodiment text (no history per
  `ramora/INDEX.md` rules):
  - `ramora/defvaluetype-design/embodiment/artifacts.md` lines 25–26: the
    `test/compatibility/value_type/...` entry drops "explicit fresh-JVM"
    and names per-parent policy, transfer and AOT tests and the shared
    child-JVM test support (alias `:fallback-test`, unchanged second
    sentence); add an entry `test/cross-jvm/value_type/...`: cross-JVM
    policy, transfer, AOT and array matrices in child JVMs with explicit
    runtime, options and classpath; alias `:cross-jvm-test`, run once under
    Java 8; cells share children under the isolation rules of
    `doc/development/runtime.md`.
  - `ramora/release-workflow.md` ("Workflow flow"): the release gate
    includes the cross-JVM tests.
  - `ramora/defvaluetype-design/assertions/fallback-architecture.md`: no
    edit. It names only `transfer-test` and `aot-test` (both stay in
    `:fallback-test`) and no suite, directory or fresh-JVM rule.
- [x] Edit `doc/development/quality-checks.md` line-64 test-directory phrase
  to a count-free form ("all test directories listed in the test layout");
  run `bb vale`.
- [x] Update `.github/workflows/release.yml`: add step id `cross-jvm`
  (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test`)
  directly followed by
  `preserve-cross-jvm-failures` (`failure() && steps.cross-jvm.outcome ==
  'failure'`, upload `.scry-results/` as `release-scry-cross-jvm`,
  `include-hidden-files: true`, `if-no-files-found: warn`), placed with the
  `fallback-*` pairs before `Test build`; check `timeout-minutes: 60` against
  the measured times.
  - pass 22: `timeout-minutes: 60` kept (cross-JVM 309 s + fallback runs ~111 s each).

## Slice 8 — after record and acceptance

- [x] Take the after record: run the four documented commands at the default
  limit on the same machine and JDK builds, each with
  `VALUE_TYPE_TEST_RECORD=target/test-record/after/<step-id>.edn` and
  `/usr/bin/time -p`; record per namespace the child count, namespace time and
  label set with cell counts from `summary :cells`. Check that no cell label
  has a cell count above 1.
- [x] Verify acceptance 2: each test within its child-count bound (summary
  child counts and distinct configurations for each kind, plus the rule-5
  exceptions); label mapping holds (every first-record label or its targets
  present, cell counts sum ≥); total time ≤ 25% of the first total.
- [x] Verify acceptance 3: all four commands exit 0 with empty Scry failures
  at limit 4; `:cross-jvm-test` and the Java 8 `:fallback-test` also exit 0 at
  limit 1 (records in `target/test-record/after-limit1/`), and their
  `:assertions` label vectors equal those of the limit-4 records.
- [x] Verify acceptance 7: `git diff` for the task shows no change under
  `src/`.
- [x] Update
  `mementum/memories/repeated-definitions-must-prove-new-class-identity.md`:
  the design replaces its fresh-JVM rule (fresh namespace + unique class name
  + own `LOADER` frame; policy keeps its own JVM).
- [x] Record final results and any open deviations in `implementation.md`.
  - After record at 4701304; total 656 s (16% of 4060 s); summaries in
    `after-record/`.

## Implementation review (pass 1)

- [x] Correct the isolation-rule citations. Rule 4 is the source-free AOT
  artifact rule. Process-wide require order and first load are rule 5
  (design "Isolation rules" names `array-matrix-test` require order as a
  rule-5 example). Change the "rule 4" comments in
  `test/cross-jvm/value_type/{array_matrix_test,array_info_boundary_test,array_sequence_boundary_test}.clj`
  to rule 5. In `array_boundary_test.clj` and `capture_loading_test.clj`
  the children differ by configuration (rule 1), so remove the rule-4
  claim. Append to `implementation.md` the rule-5 exception list for each
  namespace (test, child count, rule, reason), as the design decision
  "Exceptions are explicit" requires; the pass-19 note "all rule 4" is
  not correct.
  - review pass 1 fix: comments corrected; exception list in implementation.md.
- [x] `public-mixed-field-aot-test/persisted-public-mixed-output-test`
  exceeds the Scope 1 child-count bound (32 cell children vs 31
  configurations). Its 2 inspection cells have the same configuration, read
  files only and have no artifact on the class path, so rule 4 does not
  apply. Put both cells in one child (labels unchanged), or record a valid
  rule-5 exception. The `consumer` docstring ("isolation rule 4: one
  artifact, first load") does not fit this use.
  - review pass 1 fix: both inspection cells share one child (1 child, 1 configuration).
- [x] Extract the duplicated AOT cell helpers into shared test support
  (for example `aot-fixture`): the one-cell `consumer` (configuration +
  `:fixture-ns` + context `(pr-str [java jvm-opts policy])`) and the
  `produce` wrapper (`testing "producer"` + `compile-producer`) are copied
  in `public_field_aot_test`, `public_native_field_aot_test`,
  `public_mixed_field_aot_test`, `identity_aot_test`,
  `generated_value_aot_test`, `field_aot_test` (and `aot_test`). Keep labels
  unchanged.
  - review pass 1 fix: `aot-fixture/consumer-child` and `aot-fixture/produce`; labels unchanged.
- [x] Expose the harness contracts that tests rebuild: the cell namespace
  name `cell.<index>` (hard-coded in `cell-harness/child-form`, rebuilt in
  `transfer_test`, `child_transfer_test`, `field_transfer_test`,
  `controlled_field_capability_test`) and the fallback warning prefix
  (copied in `cell_harness`, `consumer_aot_checks`, `loader_policy_test`,
  `cell_runner_test`). Add public `cell-harness` functions/constants and use
  them, so a change in the harness cannot drift from the tests.
  - review pass 1 fix: `cell-harness/cell-ns-name`, `warning-prefix`, `policy-rejection`, `fallback-rejection`.

## Implementation review (pass 2)

- [x] Remove the unused `aot-fixture/printed`
  (`test/compatibility/value_type/aot_fixture.clj`). Pass 12 converted its
  last callers; no code calls it now.
  - review pass 2 fix: removed.
- [x] Correct the remaining isolation-rule citations. Children that differ
  by policy or JVM options are separate under rules 1 and 2; they are not a
  rule-4 exception:
  - `aot_test.clj` `fallback-aot-compilation-test` ("One-cell compile
    children (rule 4)"): the compile cells load no artifact; each policy is
    its own configuration (rule 2).
  - `controlled_field_capability_test.clj`
    `controlled-native-absence-aot-test` and
    `controlled-native-absence-java-aot-test` ("The saved class loads once
    for each JVM: one-cell children (rule 4)"): one child for each policy ×
    execution (rules 1, 2), one artifact (rule 4).
  - `aot-fixture/consumer-child` docstring ("isolation rule 4: one
    artifact, first load"): rule 4 is the artifact rule; first load is
    rule 5.
  - review pass 2 fix: comments and docstring corrected.
- [x] Give each AOT cell child its `:fixture-ns` (design Scope 2: the
  child-stderr context names the `fixture-ns` of the artifact's AOT compile
  child). Missing today: `aot-test/consumer` passes `nil` to
  `consumer-child` (use `'fallback.aot-fixture`), and the
  `controlled-field-capability-test` AOT and Java AOT cells
  (use `'checked.controlled`). Record the changed support labels.
  - review pass 2 fix: `aot-test/consumer` takes `fixture-ns`; controlled AOT cells set `'checked.controlled`; labels in implementation.md.
- [x] Correct the warn-guard text in `doc/development/runtime.md` ("Child
  JVMs"): the matching error cell is in the same test namespace, not the
  same test (for example, `transfer-test` constrained-warning cells match
  `constrained-concurrent-strict-test`), and it gives the guard evidence
  for the form and mode of the warn cell (design "Warn-guard evidence").
  - review pass 2 fix: `runtime.md` "Child JVMs" corrected.

## Implementation review (pass 3)

- [x] State the Error form in `loader-policy-test` `strict-rejection?`
  (`test/cross-jvm/value_type/loader_policy_test.clj`). Design Scope 3:
  each expected error result states its form as root class and message. The
  predicate checks only the root class and the message prefix, and accepts
  any cause chain. Compare the full `:chain` (the macroexpansion wrapper, if
  present, then `java.lang.Error`). On the baseline children, compare the
  exact message (`warning-prefix` + `java/lang/classfile/ClassFile`), as
  the matching warn cells do; keep the prefix check only for preview-off,
  whose context holds a random probe class name. Record the changed
  expectation in `implementation.md`.
  - review pass 3 fix: `strict-rejection` checks chain, root class, exact message (baseline).
- [x] Update `mementum/state.md`: the "compatibility suite is slow ...
  each matrix cell starts a child JVM. Task 018 addresses this" entry is
  stale. State the cell model (cells share child JVMs under the isolation
  rules in `doc/development/runtime.md`), the measured total (about 11
  minutes for the four commands at limit 4), and add the `:cross-jvm-test`
  suite (`test/cross-jvm/`) to the suite list.
  - review pass 3 fix: done.

## Implementation review (pass 4)

- [x] `field-transfer-test` exceeds the Scope 1 producer bound.
  `unsupported-public-transfer-test` and `supported-public-transfer-test`
  each start 2 producers (one for each tag `Long`, `String`) with one
  producer configuration (`after-record/cross-jvm.edn`: `:producer`
  count 2, configurations 1). Rule 3 does not need a second producer. Make
  one producer child for each test that gives the expansions of both tags
  (for example, `field-transfer-checks/produce` takes `[tag spec]`
  entries), and run it once. Keep the labels. Correct the pass-19 note in
  `implementation.md`: it compared producer counts with the first record,
  not with the distinct producer configurations.
  - review pass 4 fix: `produce` takes `[tag namespace-name expand]`
    specs; one producer for each test (8 → 6); labels unchanged.
- [x] Use one function for the thrown-failure map
  `{:class :message :chain}` (root class, root message, cause-chain class
  names). Three copies exist: the `cell-harness/child-form` catch,
  `identity-loader-checks/root-chain`, and the `cell-runner-test`
  `fallback-cell-form` catch. Put it in `value-type.cell-registry` (children
  load it; `clojure.core` and Java 8 only) and call it from the three
  places. Keep the result form unchanged.
  - review pass 4 fix: `cell-registry/thrown`; three copies removed.

## Implementation review (pass 5)

- [x] State the Error form (root class and message) in the remaining strict
  expectations (design Scope 3: "Each expected result states its form as
  root class and message"; pass 3 fixed only `loader-policy-test`). These
  expect a cause chain only, with no root message:
  - `runtime-test`: `public-definition-test` and
    `fallback-expansion-policy-test` (`rejected-chain`);
  - `transfer-test/transferred-expansion-test` (error cells);
  - `aot-test`: `fallback-aot-compilation-test` (strict compile),
    `source-free-fallback-aot-loading-test` (initial `java.lang.Error` and
    the `NoClassDefFoundError` retry), `source-free-runtime-only-fallback-test`
    (strict calls).
  Keep each chain. Give the exact message where the context is fixed
  (`fixed fallback representation`, Java 8 `java/lang/classfile/ClassFile`,
  the retry message), else the `warning-prefix`. Make the checks return
  `cell-registry/thrown` and remove the copied cause-chain loops
  (`aot-checks/cause-chain`, `transfer-checks/attempt`,
  `expansion-checks/attempt`, the `runtime-test` `public-definition-test`
  and `invalid-policy-test` cell forms). Record the changed expectations in
  `implementation.md`.
  - review pass 5 fix: `cell-harness/rejection?` and `rejections`; loops
    removed; expectations in implementation.md.
- [x] Retake the after record at the final commit. `after-record/*.edn`
  (taken at `4701304`) predates review passes 1–4, which changed child
  counts (`field-transfer-test` producers 8 → 6,
  `public-mixed-field-aot-test` inspection 2 → 1) and support labels
  (`:fixture-ns`, `"child"` context). The stored record still shows the
  bound violations that pass 4 fixed. Run the four commands at limit 4 and
  `:cross-jvm-test` and Java 8 `:fallback-test` at limit 1; replace
  `after-record/*.edn`; check acceptance 2 (bound, label mapping, total
  time) and 3 (equal `:assertions` label vectors). Record the label-mapping
  check (the "prefix mapping" of pass 23) in `implementation.md` or as a
  committed script, so a later record can repeat it.
  - review pass 5 fix: record at `b03b1fb`; `check_record.clj`; results in
    implementation.md.

## Implementation review (pass 6)

- [x] Use one root-cause function. `cell-registry/thrown` is the shared
  failure map (pass 4), but three root loops remain:
  `consumer-aot-checks/failure` (`{:class :message}` of the root),
  `identity-receiving-checks/root-failure` (added by this task: it calls
  `checks/failure`, then walks the chain again for the root `ex-data`) and
  `expansion-checks/root`. Add `cell-registry/root` (root `Throwable`), use
  it in `thrown`, make `consumer-aot-checks/failure` return
  `(select-keys (registry/thrown e) [:class :message])`, and use
  `registry/root` in `root-failure` and in place of `expansion-checks/root`.
  Keep the result forms unchanged.
  - review pass 6 fix: `cell-registry/root`; three loops removed.
- [x] Expose the Java 8 source-definition fallback context in
  `cell-harness`, as pass 1 did for `warning-prefix`. The literal
  `"java/lang/classfile/ClassFile"` is copied in `aot_test.clj`,
  `runtime_test.clj`, `cell_runner_test.clj` and `loader_policy_test.clj`
  (twice), and the rule "Java 8 detail, else `:any`" is written twice
  (`aot-test/fallback-aot-compilation-test` inline,
  `runtime-test/rejection-context`). Add one public constant and one
  function of the configuration (`baseline?` → detail, else `:any`) and use
  them in these places. Labels and expectations unchanged.
  - review pass 6 fix: `cell-harness/unavailable-detail`,
    `cell-harness/rejection-context`.

## Implementation review (pass 7)

- [x] Use one producer-child helper. The run (`run-jvm` with `:kind
  :producer`), the support assertions under `testing "producer"` (exit 0,
  empty `:err`) and the return value (`:result` on exit 0, else nil) are
  copied in `transfer_test.clj`, `child_transfer_test.clj` and
  `field_transfer_test.clj` (`produce`). The `check-form` wrapper
  (`(do (require 'ns) (ns/check & args))`) is copied in the same three
  files (and as `checks-form` in `aot_test.clj`). Add one public function
  for each in shared test support (for example `cell-harness`), as pass 1
  did for `aot-fixture/produce`, and use them. Also use the producer helper
  in `controlled_field_capability_test.clj`
  `controlled-native-absence-receiving-test`: its producer assertions are
  the only ones not under the support label `"producer"`; record the
  changed support labels in `implementation.md`.
  - review pass 7 fix: `cell-harness/produce`, `cell-harness/check-form`;
    support label in implementation.md.

## Implementation review (pass 8)

- [x] Use `cell-harness/check-form` in the remaining cell forms that only
  require a check namespace and call one function in it (pass 7 converted
  only the transfer and AOT tests). Copies:
  - `array_matrix_test.clj` private `check-form` (the `resolve` is not
    necessary: `load-string` evaluates a top-level `do` form by form);
  - `array_sequence_boundary_test.clj` cell `:form`;
  - `identity_receiving_cross_jvm_test.clj` `check-cell` `:form`;
  - `loader_policy_test.clj` `case-form`;
  - `public_field_aot_test.clj` and `public_mixed_field_aot_test.clj`
    inspection `:form`;
  - `runtime_test.clj` `check-expansions` and
    `constrained-discarded-with-loader` cell forms (alias require).
  Keep forms that evaluate more than one call, and the forms in
  child-loaded namespaces (`consumer-aot-checks`, `function-aot-checks`),
  as they are. Labels and expectations unchanged.
  - review pass 8 fix: all listed copies use `check-form`; local
    `array-matrix-test/check-form` removed.
- [x] Add a check that fails when the constrained transfer form loses its
  field constraint. Pass 7 found that `transfer-test`
  `constrained-concurrent-warning-scope-test` and
  `constrained-concurrent-strict-test` passed from pass 16 to pass 7 with
  an unconstrained expansion (`^Long ^:non-nil` not printed): their
  expected results are equal for both forms. For example, in
  `warning-checks/define-types`, also call the factory with `nil` and
  expect the non-nil rejection, or assert under the support label
  `"producer"` that each expansion holds the constraint. Record the new
  expectation in `implementation.md`.
  - review pass 8 fix: `warning-checks/define-types` gives
    `:nil-construction`; constrained expects the NPE class.

## Implementation review (pass 9)

- [x] Use `cell-harness/check-form` in `field_capability_test.clj`
  `policy-cell`. It builds `(do (require 'value-type.field-policy-checks)
  form)` around one call (`expansion`, `evaluation`); pass 8 did not list
  it. Make `policy-cell` take the check symbol and its arguments. Labels
  and expectations unchanged.
  - review pass 9 fix: `policy-cell` takes `check & args`.
- [x] Remove the `resolve` calls in `array_info_boundary_test.clj`
  `control-form` (`array-info-observation/observe`, `array/array-info`) and
  `export-only-form` (`array/array-info`, `array/make-array`). Each call is
  in a later form of a top-level `do`, after its `require` (each
  `require-order` holds `value-type.array`), as pass 8 found for
  `array-matrix-test`. Correct the pass-6 note in `implementation.md`
  ("the cell compiles before its `require`"). Labels and expectations
  unchanged.
  - review pass 9 fix: `resolve` removed; pass-6 note corrected.

## Implementation review (pass 10)

- [x] Use one javac support helper. The block `(testing "javac" (is (= 0
  (:exit c))) (is (= 0 (:result c))) (= 0 (:result c)))` around
  `aot-fixture/compile-java` is copied in `field_aot_test.clj`,
  `generated_value_aot_test.clj`, `identity_aot_test.clj`,
  `public_mixed_field_aot_test.clj`, `public_native_field_aot_test.clj`,
  and as the private `compile-java` in `public_field_aot_test.clj`. Add one
  public function in `aot-fixture` (beside `produce`), and use it in these
  places. Labels unchanged.
  - review pass 10 fix: `aot-fixture/javac`; six copies removed.
- [x] Put the `controlled-field-capability-test` AOT support assertions
  under the support labels of the other AOT tests.
  `controlled-native-absence-aot-test` and
  `controlled-native-absence-java-aot-test` assert the `compile-controlled`
  compile (exit, `:err`, `:compiled`, source deletion) and the
  `ControlledJavaConsumer` javac (exit, result) under the test label `[]`.
  The other AOT tests use `"producer"` and `"javac"` (pass 4, pass 7 for the
  receiving producer). Use `"producer"` for the compile and the javac
  helper of the step above. Record the changed support labels in
  `implementation.md`.
  - review pass 10 fix: compile under `"producer"`, javac via
    `aot-fixture/javac`; labels in implementation.md.
- [x] Retake the after record after the last code change of the task.
  `after-record/*.edn` is from `b03b1fb`; review passes 6–9 changed test
  code, and pass 7 changed a support label, so the record and the limit-1
  label comparison do not show the final code. The pass-5 note "later
  commits change task files only" is no longer correct. Run the four
  commands at limit 4, and `:cross-jvm-test` and Java 8 `:fallback-test`
  at limit 1; run `check_record.clj`; replace `after-record/*.edn`; record
  the commit, times and findings in `implementation.md`.
  - review pass 10 fix: record at `e5ed659`; results in implementation.md.

## Implementation review (pass 11)

- [x] Run the independent children of
  `aot-test/source-free-fallback-aot-loading-test` together (plan "Runner":
  compile children in one `run-jvms` call, then cell children). Today the
  `silent` and `warn` producers run one after the other, each in its own
  `with-fixture`, each followed by its own `run-cells` of 3 consumers: 4
  serial rounds. It is the only test with more than one compile child, and
  the two producers do not depend on each other. Open both fixtures, make
  the local `produce` return the option map, run both producers in one
  `run-jvms` call (support assertions under `"producer"` after the call),
  then run the 6 consumer children in one `run-cells` call. Labels, child
  counts and expectations unchanged; record the namespace time.
  - review pass 11 fix: `15bf0ab`; 2 producers in one `run-jvms`, 6
    consumers in one `run-cells`; times in implementation.md.

## Implementation test review (pass 1)

- [x] Remove the check-order dependency in
  `test/cross-jvm/value_type/cell_runner_test.clj`
  `failing-cell-outcome-test`. It reads the `:result` check as
  `(first (:checks b))`. The order of checks in a `cell-outcomes` outcome
  is an implementation decision, not part of the plan contract ("Cell
  outcomes"). Select the check by `:check :result` (for example
  `(some #(when (= :result (:check %)) %) (:checks b))`). Labels and
  expectations unchanged.
  - test review pass 1 fix: `:result` check selected by `:check`.

## Implementation test review (pass 2)

- [x] Make a failing predicate cell report a readable expected value.
  `cell-harness/report-check` reports the cell `:expected` as is. When it
  is a predicate (`harness/rejections`; the inline `fn` in
  `runtime-test` `capability-test` and lines 225 and 251), a failure shows
  `:expected #object[...]`. For `rejections`, the expected data map and
  the rejection context are hidden in the closure. Give each predicate a
  data description (for example `rejections` returns a fn with
  `{:expected expected :chain chain :context context}` metadata, and each
  inline predicate gets a short description), and make `report-check`
  report this description as `:expected` for a predicate. Pass/fail
  results, labels and expectations unchanged.
  - test review pass 2 fix: `cell-harness/described`; `report-check`
    reports the description.

## Implementation test review (pass 3)

- [x] Add a permanent runner-outcome test for an unreadable cell result.
  `plan.md` "Runner" and "Cell outcomes" give a separate path for a `:cell`
  child that exits 0 with a last stdout line that does not read as EDN:
  `run-jvms` returns `:read-error` and no `:result` (no throw), and each
  cell of the child fails with one `:result` check `:actual :no-result` and
  a `:failure` with `:exit` 0, the process `:out` and `:err`, and
  `:read-error`. The Invariants require it ("A child that does not return a
  result for a cell fails that cell"). Only a one-time REPL check covers it
  (`steps.md` Slice 2). The crash and timeout tests in
  `cell-runner-test` do not reach this branch (exit not 0). Add a test in
  the form of `crashing-child-outcome-test`: two fixture cells, one returns
  a value that prints as `#object[...]` (for example `(Object.)`); assert
  `:exit` 0 and a string `:read-error` on the `run-jvms` result, and for
  each outcome `:pass? false`, `[[:result :no-result]]` checks and a
  `:failure` with `:exit` 0 and `:read-error`. New labels; no other label
  or expectation changes.
  - test review pass 3 fix: `cell-runner-test/unreadable-result-outcome-test`.

## Implementation test review (pass 5)

- [x] Add a fast in-process test of the failure side of
  `cell-harness/cell-outcomes` (a pure function; no child JVM). All cell
  assertions of the matrix depend on its verdict, but the permanent tests
  show only a failing `:result` check (`failing-cell-outcome-test`) and
  `:no-result`. No test shows that the `:out` or `:err` check can fail, so
  a regression to "always pass" in `err-matches?` or the `:out` check is
  not found. Use `cell-child` maps and hand-made run maps
  (`{:exit 0 :out "" :err "" :result [...]}`). Show, for each case, the
  failing check and `:pass? false`:
  - a `warn` child: the first `:fallback?` cell with empty `*err*`, with
    two warning lines, and with a warning on a later cell; the `:any`
    warning with text that does not start with `warning-prefix`;
  - an `error` or `silent` cell with non-empty `*err*`; a cell with
    non-empty `*out*`;
  - an expected `{:thrown {...}}` with a different `:message`, and with a
    `:value` entry; a predicate that throws;
  - a one-cell child with exit not 0 and an entry (the `:exit` check).
  Also show that `rejection?` with `:any` is false for a message without
  `warning-prefix` and for a different chain. Put the test in
  `test/cross-jvm/` (runs once); new labels only.
  - test review pass 5 fix: `cell-outcomes-test`; the `:exit` check cannot
    fail (exit not 0 gives `:no-result`, plan "Cell outcomes"); see
    implementation.md.
- [x] Add a fast in-process test of the `cell-registry` checks that prove
  isolation. No test shows a false result: `new-class!` is false for a
  Class that an earlier call got (and for a non-Class); `absent?` is false
  when the import, the factory or the class name is present (one case for
  each part); `defined` gives `:factory? false` when the factory gives an
  instance of another Class. If `new-class!` always returned true, grouped
  cells would lose the new-Class proof and all tests would still pass. Use
  a fresh namespace and `deftype`/`defrecord` in the test JVM; new labels
  only.
  - test review pass 5 fix: `cell-registry-test`.

## Implementation test review (pass 6)

- [x] Remove the one-cell `:exit` check from `cell-harness/cell-outcomes`.
  It cannot fail: a cell has its `:result`/`:out`/`:err` checks only when
  the child gives an entry, which needs exit 0; exit not 0 gives one
  `:no-result` check, and its `:failure` shows the exit (pass 5 deviation).
  Each one-cell cell gets an always-pass report with no signal, and
  `cell-outcomes-test/one-cell-exit-test` "exit 0" pins it. Remove the
  check and that case (keep "exit not 0 with an entry"), and remove the
  one-cell `:exit` check text from `plan.md` "Cell outcomes", "Cell-result
  function" ("for a one-cell child also the exit code") and "Conversion
  order" ("cell exit check"); the `:no-result` rule states the exit
  contract. Labels unchanged; record the assertion-count change in
  `implementation.md`.
  - test review pass 6 fix: check, test case and plan text removed;
    counts in implementation.md.
- [x] Cover the import part of `cell-registry/defined` `:factory?`.
  `defined-test` shows `:factory? false` only for a factory that gives
  another Class; if the `(identical? c (ns-resolve n simple-name))` part
  were lost, no test fails. Add a case: define `Sample` twice in one fresh
  namespace (the import and `->Sample` now give the second Class), and
  expect `(registry/defined n 'Sample first-class 1)` to give
  `:factory? false`. New labels only.
  - test review pass 6 fix: `defined-test` "import gives another class"
    (factory of the first class kept; see implementation.md).

## Implementation test review (pass 7)

- [x] Make each predicate `:expected` give a readable failure, and enforce
  it. Pass 2 described only the `runtime-test` predicates; a failure of
  these other predicates still reports `:expected #object[...]`:
  `array-matrix-test/check-expected`,
  `array-sequence-boundary-test/expected`,
  `array-info-boundary-test/control-expected`,
  `array-boundary-test` `(partial unavailable? ...)`,
  `loader-policy-test/strict-rejection`, `persisted-output?` in
  `field-aot-test`, `public-field-aot-test` and
  `public-mixed-field-aot-test`, `field-transfer-test` `unavailable` and
  `attempts-of`, and the `field-capability-test` predicate of
  `policy-cell` callers. Give each a data description with
  `harness/described`. Then make `cell-child` throw when a cell
  `:expected` is a fn without a description, so a new predicate cannot
  hide its expectation. Pass/fail results, labels and expectations
  unchanged.
  - test review pass 7 fix: all listed predicates and
    `public-field-aot-test/strict-loads?`,
    `identity-receiving-cross-jvm-test` (2), `aot-test` retry described;
    `cell-child` throws for an undescribed fn.
- [x] Complete the in-process `:no-result` cases of
  `cell-outcomes-test`. Plan "Cell outcomes": exit 0 with a result that is
  not a vector, or shorter than the cell count, gives the cell with no
  entry one `:result` check `:actual :no-result` and a `:failure` with the
  process `:out` and `:err`; no test shows it (a regression to `(get nil
  index)` handling or to a pass for a missing entry is not found). Rename
  `one-cell-exit-test` (its `:exit` check is gone since pass 6; it now
  tests the no-result rule), and add: two cells with a result of one
  entry (first cell passes, second has `:no-result`), and a result that
  is not a vector. Also add to `rejection-test` a false case of the exact
  context branch of `rejection?` (other message), beside the `:any`
  cases. New labels only.
  - test review pass 7 fix: `no-result-test`; exact-context cases in
    `rejection-test`.
- [x] Add an in-process test of `cell-harness/assert-child` report
  translation. All matrix cell assertions pass through `report-check`;
  `cell-outcomes-test` shows only the outcome data, so a regression in
  `report-check` (for example `:type :pass` for a failing check, a lost
  `:message`, or the description not used as `:expected`) or in the
  "child" support assertions is not found. Bind `clojure.test/report` to
  collect reports, call `assert-child` with a `cell-child` map and a
  hand-made run map, and assert: one `:fail` report with the `:failure`
  text in `:message` for a failing check; a described predicate reports
  its description as `:expected`; a two-cell child with a one-entry
  result gives a failing "child" result-count report. New labels only.
  - test review pass 7 fix: `cell-outcomes-test/assert-child-report-test`
    and `undescribed-predicate-test`.

## Implementation test review (pass 8)

- [x] Remove the `is` source forms from
  `cell-outcomes-test/assert-child-report-test` "child result count". It
  expects `:expected '(= (count cells) (when (vector? result) (count
  result)))` and `:actual '(not (= 2 1))`: the source text of the
  `assert-child` assertion, an implementation detail. A change of that
  expression (same behavior) fails the test. Assert the observable
  contract: one `:fail` report under the context
  `(pr-str ["child" "java" [] "silent" nil])`, and the `:no-result` fail of
  "report count 1". Labels unchanged.
  - test review pass 8 fix: asserts fail contexts and the `:no-result` fail.
- [x] Add a permanent test of the producer read rule (plan "Runner": a
  producer or compile child with an unreadable last line throws an
  `ex-info` with child index, `:exit`, `:out`, `:err` and read error, after
  all children of the call return). Only a one-time REPL check covers it
  (Slice 2); `unreadable-result-outcome-test` covers the `:cell` side only.
  If the throw were lost, the `:producer` result would have no `:result`
  and producer tests would fail later with no read error. In
  `cell-runner-test`, call `run-jvms` with a `:producer` child whose form
  gives `(Object.)` after a `:cell` child, and assert the `ex-data`:
  `:index 1`, `:exit 0`, a string `:read-error`. New labels only.
  - test review pass 8 fix: `cell-runner-test/unreadable-producer-result-test`.
- [x] Test the runner rules that are pure functions in-process
  (`test/cross-jvm/`, no child JVM):
  - limit (plan "Runner"): `read-limit` gives 4 for nil and the value for
    `"1"`, `"12"`; throws for `"0"`, `"-1"`, `"4x"`, `""`, with the
    variable name and the value in the message. Only manual checks cover
    it (Slice 2).
  - timeout: `timed-out-child-outcome-test` passes when the
    10000 × (`:cell-count` − 1) part is lost (the child sleeps 60 s, past
    both 2 s and 12 s). Extract the wait time of `execute` into a pure
    function of the option map and show: default 120000 for one cell,
    `:timeout-ms` for one cell, `:timeout-ms` + 20000 for 3 cells.
  New labels only.
  - test review pass 8 fix: public `jvm-runner/read-limit`,
    `jvm-runner/wait-ms`; `value-type.jvm-runner-test`.
- [x] Use one notation for predicate descriptions and state it in the
  `cell-harness/described` docstring. The descriptions use several forms
  for the same idea: `'<detail>` and `:any`/`:present` for a runtime
  detail; `[x :+]` (`public-field-aot-test`, `public-mixed-field-aot-test`),
  `[:* x :*]` (`aot-test` retry) and `{:count [2 2] :each ...}`
  (`field-transfer-test/unavailable`) for repeated or contained items;
  glob strings (`"1.8.*"`, `array-info-boundary-test`). A reader of a
  failure report must guess each form. Pick one symbol for "runtime
  detail" and one form each for "contains x" and "n of x", and align the
  outliers. Pass/fail results and labels unchanged.
  - test review pass 8 fix: notation in the `described` docstring;
    `harness/rejection-description`; outliers aligned.

## Implementation test review (pass 9)

- [x] Reject a `VALUE_TYPE_TEST_JVM_LIMIT` value above the `int` range in
  `jvm-runner/read-limit`, and test it. Design "Parallel children": a
  value that is not a positive decimal integer limit is invalid, and the
  load throws an exception whose message names the variable and the
  value. Today `"99999999999999999999"` passes the regex, then
  `Long/parseLong` throws `NumberFormatException` (no variable name), and
  `"2147483648"` passes `read-limit`, then `(int limit)` throws
  `integer overflow` at the `Semaphore`. Make `read-limit` throw the same
  `ex-info` for a value above `Integer/MAX_VALUE`, and add both values to
  the invalid cases of `jvm-runner-test/read-limit-test` (and
  `"2147483647"` to the valid cases). New labels only.
  - test review pass 9 fix: `read-limit` parses with `bigint`, rejects
    above `Integer/MAX_VALUE`; cases added.
- [x] Add to `cell-outcomes-test/warn-err-check-test` a `warn` child whose
  first cell is not a fallback cell (cells `[{} {:fallback? true :warning
  "w"}]`). Design and plan "Cell outcomes": the warning goes to the first
  `:fallback?` cell, not the first cell. Each case today puts the fallback
  cell at index 0, so a regression in `expected-err` to "warn on cell 0"
  is not found in-process. Show: warning on cell 1 passes both cells;
  warning on cell 0 fails the `:err` check of cell 0 and of cell 1. New
  labels only.
  - test review pass 9 fix: two cases added.

## Implementation test review (pass 10)

- [x] Test that `cell-harness/cell-child` gives the runner its wait time.
  `jvm-runner-test/wait-ms-test` covers `wait-ms`, but no test shows that
  `cell-child` sets `:cell-count` (cell count) and keeps `:timeout-ms`.
  `timed-out-child-outcome-test` still passes when `:cell-count` is lost
  (wait 2 s, not 12 s) and, when `:timeout-ms` is lost, only runs 130 s in
  place of 12 s. In `cell-outcomes-test` (in-process), assert
  `(runner/wait-ms (harness/cell-child (assoc configuration :timeout-ms
  2000) <3 cells>))` is 22000, and is 120000 for one cell with no
  `:timeout-ms`. New labels only.
  - test review pass 10 fix: `cell-outcomes-test/cell-child-wait-test`.
- [x] Add an in-process test of `label-record/summary`. It is the
  measurement of acceptance 2 (child-count bound, label mapping, time) and
  `check_record.clj` repeats it, but no test covers it; pass 4 found a bound
  violation only from its output. Write a hand-made record file (temporary
  directory) and assert: `:children` count and distinct `:configurations`
  for each `[test kind]` (two starts with one configuration, and two with
  different configurations); `:ms` is the sum of the Var times of the
  namespace; method `:cells` gives the `cell!` count of a label and 0 for
  a label with no `cell!`; method `:start-labels` gives the cell to the
  start label, or to each nested non-start label with assertion events
  between that start and the next. Put it in `test/cross-jvm/`; new labels
  only.
  - test review pass 10 fix: `value-type.label-record-test`.

## Implementation test review (pass 11)

- [x] Add a permanent test of the cell isolation contract of
  `cell-harness/child-form` (plan "Cell harness": for each cell a fresh
  namespace `cell.<index>`, a new `Compiler/LOADER` whose parent is the
  top-level `LOADER` value, the `clojure.main/with-bindings` Vars at their
  cell-start values, own `*out*`/`*err*`). All grouping rests on it, but
  only a one-time REPL check covers it (Slice 3). If cells shared a
  namespace, a `LOADER` or a binding frame, the matrix tests still pass:
  a repeated `deftype` in one namespace still gives a new Class. In
  `cell-runner-test`, run one baseline silent child of two cells and
  assert on the returned entries: cell 0 does `(def leaked 1)` and
  `(set! *warn-on-reflection* true)`; each cell returns `(str *ns*)`, the
  identity hash of the `LOADER` value and of its parent; cell 1 also
  returns `(resolve 'leaked)` and `*warn-on-reflection*`. Expect the
  namespaces `(harness/cell-ns-name 0)` and `(harness/cell-ns-name 1)`,
  two different loaders with the same parent, `nil` and `false`. New
  labels only.
  - test review pass 11 fix: `cell-runner-test/cell-isolation-test`
    (one form in both cells; see implementation.md).
- [x] Cover the default class name of `cell-registry/absent?` (2-arity:
  munged namespace name, `.` and simple name). `absent-test` shows the
  class-name part as false only with an explicit class name; the 2-arity
  is used only in "nothing defined". If the default lost
  `namespace-munge`, the class-name part would be true for each
  namespace with `-` (all cell-check namespaces with a prefix suffix and
  the `fresh-ns` names), and a defined class would count as absent. Add a
  case: `deftype Sample` in a `fresh-ns` (its name holds `-`), `ns-unmap`
  `Sample` and `->Sample`, expect `(registry/absent? n 'Sample)` false
  (checked in a `clojure.main` JVM: false; the unmunged name gives true).
  Confirm the case passes under the `:cross-jvm-test` runner. New labels
  only.
  - test review pass 11 fix: `absent-test` case binds `LOADER` (see
    implementation.md); unmunged-name contrast added.
- [x] Give a fast command for changes to the test support. The in-process
  tests of the harness (`cell-outcomes-test`, `cell-registry-test`,
  `jvm-runner-test`, `label-record-test`) take seconds, but the only
  documented command that runs them is the full `:cross-jvm-test` suite
  (about 5 minutes). In `doc/development/runtime.md` "Child JVMs", add one
  `:cross-jvm-test` command with `--namespace` for these four namespaces
  (and state that `cell-runner-test` adds the child-JVM runner cases).
  Run `bb vale`.
  - test review pass 11 fix: command in "Child JVMs" (6 s); vale clean.

## Implementation test review (pass 12)

- [x] Align the remaining outlier descriptions with the notation of
  `cell-harness/described` (pass 8). A failure report must show data that
  a reader can compare with the actual result:
  - `field-transfer-test/unavailable` and
    `identity-receiving-cross-jvm-test` `incompatible-value-receiving-test`
    give `{:unavailable-failure x :absent? true :message {:unavailable x}}`.
    The result has no key `:unavailable-failure`, and `{:unavailable x}` is
    not a documented form. Add one description function beside
    `consumer-aot-checks/unavailable-failure`: the failure map with
    `<detail>` for `:reason`, `:absent? true`, and `:message`
    `(contains x "fixed value representation is unavailable"
    "--enable-preview")` (the names of `unavailable-message?`). Use it in
    both tests.
  - `array-sequence-boundary-test/expected` gives `:definitions :absent`
    for a rejected row, but the predicate requires no `:definitions` key.
    Remove the key from the description.
  Predicates, pass/fail results and labels unchanged.
  - test review pass 12 fix: `consumer-aot-checks/unavailable-description`;
    `:definitions :absent` removed.
- [x] Write `function-redefinition-test/repeated-definition-cell` as one
  quoted form. This task replaced the quoted child program with nested
  `list` calls that build only fixed code; the `DECLARATION` placeholder
  needs no `list`, because `postwalk-replace` also replaces it inside
  `(eval (list 'fn [] 'DECLARATION))`. Check in the REPL that `cell-form`
  gives the same form for both declarations before and after. Labels and
  expectations unchanged.
  - test review pass 12 fix: one quoted form; `cell-form` output equal.
- [x] Add the `:any` warning cases to
  `cell-outcomes-test/warn-err-check-test`: one warning line passes, two
  warning lines fail the `:err` check. The `re-matches` of `err-matches?`
  is the only warn-once proof of the preview-off warn cells (context with a
  random probe class name); today only the fixed-text warning has a
  two-line case, so a change to `re-find` is not found. New labels only.
  - test review pass 12 fix: two cases added.

## Implementation docs review (pass 1)

- [x] Add the cross-JVM tests to the suite list in
  `doc/development/release.md` ("Both modes run quality, tooling, release,
  value-path, fallback and isolated JAR consumer tests", line 45). Slice 7
  changed `release.yml` and `ramora/release-workflow.md`, but this user
  guide still omits the `:cross-jvm-test` release step. Run `bb vale`.
  - docs review pass 1 fix: "fallback, cross-JVM and isolated JAR".
- [x] Make the owner-parent command in `doc/development/runtime.md`
  "Verified fallback matrix" agree with its text. The block gives
  `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test`, and the
  next paragraph says to set the variable only "when they are not at the
  local defaults". When the variable is not set, `JAVA_CMD` is empty and
  the `clojure` script uses `java` on `PATH`, not Java 8, while
  `jvm-config` uses its local default. Use `<java8-home>/bin/java`, as the
  three `:fallback-test` lines do, or state that this command and the
  focused `:cross-jvm-test` commands need the variable set.
  - docs review pass 1 fix: `<java8-home>/bin/java`; focused commands
    need the variable set.
- [x] State the upper bound of `VALUE_TYPE_TEST_JVM_LIMIT` in
  `doc/development/runtime.md` "Child JVMs". The text says "a positive
  decimal integer"; `jvm-runner/read-limit` also rejects values above
  `Integer/MAX_VALUE` (2147483647, test review pass 9). Also state the
  Scry-visible result of an invalid value (design Scope 4: runner error,
  no test runs, exit 1).
  - docs review pass 1 fix: bound and Scry result stated; checked with
    limit `0` (runner error, exit 1).

## Code-shaper review (pass 1)

- [x] Add one harness function for the producer specs of cells. The rule
  "cell i runs in namespace `cell.i`, which is the producer namespace of
  its expansion" is written as `(map-indexed (fn [i x] [(harness/cell-ns-name
  i) x]) ...)` in `transfer_test.clj` (`specs`), `child_transfer_test.clj`
  (`produce`), `controlled_field_capability_test.clj`
  (`controlled-expansions`) and `field_transfer_test.clj` (`each-expansion`
  and three inline copies). Add a public `cell-harness` function (for
  example `cell-specs`: `[(cell-ns-name i) x]` for each item x) beside
  `cell-ns-name`, and use it in these places. In `field_transfer_test.clj`
  also use one helper for the two expansions of cell i (`(subvec expansions
  (* 2 i) (* 2 (inc i)))`, three copies), and one local `produce` (as in
  the other transfer tests) in place of the deeply indented
  `(harness/produce runtime (harness/check-form ...))` calls. Labels and
  expectations unchanged.
  - code-shaper pass 1 fix: `cell-harness/cell-specs`; field-transfer
    `cell-expansions` and local `produce`.
- [x] Expose the cause chain of a strict rejection in source definition in
  `cell-harness`, beside `fallback-rejection`. The literal
  `["clojure.lang.Compiler$CompilerException" "java.lang.Error"]` is
  copied in `runtime_test.clj` (`rejected-chain`), `loader_policy_test.clj`
  (`rejection-chain`), `aot_test.clj` (`fallback-aot-compilation-test`) and
  `transfer_test.clj` (`transferred-expansion-test`). Add one public
  constant and use it in these places. Labels and expectations unchanged.
  - code-shaper pass 1 fix: `cell-harness/source-rejection-chain`.
- [x] Align the `aot_test.clj` producers with the shared producer idiom.
  `producer-child` builds its form as a string (`(str "(require ...) (prn
  (c/compile-fixture " ... "))")`); the other tests use
  `harness/check-form` (passes 7–8; `clojure.main -e` prints the value).
  The producer exit assertion is written twice: the local `produce` and
  inline in `source-free-fallback-aot-loading-test`. Build the form with
  `harness/check-form` and use one function for the producer support
  assertions of a run-jvm result. Labels and expectations unchanged.
  - code-shaper pass 1 fix: `producer-child` uses `check-form`;
    `aot-test/produced?`.

## Code-shaper review (pass 2)

- [x] Expose the fixed fallback context in `cell-harness`, as pass 6 did
  for `unavailable-detail`. `fallback-rejection` holds the literal
  `"fixed fallback representation"`, but the tests copy it as the cell
  `:warning`: `aot_test.clj` (`fallback-warning`), `transfer_test.clj`
  (`fallback-context`), `public_field_aot_test.clj` (`fallback-warning`),
  `child_transfer_test.clj`, `identity_receiving_cross_jvm_test.clj` and
  `field_transfer_test.clj` (twice). Add one public constant (for example
  `fallback-context`), define `fallback-rejection` with it, and use it in
  these places. Labels and expectations unchanged.
  - code-shaper pass 2 fix: `cell-harness/fallback-context`; 8 copies removed.
- [x] Use one function for the binary class name of a type in a namespace
  (munged namespace name, `.`, simple name). The rule is written in
  `cell-registry/absent?` (2-arity default),
  `identity-receiving-checks/class-name`, `warning-checks` (twice),
  `transfer-test/class-name` (uses `munge`, not `namespace-munge`) and
  `field-transfer-test/unsupported-cell` (no munge). Add a public
  `cell-registry/class-name` of a namespace name (or namespace) and a
  simple name (children load `cell-registry`), use it in `absent?` and in
  these places, and remove the local copies. Labels and expectations
  unchanged.
  - code-shaper pass 2 fix: `cell-registry/class-name`; also used in
    `identity-loader-checks`.
- [x] Give the `aot-test` producer helpers the contract of
  `harness/produce` and `aot-fixture/produce`: the support label
  `"producer"` inside the helper, and a nil return on a failing producer.
  Today `produced?` has no label (both callers wrap it in
  `testing "producer"`), and `produce` returns the run-jvm result for any
  exit, so `source-free-value-aot-loading-test` checks
  `(= 0 (:exit producer))` again. Put the label in `produced?`, make
  `produce` return the result only on exit 0, and use `when-let` in the
  caller. Labels and expectations unchanged.
  - code-shaper pass 2 fix: `produced?` holds `"producer"`; `produce`
    returns nil on failure; `when-let` caller.
- [x] Write `field-transfer-test/each-expansion` with `cell-ns-name`
  (`(for [i (range n) expand expands] [(harness/cell-ns-name i) expand])`).
  Pass 1 changed it to `(harness/cell-specs (range n))` and discards the
  items: `cell-specs` pairs a name with an item, and this use has no item.
  Labels unchanged.
  - code-shaper pass 2 fix: done.

## Code-shaper review (pass 3)

- [x] Add the preview-off runtime to `jvm-config/configuration` (for
  example key `:preview-off`: the `:valhalla` map with `:jvm-opts []`).
  The expression `(assoc valhalla :jvm-opts [])` is copied 23 times in 15
  test namespaces (for example `identity_aot_test/receivers`,
  `public_field_aot_test/receivers`, `field_transfer_test`,
  `child_transfer_test`, `identity_receiving_cross_jvm_test`), and
  `loader_policy_test` has a local `preview-off`. Use the new key in each
  place. Labels and expectations unchanged.
  - code-shaper pass 3 fix: `jvm-config/configuration` `:preview-off`; 23
    copies and the local `preview-off` removed.
- [x] Use one source for the policy and execution matrix axes. The literal
  `["silent" "warn" "error"]` is written 48 times in 19 namespaces
  (`aot-test` has a local `policies`), `[[] ["-Xint"]]` 19 times in 8
  namespaces, and the configuration of a cell child
  `(assoc (update runtime :jvm-opts into execution) :policy policy)` 16
  times (`field-transfer-test/configurations` is a local helper). Add
  public `policies` and `executions` constants and one function of
  runtime, execution options and policy (for example in `cell-harness` or
  `jvm-config`), and use them in these places. Keep literals that are a
  subset (for example `["silent" "warn"]`). Labels and expectations
  unchanged.
  - code-shaper pass 3 fix: `jvm-config/policies`, `jvm-config/executions`,
    `jvm-config/child-configuration`; local `aot-test/policies` removed.
- [x] Use `aot-fixture/produce` for each AOT producer compile. Two callers
  rebuild it as `(testing "producer" (fixture/compile-producer ...))`:
  `aot-test/source-free-runtime-only-fallback-test` and
  `controlled-field-capability-test/compile-controlled` (with `:wrap`).
  Make `produce` take the `compile-producer` options (`:wrap`, `:after`,
  `:kind`) and use it in both places. Labels and expectations unchanged.
  - code-shaper pass 3 fix: `produce` passes options to `compile-producer`;
    both callers use it.
- [x] Use `cell-registry/class-name` in the remaining copies of the binary
  class name rule (pass 2): `identity-loader-checks/reject`
  (`(str (ns-name receiver) ".Holder")`; the same file uses `class-name`
  at line 83), `identity-receiving-cross-jvm-test`
  (`(str cell-ns ".Holder")`, `(str cell-ns ".Child")`) and
  `identity-receiving-test` (five `(str (ns-name receiver) ".Holder")`).
  Labels and expectations unchanged.
  - code-shaper pass 3 fix: all listed copies use `registry/class-name`.

## Code-shaper review (pass 4)

- [x] Use `jvm-config/executions` for the execution axis of the AOT
  receivers. Pass 3 replaced the `[[] ["-Xint"]]` literals, but the same
  axis is still written in two other forms: `interpreter? [false true]`
  with `(cond-> runtime interpreter? (update :jvm-opts conj "-Xint"))`
  (`public_field_aot_test` `receivers` and 2 tests,
  `public_native_field_aot_test` 2, `public_mixed_field_aot_test` 2) and
  `[["--enable-preview"] ["--enable-preview" "-Xint"]]`
  (`generated_value_aot_test` and `public_mixed_field_aot_test`
  `value-options`, `public_native_field_aot_test` 2,
  `public_field_aot_test/value-receivers`). Add a public `jvm-config`
  function of runtime and execution options (`(update runtime :jvm-opts
  into execution)`), define `child-configuration` with it, and use it with
  `config/executions` in these places. The `:jvm-opts` vectors, labels and
  expectations stay unchanged.
  - code-shaper pass 4 fix: `jvm-config/with-execution`; all listed
    copies use it with `config/executions`.
- [x] Use one function for the incompatible AOT consumer child. Five tests
  build it from `consumer-child`, `incompatible-consumer-form` and
  `incompatible-result` with the context prefix
  `(str (:java receiver) " / " policy)`, in two idioms: `update-in
  [:cells 0 :context]` after the call (`generated_value_aot_test`,
  `public_field_aot_test`, `public_native_field_aot_test`,
  `field_aot_test`) and the `:context` option (`public_mixed_field_aot_test`).
  Add one public `aot-fixture` function (fixture-ns, receiver jvm,
  classpath, policy, consumer spec) that gives the prefixed default
  context, and use it in the five places. Labels and expectations
  unchanged.
  - code-shaper pass 4 fix: `aot-fixture/incompatible-consumer-child`;
    five copies removed.
- [x] Use `consumer-aot-checks/builders` (and `outcome`) for the
  direct, factory and reflective constructions. `child-transfer-checks`,
  `field-policy-checks` and `identity-receiving-checks` use it, but
  `field-transfer-checks` (`reflective`, `consume-supported`,
  `consume-mixed`) and the `controlled-field-capability-test`
  `holder-check` child form (inline `reflective` and `outcome`) rebuild
  it, with the `InvocationTargetException` unwrap copied. Each expected
  construction entry is equal, so the order change is not visible. Labels
  and expectations unchanged.
  - code-shaper pass 4 fix: `field-transfer-checks` and `holder-check`
    use `builders` and `outcome`; local `reflective` removed.
- [x] Build the `controlled-field-capability-test` AOT cells with
  `aot-fixture/consumer-child` (`:context [(pr-str [policy execution])]`)
  in place of `harness/cell-child` with `(assoc ... :classpath classpath
  :fixture-ns 'checked.controlled)` (2 copies), and join the nested
  `(when (compile-controlled ...) (when (fixture/javac ...) ...))` into
  one `(when (and ...))`, as the other AOT tests do. Labels unchanged.
  - code-shaper pass 4 fix: both AOT tests use `consumer-child` and one
    `when`.
- [x] State the `compile-clojure-child` option defaults in one place.
  `aot-fixture/compile-producer` repeats them (`(or kind :aot-compile)`,
  `(or wrap identity)`, `(or after "(prn :compiled)")`) only to avoid
  passing nil options. Pass only the options that are present (for
  example `(apply compile-clojure ... (mapcat identity (select-keys
  options [:wrap :after :kind])))`). Labels unchanged.
  - code-shaper pass 4 fix: `compile-producer` passes only present
    options.

## Code-shaper review (pass 5)

- [x] Use one source for the receiver runtime axis, as pass 3 did for the
  policy and execution axes. `[baseline preview-off valhalla]` (all
  receivers) and `[baseline preview-off]` (receivers without value
  capability) are written 17 times in 10 namespaces (for example
  `public-field-aot-test/receivers`, `identity-aot-test/receivers`,
  `child-transfer-test`, `field-transfer-test`,
  `identity-receiving-cross-jvm-test`, `capture-loading-test`, the five
  incompatible-receiver AOT tests). Add two public `jvm-config` functions of
  the configuration map (for example `receivers` and
  `incompatible-receivers`) and use them in these places; remove
  `identity-aot-test/receivers`. Also write the
  `runtime-test/policy-test` axis `[nil "silent" "warn" "error"]` as
  `(cons nil config/policies)`. Labels and expectations unchanged.
  - code-shaper pass 5 fix: `jvm-config/receivers`,
    `jvm-config/incompatible-receivers`; `identity-aot-test/receivers`
    removed; `(cons nil config/policies)`.
- [x] Use one function for the incompatible-receiver AOT test body. Five
  tests repeat it: `with-fixture`, `fixture/produce` on Valhalla, then
  `run-cells` of `incompatible-consumer-child` for each incompatible
  receiver × policy (× execution in three tests), with
  `(fixture/classpath-with receiver output)`
  (`generated-value-aot-test/incompatible-generated-value-consumer`,
  `public-native-field-aot-test`, `public-mixed-field-aot-test`,
  `public-field-aot-test`, `field-aot-test/incompatible-restricted-field-consumer-test`).
  Add one public `aot-fixture` function (fixture-ns, source, producer jvm,
  receivers, executions, spec) and use it in the five tests. The producer
  policy differs (`generated-value-aot-test` gives none, the others
  `"error"`): keep each producer configuration, or align it and record the
  changed producer configuration in `implementation.md`. Labels and child
  counts unchanged.
  - code-shaper pass 5 fix: `aot-fixture/incompatible-consumers`; producer
    configurations kept.
- [x] Use one cell builder in `transfer-test`
  `constrained-concurrent-warning-scope-test` and
  `constrained-concurrent-strict-test`. Both copy
  `(vec (map-indexed (fn [i [[_ expand-name] serialized]] ...) (map vector
  expand-specs expansions)))` with the form
  `(harness/check-form check serialized (registry/class-name
  (harness/cell-ns-name i) 'Transferred))`; `concurrent-warning-scope-test`
  writes the same form again. Add one local function (check, context
  prefix, expected, expansions) that gives the cells, and one for the form.
  Labels and expectations unchanged.
  - code-shaper pass 5 fix: `transfer-test/concurrent-cells`,
    `transfer-test/concurrent-form`.
- [x] Make `child-transfer-test/consumer-child` take the check arguments
  explicitly. Today it selects extra arguments by symbol equality
  (`(when (= 'value-type.child-transfer-checks/consume check) [(:native?
  expected)])`), and its `expected` map holds an argument (`:native?`) with
  the expectation (`:result`). Make it take `check`, the extra arguments
  (a vector) and the expected result as separate parameters. Labels and
  expectations unchanged.
  - code-shaper pass 5 fix: `consumer-child` takes `check args expected`.
- [x] Use `consumer-aot-checks/builders` for the constructions of
  `identity-loader-checks/public-stale-sibling`. It rebuilds the direct,
  factory and reflective constructions with the copied
  `InvocationTargetException` unwrap (pass 4 removed the other copies in
  check namespaces). Build the `:construction` map from
  `(zipmap [:direct :factory :reflective] (builders ...))`, each called
  with a one-argument vector. Expectations (a map, order free) unchanged.
  - code-shaper pass 5 fix: `public-stale-sibling` uses `builders`.
- [x] Use one function for the frame of
  `controlled-field-capability-test` `controlled-native-absence-aot-test`
  and `controlled-native-absence-java-aot-test`. Both copy
  `with-fixture`, the classpath, `compile-controlled`, and `run-cells` of
  `consumer-child` for each policy × execution with the same fixture-ns
  and context; only the extra precondition (`javac`), the cell form and the
  expected value differ. Labels and expectations unchanged.
  - code-shaper pass 5 fix: `controlled-aot-consumers`.

## Code-shaper review (pass 6)

- [x] Add one `aot-fixture` function for the produced-artifact frame (for
  example `with-produced`: fixture-ns, source text, producer jvm, f). The
  frame `(with-fixture ns source (fn [{:keys [output] :as paths}] (when
  (fixture/produce producer paths ns) ...)))` is copied in about 20 tests:
  `public-field-aot-test` (9), `public-mixed-field-aot-test` (4),
  `public-native-field-aot-test` (3), `generated-value-aot-test` (2),
  `identity-aot-test` (2), `field-aot-test` (2), and in the pass-5 helpers
  `aot-fixture/incompatible-consumers` and
  `controlled-field-capability-test/controlled-aot-consumers`. Call f with
  the path map only when the producer passes; keep `javac` in f. Also use
  one idiom for the consumer jvm and classpath in these loops: today some
  write `(config/with-execution valhalla execution)` with
  `(classpath-with valhalla output)`, others `:let [jvm ...]` with
  `(classpath-with jvm output)`. Labels, child counts and expectations
  unchanged.
  - code-shaper pass 6 fix: `aot-fixture/with-produced` (with `produce`
    options); also `aot-test/source-free-runtime-only-fallback-test`;
    loops use `:let [jvm ...]` with `(classpath-with jvm output)`.
- [x] Add `consumer-aot-checks/java-consumer-result` (value?, annotations)
  beside `string-holder-result` and `holder-result`. The expected result
  of `java-consumer-form`, `{:source-absent? true :value? v :annotations a
  :checks :passed}`, is copied 8 times: `generated-value-aot-test`,
  `public-native-field-aot-test`, `public-mixed-field-aot-test`,
  `public-field-aot-test` (2), `field-aot-test`, `identity-aot-test` and
  `controlled-field-capability-test`. Use it in these places. Labels and
  expectations unchanged.
  - code-shaper pass 6 fix: 8 copies removed.
- [x] Use one described predicate for inspected AOT output. "Storage
  equal, and `:companion-references` one or more, each descriptor" is
  copied, with its `(repeat :+ descriptor)` description, in
  `field-aot-test/persisted-output?` (under `:output`),
  `public-field-aot-test/persisted-output?` and
  `public-mixed-field-aot-test/persisted-output?`. Add one public function
  of storage and descriptor (for example in `aot-fixture`) and use it in
  the three places. Also use one function for the inspection cell
  (`check-form 'value-type.field-aot-checks/inspect-output` of output,
  `"checked/aot_fixture/Holder"` and field): `public-field-aot-test/inspection`
  and the `public-mixed-field-aot-test` cells. Descriptions, labels and
  expectations unchanged.
  - code-shaper pass 6 fix: `aot-fixture/persisted-output?`,
    `aot-fixture/inspection-cell`; public `harness/description`.
- [x] Remove the parameter shadowing in `cell-registry`. The 3-arity of
  `absent?` names its parameter `class-name`, which hides the public
  `class-name` function of the same namespace (the 2-arity calls that
  function), and `class-name`, `absent?` and `defined` name a parameter
  `namespace`, which hides `clojure.core/namespace`. Rename the parameters
  (for example `binary-name`, `ns-or-name`). Behavior unchanged.
  - code-shaper pass 6 fix: `ns-or-name`, `binary-name`.

## Code-shaper review (pass 7)

- [x] Add a `jvm-config` function of one runtime that gives it in each
  execution (for example `with-executions`: `(map #(with-execution runtime
  %) executions)`). The product of one runtime and `config/executions` is
  still written as `(for [execution config/executions ... :let [jvm
  (config/with-execution valhalla execution)]] ...)` 10 times
  (`generated-value-aot-test` 2, `public-native-field-aot-test` 3,
  `public-mixed-field-aot-test` 3, `public-field-aot-test`
  `function-public-generated-value-aot-test`,
  `controlled-field-capability-test` line 161), and as the local
  `public-field-aot-test/value-receivers` and `receivers`. Use the new
  function in these places (`(for [jvm (config/with-executions valhalla)
  policy config/policies] ...)`; `receivers` as `(mapcat
  config/with-executions (config/receivers configuration))`, inline or in
  `jvm-config`), and remove both local functions. Keep the child order of
  each loop (policy outer in `controlled-field-capability-test`). Labels,
  child counts and expectations unchanged.
  - code-shaper pass 7 fix: `jvm-config/with-executions`; 9 loops and
    both locals; `controlled-aot-consumers` kept (see implementation.md).
- [x] Write the expected loads of `public-field-aot-test/strict-loads?`
  once. The description and the predicate each copy
  `(assoc harness/fallback-rejection :factory-bound? false)` and the set
  `#{"java.lang.NoClassDefFoundError" "java.lang.ExceptionInInitializerError"}`.
  Bind both in a `let` around `harness/described` (or private defs) and
  use them in the description and the predicate. Description, labels and
  expectations unchanged.
  - code-shaper pass 7 fix: `let` of `first-expected`, `retry-classes`.
- [x] Use `consumer-aot-checks/annotation-names` in the
  `controlled-field-capability-test/holder-check` child function. It
  copies the body (`(mapv #(.getName (.annotationType %))
  (.getDeclaredAnnotations field))`), and the child already requires
  `value-type.consumer-aot-checks` (it calls `builders` and `outcome`).
  Labels and expectations unchanged.
  - code-shaper pass 7 fix: done.

## Code-shaper review (pass 8)

- [x] Add one `aot-fixture` function for the source-free consumer matrix,
  as pass 5 did for `incompatible-consumers`. The body `with-produced`,
  then `run-cells` of one `consumer-child` for each jvm × policy with
  `(classpath-with jvm output)`, is copied in about 11 tests:
  `public-field-aot-test` (`source-free-public-identity-test`,
  `strict-public-identity-aot-test`, `function-public-identity-aot-test`,
  `source-free-public-generated-value-test`,
  `function-public-generated-value-aot-test`),
  `public-native-field-aot-test` and `public-mixed-field-aot-test`
  (`source-free-*`, `function-*`), `generated-value-aot-test/clojure-generated-value-consumer`,
  `identity-aot-test/source-free-identity-backend`. The Java consumer
  tests copy the same body with a `javac` precondition and one classpath
  (`generated-value-aot-test`, `public-native-field-aot-test`,
  `public-mixed-field-aot-test`, `public-field-aot-test` (2),
  `identity-aot-test`, `field-aot-test/java-restricted-field-consumer-test`).
  Give the function the fixture-ns, source text, producer, jvms, policies,
  form, the expected value of a policy, the `consumer-child` options and
  an optional Java source (javac before the cells), and use it in these
  places (and in `incompatible-consumers` if it fits). Labels, child
  counts, child order and expectations unchanged.
  - code-shaper pass 8 fix: `aot-fixture/source-free-consumers`; 16
    tests; `incompatible-consumers` kept (see implementation.md).
- [x] Use one idiom for the child configuration loop in
  `field-transfer-test`. Three tests use the local `configurations`
  (pairs `[configuration execution]`, read as `[configuration _]`,
  `[configuration execution]` and `[configuration]`); two tests
  (`strict-identity-public-transfer-test`,
  `unsupported-mixed-public-transfer-test`) and the other namespaces write
  `(for [runtime ... execution config/executions policy ...]
  (config/child-configuration runtime execution policy))`. Remove
  `configurations` and use the inline form. Labels and child order
  unchanged.
  - code-shaper pass 8 fix: `configurations` removed; inline form.
- [x] Make `cell-harness/produce` return nil when any of its support
  assertions fails. It asserts exit 0 and empty stderr under
  `"producer"`, but returns `:result` on exit 0 with non-empty stderr, so
  the cells run after a failed producer. `aot-fixture/compile-producer`
  returns nil in this case. Update the docstring. Labels unchanged.
  - code-shaper pass 8 fix: nil unless exit 0 and empty stderr.

## Code-shaper review (pass 9)

- [x] Use `aot-fixture/source-free-consumers` for each source-free
  consumer matrix. Three functions still copy its frame (`with-produced`,
  an optional precondition, then `run-cells` of `consumer-child` for each
  jvm × policy): `aot-fixture/incompatible-consumers`,
  `controlled-field-capability-test/controlled-aot-consumers` and
  `aot-test/source-free-runtime-only-fallback-test`. Pass 8 kept them
  because their context depends on jvm and policy. Give
  `source-free-consumers` a context option that is a function of jvm and
  policy (for example `:context-of`), and a way to pass the `produce`
  options (`:wrap` for `controlled-aot-consumers`). Then use it in the
  three places, and remove `incompatible-consumer-child` if it has no
  other caller. `controlled-aot-consumers` loops policy outer; a change to
  jvm outer is permitted, but record it in `implementation.md`. Labels,
  child counts and expectations unchanged.
  - code-shaper pass 9 fix: `:context-of`, `:produce`; three callers;
    `incompatible-consumer-child` removed; jvm outer (implementation.md).
- [x] Use `aot-fixture/source-free-consumers` in
  `field-aot-test/source-free-restricted-field-loading-test`. It writes
  `with-produced` and `run-cells` of one `consumer-child` on `valhalla`
  under `"error"`; `java-restricted-field-consumer-test` in the same file
  gives the same matrix as `[valhalla] ["error"]` (pass 8 did not list
  this test). Labels and expectations unchanged.
  - code-shaper pass 9 fix: done.
- [x] Use `consumer-aot-checks/value-class?` in the
  `controlled-field-capability-test/holder-check` child function in place
  of `(not (.isIdentity owner))` (and for `:eligible?` of `Long`). The
  child already requires `value-type.consumer-aot-checks`, and the other
  checks read value-class state through this function. Labels and
  expectations unchanged.
  - code-shaper pass 9 fix: done.

## Code-shaper review (pass 10)

- [x] Remove the inverse computation of the execution in
  `controlled-field-capability-test/controlled-aot-consumers`.
  `execution-of` gets the execution back from the jvm by dropping
  `(count (:jvm-opts valhalla))` items of its `:jvm-opts`. This depends on
  how `config/with-execution` joins the options. Pair each jvm with its
  execution where the jvms are made (for example
  `(zipmap (config/with-executions valhalla) config/executions)`, or a
  `for` over `config/executions`), and read the execution in
  `:context-of` from that pairing. Labels and child order unchanged.
  - code-shaper pass 10 fix: `execution-of` is `(zipmap jvms
    config/executions)`.
- [x] Use one idiom to pass keyword options. The project uses Clojure
  1.12, so a function with `& {:keys ...}` accepts a trailing map. Three
  places build a flat argument list for `apply` with `(mapcat identity
  ...)`: `aot-fixture/compile-producer` (`select-keys` of `:wrap :after
  :kind`), and `aot-fixture/source-free-consumers` (consumer-child options
  and `(:produce options)`). Pass the option map, and remove the
  `mapcat identity` calls. Labels unchanged.
  - code-shaper pass 10 fix: option maps passed; no `mapcat identity`.
- [x] Make `aot-fixture/incompatible-consumers` take the consumer jvms,
  as `source-free-consumers` does. Today it takes `receivers` and
  `executions` and makes the product with `config/with-execution`; two
  callers pass `[[]]` for "no execution axis". Callers pass
  `(config/incompatible-receivers configuration)` or
  `(mapcat config/with-executions (config/incompatible-receivers
  configuration))`. Labels, child order and expectations unchanged.
  - code-shaper pass 10 fix: `incompatible-consumers` takes jvms; five
    callers.
- [x] Remove the placeholder runtime map in
  `field-aot-test/separately-compiled-restricted-field-consumer-test`.
  `(fixture/classpath-with {:classpath artifact-classpath}
  consumer-output)` makes a map only to use `classpath-with`. Bind the
  artifact runtime once (`(assoc valhalla :policy "error" :classpath
  artifact-classpath)`), use it for `compile-producer` and for
  `classpath-with`. Labels unchanged.
  - code-shaper pass 10 fix: `artifact` runtime bound once.
- [x] Retake the after record after the last code change of the task.
  The record is from `e5ed659` (cross-JVM) and `15bf0ab` (fallback).
  Code-shaper passes 1–9 changed test code, and pass 9 changed the child
  order of `controlled-aot-consumers`. Do this after the last code-shaper
  fix: run the commands of implementation review pass 10, run
  `check_record.clj`, replace `after-record/*.edn`, and record the
  commit, times and findings in `implementation.md`.
  - code-shaper pass 10 fix: record at `41ebc3a`; results in
    implementation.md.

## Implementation test review (pass 15)

- [x] Add in-process false cases for the shared cell predicates that
  decide strict and inspection cells. Pass 5 covered `rejection?`, but no
  test shows a false result of the predicates built on it:
  - `cell-harness/rejections` (verdict of the strict cells in
    `runtime-test` (2) and `aot-test/fallback-aot-compilation-test`): if
    its postwalk replaced each map with `:chain` without `rejection?`, or
    returned true, a strict cell with a wrong message or chain would pass.
    In `cell-outcomes-test`, show: true for a result whose rejection maps
    match (nested in a vector and a map); false for a rejection map with a
    message without `warning-prefix`, with another chain, and for a
    non-rejection key that differs; and the description has the
    `rejection-description` keys in place of `:rejected? true`.
  - `aot-fixture/persisted-output?` (only verdict of the three inspection
    tests): show false for other storage, for empty
    `:companion-references`, and for one other descriptor.
  Put the cases in `test/cross-jvm/` (in-process); new labels only.
  - test review pass 15 fix: `cell-outcomes-test/rejections-test`;
    `value-type.aot-fixture-test/persisted-output-test`; namespace added
    to the in-process command in `doc/development/runtime.md`.

## Implementation test review (pass 16)

- [x] Make `cell-outcomes-test` locally consistent after pass 15. The
  namespace docstring says the tests cover "the failure side of
  `cell-outcomes`" and that "each case uses a `cell-child` map and a
  hand-made run map". This is not true for `rejection-test`,
  `rejections-test` (no child, no run map), `cell-child-wait-test`,
  `assert-child-report-test` and `undescribed-predicate-test`, and the
  pass 15 edit gives a 103-character docstring line. State what the
  namespace tests (cell outcomes, report translation, cell-child options,
  rejection predicates) and wrap the text. Also use
  `harness/source-rejection-chain` in `rejection-test` in place of the
  literal chain, as `rejections-test` does. Labels and expectations
  unchanged.
  - test review pass 16 fix: docstring states the four parts, wrapped;
    `rejection-test` uses `harness/source-rejection-chain`.

## Code-shaper review (pass 11)

- [x] Return the support verdict from the `is` values; do not compute the
  checks twice. Four helpers assert each support check with `is`, then
  write the same checks again for the return value:
  `cell-harness/produce` (exit, stderr), `aot-fixture/compile-producer`
  (`compiled?`), `aot-fixture/javac` and `aot-test/produced?`. When one
  copy changes and the other does not, the helper returns a pass for a
  failed assertion (or the reverse). `is` gives the value of its test:
  bind the `is` results (all evaluated, for example in a vector) and
  return their conjunction. Return values and labels unchanged.
  - code-shaper pass 11 fix: `every? true?` of the `is` vector;
    `produced?` returns its `is`.
- [x] Use one function for the runtime context label
  `(pr-str [java jvm-opts & parts])`. It is written about 15 times:
  `aot-fixture/consumer-child` (default context),
  `aot-fixture/incompatible-consumers`, `field-capability-test` (4),
  `identity-receiving-cross-jvm-test` line 47, `array-boundary-test`,
  `field-transfer-test` (3), `public-field-aot-test/inspection` cell,
  `child-transfer-test` (2), `loader-policy-test` (2). Add one public
  `cell-harness` function of a runtime map and the extra parts, and use it
  in these places. Keep the labels that do not start with java and
  jvm-opts (for example `[(:java configuration) policy]`). Labels
  unchanged.
  - code-shaper pass 11 fix: `cell-harness/runtime-context`; 15 copies.
- [x] Remove the `classpath` parameter of `aot-test/producer-child`. It
  joins `(str classpath File/pathSeparator source)`, a copy of
  `aot-fixture/classpath-with`, and each caller passes the
  `(:classpath selected)` value, which is equal to the `:classpath` of the
  producer jvm (`baseline`, `valhalla`). Use
  `(fixture/classpath-with jvm source)`, and remove the parameter from
  `producer-child`, `produce` and their callers. Labels unchanged.
  - code-shaper pass 11 fix: done.

## Code-shaper review (pass 12)

- [x] Use one source for the array allocation capability axis.
  `array-matrix-test/configurations` and
  `array-sequence-boundary-test/configurations` are the same six rows
  (runtime, reason), and each namespace defines the same `exports`
  constant (`array-info-boundary-test` again as `export-option`, with
  the same runtimes in `safe-inspection-boundary-test`). The expected
  `:flags` rule `(conj (:jvm-opts configuration) (str
  "-Dvalue-type.fallback=" policy))` is written 6 times: in the
  description and in the predicate of `array-matrix-test/check-expected`,
  `array-sequence-boundary-test/expected` and
  `array-info-boundary-test/control-expected`. Put the export option, the
  six rows and one expected-flags function in one place (for example a
  shared array test namespace), use them in the three namespaces, and
  bind the expected flags once in each description/predicate pair.
  Labels, child order and expectations unchanged.
  - code-shaper pass 12 fix: `value-type.array-capability`
    (`export-option`, `allocation-configurations`, `expected-flags`);
    flags bound once in each pair.
- [x] Use one predicate and one description for the allocation
  capability rejection. `array-matrix-test/rejection?`, the inner
  function of `array-sequence-boundary-test/rejected?` (adds `:effects 0`)
  and `array-boundary-test/unavailable?` check the same outcome:
  `:array-unavailable` with reason, `IllegalAccessException` cause only
  for `:array-access-denied`, and a message with `--enable-preview` and
  `--add-exports`. The copies also differ in robustness: `rejection?`
  guards a nil `:message` with `(or ... "")`, the other two give
  `re-find` a nil. The descriptions (`{:status :rejected :data ... :message
  (contains "--enable-preview" "--add-exports")}`) are copied in the same
  three places. Add one shared predicate and description (nil-safe) and
  use them; keep the per-test extra keys (`:status`, `:effects`,
  `:exception`). Labels and expectations unchanged.
  - code-shaper pass 12 fix: `array-capability/rejection?` (nil-safe),
    `array-capability/rejection-description`.
- [x] Use the bound `policy` in `field-transfer-test`
  `identity-public-transfer-test` and the incompatible-receiver loop (line 72) in
  place of `(:policy configuration)`: the `for` binds `policy` and builds
  `configuration` from it, so the label reads the same value back from
  the map. Labels unchanged.
  - code-shaper pass 12 fix: done; also `supported-public-transfer-test`
    and `mixed-public-transfer-test`.

## Code-shaper review (pass 13)

- [x] Add one described predicate for the unavailable outcome beside
  `consumer-aot-checks/unavailable-description`. Its check is copied in
  `field-transfer-test/unavailable` and in
  `identity-receiving-cross-jvm-test/incompatible-value-receiving-test`:
  `(= (assoc (unavailable-failure c capability) :absent? true) (dissoc
  outcome :message))` and `(unavailable-message? c outcome)`. The second
  test also writes `["no value classes" "Use a JEP 401 JDK"]` twice (in
  the description and in the predicate). Add one public function (class
  name, capability, extra names, outcome) or one described predicate, use
  it in both tests, and bind the extra names once. Labels, descriptions
  and expectations unchanged.
  - code-shaper pass 13 fix: `consumer-aot-checks/unavailable-outcome?`;
    `unavailable-message?` removed; `extra-names` bound once.
- [x] Write the expected attempts of `field-capability-test/policy-expected`
  once. The description and the predicate each copy `(vec (repeat 2 (if
  rejected? (merge (harness/policy-rejection detail) rejected) success)))`;
  only the detail differs (`'<detail>` or the result detail). Bind one
  local function of detail and use it in both, as
  `identity-receiving-cross-jvm-test/strict-nested-public-declaration-test`
  does with `declarations`. Description and expectations unchanged.
  - code-shaper pass 13 fix: local `attempts-of`.
- [x] Use one source for the producer runtimes and the receiver jvms of
  the AOT tests, as pass 3 did for `:preview-off`. The value producer
  `(assoc valhalla :policy "error")` is written about 24 times
  (`public-field-aot-test` 6, `public-mixed-field-aot-test` 5,
  `public-native-field-aot-test` 4, `field-aot-test` 4,
  `controlled-field-capability-test` 2, `child-transfer-test`), and the
  identity producer `(assoc baseline :policy "silent")` 6 times in
  `public-field-aot-test`. The receiver jvms
  `(mapcat config/with-executions (config/receivers configuration))` and
  `(mapcat config/with-executions (config/incompatible-receivers
  configuration))` are written 7 times (`public-field-aot-test` 5,
  `public-native-field-aot-test`, `public-mixed-field-aot-test`). Add
  public `jvm-config` functions of the configuration map (for example
  `value-producer`, `identity-producer`, `receiver-jvms`,
  `incompatible-receiver-jvms`) and use them in these places. Keep the
  `cell-runner-test` cell configurations. Labels, child order and
  expectations unchanged.
  - code-shaper pass 13 fix: `jvm-config/value-producer`,
    `identity-producer`, `receiver-jvms`, `incompatible-receiver-jvms`.
- [x] Add one constant for the expected constructor rejections of a
  non-nil checked field: `["java.lang.NullPointerException"
  "java.lang.ClassCastException"]` (nil, then wrong type). The literal is
  written about 14 times in 11 namespaces (for example
  `consumer-aot-checks`, `field-capability-test`,
  `identity-receiving-cross-jvm-test` (2), `public-native-field-aot-test`
  (2), `public-mixed-field-aot-test` (4, two as one concatenated vector),
  `public-field-aot-test`, `child-transfer-test`, `field-aot-test`,
  `function-redefinition-test`; `controlled-field-capability-test` gives
  the pair after the stored value). Add it to `consumer-aot-checks` (the
  shared expectation namespace) and use it in these places. Expectations
  unchanged.
  - code-shaper pass 13 fix: `consumer-aot-checks/checked-rejections`;
    14 copies removed.

## Code-shaper review (pass 14)

- [x] Use `jvm-config/identity-producer` in the two remaining copies of
  the identity producer (pass 13): `transfer-test/produce`
  (`(assoc (:baseline (config/configuration)) :policy "silent")`) and
  `aot-test/source-free-runtime-only-fallback-test`
  (`(assoc baseline :policy "silent" :classpath classpath)`). In the
  second, `classpath` is `(:classpath selected)`, which is equal to the
  `:classpath` of `baseline` (both are `java.class.path`; see pass 11
  `producer-child`): remove the `classpath` binding. Keep the
  `cell-runner-test` configurations. Labels unchanged.
  - code-shaper pass 14 fix: both copies; `classpath` binding removed.
- [x] Add one constant for the declared annotations of a native checked
  field beside `consumer-aot-checks/checked-rejections` (for example
  `native-annotations`: `["jdk.internal.vm.annotation.NullRestricted"]`).
  The literal vector is written 8 times in 5 namespaces:
  `public-native-field-aot-test` (3), `public-mixed-field-aot-test` (3),
  `child-transfer-test`, `field-aot-test`. Use the constant in these
  places. The class-file descriptor form
  (`"Ljdk/internal/vm/annotation/NullRestricted;"`, `field-aot-test`,
  `public-mixed-field-aot-test`) is a different data shape; keep it, or
  add a second constant beside the first. Expectations unchanged.
  - code-shaper pass 14 fix: `consumer-aot-checks/native-annotations`;
    8 copies; descriptor form kept.
- [x] Use one function for the child call forms of `consumer-aot-checks`.
  `holder-consumer-form`, `incompatible-consumer-form` and
  `strict-consumer-form` each write `(list 'do '(require
  'value-type.consumer-aot-checks) (list
  'value-type.consumer-aot-checks/<f> spec))`, the shape of
  `cell-harness/check-form`; `java-consumer-form` writes it again with
  extra requires. The namespace is loaded by children and does not
  require `cell-harness`: add one private function of a check symbol
  and args (for example `call-form`) in `consumer-aot-checks`, and
  define the three forms with it. Forms (equal data), labels and
  expectations unchanged.
  - code-shaper pass 14 fix: private `call-form`; forms equal (REPL).

## Code-shaper review (pass 15)

- [x] Use one source for the serialization and evaluation of transferred
  expansions. "Expand form with expand in a fresh namespace
  namespace-name (`create-ns`, `refer 'clojure.core`), print with
  metadata" is copied four times: `transfer-checks/produce`,
  `field-transfer-checks/expand-in`, `child-transfer-checks/produce` and
  the `controlled-field-capability-test/controlled-expansions` child form.
  "Evaluate a serialized expansion in mode" is copied four times:
  `transfer-checks/consume` (`:eval`, `:source`),
  `field-transfer-checks/operation` and `child-transfer-checks/evaluate`
  (`:eval`, `:source`, `:analysis`), and
  `controlled-field-capability-test/receiving-definition` (`:eval`,
  `:function`, `:source`). The copies also differ in names and shapes:
  `:analysis` and `:function` are the same mode; expand is a keyword
  (`case` in two places, the `expanders` map in one) or a symbol
  (`'macroexpand` in the controlled test). Add one child-loadable function
  for each (for example in `transfer-checks`) and use them in these
  places, with one mode name and one expand representation. Labels and
  expectations unchanged; record a renamed label part (for example
  `:function`) in `implementation.md`.
  - code-shaper pass 15 fix: `transfer-checks/serialize`,
    `transfer-checks/evaluate`; mode `:analysis`, expand keyword;
    controlled label `:function` → `:analysis` (implementation.md).
- [x] Add one function for the outcomes of nil and a wrong-type value of a
  construction beside `consumer-aot-checks/checked-rejections` (for
  example `checked-outcomes`: build, wrong value →
  `[(outcome #(build [nil])) (outcome #(build [wrong]))]`). The pair is
  written six times: `field-policy-checks`, `identity-receiving-checks`
  (`generated-value`, `nested-warning-scope`), `child-transfer-checks/consume`,
  the `controlled-field-capability-test/holder-check` child function and
  `function-redefinition-test/repeated-definition-cell`. Use it in these
  places. `field-transfer-checks` gives the same fact in another shape
  (local `outcome`, keys `:nil`/`:wrong` with `:rejected`); align
  `consume-supported` and `consume-mixed` with it and expect
  `checked-rejections` in `field-transfer-test` (`supported`, `mixed`).
  Labels unchanged.
  - code-shaper pass 15 fix: `consumer-aot-checks/checked-outcomes`;
    field-transfer `:rejections`; local `outcome` removed.
- [x] Use `cell-registry/root` for each root cause, and one function for the
  root failure with its ex-data. `validation-checks/root-cause` (private)
  and the `loop` over `ex-cause` in `field-transfer-checks/attempt` copy
  `registry/root`; both namespaces already require `cell-registry`.
  `(assoc (checks/failure e) :data (ex-data (registry/root e)))` is
  written in `identity-receiving-checks/root-failure` and again in
  `field-transfer-checks/attempt`. Make `root-failure` public beside
  `consumer-aot-checks/failure` and use it in both. Expectations
  unchanged.
  - code-shaper pass 15 fix: `consumer-aot-checks/root-failure`;
    `registry/root` in both places.
- [x] Make `consumer-aot-checks/builders` take the namespace, as
  `cell-registry/defined` and `absent?` do (`ns-or-name`). Each caller
  converts a namespace to its name string: `(str (ns-name n))` is written
  nine times (`identity-loader-checks`, `field-policy-checks`,
  `field-transfer-checks` (2), `identity-receiving-checks` (2),
  `child-transfer-checks`, `function-redefinition-test`, and the
  `controlled-field-capability-test/definition-cell` call of
  `holder-check`). Also read the factory of
  `identity-receiving-checks/nested-warning-scope` by name, not by
  position (`(second builders)`), as `identity-loader-checks` does with
  `zipmap [:direct :factory :reflective]`. Expectations unchanged.
  - code-shaper pass 15 fix: `builders` takes `ns-or-name`; nine
    callers; factory read by name.
- [x] Use `consumer-aot-checks/annotation-names` and `native-annotations`
  in `field-transfer-checks/native-field?`. It copies the annotation name
  read and the literal `"jdk.internal.vm.annotation.NullRestricted"`
  (pass 14 removed the other copies). Expectations unchanged.
  - code-shaper pass 15 fix: done.
- [x] Add one `consumer-aot-checks` function for the facts of a checked
  field (for example `field-facts`: `{:type :final? :annotations}`). It is
  written in `holder-consumer` (`:fields`), the
  `controlled-field-capability-test/holder-check` child function and
  `function-redefinition-test/repeated-definition-cell`;
  `child-transfer-checks/consume` writes `:final?` and `:annotations`.
  Use it in these places (merge into the result map where the keys are
  top level). Expectations unchanged.
  - code-shaper pass 15 fix: `consumer-aot-checks/field-facts`;
    `child-transfer-checks/consume` selects `:final?` `:annotations`.
- [x] Remove the name shadowing in `consumer-aot-checks`, as pass 6 did in
  `cell-registry`. The public `failure` names its parameter `failure`,
  and `outcome`, `builders` and `java-consumer` bind a local `failure`
  that hides that function. `holder-consumer` and `java-consumer` bind
  `owner` to the class name, then rebind `owner` to the Class. Rename
  the locals (for example `thrown`, `owner-class`). Behavior unchanged.
  - code-shaper pass 15 fix: `thrown`, `target`, `owner-class`.

## Code-shaper review (pass 16)

- [x] Align `field-policy-checks` with the other check namespaces. It is
  the only namespace that requires `value-type.cell-registry` as
  `cell-registry` (13 others use `registry`), and it names the caught
  Throwable `failure` (two `catch` forms), the name
  that pass 15 removed from `consumer-aot-checks` because it reads as the
  `failure` function. Use the alias `registry` and the local `thrown`.
  Behavior unchanged.
  - code-shaper pass 16 fix: alias `registry`; local `thrown`.
- [x] Use one function for "run an attempt twice". The two-attempt rule
  (the second attempt shows warn-once state, or no partial definition
  after a failure) is written as `(mapv (fn [_] ...) (range 2))` about 17
  times in 13 namespaces (for example `transfer-checks/consume`,
  `field-transfer-checks` (3), `field-policy-checks/attempts`,
  `identity-loader-checks`, `consumer-aot-checks` (2), `aot-checks` (2),
  `array-boundary-test`), and as `(mapv (fn [_] ...) [0 1])` in
  `child-transfer-checks/reject-replaced-child`. Add one public function
  of a no-argument function to a child-loadable namespace that needs only
  `clojure.core` (for example `cell-registry/twice`: `[(f) (f)]`, eager,
  in order), and use it in these places. Keep other counts (for example
  `(range 8)` in `warning-checks`). Results and labels unchanged.
  - code-shaper pass 16 fix: `cell-registry/twice`; 18 copies in 13
    namespaces; `array-info-boundary-test` (2) kept (implementation.md).
- [x] Use one source for the controlled native absence binding. The
  `with-redefs` binding `[value-type.impl.runtime/field-capability
  (constantly {:available? false :reason :controlled-absence})]` is
  `controlled-field-capability-test/controlled-absence` and is copied
  inside the quoted `function-redefinition-test/repeated-definition-cell`.
  Both tests show the same controlled boundary
  (`mementum/knowledge/controlled-fallback-boundaries.md`). Put the
  binding in one shared test namespace (for example `cell-harness`) and
  use it in both tests (in the template, for example with a placeholder
  that `cell-form` replaces, as `DECLARATION`). Forms (equal data),
  labels and expectations unchanged.
  - code-shaper pass 16 fix: `cell-harness/controlled-absence`;
    `CONTROLLED-ABSENCE` placeholder; forms equal (REPL, modulo `#()` gensym).
- [x] Retake the after record after the last code change of the task.
  The record is from `41ebc3a`; pass 15 renamed a label part of
  `controlled-native-absence-receiving-test` (`:function` → `:analysis`),
  so 12 labels in `after-record/cross-jvm.edn` do not agree with the
  tests. Do this after the last code-shaper fix: run the commands of
  implementation review pass 10, run `check_record.clj`, replace
  `after-record/*.edn`, and record the commit, times and findings in
  `implementation.md`.
  - code-shaper pass 16 fix: record at `8292c19`; results in
    implementation.md.

## Code-shaper review (pass 17)

- [x] Use the `transfer-checks` expansion and evaluation rules in
  `identity-loader-checks`. It has a third copy of the expander map
  (`expansions`, the same as the private `transfer-checks/expanders`),
  and its private `evaluate` has the mode `:function`, which calls the
  function outside its eval (`((eval (list 'fn [] form)))`). Pass 15 set
  one mode name (`:analysis`) and put the call inside the eval, because an
  outside call can keep a stale import (implementation.md, slice 4
  finding). `reject` also has its own `:function` case. Give
  `transfer-checks` one public expander function or map, use it in
  `identity-loader-checks`, and use one mode name and one call rule
  for the data form (for example `transfer-checks/evaluate` of the
  printed form, or a shared function of a form). Then the mode
  `:function` in `loader-policy-test` `cases` changes to `:analysis`.
  Record the changed label part in `implementation.md`. Expectations
  unchanged.
  - code-shaper pass 17 fix: public `transfer-checks/expanders`,
    `transfer-checks/evaluate-form`; mode `:analysis` (call inside eval);
    loader-policy label `:function` → `:analysis` (implementation.md).
- [x] Use one name for a caught Throwable in the check namespaces. Passes
  15–16 chose `thrown`, but `aot-checks` uses `failure` (3, the name of
  the `consumer-aot-checks/failure` function), and `transfer-checks`,
  `field-transfer-checks`, `identity-loader-checks` (2),
  `validation-checks` (2), `warning-checks` and `expansion-checks` (4)
  use `error`. Rename these locals to `thrown`. Behavior unchanged.
  - code-shaper pass 17 fix: listed locals `thrown`; also
    `array-matrix-checks`, `array-sequence-checks`.
- [x] Use `consumer-aot-checks/value-class?` in
  `aot-checks/load-value-fixture` in place of the inline reflective call
  of `Class/isValue`, as pass 9 did for `holder-check`. The other checks
  read value-class state through this function. Expectations unchanged.
  - code-shaper pass 17 fix: done.
- [x] Remove `controlled-field-capability-test/receiving-definition`. It
  takes `[analysis expansion]` and gives
  `(transfer-checks/evaluate expansion analysis)`, so the two orders of
  the same arguments are side by side. Write the call form at its one
  caller. Labels unchanged.
  - code-shaper pass 17 fix: inline call form.
- [x] Remove the name strings from `transfer-test/expand-specs`
  (`[[:macroexpand "macroexpand"] ...]`). Each string is `(name expand)`
  of the keyword beside it; keep a vector of the expand keywords and
  write `(name expand)` in `concurrent-cells`. Labels unchanged.
  - code-shaper pass 17 fix: `expands`; `(name expand)`.
- [x] Give the `aot-test` producer helpers a runtime with `:policy`, as
  `harness/produce` and `aot-fixture/produce` take. `producer-child` and
  `produce` take `jvm` and `policy` separately, so
  `source-free-value-aot-loading-test` writes `valhalla "error"` in
  place of `(config/value-producer configuration)` (pass 13). Remove the
  `policy` parameter, use `config/value-producer` there and
  `(assoc baseline :policy producer-policy)` in
  `source-free-fallback-aot-loading-test`. Labels unchanged.
  - code-shaper pass 17 fix: no `policy` parameter;
    `config/value-producer`.
- [x] Define `field-transfer-checks/produce-mixed` with
  `transfer-checks/produce` (`(transfer/produce form true specs)`). Its
  body is a copy of `transfer-checks/produce`. Expansions unchanged.
  - code-shaper pass 17 fix: done.

## Code-shaper review (pass 18)

- [x] Use one source for the receiving mode and expand axes, as pass 3
  did for the policy and execution axes. `transfer-checks` owns the mode
  names (`evaluate-form`) and the expand keywords (`expanders`), but the
  axes are written as literals in two orders: `[:eval :analysis :source]`
  (`identity-loader-checks/public-stale-sibling`,
  `loader-policy-test/cases`,
  `controlled-field-capability-test/receiving-specs`) and
  `[:eval :source :analysis]` (`field-transfer-test/modes`,
  `child-transfer-test/cell-specs`); `[:macroexpand :macroexpand-1]`
  (`identity-loader-checks`, `loader-policy-test`, `transfer-test/expands`,
  `field-transfer-test/expands`, `child-transfer-test`) and
  `[:macroexpand-1 :macroexpand]` (`receiving-specs`, and the symbols
  `['macroexpand-1 'macroexpand]` in the
  `controlled-field-capability-test` definition loop). Add public
  `transfer-checks/modes` and `transfer-checks/expands`, define
  `expanders` from `expands`, use them in these places, and remove the
  locals. Keep subsets (for example `[:eval :source]` in
  `transfer-test`). Label sets and expectations unchanged; record each
  changed child order in `implementation.md`.
  - code-shaper pass 18 fix: `transfer-checks/modes`
    `[:eval :analysis :source]`, `transfer-checks/expands`; locals removed;
    changed cell orders in implementation.md.
- [x] Use the `transfer-checks` and `consumer-aot-checks` rules in
  `identity-receiving-test`, as pass 17 did in `identity-loader-checks`.
  `receiving-identity-fields-test` and `receiving-redefined-child-test`
  each copy the evaluation of a form in a mode, with the mode `:function`
  that calls the function outside its eval (`((eval (list 'fn []
  form)))`; the `transfer-checks/evaluate-form` docstring states that this
  call can keep a stale import), and the `:macroexpand`/`:macroexpand-1`
  cases of `transfer-checks/expanders`. `receiving-identity-fields-test`
  also copies `consumer-aot-checks/builders` (direct `new`, factory,
  reflective with the `InvocationTargetException` unwrap). Use
  `transfer/evaluate-form` (mode `:analysis`), `transfer/expanders` for
  the two public expansions (keep `:internal` local) and
  `(zipmap [:direct :factory :reflective] (checks/builders receiver
  owner))`, each called with an argument vector. Record the changed label
  part (`:function` → `:analysis`) in `implementation.md`. Expectations
  unchanged.
  - code-shaper pass 18 fix: done; label part in implementation.md.
- [x] Wrap the docstring lines that pass 17 and earlier passes left over
  80 characters: `identity-loader-checks/stale-sibling-case` (108
  characters, `:analysis or :source ... public-stale-sibling-expected`)
  and `loader-policy-test/strict-rejection` (`rejection is
  harness/rejection? with harness/source-rejection-chain and context
  warning`). Text unchanged.
  - code-shaper pass 18 fix: done.
- [x] Retake the after record after the last code change of the task.
  The record is from `8292c19`; pass 17 renamed a label part of
  `loader-policy-test/fresh-policy-loader-rejection-test`
  (`:function` → `:analysis`), so 36 labels in
  `after-record/cross-jvm.edn` do not agree with the tests (and the
  pass 18 steps can change more). Do this after the last code-shaper fix:
  run the commands of implementation review pass 10, run
  `check_record.clj`, replace `after-record/*.edn`, and record the
  commit, times and findings in `implementation.md`.
  - code-shaper pass 18 fix: record at `691584f`; results in
    implementation.md.

## Code-shaper review (pass 19)

- [x] Remove the remaining name shadowing in the shared test namespaces,
  as passes 6 and 15 did. `cell-harness/described` names its parameter
  `description`, which hides the public `description` function (pass 6
  made it public). The private `cell-harness/check` (the check map) is
  hidden by the `check` of `report-check` (`:keys`) and of `check-form`.
  `consumer-aot-checks/unavailable-outcome?` names its parameter
  `outcome`, which hides the public `outcome` function (pass 15 missed
  it). `cell-registry/thrown` binds a local `root`, which hides the public
  `root` function. `controlled-field-capability-test/controlled-aot-consumers`
  names its parameter `expected`, which hides the private `expected`.
  Rename the locals or the private function (for example `data`,
  `check-result`, `result`, `root-cause`, `consumer-expected`). Behavior
  unchanged.
  - code-shaper pass 19 fix: `data`, `check-result`, `result`,
    `root-cause`, `consumer-expected`.
- [x] Use the `consumer-aot-checks` rejection rules in
  `identity-receiving-test`, as pass 18 did for `builders`.
  `receiving-identity-fields-test` writes
  `[[nil NullPointerException] [42 ClassCastException]]` with `(class
  thrown)`, and `receiving-redefined-child-test` writes `[[nil
  NullPointerException] [stale ClassCastException]]` with a copy of the
  reflective `InvocationTargetException` unwrap (`catch ... error (class
  (.getCause error))`). Pair the invalid values with
  `checks/checked-rejections` and read each with `checks/outcome` (one
  `is` for each value, so the assertion count stays 2012), and remove
  the `InvocationTargetException` import. Also bind the argument vector
  `[input "label" nil (long 7) (double 2.5)]` once (written five times in
  `receiving-identity-fields-test`), and name the caught Throwable of
  `receiving-identity-native-modes-test` `thrown` (pass 17). Labels and
  expectations unchanged.
  - code-shaper pass 19 fix: `checked-rejections` with `checks/outcome`;
    import removed; `args` bound once; `thrown`.
- [x] Use `transfer-checks/expanders` in the child form of
  `controlled-field-capability-test/controlled-native-absence-construction-test`.
  It writes `(symbol (name expand))` to call `macroexpand` or
  `macroexpand-1` by symbol, a second copy of the keyword-to-expander rule
  that `expanders` owns, and `definition-cell` already requires
  `value-type.transfer-checks`. Write the form as
  `(list 'eval (list (list 'value-type.transfer-checks/expanders expand)
  (list 'quote holder-definition)))`. Labels and expectations unchanged.
  - code-shaper pass 19 fix: done.
- [x] After the last code-shaper fix, run the commands of implementation
  review pass 10 and `check_record.clj`. When labels, child order or
  assertion counts differ from `after-record/*.edn` (record at
  `691584f`), replace the record. Record the commit and findings in
  `implementation.md`.
  - code-shaper pass 19 fix: run at `b2c7cf7`; summaries equal the
    `691584f` record (except the known gensym labels); record kept;
    findings in implementation.md.

## Code-shaper review (pass 20)
- [x] Use one source for the shared axes and checks of the array tests,
  as pass 12 did for the allocation rows. The require-order axis
  `[['value-type.array 'value-type.core] ['value-type.core
  'value-type.array]]` is written 3 times (`array-matrix-test`,
  `array-sequence-boundary-test`, `array-info-boundary-test`). The check
  "two outcomes, equal" (`(= 2 (count xs))` and `(= (first xs) (second
  xs))`) is written 4 times (`array-boundary-test/unavailable?`,
  `array-matrix-test/check-expected`,
  `array-sequence-boundary-test/rejected?`,
  `array-info-boundary-test/control-expected`). Add
  `array-capability/require-orders` and one public predicate (for example
  `repeated?`: two equal outcomes) and use them in these places. Labels,
  child order and expectations unchanged.
  - code-shaper pass 20 fix: `array-capability/require-orders`,
    `repeated?`.
- [x] Write each expected array value of `array-info-boundary-test` and
  `array-sequence-boundary-test` once. In `array-info-boundary-test`, the
  supported array-info map (`{:component-class "java.lang.Object"
  :component-value? false ...}`) is written 3 times (`array-outcome?`,
  the `control-expected` description, `export-only-expected`); the
  invalid-array outcome (`:invalid-array` `:not-reference-array`, `:cause
  nil`) and the unavailable outcome are written in the description and
  again in the predicate; the runtime prefix is written as a string
  (`"1.8."`, `"27-jep401ea"`) and again as a regex. In
  `array-sequence-boundary-test/expected`, `{'SequencePoint {:new-class?
  true :factory? true}}` is in the description and the predicate. Bind
  each value once (private defs or a `let`) and use it in both places, as
  pass 7 did for `strict-loads?`. Descriptions, labels and expectations
  unchanged.
  - code-shaper pass 20 fix: `supported-info`, `invalid-outcome`,
    `unavailable-outcome`, `runtime-prefix` (`str/starts-with?` in the
    predicate); private `definitions` in
    `array-sequence-boundary-test`.
- [x] Use `cell-registry/twice` for the remaining two-attempt copies.
  `field-capability-test/required-capability-policy-matrix-test` writes
  `[(attempt) (attempt)]` in its child form (pass 16 did not list it).
  Pass 16 kept the two `(mapv (fn [_] ...) (range 2))` of
  `array-info-boundary-test` because the child forms observe require
  order, but `cell-harness/child-form` requires
  `value-type.cell-registry` before each cell, so a call of `twice` loads
  no new namespace and does not change the require order. Use `twice` in
  the three places. Results and labels unchanged.
  - code-shaper pass 20 fix: 3 places.
- [x] Write the expected attempts of
  `runtime-test/fallback-expansion-policy-test` once. `:unbound` and
  `:bound` are the same expression (`(vec (repeat 2 (vec (repeat 2 (if
  strict? rejected {:returned? true :absent? true})))))`), and
  `:evaluated` and `:source` are the same expression (`(vec (repeat 2 (if
  strict? rejected defined)))`). Bind each once in the `:let`. Labels and
  expectations unchanged.
  - code-shaper pass 20 fix: `expansions`, `definitions` in the `:let`.
- [x] Use one worker count and one start rule in `warning-checks`. The
  number of workers `8` is written three times (`concurrent-definitions`:
  `CountDownLatch.` and `range`; `concurrent-rejections`: `range`) and
  twice in `transfer-test` (`concurrent-defined`, the expected of
  `constrained-concurrent-strict-test`, as `(vec (repeat 8 (vec (repeat 2
  ...))))`). The two functions also start the workers with two idioms
  (`Thread` with `bound-fn`, a ready latch with timeout and a start latch;
  `future` with a start promise and no ready barrier). Add a public
  `warning-checks/worker-count`, one private function that runs n
  workers from one start barrier and returns their results in order
  (rethrow a worker failure), use it in both functions, and add one
  `transfer-test` function of the expected result of one worker that
  gives the vector of `worker-count` workers. Labels and expectations
  unchanged.
  - code-shaper pass 20 fix: `warning-checks/worker-count`, private
    `run-workers` (ready and start latches); `transfer-test/concurrent-expected`.
- [x] Use one name for the `jvm-config/configuration` map and for the
  execution options. In `identity-receiving-cross-jvm-test` (4) and
  `field-transfer-test` (2) the map is `runtimes`, and in
  `identity-receiving-cross-jvm-test` `configuration` names one runtime;
  the other namespaces use `configuration` for the map and `runtime` for
  one runtime. The execution options are `execution-options` 12 times
  (`transfer-test`, `identity-receiving-cross-jvm-test`) and `execution`
  in the other namespaces (`jvm-config/with-execution`). Rename to
  `configuration`, `runtime` and `execution`. Behavior and labels
  unchanged.
  - code-shaper pass 20 fix: done; a child configuration local is `child`
    in `field-transfer-test` and `run-check-cells`.
- [x] Use one name for each transfer axis value, as pass 18 did for the
  axes. `transfer-checks` names them `modes` and `expands`, but locals
  name a mode `analysis` (`controlled-field-capability-test/receiving-specs`
  and its test; `identity-loader-checks` `reject`, `stale-sibling-case`,
  `public-stale-sibling`, docstrings), an expand keyword `expansion`
  (`identity-loader-checks`), and an expand index `expansion`
  (`field-transfer-test` `supported-public-transfer-test`,
  `identity-cells`). Elsewhere `expansion` is a serialized expansion (for
  example `field-transfer-test/cell-expansions`). Rename to `mode`,
  `expand` and `expand-index`. The `public-stale-sibling` result keys
  (`[expand mode]`) stay equal data. Labels and expectations unchanged.
  - code-shaper pass 20 fix: done; also `loader-policy-test` and
    `identity-receiving-test`.
- [x] Use one function for the source-absence check of a source-free
  consumer. `(nil? (io/resource source))` is written 4 times in
  `consumer-aot-checks` (`holder-consumer`, `java-consumer`,
  `incompatible-consumer`, `strict-consumer`), and again in
  `function-aot-checks`, `field-aot-test` (3, child forms),
  `controlled-field-capability-test/saved-cell` and `aot-checks` (3, in
  `assert`). Add a public `consumer-aot-checks/source-absent?` (resource
  name) and use it in these places; keep the `aot-checks` `assert`
  forms. Results and labels unchanged.
  - code-shaper pass 20 fix: `consumer-aot-checks/source-absent?`; unused
    `clojure.java.io` requires removed.

## Implementation review (pass 12)

- [x] Resolve the open acceptance 3 finding of code-shaper pass 19.
  `check_record.clj` gives limit-1 = limit-4 `:assertions` labels false
  for both commands, because Scry 0.1.61 orders namespaces by a hash map
  keyed by `Namespace` (`group-by (comp :ns meta)`), so the namespace
  order changes in each JVM run, also between two limit-4 runs. The
  earlier "Acceptance 3 holds" results depend on this order. No step
  tracks the finding. Make the comparison independent of the Scry
  namespace order: compare the label vector of each namespace (the
  `:assertions` entries of that namespace, in report order) at limit 1
  and limit 4. Record in `implementation.md` that the namespace order is
  a Scry property, not a runner property, and that acceptance 3 is
  checked for each namespace. If this reading of acceptance 3 is not
  acceptable, stop and ask the user (design decision).
  - review pass 12 fix: `check_record.clj` compares the label vector of
    each namespace and the namespace set; reading recorded.
- [x] Make the labels of
  `fields-test/public-imported-concrete-field-test` the same in each
  run. Its `testing` context is `(pr-str tag)`, and one tag is the
  `gensym` child name (`NullableChild<n>`), so 2 labels for each command
  differ in each run. They are the 2 known label mapping findings, and
  they are an exception in each acceptance 3 comparison. Use a fixed
  context for the generated child tag (for example the string
  `"NullableChild"`), keep the gensym class name, and record the label
  change (old label, new label) in `implementation.md`. Assertions and
  expectations unchanged.
  - review pass 12 fix: fixed contexts `"NullableChild"`,
    `"imported_fields.NullableChild"`; labels in implementation.md.
- [x] Make `check_record.clj` report 0 label mapping findings for the
  recorded label changes. Today it reports 46 findings for each fallback
  command, and each pass explains them again in `implementation.md`. The
  44 `:function` → `:analysis` labels are recorded mapped labels (passes
  15, 17, 18), as the 5 moved Vars are. Add these renames to the mapping
  in `check_record.clj` (as `moved-vars` does: Var and the replaced
  context part), so that a finding is a new deviation only. Update the
  header comment.
  - review pass 12 fix: `mode-renamed-vars`, `fixed-tags`; 0 findings.
- [x] After the three steps above, take the final after record at the
  last code commit, with no other load on the machine (pass 20 timing
  is not valid: the machine suspended; pass 19 gave 29.7%, above the
  25% bound, with no cause found). Run the four commands at limit 4
  and the two limit-1 commands with `/usr/bin/time -p`, run
  `check_record.clj` with both directories, replace `after-record/*.edn`,
  and record the commit, times, the acceptance 2 time percentage and the
  acceptance 3 result in `implementation.md`. If the total is above 25%
  of 4060.34 s, profile the remaining child starts (plan "Risks").
  - review pass 12 fix: record at `1be42d8`; 13.5%; results in
    implementation.md.

## Implementation review (pass 13)

- [ ] Make `design.md` and `plan.md` agree with the acceptance 3 reading
  of pass 12. Scope 4 ("Report order and labels are the same at all
  limits"), acceptance 3 ("the same labels in the same order") and the
  plan "Label order (acceptance 3): the `:assertions` vector" still state
  one order for the full vector. `check_record.clj` and
  `implementation.md` check the order of each namespace, because Scry
  0.1.61 orders namespaces by identity hash. Pass 12 said to ask the
  user if this reading is not acceptable; no user decision is recorded.
  Ask the user to accept the reading. Then state it in Scope 4,
  acceptance 3 and the plan: equal namespace sets, and equal label
  vectors of each namespace in report order. The namespace order is a
  Scry property.
- [x] Update the suite time in `mementum/state.md` (line 71: "about 11
  minutes in total at limit 4"). The final after record at `1be42d8`
  gives 548.54 s (about 9 minutes).
  - review pass 13 fix: "about 9 minutes".

## Implementation test review (pass 19)

- [x] Add in-process false cases for the two remaining shared verdict
  predicates of unavailable cells, as pass 15 did for `rejections` and
  `persisted-output?`. The cross-JVM runs show only true results:
  - `array-capability/rejection?` (only verdict of the rejection cells
    of `array-boundary-test`, `array-matrix-test` and
    `array-sequence-boundary-test`): show true for a matching outcome;
    false for another `:value-type/error`, another reason, a
    `:cause` that differs from the reason rule (an
    `IllegalAccessException` cause for `:missing-classfile-api`, no cause
    for `:array-access-denied`), a message without `--add-exports`, and a
    nil `:message`.
  - `consumer-aot-checks/unavailable-outcome?` (only verdict of the
    unavailable cells of `field-transfer-test` and
    `identity-receiving-cross-jvm-test`): show true for a matching outcome
    with extra names; false for `:absent? false`, another reason, another
    class name, and a message without one extra name.
  Put the cases in `test/cross-jvm/` (in-process; for example
  `cell-outcomes-test` or a new namespace added to the in-process command
  in `doc/development/runtime.md`); new labels only.
  - test review pass 19 fix: `value-type.unavailable-predicates-test`
    (`array-rejection-test`, `unavailable-outcome-test`); namespace added
    to the in-process command in `doc/development/runtime.md`.

## Implementation test review (pass 20)

- [x] Add the false cases for the fixed message parts of the two
  predicates of pass 19. `unavailable-predicates-test` removes only one
  extra name, and the `array-rejection-test` message case removes only
  `--add-exports`. Thus, if `unavailable-outcome?` dropped
  `unavailable-names` (class name, "fixed value representation is
  unavailable", `--enable-preview`) from its message check, or
  `array-capability/rejection?` dropped its `--enable-preview` check, no
  test fails (the cross-JVM cells show only true results). Show false for
  an unavailable message without the class name and without
  `--enable-preview`, and for an array message without `--enable-preview`.
  New labels only.
  - test review pass 20 fix: 3 cases in `unavailable-predicates-test`
    (`67a57ec`).
- [x] Retake the `cross-jvm` after record after the last code change.
  `after-record/cross-jvm.edn` is from `1be42d8` and has 24 namespaces;
  it has no labels of `value-type.unavailable-predicates-test`
  (pass 19), and the recorded assertion count (4297) is old. In-process
  namespaces are part of the record (for example `aot-fixture-test`).
  Run the `:cross-jvm-test` command at limit 4 and limit 1 with
  `VALUE_TYPE_TEST_RECORD`, run `check_record.clj` with the current
  fallback records, replace `after-record/cross-jvm.edn`, and record the
  commit, assertion count and acceptance 3 result in `implementation.md`.
  - test review pass 20 fix: record at `67a57ec`; 4313 assertions;
    acceptance 3 holds (implementation.md).

## Code-shaper review (pass 21)

- [x] Use one data shape for the label mappings in `check_record.clj`.
  `moved-vars` is a set of Var name strings (checked with a namespace
  string in `rename`), `mode-renamed-vars` is a set of qualified Var
  symbols, and `fixed-tag-var` is one qualified symbol. `rename` selects
  the context change with `cond`, so a Var can get only one change.
  Use qualified Var symbols in each mapping (for example `moved-vars`: old
  Var → new Var; `context-renames`: Var → function of one context), and
  make `rename` apply each mapping that names the Var. Also destructure
  the label once in `assertion-labels` (`{[v :as label] :label}`).
  Findings unchanged (0 mapping findings on `target/test-record/after17`).
  - code-shaper pass 21 fix: `moved-vars` (Var → Var),
    `context-renames` (Var → context functions); 0 mapping findings on
    `after17` and `after18`.
- [x] Use one idiom in the two tests of `unavailable-predicates-test`.
  `unavailable-outcome-test` has one base outcome (`unavailable`), a local
  predicate (`unavailable?`) and `without` for each message case.
  `array-rejection-test` writes the reason twice in each `is` (predicate
  argument and `array-rejection` argument, 9 times) and replaces the
  whole message with a new literal for each message case. Give it a base
  outcome of each reason, a local predicate, and use `without` for the
  message cases. Labels and expectations unchanged.
  - code-shaper pass 21 fix: bases `missing-api`, `access-denied`; local
    predicates; shared `without` of an outcome.
- [x] Use `message-checks/message-names?` in `array-capability/rejection?`,
  as `consumer-aot-checks/unavailable-outcome?` does. It writes its own
  nil guard and two `(boolean (re-find #"..." message))` calls, and the
  parts `"--enable-preview" "--add-exports"` are written again in
  `rejection-description`. Bind the parts once and use them in both
  functions. Descriptions, labels and expectations unchanged.
  - code-shaper pass 21 fix: private `rejection-names` in both functions.

## Code-shaper review (pass 22)

- [x] Use the `transfer-checks` axes and evaluation rule in
  `isolated-sibling-loader-test/public-isolated-sibling-construction-test`,
  as passes 17–18 did in `identity-loader-checks` and
  `identity-receiving-test`. It is the last copy: it writes the axes as
  literals (`[macroexpand macroexpand-1]`, `[:eval :function :source]`),
  names a mode `analysis` (pass 20 set `mode`), and has its own `case`
  with the mode `:function`, which calls the function outside its eval
  (`((eval (list 'fn [] selected)))`; the
  `transfer-checks/evaluate-form` docstring states that this call can keep
  a stale import, and this test imports the child class). Use
  `transfer/expands` with `transfer/expanders`, `transfer/modes` and
  `transfer/evaluate-form`, and wrap the 180-character `doseq` binding
  line. Add the Var to `mode-renamed-vars` in `check_record.clj` (or the
  mapping of the open pass 21 step), and record the changed label part
  (`:function` → `:analysis`) and any changed order in
  `implementation.md`. Expectations unchanged.
  - code-shaper pass 22 fix: done; Var in `context-renames`; label part
    in implementation.md.
- [x] Use one source for the policy JVM option. `jvm-runner/start-command`
  writes `(str "-Dvalue-type.fallback=" policy)` to start a child, and
  `array-capability/expected-flags` writes it again as the expected
  input argument of that child. When one copy changes, the array cells
  fail for a reason that is not in the product. Add one public
  `jvm-runner` function of policy (for example `policy-option`) and use
  it in both places. Labels and expectations unchanged.
  - code-shaper pass 22 fix: `jvm-runner/policy-option`.

## Code-shaper review (pass 23)

- [x] Write the expected chain class of
  `aot-test/source-free-value-aot-loading-test` once, and return a
  boolean from its predicate. The unavailable branch writes
  `"java.lang.UnsupportedClassVersionError"` in the description and again
  in the predicate, and the predicate returns the `some` value (a string
  or nil). The other described predicates return booleans
  (`aot-fixture/persisted-output?`, `array-capability/rejection?`). Bind
  the class name once (as pass 20 did in `array-info-boundary-test`) and
  return `(boolean ...)`. Description, labels and expectations unchanged.
  - code-shaper pass 23 fix: `chain-class` bound once; `boolean`.
- [x] Use one source for the inspection child and the producer runtime of
  the AOT inspection tests. `aot-fixture/inspection-cell` fixes the
  inspected class (`checked/aot_fixture/Holder`), but each caller writes
  the child configuration `(assoc valhalla :policy "error" :fixture-ns
  'checked.aot-fixture)` again (`public-field-aot-test/inspection`,
  `public-mixed-field-aot-test/persisted-public-mixed-output-test`). Add
  one `aot-fixture` function of the jvm, output and cells (for example
  `inspection-child`) that gives this child, and use it in both places.
  Also `field-aot-test` writes the value producer runtime
  `(assoc valhalla :policy "error" ...)` twice
  (`persisted-restricted-field-output-test`, and `artifact` in
  `separately-compiled-restricted-field-consumer-test`, which goes to
  `compile-producer`); use `(config/value-producer configuration)` there
  (pass 13). Labels and expectations unchanged.
  - code-shaper pass 23 fix: `aot-fixture/inspection-child` (jvm, output,
    `[context field expected]` cells); `config/value-producer` twice.
- [x] Write the default child kind once in `jvm-runner/run-jvms`. The
  default `:kind :cell` is in two destructurings (the `child-start!` loop
  and the read-error loop). When one default changes, the recorder and
  the read rule give different kinds to one child. Add one private
  function of an option map (for example `kind`: `(get options :kind
  :cell)`) and use it in both places. Behavior unchanged.
  - code-shaper pass 23 fix: private `kind`.
- [x] Use one idiom for the namespace of a label or Var in
  `label-record/summary`. It has `label-ns` for labels, but writes
  `(some-> % :var namespace symbol)` for the Vars and `(= (str n)
  (namespace var))` for the namespace time. Use one private function of a
  Var symbol (for example `var-ns`) in the three places. Summaries
  unchanged.
  - code-shaper pass 23 fix: private `var-ns`; `check_record.clj` output
    equal on `after19`.

## Code-shaper review (pass 24)

- [x] Use one source for the construction axis of `consumer-aot-checks/builders`.
  `builders` gives a positional vector (direct, factory, reflective), and
  its names are written as `(zipmap [:direct :factory :reflective] ...)`
  three times (`identity-loader-checks` (`constructors`),
  `identity-receiving-checks/nested-warning-scope`,
  `identity-receiving-test/receiving-identity-fields-test`). The count of
  the axis is written as `(vec (repeat 3 x))` (one expected value for each
  construction) about 13 times: `consumer-aot-checks/holder-result`,
  `field-capability-test`, `identity-receiving-cross-jvm-test` (2),
  `controlled-field-capability-test/expected`, `field-transfer-test` (2),
  `child-transfer-test`, `field-aot-test`, `function-redefinition-test`
  (2). When `builders` gets or loses an entry, these copies do not change.
  Add a public `consumer-aot-checks/constructions` (`[:direct :factory
  :reflective]`, the order of `builders`) and one function of an expected
  value that gives it for each construction (for example
  `each-construction`), and use them in these places. Expectations and
  labels unchanged.
  - code-shaper pass 24 fix: `consumer-aot-checks/constructions`,
    `each-construction`; 3 `zipmap` and 13 `repeat 3` copies.
- [x] Use `cell-registry/twice` in
  `function-redefinition-test/repeated-definition-cell`. Its child form
  writes `definitions [(define) (define)]` (passes 16 and 20 did not list
  it), and the form already requires `value-type.cell-registry`. Also bind
  the context part `(pr-str [:controlled-capability policy execution])`
  once in `controlled-capability-repeated-definition-test` (written for
  each of the two cells). Results and labels unchanged.
  - code-shaper pass 24 fix: `twice`; `context` in the `for` `:let`.
- [x] Use one function for the controlled absence form. The binding
  `cell-harness/controlled-absence` is wrapped as `(with-redefs
  controlled-absence body)` four times: `controlled-field-capability-test`
  `definition-cell`, the `:produce` `:wrap` and the `java-consumer-form`
  `:wrap` of `controlled-aot-consumers`/`controlled-native-absence-java-aot-test`,
  and the `CONTROLLED-ABSENCE` template of `function-redefinition-test`.
  Each place must also load `value-type.impl.runtime` first, and does so in
  its own way (a `require` in the cell form, a `do` in the `:wrap`, the
  `:requires` option). Replace the binding with one public `cell-harness`
  function of a body form that gives `(do (require
  'value-type.impl.runtime) (with-redefs [...] body))`, and use it in the
  four places (in the template, for example a `CONTROLLED` placeholder of
  the body). Labels and expectations unchanged.
  - code-shaper pass 24 fix: `cell-harness/controlled-absence` (body →
    form); 5 places in `controlled-field-capability-test` (also
    `controlled-expansions`), `CONTROLLED` placeholder; `:requires` option
    of `java-consumer-form` removed (no other caller).
- [x] Wrap the docstring lines over 80 characters, as pass 18 did:
  `aot-fixture/compile-producer` (81) and `consumer-child` (97),
  the namespace docstrings of `identity-loader-checks` (81),
  `array-matrix-checks` (84) and `cell-runner-test` (97),
  `transfer-test` line 25 (81), `consumer-aot-checks/value-class?` (81)
  and `function-redefinition-test/repeated-definition-cell` (82). Text
  unchanged.
  - code-shaper pass 24 fix: done; `repeated-definition-cell` names the
    `CONTROLLED` placeholder.

## Code-shaper review (pass 25)

- [x] Use one source for the expected values of the two-attempt axis, as
  pass 24 did for the construction axis. `cell-registry/twice` runs an
  attempt two times, but the expected attempts are written in three
  shapes in about 20 places: `(vec (repeat 2 x))` (for example
  `identity-loader-checks/public-stale-sibling-expected`,
  `runtime-test` (`discarded-attempts`, `fallback-expansion-policy-test`),
  `aot-test` (2), `transfer-test` (3), `function-aot-checks/install-result`,
  `field-capability-test/policy-expected`, `field-transfer-test/attempts-of`,
  `child-transfer-test`), `[x x]` (`field-capability-test`,
  `identity-receiving-cross-jvm-test/receiving-policy`,
  `field-transfer-test` (2), `runtime-test` `[:allowed :allowed]`), and
  the description `(list 'repeat 2 x)` (`array-matrix-test`,
  `array-boundary-test`, `array-sequence-boundary-test`,
  `array-info-boundary-test`, `field-transfer-test/unavailable`). The
  count is also checked as `(= 2 (count outcomes))`
  (`array-capability/repeated?`) and `(= [2 2] (mapv count attempts))`
  (`field-transfer-test/unavailable`). When `twice` changes, these copies
  do not. Add to `cell-registry`, beside `twice`, a public attempt count
  and one function of an expected value (for example `attempt-count`,
  `each-attempt`), define `twice` with the count or check it against the
  count, and use them in these places (the description as `(list 'repeat
  registry/attempt-count x)`). Check each place: a `repeat 2` of another
  axis (for example the expands of
  `identity-receiving-cross-jvm-test/strict-nested-public-declaration-test`,
  `(count transfer/expands)`) is not the attempt axis. Labels and
  expectations unchanged.
  - code-shaper pass 25 fix: `cell-registry/attempt-count`,
    `each-attempt`; `twice` uses the count; not the attempt axis (kept):
    `receiving-policy` (two holders), `concurrent-defined` (two types),
    `invalid-forms` (two forms); expand axis as `(count transfer/expands)`.
- [x] Use the `transfer-checks` expand axis in the remaining copies, as
  passes 18, 19 and 22 did. `identity-receiving-checks/nested` writes
  the axis as symbols (`'[macroexpand macroexpand-1]`) and calls the
  expander through `eval` of a list; `expansion-checks` writes it twice
  more with its own names (`expansion-attempts`: `[[macroexpand "Full"]
  [macroexpand-1 "One"]]`; `warning-timing`: `{:full macroexpand :one
  macroexpand-1}`) and names an expander `operation`;
  `public-field-annotations-test` writes `[macroexpand macroexpand-1]` and
  names an expander `expansion` (pass 20 set `expand`). Use
  `transfer/expands` with `transfer/expanders` in these places (call the
  expander with `*ns*` bound to the cell namespace), and name the local
  `expander`. Keep label parts and generated type names (`:full`/`:one`,
  `"Full"`/`"One"`) or record each change in `implementation.md` and in
  `check_record.clj`. Expectations unchanged.
  - code-shaper pass 25 fix: done; runtime-test label part `:full`/`:one`
    → `:macroexpand`/`:macroexpand-1`, type names `Expand<i>`
    (implementation.md, `check_record.clj` `expand-keyword`).
- [x] Use one template mechanism in
  `function-redefinition-test/cell-form`. Pass 24 added the `CONTROLLED`
  placeholder, so `cell-form` has its own `postwalk` with a `cond` for two
  placeholder kinds (a symbol and a call), where `warning-checks` uses
  `walk/postwalk-replace` for a symbol placeholder. Keep only the
  `DECLARATION` placeholder in the template body (the `let`), and build
  the cell form in `cell-form` as `(list 'do '(require ...)
  (harness/controlled-absence (walk/postwalk-replace {'DECLARATION
  declaration} body)))`. Also require `clojure.walk` with the alias
  `walk`, as `cell-harness` and `warning-checks` do. Forms (equal data),
  labels and expectations unchanged.
  - code-shaper pass 25 fix: `repeated-definition-body`; forms equal
    (modulo `#()` gensym).

## Code-shaper review (pass 26)

- [x] Use one source for the expected values of the expand axis, as pass
  24 did for constructions and pass 25 for attempts. Pass 25 wrote the
  count as `(count transfer/expands)` in five places, in three shapes:
  `(vec (repeat (count transfer/expands) x))` (`runtime-test`
  `fallback-expansion-policy-test`, `field-transfer-test/attempts-of`,
  `identity-receiving-cross-jvm-test/strict-nested-public-declaration-test`),
  `(list 'repeat (count transfer/expands) ...)` and `(repeat (count
  transfer/expands) registry/attempt-count)` (`field-transfer-test/unavailable`).
  `field-transfer-test/cell-expansions` still writes the count as `2`
  (`(subvec expansions (* 2 i) (* 2 (inc i)))`, docstring "the two
  expansions"; also "both expansions" in `each-expansion` and two
  comments). Add `transfer-checks/each-expand` (expected for each expand,
  as `each-attempt`) and use it in these places, and use `(count
  transfer/expands)` in `cell-expansions`. In the same test, the inner
  `(vec (repeat 2 (rejected detail)))` is the parent axis of
  `identity-receiving-checks/nested` (the literal `'[FirstParent
  SecondParent]`): add a public `identity-receiving-checks/parents` and
  use its count there. Labels and expectations unchanged.
  - code-shaper pass 26 fix: `transfer-checks/each-expand`,
    `identity-receiving-checks/parent-types` (`parents` shadows
    `clojure.core/parents`; child stderr warning); descriptions keep
    `(list 'repeat ...)`.
- [x] Use one runner for the attempt axis. `cell-registry/twice` (f of no
  argument, `(into [] (map (fn [_] (f))) (range attempt-count))`) and the
  private `expansion-checks/attempts` (f of the attempt index, `(mapv f
  (range registry/attempt-count))`) run the same axis with two idioms.
  Add one public `cell-registry` function of f of the attempt index (for
  example `attempts`), define `twice` with it, and remove the private
  copy. Also `function-redefinition-test/repeated-definition-body`
  compares only the first two owners (`(not (identical? (:owner (first
  definitions)) (:owner (second definitions))))`); write `:distinct-classes?`
  for all attempts (for example `(apply distinct? (map :owner
  definitions))`). Results and labels unchanged.
  - code-shaper pass 26 fix: `cell-registry/attempts`; `apply distinct?`.
- [x] Use the `consumer-aot-checks` construction rules in
  `identity-loader-checks`, as pass 24 did in `stale-sibling-case`.
  `public-stale-sibling-expected` writes the construction axis as a
  literal in another order (`(zipmap [:factory :direct :reflective]
  ...)`), and the private `outcome` gives the nil and wrong-type outcomes
  in its own shape (`:null-pointer`, `:class-cast` keywords, with
  `:stored`/`:not-stored`), where the other checks use
  `checks/checked-outcomes` with `checks/checked-rejections` (class
  names). Use `consumer-checks/constructions` in the expected value, and
  for each construction give the stored fact (for example `:stored?`) and
  `(consumer-checks/checked-outcomes construct stale-child)` expected as
  `checked-rejections`; remove `outcome`. Labels unchanged; the
  expectation is the same fact in the shared shape.
  - code-shaper pass 26 fix: `{:stored? :rejections}` for each
    construction; `outcome` removed.

## Code-shaper review (pass 27)

- [x] Use `cell-registry/each-attempt` in
  `consumer-aot-checks/incompatible-result`. It writes the attempt axis as
  a literal (`:loads [:unsupported-class-version
  :unsupported-class-version]`, docstring "both loads"), and
  `incompatible-consumer` makes the loads with `registry/twice`; pass 25
  did not list it. Write `(registry/each-attempt
  :unsupported-class-version)` and "each load". Expectations and labels
  unchanged.
  - code-shaper pass 27 fix: done.
- [x] Use one entry shape for the result of each construction in the two
  inline child checks. The other checks give a map for each construction
  with the stored value and `checked-outcomes` (`holder-consumer`,
  `identity-receiving-checks/generated-value`,
  `field-policy-checks/evaluation`: `{:stored v :rejections [...]}`).
  `controlled-field-capability-test/holder-check` gives a positional
  vector (`(into [stored] (checked-outcomes ...))`, expected `(into [7]
  checked-rejections)`). `function-redefinition-test/repeated-definition-body`
  gives `:stored` once (factory instance only) and `:rejections` as a
  vector of outcome pairs; it also merges two literal maps (`(merge
  (field-facts field) {...} {:new-class? ...})`). Give `{:stored
  :rejections}` for each construction under `:constructions` in both,
  write one literal map in the merge, and write the deftype rejections
  from the outcome axis (for example `(mapv (constantly :accepted)
  checks/checked-rejections)`, not `[:accepted :accepted]`). Labels
  unchanged; the expectation is the same fact in the shared shape.
  - code-shaper pass 27 fix: `{:stored :rejections}` under
    `:constructions` in both; one literal map.
- [x] Use one key name for the construction results and for the stored
  value. `holder-consumer`, `identity-receiving-checks`,
  `field-policy-checks`, `child-transfer-checks` and `field-aot-test` use
  `:constructions`; `field-transfer-checks` (`consume`, `consume-mixed`)
  and `identity-loader-checks/stale-sibling-case` use `:construction`.
  `field-transfer-checks/consume` names the stored value `:field` (others
  `:stored`). `field-policy-checks/evaluation` writes the field fact
  `:type` in each construction entry (the same value three times); give
  it once in the attempt map, as `field-transfer-checks/consume` does.
  Rename to `:constructions` and `:stored`, and update the expected
  values (`field-transfer-test`, `field-capability-test`,
  `identity-loader-checks/public-stale-sibling-expected`). Labels
  unchanged; the expectation is the same fact in the shared shape.
  - code-shaper pass 27 fix: `:constructions`, `:stored` (also
    `consume-mixed` `:fields` → `:stored`); `:type` in the attempt map.

## Code-shaper review (pass 28)

- [x] Use one alias for each shared check namespace, as pass 16 did for
  `cell-registry`. `value-type.consumer-aot-checks` is `checks` in 18
  namespaces and `consumer-checks` in 5 (`aot-fixture`,
  `identity-loader-checks`, `aot-checks`,
  `controlled-field-capability-test`, `public-field-aot-test`).
  `value-type.array-capability` is `capability` in 4 namespaces and
  `array` in `unavailable-predicates-test`. The alias `checks` names four
  namespaces (`consumer-aot-checks`, `function-aot-checks` in
  `public-field-aot-test`, `identity-loader-checks` in
  `identity-loader-test` and `loader-policy-test`, and the child requires
  of `validation-checks` and `expansion-checks` in `runtime-test`). Use
  `checks` only for `consumer-aot-checks`, `function-checks` for
  `function-aot-checks` (as 2 namespaces do), `loader-checks` for
  `identity-loader-checks`, and `capability` for `array-capability`.
  Behavior unchanged.
  - code-shaper pass 28 fix: `checks` (consumer-aot-checks), `function-checks`,
    `loader-checks`, `capability`; child requires `validation`, `expansion`.
- [x] Use one function for the construction results of a checked field.
  `(mapv (fn [build] {:stored (.get field (build [valid])) :rejections
  (checks/checked-outcomes build wrong)}) (checks/builders n owner))` is
  written in `field-policy-checks/evaluation`,
  `identity-receiving-checks/generated-value`, the
  `controlled-field-capability-test/holder-check` child function and
  `function-redefinition-test/repeated-definition-body`;
  `field-transfer-checks/consume-supported` adds `:class?` to it. Add one
  public `consumer-aot-checks` function of ns-or-name, owner, field, valid
  and wrong (for example `construction-results`) and use it in these
  places (merge `:class?` in `consume-supported`). Also name an entry of
  `builders` `build` in each place, as `checked-outcomes` does
  (`construct` in `field-transfer-checks` (2), `identity-loader-checks`,
  `controlled-field-capability-test`, `function-redefinition-test`).
  Results and labels unchanged.
  - code-shaper pass 28 fix: `consumer-aot-checks/construction-results`
    (`:facts` option merges `:class?`); entries named `build`.
- [x] Use one key for each field fact and for the stored-input fact. The
  field type class name is `:type` in `consumer-aot-checks/field-facts`,
  `nested-warning-scope`, `field-policy-checks` and
  `field-transfer-checks/consume-supported`, but `:storage` in
  `identity-receiving-checks` (`receiving-policy`, `generated-value`) and
  `:types` in `consume-mixed`; four of these read
  `(.getName (.getType field))` again in place of `field-facts`.
  "The stored value is the input" is `:stored?`
  (`identity-loader-checks`), `:retained?`
  (`child-transfer-checks/consume`) and `:input?`
  (`identity-receiving-checks/nested-warning-scope`). Use `field-facts`
  (or its `:type`) for the field type, rename `:storage` to `:type`, use
  one key for the stored-input fact (for example `:stored-input?`), and
  update the expected values (`identity-receiving-cross-jvm-test`,
  `child-transfer-test`, `identity-loader-checks/public-stale-sibling-expected`).
  Labels unchanged; the expectation is the same fact in the shared shape.
  - code-shaper pass 28 fix: `field-facts` for the field type;
    `:storage`, `:types` → `:type`; `:stored-input?`; `generated-value`
    merges `field-facts` (expected adds `:final? true`).
- [x] Use one data shape for `:constructions`, as pass 27 did for the
  entry. Each check gives a vector in the order of
  `consumer-aot-checks/constructions`, but
  `identity-loader-checks/stale-sibling-case` gives a map keyed by
  construction (`zipmap` and `update-vals`; expected with `zipmap` in
  `public-stale-sibling-expected`), and
  `identity-receiving-checks/nested-warning-scope` gives `:rejections` as
  a vector of outcome pairs, one for each construction (expected
  `(checks/each-construction checks/checked-rejections)`), the shape that
  pass 27 removed from `function-redefinition-test`. Give a vector of
  entries under `:constructions` in both, and expect it with
  `checks/each-construction`. Labels unchanged; the expectation is the
  same fact in the shared shape.
  - code-shaper pass 28 fix: vectors of entries; `nested-warning-scope`
    entries `{:stored-input? :rejections}` (implementation.md).
- [x] Use one source for the holder axis of
  `identity-receiving-checks/receiving-policy`, as pass 26 did for
  `parent-types`. The check writes `'[FirstHolder SecondHolder]` and
  `receiving-identity-policy-test` expects `[expected expected]` (pass 25
  kept it as "two holders"). In the same test namespace,
  `nested-public-warning-scope-test` writes the expand and parent axes as
  `[[declaration declaration] [declaration declaration]]`, but
  `strict-nested-public-declaration-test` writes them with
  `transfer/each-expand` and `(count identity-checks/parent-types)`. Add
  a public `identity-receiving-checks/holder-types`, use it in
  `receiving-policy`, and write the three expected values from the axes
  with one idiom (for example one private function of an expected value
  for each parent type). Labels and expectations unchanged.
  - code-shaper pass 28 fix: `identity-receiving-checks/holder-types`;
    private `each-type` of a type axis.
- [x] Write the expected definition map of a new Class with factory
  identity once. `{:new-class? true :factory? true}` (the success result
  of `cell-registry/defined`) is written about 22 times in expected
  values: `runtime-test` (6), `aot-test` (2), `transfer-test` (2),
  `identity-loader-checks`, `field-capability-test`, `array-matrix-test`,
  `array-sequence-boundary-test`, `identity-receiving-cross-jvm-test`
  (private `defined`), `cell-runner-test` (private `defined`),
  `controlled-field-capability-test`, `field-transfer-test` (2),
  `child-transfer-test` (2), `field-aot-test` (2). Add a public
  `cell-registry` value beside `defined` (for example `new-definition`)
  and merge or use it in these places; keep the literals in
  `cell-registry-test`, which checks `defined`. Labels and expectations
  unchanged.
  - code-shaper pass 28 fix: `cell-registry/new-definition`.
- [x] Use `consumer-aot-checks/call-form` in `java-consumer-form`. It
  writes the `call-form` shape again (`do`, the `require` of the
  namespace, the qualified call), with an optional `:wrap` of the call.
  Give `call-form` the `:wrap` option (default `identity`) and define the
  four `-form` functions with it. Forms (equal data) and labels
  unchanged.
  - code-shaper pass 28 fix: `call-form` takes an args vector and `:wrap`.

## Code-shaper review (pass 29)

- [x] Use one rule for a child call form. `cell-harness/check-form`
  (`do`, the `require` of the namespace of check, the call) is the shared
  rule, but `consumer-aot-checks/call-form` (pass 28) and
  `function-aot-checks/invoke-install-form` write it again. The two copies
  are in child-safe namespaces, which do not load `cell-harness`. Put one
  public function of a qualified symbol and args in a child-safe
  namespace that the three namespaces can require (for example move
  `check-form` to `cell-registry` or to a new small namespace), define
  `harness/check-form` callers, `call-form` and `invoke-install-form` with
  it, and remove the copies. For `:wrap`, wrap the whole form (for example
  `(harness/controlled-absence (checks/java-consumer-form ...))`;
  `controlled-absence` loads `value-type.impl.runtime` first). Labels and
  expectations unchanged.
  - code-shaper pass 29 fix: `cell-registry/check-form` (moved from
    `cell-harness`); `call-form` removed; `invoke-install-form` uses it;
    the Java consumer form is wrapped whole, after a `require` of
    `value-type.consumer-aot-checks` (implementation.md).
- [x] Use `consumer-aot-checks/field-facts` in
  `function-aot-checks/inspect-holder`. It writes `:type (.getName
  (.getType field))` and `:annotations (checks/annotation-names field)`
  again (pass 28 removed the other copies), and names the stored value
  `:value` (pass 27 set `:stored`). Merge `field-facts` with `:stored`,
  and update the expected values (`public-field-aot-test`,
  `public-native-field-aot-test`, `public-mixed-field-aot-test`). Labels
  unchanged; the expectation is the same fact in the shared shape
  (`:final?` added).
  - code-shaper pass 29 fix: `field-facts` with `:stored`.
- [x] Use one function for the stored-input construction results, as pass
  28 did for `construction-results`. `(mapv (fn [build] {:stored-input?
  (identical? input (.get field (build [input]))) :rejections
  (checks/checked-outcomes build wrong)}) (checks/builders n owner))` is
  written in `child-transfer-checks/consume`,
  `identity-receiving-checks/nested-warning-scope` and
  `identity-loader-checks/stale-sibling-case`. Add one public
  `consumer-aot-checks` function beside `construction-results` (for
  example `input-results`: ns-or-name, owner, field, input, wrong) and
  use it in the three places. Also `public-stale-sibling-expected` picks
  `:new-class?` and `:factory?` out of `registry/new-definition` one by
  one; merge `registry/new-definition`, as `defined-expected` does.
  Results, labels and expectations unchanged.
  - code-shaper pass 29 fix: `consumer-aot-checks/input-results`; merge of
    `registry/new-definition`.
- [x] Use one key and one function for the facts of a fallback identity
  type. Pass 27 set `:stored` for a stored value, but `:field` names it in
  `transfer-checks` `attempt`, `warning-checks/define-types`,
  `aot-checks` (`compile-fixture`, `load-fixture`, `load-value-fixture`,
  `invoke-runtime-fixture`) and `field-aot-test/source-free-consumer`.
  The identity fact `:identity? (not= a b)` of two factory calls is
  written in `transfer-checks`, `field-transfer-checks/consume-supported`,
  `warning-checks` and `aot-checks` (3). `aot-checks` also gives
  `:factory?` two other meanings (`load-fixture`: the class name of the
  instance; `invoke-runtime-fixture`: the defined Class is the instance
  Class) than `registry/defined` (import and factory identity). Rename
  `:field` to `:stored`, add one public function of a factory and args
  (for example `cell-registry/identity?`) and use it in these places, and
  rename the two `aot-checks` `:factory?` keys (for example
  `:binary-name?`, `:factory-class?`). Update the expected values
  (`transfer-test`, `aot-test`, `runtime-test`, `field-aot-test`). Labels
  unchanged; the expectation is the same fact in the shared shape.
  - code-shaper pass 29 fix: `:stored`, `cell-registry/identity?`,
    `:binary-name?`, `:factory-class?`.
- [x] Use `consumer-aot-checks/checked-outcomes` in
  `field-aot-test/source-free-consumer`. It writes `(mapv (fn [value]
  (value-type.consumer-aot-checks/outcome #(build value))) [nil "wrong"])`,
  a copy of `checked-outcomes`. Keep the compiled `construct` entries
  (the separately compiled consumer calls them), and write
  `(value-type.consumer-aot-checks/checked-outcomes build "wrong")` with
  entries that take `[x]`, as `builders` entries do. Labels and
  expectations unchanged.
  - code-shaper pass 29 fix: entries take `[x]`; `checked-outcomes`;
    `:field` → `:stored`.
- [x] Use one source for the type axis of `warning-checks/define-types`,
  as passes 26 and 28 did for `parent-types` and `holder-types`. It writes
  `'[FirstIdentity SecondIdentity]`, and
  `transfer-test/concurrent-defined` expects `(vec (repeat 2 ...))` (pass
  25 kept it as "two types"). Add a public `warning-checks/identity-types`,
  use it in `define-types`, and write the expected value from its count.
  Labels and expectations unchanged.
  - code-shaper pass 29 fix: `warning-checks/identity-types`.
- [x] Rename the private `field-policy-checks/attempts`. Pass 26 made
  `cell-registry/attempts` the runner of the attempt axis (f of the
  attempt index), and removed the private `expansion-checks/attempts`.
  `field-policy-checks/attempts` is another function (capability
  `:detail` and `twice` with the root failure). Give it a name of its
  result (for example `policy-attempts`). Behavior unchanged.
  - code-shaper pass 29 fix: `policy-attempts`.

## Code-shaper review (pass 30)

- [x] Make `cell-harness/controlled-absence` load the namespaces of its
  body. Pass 29 found that the body is compiled as one form, so a
  `require` in the body runs too late; the docstring now states a caller
  rule ("the caller loads the namespaces that body names before this
  form"), and each of the five callers writes `(list 'do '(require ...)
  (harness/controlled-absence body))` again
  (`controlled-field-capability-test`: `definition-cell`,
  `controlled-expansions`, `saved-cell`,
  `controlled-native-absence-java-aot-test`;
  `function-redefinition-test/cell-form`). Give `controlled-absence` the
  namespaces as an argument (for example `[requires body]` → `(do (require
  'value-type.impl.runtime ~@requires) (with-redefs [...] body))`), use it
  in the five places and in the `:produce` `:wrap` (no namespaces), and
  remove the caller rule from the docstring. Forms load the same
  namespaces; labels and expectations unchanged.
  - code-shaper pass 30 fix: `controlled-absence` (requires, body); 5
    callers; `:wrap` `(partial harness/controlled-absence [])`.
- [x] Use one source for the invalid declaration axes of the runtime
  checks, as passes 26, 28 and 29 did for `parent-types`, `holder-types`
  and `identity-types`. `validation-checks/invalid-forms` writes its field
  vectors as a literal (`['[__hash] '(x)]`), and
  `runtime-test/source-field-validation-test` expects `(vec (repeat 2
  ...))` (pass 25 kept it as "two forms"). `expansion-checks/invalid-constrained-discarded`
  writes five metadata maps, and
  `runtime-test/constrained-validation-before-policy-test` writes the five
  expected reasons (`[:invalid-non-nil ... :interface-type]`) in a second
  list that pairs with it only by position. Add a public
  `validation-checks/invalid-fields` and use its count in the expected
  value; add a public `expansion-checks/invalid-declarations` (pairs of
  metadata and expected reason), use its metadata in the check and its
  reasons in the expected value. Labels and expectations unchanged.
  - code-shaper pass 30 fix: `validation-checks/invalid-fields`,
    `expansion-checks/invalid-declarations`; `runtime-test` requires both.
- [x] Write the discarded-expansion result once. `expansion-checks`
  builds `{:returned? (some? x) :absent? (registry/absent? cell-ns
  type-name)}` in `attempt` and again in `warning-timing` (`expanded`).
  `runtime-test` writes the expected `{:returned? true :absent? true}`
  three times (`fallback-expansion-policy-test`; description and
  predicate of `fallback-expansion-warning-timing-test`) and `(assoc
  registry/new-definition :returned? true)` twice (`defined`,
  `evaluated`). Add one private `expansion-checks` function of cell-ns,
  type-name and the expansion, use it in both places, and bind each
  expected value once in `runtime-test` (private defs). Labels and
  expectations unchanged.
  - code-shaper pass 30 fix: private `discarded-expansion`;
    `runtime-test` `discarded-expansion`, `returned-definition`.

## Code-shaper review (pass 31)

- [x] Use one function for the factory symbol of a type name. The rule
  `(symbol (str "->" name))` is written in `cell-registry` (`absent?`,
  `defined`), `warning-checks/define-types`,
  `identity-receiving-checks` (`:factory` of the nested declarations),
  `consumer-aot-checks` (`builders`: `(symbol (str ns-or-name) (str "->"
  (.getSimpleName owner)))`; the source-free load check: `(symbol ns (str
  "->" (last (.split owner "\\."))))`) and 7 times in `fields-test`. Add
  one public child-safe `cell-registry` function of a simple name (for
  example `factory-symbol`, with an optional namespace arity) and use it
  in these places. Results and labels unchanged.
  - code-shaper pass 31 fix: `cell-registry/factory-symbol` (1 and 2
    arities); `fields-test` requires `cell-registry`.
- [x] Use one source for the runtime version rule and the preview option.
  `jvm-config/configuration` selects the runtime by the `java.version`
  prefixes `"1.8."` and `"27-jep401ea"`, and
  `array-info-boundary-test/runtime-prefix` writes the two prefixes again
  as the expected runtime of a child. `configuration` also writes
  `"--enable-preview"` twice (the option filter and the Valhalla
  `:jvm-opts`). When one copy changes, the array cells fail for a reason
  that is not in the product. Add public `jvm-config` values of the two
  prefixes and of the preview option, and use them in these places.
  Descriptions, labels and expectations unchanged.
  - code-shaper pass 31 fix: `jvm-config/baseline-version-prefix`,
    `valhalla-version-prefix`, `preview-option`.
- [x] Write the optional implementation namespaces of `runtime-test` once.
  `capability-test` and `public-require-test` each write `(boolean (some
  find-ns '[value-type.impl.loader value-type.impl.transform]))` in their
  child forms (line 34 is 102 characters). Bind the namespace vector once
  (a private def) and build both forms with it (for example `(list
  'quote optional-namespaces)`), as `array-info-boundary-test/control-form`
  builds its form. `capture-loading-test` checks another set (it loads
  `value-type.impl.loader`); keep it. Forms (equal data), labels and
  expectations unchanged.
  - code-shaper pass 31 fix: private `optional-loaded-form`; forms equal
    data.
- [x] Use one idiom for literal text in a pattern, and one function for
  the occurrence count of the description form `(repeat n x)` in a string.
  `cell-harness/err-matches?` quotes `warning-prefix` with `"\\Q" ...
  "\\E"`; `runtime-test/fallback-expansion-warning-timing-test` and
  `jvm-runner-test` use `java.util.regex.Pattern/quote`. The
  `runtime-test` predicate also counts the occurrences of
  `warning-prefix` inline (`(count (re-seq (re-pattern (Pattern/quote
  ...)) (or s "")))`, a 113-character line). Use `Pattern/quote` in
  `err-matches?`, add one public `cell-harness` function of text and a
  string (for example `occurrences`, nil as `""`) beside `described`, and
  use it in the `runtime-test` predicate. Labels and expectations
  unchanged.
  - code-shaper pass 31 fix: `Pattern/quote` in `err-matches?`;
    `cell-harness/occurrences`.

## Implementation review (pass 16)

- [x] Use one function for "expected for each entry of an axis". Passes
  24–29 added four copies of `(vec (repeat (count axis) expected))`:
  `consumer-aot-checks/each-construction`, `cell-registry/each-attempt`,
  `transfer-checks/each-expand` and the private
  `identity-receiving-cross-jvm-test/each-type` (axis, expected). The
  same rule is written inline again in
  `runtime-test/source-field-validation-test` (`(count
  validation/invalid-fields)`) and `transfer-test/concurrent-defined`
  (`(count warning/identity-types)`). Add one public child-safe function
  of an axis and an expected value (for example `cell-registry/each`),
  define the three named functions with it (or replace them), and use it
  in place of `each-type` and the two inline copies. Labels and
  expectations unchanged.
  - implementation review pass 16 fix: `cell-registry/each` (axis,
    expected); `each-construction`, `each-attempt`, `each-expand` defined
    with it; `each-type` and two inline copies replaced.
- [x] Make the `value-type.cell-registry` namespace docstring and the plan
  "New-Class checks" text agree with its content. The docstring states
  "New-Class and absence checks", and the plan states "It has two
  functions". Passes 25–31 added child-safe support of other kinds:
  child call forms (`check-form`), the attempt axis (`attempt-count`,
  `attempts`, `twice`, `each-attempt`), the identity fact (`identity?`)
  and `factory-symbol`. State the namespace role as the child-safe cell
  support (new-Class and absence checks, attempt axis, child call
  forms), or move the helpers that are not new-Class checks to a small
  child-safe namespace. Behavior unchanged.
  - implementation review pass 16 fix: docstring and plan state the
    child-safe cell support role; no move.

## Implementation test review (pass 24)

- [x] Give each nested expansion of `identity-receiving-checks/nested`
  its own `LOADER` frame again. Code-shaper pass 25 replaced
  `(evaluate n (list 'macroexpand (list 'quote form)))` (one
  `Compiler.eval` frame, thus a new `LOADER` binding, for each expansion)
  with a direct `(expander form)` call in the cell frame. Under warn and
  silent policy, `identity-field-deftype` installs a capture loader at
  expansion time. That loader now stays in the cell frame: the later
  expansions of `nested-warning-scope` (second parent, then
  `macroexpand-1` for both parents) and the `evaluate` of each expansion
  run with the capture loaders of the earlier expansions bound. Thus
  `nested-public-warning-scope-test` no longer verifies both expansion
  forms in fresh receivers (Ramora risk "`macroexpand` from tooling
  installs a loader"; design "Shared runtime state"). Bind a new
  `Compiler/LOADER` (`RT/makeClassLoader`) around each expansion, or
  expand through `eval` as before. Labels and expectations unchanged.
  - test review pass 24 fix: `with-bindings` of `*ns*` and a new
    `Compiler/LOADER` (`RT/makeClassLoader`) around each expansion.

## Implementation test review (pass 26)

- [ ] Make `nested-public-warning-scope-test` fail when an expansion
  capture loader reaches the cell frame. Pass 24 found that code-shaper
  pass 25 removed the `LOADER` frame of each expansion and that no test
  failed; the pass 24 fix restores the frame, but no check states it. A
  capture loader is set with `.set Compiler/LOADER` (`impl.loader`), so
  without the frame `@Compiler/LOADER` in the cell frame changes after an
  expansion. In `identity-receiving-checks/nested-warning-scope`, read the
  cell `LOADER` value once, and give for each declaration whether
  `@Compiler/LOADER` is still that value after `(expansion)` (for example
  `:cell-loader? true`); expect it in the shared `declaration` value.
  Check once that the step fails with the frame removed (record in
  `implementation.md`). Labels unchanged.
  - test review pass 26 attempt: `:cell-loader?` added and expected
    (passes). Blocked: with the frame removed the test still passes; see
    implementation.md.

## Implementation test review (pass 27)

- [x] Use one source for the `[expand mode]` cases of the stale sibling
  checks, and check the case keys, not a literal count.
  `identity-loader-checks/public-stale-sibling` writes the cases as `(for
  [expand transfer/expands mode transfer/modes] ...)`,
  `loader-policy-test/cases` writes the same `for` again, and
  `identity-loader-test/public-stale-sibling-class-test` checks `(= 6
  (count results))`, the product of the two axis counts. When an axis
  changes, the count fails with no case name, and a missing or extra case
  is not named. Add one public `identity-loader-checks/cases` (in order),
  use it in `public-stale-sibling` and `loader-policy-test`, and check
  `(= cases (keys results))` (or the key set) in place of the count.
  Labels and expectations unchanged.
  - test review pass 27 fix: `identity-loader-checks/cases`; key set
    check.
- [x] Use one source for the input axis of
  `array-info-boundary-test/control-form`. The child form writes the
  inputs as `[nil "not an array" (long-array 1) (object-array 1)]`, and
  `control-expected` pairs the outcomes with them only by position: `[invalid
  invalid invalid ...]` in the description, `(= 4 (count outcomes))`,
  `(take 3 outcomes)` and `(nth outcomes 3 nil)` in the predicate. Bind
  the invalid input forms once (a private def, for example
  `invalid-inputs`) and the valid input form, build the form vector with
  them, and write the description and the predicate from
  `(count invalid-inputs)`, as passes 26–30 did for the other axes. Forms
  (equal data), labels and expectations unchanged.
  - test review pass 27 fix: private `invalid-inputs`, `valid-input`;
    forms equal data.
- [x] Use one predicate for a present runtime `<detail>` in the described
  predicates. Three idioms check it: `(not (str/blank? (:detail
  capability)))` (`runtime-test/capability-test`), `(boolean (seq
  detail))` (`field-capability-test/policy-expected`) and `(seq detail)`
  (`identity-receiving-cross-jvm-test/strict-nested-public-declaration-test`,
  which also makes the predicate return nil, not a boolean, as pass 23
  set). Add one public `cell-harness` function beside `described` (for
  example `detail?`: a non-blank string) and use it in the three places.
  Labels and expectations unchanged.
  - test review pass 27 fix: `cell-harness/detail?`.

## Implementation test review (pass 28)

- [x] Use `consumer-aot-checks/checked-outcomes` for the nil and wrong
  inputs in `identity-receiving-test`, as
  `field-transfer-checks/consume-mixed` does for each field.
  `receiving-identity-fields-test` writes the input axis as `[nil 42]` and
  `receiving-redefined-child-test` as `[nil stale]`; each pairs it with
  `checks/checked-rejections` only by position (`(map vector ...)`), and
  checks one outcome in each `is`. When the order or the inputs of
  `checked-outcomes` change, these copies do not. Write
  `(is (= checks/checked-rejections (checks/checked-outcomes build
  wrong)))`, with `build` of one argument vector (for example `(fn [[x]]
  (build (assoc args position x)))`, and `(fn [[x]] (.newInstance ctor
  (object-array [x])))`). Labels and expectations unchanged.
  - test review pass 28 fix: `checked-outcomes` with one-argument
    builds in both tests.
- [x] Build both forms of `receiving-identity-fields-test` from one
  binding of the type specs. `public-form` is `(list* ... 'Holder fields
  (drop 5 form))`: the `5` is the position of the specs in the
  `identity-field-deftype` form, so the reader must count the arguments
  of `form`, and a change of that form silently changes `public-form`.
  Bind the specs (`FieldReads` ... `deref`) once (for example `specs`)
  and write `form` and `public-form` with `list*` of it. Forms (equal
  data), labels and expectations unchanged.
  - test review pass 28 fix: quoted `specs`; both forms with `list*`.
- [x] Use one source for the checked fields, valid values and invalid
  values of each saved Holder. `public-native-field-aot-test` and
  `public-mixed-field-aot-test` write them in `consumer-spec`
  (`:checked`, `:valid`, `:invalid`) and again as the arguments of
  `function-checks/invoke-install-form` (mixed: `[[nil "text"] ["wrong"
  "text"] [42 nil] [42 :wrong]]` twice); `public-field-aot-test` writes
  the `checks/string-holder` values again (`["required"] ["valid"] [[nil]
  [42]]`). The expected `:rejections` pair with `:invalid` only by
  position, and `public-mixed-field-aot-test` writes them twice
  (`(into checks/checked-rejections checks/checked-rejections)`). Make
  `invoke-install-form` take a spec (its `:checked`, `:valid` and
  `:invalid`), use the spec of each namespace (`checks/string-holder` in
  `public-field-aot-test`), and bind the mixed rejections once. Forms
  (equal data), labels and expectations unchanged.
  - test review pass 28 fix: `invoke-install-form` of a spec; private
    `rejections` in `public-mixed-field-aot-test`.

## Implementation test review (pass 29)

- [x] Give the spec keys `:valid` and `:invalid` one meaning. Pass 28
  made `function-aot-checks/invoke-install-form` take a spec, but
  `invoke-install`/`inspect-holder` apply the factory directly to
  `:valid` and to each `:invalid` entry (factory arguments), and ignore
  `:arguments`. `holder-consumer` (and the namespace docstring of
  `consumer-aot-checks`) reads them as checked values that go through
  `:arguments`. The function tests pass `checks/string-holder` and the
  `consumer-spec` of each namespace, whose `:arguments` give the
  arguments of the top-level Holder (`[required 7 2.5 nil]`), not of the
  function Holder (`[required]`). The cells pass only because each
  function Holder has only the checked fields, in `:checked` order. Make
  `inspect-holder` construct through `:arguments` of the spec, as
  `holder-consumer` does, and give each function test a spec with the
  `:arguments` of its function Holder (for example a private
  `function-spec` beside `function-source`). Forms change; labels and
  expectations unchanged.
  - test review pass 29 fix: `inspect-holder` constructs through
    `:arguments`; `invoke-install` takes the spec; private `function-spec`
    in the three function tests.
- [x] Build the `:invalid` vector of a spec from `:valid` and one wrong
  value for each checked field, in `checks/checked-outcomes` order.
  `public-mixed-field-aot-test/consumer-spec` writes
  `[[nil "text"] ["wrong" "text"] [42 nil] [42 :wrong]]`, and `rejections`
  pairs with it only by position (`(into checked-rejections
  checked-rejections)`, "for each checked field", not written from
  `:checked`). `string-holder` (`[[nil] [42]]`) and the native
  `consumer-spec` (`[[nil] ["wrong"]]`) write the same rule. Add one
  public `consumer-aot-checks` function of valid and the wrong values
  (for example `invalid-arguments`: for each position, `(assoc valid i
  nil)` then `(assoc valid i wrong)`), use it in the three specs, and
  write `rejections` from `(count (:checked consumer-spec))` (for
  example `(into [] cat (registry/each (:checked consumer-spec)
  checks/checked-rejections))`). Forms (equal data), labels and
  expectations unchanged.
  - test review pass 29 fix: `consumer-aot-checks/invalid-arguments`;
    `rejections` from `:checked`.
- [x] Write the field positions of `receiving-identity-fields-test`
  from `fields`. The construction check writes the non-nil positions as
  `[0 1]`, and the optional checks write the `optional` position as `2`
  (`(assoc args 2 optional)`); each pairs with `fields` and `args` only
  by position. When a field is added or moved, the test checks another
  field with no failure that names it. Bind the non-nil positions (the
  entries of `fields` with `:non-nil` metadata) and the `optional`
  position once from `fields`, and use them in these places. Labels and
  expectations unchanged.
  - test review pass 29 fix: `non-nil-positions`, `optional-position`
    from `fields`.

## Implementation test review (pass 30)

- [x] Use one rule for the expected rejections of a spec. Pass 29 wrote
  the mixed `rejections` from `(:checked consumer-spec)`, but the
  one-field expected values write `checks/checked-rejections` directly:
  `consumer-aot-checks/string-holder-result`,
  `public-native-field-aot-test` (`consumer-result` and the
  `function-public-native-value-aot-test` install result) and the private
  `public-field-aot-test/string-holder`. These pair with `:invalid` only
  because the spec has one checked field. Add one public
  `consumer-aot-checks` function of a spec (for example
  `spec-rejections`: `checked-rejections` for each `:checked` field, the
  order of `invalid-arguments`), and use it in these places and in place
  of the private `public-mixed-field-aot-test/rejections`. Labels and
  expectations unchanged.
  - test review pass 30 fix: `consumer-aot-checks/spec-rejections`;
    private `rejections` removed.
- [x] Write the expected checked field facts of each saved Holder once.
  Each namespace writes them twice: `public-mixed-field-aot-test`
  (`consumer-result` and the `function-public-mixed-value-aot-test`
  install result, two fields), `public-native-field-aot-test`
  (`consumer-result` and the install result) and `public-field-aot-test`
  (`checks/string-holder-result` and the private `string-holder`). The
  install result also writes the `:stored` values again (`42`, `"text"`,
  `"valid"`), which are the `:valid` values of the spec. Bind the field
  facts once in each namespace (or give `string-holder-result` its
  fields), and build the install `:fields` from them with `:stored` from
  `(zipmap (:checked spec) (:valid spec))` (one public
  `function-aot-checks` function of a spec and the field facts, beside
  `install-result`). Also rename the private
  `public-field-aot-test/string-holder` (an install invocation, not a
  spec like `checks/string-holder`; for example `function-holder`).
  Labels and expectations unchanged.
  - test review pass 30 fix: `consumer-aot-checks/string-holder-fields`,
    private `fields` (native, mixed), `function-aot-checks/holder-invocation`
    (spec, fields, value?); private `function-holder`.
- [x] Use the constructor of `owner` in
  `identity-receiving-test/receiving-identity-fields-test`. Pass 29
  wrote the field positions from `fields`, but `ctor` is
  `(.getConstructor owner (into-array Class [Object Object Object
  Long/TYPE Double/TYPE]))`, a second copy of the field kinds that pairs
  with `fields` only by position (a 106-character line). When a field is
  added or moved, the lookup fails with `NoSuchMethodException`, which
  names no field. Use the one public constructor, as
  `consumer-aot-checks/builders` does (`(first (.getConstructors
  owner))`), or the `:reflective` entry of `builders`. Labels and
  expectations unchanged.
  - test review pass 30 fix: `(first (.getConstructors owner))`.

## Implementation test review (pass 31)

- [x] Write the expected `holder-consumer` values of a spec from the
  spec, as pass 30 did for `function-aot-checks/holder-invocation`. Each
  caller of `consumer-aot-checks/holder-result`
  (`string-holder-result`, the private `consumer-result` of
  `public-native-field-aot-test` and `public-mixed-field-aot-test`)
  writes `:read` again (it is `(zipmap (:checked spec) (:valid spec))`),
  writes the checked values again in `:stored`, and writes `:rejections
  (spec-rejections spec)`. The unchecked stored values `"count" 7
  "weight" 2.5 "nullable" nil` are written three times, beside the same
  values in each `:arguments` fn. Also the argument order differs:
  `(holder-result value? fields construction)`,
  `(holder-invocation spec fields value?)`. Make `holder-result` take the
  spec (in the `holder-invocation` order) and give `:read`, the checked
  part of `:stored` and `:rejections` from it; bind the unchecked stored
  values once in `consumer-aot-checks` (beside `string-holder`); callers
  give only `:call`. Labels and expectations unchanged.
  - test review pass 31 fix: `(holder-result spec fields value? call)`;
    `consumer-aot-checks/unchecked-stored`.
- [x] Use the spec in `function-aot-checks/invoke-install` and
  `inspect-holder`. Since pass 29 they take a spec, but they ignore its
  `:source`, `:ns` and `:owner` and write the values again
  (`"checked/aot_fixture.clj"`, `'checked.aot-fixture`,
  `'checked.aot-fixture/->Holder`, `'checked.aot-fixture/install`).
  `holder-consumer` reads them from the spec. When a test gives a spec of
  another saved class, the check inspects the fixture class with no
  failure that names the spec. Read `:source` and `:ns` from the spec,
  resolve the factory with `registry/factory-symbol` of `:ns` and the
  simple name of `:owner`, and the install function from `:ns`; update
  the docstrings. Labels and expectations unchanged.
  - test review pass 31 fix: `:source`, `:ns`, `:owner` from the spec;
    docstrings updated.
- [x] Use one source for the native annotation in the persisted output
  checks. `consumer-aot-checks/native-annotations` gives the class name
  (`jdk.internal.vm.annotation.NullRestricted`), but
  `public-mixed-field-aot-test/persisted-public-mixed-output-test` and
  `field-aot-test/output?` write its descriptor
  (`"Ljdk/internal/vm/annotation/NullRestricted;"`) as a literal. When
  the annotation changes, the consumer checks change and these two do
  not. Add one public function of a class name that gives its descriptor
  (for example `aot-fixture/descriptor`), and write the two annotation
  vectors as `(mapv descriptor checks/native-annotations)`. Labels and
  expectations unchanged.
  - test review pass 31 fix: `aot-fixture/descriptor`.

## Implementation test review (pass 32)

- [x] Name the expand (and the mode) in the `testing` context of each
  in-process test that iterates the expand axis. A failure must name its
  case, and the label record must count each case on its own label. Today
  `identity-receiving-test/receiving-identity-fields-test` names mode and
  `nested?` but not `expand` (3 expands share one label);
  `receiving-redefined-child-test` has no `testing` context (3 expands ×
  3 modes, and the first/second owner, share the Var label);
  `public-field-annotations-test` names `tags` but not `expand`; and
  `fields-test` (`mutable-declaration-error-test`,
  `public-portable-declaration-errors-test`,
  `public-portable-metadata-errors-test`,
  `public-enclosing-type-error-test`,
  `public-imported-concrete-field-test`) names no expand (two have no
  context). Also `fields-test` writes the expand axis as a literal 5 times
  (`[macroexpand-1 macroexpand]`, the reverse of `transfer/expands`),
  which pass 25 did not list. Use `transfer/expands` with
  `transfer/expanders` in `fields-test`, and add an inner `testing` of the
  case (for example `(pr-str expand)`, and the owner in
  `receiving-redefined-child-test`), so that each new label extends the
  old label (the `check_record.clj` mapping rule). Record the label
  change in `implementation.md`. Expectations unchanged.
  - test review pass 32 fix: inner `(testing (pr-str expand))` (redefined
    child: `(pr-str expand mode)`, then `"first-owner"`/`"second-owner"`);
    `fields-test` uses `transfer/expands` (implementation.md).
- [x] Use the `consumer-aot-checks` construction rules in
  `fields-test/public-imported-concrete-field-test`, as pass 28 did in
  `identity-receiving-test`. It writes the factory and reflective
  constructions again, checks nil and `"wrong"` with `thrown?` for the
  factory and with a local `InvocationTargetException` unwrap for the
  constructor, and pairs the inputs with the exception classes only by
  position. Use `checks/builders` (all three constructions) and
  `(is (= checks/checked-rejections (checks/checked-outcomes build
  "wrong")))` for each construction. Labels unchanged; the expectation is
  the same fact in the shared shape (direct construction added).
  - test review pass 32 fix: `checks/builders`, stored value and
    `checked-outcomes` for each construction.
- [x] Run the full verification and retake the after record after the
  test review passes 24–31. The last full run is implementation review
  pass 16 (`after28`), and `after-record/*.edn` is from code-shaper pass
  25. Since then each pass ran only its focused namespaces, but the
  passes changed shared support (`consumer-aot-checks`,
  `function-aot-checks`, `identity-receiving-checks`,
  `identity-loader-checks`, `cell-harness`) and assertion counts (pass 28:
  one `is` for each `checked-outcomes` in `identity-receiving-test`, a
  `:fallback-test` namespace). Run the four commands at limit 4 and
  limit 1 with `VALUE_TYPE_TEST_RECORD`, run `check_record.clj`, replace
  `after-record/*.edn` when the summaries differ in more than times, and
  record the commit, assertion counts and acceptance 3 result in
  `implementation.md`. Do this after the other steps of this pass.
  - test review pass 32 fix: record at `c6e1033` (`after29`,
    `after29-limit1`); `after-record/*.edn` replaced; acceptance 3 holds
    (implementation.md).

## Implementation test review (pass 33)

- [x] Name each case of
  `isolated-sibling-loader-test/public-isolated-sibling-construction-test`
  in its `testing` context, as pass 32 did for the other in-process tests
  that iterate the expand axis. The context names only the mode, so 2
  loaders (`left`, `right`) × 2 expands share each label, and a failure
  does not name its loader or expand. Also the private `verify-owner`
  writes the construction rules again (factory, `(eval '(fn [value] (new
  Holder value)))`, a local `InvocationTargetException` unwrap; nil and
  the sibling with `thrown?`), as `fields-test` did before pass 32. Add an
  inner `testing` of the case (for example `(pr-str expand)` and
  `"left"`/`"right"`), so that each new label extends the old label
  (`check_record.clj` mapping rule), and check the constructions with
  `checks/input-results` of `valid` and `wrong`, expected as
  `(checks/each-construction {:stored-input? true :rejections
  checks/checked-rejections})`. If `checks/builders` cannot resolve the
  owner under the local loader, record why in `implementation.md`. Record
  the label change in `implementation.md`. Expectations unchanged.
  - test review pass 33 fix: inner `(testing (str loader-name " " (pr-str
    expand)))`; `checks/input-results` (implementation.md).
- [x] Name the construction in a failure of
  `fields-test/public-imported-concrete-field-test`. Pass 32 added
  `(doseq [build (checks/builders defining-ns cls)] ...)`, which has no
  context, so a failed `identical?` or rejection check does not name the
  construction (`:direct`, `:factory` or `:reflective`). Use
  `(checks/input-results defining-ns cls storage sample "wrong")` with the
  expected `(checks/each-construction {:stored-input? true :rejections
  checks/checked-rejections})` in one `is`, as the other checks give the
  construction results. Labels unchanged; the expectation is the same fact
  in the shared shape.
  - test review pass 33 fix: `checks/input-results` in one `is`.

## Implementation test review (pass 34)

- [x] Use the `consumer-aot-checks` construction rules in
  `identity-backend-test`, as passes 32 and 33 did in `fields-test` and
  `isolated-sibling-loader-test`. Three tests write the constructions
  again: `checked-identity-contract` (factory and direct with `thrown?`,
  then a reflective `ctor` of literal parameter types with a local
  `InvocationTargetException` unwrap, inputs paired with exception
  classes by position), `multiple-identity-constraints` (local `direct`,
  `factory`, `reflective`, `ctor` of literal parameter types) and
  `nested-identity-constraint` (the same three). Each `doseq` over the
  constructions has a fixed `testing` context, so a failure does not name
  the construction. Use `checks/builders` of
  `'value-type.identity-backend.fixture` and the owner, with
  `(fn [[x]] (build (assoc args i x)))` for each checked position (as
  pass 28 did in `identity-receiving-test`), and
  `(is (= checks/checked-rejections (checks/checked-outcomes build
  wrong)))`; give the stored-value checks for each construction under a
  `testing` of the construction (for example `(pr-str construction)` of
  `checks/constructions`), or use `checks/input-results`. In
  `unrelated-attributes-survive` (a class with no factory), write the nil
  rejection as `(checks/outcome #(.newInstance ctor ...))`. Record the
  label change in `implementation.md`. Expectations unchanged.
  - test review pass 34 fix: private `named-builders`; `checked-outcomes`
    for each construction; `checks/outcome` in
    `unrelated-attributes-survive` (implementation.md).
- [x] Use the `consumer-aot-checks` construction rules in
  `fields-test/disabled-declaration-behavior-test`. It gets the
  constructor with literal parameter types (`[Long/TYPE Object]`, a second
  copy of the declarations; pass 30 removed this in
  `identity-receiving-test`), checks only the reflective and factory
  constructions (no direct construction), and iterates 3 restrictions ×
  3 texts × 2 constructions with no `testing` context, so a failure names
  no case. Use `checks/builders` (all three constructions), and add a
  `testing` of the restriction and the text (and the construction), so
  that each new label extends the old label (`check_record.clj` mapping
  rule). Record the label change in `implementation.md`. Expectations
  unchanged; direct construction added.
  - test review pass 34 fix: `checks/builders`; `(testing (pr-str
    restriction))`, then `(testing (pr-str text construction))`
    (implementation.md).

## Implementation test review (pass 35)

- [x] Use one function for the named constructions, and one label form
  for a construction. Three tests pair `checks/constructions` with
  `checks/builders` in three idioms: the private
  `identity-backend-test/named-builders` (`map vector`),
  `fields-test/disabled-declaration-behavior-test` (`builds`, `map
  vector` inline) and `identity-receiving-test/receiving-identity-fields-test`
  (`zipmap`, a map). The contexts also differ: `(pr-str construction)`
  (`":direct"`) in two tests, `(name entry)` (`"direct"`) in
  `identity-receiving-test`. In the same `identity-receiving-test` check,
  `(doseq [position non-nil-positions] ...)` has no context, so a failed
  rejection does not name the field (`identity-backend-test` names it:
  `(pr-str construction field-name)`). Add one public `consumer-aot-checks`
  function of ns-or-name and owner (for example `named-builders`: pairs
  in the order of `constructions`), use it in the three tests (remove the
  private copy), use `(pr-str construction)`, and name the field of each
  position. Record the label change in `implementation.md`. Expectations
  unchanged.
  - test review pass 35 fix: `consumer-aot-checks/named-builders`;
    `(pr-str (name field))` for each position (implementation.md).
- [x] Use one function for a transform failure in `identity-backend-test`,
  and name each case of the failure loops. `shape-failure` gives
  `[error message]`; `unsupported-identity-storage-and-writes` writes a
  local `failure` that gives `(assoc (ex-data e) :message ...)`. The loops
  over `(vals transforms)` (`unsupported-storage-references`,
  `unsupported-identity-storage-and-writes`), over the opcodes, over the
  storage `[access descriptor]` cases and over the `constructor-coverage`
  cases have no case context: a failed `(= :unsupported-shape error)`
  does not name the transform (`:owner` or `:companion`), the opcode or the
  bytes case (two `constructor-coverage` cases have the same `part`).
  Keep one function (the ex-data with the message), iterate `transforms`
  as entries, and add a `testing` of each case (for example `(pr-str
  transform-key opcode)`, and a case name for each bytes case). Record the
  label change in `implementation.md`. Expectations unchanged.
  - test review pass 35 fix: `shape-failure` gives the ex-data with
    `:message`; case contexts (implementation.md).
- [x] Name the field in the field loops of
  `public-field-annotations-test`. The two `doseq` loops over the fields
  (`count_value`/`ratio`, and `field-names` with `inputs`) have no
  context, so a failed `isAnnotationPresent`, type or stored-value check
  does not name its field. Add `(testing field-name ...)` in both loops
  (appended context). Record the label change in `implementation.md`.
  Expectations unchanged.
  - test review pass 35 fix: `(testing field-name ...)` in both loops.
- [x] Use one source for the no-argument test Class.
  `isolated-sibling-loader-test/child-bytes` writes again the ASM body of
  `identity-loader-checks/named-class` (public Class, `Object` super,
  no-argument constructor), and `verify-owner` writes again the
  no-argument instance rule of `identity-loader-checks/stale-sibling-case`
  (`instance`: `(.newInstance (.getConstructor c (make-array Class 0))
  (object-array 0))`). Add public `identity-loader-checks` functions of the
  class bytes (binary name) and of an instance (Class), define
  `named-class` and `stale-sibling-case` with them, and use them in
  `isolated-sibling-loader-test` (remove `child-bytes`). Labels and
  expectations unchanged.
  - test review pass 35 fix: `identity-loader-checks/class-bytes`,
    `instance`.

## Implementation review (pass 18)

- [x] Map the pass-35 construction contexts in `check_record.clj`, and
  correct the pass-35 note. `identity-receiving-test/receiving-identity-fields-test`
  changed the construction context from `(name entry)` (`"direct"`) to
  `(pr-str construction)` (`":direct"`). The first record has the labels
  `[... "direct"]`, `[... "factory"]`, `[... "reflective"]` of this Var
  (`first-record/fallback-java8.edn` line 2202, and the other two
  commands). No after label extends them, so `mapping-findings` gives
  `:targets 0` for each. Design "Label mapping" requires targets for each
  first label; "maps these labels as new" (implementation.md, pass 35)
  does not hold for the old labels. Add a context rename for this Var in
  `context-renames` (`"direct"` → `":direct"`, and the same for
  `factory` and `reflective`), state it in the header comment, and
  correct the pass-35 note.
  - implementation review pass 18 fix: `construction-keyword` in
    `context-renames`; header updated; pass-35 note corrected (18 findings
    without it, 0 with it).
- [x] Retake the after record after the test review passes 33–35. The
  record is from `c6e1033` (pass 32). Passes 33–35 changed labels in
  `:fallback-test` namespaces (`isolated-sibling-loader-test`,
  `fields-test`, `identity-backend-test`, `identity-receiving-test`,
  `public-field-annotations-test`) and shared support that cross-JVM
  cells load (`identity-loader-checks`, used by `loader-policy-test`;
  `consumer-aot-checks`). Only focused namespaces ran. After the step
  above, run the four commands at limit 4 and the two limit-1 commands
  with `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run
  `check_record.clj`, replace `after-record/*.edn`, and record the commit,
  assertion counts, time percentage and acceptance 3 result in
  `implementation.md`.
  - implementation review pass 18 fix: record at `0d2ce00` (`after30`,
    `after30-limit1`); `after-record/*.edn` replaced; acceptance 3 holds
    (implementation.md).
- [x] Use one context form for a field name. Passes 34–35 added three
  forms: `(pr-str construction field-name)` in `identity-backend-test`
  (`":direct \"left\""`), `(pr-str (name field))` in
  `identity-receiving-test` (`"\"required\""`), and the raw name in
  `public-field-annotations-test` (`"count_value"`). Use one form (for
  example the raw field name in its own `testing`, below the construction
  context), and add each label change to `check_record.clj` when it is
  not an appended context. Record the label change in
  `implementation.md`. Expectations unchanged.
  - implementation review pass 18 fix: raw field name in its own
    `testing` below the construction context (implementation.md).

## Implementation test review (pass 36)

- [x] Test the child limit and the permit-wait rule of `run-jvms`. Design
  Scope 4: at most `VALUE_TYPE_TEST_JVM_LIMIT` children run at one time in
  one parent JVM, and "time that a child waits for the limit does not
  count" toward its timeout. `jvm-runner-test` covers only `read-limit` and
  `wait-ms`; no test covers the `Semaphore` in `execute`. If the permit
  were taken after process start, or released before the wait, or the
  wait clock started before `acquire`, every test still passes (the
  records only show labels and total time). In `cell-runner-test`, call
  `run-jvms` with `(inc runner/limit)` plain `:baseline` children (no
  `value-type.core` load). Each prints its start and end
  `System/currentTimeMillis` and sleeps a fixed time `s`, with an explicit
  `:timeout-ms` above `s` but below `2 × s` plus JVM start. Assert: each
  child exits 0 (the last child waited for a permit and did not time out);
  the maximum number of overlapping `[start end]` intervals is at most
  `runner/limit`. Use the current `runner/limit` (no test-only option);
  the test holds at limit 1 and 4. New labels only.
  - test review pass 36 fix: `cell-runner-test/child-limit-test`
    (`:producer` children; sleep 6000 ms, timeout 12000 ms); fails under
    two runner mutations (implementation.md).

## Implementation test review (pass 37)

- [x] Make `cell-runner-test/child-limit-test` meet the Scope 1
  child-count bound, with a true child kind. The test starts
  `(inc runner/limit)` children of one configuration as `:kind :producer`
  (5 at limit 4, 2 at limit 1). They make no transfer expansions (design
  Concepts, "producer"), and `producer(T)` is the count of distinct
  producer configurations (1). Thus `check_record.clj` `bound-findings`
  gives `[child-limit-test :producer]` count 5 > 1, and the test is not in
  `rule-5-exceptions` or in the implementation.md isolation exception
  table. Select one: give each child its own configuration (for example a
  child-index `-D` option in `:jvm-opts`), or add the test to the
  exception table (children / configs, rule, reason: the evidence is
  concurrency, thus `limit + 1` processes) and to `rule-5-exceptions`.
  State the kind choice in the test comment and in implementation.md.
  - test review pass 37 fix: `:cell` children; isolation exception
    (5 / 1 at limit 4) in the table and `rule-5-exceptions`.
- [x] Retake the `cross-jvm` after record after the pass 36 change. The
  record is from `0d2ce00`; `child-limit-test` adds new children, a new
  label and about 18 s at limit 4 to `cross-jvm`. After the step above,
  run `cross-jvm` at limit 4 and limit 1 with `VALUE_TYPE_TEST_RECORD`
  and `/usr/bin/time -p`, run `check_record.clj` (no bound finding),
  replace `after-record/cross-jvm.edn`, and record the commit, assertion
  count, time percentage and acceptance 3 result in implementation.md.
  - test review pass 37 fix: record at `9cb4f7b` (`after31`,
    `after31-limit1`); `after-record/cross-jvm.edn` replaced; acceptance 3
    holds (implementation.md).

## Implementation test review (pass 38)

- [x] Assert the child-level stderr of each `child-limit-test` child.
  Pass 37 made the limit children `:cell` children. Design Scope 2: "The
  parent asserts this once for each cell child, as a support assertion",
  and child-level stderr must be empty under all policies. The test shows
  `:err` only in the failure message of the exit assertion, so a child
  that writes to stderr and exits 0 passes. Call
  `harness/assert-child-stderr` for each child and its result (one
  `"child stderr"` label; equal children give one label). New label only.
  - test review pass 38 fix: `harness/assert-child-stderr` for each
    child; one `"child stderr"` label.

## Implementation test review (pass 39)

- [x] Make `cell-runner-test/child-limit-test` show that children run at
  the same time, and that the limit holds across `run-jvms` calls. Design
  Scope 4: "The runner can run independent children at the same time",
  and "The limit applies to all children of one parent JVM, across all
  tests and calls". The test asserts only `max-overlap ≤ runner/limit`.
  Thus a serial runner passes every test: with `permits` made
  `(Semaphore. 1)`, or with `run-jvms` running `execute` with `mapv` and
  no `future`, the overlap is 1 and each child exits 0 (the wait does not
  count). A semaphore for each `run-jvms` call also passes, because the
  test makes one call. Change the test:
  - assert `(= runner/limit (max-overlap ...))` (the first `limit`
    children start together; their 6000 ms sleeps overlap). This holds at
    limit 1 and 4.
  - start the `(inc runner/limit)` children through two `run-jvms` calls
    on two threads (for example one child, and `runner/limit` children),
    and compute the overlap of all results. Keep the record labels (the
    `child-start!` record of each call; `future` conveys the bindings).
  Check that each mutation above fails the test, then restore. Record the
  label change (if any) in `implementation.md`.
  - test review pass 39 fix: two `run-jvms` calls in `future`s (1 and
    `limit` children); `(= runner/limit (max-overlap ...))`; three
    mutations fail; no label change (implementation.md).

## Implementation test review (pass 41)

- [x] Make `cell-runner-test/unreadable-producer-result-test` show that
  `run-jvms` throws only after all children of the call return (plan
  "Runner"). The test puts the `:cell` child at index 0 and the producer
  at index 1. `run-jvms` derefs the futures in input order, so the cell
  child has returned before the producer result is read. Thus a runner
  that throws before the later children return (for example, a check of
  each result in the deref loop) passes the test, and leaves child
  processes that hold permits after the throw. Put the producer at index 0
  and, after it, a `:cell` child that sleeps a fixed time (for example
  3000 ms). Assert that the elapsed time of the `run-jvms` call is at least
  that sleep (a lower bound only), and assert the `ex-data` `:index 0`,
  `:exit 0`, a string `:read-error`, `:err ""` and an `:out` that contains
  the unreadable line (the plan names `:out` and `:err`). Check once that
  the deref-loop mutation fails the test, then restore (record in
  `implementation.md`). New labels only.
  - test review pass 41 fix: producer at index 0, then a cell child that
    sleeps 3000 ms; elapsed lower bound; `ex-data` `:index`, `:exit`,
    `:err`, `:read-error`, `:out`; deref-loop mutation fails
    (implementation.md).

## Implementation test review (pass 42)

- [x] Make `cell-runner-test/unreadable-producer-result-test` show the
  return of the later child by an event, not by an absolute time. Pass 41
  asserts `(<= late-cell-ms elapsed-ms)`. Under the deref-loop mutation,
  the elapsed time is the run time of the producer JVM (2144 ms in the
  pass 41 record, 3000 ms threshold). On a slower machine the producer
  alone takes more than 3000 ms, and the mutation passes. Give the cell
  child a temporary file path: it sleeps `late-cell-ms`, then writes the
  file. In the `catch`, read `(.exists file)` at the throw, and assert it
  true (delete the file after). Under the mutation the cell child is
  still asleep at the throw, independent of the JVM start time. Also
  make the `try` give a distinct value when `run-jvms` does not throw
  (for example `::no-throw`), so that this failure names the missing
  throw, not `{}`. Check once that the deref-loop mutation fails the
  test, then restore (record in `implementation.md`). Labels unchanged.
  - test review pass 42 fix: cell child writes a temp file after
    `late-cell-ms`; `(.exists file)` at the throw; `::no-throw`;
    deref-loop mutation fails (implementation.md).
- [x] Use one idiom for a literal text in a string in the runner tests.
  `cell-runner-test/assert-no-result` writes `(re-find (re-pattern
  marker) ...)` (a regex of literal text, not quoted), and
  `jvm-runner-test/read-limit-test` writes `(re-find #"VALUE_TYPE_TEST_JVM_LIMIT"
  ...)` and `(re-find (re-pattern (Pattern/quote (pr-str value))) ...)`.
  `unreadable-producer-result-test`, `array-info-boundary-test` and
  `message-checks` use `str/includes?`. Use `str/includes?` in these
  three places. Labels and expectations unchanged.
  - test review pass 42 fix: `str/includes?` in `assert-no-result` and
    `read-limit-test`.

## Implementation test review (pass 43)

- [x] Test the read rule of the runner for empty stdout and a non-zero
  exit. Plan "Runner": "Empty stdout still gives `:result` nil (no
  `:read-error`)", so a producer with empty stdout does not throw. No
  child of the suite has empty stdout (each cell child and producer
  prints a value), and no test covers the private `jvm-runner/read-result`.
  If `read-result` read `""` with a reader that throws at end of input, a
  producer that prints nothing would throw, and every test still passes.
  Make `read-result` public (a pure function of exit and stdout, as
  `wait-ms` and `read-limit` are) and add one in-process
  `jvm-runner-test` test: exit 0 and `""` gives `{:result nil}`; exit 0
  and `"x\n[1]"` gives `{:result [1]}` (last line); exit 0 and
  `"#object[java.lang.Object 0x1 \"o\"]"` gives a string `:read-error` and
  no `:result`; exit 3 and `:timeout` give `{:result nil}`. New labels
  only.
  - test review pass 43 fix: public `jvm-runner/read-result`;
    `jvm-runner-test/read-result-test`.
- [x] Use one function for the no-result outcomes in `cell-runner-test`.
  `unreadable-result-outcome-test` writes the body of `assert-no-result`
  again (`(false? pass?)`, the one `:no-result` check) and checks the
  failure against the run (`:out`, `:err`, `:read-error`), while
  `assert-no-result` checks the failure against literal facts (exit,
  marker, `""`). Each `:no-result` failure of `harness/cell-outcomes` is
  `(select-keys run [:exit :out :err :read-error])`. Make
  `assert-no-result` take the child and its run: for each outcome,
  `:pass? false`, one `:no-result` check, and that failure. Check the run
  facts (exit, marker in `:out`, `:err` `""`, string `:read-error`) once
  in each test on `run`. Labels and expectations unchanged.
  - test review pass 43 fix: `assert-no-result` of child and run;
    run facts once in each test.

## Implementation test review (pass 44)

- [x] Test the throw rule of `run-jvms` for each child kind. Plan
  "Runner": "A producer or compile child keeps the throw"; a `:cell`
  child, and a map with no `:kind` (default `:cell`), give `:read-error`
  and no throw. Only `unreadable-producer-result-test` covers the rule,
  with one `:producer` child. If the rule were `(= :producer kind)`, an
  `:aot-compile` or `:java-compile` child with an unreadable last line
  would not throw (its output is input to later children, which then
  fail with no read error), and every test still passes. The same is
  true if the default kind changed (`child-limit-test` children have no
  `:kind`, and print a readable line). Extract the rule of the
  `doseq` in `run-jvms` into a public pure function of the option maps
  and the results (for example `jvm-runner/read-failure`: nil, or the
  `ex-info` data of the first non-`:cell` child with `:read-error`:
  `:index`, `:exit`, `:out`, `:err`, `:read-error`), use it in
  `run-jvms`, and add one in-process `jvm-runner-test` test: a
  `:read-error` result gives the data for `:producer`, `:aot-compile` and
  `:java-compile`, and nil for `:cell` and for no `:kind`; with two such
  children, the index of the first. Keep
  `unreadable-producer-result-test` for the "after all children return"
  rule. New labels only.
  - test review pass 44 fix: public `jvm-runner/read-failure`;
    `jvm-runner-test/read-failure-test`.

## Implementation review (pass 20)

- [x] Make `run-jvms` return or throw only after all children of the
  call return, also when a child throws. Plan "Runner": `run-jvms`
  "returns when all children have returned". `(mapv deref ...)` derefs
  in input order, so an exception in `execute` of child i (for example
  `IOException` from `ProcessBuilder.start` when
  `VALUE_TYPE_VALHALLA_JAVA` names no executable, or a temp-file error)
  is rethrown as an `ExecutionException` at once. The later children of
  the call still run and hold permits while the next test starts its
  children, and their output is lost. Deref every future first (catch
  each exception), then throw the exception of the first failed child
  (with its index), then apply the `read-failure` rule. Add one
  `cell-runner-test` case: a child with a missing `:java` path at index
  0, then a cell child that sleeps, then writes a temp file (as
  `unreadable-producer-result-test`); assert the throw and that the file
  exists at the throw. State the rule in the `run-jvms` docstring. New
  labels only.
  - implementation review pass 20 fix: `run-jvms` derefs each future
    (catches `ExecutionException`), then throws ex-info `{:index i}` with
    the cause; `cell-runner-test/failed-start-child-test`; old deref loop
    fails it (implementation.md).
- [x] Name the child limit in the `doc/development/runtime.md` list of
  `cell-runner-test` cases. The text lists "policy rules, cell
  isolation, failing, crashing, unreadable and timed-out children", but
  `child-limit-test` (pass 36) also starts child JVMs and is the only
  test of `VALUE_TYPE_TEST_JVM_LIMIT` concurrency and the permit-wait
  rule. Add "the child limit" (and the unreadable producer throw, if
  "unreadable" does not cover it). Acceptance 6.
  - implementation review pass 20 fix: list names the unreadable
    producer, the failed start and the child limit.

## Implementation review (pass 21)

- [x] Make the start of the index-0 child of
  `cell-runner-test/failed-start-child-test` fail because of the missing
  `:java`, as its comment states. `missing` is `baseline` with another
  `:java`, and `baseline` has no `:form`. Thus `start-command` gives a
  command with a nil `-e` argument, and `ProcessBuilder.start` throws
  `NullPointerException` (message nil) before it looks for the
  executable; a valid `:java` gives the same throw. The test asserts only
  `(= {:index 0} data)`, so it does not show the start failure that the
  `run-jvms` docstring names (`IOException`). Give `missing` a `:form`
  (for example build it with `outcome-child` of `(assoc baseline :java
  ...)`), and assert that the cause (`ex-cause`) is a
  `java.io.IOException`. Labels unchanged.
  - implementation review pass 21 fix: `missing` built with
    `outcome-child`; `(instance? java.io.IOException (ex-cause e))`.
- [x] Retake the after record after passes 38–44 and implementation
  review pass 20. `after-record/cross-jvm.edn` is from `9cb4f7b` (pass
  37); later passes added labels and children in `cross-jvm`
  (`assert-child-stderr` labels of `child-limit-test`,
  `unreadable-producer-result-test`, `failed-start-child-test`,
  `read-result-test`, `read-failure-test`), and pass 20 changed
  `run-jvms`, which each `:fallback-test` command also uses. Each pass ran
  only its focused namespaces. Run the four commands at limit 4 and the
  two limit-1 commands with `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time
  -p`, run `check_record.clj` (no bound or mapping finding), replace
  `after-record/*.edn` when the summaries differ in more than times, and
  record the commit, assertion counts, time percentage and acceptance 3
  result in `implementation.md`. Do this after the step above.
  - implementation review pass 21 fix: record at `1be68f9` (`after32`,
    `after32-limit1`); `after-record/*.edn` replaced; no bound or mapping
    finding; acceptance 3 does not hold for `cell-runner-test` (75 / 72
    assertions; implementation.md).

## Implementation review (pass 22)

- [x] Make the assertions of `cell-runner-test/child-limit-test` the same
  at limit 1 and limit 4, so that acceptance 3 holds for `cross-jvm`.
  Pass 21 found that the test starts `(inc runner/limit)` children and
  calls `harness/assert-child-stderr` for each, so the `"child stderr"`
  count is 2 at limit 1 and 5 at limit 4 (75 / 72 assertions). The record
  shows this; no step addresses it. Keep one stderr assertion for each
  cell child (Scope 2) and make the child count independent of the limit
  at limit 4 or less: let `n` be `(max runner/limit d)`, with `d` the
  default limit (use `(runner/read-limit nil)`, not a second literal 4).
  Start the children through two `run-jvms` calls of 1 and `n` children,
  and assert `(= runner/limit (max-overlap ...))` and exit 0 for each of
  the `(inc n)` children. The timeout excludes the permit wait, so the
  last child at limit 1 does not time out. Update the test comment and
  the isolation exception row (children / configs). Check once that the
  serial-runner mutation and the semaphore-for-each-call mutation of pass
  39 still fail the test at limit 4. Then run `cross-jvm` at limit 4 and
  limit 1 with `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run
  `check_record.clj`, replace `after-record/cross-jvm.edn`, and record the
  commit, assertion counts, time percentage and the acceptance 3 result in
  `implementation.md`. If a design change is preferred (one assertion of
  all child stderr values, or an acceptance 3 exception), ask the user.
  - implementation review pass 22 fix: private `limit-count` (`(max
    runner/limit (runner/read-limit nil))`); calls of 1 and `limit-count`
    children; record at `ea25e65` (`after33`, `after33-limit1`);
    acceptance 3 holds (implementation.md).

## Implementation test review (pass 45)

- [x] Show that `jvm-runner/execute` waits `wait-ms`, not `:timeout-ms`.
  `wait-ms-test` and `cell-outcomes-test/cell-child-wait-test` test the
  pure parts only. With `execute` changed to wait `(:timeout-ms options
  120000)`, each test still passes: `timed-out-child-outcome-test` sleeps
  60 s, past both 2 s and 12 s, and no other child sets `:timeout-ms`
  with more than one cell. In `timed-out-child-outcome-test`, let cell 0
  print the marker, sleep past `:timeout-ms` (for example 4000 ms from
  cell start; JVM start is about 3 s), print a second marker and flush,
  then sleep 60 s. Assert both markers in `:out` (the second one is
  printed only when the wait is longer than `:timeout-ms`). Check once
  that the mutation fails the test. Same child count; no label change.
  - test review pass 45 fix: cell 0 prints `late-marker` after
    `past-timeout-ms` (4000); both markers asserted; mutation fails.

## Implementation test review (pass 46)

- [x] Test that `jvm-runner` reads `VALUE_TYPE_TEST_JVM_LIMIT` at
  namespace load. Design Scope 4: the variable sets the limit; an invalid
  value makes the load throw an exception whose message names the
  variable and the value. `read-limit-test` tests the pure parse only, and
  `child-limit-test` compares the overlap with `runner/limit`. If `limit`
  ignored the variable (for example `(read-limit nil)`), or read another
  name, each test still passes, and the limit-1 runs of acceptance 3 run
  at limit 4. In `cell-runner-test`, run one `:baseline` cell child
  (through `run-jvms`, so the limit and the record apply) whose cells each
  start a grandchild JVM (`java.home`/`bin/java`, the child
  `java.class.path`) with `ProcessBuilder` `environment` set: `"2"` →
  the grandchild prints `value-type.jvm-runner/limit` after `require`, 2;
  `"0"` → the `require` fails: nonzero exit, stderr contains
  `VALUE_TYPE_TEST_JVM_LIMIT` and `"0"`. No new runner option. Check once
  that the mutation `(read-limit nil)` fails the test. New labels only.
  - test review pass 46 fix: `cell-runner-test/limit-variable-load-test`
    (one cell child, two cells, grandchild for each); mutation fails
    (implementation.md).

## Implementation test review (pass 47)

- [x] Report a grandchild timeout in
  `cell-runner-test/limit-load-form`. After `.destroyForcibly` and
  `.waitFor`, the process is not alive, so `(if (.isAlive process#)
  :timeout ...)` never gives `:timeout`: a hung grandchild shows the kill
  exit code (for example 137) as `:exit`, and the failure looks like a
  load failure. Keep the `.waitFor` result (`finished?`) and give `:exit
  :timeout` when it is false, as `jvm-runner/execute` does. State
  `:timeout` in the docstring. No label change.
  - test review pass 47 fix: `finished?` kept; `:exit :timeout` when
    false; docstring states it.

## Implementation test review (pass 49)

- [x] Retake the `cross-jvm` after record after test review passes 45–47.
  `after-record/cross-jvm.edn` is from `ea25e65` (implementation review
  pass 22). Pass 45 added one assertion to `timed-out-child-outcome-test`,
  and pass 46 added `limit-variable-load-test` (one cell child, two
  grandchild JVMs, new labels, 9 assertions). Each pass ran only focused
  namespaces, and implementation.md states "not retaken" for each. Thus
  the record that acceptance 2 (labels, child-count bound, total time)
  and acceptance 3 (same labels at limit 1 and 4) read does not contain
  these labels, children or times. Run `cross-jvm` at limit 4 and limit 1
  with `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run
  `check_record.clj` (no bound or mapping finding), replace
  `after-record/cross-jvm.edn`, and record the commit, assertion counts,
  time percentage and acceptance 3 result in `implementation.md`.
  - test review pass 49 fix: record at `5cb0462` (`after34`,
    `after34-limit1`); `after-record/cross-jvm.edn` replaced; no bound or
    mapping finding; acceptance 3 holds (implementation.md).

## Implementation test review (pass 50)

- [x] Use one function for the late-cell throw check in
  `cell-runner-test`. `unreadable-producer-result-test` and
  `failed-start-child-test` each write again the same arrangement: a
  deleted temp `file`, a `cell` child (`outcome-child` of a form that
  sleeps `late-cell-ms`, then spits `file`), and `(try (runner/run-jvms
  [first-child cell]) [::no-throw] (catch clojure.lang.ExceptionInfo e
  [... (.exists file) ...]) (finally (.delete file)))`. Only the index-0
  child and the read of the exception differ. Add one private function
  of baseline and a function of `file` that gives the index-0 child (for
  example `late-cell-throw`), which gives `{:data :written? :cause}` or
  `::no-throw`, and use it in both tests. Update the comments. Labels
  and expectations unchanged.
  - test review pass 50 fix: private `late-cell-throw` of baseline and
    a function of `file`; both tests use it.
- [x] Use one source for the valid limit value of
  `cell-runner-test/limit-variable-load-test`. The test binds `invalid`
  once, but writes the valid value `"2"` three times (the context
  `[:limit-load "2"]`, `(limit-load-form "2")` and `:out "2"`). Bind
  `valid` beside `invalid`, and write the context, the form and the
  expected `:out` from it. Labels and expectations unchanged.
  - test review pass 50 fix: `valid` beside `invalid`.

## Implementation test review (pass 51)

- [x] Test the "first such child" rule of a child that throws in
  `run-jvms`. Docstring: "When a child throws ..., throw ex-info of the
  first such child (:index)". `failed-start-child-test` has one child
  that throws. If the rule took the last such child (for example `last`
  in place of `first` of the `keep-indexed`), each test still
  passes, and the `:index` of the throw names
  the wrong child. As pass 44 did for `read-failure`, extract the rule
  into a public pure function of the deref outcomes (for example
  `jvm-runner/thrown-failure`: nil, or `[index thrown]` of the first
  outcome with `:thrown`), use it in `run-jvms`, and add one in-process
  `jvm-runner-test` test: nil with no `:thrown`; index 1 of three
  outcomes with `:thrown` at 1 and 2. Keep `failed-start-child-test` for
  the "after all children return" rule. New labels only.
  - test review pass 51 fix: public `jvm-runner/thrown-failure`;
    `jvm-runner-test/thrown-failure-test`; `last` mutation fails.

## Implementation test review (pass 52)

- [x] Show that `run-jvms` stops a timed-out child before it returns.
  `run-jvm` docstring: "a timed-out child is stopped before returning".
  If `execute` did not call `.destroyForcibly` after the wait (the
  `when-not finished?` branch), `.waitFor` would block until the child
  ends by itself, and each test still passes:
  `timed-out-child-outcome-test` only becomes about 60 s slower (the cell
  sleeps 60000 ms after `late-marker`; `:exit` is still `:timeout`). The
  child then holds its permit past its wait. As `late-cell-throw` does,
  give cell 0 a deleted temp file that it writes after the 60000 ms
  sleep, and assert at the return of `run-jvms` that the file does not
  exist (delete it after). Check once that the mutation fails the test,
  then restore (record in `implementation.md`). Labels unchanged.
  - test review pass 52 fix: cell 0 writes a temp file after its 60000 ms
    sleep; `(false? written?)` at the return; mutation fails
    (implementation.md).

## Implementation test review (pass 53)

- [x] Retake the `cross-jvm` after record after test review passes 50–52.
  `after-record/cross-jvm.edn` is from `5cb0462` (pass 49). Pass 51 added
  `jvm-runner-test/thrown-failure-test` (new labels, 2 assertions; the
  record has no `thrown-failure` label), and pass 52 added one assertion
  and a temp file to `timed-out-child-outcome-test`. Each pass ran only
  focused namespaces, and implementation.md states "not retaken" for
  passes 51 and 52. Thus acceptance 2 (labels, child-count bound, total
  time) and acceptance 3 (same labels at limit 1 and 4) read a record
  without these labels. Run `cross-jvm` at limit 4 and limit 1 with
  `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run `check_record.clj`
  (no bound or mapping finding), replace `after-record/cross-jvm.edn`, and
  record the commit, assertion counts, time percentage and acceptance 3
  result in `implementation.md`.
  - test review pass 53 fix: record at `4482293` (`after35`,
    `after35-limit1`); `after-record/cross-jvm.edn` replaced; no bound or
    mapping finding; acceptance 3 holds (implementation.md).

## Implementation test review (pass 54)

- [x] Test that `run-jvms` removes the temporary output files of each
  child. `run-jvm` docstring: "Temporary output files are removed and a
  timed-out child is stopped before returning". Pass 52 tested the second
  part only. No test reads the `vt-jvm-` directory of `execute`: if the
  `run!` of `Files/deleteIfExists` were removed (or ran only when the
  start does not throw), each test still passes, and each child leaves a
  directory in `java.io.tmpdir`. A scan of `java.io.tmpdir` is not
  deterministic (other processes). As passes 43, 44 and 51 did, extract
  the rule into a public function (for example
  `jvm-runner/with-output-files`: create the directory and the `stdout`
  and `stderr` files, call a function of them, remove the three in a
  `finally`), use it in `execute`, and add one in-process
  `jvm-runner-test` test: after a function that returns, and after a
  function that throws, the three files that the function received do not
  exist (and the throw propagates). Check once that the mutation (no
  removal) fails the test. New labels only.
  - test review pass 54 fix: public `jvm-runner/with-output-files` (f of
    the stdout and stderr files); `jvm-runner-test/with-output-files-test`;
    mutation fails.
- [x] Use `str/starts-with?` and `str/includes?` for literal text in
  `cell-outcomes-test/assert-child-report-test`, as pass 42 did in the
  runner tests. It writes `(re-find #"^result check failed: " ...)` and
  `(re-find #":out \"\"" ...)` (a regex of literal text); these are the
  last such calls in `test/cross-jvm`. Labels and expectations unchanged.
  - test review pass 54 fix: `str/starts-with?`, `str/includes?`.

## Implementation test review (pass 55)

- [x] Use `jvm-runner/with-output-files` in
  `cell-runner-test/limit-load-form`. Pass 54 made the output-file rule
  of `execute` public and tested it, but the grandchild form writes the
  rule again: a `vt-limit-` temporary directory, `stdout` and `stderr`
  files, and a `finally` that removes the three (with `.delete`, not
  `Files/deleteIfExists`). When the rule changes, the grandchild keeps the
  old copy, and `with-output-files-test` does not cover it. The cell
  child has the runner on its classpath (the grandchild requires
  `value-type.jvm-runner`). Write the form as
  `(value-type.jvm-runner/with-output-files (fn [out# err#] ...))`, and
  remove the local directory and the local removal. Update the docstring.
  Labels and expectations unchanged.
  - test review pass 55 fix: form is `(do (require
    'value-type.jvm-runner) (value-type.jvm-runner/with-output-files (fn
    [out# err#] ...)))`; local directory and removal removed; docstring
    updated.

## Implementation test review (pass 56)

- [x] Use one function for a temporary directory that a test or test
  support removes. Four places write the rule again, in three forms:
  `jvm-runner/with-output-files` (`vt-jvm-`, three fixed files removed
  with `Files/deleteIfExists`), `aot-fixture/with-fixture` (`vt-aot-`,
  `(reverse (file-seq root))` with `Files/deleteIfExists`),
  `isolated-sibling-loader-test/public-isolated-sibling-construction-test`
  (`value-type-siblings`, `(reverse (file-seq root))` with
  `io/delete-file`), and `cell-runner-test` (`late-cell-throw` and
  `timed-out-child-outcome-test`: `(doto (File/createTempFile ...)
  (.delete))`, then `(finally (.delete file))`). When the rule changes
  (as pass 54 did for the runner), the other copies keep the old rule.
  Add one public function of a prefix and f of the directory (for
  example `with-temp-dir`: create it, call f, remove the tree in a
  `finally`) in a support namespace that does not require
  `jvm-runner` (design Scope 4: an in-process run such as
  `isolated-sibling-loader-test` does not read
  `VALUE_TYPE_TEST_JVM_LIMIT`). Use it in the four places (the written
  file of the two runner tests is a path in the directory).
  `with-output-files-test` stays the test of the removal. Labels and
  expectations unchanged.
  - test review pass 56 fix: `value-type.temp-dir/with-temp-dir` (prefix,
    f of the directory); used in `with-output-files`, `with-fixture`,
    `isolated-sibling-loader-test`, `late-cell-throw` and
    `timed-out-child-outcome-test`.
- [x] Assert the child-level stderr of the late cell child of
  `cell-runner-test/late-cell-throw`, or record the exception. Design
  Scope 2: the parent asserts child-level stderr once for each cell
  child; pass 38 added it to `child-limit-test`. The late child is a
  `:cell` child (`outcome-child`), but `run-jvms` throws, so
  `unreadable-producer-result-test` and `failed-start-child-test` lose
  its result: a late child that writes to stderr or exits non-zero after
  it writes the file passes. Select one: add the results of the call to
  the ex-data of each `run-jvms` throw (for example `:results`, nil for
  a child that throws), and call `harness/assert-child-stderr` and assert
  exit 0 for the late child in both tests; or state in implementation.md
  (isolation exception table or Scope 2 note) that the late fixture
  child of a throwing call has no stderr assertion, and why. New label
  only.
  - test review pass 56 fix: `:results` in the ex-data of each
    `run-jvms` throw; `assert-child-stderr` and exit 0 of the late child
    in both tests (implementation.md).

## Implementation test review (pass 57)

- [x] Make the late child checks of `cell-runner-test` give the same
  label, and report the missing throw first, when `run-jvms` does not
  throw. `late-cell-throw` gives `::no-throw` in that case, so the
  `{:keys [data written? cell late]}` destructuring gives nil for each
  key. Then `unreadable-producer-result-test` and
  `failed-start-child-test` call `(harness/assert-child-stderr nil nil)`
  before `(is (not= ::no-throw thrown))`. The first failure is a
  `"child stderr"` failure of a new label (`["child stderr" nil nil nil
  nil]`), not the missing throw, and the result of the late child (the
  return value of `run-jvms`) is lost. Make `late-cell-throw` always give
  the late child and its result (from the return value, or from
  `:results` of the ex-data), with the throw data or `::no-throw` in its
  own key, so that the stderr label is the same in both cases. Labels and
  expectations unchanged.
  - test review pass 57 fix: `late-cell-throw` gives `{:late-child
    :late-run :thrown}` (`:thrown` is `::no-throw` or `{:data :written?
    :cause}`); the throw check comes before `assert-child-stderr`.
- [x] Do not name a child map `:cell`. Design Concepts: a cell is one
  checked case, and a cell child runs cells. `late-cell-throw` gives the
  late child as `:cell` (local `cell`) and its result as `:late`;
  `timed-out-child-outcome-test` names the same pair `child` and `run`,
  as `harness/assert-child-stderr` (`child`, run map) does. Use one pair
  of names (for example `:late-child` and `:late-run`), and update the
  docstring. Labels and expectations unchanged.
  - test review pass 57 fix: `:late-child` and `:late-run`; docstring
    updated.
- [x] Retake the after record after test review passes 54–56.
  `after-record/*.edn` is from `4482293` (pass 53). Pass 54 added
  `jvm-runner-test/with-output-files-test` (the record has no
  `with-output-files-test` label), pass 56 added the `"child stderr"`
  labels of the late child in `cross-jvm`, and passes 54 and 56 changed
  `jvm-runner/with-output-files` (each child of the four commands),
  `aot-fixture/with-fixture` and `isolated-sibling-loader-test` (the
  `:fallback-test` commands). Each pass ran only focused namespaces
  ("After record not retaken"). After the steps above, run the four
  commands at limit 4 and the two limit-1 commands with
  `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run `check_record.clj`
  (no bound or mapping finding), replace `after-record/*.edn` when the
  summaries differ in more than times, and record the commit, assertion
  counts, time percentage and acceptance 3 result in `implementation.md`.
  - test review pass 57 fix: record at `5264320` (`after36`,
    `after36-limit1`); `after-record/cross-jvm.edn` replaced (fallback
    summaries differ in times only); no bound or mapping finding;
    acceptance 3 holds (implementation.md).

## Implementation test review (pass 58)

- [x] Use `temp/with-temp-dir` in `label-record-test/summary`. Pass 56
  made `value-type.temp-dir/with-temp-dir` the one rule for a temporary
  file that a test removes, but `label-record-test/summary` (in
  `test/cross-jvm`) still writes the old rule: `(File/createTempFile
  "label-record" ".edn")`, then `(finally (.delete f))`. It is the last
  such copy in `test/compatibility` and `test/cross-jvm`. When the rule
  changes, this copy keeps the old rule. Write the record to a path in
  the directory (as `late-cell-throw` does), and remove the `File`
  import. Also use one prefix form for the directories: the prefixes are
  `"vt-jvm-"`, `"vt-aot-"`, `"value-type-siblings"`, `"late-cell"` and
  `"timed-out-cell"` (for example `vt-` and a kebab-case name with a
  trailing `-`). Labels and expectations unchanged.
  - test review pass 58 fix: `temp/with-temp-dir` (`vt-label-record-`);
    prefixes `vt-` + kebab-case name + `-` (implementation.md).

## Implementation review (pass 24)

- [x] State both throw rules of `run-jvms` in plan "Runner". The plan
  states only the read-failure throw, and gives it `:results` "nil for a
  child that throws". But `run-jvms` checks `thrown-failure` first, so a
  read-failure throw has no child that throws, and its `:results` has no
  nil. The throw of a child whose start or run throws (implementation
  review pass 20: ex-info `{:index i :results r}` with the exception as
  the cause, after all children return) is not in the plan, but the
  comment of `cell-runner-test/failed-start-child-test` cites plan
  "Runner" for it. Add that rule to plan "Runner" (first such child, by
  `thrown-failure`; `:results` nil for each child that throws), and move
  "nil for a child that throws" to it. Code, labels and expectations
  unchanged.
  - implementation review pass 24 fix: plan "Runner" states the
    child-throw rule (`thrown-failure`, `:index`, `:results` nil for each
    child that throws, cause); read-failure `:results` has no nil.

## Code-shaper review (pass 32)

- [x] Use one function for the wait and stop rule of a child process.
  `jvm-runner/execute` writes the stop twice (`(.destroyForcibly
  process)` and `(.waitFor process)` in the `when-not finished?` branch,
  and again in the `finally` when `.isAlive`), and
  `cell-runner-test/limit-load-form` writes the full rule again (pass
  47: `.waitFor` with a time, `.destroyForcibly`, `.waitFor`, `(if
  finished? (.exitValue process) :timeout)`). Pass 55 removed the same
  kind of copy for the output files. When the rule changes, the
  grandchild keeps the old copy. Add one public `jvm-runner` function of
  a process and a wait in ms (for example `await-exit`: the exit value,
  or `:timeout` after the process is stopped), and one private stop
  function for the `finally`. Use them in `execute` and in
  `limit-load-form` (the grandchild already requires
  `value-type.jvm-runner`). Update the docstrings. Labels and
  expectations unchanged.
  - code-shaper pass 32 fix: public `jvm-runner/await-exit`, private
    `stop`; used in `execute` and `limit-load-form`.

- [x] Use one data shape and one idiom for the two failure rules of
  `run-jvms`. `read-failure` gives a map with `:index` (a `for` over
  `(map vector (range) option-maps results)`); `thrown-failure` gives a
  vector `[index thrown]` (`keep-indexed`). `run-jvms` then builds the
  ex-data of each throw in a different way (`{:index index :results
  results}`, and `(assoc failure :results results)`). Make
  `thrown-failure` give a map (for example `{:index i :thrown t}`), use
  one idiom for "the first indexed entry that matches" in both, and build
  both ex-data maps with `(assoc ... :results results)`. Update
  `thrown-failure-test` to the map shape (the same fact). Labels
  unchanged.
  - code-shaper pass 32 fix: private `first-indexed`; `thrown-failure`
    gives `{:index :thrown}`; both ex-data maps by `(assoc ... :results
    results)`.

- [x] Use one source for the limit variable name and the limit bound.
  The text `"VALUE_TYPE_TEST_JVM_LIMIT"` is written 3 times in
  `jvm-runner` (`getenv`, the message, `:variable`), again in
  `cell-runner-test` (private `limit-variable`) and in
  `jvm-runner-test/read-limit-test`. The `read-limit` message writes
  `2147483647` as a literal, but the check uses `Integer/MAX_VALUE`. Add
  a public `jvm-runner/limit-variable`, use it in these places (remove
  the private copy), and build the message bound from
  `Integer/MAX_VALUE`. Messages, labels and expectations unchanged.
  - code-shaper pass 32 fix: public `jvm-runner/limit-variable`; message
    bound from `Integer/MAX_VALUE`.

- [x] Use one function for the run facts of a failure. The keys `[:exit
  :out :err :read-error]` of a run are selected in
  `jvm-runner/read-failure`, `cell-harness/assert-child` (the exit
  message), `cell-harness/cell-outcomes` (as `(cond-> {:exit :out :err}
  read-error (assoc ...))`), and 3 times in `cell-runner-test`
  (`scope-3-policy-rules-test`, `cell-isolation-test`,
  `assert-no-result`). Add one public `jvm-runner` function of a run (for
  example `run-facts`) and use it in these places. Labels and
  expectations unchanged.
  - code-shaper pass 32 fix: public `jvm-runner/run-facts`, used in the
    six places.

## Code-shaper review (pass 33)

- [x] Use one failure message for the assertion of a run. Pass 32 added
  `jvm-runner/run-facts` and uses `(pr-str (runner/run-facts run))` as
  the message in `cell-runner-test` (`scope-3-policy-rules-test`,
  `cell-isolation-test`). Other places use other forms:
  `cell-runner-test/child-limit-test` writes the key selection again
  (`(map #(select-keys % [:exit :err :read-error]) results)`, without
  `:out`), and the support functions give the full run map
  (`cell-harness/produce`, `aot-fixture/compile-producer`,
  `aot-fixture/javac`, `aot-test/produced?`: `(is (= 0 (:exit r)) r)`).
  Use `run-facts` for each message (in `child-limit-test`, `(pr-str (map
  runner/run-facts results))`). Labels and expectations unchanged.
  - code-shaper pass 33 fix: `(pr-str (runner/run-facts r))` message in
    `child-limit-test`, `cell-harness/produce`,
    `aot-fixture/compile-producer`, `aot-fixture/javac`,
    `aot-test/produced?`.
- [x] Use one source for the record variable name. Pass 32 made
  `jvm-runner/limit-variable` the one source of
  `"VALUE_TYPE_TEST_JVM_LIMIT"`, but `"VALUE_TYPE_TEST_RECORD"` is written
  in `label-record` (private `record-path`) and again in
  `cell-runner-test/limit-load-form` (`(.remove "VALUE_TYPE_TEST_RECORD")`).
  When the name changes, the grandchild keeps the old name and records
  into the parent record file. Add a public `label-record/record-variable`,
  use it in `record-path` and in `limit-load-form` (the cell child has
  `label-record` on its classpath; `jvm-runner` requires it). Labels and
  expectations unchanged.
  - code-shaper pass 33 fix: public `label-record/record-variable`; used
    in `record-path` and `limit-load-form`.

## Code-shaper review (pass 34)

- [x] Use one function for the run assertions of a support child. Pass 33
  made `(pr-str (runner/run-facts r))` the one message, but five places
  write again the same rule (one `is` for each expected run key, with that
  message; true when all pass): `cell-harness/produce` (`:exit 0`, `:err
  ""`, `every? true?`), `aot-fixture/compile-producer` (`:exit 0`, `:err
  ""`, `:result :compiled` without `:after`, `every? true?`),
  `aot-fixture/javac` (`:exit 0`, `:result 0`), `aot-test/produced?`
  (`:exit 0`) and `cell-harness/assert-child` (`:exit 0`); also
  `cell-runner-test` (`scope-3-policy-rules-test`, `cell-isolation-test`).
  Each place binds or writes the message again. Add one public
  `cell-harness` function of a run and a map of expected run values (for
  example `assert-run`: one `is` for each key, in map order, message
  `run-facts`; true when all pass), and use it in these places. Labels,
  assertion counts and expectations unchanged.
  - code-shaper pass 34 fix: public `cell-harness/assert-run`; used in
    the seven places.
- [x] Use one function for the start, wait and read of a process.
  `cell-runner-test/limit-load-form` writes again the run rule of
  `jvm-runner/execute`: `ProcessBuilder` of a command,
  `.redirectOutput`/`.redirectError` to the `with-output-files` files,
  `.start`, `await-exit`, then `{:exit :out :err}` of the slurped files.
  The copies differ already: `execute` stops a live process in a
  `finally` (for example when the wait is interrupted); the grandchild
  does not. Passes 32 and 55 removed the other copies of this rule. Add
  one public `jvm-runner` function of a command vector, an environment
  change map (nil value: remove the variable) and a wait in ms (for
  example `run-command`: `with-output-files`, start, `await-exit`, stop in
  a `finally`; gives `{:exit :out :err}`). Use it in `execute` (then
  `read-result`) and in `limit-load-form` (`(update :out str/trim)` of
  its value). Make `stop` private to it, update the docstrings, and wrap
  the `limit-load-form` docstring (one line is not wrapped since pass 33).
  Labels and expectations unchanged.
  - code-shaper pass 34 fix: public `jvm-runner/run-command`; used in
    `execute` and `limit-load-form`; docstrings updated.

## Code-shaper review (pass 35)

- [x] Use `cell-harness/assert-run` for each remaining assertion of run
  keys. Pass 34 made `assert-run` the one rule (one `is` for each key,
  message `run-facts`), but these places still write `is` on run keys
  with no message or with the full run map: `aot-test/source-free-value-aot-loading-test`
  (`(is (= ... (:result producer)) producer)`),
  `field-aot-test` (`(is (= {:source-absent? true} (:result compiler))
  compiler)`), and `cell-runner-test`: `crashing-child-outcome-test`
  (`:exit 3`, `:err ""`), `unreadable-result-outcome-test` (`:exit 0`,
  `:err ""`), `timed-out-child-outcome-test` (`:exit :timeout`, `:err
  ""`), `unreadable-producer-result-test` and `failed-start-child-test`
  (`:exit 0` of `late-run`), and `child-limit-test` (`(map :exit
  results)` with its own message: use `assert-run` for each run). A
  failure of these shows no run facts (or other facts than the other
  places). Use `(harness/assert-run run {...})` in each place. Labels,
  assertion counts (except `child-limit-test`, where one `is` of all
  exits becomes one `is` for each child; state it in `implementation.md`)
  and expectations unchanged.
  - code-shaper pass 35 fix: `assert-run` in the nine places;
    `child-limit-test` has one `:exit` assertion for each child
    (implementation.md).
- [x] Use one source for the default compile result of
  `aot-fixture`. `compile-clojure-child` writes the default `:after` as
  `"(prn :compiled)"`, and `compile-producer` writes `:compiled` again as
  the expected `:result` when there is no `:after`. When the default
  changes, the check keeps the old value. Add one private value (for
  example `compiled`, `:compiled`), and build the default `:after` and
  the expected result from it. Labels and expectations unchanged.
  - code-shaper pass 35 fix: private `aot-fixture/compiled`; default
    `:after` is `(pr-str (list 'prn compiled))`.

## Code-shaper review (pass 36)

- [x] Assert the child-level stderr of a cell child once, with the
  `assert-run` message. Pass 35 put `:err ""` in the `assert-run` map of
  `cell-runner-test` `crashing-child-outcome-test` (`{:exit 3 :err ""}`),
  `unreadable-result-outcome-test` (`{:exit 0 :err ""}`) and
  `timed-out-child-outcome-test` (`{:exit :timeout :err ""}`). Each of
  these tests also calls `harness/assert-child-stderr` of the same run, so
  the same fact is asserted two times (design Scope 2: "once for each cell
  child"). Also `assert-child-stderr` writes `(t/is (= "" err))` with no
  message, so its failure shows no run facts, unlike `assert-run`. Write
  `assert-child-stderr` as `(assert-run run {:err ""})` in its `testing`
  context, and remove `:err ""` from the three `assert-run` maps. Labels
  and expectations unchanged; state the count change (−1 in each of the
  three tests, the same at limit 1 and 4) in `implementation.md`.
  - code-shaper pass 36 fix: `assert-child-stderr` is `(assert-run run
    {:err ""})` in its context; `:err ""` removed from the three maps
    (implementation.md).
- [x] Use one source for the Java executable and classpath of the current
  JVM. `cell-runner-test/limit-load-form` writes again the `:selected`
  rule of `jvm-config/configuration`: `(str (System/getProperty
  "java.home") "/bin/java")` and `(System/getProperty
  "java.class.path")`. When the rule changes, the grandchild keeps the old
  copy. Add one public `jvm-config` function of the current JVM (for
  example `current-runtime`: `{:java :classpath}`), use it in
  `configuration` (`:selected`, and the `classpath` local) and in the
  `limit-load-form` grandchild command (the cell child has `jvm-config` on
  its classpath; require it in the form). Update the docstring. Labels and
  expectations unchanged.
  - code-shaper pass 36 fix: public `jvm-config/current-runtime`; used in
    `configuration` and `limit-load-form`.

## Code-shaper review (pass 37)

- [x] Use one source for the runtime facts of an array child. The child
  side writes the map `{:runtime :policy :flags :require-order}` three
  times: `array-matrix-checks/check` and `array-sequence-checks/check`
  (`:runtime` from `"java.runtime.version"`) and
  `array-info-boundary-test/control-form` (`:runtime` from
  `"java.version"`, the property of `jvm-config/configuration`). Each
  copy reads `"value-type.fallback"` and the `RuntimeMXBean` input
  arguments again. The parent side writes the check of the four keys three
  times, in each description/predicate pair:
  `array-matrix-test/check-expected`,
  `array-sequence-boundary-test/expected` and
  `array-info-boundary-test/control-expected` (pass 12 bound only
  `:flags`). Add one child-safe function of require-order in
  `cell-registry` (for example `runtime-facts`, `:runtime` from
  `java.version`), and use it in the three child forms. Add one
  `array-capability` function of the expected `:runtime` (a description
  and a predicate), configuration, policy and require-order that gives
  the description part and the predicate of the four keys, and use it in
  the three pairs. Labels and expectations unchanged.
  - code-shaper pass 37 fix: child-safe `cell-registry/runtime-facts`
    (`:runtime` from `java.version`); `array-capability/runtime-expected`
    gives `[description predicate]`; used in the three forms and pairs.
- [x] Use one function for the outcome of a rejected array call. The map
  `{:exception :data :message :cause}` of a `clojure.lang.ExceptionInfo`
  (`(.getName (class e))`, `ex-data`, `ex-message`, `(some-> e ex-cause
  class .getName)`) is written three times:
  `array-matrix-checks/unavailable-call`,
  `array-sequence-checks/unavailable-call` (with `:status` and
  `:effects`) and `array-info-boundary-test/control-form`. Add one
  child-safe `cell-registry` function of the exception (beside `thrown`),
  and use it in the three places (`merge` the other keys). Labels and
  expectations unchanged.
  - code-shaper pass 37 fix: child-safe `cell-registry/rejection`; used
    in the three places.
- [x] Use `jvm-config/preview-option` for each remaining
  `"--enable-preview"`. Pass 31 made it the one source, but
  `aot-fixture/compile-java-child` (the javac option),
  `array-capability/rejection-names` and
  `consumer-aot-checks/unavailable-names` (the option that the product
  message names) write the literal again. `aot-fixture` and
  `array-capability` already require `jvm-config`; `jvm-config` is
  baseline-safe, so `consumer-aot-checks` can require it. Labels and
  expectations unchanged.
  - code-shaper pass 37 fix: `config/preview-option` in `aot-fixture`,
    `array-capability` and `consumer-aot-checks`.
- [x] Make `cell-runner-test/limit-load-form` locally comprehensible. The
  form names `value-type.label-record/record-variable` and
  `clojure.string/trim`, but requires only `value-type.jvm-config` and
  `value-type.jvm-runner`; these load only through the requires of
  `jvm-runner`. Require each namespace that the form names. Also write the
  grandchild form as data (`(pr-str '(do ...))`), as `limit-form` does,
  not as a source string. Labels and expectations unchanged.
  - code-shaper pass 37 fix: the form requires `clojure.string` and
    `value-type.label-record`; the grandchild form is `~(pr-str '(do
    ...))`.

## Code-shaper review (pass 38)

- [x] Use `harness/detail?` for the `'<detail>` runtime of
  `array-matrix-test/check-expected` and
  `array-sequence-boundary-test/expected`. Pass 27 made `detail?` the one
  predicate of a present runtime `<detail>`, but pass 37 gives
  `capability/runtime-expected` the pair `['<detail> seq]` in both places
  (`seq` is not the `detail?` rule: a blank string passes). Write
  `['<detail> harness/detail?]`. Labels and expectations unchanged.
  - code-shaper pass 38 fix: `['<detail> harness/detail?]` in both.
- [x] Use one source for the `--add-exports` text of an array capability
  message. `array-capability/rejection-names` writes `"--add-exports"`,
  and `array-info-boundary-test` writes it again in `control-expected`
  (`(list 'contains "--add-exports")`) and in `array-outcome?`
  (`str/includes?` with its own nil guard, not
  `message-checks/message-names?`). Add one public `array-capability`
  value (for example `export-name`, beside `export-option`), use it in
  `rejection-names`, and in `array-info-boundary-test` bind the names once
  (for example `[capability/export-name]`) and use them in the
  description (`list* 'contains`) and the predicate (`message-names?`), as
  `rejection-description` and `rejection?` do. Labels and expectations
  unchanged.
  - code-shaper pass 38 fix: public `array-capability/export-name`;
    private `unavailable-names` in `array-info-boundary-test`, used by
    `list* 'contains` and `message-names?`.
- [x] Write `array-info-boundary-test/control-form` with one idiom. The
  form mixes `(list 'do (list 'run! 'require (list 'quote ...)) (list
  'require ''...))` with a syntax-quoted `assoc` form. Write the whole
  form as one syntax-quoted form (`(do (run! require '~require-order)
  (require 'value-type.array-info-observation) (assoc ...))`), as
  `cell-runner-test/limit-load-form` is. Labels and expectations
  unchanged.
  - code-shaper pass 38 fix: one syntax-quoted form.

## Code-shaper review (pass 39)

- [x] Use one source for the six array runtimes. `array-capability/allocation-configurations`
  builds baseline, preview off, Valhalla with only `export-option`,
  Valhalla, Valhalla with `export-option`, and Valhalla with
  `export-option` and `"-XX:-UseArrayFlattening"`.
  `array-info-boundary-test/safe-inspection-boundary-test` builds the same
  six runtimes again (labels `:java8` … `:no-flattening`), and
  `export-only-capabilities-test` builds the export-only runtime a third
  time. When a runtime changes (for example a flag), the copies keep the
  old one. Add one public `array-capability` function of the label to the
  runtime (for example `runtimes`: a vector of `[label runtime]`, in this
  order). Build `allocation-configurations` and the two
  `array-info-boundary-test` row sets from it, and write
  `"-XX:-UseArrayFlattening"` once. Labels and expectations unchanged.
  - code-shaper pass 39 fix: public `array-capability/runtimes` and
    `runtime`; `allocation-configurations` and both
    `array-info-boundary-test` row sets use them.
- [x] Put the exception class in the rejection rule of an array call.
  `capability/rejection-description` and `capability/rejection?` check
  `:data`, `:cause` and `:message` of a `cell-registry/rejection` map, but
  not its `:exception`. Thus `array-matrix-test` (`rejection?`,
  `check-expected`) and `array-sequence-boundary-test` (`rejected?`,
  `expected`) each write `"clojure.lang.ExceptionInfo"` again, in the
  predicate and in the description. Add `:exception` to the description
  and the predicate in `array-capability`, and remove the four copies
  (the tests keep only `:status` and `:effects`). Labels and expectations
  unchanged.
  - code-shaper pass 39 fix: `:exception` in `rejection-description` and
    `rejection?`; `array-boundary-test` gives `registry/rejection`
    (implementation.md).
- [x] Use the child and parent rules of an array rejection in
  `array-info-boundary-test/export-only-capabilities-test`.
  `export-only-form` gives `(select-keys (ex-data e) [:value-type/error
  :reason])` for the rejected allocation, not `cell-registry/rejection`,
  and `export-only-expected` writes the `:data` of
  `capability/rejection-description` again (`:array-unavailable`,
  `:unsupported-class-version`). The `:cause` and `:message` checks of the
  same allocation row of `array-matrix-test` do not apply here. Also the
  form writes the `array-info` read (`update` of `:component-class` with
  `.getName`) again, as `control-form` does. Give the allocation outcome
  by `registry/rejection`, and make the expected a `described` predicate
  from `capability/rejection-description` and `capability/rejection?` of
  `:unsupported-class-version`. Bind one form of the `array-info` read
  and use it in both child forms. Labels unchanged; the rejection
  expectation adds `:cause` and `:message`.
  - code-shaper pass 39 fix: `registry/rejection`; `described` expected;
    private `inspect-form` in both child forms.
- [x] Use one source for the facts of the current JVM. `jvm-config/configuration`
  reads `"java.version"` and the `RuntimeMXBean` input arguments, and
  `cell-registry/runtime-facts` reads both again. When the rule changes,
  the copies differ (pass 37 aligned `:runtime` to `java.version` by
  hand). Add one child-safe `cell-registry` function (for example
  `jvm-facts`: `{:runtime :flags}`), use it in `runtime-facts`, and make
  `jvm-config` require `cell-registry` and use it in `configuration`.
  Labels and expectations unchanged.
  - code-shaper pass 39 fix: child-safe `cell-registry/jvm-facts`; used
    in `runtime-facts` and `configuration`.
- [x] Give the result count assertion of `cell-harness/assert-child` the
  `assert-run` message. Pass 35 made the `run-facts` message the one
  message of a run assertion, but `(t/is (= (count cells) (when (vector?
  result) (count result))))` has no message, so its failure shows no run
  facts. Add `(pr-str (runner/run-facts run))` as its message. Labels and
  expectations unchanged.
  - code-shaper pass 39 fix: message added.

## Code-shaper review (pass 40)

- [x] Use one child function for a call that must give an array
  capability rejection. The child rule (call, catch
  `clojure.lang.ExceptionInfo`, give `registry/rejection`) is written 4
  times, with 4 shapes for an unexpected success:
  `array-matrix-checks/unavailable-call` (`{:status
  :unexpected-success}`), `array-sequence-checks/unavailable-call`
  (`{:status :unexpected-success :effects n}`),
  `array-boundary-test/control-form` (the return value of `make-array`)
  and `array-info-boundary-test/export-only-form`
  (`:unexpected-success`). The matrix and sequence copies also add
  `:status :rejected`; the other two do not. Add one child-safe
  `cell-registry` function of f (beside `rejection`; for example
  `rejected-call`: `(rejection e)` for an `ExceptionInfo`, else one
  unexpected-success map), and use it in the four places (the sequence
  check merges `:effects`). Labels unchanged; state any expectation change
  in `implementation.md`.
  - code-shaper pass 40 fix: child-safe `cell-registry/rejected-call`;
    used in the four places (implementation.md).
- [x] Use one description and one predicate for the repeated allocation
  rejections. Four places write again `(list 'repeat
  registry/attempt-count (capability/rejection-description reason))`
  (with `assoc` of extra keys) and `(and (capability/repeated? xs)
  (every? ... xs))` of `capability/rejection?`:
  `array-boundary-test` (`unavailable?` and its description),
  `array-info-boundary-test/export-only-expected`,
  `array-matrix-test/check-expected` (with private `rejection?` for
  `:status :rejected`) and `array-sequence-boundary-test/rejected?` and
  `expected` (`:status :rejected :effects 0`). Add one public
  `array-capability` function of reason and a map of extra keys (for
  example `repeated-rejection`: `[description predicate]` of
  `attempt-count` equal rejections that have the extra keys), use it in
  the four places, and remove `array-matrix-test/rejection?`. Labels and
  expectations unchanged.
  - code-shaper pass 40 fix: public `array-capability/repeated-rejection`
    (adds `:status :rejected`); used in the four places;
    `array-matrix-test/rejection?` and `array-boundary-test/unavailable?`
    removed.
- [x] Use one source for the class name of an array rejection. Pass 39
  made `array-capability/rejection-exception` private, but
  `array-info-boundary-test` writes `"clojure.lang.ExceptionInfo"` again
  in `invalid-outcome` and `unavailable-outcome`, and
  `unavailable-predicates-test/array-rejection` writes it in its fixture.
  When the class changes, these copies keep the old name. Make the value
  public (for example `capability/rejection-exception`) and use it in the
  three places. Labels and expectations unchanged.
  - code-shaper pass 40 fix: public `capability/rejection-exception`;
    used in the three places.

## Code-shaper review (pass 41)

- [x] Use one function for the allocation matrix children.
  `array-matrix-test/configuration-matrix-test` and
  `array-sequence-boundary-test/sequence-configuration-matrix-test` write
  the same `for` again (`capability/allocation-configurations`,
  `config/policies`, `capability/require-orders`), the same
  `harness/cell-child` (`(assoc configuration :policy policy)`, context
  `(pr-str [(:jvm-opts configuration) reason policy require-order])`,
  `registry/check-form` of the check with `(list 'quote require-order)`
  and `(nil? reason)`), and the same runtime pair `['<detail>
  harness/detail?]` of `capability/runtime-expected`. Only the check
  symbol and the outcome expectation differ. When the axis or the context
  changes, the copies differ. Add one public `array-capability` function
  of the check symbol and a function of configuration, reason, policy
  and require-order that gives the expected (for example
  `allocation-children`), and use it in both tests. Labels and
  expectations unchanged.
  - code-shaper pass 41 fix: public `array-capability/allocation-children`
    (check, expected of reason); runtime pair once (implementation.md).
- [x] Use `cell-registry` for each child call that catches an array
  rejection. Pass 40 made `rejected-call` the one rule (call f, catch
  `clojure.lang.ExceptionInfo`, give `rejection`), but
  `array-info-boundary-test/control-form` writes it again for the
  inspection outcomes (`(try {:info ...} (catch clojure.lang.ExceptionInfo
  e# (value-type.cell-registry/rejection e#)))`), with a success map and
  no `:status`. Add one child-safe `cell-registry` function of f and a
  function of the return value (for example `guarded-call`: the success
  map, or `rejection` of the `ExceptionInfo`), build `rejected-call` from
  it, and use it in `control-form`. Also write `(registry/twice (fn []
  (unavailable-call)))` in `array-matrix-checks/check` as
  `(registry/twice unavailable-call)`. Labels unchanged; state any
  expectation change in `implementation.md`.
  - code-shaper pass 41 fix: child-safe `cell-registry/guarded-call`;
    `rejected-call` built from it; used in `control-form`; inspection
    rejections add `:status :rejected` (implementation.md).
- [x] Use one description and one predicate for the `(repeat n x)` form.
  `cell-harness/described` defines the form, but each user writes the
  pair again: `capability/repeated-rejection` (`(list 'repeat
  registry/attempt-count ...)` with `repeated?` and `every?`),
  `array-info-boundary-test/control-expected` (two `(list 'repeat
  registry/attempt-count ...)` descriptions, and a predicate of
  `capability/repeated?`, `split-at`, `(into [] cat invalid)` and `(first
  valid)`), and `field-transfer-test/unavailable` (nested `repeat`, with
  `mapv count` and `apply concat`). Also the inspection rejection of
  `control-expected` (`unavailable-outcome` with `:message (list*
  'contains unavailable-names)`, and `array-outcome?`) is a description
  and predicate written apart, unlike `capability/rejection-description`
  and `rejection?`. Add one public `cell-harness` function of n and
  `[description predicate]` (for example `repeated`: `[(list 'repeat n
  description) predicate of n items that each pass]`), and use it in the
  three places; give the inspection rejection one `[description
  predicate]` pair. Keep the equal-outcomes check of
  `capability/repeated?` where it applies. Labels and expectations
  unchanged.
  - code-shaper pass 41 fix: public `cell-harness/repeated`; used in
    `repeated-rejection`, `control-expected` and
    `field-transfer-test/unavailable`; inspection rejection is
    `unavailable-expected`.

## Implementation review (pass 26)

- [x] Retake the full verification and the after record after
  code-shaper passes 32–41. The last run of the six commands is at
  `5264320` (test review pass 57). Since then, these passes changed code
  that each documented command runs: `jvm-runner/execute` (now
  `run-command` and `await-exit`, for each child), `jvm-config/configuration`
  (now `registry/jvm-facts` and `current-runtime`; it gives `:available?`
  and `:selected` from the parent flags), `aot-fixture` and
  `cell-harness/assert-run` (support assertions of the `:fallback-test`
  commands), and the array check forms and expectations. Each pass ran
  only focused namespaces under the Java 8 parent, so the
  `fallback-preview-off` and `fallback-preview-on` commands did not run
  on this code. Passes 35 and 36 changed the `cross-jvm` assertion count
  (implementation.md: "−3 assertions in `cross-jvm`"), so
  `after-record/cross-jvm.edn` does not agree with the code. Run the four
  commands at limit 4 and the two limit-1 commands with
  `VALUE_TYPE_TEST_RECORD` and `/usr/bin/time -p`, run `check_record.clj`
  (no bound or mapping finding), replace `after-record/*.edn` when the
  summaries differ in more than times, and record the commit, assertion
  counts, time percentage and acceptance 3 result in `implementation.md`.
  - implementation review pass 26 fix: record at `d108b6d` (`after37`,
    `after37-limit1`); summaries differ in times only, kept; no bound or
    mapping finding; acceptance 3 holds (implementation.md).

## Implementation review (pass 27)

- [x] Use `harness/repeated` as the one count rule of a `(repeat n x)`
  outcome. Pass 41 made `cell-harness/repeated` check `(= n (count xs))`,
  but `array-capability/repeated?` checks `(= registry/attempt-count
  (count outcomes))` again. Each user also applies a `harness/repeated`
  predicate to the same outcomes: `capability/repeated-rejection` (`(and
  (repeated? outcomes) (rejections? outcomes))`) and
  `array-info-boundary-test/control-expected` (`(every?
  capability/repeated? outcomes)` with `invalid?` and `valid?`). Thus the
  count is checked two times, by two rules. Make `capability/repeated?`
  check only that the outcomes are equal (rename it, for example
  `equal-outcomes?`), and update its docstring and the two users. Also
  bind `(native-expected java8? supported?)` once in `control-expected`
  (it is written for the description and again for the predicate).
  Labels and expectations unchanged.
  - implementation review pass 27 fix: `capability/equal-outcomes?`
    (no count); checked after the `harness/repeated` predicates; `native`
    bound once in `control-expected`.

## Implementation test review (pass 61)

- [x] Make the limit tests state the variable name of design Scope 4.
  Code-shaper pass 32 replaced the literal in the tests with
  `runner/limit-variable`: `cell-runner-test/limit-variable-load-test`
  (`texts`, and the grandchild environment key in `limit-load-form`) and
  `jvm-runner-test/read-limit-test` (message text). Thus the expected
  value comes from the code under test: when `limit-variable` changes
  (for example to `"VT_LIMIT"`), `limit` reads another variable, the
  documented `VALUE_TYPE_TEST_JVM_LIMIT` has no effect, and all tests
  still pass. Use the literal `"VALUE_TYPE_TEST_JVM_LIMIT"` in these
  three places (one private test constant in each namespace is
  sufficient); keep `jvm-runner/limit-variable` in the runner. Add a
  test comment that the name is the Scope 4 contract, so a later
  shape pass does not replace it again. Check once that the
  `limit-variable` mutation above fails both tests (record in
  `implementation.md`). Labels and expectations unchanged.
  - test review pass 61 fix: private literal `limit-variable` (Scope 4
    contract comment) in `jvm-runner-test` and `cell-runner-test`;
    mutation fails both tests (implementation.md).

## Implementation test review (pass 62)

- [x] Show that `assert-child` fails its support assertions.
  `cell-outcomes-test/assert-child-report-test` gives each run `:err ""`
  and `:exit 0`, so no test makes the `"child stderr"` or the grouped
  `"child"` exit assertion fail. When `assert-child-stderr` asserts
  nothing (for example `(assert-run run (select-keys run [:err]))`) or
  the grouped exit check is removed, all tests still pass; design Scope 2
  (child-level stderr must be empty) and "Failures stay visible" then
  have no evidence. Add two cases with `collect-reports`: a one-cell
  child whose run has `:err "x"` and a passing cell gives exactly one
  `:fail`, with context `(pr-str ["child stderr" "java" [] "silent"
  nil])` and a message that contains `:err "x"`; a two-cell child whose
  run has `:exit 1` and two passing entries gives exactly one `:fail`,
  with context `(pr-str ["child" "java" [] "silent" nil])`. Check once
  that each mutation above fails its case (record in
  `implementation.md`). New labels only; after record not retaken.
  - test review pass 62 fix: `assert-child-report-test` cases "child
    stderr" and "child exit"; exit case also has the two cell
    `:no-result` fails (implementation.md); both mutations fail.

## Code-shaper review (pass 42)

- [x] Use one source for the policy property name. Passes 32 and 33 made
  `jvm-runner/limit-variable` and `label-record/record-variable` the one
  source of their names, but the system property `"value-type.fallback"`
  is written in `jvm-runner/policy-option` (`"-Dvalue-type.fallback="`)
  and again in `cell-registry/runtime-facts` (`System/getProperty`). The
  first sets the policy of a child; the second reads it back for the
  array checks (`array-capability/runtime-expected` compares the two).
  When the name changes in one place, the child policy and the reported
  `:policy` differ. Add one public child-safe `cell-registry` value (for
  example `policy-property`), use it in `runtime-facts`, and make
  `jvm-runner` require `cell-registry` and build `policy-option` from it.
  Update the docstrings. Labels and expectations unchanged.
  - code-shaper pass 42 fix: public child-safe
    `cell-registry/policy-property`; used in `runtime-facts` and
    `jvm-runner/policy-option`; docstrings updated.

## Code-shaper review (pass 43)

- [x] Use one child-safe function and one shape for the failure map of a
  failed type definition. The rule (catch, `registry/thrown` of the
  exception, `registry/absent?` of the type) is written 5 times, in two
  shapes: `(assoc (registry/thrown thrown) :absent? ...)` in
  `transfer-checks/attempt`, `expansion-checks/attempt` and
  `runtime-test/public-definition-test`; `{:absent? ... :rejection
  (registry/thrown thrown)}` in `cell-runner-test/fallback-cell-form` and
  `identity-loader-checks/stale-sibling-strict-case`. Plan "Key
  placement" adds `:absent?` to the failure map. Add one child-safe
  `cell-registry` function of the exception, ns-or-name and simple-name
  (for example `failed-definition`: `(assoc (thrown error) :absent?
  (absent? ns-or-name simple-name))`), and use it in the five places
  (update the namespace docstring). Then write
  `loader-policy-test/strict-rejection` with `harness/rejections` (`{:rejected?
  true :absent? true}`), and the `scope-3-policy-rules-test` expectation in
  the same shape. Labels unchanged; state the expectation shape change in
  `implementation.md`.
  - code-shaper pass 43 fix: child-safe `cell-registry/failed-definition`;
    used in the five places; `strict-rejection` is `harness/rejections`
    (implementation.md).
- [x] Use the `cell-harness` values for the strict rejection and the warning
  line in `cell-runner-test/scope-3-policy-rules-test`. The test writes
  again `{:class "java.lang.Error" :message (str harness/warning-prefix
  detail) :chain ["clojure.lang.Compiler$CompilerException"
  "java.lang.Error"]}`, which is `(assoc (harness/policy-rejection detail)
  :chain harness/source-rejection-chain)`, and `(str rejection-message
  \newline)`, which is the private `cell-harness/warning-line` (the rule
  of `expected-err`). When the rejection or warning rule changes, the copy
  keeps the old text. Use `policy-rejection` and `source-rejection-chain`,
  and make `warning-line` public and use it. Labels and expectations
  unchanged.
  - code-shaper pass 43 fix: `policy-rejection`, `source-rejection-chain`
    and public `harness/warning-line` in the test.
- [x] Use one source for the cell namespace name. `cell-harness/cell-ns-name`
  gives `(str cell-ns-prefix index)`, and `cell-harness/child-form` writes
  the rule again in the child (`(create-ns (symbol (str ~cell-ns-prefix
  index#)))`), because the child does not load `cell-harness`. When the
  rule changes, the child namespace and `cell-ns-name` (`cell-specs`, the
  producer namespace of each expansion) differ. Move the prefix and
  `cell-ns-name` to `cell-registry` (child-safe; the child form already
  requires it), call `value-type.cell-registry/cell-ns-name` in
  `child-form`, and make `harness/cell-ns-name` its alias or update its
  callers. Labels and expectations unchanged.
  - code-shaper pass 43 fix: `cell-registry/cell-ns-name` (private
    prefix); used in `child-form` and `cell-specs`; callers updated;
    `harness/cell-ns-name` removed.

## Code-shaper review (pass 44)

- [x] Use the shared sources in the child form of
  `runtime-test/policy-test`. Pass 42 made `registry/policy-property` the
  one source of `"value-type.fallback"`, but the form writes the literal
  again (`(System/setProperty "value-type.fallback" "invalid-later")`).
  When the name changes, the cell sets another property, and the check
  ("later property changes have no effect") passes with no evidence. The
  form also gives a third failure shape, `[(.getName (class e))
  (.getMessage e)]`, and the expected writes `["java.lang.Error" (str
  harness/warning-prefix "test")]`, not `harness/policy-rejection`. The
  context `"test"` is written 3 times (form, expected, `:warning`). Use
  `value-type.cell-registry/policy-property` in the form, give the
  rejection as the `{:class :message}` map of the failure function (next
  step), expect `(harness/policy-rejection context)`, and bind the
  context once. Labels unchanged; state the expectation shape change in
  `implementation.md`.
  - code-shaper pass 44 fix: syntax-quoted form with
    `registry/policy-property` and `registry/failure`; expected
    `(harness/policy-rejection context)`; context bound once
    (implementation.md).
- [x] Use one namespace and one rule for the failure maps of a child.
  `cell-registry` has `thrown`, `rejection` and `failed-definition`, but
  `consumer-aot-checks` has `failure` (`select-keys` of `registry/thrown`
  to `[:class :message]`) and `root-failure` (`failure` with `:data`).
  `expansion-checks/discarded` writes `failure` again (`(select-keys
  (registry/thrown thrown) [:class :message])`), and
  `identity-receiving-checks/define` writes `failed-definition` again
  with `root-failure` (`(assoc (checks/root-failure thrown) :absent?
  (registry/absent? n simple-name))`), so a failed definition there has
  `:data` and no `:chain` (expected `(assoc harness/fallback-rejection
  :data nil :absent? true)` in `receiving-identity-policy-test`). Move
  `failure` and `root-failure` to `cell-registry` (child-safe, beside
  `thrown`; update the namespace docstrings and callers), use
  `registry/failure` in `expansion-checks/discarded`, and use
  `registry/failed-definition` in `identity-receiving-checks/define`
  (the expected gives `:chain` in place of `:data nil`). Labels
  unchanged; state the expectation change in `implementation.md`.
  - code-shaper pass 44 fix: `registry/failure` and
    `registry/root-failure`; `failed-definition` takes an optional
    failure function; used in `define` and `field-policy-checks`
    (implementation.md).
- [x] Use one source for the `java.lang.Error` of a direct policy
  rejection. Design Scope 3: a direct call throws `java.lang.Error`;
  `harness/source-rejection-chain` names the source form, but the direct
  chain `["java.lang.Error"]` is written again in
  `aot-test/source-free-fallback-aot-loading-test` and
  `aot-test/source-free-runtime-only-fallback-test` (`(assoc
  harness/fallback-rejection ... :chain ["java.lang.Error"])`). Also
  `harness/rejection?` writes `"java.lang.Error"` again in its `:any`
  branch, beside `policy-rejection`. Add a public `cell-harness` value
  (for example `direct-rejection-chain`), use it in the two `aot-test`
  places, and build the `:any` branch class from `policy-rejection`.
  Labels and expectations unchanged.
  - code-shaper pass 44 fix: public `harness/direct-rejection-chain`;
    used in both `aot-test` places; `:any` class from `policy-rejection`.

## Code-shaper review (pass 45)

- [x] Use the `cell-registry` failure maps in `validation-checks`. Pass
  43 made `registry/failed-definition` the one rule of a failed type
  definition (failure map of the error with `:absent?`), and pass 44 gave
  it a failure function. But `validation-checks` writes the rule again in
  two more shapes: `check-fields` gives `{:key :enabled? :error
  (:value-type/error (ex-data (registry/root thrown))) :absent?}`, and
  `invalid-forms` gives `{:class (.getName (class (registry/root
  thrown))) :absent?}` (the root class rule of `registry/thrown` written
  again). Use `registry/failed-definition` in both (`registry/failure` in
  `invalid-forms`; `registry/root-failure` in `check-fields`, merged with
  `:key` and `:enabled?`), and update the expected of
  `runtime-test/source-field-validation-test`. Also the root ex-data is
  read a third way in
  `expansion-checks/invalid-constrained-discarded` (`(select-keys
  (ex-data (registry/root thrown)) [...])`): add one child-safe
  `cell-registry` function of the root ex-data (for example `root-data`),
  build `root-failure` from it, and use it there. Labels unchanged; state
  the expectation shape change in `implementation.md`.
  - code-shaper pass 45 fix: `failed-definition` in `check-fields`
    (`root-failure`) and `invalid-forms` (`failure`); child-safe
    `registry/root-data`, used in `root-failure` and
    `invalid-constrained-discarded`; expected is `described`
    (implementation.md).
- [x] Use one child-safe function for the outcome of a call that may
  fail. The rule (call f, give `:accepted`, else a failure map of the
  Throwable) is written again in `function-aot-checks` (install),
  `child-transfer-checks` (evaluate), `warning-checks` (transfer
  evaluate, and `nil-construction` with `(:class (registry/thrown
  thrown))`), `consumer-aot-checks` (reload, then `assoc`
  `:factory-bound?`; and `outcome`, with the class name of `cause`),
  `field-policy-checks/policy-attempts` (the value of f, not `:accepted`)
  and `expansion-checks/discarded` (`:returned`, not `:accepted`). Add
  one `cell-registry` function of a failure function and f (for example
  `outcome`: `:accepted`, or `(failure-of thrown)`; beside
  `guarded-call`), and use it in these places (build `checks/outcome`
  from it). Use one success value, `:accepted`, where the value of f is
  not checked (`expansion-checks/discarded`); `policy-attempts` keeps the
  value of f (for example an optional success function, as
  `guarded-call` has). Labels unchanged; state the `:returned`
  expectation change in `implementation.md`.
  - code-shaper pass 45 fix: child-safe `registry/outcome` (failure-of,
    f, optional success); used in the listed places, `checks/outcome`,
    `expansion-checks/attempt`, `invalid-constrained-discarded`,
    `field-policy-checks/evaluation` and `validation-checks`; `:returned`
    becomes `:accepted` (implementation.md).

## Code-shaper review (pass 46)

- [x] Use `registry/outcome` for each remaining catch of a Throwable.
  Pass 45 made `cell-registry/outcome` the one rule (call f, give the
  success value or `:accepted`, else `(failure-of thrown)`), but these
  places still write the `try`/`catch Throwable` again:
  `identity-receiving-checks/attempt` (this is `(registry/outcome
  registry/root-failure f identity)`) and `define`,
  `transfer-checks/attempt`, `field-transfer-checks/attempt`
  (`:unexpected-success`), `identity-loader-checks/stale-sibling-strict-case`,
  `aot-checks` (`compile-fixture` strict branch, `load-fixture`,
  `load-value-fixture`, `invoke-runtime-fixture`), the child forms of
  `runtime-test/public-definition-test` and
  `cell-runner-test/fallback-cell-form`, and the `outcome` local of
  `field-capability-test`. When the rule changes, these copies keep the
  old one. Use `registry/outcome` (or `value-type.cell-registry/outcome`
  in a child form) in each place, and remove
  `identity-receiving-checks/attempt`. Keep the catches of one class
  (for example `runtime-test/policy-test`, `catch Error`) and the
  worker catch of `warning-checks` (it rethrows). Labels and
  expectations unchanged.
  - code-shaper pass 46 fix: `registry/outcome` in the listed places;
    `identity-receiving-checks/attempt` removed; worker catches of
    `warning-checks` and `expansion-checks`, and the `cell-harness` cell
    catch, stay.
- [x] Move the `[description predicate]` helpers of
  `runtime-test/source-field-validation-test` to `cell-harness`. Pass 45
  added private `fixed-entry` (`[x #(= x %)]`) and `entries` (a predicate
  of a sequence of pairs, in order) beside `harness/repeated`, but
  `entries` gives only the predicate. The test then writes the
  description again (`(mapv first fields)`, `(mapv first invalid)`) and
  the predicate of the map again (`(and ((entries fields) ...) ((entries
  invalid) ...))`). Add public `cell-harness` functions that give
  `[description predicate]` (for example `fixed` of x, and `entries` of
  pairs: the vector of descriptions, and the in-order predicate), as
  `repeated` does, and build the `described` expected of the test from
  the two pairs. Labels and expectations unchanged.
  - code-shaper pass 46 fix: public `harness/fixed` and
    `harness/entries` (`[description predicate]`); private copies
    removed; expected built from the two `entries` pairs.

## Code-shaper review (pass 47)

- [x] Use one child-safe function and one value for a call that must
  throw. Pass 45 made `registry/outcome` the one catch rule, but the
  success value of a call that must throw has four shapes:
  `:unexpected-success` (`field-transfer-checks/attempt`, the `outcome`
  local of `field-capability-test`), `{:unexpected <case result>}`
  (`identity-loader-checks/stale-sibling-strict-case`) and `{:status
  :unexpected-success}` (`registry/rejected-call`). Each place writes the
  success function again. Add one public child-safe `cell-registry` value
  (for example `unexpected-success`, `{:status :unexpected-success}`) and
  one function of failure-of and f (for example `failed-call`: `(outcome
  failure-of f (constantly unexpected-success))`). Use them in the three
  places, and build `rejected-call` from the value. Do not put the value
  of f in the map when it may not read back in the parent (the
  `field-capability-test` value is a `Constructor` array).
  Labels and expectations unchanged.
  - code-shaper pass 47 fix: child-safe `registry/unexpected-success`
    and `registry/failed-call`; used in the three places;
    `rejected-call` built from the value.
- [x] Use one `[description predicate]` function for an expected value
  that depends on the runtime `:detail`. Four places write the same
  rule again: a function of detail, called with `'<detail>` for the
  description and with the observed `:detail` for the predicate:
  `field-capability-test/policy-expected` (`attempts-of`),
  `identity-receiving-cross-jvm-test/strict-nested-public-declaration-test`
  (`declarations`), and `runtime-test`
  `constrained-discarded-expansion-test` and
  `constrained-validation-before-policy-test` (`discarded-attempts`). The
  copies differ already: the first two describe `:detail '<detail>` and
  check `harness/detail?`; the `runtime-test` copies do not describe
  `:detail` and do not check it. Add one public `cell-harness` function
  of a function of detail (for example `detailed`: `[description
  predicate]`, with `:detail '<detail>` and `detail?` in the rule), and
  use it in the four places (`field-capability-test` keeps nil detail
  when there is no reason). Labels unchanged; state the added `:detail`
  check of `runtime-test` in `implementation.md`.
  - code-shaper pass 47 fix: public `harness/detailed` (present?,
    expected-of); used in the four places; `runtime-test` checks
    `:detail` (implementation.md).
- [x] Use one `[description predicate]` function for a map of
  `[description predicate]` pairs. Pass 46 added `harness/fixed` and
  `harness/entries` (sequence), beside `harness/repeated`, but each
  `described` map expectation writes the description map, then a
  predicate of the same keys again: `runtime-test/source-field-validation-test`
  (`{:fields :invalid}` of two `entries` pairs),
  `field-aot-test/persisted-output?`,
  `identity-receiving-cross-jvm-test/incompatible-value` (the
  `described` of `run-check-cells`), `public-field-aot-test/strict-loads?`
  and `array-info-boundary-test/control-expected` (`facts`, `:native`,
  `:outcomes`). When a key is added to one side only, the description and
  the check differ. Add one public `cell-harness` function of a map of
  key to `[description predicate]` (for example `keyed`: the map of the
  descriptions, and a predicate that is true when each key passes its
  predicate), and build these expectations from it (with `fixed` for
  fixed values). Labels and expectations unchanged.
  - code-shaper pass 47 fix: public `harness/keyed`; used in the listed
    places; `array-capability/runtime-expected` gives keyed pairs;
    `incompatible-value` not changed (cross-key check;
    implementation.md).

## Code-shaper review (pass 48)

- [x] Use `harness/keyed` for the remaining map expectations. Pass 47
  made `keyed` the one rule of a map of key to `[description
  predicate]`, but `capability/allocation-children` writes the rule again
  (`(merge facts description)` and `(and (facts? result) (result?
  result))`), and its `expected` argument gives a description map and a
  predicate written apart: `array-matrix-test/outcomes-expected`
  (`[{:outcomes description} (comp outcomes? :outcomes)]`) and
  `array-sequence-boundary-test/expected` (`:outcomes` and
  `:definitions` checked by hand; the rejected branch also checks
  `:explicit?` by `mapv` and the absence of `:definitions`).
  `field-transfer-test/attempts-of` writes `(harness/described
  {:attempts attempts} #(= attempts (:attempts %)))`. Make `expected` of
  `allocation-children` give a map of key to `[description predicate]`,
  build the child expected by `(keyed (merge (runtime-expected ...)
  (expected reason)))`, and write the three places with `keyed`,
  `entries` and `fixed`. Labels unchanged; state any description change
  (for example the absent `:definitions`) in `implementation.md`.
  - code-shaper pass 48 fix: `expected` gives keyed pairs;
    `allocation-children` applies `keyed`; absent `:definitions` is
    `(fixed nil)` (implementation.md).
- [x] Use one function for the equal repeated outcomes of an array call.
  Pass 47 added private `array-info-boundary-test/equal-repeated`
  (`harness/repeated` of `registry/attempt-count`, and
  `capability/equal-outcomes?`), but `capability/repeated-rejection`
  writes the same composition again (`harness/repeated`, then `(and
  (rejections? outcomes) (equal-outcomes? outcomes))`). When the rule
  changes, the copies differ. Move `equal-repeated` to `array-capability`
  (public), build `repeated-rejection` from it, use it in
  `control-expected`, and make `equal-outcomes?` private. Labels and
  expectations unchanged.
  - code-shaper pass 48 fix: public `capability/equal-repeated`; used in
    `repeated-rejection` and `control-expected`; `equal-outcomes?`
    private.
- [x] Give `harness/described` one `[description predicate]` argument.
  Passes 41–47 made each expectation helper (`fixed`, `entries`, `keyed`,
  `repeated`, `detailed`, `capability/repeated-rejection`) give a
  `[description predicate]` pair, so nine places write `(apply
  harness/described pair)` (`runtime-test` 3, `field-capability-test`,
  `array-info-boundary-test`, `identity-receiving-cross-jvm-test`,
  `array-boundary-test`, `public-field-aot-test`, `field-aot-test`). Make
  `described` take the pair (or add one public function of the pair), use
  it in these places and in the remaining two-argument callers
  (`rejections`, `allocation-children`, `cell-outcomes-test`), and update
  the docstring. Labels and expectations unchanged.
  - code-shaper pass 48 fix: `described` takes `[data f]`; all callers
    updated; `export-only-expected` uses `keyed`.

## Code-shaper review (pass 49)

- [x] Add one `[description predicate]` function for each description
  form of `harness/described`. The docstring defines `<detail>`,
  `(contains x ...)`, `(repeat n x)` (n `:+`: one or more) and
  `(starts-with s)`, but only `repeated` (fixed n) and `fixed` give a
  pair. Each other use writes the description form and its check apart:
  `(list* 'contains names)` with `message-names?` in
  `runtime-test/failed-entry`, `consumer-aot-checks/unavailable-description`
  and `unavailable-outcome?`, `capability/rejection-description` and
  `rejection?`, `array-info-boundary-test/unavailable-expected`; with
  `str/includes?` in `cell-runner-test/limit-variable-load-test`; with
  `some` of a collection in `aot-test` (`:chain`); `(list 'starts-with
  prefix)` with `str/starts-with?` in
  `array-info-boundary-test/control-expected`; `(list 'repeat :+
  descriptor)` with `seq` and `every?` in `aot-fixture/persisted-output?`
  (not `harness/repeated`); `['<detail> harness/detail?]` in
  `capability/allocation-children`, and `'<detail>` with `integer?` in
  `limit-variable-load-test`. When a form or its check changes, the
  copies differ. Add public `cell-harness` pair functions (for example
  `contains` of texts or items, `starts-with`, `detail`, and `:+` in
  `repeated`), use them in these places, and build the map expectations
  with `keyed` where the keys are checked alone
  (`runtime-test/capability-test`, `limit-variable-load-test`, `aot-test`
  `:chain`). Labels unchanged; state any description change in
  `implementation.md`.
  - code-shaper pass 49 fix: public `harness/contains`, `starts-with`,
    `detail`, `with-entry`, and `:+` in `repeated`; used in the listed
    places (implementation.md).
- [x] Give each rejection rule one `[description predicate]` function.
  Pass 48 made the pair the one shape of an expectation, but three rules
  still have a description function and a predicate written apart:
  `harness/rejection-description` and `harness/rejection?`,
  `capability/rejection-description` and `capability/rejection?`, and
  `consumer-aot-checks/unavailable-description` and
  `unavailable-outcome?`. Each user then joins them again
  (`harness/rejections`, `capability/repeated-rejection`,
  `identity-receiving-cross-jvm-test/incompatible-value`,
  `field-transfer-test/unavailable`). When one side changes, the
  description and the check differ. Make each rule one function that
  gives the pair (build it from the pair functions of the step above),
  use it in these places, and use `second` of the pair in
  `cell-outcomes-test` and `unavailable-predicates-test`. Labels and
  expectations unchanged.
  - code-shaper pass 49 fix: `harness/rejection`,
    `capability/rejection` and `value-capability/unavailable` (moved from
    `consumer-aot-checks`; implementation.md).

## Code-shaper review (pass 50)

- [x] Use one `[description predicate]` function for an expectation that
  depends on an observed runtime value. Pass 47 made `harness/detailed`
  the rule (a function of the value, called with `'<detail>` for the
  description and with the observed value for the predicate), but only
  for `:detail`. Three places write the rule again by hand, as `[(first
  (f '<detail>)) (fn [observed] ((second (f (get observed k))) ...))]`:
  `runtime-test/failed-entry` (`[:data :class-name]`),
  `identity-receiving-cross-jvm-test/incompatible-value-receiving-test`
  (`:capability`) and `field-transfer-test/unavailable` (`:capability`).
  The copies differ already: the check of the observed value is
  `string?` in the first, `(false? (:available? capability))` in the
  second and none in the third. Also `detailed` writes `'<detail>` and
  `detail?` again, not `harness/detail`. Add one public `cell-harness`
  function of a key path, a `[description predicate]` of the observed
  value (for example `(detail)`) and a function of the value to a pair
  (for example `observed`), build `detailed` from it, and use it in the
  three places. Labels unchanged; state any predicate change in
  `implementation.md`.
  - code-shaper pass 50 fix: public `harness/observed`; `detailed` built
    from it; used in the three places (implementation.md).
- [x] Give the string form `(repeat n x)` a `[description predicate]`
  function. The `described` docstring defines it ("in a string, n
  occurrences of x"), but `harness/repeated` checks only a sequence. Thus
  `runtime-test/fallback-expansion-warning-timing-test` writes the
  description `(list 'repeat 1 harness/warning-prefix)` two times and its
  check apart (`harness/occurrences`), and checks `:loader-bound?`,
  `:expanded` and `:evaluated` by hand, not by `keyed` and `fixed`. Add
  the string case to `repeated` (or one pair function beside it), make
  `occurrences` private, and build the expected with `keyed`; keep the
  cross-key check (`(= after-expansion after-evaluation)`) in one `and`
  with the `keyed` predicate. Labels and expectations unchanged.
  - code-shaper pass 50 fix: public `harness/repeated-text`;
    `occurrences` private; expected built with `keyed`.
- [x] Use one `[description predicate]` function for a map that has at
  least some fixed entries. Two rules exist: `array-info-boundary-test/control-expected`
  writes the pair `[native #(= native (select-keys % (keys native)))]`
  by hand, and `cell-harness/result-matches?` has a special branch for an
  expected `{:thrown {...}}` (the thrown map has at least these entries),
  a second expectation language beside `described` that its docstring
  does not name. Add one public `cell-harness` pair function (for example
  `including` of a map), use it for `:native`, and write
  `runtime-test/invalid-policy-test` with `described` of `keyed`
  `{:thrown (including {...})}`; remove the `{:thrown}` branch and update
  `cell-outcomes-test` (its `{:thrown}` case). Labels unchanged; state any
  description change in `implementation.md`.
  - code-shaper pass 50 fix: public `harness/including` (form
    `(including m)`); `{:thrown}` branch removed (implementation.md).

## Code-shaper review (pass 51)

- [x] Make the map pair functions agree with the description language.
  The `described` docstring says that a bare map in a description is the
  expected value, and `(including m)` is a map that has each entry of m.
  But `harness/keyed` gives a bare map description and a predicate that
  accepts other keys (and a non-map, when each pair accepts nil). Thus a
  failure report shows an exact map where the check is open, for example
  `aot-test` `{:chain (contains ...)}` (the thrown map also has `:class`
  and `:message`), `identity-receiving-cross-jvm-test`
  `:capability {:available? false}` (the result also has `:reason`) and
  `field-transfer-test/unavailable` `{:attempts ...}` (the result also has
  `:capability`). Also three functions give a map rule: `keyed` (open,
  pairs), `including` (open, fixed values only) and `with-entry` (closed,
  fixed values and one pair). Make `keyed` closed (a map with exactly the
  keys of pairs), make `including` take a map of key to `[description
  predicate]` (open; description `(including {...})`), build `with-entry`
  from `keyed` and `fixed` (or remove it), and use `including` where a
  test checks only some keys. Labels unchanged; state each description
  change in `implementation.md`.
  - code-shaper pass 51 fix: `keyed` closed; `including` of pairs (open);
    `with-entry` built from `keyed` and `fixed`; `including` in the three
    places and three more (implementation.md).
- [x] Give the `:err` check of a cell one `[description predicate]` pair.
  Pass 50 removed the `{:thrown}` branch of `result-matches?` because it
  was a second expectation language beside `described`. But
  `cell-harness/expected-err` and `err-matches?` keep one more: the
  sentinel `[:any]` (from cell `:warning :any`) with its own regex rule,
  and a failure report gives `:expected [:any]`, a form that the
  `described` docstring does not name. Make `expected-err` give a pair
  (`(fixed "")`, `(fixed (warning-line warning))`, or one pair for one
  warning line of any context, for example a `cell-harness` function with
  description `(warning-line <detail>)`), check `:err` (and `:out`) with
  the pair predicate, and report its description. Remove `err-matches?`
  and update `cell-outcomes-test` (its `:warning :any` cases). Labels
  unchanged; state the description change in `implementation.md`.
  - code-shaper pass 51 fix: `expected-err` gives a described pair
    (private `any-warning-line`, `(warning-line <detail>)`); `:out` and
    `:err` use `result-matches?`; `err-matches?` removed
    (implementation.md).

## Implementation review (pass 28)

- [x] Replace the `:rejected? true` sentinel of `cell-harness/rejections`
  with a `[description predicate]` pair. Code-shaper passes 50 and 51
  removed the `{:thrown}` branch and the `[:any]` sentinel because each
  was an expectation language beside `described`. `rejections` keeps one
  more: an expected value marks a rejection map with `:rejected? true`,
  the predicate `postwalk`s the result to put the sentinel in place of
  each map whose `:class :message :chain` pass `rejection`, and the
  description `postwalk`s the expected value back. The `described`
  docstring does not name this form. Add one pair function of a strict
  rejection map with other entries (for example `(rejected chain context
  pairs)`: `keyed` of the `rejection` keys and `pairs`), build the
  expected values of its users with `keyed`, `fixed` and
  `registry/each-attempt` (or `entries`/`repeated`) and wrap them with
  `described`: `aot-test/fallback-aot-compilation-test`,
  `runtime-test/public-definition-test` and the constrained test at
  line 208, and `loader-policy-test`. Remove `rejections` and update
  `cell-outcomes-test` (its `rejections` cases). Labels unchanged; state
  each description change in `implementation.md`.
  - implementation review pass 28 fix: public `harness/rejected` (chain,
    context, pairs); `rejection` built from the same pairs; users use
    `keyed`, `fixed`, `entries`; `rejections` removed (implementation.md).

## Implementation review (pass 29)

- [x] Use one expectation function for a failed type definition under
  strict policy. Pass 43 made `registry/failed-definition` the one child
  shape (the `thrown` map of the rejection with `:absent?`), and pass 28
  added `harness/rejected`. But the expected value of that shape is
  written four times: private `runtime-test/strict-rejected` and
  `loader-policy-test/strict-rejection` (the same `(harness/rejected
  harness/source-rejection-chain context {:absent? (harness/fixed
  true)})`), `aot-test/fallback-aot-compilation-test` (the same, with
  `:emitted?`) and `transfer-test/transferred-expansion-test` (`(assoc
  harness/fallback-rejection :absent? true :chain
  harness/source-rejection-chain)`, a fixed map). When the shape of
  `failed-definition` changes, the copies differ. Add one public
  `cell-harness` function of context and pairs (for example
  `failed-definition`: `rejected` of `source-rejection-chain`, context
  and `pairs` with `:absent? (fixed true)`), and use it in the four
  places (remove the two private functions). Keep the stated entries of
  `cell-runner-test/scope-3-policy-rules-test` (plan "Slice 6 tests").
  Labels unchanged; state any description change in
  `implementation.md`.
  - implementation review pass 29 fix: public `harness/failed-definition`
    (context, pairs); used in the four places; private copies removed
    (implementation.md).
- [x] Remove `harness/rejection`. After pass 28 no cell expectation calls
  it; only `cell-outcomes-test` (`rejection-test` and the description
  case of `rejected-test`) does, and it is `(rejected chain context
  {})`. Remove it, move the `rejection-test` cases to `rejected` with
  `{}` pairs, and update the namespace docstring of
  `cell-outcomes-test`. Labels unchanged.
  - implementation review pass 29 fix: removed; `rejection-test` uses
    `(rejected chain context {})`; docstring updated.

## Implementation review (pass 30)

- [x] Use `harness/failed-definition` in
  `identity-receiving-cross-jvm-test/receiving-identity-policy-test`.
  Pass 29 named four places, but this fifth place writes the same
  expected shape as a fixed map: `(assoc harness/fallback-rejection
  :chain harness/source-rejection-chain :absent? true)`, for the
  `registry/failed-definition` (`registry/thrown`) map that
  `identity-receiving-checks/receiving-policy` gives (through `define`).
  This is `(harness/failed-definition harness/fallback-context {})`.
  Build the `error` expected with it, and the other policies with
  `harness/fixed`, as `transfer-test/transferred-expansion-test` does
  (`described` of `entries` of `registry/each`). Labels unchanged; state
  any description or predicate change in `implementation.md`.
  - implementation review pass 30 fix: `described` of `entries` of
    `registry/each`; `error` is `(harness/failed-definition
    harness/fallback-context {})`, others `harness/fixed`
    (implementation.md).
