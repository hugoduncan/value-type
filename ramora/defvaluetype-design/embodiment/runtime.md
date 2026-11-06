# Runtime embodiment

## Components

| Namespace | Responsibility | Decisions |
|---|---|---|
| `value-type.core` | Public macro `defvaluetype` and runtime check `check-value-class`. Validate, select representation, lazily install the value loader, expand and check. | D1 D8 D9 D10 D12 |
| `value-type.capability` | Public `report` of the four cached capability results. Reads no policy. | D14 |
| `value-type.impl.runtime` | Initialized policy, warning flag and fallback guards. | D10 D13 |
| `value-type.impl.capability` | Baseline-safe API checks, isolated format probe and the cached value-class result (`capability`). | D12 D13 |
| `value-type.impl.field-capability` | Native-field probe, its cached result (`capability`) and the native-field guard. | D12 D13 |
| `value-type.impl.loader` | Capture loader. Transform target storage and companion references, check exact field Classes, write for AOT, define and link. | D3 D4 D7, D9 define errors |
| `value-type.impl.transform` | Transform identity-class bytes with selected field descriptors and an optional hierarchy loader. | D5 D6 |

Dependency direction: `core → {runtime, impl.capability,
impl.field-capability}`; `impl.field-capability → impl.capability`;
`value-type.capability → {impl.capability, impl.field-capability,
impl.array}`; `impl.runtime` requires no capability namespace. Only the
available branch resolves `core → loader → transform`. `transform` composes
field-storage and field-access helpers using the JDK class-file API. An
optional class loader resolves dynamic types for stack maps. `transform` has
no Clojure compiler dependency.

## Fallback boundary

For definitions without enabled non-nil fields, before loader installation,
the value-class capability result and the policy select
the value-class path, ordinary `deftype`, or strict failure (D10 D12).
D13 state owners: `impl.capability` owns the delayed result of
baseline-safe `detect`; `value-type.impl.runtime` owns the initialized policy
and an atomic warning flag.
`core` must not eagerly require `loader` or `transform`; fallback must
load without their optional JDK APIs. Only the value-class path crosses
that dependency boundary.

## Interfaces

### Public

```clojure
(defvaluetype Name [fields*] options* specs*)
```

- Syntax and options: the same as `clojure.core/deftype`.
- Result: the class `Name`, the factory `->Name`. The fallback
  path returns an identity class: an ordinary `deftype` class without enabled
  non-nil fields, or a class with generated constructor checks with them.
  Enabled fields require an explicit tag that names an already defined concrete
  class: identity, value or external.
- Strict failure: an `Error` on every attempted fallback.
  Direct rejection uses plain java.lang.Error. Source compiler operations
  wrap it in CompilerException; failed AOT initializer retries can throw
  NoClassDefFoundError. These phase behaviours are verified by production tests on the recorded matrix.
- Errors: `ex-info` with `:value-type/error` and `:class-name`;
  `:value-type/error` in `#{:invalid-field :mutable-field :not-installed
  :not-value-class :value-capability-unavailable
  :field-capability-unavailable :invalid-field-enforcement :jvm-rejected
  :unexpected-constructor :unsupported-shape}`. The `defvaluetype` docstring
  Errors list ([API](../../../doc/API.md)) gives the phase and data of each. A
  `deftype` syntax error (for example a reserved field name) gives the
  `deftype` error.

```clojure
(check-value-class cls installed) ;=> nil, or throws
;; Called by the defvaluetype expansion. :not-installed when
;; installed is false, else :not-value-class.
```

### Internal

```clojure
;; value-type.impl.transform
(->value-class identity-class-bytes storage class-loader)
;=> bytes
;; storage maps emitted field names to {:storage-type ClassDesc
;; :native? boolean}. :native? false selects constructor null checks
;; instead of native annotations.
;; class-loader (or nil) resolves dynamic types for stack maps.
;; throws ex-info {:value-type/error :unexpected-constructor
;;                  :class-name "binary.Name"}
;; when D6 does not match. Check steps, in order: count of
;; constructors, parameters, code (super call, field writes).

;; value-type.impl.loader
(install! class-name specifications representation)
;=> true when installed, false when LOADER is not thread-bound
;; representation is :value or :identity; :identity requires
;; :generated enforcement.
;; Ordered specifications retain :field, exact :type Class and
;; :enforcement. Backends derive descriptors from :type.
;; :generated selects constructor checks; :native selects native
;; enforcement.
;; The capture loader throws ex-info {:value-type/error :jvm-rejected
;; :class-name} with the LinkageError as cause when the JVM rejects
;; the define or the link. Errors of ->value-class pass through.
```

