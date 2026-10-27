# Implementation notes

## Architecture review — 2026-10-23

- No architectural review feedback. Scope is unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context
  came from `AGENTS.md`, `ramora/INDEX.md`, the array design and its decisions,
  verification and implementation gates, and the controlled-fallback design.
  D1–D5 support this slice. Early validation and input-effect ordering remain
  planning decisions within those boundaries.

## Ambiguity review — 2026-10-23

- No ambiguity review feedback. Scope is unchanged.

## Inconsistency review — 2026-10-23

- No inconsistency review feedback. Scope is unchanged.

## Review handoff — 2026-10-23

- Planning reference: `mementum/knowledge/value-array-jvm-boundaries.md`
  records the implemented allocator's validation order and lazy capability
  boundaries. Use it when selecting sequence input-effect ordering; diagnostic
  access alone does not prove allocation capability.

## Execution planning and review — 2026-10-23

- Created `plan.md` and `steps.md`: four ordered slices and 29 unchecked
  implementation actions. No implementation work was done.
- Selected capability/component checks before input consumption, explicit-seed
  validation before realization, indexed sequence errors and bounded arithmetic
  in the actual buffering loop. Reuse the allocator after one input traversal.
- Reviewed plan ambiguity with `steps.md` as read-only context. The order,
  ownership, length boundary, process matrix and acceptance coverage are
  explicit; no blocking ambiguity or checklist correction was found.
- Reviewed design consistency internally and against task 013, the array
  contract, D4, implementation gates and current code. No contract conflict
  was found. Architecture pages still describe closed task 014 as open for
  review; slice 4 records this non-blocking status correction.
- Local Markdown link targets and checklist structure passed. Runtime tests
  were not run: only planning and orientation records changed.

## Plan-review handoff

- `923051f` and `66aa037` are empty commits that record the two review passes,
  not implementation or runtime-test evidence. Neither added `design-steps.md`;
  there are no design-step follow-ups from this slice.

## Slice 1a — shared validation and length accounting — 2026-10-23

- Extracted component and initial-value validation without changing allocator
  order, messages or error data. The seed validator takes context so the
  sequence API can omit an unknown length. No public API or dependency change.
- Added checked-next-length with promoting arithmetic and the existing length
  validator. Tests cover zero, MAX_VALUE minus one and rejection at 2147483648
  without large allocation. The next slice must use this helper before append.
- EA3 with preview and exports: allocation and sequence arithmetic tests passed
  (11 tests, 449 assertions). Structured results: `evidence/slice-1a.edn`.
  Scry CLI exited zero with no failure files. Focused formatting passed.
- Command: `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java"
  clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test
  -m scry.cli --namespace value-type.array-test
  --namespace value-type.array-sequence-test`.
- Design and approach are unchanged. Next: public constructors and the remaining
  slice 1 success/error cases; this pass does not implement sequence traversal.
- Project lint found one warning: checked-next-length is not yet used by
  production code. Do not suppress it; the next constructor slice must use
  the helper and rerun lint. No lint errors were reported. Whitespace passed.

## Slice 1b — public construction — 2026-10-23

- Added both public arities with early capability/component/explicit-seed checks,
  indexed element validation, bounded transient buffering and ordered copying.
  Inferred seeds come from the validated buffer, not another input traversal.
  No adapter, dependency or design change. API reference regenerated.
- Real fixture success matrix covers both arities, lists/vectors/lazy input,
  one/several values and distinct explicit seeds. Allocation and sequence tests
  pass: 12 tests/629 assertions; structured results: `evidence/slice-1b.edn`.
  Used the slice 1a EA3 command and flags. CLI exit zero, no failure EDN files;
  focused formatting, project lint and whitespace checks pass.
- Next: empty/nil and invalid-input tests in slice 1, then observable input
  effects in slice 2. Those paths are implemented but not yet fully verified.

## Slice 1c — empty and invalid input — 2026-10-23

- Completed slice 1 with real-array tests for empty/nil input, invalid seeds,
  invalid components and null/wrong-type entries at first/middle/last indexes.
  Exact error-map assertions exclude retained input, rejected values and buffers.
  Non-seqable input throws ordinary IllegalArgumentException in both arities.
