# Implementation checklist

Record evidence in `implementation.md`; preserve Scry failure EDN. Require zero
exit, nonzero test count and no failures. Commit each slice; never prepare this checkout.

## Plan review

- [x] Resolve `design-steps.md`’s forced-JIT scope item: parent-only compilation, normal runtime children, unchanged compatibility controls. `plan.md` and Slice 3 define verification and evidence.

## Slice 1 — Facts and checked provisioning

- [x] Create `dev/jvm/jvms.edn` with the existing baseline/Valhalla pins and the selected Temurin 25 smoke build; obtain publisher URLs and SHA-256 values for Linux x64 and macOS ARM64.
- [x] Implement the Java-8-safe facts reader with unique-role, tier, platform, checksum and flag-profile validation; reject invalid or incomplete data with specific errors.
- [x] Implement role executable selection with documented provisioning defaults and explicit environment overrides; verify the selected executable's exact build identity.
- [x] Add a Babashka provisioning command that reads the selected platform facts, verifies downloaded SHA-256 before extraction and returns executable paths.
- [x] Add focused facts/provisioning tests using data and real temporary archives for valid selection, unknown roles/platforms, missing executables, build mismatch and checksum mismatch; assert bad archives are not extracted.
- [x] Run the reader on Java 8 and its focused tests; record both results.
- [x] Extend focused tests to executable selection and real-archive provisioning once implemented.

## Slice 2 — Existing consumers

- [x] Add reader paths to compatibility aliases and verify focused support tests on Valhalla; build alias and build verification remain below.

- [x] Add the development-only reader path to the required aliases and Babashka configuration without changing library paths or runtime dependencies.
- [x] Change `test/compatibility/value_type/jvm_config.clj` to select primary roles and named profiles from facts while retaining independent capability expectations and existing matrix axes.
- [x] Change isolated build consumers in `test/build/build_test.clj` to use facts-selected executables/profiles while retaining JAR-only classpaths and exact resource-origin assertions.
- [x] Change runtime environment diagnostics to link the facts and remove duplicated build/download literals from maintained tests.
- [x] Add configuration regression tests that prove preview-off, export-only and baseline children receive only their selected flags and smoke entries do not expand the primary matrix.
- [x] Run the runtime guide's focused support tests, including `cell-runner-test`, and the build suite on the selected Valhalla parent; record results and inspect the packaged contents.

## Slice 3 — Smoke and forced JIT

- [x] Add `:smoke-test` and `test/smoke/` discovery with shared baseline-safe support but no compatibility-suite discovery or value-only alias.
- [x] Add a smoke startup check that confirms the selected build, classfile API presence, `Class.isValue` absence, warning-free namespace loading and the exact missing-value-predicate report.
- [x] Add separate fresh-process silent/warn/error cases that define two types and check every construction, field, identity, warning-count and undefined-class condition in the design.
- [x] Add the silent same-JDK AOT producer and fresh consumer; remove fixture source before consumption and assert construction, field values, identity and absence of warnings in both processes.
- [x] Add the facts-selected forced-JIT command and reject inherited JVM option variables; verify configuration with changed facts and a real executable.
- [x] Verify `bb test:forced-jit` end to end: candidate facts, preview/export/auto and compilation flags in the Scry parent only; normal preview-only runtime children; unchanged compatibility controls. Reject nonempty `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS` or `_JAVA_OPTIONS`. Discover only `test/runtime/`; propagate Scry exit status.
- [x] Test changed-facts selection, mismatched overrides and suite failure propagation with data and real processes, not mocks.
- [x] Check real Scry parent and `run-in-new-jvm` child input arguments: three compilation flags only in the parent; preview retained in the child. Test rejection of each environment variable above.
- [x] Provision the smoke JDK through checked provisioning and run the smoke suite normally; record the exact build, command, duration and result.
- [x] Run forced JIT on facts-selected EA3; record build, parent/child flags, command, duration and result before admission. State the limit: forced-JIT AOT production, normal runtime AOT consumers and fresh-process probe; no forced-JIT child evidence.

## Slice 4 — Prepared release coordinates

- [x] Add uniquely marked README status/install regions stating no release is published and identifying clone/local-root instructions as development only.
- [x] Add README validation/rendering that installs or replaces Clojars coordinates and publication-conditional status text while preserving other content; reject missing or duplicate regions before writes.
- [x] Update `release/prepare` to validate both documents, compute one version and commit only README.md plus CHANGELOG.md; preserve all existing guards and inspectable failure behavior.
- [x] Extend real-Git preparation tests for first preparation and replacement, reading committed README bytes and asserting version equality, conditional wording, development labels, exactly one two-file commit, clean status and unchanged tags/remotes.
- [x] Extend rejection and hook-failure tests to cover both files, including the post-commit extra-commit guard; run the release suite and record results.

