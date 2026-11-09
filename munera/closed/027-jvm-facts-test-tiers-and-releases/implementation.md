# Implementation notes

## 2026-11-08 — Architecture review

- No architectural review feedback. Scope unchanged.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context:
  `AGENTS.md`, `ramora/INDEX.md`, defvaluetype decisions D21/D22/D24 in
  `ramora/defvaluetype-design/decisions/verification-and-release.md`, and
  `ramora/release-workflow.md`. The task's plan and steps were not reviewed.

## 2026-11-08 — Ambiguity review

- Ambiguity review added 3 new design steps. Scope unchanged.
- JIT flags and the smoke-JDK build are explicit planning choices, not review
  findings. The task's plan and steps were not reviewed.

## 2026-11-08 — Inconsistency review

- Inconsistency review added 1 new design step. Scope unchanged. Existing
  ambiguity findings were not repeated. The task's plan and steps were not
  reviewed.

## 2026-11-08 — Design follow-up handoff

- Keep execution flags specific to each test configuration. The isolation and
  preview-free control rules are in `doc/development/runtime.md` under "Test
  layout" and "Child JVMs"; required facts-file flags must not enable preview
  or exports in negative controls.
- Release preparation is local; publication remains a separate operation.
  Consult `dev/release/value_type/release.clj`,
  `test/release/value_type/release_test.clj` and `doc/development/release.md`
  for commit-count versioning and inspectable failures. A second commit to
  repair README coordinates changes the version; do not assume it is neutral.

## 2026-11-08 — Design follow-up implementation guidance

- `impl.capability/required-apis` checks the classfile API before `Class.isValue`;
  the value-class switch is checked before both. Use `auto` to reach the
  smoke JDK's missing-predicate path.
- `test/compatibility/value_type/aot_fixture.clj` already provides a producer
  that deletes fixture source before consumption (`with-produced`).
- Release tests create a repository with only `CHANGELOG.md`; add README
  fixtures for first preparation and version replacement. The existing
  preparation test asserts a changelog-only commit.

## 2026-11-08 — Ambiguity re-review

- No new ambiguity review feedback. Scope unchanged; prior follow-ups are resolved.

## 2026-11-08 — Inconsistency re-review

- No new inconsistency review feedback. Scope unchanged; the release ownership
  update is already required by the design and covered by the prior follow-up.

## 2026-11-08 — Review handoff

- When changing preparation to include README, retain the post-commit version
  check and inspectable failure state. A successful Git command can still run
  a hook that adds another commit. See
  `mementum/knowledge/release-identity-and-failure-boundaries.md` for the real-Git
  failure test and why a pre-commit rejection does not cover this case.

## 2026-11-08 — Plan ambiguity review

- Ambiguity review added 1 new design step: forced-JIT scope for runtime child
  JVMs. Reviewed the plan and referenced launchers, tests and release docs;
  `steps.md` remained read-only. Scope unchanged.

## 2026-11-08 — Plan inconsistency review

- No new inconsistency review feedback. Reviewed the plan against the design
  and read-only steps. The forced-JIT runtime-child ambiguity remains in
  `design-steps.md`; no duplicate follow-up added.

## 2026-11-08 — Plan review handoff

- `test/runtime/value_type/test_support.clj` selects child Java from the
  parent's `java.home`; the forced-JIT question concerns flags, not a separate
  JVM pin. Its child timeout is 120 seconds. If child coverage is selected,
  verify flags inside a real child and measure this timeout separately from
  the outer suite limit. Keep normal runtime runs unchanged; avoid global
  JVM environment options that can also affect compatibility controls.

## 2026-11-08 — Plan-review follow-up resolution

- Resolved the runtime-child scope item with parent-only forced compilation.
  `steps.md` records planning completion; `design-steps.md` is unchanged as
  the attributed review input. No runner has been implemented or verified.
- `support/aot-compile` runs inside the parent, unlike `run-in-new-jvm`.
  Thus `test/runtime/value_type/core_test.clj` (`aot-new-jvm-test`) and
  `deftype_compat_test.clj` (`aot-clj-979-clj-1208-test`) split production
  and consumption across the compilation boundary. In
  `non_nil_fields_test.clj`, `native-probe-scope-test` runs its probe checks
  in the normal child. Preserve this distinction in admission evidence.

## 2026-11-08 — Plan ambiguity re-review

- No new ambiguity review feedback. The revised plan resolves the prior
  runtime-child scope finding and defines its verification. Reviewed referenced
  launchers, tests and release docs; `steps.md` remained read-only.

## 2026-11-08 — Plan inconsistency re-review

- No new inconsistency review feedback. The revised plan and read-only steps
  agree on the runtime-child boundary and verification; the retained review
  input in `design-steps.md` is already resolved in the task records.

## 2026-11-08 — Review handoff for quality integration

- `bb/value_type/quality.clj` explicitly lists `maintained-paths` and
  `dependency-aliases`; it does not discover new directories or aliases.
  Include the new JVM tooling and smoke/JVM tests in these lists so the
  planned quality checks cover them.

## 2026-11-08 — Slice 1a: facts and validated selection

- Added `dev/jvm/jvms.edn` and Java-8-safe `value-type.jvm-facts`.
  `read-facts` accepts an explicit source; `select` takes facts, role, platform
  and profile. It returns one archive and only that profile's flags. No runtime
  consumer has migrated yet. Executable verification and provisioning are next.
- Publisher metadata reconfirmed Temurin 25.0.5+8-LTS for both platforms,
  and the baseline macOS x64 checksum. Valhalla macOS checksum came from
  the publisher's archive `.sha256`; Linux pins retain the workflow values.
  The version-specific Adoptium 25 API returned 404; latest metadata resolved
  the planned exact build and supplied immutable release URLs. This is not
  archive download or smoke evidence. macOS baseline requires Rosetta.
- Added `:jvm-test` and quality scope entries, without changing library paths.
  Tests use real facts and pure malformed-data cases; no mocks.
- Verification: `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:jvm-test`
  exited 0 on Temurin `1.8.0_492-b09`: 2 tests, 21 assertions, no failures
  or errors; `.scry-results` empty. Focused cljfmt check and clj-kondo
  passed (0 warnings/errors). No full-suite or admission claim.
- Design unchanged. The facts describe proposed tier selection, not proof
  that the smoke or forced-JIT rows have passed.

## 2026-11-08 — Slice 1b: executable selection

- Added `value-type.jvm-executable`: explicit environment/root inputs, supported
  host selection, shared installation-directory layout and exact
  `java.runtime.version` verification through a real `-XshowSettings:properties
  -version` process. No preview/export flags are added. Overrides do not bypass
  verification. Provisioning must extract the archive root into this directory.
- Real-process tests use the parent executable and its runtime version; they
  cover successful override resolution, build mismatch and missing paths.
  Pure tests cover default paths, blank overrides and unsupported platforms.
  No mocks or new dependencies. Design unchanged.
- Verification: `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:jvm-test`
  exited 0: 5 tests, 30 assertions, no failures/errors; `.scry-results` empty.
  `bb clj-fmt check dev/jvm test/jvm` and
  `mise exec -- clj-kondo --lint dev/jvm test/jvm` passed with no findings.
- Next: checked archive provisioning and real-archive tests. Existing launchers
  have not migrated; this pass does not establish smoke or forced-JIT evidence.

## 2026-11-08 — Slice 1c: checked provisioning

- Added `bb jvm:provision ROLE [PLATFORM [ROOT]]`. HTTPS-only curl downloads
  have finite timeouts. SHA-256 is checked before tar extraction; staging is
  removed on failure. Exact build verification precedes installation. Existing
  installations are verified, not overwritten. No dependencies added.
- Real temporary tar tests link to the real parent JVM: checksum rejection
  creates no extraction directory; wrong builds leave no installation; valid
  archives install and can be reused. A standard digest vector checks hashing.
- Java 8 `clojure -M:jvm-test` with the previously recorded `JAVA_CMD` passed:
  7 tests, 40 assertions, no failures/errors; `.scry-results` empty. Focused
  cljfmt and clj-kondo passed. Babashka archive tests passed (2 tests, 10
  assertions) with java.home/runtime.version set to the installed Java 8:
  native-image's own java.home has no executable, so it cannot supply this fixture.
- `bb jvm:provision baseline macos-aarch64` completed a real publisher download,
  checksum check, extraction and exact-build check into the default cache.
  `bb jvm:provision unknown` rejected the role before downloading.
- Design unchanged. Next slice migrates consumers and their alias paths.
  Provisioning ignores executable overrides deliberately: it installs facts;
  launch selection separately checks overrides. No smoke/JIT admission claim.

## 2026-11-08 — Slice 2a: compatibility selection

- Compatibility aliases now include the development reader. `role-runtime`
  resolves exact builds and one named profile, preserving the supplied
  classpath. Configuration verifies the parent and selects only the primary
  pair. Independent capability expectations and matrix axes are unchanged.
- Platform selection accepts macOS x86_64 because the pinned Java 8 parent
  runs under Rosetta. A real Java 8 configuration check resolved the cached
  baseline and explicit Valhalla override successfully.
- Valhalla `clojure -M:cross-jvm-test` with the runtime guide's six support
  namespaces plus `cell-runner-test` and `jvm-config-test` passed: 35 tests,
  249 assertions. Java 8 focused configuration tests passed: 2 tests,
  9 assertions. Java 8 `-M:jvm-test` passed: 7 tests, 41 assertions.
  Scry failure directory empty. Focused cljfmt and clj-kondo passed.
- Tests use real executable verification and changed facts, without mocks.
  An attempted `-e` through the Scry main alias was rejected; the subsequent
  configuration check used plain `-M` with explicit paths and succeeded.
- Design unchanged. Next: build consumer/alias migration and runtime
  diagnostics; full matrix and build verification remain open.


## 2026-11-08 — Slice 2b: isolated build consumers

