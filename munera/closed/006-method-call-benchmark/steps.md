# Steps

## Slice 1: :bench alias

- [x] Add a `:bench` alias to `deps.edn`: `:extra-paths ["bench"]`,
  `:extra-deps` criterium `org.hugoduncan/criterium` `0.5.245-ALPHA`,
  `:jvm-opts` `--enable-preview`, `-XX:+UnlockExperimentalVMOptions`,
  `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`.
  Do not change the `:test` alias.
- [x] Check with `JAVA_CMD` set to the Valhalla java:
  `clojure -M:bench -e "(require 'criterium.bench)"` exits 0.

## Slice 2: value-type.bench namespace

- [x] Create `bench/value_type/bench.clj` with ns `value-type.bench`,
  `(set! *warn-on-reflection* true)`, requires for `value-type.core`
  and `criterium.bench`.
- [x] Define `(definterface INorm (^long norm []))` and
  `(defprotocol Norm (norm [this]))`.
- [x] Write a macro that defines a type pair (one `deftype`, one
  `defvaluetype`) with fields `^long x` `^long y` and a given body, so
  the two types of a pair differ only by the defining macro.
- [x] Define the three type pairs: `interface` pair implementing
  `INorm` `(norm [this] (+ x y))`, `protocol` pair implementing `Norm`
  `(norm [this] (+ x y))` inline, and one plain pair (no method) that
  the `field`, `identical` and `allocate` cases share.
- [x] Write an array builder: `object-array` of `n` instances of a
  type, element `i` has `x = i`, `y = 2i`.
- [x] Write a macro that defines a pass function for a (case, kind):
  `loop` over the array index, add the case value to a `long` sum,
  return the sum. Case values per design: `(.norm ^INorm e)`,
  `(long (norm e))`, `(.-x ^T e)`, `(if (identical? e e2) 1 0)` with
  `e2` from a second array, `(.-x ^T (T. (.-x ^T e) (.-y ^T e)))`.
  One pass function per (case, kind); the two of a case differ only
  by `T`.
- [x] Write a median helper (sample median / `:batch-size`, see implementation.md) from
  `(-> (criterium.bench/last-bench) :data :stats :stats :elapsed-time :median)`.
- [x] Write a run function: for each (case, kind), build the arrays
  before measurement, run `criterium.bench/bench` on the pass with
  `:limit-time-s` (full run < 5 min) and the criterium report
  suppressed, collect median per pass and per call (median / n).
- [x] Write output: print `n`, a Markdown table (case, deftype time
  per call, defvaluetype time per call, ratio defvaluetype/deftype),
  and the same data as EDN.
- [x] Write `-main`: parse optional string arg `n` to a long (default
  1024), run all cases, print output.
- [x] Load the namespace with reflection warnings on and fix any
  warning.

## Slice 3: Measurement and verification

- [x] Run `time clojure -M:bench -m value-type.bench` (Valhalla
  `JAVA_CMD`): exits 0, prints the table and EDN with all five cases,
  no reflection warning, no criterium blackhole warning
  (Verification 1, 2). Keep the printed table and EDN for
  `benchmark.md` (Slice 4). The wall time (`real`) is less than 5
  minutes (design "Time limit").
- [x] Run `time clojure -M:bench -m value-type.bench 2048`. The wall
  time (`real`) is less than 5 minutes. If the record run or this run
  takes 5 minutes or more, decrease `:limit-time-s` (all cases) and
  do both runs again. Record the wall times of the final record run
  and the final fold check in `implementation.md` (not in
  `benchmark.md`). For each
  criterion case and kind, check the median pass time at 2048 is
  1.5–2.5× the pass time at 1024 (pass time = time per call × n). Any
  ratio outside `[1.5, 2.5]` is a failed check (≤ 1.0: no growth;
  `(1.0, 1.5)`: part of the work hoisted; > 2.5: cache, GC or noise).
  Find the cause, then change the case, or the run setup for all
  cases (for example `:limit-time-s`, with the full run < 5 minutes;
  or stop a background load on the machine). Then do the record run
  (1024) and this fold check (2048) again. Do not repeat unchanged
  runs to get a pass. `benchmark.md` uses only the record run and the
  fold check of the final code. Record the full check run and the
  ratios in `implementation.md` (Verification 3), and record each
  earlier failed fold check there with its cause and change (not in
  `benchmark.md`).
- [x] Run `clojure -M:test -m scry.cli`: exit 0, `:test` behaviour
  unchanged (Verification 4).
- [x] Confirm `git status`: no change in `src/` or `test/`.

## Slice 4: Results document and documentation

- [x] Write `ramora/defvaluetype-design/benchmark.md`: environment
  (`java.vm.version`, OS, CPU, Clojure version, criterium version,
  JVM options, date), the command, the printed table, the printed
  EDN of the record run in a code block, the classification of each criterion case (equal [0.9, 1.1] / faster /
  slower), a short interpretation with the known measurement limits,
  the fold-check ratios (one line per criterion case, each kind), and
  the validity statement (this environment only; rerun on each new
  early-access build).
- [x] `ramora/defvaluetype-design.md`: add "Benchmark" to "Sections"
  and update "Status".
- [x] `ramora/defvaluetype-design/assertions.md`: add A-28 (status
  `observed`, subject `defvaluetype`, predicate `exhibits`, object
  "method call time / `deftype` time for each criterion case" with
  the `interface` and `protocol` ratios, basis `benchmark.md` naming
  the JDK build, confidence `medium`).
