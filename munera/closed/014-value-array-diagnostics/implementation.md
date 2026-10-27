# Implementation notes

## Architecture design review — 2026-10-23

- No architectural review feedback. Scope is unchanged; no design steps added.
- `ramora/META.md` and `doc/architecture.md` are absent. Architecture context
  came from `AGENTS.md`, `ramora/INDEX.md`, `ramora/defvaluetype-design.md`
  and the array design, decisions, verification, implementation gates and
  semantic report linked there.
- Planning detail: the current adapter's private `discover` calls
  `capability/detect`, which gates allocation on value-class support.
  Inspection discovery must not reuse that gate: Object-array inspection
  with exports works without preview. This is an implementation constraint
  already covered by the design, not a new finding.

## Ambiguity design review — 2026-10-23

- Ambiguity review added 1 new design step: error precedence for invalid input
  when inspection capability is unavailable. Scope is unchanged.

## Inconsistency design review — 2026-10-23

- No inconsistency review feedback. No design steps added; the existing
  error-precedence ambiguity remains open. Scope is unchanged.

## Design follow-up handoff — 2026-10-23

- Allocation's capability-first order in `src/value_type/array.clj` and
  `ramora/value-type-array-design/decisions.md` (Failure behavior) is specific
  to allocation, not a required order for diagnostics. Resolve inspection
  precedence explicitly without changing that existing allocation contract.
  Native diagnostic outcomes cannot select public error precedence; see
  `ramora/value-type-array-design/semantic-verification.md`, section 5.

## Error-precedence follow-up — 2026-10-23

- Fresh-JVM support already exists in `test-fallback/value_type/jvm_runner.clj`:
  `run-jvm` accepts explicit flags and a timeout, and returns process status
  plus parsed EDN on exit zero. Use `jvm_config.clj` for executable selection;
  its `:available?` describes value-class support, not inspection capability.
  The allocation boundary test supplies a process-test pattern, but its
  capability-first placeholder calls are not diagnostic precedence tests.
- Static checks confirmed agreement with the array contract and decisions.
  Runtime tests were not run; only design records changed.

## Ambiguity design re-review — 2026-10-23

- No new ambiguity review feedback. The error-precedence follow-up is complete;
  no design steps added. Scope is unchanged. Static review only.

## Inconsistency design re-review — 2026-10-23

- No new inconsistency review feedback after the error-precedence follow-up.
  No design steps added; scope is unchanged. Static review only.

## Design-review handoff — 2026-10-23

- The earlier inconsistency-review note that error precedence remains open is
  historical. Use the completed `design-steps.md` item and the later re-reviews
  for current status; do not reopen it from that note alone.

## Execution planning and reviews — 2026-10-23

- Created `plan.md` and `steps.md` from the stable design. Five slices cover
  the safe boundary, observed properties, startup/regression matrix, packaged
  consumer, and documentation/acceptance. No implementation work was started.
- Plan ambiguity review treated `steps.md` as read-only context. All seven
  acceptance criteria have concrete actions and verification. No blocking
  ambiguity or new follow-up was found. Array-component arrays remain within
  the reference-array input domain; the constructor's one-dimensional output
  rule does not restrict diagnostic input.
- Design inconsistency review compared the design with task 013, the array
  contract/decisions, semantic report and current adapter/process helpers.
  No internal or contract inconsistency was found. Allocation's capability-first
  rule does not apply to diagnostics. Task 012's statement that no production
  API is implemented describes historical probe scope, not current allocation
  status. No design change or unresolved design question is required.
- Verified local Markdown links, unchecked implementation items and whitespace.
  The design and checklist were unchanged during review. Runtime tests were
  not run because this change contains planning records only.

## Plan ambiguity re-review — 2026-10-23

- First `plan-review` turn: no new actionable ambiguity feedback. The prior
  planning outcome is confirmed; no design steps added. Static review only;
  `steps.md` is unchanged. Inconsistency review remains the next turn.

