# Plan: Public JVM capability report

## Approach

Move cached capability results to their probe owners, then add a thin public
facade. Work inside-out so every slice keeps the suites green:

1. **Owner accessors.** Give each probe namespace a private delay and a
   public accessor: `impl.capability/capability`,
   `impl.field-capability/capability`, `impl.array/allocation-capability`,
   `impl.array/inspection-capability`. Dependent probes (field, allocation)
   read the prerequisite through the `impl.capability/capability` var, not
   `capability/detect`. After Slice 2 removes the `impl.runtime` cache, one
   runtime has one value-class computation, and `with-redefs` of the
   accessor replaces the result for every reader.
   `require-allocation` and `require-inspection` read the array accessors.
   `impl.runtime` does not change yet, so all readers outside `impl.array`
   keep their current source and the suites pass.
2. **Value-class reader switch.** In one slice, switch every value-class
   reader from `runtime/capability` to `impl.capability/capability`: the
   `value-type.core` readers and the `test/` readers. Then delete the
   value-class delay, the `capability` accessor and the `impl.capability`
   require from `impl.runtime`.
3. **Field reader and guard switch.** In one slice, move
   `require-field-capability!` from `impl.runtime` to
   `impl.field-capability` with identical message, ex-data and check order,
   and switch every native-field reader together: the guard, the
   `value-type.core` reader and guard emission, the `with-redefs` target in
   `cell_harness/controlled-absence`, and the other `test/` readers.
   `value-type.core` requires `impl.field-capability` so expansions and AOT
   artifacts resolve the guard. A test child that calls the moved guard
   under a policy requires `value-type.core`, which loads the policy and
   the guard, so the policy axis keeps its evidence. Then delete the field
   delay, the `field-capability` accessor, the old guard and the
   `impl.field-capability` require from `impl.runtime`.
4. **Runtime cleanup check (Acceptance 5).** `impl.runtime` keeps only
   policy, warning state and fallback guards; update its namespace
   docstring. Grep `src/` and `test/` for remaining readers, check the
   require graph against the design edge list, and run the full gate
   (see "Test gates").
5. **Public facade.** New `src/value_type/capability.clj` with `report`:
   reads the four accessors, strips internal keys to `:available?`/`:reason`,
   propagates probe defects, no partial report. Depends only on the three
   probe namespaces; never loads `impl.runtime` or reads
   `value-type.fallback`; loads baseline-safe on Java 8.
6. **Tests.** Acceptance 1/3 matrix (`capability_matrix_test.clj`, 54 child
   cells: 6 runtimes × 3 policies × 3 require orders, report-vs-outcome and
   contract-identity checks per cell); Acceptance 2 invalid-policy deftest in
   `runtime_test.clj` (checks report row and that core/runtime stay
   unloaded); Acceptance 4 controlled test
   (`controlled_value_capability_test.clj`, `with-redefs` on
   `impl.capability/capability` in an `:export` child); Acceptance 8
   isolated-consumer additions (load from JAR, call `report` in four
   runtimes).
7. **Docs.** Quickdoc source path + regenerate `doc/API.md`;
   `public-reference-test` entry; `doc/usage.md` section;
   `doc/development/runtime.md` verify section and layout table;
   `CHANGELOG.md`; `mementum/state.md`; ramora design updates (D12
   boundary/verification, D13 owners, A-34, array D3, non-nil embodiment,
   runtime embodiment, validation-and-errors, new D14 +
   `decisions/capability-report.md`, INDEX link). In
   `embodiment/artifacts.md`, the four parts that Acceptance 7 names (the
   source list, which gets the new owners; the `test/cross-jvm/`
   entry that names the matrices; the "Development quality workflow"
   paragraph on API doc sources and documented functions; the isolated
   source-JAR consumer results, for Acceptance 8).

## Key decisions

- Reuse the existing cross-JVM cell harness patterns
  (`array_matrix_test.clj`, `controlled_field_capability_test.clj`) for the
  new test namespaces.
- Require-order rotation is a per-cell property: each cell is one child JVM.
  As its first action, the cell requires `value-type.capability`,
  `value-type.core` and `value-type.array` in its rotation order, as the
  `array-capability/require-orders` cells do
  (`array_matrix_checks/check`: `(run! require require-order)`). The order
  also sets the `report` timing. In the order that starts with
  `value-type.capability`, the cell calls `report` after all three
  requires and before the first definition or array call. Loading the
  three namespaces reads none of the four results, so `report` is still
  the first reader. In the other two orders, the cell calls `report` after
  the Acceptance 3 definition and array calls.
