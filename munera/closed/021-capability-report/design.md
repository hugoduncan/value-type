# Public JVM capability report

## Intent

Callers cannot ask value-type what the running JVM supports. They find out
only from behaviour: fallback, an error key or a failed array call. Give
callers one public read operation that returns the four capability results
as data. Each result must have one owner and one computation per loaded
library runtime.

Priorities, in order: the report states the same results that definitions
and array functions use; namespace loading stays baseline-safe; one owner
per result; a minimal public surface.

## Problem and context

Four lazy probes decide capability. Their results are private:

| Result | Probe | Cache owner now |
|---|---|---|
| Value-class format | `impl.capability/detect` | `impl.runtime` |
| Native non-nil field enforcement | `impl.field-capability/detect` | `impl.runtime` |
| Array allocation | `impl.array` discovery | `impl.array` |
| Array inspection | `impl.array` inspection discovery | `impl.array` |

Underlying issues:

- No public query. To get diagnostics, callers must cause a failure.
  Libraries and test suites cannot select behaviour before they define
  types or allocate arrays.
- Duplicated computation. The field probe and allocation discovery call
  `impl.capability/detect` directly, not through the cache. One runtime can
  run the value-class probe up to three times. The results can then come
  from different runs.
- Mixed ownership. `impl.runtime` caches two results that it does not
  compute. It also owns fallback policy. `impl.runtime` validates
  `value-type.fallback` when it loads, so any reader of its caches depends
  on a valid policy.

Constraints: fallback D12 (separate capability from policy; only
established absence permits fallback; defects propagate), fallback D13
(state scope is one loaded library runtime), array D2 and D3 (array
capability is strict and separate; only the adapter calls
`jdk.internal.value`; allocation and inspection are discovered separately;
no public SPI). The project keeps no backward compatibility. The library
is experimental.

## Concepts

- Capability result: `{:available? true}` or
  `{:available? false :reason <keyword>}`.
- Capability report: a map from the four capability keys to capability
  results.
- Result owner: the namespace that defines a probe. It computes and caches
  that probe's result.

## Scope

Include:

- A public namespace `value-type.capability` with one function, `report`.
  It takes no arguments.
- A single owner for each result, by this rule: the namespace that defines
  a probe owns its cached result. `impl.runtime` keeps only policy,
  warning state and the fallback guards.
- Dependent results (field, allocation) use the cached value-class result
  as their prerequisite.
- Documentation, tests and design-document updates (see Acceptance).

Exclude:

- Fallback policy in the report. Policy is application configuration, not
  JVM capability (D12).
- A prediction of the representation of a definition. Transferred
  expansions and AOT artifacts fix representation (D13).
- A public predicate for the value status of a Class.
- Changes to probe logic, absence classification, reasons, error data or
  error order of definitions and array functions.
- Merging probe code into one namespace. Moving array discovery out of the
  adapter. A provider framework or SPI.
- Exception detail text or causes in the report.

Adjacent work, as separate tasks: a public `value-class?` predicate; a
policy query.

## Contract

- `(value-type.capability/report)` returns a map with exactly these keys:
  - `:value-class`: this runtime can define value classes in the supported
    format.
  - `:native-non-nil-field`: value classes use native restriction for
    eligible `:non-nil` fields.
  - `:array-allocation`: `make-array` and `into-array` can allocate.
  - `:array-inspection`: `array-info` can inspect.
- Each value is a capability result. Reasons:
  - `:value-class` and `:native-non-nil-field`: `:missing-classfile-api`,
    `:missing-value-predicate`, `:unsupported-class-version`.
  - `:array-allocation`: the value-class reasons, `:missing-array-api`,
    `:missing-array-signature`, `:array-access-denied`.
  - `:array-inspection`: `:missing-array-api`, `:missing-array-signature`,
    `:array-access-denied`.
- The report is EDN-printable. It contains no Methods, Classes or
  Throwables.
- Each report value is identical to the result that the related
  definitions and array functions use in the same runtime: it equals the
  `:available?` and `:reason` entries of the owner's cached result (see
  Architecture). The comparison uses only these two keys. Internal results
  also carry `:detail`, `:cause` or Methods; the report omits them. A field
  or allocation result can be available only when `:value-class` is
  available.
- `report` runs each probe that has not run yet. Probe defects propagate
  unchanged, as in D12. If a probe throws, `report` returns no partial
  report.
- Loading `value-type.capability` and calling `report` do not read
  `value-type.fallback`. An invalid policy value has no effect on them.
- Loading `value-type.capability` needs no JEP 401 support, preview, export
  or `java.lang.classfile`.

