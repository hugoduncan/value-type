---
title: Value-array capability and safety boundaries on Valhalla EA3
status: active
category: architecture
tags: [valhalla, arrays, capability, diagnostics, atomicity]
related: [mementum/knowledge/controlled-fallback-boundaries.md, mementum/knowledge/benchmarking-value-types.md, mementum/knowledge/validating-retained-jvm-evidence.md]
depends-on: []
---

# Value-array JVM boundaries

Source: task `012-verify-value-arrays`, its design, plan, steps and
implementation notes. Key commits: discovery `860286f`, allocation `8f722fb`,
diagnostics `6eb7555`, layout comparison `b7c1fdb`, startup `77d967b`, report
`3f78aaf` and selected-slot checks `44f6592`. Task
`013-value-array-allocation` adds the public adapter (`73686bb`), native
semantics (`40099e9`), fresh-JVM matrix (`6db5106`) and native-failure
regression (`3408e0b`).

These observations apply to Valhalla `27-jep401ea3+1-1`, Clojure 1.12.6,
macOS aarch64 and the tested fixtures. Recheck on another EA build. Public
allocation is implemented; public diagnostics remain separate work. Neither
the probes nor the public tests establish a universal JDK contract.

## Diagnostic access is not allocation capability

`jdk.internal.value.ValueClass` exposes
`Object[] newNullRestrictedAtomicArray(Class, int, Object)`. Its diagnostics
`isFlatArray`, `isNullRestrictedArray` and `isAtomicArray` each take Object
and return boolean. Discovery works without exports, but invocation needs
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.

With exports, an Object-array diagnostic works without preview. Thus a
successful diagnostic cannot establish value-array allocation capability.
With preview but no exports, value creation succeeds while allocator access
fails. Without preview, strict value fixture creation fails; dependent
allocation is unresolved, not an observed native allocator rejection.

Keep API presence, access, value creation and allocation as separate checks.
Do not infer an array capability from the class fallback detector alone.

## Keep optional access lazy and operation-specific

Requiring `value-type.array` must not resolve optional JDK APIs or require
preview or exports. The adapter caches allocation prerequisites and methods
per loaded library runtime, never caller arrays or seeds. Allocation must
not depend on diagnostic methods or the class-fallback warning policy.
There is no ordinary-array fallback.

Use `MethodHandles.lookup().unreflect` to check allocator access without
allocating a probe array or changing module access. Caller lookup uses the
export to unnamed modules; `publicLookup` does not provide that access.
Catch only identified discovery/access failures as capability absence.
Native eligibility rejection and unexpected failures must remain visible.

Public validation runs in this order: capability, component, length, seed.
This lets unsupported-JVM controls call with ordinary placeholders without
loading value fixtures. On a capable JVM, compare integer bounds before
conversion to `int`; test the maximum through validation rather than a
near-heap-limit allocation. A zero-length call still checks the seed.

## Test native failures below public validation

Successful allocation and invalid public inputs do not test reflective
exception propagation. Invoke the real adapter with a valid value component
and seed but an `int` length of -1. EA3 throws `IllegalArgumentException`.
Assert that the original exception escapes, not `InvocationTargetException`
or an unavailable-capability error. Then allocate successfully through the
public API in the same runtime to check that rejection did not poison the
capability cache.

The task 013 regression failed when cause unwrapping was removed and passed
when restored. This gives bounded coverage without mocks or forced memory
exhaustion. It does not directly test out-of-memory behavior.

## Empty allocation still validates the seed

The selected allocator accepts lengths 0, 1 and 3 with a valid seed. Null
and wrong-component seeds fail even at length zero. Installed signatures
and bundled Java declarations contain no seed-free overload of this
allocator. This does not exclude other allocation APIs.

Zero-field, one-long, two-long and one-Object-field value classes accept
valid seeds. A value with a null reference field is a valid non-null element.
Identity components and primitive long reject. In particular,
`long.class.isValue()` is true, but allocation with a boxed long seed fails.
Value status alone is not component eligibility; check primitives separately.

## Guard diagnostic inputs before native calls

**All three diagnostics crash this EA3 build on null with SIGSEGV.**
There is no returned false value or Java exception. The crash persists with
array flattening disabled. A public adapter must reject null before invocation.
Tests of this native failure must use separate JVMs, never the shared test JVM.
The native cause remains unresolved.

A String and a primitive long array return false for all three queries.
False is not proof that an input belongs to a proposed public diagnostic
domain. Keep public validation separate from these observed native outcomes.

## Eligibility, layout and atomic access are different properties

The restricted two-long fixture is eligible and atomic but not flat.
Disabling array flattening preserves contents, null restriction, atomic
diagnostics and store exception classes. It changes layout and some exception
messages. Retain messages for diagnosis; do not require message equality for
this semantic comparison.

Clojure and ordinary Java array access enforce null/type stores with
NullPointerException and ArrayStoreException. Selected-slot checks confirm
that accepted writes change only the selected slot and rejected writes leave
all slots unchanged. Array slots are mutable; value objects are immutable,
but their reference fields can refer to mutable objects.

The bundled declaration describes a layout designed for atomic access.
Sequential probes do not prove concurrent behavior. Atomic element access
neither makes read-modify-write atomic nor establishes safe publication.
Flat status alone proves no size or speed benefit.

## Evidence and reproduction

- Report: `ramora/value-type-array-design/semantic-verification.md`.
- Instructions: `spike/value-array-semantics/README.md`.
- Retained selected-slot matrix:
  `ramora/value-type-array-design/evidence/matrix-selected-slots/`.

The earlier `evidence/matrix/` is historical. It lacks the selected-slot
coverage required by the later validator; preserve it rather than rewrite it.