- Build consumers now resolve baseline/Valhalla roles and named profiles from
  facts, independently of the parent JVM. Consumer controls remain separate;
  JAR-only classpaths, exact resource origins and capability expectations are
  unchanged. Added JVM-tooling exclusion fixtures to the archive contract.
  Only `:build-test` gains `dev/jvm`; library and build-tool paths are unchanged.
- Runtime startup diagnostics now point to facts and the runtime guide.
  Design unchanged. No dependencies added.
- On the facts-selected Valhalla override at
  `~/.local/share/mise/installs/java/openjdk-27.0.0-jep401ea3+1-1/bin/java`,
  `clojure -M:build-test -m scry.cli --dir test/build` passed in 17.1 seconds:
  9 tests, 114 assertions, including archive inspection and all four isolated
  consumers. The baseline used the checked default cache installation.
- With the same `JAVA_CMD` and `VALUE_TYPE_VALHALLA_JAVA`, the runtime guide's
  six support namespaces plus `cell-runner-test` and `jvm-config-test` through
  `-M:cross-jvm-test` passed in 72.1 seconds: 35 tests, 249 assertions.
  `clojure -M:test -m scry.cli --namespace value-type.environment-test` passed:
  3 tests, 3 assertions. All exited 0; Scry failure files absent. Focused
  cljfmt and clj-kondo passed with no findings.
- Next: smoke and forced-JIT runners. Full matrix verification remains in
  Slice 6. `mementum/knowledge/testing-library-artifacts.md`'s parent-JVM
  selection paragraph is now stale; the consumer selects the verified facts
  role, not `java.home`. Refresh that knowledge during task extraction.


## 2026-11-08 — Slice 3a: ordinary startup

- Added `:smoke-test`, scoped discovery and quality integration. It reuses the
  baseline-safe process runner without discovering compatibility tests. The
  parent can be Java 8; children resolve and verify the ordinary facts role.
- Startup runs in three fresh real processes, one per policy. Each checks the
  exact build, classfile API, absent value predicate, exact capability reason,
  empty stderr and input flags (auto plus policy only). No mocks or new
  dependencies. Design unchanged.
- `bb jvm:provision ordinary macos-aarch64` verified and installed the pinned
  publisher archive. Build: `25.0.5+8-LTS`.
- `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
  clojure -M:smoke-test` passed in 10.33 seconds: 1 test, 21 assertions,
  zero failures/errors, exit 0, empty `.scry-results`. Focused cljfmt and
  clj-kondo passed with no findings.
- Next: definition-policy cases and source-free AOT, using the same selected
  runtime and baseline-safe support. Provisioning and startup alone do not
  establish the full smoke contract or EA admission.

## 2026-11-08 — Slice 3b: smoke definition policies

- Added two definitions per fresh policy process. Silent/warn check both field
  reads, distinct identity, unequal equal-field instances and monitor support;
  warn emits one fallback diagnostic. Error checks both root Errors and absent
  classes through the definition loader. No dependencies or design changes.
- The ordinary JDK has no `Class.isIdentity` method. An initial run failed on
  that assumption; the corrected test uses successful monitor acquisition as
  identity evidence, together with identity/equality behavior. Structured Scry
  failures were inspected before correction.
- Java 8 parent: `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:smoke-test`
  passed in 8.13 seconds on facts-selected ordinary `25.0.5+8-LTS`: 2 tests,
  38 assertions, exit 0, no failures/errors, empty `.scry-results`.
  Focused cljfmt and clj-kondo checks passed after formatting.
- Next: source-free same-JDK AOT. This pass does not establish the complete
  smoke tier or forced-JIT admission evidence.

## 2026-11-08 — Slice 3c: source-free smoke AOT

- Reused the baseline-safe AOT fixture lifecycle: silent production deletes
  fixture source before a fresh silent consumer loads compiled output. The
  consumer also checks that fixture source is absent from its classpath, both
  field reads, distinct identity, inequality and monitor support. Both processes
  require empty stderr. No dependencies, runtime API or design changes.
- Java 8 parent: `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:smoke-test`
  passed in 9.30 seconds on facts-selected ordinary `25.0.5+8-LTS`: 3 tests,
  45 assertions, exit 0, no failures/errors, empty `.scry-results`. This completes
  the bounded smoke contract, using the checked installation from Slice 3a.
  Focused cljfmt passed after formatting; clj-kondo had no findings.
- Next: forced-JIT runner and real-process boundary tests. Smoke success does
  not admit the EA candidate or establish forced-JIT coverage.

## 2026-11-08 — Slice 3d: forced-JIT launch configuration

- Added `bb test:forced-jit`. It verifies the current facts' EA executable,
  resolves the runtime classpath, then launches Java directly with parent-only
  compilation flags. It reports selected build, parent/child flags, elapsed
  time and exit status. Runtime child code is unchanged. No dependencies or
  design changes.
- Nonempty global JVM option variables are rejected before resolution,
  including whitespace. Configuration tests use changed facts and the real
  parent executable; mismatched builds fail. No mocks.
- Java 8 `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java clojure -M:jvm-test`
  passed: 9 tests, 56 assertions, exit 0, no Scry failure files. Focused
  formatting and lint passed. `JAVA_TOOL_OPTIONS=-Xcomp bb test:forced-jit`
  failed before launch with the expected inherited-options diagnostic.
- Next: test the real Scry parent and runtime child argument boundary and
  suite-failure propagation, then run the complete EA forced-JIT row. The
  launcher has not yet established forced-JIT admission evidence.

## 2026-11-08 — Slice 3e: forced-JIT boundary and admission

- The first real launch exposed a Babashka shell argument bug: a command vector
  was treated as one executable name. Apply the command arguments to `shell`.
  No dependency, design or runtime-child changes.
- Added a runtime boundary test using ManagementFactory and `run-in-new-jvm`.
  In the forced row it checks all three compilation flags, preview, exports and
  auto in the Scry parent. In both rows the actual child arguments are exactly
  `["--enable-preview"]`. Normal focused run: 1 test, 3 assertions, exit 0.
- With `VALUE_TYPE_VALHALLA_JAVA=$HOME/.local/share/mise/installs/java/openjdk-27.0.0-jep401ea3+1-1/bin/java`,
  `bb test:forced-jit` passed twice on the facts-selected `27-jep401ea3+1-1`:
  24.23 and 24.26 seconds, 191 tests, 5237 assertions, exit 0 and no Scry
  failure files. Parent flags: preview, internal-value export, explicit auto,
  `-Xcomp -Xbatch -XX:-TieredCompilation`. This admits this exact EA configuration
  for the bounded row: forced AOT production, normal AOT consumers and probe
  children. It is not forced-JIT child evidence or full release acceptance.
- Real CLI checks rejected each of JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS and
  _JAVA_OPTIONS set to `-Xcomp` (exit 1 before launch). A temporary runtime
  test with one deliberate failing assertion made the full launcher return 1;
  Scry EDN identified that assertion. Removed the temporary test, then repeated
  the successful full row. Expected failure EDN was copied to
  `/tmp/task027-expected-failure-results` (local diagnostic, not durable evidence).
- Java 8 `clojure -M:jvm-test` passed: 9 tests, 56 assertions, including
  changed-facts and mismatch checks. Focused cljfmt and clj-kondo passed.
  Discovery remains restricted to `test/runtime`; compatibility code is unchanged.
- Next: Slice 4, README regions and real-Git two-file preparation contracts.

## 2026-11-08 — Slice 4a: README regions and rendering

- Added status/install markers and development-only clone/local-root wording.
  `value-type.readme/render` validates both unique, ordered, non-overlapping
  regions before returning prepared coordinates. It preserves outside bytes,
  replaces prior versions and states the publication condition. No dependencies
  or design changes. Preparation still commits only the changelog until the
  next slice integrates this renderer; this checkout was not prepared.
- Pure tests cover first/replacement rendering, repeat rendering, missing and
  duplicate markers, reversed/overlapping regions and invalid versions.
  Java 8 `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
  clojure -M:release:release-test -m scry.cli --dir test/release` passed:
  14 tests, 155 assertions, exit 0, no Scry failure files. Focused cljfmt and
  clj-kondo passed with zero findings.
- Next: call the renderer alongside changelog stamping before either write;
  extend real-Git fixtures and guard/hook tests to both files. Keep the
  post-commit count check and inspectable partial failures.

## 2026-11-08 — Slice 4b: two-file preparation

- Preparation now renders README and stamps changelog before either write,
  then stages and commits only those files. Existing guards and the post-commit
  count check remain. No dependencies or design changes; this checkout was
  not prepared or published.
- Real-Git tests read committed README bytes for first and replacement
  preparation. They verify one two-file commit, version agreement, publication
  conditions, development labels, clean status and unchanged remote refs/tags.
  Invalid README regions preserve both files; rejecting and extra-commit hooks
  retain prepared README state for inspection.
- Java 8 `JAVA_CMD=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
  clojure -M:release:release-test -m scry.cli --dir test/release` passed:
  15 tests, 175 assertions, exit 0, no Scry failure files. Focused cljfmt and
  clj-kondo passed with zero findings.
- Next: Slice 5 workflow and documentation integration. Full acceptance and
  hosted/publication verification remain outside this slice.

## 2026-11-08 — Slice 5a: checked workflow provisioning and tier gates

- Release now provisions all three roles through the checked command into
  RUNNER_TEMP, then exports the returned executable paths. Tooling JAVA_CMD
  stays separate. New JVM-tooling, smoke and forced-JIT gates run in both
  preview and publication before notes, credentials or tags. Each has a
  ten-minute timeout and its own immediate Scry failure upload. No design,
  dependency or library API changes.
- actionlint 1.7.12 passed for release.yml. Real local gate commands passed:
  Java 8 `clojure -M:jvm-test`: 9 tests, 56 assertions;
  Java 8 `clojure -M:smoke-test`: 3 tests, 45 assertions, 9.39 seconds;
  `bb test:forced-jit`: 191 tests, 5237 assertions, 24.34 seconds.
  Each exited 0 with no Scry failure files. Smoke selected ordinary
  `25.0.5+8-LTS`; forced JIT selected `27-jep401ea3+1-1`, with the previously
  recorded parent-only flags. Baseline and ordinary provisioning reused
  verified macOS cache installations; EA used the verified role override.
