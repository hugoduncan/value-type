# Implementation

## Design review

- 2026-10-20 architecture review added 3 new design steps.
  - Sources: `ramora/META.md` and `doc/architecture.md` do not exist.
    Architecture is in `ramora/defvaluetype-design*` and the external
    metamodel `~/projects/hugoduncan/architecture/ramora/meta-model.md`
    (assertion status values and quality relations).
  - Fit is good for: separate `bench/` path and `:bench` alias (test
    deps unchanged, alpha dependency isolated), no `src/` change,
    results scoped to one environment (metamodel rule "quality applies
    only within its context"), new section `benchmark.md` linked from
    `defvaluetype-design.md`.
- 2026-10-20 ambiguity review added 3 new design steps.
- 2026-10-20 inconsistency review added 2 new design steps.
  - Checked: `^T` hint on an element of an `Object` array gives no
    reflection (Clojure 1.12.6). The cited mementum memories exist.

## Notes for addressing design-steps

- Principles: scope is frozen; edit `design.md` in place (no revision
  markers, per munera); STE style (`AGENTS.md`); when unsure, choose
  the smaller benchmark (design "Intent"); tick each step in
  `design-steps.md` with a short note.
- Coupled steps: resolve the `field` criterion step before the A-28 /
  A-21 step (the set of criterion cases sets the classification). The
  `Object`-array fix and the method return-type choice both change
  "Cases" and "Decisions"; do them in one edit.
- Relevant paths: `ramora/defvaluetype-design/{embodiment,decisions,
  risks-and-evolution,assertions,qualities}.md`,
  `~/projects/hugoduncan/architecture/ramora/meta-model.md`
  ("Assertion Status", "Quality Relations"), precedent
  `munera/closed/005-deftype-compat-tests/design-steps.md`.

## Design-step resolution

- 2026-10-20 all 8 design steps done in `design.md`. No
  `SCOPE_QUESTION:` items.
  - Do not put `INorm` and protocol `Norm` on one type: both have a
    no-argument `norm`, with return types `long` and `Object`. The
    design gives each case its own type pair.
  - `(long (norm e))` in the `protocol` case keeps the `loop` sum a
    primitive `long`. `(+ s (norm e))` gives a boxed `Number`, and the
    `recur` then does not match the `long` loop local.
  - `-main` gets `n` as a string. Parse it to a `long`.
  - A-28 confidence `medium` is the first value other than `high` in
    `assertions.md`. The metamodel does not list confidence values.

## Design review (pass 2)

- 2026-10-20 architecture review (pass 2, after resolution): no
  architectural review feedback. A-28 `exhibits` and A-21
  `contested` match `meta-model.md` ("Quality Relations",
  "Assertion Status").
- 2026-10-20 ambiguity review (pass 2) added 1 new design step (one
  call site for each case and kind).
- 2026-10-20 inconsistency review (pass 2) added 1 new design step
  (invariant against `allocate`).
  - Checked: each section that "Documentation" names exists. The
    quotes from `purpose.md`, `qualities.md` and A-21 match. Criterium
    `bench` has a `:viewer` option, so the "Output" rule (no criterium
    report) is possible.

## Notes for addressing design-steps (pass 2)

- The 2 open steps change only "Cases" (one pass function for each
  case and kind) and "Invariants" (the arrays are made before the
  measurement). They do not change "Documentation", "Results
  document" or the A-28 / A-21 rules.
- Keep the rule symmetric: the two pass functions of a case differ
  only by `T`, the same as the "differ only by `deftype` and
  `defvaluetype`" invariant for the types. If the step adds a rule
  for the pass functions, put it in "Invariants" and in acceptance
  criterion 4.
- The order in which the kinds run must not change the result. Do not
  add a run-order rule as well: one call site for each case and kind
  is sufficient.
- Criterium API source, if you must make a check:
  `~/.m2/repository/org/hugoduncan/criterium/0.5.245-ALPHA/criterium-0.5.245-ALPHA.jar`
  (`criterium/bench.clj`: `:viewer`, `last-bench`).

## Design-step resolution (pass 2)

- 2026-10-20 both pass-2 steps done in `design.md`. No
  `SCOPE_QUESTION:` items.
  - The `identical` pass functions of the two kinds have the same
    text (the value does not name `T`). Only their arrays differ. This
    satisfies "differ only by `T`".
  - No run-order rule added (see pass-2 notes).

## Design review (pass 3)

- 2026-10-20 architecture review (pass 3, after pass-2 resolution): no
  architectural review feedback. The pass-2 changes (one pass function
  for each case and kind, invariant scope) are local to
  `value-type.bench`.
- 2026-10-20 ambiguity review (pass 3): no ambiguity review feedback.
  - Not raised (implementer choice under the 5-minute limit and the
    "smaller benchmark" rule): the `:limit-time-s` value, the table
    columns and time unit, the EDN keys. Fold-check pass times come
    from time per call × `n`.
- 2026-10-20 inconsistency review (pass 3): no inconsistency review
  feedback.
  - Checked: criterium `bench` has `:limit-time-s`; `criterium.arg-gen`
    is not in the criterium jar (a separate dependency); README has
    "Development".
  - Not raised: "Many instances" says 1024, `-main` takes `n`. 1024 is
    the default (the recorded run), so the text is correct.

## Notes for addressing design-steps (pass 3)

- Pass 3 added no design steps. All items in `design-steps.md` are
  done. `design.md` is stable and ready for `create-task-plan`.

## Plan review

- 2026-10-20 ambiguity review (plan) added 1 new design step (where
  the record-run EDN goes).
  - Checked: criterium `0.5.245-ALPHA` has a `:viewer :none` (no-op
    methods in `criterium/view.clj`), not in the `bench` docstring.
    It suppresses the report. The blackhole warning is printed at
    load (`criterium/blackhole.clj`), so `:none` does not hide it
    (Verification 2 stays valid).
  - Probe (JDK 27-jep401ea3): a user macro that expands to
    `(do (deftype D …) (vt/defvaluetype V …))` gives a value class
    `V`; `(V. x y)` and `(.-x ^V (aget ^objects a i))` in a `loop`
    give no reflection warning.
- 2026-10-20 inconsistency review (plan) added 1 new design step
  (type pairs: one for each case in `plan.md`, 3 in `steps.md`).
  - Not raised: `plan.md` Slice 4 says "eight documentation targets"
    and lists ten names. Eight is the bullet count of design
    "Documentation"; the list splits A-28 / A-21 and `state.md` /
    `plan.md`. `steps.md` lists all targets.

## Notes for addressing design-steps (plan review)

- The 2 open steps come from the plan review. Unlike earlier passes,
  they can need edits to `plan.md` and `steps.md` as well as
  `design.md`. Make all three agree in one commit.
  `steps.md` is read-only only during review.
- Type-pair step: keep the existing rules. The two types of a pair
  differ only by the defining macro. Each (case, kind) has its own
  pass function. With a shared plain pair, the `field`, `identical`
  and `allocate` pass functions all use the same `T`; acceptance
  criterion 4 ("differ only by `T`") still holds.
- EDN step: if the EDN goes into `benchmark.md`, update design
  "Results document" and the `steps.md` Slice 4 `benchmark.md` item.
  If not, change only the "Output" rationale and leave "Save the
  output" as a scratch copy for writing `benchmark.md`.
- Paths: `munera/open/006-method-call-benchmark/{design,plan,steps}.md`;
  criterium viewer source `criterium/view.clj` in
  `~/.m2/repository/org/hugoduncan/criterium/0.5.245-ALPHA/criterium-0.5.245-ALPHA.jar`;
  Valhalla java `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  (from `.psi/project.local.edn`, not committed).

## Plan-step resolution

- 2026-10-20 both plan-review steps done in `plan.md` and `steps.md`.
  `design.md` not changed (read-only for the plan profile).
  - Type pairs: 3 (`interface`, `protocol`, shared plain pair). A
    later design pass can make design "Cases" say this explicitly; it
    is not required, because "Cases" allows both readings.
  - Record-run EDN: code block in `benchmark.md`. A later design pass
    can add it to design "Results document"; review acceptance
    criterion 3 against `plan.md` / `steps.md` Slice 4 for the EDN.

## Plan review (pass 2)

- 2026-10-20 ambiguity review (plan, pass 2) added 1 new design step
  (which runs to repeat after a fold-check failure).
  - Checked: `criterium/bench.clj` stores `last-bench` after the view
    step for every viewer, so `:viewer :none` does not stop the median
    helper.
- 2026-10-20 inconsistency review (plan, pass 2): no inconsistency
  review feedback.

## Notes for addressing design-steps (plan review, pass 2)

- One open step (which runs to repeat after a fold-check failure). It
  changes only Slice 3 of `plan.md` and `steps.md`. Do not change
  `design.md` (read-only for the plan profile). Design Verification 3
  ("change the case, not the result") and acceptance criterion 3
  ("from one run of the command") already imply the rule.
- Keep the order record run → fold check. After a change to a case,
  repeat both runs, then take the table and EDN for `benchmark.md` only
  from the final record run. Record the earlier failed fold check in
  `implementation.md` (discovery), not in `benchmark.md`.
- Do not add a new verification or a run-order rule; one sentence in
  each Slice 3 is sufficient.

## Plan-step resolution (pass 2)

- 2026-10-20 the pass-2 plan-review step done in `plan.md` and
  `steps.md` Slice 3. `design.md` not changed.
  - Rerun rule: a change to a case repeats the record run (1024) and
    the fold check (2048). The `benchmark.md` table, EDN and fold-check
    lines come from the final pair of runs only. The Slice 3 first item
    ("Keep the printed table and EDN") then means the final record run.
  - Review check: the fold-check lines in `benchmark.md` and the
    ratios in `implementation.md` must come from the same final pair
    of runs.

## Plan review (pass 3)

- 2026-10-20 ambiguity review (plan, pass 3) added 1 new design step
  (content of the `mementum/state.md` and `munera/plan.md` updates).
  - Not raised: criterium `bench` takes options after the expression
    (`:limit-time-s`, `:viewer` in `criterium/bench.clj` docstring).
- 2026-10-20 inconsistency review (plan, pass 3): no inconsistency
  review feedback.
  - Not raised: `plan.md` leaves "table columns" open and `steps.md`
    Slice 2 fixes them (case, two times, ratio); the steps make the
    choice, so no conflict. The open pass-3 step ("no edit" to
    `munera/plan.md`) agrees with design "Documentation" and
    acceptance criterion 6 ("agree with this design"), as in task 005.

## Notes for addressing design-steps (plan review, pass 3)

- One open step (content of the `mementum/state.md` and
  `munera/plan.md` updates). Edit only Slice 4 of `plan.md` and
  `steps.md`, in one commit. Do not change `design.md`: its
  "Documentation" bullet names the two files, and a check-only rule
  for `munera/plan.md` still agrees with it.
- Do not move the task to `munera/closed/` or remove its
  `munera/plan.md` entry in this task. The close step of the lifecycle
  does that (munera protocol).
- `state.md` "Next" item for 006 to replace starts "The benchmark task
  is designed". Keep the bullet for the other adjacent task (nested
  `defvaluetype`). Do not write the A-21 status in the plan; it comes
  from the run (`intended` or `contested`).
- Wording precedent: `munera/closed/005-deftype-compat-tests/steps.md`
  (the `mementum/state.md` and `munera/plan.md` items, Slice 9).

## Plan-step resolution (pass 3)

- 2026-10-20 the pass-3 plan-review step done in `plan.md` and
  `steps.md` Slice 4. `design.md` not changed.
  - `steps.md` Slice 4: the "slower" item now comes before the
    `mementum/state.md` item, because the "Next" bullet names the
    proposed follow-up task.
  - The "Next" item for 006 in `mementum/state.md` holds the only
    `state.md` note of the JIT fold trap (one captured instance).
    `mementum/memories/` has no memory for it. After the run,
    `benchmark.md` (interpretation, fold check) records it; a memory
    is a candidate for `extract-task-knowledge`.

## Plan review (pass 4)

- 2026-10-20 ambiguity review (plan, pass 4) added 1 new design step
  (action for a fold-check ratio outside `[1.5, 2.5]` other than "does
  not grow").
  - Checked: criterium `bench` captures local bindings as arguments
    (docstring), so `(bench (pass a))` in a run function is valid;
    `metric.clj` gives elapsed time in nanoseconds.
  - Not raised: the content of the `ramora/defvaluetype-design.md`
    "Status" update (one sentence that names `benchmark.md`; no real
    choice). One `bench` call site for all (case, kind) in a run
    function is acceptable: the element call sites stay in the pass
    functions, and one call per pass is amortized over `n`.
- 2026-10-20 inconsistency review (plan, pass 4): no inconsistency
  review feedback.
  - Checked: `deftype_compat_test.clj` has `defvaluetype` forms with
    no methods, so the shared plain pair is valid. The `state.md`
    "Next" items that Slice 4 names exist (006 item, nested
    `defvaluetype` bullet).
  - For the pass-4 step: option 2 (one repeat with no change) must
    still agree with design Verification 3 ("change the case, not the
    result"). Option 1 agrees with no design edit.

## Notes for addressing design-steps (plan review, pass 4)

- One open step (action for a fold-check ratio outside `[1.5, 2.5]`).
  Edit only Slice 3 of `plan.md` and of `steps.md`, in one commit. Do
  not change `design.md` (read-only for the plan profile).
- Prefer option 1 (any out-of-range ratio is a failed check, same
  action as "does not grow"). It needs no design edit. Option 2 needs
  a design pass for Verification 3.
- Put the rule in the existing fold-check sentence ("If a time does
  not grow …"). Keep the pass-2 rerun rule (repeat both runs, use only
  the final pair) and the record of failed checks in
  `implementation.md`. Do not add a new verification.
- A change to the "run setup" (for example `:limit-time-s`) applies
  to all cases. It must keep the full run under 5 minutes (design
  "Time limit").

## Plan-step resolution (pass 4)

- 2026-10-20 the pass-4 plan-review step done in `plan.md` and
  `steps.md` Slice 3 (option 1). `design.md` not changed.
  - "Run setup" includes the machine state (for example, stop a
    background load), not only `:limit-time-s`. Such a change applies
    to all cases, so repeat both full runs, not one case.
  - Review check: each failed fold check in `implementation.md` names
    its cause and the change. A pair of runs with no change after a
    failed check does not count as the final pair.

## Plan review (pass 5)

- 2026-10-20 ambiguity review (plan, pass 5) added 1 new design step
  (how and for which run the 5-minute limit is checked).
  - Checked (`criterium/bench/config.clj`,
    `criterium/collect_plan/config.clj`): `:limit-time-s` is cast
    with `long` (whole seconds only). The default limit is 10 s for
    each `bench` call, so 10 measurements take about 100 s plus warmup
    and JVM start. The default can already meet the limit; tune only
    if the measured time is over it.
  - Not raised: the source of the environment values in
    `benchmark.md` (`java.vm.version`, CPU). `-main` does not print
    them; get them with the same `JAVA_CMD` (for example
    `-XshowSettings:properties -version`) and `sysctl` for the CPU.
    Implementer choice; the "Wrong JDK" risk stays covered if the
    same `JAVA_CMD` is used.
- 2026-10-20 inconsistency review (plan, pass 5): no inconsistency
  review feedback.
  - Not raised: the `steps.md` Slice 4 `benchmark.md` item says "a
    short interpretation with the known measurement limits" and does
    not repeat "the result for the success criterion" (design
    "Results document"). The classification item covers it; review
    against the design list.

## Notes for addressing design-steps (plan review, pass 5)

- One open step (which run is timed for the 5-minute limit, how, and
  where the time goes). Edit only Slice 3 of `plan.md` and of
  `steps.md`, in one commit. Do not change `design.md` (read-only for
  the plan profile); design "Time limit" names no check, so a Slice 3
  timing rule agrees with it.
- Prefer option 1 (time both final runs). The pass-4 rule lets a
  run-setup change happen only "with the full run < 5 minutes", and
  the fold check (2048) is a full run too.
- Put the timing in the existing record-run and fold-check items. The
  times come from the final pair of runs only (pass-2 rule). Record
  them in `implementation.md`, not in `benchmark.md` (design "Results
  document" does not list them). Do not add a new verification.
- `:limit-time-s` takes whole seconds (cast with `long`). Source:
  `criterium/bench/config.clj` and `criterium/collect_plan/config.clj`
  in
  `~/.m2/repository/org/hugoduncan/criterium/0.5.245-ALPHA/criterium-0.5.245-ALPHA.jar`.

## Plan-step resolution (pass 5)

- 2026-10-20 the pass-5 plan-review step done in `plan.md` and
  `steps.md` Slice 3 (option 1). `design.md` not changed.
  - Timing: `time clojure -M:bench -m value-type.bench [2048]`; use
    `real`. The JVM start and the warmup are in the time, so the time
    is of the full run, as design "Time limit" says.
  - A time of 5 minutes or more is a run-setup change (pass-4 rule):
    decrease `:limit-time-s` for all cases and repeat both runs. The
    fold-check ratios of that new pair must pass too.
  - Review check: the two wall times in `implementation.md` come from
    the same final pair as the `benchmark.md` table, EDN and
    fold-check lines.

## Plan review (pass 6)

- 2026-10-20 ambiguity review (plan, pass 6): no ambiguity review
  feedback.
  - All earlier open points (`:limit-time-s` value, table columns and
    time unit, EDN keys, environment-value source, viewer option)
    stay implementer choices with bounded defaults; no instruction in
    `plan.md` or `steps.md` admits two readings with different
    acceptance outcomes.
- 2026-10-20 inconsistency review (plan, pass 6): no inconsistency
  review feedback.
  - Not raised: the plan risk "≤ ~25 s per measurement" is approximate
    guidance (10 measurements ≈ 250 s plus warmup and JVM start); the
    Slice 3 timing rule governs, so no conflict with the 5-minute
    limit.

## Notes for addressing design-steps (plan review, pass 6)

- Pass 6 added no design steps. All items in `design-steps.md` are
  done. `plan.md` and `steps.md` are stable and ready for
  `implement-task`.
- For implementation: the review-check notes in the pass 2–5
  resolutions above are the acceptance conditions for Slice 3 (final
  pair of runs only, failed checks with cause and change, two wall
  times) — read them before the measurement runs.
- All runs need `JAVA_CMD` set to the Valhalla java:
  `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
  (from `.psi/project.local.edn`, not committed).

## Implementation (slices 1–3)

- 2026-10-20 `:bench` alias and `bench/value_type/bench.clj` done.
  - `defpair` macro: one `deftype` and one `defvaluetype` from the same
    fields and specs. Pairs: `IDt`/`IVt` (INorm), `PDt`/`PVt` (Norm),
    `Dt`/`Vt` (plain, shared by `field`, `identical`, `allocate`).
  - `defpass` macro: one pass function per (case, kind) from one value
    template. `substitute-t` replaces `T`, `T.` and the `:tag` metadata
    `^T` (a plain `postwalk-replace` does not touch hints). Pass
    functions take `[a b]` (`b` is used by `identical` only) and are
    `IFn$OOL` (primitive `long` return). No reflection warning.
  - Discovery (design "Context" fact corrected): the
    `:stats :stats :elapsed-time :median` value is the median of one
    *sample*, and a sample is a batch (`:data :stats :batch-size`,
    about 30). The first run without the division gave about 10 ns per
    call (the batch time / n). `median-ns` divides by the batch size.
    `design.md` Context and `plan.md` Slice 2 updated.
  - `:limit-time-s` 10 (default). A full run takes about 25 s, so no
    tuning was necessary.
  - The JVM prints `CompileCommand: blackhole ...` at start. This is
    the JVM ack of the option, not the criterium blackhole warning.
- Final pair of runs (JDK 27-jep401ea3, no failed fold check):
  - Record run `time clojure -M:bench -m value-type.bench`: real
    25.1 s. Table: interface 0.512 / 0.510 (1.00), protocol 0.574 /
    0.576 (1.00), field 0.324 / 0.318 (0.98), identical 0.525 / 3.947
    (7.52), allocate 0.317 / 0.318 (1.00) ns/call.
  - Fold check `time clojure -M:bench -m value-type.bench 2048`: real
    23.0 s. Table: interface 0.490 / 0.482, protocol 0.570 / 0.570,
    field 0.313 / 0.315, identical 0.535 / 3.927, allocate 0.315 /
    0.316 ns/call.
  - Pass-time ratio 2048/1024 (= 2 × per-call ratio): interface
    deftype 1.91, defvaluetype 1.89; protocol deftype 1.98,
    defvaluetype 1.98. All in `[1.5, 2.5]`.
  - `identical` (information): value class `identical?` is a field
    compare (`acmp` substitutability), about 7.5× slower. Expected
    semantic difference, not a criterion case.
  - Both criterion cases "equal" → A-21 stays `intended`.
  - Full outputs of both runs: `benchmark-runs.txt` in this task dir
    (the record-run table and EDN for `benchmark.md` come from it).
- `clojure -M:test -m scry.cli`: exit 0 (64 tests). `src/`, `test/`
  unchanged.
- Next slice: Slice 4 (`benchmark.md` and documentation).

## Implementation (slice 4)

- 2026-10-20 `benchmark.md` written from the final record run and fold
  check above (table and EDN copied verbatim from
  `benchmark-runs.txt`; the `n = 1024` line is not in the table, `n`
  is in the EDN). Documentation targets updated: design index
  (Sections, Status), A-28 added, A-21 basis (status `intended`),
  qualities performance row, embodiment artifacts, D11,
  risks-and-evolution assumptions, README "Run the benchmark",
  `mementum/state.md`.
- `munera/plan.md`: no edit; the 006 entry agrees with design "Intent".
- No criterion case is "slower", so no follow-up task is proposed.
- Memory candidate for `extract-task-knowledge`: criterium
  `last-bench` median is per batch (divide by `:batch-size`).

## Implementation review

- 2026-10-20 implementation review added 2 steps to be addressed.

- 2026-10-23 implementation review added 2 steps to be addressed.

## Review follow-up (2026-10-23)

- Addressed 2 review steps. Retained and checked the existing working-tree
  fix: only `identical` takes and loads a second array; `:gen-class` is
  removed. All ten pass functions return the expected sums at n = 8.
  Namespace load has no reflection or blackhole warning.
- Retained the saved final pair in `benchmark-runs.txt`; no unchanged
  rerun to seek an equal classification. Record run: 23.673 s; fold
  check: 22.488 s. Full output for both runs is in that file.
  Pass-time ratios (2048/1024): interface 2.18 / 2.00, protocol
  2.01 / 2.17 (deftype / defvaluetype). All pass [1.5, 2.5].
  `benchmark.md` contains that record-run table and EDN; A-28 now
  records interface 1.14 and protocol 1.00.
- Verification: scry CLI exit 0, 64 tests, 332 assertions; no failure
  files in `.scry-results/`. No change to `src/` or `test/`.

- 2026-10-23 addressed 1 review step: aligned A-21 (`contested`),
  design index, state and Slice 4 with interface 1.14 (slower) and
  protocol 1.00 (equal). Proposed follow-up: investigate the interface
  slowdown on this JDK, including measurement stability and generated
  code. No new task or `src/` change.

- 2026-10-23 addressed 1 review step: replaced the unsupported ±15 %
  variance and identical-generated-work claims with the observed
  ratios. Escape analysis is a possible explanation, not a verified
  result. All 4 implementation-review steps are complete.

- 2026-10-23 implementation review (pass 3): no new actionable issues.

- 2026-10-23 test review: no new actionable issues.

- 2026-10-23 documentation review: no new actionable issues.

- 2026-10-23 code-shaper review added 1 step to be addressed.

- 2026-10-23 addressed 1 code-shaper review step: validate `n` before
  allocation and measurement; document [1, 2147483647]. Removed the
  primitive argument hint so non-integers cannot be coerced before
  validation. CLI errors retain the invalid argument.
  - Direct checks reject zero, negative, nil, string, fractional and
    oversized values. CLI checks reject zero, negative, non-numeric,
    fractional and oversized arguments (including long overflow).
    Boundary validation accepts 1 and 2147483647 without allocation.
  - Full default and 2048 runs exit 0, with no reflection warnings.
    These are input smoke checks, not replacement benchmark records.
    Scry exits 0: 64 tests, 332 assertions; no failure files.
    `src/`, `test/` and `design.md` are unchanged.

- 2026-10-23 code-shaper review (pass 2) added 1 step to be addressed.

- 2026-10-23 addressed 1 code-shaper review step: use `mapv` for
  measurement traversal. The 1024-instance smoke run passed: five maps,
  unchanged keys, order interface/protocol/field/identical/allocate.
  Validation and deftype-before-value-type order are unchanged. No
  reflection or blackhole warnings. Saved benchmark results unchanged.

- 2026-10-23 code-shaper review (pass 3) added 1 step to be addressed.

- 2026-10-23 addressed 1 code-shaper review step: allocate and fill
  instance arrays directly with `dotimes` and `aset`. At n = 8, checks
  passed for Object-array class, length, separate arrays and all field
  values for six constructors, and sums for all ten pass functions.
  No reflection or blackhole warning. Measured passes, saved benchmark
  results, `src/`, `test/` and `design.md` are unchanged.

- 2026-10-23 code-shaper review (pass 4): no new actionable issues.

- 2026-10-23 closed at the user's request after implementation review
  and knowledge extraction. Lessons are in
  `mementum/knowledge/benchmarking-value-types.md` (commit `79a650f`).
  All checklist items are complete. The interface slowdown remains a
  proposed separate investigation; A-21 remains `contested`.
