# Plan

## Approach

Implement only `value-type.array/array-info [array]` from [design.md](design.md).
Keep the existing allocation contract, class fallback and array access unchanged.
Follow the [array decisions](../../../ramora/value-type-array-design/decisions.md)
and the scoped EA3 [semantic report](../../../ramora/value-type-array-design/semantic-verification.md).
Task 013 is complete; sequence construction is not a dependency.

### Public boundary

In `src/value_type/array.clj`, reject null, non-arrays and arrays whose immediate
component is primitive before requesting inspection capability. Use `ex-info`
with `:value-type/error :invalid-array` on every JVM. Use baseline Java class
inspection for this check; do not invoke an optional API to validate input.
Reference arrays include ordinary Object/String arrays and typed value arrays.
An array with an array component is also a reference array; do not add a
one-dimensional input restriction from the constructor contract.

For valid input, require inspection capability and return exactly five keys:
`:component-class` (the runtime Class), plus boolean `:component-value?`,
`:null-restricted?`, `:atomic?` and `:flat?`. Read the component's value status
and all three native properties directly. Return a complete map or throw;
never infer layout from names, allocation success or component value status.
Inspection must not change the array or retain it in library state or errors.

### Adapter and failures

Extend `src/value_type/impl/array.clj` with a separate delayed inspection
capability. Resolve `Class.isValue()` and the three static boolean methods
`isNullRestrictedArray(Object)`, `isAtomicArray(Object)` and `isFlatArray(Object)`
on `jdk.internal.value.ValueClass`. Verify signatures and invocation access
with the existing caller-lookup method-handle pattern, without changing module
access. Keep optional resolution out of namespace loading.

Do not use `capability/detect` or `require-allocation` as inspection prerequisites.
Object-array inspection must work with exports and preview disabled. Do not
add diagnostics to allocation discovery. Cache only capability and methods,
never inspected arrays. Startup configuration remains fixed per loaded runtime.
Reuse the existing reflective invocation helper and cause unwrapping.

Known missing APIs/signatures or denied access produce `ex-info` with
`:value-type/error :array-inspection-unavailable`, a useful reason and the
underlying cause. Messages identify the export requirement without claiming
that ordinary reference-array inspection needs preview. Unexpected discovery,
linkage or invocation failures propagate; do not cache native failures as
capability absence or return false for unknown facts. No public SPI is needed.

## Slice order

1. **Safe public and adapter boundary.** Implement validation, independent lazy
   discovery and the complete result. First test crash-risk public inputs in
   disposable JVMs. Establish exact errors and clean exits before shared-JVM
   semantic checks.
2. **Observed properties and ownership.** Add public diagnostic tests for
   ordinary and restricted arrays, empty arrays, all task 013 fixture shapes
   and the wider non-flat fixture. Compare with independent direct JDK queries
   on valid arrays. Verify unchanged contents and current, uncached results.
3. **Startup and regression matrix.** Add baseline-safe diagnostic process
   tests using `test-fallback/value_type/jvm_runner.clj` and `jvm_config.clj`.
   Keep diagnostic expectations separate from the configuration helper's
   value-class `:available?`. Run allocation and fallback regressions unchanged.
4. **Packaged consumer.** Extend `test-build/build_test.clj` to exercise the
   public function from the source-only JAR, preserving macro/allocation checks.
5. **Documentation and acceptance.** Update API, usage, runtime guidance,
   architecture status and release notes. Run relevant checks and record
   commands, environments, structured results and acceptance coverage locally.

## Verification

Use real JVMs, not mocked capability state. New diagnostic namespaces should
be separate from the allocation tests: `test/value_type/array_info_test.clj`,
`test-fallback/value_type/array_info_boundary_test.clj` and baseline-safe child
helpers as needed. Reuse task 013 fixtures without loading them in controls.

The process matrix uses these explicit configurations:

| JVM and flags | Valid Object-array inspection |
| --- | --- |
| Java 8, no preview or exports | Inspection unavailable |
| EA3, neither preview nor exports | Inspection unavailable |
| EA3, preview only | Inspection unavailable |
| EA3, export only | Complete result |
| EA3, preview and export | Complete result |
| EA3, preview and export, `-XX:-UseArrayFlattening` | Complete result; flat false |