- Next: remove the remaining inline runtime/fallback flags through facts
  profile selection, then update maintained documentation. This is not Linux
  preview emulation or hosted Actions evidence. Full acceptance stays open.

## 2026-11-08 — Slice 5b: workflow flag profiles

- Runtime and preview-enabled fallback workflow steps now select the
  `:preview-export` and `:preview` profiles through the validated facts reader.
  Shell arrays preserve separate CLI arguments. Plain controls remain without
  added flags; the runtime alias retains its existing preview option. No
  library, dependency or design changes.
- `mise exec actionlint -- actionlint .github/workflows/release.yml` passed.
  The default mise environment did not activate actionlint; explicit tool
  selection resolved this without changing project configuration.
- Executed the workflow's runtime profile extraction and launch on local EA3:
  `clojure "${jvm_flags[@]}" -M:test -m scry.cli --dir test/runtime`
  passed in 3.78 seconds: 191 tests, 5233 assertions. The fallback preview
  extraction with `-M:fallback-test --namespace value-type.jvm-config-test`
  passed in 1.25 seconds: 2 tests, 9 assertions. Both exited 0 with no Scry
  failure files. Existing real configuration tests cover profile separation;
  no source-text tests or mocks were added.
- Next: maintained documentation and remaining acceptance checks. These local
  macOS launches do not establish Linux preview or full fallback-matrix evidence.

## 2026-11-08 — Slice 5c: operator guides

- Runtime/release guides now describe checked provisioning, role overrides,
  smoke limits and exact-candidate admission. They state parent-only forced
  compilation, normal runtime children and two-file preparation with conditional
  coordinates. Removed stale migration and parent-selected build-consumer claims.
  No design, code, dependency or test-contract changes.
- `bb vale` passed with no alerts after corrections; `git diff --check` passed.
  The Vale wrapper accepts no path arguments; use the whole maintained-prose
  check. No suites rerun for this documentation-only slice.
- Next: architecture ownership/D21 wording, Unreleased notes and embodiment
  gaps, then full acceptance checks and Linux preview emulation. These guides
  do not establish new JVM, hosted workflow or publication evidence.

## 2026-11-08 — Slice 5d: architecture and release notes

- Architecture now assigns two-file preparation and README rendering to their
  owners, includes ordinary smoke roles and describes checked provisioning
  and parent-only forced JIT. Removed obsolete D21/D22/D24 implementation
  gaps; full acceptance remains explicitly open. Added Unreleased notes and
  synchronized project state. Design and test contracts are unchanged.
- `bb vale` passed with no alerts; `git diff --check` passed. This slice changes
  documentation only, so no runtime tests were added or rerun.
- Next: Slice 6 quality checks, all suites, Linux preview emulation and the
  acceptance evidence map. No new JVM or publication evidence is claimed.

## 2026-11-08 — Slice 6a: development quality acceptance

- On macOS ARM64 with tooling OpenJDK `25.0.2+10-69`, all commands exited 0:

  | Command | Wall time |
  | --- | --- |
  | `bb clj-fmt:check` | 1.75 s |
  | `bb clj-kondo:lint` | 6.54 s |
  | `bb vale` | 0.41 s |
  | `bb api-docs --check` | 2.69 s |
  | `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir test/tooling` | 20.25 s |
  | `mise exec actionlint -- actionlint` | 0.04 s |

- Lint: zero errors/warnings, three informational redundant-double notices
  in existing compatibility/loader tests. Prose: no alerts. Actionlint checked
  all workflows. Checks left tracked files unchanged; no Scry failure files.
- A second in-process Scry run of all three tooling namespaces returned
  `{:pass? true :summary {:test 11 :pass 245 :fail 0 :error 0
  :duration-ms 17661.882917 :var-count 11 :fail-var-count 0}}`.
  Full local results: `/tmp/task027-quality/tooling.edn`; command log:
  `/tmp/task027-quality/results.log`. These temporary files are not durable
  artifacts; the summary above retains the verification evidence.
- No code, dependency or contract changes were needed; design remains unchanged.
  Next: run the complete JVM suite sequence and Linux preview emulation, then
  map all acceptance evidence. This pass does not claim those checks passed.


## 2026-11-08 — Slice 6b: complete local JVM suite sequence

- macOS ARM64; baseline `1.8.0_492-b09` from checked cache (Rosetta),
  Valhalla `27-jep401ea3+1-1` from the verified mise override, ordinary
  `25.0.5+8-LTS` from checked cache. Default child limit: 4.
  `VALUE_TYPE_BASELINE_JAVA` selected the cached baseline;
  `VALUE_TYPE_VALHALLA_JAVA` selected the same EA executable as Slice 3e.
  No global JVM option variables were set.
- All commands below exited 0. Each CLI reported nonzero test counts and
  zero failed/errored tests or assertions. Scry produced no failure EDN;
  per-run copies of `.scry-results` contain no files. Local logs and exact
  commands are in `/tmp/task027-suites/` (not durable artifacts).
  Times are whole wall-clock seconds, except the runner's own JIT duration.

  | Parent | Command | Seconds | Tests | Assertions |
  | --- | --- | ---: | ---: | ---: |
  | EA | `clojure -J--enable-preview -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime` | 4 | 191 | 5233 |
  | Java 8 | `clojure -M:fallback-test` | 184 | 77 | 2624 |
  | EA, preview off | `clojure -M:fallback-test` | 63 | 77 | 2624 |
  | EA, preview on | `clojure -J--enable-preview -M:fallback-test` | 66 | 77 | 2624 |
  | Java 8 | `clojure -M:cross-jvm-test` | 549 | 115 | 5093 |
  | EA | `clojure -M:build-test -m scry.cli --dir test/build` | 17 | 9 | 114 |
  | Tooling | `clojure -M:release:release-test -m scry.cli --dir test/release` | 8 | 15 | 175 |
  | Java 8 | `clojure -M:smoke-test` | 10 | 3 | 45 |
  | Facts-selected EA | `bb test:forced-jit` | 25 | 191 | 5237 |
  | Java 8 | `clojure -M:jvm-test` | 7 | 9 | 56 |

- Parent selection uses `JAVA_CMD` for each Clojure command. Forced JIT
  reported `{:exit 0 :elapsed-seconds 25.449273333}`. Its configuration and
  parent-only compilation boundary are unchanged from Slice 3e; runtime
  children remain normal preview-only processes. No new admission claim
  for a different configuration is made.
- `git diff --exit-code HEAD --` passed after all checks, before task-record
  edits. No code, dependencies, tests or design contracts changed. Existing
  real-process and real-Git tests cover this acceptance slice without mocks.
- Next slice: Linux preview emulation, then the duplication/tag-order audit
  and acceptance map. Local macOS success is not Linux or hosted Actions
  evidence. Do not prepare or publish this checkout.


## 2026-11-08 — Slice 6c: maintained-source and release-order audit

- Searched tracked workflows, tests, guides, README, development tooling and
  architecture records for archive URLs and 64-digit hexadecimal values.
  JVM publisher URLs occur only in `dev/jvm/jvms.edn` outside historical Munera
  records. Other matches are the SHA-256 `abc` test vector and retained
  experiment/agent hashes, not JVM archive facts. Runtime and release guides
  link the facts file. Historical evidence is unchanged.
- README correctly has no published coordinates before preparation and labels
  clone/local-root installation as development only. The renderer's prepared
  coordinates remain conditional on publication; Slice 4b supplies real-Git
  first/replacement and two-file commit evidence. No preparation was run.
- Inspected release step order: checked provisioning and every quality/test
  gate precede notes selection, credential validation and tag creation.
  Preview builds the JAR; tag, deploy and GitHub Release steps each require
  explicit false for dry-run. Provisioning checks the digest before extraction
  and exact build before installation. No workflow changes were needed.
- `mise exec actionlint -- actionlint .github/workflows/release.yml` and
  `git diff --exit-code HEAD --` passed before task-record edits. No code or
  test contracts changed; no new tests were required. Design remains unchanged.
- Linux setup check: the existing Podman machine is running;
  `podman run --rm --platform linux/amd64 ubuntu:24.04 uname -m` returned
  `x86_64` with exit 0. This proves emulation availability only, not JVM
  provisioning or any Linux gate. The next slice must run checked downloads
  and the full preview gate sequence in an isolated container filesystem,
  then select preview notes and build without credentials. Do not reuse old
  containers as evidence or extract JVM archives onto the macOS shared mount.
- Acceptance criteria 1 and the static ordering/wording parts of 2 and 4 pass.
  Full Linux preview evidence (criterion 2) and the final five-criterion map
  remain open. There is no human blocker; hosted Actions and publication remain
  outside the local evidence.


## 2026-11-08 — Slice 6d: fresh Linux checked provisioning

- Created `vt-task027-preview` from `ubuntu:24.04` with Podman
  `--platform linux/amd64`. The guest reports Ubuntu 24.04.5 LTS,
  Linux x86_64. Copied `git archive 0d9dc4a1a733f1ea73d6032320d9d96de2e56c89`
  into `/work`; no shared host extraction paths or old containers were used.
- Installed bootstrap packages and Babashka 1.13.225 through mise. Bootstrap
  installed mise 2026.11.3, not the workflow's pinned 2026.4.11. The next slice
  must select the pinned mise before claiming workflow-tooling parity.
- In `/work`, each `mise exec -- bb jvm:provision ROLE linux-x64 /jvms`
  exited 0 after a fresh publisher download, digest check, extraction and
  exact-build check: baseline 22 s, Valhalla 18 s, ordinary 12 s.
  Direct `java -version` also passed for each installed executable:
  `1.8.0_492-b09`, `27-jep401ea3+1-1`, `25.0.5+8-LTS`.
