# Implementation steps

Follow [plan.md](plan.md) in order. Record decisions and verification in
`implementation.md`; keep task 012 evidence unchanged. All five implementation slices are complete. The task remains open for review. The disposable-JVM safety gate precedes shared-JVM tests.

## Slice 1 — Safe public and adapter boundary

- [x] Confirm the EA3 and Java 8 executables selected by `jvm_config.clj`; record versions and executable overrides in `implementation.md`.
- [x] Add independent delayed inspection discovery in `src/value_type/impl/array.clj` for the component value predicate and three diagnostic methods; check signatures and caller invocation access without resolving them during namespace loading.
- [x] Add the adapter inspection operations using the existing cause-unwrapping helper; return inspection-unavailable errors only for known discovery/access failures, with reasons and causes.
- [x] Add documented `array-info` in `src/value_type/array.clj`: validate the reference-array domain before capability checks, then return the complete five-key result without storing the input.
- [x] Add disposable public-wrapper checks in `test-fallback/value_type/array_info_boundary_test.clj` for null, String and primitive-array inputs on Java 8 and EA3 with/without the export; assert `ExceptionInfo`, exact `:invalid-array`, complete EDN outcomes, exit zero and a 120-second timeout.
- [x] Add valid Object-array controls to those child checks; assert complete results on supported inspection rows and exact `:array-inspection-unavailable` on Java 8 and missing-export rows, including repeated calls and preserved causes.
- [x] Run the focused child checks and retain structured results; stop on any crash, timeout or wrong error before running shared-JVM diagnostic tests.

## Slice 2 — Observed properties and ownership

- [x] Add valid-array direct JDK comparison helpers in `test/value_type/array_info_test.clj`, independent of production inspection; compare the exact key set, component Class and four boolean types and values.
- [x] Add ordinary Object/String and typed value-array cases at lengths 0, 1 and 3; include an array with an array component to verify the reference-array domain.
- [x] Add restricted-array cases using task 013's zero-field, one-long, two-long and reference-field fixtures at lengths 0, 1 and 3, including null/non-null reference payloads; compare all properties against direct inspection.
- [x] Add ownership checks that compare array contents before/after inspection and repeat inspection after a caller store; verify that inspection leaves contents unchanged and returns current properties.
- [x] Add controlled layout assertions for the wider non-flat restricted fixture and the one-long flat fixture; require null restriction and atomicity independently of flattening.
- [x] Run focused diagnostics and existing allocation/layout tests with preview and exports, then with `-XX:-UseArrayFlattening`; require all flat results false in the disabled run and retain both structured results.

## Slice 3 — Startup and regression matrix

- [x] Extend the diagnostic child matrix to all six plan configurations, three fallback policies and both public namespace require orders; assert actual runtime/flags, repeated outcomes and no added fallback warnings.
- [x] Add explicit export-only EA3 assertions that Object-array inspection returns all five properties without value fixture creation, while allocation retains its existing unavailable result.
- [x] Add independent non-null signature/access observations to child results so Java 8 API absence and EA3 missing-export invocation denial are distinguished from preview settings.
- [x] Run the complete diagnostic matrix and existing allocation boundary/matrix tests with explicit JVM overrides; require every child to finish within its timeout and exit zero with the expected EDN.
- [x] Review adapter state and call paths for no retained caller arrays, no eager optional resolution, no diagnostic dependency in allocation discovery and no broad translation of unexpected failures; record the findings.
- [x] Run existing fallback/loading suites on Java 8 and EA3 with preview off/on; retain structured results that confirm unchanged class policy and namespace loading.

## Slice 4 — Packaged consumer

- [x] Extend `test-build/build_test.clj` so the supported JAR-only consumer inspects ordinary and restricted arrays, verifies complete results and preserves existing macro/allocation assertions.
- [x] Add JAR-only Java 8 and preview-disabled EA3 diagnostic controls: invalid input keeps `:invalid-array`, missing support/access reports inspection unavailable, and export-only EA3 inspects an Object array without value creation.
- [x] Run the build suite with explicit supported/baseline executable selection; verify consumer resources come from the built JAR and the classpath contains no checkout sources; retain structured results outside `target/`.

