# Steps

Configuration approval is complete (2026-10-23; see `implementation.md`).
The unchecked actions below are implementation work, not claims of results.
Record commands, versions, outcomes and decisions in `implementation.md` as
each action completes. Commit each completed action with its evidence.

## Slice 1 — Resolve technical decisions

- [x] Record the current dependency and compiler-phase boundaries in `core`, `loader`, `transform`, test support and build consumers; identify where optional APIs first load.
- [x] Select and record Java executable paths and full versions for a supported Clojure JVM without `java.lang.classfile` and supported Valhalla; record commands with preview enabled and disabled.
- [x] Verify a fixed Java 27 preview value-class definition and instantiation on all three runtime configurations (`spike/fallback/format-check.clj`).
- [x] Extend the fixed-format probe with a strict long field and pre-super assignment; verify constructor argument readback on Valhalla and version rejection on the two incapable configurations.
- [x] Verify the strict-field probe using ClassFile.latestMajorVersion, matching the transformer version rule, with preview enabled and disabled.
- [x] Run capability experiments on those JVMs; record required API checks, supported-format checks, preview checks and the exact conditions that may select fallback.
- [x] Verify Java Error cause chains and caught-error retries for macroexpand, macroexpand-1, eval, source loading and runtime calls on all three JVM configurations (`spike/fallback/error-check.clj`); verify the rejected type is absent.
- [x] Run a strict-policy Error experiment through `macroexpand`, `macroexpand-1`, source compilation, `eval` and AOT namespace loading; select the concrete Error type and record each compiler cause chain.
- [x] Verify transferred macroexpand and macroexpand-1 expansions retain an analysis guard; test silent identity definition and repeated strict rejection before class definition through eval and source loading on all three JVM configurations (`spike/fallback/transfer-check.clj`).
- [x] Verify source-free fallback AOT initialization under silent/error on all three JVM settings; record first-failure and retry Error chains (`spike/fallback/aot-check.clj`).
- [x] Verify strict AOT compilation rejects twice without emitting, resolving or loading the rejected type on all three JVM settings (`spike/fallback/aot-check.clj`).
- [x] Prototype a fallback expansion guard; prove that compiling a transferred expansion rejects strict policy before source type definition, and that evaluation and AOT namespace initialization enforce the receiving runtime's policy.
- [x] Verify runtime-only enforcement in a source-free compiled function on all three JVM settings; strict repeated calls throw plain Error (`spike/fallback/runtime-check.clj`).
- [x] Finalize the detector, guard interfaces and state-owner placement in `plan.md` and the fallback/runtime architecture; confirm experiments meet the design before production changes.

## Slice 2 — Add isolated verification support

- [x] Add a baseline-safe JVM test runner with explicit executable, JVM options, policy and classpath; verify structured results, stderr capture, non-zero exits, timeouts and cleanup without loading optional APIs.
- [x] Add a fallback test entry point that does not inherit preview flags or discover value-only tests; verify it runs on the selected non-value JVM and retain existing Valhalla test commands.

## Slice 3 — Implement capability, policy and source fallback

- [x] Implement the single policy/capability state owner using the verified detector and narrow absence classification; verify cached unavailable reasons and successful capability detection on the selected matrix.
- [x] Add isolated configuration tests for absent/default, silent, warn, error and invalid property values; verify one-time initialization does not adopt later property changes.
- [x] Remove eager optional dependencies from the public namespace; verify requiring it succeeds without optional APIs and produces no availability warning under each valid policy.
- [x] Implement fallback selection and the verified compile/evaluation guards while preserving the value-class pipeline; verify basic source definition under all policies on each matrix entry.
- [x] Add fallback compatibility tests for the returned class, factory, import, type hints, methods, protocols/interfaces and ordinary identity/equality/hash behaviour against equivalent `deftype` forms.
- [x] Test mutable-field rejection in both representations, including both mutable metadata keys and false metadata values; preserve existing invalid-form errors.
- [x] Test `macroexpand` and `macroexpand-1` under every policy on incapable JVMs, with and without a compiler-loader binding; verify discarded expansions consume warn state and strict returns no expansion or type.
- [x] Test fallback expansion followed by evaluation in the same runtime; verify shared warning state, silent output and enforcement before source type definition.
- [x] Test repeated strict source and expansion attempts after catching earlier failures; assert the selected Error cause chain and absence of each rejected source type.
- [x] Run D9 tooling and defect regressions on capable Valhalla; verify missing loader bindings, separated expansion/evaluation, loader failures and transform failures do not become successful fallback.

## Slice 4 — Verify runtime isolation and AOT

