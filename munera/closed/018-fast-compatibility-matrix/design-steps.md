# Design steps

## Architecture review (pass 1)

- [x] Correct the decision "A fresh namespace is the isolation unit for cells".
  A transferred expansion fixes its class name in the producer. For example,
  `child-transfer-checks/produce` gives one expansion of `user.TransferParent`,
  and each consumer cell evaluates that same expansion. In a shared consumer, a
  fresh consumer namespace does not give a fresh class name. The
  `DynamicClassLoader` static cache and the earlier Class can then make a
  skipped definition pass. State the full isolation unit: a fresh namespace and a
  unique class name for each cell. For transfer, the producer gives one
  expansion for each consumer cell, each from its own namespace. Also state that
  each cell checks that its definition gives a new Class. Refer to
  `mementum/knowledge/testing-clojure-generated-classes.md` ("Static cache") and
  `mementum/memories/repeated-definitions-must-prove-new-class-identity.md`.
  That memory requires a fresh JVM for each policy, receiving mode and expansion
  form. State that this design replaces that rule and give the reason.
- [x] Keep D13 warn-guard evidence for each serialized form and receiving mode.
  Today each warn consumer cell asserts exactly one warning. This shows that the
  receiving guard runs for that `macroexpand`/`macroexpand-1` form and that mode
  (`ramora/defvaluetype-design/fallback.md` D13: "verify the do barrier, analysis
  macro and runtime guard"). In a shared warn child, only the first fallback cell
  can warn. Thus a later cell whose guard is lost still passes. In isolation rule 5
  or in Decisions, state the cells that give this evidence for each form and
  mode. Use one of these: warn cells keep one child for each form and mode, or
  error-policy cells (repeated rejection) are the per-cell guard evidence and
  warn cells check only warn-once.
- [x] State how a cross-JVM matrix runs once, and what a test reports when it
  does not run. Use the current suite architecture where possible. Tests select
  suites by `--dir`, alias and `--namespace` (`doc/development/runtime.md`
  "Test layout"). Scry 0.1.61 has no skip result. A `deftest` with no assertions
  passes (`mementum/memories/scry-pass-true-with-zero-tests.md`), and Scry
  changes are out of scope. Compare these mechanisms: suite or namespace
  selection in the documented commands, and an in-test check of the parent JVM.
  An in-test parent check makes cross-JVM tests depend on the parent again.
  Select the simpler mechanism. Name the Scry-visible form of "reports this
  explicitly" (for example, an assertion that names the owner parent).

## Ambiguity review (pass 1)

- [x] Classify each compatibility namespace. The Problem section says "Only
  `runtime-test` uses the parent-dependent `:selected` configuration". This is
  not correct: `aot-test` and `transfer-test` also run `:selected` children,
  with `:baseline` producers. Some namespaces start no child, for example
  `fields-test`, `identity-backend-test`, `identity-loader-test`,
  `isolated-sibling-loader-test`, `public-field-annotations-test` and
  `capture-failure-test`. Scope 5 does not say whether these run under each
  parent. State the classes (cross-JVM, parent-dependent child, in-process and
  mixed) and the class of each namespace or each test. Use this for Scope 5 and
  acceptance 1–2.
- [x] Define the grouping unit in Scope 1 and acceptance 2. "Each test" can mean
  one `deftest` or one namespace. State whether cells of different `deftest`s
  in one namespace can share a child. State where the "listed exceptions" are
  listed (for example, in `implementation.md` for each namespace, with the
  isolation rule number), in addition to the code comments.
- [x] Define "cell label" and the comparison of expected results. Current
  `testing` strings are not unique for each cell. For example,
  `receiver-child-replacement-test` does not put the serialized form in its
  label, so two cells have one label. State the label key (for example, test
  Var plus `testing` context), and state how current duplicate labels are
  counted and renamed. The invariant says expected results "do not decrease".
  State what "decrease" means for an expected result. State how a changed
  expectation is recorded, for example a later warn cell that now expects no
  warning (Scope 3).
- [x] Specify the concurrency limit. Give the environment variable name and the
  default value. State whether the limit applies to all children of one parent
  JVM, or to each batch call. State how a producer and its consumers count
  against the limit.
- [x] Specify the child timeout after grouping. Today each child has a default
  of 120 s (`run-jvm :timeout-ms`), and `doc/development/runtime.md` documents
  120 s for some matrices. A grouped child runs more cells, and concurrent
  children are slower. State whether the timeout applies to each child or each
  cell, and how it scales with the cell count. State which documented values
  change.
- [x] Specify the output channels of a grouped child. Scope 2 assigns `*err*`
  text to a cell. Some `*err*` text belongs to no cell, for example text from
  child start or from the child program outside a cell. Today a silent check
  asserts that all child `stderr` is empty. State whether that child-level text
  must also be empty under silent policy, and how it is reported. State whether a
  cell's `*out*` text is captured. Today the last `stdout` line carries the EDN
  result, so cell output on `*out*` can corrupt it.
- [x] Define the time measurement in acceptance 1–2. Name the commands that make
  the "full documented verification": only the three `:fallback-test` commands,
  or also the focused commands in `doc/development/runtime.md` that run
  cross-JVM namespaces under a parent. Identify the "reference machine" (or say
  that before and after runs must use the same machine). State how the time for
  each namespace is measured, and whether the baseline uses the current serial
  runner.
- [x] State the form of the focused checks in acceptance 4 and 5. State whether
  they are permanent tests in `test/compatibility` or recorded one-time runs.
  If they are tests, state whether their labels are in the label-set record.
  State how a test causes a child crash, for example `System/exit` with a
  nonzero code, or a timeout, in a fixture cell.

## Inconsistency review (pass 1)

- [x] Make the label rules agree. The invariant says that the label set does
  "not decrease", so new labels are permitted. Acceptance 2 says that the label
  set "is equal to the first record", with renamed labels mapped. Select one
  rule. If new labels are permitted (for example, focused checks or labels that
  now name the serialized form), make acceptance 2 say "contains".
- [x] Limit the error-policy rule in Scope 3 to fallback cells. Scope 3 says
  "Error policy: each cell rejects". D10
  (`ramora/defvaluetype-design/fallback.md`) and
  `ramora/non-nil-field-design/verification.md` ("With value support: all
  policies produce value owners") require success where value support exists.
  For example, `child-transfer-test/value-parent-replacement` expects success
  under `error` on preview-enabled EA3. Also state that a repeated rejection can
  have a different Error form for each phase. A retried top-level AOT
  initializer throws `NoClassDefFoundError`.
- [x] Make the invariant "No cell can pass because of state from an earlier
  cell" agree with Scope 3. In a shared warn child, a later fallback cell passes
  its "no warning" check because an earlier cell set the warn-once flag
  (`value-type.impl.runtime/warned?`). Later cells also use the cached
  capability result. Name the shared library-runtime state that cells may see
  (policy, warn-once flag, capability caches). Limit the invariant to
  definition state: Vars, imports, Classes and namespaces.
- [x] Make acceptance 3 agree with Scopes 4 and 5. Acceptance 3 names "the three
  documented commands". Scope 5 can change the documented command set, for
  example with a separate cross-JVM command. Acceptance 3 also requires the
  limit-1 run only for the Java 8 parent. If Java 8 does not own the cross-JVM
  matrices, the limit-1 run does not start their children. Then nothing checks
  the Scope 4 rule (same report order and labels at all limits) for most
  children. State acceptance 3 for the updated documented commands. Require the
  limit-1 run for the command that runs the cross-JVM matrices.

## Architecture review (pass 2)

- [x] SCOPE_QUESTION: Scope 5 moves the cross-JVM tests out of
  `:fallback-test` to `test/cross-jvm/`. Some files outside the in-scope
  categories (test support, tests, test aliases, documentation) list the suites
  or the test directories. Without a change to them, the project loses evidence
  and checks that the architecture requires:
  - `.github/workflows/release.yml` runs only the three `:fallback-test`
    commands. `ramora/release-workflow.md` ("Workflow flow") makes these
    fallback tests a release gate. After the move, the release gate does not run
    any cross-JVM test. This conflicts with the Intent "Keep all of its
    evidence".
  - `bb/value_type/quality.clj` (lint paths and aliases),
    `test/tooling/ci-failures.sh` and `test/tooling/hooks.sh` list
    `test/compatibility` but not `test/cross-jvm`. The development quality
    workflow (`ramora/defvaluetype-design/embodiment/artifacts.md`) then does
    not lint or check the moved tests.
  "Out of scope" names "the `:test`, release and build suites". It is not clear
  whether this excludes the release workflow. A human must decide whether the
  task can change these files and `ramora/release-workflow.md`, or whether a
  separate task does this.
  Decision (user, 2026-10-29): the task changes these files. See `design.md`
  Scope 7, "Out of scope", "Decisions" and acceptance 8.

## Architecture review (pass 4)

- [x] Isolate the `Compiler/LOADER` binding for each cell. Ramora
  `ramora/defvaluetype-design/risks-and-evolution.md` (risk "`macroexpand` from
  tooling installs a loader": "A loader stays in the tooling binding frame")
  requires "Verify both expansion forms in fresh receivers". The design puts
  both expansion forms in one child, but "Shared runtime state" and the
  definition-state list do not name the thread-bound `Compiler/LOADER`.
  `value-type.impl.loader/install!` calls `.set` on the bound `LOADER`, and
  the new capture loader takes the old value as parent. Children run all code
  in one `clojure.main -e` top-level form, thus in one `LOADER` frame. Cells
  that call `macroexpand` or `macroexpand-1` in the child (for example
  `expansion-checks`, `controlled-field-capability-test`) leave their capture
  loader in that frame. A later cell then defines through it. State that each
  cell runs in its own `LOADER` binding (a new `DynamicClassLoader`, as
  `Compiler.eval` gives each top-level form), and its own frame for the other
  compiler bindings that a cell can `set!` (for example `*ns*`). Add the
  binding to the state that cells must not share. State why this meets the
  Ramora "fresh receivers" mitigation, and update that Ramora text under
  Scope 6.

## Ambiguity review (pass 2)

- [x] Define the child-count bound in Scope 1 and acceptance 2. The bound is
  "distinct child configurations, plus producers, plus the exceptions". It does
  not state how many producers a test can start: one for each distinct producer
  configuration, or one for each consumer child. It does not state whether a
  producer and a consumer with the same configuration count as one
  configuration or two (isolation rule 3 requires two JVMs). It does not state
  where compile children count. `aot-fixture/compile-clojure`,
  `compile-producer` and `compile-java` each start a `run-jvm` child, and Scope 4
  names "AOT compile child" as a child kind. State the bound as a formula that
  names each child kind: producer, consumer, AOT compile and Java compile.
- [x] Define "their own classpath" in isolation rule 4. A source-free AOT
  consumer classpath holds one compile output directory today
  (`aot-fixture/classpath-with`). State whether one consumer child can load
  more than one artifact of one test, with one classpath that holds all their
  output directories, or whether each artifact needs its own consumer child.
  Rule 5 (first initialization of an AOT namespace that another cell also
  loads) applies in both cases. This decision changes the child count for the
  largest users (`public-field-aot-test`, `aot-test`, `field-aot-test`).
- [x] Define the matching error cell in "Warn-guard evidence". The match
  requires the "same producer expansion". The transfer rule gives each consumer
  cell its own expansion with its own class name. Thus no two cells have the
  same expansion. State the attributes that must agree (for example, producer
  configuration, source form and serialized form) and the attributes that can
  differ (namespace and class name). The rule names only transfer attributes.
  State the match for warn fallback cells that are not transfer cells: source
  definitions, function-contained definitions and source-free AOT artifacts.
- [x] State the concurrency limit for the time criterion in acceptance 2. It
  says "Record the concurrency limit used", but it does not limit the value. A
  high limit on a machine with many CPUs can meet the 25% target, but the
  default limit cannot. State whether the target applies at the default limit
  (4) or at a limit that the plan selects.

## Ambiguity review (pass 4)

- [x] State the observable form of an invalid `VALUE_TYPE_TEST_JVM_LIMIT`.
  Scope 4 says that an invalid value "stops the run with a message that names
  the variable". It does not say when the runner reads the value, or what Scry
  shows. If the runner reads it at each child start, each test that starts a
  child gets an error, and the other tests continue: the run does not stop.
  If `jvm-runner` reads it at namespace load, each namespace that requires
  `jvm-runner` gives a Scry load error, and in-process namespaces still run.
  State the point of the check and the Scry-visible result (for example, Scry
  runner error and exit 1). State whether a run that starts no child (for
  example, only in-process namespaces) also fails.

## Inconsistency review (pass 2)

- [x] Make the AOT retry form in Scope 3 agree with isolation rule 5. Scope 3
  says that each fallback cell rejects "also after an earlier rejection in the
  same child", and gives the form for "a retried failed AOT top-level
  initializer" (`NoClassDefFoundError`). Across cells, a retry occurs only when a
  later cell loads an AOT namespace that an earlier cell loaded. Rule 5 gives
  such a first-initialization cell its own child. Thus the cross-cell retry case
  cannot occur. Today the retry is in one cell:
  `aot-test/source-free-fallback-aot-loading-test` calls
  `aot-checks/load-fixture`, which returns `[initial retry]` from one consumer.
  Both producer artifacts there use the namespace `fallback.aot-fixture`. State
  that a retry is part of one cell, or change rule 5 to let a retry cell share
  the child of its first-initialization cell.
- [x] Make the label record agree with the cell concept. Concepts and the
  Invariants make a label the key of one cell, and the first record "counts each
  label with its number of cells". Scry reports assertions, not cells. Today one
  cell makes more than one assertion with one label (for example, `:exit`,
  `:result` and `:err` in `aot-test`). Some assertions belong to no cell: the
  producer exit checks, which are outside a `testing` context and repeat for
  each producer policy, and the new child-level stderr assertion (Scope 2).
  State how the record gets the cell count from Scry assertion events. State
  whether assertions that belong to no cell are in the label record, the label
  mapping and the "each label is unique in its command" invariant.


## Inconsistency review (pass 5)

- [x] Make the first label record agree with tests whose one child gives
  results for more than one `testing` context. The Invariants say "Today one
  cell child runs one cell", and the first record counts a label by the cell
  children that start under it. This is not correct for
  `loader-policy-test/fresh-policy-loader-rejection-test`. Each of its 14
  children returns 6 cases. The parent asserts each case in a nested
  `testing` context (`(testing (pr-str case-key) ...)`, line 28). Thus each
  nested label has assertions but no child start, and gets a cell count of 0.
  By "Support assertion" in Concepts, these case assertions are not support
  assertions. By "Cell" in Concepts, each case is a cell. If the work makes
  each case a cell, the label mapping fails (a count of 0 maps to targets
  with a count of 1 or more). State how the first record counts a nested
  label under a cell child: as part of the cell of the outer label, or as its
  own cell. Correct the sentence "Today one cell child runs one cell".

## Ambiguity review (pass 6)

- [x] State how the record after the work finds its cell labels. The
  Invariants give a method only for the first record (start label at each
  cell child start; nested labels under it are cells). For the record after
  the work, they say only "one for each cell label". They do not say how the
  record separates cell labels from support labels. If the record after the
  work uses the first-record method, a grouped child gives wrong counts: the
  child-level stderr assertion of Scope 2 has its own label, in the child
  context, with an assertion on the result of that child, and it starts no
  child. Thus the nested-label rule counts it as a cell, but Concepts makes it
  a support assertion. Acceptance 2 compares cell counts, so this changes the
  result. State the method for the record after the work. For example: apply
  the first-record method, and exclude the support labels that Scope 2 and the
  "Support assertion" concept name; or the parent records each label that it
  asserts from a cell result.

## Ambiguity review (pass 7)

- [x] State the stderr checks of a child kept under isolation rule 5. Scope 2
  says that a rule-5 child "can keep its current checks of process stderr".
  The Invariants ("after the work") say that the cell-result function "also
  makes" these checks. Today some rule-5 checks expect the fallback warning in
  process stderr (for example, `transfer-test/concurrent-warning-scope-test`
  line 53, and the warn consumers in `aot-test`). Scope 2 also says that
  child-level stderr "must be empty, under all policies", asserted "once for
  each child". The design does not state, for a rule-5 child:
  - whether its cell binds `*out*` and `*err*` to its own writers (then the
    warning is in the cell result, not in process stderr, and the current
    check changes);
  - whether the parent also makes the child-level empty-stderr support
    assertion (this fails when the warning is in process stderr);
  - whether "can keep" is a choice of the plan, or a rule.
  Select one model for rule-5 children, and make Scope 2 and the Invariants
  use the same words.

## Ambiguity review (pass 8)

- [x] State the expected `*out*` and `*err*` text of each cell. The
  Invariants say that the cell-result function "checks the `*out*` and
  `*err*` text of the cell", but the design gives the expected text only for
  some cells: Scope 3 gives `*err*` for warn and silent fallback cells, and
  Scope 2 maps a current process-stderr check to "its cell". The design does
  not state the expected text for:
  - `*err*` of error-policy cells and of cells with value support (no
    fallback);
  - `*out*` of any cell (today the runner ignores stdout lines before the
    last line, so no test checks them);
  - cells of tests that check no stderr today. Some of these run warn
    children, for example `field-capability-test`, `loader-policy-test`,
    `array-matrix-test` and `function-redefinition-test`. Their warning
    text is unchecked today, and moves from process stderr to the cell;
  - a current child whose one process-stderr check covers more than one new
    cell (for example, the `transfer-test/transferred-expansion-test`
    consumer, which runs `:eval` and `:source` and both forms in one child).
    State which cell gets the current expected text.
  State one rule, for example: each cell expects empty `*out*`, and empty
  `*err*` except the Scope 3 warning; or: the function checks the text only
  where the design states an expected value.

## Inconsistency review (pass 8)

- [x] Make the child-level stderr label of Scope 2 agree with acceptance 3.
  Scope 2 gives this support assertion "a label that names the test and the
  child configuration". By Concepts, the child configuration includes the
  classpath. By rule 4, the classpath of an AOT cell child holds its
  artifact, and `aot-fixture` puts the artifact in a new
  `Files/createTempDirectory` directory for each run. Thus this label
  changes in each run. Acceptance 3 requires the same labels at limit 1 and
  at the default limit. No current label holds a temporary path. State the
  parts of the configuration that the label names (for example, Java
  executable, JVM options and policy, and the artifact by its fixture name,
  not its path). More than one support assertion can have one label, so
  children that differ only in the artifact can share it.

## Ambiguity review (pass 9)

- [x] State the warning context that the first fallback cell of a split
  child expects. Scope 2 says that a cell expects the context "where the
  current check of the cell states the context". It also says that a
  current child that splits into more than one cell "has no expected text
  of its own". Thus no current check belongs to a split cell, and its first
  fallback cell expects only the prefix and "a context" (any text). But the
  current check of the split child often states the context. For example,
  the `transfer-test/transferred-expansion-test` consumer (line 33) expects
  exactly `value-type: identity deftype fallback: fixed fallback
  representation\n`. Prefix only accepts a different context, which the
  current check rejects. This conflicts with "Expected results do not
  decrease", whose only exception is the later warn cells. State that the
  first fallback cell of a split child expects the context that the current
  child check states. Or give the expected context from the kind of fallback
  (for example, `fixed fallback representation` for each fixed fallback
  expansion or artifact).

## Inconsistency review (pass 9)

- [x] Make "Label mapping" agree with the split child of Scope 2. Scope 2
  says that a current child can split into more than one cell (for example,
  the `transfer-test/transferred-expansion-test` consumer: `:eval` and
  `:source`, both serialized forms). Today that child runs under one label
  (`(testing policy ...)`, line 20) with no nested labels, so the first
  record gives the label a cell count of 1. After the split, each new cell
  has its own label with count 1 (Invariants, "after the work"). "Label
  mapping" says that the cell counts of the target labels "add up to its cell
  count". Thus the label can map to only one of its new cell labels. The
  other new cells must then be "new labels", and the label-change record
  ("old label, new labels") lists targets that the mapping does not count.
  State the mapping rule for a split: for example, the target counts add up
  to at least the cell count, and a split maps one label to all of its new
  cell labels. Or state that a split cell keeps one label and is one cell.

## Architecture review (pass 10)

- [x] Make the Scope 7 release gate keep the failures of the cross-JVM suite.
  `ramora/release-workflow.md` ("Workflow flow") says: "Each suite preserves
  Scry failures before another suite can clear them." In `release.yml`, each
  suite step has a "Preserve ... failures" step that uploads `.scry-results/`
  when that step fails (for example, `preserve-fallback-java8-failures`).
  Scope 7 and acceptance 8 require only that the release gate "runs the
  `:cross-jvm-test` command". They do not require its preserve step. Without
  it, a release failure in the cross-JVM suite keeps no Scry results. This
  conflicts with "Failures stay visible". State in Scope 7 and acceptance 8
  that the `:cross-jvm-test` step has its own preserve step and artifact name,
  in the same form as the `:fallback-test` steps.

## Architecture review (pass 11)

- [x] Make the documented quality-check scope agree with Scope 7.
  `ramora/defvaluetype-design/embodiment/artifacts.md` ("Development quality
  workflow") refers to `doc/development/quality-checks.md` for the check
  scopes. That file says (line 64): "Formatting and code lint cover `src/`,
  all six test directories listed in the test layout". Scope 7 adds
  `test/cross-jvm` to the lint paths, and Scope 6 adds it to the test
  layout. Then the lint scope has seven test directories, and the text is
  false. The file does not name `test/compatibility` or `:fallback-test`.
  Thus the Scope 7 item "Other files that list `test/compatibility` or
  `:fallback-test`" does not find it, and acceptance 6 and 8 do not check
  it. Name `doc/development/quality-checks.md` in Scope 7 and acceptance 8:
  its check-scope text agrees with the lint paths (for example, it gives no
  count, or the count seven).

## Plan ambiguity review (pass 1)

- [x] State the source of each part of the label records in `plan.md` and
  `steps.md` (Slice 1, Slice 3 record output, Slice 8). The Invariants make
  the label record "each label that its Scry assertion events report",
  support labels included (cell count 0), and the nested case labels of
  `loader-policy-test`. The plan names only two sources: the temporary
  recorder (start labels at `run-jvm`) and the cell-result function (cell
  labels). Neither gives support labels or nested labels. The documented
  CLI commands do not give them either: Scry 0.1.61 CLI writes result files
  only for `:fail` and `:error` entries (`scry.cli.results/failure-entry?`).
  State:
  - the source of all assertion labels in each record (for example, a
    temporary `clojure.test/report` hook, or `scry.core/run` with `:var`
    result scope), and whether the labels and the total time come from one
    run of each documented command or from two runs;
  - how the after record counts children for each test (the Slice 1 patch
    again on a changed `jvm_runner.clj`, or a counter in the new runner),
    with the parallel runner (child starts on other threads must still get
    the test Var);
  - when the Slice 3 record output is written (each run, or only when a
    variable enables it), and the file name for each command (the three
    `:fallback-test` commands use one alias);
  - the source of the label order that acceptance 3 compares at limit 1
    and at limit 4.
- [x] Replace the namespace time method in `plan.md` and Slice 1. The plan
  uses a `clojure.test` report wrapper on `:begin-test-ns`/`:end-test-ns`.
  Scry 0.1.61 does not use `clojure.test/test-ns`: it runs the Vars of each
  namespace itself (`scry.clojure-test/test-vars-with-output-owners`) and
  reports no namespace events. It also installs its own `test/report` with
  `with-redefs` at run start, so a report wrapper installed before the run
  does not run. Name a method that works under Scry and the point where it
  enters the CLI run (for example, a wrapper on the root of
  `clojure.test/test-var` installed when `jvm-runner` loads: Scry keeps the
  root value that it finds at run start and calls it for each Var; sum the
  Var times for each namespace). Use the same method for the after record.
- [x] State the parallel call of the runner (`plan.md` "Runner", Slice 2).
  The plan adds a semaphore and a grouped-child form, but no call that
  starts more than one child at a time. Today each test calls `run-jvm` in
  sequence (for example `child-transfer-test` lines 27–36), so a semaphore
  alone runs nothing in parallel. State:
  - the call that a test uses to run a set of children (its input and its
    result order);
  - how a test states that a child uses the output of another child (for
    example, the producer, then the consumer set);
  - that the parent makes all assertions on the test thread, after the
    children return, in the input order. This keeps the report order and
    labels the same at each limit (Scope 4, acceptance 3).
- [x] State the cell description that a test gives to the harness and to
  the cell-result function (Slice 3). The function must apply Scope 2
  ("Expected cell text") and Scope 3, but the plan does not say from which
  inputs. State:
  - the inputs for each cell: label, form, expected result (Error form as
    root class, message and kept cause chain), fallback cell or not (from
    the explicit configuration), and the expected warning context (exact
    context or prefix only);
  - whether the function finds the first fallback cell of a warn child from
    the fixed cell order, or each test states the expected `*err*` of each
    cell;
  - the support assertions on a grouped child as a whole (exit code, result
    count), if any, and their `testing` context. A label has support
    assertions or cells, not both.
- [x] Select the open names in `plan.md` before Slice 5. "Cross-JVM suite"
  gives Java 8 only as the "preferred candidate" of the owner parent and
  leaves the choice to a Slice 5 step. Slice 7 (`release.yml` `JAVA_CMD`,
  `runtime.md`) and Slice 8 (the commands of acceptance 3) depend on it.
  The stated reason ("the slowest parent benefits most") does not apply:
  the cross-JVM suite leaves all three parent runs, and the owner runs it
  once. Select the owner parent and give a reason. Also name the new
  namespace for the cross-JVM tests of `identity-receiving-test`. Its Var
  names are part of the target labels in the label mapping.

## Plan inconsistency review (pass 1)

- [x] Make `plan.md` apply the cell-result function and the Scope 2 text
  rule to every cell child, not only to grouped children. `plan.md`
  "Record after the work" says "All cell assertions in grouped children go
  through this one function. Inline `is` on a child result is a support
  assertion". "Conversion order" says "AOT cell children keep one artifact
  each (rule 4) and gain only parallelism". The design is wider:
  - Invariants ("after the work"): the function records each cell label;
    for a one-cell child it makes the exit assertion under the cell label;
    "This also applies to a child kept under isolation rule 5".
  - Scope 2: the per-cell `*out*`/`*err*` writers, the empty child-level
    stderr and "Expected cell text" apply to "a grouped child, a one-cell
    child and a child kept under isolation rule 5".
  Most AOT cell children are one-cell children (rules 4–5). If they keep
  inline `is`, their labels are support labels with cell count 0 in the
  record after the work, and the label mapping (sum of target counts ≥ the
  first-record count) fails for them. Their current process-`:err` checks
  also stay process checks, against Scope 2. `steps.md` Slice 3 ("for a
  one-cell child also asserts exit") and the Slice 4 `aot-test` item follow
  the design. The other AOT items in Slice 4 (`field-aot-test`,
  `generated-value-aot-test`, `identity-aot-test`, the `public-*-aot-test`
  namespaces) do not say this. State in `plan.md` that every cell child uses
  the harness and the cell-result function, and replace "gain only
  parallelism" with "keep one cell for each child".

## Plan ambiguity review (pass 2)

- [x] State how the Slice 6 runner-outcome tests (acceptance 5) find that a
  cell fails without a suite failure. `steps.md` Slice 6 says "These assert
  on returned results and do not fail the suite". The failure of a cell is
  the work of `assert-child` (`is` under the cell label). A returned
  `run-jvm` result shows `:exit`, `:out`, `:err` and the cell results, but
  not that "every cell of the child fails". State in `plan.md` "Cell-result
  function" and in Slice 3 and Slice 6:
  - the function that gives the outcome of each cell as data (pass or fail,
    with the failure text: exit code, stdout, stderr), and that
    `assert-child` makes its `is` from this data. Or, as an alternative, a
    test-local `binding` of `clojure.test/report` around `assert-child`;
  - whether these tests make `cell!` records. If they do, their labels are
    cell labels with no Scry assertion events. If they do not, their labels
    are support labels (cell count 0), as `implementation.md` (inconsistency
    review pass 8) states.
- [x] State the kind of an AOT compile that no later child loads.
  `field-aot-test/persisted-restricted-field-output-test` calls
  `compile-producer` with `:after` (`c/inspect-output`). No later child
  loads its output, and the assertions on its result are the checked case.
  Concepts give "AOT compile child: compiles ... output that a later child
  of the test loads" and "A child that compiles as its checked case is a
  cell child". `steps.md` Slice 1 adds `:kind` to "the three `aot-fixture`
  compile functions", so this child gets `:aot-compile` and its label has
  cell count 0. State its kind in Slice 1 (a `:kind` option of
  `compile-producer` or `compile-clojure`), and, if it is a cell child, its
  Slice 4 conversion (cell harness form with a compile path). Grep the other
  `compile-producer` and `compile-java` callers for the same case.
- [x] State the order between child starts and assertion events in the
  record file. `plan.md` "Recorder" writes `:assertions` and `:children` as
  two vectors. Method `:start-labels` needs "assertion events after that
  child start and before the next child start". Two vectors do not give
  this order. Add one sequence (for example, an event index in each
  `:children` entry, or one vector of events of both types).
- [x] State the Slice 7 change for each Ramora file in the third Slice 7
  item. `embodiment/artifacts.md` line 25
  ("`test/compatibility/value_type/...`: explicit fresh-JVM policy, transfer
  and AOT tests. Alias `:fallback-test` ...") becomes false: it needs an
  entry for `test/cross-jvm/` and `:cross-jvm-test`, and the "fresh-JVM"
  text changes (cells share children). `assertions/fallback-architecture.md`
  names only `transfer-test` and `aot-test` (they stay in `:fallback-test`)
  and no suite. `implementation.md` (inconsistency review pass 1, plan)
  states that it needs no edit. Remove it from the step, or state the text
  that changes.

## Plan inconsistency review (pass 2)

- [x] Make the read failure of a child result agree with the Invariants.
  `plan.md` "Runner" says "`run-jvm` is `run-jvms` of one map and keeps its
  contract". That contract (`jvm_runner.clj` docstring) is "Invalid success
  EDN throws". In `run-jvms` the throw comes from the `future` deref. It
  stops the test before `assert-child` runs, also for the other children of
  the same call. The Invariants require: "The parent asserts each cell that
  it sent to a child, also when the child ... returns no result for that
  cell", and "A child that does not return a result for a cell fails that
  cell". `plan.md` "Cell-result function" and `steps.md` Slice 3 also say
  that each cell fails when the child "returns no result for it". A cell
  value that prints as `#object[...]` (for example a Class) makes the
  child stdout unreadable as EDN. State in `plan.md` "Runner" and in Slice 2
  that a cell child with exit 0 and an unreadable last line gives a result
  with no cell results (for example `:read-error`, and `:out`/`:err`), and
  that `run-jvms` returns all results of the call. Then `assert-child`
  fails each cell of that child with exit code, stdout and stderr. State
  whether producer and compile children keep the throw.
- [x] Make the Slice 7 `runtime.md` item cover each focused command that
  runs a cross-JVM namespace (Scope 6: "Focused commands that run cross-JVM
  namespaces use `:cross-jvm-test`"). The item names only the focused
  non-nil command (line 83) and the focused receiving command (line 120).
  `doc/development/runtime.md` also has these `:fallback-test` commands
  for cross-JVM namespaces only: line 136 (`controlled-field-capability-test`,
  `function-redefinition-test`), line 182 (`array-matrix-test`), line 213
  (`array-sequence-boundary-test`), line 250 (`array-info-boundary-test`).
  After Scope 5 each one gives a Scry runner error. Also state the change of
  the "fresh JVMs" text next to line 182 (line 185).

## Plan ambiguity review (pass 3)

- [x] State the method and the scope of the new-Class identity checks
  (`plan.md` "Cell harness", "Class names"; `steps.md` Slice 3 item "Add the
  new-Class identity checks to the check code"). The check "a Class that no
  earlier definition in the child returned" needs state across the cells of
  one child. The cell code of one cell cannot know the Classes of earlier
  cells. The plan does not name this state or the code that keeps it. The
  step names no namespace. Today cell code is in 17 check namespaces
  (`test/compatibility/value_type/*_checks.clj`) and in inline forms in the
  tests (for example `aot-test`, `field-aot-test`). No Slice 4 item adds the
  checks. State:
  - the child-side function or registry (for example in `cell-harness`)
    that records each Class and factory that a cell defines, and how a cell
    result reports the check;
  - which cell code calls it, and in which slice: one change in Slice 3, or
    one change for each namespace in Slice 4;
  - which cells need the check: source and transfer definitions only, or
    also one-cell AOT children that load saved classes and define no type
    (for example `aot-test/source-free-fallback-aot-loading-test`), and the
    compile cells (`persisted-restricted-field-output-test`,
    `fallback-aot-compilation-test`).
- [x] State what the Slice 6 failing-cell test asserts for "the cell fails
  with its output" (acceptance 5), and the shape of one cell result.
  `plan.md` "Cell outcomes" gives `:failure` as the child `:exit`, `:out`,
  `:err` and `:read-error`. For one failing cell in a child that exits 0,
  `:out` is the full EDN line of all cells, and `:err` is empty. The text
  and the `:thrown` map of the failing cell are only in the `:actual` of its
  `:checks`. `steps.md` Slice 6 says that the tests assert on "`:failure`
  with `:exit`, `:out`, `:err`". The plan also does not give the form of one
  entry of the child result vector: the cell value, its `*out*` and `*err*`
  text, and `{:thrown ...}` (for example `{:value v :out s :err s}` or
  `{:thrown t :out s :err s}`). Thus `:expected` (compared with `=`) can
  apply to the value or to the full entry. State the entry form, and the
  outcome fields that the failing-cell test asserts (for example the
  `:actual` of the `:result` check is the `:thrown` map, and the `:actual`
  of the `:out` or `:err` check holds the text that the fixture cell printed
  before it threw).
- [x] State how the Slice 6 Scope 3 test (acceptance 4) asserts its cells.
  Acceptance 5 says "The tests for 4 and 5 ... assert on the cell results
  that the runner returns; they do not make the suite fail". `plan.md` "Cell
  outcomes" and `steps.md` Slice 6 give a method only for the runner-outcome
  tests (`cell-outcomes` data, no `cell!` record, support labels). The first
  Slice 6 item does not say if the Scope 3 test uses `assert-child` (cell
  labels, cell count 1 in the after record) or `cell-outcomes` (support
  labels, cell count 0). It also does not say if the test states the
  expected `*err*` of each cell itself, or uses the `cell-outcomes` rule
  (first `:fallback?` cell of a `warn` child). If it uses only the harness
  rule, the test does not show warn-once independently of that rule. Select
  one method.

## Plan inconsistency review (pass 3)

- [x] Make the "every cell child" rule of `plan.md` agree with the Slice 6
  fixture children. `plan.md` "Record after the work" says: "Every cell
  child — grouped, one-cell, or kept under isolation rule 5 — is built with
  the cell harness and asserted with the cell-result function. Thus every
  cell label comes from `cell!`." `plan.md` "Cell outcomes" and `steps.md`
  Slice 6 say that the runner-outcome tests build their fixture children
  with `cell-child` (`:kind :cell`) but "do not call `assert-child`". The
  two statements conflict. Also, `assert-child` makes the child-level
  stderr support assertion. Design Scope 2 requires this assertion "once
  for each cell child". Slice 6 does not say if its tests make it inline.
  The fixture children (failing cell, `System/exit`, timeout) can have
  empty child-level stderr, so this assertion does not make the suite
  fail. In `plan.md` "Record after the work", limit the rule to the cell
  children of the converted tests (Slice 4), and name the Slice 6 fixture
  children as the exception. In Slice 6, state if each test asserts empty
  child-level stderr for its fixture child, or why the Scope 2 rule does
  not apply to it. Decide this together with the pass-3 ambiguity step on
  the Scope 3 test method, because that test can also skip `assert-child`.

## Plan ambiguity review (pass 4)

- [x] State how the new-Class and absence keys ("New-Class checks",
  "Report") fit the current cell results. The plan gives one key per
  result map: `:new-class?` next to `:factory?`/`:class?`, or `:absent?`
  (class name not loadable through `RT/baseLoader` and factory not
  resolved). The current check code does not have this form:
  - One cell evaluates more than one type definition, but has one result
    map. `child-transfer-checks/consume` defines `TransferChild`
    (`deftype`) and the transferred owner, and has no `:factory?` or
    `:class?` key. `reject-replaced-child` defines a child that must
    succeed and an owner that must reject. Each `warning-checks/define-types`
    map is one definition, but each worker defines two types.
  - Current absence keys have other names or a weaker check:
    `:owner-absent?` and `:factory-absent?` by `resolve`
    (`child-transfer-checks`, `runtime-test`), `:absent` by `resolve`
    (`runtime-test` line 66, `validation-checks`), `:absent?` by
    `ns-resolve` only (`field-transfer-checks`, `identity-loader-checks`),
    and `:absent? false` as a presence check
    (`runtime-test` line 135 and 162, from `expansion-checks`).
  State:
  - the key form for a cell with more than one definition (for example,
    one `:new-class?` and one `:factory?` per definition, in the map that
    describes that definition, or a named key per definition);
  - whether a definition with no current factory check gets `:factory?`;
  - whether the current absence keys change to the plan `:absent?` form
    (rename and stronger check, recorded as a changed expectation), stay
    and get `:absent?` next to them, or stay as they are when they already
    check the class name.

## Plan inconsistency review (pass 4)

- [x] Make the compile-cell exemption in `plan.md` "New-Class checks"
  agree with its own scope rule and with design "Shared runtime state".
  The plan defines a type definition as an evaluation of a
  `defvaluetype` or `deftype` form, `load` of source included. It also
  says "Each cell that evaluates a type definition makes the check, also
  in a one-cell child". Then it exempts the compile cells
  (`field-aot-test/persisted-restricted-field-output-test`,
  `aot-test/fallback-aot-compilation-test`), with the reason that they are
  one-cell children. But `compile` loads the fixture source, so these cells
  evaluate definitions. The reason "one-cell child" is the same case that
  the rule says does not exempt a cell. The design says "Each cell checks
  that each of its type definitions returns a Class that no earlier
  definition in the child returned, and that the factory constructs
  exactly that Class". `aot-checks/compile-fixture` (non-strict branch)
  compares only the class name (`:factory?`). Its compile defines a new
  Class, so `new-class!` would not fail on correct behavior (this is not
  the AOT consumer case). Select one:
  - add the check to the compile cells: after `compile`, resolve the
    Class, call `new-class!` once, and compare the factory Class by
    identity;
  - or keep the exemption, give a reason that does not conflict with "also
    in a one-cell child", and record it as a deviation from the design
    rule in `implementation.md`.
- [x] Make the absence model in `plan.md` "New-Class checks" ("Report")
  agree with the code that it names. The plan says that `:absent?` means
  "its class name does not load through `RT/baseLoader` and its factory
  does not resolve in its namespace (the form of
  `transfer-checks/absent?`)". `transfer-checks/absent?` does not check the
  factory: it checks `(ns-resolve *ns* 'Transferred)` (the class import)
  and the class name. Design "Shared runtime state" requires that the
  "class name and factory are absent". State the parts of `:absent?`
  (import, factory `->Name`, class name), and name a model that has them,
  or change `transfer-checks/absent?` in Slice 4. Decide this together
  with the plan ambiguity step of pass 4 (current absence keys).

## Plan ambiguity review (pass 5)

- [x] State the method that gives each cell a unique class name when its
  check code does not define in the cell namespace (`plan.md` "Cell
  harness", "Class names"; `steps.md` Slice 4). The plan says "a source
  definition gets a unique class name from its cell namespace". But some
  check code binds `*ns*` to its own fixed namespace, or creates a
  namespace with a fixed name, before it evaluates a definition:
  `field-policy-checks/evaluation` (`value-type.field-policy-checks`,
  `EvaluatedField`), `identity-receiving-checks/evaluate`,
  `expansion-checks` (lines 45, 67, 95, 106, 135), `transfer-checks/consume`
  (`transfer.<mode><index>`) and `warning-checks` (`warning.worker<index>`,
  `strict.worker<index>`). In a grouped child, two cells that call this
  code define the same class name. Example:
  `field-capability-test/public-field-evaluation-policy-test` has two cells
  (tag `Long`, tag `String`) for each child configuration, and both define
  `value_type.field_policy_checks.EvaluatedField`. This breaks design
  "Shared runtime state" ("a class name that no other cell in the child
  uses"). The `absent?` default class name and the `:factory?` namespace
  ("`*ns*` when the definition is evaluated") also depend on this
  namespace. `implementation.md` lists some of these cases as "Fix them in
  the Slice 4 conversion", but the plan gives no rule. State:
  - the rule for this check code (for example: define in `*ns*` at cell
    start, which is the cell namespace; or make each fixed namespace name
    from the cell namespace name);
  - the list of check functions that the rule changes, or a Slice 4 check
    that finds them (for example, a grep for `binding [*ns*` and
    `create-ns` with a fixed symbol);
  - whether a cell whose check code cannot change keeps its own child, and
    under which rule.
- [x] State the definition-map placement for repeated attempts whose
  attempt value is not a map ("Key placement"). The plan has two branches:
  the current map of each definition or attempt, or `:definitions` (simple
  type name → one definition map). `field-policy-checks/evaluation` defines
  `EvaluatedField` twice in one cell (`attempts`). A successful attempt
  gives a vector of construction maps, and a rejected attempt gives the
  `checks/failure` map. Thus no current map holds the keys of a successful
  attempt, and `:definitions` cannot hold two attempts of one name. The
  rejected attempt also has no stated place for `:absent?`, and its
  expected value comes from `checks/policy-rejection` in the parent. State
  the form of each attempt (for example, `{:constructions [...]
  :new-class? true :factory? true}` and the failure map with `:absent?`),
  and the expected values in `field-capability-test`.

## Plan ambiguity review (pass 6)

- [x] State the agent and `future` thread rule of the harness child form
  (`plan.md` "Cell harness"; `steps.md` Slice 3).
  `transfer-test/constrained-concurrent-strict-test` calls
  `(shutdown-agents)` in its child form today (`transfer_test.clj` line
  107), because the `future` threads of
  `warning-checks/concurrent-rejections` are not daemon threads and delay
  JVM exit. In a grouped child, a cell must not call it: later cells'
  `future` calls fail after the pool shuts down. Without any call, a child
  whose cells use `future` or agents waits for the agent pool idle timeout
  before exit, which adds to the child wall time that the timeout rule
  bounds (120 s + 10 s per extra cell). `implementation.md` ("Plan-steps
  work (pass 5)", "Open for the harness") records this as not in a plan
  step yet. State in `plan.md` "Cell harness" that the child form calls
  `shutdown-agents` exactly once, after it prints the EDN result line (or
  state another exit rule), and that no cell form calls it. Add the rule to
  the Slice 3 child-form step and remove the call from the converted test
  in its Slice 4 item.

## Plan inconsistency review (pass 5)

- [x] Make the two `plan.md` rules for `runtime-test/public-definition-test`
  agree ("New-Class checks"). "Key placement" says that its definition has
  no map of its own, so `Sample` gets a definition map under
  `:definitions`, next to the `{:available? ... :outcome ...}` keys.
  "Current absence keys" says that its `:absent` key "becomes `:absent?`".
  That key is in the `:outcome` failure map (`{:chain [...] :absent
  (nil? (resolve 'Sample))}`, `runtime_test.clj` line 66). Thus under
  `error` the plan gives two places for one absence check: `:outcome
  {:chain [...] :absent? true}`, or `:definitions {Sample {:absent?
  true}}` with `:outcome {:chain [...]}`. On success, the current factory
  check is the first item of the `:outcome` vector `[(= Sample (class a))
  42 available?]`. "A current factory check of another form becomes
  `:factory?`" does not say whether it leaves that vector. Select one
  place for `:absent?`, `:new-class?` and `:factory?` of `Sample`, and
  give the new expected `:outcome` for each policy. Decide this together
  with the plan-ambiguity pass-5 step on non-map attempt values.
