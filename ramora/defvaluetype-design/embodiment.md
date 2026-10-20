# Embodiment

Elements, relations, boundaries and rules that realize the
[decisions](decisions.md). Pattern: pipes and filters. Each stage has
one responsibility and a data interface.

## Components

| Namespace | Responsibility | Decisions |
|---|---|---|
| `value-type.core` | Public macro `defvaluetype`. Validate, install the loader, expand, add the runtime check. | D1 D8 D9 |
| `value-type.impl.loader` | Capture loader. Catch the target bytes, transform, define, write for AOT. | D3 D4 D7 |
| `value-type.impl.transform` | Pure function: identity-class bytes → value-class bytes. | D5 D6 |

Dependency direction: `core → loader → transform`. `transform` depends
only on `java.lang.classfile`. `transform` has no Clojure compiler
dependency.

## Interfaces

### Public

```clojure
(defvaluetype Name [fields*] options* specs*)
```

- Syntax and options: the same as `clojure.core/deftype`.
- Result: the class `Name`, the factory `->Name`.
- Errors: `ex-info` with `:value-type/error` in
  `#{:mutable-field :reserved-field :not-installed :not-value-class
  :jvm-rejected :unexpected-constructor}`.

### Internal

```clojure
;; value-type.impl.transform
(->value-class ^bytes identity-class-bytes) ;=> ^bytes
;; throws ex-info :unexpected-constructor when D6 does not match

;; value-type.impl.loader
(install! class-name) ;=> true when installed, false when LOADER is not thread-bound
```

`->value-class` does not depend on the class name or a class loader.
So unit tests can call it on bytes from any `deftype`.

## Data flow

```text
macroexpand defvaluetype
  core: validate fields                 → ex-info on reject
  core: (loader/install! "ns.Name")     → LOADER := capture loader
  core: return (let [] (deftype ...) (check Name))
analyze (deftype* ...)
  compiler: emit bytes
  compiler: write .class (AOT only)
  compiler: LOADER.defineClass("ns.Name", bytes)
    loader: bytes' := transform/->value-class bytes
    loader: write bytes' (AOT only)
    loader: delegate.defineClass("ns.Name", bytes')
runtime
  check Name is a value class           → ex-info when not
```

## State

- The capture loader is transient. The thread binding of
  `Compiler/LOADER` owns it. It ends with the top-level form.
- Defined classes are in the `DynamicClassLoader` static cache. This
  is the same as `deftype`.
- The library has no other state.

## Boundaries

- The `transform` boundary hides the class file format. Only this
  namespace knows the flag values and the version rule. A change in a
  Valhalla build changes only this namespace.
- The `loader` boundary hides the compiler coupling. Only this
  namespace knows `Compiler/LOADER`, `DynamicClassLoader` and
  `Compiler/writeClassFile`.
- `core` knows only the `deftype` syntax.

## Invariants

| Invariant | Enforcement point |
|---|---|
| The capture loader changes only the target class. | `loader`: name check |
| Each defined target class is a value class. | `core`: runtime check (D9) |
| The AOT file equals the defined bytes. | `loader`: write after transform |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| The transform changes only version, class flags, field flags, constructor. | `transform` unit tests |

## Project artifacts

- `deps.edn`: Clojure 1.12. Alias `:test` with `--enable-preview` in
  `:jvm-opts`. The JDK is a Valhalla early-access build.
- `src/value_type/core.clj`, `src/value_type/impl/loader.clj`,
  `src/value_type/impl/transform.clj`.
- `test/value_type/...`: unit, integration and AOT tests (D11).
- `README.md`: usage, JDK requirement, semantic differences from
  `deftype`.