- [x] Transfer returned fallback expansions to independent strict JVMs, both capable and incapable; compile them directly and through `eval`, and verify rejection before source type definition.
- [x] Verify independent silent and warn runtimes apply their own policy when compiling/evaluating transferred fallback expansions, including on capable Valhalla; confirm representation remains identity.
- [x] Test warning scope across multiple types, namespaces and synchronized concurrent attempts in one runtime; assert exactly one stderr warning and independent warning state in a fresh runtime.
- [x] Add fallback AOT fixtures compiled under silent and warn on incapable JVMs; verify emitted identity classes and strict compilation rejection without rejected type output.
- [x] Load fallback AOT fixtures without consumer source in fresh JVMs under all policies on capable and incapable runtimes; verify silent load, one artifact-specific warning, identity representation and repeated strict rejection with recorded cause chains.
- [x] Verify value-class AOT artifacts still load on their supported preview-enabled JVM and fail rather than convert on unsupported or preview-disabled JVMs; document direct-class-load guard limits.

## Slice 5 — Verify packaging and regressions

- [x] Extend the isolated source-JAR consumer check to the JVM without optional APIs; verify fallback definition and behaviour using only the artifact and POM-resolved runtime JARs, with the public source resource resolved from that artifact.
- [x] Run the isolated Valhalla source-JAR consumer and source-only archive checks; verify value-class creation and no new compiled classes or runtime build dependencies in the JAR.
- [x] Run the full fallback policy/capability matrix through its explicit entry point; record Scry results and inspect failure EDN files for any non-zero run.
- [x] Run the existing transform, loader, core, environment, compatibility and build suites on supported preview-enabled Valhalla; record structured results and resolve failures.

## Slice 6 — Document and close verification

- [x] Update README and public API docstrings with configuration, startup timing, semantic differences, warning scope, strict retries, Error wrapping, AOT limits and verified JDK commands.
- [x] Update fallback decisions, runtime/artifact descriptions, risks and assertions with selected mechanisms and recorded evidence; distinguish tested coverage from the fallback baseline and remaining assumptions.
- [x] Map each of the eight design acceptance criteria to tests and recorded results in `implementation.md`; identify any missing evidence explicitly.
- [x] Review the completed changes against `design.md` and referenced architecture; resolve actionable findings and rerun affected verification before marking the task complete.

## Implementation review follow-ups

- [x] Correct the remaining stale fallback documentation. In `ramora/defvaluetype-design/decisions/validation-and-errors.md`, D9 still says the current implementation reports unavailable JVMs through the value check or class definition, and says only core knows the value predicate. Describe the implemented capability boundary and detector instead. In README's final “Differences from deftype” bullet, restrict the preview-version and same-feature-release AOT requirements to value-class output; ordinary fallback output does not have those requirements.
- [x] Add a production runtime-only guard regression to `test-fallback/value_type/aot_test.clj` and its fixtures. Compile a function containing `value-type.core/defvaluetype` under silent fallback, remove fixture source, and invoke it twice in fresh receiving JVMs. Verify namespace loading succeeds before invocation, strict calls each throw plain Error, and allowed calls retain identity behavior. Run on Java 8 and Valhalla with preview off/on. Existing production AOT tests cover top-level initialization; `spike/fallback/runtime-check.clj` covers this distinct phase only through the prototype `fallback.phase-guard`, not the production macro.

## Test review follow-ups

- [x] Remove the tests of test-only infrastructure in `test-fallback/value_type/jvm_runner_test.clj`. Its four tests cover the runner rather than observable library behavior, contrary to the task-test-review rule against tests covering tests. Keep the runner support needed by the production fallback tests; verify process isolation and diagnostics through those feature tests instead.
- [x] Make `fallback-source-compatibility-test` in `test-fallback/value_type/runtime_test.clj` reject unexpected child results. Its `(when (map? observed) ...)` skips every compatibility assertion for nil, an unexpected scalar, or any other non-map result while exit 0 and empty stderr still pass. Accept only the explicit `:value-mode` outcome on the capable matrix entry; require the full compatibility map on incapable entries. Keep the existing real-JVM comparisons without mocks or tests of the helper itself.
- [x] Verify warning timing before evaluation in `fallback-expansion-policy-test` and `expansion-checks/check-expansions`. The current check counts stderr only after discarded expansions, evaluation and source definitions have all run. It therefore passes if the first warning is delayed until evaluation, contrary to the macroexpansion contract. In fresh warn runtimes, exercise each expansion entry point with bound and unbound compiler loaders, and assert one warning after expansion alone, no defined type, and no additional warning after later evaluation. Use real compiler operations and captured diagnostic state, without replacing production behavior.
- [x] Remove the redundant `require-only-test` in `test-fallback/value_type/runtime_test.clj`, or merge any distinct assertion into `public-require-test`. Both start fresh processes for silent/warn/error and require exit 0 with no diagnostic. Requiring `value-type.core` also loads `impl.runtime`, so the public test already covers the internal require-only contract and additionally checks the optional dependency boundary. Retain that public coverage and avoid three extra JVM starts per matrix run.
- [x] Correct the fallback status in `ramora/defvaluetype-design.md`. Its Summary and Status still say fallback is not implemented or verified and has no implementation evidence; its AOT guard is described as intended. Align these claims with the production tests and scoped Java 8 / Valhalla 27 evidence in `fallback.md` and task 009. Keep the distinction between tested coverage and the general fallback baseline.
- [x] Make `fallback-expansion-policy-test` in `test-fallback/value_type/runtime_test.clj` select expectations from the known JVM matrix entry, not from the child result. Its `:value-mode` branch currently accepts that result on an incapable JVM and skips all bound/unbound expansion, strict-retry and rejected-type checks. Require `:value-mode` only on the capable entry; require the complete phase results on incapable entries under every policy. The warning-timing test covers only warn and does not close this strict-policy gap. Retain real compiler operations and do not add a separate helper test.
- [x] Make `public-definition-test` in `test-fallback/value_type/runtime_test.clj` derive expected capability, representation and diagnostics from the selected JVM matrix entry, not the child's `:available?` result. It currently accepts identity output or strict rejection on a capable JVM if that child reports unavailable capability. Assert the reported capability against the independent expectation, then require value output under all policies on capable Valhalla and the specified fallback outcomes on incapable entries. Keep the existing fresh processes; do not add a duplicate test.

