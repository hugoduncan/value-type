# Design steps

- [x] Architecture: "Documentation" does not record the benchmark in
  the architecture sections that list project artifacts and
  verification elements. The `:bench` alias, the `bench/` path and
  `value-type.bench` are new project artifacts. The benchmark is a new
  verification element for the performance quality (precedent: task
  005 added its corpus to D11). Add to "Documentation":
  - `ramora/defvaluetype-design/embodiment.md`, "Project artifacts":
    the `:bench` alias (criterium, blackhole JVM options) and
    `bench/value_type/bench.clj`.
  - `ramora/defvaluetype-design/decisions.md` D11: the method-call
    benchmark (`value-type.bench`), manual, not a gate, results in
    `benchmark.md`.
  - `ramora/defvaluetype-design/risks-and-evolution.md`: run the
    benchmark again on each new early-access build (the same rule as
    the spike assumptions).
  - Done: "Documentation" lists `embodiment.md`, D11 and
    `risks-and-evolution.md`.
- [x] Architecture: the A-28 and A-21 updates do not follow the
  assertion metamodel (`~/projects/hugoduncan/architecture/ramora/meta-model.md`).
  A-28 has no predicate and no confidence. A-21 stays `intended` for
  each result, but an observation that contradicts an intended
  assertion must change its status. State in "Documentation":
  - A-28: a quality relation of the metamodel, for example
    `defvaluetype` `exhibits` "method call time / `deftype` time per
    criterion case", with the environment as scope, and a confidence.
  - A-21: the status after the run, from the classification of the
    criterion cases. All "equal" or "faster": keep `intended` and add
    the basis. One or more "slower": `contested` (or `violated`), with
    `benchmark.md` as the basis.
  - Done: A-28 has subject, predicate `exhibits`, scope in the basis
    cell, confidence `medium`. A-21: `intended` or `contested`.
- [x] Architecture: the evidence that the measurement is valid is not
  in the architecture record. Verification 3 (the 2048 / 1024 fold
  check) goes only to `implementation.md`, but `benchmark.md` is the
  basis of the `observed` assertion A-28, and the fold trap is the
  main known measurement risk. Precedent: `verification.md` keeps the
  evidence next to each result. In "Results document", add one line
  per criterion case with the fold-check ratio (or one summary line
  that all ratios are in `[1.5, 2.5]`). The full check run can stay in
  `implementation.md`.
  - Done: "Results document" has one fold-check line for each
    criterion case. Verification 3 names the check command.
- [x] Ambiguity: "1024 different instances" does not give the field
  values. For a value class, two instances with equal fields are the
  same value, so "different" must mean different field values. State
  the values, for example element `i` has `x = i` and `y = 2i`, the
  same for both kinds.
  - Done: element `i` has `x = i`, `y = 2i`.
- [x] Ambiguity: the method and the pass loop are not specified.
  State:
  - The method: name, signature and body, the same in the
    `definterface` and the protocol (for example `(norm [this])` with
    body `(+ x y)`). State the return type: the `definterface` method
    can return a primitive `^long`, but a protocol method returns
    `Object` (boxed). Say if this difference is accepted or if the
    interface method also returns `Object`.
  - The call form for each case (for example `(.norm ^IT e)` and
    `(norm e)`).
  - How one pass consumes the result of each element so that the JIT
    cannot remove it, for example a `long` sum that the measured
    expression returns (criterium blackholes the return value).
  - Done: `INorm` returns `^long`, protocol `Norm` returns `Object`
    (accepted). Call forms and the `long` sum loop are in "Cases".
- [x] Ambiguity: the information cases are not specified.
  - `identical`: the source of `e1` and `e2` is not given. For
    example, a second array of 1024 instances with the same fields as
    the first array, made separately, and compare element `i` of each
    array.
  - `allocate`: the constructor form is not given (`(T. x y)` or the
    factory `(->T x y)`, which adds a function call), nor the field
    that is read.
  - Done: `e2` from a second array; `allocate` uses `(T. x y)` and
    reads `x`.
- [x] Inconsistency: "The array type" contradicts "Out of scope" and
  its own rationale.
  - "Out of scope" excludes flattened arrays, but the `field` case
    uses a typed array, which can be flattened for the value class
    (`VPt/1`). Then the `field` case compares a flat array with a
    reference array, and not only the body in "Cases" (see
    "Invariants").
  - The rationale "the typed field access needs a typed array to have
    no reflection" is not correct. `(.-x ^T (aget ^objects a i))`
    compiles with no reflection warning (checked: Clojure 1.12.6,
    `*warn-on-reflection*` true, `deftype` with `^long` fields).
  - Fix: use an `Object` array for all cases, with a `^T` hint on the
    element. Remove the typed array from "The array type" and from
    "Results document" ("the array type of the `field` case").
  - Done: all cases use an `Object` array; typed array is rejected
    in "The array type".
