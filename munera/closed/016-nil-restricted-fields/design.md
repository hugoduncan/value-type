# Native nil-restricted fields

## Goal

Support required nested value fields in `defvaluetype` through the native JVM
mechanism. Callers must be able to rely on both the declared field type and nil
rejection, including when they bypass the generated Clojure factory.

Use the contract and decisions in the
[Ramora design](../../../ramora/nil-restricted-field-design.md) and its
[verification requirements](../../../ramora/nil-restricted-field-design/verification.md).

```clojure
(defvaluetype Point [^long x ^long y])
(defvaluetype Segment
  [^Point ^:nil-restricted start
   ^Point ^:nil-restricted end])
```

## Context

The [EA3 experiment](../../../spike/null-restricted-fields/report.md) establishes
bounded feasibility on Valhalla `27-jep401ea3+1-1` with Clojure 1.12.6. It is not a
production implementation. Clojure emits reference fields as Object even with
a type hint, so annotation emission alone is insufficient.

The feature is internal JVM support, not a Java SE contract. Field restriction
can permit flattened storage, but neither flattening nor performance is promised.
The existing fallback property controls ordinary definitions. An enabled
nil-restricted field must instead reject unavailable native support under every
fallback setting. Constructor-check fallback is not part of this task.

## Scope and constraints

- Use unqualified `:nil-restricted` metadata and an explicit `:tag`.
- Accept already defined, concrete, non-primitive runtime value classes, including
  eligible classes defined outside this library.
- Preserve ordinary fields, existing type-hint behavior, constructor arity and
  argument order, generated factories, methods and unrelated metadata.
- Require native type and nil enforcement. Keep public namespace loading safe on
  unsupported JVMs; construction must not require internal-package exports.
- Keep declaration validation, field specifications, runtime capability and native
  enforcement as distinct concepts. The JVM owns native enforcement; callers own
  constructor arguments. Do not introduce a global field registry.
- Preserve AOT's fixed representation and existing compiler error wrapping.
  Unexpected defects remain errors, not fallback or capability absence.

Exclude general non-null references, fallback emulation, mutable fields, default
values, recursive or forward type declarations, layout controls, public field
inspection, new JVM support claims and performance benchmarking. These are
separate extensions, not prerequisites for this task.

## Acceptance

1. Both `^Point ^:nil-restricted field` and
   `^{:tag Point :nil-restricted true} field` work. True enables restriction;
   absent, false and nil disable it. Other values produce a structured definition
   error. Disabled fields retain existing behavior.
2. Enabled fields reject missing or unresolved types, primitives, identity classes,
   interfaces, abstract classes, arrays and the enclosing type. Existing mutable
   field rejection remains intact. Eligible external value classes are accepted.
3. Non-null values of the declared type construct successfully. Direct constructors,
   generated factories and reflective construction reject nil with
   NullPointerException and wrong-type arguments with ClassCastException, allowing
   ordinary reflection wrapping. Multiple restricted fields and mixed two-slot
   primitive arguments work. Nested values may themselves contain nullable fields.
4. Direct and reflective field access, protocols/interfaces, Object overrides,
   closures, companion classes and nested control flow retain correct behavior.
   Unrelated field attributes are preserved; unmarked field descriptors do not
   change. Existing compatibility tests pass.
5. A lazy capability check proves valid construction and native null rejection.
   Java 8 and preview-disabled EA3 can require the library. Enabled definitions
   reject unavailable support under silent, warn and error fallback settings;
   ordinary definitions keep existing policy behavior. Structured errors identify
   class, field where applicable, and reason. No unexpected defect is swallowed.
6. Discarded or transferred macroexpansions cannot bypass the requirement. AOT
   fixtures work without source in a fresh JVM, including separately compiled
   consumers and Java calls. Incompatible JVMs reject value artifacts. Redefinition
   and class-loader cases use the intended class rather than only its binary name.
7. Semantic checks pass with field flattening enabled and disabled, and under
   interpreter and normal execution. Tests do not require flat storage. Any layout
   observation remains separate from the semantic acceptance result.
8. Usage and generated API documentation describe syntax, eligibility, exceptions,
   unsupported-JVM behavior and limits. Ramora embodiment reflects the implementation
   and evidence. Repository quality checks and relevant runtime, compatibility and
   AOT suites pass, with commands and results recorded in implementation.md.