- Local setup evidence and executable paths remain in the container's
  `/evidence/`; these are temporary, not durable artifacts. No tests, API,
  dependencies or design contracts changed. `git diff --exit-code HEAD --`
  passed before record edits. No preparation or publication ran.
- Next: install pinned workflow tools, copy full Git history into the isolated
  checkout (the archive has no `.git`), and run all preview gates, notes selection
  and JAR build. Preserve per-gate exit status, durations and Scry failure EDN.
  Provisioning success alone does not satisfy the Linux preview acceptance item.
  There is no human blocker; hosted Actions and publication remain unverified.

## 2026-11-08 — Slice 6e: Linux quality and tooling gates

- Installed workflow-pinned mise `2026.4.11` in `vt-task027-preview`.
  Cloned a full-history Git bundle into `/checkout` at `126b2a51`;
  use this checkout, not the older `/work` archive, for remaining gates.
  `mise install` confirmed pinned tools. A transient plugin metadata warning
  did not recur when Clojure ran.
- Ran `/tmp/quality.sh` with `mise env -s bash`, tooling
  `JAVA_CMD=$(mise which java)` and role overrides from `/evidence/*.path`.
  All commands exited 0:

  | Command | Seconds |
  | --- | ---: |
  | `bb clj-kondo:copy-config` | 114 |
  | `bb clj-fmt:check` | 38 |
  | `bb clj-kondo:lint` | 14 |
  | `bb vale` | 5 |
  | `bb api-docs --check` | 51 |
  | `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir test/tooling` | 398 |
  | `git diff --exit-code HEAD --` | 0 |

- Tooling: 11 tests, 245 assertions, zero failures/errors. The copied Scry
  failure directory is empty. Logs and script remain in the container under
  `/evidence/` and `/tmp/quality.sh`; these are not durable artifacts.
  Host tracked files also remained unchanged before task-record edits.
- No code, dependencies, tests or design contracts changed. Existing real
  process tests were used without mocks. Emulated x64 runs are much slower
  than native macOS; tool-call wait expiry did not stop the container process.
  The quality script completed; no test process remains running.
- Next: in `/checkout`, run the workflow JVM gates with the same overrides,
  preserve each Scry result, then select preview notes and build the JAR.
  Full Linux preview success is not yet established. Do not prepare, tag or
  publish. Hosted Actions and live publication remain unverified.

## 2026-11-08 — Slice 6f: Linux tier gates

- Continued in the isolated Linux x64 container and full-history `/checkout`
  from Slice 6e, with pinned mise and the checked `/jvms` role overrides.
  `/tmp/jvm-tiers.sh` ran each gate with the workflow's 600-second limit.
  All exited 0, with nonzero test counts and no failures/errors:

  | Command | Seconds | Tests | Assertions |
  | --- | ---: | ---: | ---: |
  | `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:jvm-test` | 33 | 9 | 56 |
  | `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:smoke-test` | 94 | 3 | 45 |
  | `bb test:forced-jit` | 430 | 191 | 5237 |

- Forced JIT selected `27-jep401ea3+1-1`; runner duration was 428.98 seconds.
  Parent flags were preview, internal-value export, explicit auto and
  `-Xcomp -Xbatch -XX:-TieredCompilation`. Children retained preview only.
  This is parent-only forced compilation, not forced-JIT consumer evidence.
  Smoke used ordinary `25.0.5+8-LTS`; tooling used `1.8.0_492-b09`.
- Each copied Scry failure directory is empty. Container logs and result
  directories remain under `/evidence/`; they are temporary artifacts.
  Container and host `git diff --exit-code HEAD --` passed before record edits.
  No code, dependencies or test contracts changed; no new tests were needed.
  Design remains unchanged. No preparation or publication ran.
- Next: run Linux runtime, three fallback parents, cross-JVM, build and release
  gates, then preview notes and JAR build. Retain per-gate results and check
  tracked files. The complete Linux preview and final acceptance map remain
  open. Hosted Actions and live publication remain unverified.

## 2026-11-08 — Slice 6g: Linux runtime, build and release gates

- Continued in `vt-task027-preview`, full-history `/checkout` at `126b2a51`,
  with pinned mise and checked `/jvms` overrides from Slice 6e. Runtime flags
  came from the facts `:preview-export` profile, as in the workflow.
  `/tmp/remaining-focused.sh` imposed a finite 600-second limit per command.
  All commands exited 0 with no failures/errors:

  | Parent | Command | Seconds | Tests | Assertions |
  | --- | --- | ---: | ---: | ---: |
  | EA | `clojure "${jvm_flags[@]}" -M:test -m scry.cli --dir test/runtime` | 90 | 191 | 5233 |
  | EA | `clojure -M:build-test -m scry.cli --dir test/build` | 278 | 9 | 114 |
  | Tooling | `clojure -M:release:release-test -m scry.cli --dir test/release` | 131 | 15 | 175 |

- Each copied Scry failure directory is empty. Logs and copied result
  directories remain under container `/evidence/` (temporary artifacts).
  Container and host `git diff --exit-code HEAD --` passed before record edits.
  Existing real-process and real-Git tests required no changes or mocks.
  No code, dependency, API or design-contract changes were needed; design.md
  remains current. No preparation, tag creation or publication ran.
- Next: run the three Linux fallback parents and Java-8-parent cross-JVM gate,
  then preview notes selection and JAR build. These focused gates ran before
  the remaining matrix gates; this is staged local emulation, not a hosted
  sequential workflow pass. Retain that limit in the final acceptance map.
  Full Linux preview and the five-criterion map remain open.

## 2026-11-08 — Slice 6h: Linux preview outputs

- Continued in the isolated Linux x64 `/checkout` at `126b2a51`, with
  workflow-pinned mise and its tooling JVM. Both commands had a 600-second
  limit and exited 0: `clojure -M:release -m value-type.release notes
  /evidence/preview-notes.md /evidence/preview-output --preview` (12 seconds),
  then `clojure -T:build jar` (24 seconds).
- Notes selected `Unreleased`, version `0.1.579`, tag `v0.1.579`, and
  `preparation-required=true`. The JAR exists at
  `target/value-type-0.1.579.jar`. These identify the isolated checkout, not
  a prepared or published release. No credentials, preparation, tags or
  publication writes were used. Container status remained clean; container
  and host tracked-file diff checks passed before record edits.
- This completes only the preview output steps. They ran before the remaining
  Linux matrix gates, so this is staged emulation, not a sequential workflow
  pass. Next: three fallback parents and Java-8-parent cross-JVM, then the
  five-criterion acceptance map. Hosted Actions remain unverified.
- No code, dependencies or test contracts changed; existing real-process
  verification needed no new tests or mocks. Design remains current.


## 2026-11-08 — Slice 6i: Linux baseline fallback gate

- Continued in `vt-task027-preview`, full-history `/checkout` at `126b2a51`,
  with pinned mise and checked role overrides. Changes since that revision
  affect only task records and project state, not the tested code or workflow.
- `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:fallback-test` passed
  in 742 seconds: 77 tests, 2624 assertions, zero failures/errors, exit 0.
  The baseline is `1.8.0_492-b09`; the child limit remains 4. The command
  had a finite 3600-second limit; unlike the new tier gates, this workflow
  step has no separate ten-minute limit. This is staged emulation, not
  evidence that the complete emulated job fits its 60-minute limit.
- Scry failure files are absent. Logs, copied results and the driver remain
  under container `/evidence/fallback-java8*` and `/tmp/fallback-baseline.sh`
  (temporary artifacts). Container and host tracked-file checks passed before
  record edits. No preparation, tag creation or publication ran.
- No code, dependencies or test contracts changed; existing real-process tests
  required no mocks or new tests. The design remains current. Next: run both
  EA fallback parents and the Java-8-parent cross-JVM gate, then complete the
  acceptance map. Full Linux preview and hosted Actions remain unverified.


## 2026-11-08 — Slice 6j: Linux EA fallback gates

- Continued in the isolated Linux x64 container, `/checkout` at `126b2a51`,
  with pinned mise and checked role overrides. Both EA3 parent gates passed
  with child limit 4 and a finite 3600-second command limit:

  | Command (EA `JAVA_CMD`) | Seconds | Tests | Assertions | Exit |
  | --- | ---: | ---: | ---: | ---: |
  | `clojure -M:fallback-test` | 668 | 77 | 2624 | 0 |
  | `clojure -J--enable-preview -M:fallback-test` | 709 | 77 | 2624 | 0 |

- Both had zero failures/errors and empty copied Scry failure directories.
  Preview flags came from the facts `:preview` profile's `:jvm-opts` key.
  The first temporary driver read `:flags` instead and repeated preview-off
  (668 seconds); that run is not preview-on evidence. Corrected the driver
  and reran the preview gate. No repository code needed correction.
- Logs, scripts and copied results remain in container `/evidence/fallback-ea*`
  and `/tmp/fallback-ea*.sh` (temporary artifacts). Container status was clean;
  container and host tracked-file checks passed before record edits.
  No preparation or publication ran. No dependencies or test contracts changed;
  existing real-process tests needed no mocks or additions. Design remains current.
- Next: Linux Java-8-parent cross-JVM gate, then the five-criterion acceptance
  map. This remains staged emulation, not a sequential hosted workflow pass or
  proof that emulation fits the job's 60-minute limit.


## 2026-11-08 — Slice 6k: Linux cross-JVM failure evidence

- Ran `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test`
  in the same isolated `/checkout`, with checked role overrides and child
  limit 4. The finite 3600-second wrapper returned 124. No complete Scry
  summary exists; this is not a gate pass. The surviving orphan Scry JVM
  was killed after preserving failure files. Container and host tracked-file
  checks passed before record edits.
- Inspected and retained both Scry EDN files in `evidence/`. The timeout test
  printed its first marker but not its late marker. All five limit-test
  children timed out with empty output; absent intervals then caused an NPE.
  These tests use 12-second process budgets, including JVM startup. The
  evidence suggests emulation timing pressure but does not establish its cause.