## Plan inconsistency review — 2026-10-23

- Second `plan-review` turn: no new actionable inconsistency feedback across
  the task files. No design steps added; `steps.md` is unchanged. Static review
  used the first turn's context, with no intervening changes. No runtime tests ran.

## Slice 1 — safe inspection boundary — 2026-10-23

- Added `array-info` with input-first validation and a separate delayed
  inspection capability. Discovery verifies boolean signatures, static status
  and caller access. Allocation discovery is unchanged. Only known missing
  API/signature/access exceptions become capability errors; invocation causes
  propagate through the existing helper. Cached state contains no caller arrays.
- Executables (no selection overrides): Java 8 at
  `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  Temurin `1.8.0_492-b09`; EA3 at
  `/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`,
  `27-jep401ea3+1-1`. Host: macOS aarch64. Parent selected with `JAVA_CMD`.
- Safety checks passed in four disposable configurations, each with a 120-second
  timeout, complete EDN and exit zero. Invalid inputs throw the exact public
  error; valid unsupported inputs preserve reasons and causes. Export-only
  inspection works. An initial test incorrectly expected Object-array atomic
  false; corrected to true after checking results and task 012's semantic report.
  No crash or timeout occurred. No native null diagnostic was called.
- Verification: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:fallback-test --namespace value-type.array-info-boundary-test`
  passed 1 test/136 assertions, exit zero, no Scry failure files. A second
  in-process Scry run retained structured results in `evidence/slice-1.edn`.
  `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb api-docs` and
  `git diff --check` passed. Full regressions were not run in this slice.
- Next slice can safely start valid-array shared-JVM comparisons. Keep invalid
  cases in children. No dependencies or design scope changed; later slices
  still own the expanded startup matrix, packaging and documentation acceptance.

## Slice 2 — observed properties and ownership — 2026-10-23

- Added direct static JDK comparisons independent of adapter discovery. Reused
  task 013 fixtures through their existing private fixture function; no fixture
  or production API change was needed. Nested primitive arrays are valid
  reference arrays because their immediate component is an array Class.
- Tests compare all keys and boolean types, preserve contents, and repeat after
  caller stores (including null in ordinary typed arrays). These checks verify
  observable ownership; static cache/retention review remains in slice 3.
- On the same EA3 executable and host as slice 1, all 14 diagnostic, allocation
  and layout tests passed: 1728 assertions with flattening enabled, 1848 with
  it disabled. Wide arrays stayed non-flat; one-long arrays followed the flag,
  including length zero. Both retained Scry maps are in `evidence/slice-2-*.edn`.
