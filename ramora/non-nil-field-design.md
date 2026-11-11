# Non-nil field design

This design follows the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

## Purpose and priorities

Let source libraries declare required, typed fields without requiring Valhalla.
The field must reject nil and incompatible values through every supported
constructor path. Use native JVM restriction when available; otherwise generate
enforcement. Native storage can permit flattening but is not the public contract.

Priorities: preserve the field constraint; obey application representation policy;
retain source portability and Clojure construction conventions; minimize mechanism
and cost. Neither path guarantees flattening, reduced allocation or faster execution.

Success means one declaration and the same constructor inputs have the same field
constraint on supported JVMs, when application policy permits the representation.

## Context and scope

Library authors declare fields. Applications select the JVM, flags and
`value-type.fallback` policy. Maintainers own compiler integration and enforcement.

Clojure emits reference `deftype` fields as Object despite concrete type hints.
Native restriction uses the internal `NullRestricted` annotation and eligible
value-class storage. Older JVMs lack both this mechanism and `java.lang.classfile`.
Portable enforcement must not load these APIs or raise the fallback JDK minimum.

Include immutable fields with explicit tags resolving to already defined, concrete,
non-primitive classes: value or identity classes, from this library or elsewhere.
Exclude interfaces, abstract classes, arrays, primitives, enclosing types, forward
references, defaults, recursive declarations and layout controls. Accepting concrete
identity classes avoids a provenance registry for types that have fallen back.

## Public contract

```clojure
(defvaluetype Point [^long x ^long y])
(defvaluetype Segment
  [^Point ^:non-nil start
   ^Point ^:non-nil end])
```

The explicit form is `^{:tag Point :non-nil true}`. True enables the constraint;
absent, false or nil disables it. Other values are definition errors. An ordinary
type hint remains a hint, not a new storage contract. Mutable fields remain invalid.

An enabled field requires an explicit eligible tag, resolved in the defining
namespace. Store a concrete typed final field. Preserve exact Class identity;
do not infer the type from arguments. A nested object can contain nullable fields.

Constructors retain argument order, arity and Object reference parameters.
Direct constructors, generated factories and reflective constructor calls reject
nil with NullPointerException and incompatible objects with ClassCastException.
Reflection can wrap these errors in InvocationTargetException. Error text and
which invalid argument fails first are not specified. Unmarked fields are unchanged.

The invariant applies to successful normal construction and supported reads.
Access-suppressed reflective writes, Unsafe, instrumentation and allocation or
restoration that bypasses constructors are outside this contract. Fallback does
not promise native resistance to those mechanisms. Identity, equality and hashing
still follow the selected class representation, not a cross-JVM emulation.

| Owner representation | Field enforcement | Policy |
|---|---|---|
| Value class; eligible field and native support | Native restriction and type cast | All policies |
| Value class; native restriction unavailable or field ineligible for it | Generated nil check and type cast | All policies |
| Identity class; value-class support unavailable | Generated nil check and type cast | silent or warn |
| Value-class support unavailable | No definition | error |

Requiring the public namespace stays safe on unsupported JVMs. Invalid declarations
produce structured definition errors naming class, field where applicable and
reason. Unexpected probe, loader or transformation defects remain errors. A JVM
that accepts null in the native-restriction probe field has no native support
(defvaluetype D12); generated enforcement applies.

## Architectural decisions

[Decisions](non-nil-field-design/decisions.md) define the choices and trade-offs:

- D1: use `:non-nil` to name the constraint, not the mechanism; reject the old key.
- D2: select representation through application policy, then enforcement per field.
- D3: share field specifications; isolate native and baseline-safe bytecode backends.
- D4: place generated enforcement in actual constructors, not only factories.
- D5: preserve receiver-local compilation and fixed AOT representation/enforcement.

## Embodiment and observed qualities

[Embodiment](non-nil-field-design/embodiment.md) describes the current
implementation and verification limits.

## Qualities, risks and verification

Portability and stable field constraints take priority over minimum constructor cost.
Two backends increase compiler coupling and verification cost. Native reflection
resistance and layout are not portable guarantees. Future JVM formats remain unverified.

[Verification](non-nil-field-design/verification.md) defines the fitness checks
and backend experiment. Keep performance measurements separate from semantic tests.