- Next: measure isolated baseline startup and rerun these two real-process
  tests. Preserve timeout aggregation and concurrency contracts when resolving
  timing assumptions. Use a kill-after bound on future outer timeout commands:
  this run's timeout left an orphan process. Do not repeat the full matrix
  until the focused failures are resolved. No runtime, dependency or design
  changes were made; existing tests exposed the failure without mocks.
- The Linux cross-JVM gate, complete preview evidence and acceptance map stay
  unchecked. This is an implementation follow-up, not a human blocker.


## 2026-11-08 — Slice 6l: focused timing diagnosis

- In the same Linux container, ran `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"
  clojure -M:cross-jvm-test --var
  value-type.cell-runner-test/timed-out-child-outcome-test --var
  value-type.cell-runner-test/child-limit-test` with checked role overrides
  and `timeout -k 10 300`. Exit 1 in 62 seconds: 15 passing assertions,
  7 failures and 1 error. Inspected and retained structured Scry results in
  `evidence/linux-focused-timing/`. This reproduces the failures without
  the full matrix; matrix load is not required.
- Measured real baseline `clojure.main -e '(println :ready)'` startup with
  the cross-JVM classpath: three serial processes took 8, 7 and 6 seconds;
  four concurrent processes took 7, 8, 8 and 9 seconds, all exit 0.
  These are whole-second shell measurements, not benchmarks. The limit
  test allows only 6 seconds for startup plus a 6-second sleep. The
  timeout test also requires harness loading and a 4-second sleep within
  12 seconds. These fixed budgets are not safe on this emulator.
- Next: replace startup-dependent timing assumptions with real-process
  synchronization or measured finite budgets while preserving aggregate
  timeout, exact overlap and permit-wait exclusion checks. Do not merely
  increase the limit-test timeout: that can stop testing permit-wait
  exclusion. No production code, dependencies or contract changed; design
  remains current. Full gate and acceptance map remain open.


## 2026-11-08 — Slice 6m: aggregate timeout startup allowance

- Expanded the timeout fixture from two to six cells. The base timeout remains
  2000 ms and the late marker still follows a 4000 ms sleep. Thus a runner that
  incorrectly uses only the base timeout still fails. The aggregate budget is
  now 52000 ms, above measured emulated startup; the final 60000 ms sleep
  still requires process destruction before the file write. No runtime code,
  dependency or design contract changed. Tests use real child processes.
- Ran `clojure -M:cross-jvm-test --var
  value-type.cell-runner-test/timed-out-child-outcome-test --var
  value-type.jvm-runner-test/wait-ms-test` with Java-8 parents and verified
  role overrides: Linux x64 emulation passed in 76 seconds (outer bound
  `timeout -k 10 180`); macOS passed in 59 seconds. Each: exit 0, two tests,
  27 assertions, no failures/errors and no Scry failure files. Formatting
  and `git diff --check` pass. The first macOS attempt lacked the EA override
  and failed executable selection; the corrected run above passed.
- Linux container `/checkout` now contains the changed test file; other code
  remains at the previously recorded revision. Temporary Linux output is
  `/evidence/timeout-aggregation.log`. Design remains current. Next: resolve
  `child-limit-test` without weakening permit-wait exclusion. Do not run the
  full Linux gate until that focused test passes.


## 2026-11-08 — Slice 6n: child-limit startup allowance

- Increased startup allowance to 30000 ms and each real child's work interval
  to 60000 ms. The 90000 ms process timeout exceeds startup plus work, but
  stays below a queued child's wait plus work (at least 120000 ms). This
  retains permit-wait exclusion and the unchanged exact-overlap assertion
  across two concurrent runner calls. The cost is about two minutes per
  focused run at limit 4. No mocks, runtime changes or dependencies were added.
- Ran `clojure -M:cross-jvm-test --var
  value-type.cell-runner-test/child-limit-test` with Java-8 parents and
  verified baseline/EA overrides: Linux x64 emulation 160 seconds (outer
  `timeout -k 10 400`), macOS 132 seconds. Each exited 0: one test, 12
  assertions, no failures/errors, empty Scry failure directory. Linux log:
  container `/evidence/child-limit-corrected.log` (temporary evidence).
  `cljfmt check test/cross-jvm/value_type/cell_runner_test.clj` and
  `git diff --check` pass.
- Design remains current. The container's test file matches this slice;
  other code remains at the prior staged revision. Next: run the complete
  Linux cross-JVM gate with a kill-after bound, then complete the preview
  evidence and acceptance map. Focused passes are not a full gate pass.


## 2026-11-08 — Slice 6o: bounded full-gate rerun

- Reran the Java-8-parent `clojure -M:cross-jvm-test` in the same isolated
  Linux x64 `/checkout`, with pinned mise, checked role overrides and child
  limit 4. The corrected test file matches host SHA-256
  `efd21656c4d1a07169d49e7953b9ac54758bf5fb9a028290ca4809a942d12c31`.
- `timeout -k 10 3600` returned 124 after 3601 seconds. Scry produced no
  failure EDN, but no complete summary exists. This is not a pass. No live
  Java process remained after timeout. The earlier timing failures did not
  recur as recorded failures; this does not prove all tests completed.
- Temporary container evidence: `/tmp/cross-jvm-corrected.sh`,
  `/evidence/cross-jvm-corrected.log`, its driver log and copied `-scry`
  directory. Host tracked files were unchanged before record edits. Container
  diff remains limited to the previously recorded timing-test corrections.
- Next: measure progress and emulated suite cost, then choose an evidence-based
  finite limit or diagnose slow tests. The 60-minute hosted job limit is not
  evidence of a suitable emulation budget. Keep the full gate, preview and
  acceptance map unchecked. This runtime timeout is not a human blocker.
  No code, dependencies or design contracts changed; existing real-process
  tests were used without mocks. No preparation or publication ran.


## 2026-11-08 — Slice 6p: emulated AOT gate cost

- In the same isolated Linux x64 `/checkout`, ran Java-8-parent
  `clojure -M:cross-jvm-test --namespace` for
  `value-type.public-native-field-aot-test` and
  `value-type.public-field-aot-test`, separately with `timeout -k 10 900`.
  Used pinned mise and checked baseline/EA role overrides from `/evidence/`;
  no JVM configuration or test code changed.
- Native-field namespace: exit 0, 200 seconds, 4 tests, 138 assertions.
  Public-field namespace: exit 0, 498 seconds, 10 tests, 388 assertions.
  Both had zero failures/errors and empty copied Scry failure directories.
  Temporary scripts and logs are `/tmp/cross-jvm-{native,public}-cost.sh`
  and `/evidence/cross-jvm-{native,public}-cost.log` in the container.
- These real-process AOT checks average about 50 seconds per test. There
  are 115 cross-JVM test definitions; a deliberately rough extrapolation is
  5750 seconds. Select `timeout -k 10 7200` for the next full emulated run,
  allowing about 25% above that estimate. This is a bounded experiment, not
  a measured full-suite duration or a reason to change hosted CI limits.
  The prior 95 progress dots do not identify completed tests and are not
  structured evidence of coverage.
- Next: run the full gate with this bound and retain exit, duration and
  Scry summary/failures before completing the preview evidence map. Container
  changes remain limited to the two previously recorded timing fixes.
  Design remains current; no APIs, dependencies, mocks or new tests were
  needed. No preparation or publication ran. Full-gate and preview acceptance
  remain open; there is no human blocker.


## 2026-11-08 — Slice 6q: extended full-gate bound

- Ran `/tmp/cross-jvm-final.sh` in `vt-task027-preview`: the prior corrected
  driver with `timeout -k 10 7200`, Java-8 parent, pinned mise and checked role
  overrides. Exit 124 after 7200 seconds; no summary and no Scry failure files.
  This is not a pass. Retained raw output and attach error in
  `evidence/linux-full-gate/`; progress dots do not establish coverage.
- A Java parent remained orphaned (PID 107637, PPID 1) after timeout.
  Java-8 `jstack` could not attach (target not responding or VM not loaded).
  Killed that specific process with SIGKILL and verified no `clojure.main`
  process remained. The next driver must check and clean up descendants;
  the timeout wrapper alone did not establish process cleanup under emulation.
- Do not increase the bound again from the prior per-test extrapolation.
  Next slice: run namespace-sized checks with structured results and finite
  bounds to locate the stall or variable emulation cost before another full
  gate. Preserve the complete-gate requirement. Container diff remains only
  the two timing-test fixes. No code, APIs, dependencies or design contract
  changed; existing real-process tests were used without mocks.
- `git diff --check` passes. Preview and final acceptance remain open. This
  runtime timeout is not a human blocker; no preparation or publication ran.

## 2026-11-08 — Slice 6r: bounded namespace diagnosis

- Ran Java-8-parent namespace checks in the same Linux x64 container,
  `/checkout`, with pinned mise and checked role overrides. Each used
  `timeout -k 10 900` and `clojure -M:cross-jvm-test --namespace`:

  | Namespace | Seconds | Tests | Assertions | Exit |
  | --- | ---: | ---: | ---: | ---: |
  | `value-type.function-redefinition-test` | 73 | 1 | 54 | 0 |
  | `value-type.loader-policy-test` | 131 | 1 | 378 | 0 |

- Both CLI summaries report zero failures/errors; copied Scry failure
  directories contain no files. Process checks after each run found no
  `clojure.main` parent or child. These namespaces do not reproduce the stall
  in isolation. They do not explain or replace the failed full-gate runs.
- Temporary drivers and logs are `/tmp/cross-jvm-{redefinition,loader}-diagnosis.sh`
  and `/evidence/cross-jvm-{redefinition,loader}-diagnosis*` in the container.
  Next: continue bounded namespace diagnosis, retaining complete results and
  checking descendants. Do not raise the full-gate limit from these samples.
- No code, dependencies or test contracts changed; existing real-process tests
  required no mocks or additions. Design remains current. Full Linux gate,
  preview acceptance and final evidence map remain open. No publication ran.

