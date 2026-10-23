# Implementation notes

## 2026-10-23 — Architecture review

- No architectural review feedback. Scope unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture
  context is in `ramora/defvaluetype-design.md` and its linked sections.
- D3 rejects an agent for library class transformation, not an agent
  used only by a manual measurement. The proposed measurement agent
  does not conflict with that decision.

## 2026-10-23 — Ambiguity review

- No ambiguity review feedback. Scope unchanged.

## 2026-10-23 — Inconsistency review

- No inconsistency review feedback. Scope unchanged.

## 2026-10-23 — Review handoff

- In the cached Criterium `0.5.251-ALPHA` JAR,
  `criterium/agent/instrumentation.clj` sets the instrumentation state
  in `-agentmain`; `-premain` only logs. Do not assume startup agent
  attachment initializes `object-size`. No agent was run in this review.

## 2026-10-23 — Planning feasibility check

- Design is complete for planning. No user decision is needed.
- Inspected the cached published Criterium `0.5.251-ALPHA` POM, JAR
  manifest and `criterium.agent`, `agent.core`, `agent.loader` and
  `agent.instrumentation` sources. The direct instrumentation function
  has no packaged agent entry point: no agent manifest attribute or
  compiled instrumentation class; `-premain` only logs. No custom
  instrumentation agent was built or tested.
- Verified the supported native route using the published artifact,
  not a local Criterium build. Setup command, with `JAVA_CMD` set to
  `~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`:

  ```sh
  clojure -Sdeps '{:deps {org.hugoduncan/criterium {:mvn/version "0.5.251-ALPHA"}}}' \
    -J--enable-preview -M \
    -e "(require '[criterium.agent :as a]) (prn (a/extract-agent!))"
  ```

- Extraction returned the bundled `macos-arm64` dylib with hash
  `fbf14546dcbb868c1ab0ca8c61b290bb60bdc2c3d6fe75bd0aa3f7d7b9435897`.
  A second JVM used the same dependency override, `--enable-preview`
  and `-agentpath:<extracted-path>`. `a/attached?` passed on
  `27-jep401ea3+1-1`, Mac OS X/aarch64, Clojure 1.12.6. Exit code: 0.
- The temporary probe declared eight top-level types with no added
  methods. For each class it traced `mapv` of ten constructor calls,
  retained the vector, selected records by exact class name, printed
  fields and `Class.isValue`, then called `Reference.reachabilityFence`.
  Values were `long(i + field-index)` for sample indices 0–9.
  Each class had exactly ten target records, none freed, with these
  sizes: identity `[16 24 32 40]`, value `[16 32 40 48]` for 0–3 fields.
  All expected fields were primitive `long`; value status was correct.
  These are feasibility observations, not acceptance evidence or a
  portable size claim. Final code must repeat the run, also select by
  thread, capture VM settings and preserve complete raw evidence.
- The public Criterium API documents `:object_size` as per-object bytes.
  The JVMTI specification links `SampledObjectAlloc.size` to
  `GetObjectSize`: an implementation-specific storage approximation,
  not retained size. Reference:
  <https://docs.oracle.com/en/java/javase/25/docs/specs/jvmti.html#SampledObjectAlloc>.
  The API does not establish a byte-level header layout. Do not infer
  one from the observed difference.
- Selected the bundled native route and an isolated `:object-size`
  alias. No repository implementation files were changed by the probe.

## 2026-10-23 — Plan ambiguity review

- Reviewed `plan.md` with `steps.md` as read-only context. Checklist
  SHA-256: `5ecf6c8ce15ff352d8888bc1e0a977042d97f7beda1f2087cc05e88dfad26316`.
- Clarified in the plan: explicit Scry namespace selection for tests
  under `bench/`, attachment checks at measurement entry rather than
  namespace load, and output cleanup/publication order after failures.
  The checklist was not changed during review.
- Acceptance coverage: criteria 1 and 3 map to slices 1–3; criterion 2
  to slices 2–4; criterion 4 to slices 3–4; criteria 5 and 6 to slice 4.
