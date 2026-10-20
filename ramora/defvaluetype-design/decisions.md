# Decisions

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
assumptions, commitment⟩. Commitment: `firm` or `provisional`
(change after the spike if necessary).

## D1. Expand to `clojure.core/deftype` (abstraction, interface)

- Choice: `defvaluetype` validates the form. Then it returns
  `(let [] (clojure.core/deftype ...) (check Name) Name)`. The
  `deftype` call has the same arguments. `check` is D9.
- Rejected: copy the `deftype` macro code. Write a new macro.
- Rationale: maximum syntax compatibility with minimum code. Option
  parsing, the factory function and the import stay the same as
  Clojure.
- Improves: compatibility, minimality. Degrades: nothing found.
- Commitment: firm.

## D2. Change compiler output, do not generate the class (responsibility)

- Choice: the Clojure compiler compiles the method bodies. The
  library changes only the class structure.
- Rejected:
  - Make the full class with the classfile API. This needs a second
    compiler for method bodies.
  - Make Java source and call `javac`. Slow. Type hints and closures
    do not map.
  - Fork `Compiler.java`. High maintenance.
- Rationale: the method bytecode does not change for a value class.
  Only the class flags, the field flags, the version and the
  constructor change.
- Improves: compatibility, minimality. Degrades: coupling to the
  shape of compiler output (see D5).
- Commitment: firm.

## D3. Catch the bytes with a capture loader (connection, boundary)

- Choice: at macroexpansion time, the macro replaces the thread-bound
  value of `Compiler/LOADER` with a capture loader. The parent is the
  old value. The capture loader changes only the bytes of the target
  class name. The thread binding ends with the top-level form.
- Rejected:
  - A Java agent with a `ClassFileTransformer`. It needs JVM flags and
    acts on all classes.
  - Call the package-private `Compiler.analyze` with reflection.
    Private API.
  - Compile to a temporary directory and define the class again. Two
    classes then have the same name. Inner function classes link to
    the identity class and cause `ClassCastException`.
- Rationale: `Compiler/LOADER` is a public dynamic var. The compiler
  binds it before it macroexpands. The expansion is a `let`, so no new
  loader is pushed between the macro and `deftype*`.
- Rule: when `Compiler/LOADER` is not thread-bound, do not install.
  D9 then detects the identity class. An install can also have no
  effect, for example in `(eval (macroexpand form))`. D9 detects this
  too.
- Improves: locality, no JVM flags. Degrades: depends on compiler
  evaluation order (verified by the spike and by tests).
- Commitment: firm.

## D4. Capture loader shape (technology)

- Choice: a `proxy` of `clojure.lang.DynamicClassLoader`. It sends each
  `defineClass` call to one delegate `DynamicClassLoader`. It changes
  the bytes for the target name first.
- Rejected: a Java class (needs a Java build step). `gen-class` (needs
  an AOT step). `proxy-super` (not thread safe).
- Rationale: one delegate defines all classes of the form. So the
  deftype class and its inner function classes have the same defining
  loader. The static class cache lets the compiler find them.
- Commitment: firm (verified by the spike).

## D5. Transformation with `java.lang.classfile` (technology)

- Choice: a pure function `bytes → bytes` with the JDK classfile API.
  It makes these changes only:
  1. Version: running feature release, minor 0xFFFF.
  2. Class flags: remove `ACC_SUPER`/`ACC_IDENTITY`. Keep the others.
  3. Instance fields: add `ACC_STRICT_INIT`.
  4. Constructor: replace the code (D6).
  All other methods and attributes pass through unchanged.
- Rejected: ASM (can lag Valhalla, extra dependency). `clojure.asm`
  (old fork, no Valhalla flags).
- Rationale: the JDK API moves with the JDK. Existing stack map frames
  stay valid at a higher version.
- Commitment: firm. Verified on JDK 27 jep401ea3.

## D6. Make a new constructor from the field list (data model)

- Choice: the new constructor code is: for each field, `aload_0`, load
  argument, `putfield`; then `aload_0`, `invokespecial Object.<init>`;
  `return`.
- Rejected: move instructions in the existing code. Fragile.
- Rationale: the code has no branches, so it needs no stack map frames
  except what the API makes. The field list and descriptors come from
  the class file.
- Check: the transform fails when the old constructor has a different
  shape. This finds a change in Clojure output early.
- Commitment: firm.

## D7. AOT output (deployment)

- Choice: when `*compile-files*` is true, the capture loader writes
  the changed bytes with `Compiler/writeClassFile`. This replaces the
  file that the compiler wrote before.
- Rationale: the compiler writes the file before it defines the class.
- Commitment: firm (verified by the spike).

## D8. Validation at macroexpansion (failure handling)

- Choice: reject with `ex-info` before expansion:
  - fields with `:volatile-mutable` or `:unsynchronized-mutable`;
  - the reserved field names of `deftype`.
- Rationale: these forms cannot be value classes. An early error is
  clearer than a `VerifyError`.
- Commitment: firm.

## D9. Runtime check of the result (failure handling)

- Choice: the expansion ends with a check `(.isValue Class)` on the
  fully qualified class symbol. If not, it throws `ex-info`. The
  runtime check is in `value-type.core`.
- Define errors: the capture loader (`value-type.impl.loader`) wraps
  each `LinkageError` of the define (`UnsupportedClassVersionError`,
  `ClassFormatError`, `VerifyError`) in `ex-info` with
  `:value-type/error :jvm-rejected` and `:class-name`. The cause
  stays. The JVM message already names `--enable-preview`. The define
  is in analysis, so `core` cannot catch these errors. The loader
  links the class (`getDeclaredMethods`) after the define, because
  `DynamicClassLoader.defineClass` does not verify. So `VerifyError`
  comes at define time.
- Rationale: some paths skip D3, for example
  `(eval (macroexpand form))`. The priority is: never an identity
  class silently.
- Commitment: firm.

## D10. No fallback to identity classes (scope)

- Choice: on a JVM without value classes, `defvaluetype` fails.
- Rejected: fall back to `deftype`. The equality semantics differ
  (identity versus field values). A fallback would change program
  behaviour silently.
- Commitment: provisional. A user option can add a fallback later.

## D11. Verification (verification)

- Choice:
  - Unit tests for the transform: bytes in, flags and code out.
  - Integration tests on the Valhalla JDK: value semantics, protocols,
    `Object` overrides, type hints, inner functions, redefinition.
  - An AOT test in a subprocess JVM that loads the written class.
  - A test for each rejected form and each error path.
- Commitment: firm.