- No production, API, dependency or design change was needed. Next: observable
  input traversal and failure effects, then selected-slot store checks in slice 2.
- Used the slice 1a EA3 command and flags. Structured and CLI runs pass:
  16 tests/791 assertions; evidence: `evidence/slice-1c.edn`. CLI exit zero
  and no failure files. Focused formatting, project lint and whitespace pass.

## Slice 2a — observable input effects — 2026-10-23

- A non-caching Seqable supplies a fresh lazy cursor on each walk. Both arities
  perform one walk and preserve ordered contents. Early component/seed rejection
  leaves lazy input untouched; indexed rejection preserves prior input effects.
- Realization failures at the start and after a valid prefix retain the original
  exception object. No partial result returns; subsequent construction succeeds.
  Chunked tests check ordered output and failure effects without fixing callback
  counts or requiring full consumption.
- No production, dependency or design change. Selected-slot stores, construction
  state review and enabled/disabled layout runs remain in slice 2.
- EA3 with preview and exports: sequence/allocation checks pass (20 tests,
  839 assertions). Structured evidence: `evidence/slice-2a.edn`. Used the slice 1a
  CLI command; exit zero and no failure files. Focused formatting, project lint
  and whitespace checks pass.

## Slice 2b — native stores and state review — 2026-10-23

- Both arities now have selected-slot checks for every fixture and index. Each
  accepted/rejected store uses a fresh array; `aget` checks every slot and
  `alength` checks length. Null and wrong-type stores throw the native
  NullPointerException and ArrayStoreException without mutation. Sequence tests
  use no diagnostics and make no flat-layout assertion.
- Reviewed `buffer-input`, `copy-buffer`, both arities and adapter allocation.
  Input/buffer/result remain call-local. Buffering finishes before allocation;
  copying invokes no caller code and returns only after all stores. Error maps
  contain no input, buffer or result. Adapter delays retain capability metadata,
  not caller data. No class-generation or fallback-policy change was made.
- Sequence/allocation/layout structured and CLI runs pass in each layout:
  22 tests/1350 assertions, exit zero, no failure EDN files. Evidence:
  `evidence/slice-2b-enabled.edn` and `evidence/slice-2b-disabled.edn`, including
  runtime, host and flags. Used the slice 1a command with the layout namespace
  added and explicit `-J-XX:+UseArrayFlattening` / `-J-XX:-UseArrayFlattening`.
  Structured runs used `scry.core/run` with the same three namespaces.
- Focused formatting, project lint and whitespace checks pass. No production,
  dependency or design change. Next: slice 3 baseline-safe startup controls and
  isolated packaged consumers; full acceptance remains open.

## Slice 3a — sequence startup controls — 2026-10-23

- Added baseline-safe child checks and the 36-process sequence matrix using
  shared JVM configuration/runner helpers and explicit 120-second timeouts.
  Both arities reject twice with untouched lazy input on unsupported rows.
  Supported rows check ordered contents, component and length for both arities
  plus explicit nil input. Value fixtures load only on supported rows; no
  diagnostic dependency or production change was added.
- All six configurations, three policies and two require orders pass: 1 test,
  1116 assertions. Evidence: `evidence/slice-3a.edn`, with complete structured
  assertions, host/runtime and executable paths. CLI exit zero; no failure files.
  Focused formatting, project lint and whitespace checks pass.
- CLI: with `JAVA_CMD` and `VALUE_TYPE_VALHALLA_JAVA` set to
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java` and
  `VALUE_TYPE_BASELINE_JAVA` set to
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`, run
  `clojure -M:fallback-test --namespace value-type.array-sequence-boundary-test`.
  Structured run used `scry.core/run` with that namespace and an empty
  `:main-opts` override alias. An initial invocation incorrectly appended `-e`
  to the CLI alias; it ran no tests and was corrected before verification.
- Design and approach are unchanged. Next: supported and unsupported JAR-only
  consumers, then the full build and fallback/loading suites. Documentation
  and final acceptance remain in slice 4.

## Slice 3b — supported packaged sequence consumers — 2026-10-23

