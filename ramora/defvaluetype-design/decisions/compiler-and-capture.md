# Compiler and capture loader decisions

Scope: the value-class path unless stated otherwise. Ordinary definitions have
no enabled non-nil fields. Definitions with enabled non-nil fields also
follow the [portable field contract](../../non-nil-field-design.md).
[D19](compiler-boundary.md) puts all compiler code use (capture loader,
class definition, class file output) in the compiler boundary
`impl.compiler`. [D20](compiler-boundary.md) gives all value definitions one
receiving expansion.

## D1. Expand to `clojure.core/deftype` (abstraction, interface)

- Choice: `defvaluetype` validates the form in both modes. On the
  value-class path it expands to a receiving macro (`let` wrapper; the
  modes are `[]` for ordinary definitions). In its own analysis the
  receiving macro checks capability, installs the loader and returns

  ```clojure
  (let []
    (value-type.impl.capability/require-value-capability! "<class>")
    ;; native-field output only:
    (value-type.impl.field-capability/require-field-capability! "<class>")
    (clojure.core/deftype name fields & opts+specs)
    (value-type.core/check-value-class <class-symbol>)
    <class-symbol>)
  ```

  - The `deftype` call has the macro arguments with no change (the
    same objects, so the metadata stays).
  - `<class-symbol>` is fully qualified; analysis precedes the `deftype` import.
  - The value-class guard is the first form; it runs where the form is
    evaluated (D17, D18).
  - `check-value-class` is D9.
  - The expansion has no installation literal. A replayed or transferred
    expansion installs where it is analysed.
- Non-nil field choice: selection validates fields, selects representation
  through application policy, and selects enforcement per field. A receiving
  macro resolves exact field Classes and installs a local capture loader before
  emitting guarded deftype and applicable result checks. Generated enforcement
  does not require native field capability. See field decisions D2–D5 for backend and transfer rules.
- Rejected: copy the `deftype` macro code. Write a new macro.
- Rationale: maximum syntax compatibility with minimum code. Option
  parsing, the factory function and the import stay the same as
  Clojure.
- Improves: compatibility, minimality. Degrades: nothing found.
- Commitment: firm.

## D2. Change compiler output, do not generate the class (responsibility)

- Choice: the Clojure compiler compiles the method bodies. The
  library changes class structure and selected field references, not the
  source method logic.
- Rejected:
  - Make the full class with the classfile API. This needs a second
    compiler for method bodies.
  - Make Java source and call `javac`. Slow. Type hints and closures
    do not map.
  - Fork `Compiler.java`. High maintenance.
- Rationale: ordinary method bytecode need not change. Non-nil fields also
  require concrete storage descriptors, native annotations or generated nil
  checks, constructor casts and matching field references in owner and
  companion method bodies.
- Improves: compatibility, minimality. Degrades: coupling to the
  shape of compiler output (see D5).
- Commitment: firm.

## D3. Catch the bytes with a capture loader (connection, boundary)

- Choice: on paths that require transformation, at macroexpansion time, the macro
  replaces the thread-bound
  value of `Compiler/LOADER` with a capture loader. The parent is the
  old value. All definitions install in receiving macro analysis; there is
  no installation during selection. Ordinary definitions change only the
  target bytes. Definitions with enabled non-nil fields also rewrite selected
  target-field references in other captured classes. The thread binding ends with the top-level form.
- Rejected:
  - A Java agent with a `ClassFileTransformer`. It needs JVM flags and
    acts on all classes.
  - Call the package-private `Compiler.analyze` with reflection.
    Private API.
  - Recompile from temporary files: duplicate class names break companion links.
- Rationale: `Compiler/LOADER` is a public dynamic var. The compiler
  binds it before it macroexpands. The expansion is a `let`, so no new
  loader is pushed between the macro and `deftype*`.
- Rule: when `Compiler/LOADER` is not thread-bound, do not install; the
  receiving macro throws `:not-installed` (D9). Receiving installation
  supports separate evaluation, replay (`(eval (macroexpand form))`),
  metadata-preserving source loading and function analysis followed by
  invocation.
- Improves: locality, no JVM flags. Degrades: depends on compiler
  evaluation order; verify this with spike and integration tests.
- Commitment: firm.

## D4. Capture loader shape (technology)

- Choice: a `proxy` of `clojure.lang.DynamicClassLoader`, in the compiler
  boundary (D19). `impl.loader` gives the boundary the byte transforms, the
  field class check and the target define as functions. It sends each
  `defineClass` call to one delegate `DynamicClassLoader`. It changes
  the bytes for the target name first. With field specifications it also
  rewrites owner-field references in other captured classes; their class kind
  stays unchanged. Exact field Class identity is checked through the delegate
  before target transformation.
- Rejected: a Java class (needs a Java build step). `gen-class` (needs
  an AOT step). `proxy-super` (not thread safe).
- Rationale: one delegate defines owner and companions. The static class
  cache lets the compiler find them.
- Commitment: firm.