- [x] Inconsistency: `field` is a criterion case, but it has no method
  call. "Criterion cases test the success criterion: the same method
  body, called on an instance", and the success criterion is "method
  call performance". The classification and A-28 depend on the set of
  criterion cases. Fix one of: change the criterion definition to
  "the same code on an instance (field access or method call)", or
  make `field` an information case.
  - Done: `field` is an information case.
- [x] Ambiguity: "Cases" does not say if the two kinds of a case use
  one pass function or two. In the `interface` and `protocol` cases
  the call form does not name `T` (`(.norm ^INorm e)`, `(norm e)`), so
  one shared pass function for both kinds is a natural reading. Then
  the call site sees two classes. The JIT profile is bimorphic, and
  the result depends on which kind runs first. This changes the
  ratio, and the ratio is the result. State the rule, for example:
  each pair (case, kind) has its own pass function (its own call
  site), and its arrays hold only instances of `T`. A macro can make
  the functions, so the source stays small.
  - Done: "Cases" gives each (case, kind) its own pass function and
    arrays. The rule is also in "Invariants" and acceptance
    criterion 4.
- [x] Inconsistency: "Invariants" says "The instances are made before
  the measurement", but the `allocate` case makes one instance for
  each element inside the measured pass (`(T. ...)` in "Cases"). A
  review of the invariant can then reject the `allocate` case. Fix:
  limit the invariant to the array instances, for example "The arrays
  and their instances are made before the measurement."
  - Done: invariant is "The arrays and their instances are made
    before the measurement."
- [x] Ambiguity (plan review): steps.md Slice 3 says "Save the
  output" of the record run, but no artifact says where. "Output"
  gives the EDN the purpose "keeps the numbers for a later compare",
  but "Results document" lists only the table, so the EDN of the
  record run is not kept in git and a later run on a new early-access
  build cannot compare with it. State one of (smaller first):
  - `benchmark.md` also contains the printed EDN in a code block.
  - The EDN is not kept; remove "for a later compare" from "Output".
  - Done: "Results document" (or "Output") states where the EDN of
    the record run goes, and the Slice 3 "Save the output" step has
    one target.
  - Resolved (plan pass): the EDN goes into `benchmark.md` as a code
    block. `plan.md` ("Plan decisions", Slices 3 and 4) and `steps.md`
    (Slices 3 and 4) state it. `design.md` not changed (read-only in
    this pass); its "Results document" list does not exclude it, and
    "Output" ("for a later compare") stays true.
- [x] Inconsistency (plan review): the number of type pairs.
  `plan.md` Slice 2 says "One type pair for each case" (5 pairs).
  `steps.md` Slice 2 defines 3 pairs: `interface`, `protocol`, and
  one plain pair shared by `field`, `identical` and `allocate`.
  `design.md` "Cases" ("Each case has two types") allows both
  readings. A review of acceptance criterion 4 against `plan.md` can
  reject the shared pair. Choose one and make the three files agree.
  The shared plain pair is the smaller benchmark, and it keeps the
  ratio fair: each (case, kind) has its own pass function (call
  site), so a shared class does not make a shared JIT type profile.
  - Done: `design.md` "Cases", `plan.md` Slice 2 and `steps.md`
    Slice 2 give the same set of type pairs.
  - Resolved (plan pass): three pairs (shared plain pair).
    `plan.md` Slice 2 and "Plan decisions" now agree with `steps.md`
    Slice 2. `design.md` not changed (read-only in this pass); "Cases"
    allows the shared pair.
