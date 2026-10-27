# Plan

## Approach

Implement only `value-type.array/make-array [component length initial]`.
Follow [design.md](design.md), the [array decisions](../../../ramora/value-type-array-design/decisions.md)
and task 012's [semantic report](../../../ramora/value-type-array-design/semantic-verification.md).
Return the native restricted atomic array, including when it is not flat.
Do not add diagnostics, sequence construction or an ordinary-array fallback.

### Public and adapter boundary

- Add `src/value_type/array.clj` for public validation and the documented API.
  Exclude `clojure.core/make-array` from its referred names.
- Add `src/value_type/impl/array.clj` for lazy optional API discovery,
  access checks, value-status queries and allocation invocation. Use one
  delayed allocation-capability result per loaded library runtime. Cache
  methods, access and prerequisite results, never caller arrays or seeds.
- Resolve the exact `newNullRestrictedAtomicArray(Class, int, Object)`
  signature. Check invocation access without changing module access or
  using `setAccessible`. Resolve no diagnostic methods as prerequisites.
- Use the existing policy-free `value-type.impl.capability/detect` only as
  a value-class prerequisite, not proof of array support. Array API presence
  and invocation access need separate checks. Do not change that detector,
  class generation, loader, transformer, runtime policy or `value-type.core`.
  No optional JDK import or capability probe runs during namespace loading.
- Prefer ordinary reflection or method handles to a provider framework.
  Allocation remains the final eligibility test for a supplied runtime class;
  no class registry, persistent probe array or invented default seed is needed.

### Error precedence and ownership

For each call, apply this order and stop at the first failure:

1. Require allocation prerequisites and access. Known API/signature absence,
   unsupported value-class prerequisites or denied invocation access produce
   `ex-info` with `:value-type/error :array-unavailable`.
2. Require a `Class`, reject primitive classes explicitly, then require value
   status. Fail with `:invalid-array-component`.
3. Require `integer?` length in `[0, Integer/MAX_VALUE]`. Compare before `int`
   conversion; fail with `:invalid-array-length`.
4. Require a non-null seed compatible with the component, including at zero
   length. Fail with `:invalid-array-element`.
5. Invoke the allocator and return its result without a library-held reference.

Capability-first precedence is deliberate: on Java 8 or preview-disabled
Valhalla, even a call with ordinary placeholder arguments reports unavailable
capability. It does not claim that native allocation rejected a value fixture.
On a capable JVM, component errors precede length errors, which precede seed
errors. Test calls with multiple faults to establish this order.

Use `:component`, `:length` and `:reason` where relevant. Identify an invalid
seed as the initial value in the message; do not invent a slot index for an
empty array. Messages name missing startup flags when applicable. Preserve
available underlying causes. Catch only identified capability failures;
unexpected linkage, adapter and native allocation failures remain visible.
Unwrap reflective invocation failures so JVM errors propagate, including
memory exhaustion. Do not turn native eligibility rejection into capability
absence or cache it as such. Later stores retain native exceptions.

Construction owns the array until return; the caller then owns its slots.
Atomic element access does not supply publication, synchronization or atomic
compound updates.

## Slice order

1. **Lazy allocation boundary.** Add the baseline-safe adapter and public
   allocator. Cover one supported allocation, validation, error precedence
   and repeatable unsupported calls before widening semantic coverage.
2. **Native array semantics.** Test all four verified fixture shapes at
   lengths 0, 1 and 3, including null/non-null reference payloads. Test selected
   slots through Clojure and ordinary Java array bytecode. Add one independently
   compiled Java value fixture to check the absence of a macro registry.
3. **Fresh-JVM configuration matrix.** Keep value fixtures out of control
   namespace loading. Use the explicit process runner in
   `test-fallback/value_type/jvm_runner.clj` and its JVM selection conventions.
   Add baseline-safe array checks in `test-fallback/`; keep supported semantic
   tests in `test/`. Pass exports explicitly to the supported library run,
   rather than adding them to control configurations. Check both public
   namespaces in both require orders, repeated failures, fallback independence
   and disabled flattening. Test-only direct inspection uses non-null arrays.
4. **Packaged source consumer.** Extend `test-build/build_test.clj` with the
   allocator, preserving the existing macro consumer assertions. Resolve only
   the built JAR and POM runtime dependencies, without checkout sources.
5. **Documentation and acceptance.** Update usage, generated API, release notes
   and architecture status. Run the matrix and relevant regression/quality
   suites; retain exact commands, versions, flags and structured results in
   this task's acceptance record.

## Verification

Use real JVMs, not mocked capabilities. Scry checks child EDN outcomes as well
as process status; CLI verification checks exit status and `.scry-results/`.

The bounded matrix is Java 8 without preview; EA3 without preview, with and
without exports; EA3 with preview but no exports; EA3 with preview and exports;
and that supported configuration with `-XX:-UseArrayFlattening`. Run each with
`silent`, `warn` and `error` class-fallback policies. Controls require namespaces
and call the allocator without defining unsupported value fixtures. Supported
rows verify allocation; no-export rows may create real values but must report
array unavailability. Namespace loading and array calls emit no fallback warning.
Existing fallback tests separately verify unchanged class behavior and warnings.

Use the exact EA3 and Java 8 versions from the design evidence, recording actual
versions and OS/architecture. Select build-suite Valhalla with `JAVA_CMD` and
its baseline consumer with `VALUE_TYPE_BASELINE_JAVA`; use
`VALUE_TYPE_VALHALLA_JAVA` for cross-JVM tests. Existing commands are in
[the runtime guide](../../../doc/development/runtime.md) and
[quality guide](../../../doc/development/quality-checks.md).

## Risks and blockers

- No design blocker is known. Missing required JVMs or a different EA signature
  blocks verification; record it rather than substitute a build or skip a row.
- API presence, accessible methods and class prerequisites do not prove that
  every component is allocatable. Keep native rejection separate and visible.
- Class value status includes primitives on EA3. Reject them before accepting
  value status; do not assume flattening for wider fixtures.
- Native null diagnostic queries crash EA3. Never use them in these tests.
- Valid integer lengths can exceed memory limits. Check the upper bound through
  pure validation, not a near-limit allocation or an exhaustion experiment.
- New Java fixtures need the matching EA3 compiler and temporary-file cleanup.
  Keep them out of baseline class loading and the source-only library artifact.
- Build/clean deletes `target/`. Keep retained acceptance evidence outside it.

## Implementation progress

Slice 1 is complete. The adapter uses reflective invocation and a method-handle
access check; the public contract and error order are unchanged. Focused native
and control tests pass. Slice 2 is also complete: all fixture shapes, selected-slot access through
Clojure and Java, an independent Java value class and direct non-null inspection
pass. Java sources stay in test-resources; compiled classes use temporary
directories with cleanup. Slice 3 is complete: all six configurations, three policies and both require
orders pass. Native semantic tests pass with flattening enabled and disabled;
the existing fallback suite passes on all three host configurations.
Slice 4 is complete: JAR-only allocation and Java 8/preview-disabled controls
pass, with existing macro assertions retained. Slice 5 is complete: usage,
generated API, runtime guidance, release notes and architecture status are
updated. Final acceptance passed. Exact commands, outcomes and limits are in
[acceptance.md](acceptance.md). No plan deviation was needed.