- Extended the existing isolated consumer with inferred list input, explicit
  vector input whose seed differs from the first value, and explicit empty input.
  Checks cover component, length and ordered contents. Exact JAR resource origins,
  runtime-only classpath, macro, allocation and diagnostic checks remain intact.
- Focused structured and CLI checks pass (1 test/11 parent assertions); evidence:
  `evidence/slice-3b.edn`. CLI exit zero, no failure files. Used explicit EA3
  `JAVA_CMD` and Java 8 `VALUE_TYPE_BASELINE_JAVA`, with `clojure -M:build-test
  -m scry.cli --var build-test/isolated-consumer-test`. Formatting and lint pass.
- An initial structured command had an invalid var literal and ran no tests;
  corrected it to resolve the test var before the successful run.
- No production, dependency or design change. Next: unsupported packaged sequence
  calls before value creation, then full build and fallback/loading regressions.

## Slice 3c — unsupported packaged sequence consumers — 2026-10-23

- Both arities now reject twice before value creation in each unsupported
  JAR-only consumer. Fresh lazy inputs remain untouched. Controls include
  Java 8, EA3 without flags and export-only EA3; diagnostic success in the
  last case does not permit sequence allocation. Existing resource-origin
  and runtime-only classpath checks remain intact.
- Full build structured and CLI runs pass: 9 tests/103 assertions. Evidence:
  `evidence/slice-3c.edn`. CLI exit zero and no failure files. Formatting,
  project lint and whitespace checks pass. Commands used EA3 `JAVA_CMD`
  and the explicit Java 8 `VALUE_TYPE_BASELINE_JAVA` paths from slice 3a:
  `clojure -M:build-test -m scry.cli --dir test-build`; the structured run
  used `scry.core/run` with namespace `build-test`.
- No production, dependency or design change. Full fallback/loading runs
  remain next in slice 3; documentation and final acceptance remain open.

## Slice 3d — full fallback/loading regressions — 2026-10-23

- Full `test-fallback` structured and CLI runs pass on Java 8 and EA3 with
  preview off/on. Each runs 20 tests: 3377 assertions on Java 8 and preview
  off, 3352 with preview on. All CLI exits are zero with no failure EDN files.
  This includes allocation, diagnostic and sequence startup controls, class
  fallback, AOT and transfer tests against real JVMs; no mocks were added.
- Evidence: `evidence/slice-3d-{baseline,preview-off,preview-on}.edn`; commands,
  explicit executable paths, runtime/host versions and CLI outcomes are retained
  in the adjacent `slice-3d-commands.sh`, environment and log files. Parent
  preview flags do not replace the explicit child configuration flags.
- Slice 3 is complete. No production, test, dependency or design change was
  needed. Next: slice 4 documentation and API contract tests, then final combined
  verification and acceptance. These runs do not complete final acceptance.

## Slice 4a — usage and API contracts — 2026-10-23

- Added sequence examples and the seed, validation, indexed-error, finite-input,
  temporary-storage and failure-effect contracts to the usage guide. Expanded
  the docstring and regenerated API output. No runtime or design change.
- The existing public API inventory test still expected only allocation and
  inspection; updated it and added rendered sequence contract checks. Real
  Quickdoc structured and CLI runs pass: 11 tests/217 assertions, exit zero,
  no failure files. Evidence: `evidence/slice-4a-quickdoc.edn`. Commands:
  `mise exec -- clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc`;
  structured run used `scry.core/run` with `{:dirs ["test-quickdoc"]}`.
- Examples pass on EA3 with preview and exports. Reproduce with EA3 `JAVA_CMD`
  and `clojure -J--enable-preview
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M
  munera/open/015-value-array-sequence-construction/evidence/slice-4a-examples.clj`.
  Actual/expected values and runtime version are in the adjacent EDN file.
- Added Clojure terms `arity` and `seqable` to the Vale vocabulary. Formatting,
  lint, Vale, API freshness and whitespace checks pass. Runtime matrix and
  packaging were not repeated. Next: runtime guide, release/architecture status,
  then final combined verification and acceptance.

## Slice 4b — runtime guidance — 2026-10-23

