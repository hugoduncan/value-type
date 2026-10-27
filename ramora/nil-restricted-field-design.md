# Nil-restricted field design

This design follows the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

## Purpose and priorities

Let callers compose value types with required value fields. Enforce the field
contract through the JVM, including construction outside Clojure factories.
Permit flattened storage without requiring a flat source data model.

Priorities: preserve type and nil restriction; report unsupported definitions;
retain Clojure construction conventions; permit layout gains. Flattening, reduced
allocation and faster execution are not guarantees.

## Context and scope

Library users declare fields. Applications select the JVM and startup flags.
Maintainers isolate dependencies on experimental class-file behavior.

The native mechanism is `jdk.internal.vm.annotation.NullRestricted`, not a Java
SE contract. Clojure emits non-primitive `deftype` fields as `Object`, even with
a concrete type hint. Native restriction therefore needs descriptor changes,
not just an annotation. Existing strict initialization supplies the required
constructor ordering.

Include instance fields whose explicit types resolve to already defined,
concrete, non-primitive runtime value classes, including classes defined outside
this library. Exclude general non-null references, mutable fields, forward or
recursive type declarations, default values, layout controls and field diagnostics.

## Public contract

```clojure
(defvaluetype Point [^long x ^long y])

(defvaluetype Segment
  [^Point ^:nil-restricted start
   ^Point ^:nil-restricted end])
```

The explicit metadata form is `^{:tag Point :nil-restricted true}`.

| Declaration | Meaning |
|---|---|
| `field` | Existing nullable reference behavior. |
| `^Point field` | Existing type hint; no new storage contract. |
| `^Point ^:nil-restricted field` | Concrete `Point` field with native nil restriction. |

`:nil-restricted` is unqualified. `true` enables it; absent, `false` or `nil`
disables it. Other values are definition errors. Disabling it does not remove
existing primitive-field constraints.

An enabled field requires an explicit `:tag` that resolves in the defining
namespace to an eligible class. Reject missing or unresolved types, primitives,
identity classes, interfaces, abstract classes, arrays and the enclosing type.
Do not infer a type from constructor values. Resolution does not create missing
types. Preserve existing mutable-field rejection.

Construction requires a non-null instance of the declared type. Constructors
and generated factories retain their argument order and arity. Null construction
throws `NullPointerException`; incompatible arguments throw `ClassCastException`.
Reflection can wrap constructor exceptions in `InvocationTargetException`.
Unmarked fields retain existing behavior. Nil restriction is not recursive: the
nested value can contain nullable fields.

A definition with an enabled field requires native support. Reject it when that
support is unavailable, under every `value-type.fallback` policy. Do not emit an
ordinary identity replacement or emulate the contract with constructor checks.
Definitions without enabled fields keep existing fallback behavior. Requiring
the public namespace remains safe on unsupported JVMs.

Invalid declarations and unavailable capability produce structured definition
errors with class, field where applicable, and reason. Compiler operations may
wrap them. Preserve unexpected implementation failures; do not convert them to
capability absence or retry as fallback.

## Architectural decisions

All choices below apply to enabled fields only. API and failure semantics are
firm; the internal JVM mechanism is provisional across EA builds.

### D1. Metadata separates type from restriction

Use standard `:tag` plus `:nil-restricted`. Reject type-valued restriction metadata
and a new field-vector grammar. This keeps declarations familiar and avoids two
type-resolution mechanisms. Do not call this general `:non-null` support.

### D2. Carry explicit field specifications

`core` validates declarations and resolves types. The loader carries normalized
field specifications to the transform. Do not use a global field registry or
expose the internal annotation as the user interface. Keep optional JDK classes
behind the existing runtime capability boundary.

### D3. Retype storage, preserve constructor signatures

Keep the existing constructor-shape validation. Retype selected fields, emit the
native annotation, update affected field-access descriptors and insert casts
before constructor writes. Retain Object constructor parameters for reference
fields so generated factories remain compatible.

Preserve unrelated field attributes, methods and unmarked fields. Check accesses
in generated companion classes; do not assume transforming the owner alone
covers every compiler shape. AOT must write the same transformed representation
that runtime definition uses.

### D4. Verify enforcement, not annotation presence

Use a separate, lazy capability probe that constructs a valid nested value and
rejects null through the native field. Do not infer support from value-class
capability or successful annotation loading. Cache only environment capability;
validate each declared type separately. No internal-package export is part of
the intended construction contract.

### D5. Fail closed and retain representation

Native restriction is the feature, not an optional optimization. This justifies
rejecting unsupported definitions rather than weakening the contract. AOT fixes
representation and requires a compatible preview-enabled JVM; it has no load-time
fallback. Do not add `LoadableDescriptors` without a demonstrated loading need.

## Embodiment and observed qualities

The implementation separates declaration resolution (`impl.fields`), lazy native
capability (`impl.field-capability` and `impl.runtime`), capture-loader transport
and class-file emission. Specifications retain exact Classes and descriptors;
the loader rejects a same-name Class mismatch. No global field registry is used.

The owner transform composes concrete storage, merged native annotations,
constructor casts and field-access rewriting in one class-file build. Companion
references use the same selected descriptors. Constructor reference parameters
remain Object, including mixed long/double signatures. The capture delegate
resolves dynamic field types for branch-merge stack maps. Runtime definitions
and AOT writes use the same transformed bytes.

Public selection and receiving guards enforce native support. Transferred
expansions resolve specifications and install a receiver-local capture loader.
Unexpected probe and transform errors remain errors. No constructor null checks,
internal-package exports or LoadableDescriptors attribute are required by the
verified field fixtures.

Real JVM tests cover both metadata forms, eligibility, external value classes,
native rejection through all construction paths, nullable controls, attributes,
methods, closures, redefinition and loader identity. Fresh-process tests cover
all fallback policies, both expansion entry points and source-free AOT with
separately compiled Clojure and direct Java consumers. Java 8 and preview-disabled
EA3 reject saved restricted classes rather than convert their representation.

The verified environment is Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1`. Field semantics pass with flattening on/off and normal/interpreter
execution, without exports. See the [runtime commands](../doc/development/runtime.md#verify-nil-restricted-fields)
and [task evidence](../munera/closed/016-nil-restricted-fields/implementation.md).
Final full-suite acceptance remains in the task checklist.

The [isolated experiment](../spike/null-restricted-fields/report.md) provides
separate layout observations: a restricted one-long value field is flat, while
its typed nullable control is not; two-long fields are not flat in either case.
Disabling field flattening preserves nil rejection. The prototype replaces
selected attributes and is not the production transform. Its observations do
not establish production layout, performance or support for future EA builds.

## Verification and evolution

Required checks and outstanding assumptions are in
[Verification](nil-restricted-field-design/verification.md). Recheck the internal
annotation, descriptor and initialization rules on each supported JVM build.
Keep contract correctness independent of observed layout.
