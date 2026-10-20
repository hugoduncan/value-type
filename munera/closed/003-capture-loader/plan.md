# Plan: 003 Capture loader

## Approach

Implement `value-type.impl.loader` as one namespace with one public
function `install!` and a private define step. Follow the spike shape
(D4): a `proxy` of `clojure.lang.DynamicClassLoader` that overrides
only `defineClass(String, byte[], Object)` and sends each call to one
delegate `DynamicClassLoader`.

Key decisions (all fixed in design.md):

- `install!` takes the binary name of the target class. When
  `Compiler/LOADER` has a thread binding, it sets the binding to a new
  capture loader (parent: the old binding value) and returns `true`.
  Else it changes nothing and returns `false`. It throws no error of
  its own.
- The target-class path, in order: transform with `->value-class`
  (errors pass through unwrapped); when `*compile-files*` is true,
  write bytes′ with `Compiler/writeClassFile` before the define (D7);
  define through the delegate; link with `Class.getDeclaredMethods`;
  return the class.
- The define-and-link step (steps 3 to 5 with the wrap) is a private
  function, so the `VerifyError` test can call it through its var.
  It takes the delegate, the binary name, the bytes and the `src`
  argument of `defineClass`, and gives `src` to the delegate.
- `LinkageError` from define or link is wrapped as `ex-info` with
  `:value-type/error :jvm-rejected`, `:class-name`, the `LinkageError`
  as cause, and the message format from the "Error contract" section.
- Tests call `install!` from a test macro at macroexpansion time
  (the real path). The test macro computes the binary name from
  `*ns*` at expansion time, as `deftype` does, and expands to
  `(let [] (deftype ...) <fq class symbol>)`, or to a `defrecord`
  form for the transform-error test. Each test class name is unique
  (`gensym`). Value checks use `clojure.lang.Reflector` to avoid
  reflection warnings.
- The `VerifyError` test builds bytes with `java.lang.classfile` and
  `StackMapsOption/DROP_STACK_MAPS` (constructor calls `Object.<init>`
  before the write of a strict final field) and gives them to the
  private define step directly.

## Risks

- Link-at-define relies on HotSpot behaviour
  (`getDeclaredMethods` triggers verification). Observed on JDK
  `27-jep401ea3`. On a JVM that links later, `VerifyError` comes at
  first use — accepted in the design (not silent).
- Preview/Valhalla JDK dependence: tests need `JAVA_CMD` set to the
  Valhalla `java`. A wrong JDK gives
  `UnsupportedClassVersionError` or identity classes.
- Reflection warnings on two JDKs (Valhalla and JDK 25) — interop
  calls need type hints; `Reflector` is used in tests where hints are
  impractical.
- The static class cache of `DynamicClassLoader` can make test
  redefinition flaky — mitigated by `gensym` class names.
- AOT test uses `Compiler/compile` with bound `*compile-files*` and
  `*compile-path*`; temporary-directory cleanup must run on failure
  (see `mementum/knowledge/testing-clojure-generated-classes.md`).

## Slice order

1. Loader core: `value-type.impl.loader` with `install!`, the capture
   loader proxy, the private define step, the error wrap, and the AOT
   write. Docstring per acceptance criterion 5.
2. Test scaffolding: test namespace, test macro, unique-name and
   value-check helpers, temp-dir fixture.
3. Behaviour tests: no-binding, catch paths (eval, load-string,
   top-level do, top-level let, redefinition), only-the-target,
   AOT write, no AOT write.
4. Error tests: `:jvm-rejected` (ClassFormatError), unwrapped
   transform error (defrecord), `VerifyError` via the private define
   step with built bytes.
5. Verification runs: CLI suite, REPL run with var-count check,
   reflection-warning checks on both JDKs.
6. Documentation: update the five documents in "Documentation" and
   `mementum/state.md`.