- Added sequence startup and enabled/disabled layout commands, input-effect
  coverage and supported/unsupported JAR-only sequence controls to the runtime
  guide. Checked namespace names, flags, matrix cases and consumer behavior
  against the implemented tests. No production, test or design change.
- `mise exec -- bb vale`, `mise exec -- bb api-docs --check`, runtime Markdown
  links/anchors and `git diff --check` pass. Runtime tests were not repeated;
  this slice changes documentation only. Architecture/release status and final
  combined verification remain next.

## Slice 4c — architecture and release status — 2026-10-23

- Release notes and maintained array architecture now describe delivered sequence
  construction, validation/input effects and temporary storage. Task 014 is
  marked closed. Historical direct-JVM evidence remains unchanged; sequence
  performance is unmeasured and final combined acceptance is explicitly pending.
- Project Vale, API freshness, changed-page Markdown links/anchors and
  `git diff --check` pass. Runtime tests were not repeated for this documentation
  slice. No production, test, dependency or design change was needed.
- Next: final quality and runtime/build verification, then acceptance.md. Slice
  results in the verification page must not be treated as final acceptance.

## Slice 4d — final quality verification — 2026-10-23

- Formatting, dependency lint configuration, lint, Vale and API freshness pass
  with `JAVA_CMD` unset and `mise exec --` using the quality-guide commands.
  Quickdoc structured and CLI runs pass: 11 tests/217 assertions, zero failures
  or errors, CLI exit zero and no Scry failure files. Evidence:
  `evidence/slice-4d-quickdoc.edn`.
- Commands: `clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir
  test-quickdoc`; the structured run uses `scry.core/run` with
  `{:dirs ["test-quickdoc"]}`. Environment: Darwin arm64, Temurin
  21.0.10+7-LTS. Checked 80 local Markdown link paths across the usage, API,
  runtime, changelog and array architecture pages; whitespace checks pass.
- No code, test, dependency or design change was needed. Next: final supported
  library/layout checks, fallback/loading matrix and packaging, then acceptance.
  These quality results do not replace runtime acceptance.

## Slice 4e — final library and layout verification — 2026-10-23

- Full supported library passes: 89 tests/2963 assertions. Sequence, allocation
  and layout controls pass with flattening enabled and disabled: 22 tests/1350
  assertions each. All structured results have `:pass? true`, no failures or
  errors; all CLI exits are zero with no Scry failure EDN files.
- Evidence: `evidence/slice-4e-{library,enabled,disabled}.edn`; exact commands,
  executable path, flags, host and CLI outcomes are in adjacent commands,
  environment and run-log files. Scope: EA3 `27-jep401ea3+1-1`, Clojure 1.12.6,
  macOS aarch64, preview and internal-package export enabled.
- No production, test, dependency or design change was needed. Existing real-JVM
  tests cover this slice without mocks. Next: final fallback/loading and build
  checks, then acceptance mapping and architecture status. These runs alone do
  not complete acceptance.

## Slice 4f — final fallback/loading verification — 2026-10-27

- Full fallback/loading structured and CLI runs pass on Java 8 and EA3 with
  preview off/on: 20 tests each, with 3377/3377/3352 assertions respectively.
  All structured results have `:pass? true` and no failures or errors; all
  CLI exits are zero with no failure EDN files. Existing real-JVM tests cover
  sequence/allocation startup controls and class fallback without mocks.
- Evidence: `evidence/slice-4f-{baseline,preview-off,preview-on}.edn`.
  Adjacent commands, environment and run-log files retain executable paths,
  flags, host, runtime versions and CLI outcomes. Scope: Temurin 8u492 and
  Valhalla `27-jep401ea3+1-1` on macOS arm64.
- No production, test, dependency or design change was needed. Next: final
  full build suite, then acceptance mapping and architecture status. Preserve
  evidence outside `target/` because the build suite cleans that directory.

## Slice 4g — final build verification — 2026-10-27

- Full build structured and CLI runs pass: 9 tests/103 assertions, no failures
  or errors. All nine structured test results pass; CLI exit zero and no failure
  EDN files. Real isolated JAR consumers cover supported and unsupported sequence
  calls. No production, test, dependency or design change was needed.
