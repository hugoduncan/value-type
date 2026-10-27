# Implementation steps

Execute slices in order. Mark an item complete only after its stated check
passes; record decisions and evidence in `implementation.md`. The plan defines
validation order, process configurations and result-recording requirements.

## Slice 1 — Construction and validation

- [x] Extract private component and seed validators in `value-type.array`; run existing `value-type.array-test` to verify unchanged allocator validation order and error data.
- [x] Add the private checked-next-length operation and focused tests for zero, MAX_VALUE minus one and MAX_VALUE; verify the rejected attempted length is 2147483648 without allocating large storage.
- [x] Add both public `into-array` arities with the core-name exclusion, early checks, one-pass transient buffering, checked length accounting, existing allocator call and ordered copy; verify one inferred and one explicit construction.
- [x] Add construction tests in `test/value_type/array_sequence_test.clj` for lists, vectors and lazy inputs, both arities, one/several elements and all shared fixture shapes; assert exact component, length, order and contents, including an explicit seed different from the first input value.
- [x] Add empty and nil input tests for both arities; verify inferred-seed guidance, explicit zero-length results, and null/wrong-type explicit seed rejection even when input is empty.
- [x] Add invalid component and element tests; verify non-Class, primitive and identity rejection, indexed null/wrong-type failures at first/middle/last positions, ordinary non-seqable errors, and exception data that contains no input, rejected value or buffer.
- [x] Run sequence and allocation tests on supported EA3 with preview and exports; retain structured Scry results and verify CLI exit zero with no failure files.

## Slice 2 — Input effects and native semantics

- [x] Add observable non-caching finite Seqable tests for both arities that detect a second input traversal; assert successful contents and one input walk without relying only on cached lazy callbacks.
- [x] Add unchunked lazy-input tests for component/explicit-seed rejection before realization and indexed element rejection after a valid prefix; verify permitted input effects and no returned partial array.
- [x] Add realization-failure tests for both arities at the first element and after a valid prefix; assert the original thrown object escapes and a later valid construction still succeeds.
- [x] Add chunked lazy-input tests that verify correct ordered output without requiring one callback per requested element or full consumption after failure.
- [x] Add selected-slot `aget`, `aset` and `alength` checks for arrays from both arities; verify valid replacement affects only its slot and null/wrong-type stores reject without changing any slot.
- [x] Review construction state and exception paths; confirm no retained input/buffer/array, no caller access to unfinished arrays, no diagnostic calls and no changes to class generation or fallback policy; record the findings.
- [x] Run sequence, allocation and existing layout tests with flattening explicitly enabled and disabled; retain structured results for each run without adding a flat-layout requirement to sequence tests.

## Slice 3 — Startup controls and packaging

- [x] Add baseline-safe sequence child checks and `array_sequence_boundary_test.clj` using `jvm-config` and `jvm-runner`; verify both arities, repeated unsupported failures, untouched observable input and supported construction across the plan's six configurations, three policies and two require orders.
- [x] Run the new process matrix with explicit Java executable paths; verify each child has complete EDN, expected runtime flags/reasons, no new fallback warning, exit zero and a 120-second timeout; retain structured results.
- [x] Extend `test-build/build_test.clj` supported JAR-only consumers to exercise both arities, ordered non-empty contents and explicit empty input; retain resource-origin assertions and existing macro/allocation checks.
- [x] Extend unsupported JAR-only consumers to require the API and reject both arities without consuming input; include export-only EA3 so diagnostic access is not mistaken for allocation capability.
- [x] Run the full build suite and retain structured results; verify isolated consumers use only the built JAR and runtime dependencies, not checkout sources.
- [x] Run the full fallback/loading suite on Java 8 and EA3 preview off/on with explicit cross-JVM paths; retain results showing allocator, class-fallback and startup controls remain unchanged.

## Slice 4 — Documentation and acceptance

- [x] Update public docstrings and `doc/usage.md` with both arities, examples, seed/empty/nil behavior, validation order and indexed errors, finite-input limits, temporary O(n) storage and failure side effects; verify examples on supported EA3.
- [x] Regenerate `doc/API.md` with `bb api-docs` and extend `test-quickdoc` API contract checks for both arities and the new contract text; verify focused tests and API freshness.
- [x] Update `doc/development/runtime.md` with sequence test/matrix commands and JAR-consumer coverage; verify named namespaces and flags match the implemented tests.
- [x] Update `CHANGELOG.md`, the array architecture/decisions/verification/gates pages and `ramora/INDEX.md` to reflect delivered sequence construction and closed task 014; distinguish implementation from pending final acceptance and preserve historical evidence limits.
- [x] Run formatting, lint, Vale, API freshness and Quickdoc tests from the quality guide; check changed Markdown links and `git diff --check`, retaining structured test results.
- [x] Run the full supported library suite and the sequence/allocation/layout controls with flattening enabled and disabled; record exact environment, commands and structured results, plus CLI status and failure-file checks.
- [x] Repeat final fallback/loading suites on Java 8 and EA3 preview off/on, including sequence and allocation startup controls; retain complete structured results before the next run replaces failure files.
- [x] Repeat the full build suite after final source and documentation changes; retain JAR-only consumer results outside `target/`.
- [x] Write `acceptance.md` mapping all seven design criteria to retained results and review findings; reconcile final architecture acceptance status with that evidence and verify links and whitespace.

## Test review follow-up

- [x] Relax the failure-consumption assertion in `validation-input-effects-test` (`test/value_type/array_sequence_test.clj`). The exact `[replacement nil]` effects assertion requires traversal to stop at the invalid element, but the design does not specify how much input is consumed after failure. Assert that the observed effects include the valid prefix and rejected entry in order, without excluding later effects. Keep the indexed-error and no-partial-result checks. Run the focused sequence tests in both layout modes.

## Documentation review follow-ups

- [x] Add `value-type.array/into-array` to the README array overview and link to `doc/usage.md#value-array-sequence-construction`. The overview currently describes only explicit-initial-value allocation and diagnostics. Briefly identify finite sequence input, inferred or explicit seeds, and the shared allocation requirements; keep detailed limits in the linked guide. Check the link and run Vale.
- [x] Update the generated API inventory in `ramora/defvaluetype-design/embodiment/artifacts.md` under "Development quality workflow". It lists only `make-array` and `array-info`, but `doc/API.md` and `test-quickdoc/value_type/api_docs_test.clj` also include `into-array`. Include the new public function and verify the inventory against the generated reference; run API freshness.
- [x] Update the isolated-consumer coverage in `ramora/defvaluetype-design/embodiment/artifacts.md` under "Library packaging". Record both sequence arities, ordered contents and explicit empty input on supported EA3, plus repeated rejection before input effects in all three unsupported configurations, including export-only EA3. Verify the description against `test-build/build_test.clj` and `doc/development/runtime.md#run-the-build-tests`; preserve the recorded JVM limits.

## Code-shape review follow-up

- [x] Separate the consumer program from process setup and expected results in `isolated-consumer-test` (`test-build/build_test.clj`). The test now embeds a large quoted program that mixes resource-origin checks, unsupported diagnostic/allocation/sequence checks, value-type definition and supported operations. Extract the program into a dedicated test resource, with small consumer-local functions for these checks, and have the existing process runner execute it with the same JAR-only runtime classpath. Keep unsupported checks before value-type creation, preserve all four JVM configurations and exact result assertions, and do not add checkout source or test directories to the consumer classpath. Run the full build suite to verify resource origins and all consumer outcomes.