- [x] Ambiguity (plan review, pass 2): steps.md Slice 3 (fold check)
  says "If a time does not grow, change the case and rerun", and
  `plan.md` Slice 3 says "change the case, not the result". Neither
  says which runs to repeat. The record run (1024) comes first. If
  only the 2048 run is repeated, the fold check compares the changed
  case against the 1024 times of the old case, and the table and EDN
  in `benchmark.md` come from code that is not the final code
  (acceptance criterion 3: "from one run of the command in this
  task"). State: after a change to a case, run the record run and the
  fold check again, and use only the runs of the final code.
  - Done: `plan.md` Slice 3 and `steps.md` Slice 3 state that a
    change to a case repeats both runs, and that `benchmark.md` uses
    the record run of the final code.
  - Resolved (plan pass 2): one sentence in `plan.md` Slice 3 and in
    the `steps.md` Slice 3 fold-check item. `steps.md` also puts each
    earlier failed fold check in `implementation.md`, not in
    `benchmark.md`. `design.md` not changed (read-only in this pass);
    Verification 3 and acceptance criterion 3 already imply the rule.
- [x] Ambiguity (plan review, pass 3): `steps.md` Slice 4 says
  "Update `mementum/state.md` and `munera/plan.md`" and `plan.md`
  Slice 4 lists the two names, but neither says what the update is.
  For `munera/plan.md` this is a real choice: the munera protocol
  removes the entry when the task moves to `munera/closed/`, so an
  edit in this task can remove the entry of an open task, or change
  it, or do nothing. Precedent (`munera/closed/005-deftype-compat-tests`
  `steps.md`): "`munera/plan.md`: no edit in this task. Check that the
  entry agrees with `design.md` 'Intent'. The move to
  `munera/closed/` removes the entry." For `mementum/state.md`, the
  "Next" item for 006 ("review the design, then plan and implement
  it") becomes stale. State (smaller first):
  - `munera/plan.md`: no edit; check the entry agrees with "Intent".
  - `mementum/state.md`: in "Status", add that the benchmark is done
    (`value-type.bench`, `benchmark.md`, the A-21 status). In "Next",
    replace the 006 item; the user selects the next task.
  - Done: `plan.md` and `steps.md` Slice 4 give the content of each
    update.
  - Resolved (plan pass 3): `plan.md` Slice 4 and `steps.md` Slice 4
    give the content of each update. `munera/plan.md`: no edit, check
    only. `mementum/state.md`: "Status" entry and one "Next" bullet
    (no next task created; a proposed follow-up task for a "slower"
    case is named as a candidate). `steps.md` moves the "slower" item
    before the `state.md` item. `design.md` not changed.
- [x] Ambiguity (plan review, pass 4): the fold check has one action
  only, for "a time does not grow" (`plan.md` Slice 3, `steps.md`
  Slice 3). The pass condition is a ratio in `[1.5, 2.5]`. A ratio
  in `(1.0, 1.5)` (part of the work hoisted) or above `2.5` (cache,
  GC or noise) fails Verification 3, and acceptance criterion 1 needs
  it to pass, but no artifact says what to do. A repeat of the same
  runs until a ratio passes selects the result, against "change the
  case, not the result". State one rule (smaller first):
  - Any ratio outside `[1.5, 2.5]` is a failed check, with the same
    action as "does not grow": find the cause, change the case (or
    the run setup, for example `:limit-time-s`), and repeat both
    runs. Do not repeat unchanged runs to get a pass. Record each
    failed check in `implementation.md`.
  - Or: permit one repeat of both runs with no change (noise), and
    record the first runs in `implementation.md`.
  - Done: `plan.md` Slice 3 and `steps.md` Slice 3 state the action
    for each ratio outside `[1.5, 2.5]`.
  - Resolved (plan pass 4): option 1. `plan.md` Slice 3 and the
    `steps.md` Slice 3 fold-check item: any ratio outside
    `[1.5, 2.5]` is a failed check with one action (find the cause,
    change the case or the run setup for all cases, repeat both
    runs; no repeat of unchanged runs). Each failed check, its cause
    and the change go into `implementation.md`. `design.md` not
    changed; Verification 3 ("change the case, not the result")
    agrees.
- [x] Ambiguity (plan review, pass 5): no step says how the run time
  is checked. Design "Time limit" says "The full run takes less than
  5 minutes", and `plan.md` Slice 2 / `steps.md` Slice 2 tune
  `:limit-time-s` to it. The pass-4 fold-check rule permits a
  run-setup change only "with the full run < 5 minutes". But no
  Slice 3 step measures or records the time of a run, and no artifact
  says which run must be under the limit (the record run (1024), the
  fold check (2048), or each). A review of the "Time limit" choice
  then has no evidence. State (smaller first):
  - Slice 3: measure the wall time of the final record run and of the
    final fold check (for example with `time`), and record both in
    `implementation.md`. Each must be under 5 minutes.
  - Or: record only the wall time of the final record run.
  - Done: `plan.md` Slice 3 and `steps.md` Slice 3 say which run is
    timed, how, and where the time is recorded.
  - Resolved (plan pass 5): option 1. `plan.md` Slice 3 and the
    `steps.md` Slice 3 run items: run each with `time` (shell); the
    wall time (`real`) of the final record run and of the final fold
    check is each less than 5 minutes and goes into
    `implementation.md` (not `benchmark.md`). A run of 5 minutes or
    more: decrease `:limit-time-s` (all cases) and do both runs
    again. `design.md` not changed.