## Documentation review follow-ups

- [x] Correct the stale fallback evidence statements in `ramora/defvaluetype-design/qualities.md`. The paragraph after the quality table says fallback conditions are not observed and verification covers only the value path; the semantic-differences section still says “when fallback is built.” Describe implemented fallback and link the scoped Java 8 / Valhalla 27 production evidence in `fallback.md`. Keep the general baseline constraint separate from tested coverage.
- [x] Correct the dependency claims in the “Boundaries” section of `ramora/defvaluetype-design/embodiment/runtime.md`. It says only the loader knows `DynamicClassLoader` and a predicate change affects core, but `impl.capability` also constructs `DynamicClassLoader` and resolves `Class.isValue`. State the shared detector dependencies while keeping `Compiler/LOADER` and `Compiler/writeClassFile` coupling assigned to the loader. Check against `src/value_type/impl/capability.clj` and `core.clj`.
- [x] Qualify the AOT namespace-load guarantees in README's “AOT limits,” `fallback.md` D13, and the runtime embodiment invariant, and align the public macro docstring. The guard runs where the fallback form is evaluated: a top-level form enforces policy during namespace initialization, but a form inside a function permits namespace loading and enforces policy when that function runs. `source-free-runtime-only-fallback-test` and `aot-checks/invoke-runtime-fixture` explicitly verify successful strict loading followed by two plain Error rejections. Document this distinction so users do not infer that strict policy rejects every namespace containing compiled fallback during require. Retain the existing direct-class-access and failed-initializer limits.
- [x] Apply the completed AOT timing correction to the remaining architecture summaries: `ramora/defvaluetype-design.md` (Summary), `ramora/defvaluetype-design/embodiment/artifacts.md` (Library packaging), `ramora/defvaluetype-design/risks-and-evolution.md` (AOT dependency risk and guard limits), and `ramora/defvaluetype-design/assertions/fallback-architecture.md` (A-36). These still describe a namespace-load guard without the form-evaluation limit. Align them with D13 and `source-free-runtime-only-fallback-test`: top-level forms enforce policy during initialization; forms inside functions allow namespace loading and enforce policy on invocation. Preserve direct-class-access and failed-initializer limits. The earlier completed step corrected README, D13, runtime embodiment and the macro docstring, not these remaining locations.

## Code-shaper review follow-ups

- [x] Give fallback test JVM configuration one baseline-safe owner. `runtime_test.clj` repeats host preview/version interpretation in five tests, while `aot_test.clj` uses a different capability rule (`seq options`); Java executable, preview options and producer overrides are also repeated across `runtime_test.clj`, `transfer_test.clj` and `aot_test.clj`. Extract a small explicit configuration map for the selected matrix entry and producer paths, and pass it to the existing runner. Keep expected capability independent of production detector results, retain deliberate preview omission for incapable AOT compilation, and fail clearly for an unsupported test configuration. Verify the existing suites on all three documented matrix entries; do not add tests of test support.
- [x] Separate AOT fixture lifecycle from scenario assertions in `test-fallback/value_type/aot_test.clj`. All four tests repeat temporary root/source/output creation and recursive cleanup; three also repeat the same top-level fixture source. Introduce a small baseline-safe fixture helper that takes fixture namespace/source and a callback with explicit paths. Keep producer/consumer policy, source deletion before consumption, classpath exclusion and phase-specific assertions visible in each test. Do not reuse `value-type.test-support`, which eagerly loads optional implementation code. Run the existing AOT suite on all three matrix entries to verify that cleanup and source-free coverage are preserved.