Expected results on the test matrix (the `array-capability/runtimes`
labels):

| Runtime | value-class | native-non-nil-field | array-allocation | array-inspection |
|---|---|---|---|---|
| `:java8` | `:missing-classfile-api` | `:missing-classfile-api` | `:missing-classfile-api` | `:missing-array-api` |
| `:no-options` | `:unsupported-class-version` | `:unsupported-class-version` | `:unsupported-class-version` | `:array-access-denied` |
| `:export-only` | `:unsupported-class-version` | `:unsupported-class-version` | `:unsupported-class-version` | available |
| `:no-export` | available | available | `:array-access-denied` | `:array-access-denied` |
| `:export` | available | available | available | available |
| `:no-flattening` | available | available | available | available |

## Architecture

- What exists: one public read function and four cached results. No new
  state.
- Responsibility: each probe namespace owns its result
  (`impl.capability`: value class; `impl.field-capability`: native field;
  `impl.array`: allocation and inspection). `impl.runtime` owns policy, the
  warning flag and the fallback guards. The public namespace owns
  translation to the public shape. It removes internal data such as
  Methods, causes and detail.
- Accessors: each result has one public accessor function in its owner.
  The accessor returns the cached result:
  - `impl.capability/capability`: value class.
  - `impl.field-capability/capability`: native field.
  - `impl.array/allocation-capability`: allocation.
  - `impl.array/inspection-capability`: inspection.

  Only the owner's private delay calls the owner's probe. All readers call
  the accessor through its var: `value-type.core`, the native-field guard,
  `require-allocation`, `require-inspection`, `report`, and the dependent
  probes, which read their prerequisite through
  `impl.capability/capability`. Thus `with-redefs` of an accessor var
  replaces that result for every reader in the JVM. A dependent result
  caches its prerequisite when it is first computed. A replacement of
  `impl.capability/capability` therefore has an effect on the field and
  allocation results only when it starts before their first read.
- Native-field guard: `require-field-capability!` (error
  `:field-capability-unavailable`) moves from `impl.runtime` to
  `impl.field-capability`. Expansions and AOT artifacts emit
  `value-type.impl.field-capability/require-field-capability!`.
  `value-type.core` requires `impl.field-capability` when it loads, so an
  expansion or a loaded AOT artifact can always resolve the guard. The
  guard message, its ex-data (`:value-type/error`, `:class-name`,
  `:reason`) and its position in the order of definition checks do not
  change.
- Separation: `value-type.capability` depends on the three probe
  namespaces. It does not depend on `impl.runtime` or `value-type.core`.
  `value-type.core` and `value-type.array` do not depend on
  `value-type.capability`; they read the result owners directly.
  `impl.runtime` depends on no probe namespace. The capability-related
  dependencies are:
  - `value-type.core → impl.runtime, impl.capability, impl.field-capability`
  - `value-type.array → impl.array`
  - `value-type.capability → impl.capability, impl.field-capability, impl.array`
  - `impl.field-capability → impl.capability`
  - `impl.array → impl.capability`

  This list gives only the edges to and between the runtime, probe and
  public namespaces. It is not a complete require graph. Other requires,
  for example `value-type.core → impl.fields` and
  `impl.fields → impl.capability`, do not change. In this list, the task
  adds the edges from `value-type.core` to `impl.field-capability` and
  from `value-type.capability` to the probe namespaces. It removes the
  edges from `impl.runtime` to the two probe namespaces. The complete
  require graph has no cycles.
- Interaction: definitions, array functions and `report` read the same
  cached results.
- Patterns: follow the existing lazy, baseline-safe adapter pattern. Add
  a read-only facade. Remove capability caching from `impl.runtime`.

Rejected:

- Facade only, caches unchanged. Smaller, but the duplicated probe runs
  stay, so report entries can come from different runs.
- One cache registry for all four results. The array adapter needs its
  cached Methods, so a registry causes a dependency cycle or moves
  `jdk.internal.value` access out of the adapter (against array D3).
- Report functions in `value-type.core`. That couples the report to policy
  validation and puts array concerns in the macro namespace.
- Per-key query functions. These add surface. The four probes run once
  and cost little.

## Qualities and risks

- Better: diagnosability, consistency (one computation per result), local
  ownership.
- Cost: four reason sets become public contract; JVM changes can change
  them. The library is experimental and keeps no compatibility.
