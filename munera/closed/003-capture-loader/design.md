# 003 Capture loader

## Intent

Make the capture loader. When the Clojure compiler defines the class
of one `deftype` form, the loader gets the bytes, changes them into a
value class with `->value-class`, and defines the value class. In AOT
compilation, the loader also writes the changed bytes to the class
file. When the JVM rejects the changed class, the loader gives a clear
error with the class name and the JVM cause.

This is decisions D3, D4 and D7 in `ramora/defvaluetype-design`, and
the define-error part of D9. The transform (D5, D6) is done (task
`002-value-class-transform`). The macro (D1, D8, the D9 runtime check)
is a later task. It calls `install!` from this task.

When a choice is not clear, choose the smaller loader. The loader must
never cause a wrong class silently.

## Problem

`->value-class` changes bytes. Nothing yet connects it to the
compiler. The compiler defines a `deftype` class during analysis,
inside the `Compiler/LOADER` binding of the top-level form. Only a
loader in that binding sees the bytes before the JVM defines the
class. `spike/valhalla/src/spike/vt.clj` shows that a capture loader
works. The spike code has no error handling, no tests, and it does
not make the JVM verify the class at define time.

## Context

- Design: `ramora/defvaluetype-design/decisions.md` (D3, D4, D7, D9),
  `embodiment.md` (namespace `value-type.impl.loader`, interface
  `install!`, boundary rules, invariants), `context.md` (compiler
  facts), `verification.md` (spike results, item 6).
- Knowledge: `mementum/memories/clojure-compiler-loader-hook.md`,
  `mementum/knowledge/testing-clojure-generated-classes.md`,
  `mementum/knowledge/java-lang-classfile.md`.
- Compiler facts (Clojure 1.12 source, `Compiler.java`):
  - `Compiler.eval` pushes a new binding of `LOADER` for each call,
    then macroexpands. A top-level `do` calls `eval` for each form.
  - `Compiler.compile1` (AOT) pushes a new binding of `LOADER` for
    each top-level form, also for each form of a top-level `do`.
  - `Compiler.load` (`require`, `load-string`) calls `eval` for each
    form.
  - `LOADER` has no root value. `(.isBound Compiler/LOADER)` is true
    only when the current thread has a binding.
  - `analyzeSeq` wraps each exception of analysis in a
    `CompilerException`. The define of a `deftype` class is in
    analysis. So an error that the loader throws reaches the caller as
    the cause of a `CompilerException`.
- Facts checked for this design on JDK `27-jep401ea3`,
  Clojure 1.12.6, with a spike capture loader:
  - Define order for a `deftype` with inner `fn`s:
    `compile__stub.<ns>.<Name>`, each inner `fn` class
    (`<ns>.<Name>$...`), the `deftype` class, then the factory and
    `eval` classes. So the inner `fn` classes exist before the
    `deftype` class is defined.
  - `DynamicClassLoader.defineClass` does format checks only. It does
    not verify. A value class with a bad constructor defines with no
    error. `Class.getDeclaredMethods` then links the class and throws
    `VerifyError`.
  - A strict instance field that is not final gives `ClassFormatError`
    at define. The output of `->value-class` for a `deftype` with a
    `:unsynchronized-mutable` field has such a field.
  - Without `--enable-preview`, the define gives
    `UnsupportedClassVersionError`. It arrives as the cause of a
    `CompilerException` ("Syntax error compiling deftype*").
  - `Compiler/compile` on a source string, with `*compile-files*` true
    and `*compile-path*` bound to a temporary directory, runs
    `compile1` with no classpath setup. With a capture loader that
    writes the changed bytes, a `URLClassLoader` on that directory
    (parent: the loader of `clojure.lang.IType`) loads the class, and
    `Class::isValue` is true. With no write, the file is the identity
    class of the compiler.
  - On JDK 25 with `--enable-preview`, the output of `->value-class`
    defines with no error as an identity class. So only the D9 runtime
    check (macro task) can find this case. The loader cannot.
- Test run: `clojure -M:test -m scry.cli` with `JAVA_CMD` set to the
  Valhalla `java` (`mementum/memories/valhalla-jdk-selection-java-cmd.md`).
  A REPL run must also check `[:summary :var-count]`.

