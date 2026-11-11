# Runtime boundaries and invariants

Part of the [runtime architecture](../runtime.md).

## Boundaries

- `impl.jvm-format` holds the class file format. `transform` and the probe
  builder read the same row. A change of the class file format in a
  Valhalla build (the flag values, the version rule or the constructor
  order) changes `impl.jvm-format`. Capability detection may also need an
  update when supported APIs change. A change of
  the value-class predicate affects `capability`.
- `impl.compiler` holds all compiler code use in `src`: the read and set
  of `Compiler/LOADER`, every construction and subclass of
  `DynamicClassLoader` (capture loader and probe loaders), `.defineClass`,
  `Compiler/writeClassFile`, `Compiler$HostExpr` tag resolution and
  `Compiler/munge`. Other `src` namespaces name these only in text. The
  shape checks stay in `transform` and `impl.identity-backend` and read the
  shape data of the boundary. A new Clojure version changes this one
  namespace (D19).
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
| Value-class output runs only where the value-class result is available. | `impl.capability/require-value-capability!`: at analysis and as the first emitted form (namespace initialization or invocation) |
| Fallback follows application policy; strict retries always fail. | `runtime/enforce-fallback!` at selection, analysis and evaluation |
| Warning occurs at most once per loaded library runtime. | `runtime`: compare-and-set warning flag |
| Fallback needs no optional JDK APIs. | `loader`: lazy backend selection; identity backend uses bundled ASM; `impl.capability` and `impl.field-capability`: lazy builder resolution after the API checks |
| AOT fallback enforces policy when the form runs: namespace initialization for top-level forms, invocation for forms inside functions. | `runtime/guarded-deftype`: emitted runtime guard |
| The AOT file equals the defined bytes. | `impl.compiler/capture-loader`: `write-class-file!` after transform, before definition |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| A rejected form does not change `LOADER`. | `core`: validate before install |
| Only the compiler boundary has compiler code use in `src`. | `impl.compiler`; search check (D19) |
| Selected storage, constructors and owner/companion accesses agree on concrete descriptors. | Composed transform, companion rewrite and runtime tests |

