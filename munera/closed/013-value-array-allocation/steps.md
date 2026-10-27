# Implementation steps

Slices 1–4, the usage guide, generated API and runtime guidance are complete.
Release notes and architecture status are complete. Final acceptance passed; see [acceptance.md](acceptance.md). Apply the error order and matrix in [plan.md](plan.md).
Record decisions and verification in `implementation.md` during execution.

## Slice 1 — Lazy allocation boundary

- [x] Add the lazy allocation adapter in `src/value_type/impl/array.clj`, with separate class prerequisites, exact allocator discovery and invocation-access checks; verify both new namespaces load without running these checks.
- [x] Add `value-type.array/make-array` with capability → component → length → seed validation, architecture error keys, relevant context and native allocation; verify one-long allocation at lengths 0 and 1 on EA3 with preview and exports.
- [x] Add supported-JVM validation tests for non-Class, primitive and identity components; null and incompatible seeds at lengths 0 and 1; and negative, fractional, non-numeric and out-of-range lengths.
- [x] Add pure length-validation tests for zero, small integer representations, `Integer/MAX_VALUE` and larger arbitrary-precision integers; verify conversion only follows range acceptance, without allocating near heap limits.
- [x] Add multi-fault tests on the supported JVM to verify component-before-length and length-before-seed errors, including error data and initial-value messages.
- [x] Add baseline-safe fresh-JVM tests for Java 8 unavailability and EA3 missing exports; assert capability-before-input errors, retained causes where present, required-flag guidance and repeated-call stability without unsupported value fixtures.
- [x] Check adapter failure handling against the native call boundary: unwrap invocation causes, propagate unexpected failures and JVM errors, and cache only capability outcomes; record the reviewed catch cases without forcing memory exhaustion.
- [x] Run the focused allocator and control tests with Scry; record commands, exit status and structured results before continuing.

## Slice 2 — Native array semantics

- [x] Add supported-only zero-field, one-long, two-long and reference-field fixtures to allocator tests; verify component type, length and every initial slot at lengths 0, 1 and 3, with null and non-null reference payloads.
- [x] Add Clojure `aget`, `aset` and `alength` tests using a fresh array for each selected index and write kind; verify accepted writes change only that slot and null/type failures leave all slots unchanged.
- [x] Add a temporary compiled Java array-access helper and equivalent selected-slot tests; assert native `NullPointerException` and `ArrayStoreException` without reflection wrappers and remove temporary files after each test.
- [x] Add one temporary Java value class compiled with the matching EA3 compiler; allocate with its real seed through the public API and verify contents without a `defvaluetype` registration.
- [x] Add test-only direct inspection of non-null results for null restriction and atomicity; accept the two-long non-flat result and keep layout assertions separate from semantic assertions.
- [x] Run focused semantic tests with Scry and record fixture, length and access-path coverage.

## Slice 3 — Fresh-JVM configuration matrix

- [x] Add baseline-safe array matrix checks under `test-fallback/`, using the existing explicit JVM runner and executable overrides; return EDN with runtime, flags, policy, outcome and exception/cause data.
- [x] Test both public namespace require orders in all six configurations in the plan, under `silent`, `warn` and `error`; assert safe loading and no added fallback warnings.
- [x] Test repeated allocator calls in Java 8, both preview-disabled EA3 configurations and preview-enabled EA3 without exports; assert unavailable reasons and no warnings, distinguishing prerequisite absence from native rejection.
- [x] Test successful allocation under all three policies on supported EA3; verify that each returned array is independent by mutating one and checking another from the same component and seed.
- [x] Run the semantic cases in a fresh supported JVM with flattening disabled; verify non-flat restricted atomic results and unchanged contents/store exception classes, without comparing exception-message text.
- [x] Run the existing fallback suite on Java 8 and EA3 with preview off/on; record structured results proving class behavior and warning policy remain unchanged.

## Slice 4 — Packaged source consumer

- [x] Extend source-JAR content checks to require both new source namespaces while preserving the exclusion of tests, Java fixtures and compiled classes.
- [x] Extend the isolated consumer with EA3 preview/exports allocation, native access and rejected stores; assert both new resources resolve from the exact built JAR and retain existing macro checks.
- [x] Add isolated JAR loading and repeated unavailable-allocation controls on Java 8 and preview-disabled EA3, without loading value fixtures or adding checkout paths.
- [x] Run the build suite with Valhalla selected by `JAVA_CMD` and the explicit Java 8 executable; record exit status, structured assertions and JAR-only dependency isolation.

