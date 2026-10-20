# Context

C = ⟨H, S, K⟩: human environment, interacting systems, constraints.

## Human environment (H)

- Stakeholders: Clojure programmers who want value objects for
  performance or for value semantics. The maintainer.
- Expectations: `deftype` syntax and behaviour. Clear errors.
- Capabilities: users know `deftype`. Most users do not know the class
  file format.
- Risks: users can depend on identity without knowing it, for example
  with `locking` or `identical?`.

## Interacting systems (S)

### The JVM (Valhalla early-access build)

The JVM defines and verifies the class. The rules below come from
JEP 401 and the companion JEP for strict field initialization. Status:
observed on `openjdk-27-jep401ea3+1-1` (see
[Verification](verification.md)). Other builds can differ.

- A class file without `ACC_IDENTITY` (0x0020, the old `ACC_SUPER`
  bit) is a value class.
- The class file must use the preview minor version (0xFFFF). The
  major version must equal the running JVM feature release (71 for
  JDK 27).
- A concrete value class is `ACC_FINAL`.
- The superclass is `Object` or an abstract value class.
- Each instance field is `ACC_FINAL` and `ACC_STRICT_INIT` (0x0800).
- The constructor writes each strict field before it calls the super
  constructor.
- Instance methods are not `ACC_SYNCHRONIZED`.
- The JVM must run with `--enable-preview`.
- The JDK `java.lang.classfile` API knows the new flags
  (`ClassFile/ACC_IDENTITY`, `ClassFile/ACC_STRICT_INIT`,
  `ClassFile/PREVIEW_MINOR_VERSION`).
- `Class::isValue` tells if a class is a value class.

### The Clojure compiler (1.12)

Status: observed in the Clojure 1.12.1 source and with `javap` on a
1.12.6 `deftype` class.

- `clojure.core/deftype` expands to
  `(let [] (deftype* ...) (import ...) (->Name factory) Name)`.
- `Compiler.eval` and `Compiler.compile1` push a thread binding of
  `Compiler/LOADER` to a new `DynamicClassLoader`. Then they
  macroexpand the form.
- A top-level `do` is evaluated form by form. Each form gets a new
  loader. A `let` form does not.
- `NewInstanceExpr` defines the class with
  `((DynamicClassLoader) LOADER.deref()).defineClass(name, bytes, src)`.
- When `*compile-files*` is true, `NewInstanceExpr.compile` writes the
  `.class` file first. `getCompiledClass` defines the class after.
- The compiler also defines a stub class `compile__stub.<ns>.<Name>`
  for type hints. The stub stays an identity class.
- The emitted class uses major version 52 and the flags
  `ACC_PUBLIC ACC_FINAL ACC_SUPER`. Immutable fields are
  `public final`.
- The constructor calls `Object.<init>` first. Then it does one
  `putfield` for each field, in field order.
- `DynamicClassLoader` keeps a static class cache by name. Each
  `DynamicClassLoader` finds classes in this cache.

### Other systems

- `clojure.core` protocol dispatch uses the class, not the instance
  identity. Protocols work with value objects.
- Tooling (CIDER, nREPL) can call `macroexpand` outside compilation.

## Constraints (K)

- Technology: Clojure 1.12. JDK with JEP 401. No Java source in the
  project, if practical.
- Compatibility: `deftype` syntax and the semantics that a value class
  can keep.
- Time: JEP 401 is a preview. The class file details can change.
- Quality: no silent identity class. No added runtime cost.
- Organization: one maintainer. Small code base.
- Irreversibility: preview class files are tied to one JDK feature
  release. AOT output must be rebuilt for each JDK release.