## 2026-11-08 — Slice 6s: generated-value AOT diagnosis

- Ran the Java-8-parent `clojure -M:cross-jvm-test --namespace
  value-type.generated-value-aot-test` in the same isolated Linux x64
  `/checkout`, with pinned mise and checked role overrides. The driver used
  `timeout -k 10 900`: exit 0 in 135 seconds, 3 tests, 86 assertions,
  zero failures/errors. The copied Scry failure directory is empty.
- No `clojure.main` processes remained. Container diff remains limited to
  the two recorded timing-test corrections; host tracked-file check passed
  before these record edits. Temporary driver and evidence:
  `/tmp/cross-jvm-generated-diagnosis.sh` and
  `/evidence/cross-jvm-generated-diagnosis*` in the container.
- This namespace does not reproduce the full-gate stall. Continue bounded
  namespace diagnosis before another full run; do not increase its bound
  from this sample. Full Linux gate and acceptance remain open.
  No code, dependencies or test contracts changed; existing real-process
  tests needed no mocks or additions. Design remains current. No preparation
  or publication ran.

## 2026-11-08 — Slice 6t: identity AOT diagnosis

- Ran Java-8-parent `clojure -M:cross-jvm-test --namespace
  value-type.identity-aot-test` in the same isolated Linux x64 `/checkout`,
  with pinned mise and checked role overrides. `timeout -k 10 900` returned
  0 in 100 seconds: 2 tests, 58 assertions, zero failures/errors. The copied
  Scry failure directory is empty; no `clojure.main` processes remained.
- Temporary driver and logs: container `/tmp/cross-jvm-identity-diagnosis.sh`
  and `/evidence/cross-jvm-identity-diagnosis*`. Container changes remain
  limited to the two recorded timing corrections. Host tracked-file check
  passed before record edits. No code, dependencies or test contracts changed;
  existing real-process tests required no mocks or additions. Design remains
  current; no preparation or publication ran.
- This namespace does not reproduce the full-gate stall. Continue bounded
  namespace diagnosis, with descendant checks, before another full run.
  Full Linux gate, preview acceptance and final evidence map remain open.

## 2026-11-08 — Slice 6u: mixed-field AOT timeout

- Ran Java-8-parent `clojure -M:cross-jvm-test --namespace
  value-type.public-mixed-field-aot-test` in the same Linux x64 container,
  with pinned mise and checked role overrides. `timeout -k 10 900` returned
  124 after 901 seconds. No complete summary or Scry failure EDN exists;
  this is not a namespace pass. Two progress dots do not identify coverage.
- The wrapper left the Scry parent orphaned (PID 112024, PPID 1). Killed
  that specific process and confirmed no `clojure.main` processes remained.
  Temporary evidence: `/tmp/cross-jvm-mixed-diagnosis.sh` and
  `/evidence/cross-jvm-mixed-diagnosis*` in `vt-task027-preview`.
- This narrows diagnosis to a five-test namespace that also fails to complete
  within its bound. Next: run its five vars separately with structured results
  and finite bounds, checking descendants after each. Do not infer a JVM defect
  or raise the full-gate bound from this timeout alone.
- Container changes remain only the two timing fixes; host tracked-file check
  passed before record edits. No code, dependencies or test contracts changed;
  existing real-process tests used no mocks. Design remains current. Full Linux
  gate and acceptance remain open. No preparation or publication ran.

## 2026-11-08 — Slice 6v: isolated mixed-field AOT checks

- Ran each var in `value-type.public-mixed-field-aot-test` separately in
  `vt-task027-preview`, `/checkout`, with pinned mise and checked role
  overrides. Command: `timeout -k 10 180 env JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"
  clojure -M:cross-jvm-test --var value-type.public-mixed-field-aot-test/VAR`.

  | Var | Seconds | Assertions | Exit |
  | --- | ---: | ---: | ---: |
  | `source-free-public-mixed-value-test` | 67 | 28 | 0 |
  | `incompatible-public-mixed-value-consumer` | 83 | 52 | 0 |
  | `java-public-mixed-value-consumer` | 70 | 30 | 0 |
  | `function-public-mixed-value-aot-test` | 66 | 28 | 0 |
  | `persisted-public-mixed-output-test` | 44 | 13 | 0 |

- Each CLI summary reports one passing test and zero failures/errors. Each
  copied Scry failure directory is empty. Process checks after each run found
  no `clojure.main` processes; no cleanup was needed. Temporary drivers and
  evidence remain at `/tmp/cross-jvm-mixed-{source-free,incompatible,java,function,persisted}.sh`
  and `/evidence/cross-jvm-mixed-*` in the container.
- All five isolated vars pass, so no individual var reproduces the grouped
  timeout. Next: capture per-var structured results and process state during
  a bounded grouped run. Do not infer a defect or increase the full-gate limit
  from isolated timings. Full Linux gate and acceptance remain open.
- No code, API, dependency or test contract changed. Existing real-process
  tests needed no mocks or additions. Design remains current. No preparation
  or publication ran. `git diff --check` passes.

## 2026-11-08 — Slice 6w: grouped mixed-field checks

- In the existing Linux x64 container, ran all five mixed-field vars in one
  Java-8 parent, in source order, through separate `scry/run` calls. Each
  result passed: 5 tests, 151 assertions in total; exit 0 in 227 seconds
  under `timeout -k 10 900`. The retained diagnostic and structured progress
  are in `evidence/mixed-grouped/`. This checks shared-process state but does
  not reproduce namespace discovery order or a single grouped Scry call.
- Repeated the unchanged CLI namespace command with `timeout -k 10 600 env
  JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test --namespace
  value-type.public-mixed-field-aot-test`: exit 0 in 216 seconds, 5 tests,
  151 assertions, zero failures/errors, empty copied Scry failure directory.
  No live Java/Clojure processes remained; only previously recorded zombies
  remain. Temporary logs: `/evidence/mixed-namespace-repeat*` in
  `vt-task027-preview`. No descendant cleanup was needed.
- The earlier grouped timeout is not reproducible in this pass. Do not claim
  a cause or a full-gate pass. Next: instrument the complete gate with bounded
  progress and process snapshots to locate any stall; keep its finite bound.
  The diagnostic launch overrides alias `:main-opts` with `[]` through a last
  `:diagnostic` alias to run the script; appending `-m` to the existing Scry
  alias does not override it. Two invalid diagnostic launches ran no tests
  before this correction and are not verification evidence.
- No library code, dependencies or test contracts changed. Existing real-JVM
  tests used no mocks; no new behavior needs tests. Design remains current.
  Full Linux gate, preview acceptance and the acceptance map remain open.
  No preparation or publication ran. `git diff --check` passes.

## 2026-11-08 — Slice 6x: grouped progress diagnostic

- Added `evidence/full-progress/diagnostic.clj`. Scry 0.1.61 accepts a
  synchronous `:progress-callback` that receives each completed var's full
  canonical result. The diagnostic uses one grouped `scry/run`, preserving
  discovery and fixture grouping, instead of separate per-var runs. It writes
  timestamped EDN entries and the final result; zero tests fail the process.
- Verified on the Linux Java-8 parent in `vt-task027-preview`, with pinned
  mise and checked role overrides: `timeout -k 10 300 env
  JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -Sdeps
  '{:aliases {:diagnostic {:main-opts []}}}' -M:cross-jvm-test:diagnostic
  /tmp/full-progress.clj /evidence/progress-check
  value-type.identity-aot-test` exited 0 in 98 seconds. Structured result:
  2 tests, 58 assertions, zero failures/errors. Retained result and both
  callback entries beside the diagnostic. EDN checks confirm each executed
  var has one passing callback entry. No Clojure processes remained.
- Next: omit the optional namespace to observe the full gate in one call.
  Capture external process snapshots as well: callbacks identify completed
  vars, not the currently stalled var. Use a finite bound and explicit
  descendant checks/cleanup. This focused verification is not a full-gate
  pass. No runtime, dependency or design-contract changes; real processes,
  no mocks. Formatting and diff checks pass; no publication ran.


## 2026-11-08 — Local acceptance platform decision

- At the user's request, changed the remaining local acceptance path to Linux
  ARM64 in an ARM64 VM. Linux x64 remains the hosted release platform. No
  library requirement for x64 has been established. Do not continue the
  x64-emulated full-matrix timeout investigation as a completion requirement.
- Checked upstream metadata: Adoptium's version-assets API lists Linux AArch64
  Temurin 8 for the pinned baseline. Its latest-25 API and GitHub release
  `jdk-25.0.5+8` list Linux AArch64 for the pinned ordinary build. The OpenJDK
  Valhalla page lists Linux AArch64 for the pinned EA3 build, and its checksum
  endpoint returns SHA-256. The exact-version Temurin 25 API request returned
  404; the release metadata and latest response agree on the pinned build.
- This verifies published archive availability and checksum metadata only.
  No ARM64 archive was downloaded, hashed, installed or executed in this
  check. Implementation must put URLs and hashes in the facts file, verify
  downloaded bytes and build/architecture identities, and run all gates.
- Updated design, plan, remaining steps, runtime/release guides, architecture
  status and working memory. Preserve previous x64 results and timeouts;
  their cause remains unresolved. Local ARM64 results must not be reported
  as x64 or hosted Actions evidence. No CI, code, JVM pin or test assertion
  changes are part of this documentation update. No publication ran.

## 2026-11-08 — Slice 6y: Linux ARM64 facts and selection

- Added native Linux ARM64 archives for the three unchanged build pins.
  Temurin checksums agree between GitHub release asset digests and publisher
  `.sha256.txt` files; EA3 uses its publisher checksum endpoint. Existing
  x64/macOS archive entries and hosted workflow are unchanged.
- Linux `aarch64` and `arm64` now select `:linux-aarch64`. Focused tests
  check native archive metadata, unchanged role/build/tier/profile data and
  unsupported-host rejection. Real temporary-archive tests now exercise the
  ARM64 installation path, checksum rejection and exact-build rejection.
  Their executable is the parent JVM: they do not prove publisher archive
  architecture or ARM64 execution. No mocks or dependencies were added.
