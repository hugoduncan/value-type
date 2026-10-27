# 014 Value-array diagnostics

## Intent

Let callers inspect actual array properties without treating a class name
or successful allocation as proof of flattening. Invalid diagnostic input
must fail safely before any unsafe native call.

## Context and dependencies

Depends on [task 013](../../closed/013-value-array-allocation/design.md) for the public
namespace, JDK adapter boundary and allocation fixtures. Sequence construction
is not required.

Follow the [array contract](../../../ramora/value-type-array-design.md),
[decisions](../../../ramora/value-type-array-design/decisions.md) and task 012's
[semantic evidence](../../../ramora/value-type-array-design/semantic-verification.md).
All three native diagnostic calls crash the tested EA3 JVM on null. Native
queries also accept some inputs outside the proposed public domain. Neither
behavior defines the public validation contract.

## Scope and contract

`value-type.array/array-info [array]` accepts a reference array and returns:

- `:component-class`: the runtime component `Class`.
- `:component-value?`: whether that component is a value class.
- `:null-restricted?`: actual null restriction.
- `:atomic?`: actual atomic access status reported by the supported JVM API.
- `:flat?`: actual flattened storage status.

Accept ordinary reference arrays as well as arrays from the allocator.
Reject null, non-arrays and primitive arrays with `:invalid-array` under
`:value-type/error`, before native inspection. Require available inspection
capability; use `:array-inspection-unavailable` rather than substitute false
for unknown facts. Return a complete map or fail, not a partial result.

No flag is inferred from the array class name or component value status.
False flattening is valid. Atomic access is not a claim about compound
updates, safe publication or caller synchronization.

## Boundaries and approach for planning

Extend the architecture's existing adapter boundary, not the array access
path. Public validation guards every native diagnostic invocation. The
adapter owns lazy inspection capability state and exposes no new public SPI.
The caller owns the inspected array; inspection must not change or retain it.

Inspection capability is distinct from allocation capability. Discovery does
not prove invocation access. In task 012, ordinary Object-array inspection
worked with exports and preview disabled; allocation prerequisites were not
thereby established. Do not make allocation depend on diagnostic availability
or make diagnostics unnecessarily depend on value-class construction.

Prefer direct inspection over cached per-array properties. Do not change the
existing allocator or class-fallback semantics. Preserve causes and unexpected
failures; do not classify every invocation failure as missing capability.

## Acceptance

1. Ordinary Object/String reference arrays and restricted value arrays return
   all five properties with correct types and values. Include empty arrays,
   the verified value fixtures and a wider non-flat fixture. Compare against
   supported JDK inspection in the recorded environment.
2. Null, non-array and primitive-array inputs fail through public validation.
   Run crash-risk public-wrapper cases in disposable JVMs with timeouts first;
   assert a normal exception and clean process completion, not merely absence
   of a returned value. Never call native null diagnostics in a shared JVM.
3. Fresh-JVM tests distinguish API absence, invocation access and preview
   settings. Object-array inspection works without preview where verified;
   Java 8 and missing exports fail clearly. Invalid-domain validation remains
   safe even when optional APIs are unavailable.
4. Flattening-disabled controls still report null restriction and atomicity
   accurately. Ordinary and restricted arrays are not confused. No property
   is fabricated when inspection is unavailable.
5. Allocation regression checks still pass, and allocation does not acquire
   an inspection-capability dependency. Namespace loading remains safe on the
   task 013 control JVMs; class-fallback policy remains unchanged.
6. An isolated packaged consumer exercises the public function. Usage and API
   documentation explain its domain, return keys, errors, flags and limits.
   Architecture status and release notes identify the delivered diagnostics.
7. Relevant semantic, fallback/loading, packaging and quality checks pass
   with structured results. Retained task 012 crash evidence is not overwritten
   or rerun as ordinary library tests.

## Out of scope

Sequence construction, a general object inspector, primitive-array diagnostics,
layout-size estimates, native crash-cause investigation, broader JDK support,
concurrency guarantees, performance gates and array-copy semantics. These need
separate contracts or evidence rather than additional diagnostic flags.