- Commands: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
  [-J-XX:-UseArrayFlattening] -M:test -m scry.cli
  --namespace value-type.array-info-test --namespace value-type.array-test
  --namespace value-type.array-layout-test`. Both CLI runs exited zero with
  no failure EDN files. In-process runs of the same namespaces retained the
  structured maps. `bb clj-fmt:check`, `bb clj-kondo:lint` and
  `git diff --check` passed. No invalid native diagnostic was invoked.
- Next: expand the startup matrix in slice 3. No dependencies, scope or public
  behavior changed in this slice; documentation and packaging remain pending.

## Slice 3a — full public startup matrix — 2026-10-23

- Expanded the existing safety test to six configurations, three policies and
  both require orders (36 fresh JVMs). Each child reports runtime version,
  flags, policy and order; assertions check these against its selected row.
  Repeated public outcomes and clean exits remain required. No native null
  inspection or value fixture creation was added.
- With explicit `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA`
  overrides set to the slice 1 paths, and `JAVA_CMD` set to EA3,
  `clojure -M:fallback-test --namespace value-type.array-info-boundary-test
  --namespace value-type.array-boundary-test --namespace value-type.array-matrix-test`
  passed: 3 tests, 2008 assertions, exit zero, no Scry failure files.
  A second in-process Scry run of those namespaces passed and retained
  `evidence/slice-3a.edn`. Formatting, lint and whitespace checks passed.
- Next: add independent non-null native signature/access observations and the
  explicit export-only allocation comparison. Then review adapter ownership
  and run the fallback/loading suites. These remaining slice 3 checks are not
  established by the expanded public matrix. Production behavior, dependencies
  and the design contract are unchanged.

## Slice 3b — ownership review and fallback regressions — 2026-10-27

- Static review of both array namespaces confirms that the two delays capture
  no caller arguments. Discovery caches only methods or capability failures.
  Optional resolution occurs inside delayed functions, not namespace loading.
  Allocation discovery calls only the class capability detector and allocator
  lookup; it does not force inspection. Public validation errors contain no
  input array. Inspection returns only the component Class and booleans.
  Discovery catches only missing class/method and access exceptions; invocation
  unwraps the native cause without reclassifying it. No code change was needed.
- Existing runtime, AOT and transfer tests passed on Java 8 (15 tests/166
  assertions), EA3 preview off (15/166) and EA3 preview on (15/141). Each CLI
  run exited zero with no Scry failure files. Separate in-process runs retained
  complete structured maps in `evidence/slice-3b-*.edn`. Real child JVMs were
  used; no mocks or unsafe native queries were added.
- Commands used the same executable paths and host as slice 1, with explicit
  `VALUE_TYPE_BASELINE_JAVA` and `VALUE_TYPE_VALHALLA_JAVA`. For each parent
  `JAVA_CMD`, ran `clojure [-J--enable-preview] -M:fallback-test
  --namespace value-type.runtime-test --namespace value-type.aot-test
  --namespace value-type.transfer-test`. No exports were added. Structured
  runs used the same paths/dependency through a temporary `-Sdeps` alias and
  `scry.core/run` with those three namespaces. The first shell call exceeded
  its tool timeout during the Java 8 structured run; that process completed
  successfully. The remaining configurations were run separately.
- Next: independent non-null signature/access observations and the explicit
  export-only allocation comparison. Packaging and documentation remain open.
  Design, production behavior and dependencies are unchanged. Whitespace
  verification passed; code quality tools were not rerun for records-only edits.

## Slice 3c — export-only capability separation — 2026-10-27

- Added a real EA3 child test under silent, warn and error policies. With only
  the export flag, Object-array inspection returns all five properties before
  and after repeated allocation attempts. Allocation retains
  `:array-unavailable` with `:unsupported-class-version`; no value fixtures are
  loaded and no fallback warning occurs. Production code is unchanged.
- Focused CLI verification passed (1 test, 15 assertions), exit zero, no Scry
  failure files: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  clojure -M:fallback-test --var
  value-type.array-info-boundary-test/export-only-capabilities-test`.
  An in-process Scry run with the slice 1 executable paths explicitly selected
  through both VALUE_TYPE JVM overrides also passed; full results are retained
  in `evidence/slice-3c.edn`. Formatting, lint and whitespace checks passed.
- Next: independent non-null signature/access observations in the startup
  children. The existing full matrix was not repeated in this focused slice.
  Packaging and documentation remain open. No design or dependency change.

## Slice 3d — independent native observations — 2026-10-27

- Added a baseline-safe test helper that reads JDK signatures and invokes the
  component predicate and three array queries without the production adapter
  or access overrides. Its only array input is a non-null Object array.
  Each of the 36 startup children now reports these observations separately
  from public outcomes. Java 8 API absence, EA3 missing-export access denial
  and export-enabled results are asserted, including return/parameter types
  and static status. Unexpected native failures escape and fail the child.
- With the same explicit JVM overrides and EA3 parent as slice 3c, the
  diagnostic boundary namespace passed in-process and through
  `clojure -M:fallback-test --namespace value-type.array-info-boundary-test`:
  2 tests, 1455 assertions; CLI exit zero and no Scry failure files.
  Structured results are retained in `evidence/slice-3d.edn`.
  Formatting, project lint and whitespace checks passed.
