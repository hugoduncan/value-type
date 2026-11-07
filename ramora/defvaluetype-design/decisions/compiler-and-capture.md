# Compiler and capture loader decisions

Scope: the value-class path unless stated otherwise. Ordinary definitions have
no enabled non-nil fields. Definitions with enabled non-nil fields also
follow the [portable field contract](../../non-nil-field-design.md).
[D19 and D20](compiler-boundary.md) change D1, D3 and D4: one compiler boundary
owns class definition, and all definitions use a receiving expansion.

## D1. Expand to `clojure.core/deftype` (abstraction, interface)

- Choice: `defvaluetype` validates the form in both modes. On the
  ordinary value-class path it installs the loader and returns

  ```clojure
  (let []
    (clojure.core/deftype name fields & opts+specs)
    (value-type.core/check-value-class <class-symbol> <installed>)
    <class-symbol>)
  ```

  - The `deftype` call has the macro arguments with no change (the
    same objects, so the metadata stays).
  - `<class-symbol>` is fully qualified; analysis precedes the `deftype` import.
  - `<installed>` is the boolean result of `loader/install!`, as a
    literal.
  - `check-value-class` is D9.
  - Changed by D20: ordinary value definitions also expand to a receiving
    macro. The `<installed>` literal is removed.
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
  old value. Ordinary definitions install during selection and change only the
  target bytes. Definitions with enabled non-nil fields install in receiving
  macro analysis and also rewrite selected target-field references in other
  captured classes. The thread binding ends with the top-level form.
- Rejected:
  - A Java agent with a `ClassFileTransformer`. It needs JVM flags and
    acts on all classes.
  - Call the package-private `Compiler.analyze` with reflection.
    Private API.
  - Recompile from temporary files: duplicate class names break companion links.
- Rationale: `Compiler/LOADER` is a public dynamic var. The compiler
  binds it before it macroexpands. The expansion is a `let`, so no new
  loader is pushed between the macro and `deftype*`.
- Rule: when `Compiler/LOADER` is not thread-bound, do not install.
  D9 then detects the identity class. An install can also have no
  effect for ordinary definitions, for example in `(eval (macroexpand form))`.
  D9 detects this too. Expansions with enabled non-nil fields retain receiving
  installation and support separate evaluation, metadata-preserving source
  loading and function analysis followed by invocation.
- Improves: locality, no JVM flags. Degrades: depends on compiler
  evaluation order; verify this with spike and integration tests.
- Commitment: firm.

## D4. Capture loader shape (technology)

- Choice: a `proxy` of `clojure.lang.DynamicClassLoader`. It sends each
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