## Slice 5 — Documentation and acceptance

- [x] Update `doc/usage.md` and the API docstring with the input domain, five keys, error precedence, inspection export requirement, preview distinction, experimental limits and atomicity caveats.
- [x] Regenerate `doc/API.md` with `bb api-docs` and extend focused API-documentation tests to require the diagnostic contract; do not edit generated output directly.
- [x] Update `doc/development/runtime.md` with diagnostic safety, matrix and layout commands, keeping exports explicit so unsupported controls remain valid.
- [x] Update array architecture status, decisions, verification/gates and `ramora/INDEX.md` to identify delivered diagnostics while keeping sequence construction pending; preserve task 012 historical evidence.
- [x] Add an Unreleased diagnostics entry to `CHANGELOG.md` with the supported scope and capability limits.
- [x] Run the full supported library suite, both layout checks, diagnostic/allocation process matrices, fallback/loading controls and packaged-consumer suite; record exact commands, versions, flags and Scry results before subsequent runs replace failure artifacts.
  - [x] Final supported library and enabled-layout checks: 78 tests/2060 assertions; disabled-layout array checks: 14 tests/1848 assertions. Structured and CLI runs pass; see slice 5e.
  - [x] Final diagnostic/allocation process matrices: 4 tests/2095 assertions. Structured and CLI runs pass; see slice 5f.
  - [x] Repeat fallback/loading controls and packaged-consumer suite for final acceptance.
- [x] Run formatting, lint, Vale, generated API freshness and focused Quickdoc tests using the quality guide; verify changed documentation links and `git diff --check`.
- [x] Create `acceptance.md` mapping all seven design acceptance criteria to retained results and limits; append implementation decisions, update completed checklist items and project status without closing the task.

## Implementation review follow-up

- [x] Reconcile current diagnostic acceptance status with `acceptance.md` and the completed slice 5g. `munera/plan.md` still lists final fallback/loading, packaging and acceptance as remaining. `ramora/INDEX.md`, `ramora/value-type-array-design.md` (status and stage 2), and `ramora/value-type-array-design/{decisions,verification,implementation-gates}.md` still mark final acceptance open. Link the completed acceptance record and distinguish completed verification from the task remaining open for review. Preserve historical slice notes and keep sequence construction pending. Verify that no current status summary still requests the completed checks.

## Documentation review follow-up

- [x] Add `value-type.array/array-info` to the README's array overview and link to `doc/usage.md#value-array-diagnostics`. State that it inspects ordinary and restricted reference arrays, needs the internal-package export, and does not need preview for ordinary-array inspection on the tested EA3 build. The current overview mentions allocation only. Check the summary against the public docstring and usage guide.
- [x] Update the public API inventory in `ramora/defvaluetype-design/embodiment/artifacts.md` under “Development quality workflow” to include `value-type.array/array-info`. It currently lists only `defvaluetype` and `make-array`, although the generated reference and API inventory tests include diagnostics. Verify the list against `doc/API.md` and run `bb api-docs --check`.
- [x] Update “Library packaging” in `ramora/defvaluetype-design/embodiment/artifacts.md` with diagnostic consumer requirements and current isolated-consumer coverage. The page still describes three configurations and allocation-only checks; `test-build/build_test.clj` now has four configurations, including export-only inspection, invalid-input precedence, unavailable inspection and supported ordinary/restricted diagnostics. Distinguish inspection's export requirement from allocation's preview requirement, link the diagnostic guide, and check the summary against the build test and runtime guide.

## Code-shape review follow-up

- [x] Move the shared array value types and fixture data from `test/value_type/array_test.clj` to a dedicated test-support namespace. `test/value_type/array_info_test.clj` currently requires the allocation test namespace, calls its private `fixtures` var, and names its generated `ArrayPoint` and `ArrayPair` classes directly. This couples diagnostic coverage to allocation-test internals and loads unrelated tests to obtain fixtures. Have both test namespaces use the shared fixture API and classes; keep the direct diagnostic oracle independent of the production adapter. Preserve all fixture shapes, null/non-null payloads and layout expectations. Verify diagnostic, allocation and layout tests with flattening enabled and disabled.