Export means `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
In every row, null, a String and a primitive array must produce `:invalid-array`.
Child processes catch normal public exceptions, print structured EDN and exit
zero within an explicit 120-second timeout. A timeout, crash, missing outcome
or unexpected success fails the test. Keep invalid-input checks in children.
Run rows under `silent`, `warn` and `error` policies and both public namespace
require orders. Check repeated calls, reasons/causes, runtime flags and no new
fallback warning. No unsupported row defines value fixtures.

Supported semantic tests use lengths 0, 1 and 3; zero-field, one-long,
two-long and reference-field values, including null/non-null reference payloads.
Compare ordinary and restricted arrays. Run with flattening enabled and disabled;
keep build-specific flat expectations separate from semantic assertions.
Inspect only valid arrays directly. Check contents before/after inspection and
again after a caller store. Review state ownership and allocation discovery
statically; do not use GC timing or replace production capabilities to test them.

Use Scry structured results during development. Final CLI runs must exit zero
with no failure EDN files in `.scry-results/`; retain results before another run
replaces them. Follow the [runtime guide](../../../doc/development/runtime.md)
and [quality guide](../../../doc/development/quality-checks.md). Record exact
Java 8 and EA3 versions, executable overrides, OS, flags and commands in this
task's `acceptance.md`, with evidence outside `target/`.

## Risks

- Native null diagnostics crash EA3. Never call them directly, including as
  a negative adapter test. Public-wrapper child tests are the first safety gate.
- API discovery does not prove access. Missing JVMs, changed signatures or
  unexpected preview-off behavior block verification; do not skip required rows.
- The existing class detector gates allocation on preview. Reusing it would
  incorrectly disable ordinary-array inspection.
- Flat status varies with fixture and VM flags. Atomic status gives no compound
  update, publication or synchronization guarantee.
- JAR build/clean removes `target/`; retained task evidence must survive it.
- No design blocker is known. Task 012's “No production API is implemented”
  describes its historical evidence scope, not current task 013 delivery.
  The decisions' capability-first rule applies to allocation only. Neither
  statement overrides this design's explicit diagnostic validation order.

## Execution status

Slice 1 is complete. The initial safety gate covers Java 8, EA3 preview without
exports, EA3 preview with exports, and EA3 exports without preview. Slice 3
will expand this to all configurations, policies and require orders. Ordinary
Object arrays report atomic true, consistent with task 012's semantic report.
No design change is required. API output was regenerated with the new function;
full documentation and API test updates remain in slice 5.

Slice 2 is complete. Direct JDK comparisons, caller-store checks and layout
controls pass with flattening enabled and disabled. Slice 3 is next. The
fixture shapes and reference-array domain need no design change.

Slice 3a expands the public safety matrix to all 36 combinations. Diagnostic
and allocation process tests pass together. Independent native observations,
export-only allocation comparison, static ownership review and fallback/loading
suite runs remain in slice 3. The design and approach are unchanged.

Slice 3b completes the static adapter ownership review and existing fallback,
AOT and transfer regressions on all three control runtimes. No production
change or design change was needed. Independent native observations and the
explicit export-only allocation comparison remain before slice 3 is complete.

Slice 3c completes the explicit export-only allocation comparison under all
three policies. Inspection succeeds before and after repeated allocation
rejections. Independent native signature/access observations remain in slice 3.
The design and production behavior are unchanged.

Slice 3d completes independent native observations in all startup children.
Java 8 lacks both APIs; EA3 exposes the expected signatures but denies array
method invocation without exports. Preview does not change these results.
Slice 3 is complete. Packaged-consumer coverage is next; the design is unchanged.

Slice 4a completes supported JAR-only diagnostic coverage. Existing macro and
allocation checks still pass on all three consumer runtimes. Diagnostic controls
and full build-suite verification remain in slice 4. The design is unchanged.

Slice 4b completes JAR-only diagnostic controls and full build-suite verification.
Java 8 and EA3 without exports preserve public diagnostic errors. Export-only
EA3 inspects ordinary arrays before value creation. Slice 4 is complete;
documentation and final acceptance are next. The design is unchanged.

Slice 5a completes usage and generated API documentation with focused contract
tests. Runtime guidance, architecture status, release notes and final acceptance
remain. The design and runtime behavior are unchanged.

Slice 5b completes runtime safety, matrix, layout and packaged-consumer guidance.
Architecture status, release notes and final acceptance remain. The design and
runtime behavior are unchanged.

Slice 5c completes architecture status, diagnostic decisions and verification
coverage, implementation gates and release notes. Final combined verification
and acceptance.md remain. The design and runtime behavior are unchanged.

Slice 5d completes final quality and Quickdoc verification. No implementation
or design change was needed. Combined runtime verification and acceptance.md
remain before implementation is complete.

Slice 5e completes final supported-library and both layout checks. Structured
and CLI runs pass. Final process matrices, fallback/loading controls, packaging
and acceptance.md remain. No design or implementation change was needed.

Slice 5f completes final diagnostic and allocation process matrices. Structured
and CLI runs pass (4 tests/2095 assertions). Final fallback/loading controls,
packaging and acceptance.md remain. No design or implementation change was needed.

Slice 5g completes final fallback/loading and packaged-consumer checks. All
implementation steps are complete; acceptance.md maps the retained evidence to
the design. The task stays open for review. No design change was needed.
