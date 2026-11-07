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
| D10 policy at first read | `impl.runtime` reads and validates the policy at namespace load. |
| D12 classified absences | A probe class that is not a value class, and a JVM that ignores `NullRestricted`, throw invariant errors. |
| D14 `:fallback-policy` | `report` does not read the policy; `invalid-policy-report-test` expects that. |
| D15 format table | Flag values and the version rule are in `impl.capability` and `impl.transform`. |
| D16 probe builder | `impl.capability/probe-bytes` uses `clojure.asm`, imported at namespace load. |
| D17 self-check | The probe checks only the value predicate and one field read. No array round trip; no AOT initialization check. |
| D18 switch | No `value-type.value-classes` property. |
| D19 compiler boundary | Shape assumptions are in `loader`, `transform`, `identity-backend` and `core`; each probe makes its own `DynamicClassLoader`. |
| D20 receiving expansion | Ordinary value definitions expand to `check-value-class <class> <installed>`. |
| D21 facts file | `release.yml` and the runtime guide name the JVM builds directly. |
| D22 test tiers | Full matrix for each configured JVM; no forced-JIT row. |
| D23 snapshots | No disassembly snapshots. |
| D24 tested releases | The README installs from a clone of the repository. |

## Sections

- [Runtime embodiment](embodiment/runtime.md): components, implemented
  fallback boundary, interfaces, data flow, state and invariants.
- [Project artifacts](embodiment/artifacts.md): source, tests, build and
  documentation artifacts.

## Library packaging

See [library packaging](embodiment/artifacts.md#library-packaging).
