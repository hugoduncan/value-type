# 013 Value-array allocation

## Intent

Provide the smallest useful public value-array API: allocate a native,
null-restricted atomic array with an explicit initial value. Callers can
then use ordinary Clojure and Java array operations. Safe loading and clear
capability failures are part of this stage, not later hardening work.

## Context and dependencies

The [array architecture](../../../ramora/value-type-array-design.md) defines
the contract. [Task 012](../../closed/012-verify-value-arrays/design.md) completed direct
JVM verification and review and is now closed.
Its [semantic report](../../../ramora/value-type-array-design/semantic-verification.md)
and [implementation gates](../../../ramora/value-type-array-design/implementation-gates.md)
are the evidence baseline, not tests of a public API.

Target the verified Valhalla `27-jep401ea3+1-1` configuration with preview and
`java.base/jdk.internal.value` exported to unnamed modules. Java 8 and
preview-disabled Valhalla remain namespace-loading controls. Do not infer
allocation capability from method discovery or a successful diagnostic call.

## Scope and contract

The public provider is `value-type.array`:

```clojure
(va/make-array Point 100 (Point. 0))
```

`make-array [component length initial]` returns a one-dimensional native array
of the supplied runtime value class. Every slot contains the initial value.
Length must be an integer from zero through `Integer/MAX_VALUE`; this does
not promise that the JVM can allocate every valid length. The initial value
must be non-null and compatible, even for length zero.

Reject primitive and identity components independently of value status.
Accept eligible runtime value classes without a `defvaluetype` registry;
do not promise eligibility for every untested class. Flattening is optional.
A non-flat restricted atomic array is a valid result.

Use the architecture's `ex-info` error keys for capability, component, length
and element validation. Include relevant context and retain causes without
catching all failures as capability absence. Unexpected failures and JVM
errors remain visible. Later invalid stores use native JVM exceptions.

## Boundaries and approach for planning

Follow the proposed public/adapter boundary: public validation belongs to
`value-type.array`; optional JDK discovery and invocation belong to
`value-type.impl.array`. Resolve optional APIs lazily. Cache capability, not
arrays, per loaded library runtime. Startup configuration is fixed.

Requiring either public library namespace must not require preview, internal
exports or optional JDK classes. Do not change class generation, the loader,
transformer or `value-type.fallback`. No ordinary-array fallback, warning
policy or runtime module-access change is introduced.

Prefer the smallest adapter to a provider framework. A collection wrapper
would prevent native-array interop and is not an alternative within scope.
Construction owns the unfinished array; the caller owns its slots after
return. The library retains no array. Atomic access does not establish
publication, synchronization or compound-update safety.

Planning must define validation/capability error precedence and test it,
without requiring unsupported value fixtures to load on control JVMs.

## Acceptance

1. Public allocation works for zero, one and several elements with the
   verified zero-field, one-long, two-long and reference-field fixtures.
   Initial contents, runtime component type and length are correct.
2. Ordinary `aget`, `aset`, `alength` and representative Java access work.
   Later null and wrong-type stores reject without changing stored contents.
3. Public validation covers non-Class components, primitive and identity
   classes, null/wrong-type seeds, and negative, fractional and out-of-range
   lengths. Integer conversion cannot truncate a rejected length. Do not
   allocate near heap limits solely to test validation.
4. Fresh-JVM tests cover namespace loading on Java 8 and Valhalla without
   preview; missing exports; supported allocation; repeated capability
   failures; and flattening disabled. Controls distinguish unavailable
   prerequisites from native allocation rejection.
5. Array behavior is independent of all existing class-fallback policies.
   Namespace loading adds no fallback warnings. Class behavior is unchanged.
6. An isolated packaged-source consumer can require and use the allocator
   with required flags; unsupported-runtime loading remains safe. Tests use
   real JVM boundaries, with structured Scry results, not mocked capabilities.
7. Usage documentation, API reference, release notes and architecture status
   identify the delivered allocator, required flags and experimental limits.
   Relevant library, fallback, packaging and quality checks pass. Record the
   exact verification matrix; broader JDK support is not implied.

## Out of scope

Public diagnostics (task 014), sequence construction (task 015), indexed
builders, non-atomic arrays, collection wrappers, concurrency stress, clone
or copy guarantees, wider-layout optimization and new performance studies.
Direct JDK inspection may verify tests without adding a public diagnostic API.
