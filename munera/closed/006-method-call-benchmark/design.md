# 006 Method-call benchmark

## Intent

Measure the method call performance of `defvaluetype` against
`deftype` with the same body, on the Valhalla JDK. Record the results
in a new design document. This gives evidence for the success
criterion in `ramora/defvaluetype-design/purpose.md`: "Method call
performance is equal to a `deftype` with the same body."

The task measures and records. It does not make a pass/fail gate, and
it does not change `src/`.

When a choice is not clear, choose the smaller benchmark that still
gives a fair comparison.

## Problem

The performance quality has a structural check only:
`qualities.md` gives the fitness condition "method bytecode is the
same as `deftype`", and the transform tests check that methods pass
through unchanged. No measurement exists. Assertion A-21 ("D2
intendedToImprove compatibility, performance") is `intended`. The JVM
treats value objects differently (no identity, trusted final fields,
scalarization), so equal bytecode does not prove equal speed.

## Context

- Design: `ramora/defvaluetype-design/purpose.md` (success criteria,
  priority 4), `qualities.md` (performance target),
  `assertions.md` (A-21), `decisions.md` (D2).
- Project: `deps.edn` has the aliases `:test` and `:nrepl`. The test
  run (`clojure -M:test -m scry.cli`) runs only namespaces under
  `test/` whose names end in `-test`
  (`mementum/memories/scry-pass-true-with-zero-tests.md`). Select the
  JDK with `JAVA_CMD`
  (`mementum/memories/valhalla-jdk-selection-java-cmd.md`).
- Tool: criterium `org.hugoduncan/criterium` `0.5.245-ALPHA` from
  Clojars (the latest version, an alpha; the API can change).
  `criterium.bench/bench` measures an expression and prints a report.
  `criterium.bench/last-bench` returns the data of the last run.
- Facts checked for this design on JDK `27-jep401ea3`, Clojure 1.12.6,
  criterium `0.5.245-ALPHA`, macOS arm64 (Apple M5 Max):
  - criterium loads and runs with `--enable-preview` and
    `defvaluetype` classes.
  - With no blackhole JVM options, criterium prints a warning and uses
    a runtime blackhole (about 3 ns per call). The options
    `-XX:+UnlockExperimentalVMOptions` and
    `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`
    remove the warning.
  - `(bench (norm p))` on one instance `p` that the expression
    captures: about 3.9 ns for `deftype` and about 0.12 ns for
    `defvaluetype`. A time below one CPU cycle means that the JIT
    folded or hoisted the call. The fields of a value class are
    trusted final fields, so the JIT can treat the captured instance
    as a constant. This is a measurement artifact, not a method call
    speed.
  - Over an array of 1024 different instances, the same protocol call
    and a typed field access give times of the same order for both
    kinds. Thus each case must run over many different instances.
  - A loop that creates an instance and reads one field allocates
    about the same per call for both kinds (escape analysis removes
    the allocation for both).
  - The median elapsed time of one sample of the last run is at
    `(-> (last-bench) :data :stats :stats :elapsed-time :median)`. A
    sample is a batch of evaluations; the batch size is at
    `(-> (last-bench) :data :stats :batch-size)`. The time of one
    evaluation is the sample median divided by the batch size. These
    paths are internal to an alpha library.

## Scope

In scope:

1. A `:bench` alias in `deps.edn`: extra path `bench`, extra
   dependency criterium `0.5.245-ALPHA`, JVM options
   `--enable-preview` and the two blackhole options.
2. A benchmark namespace `value-type.bench` in `bench/`. It runs with
   `clojure -M:bench -m value-type.bench`. It runs the cases in
   "Cases" and prints the results (see "Output").
3. A new design document
   `ramora/defvaluetype-design/benchmark.md` with the results (see
   "Results document").
4. Documentation updates (see "Documentation").

## Out of scope

- JMH, or a second benchmark tool.
- A pass/fail gate, a threshold in the test suite, or a CI job.
- Changes to `src/`. When a result is slower, record it in
  `benchmark.md` and `implementation.md`, and propose a task. Do not
  fix it in this task.
- Flattened arrays, null-restricted fields, and allocation tracking
  with the criterium native agent.
- Other JDK builds, other machines, and a comparison with a `javac`
  value class.
- Benchmark runs in the test suite (`scry.cli`).

## Cases

Each case has two types with the same name stem, the same fields and
the same method bodies: one `deftype`, one `defvaluetype`. The fields
are `^long x` and `^long y`.

Each case runs over an `Object` array (`object-array`) of `n`
instances, made before the measurement. `-main` takes an optional
argument `n`; the default is 1024. Element `i` (`0 <= i < n`) has
`x = i` and `y = 2i`, the same for both kinds. The code reads an
element with a `^T` hint, where `T` is the measured type of the
case, for example `(.-x ^T (aget ^objects a i))`.

The method of the method call cases:

- `interface`: `(definterface INorm (^long norm []))`. The types of
  the case implement `(norm [this] (+ x y))`. The return type is the
  primitive `long`.
- `protocol`: `(defprotocol Norm (norm [this]))`. The types of the
  case implement `(norm [this] (+ x y))` inline. The return type is
  `Object` (a boxed `Long`).
- The two return types are different. This is accepted: each ratio
  compares the two kinds in one case, and the two kinds of a case have
  the same return type.

One measurement is one pass over the array. The pass is a `loop` over
`i` that adds the value of each element to a `long` sum and returns
the sum. criterium blackholes the return value, so the JIT cannot
remove the work. The reported time per call is the median time of one
pass divided by the array length.

Each pair (case, kind) has its own pass function, thus its own call
site, and its own arrays. The arrays of a pair hold only instances of
its `T`. The two pass functions of a case differ only by `T`. A macro
can make the pass functions. Rationale: one pass function shared by
the two kinds has a call site that sees two classes. The JIT profile
is then bimorphic, and the result depends on the kind that runs
first.

| Case | Value added to the sum for element `e` | Kind |
|---|---|---|
| `interface` | `(.norm ^INorm e)` | criterion |
| `protocol` | `(long (norm e))` | criterion |
| `field` | `(.-x ^T e)` | information |
| `identical` | `(if (identical? e e2) 1 0)` | information |
| `allocate` | `(.-x ^T (T. (.-x ^T e) (.-y ^T e)))` | information |

- `identical`: `e2` is element `i` of a second `Object` array, made
  separately with the same field values as the first array.
- `allocate`: the constructor form `(T. x y)`, not the factory
  `(->T x y)`, so that the case has no function call.
- Criterion cases test the success criterion: the same method body,
  called on an instance.
- Information cases do not test the success criterion. They show
  related costs and known differences. `field` shows the cost of a
  field load with no method call. `identical?` is a reference compare
  for `deftype` and a field compare for a value class (a semantic
  difference, see `qualities.md`). `allocate` shows the effect of
  escape analysis.

## Decisions

### Many instances, not one captured instance

- Choice: each case runs over an array of 1024 instances with
  different field values.
- Rationale: a captured single value object lets the JIT fold the call
  (observed: 0.12 ns against 3.9 ns). Different instances in an array
  make the JIT load the fields for each call, for both kinds. 1024
  elements fit in the CPU cache, so memory speed does not dominate.
- Rejected: criterium argument generation (`criterium.arg-gen`). It
  adds a second alpha dependency and `test.check`.

### The array type

- Choice: all cases use an `Object` array, with a `^T` hint on the
  element.
- Rationale: both kinds use reference arrays (flattened arrays are out
  of scope), so each case compares only the code in "Cases". A `^T`
  hint on an element of an `Object` array gives no reflection
  (checked: `(.-x ^T (aget ^objects a i))`, Clojure 1.12.6).
- Rejected: a typed array (`into-array` of the class). On the Valhalla
  JDK, a typed array of a value class can be flattened (`into-array`
  of a value class gives the array class `VPt/1` in the probe). Then
  the case also compares a flat array with a reference array.

### Separate `bench/` path and alias

- Choice: the namespace is in `bench/`, used only by the `:bench`
  alias.
- Rationale: the test run stays fast, and criterium is not a test
  dependency. The name `value-type.bench` does not end in `-test`, so
  `scry.cli` does not run it in any case.

### Output

- Choice: the namespace prints the array length `n` and, for each
  case, the median time per call for each kind, and the ratio
  `defvaluetype / deftype`. It prints a Markdown table and the same
  data as EDN. The criterium
  report of each run is not printed.
- Classification for the results document (not a gate): a criterion
  case is "equal" when the ratio is in `[0.9, 1.1]`, "faster" when it
  is below, and "slower" when it is above.
- Rationale: the table goes into `benchmark.md` with no change. The
  EDN keeps the numbers for a later compare. One function reads the
  median from `last-bench`, because the path is internal to an alpha
  library.

### Time limit

- Choice: each measurement has a time limit (criterium
  `:limit-time-s`). The full run takes less than 5 minutes.
- Rationale: the run must be easy to repeat on each new early-access
  build.

## Invariants

- The two types of a case differ only by `deftype` and
  `defvaluetype`.
- `value-type.bench` loads with no reflection warnings. A reflective
  call would dominate the time.
- The two pass functions of a case differ only by `T`.
- Each case measures only the pass loop and the value in "Cases".
  The arrays and their instances are made before the measurement.
- `src/` and `test/` do not change.

## Results document

`ramora/defvaluetype-design/benchmark.md` contains:

- The environment: JDK build (`java.vm.version`), OS and CPU, Clojure
  version, criterium version, JVM options, date.
- The command.
- The table that the namespace printed.
- The classification of each criterion case, and a short
  interpretation: the result for the success criterion, and the
  known measurement limits (one machine, one JDK build, an alpha
  tool, the boxed return value of the `protocol` case).
- The fold check (Verification 3): one line for each criterion case
  with the ratio of each kind.
- A statement that the results are valid only for this environment,
  and that each new early-access build needs a new run.

## Documentation

- `ramora/defvaluetype-design.md`: add "Benchmark" to "Sections", and
  update "Status".
- `ramora/defvaluetype-design/assertions.md`:
  - A new assertion A-28, a quality relation of the metamodel:
    status `observed`, subject `defvaluetype`, predicate `exhibits`,
    object "method call time / `deftype` time for each criterion
    case" with the ratio of `interface` and of `protocol`, basis
    `benchmark.md`, confidence `medium` (one machine, one JDK build,
    an alpha tool). The scope is the environment in `benchmark.md`.
    The table has no scope column, so the basis cell names the JDK
    build, as in A-25 to A-27.
  - A-21: add A-28 and `benchmark.md` to the basis. The status comes
    from the classification of the criterion cases. All "equal" or
    "faster": keep `intended`. One or more "slower": `contested`.
    `contested`, not `violated`: the result is for one environment,
    and it contradicts only the performance part of A-21.
- `ramora/defvaluetype-design/qualities.md`: the performance row
  names `benchmark.md` in its fitness condition.
- `ramora/defvaluetype-design/embodiment.md`, "Project artifacts":
  the `:bench` alias (criterium, blackhole JVM options) and
  `bench/value_type/bench.clj`.
- `ramora/defvaluetype-design/decisions.md` D11: the method-call
  benchmark (`value-type.bench`). It is manual and not a gate. The
  results are in `benchmark.md`.
- `ramora/defvaluetype-design/risks-and-evolution.md`, "Assumptions
  (verified)": run the benchmark again on each new early-access
  build, the same rule as for the spike assumptions.
- README, "Development": a "Run the benchmark" section with the
  command and the `JAVA_CMD` rule.
- `mementum/state.md` and `munera/plan.md`.

## Verification

1. `clojure -M:bench -m value-type.bench` with `JAVA_CMD` set to the
   Valhalla `java` exits 0, and prints the table and the EDN with each
   case in "Cases".
2. The run prints no reflection warning and no criterium blackhole
   warning.
3. No folded work. The check run is
   `clojure -M:bench -m value-type.bench 2048`. For each criterion
   case and each kind, the median time of one pass with 2048
   instances is between 1.5 and 2.5 times the median time of one pass
   with 1024 instances. A time that does not grow with the array
   length means that the JIT removed the work; then change the case,
   not the result. The ratios go into `benchmark.md` (see "Results
   document"). The full check run is recorded in `implementation.md`.
4. `clojure -M:test -m scry.cli` still passes (exit 0).

## Acceptance criteria

1. The checks in "Verification" pass on the Valhalla JDK.
2. `deps.edn` has the `:bench` alias as in "Scope", and the `:test`
   alias does not change.
3. `benchmark.md` has the content in "Results document", from one run
   of the command in this task.
4. Each type pair in `value-type.bench` differs only by `deftype` and
   `defvaluetype`, and the two pass functions of a case differ only by
   `T` (checked by review).
5. `src/` and `test/` have no change.
6. The documents in "Documentation" agree with this design.
