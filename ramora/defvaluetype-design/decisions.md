# Decisions

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
assumptions, commitment⟩. Commitment: `firm` or `provisional`
(change after the spike if necessary).

The value-class and fallback paths are implemented. D10 configuration is
user-approved. Evidence is scoped to the matrix in [fallback](fallback.md).

## D1. Expand to `clojure.core/deftype` (abstraction, interface)

See [decision details](decisions/compiler-and-transform.md).

## D2. Change compiler output, do not generate the class (responsibility)

See [decision details](decisions/compiler-and-transform.md).

## D3. Catch the bytes with a capture loader (connection, boundary)

See [decision details](decisions/compiler-and-transform.md).

## D4. Capture loader shape (technology)

See [decision details](decisions/compiler-and-transform.md).

## D5. Transformation with `java.lang.classfile` (technology)

See [decision details](decisions/compiler-and-transform.md).

## D6. Make a new constructor from the field list (data model)

See [decision details](decisions/compiler-and-transform.md).

## D7. AOT output (deployment)

See [decision details](decisions/compiler-and-transform.md).

## D8. Validation at macroexpansion (failure handling)

See [decision details](decisions/validation-and-errors.md).

## D9. Runtime check of the result (failure handling)

See [decision details](decisions/validation-and-errors.md).

## D10. Controlled fallback (scope and authority)

See [fallback policy](fallback.md#d10-controlled-fallback).

## D11. Verification (verification)

- Choice:
  - Unit tests for the transform: bytes in, flags and code out.
  - Integration tests on the Valhalla JDK: value semantics, protocols,
    `Object` overrides, type hints, inner functions, redefinition.
  - An AOT test in a subprocess JVM that loads the written class.
  - A test for each rejected form and each error path.
  - The `deftype` compatibility corpus (`value-type.deftype-compat-test`),
    copied from the Clojure 1.12.6 test suite.
  - The method-call benchmark (`value-type.bench`,
    `clojure -M:bench -m value-type.bench`). It is manual and not a
    gate. The results are in [Benchmark](benchmark.md).
- Commitment: firm.

Fallback verification extends D11; see [verification](fallback.md#verification).

## D12. Capability boundary (dependencies and failure handling)

See [capability boundary](fallback.md#d12-capability-boundary).

## D13. Fallback lifecycle (state and deployment)

See [fallback lifecycle](fallback.md#d13-fallback-lifecycle).
