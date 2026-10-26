# Array decisions and embodiment

Status: proposed. All decisions apply to the first array API release;
commitments remain provisional until design approval and the verification
checks in [verification](verification.md). See the [contract](../value-type-array-design.md).

## D1. Native arrays, not a collection abstraction

- Choice: return native null-restricted atomic reference arrays.
- Rationale: the spike already uses Clojure array operations. The JVM can
  enforce stores without a library call on every read or write.
- Alternatives: ordinary typed arrays do not preserve null restriction;
  wrappers require a new access API and cannot supply native-array interop.
- Qualities: low access overhead and direct interop; mutable storage and
  JVM-specific behavior remain visible. Array identity is not element identity.
- Assumption: the selected JVM supports these arrays for accepted value classes,
  including classes for which storage remains non-flat.

## D2. Strict array capability, separate from class fallback

- Choice: fail on unavailable array capability. Do not use or extend
  `value-type.fallback`, and do not substitute an ordinary array.
- Rationale: callers must not lose null restriction because a module export
  is missing. Class generation and array allocation have separate requirements.
- Alternatives: silent or warned fallback would change store semantics;
  wrapper fallback would violate D1.
- Qualities: stable semantics and clear failures, at the cost of portability
  for callers that choose this API. Ordinary Clojure arrays remain available.
- Constraint: namespace loading must work on the library's fallback JVMs,
  without preview, internal exports or optional JDK classes.

## D3. Isolate the JDK interface

- Choice: place public validation and sequence construction in
  `value-type.array`. Place internal method discovery, invocation and layout
  inspection in `value-type.impl.array`.
- Rationale: an EA signature change should affect one implementation boundary,
  not callers, the class transformer or the capture loader.
- Alternatives: direct public imports couple namespace loading to optional
  APIs; a general provider framework adds no current capability.
- Qualities: local changes and safe loading, with a small indirect call cost
  per allocation or diagnostic request, not per element access.
- State: the adapter owns delayed capability results and method handles or
  reflective methods, scoped to one loaded library runtime. Startup
  configuration is fixed; runtime module-access changes are unsupported.
- Boundary: only the adapter calls `jdk.internal.value`. Discover allocation
  and inspection capabilities separately. Missing diagnostics must not disable
  allocation. Do not create a public SPI or reuse the class-format detector
  as proof of array support.

## D4. Explicit initialization and bounded array lengths

- Choice: require a valid seed for allocation. For sequence construction,
  realize and validate input once into temporary storage, then allocate and
  copy. Enforce the JVM integer length limit before conversion to `int`.
- Rationale: value types need not have a no-argument constructor. One input
  traversal avoids repeated effects from lazy sequences. The explicit-seed
  overload supports empty input without constructing an invented value.
- Alternatives: count then traverse can repeat work; incremental native-array
  growth adds copies; a special empty-array allocator is not yet established.
- Qualities: predictable order and errors, but O(n) temporary references and
  added allocation. The API is not a zero-allocation builder.
- Scope: inputs must be finite; memory exhaustion remains possible below the
  integer limit. A later indexed builder needs separate evidence and design.

## D5. Expose observed layout, not a flattening promise

- Choice: report actual layout through `array-info`; successful construction
  does not require `:flat?` to be true.
- Rationale: flattening depends on payload, VM configuration and JDK build.
  It is not implied by the component or array class name.
- Alternatives: a `flat-array` constructor would overstate the contract;
  rejecting non-flat storage would turn a valid semantic result into failure.
- Qualities: inspectable performance behavior without an unstable guarantee.
- Assumption: the adapter can distinguish absence of inspection support from
  a valid false result for each reported property.

## Embodiment and interactions

```text
caller → value-type.array → value-type.impl.array → JDK ValueClass
           validation         capability/access       allocation
           input realization  layout inspection       store invariants
caller → Clojure/Java array operations → returned native array
```

The public namespace supplies the four documented function arities and
`array-info`. The adapter supplies allocation and inspection internally.
Neither changes `value-type.core`, the transformer, loader or fallback policy.
No element registry or shared array cache is needed.

Construction owns the unfinished array and temporary input storage. On
success, the caller receives the array; library state retains neither.
Java GC owns reclamation. The JVM enforces later store invariants; the
library validates inputs before return. Neither layer enforces caller
synchronization.

## Failure behavior

Use `ex-info` with `:value-type/error` for library-detected failures:

- `:array-unavailable`: missing allocation support or inaccessible methods.
- `:array-inspection-unavailable`: missing inspection support or access.
- `:invalid-array-component`, `:invalid-array-length`,
  `:invalid-array-element`, `:empty-array-input`, `:invalid-array`.

Include relevant component, length, element index or capability reason.
Do not retain an entire input sequence in exception data. Messages identify
required JVM flags when applicable. Preserve underlying causes.

Errors from input realization propagate. Unexpected adapter, linkage and
allocation failures remain visible; do not classify all exceptions as
capability absence. JVM errors, including memory exhaustion, propagate.
Later `aset` failures use native JVM exceptions, not library error wrapping.
