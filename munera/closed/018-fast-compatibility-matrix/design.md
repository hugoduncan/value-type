# 018 — Fast compatibility matrix

## Intent

Make the fallback compatibility suite fast. Keep all of its evidence.
A JVM process must exist only where the evidence needs a process boundary.

## Problem

The `:fallback-test` suite (`test/compatibility`) is too slow to run often.
On 2026-10-29, the Java 8 parent run took about 27 minutes for 119 tests and
7723 assertions. The documented verification runs the suite three times (Java 8,
EA3 preview off, EA3 preview on), so it takes about 80 minutes.

The time is in child JVMs, not in assertions:

- `value-type.jvm-runner/run-jvm` starts one new JVM for each matrix cell.
  A child takes about 3.5 s (JVM start, Clojure start, source load of
  `value-type.core` and the test namespace, then the check).
- The children run one at a time.
- A matrix gives each cell its own child. For example,
  `child-transfer-test/receiver-child-replacement-test` has 3 runtimes ×
  2 policies × 2 execution modes × 3 receiving modes × 2 serialized forms
  = 72 consumer JVMs. Only 12 distinct child configurations exist.
- Most tests select their children from `jvm-config/configuration`
  `:baseline` and `:valhalla`. These do not depend on the parent JVM. Thus each
  of the three documented parent runs repeats the same child matrix. Only
  `:selected` copies the parent JVM. "Test classes" lists the tests of each kind.

## Concepts

- **Child configuration** — the facts fixed when a child JVM starts: Java
  executable, JVM options (preview, `-Xint`), `value-type.fallback` policy and
  classpath.
- **Cell** — one checked case in a matrix. It has a label and an expected result.
- **Cell label** — the key of a cell: the test Var and the `testing` context
  vector, as Scry reports them (`:var` and `:contexts`).
- **Child kind** — each `run-jvm` child has one kind:
  - *cell child*: runs cells and returns one result for each cell;
  - *producer*: makes transfer expansions for cell children;
  - *AOT compile child*: compiles Clojure source to output that a later child
    of the test loads;
  - *Java compile child*: compiles Java source against an artifact.
  A child that compiles as its checked case is a cell child (for example,
  `aot-test/fallback-aot-compilation-test`).
- **Artifact** — the AOT output that a cell child loads: the output directory
  of one AOT compile child, plus the output of the compile children of the
  same test that compiled against it (for example, the Java consumer classes,
  or the separately compiled Clojure consumer in `field-aot-test`).
- **Support assertion** — an assertion that belongs to no cell: an assertion
  on a producer, AOT compile or Java compile child, on a cell child as a whole
  (for example, its child-level stderr; or its exit code and result count
  when the child runs more than one cell), or on fixture files.
- **Fallback cell** — a cell whose check evaluates a policy-guarded identity
  fallback: a source definition with no value support, or a fixed fallback
  expansion or artifact on any JVM. The explicit child configuration gives
  this, not what the child detects
  (`mementum/knowledge/controlled-fallback-boundaries.md`). A direct call of
  `value-type.impl.runtime/enforce-fallback!` is also a fallback cell (for
  example, `runtime-test/policy-test`).
- **Library runtime** — one load of `value-type.core` in one JVM. It owns the
  policy value, the warn-once state and the cached capability results.
- **Process boundary** — a reason that a cell must have its own JVM.
- **Test** — one `deftest`.

## Test classes

Each test has one class:

| Class | Children | Runs |
|---|---|---|
| Cross-JVM | only from `:baseline` and `:valhalla` | once, in the cross-JVM suite (Scope 5) |
| Parent-dependent | only from `:selected` | under each parent |
| Mixed | `:baseline` or `:valhalla` producers or AOT compile children, `:selected` cell children | under each parent; producers and compile children run again under each parent |
| In-process | none; checks run in the parent JVM | under each parent |

Classes on 2026-10-29 (all namespaces are in `value-type.`):

