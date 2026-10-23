# Plan

## Approach

Implement a manual shallow-size comparison under `bench/`. Do not
change `src/`, the existing `:bench` alias, or task 006 results.

### Measurement route

Use the published `org.hugoduncan/criterium` `0.5.251-ALPHA` artifact
from Clojars in a new `:object-size` alias. Add `bench` to its paths
and `--enable-preview` to its JVM options. Keep Clojure 1.12.6.

Use `criterium.agent/extract-agent!` to extract its bundled native
agent. Load that agent at JVM startup with `-agentpath:<absolute-path>`.
Require `criterium.agent/attached?` before any measurement. Use
`criterium.agent/with-allocation-tracing`; read per-object
`:object_size`, not allocation totals or timing statistics.

Planning verified this route on macOS/aarch64 with Valhalla
`27-jep401ea3+1-1`. All eight classes produced ten target records each,
with consistent sizes within each class. See `implementation.md` for
probe details and limitations. These are feasibility observations,
not the final measurement report.

The direct `criterium.agent.instrumentation/object-size` function is
not selected. The published JAR has no agent manifest entry or compiled
instrumentation agent class; its `-premain` does not store the supplied
instrumentation. Packaging a custom agent is unnecessary when the
bundled native route works.

JVMTI `SampledObjectAlloc.size` is the allocated object's size in bytes
and refers to `GetObjectSize` semantics. Treat it as the VM's shallow
size, including its header and padding, not an exact portable layout
specification. Preserve this qualification in the results.

### Runner and evidence

- Add `bench/object-size.sh` as the single entry command, run from the
  repository root: `bash bench/object-size.sh`. It uses `JAVA_CMD`,
  resolves the isolated alias, extracts the agent in a setup JVM, then
  starts the measurement JVM with the same alias and `-agentpath`.
  Reject an absent agent path and propagate child failures. Do not use
  runtime attachment, blackhole options, or a locally built agent.
- Add `bench/value_type/object_size.clj` (`value-type.object-size`).
  Declare eight top-level types: paired empty, one-, two- and
  three-`long`-field types. Within a pair, change only macro and name.
  Use fields `f0`, `f1`, `f2` in that order. For sample index `i` in
  0–9, supply `long(i + field-index)` to both kinds.
- Before tracing, validate non-static reflected fields by name and
  primitive type, their expected count, and `Class.isValue` for both
  kinds. Do not rely on reflection's field enumeration order. Declarations
  establish field order. Do not add measurement interfaces or fields.
- Trace each case separately on the calling thread. Retain ten freshly
  constructed objects in a vector through collection of the records;
  use `Reference.reachabilityFence` after inspection. Do not test value
  object identity to establish separate allocations.
- Select records by exact target class name and allocating thread.
  Preserve all records, including unrelated allocations, as raw evidence.
  Require ten target records with positive integer sizes and no freed
  targets. Require one common size per class. On missing records, wrong
  layout, agent failure or size discrepancies, retain diagnostics and
  fail; do not average, substitute zero, or silently retry.
- Write `target/object-size/results.edn`, `raw.edn`, and separate
  `stdout.log`/`stderr.log`. The EDN records identify classes, fields,
  supplied values, sample counts, selection criteria and observations.
  Successful results contain four ordered rows with field count,
  `deftype` bytes, `defvaluetype` bytes and signed difference
  (`defvaluetype` minus `deftype`).
- Capture environment data from the measurement JVM: JDK/VM build,
  OS/version, architecture, Clojure and resolved Criterium versions,
  JVM input arguments, agent path and bundled hash. Query available VM
  options for object alignment, compressed oops, compressed class
  pointers and compact headers. Mark unavailable options as unknown;
  do not change them to obtain a desired result.
- Keep pure result validation tests in
  `bench/value_type/object_size_test.clj`, outside default test discovery.
  Run them explicitly with `clojure -M:test:object-size -m scry.cli
  --namespace value-type.object-size-test`. The namespace must load
  without an attached agent; check attachment only at measurement entry.
  Tests check evidence contracts, not fixed byte sizes.
- Before each run, clear only the runner-owned output files. Save raw
  records and diagnostics before validating results; write `results.edn`
  only after all cases pass. This prevents stale results after failure.

## Risks

- Native agent support is platform- and build-specific. The verified
  platform is macOS/aarch64 only. Extraction or startup can fail due to
  permissions or an unsupported platform. Fail with the cause.
- Sampling, JIT elimination or value-object reuse can reduce record
  counts, especially for empty values. The probe passed, but the final
  runner must enforce its own evidence checks. A failed check blocks
  publication; diagnose it rather than choose convenient records.
- Criterium is an alpha release. Its warnings or absent records must not
  become valid output. Keep its record handling at one boundary.
- Report observed size differences without claiming an unverified
  header layout or a general size saving. Unknown VM details are valid
  if identified explicitly.
- If the verified route fails in the implementation, record evidence
  and stop for a decision before changing library, version or method.
  There is no current blocker.

## Slice order

1. **Isolated launch:** dependency alias, agent extraction and startup
   wrapper. Verify attachment without changing the timing benchmark.
2. **Measurement and validation:** paired types, reflection checks,
   retained samples, raw records, result validation and focused tests.
3. **Reproducible evidence:** metadata and file output; run all eight
   cases with the documented command and preserve the complete run.
4. **Documentation and regression:** publish
   `ramora/defvaluetype-design/object-size.md` and evidence under
   `ramora/defvaluetype-design/object-size/`; link the report from the
   design index, add the README command, and run the existing suite.

## Current implementation

Slices 1–4 are complete. The runner writes raw EDN before result validation
and publishes validated cases and rows with measurement-JVM metadata.
The wrapper clears only its four output files before setup and redirects
both JVMs into separate logs. The successful run is preserved under
`ramora/defvaluetype-design/object-size/`. The report and README command are published. Full regression and focused
validation tests pass without an attached agent.

Review follow-ups add a state-based publication boundary with isolated filesystem
tests. Run native lifecycle checks explicitly with
`JAVA_CMD=<valhalla-jdk-home>/bin/java bash bench/object-size-integration-test.sh`.
Metadata tests use state inputs. Real extraction/JVM checks are separate:
`clojure -M:test:object-size -m scry.cli --namespace value-type.object-size-integration-test`
with the selected `JAVA_CMD`. No attached agent is required for this metadata check.

The native lifecycle script also runs `value-type.object-size-evidence-test`
against its temporary output. This checks all eight cases, layouts, supplied
values, target records and four result rows without fixed byte sizes.

The native evidence test also checks matching environment metadata in both
EDN files, required versions and system properties, startup arguments, agent
path/hash, and all four VM settings with explicit unknowns permitted.