The loader passes its delegate through `value-backend/prepare` as the
class-loader argument, so stack-map generation can resolve dynamically
defined field types.

## Data flow

Representation selection for definitions without enabled non-nil fields:

```text
validate fields → determine capability and application policy
  available   → existing value-class flow below
  unavailable → silent: deftype
                warn: warn once, then deftype
                error: throw Error on every attempt
```

Fallback expansions also enforce policy where the form is evaluated (D13).
For AOT, top-level forms enforce policy during namespace initialization.
Forms inside functions permit namespace loading and enforce policy on
invocation; repeated strict calls throw plain Error. Direct class access
that bypasses the guarded form is not protected, and guards cannot prevent
all prior class loading. Failed initializer retries can throw
NoClassDefFoundError without guard re-entry.
Fallback expansions do not install a capture loader or call `check-value-class`.
The existing value-class flow remains:

```text
macroexpand defvaluetype
  core: validate fields                 → ex-info :mutable-field
  core: installed := (loader/install! "ns.Name" [] :value)
                                        → LOADER := capture loader
  core: return (let [] (deftype ...)
                       (check-value-class ns.Name <installed>)
                       ns.Name)
        ;; fully qualified: the import has not run at analysis time
analyze (deftype* ...)
  compiler: emit bytes
  compiler: write .class (AOT only)
  compiler: LOADER.defineClass("ns.Name", bytes)
    loader: bytes' := transform/->value-class bytes storage delegate
            ;; value-backend/prepare: storage from [] (empty),
            ;; delegate resolves stack-map types
    loader: write bytes' (AOT only)
    loader: delegate.defineClass("ns.Name", bytes')
    loader: link (getDeclaredMethods)
    loader: define or link LinkageError → ex-info :jvm-rejected
            ;; ClassFormatError: define; VerifyError: link
runtime
  check-value-class                     → ex-info :not-installed
                                          or :not-value-class
```

### Enabled non-nil fields

`defvaluetype` validates mutable fields, constraint metadata and concrete tags
before selecting representation and ordered enforcement modes. Value output uses
native enforcement where eligible and available, otherwise generated checks.
Identity output uses generated checks and remains subject to fallback policy.
Selection does not install a loader.

Both public macroexpansion forms retain a receiving compiler boundary:
`core/value-field-deftype` or `core/identity-field-deftype`. Receiving analysis
resolves exact Classes and installs the capture loader with fixed specifications.
Unsupported receivers reject the selected mode rather than reselect enforcement.
Emitted forms retain evaluation-time representation guards.

Before target definition, the loader checks each selected Class through its delegate.
A different Class gives `:invalid-field` with `:reason :field-class-mismatch`.
Bundled ASM transforms identity output; `java.lang.classfile` transforms value
output. Both preserve concrete storage, constructor checks and owner/companion
field references. Captured companions keep their class kind. Both paths write
transformed bytes during AOT. See the
[field embodiment](../../non-nil-field-design/embodiment.md) for evidence
and accepted evidence limits.

## State

- The capture loader is transient. The thread binding of
  `Compiler/LOADER` owns it. It ends with the top-level form.
- Defined classes are in the `DynamicClassLoader` static cache. This
  is the same as `deftype`.
- The current implementation also caches reflection lookup of
  `Class::isValue` in the `capability/value-predicate` delay.
- Cached capability results owned by the probe namespaces, and initialized
  application policy plus an atomic warning flag owned by `impl.runtime`
  (D13). Concurrent attempts share this state. Strict failure is never
  disabled by a previous attempt.

## Boundaries

- The `transform` boundary hides the class file format. The detector probe also
  uses the flag values and the version rule. A change of
  the class file format in a Valhalla build (the flag values or the
  version rule) changes `transform`. Capability detection may also
  need an update when supported formats or APIs change. A change of
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
| Fallback needs no optional JDK APIs. | `core`: lazy loader resolution after positive capability |
| AOT fallback enforces policy when the form runs: namespace initialization for top-level forms, invocation for forms inside functions. | `runtime/guarded-deftype`: emitted runtime guard |
| The AOT file equals the defined bytes. | `loader`: write after transform |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| A rejected form does not change `LOADER`. | `core`: validate before install |
| Selected storage, constructors and owner/companion accesses agree on concrete descriptors. | Composed transform, companion rewrite and runtime tests |



## Selected fallback interfaces

See [fallback interfaces](runtime/fallback-interfaces.md).