- [x] `ramora/defvaluetype-design/assertions.md`: update A-21: add
  A-28 and `benchmark.md` to the basis; status `intended` if all
  criterion cases are equal/faster, `contested` if any is slower.
- [x] `ramora/defvaluetype-design/qualities.md`: the performance row
  names `benchmark.md` in its fitness condition.
- [x] `ramora/defvaluetype-design/embodiment.md`, "Project
  artifacts": add the `:bench` alias (criterium, blackhole JVM
  options) and `bench/value_type/bench.clj`.
- [x] `ramora/defvaluetype-design/decisions.md` D11: add the
  method-call benchmark (`value-type.bench`), manual, not a gate,
  results in `benchmark.md`.
- [x] `ramora/defvaluetype-design/risks-and-evolution.md`,
  "Assumptions (verified)": run the benchmark again on each new
  early-access build.
- [x] README "Development": add a "Run the benchmark" section with
  the command and the `JAVA_CMD` rule.
- [x] If any criterion case is "slower": record in `benchmark.md` and
  `implementation.md`, and propose a new task (do not change `src/`).
  - Interface is slower (1.14); protocol is equal (1.00). Proposed
    follow-up: investigate the interface-call slowdown and measurement
    stability on this JDK. No new task is created.
- [x] `mementum/state.md`: in "Status", add that the benchmark is
  done (task 006, `value-type.bench`, the command
  `clojure -M:bench -m value-type.bench`, results in
  `ramora/defvaluetype-design/benchmark.md`, the A-21 status from the
  run: `intended` or `contested`). In "Next", replace the 006 item
  (it starts "The benchmark task is designed") with one bullet: no
  next task is created; the user selects the next task. If a
  criterion case is "slower", the bullet names the proposed follow-up
  task (item above) as a candidate. Keep the bullet for
  the other adjacent task (nested `defvaluetype` in a method body).
- [x] `munera/plan.md`: no edit in this task. Check that the entry
  for 006 agrees with `design.md` "Intent". The move of the task to
  `munera/closed/` removes the entry (munera protocol).

## Implementation review

- [x] `defpass` loads `e2` (`(aget b i)`) in each pass function, but
  only `identical` uses it. Each other case also measures a second
  array load and its range check (design invariant "each case
  measures only the pass loop and the value in Cases"). Bind `e2` and
  read `b` only in the `identical` passes (for example, a pass
  without `b` for the other cases, and make `b` in `measure` only
  when the case needs it). Then do the record run and the fold check
  again, and update `benchmark.md`, A-28 and `implementation.md` from
  the new final pair of runs.
- [x] Remove `(:gen-class)` from the `value-type.bench` ns form. The
  namespace runs with `clojure -m` and is not AOT compiled, so
  `:gen-class` has no effect.
- [x] Complete the documentation response to the new `interface`
  ratio 1.14 in `benchmark.md`. A-21 in `assertions.md` still says
  `intended`; the design index and `mementum/state.md` still report
  equal method-call times. Set A-21 to `contested` and make those
  summaries agree with the recorded classification. Propose the
  required follow-up task for the slower criterion case, record it
  in `implementation.md` and the state "Next" section, and correct
  the Slice 4 note "Not applicable: both criterion cases are equal".
  The existing review item already covers A-28 and the new run record;
  do not add another run solely to obtain an equal classification.
- [x] Qualify the claims in `benchmark.md` "Interpretation" that
  run-to-run variance is about ±15 % and that `field` and `allocate`
  compile to the same work. The saved runs do not establish a
  variance estimate or include generated-code evidence. Describe
  only the observed differences between these runs, and state escape
  analysis as a possible explanation unless code inspection supports
  the stronger claim. No extra benchmark tool is needed.

## Code-shaper review

- [x] Validate `n` before `value-type.bench/run` starts a measurement.
  It must be a positive integer within the JVM array-length range.
  `run 0` currently completes all ten measurements and returns `##Inf`
  times and `##NaN` ratios; a negative `n` builds empty arrays and
  divides their pass times by a negative number. Reject invalid input
  with a clear error that names the allowed range. Make `-main` report
  an invalid numeric argument clearly (`parse-long` can return nil),
  and document the range in the run docstring and README. Verify zero,
  negative, non-numeric and out-of-range inputs fail before measurement,
  while the default and 2048 remain valid. Keep these checks outside
  the measured pass functions.
  - Verified direct and CLI rejection, range boundaries, and full runs
    with the default and 2048. Validation is outside the measured passes.

- [x] Make the measurement traversal in `value-type.bench/run` eager
  with `mapv`, instead of `vec` around a lazy `for`. The body runs
  measurements and reads criterium state; it is not a lazy data
  transformation. This also follows the Clojure standard against
  unnecessary lazy sequences. Keep case order, deftype-before-value-type
  order, validation before measurement, and the result maps unchanged.
  Verify that a smoke run returns five result maps in the current order.
  Do not replace the saved benchmark results for this collection change.

- [x] Remove the unnecessary lazy sequence in `instances` in
  `bench/value_type/bench.clj`. `object-array` consumes the lazy `map`
  only to copy its elements into an array. Allocate an `Object` array
  of length `n` and fill it directly, without an intermediate sequence
  or vector. This follows the Clojure standard and avoids intermediate
  allocation during setup. Keep element `i` at `x = i`, `y = 2i`,
  separate arrays for each kind, and setup outside measurement. Verify
  array class, length and field values for all six constructors, and
  the sums of all ten pass functions on a small input. Keep the saved
  benchmark results; this change does not alter the measured passes.
