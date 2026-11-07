# Runtime embodiment

## Components

| Namespace | Responsibility | Decisions |
|---|---|---|
| `value-type.core` | Public macro `defvaluetype` and runtime check `check-value-class`. Validate, select representation, lazily install the value loader, expand and check. | D1 D8 D9 D10 D12 |
| `value-type.capability` | Public `report` of the four cached capability results and the policy in effect (`impl.runtime/fallback-policy`, read before the probes). | D14 |
| `value-type.impl.runtime` | Policy fixed at the first read (`fallback-policy`), warning flag and fallback guards. | D10 D13 |
| `value-type.impl.capability` | Baseline-safe API checks, lazy resolution of the probe builder, classification with the semantic self-check (`classify`, `passes-self-check?`) and the cached value-class result with the probe Class (`capability`). | D12 D13 D16 D17 |
| `value-type.impl.jvm-format` | The format row (`row`): major and minor version, identity-class and strict-field flags, constructor order. Owns format data only. | D15 |
| `value-type.impl.probe-builder` | Probe class bytes (`probe-bytes`) on `java.lang.classfile`, from the format row. | D16 |
| `value-type.impl.field-capability` | Native-field probe, its cached result (`capability`) and the native-field guard. | D12 D13 |
| `value-type.impl.loader` | Capture loader. Transform target storage and companion references, check exact field Classes, write for AOT, define and link. | D3 D4 D7, D9 define errors |
| `value-type.impl.transform` | Transform identity-class bytes with selected field descriptors and an optional hierarchy loader. | D5 D6 |

Dependency direction: `core → {runtime, impl.capability,
impl.field-capability}`; `impl.field-capability → impl.capability`;
`value-type.capability → {impl.runtime, impl.capability,
impl.field-capability, impl.array}`; `impl.runtime` requires no capability
namespace. `{impl.probe-builder, transform} → impl.jvm-format`;
`impl.jvm-format` requires no value-type namespace. `impl.capability` and
`impl.field-capability` resolve `impl.probe-builder` only with
`requiring-resolve`, after the API checks pass. `impl.array` reads the probe
Class from the value-class result and has no edge to the builder. Only the
available branch resolves `core → loader → transform`. `transform` composes
field-storage and field-access helpers using the JDK class-file API. An
optional class loader resolves dynamic types for stack maps. `transform` has
no Clojure compiler dependency.

## Fallback boundary

For definitions without enabled non-nil fields, before loader installation,
the value-class capability result and the policy select
the value-class path, ordinary `deftype`, or strict failure (D10 D12).
D13 state owners: `impl.capability` owns the delayed result of
baseline-safe `detect`; `value-type.impl.runtime` owns the policy in effect
(fixed at the first read) and an atomic warning flag.
`core` must not eagerly require `loader` or `transform`; fallback must
load without their optional JDK APIs. Only the value-class path crosses
that dependency boundary.

## Interfaces

See [runtime interfaces](runtime/interfaces.md): the public
`defvaluetype` and `check-value-class` contract, and the internal
transform and loader interfaces.

## Data flow

See [runtime data flow](runtime/data-flow.md): representation
selection, the value-class flow and enabled non-nil fields.

## State

- The capture loader is transient. The thread binding of
  `Compiler/LOADER` owns it. It ends with the top-level form.
- Defined classes are in the `DynamicClassLoader` static cache. This
  is the same as `deftype`.
- The current implementation also caches reflection lookup of
  `Class::isValue` in the `capability/value-predicate` delay.
- The available cached value-class result holds the probe Class
  (`:probe-class`), and thus its class loader, for the loaded library
  runtime.
- Cached capability results owned by the probe namespaces, and the
  application policy fixed at the first read plus an atomic warning flag
  owned by `impl.runtime` (D13). Concurrent attempts share this state. Strict
  failure is never disabled by a previous attempt.

## Boundaries

- `impl.jvm-format` holds the class file format. `transform` and the probe
  builder read the same row. A change of the class file format in a
  Valhalla build (the flag values, the version rule or the constructor
  order) changes `impl.jvm-format`. Capability detection may also need an
  update when supported APIs change. A change of
  the value-class predicate affects `capability`.
- The `loader` boundary hides the compiler coupling. Only this
  namespace uses `Compiler/LOADER` and `Compiler/writeClassFile`.
  Both `loader` and `capability` construct `DynamicClassLoader` instances:
  the loader captures user-type bytes; the detector defines an isolated probe.
- `core` knows the `deftype` syntax. Only `capability` resolves
  `Class::isValue` (`value-predicate`); core checks generated value classes
  (D9) through `capability/value-class?`, and the detector checks API
  availability and the probe result. Core reads the owner capability
  accessors and runtime policy, and lazily resolves `loader/install!`.

## Invariants

| Invariant | Enforcement point |
|---|---|
| Only the target changes class kind; captured companions can change field references. | `loader`: target transform and companion rewrite |
| Each target on the value-class path is a value class. | `core`: runtime check (D9) |
| Fallback follows application policy; strict retries always fail. | `runtime/enforce-fallback!` at selection, analysis and evaluation |
| Warning occurs at most once per loaded library runtime. | `runtime`: compare-and-set warning flag |
| Fallback needs no optional JDK APIs. | `core`: lazy loader resolution after positive capability; `impl.capability` and `impl.field-capability`: lazy builder resolution after the API checks |
| AOT fallback enforces policy when the form runs: namespace initialization for top-level forms, invocation for forms inside functions. | `runtime/guarded-deftype`: emitted runtime guard |
| The AOT file equals the defined bytes. | `loader`: write after transform |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| A rejected form does not change `LOADER`. | `core`: validate before install |
| Selected storage, constructors and owner/companion accesses agree on concrete descriptors. | Composed transform, companion rewrite and runtime tests |

## Selected fallback interfaces

See [fallback interfaces](runtime/fallback-interfaces.md).