- No unresolved plan ambiguity. Sample count, filenames and launch
  command were planning choices, not missing design requirements.

## 2026-10-23 — Design consistency review for planning

- Re-read `design.md` against `deps.edn`, README, library macro,
  benchmark code/results, architecture decisions and measurement
  guidance. No internal inconsistency or conflict with referenced
  artifacts was found. The existing alias still uses `0.5.245-ALPHA`;
  the new isolated alias preserves that history.
- D3 excludes agents for library transformation, not manual
  measurement. No library behavior change is planned.
- The design explicitly permits per-object allocation records. The
  verified native route satisfies that choice without substituting a
  library or version. Unknown VM settings remain explicitly reportable.
- Design unchanged. No blocking or non-blocking design ambiguities
  remain. Implementation has not started.

## 2026-10-23 — Shared plan-review, ambiguity turn

- No new actionable ambiguity feedback; prior plan clarifications stand.

## 2026-10-23 — Shared plan-review, inconsistency turn

- No new actionable inconsistency feedback across the task files.

## 2026-10-23 — Slice 1: isolated launch

- Added the isolated alias and startup wrapper. `:bench` and `src/`
  are unchanged. The entry point currently reports attachment only.
- Transfer the extracted path through a temporary file with trap cleanup;
  Criterium startup messages are not path data. Use an environment
  variable for that file: arguments after `clojure -e` did not populate
  `*command-line-args*` in the initial check.
- Verification on the selected Valhalla JDK: wrapper exit 0 with
  `{:agent-attached? true}`; resolved classpath contains the published
  `0.5.251-ALPHA` JAR; namespace loads without an agent; direct main
  without an agent exits nonzero with the launcher instruction. An
  invalid `JAVA_CMD` propagates a nonzero setup exit. `bash -n` passes.
- No size evidence is produced yet. Next slice must replace the startup
  report with measurement and pure validation tests. Design unchanged.

## 2026-10-23 — Slice 2: measurement and validation

- Added eight symmetric declarations and checked real generated layouts
  without an agent. Pure validation selects exact class name and Java
  thread ID; missing/extra records, freed targets, invalid sizes and
  differing sizes fail with the original evidence in exception data.
- Tracing retains ten constructed objects through record collection and
  uses a reachability fence. Raw evidence includes supplied values and
  all allocations, not only targets. Main prints raw evidence before
  result validation; slice 3 must replace this diagnostic stream with
  durable EDN output before validation and add environment metadata.
- Focused Scry CLI verification: exit 0, four tests, 24 assertions;
  `.scry-results/` empty. Tests use data and real generated classes,
  without mocks or a native agent.
- Startup-wrapper smoke run: exit 0 on the selected Valhalla JDK; all
  eight cases passed ten-record validation. Diagnostic rows were
  identity `[16 24 32 40]`, value `[16 32 40 48]`. Local raw output is
  `target/object-size/slice-2.log`; this is not publication evidence.
  Design unchanged. No dependencies added in this slice.

## 2026-10-23 — Slice 3: reproducible evidence

- Metadata reads the resolved artifact's Maven properties and rejects any
  version other than `0.5.251-ALPHA`. Agent SHA-256 is computed from the
  extracted binary. VM options include their origins; absent options are
  explicitly unknown. No dependency or design change.
- `raw.edn` contains environment, selection rules and all case records;
  it is written before validation. `results.edn` contains the same metadata,
  validated case details and four rows. Logs include setup and measurement.
- Focused Scry: exit 0, five tests, 30 assertions; no failure files.
  Real JVM metadata tests need no attached agent. Invalid `JAVA_CMD`
  exits nonzero, removes prior raw/results files and retains stderr.
  Shell syntax check passed.
- Final native run: exit 0; eight cases, ten target records each. Rows
  are identity `[16 24 32 40]`, value `[16 32 40 48]`, delta `[0 8 8 8]`.
  All four preserved files compare byte-for-byte with the run output.
  Run timestamp: `2026-10-23T23:13:22.658960Z`.