- `clojure -M:jvm-test` and the same command with `JAVA_CMD` set to the
  installed Temurin 8 executable both exit 0: 10 tests, 70 assertions, no
  failures/errors; Scry failure directory empty. `bb clj-fmt:check`,
  `bb clj-kondo:lint`, `bb vale` and `git diff --check` pass. Kondo reports
  zero warnings/errors. An initial `bb vale PATH` invocation was rejected;
  the supported no-argument command then passed.
- Next: use a separate ARM64 Linux guest, checked downloads and full history;
  verify actual JVM architectures and builds before the full bounded preview
  sequence. No ARM64 publisher archive was installed in this slice. Design
  remains unchanged; plan, steps, runtime guide and state reflect this limit.


## 2026-11-08 — Slice 6z: native ARM64 guest provisioning

- Created separate Podman container `vt-task027-arm64` from Ubuntu 24.04
  with `--platform linux/arm64`. The existing AppleHV VM and guest report
  `aarch64`. Cloned a full-history Git bundle into `/checkout`; revision and
  non-shallow status are retained in `evidence/arm64-setup/`.
- Installed mise 2026.4.11 for Linux ARM64 and all `mise.toml` pins. With
  `eval "$(mise env -s bash)"`, ran `timeout -k 10 600 bb jvm:provision ROLE
  linux-aarch64 /opt/task027-jvms` for baseline, valhalla and ordinary into
  a new root. All exited 0 in 5, 14 and 6 seconds, respectively. Provisioning
  checks downloaded SHA-256 and exact build before installation. Retained
  paths and `-XshowSettings:properties -version` output confirm native
  `aarch64` and the three pinned runtime identities.
- `timeout -k 10 300 env JAVA_CMD="$(cat /evidence/baseline.path)" clojure
  -M:jvm-test` exited 0 in 5 seconds: 10 tests, 70 assertions, no failures
  or errors; Scry failure directory empty. Process snapshot shows no surviving
  JVM descendants. Guest tracked files remain unchanged. These real archive
  and JVM checks use no mocks; no code or test contract changed.
- Next slice: reuse this container, `/checkout`, `/evidence/*.path` and pinned
  mise; set all three role overrides from those path files. Run the complete
  bounded preview sequence and retain structured results. This is setup and
  focused tooling evidence only, not a complete ARM64 preview pass. Design
  remains current; x64 and hosted limits are unchanged. No publication ran.


## 2026-11-08 — Slice 6aa: ARM64 quality and initial preview gates

- Reused `vt-task027-arm64`, pinned mise and checked role overrides. Guest
  revision `97eba3b` differs from host `0886fc8` only in setup evidence and
  task/state records; tested code, facts and tooling are identical.
- `evidence/arm64-quality/run.sh` retains exact commands and the 600-second
  per-command bound with 10-second kill grace. All commands exited 0:
  dependency lint configuration 26s, formatting 2s, lint 1s, prose 1s,
  API freshness 4s, tooling 23s, Java-8 JVM-tooling 1s and smoke 5s.
  CLI results: tooling 11 tests/245 assertions; JVM-tooling 10/70;
  smoke 3/45. No failures or errors; Scry failure directories were empty.
  Per-command EDN exit/duration records, logs and process snapshots are retained.
- Process snapshots after every gate show no surviving JVM descendants.
  Tracked guest files remain unchanged. These are real-process checks with
  no mocks; no code, dependency, test contract or design change was needed.
- Next: forced JIT, runtime, three fallback parents, cross-JVM, build and
  release, then preview notes and JAR. This staged result is not a complete
  ARM64 preview pass. Keep x64 and hosted limits unchanged. No preparation,
  credentials or publication writes were used.


## 2026-11-08 — Slice 6ab: ARM64 forced JIT and runtime

- Reused `vt-task027-arm64`, pinned mise and the three checked role overrides.
  `evidence/arm64-runtime/run.sh` retains commands with a 600-second bound
  and 10-second kill grace. Forced JIT exited 0 in 24 seconds: 191 tests,
  5237 assertions. Runtime exited 0 in 5 seconds: 191 tests, 5233 assertions.
  Both report zero failures/errors; copied Scry failure directories are empty.
- Forced-JIT output confirms EA3, preview/export/auto and all three compilation
  flags in the parent only; children retain normal preview-only execution.
  Runtime flags came from the ARM64 facts `:preview-export` profile. Process
  snapshots after both gates show no surviving JVM descendants. The guest
  tracked-file diff is empty. Revision, logs and EDN exit/duration records
  are retained beside the driver.
- Next: three fallback parents and the Java-8-parent cross-JVM gate, then
  build/release and preview notes/JAR. These staged passes do not establish
  complete preview acceptance or hosted/x64 success. No code, dependencies
  or test contracts changed; existing real-process tests required no mocks
  or additions. Design remains current. No preparation or publication ran.


## 2026-11-08 — Slice 6ac: ARM64 compatibility gates

- Reused `vt-task027-arm64`, pinned mise and checked role overrides.
  `evidence/arm64-compatibility/run.sh` retains exact commands with a
  1200-second per-gate bound and 10-second kill grace. All exited 0:
  baseline fallback 36s, EA preview-off fallback 32s, EA preview-on fallback
  34s and Java-8-parent cross-JVM 433s. Each fallback reports 77 tests and
  2624 assertions; cross-JVM reports 115 tests and 5105 assertions. All
  report zero failures/errors; copied Scry failure directories were empty.
- Retained command, revision, log, EDN exit/duration and process evidence.
  Every post-gate snapshot shows no surviving JVM descendants; the guest
  tracked-file diff is empty. These existing real-process tests needed no
  mocks, new tests, code changes or relaxed assertions. Design is unchanged.
- Next: build/release, preview notes and JAR, then final documentation checks
  and acceptance mapping. The native ARM64 cross-JVM pass does not resolve
  the historical x64-emulation timeouts or establish hosted CI success.
  No preparation, credentials or publication writes were used.


## 2026-11-08 — Slice 6ad: ARM64 build and preview artifacts

- Reused the native container and checked role overrides. The guest revision
  `97eba3b` differs from current host code only in task evidence and state.
  `evidence/arm64-artifacts/run.sh` retains exact commands, a 600-second bound
  and 10-second kill grace. Build passed in 13s (9 tests, 114 assertions);
  release passed in 7s (15 tests, 175 assertions). Both exited 0 with no
  failures/errors and empty Scry failure directories.
- Preview notes selection exited 0 in under one second; JAR build exited 0
  in 2s. Retained notes, selection and JAR SHA-256. The selected `0.1.601`
  is the guest revision's preview version, not a prepared or published release;
  selection correctly reports `Unreleased` and preparation required.
- All four process snapshots show no surviving JVM descendants. Guest tracked
  files remain unchanged. No credentials, preparation or publication ran.
  Existing real-process tests required no mocks, new tests or code changes.
  Design remains current. This completes the staged ARM64 preview gates;
  historical x64 timeouts and hosted/publication limits remain unchanged.
- Next: update final platform documentation, repeat affected quality checks
  and facts-duplication audit, then map all five acceptance criteria to evidence.


## 2026-11-08 — Slice 6ae: final acceptance map

- Updated stale runtime/release platform guidance and embodiment status to
  match verified native ARM64 provisioning and staged preview gates. Updated
  working memory; design.md remains the unchanged acceptance contract.
  No code, API, dependency or test contract changed. Existing real-process
  and real-Git tests provide the evidence; no new tests or mocks were needed.
- Repeated `bb vale`, `bb clj-fmt:check`, `bb clj-kondo:lint`,
  `bb api-docs --check`, `mise exec actionlint -- actionlint` and
  `git diff --check`: each exited 0. Lint has zero errors/warnings and three
  existing informational redundant-double notices. SHA-256 snapshots of all
  tracked files before and after these checks are identical.
- Repeated the facts audit over maintained workflows, tests, documentation,
  development tooling and architecture files: no facts-file archive URL or
  checksum occurs elsewhere. The other test checksum is the SHA-256 `abc`
  vector, not a JVM fact. Historical task evidence is retained.

### Five-criterion acceptance map

1. **Single facts source — pass.** Slice 6c and the repeated audit above;
   runtime and release guides link `dev/jvm/jvms.edn`. No maintained JVM
   archive URL or checksum duplication remains.
2. **Local Linux ARM64 preview — pass, staged.** Slice 6z and
   `evidence/arm64-setup/` retain guest/JVM architecture, fresh checked
   downloads and exact build identities. Slices 6aa–6ad and
   `evidence/arm64-{quality,runtime,compatibility,artifacts}/` retain commands,
   bounds, exit/duration records, summaries, failure directories and process
   snapshots for every gate, notes selection and JAR build. All exit 0;
   no JVM descendants or tracked changes remain. This is a staged local
   sequence, not a hosted workflow run or a job-duration guarantee.
3. **Smoke and forced JIT — pass.** Slices 3e, 6aa and 6ab retain the
   contract and configuration evidence. Native ARM64 smoke: 5 seconds,
   3 tests/45 assertions. `bb test:forced-jit`: 24 seconds,
   191 tests/5237 assertions on EA3. Both exit 0 with no failures/errors.
   Commands and logs are in the quality/runtime evidence directories.
   Forced compilation covers the parent only; children remain normal.
4. **README preparation contract — pass.** Slice 4b records real-Git first
   and replacement preparation, committed version agreement, conditional
   coordinates, one two-file commit, clean status and unchanged tags/remotes.
   Slice 6ad repeats the release suite: 15 tests/175 assertions, exit 0.
   This checkout was not prepared. Publication remains a separate action.
5. **Documentation and checks — pass.** README, runtime/release guides,
   release architecture, changelog, D21/D22/D24 embodiment and working memory
   match the implementation. Slices 6a/6b and 6z–6ad retain full suite
   evidence; this slice repeats affected quality checks after documentation
   updates. All required selected-platform suites and checks pass.

