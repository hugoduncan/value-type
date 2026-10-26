# Steps: verify value arrays

## Slice 1: runtime provenance and API discovery

- [x] Record the primary Valhalla and Java 8 executable paths and actual versions in `implementation.md`; record any missing runtime as an obstacle without substitution.
- [x] Create `spike/value-array-semantics/` with a safe discovery entry point that runs on Java 8 without eager optional JDK references; verify that it emits an API-presence result.
- [x] Capture allocation and diagnostic method signatures and access requirements from the installed EA build; identify matching source or documentation and record any source-version uncertainty.
- [x] Define stable case identifiers and an EDN result format with environment, flags, operation, inputs, observed value or exception causes, and pass/rejection/unresolved status; keep coverage failures distinct from expected rejections.

## Slice 2: direct semantic cases

- [x] Add isolated zero-field, one-long, two-long and one-Object-field value fixtures plus identity and primitive controls; record actual component value status before allocation.
- [x] Add direct atomic-allocation cases for every value component at lengths 0, 1 and 3 with valid, null and incompatible seeds; retain outcomes independently of flattening.
- [x] Add identity-component, primitive-component and negative-length rejection cases with otherwise suitable arguments; retain native exception and cause information.
- [x] Check the selected atomic API for a seed-free empty route using signatures and matching source or documentation; probe any available route and record absence or unresolved evidence otherwise.
- [x] Add Clojure `alength`, `aget` and `aset` cases that check every initial slot, valid replacement and null/wrong-type rejection for accepted arrays, including unchanged contents after rejected writes.
- [x] Add a small Java helper using ordinary reference-array length, reads and stores; check the same accepted-array contents and store restrictions without using reflection as the access path.
- [x] Add null-field and non-null-field reference payload cases to distinguish valid values containing null from forbidden null array elements.
- [x] Add independent calls to each diagnostic for ordinary Object, identity and value arrays, restricted arrays, primitive arrays, null and a non-array object; retain false values separately from unavailable queries and failures.

## Slice 3: fresh-JVM matrix and retained evidence

- [x] Add isolated null-diagnostic fatal outcome records and a process validator; never run them in the shared semantic JVM.
- [x] Integrate the isolated-process validator into the full configuration matrix.

- [x] Add a runner for all four preview/export combinations, a preview-on/exported flattening-disabled run and an ordinary Java 8 control; save exact commands, environment, exit statuses and stdout/stderr for each process, including expected failures.
- [x] Run the full semantic suite with preview and exports enabled; verify that each planned case has an explicit outcome and each accepted array has separate value-status, null-restriction, atomicity and flattening observations.
- [x] Run the other three preview/export combinations and Java 8 discovery; verify that access denial, API absence and disabled-preview effects remain distinct, with blocked dependent cases explicitly unresolved.
- [x] Run the full semantic suite with array flattening disabled where supported; compare contents and store restrictions with the baseline and record layout changes separately, or retain the unsupported-flag diagnostic.
- [x] Add and run a bounded result validator that checks required configuration/case coverage and semantic assertions, rejects missing results or unexpected process failures, and lists unresolved cases without marking them verified.
- [x] Repeat the documented matrix from unchanged probe sources into a fresh output directory; compare semantic outcomes and investigate unexplained differences before selecting retained evidence.
- [x] Copy the reviewed EDN results, raw logs, environment records, exact commands and source revision or content hashes to `ramora/value-type-array-design/evidence/`; verify that the retained set matches the report inputs.

## Slice 4: explanation and design gates

- [x] Write `spike/value-array-semantics/README.md` with prerequisites, exact reproduction and validation commands, output locations and expected-failure handling; check the commands against the completed runs.
- [x] Write `ramora/value-type-array-design/semantic-verification.md` with an answer to each of the six design questions and links to case identifiers, evidence and matching source where needed; label unsupported mechanisms unresolved.
- [x] Explain mutable slots, immutable value objects and atomic element access in the report without claiming compound-update safety or synchronization; bound the historical one-long storage/timing claims to their measured setup.
- [x] Update the proposed array design, decisions and verification assumptions only where evidence resolves or contradicts them; distinguish JVM outcomes from proposed public validation and name each remaining implementation blocker and required decision or experiment.
- [x] Link the report, runnable instructions and retained evidence from the array design and `ramora/INDEX.md`; verify changed Markdown links and anchors and run `git diff --check`.
- [x] Review the final diff for unchanged production code, runtime behavior and historical measurements; run affected checks if shared test or dependency configuration changed and append results to `implementation.md`.
- [x] Map all seven acceptance criteria to retained evidence or explicit unresolved findings in `implementation.md`; update task progress and working-memory records without claiming unrun cases passed.

## Implementation review follow-ups