## Slice 5 — Workflow and documentation

- [x] Replace inline JVM downloads/hashes in `release.yml` with checked facts provisioning; keep the tooling JVM separate.
- [x] Replace the remaining inline runtime/fallback launch flags in `release.yml` with facts-selected profiles.
- [x] Add JVM-tooling, smoke and forced-JIT gates to preview and publication before tag creation, each with failure-artifact preservation; retain finite timeouts and all existing quality/runtime/build/release gates.
- [x] Update runtime/release guides: role selection, commands, smoke limits, exact-candidate admission with parent-only forced compilation and normal children. Replace build/download duplication with facts links.
- [x] Update `ramora/release-workflow.md` ownership and flow plus D21 role wording to match facts, two-file preparation and tiers; retain hosted/publication limits.
- [x] Add factual Unreleased notes and update the D21/D22/D24 embodiment gaps and `mementum/state.md` to match only completed, verified work.

## Slice 6 — Acceptance

- [x] Run formatting, lint, prose, API freshness, tooling tests and actionlint; resolve every failure. Slice 6a records passing checks and structured tooling results.
- [x] Run all runtime, three fallback-parent, Java-8-parent cross-JVM, build, release and smoke suites plus forced JIT; retain per-suite commands, durations and structured results. Slice 6b records CLI exit status and the absence of Scry failure EDN.
- [x] Verify fresh Linux x64 facts-based downloads, SHA-256 checks and exact executable builds in an isolated container filesystem (Slice 6d). This is setup evidence, not a preview gate pass.
- [x] Install pinned mise and run Linux preview quality/tooling gates in a full-history checkout (Slice 6e).
- [x] Run Linux JVM-tooling, ordinary smoke and parent-only forced-JIT gates with workflow timeouts (Slice 6f).
- [x] Run Linux runtime, build and release gates with checked role overrides (Slice 6g).
- [x] Run Linux preview notes selection and JAR build without credentials or publication writes (Slice 6h). This is staged evidence, not a complete preview pass.
- [x] Run the Linux Java-8-parent fallback gate; retain exit status, duration and Scry results (Slice 6i).
- [x] Run both Linux EA fallback-parent gates; retain exit status, durations and Scry results (Slice 6j).
- [x] Reproduce both timing failures in isolation and measure baseline serial/concurrent startup (Slice 6l).
- [x] Fix the startup allowance in `timed-out-child-outcome-test` while retaining aggregate timeout and destruction checks; focused Linux and macOS checks pass (Slice 6m).
- [x] Fix startup-dependent timing in `child-limit-test`; preserve exact overlap and permit-wait exclusion, then rerun focused checks before the full gate. Slice 6n records Linux and macOS passes; Slice 6k retains failure EDN.
- [x] Rerun the full Linux cross-JVM gate after both timing fixes with a kill-after bound; Slice 6o records timeout without Scry failures.
- [x] Measure full-gate progress and focused suite cost under Linux emulation; select an evidence-based finite rerun limit. Slice 6p measures two AOT namespaces and selects 7200 seconds for emulation only; total duration still requires a complete run.
- [x] Run the planned 7200-second full-gate experiment; Slice 6q records timeout, empty failure directory and explicit orphan cleanup, not a pass.
- [x] Run bounded function-redefinition and loader-policy namespace checks; both pass with no remaining child processes (Slice 6r). These checks do not locate the full-gate stall.
- [x] Run bounded generated-value AOT namespace diagnosis; 3 tests and 86 assertions pass with no surviving Clojure processes (Slice 6s). This does not reproduce the stall.
- [x] Run bounded identity AOT namespace diagnosis; 2 tests and 58 assertions pass with no surviving Clojure processes (Slice 6t). This does not reproduce the stall.
- [x] Run bounded mixed-field AOT namespace diagnosis; it times out at 900 seconds without a summary; orphan cleanup confirmed (Slice 6u).
- [x] Isolate the five mixed-field AOT vars with bounded runs and structured results; all pass with no surviving Clojure processes (Slice 6v).
- [x] Repeat bounded grouped mixed-field checks with structured per-var progress and process checks. Slice 6w passes both a shared-parent diagnostic and the unchanged namespace CLI; the earlier timeout is not reproduced or explained.
- [x] Add and verify a grouped Scry progress diagnostic without changing discovery or fixture grouping; Linux identity AOT check and callback/result consistency pass (Slice 6x).

### Remaining local acceptance — Linux ARM64