- Risk: tests read capability through `impl.runtime`. Now these tests
  do so:
  - `cell_harness/controlled-absence` uses `with-redefs` on the public
    accessor `impl.runtime/field-capability`.
  - `expansion_checks`, `field_policy_checks`, `field_transfer_checks`,
    `identity_receiving_checks`, `runtime_test`, `removed_field_test` and
    `deftype_star_test` call `runtime/capability`.
  - `deftype_star_record_test` calls `runtime/capability` and
    `runtime/field-capability`.
  - The cross-JVM `field_capability_test` calls `runtime/field-capability`
    and the guard `runtime/require-field-capability!`.
  - `non_nil_fields_test` and the cross-JVM `field_capability_test` deref
    the private delay `detected-field-capability` for laziness and cache
    checks.

  These tests must move to the owner accessors, the owner delays and the
  guard in `impl.field-capability`, and keep their evidence. This list
  can become incomplete before implementation. Acceptance 5 covers every
  reader in `test/`. A redefinition has an effect only on readers that
  call the accessor var, so every reader must read through it.

## Acceptance

1. `report` returns the Contract shape. Cross-JVM tests show the expected
   table row for each of the six runtime labels, under each fallback
   policy, in each of three require orders. The require orders are the
   rotations of (`value-type.capability`, `value-type.core`,
   `value-type.array`), so each namespace loads first in one order. This
   is a new axis; the two-namespace `array-capability/require-orders` axis
   does not change. Require order is process-wide, so each order is a
   one-cell child JVM: 6 runtimes × 3 policies × 3 orders = 54 children.
   The matrix is a new test namespace, `value-type.capability-matrix-test`
   (`test/cross-jvm/value_type/capability_matrix_test.clj`). It runs under
   the `:cross-jvm-test` alias with the Java 8 parent, as
   `value-type.array-matrix-test` does.
   In the order that starts with `value-type.capability`, the cell calls
   `report` before the first definition or array call. In the other two
   orders, the cell calls `report` after the Acceptance 3 definition and
   array calls. Each cell calls `report` again as its last step and gets an
   equal report.
2. An invalid-policy test, in the pattern of
   `runtime_test/invalid-policy-test`, runs one child JVM for each invalid
   `value-type.fallback` value (`""`, `"WARN"`, `"unknown"`). Each child
   loads `value-type.capability` and calls `report`. The test is a new
   `deftest` in `value-type.runtime-test`
   (`test/compatibility/value_type/runtime_test.clj`). It runs in the
   `:fallback-test` suite on each of its three parent JVMs. The child uses
   the runtime of its parent: Java 8 is `:java8`, Valhalla without
   options is `:no-options`, and Valhalla with preview is `:no-export`.
   Pass criterion: the report equals the Contract row of that label, and
   after `report` returns, `value-type.core` and `value-type.impl.runtime`
   are not loaded (`find-ns` returns nil).
3. In each cell of Acceptance 1 (in `value-type.capability-matrix-test`),
   the report results agree with the outcomes in the same JVM. Agreement compares `:available?`, and
   `:reason` where the outcome exposes one:
   - `:value-class`: an ordinary `defvaluetype` (no enabled non-nil
     fields). Available: it defines a value class. Unavailable: it defines
     an identity class under `silent` and `warn`, and throws the fallback
     Error under `error`. The outcome exposes no reason.
   - `:native-non-nil-field`: a `defvaluetype` with one `:non-nil` field
     whose tag is the class of the `:value-class` check. Available: the
     field has the `jdk.internal.vm.annotation.NullRestricted` annotation
     (native enforcement, as `consumer_aot_checks/native-field?` reads
     it). Unavailable: the field has no such annotation (generated
     enforcement). The outcome exposes no reason. Under `error` with
     `:value-class` unavailable, the tag class does not exist, so the cell
     does not run this check.
   - `:array-allocation`: `make-array` succeeds, or throws
     `:array-unavailable` with ex-data `:reason`.
   - `:array-inspection`: `array-info` of an `Object` array succeeds, or
     throws `:array-inspection-unavailable` with ex-data `:reason`.

   Each cell also checks the Contract identity: each report value equals
   the `:available?` and `:reason` entries of its owner accessor result.
4. A controlled test runs in an EA3 preview child JVM with the export
   option (`:export`) under `silent`. Before any capability result is
   read, it replaces `impl.capability/capability` by `with-redefs` with an
   unavailable result. In that scope, `report` gives that result for
   `:value-class`, `:native-non-nil-field` and `:array-allocation`;
   `make-array` throws `:array-unavailable` with that `:reason`; an
   ordinary `defvaluetype` defines an identity class. `:array-inspection`
   stays available. This proves that there is one value-class computation.
   The test is a new namespace, `value-type.controlled-value-capability-test`
   (`test/cross-jvm/value_type/controlled_value_capability_test.clj`). It
   runs under the `:cross-jvm-test` alias with the Java 8 parent. It is
   controlled-capability evidence, so it is not in the Acceptance 1 matrix
   namespace.
