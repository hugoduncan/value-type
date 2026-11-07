# Array embodiment

The public namespace implements `make-array`, both `into-array` arities and
`array-info`. See the [contract](../value-type-array-design.md#contract) and
[decisions](decisions.md).

## Components and interactions

```text
caller → value-type.array → value-type.impl.array → JDK ValueClass
           validation         capability/access       allocation
           input realization  layout inspection       store invariants
caller → Clojure/Java array operations → returned native array
```

The public namespace supplies `make-array [component length initial]` and
both `into-array` arities and `array-info [array]`.
The adapter lazily resolves the allocator and value predicate, checks access
with a method handle, and invokes through reflection. It caches capability,
not arrays or seeds. Diagnostics are not allocation prerequisites.
Allocation discovery invokes the allocator once, in a round trip with the
probe Class from the value-class result: it allocates a null-restricted array
of length 2, writes one element and reads both. The round trip is the D17
semantic check of the allocated array. A failed round trip gives
`:failed-self-check`; an exception in it propagates. Discovery does not use
the class-format detector as proof of array support (D3 boundary).
Inspection has its own lazy discovery and access checks, independent of the
class capability detector. It reads the component value predicate and all three
JDK array properties directly. It does not change or retain the caller array.
Sequence construction validates one input traversal into a call-local transient
vector, then uses the allocator and copies the buffered values in order.
None of these operations changes `value-type.core`, the transformer, loader or fallback policy.
No element registry or shared array cache is needed.

Construction owns the unfinished array and temporary input storage. On
success, the caller receives the array; library state retains neither.
Java GC owns reclamation. The JVM enforces later store invariants; the
library validates inputs before return. Neither layer enforces caller
synchronization.

## Verification scope and gaps

Public API tests cover allocation, inspection and sequence construction on
Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1`,
on macOS aarch64. They include:

- Native fixtures at lengths 0, 1 and 3, an independent Java value class,
  selected-slot Clojure and Java stores, and both flattening settings.
- Direct JDK diagnostic comparisons and disposable-JVM input validation,
  without calling native diagnostics on null.
- Both sequence arities, one input traversal, early validation without input
  effects, realization failures, indexed errors and checked length accounting.
- Six startup configurations, three class-fallback policies and both namespace
  require orders. Export-only inspection does not require allocation capability.
- JAR-only consumers for supported operations and unsupported-runtime loading.

These tests do not establish arbitrary-class eligibility or broader JDK support.
Sequence construction uses O(n) temporary references; its allocation amount
and speed have not been measured. Native layout selection and the null-crash
mechanism are not explained by the probes. Concurrent stress and copy semantics
are not verified by this evidence.

The [direct-JVM report](semantic-verification.md) describes the native boundary;
it does not replace public API tests. Detailed commands and acceptance records
belong to the allocation, diagnostic and sequence tasks in `munera/closed/`.