- Cross-JVM namespaces: `array-boundary-test`, `array-info-boundary-test`,
  `array-matrix-test`, `array-sequence-boundary-test`, `capture-loading-test`,
  `child-transfer-test`, `controlled-field-capability-test`, `field-aot-test`,
  `field-capability-test`, `field-transfer-test`,
  `function-redefinition-test`, `generated-value-aot-test`,
  `identity-aot-test`, `loader-policy-test`, `public-field-aot-test`,
  `public-mixed-field-aot-test`, `public-native-field-aot-test`.
- Parent-dependent: all tests in `runtime-test`;
  `aot-test/fallback-aot-compilation-test`.
- Mixed: all tests in `transfer-test`; the other three tests in `aot-test`.
- In-process namespaces: `capture-failure-test`, `fields-test`,
  `identity-backend-test`, `identity-loader-test`,
  `isolated-sibling-loader-test`, `public-field-annotations-test`.
- `identity-receiving-test` has two classes. In-process:
  `receiving-identity-fields-test`, `receiving-redefined-child-test`,
  `receiving-identity-native-modes-test`. Cross-JVM:
  `receiving-identity-policy-test`, `incompatible-value-receiving-test`,
  `generated-value-receiving-policy-test`,
  `strict-nested-public-declaration-test`, `nested-public-warning-scope-test`.

A new test gets its class from this table.

## Isolation rules

These axes need a process boundary. Keep one child for each distinct value:

1. Java executable and JVM options. The JVM fixes them at start.
2. Fallback policy. `value-type.impl.runtime/policy` reads the system property
   once, at namespace load.
3. Producer and consumer roles. Transfer evidence requires that no producer
   compiler state reaches the consumer.
4. Source-free AOT consumers. A cell child that loads AOT output has no
   fixture source on its classpath. Its classpath is the `:classpath` of its
   `jvm-config/configuration` entry plus exactly one artifact. Cells that load
   different artifacts use different children, also when the rest of the
   child configuration is equal. Reason: the artifacts of one test can use one namespace name (each
   `aot-test` artifact uses `fallback.aot-fixture`).
5. Cells whose evidence is the first event in a fresh library runtime, or the
   first initialization of an AOT-compiled namespace or class that another
   cell also loads. Examples: first capability detection, require order
   (`array-matrix-test`), warning scope across runtimes
   (`transfer-test/concurrent-warning-scope-test`), the first load of a
   source-free AOT namespace. Repeated loads of one namespace in one cell are
   part of that cell, and this rule does not separate them: a retry after a
   failed load (`aot-checks/load-fixture` under error policy), or a load
   before and after `:reload` (`aot-fixture/reload-form`).

These axes do not need a process boundary. Cells that differ only in these
axes can share one child:

- receiving mode (`:eval`, `:source`, `:analysis`);
- serialized form (`macroexpand`, `macroexpand-1`);
- other inputs to the same check in the same library runtime.

### Shared runtime state

Cells in one child share one library runtime. They can see its state:

- the policy (`value-type.impl.runtime/policy`);
- the warn-once flag (`value-type.impl.runtime/warned?`);
- the capability caches (`detected-capability` and
  `detected-field-capability` in `value-type.impl.runtime`;
  `allocation-capability` and `inspection-capability` in
  `value-type.impl.array`).

Scope 3 states the policy evidence that follows from this state.

Cells must not share definition state: Vars, imports, Classes, class names,
namespaces and thread bindings (`Compiler/LOADER` and the Vars that a cell can
`set!`). Thus:

- Each cell uses a fresh namespace.
- Each cell runs in its own thread-binding frame. The grouped child gives each
  cell new bindings, made at cell start:
  - `Compiler/LOADER`: a new `DynamicClassLoader`
    (`clojure.lang.RT/makeClassLoader`), as `Compiler.eval` gives each
    top-level form. Its parent is the `LOADER` value of the child's top-level
    frame.
  - the Vars that `clojure.main/with-bindings` binds (for example `*ns*` and
    `*warn-on-reflection*`), each with its value at cell start.

  Reason: a child is one `clojure.main -e` form, thus one top-level `LOADER`
  frame. `value-type.impl.loader/install!` sets the innermost `LOADER` binding
  to a capture loader whose parent is the old value. A cell that calls
  `macroexpand` or `macroexpand-1` itself (for example `expansion-checks`,
  `controlled-field-capability-test`) leaves this loader in the frame. With
  its own frame, the loader stays in that cell. No cell changes the top-level
  frame. Thus at cell start, no capture loader of another cell is bound or is
  a parent of the bound loader, as in a fresh JVM. This meets the Ramora
  mitigation "Verify both expansion forms in fresh receivers"
  (`ramora/defvaluetype-design/risks-and-evolution.md`, risk "`macroexpand`
  from tooling installs a loader"). The unique class name (next item) removes
  the other shared path, the `DynamicClassLoader` static cache.
- Each cell that defines a type uses a class name that no other cell in the
  child uses. A fresh namespace alone is not sufficient: a transferred
  expansion fixes its class name in the producer (for example,
  `child-transfer-checks/produce` gives `user.TransferParent`).
- Transfer: the producer gives one expansion for each consumer cell. It
  expands each one in its own producer namespace, so each expansion has its
  own class name. Consumer cells do not share an expansion.
- Each cell checks that each of its type definitions returns a Class that no
  earlier definition in the child returned, and that the factory constructs
  exactly that Class. Each cell that expects rejection checks that its class
  name and factory are absent.
- Cells in one child run in a fixed order.

### Warn-guard evidence

D13 (`ramora/defvaluetype-design/fallback.md`) requires evidence that the
receiving guard runs for each serialized form and receiving mode. In a shared
warn child, only the first fallback cell warns. Thus:

- Error-policy fallback cells are the guard evidence for each cell. Each one
  rejects (Scope 3). If the guard is lost for a form or mode, that cell
  succeeds and thus fails its check.
- Warn cells check only warn-once (Scope 3).
- Each grouped warn fallback cell has a matching error-policy fallback cell
  in the same test namespace. The match runs the same guard through the same
  path on the same JVM. These attributes agree:
  - Java executable and JVM options of the cell child;
  - the definition source form, apart from namespace and class names;
  - the path of the guarded code to the cell:
    - transfer: producer configuration, serialized form and receiving mode;
    - source definition: the operation that evaluates the form (for example,
      `eval`, `load` or `compile`);
    - function-contained definition: the operation that defines the function
      and the call that runs it;
    - source-free AOT artifact: the configuration of its AOT compile child
      and the load operation (`require`, `:reload` or a function call).

  These attributes can differ: policy, test Var, label, namespace and class
  name. The transfer rule gives each cell its own expansion. Thus the match
  is on the producer configuration and source form, not on one expansion.
  If no match exists, the plan adds one (a new label), or keeps the unmatched
  warn cell in its own child under rule 5.

Reason: the guard is part of the expansion. The child policy selects only what
the guard does. Thus an error cell shows that the guard runs for its path
(for transfer: its form and mode).

## Scope

In scope:

1. **Grouping.** The grouping unit is one test. A child runs cells of one test
   only. To share a child between tests, the plan merges the tests; their
   labels then change and are mapped. A cell child runs all cells of its group
   and returns one result for each cell. The parent asserts each cell with its
   label and expected result.
   - Child-count bound: a test T starts at most
     `cell(T) + producer(T) + aot(T) + java(T) + exceptions(T)` children:
     - `cell(T)`: the number of distinct child configurations of the cell
       children of T. The classpath is part of the configuration; thus each
       artifact gives its own configurations (rule 4).
     - `producer(T)`: the number of distinct producer configurations of T.
       One producer makes the expansions for all consumer cells of its
       configuration. A producer and a cell child with the same
       configuration count as two (rule 3).
     - `aot(T)`: one for each Clojure source fixture that T compiles in an
       AOT compile child.
     - `java(T)`: one for each Java source compile of T.
     - `exceptions(T)`: the added cell children that rule 5 requires, listed
       in `implementation.md`.
2. **Per-cell outcome.** A child records the result of each cell separately.
   An exception in one cell does not stop the other cells. It becomes that
   cell's result. Each cell binds `*out*` and `*err*` to its own writers. The
   text of both is part of that cell's result. A thread that a cell starts
   binds `*out*` and `*err*` to the same writers. Where the check needs other
   bindings absent on that thread, the thread binds only these two Vars (for
   example, `expansion-checks/on-thread` checks an unbound
   `Compiler/LOADER`). Child output outside cells:
   - stdout: the last line is the EDN result of all cells. Cell text does not
     reach it.
   - stderr: child-level stderr must be empty, under all policies. The parent
     asserts this once for each cell child, as a support assertion. The
     parent makes it in its own `testing` context. This context names
     `child stderr` and these parts of the child configuration: Java
     executable, JVM options, policy and, for an AOT cell child, the
     `fixture-ns` of the AOT compile child of its artifact. It names no
     classpath and no temporary path. Thus the label is the same in each run
     and at each limit (acceptance 3). Children that differ only in the
     artifact can have one label. No cell label has this context, so this
     label is not a cell label.
   Expected cell text. A cell gives its result as its return value. It does
   not print its result. Text that a cell captures itself is not cell text
   (for example, `with-out-str` in `aot-fixture/reload-form`, or the own
   `*err*` binding in `expansion-checks`). One rule applies to every cell:
   - `*out*`: empty.
   - `*err*`: the fallback warning that Scope 3 assigns to the cell, and no
     other text. Thus the first fallback cell of a warn child expects
     exactly one line: `value-type: identity deftype fallback: ` and a
     context. Where the current check of the cell states the context (for
     example, `fixed fallback representation`, or `test` in
     `runtime-test/policy-test`), the cell expects that context. Otherwise
     (a prefix or `re-seq` check, or no text check), the cell expects the
     prefix and a context of one line. Each other cell expects empty
     `*err*`: error-policy and silent cells, cells with value support, and
     the later fallback cells of a warn child.
   - The current check of a cell is the check of the current child that
     the cell comes from. A current child can split into more than one cell
     (a split; for example, the `transfer-test/transferred-expansion-test`
     consumer: `:eval` and `:source`, both serialized forms, 4 cells). Its
     current check is then the current check of each of its cells. Thus,
     under warn, if a cell of the split is the first fallback cell of its
     new child (fixed cell order), it expects the current warning with the
     context that the current check states (in the example, `fixed fallback
     representation`). Each other cell of the split expects empty `*err*`.
     The plan selects the splits. "Label mapping" (Invariants) gives their
     labels.
   These rules apply to every cell child: a grouped child, a one-cell child
   and a child kept under isolation rule 5. A current check of process stderr
   becomes a check of the `*err*` text of its cell under this rule (for
   example, the fallback warning in
   `transfer-test/concurrent-warning-scope-test`). With the empty
   child-level stderr, the new checks accept no outcome that the current
   check rejects, except the Scope 3 warn exception (Invariants).
3. **Policy evidence in shared runtimes.** These rules apply to fallback cells.
   - Warn: exactly one fallback warning in each warn child that has one or more
     fallback cells. It is in the result of the first fallback cell. Later
     fallback cells expect no warning. "Warn-guard evidence" gives the guard
     evidence for these cells.
   - Error: each fallback cell rejects, also after an earlier rejection by
     another cell in the same child. A cell with value support succeeds under
     error policy (D10; `ramora/non-nil-field-design/verification.md`, "With
     value support: all policies produce value owners"). The Error form
     depends on the phase: a direct call throws `java.lang.Error`; a source
     compiler operation wraps it in `Compiler$CompilerException`; a retried
     failed AOT top-level initializer throws `NoClassDefFoundError` without
     guard re-entry. The AOT retry is a second load in the same cell (rule 5).
     Each expected result states its form as root class and message. Where
     the current check compares the cause chain, the expected result keeps
     that chain.
   - Silent: no cell writes to `*err*`. A child with no `:policy` has the
     silent policy (the default of `value-type.impl.runtime/policy`).
4. **Parallel children.** The runner can run independent children at the same
   time.
   - Limit: the environment variable `VALUE_TYPE_TEST_JVM_LIMIT` sets the
     maximum number of concurrent children. It is a positive decimal integer.
     When the variable is not set, the limit is 4. Any other value is invalid.
     `value-type.jvm-runner` reads and checks the variable once, when its
     namespace loads. An invalid value makes the load throw an exception whose
     message names the variable and the value. Scry then reports a runner
     error (`:scry.cli/runner-error`), runs no test and exits 1. Each
     documented command loads `jvm-runner`, because its tests that start
     children require it. A run that loads no namespace that requires
     `jvm-runner` (for example, `--namespace value-type.fields-test`) starts
     no child. It does not read the variable and does not fail because of it.
     The limit applies to all children of one parent JVM, across
     all tests and calls. Each running child counts as one, of any kind
     (Concepts). A child starts only after each child whose output it uses
     returns.
   - With the limit at 1, the runner works serially. Report order and labels
     are the same at all limits.
   - Timeout: applies to each child, from process start. Time that a child
     waits for the limit does not count. The value is the explicit
     `:timeout-ms` (default 120 s) plus 10 s for each cell after the first.
     A one-cell child keeps its current timeout.
   - Each child keeps its own temporary directories.
5. **One child matrix for each verification.** Cross-JVM tests move to a
   separate suite: class path root `test/cross-jvm/` and alias
   `:cross-jvm-test`, with `--dir test/cross-jvm`. The alias also adds
   `test/compatibility` for the shared test support. A moved namespace keeps
   its name. The cross-JVM tests of `identity-receiving-test` move to a new
   namespace. `:fallback-test` keeps the parent-dependent, mixed and
   in-process tests and runs under each parent. The documented verification
   runs `:cross-jvm-test` once, under the owner parent.
   - A per-parent run does not have the cross-JVM tests on its class path. It
     does not discover them and reports no result for them. Thus it cannot
     pass them with no checks.
   - Scry-visible form: the namespaces in the results of each command. The
     cross-JVM namespaces appear only in the `:cross-jvm-test` results. A
     `--namespace` request for a cross-JVM namespace under `:fallback-test`
     gives a Scry runner error and exit 1.
6. **Documentation.** Update `doc/development/runtime.md` and the related Ramora
   verification and embodiment text. State the commands, the test layout, the
   concurrency setting, the child timeout, the parent that owns the cross-JVM
   matrices, and the isolation rules. Focused commands that run cross-JVM
   namespaces use `:cross-jvm-test`. The "120-second timeout" text changes to
   the timeout rule in Scope 4. In
   `ramora/defvaluetype-design/risks-and-evolution.md` (risk "`macroexpand`
   from tooling installs a loader"), state that a fresh receiver is a new JVM
   or a cell with its own `LOADER` binding ("Shared runtime state").
7. **Release and quality tooling.** Add the cross-JVM suite to each file that
   lists the compatibility suites or test directories:
   - `.github/workflows/release.yml`: the release gate runs the
     `:cross-jvm-test` command once, under the owner parent, in addition to
     the three `:fallback-test` commands. The `:cross-jvm-test` step has its
     own preserve step, in the same form as the `:fallback-test` steps: step
     id `cross-jvm`, then, directly after it, `preserve-cross-jvm-failures`,
     which runs only when the `cross-jvm` step fails and uploads
     `.scry-results/` as the artifact `release-scry-cross-jvm`.
   - `ramora/release-workflow.md` ("Workflow flow"): the release gate includes
     the cross-JVM tests.
   - `bb/value_type/quality.clj`: the lint paths include `test/cross-jvm` and
     the aliases include `:cross-jvm-test`.
   - `test/tooling/ci-failures.sh` and `test/tooling/hooks.sh`: the checked
     paths include `test/cross-jvm`.
   - `doc/development/quality-checks.md` ("Quality checks"): the formatting
     and code lint scope text agrees with the lint paths in
     `bb/value_type/quality.clj`. It gives no count of test directories (for
     example, "all test directories listed in the test layout"). Its other
     paths do not change.
     `ramora/defvaluetype-design/embodiment/artifacts.md` ("Development
     quality workflow") refers to this file for the check scopes.
   - Other files that list `test/compatibility` or `:fallback-test` for a
     suite or lint check: the plan finds them and adds the cross-JVM suite.

Out of scope:

- Changes to `src/`. This task changes test support, tests, test aliases,
  documentation and the release and quality tooling in Scope 7 only.
- Removal of matrix axes that the designs require: runtimes, policies,
  normal and interpreter execution, receiving modes, serialized forms, AOT
  consumers, flattening on/off.
- AOT compilation of `value-type.core` for children. The suite checks runtime
  compilation.
- JVM start-up tuning (CDS, tiered compilation flags). This can be a later task.
- The `:test`, release and build suites. This excludes changes to the tests
  of these suites. It does not exclude Scope 7.
- Changes to Scry.

## Decisions

- **Group by child configuration. Do not use isolated class loaders.** One JVM
  can hold more than one library runtime if each runtime has its own class loader.
  But this tests a deployment shape that users do not use, and it adds a second
  isolation mechanism. Policy stays one value for each JVM.
- **A fresh namespace, a unique class name and an own thread-binding frame
  are the isolation unit for cells.** Clojure creates a new Class for each
  `deftype` evaluation. But the
  `DynamicClassLoader` static cache keys Classes by name
  (`mementum/knowledge/testing-clojure-generated-classes.md`, "Static cache"),
  and a transferred expansion fixes its name in the producer. Thus a name that
  an earlier cell used can make a skipped definition pass. A unique class name
  removes the shared name. The new-Class check detects a skipped definition.
  The own `LOADER` binding keeps a capture loader that one cell installs out
  of later cells ("Shared runtime state").
  This replaces the rule in
  `mementum/memories/repeated-definitions-must-prove-new-class-identity.md`
  that each policy, receiving mode and expansion form needs a fresh JVM.
  Policy keeps its own JVM (rule 2). Receiving mode and expansion form share a
  child. Reason: the hazard in that memory is an earlier Class or factory with
  the same name, not the shared process.
- **Error cells are the guard evidence.** See "Warn-guard evidence". The
  alternative, one warn child for each form and mode, keeps most warn
  children and thus most of the cost.
- **One artifact for each AOT cell child (rule 4).** Rejected: one classpath
  with all artifacts of a test. It needs a distinct namespace name for each
  artifact, and thus changes the fixtures. It decreases the child count only
  in `aot-test/source-free-fallback-aot-loading-test` (6 to 3 cell children).
  The other AOT tests compile one artifact for each test.
- **The grouping unit is the test.** Tests run independently. A child that
  serves two tests couples their order and state.
- **Suite selection runs a cross-JVM matrix once.** The documented commands
  already select suites by `--dir` and alias. Rejected: a check of the parent
  JVM in each test. It makes cross-JVM tests depend on the parent again, and a
  skipped run needs a placeholder assertion to be visible.
- **The parent keeps the assertions.** Children return data. The parent asserts.
  This keeps Scry labels, failure results and exit codes unchanged in form.
- **Exceptions are explicit.** A cell that keeps its own child has a comment
  that names its isolation rule. `implementation.md` also lists each exception
  for each namespace: test, child count, isolation rule and reason.
- **This task updates the release and quality tooling (Scope 7).** Decided
  by the user on 2026-10-29. Reason: the move in Scope 5 removes the
  cross-JVM tests from the release gate and from the quality checks. A
  separate task leaves a gap between the two tasks, and the Intent requires
  that the task keeps all evidence. Rejected: a separate task.
- **The plan selects the owner parent for cross-JVM matrices.** The owner must
  be one of the three documented parents. The plan records the reason.

## Invariants

- Labels: the label record of a command lists each label that its Scry
  assertion events report (`:var` and `:contexts`), with a cell count:
  - first record: Scry does not report children. Thus the record gets the
    label at each cell child start (the start label), with the `:contexts`
    value that Scry uses (`clojure.test/*testing-contexts*`, reversed). The
    plan names the method. The cells of one child:
    - nested labels: each label that extends the start label with more
      contexts, has assertions on the result of that child, and starts no
      child is one cell of that child. The assertions under the start label
      itself are then support assertions. Today only
      `loader-policy-test/fresh-policy-loader-rejection-test` has nested
      labels: each child returns 6 cases, and each case label
      (`(testing (pr-str case-key) ...)`) is one cell. Its start label holds
      the exit and result-count assertions.
    - no nested labels: the start label is one cell.

    Thus the cell count of a label is the number of children that give it a
    cell. Today each cell child runs one cell, except the children of
    `fresh-policy-loader-rejection-test` (6 cells each).
  - after the work: the parent records the label once for each cell result
    that it asserts, at that assertion. The label is the running test Var
    and the `:contexts` value, as Scry reports them for that assertion
    (`:var` and `:contexts`). The test support function that asserts a cell
    result makes the record, once for each cell, also when it makes more
    than one assertion.
    The plan names the record output. The cell count of a label is the
    number of cell results recorded under it. Each cell has its own label;
    no two cells in one command have the same label. Thus each cell label
    has a cell count of 1.
    - The parent asserts each cell that it sent to a child, also when the
      child crashes, times out or returns no result for that cell. Thus a
      failed child keeps its cell count.
    - An assertion that the parent does not make through this function is a
      support assertion (for example, the exit and result count of a grouped
      child; the child-level stderr of each cell child; producer and compile
      children).
    - The function checks the `*out*` and `*err*` text of the cell as part of
      the cell result, with the expected text that Scope 2 gives ("Expected
      cell text"). A current check of process stderr becomes this check
      (Scope 2). This also applies to a child kept under isolation rule 5.
    - For a child with one cell, the function makes the exit assertion of
      that child under the cell label. This is a cell assertion (Concepts,
      "Support assertion").
    - Reason: the start-label method of the first record does not separate
      cells from the Scope 2 child-level stderr label of a grouped child.
      The first record cannot use this method: the current tests assert
      child results inline, with no cell-result function.
  - support assertions: they are in the label record and in the mapping. A
    label with only support assertions has a cell count of 0. More than one
    support assertion can have one label (for example, the producer checks
    for each producer policy). After the work, a label has support assertions
    or cells, not both.
- Label mapping: each label of the first record maps to one or more labels
  after the work (to itself, if it does not change). These are its target
  labels. A cell label after the work is the target of at most one label of
  the first record. The cell counts of the target labels of a label add up to
  at least its cell count. Thus a label with n cells maps to n or more cell
  labels (for example, labels that name the serialized form). A split
  (Scope 2, "Expected cell text") maps the label of its current child to
  all of its new cell labels, for example 1 cell to 4 cell labels. A label
  after the work that is no target is a new label. New labels are
  permitted.
- Expected results do not decrease: a new check must not accept an outcome
  that the old check rejected. The one exception is Scope 3 warn: a later
  warn fallback cell expects no warning. Its record names its matching
  error cell ("Warn-guard evidence").
- `implementation.md` records each label change and each changed expectation:
  old label, target labels, old expectation, new expectation and reason. A text
  check that only the Scope 2 text rule adds or makes stricter (for example,
  in a test that checks no `*out*` or `*err*` today) is recorded once for each
  test: the test, its current text check (or none) and the rule.
- Failures stay visible. A child crash or timeout fails every cell in that
  child and shows the child's exit code, stdout and stderr.
- No cell can pass because of definition state from an earlier cell (Vars,
  imports, Classes, class names, namespaces, thread bindings such as
  `Compiler/LOADER`). Cells can see the shared
  library-runtime state ("Shared runtime state").
- A child that does not return a result for a cell fails that cell.

## Acceptance criteria

Measurement:

- The full documented verification is the set of commands in "Verified
  fallback matrix" of `doc/development/runtime.md`. Before the work: the three
  `:fallback-test` commands. After the work: the three `:fallback-test`
  commands and the `:cross-jvm-test` command. The focused commands are not
  part of it.
- Total time: the sum of the end-to-end wall-clock times of these commands,
  run one after another.
- Namespace time: the wall-clock time of the namespace's tests in the parent.
  Before and after use the same method; the plan names it.
- Child count: the number of `run-jvm` children for each namespace and each
  test.
- Before and after runs use the same machine and the same JDK builds.
  `implementation.md` records the machine (model, CPU count, OS).
- The first record uses the current serial runner, at the commit where the
  task work starts.

1. Before work starts, `implementation.md` records for each compatibility
   namespace: the child count, the namespace time and the label set with the
   cell count of each label. Use the three documented parent runs.
2. After the work, the same record shows:
   - each test starts no more children than the child-count bound in Scope 1;
   - the label set contains each label of the first record, or its mapped
     labels, with the cell counts that the label mapping requires. A
     cross-JVM label compares with the `:cross-jvm-test` run. Other labels
     compare with the run under the same parent;
   - at the default concurrency limit (4), the total time is at most 25% of
     the first total.
3. Each documented command exits 0 with empty Scry failure results at the
   default concurrency limit: `:fallback-test` under each of the three parents
   and `:cross-jvm-test` under the owner parent. The `:cross-jvm-test` command
   and the Java 8 `:fallback-test` command also exit 0 with a limit of 1. Each
   reports the same labels in the same order as at the default limit.
4. A permanent test shows each rule in Scope 3 (warn-once, repeated strict
   rejection with its Error form, silent with no `*err*` text) in a child that
   has more than one fallback cell.
5. A permanent test shows these runner outcomes:
   - a failing cell (a fixture cell throws): the cell fails with its output,
     and the other cells of the child report their results;
   - a crashing child (a fixture cell calls `System/exit` with a nonzero code)
     and a timed-out child (a fixture cell sleeps past a short explicit
     `:timeout-ms`): every cell of the child fails and shows the exit code,
     stdout and stderr.
   The tests for 4 and 5 are in `test/cross-jvm/` and use `:baseline`
   children. They assert on the cell results that the runner returns; they
   do not make the suite fail. Their labels are new labels.
6. The documentation in Scope item 6 agrees with the suite behavior.
7. `git diff` for the task shows no change under `src/`.
8. The release workflow runs the `:cross-jvm-test` command under the owner
   parent and the three `:fallback-test` commands. The `:cross-jvm-test` step
   has its own preserve step and artifact name, in the form that Scope 7
   gives. `ramora/release-workflow.md` states this gate. The quality workflow (`bb/value_type/quality.clj`,
   `test/tooling/ci-failures.sh`, `test/tooling/hooks.sh`) lints and checks
   `test/cross-jvm` with the same checks as `test/compatibility`.
   `doc/development/quality-checks.md` states the lint scope with no count of
   test directories, and its scope text agrees with the lint paths
   (Scope 7).

## Context

- Test support: `test/compatibility/value_type/jvm_runner.clj`,
  `test/compatibility/value_type/jvm_config.clj`, `aot_fixture.clj`.
- 21 test namespaces and `aot-fixture` call `run-jvm`. The largest users are
  `field-transfer-test`, `public-field-aot-test`, `child-transfer-test` and
  `aot-test`.
- The runner and the AOT fixtures already use `Files/createTempDirectory`.
  This makes parallel children possible.
- Policy and warn-once state: `src/value_type/impl/runtime.clj`. The warning
  goes to the `*err*` binding active when the fallback occurs, so each cell
  can capture its own warning.
- Required evidence: `ramora/non-nil-field-design/verification.md`
  ("Runtime and deployment matrix", "Layout and performance evidence"), and
  task 017 `design.md` acceptance item 8.