## Scope

In scope:

1. The namespace `value-type.impl.loader` with one public function
   `install!`. Input: the binary name of the target class, a string
   (for example `"my.ns.Point"`). Output: `true` or `false`.
   - When `Compiler/LOADER` has a thread binding, `install!` sets the
     binding to a new capture loader and returns `true`. The parent of
     the capture loader is the old value of the binding.
   - Else `install!` changes nothing and returns `false`.
2. The capture loader (D4): a `proxy` of
   `clojure.lang.DynamicClassLoader` that overrides only
   `defineClass(String, byte[], Object)`. It sends each call to one
   delegate `DynamicClassLoader`. The parent of the delegate is the
   parent of the capture loader. The proxy does not use `proxy-super`.
   - A name that is not the target name: the delegate defines the
     bytes with no change.
   - The target name (exact string equality), in this order:
     1. bytes′ := `->value-class` of the bytes. Errors of
        `->value-class` pass through with no wrap.
     2. When `*compile-files*` is true: write bytes′ with
        `Compiler/writeClassFile` (D7). The internal name is the
        binary name with each `.` replaced by `/`.
     3. The delegate defines bytes′.
     4. Link the class (see "Link at define time").
     5. Return the class.
     A `LinkageError` from step 3 or step 4 is wrapped (see "Error
     contract").
3. A test namespace `value-type.impl.loader-test` (see
   "Verification").
4. Documentation updates (see "Documentation").
5. `mementum/state.md`: update the status and the next step.

## Out of scope

- The macro `defvaluetype`: field validation (D8), the expansion
  (D1), the D9 runtime check, the computation of the binary name from
  `*ns*` and the type name.
- The case where the define succeeds but the class is an identity
  class (JDK without JEP 401, or an install with no effect, such as
  `(eval (macroexpand form))`). The D9 runtime check in the macro task
  finds it.
- An end-to-end AOT test of `(compile 'ns)` in a new JVM (D11). The
  macro task does it with `defvaluetype`.
- A `defvaluetype` inside a method body of another `defvaluetype`.
  The inner install replaces the outer capture loader before the outer
  class is defined. The outer class then stays an identity class, and
  the D9 runtime check finds it.
- Removal of the identity class file that the compiler wrote when
  `->value-class` fails. The compile fails, so the namespace `__init`
  class is not written.
- Errors of classes that are not the target class.
- Changes to `value-type.impl.transform`, `spike/valhalla/` and the
  README.

## Decisions

### Keep the spike shape of D4

- Choice: a `proxy` of `DynamicClassLoader` with one delegate, as D4
  says. Only `defineClass(String, byte[], Object)` is overridden.
- Rationale: D4 is firm and verified. One delegate defines the
  `deftype` class and its inner `fn` classes, so they have the same
  defining loader. The compiler casts `LOADER` to `DynamicClassLoader`
  and defines classes only with this method. Class lookups through
  `LOADER` (`RT.baseLoader`) use the inherited methods: the static
  class cache, then the parent.
- Rejected: send each define to the parent, with no delegate. This
  is also possible, but it changes a firm decision with no need.

### Link at define time

- Choice: after the define, the loader calls
  `Class.getDeclaredMethods` on the target class. This makes the JVM
  link and verify the class. A `VerifyError` then comes from the
  loader, and the loader wraps it.
- Rationale: D9 says that `VerifyError` is wrapped. `defineClass`
  does not verify. Without the link step, a `VerifyError` comes at the
  first use of the class, with no wrap and far from the cause.
- The link step does not initialize the class. The inner `fn` classes
  are defined before the target class, so the verifier can load them.
- Basis: observed on HotSpot (JDK `27-jep401ea3`). The JVM
  specification lets a JVM link later. On such a JVM, a `VerifyError`
  comes at first use. It is not silent, so the priority "never an
  identity class silently" stays true.
- Rejected:
  - Initialize the class (`Class/forName` with initialize true). This
    runs the static initializer of the `deftype` class at compile
    time. A link is sufficient.
  - No link step. Then D9 does not hold for `VerifyError`.

### The loader wraps define errors (D9 part)

- Choice: the loader, not `value-type.core`, wraps the JVM errors of
  the define with `:value-type/error :jvm-rejected`.
- Rationale: the define is in analysis of the expansion. The code of
  `value-type.core` runs at macroexpansion and after analysis. It
  cannot catch an error of the define. The loader is the only place
  that sees the error with the class name.
- The wrap catches `LinkageError`. This covers `ClassFormatError`,
  `UnsupportedClassVersionError` and `VerifyError`, and each other
  JVM rejection of the class. One rule is simpler than a list.

### Write the AOT file before the define

- Choice: write bytes′ after `->value-class` and before the define
  (spike order).
- Rationale: the compiler wrote the identity class file before it
  called `defineClass` (A-06). When the write is first, the identity
  class file is replaced also when the JVM rejects bytes′. Then no
  identity class file stays after a successful transform. The file
  always equals the bytes that the loader gave to the JVM.

### Test through `install!` and a test macro

- Choice: the tests call `install!` from a test macro, at
  macroexpansion time, as the macro task will. The test macro expands
  to `(let [] (deftype ...) <fully qualified class symbol>)`, or to a
  `defrecord` form for the transform-error test. It is not
  `defvaluetype`: it has no validation and no runtime check.
- Rationale: `Compiler.eval` pushes a new `LOADER` binding before it
  macroexpands. So only an install during macroexpansion is in the
  binding that the define uses. This is the real path.
- Exception: the `VerifyError` test. The transform output of a
  `deftype` always verifies. So the test gives built bytes to the
  define step directly. The define step (scope item 2, steps 3 to 5,
  with the wrap) is a private function, and the test calls it through
  its var. The bytes are built with `java.lang.classfile` and
  `StackMapsOption/DROP_STACK_MAPS`. The constructor calls
  `Object.<init>` before the write of a strict final field
  (Verification item 7). Basis (observed on JDK `27-jep401ea3`): with
  the default options, `ClassFile/of` throws
  `IllegalArgumentException` ("Unset fields mismatch") at build for
  this constructor. With `DROP_STACK_MAPS`, the class builds, and the
  JVM gives `VerifyError` ("All strict final fields must be
  initialized before super()") when it verifies the class.

### Unique class names and value checks in tests

- Each test class has a unique name (`gensym`), because of the static
  class cache of `DynamicClassLoader`.
- The tests call `isValue` with `clojure.lang.Reflector`, so the test
  namespace loads with no reflection warnings on each JDK.
- The no-binding test calls `install!` on a new `Thread`. A `future`
  conveys the thread bindings, also the binding of `LOADER`.

## Error contract

When the define or the link of the target class throws a
`LinkageError`, the capture loader throws `ex-info`:

- data: `:value-type/error` `:jvm-rejected`, and `:class-name`, the
  binary name of the target class (a string);
- cause: the `LinkageError`;
- message: `Cannot define value class <class-name>: the JVM rejected
  the class. ` followed by the message of the `LinkageError`. The JVM
  message names the fix when the JVM knows it (for example
  `--enable-preview`).

The caller of the compiler gets a `CompilerException`. Its cause is
this `ex-info`. The errors of `->value-class`
(`:unexpected-constructor`) are not wrapped. They are the cause of the
`CompilerException` directly.

`install!` throws no error of its own.

## Invariants

- The capture loader changes only the target class. Each other class
  is defined with the bytes of the compiler.
- Each class that the compiler defines while a capture loader is the
  value of `LOADER` has the delegate of that capture loader as its
  defining loader. So the target class and its inner `fn` classes
  have the same defining loader.
- When `*compile-files*` is true, the class file of the target equals
  the bytes that the loader gave to the JVM.
- `install!` returns `false` exactly when it does not change
  `LOADER`.
- In `src/`, `value-type.impl.loader` is the only namespace that uses
  `Compiler/LOADER`, `DynamicClassLoader` and `Compiler/writeClassFile`
  (embodiment boundary rule, acceptance criterion 4). The rule does
  not apply to test namespaces: for example, Verification item 1
  reads `Compiler/LOADER`, and the test of item 7 can make a delegate
  `DynamicClassLoader`.
- The namespace loads with no reflection warnings, also on JDK 25.

## Documentation

Update these so that they agree with this design:

- `ramora/defvaluetype-design/decisions.md`, D9: the loader wraps the
  define errors, and it links the class so that `VerifyError` comes
  at define time. The runtime check stays in `value-type.core`.
- `ramora/defvaluetype-design/embodiment.md`: the components table
  (loader: D3 D4 D7, D9 define errors), the loader interface (errors),
  the data flow (link and wrap steps).
- `ramora/defvaluetype-design/assertions.md`: A-12 and A-19 to
  `observed` with the loader source and tests as basis. New
  assertions: `defineClass` does not verify; `getDeclaredMethods`
  links and verifies (HotSpot, JDK `27-jep401ea3`).
- `ramora/defvaluetype-design/verification.md`, "Other observations":
  on JDK 25 with `--enable-preview`, the transformed bytes define as an
  identity class.
- `ramora/defvaluetype-design.md`, "Status": the loader is
  implemented.

## Verification

The test namespace `value-type.impl.loader-test` must cover:

1. No binding. On a new `Thread` with no bindings, `install!` returns
   `false`, and `(.isBound Compiler/LOADER)` stays false.
2. Catch paths. For each path, the class of the test macro is a value
   class (`isValue` true), and a new instance has its field values:
   - `eval` of one form;
   - `load-string` of a source string (the `Compiler.load` path of
     `require`);
   - a top-level `do` with two test macro forms (two `LOADER`
     bindings);
   - a top-level `let` with two test macro forms (two installs in one
     binding: the second capture loader has the first as parent);
   - redefinition: `eval` of the same name twice gives two different
     value classes.
3. Only the target. A fixture with a protocol method that returns an
   inner `fn` that captures `this`:
   - the `fn` class is an identity class (`isValue` false);
   - the `fn` class and the target class have the same defining
     loader;
   - calling the `fn` returns an instance `identical?` to the
     receiver.
4. AOT. With `*compile-files*` true and `*compile-path*` bound to a new
   temporary directory, `Compiler/compile` of a source string with an
   `ns` form and a test macro form. A `URLClassLoader` on that
   directory, with the loader of `clojure.lang.IType` as parent, loads
   the class with no initialization. `isValue` is true.
5. No AOT write. With `*compile-files*` false and `*compile-path*`
   bound to a new temporary directory, `eval` of a test macro form
   writes no file to the directory.
6. Errors. Each case gives a `CompilerException` whose cause is the
   expected `ex-info`:
   - a `deftype` with an `:unsynchronized-mutable` `long` field:
     `:value-type/error :jvm-rejected`, the correct `:class-name`, a
     `ClassFormatError` cause, and a message that contains the class
     name and the message of the cause;
   - a `defrecord` through the test install path: `:value-type/error
     :unexpected-constructor` directly (no `:jvm-rejected` wrap).
7. Link. The private define step, given built value class bytes whose
   constructor calls `Object.<init>` before the field write: `ex-info`
   with `:value-type/error :jvm-rejected`, the correct `:class-name`
   and a `VerifyError` cause.

The tests delete their temporary directories
(`mementum/knowledge/testing-clojure-generated-classes.md`).

## Acceptance criteria

1. The tests in "Verification" exist and pass on the Valhalla JDK with
   `clojure -M:test -m scry.cli` (exit 0). The full suite passes.
2. A REPL run of `value-type.impl.loader-test` with `scry.core/run`
   gives `:pass? true` and a `[:summary :var-count]` equal to the count
   of `deftest` vars in the namespace. This count is more than 0.
3. Loading `value-type.impl.loader` and
   `value-type.impl.loader-test` gives no reflection warnings on the
   Valhalla JDK and on JDK 25.
4. Only `value-type.impl.loader` refers to `Compiler/LOADER`,
   `DynamicClassLoader` and `Compiler/writeClassFile` in `src/`
   (checked by review).
5. `install!` has a docstring that gives the input, the output, the
   effect on `Compiler/LOADER`, the call point (macroexpansion time),
   and the error contract of the capture loader.
6. The documents in "Documentation" agree with this design.
7. `mementum/state.md` shows the loader as done and gives the next
   step (the macro task).
