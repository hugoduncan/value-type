# Implementation checklist

Follow [plan.md](plan.md). Append dated evidence to `implementation.md` and
commit each completed step. Keep unverified work unchecked.

## Slice 1 — Reflective baseline

- [x] Resolve the facts-selected baseline and Valhalla executables and Valhalla tools; record exact builds, host, profiles and the unchanged production revision in `implementation.md`.
- [x] Add `dev/bench/array-adapter/` measurement support with the cases, fork counts, time bounds, result consumption and raw-output schema specified in the plan; verify sample counts and time-per-operation normalization.
- [x] Run the measurement support against the unchanged reflective implementation; retain raw samples, commands, setup timings and a summary under this task directory.

## Slice 2 — Direct allocation

- [x] Add `value-type.impl.array-allocation` with typed direct `value-class?` and `allocate` functions; compile it on EA3 with preview and exports and require no reflection warnings.
- [x] Change allocation discovery in `value-type.impl.array` to resolve and cache allocation functions only after existing prerequisite and access checks; keep adapter loading outside classified-absence catches.
- [x] Change `passes-round-trip?`, `value-class?` and `allocate` to invoke selected functions; preserve semantic rejection data and update `test/runtime/value_type/impl/array_test.clj` for the function contract.
- [x] Run the probe and allocator self-check tests, including the constant-value probe and throwing constructor; verify false checks classify as before and unexpected exceptions propagate.
- [x] Run allocation, layout and both sequence-arity runtime tests; verify validation precedence, single traversal, ordered contents and unchanged error data.
- [x] Verify the real negative-length adapter regression throws its native cause and a later public allocation succeeds with the same successful capability result.

## Slice 3 — Independent direct inspection

- [x] Add `value-type.impl.array-inspection` with a typed component predicate and three direct diagnostic calls; compile and inspect an Object array on EA3 with exports but no preview.
- [x] Change inspection discovery to select its cached inspection function after its own access checks, outside absence-classification catches; route the baseline wrapper to it and remove obsolete reflective invocation code.
- [x] Extend fresh-process boundary tests to observe optional namespace and delay state: inspection leaves allocation and value-class discovery unrealized; allocation leaves the inspection namespace unloaded.
- [x] Extend fresh-process switch tests to prove `value-type.value-classes=off` blocks allocation while ordinary inspection works with exports; verify reports retain their existing results.
- [x] Run disposable diagnostic-input tests for null, non-arrays and primitive arrays; require complete structured results and normal exit before running shared diagnostic tests.
- [x] Run valid-array inspection comparisons against the independent JDK oracle, including empty, nested and non-flat arrays with flattening enabled and disabled; verify all five facts and unchanged contents.

  Both discovery directions are isolated. Disabled allocation and ordinary
  inspection/report agreement pass with preview on and off.

## Slice 4 — Bytecode and deployment boundaries

- [x] Add a compiled-bytecode check for both optional namespaces and baseline operation wrappers; require all five scoped JDK targets and reject reflective or method-handle invocation along operation helper paths.
- [x] Run the bytecode check and retain disassembly identifying compiled functions, method descriptors, call targets, build and command under this task directory.
- [x] Extend require-only fresh-process tests for Java 8 `:plain` and all four EA3 profiles; verify public require orders load no optional adapters and unavailable operations do not load their adapters.
- [x] Add require-only application AOT producers and source-free consumers for each of the five role/profile rows; assert optional adapters are neither loaded nor emitted during require-only compilation and loading.
- [x] Add an EA3 `:plain` require-only producer with an EA3 `:preview-export` source-free consumer; verify repeated supported array operations select receiver capabilities.
- [x] Run the new require/AOT checks and existing array, capability-report and value-class-switch cross-JVM tests; retain complete results, builds and actual child arguments.
- [x] Extend isolated JAR consumers to check repeated supported allocation/inspection and unavailable-path optional loading without checkout sources; preserve the source-only JAR contract.
- [x] Run the build suite with facts-selected consumers; verify supported, Java 8, preview-disabled and export-only results and record JAR identity and commands.

## Slice 5 — Measurements, regression gates and documentation

