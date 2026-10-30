# Embodiment

Elements, relations, boundaries and rules that realize the
[decisions](decisions.md). Pattern: pipes and filters. Each stage has
one responsibility and a data interface.

The runtime and artifact sections describe the current implementation,
including capability detection and guarded fallback for ordinary definitions.
Non-nil fields use native or generated constructor enforcement with a separate
baseline-safe identity backend. The [field embodiment](../non-nil-field-design/embodiment.md)
records implementation details and accepted evidence limits.

## Implementation and verification scope

The macro, capture loader, byte transform and controlled fallback are
implemented. Production tests cover source policy, transferred expansions,
warning scope and source-free AOT guards, including runtime-only calls and
strict retries.

Fallback coverage uses Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1` with preview disabled and enabled. Value-class verification
uses the same Valhalla build. This coverage does not establish support for
every Clojure JVM or future EA class formats. The broad performance target
is not a general guarantee; see the [interface-call evidence](interface-call.md).

## Sections

- [Runtime embodiment](embodiment/runtime.md): components, implemented
  fallback boundary, interfaces, data flow, state and invariants.
- [Project artifacts](embodiment/artifacts.md): source, tests, build and
  documentation artifacts.

## Library packaging

See [library packaging](embodiment/artifacts.md#library-packaging).
