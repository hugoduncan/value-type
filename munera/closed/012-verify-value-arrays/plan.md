# Plan: verify value arrays

## Approach

Use [design.md](design.md) as the acceptance boundary. Verify the internal JVM
API directly, outside production namespaces. A rejected assumption is a valid
finding. Do not implement the public array API or select new public errors.

Create an isolated probe in `spike/value-array-semantics/`, with a shell runner,
small Clojure cases, a Java array-access helper and reproduction instructions.
Reuse the structure, not the benchmark dependencies or assertions, of
`spike/typed-array/`. Leave historical probes and measurements unchanged.
Use the existing project classpath; do not add a general experiment framework.

Write disposable output under `target/value-array-semantics/`. Retain the
reviewed machine-readable results, process logs, commands and environment
records under `ramora/value-type-array-design/evidence/`. Write the report at
`ramora/value-type-array-design/semantic-verification.md`. Link each finding
to stable case identifiers and evidence files.

### Runtime and evidence boundary

Use `~/.local/jdks/jdk-27.jdk/Contents/Home` as the primary JDK. Record the actual
build, Clojure version, OS, architecture, executable paths and exact flags.
Locate and record an ordinary Java 8 installation for the unsupported control.
If either required runtime is unavailable, record the obstacle; do not replace
it silently or mark its cases verified.

Discover allocator and diagnostic signatures before writing invocation cases.
Keep the control entry point free of eager optional JDK imports. Isolate
value-class definitions and supported-runtime helpers so Java 8 and disabled
preview can report capability failures rather than fail before recording them.
Keep discovery, access, component eligibility and argument failures separate.
Preserve exception classes, messages, cause chains, raw output and exit status.
Do not add module access at runtime.

Use a bounded matrix:

- Four fresh Valhalla JVMs cover preview on/off crossed with exports
  present/absent. The preview-on/exported run is the semantic baseline.
- A fresh preview-on/exported JVM disables array flattening with
  `-XX:-UseArrayFlattening`, if supported. Compare semantics, not timing.
- A fresh Java 8 JVM records API absence without Valhalla-only startup flags.

Run the full semantic cases in the baseline and flattening-disabled runs.
Other configurations probe discovery and access independently and record
which later cases cannot run. They must not disguise fallback identity classes
as value-class evidence. An unavailable flag or query is a recorded result,
not a false diagnostic value.

### Semantic cases

Use zero-field, one-long, two-long and one-Object-field `defvaluetype` classes.
Use identity and primitive components as rejection controls. Record component
value status, allocation eligibility and each layout diagnostic separately.

For each value component, test lengths 0, 1 and 3 with a valid explicit seed,
null seed and incompatible seed. Test length -1 separately with a valid seed.
Inspect available signatures and matching JDK sources or documentation for a
seed-free empty route in the selected atomic API; invoke it only if available.
Do not allocate near heap or JVM length limits.

Check all initial slots, replacement with another valid value, and null and
wrong-type stores through Clojure operations and ordinary Java reference-array
access. Verify contents after both accepted and rejected writes. A zero-field
value cannot demonstrate distinct payload replacement; other fixtures do.
Read and store cases apply only to existing slots; length-zero cases check
allocation, seed validation, length and diagnostics, not indexed access.
Include reference-field values with null and non-null fields without confusing
a null field with a null element.

Query diagnostics independently for ordinary Object, identity-component and
value-component arrays, restricted arrays, primitive arrays, null and a non-array
object. Preserve each returned value or failure, including partial capability.
Use matching source or documentation for atomic-access meaning; sequential
probes cannot prove concurrent behavior or a synchronization protocol.

## Risks

- EA signatures, access rules or layout flags can differ from prior evidence.
  Record the installed build and inspect it before selecting calls.
- Fixture loading can hide the actual failure boundary. Separate safe startup,
  discovery, value-class definition, allocation and inspection results.
- Restricted atomic allocation may reject wider or empty cases. Surface any
  resulting implementation gate rather than add fallback or weaken the contract.
- Source evidence may be unavailable or may not match the installed build.
  Label mechanism explanations unresolved when only outcomes are established.
- Flattening is not a semantic requirement. Do not import size or speed targets
  from the one-long benchmark, or claim benefits for unmeasured layouts.
- Evidence under `target/` can be deleted by builds. Retain reviewed evidence
  before cleanup. Shared dependency or test changes should not be needed;
  if introduced, run their affected checks and record results.

## Slice order

1. Establish runtime provenance, direct API signatures and the evidence format.
2. Implement allocation, seed, store and diagnostic cases with stable identifiers.
3. Run fresh-JVM controls, validate coverage and retain reproducible evidence.
4. Explain findings, update evidence-backed design gates and check documentation.