Previous Linux entries above record x64 emulation. Keep that evidence; no
further x64-emulated full-matrix run or timeout diagnosis is required. Do not
mark the unresolved x64 gate as passed. Hosted CI remains Linux x64.

- [x] Add Linux ARM64 archive facts for all three pinned builds, using publisher URLs and SHA-256 values; keep x64 and macOS entries unchanged.
- [x] Add Linux `aarch64`/`arm64` platform detection and focused selection/provisioning tests, including architecture, exact-build and checksum rejection checks.
- [x] Use a separate `linux/arm64` container in an ARM64 VM with full Git history, pinned tooling and a clean provisioning root. Record guest/JVM architectures, checked downloads and exact build identities for all roles.
- [x] Run Linux ARM64 quality, tooling, JVM-tooling and smoke gates with finite bounds and descendant checks (Slice 6aa). Remaining preview gates stay open below.
- [x] Run Linux ARM64 forced-JIT and runtime gates with finite bounds and descendant checks (Slice 6ab). Both pass; the complete preview sequence remains open.
- [x] Run Linux ARM64 three fallback parents and Java-8-parent cross-JVM with finite bounds and descendant checks (Slice 6ac). All pass; build/release and preview artifacts remain.
- [x] Run the complete Linux ARM64 preview gate sequence, including quality, tooling, JVM-tooling, runtime, three fallback parents, Java-8-parent cross-JVM, build, release, smoke and parent-only forced JIT. Retain exit status, duration and structured results for every gate; resolve failures without skipping tests or weakening assertions.
- [x] Run preview notes selection and JAR build on Linux ARM64 without credentials or publication writes. Keep finite bounds and verify descendant cleanup after each process-heavy gate or timeout.
- [x] Update runtime/platform documentation after implementation and repeat affected quality checks and the facts-duplication audit. Keep local ARM64 evidence separate from historical x64 results and unverified hosted execution.
- [x] Audit maintained workflows/tests/guides for duplicated JVM URLs or SHA-256 values and verify facts links, prepared-coordinate wording and tag-after-tests order.
- [x] Map evidence to all five design acceptance criteria in `implementation.md`; verify tracked files are unchanged by checks and report any remaining evidence limits before completion.

## Implementation review follow-ups

- [x] Remove the hard-coded EA version gate from `test/compatibility/value_type/jvm_config.clj`. `configuration` rejects any parent whose version does not start with `27-jep401ea`, even when its exact build matches the selected Valhalla facts. `test/cross-jvm/value_type/array_info_boundary_test.clj` also uses that prefix as a runtime expectation. Select the parent role and check runtime identity from facts, while keeping capability expectations independent. Add a regression check for a facts-selected EA with a different version prefix. An EA update must not require edits to these files in addition to facts and the format row.
- [x] Enforce the successful-preparation postconditions in `dev/release/value_type/release.clj`, not only the commit count. A real `post-commit` hook that appends to README returns success from `prepare` with version `0.1.2` but leaves ` M README.md`. Check the final worktree/index and committed document contents before reporting success; retain failed state for inspection. Add real-Git hook tests for dirty output and changed committed coordinates without an extra commit, alongside the existing extra-commit test.
- [x] Correct the stale verification status in `ramora/release-workflow.md`, under Embodiment: it still says full acceptance and local ARM64 preview remain open and ARM64 provisioning is only planned. Link the completed staged ARM64 evidence from Slices 6z–6ae, while retaining the unresolved historical x64 timeouts and unverified hosted/publication limits. Check agreement with the runtime guide and embodiment gap table.

- [x] Check the release commit's changed paths in `dev/release/value_type/release.clj` before reporting successful preparation. A real post-commit hook can add `unrelated.txt` and run `git -c core.hooksPath=/dev/null commit --amend --no-edit`; `prepare` still returns success because the count, document bytes and clean status all pass. The resulting commit changes three files, contrary to the two-file contract. Compare the resulting commit with the original HEAD, require only `README.md` and `CHANGELOG.md`, and reject unexpected history changes. Add a real-Git regression test that retains the failed state for inspection. This is separate from the completed dirty-output and changed-coordinate checks.

- [x] Remove duplicated current EA build IDs from maintained user guidance. `README.md:28`, the allocation instructions in `doc/usage.md:207–209`, and the public docstring in `src/value_type/core.clj:485` (also rendered in `doc/API.md`) still name `27-jep401ea3+1-1`. D21 requires documents to link the facts instead of repeating build IDs; the allocation guide explicitly tells users to select that build. Link current JVM selection and support guidance to `dev/jvm/jvms.edn` or the facts-based runtime guide, and regenerate API documentation. Audit other maintained guidance for the same issue. Preserve explicitly historical experiment results and their build identities. Verify that a future EA facts update does not leave current installation or API guidance pointing to the previous build.

