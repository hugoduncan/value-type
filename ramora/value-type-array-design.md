# Value-type array design

Status: proposed; not implemented. The existing spike supplies evidence, not
an implemented library API. [Task 012](../munera/closed/012-verify-value-arrays/design.md)
covers verification and explanation before API implementation.

This design uses the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

## Purpose and priorities

Supply a small Clojure API for native, null-restricted arrays of value
objects. Let the JVM flatten eligible elements without requiring callers
to use internal JDK methods directly.

Priorities, in order:

1. Preserve component type, null restriction and atomic element access.
2. Return native JVM arrays for existing Clojure and Java operations.
3. Keep unsupported JVMs able to load the library.
4. Permit storage and allocation gains where the JVM supports them.

Flattening and faster application execution are not guarantees.

## Context

Library users construct arrays; application operators select the JDK and
startup flags. Maintainers isolate changes to the experimental JDK API.

The current probe uses Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`,
macOS aarch64 and
`jdk.internal.value.ValueClass/newNullRestrictedAtomicArray`.
Applications need:

```text
--enable-preview
--add-exports=java.base/jdk.internal.value=ALL-UNNAMED
```

This is not a Java SE API. Method signatures and layout rules can change.
Value-class support alone does not establish array API access.

The library currently supports ordinary `deftype` fallback. That policy
controls class definition, not this new array capability. A fallback class
is an identity class and is not an accepted array component here.

## Scope

Include allocation with an initial value, construction from a finite
sequence, layout diagnostics and clear capability failures. Return
one-dimensional native arrays. Accept runtime value classes, without a
registry that limits input to classes created by `defvaluetype`.

Exclude ordinary-array fallback, collection wrappers, persistent collections,
multidimensional builders, indexed generators, non-atomic arrays, class-layout
changes and guarantees about copying through other APIs. Existing Clojure
`make-array` and `into-array` remain the portable nullable alternatives.

## Proposed contract

The provider is `value-type.array`; names are local to that namespace.

```clojure
(require '[value-type.array :as va])

(va/make-array Point 100 (Point. 0))
(va/into-array Point [(Point. 1) (Point. 2)])
(va/into-array Point (Point. 0) [])
(va/array-info a)
```

| Function | Contract |
|---|---|
| `make-array [component length initial]` | Allocate and initialize every slot. Require an integer length from zero through `Integer/MAX_VALUE`, and a non-null initial value of the component type. |
| `into-array [component values]` | Copy a non-empty finite sequence in order. Use its first value as the allocation seed. Reject empty input with guidance to supply an explicit seed. |
| `into-array [component initial values]` | Copy a finite sequence in order, including empty input. Validate the seed even when the result is empty; it is not an extra element. |
| `array-info [array]` | For a reference array, return `:component-class`, `:component-value?`, `:null-restricted?`, `:atomic?` and `:flat?`. Reject non-arrays and primitive arrays. Require diagnostic capability rather than report false for unavailable information. |

All constructors reject primitive or identity component classes, `nil`
elements and incompatible elements. They return only complete arrays.
Failure does not undo effects from consuming the input sequence.

Use `aget`, `aset` and `alength` on results. The JVM enforces component type
and null restriction on later stores, including stores outside this API.
The caller owns the mutable slots and must coordinate shared access.
Atomic element access does not make compound updates atomic, or establish
a publication or synchronization protocol. Elements themselves retain
value-class semantics.

## Design and evidence

- [Decisions and embodiment](value-type-array-design/decisions.md):
  alternatives, boundaries, state, construction flow and failures.
- [Verification and evolution](value-type-array-design/verification.md):
  scoped observations, intended qualities, tests and unresolved assumptions.
- [Semantic verification](value-type-array-design/semantic-verification.md):
  six-question report, including fatal null diagnostics and capability limits.
- [Runnable instructions](../spike/value-array-semantics/README.md) and
  [retained matrix evidence](value-type-array-design/evidence/matrix-selected-slots/).
- [Typed-array investigation](defvaluetype-design/typed-array.md): retained
  measurements and reproduction instructions.
- [Controlled fallback](defvaluetype-design/fallback.md): existing class
  policy, which this design does not change.

The contract and decisions remain proposals. Task 012 verified zero-length
allocation with valid seeds and diagnostic outcomes on the recorded EA3 build.
Null diagnostic inputs crash that JVM; public validation must reject them
before native invocation. See the remaining [implementation gates](value-type-array-design/verification.md#implementation-gates).
The explicit-seed overload avoids inventing a default value or adding a
no-argument constructor requirement.
