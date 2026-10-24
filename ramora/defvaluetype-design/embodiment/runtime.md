# Runtime embodiment

## Components

| Namespace | Responsibility | Decisions |
|---|---|---|
| `value-type.core` | Public macro `defvaluetype` and runtime check `check-value-class`. Validate, select representation, lazily install the value loader, expand and check. | D1 D8 D9 D10 D12 |
| `value-type.impl.runtime` | Initialized policy, cached capability, warning flag and fallback guards. | D10 D13 |
| `value-type.impl.capability` | Baseline-safe API checks and isolated format probe. | D12 |
| `value-type.impl.loader` | Capture loader. Catch the target bytes, transform, write for AOT, define, link, wrap define errors. | D3 D4 D7, D9 define errors |
| `value-type.impl.transform` | Pure function: identity-class bytes → value-class bytes. | D5 D6 |

Dependency direction: `core → runtime → capability`; only the available
branch resolves `core → loader → transform`. `transform` depends
only on JDK classes: `java.lang.classfile`, `java.lang.constant` and
`java.util.function`. `transform` has no Clojure compiler dependency.

## Fallback boundary

Before loader installation, a capability/policy responsibility selects
the value-class path, ordinary `deftype`, or strict failure (D10 D12).
`value-type.impl.runtime` owns the state in D13: initialized policy, a
delayed result from baseline-safe `impl.capability/detect`, and an atomic
warning flag.
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
  path has the same syntax and returns an ordinary identity class.
- Strict failure: an `Error` on every attempted fallback.
  Direct rejection uses plain java.lang.Error. Source compiler operations
  wrap it in CompilerException; failed AOT initializer retries can throw
  NoClassDefFoundError. These phase behaviours are verified by production tests on the recorded matrix.
- Errors: `ex-info` with `:value-type/error` and `:class-name`;
  `:value-type/error` in `#{:mutable-field :not-installed
  :not-value-class :jvm-rejected :unexpected-constructor}`. A
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
(->value-class ^bytes identity-class-bytes) ;=> ^bytes
;; throws ex-info {:value-type/error :unexpected-constructor
;;                  :class-name "binary.Name"}
;; when D6 does not match. Check steps, in order: count of
;; constructors, parameters, code (super call, field writes).

;; value-type.impl.loader
(install! class-name) ;=> true when installed, false when LOADER is not thread-bound
;; The capture loader throws ex-info {:value-type/error :jvm-rejected
;; :class-name} with the LinkageError as cause when the JVM rejects
;; the define or the link. Errors of ->value-class pass through.
```

`->value-class` does not depend on the class name or a class loader.
So unit tests can call it on bytes from any `deftype`.

## Data flow

Representation selection, before the existing value-class flow:

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
  core: installed := (loader/install! "ns.Name")
                                        → LOADER := capture loader
  core: return (let [] (deftype ...)
                       (check-value-class ns.Name <installed>)
                       ns.Name)
        ;; fully qualified: the import has not run at analysis time
analyze (deftype* ...)
  compiler: emit bytes
  compiler: write .class (AOT only)
  compiler: LOADER.defineClass("ns.Name", bytes)
    loader: bytes' := transform/->value-class bytes
    loader: write bytes' (AOT only)
    loader: delegate.defineClass("ns.Name", bytes')
    loader: link (getDeclaredMethods)
    loader: define or link LinkageError → ex-info :jvm-rejected
            ;; ClassFormatError: define; VerifyError: link
runtime
  check-value-class                     → ex-info :not-installed
                                          or :not-value-class
```

## State

- The capture loader is transient. The thread binding of
  `Compiler/LOADER` owns it. It ends with the top-level form.
- Defined classes are in the `DynamicClassLoader` static cache. This
  is the same as `deftype`.
- The current implementation also caches reflection lookup of
  `Class::isValue` in a delay in `core`.
- Cached capability and initialized application policy, plus
  an atomic warning flag owned by the capability/policy responsibility
  (D13). Concurrent attempts share this state. Strict failure is never
  disabled by a previous attempt.

## Boundaries

- The `transform` boundary hides the class file format. The detector probe also
  uses the flag values and the version rule. A change of
  the class file format in a Valhalla build (the flag values or the
  version rule) changes `transform`. Capability detection may also
  need an update when supported formats or APIs change. A change of
  the value-class predicate affects both `core` and `capability`.
- The `loader` boundary hides the compiler coupling. Only this
  namespace uses `Compiler/LOADER` and `Compiler/writeClassFile`.
  Both `loader` and `capability` construct `DynamicClassLoader` instances:
  the loader captures user-type bytes; the detector defines an isolated probe.
- `core` knows the `deftype` syntax. Both `core` and `capability` resolve
  `Class::isValue`: core checks generated value classes (D9), and the
  detector checks API availability and the probe result. Core uses runtime
  capability and policy, and lazily resolves `loader/install!`.

## Invariants

| Invariant | Enforcement point |
|---|---|
| The capture loader changes only the target class. | `loader`: name check |
| Each target on the value-class path is a value class. | `core`: runtime check (D9) |
| Fallback follows application policy; strict retries always fail. | `runtime/enforce-fallback!` at selection, analysis and evaluation |
| Warning occurs at most once per loaded library runtime. | `runtime`: compare-and-set warning flag |
| Fallback needs no optional JDK APIs. | `core`: lazy loader resolution after positive capability |
| AOT fallback enforces policy when the form runs: namespace initialization for top-level forms, invocation for forms inside functions. | `runtime/guarded-deftype`: emitted runtime guard |
| The AOT file equals the defined bytes. | `loader`: write after transform |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| A rejected form does not change `LOADER`. | `core`: validate before install |
| The transform changes only version, class flags, field flags, constructor. | `transform` unit tests |



## Selected fallback interfaces

See [fallback interfaces](runtime/fallback-interfaces.md).
