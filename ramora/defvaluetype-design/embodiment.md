# Embodiment

Elements, relations, boundaries and rules that realize the
[decisions](decisions.md). Pattern: pipes and filters. Each stage has
one responsibility and a data interface.

The runtime and artifact sections describe the current implementation,
including capability detection and guarded fallback. Their contract is in [Controlled fallback](fallback.md).

## Sections

- [Runtime embodiment](embodiment/runtime.md): components, implemented
  fallback boundary, interfaces, data flow, state and invariants.
- [Project artifacts](embodiment/artifacts.md): source, tests, build and
  documentation artifacts.

## Library packaging

See [library packaging](embodiment/artifacts.md#library-packaging).
