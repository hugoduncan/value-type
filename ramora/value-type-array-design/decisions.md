# Array decisions

## D1. Native arrays, not a collection abstraction

- Choice: return native null-restricted atomic reference arrays.
- Rationale: native arrays support Clojure array operations. The JVM can
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
  `value-type.array`. Keep discovery and capability selection in the
  baseline-safe `value-type.impl.array`. Put direct JDK calls in two optional
  namespaces, `value-type.impl.array-allocation` and
  `value-type.impl.array-inspection`.
- Rationale: an EA signature change should affect one implementation boundary,
  not callers, the class transformer or the capture loader.
- Alternatives: direct public imports couple namespace loading to optional
  APIs; a general provider framework adds no current capability.
- Qualities: local changes and safe loading, with a small indirect call cost
  per allocation or diagnostic request, not per element access.
- State: the boundary owns delayed capability results and selected functions,
  scoped to one loaded library runtime. Startup
  configuration is fixed; runtime module-access changes are unsupported.
  Readers use the adapter accessors (`allocation-capability`,
  `inspection-capability`): the array functions and
  `value-type.capability/report`, which reads the same cached results.
- Invocation: use reflection and method handles only for discovery and access
  checks. Load each optional namespace after its prerequisites pass. Repeated
  operations, including component predicates, use direct JDK calls through
  cached Clojure functions. Run the allocation self-check through the selected
  allocator before publishing success. Adapter compilation and linkage failures
  propagate outside classified-absence handlers.
- Deployment: public require and require-only application AOT must not load or
  emit optional adapters. Select capabilities on the receiving JVM, not the
  build JVM. Keep the library source-only. Lazy compilation adds first-use
  cost; direct calls do not promise a speed increase.
- Boundary: only optional adapters call `jdk.internal.value`. Discover allocation
  and inspection capabilities separately. Missing diagnostics must not disable
  allocation; inspection must not trigger value-class discovery. Do not create a public SPI or reuse the class-format detector
  as proof of array support.

## D4. Explicit initialization and bounded array lengths

- Choice: require a valid seed for allocation. For sequence construction,
  realize and validate input once into temporary storage, then allocate and
  copy. Enforce the JVM integer length limit before conversion to `int`.
- Rationale: value types need not have a no-argument constructor. One input
  traversal avoids repeated effects from lazy sequences. The explicit-seed
  overload supports empty input without constructing an invented value.
- Alternatives: count then traverse can repeat work; incremental native-array
  growth adds copies; the selected EA3 atomic allocator has no seed-free overload.
- Qualities: predictable order and errors, but O(n) temporary references and
  added allocation. The API is not a zero-allocation builder.
- Scope: inputs must be finite; memory exhaustion remains possible below the
  integer limit. Indexed builders need separate design.

## D5. Expose observed layout, not a flattening promise

- Choice: report actual layout through `array-info`; successful construction
  does not require `:flat?` to be true.
- Rationale: flattening depends on payload, VM configuration and JDK build.
  It is not implied by the component or array class name.
- Alternatives: a `flat-array` constructor would overstate the contract;
  rejecting non-flat storage would turn a valid semantic result into failure.
- Qualities: inspectable performance behavior without an unstable guarantee.
- Assumption: the adapter can distinguish absence of inspection support from
  a valid false result.

## Failure behavior

Use `ex-info` with `:value-type/error` for library-detected failures:

- `:array-unavailable`: missing allocation support or inaccessible methods.
- `:array-inspection-unavailable`: missing inspection support or access.
- `:invalid-array-component`, `:invalid-array-length`,
  `:invalid-array-element`, `:empty-array-input`, `:invalid-array`.

Allocation checks capability, component, length, then seed, stopping at the
first failure. It uses `:array-unavailable`, `:invalid-array-component`,
`:invalid-array-length` and `:invalid-array-element`. Initial-value errors have no slot index, even for
length zero. Native operation failures propagate without reflective wrappers;
allocation rejection is not cached as capability absence.

Inspection first rejects null, non-arrays and primitive arrays with
`:invalid-array`, on every JVM. For valid reference arrays, missing APIs or
denied access produce `:array-inspection-unavailable`. Return all five properties
or throw; never replace unknown facts with false.

Sequence construction checks allocation capability and component before input
consumption. The explicit seed is also checked before input consumption, even
for empty or nil input. Inferred empty input fails with `:empty-array-input`.
Invalid entries use `:invalid-array-element` with a zero-based `:index` and
`:reason`; explicit-seed errors have no index. Length is checked before each
buffer append without integer narrowing. Non-seqable input uses ordinary
Clojure errors. Chunked input can realize ahead; failure does not promise full
consumption or undo input effects.

Include relevant component, length, element index or capability reason.
Do not retain an input array or entire input sequence in exception data. Messages identify
required JVM flags when applicable. Preserve underlying causes.

Errors from input realization propagate. Unexpected adapter, linkage and
allocation failures remain visible; do not classify all exceptions as
capability absence. JVM errors, including memory exhaustion, propagate.
Later `aset` failures use native JVM exceptions, not library error wrapping.

## JVM evidence constraints

The [semantic report](semantic-verification.md) describes the direct-JVM
boundary, separate from the public contracts.

- D1: all four tested value classes support native restricted atomic arrays.
  Two-long arrays remain non-flat. Clojure and Java stores enforce null and
  type restrictions, also with array flattening disabled.
- D2/D3: discovery, diagnostic access and value allocation are distinct.
  An Object-array query works without preview when exports are present;
  it cannot prove allocation capability. Java 8 lacks the optional API.
- D4: valid seeds support length zero. Null and wrong-type seeds reject even
  then. Primitive long reports value status but allocation rejects its boxed
  seed; public validation must check primitive status separately.
- D5: native queries return false for primitive long arrays and String, but
  crash on null. Validate the reference-array domain before calling
  native diagnostics.

These findings do not establish arbitrary-class eligibility, a native crash
mechanism or concurrent behavior. See [safety constraints](implementation-gates.md).
