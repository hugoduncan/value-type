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

## Decided changes not yet implemented

The implementation does not yet realize these decisions:

| Decision | Gap in the current implementation |
|---|---|
| D23 snapshots | No disassembly snapshots. |


## JVM and release tooling

D21 uses [JVM facts](../../dev/jvm/jvms.edn), a baseline-safe reader and
checked provisioning. Workflow, compatibility and isolated build consumers
select roles and flag profiles from these facts.

D22 provides an ordinary-JDK smoke row and a primary-EA forced-JIT row. The
full matrix remains on the primary pair. Forced compilation applies to the
runtime suite parent, not its child JVMs. D24 preparation validates README
and changelog before writing and commits both with one version; coordinates
remain conditional on publication. No release is published.

[Task 027 evidence](../../munera/closed/027-jvm-facts-test-tiers-and-releases/implementation.md)
records the acceptance checks, bounded smoke and forced-JIT passes, checked
Linux ARM64 provisioning and complete staged local preview. The hosted runner stays Linux x64;
local ARM64 results cannot establish x64 coverage. Hosted execution
and live publication remain unverified.

## Sections

- [Runtime embodiment](embodiment/runtime.md): components, implemented
  fallback boundary, interfaces, data flow, state and invariants.
- [Project artifacts](embodiment/artifacts.md): source, tests, build and
  documentation artifacts.

## Library packaging

See [library packaging](embodiment/artifacts.md#library-packaging).
