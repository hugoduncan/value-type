# Embodiment

Elements, relations, boundaries and rules that realize the
[decisions](decisions.md). Pattern: pipes and filters. Each stage has
one responsibility and a data interface.

## Components

| Namespace | Responsibility | Decisions |
|---|---|---|
| `value-type.core` | Public macro `defvaluetype` and runtime check `check-value-class`. Validate, install the loader, expand, check the value-class predicate. | D1 D8 D9 |
| `value-type.impl.loader` | Capture loader. Catch the target bytes, transform, write for AOT, define, link, wrap define errors. | D3 D4 D7, D9 define errors |
| `value-type.impl.transform` | Pure function: identity-class bytes → value-class bytes. | D5 D6 |

Dependency direction: `core → loader → transform`. `transform` depends
only on JDK classes: `java.lang.classfile`, `java.lang.constant` and
`java.util.function`. `transform` has no Clojure compiler dependency.

## Interfaces

### Public

```clojure
(defvaluetype Name [fields*] options* specs*)
```

- Syntax and options: the same as `clojure.core/deftype`.
- Result: the class `Name`, the factory `->Name`.
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
- The library has no other state.

## Boundaries

- The `transform` boundary hides the class file format. Only this
  namespace knows the flag values and the version rule. A change of
  the class file format in a Valhalla build (the flag values or the
  version rule) changes only `transform`. A change of the value-class
  predicate changes only `core`.
- The `loader` boundary hides the compiler coupling. Only this
  namespace knows `Compiler/LOADER`, `DynamicClassLoader` and
  `Compiler/writeClassFile`.
- `core` knows the `deftype` syntax and the value-class predicate
  (`Class::isValue`, see D9). It uses only `loader/install!`.

## Invariants

| Invariant | Enforcement point |
|---|---|
| The capture loader changes only the target class. | `loader`: name check |
| Each defined target class is a value class. | `core`: runtime check (D9) |
| The AOT file equals the defined bytes. | `loader`: write after transform |
| Instance fields are final. | `core`: validation (D8); JVM verifier |
| A rejected form does not change `LOADER`. | `core`: validate before install |
| The transform changes only version, class flags, field flags, constructor. | `transform` unit tests |

## Project artifacts

- `deps.edn`: Clojure 1.12.6. Alias `:test` with `--enable-preview` in
  `:jvm-opts`. The JDK is a Valhalla early-access build (spike:
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). Alias `:bench`: extra
  path `bench`, criterium `0.5.245-ALPHA`, `--enable-preview` and the
  criterium blackhole JVM options (`-XX:+UnlockExperimentalVMOptions`,
  `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`).
- `deps.edn` build aliases: `:build` selects `build` with tools.build
  `0.10.10`; `:build-test` adds the separate build suite and preview
  support. Build dependencies are not runtime dependencies.
- `build.clj`: source-only JAR assembly and target cleanup. See
  [Library packaging](#library-packaging).
- `LICENSE`: full EPL-2.0 text, also included at the JAR root.
- `test-build/build_test.clj`: build contract, real CLI, input
  preservation and isolated JAR consumer checks. See the README
  [build-test command](../../README.md#run-the-build-tests).
- `src/value_type/core.clj`, `src/value_type/impl/loader.clj`,
  `src/value_type/impl/transform.clj`.
- `test/value_type/...`: unit, integration and AOT tests (D11).
- `bench/value_type/bench.clj`: the method-call benchmark
  (`value-type.bench`, D11).
- `README.md`: usage, JDK requirement, semantic differences from
  `deftype`.

## Library packaging

`build.clj` creates `target/value-type-0.1.N.jar` for
`org.hugoduncan/value-type`. The JAR contains library sources, Maven
project and runtime dependency metadata, and the EPL-2.0 license.
It excludes compiled classes, bundled dependencies and development
files. Packaging does not load or AOT-compile library namespaces.
The metadata basis excludes user configuration and development aliases.

See the README [local JAR build guide](../../README.md#build-a-local-jar)
for build and clean commands, return values, Git requirements, version
limits and the target-deletion warning. `N` is the Git commit count;
shallow history gives a partial count, and branches can share a version.
Both commands remove existing `target/` output, including benchmark
results. This creates a local artifact, not an installed or published
release.

Source-only library packaging is distinct from
[D7 consumer AOT output](decisions.md#d7-aot-output-deployment).
Consumers can still compile their value types ahead of time; those
class files require the same JDK feature release used to compile them.
JAR consumers still need runtime dependencies and a JEP 401 Valhalla
JDK with `--enable-preview`. See the README
[consumer JDK requirement](../../README.md#jdk-requirement).
