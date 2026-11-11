# Embodiment

Elements, relations, boundaries and rules that realize the
[decisions](decisions.md). Pattern: pipes and filters. Each stage has
one responsibility and a data interface.

The runtime and artifact sections describe the current implementation,
including capability detection and guarded fallback for ordinary definitions.
Non-nil fields use native or generated constructor enforcement with a separate
baseline-safe identity backend. The [field embodiment](../non-nil-field-design/embodiment.md)
describes implementation details and verification limits.

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

The hosted runner targets Linux x64. Local Linux preview coverage uses native
ARM64; it does not establish x64 behavior or hosted success. Live publication
remains unverified. See the [release embodiment](../release-workflow/embodiment.md)
for release verification scope.

## Sections

- [Runtime architecture](embodiment/runtime.md): components,
  fallback boundary, interfaces, data flow, state and invariants.
- [Project artifacts](embodiment/artifacts.md): source, tests, build and
  documentation artifacts.

## Library packaging

The source-only JAR excludes compiled classes and development dependencies.
See [packaging architecture](embodiment/artifacts.md#library-packaging).
Isolated consumers check exact-JAR resources, macro definitions and capability
reports on Java 8 and EA3 with supported, plain and export-only profiles.
Supported consumers exercise allocation, both sequence arities and diagnostics.
Unavailable paths reject allocation without input effects or optional adapter
loading; export-only EA3 permits ordinary-array inspection.

## Verification boundaries

Runtime, transfer and source-free AOT suites cover representation selection,
strict retries, warning scope, compiler exception wrapping and function-contained
guards. Failed initializer retries can bypass guard re-entry; direct class
access is outside the guarded form. The compatibility corpus tests immutable
Clojure `deftype` forms, with exclusions documented in the test fixture.
Semantic tests do not establish general performance. The
[AOT startup measurement](../../munera/closed/026-compiler-boundary-and-receiving-expansion/implementation.md)
records bounded guard-cost evidence.

## Capability report coverage

Report translation and defect propagation tests cover the public map boundary.
Cross-JVM and controlled-capability tests cover agreement with cached owner
results. Runtime and policy tests cover first-read behavior and invalid policy
values; isolated source-JAR consumers check the public report without checkout
sources.