- Slice 3 is complete. Next: JAR-only consumer coverage in slice 4. Production
  code, dependencies and design are unchanged. Allocation and fallback suites
  were not repeated; their slice 3 results remain recorded above.

## Slice 4a — supported packaged diagnostics — 2026-10-27

- The isolated source-JAR consumer now checks the exact Object-array result
  and all five restricted-array properties. Restricted flat status must be a
  boolean, not a fixed layout claim. Existing macro, allocation, content and
  JAR-resource/classpath checks remain in place. No production or design change.
- Focused in-process Scry and CLI runs passed (1 test, 9 parent assertions;
  child assertions also gate exit zero). Evidence: `evidence/slice-4a.edn`.
  Command: `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java
  VALUE_TYPE_BASELINE_JAVA=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java
  clojure -M:build-test -m scry.cli --var build-test/isolated-consumer-test`.
  CLI exit zero; no Scry failure files. Same JVM versions as slice 1.
  Formatting was corrected; formatting, project lint and whitespace checks pass.
- Next: add packaged diagnostic controls for Java 8 and preview-disabled EA3,
  including export-only inspection before any value creation, then run the full
  build suite. Existing unsupported consumer rows still test allocation only.

## Slice 4b — packaged diagnostic controls — 2026-10-27

- Added invalid-input and valid Object-array checks before any value creation
  or allocation in unsupported JAR-only consumers. Added export-only EA3:
  inspection succeeds while allocation remains unavailable. Repeated calls
  and exact public errors are checked in real, timeout-protected children.
  All four consumer rows retain JAR resource and runtime-only classpath checks.
- Full build suite passed in-process and through CLI: 9 tests, 103 assertions,
  exit zero and no Scry failure files. Structured results are retained in
  `evidence/slice-4b.edn`. Used the same EA3 parent and Java 8 paths as slice 1,
  selected through `JAVA_CMD` and `VALUE_TYPE_BASELINE_JAVA`. Commands:
  `clojure -M:build-test -e` with `scry.core/run` for namespace `build-test`,
  then `clojure -M:build-test -m scry.cli --namespace build-test`.
  Formatting, project lint and whitespace checks passed.
- Slice 4 is complete. Next: documentation and final acceptance in slice 5.
  No production, dependency or design change. The full runtime matrices were
  not repeated in this packaging slice.

## Slice 5a — usage and API contract — 2026-10-27

- Documented reference-array inspection, exact result types, input-first errors,
  export-only inspection and ownership/concurrency limits. Regenerated API output
  from the docstring; no runtime behavior or design change was needed.
- Updated the public API inventory test, which still expected only the allocator
  and macro, and added rendered diagnostic contract checks. Tests use the real
  generator and file/CLI operations, not source-text assertions or mocks.
- In-process Scry passed 3 tests/84 assertions; retained results are in
  `evidence/slice-5a.edn`. Final `clojure -Srepro -M:quickdoc:quickdoc-test
  -m scry.cli --dir test-quickdoc` passed 11 tests/199 assertions, exit zero,
  with no failure files. Formatting, lint, Vale, API freshness and whitespace
  checks passed. Corrected Vale's boolean capitalization errors before rerunning.
  Usage links resolve to existing API, README and runtime files.
- Next: runtime commands, architecture status and release notes, then full
  acceptance runs and acceptance.md. Runtime suites were not repeated for this
  documentation-only slice; the final quality checklist remains open for the
  remaining documentation changes.

## Slice 5b — runtime guidance — 2026-10-27

- Added diagnostic safety order, explicit startup-matrix and layout commands,
  export-only capability limits and JAR-only diagnostic controls to the runtime
  guide. Commands match existing test namespaces and retained slice results.
  No code, tests, dependencies or design contract changed.