Implementation is complete under the revised local-platform contract. No
further implementation slice is required. Review must preserve these limits:
historical x64-emulation timeouts remain unexplained, Linux x64 hosted CI
has not been verified, and no credentials, preparation or publication writes
were used. ARM64 success does not establish x64 or live publication success.

## 2026-11-08 — Implementation review

- Added 3 steps to be addressed.

## 2026-11-08 — Review follow-up 1

- Addressed facts-based parent selection and exact runtime-build expectations;
  capability expectations remain independent. A changed-prefix facts regression
  covers selection and stale-build rejection.
- Java-8-parent Scry: `-M:cross-jvm-test --namespace
  value-type.array-info-boundary-test --namespace value-type.jvm-config-test`:
  5 tests, 170 assertions, exit 0; no failure EDN. Valhalla override selects
  the pinned mise EA3 executable. An initial ordinary-parent invocation correctly
  rejected the unsupported parent; failure EDN retained in
  `/tmp/task027-review-initial/`.

## 2026-11-08 — Review follow-up 2

- Preparation now requires a clean final worktree/index and exact committed
  README/changelog bytes. Failure retains local state. Real post-commit hooks
  cover dirty worktree, dirty index and amended coordinates without an extra
  commit; the existing extra-commit test remains.
- `clojure -M:release:release-test -m scry.cli --dir test/release`:
  exit 0, 16 tests, 193 assertions, no failures/errors or failure EDN.
  Preparation ran only in temporary test repositories.

## 2026-11-08 — Review follow-up 3

- Corrected release architecture verification status and linked the completed
  staged ARM64 acceptance evidence. Checked agreement with the runtime guide
  and embodiment gap table. Historical x64 timeouts and hosted/publication
  limits remain unchanged. Updated working memory for preparation postconditions.
- `bb vale`, `bb clj-fmt:check`, `bb clj-kondo:lint` and `git diff --check`
  exit 0. Lint: no errors/warnings; three existing informational notices.
- Addressed 3 review steps. No design changes or blocked follow-ups.

## 2026-11-08 — Implementation re-review

- Added 1 step to be addressed.

## 2026-11-08 — Re-review follow-up

- Addressed 1 review step. Preparation now requires exactly README.md and
  CHANGELOG.md changes against the original HEAD, which must remain the only
  parent. Real-Git hooks verify rejection of an extra committed path and
  replaced ancestry while preserving failed state for inspection.
- Release Scry CLI: 17 tests, 210 assertions, exit 0; no failure EDN.
  Formatting, lint, prose and diff checks pass (three existing lint notices).
  Updated plan, architecture and state; design unchanged. No blocked steps.

## 2026-11-08 — Documentation consistency re-review

- Added 1 step to be addressed.


## 2026-11-08 — Review follow-up: current JVM guidance

- Addressed 1 review step. README, allocation/support guidance, the public
  docstring and regenerated API docs link facts or the runtime guide instead
  of fixing current selection to an EA build. Runtime launch instructions
  now name facts-selected Valhalla.
- Audited README, doc, src, Ramora and spike guides for EA identifiers.
  The only exact EA build IDs left in README/doc/src are two explicitly
  historical example results in the usage guide. Retained experiment,
  spike and verification evidence keeps its build identities; it does not
  select the current installation or API target. Current selection links
  need no edit when the EA facts change.
- Passed: `bb api-docs`, `bb api-docs --check`, `bb clj-fmt:check`,
  `bb vale`, `bb clj-kondo:lint` (zero errors/warnings), `git diff --check`.
  No runtime behavior changed; runtime suites were not rerun.

## 2026-11-08 — Final implementation review

- No new actionable findings. JVM-tooling and release suites pass: 10 tests/70
  assertions and 17 tests/210 assertions; both exit 0 with no failure EDN.

## 2026-11-08 — Test review

- Added 2 steps to be addressed.

## 2026-11-08 — Test review follow-up 1

- Addressed 1 review step. The unchanged launcher runs real Scry in a temporary
  project; retained input fixtures check the complete parent configuration and
  passing/failing suite exits. Normal runtime tests remain unchanged.
- `clojure -M:jvm-test` with the facts-selected EA override: exit 0,
  11 tests, 80 assertions; no failure EDN.

## 2026-11-08 — Test review follow-up 2

- Addressed 1 review step. Parent-role checks now select builds by role and
  cover reordered facts with ordinary first and baseline last.
- Java-8 `clojure -M:cross-jvm-test --namespace value-type.jvm-config-test`:
  exit 0, 3 tests, 21 assertions; no failure EDN. Formatting, lint and diff
  checks pass; lint has only three existing informational notices.
- Both test-review steps are complete. No blocked items or design changes.

## 2026-11-08 — Test re-review

- No new actionable findings. JVM-tooling, smoke and release verification:
  31 tests, 335 assertions; all commands exited 0 with no failure EDN.

## 2026-11-08 — Test process-lifecycle review

- Added 1 step to be addressed.

## 2026-11-08 — Process cleanup review follow-up

- Addressed 1 review step. A test-only Babashka supervisor stops and awaits
  children before parents, with 180-second execution and 15-second cleanup
  bounds. Java-8 tests verify all recorded handles are stopped before deletion.
  Cleanup errors retain the project and launcher output. A real Scry child
  signals readiness to trigger the same timeout path; pass/fail checks remain.
- Java-8 `clojure -M:jvm-test` with the facts-selected EA override: exit 0,
  11 tests, 89 assertions, no failure EDN. Formatting and lint pass (three
  existing lint notices). An intermediate counter-based diagnostic failed
  because Scry does not bind the standard counter; removed it and reran.
  Its failure EDN is retained at `/tmp/027-counter-failure.edn`.

## 2026-11-08 — Test discovery-boundary review

- Added 1 step to be addressed.

## 2026-11-08 — Discovery-boundary review follow-up

- Addressed 1 review step. The isolated launcher project now contains an
  executable out-of-tier fixture. All three cases check that it did not run,
  while retaining runtime execution, configuration, exit and cleanup checks.
- Java-8 `clojure -M:jvm-test` with the facts-selected EA override: exit 0,
  11 tests, 92 assertions, no failure EDN. Formatting, lint and diff checks
  pass (three existing informational lint notices).
- Temporarily broadened actual launcher discovery to `test/`: the focused
  launcher test exited 1 with four failed assertions, including the exclusion
  check in all three cases. Restored production code before final verification.
  Failure EDN: `/tmp/027-discovery-mutation-results/`. The first experiment
  exposed a missing fixture classpath entry; corrected it before this check.

## 2026-11-08 — Discovery-boundary test re-review

- No new actionable findings. Java-8 JVM-tooling verification: 11 tests,
  92 assertions, exit 0; no failure EDN.

## 2026-11-08 — Documentation review

- Added 2 steps to be addressed.

## 2026-11-08 — Documentation scope follow-up

- Addressed 1 review step: documented `dev/jvm/` in formatting and lint scope;
  checked against `maintained-paths`. Direct Vale check passes. `bb vale`
  could not sync styles (HTTP 504); the direct check used installed styles.

## 2026-11-08 — Cached installation documentation follow-up

- Addressed 1 review step: distinguished new-download SHA-256 checks from
  cached executable/build verification and documented a new ROOT for a fresh
  installation. Checked against `provision!`; direct Vale and diff checks pass.
  No code changed; provisioning tests were not rerun.

## 2026-11-08 — Documentation re-review

- No new actionable findings.

## 2026-11-08 — Code-shaper review

- Added 1 step to be addressed.

## 2026-11-08 — Installation-path review follow-up

- Addressed 1 review step. Facts and installation paths reject separators, dot
  components, qualified keywords and drive syntax. Canonical containment also
  rejects existing symlinks outside the root. Archive installation checks the
  path before reading the archive.
- Focused facts, executable and provisioning suites pass on the default JVM and
  Java 8: 10 tests, 166 assertions each; exit 0, no Scry failure files. Tests
  cover current facts, build punctuation, malformed selections, no writes and
  symlink escape rejection through both provisioning entry points.
- `bb clj-fmt:check`, `bb clj-kondo:lint` and `git diff --check` pass (lint:
  zero errors or warnings). No download or full matrix run was needed.

## 2026-11-08 — Code-shaper verification-boundary review

- Added 1 step to be addressed.

## 2026-11-08 — Executable verification review

- Addressed 1 review step. Build verification now defaults to 30 seconds,
  captures stdout/stderr without pipe blocking, and forcibly stops and awaits
  the process on timeout. Errors retain role, platform, expected build,
  executable, timeout, output and termination status. Java 8 APIs only.
- Real stalled-shell regression checks captured diagnostics and PID termination;
  existing real-JVM success and mismatch checks remain.
- Java 8: `clojure -M:jvm-test --namespace value-type.jvm-executable-test`
  passed 4 tests / 20 assertions. Default JVM: executable and provisioning
  namespaces passed 7 tests / 118 assertions. Both CLI exits were zero;
  `.scry-results` contains no failure EDN. Babashka verified the baseline JVM.
- Focused formatting and repository lint passed (zero errors or warnings).
  No full matrix or hosted workflow run was needed for this bounded check.

## 2026-11-08 — Code-shaper re-review

- No new actionable findings. Facts, executable and provisioning checks pass:
  11 tests, 174 assertions, exit 0; no Scry failure files.


## Closure — after knowledge extraction `c2db0881`

- Closed at the user's request after implementation, implementation review
  and knowledge extraction completed. All checklist items are complete.
- Knowledge extraction commits: `1bda6ef4`, `4b62423c`, `c2db0881`.
- Moved the task to `munera/closed/`, removed it from the active plan and
  updated maintained evidence links and working memory.
- Existing verification limits remain: historical x64-emulation timeouts are
  unresolved; hosted Linux x64 Actions and live publication are unverified.
  Local ARM64 results do not establish x64 success. Forced JIT covers the
  suite parent only. Closure does not prepare or publish a release.