Append decisions, obstacles, commands and verification results to
[implementation.md](implementation.md) during execution. Update the checklist
and commit each completed implementation slice.

## Planning review

The design supplies intent, scope, target runtime, questions and acceptance.
No blocking ambiguity or internal inconsistency was found. Unknown seed,
eligibility and diagnostic behavior is experiment input, not missing scope.

The referenced architecture verification list also covers later constructors,
sequence realization, adapters, fallback policies and packaged consumers.
Those checks are not task 012 acceptance. The historical spike's timing and
size checks are also outside this task. This distinction agrees with the
existing implementation review notes. Proposed architecture claims remain
proposals until supported by the new evidence.

The plan ambiguity review treated `steps.md` as read-only context. Indexed
access checks apply only to non-empty arrays. The slice 3 reproduction uses
the runner's recorded commands; slice 4 turns those commands into the README.
No unresolved planning ambiguity remains. The design consistency review found
no conflict with the array decisions, verification assumptions, fallback
boundary or historical typed-array report.

Slice 1 established that diagnostic access can succeed with preview disabled.
Keep this separate from value-class and allocation capability in slice 2.
The installed source archive supplies Java declarations, not native mechanism proof.

Slice 2a covers allocation and initial contents only. The remaining slice 2 work
is stores, Java array access and layout diagnostics. EA3 reports primitive long
as a value class but rejects it as an array component with a boxed seed; keep
value status distinct from eligibility in later assertions and reports.

Slice 2b adds direct Clojure and Java stores with fresh arrays per case.
Both access paths check all slots before and after writes. Diagnostics remain
the last slice 2 item; full startup and flattening controls remain slice 3.

Slice 2c isolates invalid diagnostic arguments in fresh JVMs: null crashes all
three native queries on EA3. Never include null in the shared semantic process.
Retain fatal logs and process status, not a fabricated exception or false result.
The matrix validator must account for these fatal observations separately.

Slice 3a adds a reusable isolated-process validator and fatal EDN observations.
The configuration matrix must integrate this validator; it must not infer full
matrix coverage from the six isolated baseline outcomes.

Slice 3b runs both semantic configurations through `semantics.sh`, including
isolated-process validation. Compare exception classes, not message text: EA3
changes null/type store messages when flattening is disabled. Preserve the raw
messages. The remaining matrix work is startup controls and aggregate coverage;
the two-layout comparison does not satisfy those gates.

Slice 3c adds independently validated startup controls. Strict fallback rejects
value fixture creation without preview or on Java 8; dependent allocation is
unresolved, not a native allocation rejection. With preview and no exports,
value creation works but allocator invocation fails. Aggregate integration must
combine these records with the two-layout semantic and isolated fatal records.

Slice 3d combines startup and both semantic layouts with `matrix.sh` and an
aggregate validator. Two complete unchanged-source runs have identical semantic
summaries and source hashes. Raw fatal records stay separate from API rejections;
unresolved startup dependencies remain explicit. Slice 4 is next.

Slice 4a documents reproduction and expected failures in the probe README.
Retained matrix revalidation passes. The report, architecture gates, links and
acceptance mapping remain the next slice; no design scope change is needed.

Slice 4b supplies the six-question semantic report and atomic-access limits.
Architecture corrections, index links and final acceptance mapping remain.
No further runtime experiments are needed for the report’s scoped findings.

Slice 4c completes evidence-backed architecture gates and acceptance mapping.
Direct-JVM questions are resolved within the tested scope; native mechanisms
remain explicitly unresolved, not public API blockers. Public implementation
and its regression checks remain separate. No task design scope change.

Review follow-ups reuse fixture-free semantic contracts for live and retained
results. Copy-based mutation checks must reject invalid evidence. Separate
isolated-record creation from read-only validation in the next review step.

Both review follow-ups are complete. Isolated records are created only by the
probe runner; retained validation compares records with raw evidence without
writes. Matrix references are portable. Six copy-based regression checks pass.

The test review follow-up checks one selected index per fresh array, including
independent rejected writes at every index. Both layouts now require 120 store
cases. The report and validator use `evidence/matrix-selected-slots/`; historical
evidence remains unchanged.

Isolated-process review follow-up is complete. Aggregate validation checks each
raw-matched isolated record against its expected runtime and layout flags.
Copy-based checks mutate each process independently and verify read-only behavior.

Code-shape follow-up separates reusable helper loading from explicit probe
execution. Entry points retain allocation-first checks and independent discovery;
source hashes include helper files. Fresh-JVM and retained-evidence checks pass.