- [x] Run the same measurement support against the direct implementation with the baseline settings; retain all first-use and warmed samples and exact revision identities.
- [x] Write the before/after comparison with medians, variability, setup costs and limits; investigate and explain any repeatable regression before acceptance.
- [x] Run the complete runtime suite and the allocation, sequence and diagnostic controls with both flattening settings; retain Scry exit status and structured failure results before another run replaces them.
- [x] Run the full cross-JVM suite under Java 8 and the fallback suite under Java 8, EA3 preview off and EA3 preview on using the runtime guide; record results and unresolved limits.
- [x] Run `bb test:forced-jit` and `clojure -M:smoke-test`; record the actual parent/child coverage without claiming forced compilation in normal children.
- [x] Update array `decisions.md` and `embodiment.md` to describe lazy direct functions, independent discovery and measured limits; preserve public contracts and mark only verified behavior as implemented.
- [x] Update `mementum/state.md` and the task entry in `munera/plan.md` to match the implemented boundary and verification limits, without adding test history to project state.
- [x] Run `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb api-docs --check`, `bb vale`, and the tooling suite with `-M:quickdoc:tooling-test`; resolve failures and record results.
- [x] Audit all six design acceptance criteria against retained evidence in `implementation.md`; state remaining limits and verify `git diff --check` before the final implementation commit.

## Implementation review follow-up

- [x] Remove the stale completion statement from `ramora/value-type-array-design/embodiment.md:83` ("Quality checks and final task acceptance remain open"). It conflicts with the completed quality gates and acceptance audit in `implementation.md` and the current project state. Keep task progress in Munera; retain the measurement limits in the architecture document. Verify that the architecture and task records agree.

## Test review follow-up

- [x] Add fresh-process negative tests for optional adapter loading failures in both allocation and inspection discovery (`src/value_type/impl/array.clj`, `discover` and `discover-inspection`). The current tests cover unavailable prerequisites, round-trip exceptions and native allocation rejection, but not failures at the new lazy resolution boundary. Use real classpath/artifact state (for example, an isolated source copy with the relevant optional resource absent, and an incompatible compiled adapter), not replacement functions or production test switches. With API/access prerequisites satisfied, assert that loading/linkage failures escape and are not returned as `:available? false` or public unavailable-capability errors. Cover compiler failure propagation with a real failing compilation fixture where feasible. Keep these cases separate from supported-JVM evidence.

- [x] Make observation order explicit in `test/cross-jvm/value_type/array_loading_test.clj`. In `lazy-require-only-aot-test`, bind the adapter state before evaluating `loading-form`; the result map currently contains both the state read and discovery. In `receiving-runtime-aot-test`, bind repeated operation results, post-operation adapter state and the report in that order before building the result map. The report itself loads available adapters, so it must not establish the state that the operation check is meant to observe. Keep result maps free of order-sensitive work. Run the loading tests across their existing profiles and verify that moving result-map entries cannot change the observations.

## Documentation review follow-up

- [x] Document lazy array adapter loading in `README.md` and `doc/usage.md` (array and capability-report sections). The architecture now records direct calls and about 3–4 ms additional first-use time in the measured workload, but the user guides omit this trade-off. Explain that successful first discovery loads and compiles the relevant optional source adapter, including discovery triggered by `report`; public require alone does not do this. Correct the usage guide statement that inspection discovery checks "only the JDK API and access": adapter loading must also succeed, and unexpected compilation or linkage failures propagate. Link the bounded measurement evidence from the README performance section; do not imply a general speed guarantee. Check the text against `impl.array/discover`, `discover-inspection`, the loading/failure tests and `evidence/comparison.md`.

- [x] Add an entry under `CHANGELOG.md` → `Unreleased` for the lazy direct-call array implementation. The current changelog omits task 029. State the replacement of repeated reflective calls and the first-discovery compilation cost, while preserving the existing API, JVM flags and no-fallback contract. Keep any performance statement bounded by `evidence/comparison.md`; do not promise a speed increase.

## Code-shaper review follow-up

- [x] Make access-check results distinct from selected operation state in `src/value_type/impl/array.clj`. `allocation-methods` and `inspection-methods` still construct successful maps of reflective Methods, but discovery now reads only `:available?` and discards those Methods. The allocation check and selected state also reuse `:allocator` and `:predicate` for different value types. Return only access-check status on success, retain every API/signature/access check and classified failure, and name the helpers for their checking role. Rename the selected-state `methods` arguments and bindings in this namespace and `src/value_type/array.clj` to describe capability or operation state. Keep optional resolution outside the classification catches and preserve discovery independence. Update private-helper references in `array_adapter_failure_test.clj`; run the focused discovery, failure, loading and public array tests. No new abstraction or public API change is needed.