- [x] Make retained-matrix validation check the required case IDs and semantic expectations, not only counts, unique IDs and agreement between layouts. Reuse the allocation, store and diagnostic assertions without loading JVM fixtures; also verify startup runtime versions and preview/export flags against each configuration. In a temporary copy, changing `:diagnostic/long-restricted-0-isNullRestrictedArray` to false in both layouts still lets `validate_matrix.clj` exit zero. Add negative checks for this mutation, a renamed required case and incorrect startup flags; each must fail while unchanged evidence passes.
- [x] Make retained-evidence validation read-only. `validate_matrix.clj` invokes `validate_isolated.clj`, which overwrites each `isolated.edn` and changes `:fatal-log` paths to the supplied root. Separate record creation from validation, use portable evidence references, and check existing records against raw process evidence rather than replacing them. Verify that validation leaves all evidence bytes unchanged and rejects a corrupted isolated record.

## Test review follow-up

- [x] Verify that stores change only the selected slot. In `spike/value-array-semantics/stores.clj`, valid cases write the same replacement to every slot before checking contents; null and wrong-type cases stop at the first exception, so they attempt only index zero. For length-three arrays, write one selected slot and check that the other slots retain their previous values. Attempt rejected writes independently at each index and check all contents after each attempt. Cover both Clojure and Java access and both flattening configurations, retain the new observations, and update the evidence counts and report as needed.

## Test review: isolated process configuration

- [x] Validate the runtime and flags of every isolated diagnostic process against its matrix layout. `isolated_evidence.clj` requires only a non-empty environment; `validate_matrix.clj` does not check isolated-process configuration. In a temporary copy of `evidence/matrix-selected-slots/`, replacing all six `semantics/disabled/diagnostics/{null,non-array}-*` directories and `isolated.edn` with their default-layout counterparts still passes aggregate validation. Require the recorded EA3 runtime, preview and exports, and require disabled flattening only in the disabled layout. Add copy-based regression checks for substituted layout evidence and incorrect isolated runtime/flags; unchanged evidence must pass and validation must remain read-only.

## Test review: startup allocation status

- [x] Require `:pass` status for the baseline allocation in `spike/value-array-semantics/validate_startup.clj`, in addition to its expected value. In a temporary copy of `evidence/matrix-selected-slots/`, changing only `:startup/allocation` in `startup/baseline.edn` from `:pass` to `:unresolved` still lets aggregate validation exit zero. Add copy-based regression checks that reject unresolved or rejected baseline allocation records even when the expected value remains; unchanged evidence must pass and validation must remain read-only.

## Test review: current evidence links

- [x] Update the current-evidence links in `ramora/INDEX.md`, `ramora/value-type-array-design.md` and `ramora/value-type-array-design/verification.md` from `evidence/matrix/` to `evidence/matrix-selected-slots/`. They still present the historical 60-store-case matrix as task 012 evidence; the current `validate_matrix.clj` rejects it because 120 selected-slot cases are required. Preserve historical evidence and label any retained historical links. Check that the entry points lead to the same evidence as the probe README and semantic report, and that the linked current matrix passes validation.

## Test review: discovered signatures

- [x] Validate the discovered API signatures in `spike/value-array-semantics/validate_startup.clj`, not only their `:pass` status. Changing `:discovery/signatures` to an empty `:value` in all four Valhalla startup records still passes aggregate validation. Require the selected allocator's parameter and return types and all three diagnostic signatures. Check the reported absence of a seed-free overload for the selected atomic allocator, without rejecting unrelated API methods. Add copy-based regression checks for missing or changed required signatures and a seed-free overload that contradicts the report; unchanged evidence must pass and validation must remain read-only.

## Documentation review follow-ups

- [x] Correct the result-format description in `spike/value-array-semantics/README.md`. Store and diagnostic result files have no top-level `:environment`; their process environment is in the adjacent `allocation.edn`. Isolated records put `:environment` on each case, and unresolved startup cases can omit `:inputs`. Describe these variants and their provenance links instead of claiming one uniform shape. Check the description against the writers and retained selected-slot matrix.
- [x] Add an Unreleased note to `CHANGELOG.md` for the new value-array probe and validation commands, with a link to `spike/value-array-semantics/README.md`. State that this is experimental JVM verification, not a public array API, and mention that reproduction deliberately crashes isolated JVMs for null diagnostic inputs. The new contributor commands are user-visible even though production behavior is unchanged.

## Code-shape review follow-up

- [x] Separate reusable probe helpers from script execution in `spike/value-array-semantics/`. `stores.clj` and `diagnostics.clj` load `allocation.clj` for fixtures and helpers, but loading also runs 59 cases and writes the path in the caller's `*command-line-args*`. `startup.clj` similarly loads `discover.clj`, which writes discovery evidence during loading. Make execution and output paths explicit at the entry points, and keep reusable helper loading free of probe execution and file writes. Preserve the intended allocation-before-store/diagnostic checks, independent discovery evidence, Java 8-safe startup boundary and current case contracts; do not add a general framework. Check helper loading without CLI arguments, rerun affected fresh-JVM entry points, and run retained-evidence regression checks. Preserve historical evidence.
