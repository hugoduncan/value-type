# Implementation steps

## Slice 1 — Isolated launch

- [x] Add `:object-size` to `deps.edn` with the `bench` path, Criterium `0.5.251-ALPHA` from Clojars and `--enable-preview`; verify the resolved version without changing `:bench`.
- [x] Add `bench/object-size.sh` to extract the bundled agent with `criterium.agent/extract-agent!` using `JAVA_CMD`; reject a missing path and propagate setup errors.
- [x] Add the `value-type.object-size` entry point and launch it from the wrapper with startup `-agentpath`; verify `criterium.agent/attached?` succeeds on the selected Valhalla JDK and fails clearly without the agent.

## Slice 2 — Measurement and validation

- [x] Declare four matched `deftype`/`defvaluetype` pairs in `bench/value_type/object_size.clj`, with 0–3 primitive `long` fields and no added measurement fields or interfaces; check declaration symmetry.
- [x] Add pre-measurement validation for each class's non-static field names, primitive types, count and value-class status; verify all eight classes pass.
- [x] Add ten-object construction per case with matching values from the plan and keep the resulting vector reachable through record collection with `Reference.reachabilityFence`.
- [x] Trace each case with `criterium.agent/with-allocation-tracing` and preserve all returned records; verify exact class-name and allocating-thread selection excludes unrelated allocations.
- [x] Add result validation that requires ten non-freed target records, positive integer byte sizes and one common size per case; make missing records or discrepancies fail with their evidence.
- [x] Add four-row result assembly ordered by field count, with both byte sizes and signed `defvaluetype`-minus-`deftype` differences; verify positive, zero and negative differences.
- [x] Add focused tests in `bench/value_type/object_size_test.clj` for record selection, count/size/freed-record failures, discrepancy reporting and ordered signed differences, using data fixtures without a native agent or fixed VM byte-size expectations.
- [x] Run those tests with Scry under `:test:object-size`; verify the structured result passes.

## Slice 3 — Reproducible evidence

- [x] Add measurement-JVM metadata capture for JDK/VM, OS/version, architecture, Clojure/Criterium versions, JVM options and agent path/hash; verify the resolved artifact is the pinned release.
- [x] Capture available alignment, compressed-reference, compressed-class-pointer and compact-header VM settings; record unavailable settings as unknown.
- [x] Add EDN output at `target/object-size/results.edn` and `raw.edn`, including classes, fields, supplied values, sample counts and selection criteria; retain raw evidence before validation can fail.
- [x] Complete wrapper output handling for separate `stdout.log` and `stderr.log` under `target/object-size/`; verify fresh runs cannot expose stale success results and child failures return nonzero.
- [x] Run `JAVA_CMD=<valhalla-jdk-home>/bin/java bash bench/object-size.sh` from the repository root; verify eight valid cases, ten matched records per case and four ordered result rows.
- [x] Copy the successful run's EDN and logs into `ramora/defvaluetype-design/object-size/`; verify the preserved files match that run, not the planning probe.

## Slice 4 — Documentation and regression

- [x] Write `ramora/defvaluetype-design/object-size.md` with the exact command, environment, agent setup, JVM settings, measurement semantics, raw-evidence links and four-row byte table.
- [x] Explain the observed differences and evidence limits in the report without assuming savings or asserting an unverified header layout; explicitly restrict conclusions to standalone objects with this field layout and VM setup.
- [x] Link the size report from `ramora/defvaluetype-design.md` without replacing the historical timing report or its version labels.
- [x] Add the runnable size command, JDK prerequisite and output locations to README.
- [x] Run the existing suite with `JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -M:test -m scry.cli`; verify the exit code and structured Scry results, with no native agent required.
- [x] Check the final diff leaves `src/`, the `:bench` configuration, `bench/value_type/bench.clj` and historical timing results unchanged; map all six acceptance criteria to the final evidence in `implementation.md`.

## Test review follow-up

- [x] Extend `layouts-test` in `bench/value_type/object_size_test.clj` to check layouts by kind and field count, not by the private case-vector order. Add rejection cases for wrong field names, non-`long` fields, wrong field count and wrong value-class status, using real classes and input data. The current success-only test would pass if `class-evidence` stopped rejecting invalid layouts.
- [x] Extend `rows-test` to assert complete output rows, including both byte columns. It currently checks only row order and differences; missing or equally shifted byte columns would pass despite violating the results contract. Keep the sizes synthetic, not tied to this VM.
- [x] Add repeatable tests for the runner's output lifecycle: successful EDN publication, raw evidence retained on validation failure, no success file after failure, stale runner files removed, unrelated files preserved, and nonzero setup/measurement exits. Current coverage is manual only. Use state inputs and isolated filesystem state; do not mock process calls or add test-only production options.
- [x] Make metadata tests independent of native-agent extraction and host filesystem state. `environment-test` calls `environment-data`, which extracts a platform-specific binary and reads it to compute a hash; these infrastructure dependencies are neither injectable nor nullable. Separate metadata logic from infrastructure, inject state rather than behavior, and test missing agent/version/VM-option states without mocks or stubs. Keep real extraction and JVM checks as explicit narrow integration tests.

## Test review — measurement evidence

- [x] Extend the native success check in `bench/object-size-integration-test.sh` to read and assert the generated EDN contract, not only nonempty files. Check all eight kind/field-count cases, exact target classes, field/value-class evidence, ten supplied value vectors following `long(i + field-index)`, matching values within each pair, and four ordered rows consistent with raw target sizes and signed differences. Do not fix expected byte sizes to this VM. Current pure tests use synthetic evidence and the native check only checks file existence; removing `:values` from `trace-case`, for example, would pass all current tests while losing required measurement evidence. Use the real run's output, without mocks or test-only production options.

## Test review — published environment

- [x] Extend `bench/value_type/object_size_evidence_test.clj` to check the environment metadata in the real launcher's `raw.edn` and `results.edn`. Require matching metadata in both files, JDK/VM build, OS and architecture, Clojure and pinned Criterium versions, startup agent/preview arguments, agent path/hash, and the four VM settings (values or explicit unknowns). Do not hard-code this host's build, paths, hash or option values. The native test currently ignores `:environment`; the separate metadata integration test checks the reader, not its use by `-main`. Omitting environment metadata at the measurement entry point would pass the current tests but violate acceptance criterion 4. Use the existing real-run files, without mocks or test-only production options.