## Test review follow-ups

- [x] Add a retained real-process regression check for the forced-JIT launcher in `bb/value_type/forced_jit_runner.clj`. The JVM-tooling tests check configuration and command data, but do not execute the launcher. In `test/runtime/value_type/jvm_boundary_test.clj`, all parent checks are conditional on at least one compilation flag already being present: a launcher that drops all three flags still passes that test. Slice 3e records a manual failure-propagation check, not a retained regression test. Exercise the launcher in an isolated temporary project with real processes and input files; require the full parent configuration and a nonzero launcher exit when the selected run fails. Do not add test-only launcher arguments, behavior substitutes or source-text assertions. Keep the normal runtime row valid without forced compilation.

- [x] Remove facts-row order assumptions from `parent-role-test` in `test/compatibility/value_type/jvm_config_test.clj`. It uses `(last entries)` as the ordinary role and `(first entries)` as the baseline. The facts contract identifies rows by `:role`, not position; moving the ordinary row to the front makes this test reject valid selection behavior. Select expected builds by role and exercise reordered facts so this test checks primary-role admission without fixing an implementation-independent data order.

- [x] Bound and verify process-tree cleanup in `test/jvm/value_type/forced_jit_test.clj` (`launch!`). On its 180-second timeout, the helper calls `destroyForcibly` only on the Babashka process, without waiting for termination. The launcher starts Clojure classpath resolution and a separate Scry JVM, so killing only Babashka can leave descendants running while `launcher-process-test` deletes their temporary project. Stop and await the launched process tree before file cleanup, with finite bounds and Java-8-compatible test support. Add a real-process timeout case that confirms the launcher and its descendants have stopped; retain diagnostic output on failure. Keep the existing passing/failing suite checks.

- [x] Add a behavioral discovery-boundary check to `launcher-process-test` in `test/jvm/value_type/forced_jit_test.clj`. Its temporary project contains only `test/runtime/launcher_test.clj`, so a launcher that broadens Scry discovery to `test/` can still pass all three process cases. The pure `jit/command` assertion checks command construction, not the launcher's actual discovery. Add an out-of-tier test fixture that records execution or fails if selected, and assert that the unchanged launcher runs the runtime fixture but does not run that fixture. Keep this check in the existing isolated real-process test; do not add a full matrix run or source-text assertions. Verify that broadening discovery makes the regression check fail.

## Documentation review follow-ups

- [x] Add `dev/jvm/` to the formatting and code-lint scope in `doc/development/quality-checks.md`. The scope list includes `dev/bench/` and `dev/release/` but omits the new facts and JVM tooling directory. `bb/value_type/quality.clj` includes `dev/jvm` in `maintained-paths`, so both checks already cover it. Make the documented scope match the implementation.

- [x] Clarify cached-installation behavior in `doc/development/runtime.md`, under facts-based executable selection. The guide says each provisioning command checks SHA-256 before extraction, but `value-type.jvm-provision/provision!` reuses an existing role/build/platform directory after only executable and exact-build verification. State that SHA-256 is checked for new downloads, that repeat calls do not download or hash the installed files, and how to request a fresh checked installation (for example, select a new ROOT). Keep the archive-integrity claim distinct from the cached build-identity check; `checked-archive-test` already covers reuse.

## Code-shaper review follow-ups

- [x] Enforce safe installation-path components in `dev/jvm/value_type/jvm_facts.clj` and `jvm_executable.clj`. `validate` accepts any nonblank build string, but `installation-directory` uses that string as a directory component. A path-only check with baseline build `../../outside` and root `/tmp/review-jvms` resolves to `/private/tmp/outside/linux-x64` on macOS, outside the selected root. Reject separators and dot path components before provisioning, and require the normalized destination to remain under the selected root. Apply the same invariant to role/platform names used in the path. Keep valid build punctuation such as `+` intact. Add focused tests for accepted current facts and rejected traversal inputs; verify rejection before download, extraction or installation writes. This is a malformed-facts robustness issue, not evidence of an untrusted-input attack.

- [x] Bound executable build verification in `dev/jvm/value_type/jvm_executable.clj` (`verify!`). It uses blocking `clojure.java.shell/sh` with no timeout for `java -XshowSettings:properties -version`. A JVM that stalls during startup can therefore block `resolve!`, cached provisioning, archive installation and compatibility setup before the bounded test runner starts. Use a finite verification timeout; stop and await the process on expiry, and report the role, platform, expected build, executable and captured output. Keep Java-8 compatibility and exact-build checks. Add a real-process timeout regression check that verifies termination and retained diagnostics, alongside successful and mismatched-build cases.
