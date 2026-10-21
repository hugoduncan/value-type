# Plan

## Approach

Implement in four slices. Each slice ends with a verifiable state.

1. **Bench infrastructure**: add the `:bench` alias to `deps.edn`
   (extra path `bench`, criterium `org.hugoduncan/criterium`
   `0.5.245-ALPHA`, JVM options `--enable-preview`,
   `-XX:+UnlockExperimentalVMOptions`,
   `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`).
2. **Benchmark namespace** `value-type.bench` in
   `bench/value_type/bench.clj`:
   - `definterface INorm` and `defprotocol Norm`.
   - Three type pairs (`deftype` / `defvaluetype`), fields `^long x`
     `^long y`, identical bodies: an `interface` pair (implements
     `INorm`), a `protocol` pair (implements `Norm` inline), and one
     plain pair (no method) that the `field`, `identical` and
     `allocate` cases share. A macro makes each pair to guarantee the
     "differ only by kind" invariant. The shared plain pair keeps the
     ratio fair: each (case, kind) has its own pass function, so a
     shared class does not give a shared call site or JIT type
     profile.
   - A macro makes the two pass functions of each case (differ only by
     `T`), one call site per (case, kind). Pass = `loop` over an
     `Object` array, `long` sum, returns the sum.
   - Arrays made before measurement: element `i` has `x = i`,
     `y = 2i`; a second array for `identical`.
   - One helper reads the median from
     `(-> (last-bench) :data :stats :stats :elapsed-time :median)`
     and divides it by `(-> (last-bench) :data :stats :batch-size)`
     (a sample is a batch; isolates the alpha-internal paths).
   - `bench` with `:limit-time-s` tuned so the full run is < 5
     minutes; suppress the criterium report (`:viewer`).
   - `-main` parses optional `n` (string → long, default 1024), runs
     all five cases, prints `n`, a Markdown table (median time per
     call per kind, ratio defvaluetype/deftype) and the same data as
     EDN.
3. **Measurement runs**: run with 1024 (record run) and 2048 (fold
   check, Verification 3). The printed table and EDN of the record run
   go into `benchmark.md` (Slice 4). Record the fold-check ratios and
   the full check run in `implementation.md`. Any fold-check ratio
   outside `[1.5, 2.5]` (a time that does not grow, part of the work
   hoisted, or cache, GC or noise above 2.5) is a failed check: find
   the cause, then change the case (or the run setup for all cases,
   for example `:limit-time-s` with the full run < 5 minutes, or stop
   a background load), not the result. Do not repeat unchanged runs
   to get a pass. Record each failed check, its cause and the change
   in `implementation.md`. After a change, do the record run and the
   fold check again; `benchmark.md` uses only the record run and the
   fold check of the final code. Measure the wall time of each run
   with `time` (shell). The final record run and the final fold check
   must each take less than 5 minutes (design "Time limit"); record
   both times in `implementation.md`, not in `benchmark.md`. If a run
   takes 5 minutes or more, decrease `:limit-time-s` (all cases) and
   do both runs again.
4. **Results document and documentation**: write
   `ramora/defvaluetype-design/benchmark.md` (environment, command,
   table, the EDN of the record run in a code block, classification
   with [0.9, 1.1] bounds, interpretation, fold check, validity
   statement), then update the eight documentation
   targets listed in design "Documentation" (design index, A-28, A-21,
   qualities, embodiment, D11, risks-and-evolution, README,
   `mementum/state.md`, `munera/plan.md`).
   - `mementum/state.md`: in "Status", add that the benchmark is done
     (task 006, `value-type.bench`, the run command, `benchmark.md`,
     the A-21 status from the run). In "Next", replace the 006 item
     with one bullet: no next task is created; the user selects the
     next task. If a criterion case is "slower", the bullet names the
     proposed follow-up task as a candidate. Keep the bullet for the
     nested `defvaluetype` task.
   - `munera/plan.md`: no edit. Check that the 006 entry agrees with
     design "Intent". The move of the task to `munera/closed/` (not
     in this task) removes the entry (munera protocol).

Key decisions (all fixed in design.md): `Object` arrays with `^T`
element hints, many instances not one captured instance, separate
`bench/` path, output format, classification bounds.

Plan decisions (design.md allows them; the smaller option):

- Three type pairs, not five (see Slice 2). design "Cases" ("Each
  case has two types") allows a pair that cases share.
- The EDN of the record run is kept in git as a code block in
  `benchmark.md`. This gives design "Output" its purpose ("keeps the
  numbers for a later compare"): a run on a new early-access build
  compares with it. design "Results document" does not exclude more
  content.

Implementer choices left open by design (choose the smaller option):
`:limit-time-s` value, table columns and time unit, EDN keys.

## Risks

- **JIT folding despite the array approach**: Verification 3 detects
  it; mitigation is to change the case per the design rule. May need
  iteration.
- **Alpha criterium API drift**: the `last-bench` median path is
  internal. Isolated in one helper function; if the path changed,
  inspect `last-bench` output at the REPL.
- **Reflection warnings**: would invalidate times. Load with
  `*warn-on-reflection*` true and fix hints before measuring.
- **Run time over 5 minutes**: 5 cases × 2 kinds; tune
  `:limit-time-s` accordingly (≤ ~25 s per measurement).
- **A "slower" result**: not a blocker; set A-21 `contested`, record,
  and propose a follow-up task (do not fix in this task).
- **Wrong JDK**: all runs need `JAVA_CMD` set to the Valhalla java;
  check `java.vm.version` in the environment record.

## Slice order

1. `:bench` alias in `deps.edn`.
2. `value-type.bench` namespace (types, pass functions, measurement,
   output).
3. Measurement runs and verification (record run, fold check, test
   suite unchanged).
4. `benchmark.md` and documentation updates.
