# Portable non-nil fields

## Goal

Let a source library declare required, typed fields on both value-capable and
ordinary JVMs. Preserve the field type and nil constraint when the application
permits identity fallback. Use native restriction where available, without making
native support a condition of the public field contract.

Implement the target architecture recorded in commit `996cd56`:

- [Field design](../../../ramora/non-nil-field-design.md)
- [Decisions](../../../ramora/non-nil-field-design/decisions.md)
- [Verification requirements](../../../ramora/non-nil-field-design/verification.md)
- [Application fallback policy](../../../ramora/defvaluetype-design/fallback.md)

```clojure
(defvaluetype Point [^long x ^long y])
(defvaluetype Segment
  [^Point ^:non-nil start
   ^Point ^:non-nil end])
```

## Problem and context

The current `:nil-restricted` implementation accepts only eligible value-class
tags and rejects unavailable native support under every fallback policy. A library
therefore loses source portability when it adds a required nested field.

The target separates two decisions: application policy controls class representation;
field eligibility and native capability control enforcement. The invariant is fixed;
the mechanism can differ. Constructor checks are permitted costs, not value-semantics
emulation. Identity, equality and hashing still follow the selected representation.

Clojure emits reference fields as Object despite concrete hints. Portable enforcement
needs concrete storage and actual constructor checks, not factory-only validation.
The isolated feasibility gate selects bundled ASM for identity output and
java.lang.classfile for value output; production integration and public AOT have recorded verification evidence. The
[AOT audit](aot-acceptance-audit.md) records the remaining evidence limits. Java 8 and
preview-disabled EA3 are baseline verification cases; no higher fallback JDK minimum
is permitted. Existing native evidence does not prove the new contract.

## Scope and constraints

- Replace the public key with unqualified `:non-nil`; reject `:nil-restricted`
  explicitly, including disabled uses. Do not provide a compatibility alias.
- True enables the constraint; absent, false and nil disable it. Other values fail.
- Require explicit tags naming already defined concrete, non-primitive classes,
  including identity classes, fallen-back defvaluetype classes and external classes.
  Reject interfaces, abstract classes, arrays, enclosing types and unresolved types.
- Preserve concrete final storage, exact Class identity and non-nil/type enforcement
  through supported construction and reads. Mutable fields remain invalid.
- Preserve constructor order, arity, Object reference parameters, factories, ordinary
  fields, methods, companions and unrelated metadata.
- Use native enforcement when eligible and available; otherwise use generated checks.
  Missing native support alone must not warn or fail under strict policy.
- Preserve application policy, warning scope, receiver-local compilation state and
  fixed AOT representation/enforcement. Never turn an unexpected defect into fallback.
- Keep optional JDK APIs outside public loading and the identity backend. Share field
  specifications, not a global provenance registry or duplicated semantic contracts.
- Update public usage/API documentation and architecture embodiment to match delivery.
  Retain historical task and experiment evidence with its original limits.

Exclude value-semantics emulation, interface/abstract/array field tags, forward or
recursive declarations, defaults, mutable fields, layout controls, array API changes,
load-time AOT conversion and new JVM support claims. Reflective mutation with access
checks suppressed, Unsafe, instrumentation and constructor-bypassing restoration or
allocation are not covered. Performance benchmarking is adjacent work, not acceptance;
neither enforcement path promises flat storage or faster execution.

## Controlled capability testing

For native-capability absence only, `with-redefs` may replace
`value-type.impl.runtime/field-capability` with an unavailable result. This approved
no-mocks exception does not permit replacing compilation, loaders, transforms or
constructors. Override the accessor, not the detector behind its cached delay.

Keep real value-class capability and an eligible value-class tag. Compile quoted
public declarations inside the override. Use isolated child JVMs without unrelated
concurrent work: Var redefinition is process-wide. Apply the override independently
in receiving/AOT consumers where needed.

Verify value representation, concrete storage, generated checks, constructor outcomes
and no warning under every policy, including fixed generated receiving/AOT output.
Recorded boundary coverage and limits are in the
[controlled-capability audit](controlled-capability-audit.md).
Label this controlled-capability evidence, not new JVM support. No production switch
or architecture refactor is needed solely for this test.

## Acceptance

1. Both non-nil metadata forms work. Disabled constraints and plain hints retain
   ordinary behavior. Invalid metadata, the old key and ineligible tags produce
   clear structured definition errors. Eligible identity and external classes work.
2. Native fields and generated-check fields store the declared concrete type.
   Valid arguments succeed; nil throws NullPointerException; wrong types throw
   ClassCastException. Direct Clojure and Java constructors, factories and reflective
   construction agree, allowing InvocationTargetException wrapping. Error text and
   failure order among multiple invalid arguments are not contractual.
3. Nested defvaluetype declarations work unchanged on baseline JVMs under silent
   and warn. Warn remains once per loaded runtime. Error repeatedly rejects identity
   fallback. With value support, all policies allow generated enforcement when native
   enforcement is unavailable or ineligible, without a fallback warning.
4. Mixed native/generated/nullable fields, multiple constrained fields and long/double
   slots retain correct behavior. Direct and reflective reads, methods, protocols,
   interfaces, closures and companions work. Nested objects may contain nullable fields.
5. Public loading and packaged source use work without optional APIs. Baseline output
   does not require preview or raise the fallback JDK minimum. Unexpected probe,
   verification, loader and transformation failures remain visible.
6. Discarded and transferred expansions preserve validation, representation policy and
   enforcement. Receiving analysis installs local compiler state. Redefinition and
   same-name/different-loader cases retain the intended Class identity.
7. Source-free AOT works for both representations with separate Clojure and Java
   consumers. Identity artifacts retain checks and receiving policy on capable JVMs;
   value artifacts reject incompatible JVMs. Compiled enforcement does not change at
   load time. Top-level and function-contained definitions retain their policy timing.
8. The verification matrix covers baseline, preview-disabled and preview-enabled JVMs,
   all policies and every enforcement mode. Controlled capability cases are labelled
   separately from real JVM evidence. Native semantics pass with flattening on/off;
   generated and native semantics pass under interpreter and normal execution where
   supported. No timing or layout result is required.
9. Usage, generated API documentation, runtime instructions and Ramora embodiment
   distinguish implemented behavior from evidence limits. Relevant runtime,
   compatibility, packaging and tooling suites and repository quality checks pass.
   Commands, structured results and limitations are recorded in implementation.md.

## Acceptance evidence

The user accepts the combined evidence for criteria 1–8 and the delivered
documentation as sufficient. The [acceptance audit](acceptance-audit.md) maps that
evidence to the contract. [blockers.md](blockers.md) records completed final
validation of the delivered tree. Earlier partial-status notes do not
add completion requirements. Evidence limits remain explicit and do not establish
support for additional JVMs.