- Each test reader moves in the same slice as the `src/` change that it
  depends on, so every slice ends with suites that pass. All native-field
  readers switch together (Slice 3): if the redefined accessor in
  `cell_harness/controlled-absence` and the accessor that `value-type.core`
  reads differ, `controlled_field_capability_test` fails. No temporary
  delegation from `impl.runtime` accessors: a redefinition of a delegating
  var does not reach readers of the owner var.
- Report translation keeps only `:available?` and `:reason`; equality checks
  compare `select-keys` of the accessor result.

## Risks

- **Hidden readers.** The design's test-reader list is declared possibly
  incomplete, and some readers use an alias (`r/capability` in
  `runtime_test`). Mitigation: in Slices 2 and 3, find the readers of each
  runtime accessor with a grep that does not depend on the alias (list
  the files that load `impl.runtime`, then grep them for the accessor
  name; steps.md gives the commands) and migrate them before the accessor
  is deleted in the same slice. A missed reader then fails in that
  slice's code gate: at compile time, or at run time in the child JVM for
  a quoted child form. Slice 4 runs the same greps again.
- **Expansion/AOT guard symbol.** Transferred expansions and AOT artifacts
  emit the guard symbol. There is no checked-in AOT output; AOT tests
  compile in the test run. The only test that names the guard symbol is
  the cross-JVM `field_capability_test`, which Slice 3 migrates.
- **Matrix cost.** 54 child JVMs per matrix run; keep per-cell work minimal
  and record runtimes/limits in `implementation.md` (Acceptance 6).
- **Require-order sensitivity.** Dependent caches snapshot the prerequisite
  on first read; the controlled test (Acceptance 4) must install the redef
  before any capability read in the child.
- **Doc breadth.** Acceptance 7 touches many ramora files; verify with
  `bb vale` and cross-check assertions after editing.

## Slice order

1. Owner accessors; dependent probes read the value-class accessor
   (`src/` only; `impl.runtime` unchanged).
2. Value-class reader switch (`src/` and `test/`); delete the runtime
   value-class cache.
3. Guard move and native-field reader switch (`src/` and `test/`); delete
   the runtime field cache and guard.
4. Runtime cleanup check (Acceptance 5).
5. Public `value-type.capability/report` facade.
6. New tests: matrix, invalid-policy, controlled, isolated-consumer.
7. Documentation, generated API docs, ramora/design updates, quality gates.

## Test gates

Each slice gate uses these suite names and the commands in
`doc/development/runtime.md` and `doc/development/quality-checks.md`
unchanged. Set `VALUE_TYPE_BASELINE_JAVA` (Java 8) and
`VALUE_TYPE_VALHALLA_JAVA` (EA3) to full executable paths first.

| Suite | Alias | Parent JVM | Command source |
| --- | --- | --- | --- |
| runtime suite | `:test` | EA3; alias adds preview, command adds the `jdk.internal.value` export | `runtime.md` "Run the tests" |
| fallback suite | `:fallback-test` | three runs: Java 8; EA3; EA3 with `-J--enable-preview` | `runtime.md` "Verified fallback matrix" |
| cross-JVM suite | `:cross-jvm-test` | Java 8 (one run) | `runtime.md` "Verified fallback matrix" |
| build suite | `:build-test` | EA3 (`JAVA_CMD`); alias adds preview | `runtime.md` "Run the build tests" |
| tooling suite | `:tooling-test` | `JAVA_CMD` unset; `-Srepro -M:quickdoc:tooling-test` | `quality-checks.md` check list |

- **Code gate:** runtime suite, fallback suite (all three parents) and
  cross-JVM suite. Slices 1, 2 and 3 end with it. Slice 1 runs the
  cross-JVM suite, because it changes allocation discovery and the field
  probe prerequisite, and only that suite runs the six-runtime array
  matrices and the cross-JVM `field_capability_test`.
- **Full gate:** the code gate plus the build suite and the tooling suite.
  These are the five suites of Acceptance 6. Slices 4, 6 and 7 end with
  it. Slice 7 also runs the `bb` checks of Acceptance 6.
- Slice 5 has no gate: no existing test loads the new leaf namespace.
  Slice 6 runs the full gate with the new tests.
- Each Scry run replaces `.scry-results/`. Keep the failure files of a
  failed run before the next run.
