# Compiler and transform decisions

Scope: the value-class path unless stated otherwise. Ordinary definitions have
no enabled non-nil fields. Definitions with enabled non-nil fields also
follow the [portable field contract](../../non-nil-field-design.md).

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

## D5. Transformation with `java.lang.classfile` (technology)

- Choice: value-class byte transformation uses the JDK classfile API. Identity
  output for definitions with enabled non-nil fields uses Clojure's bundled
  ASM, as field decision D3 specifies. Inputs include optional storage
  descriptors and a class loader for hierarchy resolution during stack-map
  generation. The capture loader supplies its delegate.
  For ordinary definitions it makes these changes:
  1. Version: running feature release, minor 0xFFFF.
  2. Class flags: remove `ACC_SUPER`/`ACC_IDENTITY`. Keep the others.
  3. Instance fields: add `ACC_STRICT_INIT`.
  4. Constructor: replace the code (D6).
  Ordinary non-constructor methods and unrelated attributes pass through.
  Non-nil fields also get concrete descriptors. Native enforcement adds a
  merged `NullRestricted` annotation; generated enforcement adds constructor nil
  checks. Existing annotation values and unrelated field elements remain.
  Selected Object field references in owner and companion methods change to
  concrete descriptors; other references remain unchanged.
- Rejected for value-class output: ASM (can lag Valhalla, extra dependency).
  `clojure.asm` (old fork, no Valhalla flags). This does not reject a baseline
  backend for ordinary identity bytecode.
- Rationale: the JDK API moves with the JDK. Existing stack map frames
  stay valid at a higher version.
- Commitment: firm.

## D6. Make a new constructor from the field list (data model)

- Choice: the new constructor code is: for each field, `aload_0`, load
  argument, `putfield`; then `aload_0`, `invokespecial Object.<init>`;
  `return`. For selected non-nil field references, insert `checkcast` before
  `putfield` and use the concrete storage descriptor. Native storage rejects
  nil where selected; otherwise an explicit constructor check rejects it. The cast
  enforces type compatibility. Identity constructors retain their ordinary superclass
  initialization order rather than the value-class order above. Constructor reference parameters
  remain Object. Argument order, arity and slots come from the original field
  model, including two-slot long and double arguments.
- Rejected: move instructions in the existing code. Fragile.
- Rationale: constructor generation uses backend-produced stack maps and the
  original class-file field model. Generated checks can require additional code.
- Check: the transform fails when the old constructor has a different
  shape. This finds a change in Clojure output early.
- Commitment: firm.

## D7. AOT output (deployment)

- Choice: on every transformed path, when `*compile-files*` is true,
  the capture loader writes
  the changed bytes with `Compiler/writeClassFile`. This replaces the
  file that the compiler wrote before.
- Rationale: the compiler writes the file before it defines the class.
- Ordinary fallback AOT keeps the compiler identity-class bytes. Fallback for
  definitions with enabled non-nil fields writes transformed identity bytes
  with typed storage and checks. Representation and enforcement are fixed at
  compilation. The fallback load guard is specified in D13.
- Commitment: firm. Source-free AOT tests must verify both representations
  and fallback receiving-policy guards.

