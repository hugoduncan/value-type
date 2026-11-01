# Plan — 018 fast compatibility matrix

## Approach

Add the label recorder and make the first record. Then build the shared test
support (parallel runner, cell harness, cell-result function). Then convert
tests namespace by namespace, then split the cross-JVM suite, then add the
permanent runner tests, then update documentation and tooling, then take the
after record and verify the acceptance criteria.

Key decisions:

- **Recorder.** One test support namespace, `value-type.label-record`
  (`test/compatibility/value_type/label_record.clj`), makes both records with
  one method. It is permanent and is active only when the environment variable
  `VALUE_TYPE_TEST_RECORD` names an output file. When the variable is not set,
  it installs nothing and records nothing.
  - Hook: when the namespace loads with the variable set, it wraps the root of
    `clojure.test/test-var` (once; `defonce` guard). `jvm-runner` requires it.
    Scry 0.1.61 requires all test namespaces before its `with-redefs`, and its
    `test-var` replacement calls the root value that it finds then. Thus the
    wrapper runs for each Var. Each documented command loads `jvm-runner`.
  - For each Var, the wrapper binds a dynamic Var `*var*` to the Var, and
    binds `clojure.test/report` (dynamic) to a function that records each
    `:pass`, `:fail` and `:error` event and then calls the previous `report`
    value (Scry's). The event label is the Var and
    `(vec (reverse clojure.test/*testing-contexts*))`, as Scry builds
    `:contexts`. The Var is the argument that Scry gives to `test-var`, so it is
    equal to the Scry `:var` by construction.
  - The wrapper measures the wall-clock time of each Var. Namespace time is the
    sum of the Var times of the namespace. No compatibility namespace uses
    `use-fixtures`, so no fixture time is lost.
  - Child starts: `child-start!` records the test Var (`*var*`), the start
    label, the child kind, a hash of the child configuration (Java
    executable, JVM options, policy, classpath) and `:assertion-index`: the
    number of entries in `:assertions` at the child start. The runner calls
    it on the calling thread, once for each child, before the child goes to a
    worker thread. Thus a parallel child gets the test Var of its caller.
  - Child kind: the `run-jvm` option `:kind` (`:cell`, `:producer`,
    `:aot-compile`, `:java-compile`; default `:cell`). The runner uses it only
    for the record. The kind follows Concepts: an AOT compile child compiles
    output that a later child of the test loads; a child that compiles as its
    checked case is a cell child. The recorder commit sets `:kind` on each
    current producer and compile child:
    - `aot-fixture/compile-clojure` and `compile-producer` take a `:kind`
      option (default `:aot-compile`) and pass it to `run-jvm`.
      `field-aot-test/persisted-restricted-field-output-test` passes
      `:kind :cell` through its local `compile-producer`: no later child
      loads its output, and its `:after` result (`c/inspect-output`) is the
      checked case.
    - `aot-fixture/compile-java` always uses `:java-compile`. A later child
      loads the output of each current caller.
    - Each direct producer `run-jvm` call in the tests gets `:producer`, and
      each direct AOT compile call gets `:aot-compile` (for example `aot-test`
      lines 48, 89 and 121: a later consumer child loads their output).
      `aot-test/fallback-aot-compilation-test` (line 18) compiles as its
      checked case and starts no later child; it keeps the default `:cell`.
  - Cells: `cell!` records the label of one cell result. Only the cell-result
    function calls it.
  - Output: after each Var, the wrapper writes the full record to the file
    (EDN map: `:vars` with time, in run order; `:assertions`, the label and
    type of each assertion event, in report order; `:children`, in start
    order, each with its `:assertion-index`; `:cells`). The
    `:assertion-index` gives the order of child starts and assertion events
    in one sequence: the events of a child start S are the `:assertions`
    entries from the index of S up to the index of the next child start.
    Thus the file is complete when the CLI exits. A child that loads the
    recorder runs no Var and writes nothing.
  - File names: `target/test-record/<record>/<command>.edn`. `<record>` is
    `first`, `after` or `after-limit1`. `<command>` is the release step id:
    `fallback-java8`, `fallback-preview-off`, `fallback-preview-on`,
    `cross-jvm`. `/target/` is ignored by git.
  - `summary` (in the same namespace) reads one record file and gives, for
    each namespace: child count and distinct configurations for each test and
    kind (the terms of the Scope 1 bound), namespace time, and each label with
    its cell count. Method `:start-labels` (first record): each `:cell` child
    start with label S gives one cell to S, or, when nested labels exist, to
    each nested label: a label that strictly extends S, is no start label, and has
    assertion events after that child start and before the next child start
    (by `:assertion-index`).
    Method `:cells` (after record): the cell count of a label is the number of
    `cell!` records under it. Any other label has cell count 0.
  - One run of each documented command gives labels, children, Var times and
    total time. Total time: `/usr/bin/time -p` around each command, in that
    same run.
  - Label order (acceptance 3): the `:assertions` vector. The limit-1 and
    limit-4 records of one command must have equal label vectors.
- **First record.** It runs at the recorder commit. That commit adds only the
  recorder, the `child-start!` call in `run-jvm` and the `:kind` options. It
  changes no child, assertion or runner behavior; the runner is still serial.
  `implementation.md` records its SHA, the summaries and the total times.
- **Record after the work.** Same recorder and command forms. Every cell child
  of the converted tests (Slice 4) — grouped, one-cell, or kept under
  isolation rule 5 — is built with the cell harness and asserted with the
  cell-result function. Thus every cell label comes from `cell!`. An inline
  `is` on a child result is a support assertion and must not use a cell
  label. Exception: the fixture children of the Slice 6 tests are built with
  the cell harness but are not asserted with the cell-result function
  ("Slice 6 tests"). Their assertions are support assertions with new
  labels, as acceptance 5 states.
- **Runner.** `value-type.jvm-runner` gains:
  - the limit: `VALUE_TYPE_TEST_JVM_LIMIT`, read once at namespace load
    (default 4; an invalid value throws at load with the variable name and
    value in the message);
  - one `java.util.concurrent.Semaphore` with limit permits for all children
    of the parent JVM;
  - `run-jvms`: takes a vector of `run-jvm` option maps and returns a vector
    of results in the same order. It calls `child-start!` for each map on the
    calling thread, runs each child on a `future` that takes a permit before
    process start and releases it after the output is read, and returns when
    all children have returned. `run-jvm` is `run-jvms` of one map;
  - the read of the result (replaces "Invalid success EDN throws" in the
    `run-jvm` docstring): on exit 0, the last stdout line is read as EDN.
    When that line does not read (for example, a cell value that prints as
    `#object[...]`), a `:cell` child gives a result with `:exit`, `:out`,
    `:err`, `:read-error` (the exception message) and no `:result`. It does
    not throw, so `run-jvms` returns the results of all children of the call
    and `assert-child` fails each cell of that child. A producer or compile
    child keeps the throw: after all children of the call return, `run-jvms`
    throws an `ex-info` that names the child index, `:exit`, `:out`, `:err`
    and the read error, and gives `:results` (the results of the call, in
    input order, so the parent can assert the other children). Its output
    is input to later children, so the test stops there, as today. Empty
    stdout still gives `:result` nil (no `:read-error`);
  - the throw of a child that throws (for example, its start throws
    `IOException` when `:java` names no executable): `run-jvms` derefs each
    future and catches each exception. After all children of the call
    return, it throws an `ex-info` of the first such child (by
    `thrown-failure`) with `:index` and `:results` (the results of the call,
    in input order; nil for each child that throws), and that exception as
    the cause. This rule comes before the read rule, so a read-failure
    throw has no child that throws;
  - the timeout from process start (permit wait excluded): `:timeout-ms`
    (default 120000) plus 10000 × (`:cell-count` − 1). `:cell-count` defaults
    to 1, so a one-cell child keeps its current timeout.
  Dependent children: a child that uses the output of another child is in a
  later `run-jvms` call. For example, a transfer test calls `run-jvms` with
  its producers, makes the producer support assertions, then calls `run-jvms`
  with its consumer children. AOT tests do the same: compile children, then
  Java compile children, then cell children. `aot-fixture` gains functions
  that return the option map of each compile child, so a test can put more
  than one compile child in one call.
  Assertions: the parent makes all assertions on the test thread, after each
  `run-jvms` call returns, in input order. No `is` runs on a worker thread.
  Thus the report order and labels are the same at each limit.
- **Cell harness.** `value-type.cell-harness`
  (`test/compatibility/value_type/cell_harness.clj`).
  - `cell-child` takes a configuration (`:java`, `:classpath`, `:jvm-opts`,
    `:policy`, optional `:timeout-ms`, optional `:fixture-ns` for the label
    only) and a vector of cells. It returns a `run-jvm` option map with
    `:kind :cell`, `:cell-count` and the child form.
  - Cell description (one map for each cell):
    - `:context` — a vector of `testing` strings. The cell label is the test
      Var, the contexts at the `assert-child` call, then `:context`. Each cell
      label is unique in its command and is not the label of a support
      assertion. Thus `:context` is non-empty when the test makes support
      assertions (for example producer checks) at the contexts of the
      `assert-child` call.
    - `:form` — the cell code. Its value is the cell result. It requires its
      check namespace itself.
    - `:expected` — the expected cell result ("Cell result entry"). One of:
      - a map whose only key is `:thrown`: the entry must have `:thrown`, and
        each key of the expected `:thrown` map (`:class` and `:message`, and
        `:chain` only where the current check compares the chain) equals the
        same key of the entry `:thrown`;
      - a function: true for the cell result (for a current check that uses
        a predicate, for example `message-checks/message-names?`). The
        function carries a data description (`cell-harness/described`,
        whose docstring states the description notation); a failure report
        gives it as `:expected`. `cell-child` throws for a
        function with no description;
      - any other value: equal (`=`) to the cell result.
    - `:fallback?` — true when the explicit child configuration makes the cell
      a fallback cell (Concepts). The test computes it; the child does not
      detect it.
    - `:warning` — for a fallback cell: the expected warning context, a string
      (where the current check states the context) or `:any` (the prefix and a
      one-line context).
  - The child form requires `value-type.cell-registry` ("New-Class checks")
    at top level, then runs the cells in input order. For each cell: a fresh
    namespace (`cell.<index>`, made with `create-ns`; the harness refers
    `clojure.core` in it, as `ns` does), a new binding frame (`Compiler/LOADER` = a new
    `RT/makeClassLoader` whose parent is the top-level `LOADER` value; the
    `clojure.main/with-bindings` Vars at their cell-start values; `*ns*` =
    the cell namespace), own `*out*`/`*err*` writers, and capture of any
    `Throwable`. The child prints only the EDN vector of cell result entries
    as the last stdout line, flushes stdout, then calls `shutdown-agents`
    exactly once and exits. No cell form calls `shutdown-agents`: after the
    pool shuts down, `future` calls of later cells fail. The unconditional
    call after the print is safe in children whose cells use no agents, and
    it removes the agent-pool idle wait (about 60 s) from the child wall
    time that the timeout rule bounds. Raw threads in cell code
    (`expansion-checks/on-thread`) bind only `*out*`/`*err*`.
  - Cell result entry: one map for each cell, in input order:
    - `{:value v :out o :err e}` when the cell form returns `v`;
    - `{:thrown {:class <root class> :message <root message> :chain <class
      names, outermost first>} :out o :err e}` when it throws.
    `o` and `e` are the text of the cell `*out*` and `*err*` writers, also
    the text written before a throw. The cell result is `v`, or
    `{:thrown ...}` for a throw. `:expected` applies to the cell result only;
    the text goes only to the `*out*` and `*err*` checks.
  - Class names (design "Shared runtime state": a class name that no other
    cell in the child uses). The grouping unit is one test, so only cells of
    one test can collide.
    - Rule: cell code defines each type in the cell namespace (`*ns*` at
      cell start), or in a namespace whose name is the cell namespace name,
      `.` and a suffix. A source definition thus gets a unique class name
      from its cell namespace. A transfer producer expands each consumer
      cell in its own producer namespace.
    - Check code that binds `*ns*` to a fixed namespace, or creates a
      namespace with a fixed name, changes. Its public function reads `*ns*`
      once, on the cell thread, before it starts a thread. It uses that
      namespace in each place where it uses the fixed namespace today: the
      `*ns*` binding (also on the threads that it starts), `ns-resolve`, the
      class-name prefix (`namespace-munge` of the name) and the namespace
      argument of `consumer-aot-checks/builders`. A check that needs more
      than one namespace in a cell (concurrent workers) names each one from
      the cell namespace name, `.` and its current name (for example
      `cell.2.warning.worker5`).
    - Check functions that this rule changes (from `git grep` for
      `binding [*ns*`, `the-ns` and `create-ns` in the check namespaces).
      Each check namespace has one caller test namespace, so it changes in
      the Slice 4 commit of that namespace:
      - `field-policy-checks/evaluation` (fixed
        `value-type.field-policy-checks`; `builders` argument). Caller:
        `field-capability-test`. Collision without the rule:
        `public-field-evaluation-policy-test` has two cells (tag `Long`,
        tag `String`) in each child; both define `EvaluatedField`.
      - `identity-receiving-checks`: `ns-name*`, `evaluate`, `resolved`,
        the class name of `receiving-policy`, the literal class name
        `"value_type.identity_receiving_checks.Holder"` of
        `incompatible-value`, and the `builders` argument. Caller: the
        cross-JVM tests of `identity-receiving-test` (one cell for each
        child today; the rule applies to them all the same).
      - `expansion-checks`: `attempt`, `warning-timing`, `check-expansions`
        (`:evaluated`), `constrained-discarded` and
        `invalid-constrained-discarded`. The private `absent?` (fixed
        prefix `value_type.expansion_checks.`) changes to
        `cell-registry/absent?` ("Current absence keys"). `on-thread` runs
        its function on a raw thread whose `*ns*` is the root value. Thus
        the public function reads `*ns*` before `on-thread`, and the
        function on the thread binds it. Caller: `runtime-test`.
        Collision without the rule: `constrained-discarded-expansion-test`
        can have four cells (entry point × loader binding) in each policy
        child; all use `Discarded0` and `Discarded1`.
      - `transfer-checks/consume` (`transfer.<mode><index>`). After the
        split (`transferred-expansion-test`, 1 → 4 cells), one cell gets
        one receiving mode and one serialized expansion. `consume` takes
        both and makes its two attempts in the cell namespace. Caller:
        `transfer-test`.
      - `warning-checks/define-types` (`warning.worker<index>`) and
        `concurrent-rejections` (`strict.worker<index>`). Their worker
        namespaces take the cell namespace name as prefix. `define-types`
        replaces the class name of its expansion, which its producer
        namespace fixes (today the literal `"user.Transferred"`); the test
        gives that class name in the cell form. Caller: `transfer-test`.
        Collision without the rule: `constrained-concurrent-strict-test`
        can have two cells (serialized forms) in each child; both use
        `strict.worker0` to `strict.worker7`.
    - No change: namespaces from `gensym` (`identity-loader-checks`) are
      unique in the JVM. Check code that defines in `*ns*` today
      (`child-transfer-checks`, `field-transfer-checks`,
      `array-matrix-checks`, `array-sequence-checks`, `validation-checks`)
      defines in the cell namespace after the work.
    - Fixed source namespaces: a cell that loads or compiles a source
      namespace defines its types in that namespace, whose name the source
      fixes (`require` of `value-type.source-checks`, `compile` of an AOT
      fixture namespace). At most one cell of a child loads or compiles a
      given source namespace. Today this holds with no exception:
      `source-checks` has one caller, the one-cell
      `runtime-test/fallback-source-compatibility-test`, and each compile
      cell is the only cell of its child (its classpath holds its own
      temporary fixture source directory). The other check namespaces
      define no type when they load, so more than one cell can require
      them.
    - Slice 4 check: before the commit that converts a namespace, run
      `git grep -n -e 'binding \[\*ns\*' -e 'the-ns' -e 'create-ns'` on
      the check namespaces and inline forms that its cells run. Each match
      with a fixed name changes by this rule.
    - All check code is test code, so no cell keeps its own child for a
      class-name reason, and no rule-5 exception has this reason. If a
      conversion finds a case that this rule does not cover, record it in
      `implementation.md` and stop that conversion: it needs a design
      decision.
- **New-Class checks** (design "Shared runtime state").
  - Registry: `value-type.cell-registry`
    (`test/compatibility/value_type/cell_registry.clj`). It requires only
    `clojure.core` and uses Java 8 APIs, so a child loads it without
    `clojure.test`, `jvm-runner` or the recorder. The registry also works in
    the parent JVM. It is the child-safe cell support: the new-Class and
    absence checks below, and also the attempt axis (`attempt-count`,
    `attempts`, `twice`, `each-attempt`), expected values of an axis
    (`each`), the identity fact (`identity?`), `factory-symbol`,
    `class-name`, failure maps (`root`, `thrown`), the definition map
    (`defined`, `new-definition`) and child call forms (`check-form`).
    The new-Class and absence checks are two functions:
    - `new-class!` takes a value and returns true only when it is a Class
      that no earlier call in this JVM got; it adds the Class. State: one
      `defonce` synchronized identity set of Classes
      (`Collections/synchronizedSet` of `Collections/newSetFromMap` of an
      `IdentityHashMap`) for the JVM. It compares by identity, not by name:
      the hazard is an earlier Class with the same name. The set is
      synchronized because cell code defines types on more than one thread
      (`warning-checks/concurrent-definitions`).
    - `absent?` takes a namespace (symbol or Namespace), a simple type name
      and, optionally, a binary class name (default: the munged namespace
      name, `.` and the simple name). It returns true only when all three
      parts are absent: the simple name does not resolve in the namespace
      (import); `->Name` does not resolve in the namespace (factory); the
      class name does not load through `Class/forName` with
      `RT/baseLoader` (`ClassNotFoundException`). Model:
      `expansion-checks/constrained-discarded` (`:owner-absent?` is import
      and class name, `:factory-absent?` is `->Name`). A transfer cell gives
      the class name of its expansion (its producer namespace).
  - Definition map: one map for each type definition, or for each attempt
    when a cell repeats a definition. It holds the keys of that definition:
    - successful definition: `:new-class?` (the value of `new-class!` on its
      Class) and `:factory?`: the simple name resolves in its namespace to its
      Class, and `->Name` constructs an instance whose Class is identical to
      its Class. Its Class is the value that the definition returns when that
      value is a Class (`eval`, `load-string`, a function call, a transferred
      expansion). Otherwise (`compile`, `require` or `load` of a namespace), it
      is the Class that the simple name resolves to in its namespace after
      the operation. Its namespace is `*ns*` when the definition is
      evaluated (for example a `warning-checks` worker namespace), or the
      namespace that `compile`, `require` or `load` defines. Cell code calls `new-class!` exactly once for each
      successful definition, directly after the definition or operation
      returns.
    - definition that is expected to reject: `:absent?` (the value of
      `cell-registry/absent?`).
    `:expected` states `:new-class? true :factory? true`, or `:absent? true`.
    Thus the `:result` check covers the new-Class and absence checks; the
    harness adds no check of its own.
  - Key placement:
    - Where the current cell code returns one map for each definition or
      attempt, that map is the definition map: `transfer-checks/attempt`,
      `expansion-checks/attempt`, `warning-checks/define-types` (one map for
      each of the two types of a worker) and `concurrent-rejections`, the
      `field-transfer-checks` attempts, `identity-loader-checks/reject` and
      the result map of `stale-sibling-case` (the accepted `Holder`), the
      `source-checks` `:ordinary` and `:portable` maps, each
      `aot-checks/compile-fixture` strict attempt and its non-strict result,
      and each `validation-checks/check-fields` entry.
    - Where the current cell code returns one value for each attempt or
      outcome of one definition, and that value is not a map on success,
      the value becomes the definition map. A successful attempt gives a
      map with its current value under named keys, and `:new-class?` and
      `:factory?`. A rejected attempt keeps its failure map and gains
      `:absent?`. The check code that catches the failure adds `:absent?`;
      the shared `consumer-aot-checks/failure` does not change (AOT
      consumer cells use it and are exempt). Cases:
      - `field-policy-checks/evaluation`: each of its two `attempts` (one
        `EvaluatedField` definition each, in the cell namespace). Success:
        `{:constructions [<3 construction maps, as today>] :new-class?
        true :factory? true}`. Rejection: `(assoc (checks/failure t)
        :absent? (cell-registry/absent? *ns* 'EvaluatedField))`, thus
        `{:class c :message m :absent? true}`. `evaluation` makes its own
        attempts (try, catch); `attempts` stays as it is for `expansion`,
        which defines no type and gets no new key. The cell result stays
        `{:detail d :attempts [a0 a1]}`. Expected values in
        `field-capability-test/public-field-evaluation-policy-test`: the
        cell `:expected` is a predicate on the cell result. With a
        fallback reason and policy `error`: `(seq d)`, and `:attempts`
        equal to `[r r]` with `r` = `(assoc (checks/policy-rejection d)
        :absent? true)`. Otherwise (and `(seq d)` when there is a
        fallback reason): `:attempts` equal to `[s s]` with `s` =
        `{:constructions (vec (repeat 3 {:type expected-type :stored valid
        :rejections ["java.lang.NullPointerException"
        "java.lang.ClassCastException"]})) :new-class? true :factory?
        true}`.
      - `runtime-test/public-definition-test`: the `:outcome` of `Sample`
        is its definition map; the cell result has no `:definitions` key.
        The cell form calls `new-class!` on the Class that the `eval` of
        the definition returns. Success: `{:field 42 :equal? (= a b)
        :new-class? true :factory? true}`. `:factory?` replaces the first
        item of the current vector `[(= Sample (class a)) (.-x a) (= a
        b)]`; `:field` and `:equal?` name the other two items. Rejection:
        `{:chain [...] :absent? true}`; `:absent?` (from
        `cell-registry/absent?` on the cell namespace and `Sample`)
        replaces `:absent` (by `resolve`). The cell result stays
        `{:available? available? :outcome o}`. Expected `:outcome` for each
        policy: when `available?` is false and the policy is `error`,
        `{:chain ["clojure.lang.Compiler$CompilerException"
        "java.lang.Error"] :absent? true}`; otherwise (`silent`, `warn`, or
        `available?` true) `{:field 42 :equal? available? :new-class? true
        :factory? true}`.
    - Otherwise the cell result map gets the key `:definitions`: a map from
      the simple type name (symbol) to the definition map, for each
      definition with no map of its own:
      - `child-transfer-checks/consume`: `TransferChild` and `TransferParent`
        (the current map describes fields of both);
      - `child-transfer-checks/reject-replaced-child`: the result changes
        from the vector of attempts to `{:definitions {TransferChild ...}
        :attempts [...]}`; each attempt map is the definition map of one
        `TransferParent` attempt;
      - `field-aot-test/persisted-restricted-field-output-test`: `Leaf` and
        `Holder` ("Conversion order");
      - other inline cell forms with more than one definition and no map
        for each definition.
      A cell result that is not a map becomes a map with the current value
      under a key that names it (for example `:attempts`).
    - Each definition has one definition map, in one place. Thus
      `:new-class?`, `:factory?` and `:absent?` of one definition are in
      that map only.
    - Each definition map of a successful definition gets `:factory?`, also
      where the current code has no factory check (for example
      `TransferChild`, `TransferParent`, the `validation-checks`
      successes). A current factory check of another form becomes
      `:factory?`: by class name (`aot-checks/compile-fixture`), by the
      returned Class only (`warning-checks/define-types`), by the import only
      (`transfer-checks/attempt`, the inline form of
      `runtime-test/public-definition-test`), or under another key
      (`source-checks` `:factory`). `field-transfer-checks` keeps `:class?` for each
      construction (factory, direct, reflective) and gains `:factory?`. Its
      current `:new-class?` (by `identical?` to the previous attempt of the
      cell) changes to `new-class!`.
  - Current absence keys: each boolean absence key of a definition or of a
    discarded expansion becomes the one key `:absent?` from
    `cell-registry/absent?`. The new key has all parts of the old key, so it
    accepts no outcome that the old key rejects:
    - `:owner-absent?` and `:factory-absent?` (`child-transfer-checks`,
      `warning-checks/concurrent-rejections`,
      `expansion-checks/constrained-discarded` and
      `invalid-constrained-discarded`; expected values in
      `child-transfer-test`, `transfer-test` lines 110–111 and the
      `runtime-test` constrained tests) become `:absent?`;
    - `:absent` (inline form of `runtime-test/public-definition-test`, in
      its `:outcome` failure map; `validation-checks/check-fields`) becomes
      `:absent?` in the same map;
    - `:absent?` by import only, or by import and class name
      (`transfer-checks/absent?`, the private `expansion-checks/absent?`,
      `field-transfer-checks` (both names, `TransferredField` and
      `TransferredMixed`, as today), `identity-loader-checks/reject`) keeps
      its key and uses `absent?`;
    - `:mapped?` and `:loadable?` (`aot-checks/compile-fixture` strict
      branch) become `:absent?`.
    - A presence check `:absent? false` on a successful evaluation
      (`expansion-checks` `:evaluated` and `:source` phases and
      `warning-timing` `:evaluated`; expected in `runtime-test` lines 135 and
      162) becomes `:new-class? true :factory? true`. `:factory?` requires
      the import, so the new check accepts no outcome that `:absent? false`
      (import or class name present) rejects.
    - A discarded expansion makes no definition and no `new-class!` call.
      It keeps `:absent? true` from `absent?` (for example the
      `expansion-checks` `:bound`, `:unbound` and `:expanded` results).
    - Keys that report what a name resolves to
      (`identity-receiving-checks/strict-nested` `:owner` and `:factory`)
      describe expansions only and do not change.
    Each key change is a changed expectation. The commit that converts the
    namespace records it (Invariants).
  - Scope: a type definition is an evaluation of a `defvaluetype` or
    `deftype` form in the child: `eval`, `load-string`, `load` or `compile`
    of source, a transferred or fixed expansion, or the evaluation of a
    function form that contains the definition (each `eval` of the function
    form defines a new Class; each call of the function returns that Class).
    Each cell that evaluates a type definition makes the check, also in a
    one-cell child: the cells of the converted tests (Slice 4) and of the
    Slice 6 Scope 3 test. The Slice 6 runner-outcome fixture cells define no
    type.
    - Compile cells (`field-aot-test/persisted-restricted-field-output-test`,
      `aot-test/fallback-aot-compilation-test`) make the check. `compile`
      loads the fixture source and defines each Class in the child. After
      `compile`, the cell resolves each Class in the fixture namespace, calls
      `new-class!` once for it and computes `:factory?`. A strict compile
      attempt gives `:absent?`. Their other current checks (`:emitted?`,
      `:identity?`, `:field`, `:chain`, `c/inspect-output`) stay.
    - No check in cells that load saved classes from an artifact (AOT
      consumer cells, for example
      `aot-test/source-free-fallback-aot-loading-test` through
      `aot-checks/load-fixture`, and `aot-checks/invoke-runtime-fixture`):
      they evaluate no definition, and an AOT class is the same Class on each
      load or call, so the check fails on correct behavior. Their current
      keys (for example `:factory?` by class name in `load-fixture`) do not
      change.
  - Slice: Slice 3 adds the registry and the top-level require in the child
    form. Slice 4 adds the `new-class!` and `absent?` calls and the expected
    keys, one namespace at a time, in the code that the cells of that
    namespace run (check namespace or inline form), in the commit that
    converts the namespace. A check function that other test namespaces also
    call changes with all of its callers and their expected values in one
    commit. Today the only such function that defines types is
    `identity-loader-checks/public-stale-sibling` (also called by the
    in-process `identity-loader-test`, which shares
    `public-stale-sibling-expected`).
- **Cell outcomes.** `cell-harness/cell-outcomes` is a pure function. It
  takes the option map from `cell-child` and its `run-jvm` result, and
  returns a vector with one outcome for each cell, in input order:
  `{:context <cell :context> :pass? <boolean> :checks [...] :failure ...}`.
  - `:checks`: one map `{:check k :expected e :actual a :pass? b}` for each
    check of the cell: `:result` (`:actual`: the cell result), `:out` and
    `:err` (`:actual`: the `:out` and `:err` text of the entry). The expected
    values follow "Cell harness" (`:expected`) and "Cell-result function"
    (expected `*err*`).
  - When the child crashes (exit not 0), times out (`:exit :timeout`), has a
    `:read-error`, or returns no entry for the cell (result not a vector,
    or shorter than the cell count), the cell has only one check: a failing
    `:result` check with `:actual :no-result`.
  - `:failure`: nil when the cell passes. Else a map with the child `:exit`,
    and `:out` and `:err`:
    - when the child gives an entry for the cell: the `:out` and `:err` text
      of that entry (the output of the cell), and its `:thrown` if present;
    - when the child gives no entry for the cell: the process stdout and
      stderr of the child, and its `:read-error` if present.
  The function makes no assertion and no `cell!` record.
- **Cell-result function.** `cell-harness/assert-child` takes the option map
  from `cell-child` and its `run-jvm` result. It calls `cell-outcomes` and
  makes its cell assertions from that data: for each check one
  `clojure.test/do-report` with `:type` `:pass` or `:fail`, `:expected`,
  `:actual` and, for a failure, a `:message` with the `:failure` text. On the
  test thread, in input order, it makes:
  - support assertions:
    - every cell child: empty child-level stderr, in the context
      `(pr-str ["child stderr" java jvm-opts policy fixture-ns])`. The
      public function `assert-child-stderr` (option map from `cell-child`
      and `run-jvm` result) makes this assertion; `assert-child` and the
      Slice 6 tests call it;
    - a child with more than one cell: exit 0 and result count = cell count,
      in the context `(pr-str ["child" java jvm-opts policy fixture-ns])`.
    No cell `:context` uses these strings. With the `:context` rule, a label
    has support assertions or cells, not both.
  - for each cell, under its label: one `cell!` record, then the reports of
    its outcome checks (expected result; `*out*` empty; expected `*err*`).
    When the child crashes, times out,
    has a `:read-error` or returns no entry for the cell, the cell fails,
    and the failure shows exit code, stdout and stderr.
  - Expected `*err*` (in `cell-outcomes`): it finds the first cell with
    `:fallback?` true in the fixed order. In a `warn` child that cell expects
    exactly one line, `value-type: identity deftype fallback: ` and its
    `:warning` context. Each other cell, and each cell of an `error` or
    `silent` child, expects empty `*err*`. The converted tests do not state
    the `*err*` of each cell.
- **Slice 6 tests.** The tests for acceptance 4 and 5 build their fixture
  children with `cell-child` and run them with `run-jvms`. They do not call
  `assert-child` and make no `cell!` record. They assert inline on the
  returned data, so a failing fixture cell does not fail the suite. Thus
  their labels are support labels (cell count 0) and new labels. Each test
  calls `cell-harness/assert-child-stderr` once for each fixture child (the
  Scope 2 child-level stderr support assertion, "Cell-result function"). The
  fixture children write no process stderr: cell text goes to the cell
  writers, the crash and timeout markers go to process stdout, and
  `destroyForcibly` gives no output.
  - Scope 3 test (acceptance 4): three `:baseline` children (`warn`,
    `error`, `silent`), each with three or more fallback cells (source
    definitions through `eval` with no value support; each cell defines one
    type in its cell namespace). The test states the expected entry of each
    cell itself, and asserts on the entries of the `run-jvms` result, not
    through the harness `*err*` rule:
    - `warn`: the first cell `:err` is exactly one line,
      `value-type: identity deftype fallback: ` and its context; each later
      cell `:err` is `""`; each cell `:value` is
      `{:new-class? true :factory? true}`;
    - `error`: each cell catches the rejection of its definition and its
      `:value` is `{:absent? true :rejection r}` (`:absent?` from
      `cell-registry/absent?`). `r` has the form of the
      entry `:thrown` map: root class `java.lang.Error`, the root message,
      and the cause chain of a source compiler rejection
      (`Compiler$CompilerException` outermost). Each cell `:err` is `""`;
    - `silent`: each cell `:err` is `""` and each cell `:value` is
      `{:new-class? true :factory? true}`.
    It also asserts that `cell-outcomes` gives `:pass? true` for each cell:
    the harness rule agrees with the stated entries.
  - Runner-outcome tests (acceptance 5), on `cell-outcomes` data:
    - failing cell: a child of three cells; the middle cell prints a marker
      to `*out*` and `*err*`, then throws. Its outcome has `:pass? false`;
      its `:result` check `:actual` is `{:thrown ...}` with the fixture class
      and message; its `:failure` has `:exit` 0, `:out` and `:err` equal to
      the marker text that the cell printed, and the `:thrown` map. The other two cells have
      `:pass? true` and `:failure` nil.
    - crashing child: a child of two cells; the second cell writes and
      flushes a marker line to `System/out`, then calls `(System/exit 3)`.
      Each cell has `:pass? false`, one `:result` check with `:actual
      :no-result`, and a `:failure` with `:exit` 3, `:out` that contains the
      marker, and `:err` `""`.
    - timed-out child: a child of two cells, explicit `:timeout-ms` 2000;
      the first cell writes and flushes a marker line to `System/out`, then
      sleeps past 2000 + 10000 × (2 − 1) ms. Each cell has `:pass? false`,
      one `:result` check with `:actual :no-result`, and a `:failure` with
      `:exit :timeout`, `:out` that contains the marker, and `:err` `""`.
- **Warn-guard evidence.** Error-policy fallback cells are the guard evidence.
  During conversion, verify for each grouped warn fallback cell that a
  matching error cell exists in the same namespace (same executable, JVM
  options, source form, path); add a new label where no match exists, or keep
  the warn cell in its own child under rule 5 and record it as an exception.
- **Conversion order.** Convert the largest matrices first for early time
  measurement: `child-transfer-test`, `field-transfer-test`,
  `public-field-aot-test`, then the rest of the cross-JVM tests, then the
  mixed tests (`transfer-test`, `aot-test`), then parent-dependent
  (`runtime-test`). Every cell child uses `cell-child` and `assert-child`.
  AOT cell children keep one cell for each child (rules 4–5), and their
  current process-`:err` checks become cell `*err*` checks. Splits (for
  example `transferred-expansion-test`, 1 → 4 cells) follow the pass-9 model:
  k cells, label maps to all k cell labels, first fallback cell keeps the
  current context. Cell forms that `prn` today change to return their value.
  A child that compiles as its checked case becomes a one-cell child whose
  cell compiles: `field-aot-test/persisted-restricted-field-output-test` uses
  `cell-child` with the `:valhalla` executable and options, policy `error`,
  and the classpath of `aot-fixture/classpath-with` the fixture source
  directory. Its cell `:form` is the compile form (new
  `aot-fixture/compile-form`, which `compile-clojure` also uses), then the
  new-Class checks of `Leaf` and `Holder` ("New-Class checks", compile
  cells; factory arguments `(->Leaf nil)` and `(->Holder leaf nil 42 2.5)`).
  The cell returns `{:output (c/inspect-output <output path>) :definitions
  {Leaf ... Holder ...}}`. `:expected` is a predicate: the three current
  checks on `:output`, and `:definitions` equal to `{Leaf {:new-class? true
  :factory? true} Holder {:new-class? true :factory? true}}`. It does not call
  `compile-producer`, so the current exit and empty-stderr checks become the
  `:no-result` rule of "Cell outcomes" and the cell and child-level stderr
  checks; the source is
  not deleted (no later child loads the output). `:context` is empty: the
  label stays `["AOT preserves native storage and rewrites persisted
  companions"]`. `aot-test/fallback-aot-compilation-test` gets the same form
  with its `aot-checks/compile-fixture` call as the cell form. Its expected
  result changes: under `error`, each of the 2 attempts is `{:chain [...]
  :emitted? false :absent? true}` (was `:mapped? false :loadable? false`);
  otherwise `{:emitted? true :identity? true :field 42 :new-class? true
  :factory? true}` (`:factory?` was a class-name comparison).
  Every label change and expectation change is recorded in
  `implementation.md` per the Invariants.
- **Cross-JVM suite.** New class path root `test/cross-jvm/`, alias
  `:cross-jvm-test` (`--dir test/cross-jvm`, also adds `test/compatibility`
  for shared support). Moved namespaces keep their names. The cross-JVM tests
  of `identity-receiving-test` move to the new namespace
  `value-type.identity-receiving-cross-jvm-test`
  (`test/cross-jvm/value_type/identity_receiving_cross_jvm_test.clj`), with
  the private `run-check` helper (only these tests use it). Their Var names do
  not change; their labels map from `value-type.identity-receiving-test/<name>`
  to `value-type.identity-receiving-cross-jvm-test/<name>`.
- **Owner parent: Java 8** (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"`). Reason:
  cross-JVM children take executable, options and classpath from `:baseline`
  and `:valhalla`, and the tests read no parent-derived key. Thus each parent
  gives the same children, labels and evidence; the parent only runs test
  support and waits. Java 8 is the baseline runtime of the project and needs
  no preview option. Acceptance 3 already requires a limit-1 run of the Java 8
  `:fallback-test` command, so both limit-1 runs use one parent. Consequence:
  the runner, harness and recorder use only Java 8 APIs.
- **Tooling.** `quality.clj` `maintained-paths` throws on missing paths, so
  `test/cross-jvm` is added in the same commit that creates the directory.
  The `release.yml` `cross-jvm` step pair sits next to the `fallback-*` pairs,
  before `Test build` (preserve runs before the next suite clears
  `.scry-results/`).

## Risks

- **Unchecked cell text.** Tests with no `:err` check today may emit other
  text (for example "already refers to" warnings). The new one-line rule fails
  them. Mitigation: fix in test code only; budget time per namespace.
- **Timeout and limit values unmeasured.** The 10 s per extra cell and default
  limit 4 are estimates. If measurement shows a problem, change `design.md`
  (recorded in pass 1 notes).
- **25% total-time target.** Tests run one at a time; parallelism is only
  within a test (`run-jvms`). AOT tests gain only parallelism and the run-once
  suite. If the target is missed at limit 4, profile the remaining child
  starts before changing scope.
- **Missing `:kind`.** A producer or compile child without `:kind` counts as a
  cell child in the first record. Mitigation: the recorder commit greps all
  `run-jvm` callers; the first-record summary is checked against the known
  counts (for example 72 consumers and 1 producer in
  `child-transfer-test/receiver-child-replacement-test`).
- **Compile in a cell.** `compile` in a cell runs under the per-cell binding
  frame (own `LOADER`, `*ns*`). This is not verified yet. Check it with the
  `field-aot-test` conversion and record the result in `implementation.md`.
- **Stronger absence keys.** `cell-registry/absent?` adds the class-name or
  factory part where the current key does not have it (for example
  `child-transfer-checks`, `validation-checks`, `identity-loader-checks`).
  A rejection after the Class is defined fails the new key. The design
  requires that part ("Shared runtime state"), so such a failure is a
  finding: record it in `implementation.md` and do not weaken the key.
- **Release job time.** `timeout-minutes: 60` must cover the four commands
  after the work; check with the measured times.
- **Hidden shared state in grouped cells.** The new-Class checks and fixed
  cell order mitigate; any cell that cannot be isolated stays in its own child
  under rule 5 and is listed in `implementation.md`.

## Slice order

1. Recorder and first record (acceptance 1): recorder commit, three parent
   runs, summaries in `implementation.md`.
2. Runner: concurrency limit, invalid-value load failure, `run-jvms`,
   timeout rule.
3. Cell harness, new-Class registry and cell-result function; child-level
   support assertions.
4. Test conversion, namespace by namespace, with new-Class checks, label
   mapping, expectation records and rule-5 exception list.
5. Cross-JVM suite split: `test/cross-jvm/`, alias, `identity-receiving-test`
   split, quality-tooling path updates (same commit as the directory).
6. Permanent tests for acceptance 4 and 5 in `test/cross-jvm/`.
7. Documentation (Scope 6) and release workflow (Scope 7).
8. After record, acceptance verification, mementum memory update.