## Slice 5 — Documentation and acceptance

- [x] Document explicit-seed allocation, error precedence, required flags, mutable-slot ownership and experimental limits in `doc/usage.md`; link allocation from README without presenting tasks 014 or 015 as implemented.
- [x] Add `src/value_type/array.clj` to Quickdoc source paths, update API-content tests, and regenerate `doc/API.md` with `bb api-docs`; verify allocator documentation and freshness.
- [x] Update `doc/development/runtime.md` with the supported export-enabled test command and explicit control commands, preserving preview-free baseline execution.
- [x] Add an Unreleased allocator entry to `CHANGELOG.md` and update the Ramora array design, decisions, verification and index to distinguish delivered allocation from pending diagnostics and sequence construction.
- [x] Run library tests on supported EA3 with explicit exports, the full array matrix, the three fallback suite runs, and isolated build consumers; retain each Scry outcome before a later run replaces `.scry-results/`.
- [x] Run formatting, clj-kondo, Vale, API freshness and focused Quickdoc/tooling tests using the documented tooling JVM; check changed Markdown links and `git diff --check`.
- [x] Write task-local `acceptance.md` mapping all seven design criteria to tests and results, with exact JDK/Clojure versions, OS/architecture, flags, commands, structured summaries and any unverified limits.

## Implementation review follow-ups

- [x] Add `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` to the `Test runtime` command in `.github/workflows/release.yml`. The command still runs the now export-dependent library suite without access, which blocks both release preview and publication. Keep unsupported-runtime controls free of the export where required. Verify the corrected runtime command on EA3 and check the workflow syntax. Review reproduction: focused `value-type.array-test` without the export exited 1 with Scry capability/access errors; with the export, array and layout tests passed (10 tests, 399 assertions, exit 0, no failure files).
- [x] Reconcile the final-acceptance status in `ramora/value-type-array-design.md` (Implementation stages) and `ramora/value-type-array-design/verification.md` (Public allocation verification). Both still say acceptance is pending, contrary to their page introductions and task `acceptance.md`. Link the completed acceptance record and keep diagnostics and sequence construction marked as pending. Check the maintained architecture pages for other contradictory allocation-status claims.

## Test review follow-ups

- [x] Add a real-JVM regression test for native allocation failure propagation at `value-type.impl.array/allocate`. Current tests exercise successful allocation, public validation and native stores, but no allocator invocation that throws; removing the `InvocationTargetException` unwrapping would leave them passing. Use the real discovered allocator with a valid value component/seed and a negative length to obtain a bounded native rejection. Assert the original native exception escapes, not a reflection wrapper or `:array-unavailable`, then verify a valid public allocation still succeeds in the same runtime. Do not mock methods, add production test options or force memory exhaustion.
- [x] Extend `value-type.array-test/validation-test` to check the documented error context. Component cases currently check only the error key and component; length cases omit component and reason; seed cases omit component and reason. Assert the relevant component/length and reason for each category, distinguish null from incompatible initial values, and check that initial-value errors have no invented slot index at lengths zero and one. Keep message checks semantic rather than exact full-string comparisons.

## Documentation review follow-ups

- [x] Update the Development quality workflow section in `ramora/defvaluetype-design/embodiment/artifacts.md` to include `src/value_type/array.clj` as an API-generation source and `value-type.array/make-array` as a documented public function. It still describes only the core source and macro. Check the text against `bb/value_type/api_docs.clj`, `doc/API.md` and `test-quickdoc/value_type/api_docs_test.clj`; retain the exclusions for private and implementation vars.
- [x] Update the Library packaging section in `ramora/defvaluetype-design/embodiment/artifacts.md` to distinguish array allocation requirements from class creation and fallback loading. Add the required `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` flag and state that allocation has no ordinary-array fallback. Align the packaged-consumer coverage with `test-build/build_test.clj` and link the current runtime or usage guide. The section currently lists preview alone for value-class consumers and does not explain the additional array requirement.

## Code-shape review follow-up

- [x] Use `value-type.jvm-config/configuration` in `test-fallback/value_type/array_boundary_test.clj` for the baseline and Valhalla runner configurations. `unavailable-boundary-test` repeats both environment-variable names, executable defaults, classpath selection and preview options already owned by `jvm_config.clj` and used by `array_matrix_test.clj`. This permits the two array controls to diverge when JVM selection changes. Keep the boundary-specific expected reasons, causes and assertions local. Run the focused boundary and matrix tests with the documented executable overrides to verify unchanged child configurations and outcomes.