- Project Vale, generated API freshness and whitespace checks passed. Corrected
  a spelling alert and checked the usage link against its actual heading.
  Runtime tests were not repeated for this documentation-only slice; final
  acceptance runs remain open. No new test coverage was required.
- Next: architecture status and release notes, then final verification and
  acceptance.md. Keep task 012 crash evidence unchanged.

## Slice 5c — architecture and release notes — 2026-10-27

- Updated maintained architecture pages and index to separate implemented
  diagnostics from pending sequence construction. Recorded input-first errors,
  independent inspection capability, ownership and retained verification scope.
  Final task 014 acceptance stays open; task 012 historical evidence is unchanged.
- Added the Unreleased diagnostic scope and capability limits. Project Vale,
  API freshness, local link-target checks and whitespace verification passed.
  Runtime tests were not repeated for documentation-only changes; no new test
  coverage, dependency or design change was needed.
- Next: final combined runtime and quality verification, then acceptance.md.
  Existing per-slice results are not a substitute for the remaining checklist.

## Slice 5d — final quality verification — 2026-10-27

- Formatting, project lint, Vale and API freshness passed with `JAVA_CMD`
  unset: `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and
  `bb api-docs --check`. Local documentation link paths from the three
  documentation slices resolve; whitespace verification passed.
- `clojure -Srepro -M:quickdoc:quickdoc-test -m scry.cli --dir test-quickdoc`
  exited zero with no Scry failure files. A separate in-process run of the
  same directory passed 11 tests/199 assertions; full structured results are
  retained in `evidence/slice-5d.edn`.
- No code, test, dependency or design change was needed. Final combined runtime
  verification and acceptance.md remain. Keep the task open; earlier runtime
  evidence does not complete that remaining checklist item.

## Slice 5e — final library and layout verification — 2026-10-27

- Full supported library tests pass: 78 tests/2060 assertions. This includes
  enabled-layout diagnostic and allocation checks. Disabled-flattening array
  tests pass: 14 tests/1848 assertions. Both in-process and CLI runs pass;
  each CLI exits zero with no `.scry-results/*.edn` files.
- Structured results: `evidence/slice-5e-library.edn` and
  `evidence/slice-5e-disabled.edn`. Host: Darwin 27.0.0 arm64; Java:
  OpenJDK `27-jep401ea3+1-1`. `JAVA_CMD` was
  `$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`.
- Commands: `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
  -M:test -m scry.cli --dir test`; disabled layout used the same export,
  `-J-XX:-UseArrayFlattening -M:test -m scry.cli` and three `--namespace`
  options: `value-type.array-test`, `value-type.array-info-test`, and
  `value-type.array-layout-test`. The test alias supplies `--enable-preview`.
  In-process runs used the same JVM options and `scry.core/run` with
  `{:dirs ["test"]}` or the three namespaces, then retained the complete result.
- No production, test, dependency or design change was needed. Next: final
  process matrices, fallback/loading controls and packaging, then acceptance.md.
  Earlier safety gates remain valid; no native invalid-input calls were added
  and task 012 evidence was not changed.

## Slice 5f — final process matrices — 2026-10-27

- Diagnostic and allocation process matrices pass in-process and through CLI:
  4 tests/2095 assertions, no failures or errors. CLI exited zero with no
  `.scry-results/*.edn` files. Complete structured results are retained in
  `evidence/slice-5f-matrices.edn`.
- Used the slice 1 EA3 and Java 8 executable paths, with explicit `JAVA_CMD`,
  `VALUE_TYPE_VALHALLA_JAVA` and `VALUE_TYPE_BASELINE_JAVA`. Command:
  `clojure -M:fallback-test --namespace value-type.array-info-boundary-test
  --namespace value-type.array-boundary-test --namespace value-type.array-matrix-test`.
  The parent has no preview/export flags; children select their matrix flags.
  The structured run used a temporary `-Sdeps` alias with `test-fallback`,
  Scry 0.1.61 and `scry.core/run` for those same three namespaces.
- No code, test, dependency or design change was needed. Existing real-JVM
  tests cover this slice without mocks or new native invalid-input calls.
  Next: final fallback/loading controls and packaged-consumer suite, then
  acceptance.md. Task 012 evidence is unchanged.

## Slice 5g — final controls and acceptance — 2026-10-27

- Final fallback/loading runs passed on Java 8 and EA3 preview off (15 tests,
  166 assertions each), and EA3 preview on (15/141). The full build suite
  passed (9/103). In-process results and CLI runs agree; all CLI runs exited
  zero with no Scry failure files before the next run. Exact commands, runtime
  versions and structured results are retained under `evidence/slice-5g-*`.
- Existing real-JVM and JAR-only tests cover these checks without mocks. No
  production, test, dependency or design change was needed. Added acceptance.md
  to map all seven criteria to final evidence and limits. All checklist items
  are complete; keep the task open for review. Task 012 evidence is unchanged.

## Implementation review — 2026-10-27

- Added 1 step to be addressed.

## Implementation review follow-up — 2026-10-27

- Addressed 1 review step: current task and architecture summaries link completed
  acceptance and keep task 014 open for review. Historical slice notes and
  pending sequence construction are unchanged. Local link paths, status search,
  project Vale and whitespace checks passed. Runtime tests were not repeated;
  only documentation and task records changed.

## Implementation re-review — 2026-10-27

- No new actionable issues found; no steps added.

## Test review — 2026-10-27

- No new actionable test issues found; no steps added.

## Documentation review — 2026-10-27

- Added 3 steps to be addressed.

## Documentation follow-up — README

- Addressed 1 review step: README diagnostics match the public docstring and usage guide. Guide anchor, project Vale and whitespace checks pass. No runtime tests ran.

## Documentation follow-up — API inventory

- Addressed 1 review step: artifact API inventory matches `doc/API.md`. `bb api-docs --check` and whitespace checks pass. Generated output is unchanged.

## Documentation follow-up — packaging

- Addressed 1 review step: diagnostic requirements and four consumer configurations match the build test and runtime guide. Linked headings, project Vale, API freshness and whitespace checks pass. No runtime tests ran; only documentation and records changed. All 3 documentation review steps are complete.

## Documentation re-review — 2026-10-27

- No new actionable documentation issues found; no steps added.

## Code-shape review — 2026-10-27

- Added 1 step to be addressed.


## Code-shape review follow-up — shared fixtures

- Addressed 1 review step. Moved all four value types and five seed/replacement
  triples to `value-type.array-test-support`. Allocation and diagnostic tests
  use its public fixture function and classes. The direct JDK oracle is unchanged.
- Verified on EA3 `27-jep401ea3+1-1`, with
  `JAVA_CMD=$HOME/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`:
  `clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --namespace value-type.array-info-test --namespace value-type.array-test --namespace value-type.array-layout-test`.
  Passed 14 tests/1728 assertions. Repeated with
  `-J-XX:-UseArrayFlattening`: 14 tests/1848 assertions. Both exited zero;
  `.scry-results/` contained no failure files after either run.
- A fresh JVM required only `value-type.array-info-test` and confirmed that
  `value-type.array-test` was not loaded. Focused formatting, project lint and
  whitespace checks passed. Production code and design are unchanged;
  full acceptance was not repeated.

## Code-shape re-review — 2026-10-27

- No new actionable issues found; no steps added.

## Closure — user request

- Closed after implementation, final acceptance, review follow-ups and knowledge
  extraction. All checklist items are complete. Moved the task and retained
  evidence to `munera/closed/`, removed it from the active plan and updated
  incoming links and working memory.
- Closure changes records only. No runtime tests or hosted workflows were
  rerun. Recorded JVM scope and verification limits remain. Sequence
  construction is separate task 015.