- Next slice: publish the report and README command, then run the full
  suite. Exact command used: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java bash bench/object-size.sh`.
  Alignment is 8; compressed oops and class pointers are enabled;
  compact headers are disabled. Do not infer a header layout from deltas.

## 2026-10-23 — Slice 4: report and regression

- Published the report from the preserved slice-3 evidence; no rerun or
  replacement of measurements was needed. Header contents and field
  offsets remain unknown; the +8-byte difference does not identify its
  cause. No code, API or dependency changes in this slice.
- Full Scry CLI run on the selected Valhalla JDK: exit 0, 64 tests,
  332 assertions. Focused `:test:object-size` run: exit 0, five tests,
  30 assertions. Both left `.scry-results/` empty; neither attached an
  agent. Existing data-based and real-class tests cover the contract;
  documentation changes need no new tests.
- Acceptance evidence: (1) README command and launcher; (2) report table
  and preserved `results.edn`/`raw.edn`; (3) eight class/field/value-status
  records, ten targets per case, and report semantics; (4) report and
  EDN environment/agent/VM metadata; (5) bounded interpretation and
  design-index/README links; (6) passing regression suite and unchanged
  library and timing benchmark.
- Diff against pre-implementation commit `6aef179` confirms no changes
  to `src/`, `bench/value_type/bench.clj` or historical `benchmark.md`.
  The only `deps.edn` change is the isolated `:object-size` alias;
  `:bench` is unchanged. `git diff --check` passes.
- All implementation slices are complete. The task remains open for
  implementation review; no blocker or follow-up implementation item.

## 2026-10-23 — Implementation review

- No new actionable findings.

## 2026-10-23 — Test review

- Added 4 steps to be addressed.

## 2026-10-23 — Review follow-ups

- Addressed layout review step. Focused Scry: 5 tests, 50 assertions, exit 0.
- Addressed complete-row review step. Focused Scry: 5 tests, 49 assertions, exit 0.
- Addressed output-lifecycle review step. Publication uses real temporary files and synthetic evidence. Explicit shell integration checks cover setup failure, measurement failure without an agent, cleanup and native success. Focused Scry: 6 tests, 56 assertions, exit 0; shell checks passed.
- Addressed metadata review step: pure state validation is separate from real extraction/JVM reads. Missing agent/version states fail; unavailable VM options are explicit. Focused Scry: 6 tests, 60 assertions. Explicit metadata integration: 1 test, 6 assertions. Native lifecycle checks passed. Full regression: 64 tests, 332 assertions. All exits 0; no Scry failure files. All 4 review steps addressed; design unchanged.

## 2026-10-23 — Measurement-evidence test review

- Added 1 step to be addressed.

## 2026-10-23 — Measurement-evidence follow-up

- Addressed 1 review step. Native lifecycle and EDN contract checks passed
  (1 test, 99 assertions). Removing `:values` from a temporary evidence copy
  caused the expected nonzero exit (16 assertion failures). Focused tests
  passed (6 tests, 60 assertions); no Scry failure files remain.
- Production code, design and preserved measurement evidence are unchanged.

## 2026-10-23 — Published-environment test review

- Added 1 step to be addressed.

## 2026-10-23 — Published-environment follow-up

- Addressed 1 review step. Native lifecycle/evidence checks passed (1 test,
  125 assertions). Removing environment metadata from both temporary EDN
  copies produced 17 assertion failures and no errors. Focused tests passed
  (6 tests, 60 assertions); no Scry failure files remain.
- Production code, design and preserved evidence are unchanged.

## 2026-10-23 — Final test review

- No new actionable test findings.

## 2026-10-23 — Documentation review

- No new actionable documentation findings.

## 2026-10-23 — Code-shaper review

- No new actionable code-shaping findings.

## 2026-10-23 — Closure

Closed at the user's request after implementation, all reviews and
knowledge extraction. All checklist items are complete. Knowledge was
updated in `mementum/knowledge/benchmarking-value-types.md` (commit
`faeb15b`). Removed the task from `munera/plan.md`. No tests or
measurements were rerun for this task-record change.