5. `impl.runtime` holds no capability cache and no capability accessor.
   No file in `test/` reads a capability result or calls the native-field
   guard through `impl.runtime`. Tests read the owner accessors and call
   the guard in `impl.field-capability`.
   Controlled-capability tests replace the owner accessor vars
   (`cell_harness/controlled-absence` replaces
   `impl.field-capability/capability`). Laziness and cache checks read the
   owner's private delay. The tests pass with the same evidence.
6. The existing runtime, fallback, cross-JVM, build and tooling suites
   pass. `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint` and
   `bb vale` pass. `bb api-docs --check` gives evidence for `report` only
   after the Acceptance 7 generator change. Record commands, results and
   limits in `implementation.md`.
7. Documentation: the `report` docstring, `doc/API.md`, a "Capability
   report" section in `doc/usage.md`, the `CHANGELOG.md` entry and
   `mementum/state.md`.
   `doc/API.md` is generated by Quickdoc. Do not edit it by hand. Add
   `src/value_type/capability.clj` to `:source-paths` in
   `quickdoc-options` (`bb/value_type/api_docs.clj`), then regenerate it
   with `bb api-docs`. Add `value-type.capability/report` to the list of
   documented public vars in `public-reference-test`
   (`test/tooling/value_type/api_docs_test.clj`).
   In `doc/development/runtime.md`, add a "Verify the capability report"
   section after "Verify array diagnostics". It gives two separate
   `:cross-jvm-test` commands with
   `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"`: one for
   `value-type.capability-matrix-test`, and one for the controlled check
   `value-type.controlled-value-capability-test`. Add the capability
   matrix to the `test/cross-jvm/` row of the "Test layout" table. The
   "Verified fallback matrix" commands run all of `test/compatibility`, so
   Acceptance 2 needs no new command.

   In `ramora/defvaluetype-design/fallback.md`:

   - D12 "Boundary": the sentence "the public namespace and fallback path
     must load ..." names the public namespaces `value-type.core` and
     `value-type.capability`. Array D3 already covers `value-type.array`.
     D12 states no cache owner, so no other D12 text changes.
   - "Verification": change the item "Public namespace and packaged
     source JAR load without optional APIs" to "Public namespaces
     `value-type.core` and `value-type.capability` and the packaged source
     JAR load without optional APIs." It names the same public namespaces
     as D12 "Boundary". The evidence for `value-type.capability` is
     Acceptance 8.
   - D13 "Choice" and "Rationale": state the cache owners. The probe
     namespaces own the cached capability results. `impl.runtime` owns the
     initialized policy and the atomic warning flag.

   Update assertion A-34 in
   `ramora/defvaluetype-design/assertions/fallback-architecture.md`, so
   that it states the new owners: the probe namespaces own the cached
   capability results, and `impl.runtime` owns the initialized policy and
   the atomic warning flag.
   Update array D3 to name the report as a reader of the adapter results.
   In `ramora/non-nil-field-design/embodiment.md` ("Implementation"),
   replace "Native capability is probed lazily." with text that names the
   owner: `impl.field-capability` probes native capability lazily and
   caches the result for one loaded library runtime. It reads the
   value-class prerequisite through `impl.capability/capability`, and it
   owns the native-field guard. Update the
   defvaluetype runtime embodiment for the owners and the guard move:
   `embodiment/runtime.md` (component table and dependency direction),
   `embodiment/runtime/fallback-interfaces.md`,
   `embodiment/artifacts.md` (the source list; the `test/cross-jvm/`
   entry, which names the matrices; the "Development quality workflow"
   paragraph that names the API doc sources and documented functions; the
   isolated source-JAR consumer results, for Acceptance 8) and
   `decisions/validation-and-errors.md` (cache owner). Add decision D14
   "Public capability report" to `ramora/defvaluetype-design/decisions.md`,
   with details in a new `ramora/defvaluetype-design/decisions/capability-report.md`.
   Link the details file from `ramora/INDEX.md`.
8. The isolated-consumer build test (`build_test/isolated-consumer-test`)
   loads `value-type.capability` from the packaged JAR (resource-origin
   check) and calls `report` in each of its four runtimes. Each report
   equals the Contract row of the matching label: Java 8 `:java8`,
   Valhalla without options `:no-options`, export only `:export-only`,
   preview and export `:export`. The first two runtimes have no JEP 401
   support.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