- Evidence: `evidence/slice-4g-build.edn`; adjacent commands, environment and
  run log retain executable paths, flags and outcomes. Results remain outside
  `target/`. Scope: EA3 and Temurin 8u492 on macOS arm64.
- The detailed result contains regex literals and printed tagged values from
  assertions. Read it with the Clojure reader, `*read-eval* false` and
  `*default-data-reader-fn* tagged-literal`, not the strict EDN reader.
- Next: write acceptance.md using the final slice 4d–4g evidence and earlier
  focused checks; reconcile architecture acceptance status. No runtime check
  remains unchecked, but final acceptance mapping is not yet complete.

## Slice 4h — final acceptance record — 2026-10-27

- Added acceptance.md with all seven criteria, final result counts, reproduction
  links and JVM limits. Re-read all eight final structured result files with
  evaluation disabled; pass flags, summaries, result statuses and failure
  collections agree. No runtime tests were repeated for this records-only slice.
- Reconciled maintained architecture and task status with completed acceptance.
  All implementation checklist items are done; task 015 remains open for review.
  No production, test, dependency or design change was needed.
- Project Vale, changed-document local link paths and whitespace checks pass.
  Next review should use acceptance.md and retained slice 4d–4g results rather
  than historical pending-status entries in these append-only notes.

## Implementation review — 2026-10-27

- No new actionable issues.

## Test review — 2026-10-27

- Added 1 step to be addressed.

## Test review follow-up — 2026-10-27

- Addressed 1 review step. The effects assertion checks the first two entries
  and permits later effects. Indexed-error and no-partial-result checks remain.
- Focused sequence tests passed with `-XX:+UseArrayFlattening` and
  `-XX:-UseArrayFlattening`: 11 tests/903 assertions each, CLI exit zero,
  no Scry failure files. Used the EA3 executable at
  `~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`, `-M:test` (preview),
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`, and
  `scry.cli --namespace value-type.array-sequence-test`.
- Focused formatting, project lint and whitespace checks passed. Production
  code and design are unchanged; full acceptance was not repeated.

## Test re-review — 2026-10-27

- No new actionable issues.

## Documentation review — 2026-10-27

- Added 3 steps to be addressed.

## Documentation review follow-ups — 2026-10-27

- Addressed README review step. Sequence overview link and project Vale pass.
- Addressed API inventory review step. Inventory matches the generated reference
  and API contract test; `bb api-docs --check` passes.
- Addressed consumer coverage review step. Checked both supported arities,
  ordered contents, explicit empty input and repeated effect-free rejection
  in all three unsupported configurations against `test-build/build_test.clj`
  and the runtime guide. Guide anchor and whitespace checks pass.
- All 3 documentation review steps are complete. Runtime tests were not rerun;
  code, tests, design and recorded JVM limits are unchanged.

## Documentation re-review — 2026-10-27

- No new actionable issues.

## Code-shape review — 2026-10-27

- Added 1 step to be addressed.

- Code-shape review follow-up: addressed 1 review step. Extracted the consumer
  into `test-resources/build/isolated-consumer.clj`, with local functions for
  resource origins, unsupported controls and supported operations. The runner
  copies and executes the script without changing the JAR-only classpath, four
  JVM configurations, check order or exact result assertions. Full build CLI
  passed: 9 tests, 103 assertions, exit zero, no Scry failure files. Command:
  `JAVA_CMD="$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java" VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:build-test -m scry.cli --dir test-build`.
  Focused formatting, project lint, explicit resource lint and whitespace checks
  passed. Production code and design are unchanged; full acceptance was not repeated.

## Code-shape re-review — 2026-10-27

- No new actionable issues.


## Closure — 2026-10-27 — user request

- Closed after implementation, final acceptance, review follow-ups and knowledge
  extraction. All checklist items are complete. Moved the task and retained
  evidence to `munera/closed/`, removed it from the active plan and updated
  incoming links and working memory.
- Closure changes records only. No runtime tests or hosted workflows were
  rerun. Recorded JVM scope and verification limits remain. Historical commands
  retain the task path used at execution; use the closed task path to rerun them.
