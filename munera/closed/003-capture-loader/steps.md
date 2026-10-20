# Steps: 003 Capture loader

## Slice 1: loader core

- [x] Create `src/value_type/impl/loader.clj` with namespace
      `value-type.impl.loader`, requiring `value-type.impl.transform`.
- [x] Add a private define-and-link function: takes the delegate, the
      binary name, the bytes and the `src` argument of
      `defineClass(String, byte[], Object)`; defines through the
      delegate with the same `src`, links with
      `Class.getDeclaredMethods`, returns the class; wraps any
      `LinkageError` as `ex-info` with `:value-type/error
      :jvm-rejected`, `:class-name`, the error as cause, and the
      message `Cannot define value class <class-name>: the JVM
      rejected the class. <cause message>`.
- [x] Add the capture loader constructor: a `proxy` of
      `DynamicClassLoader` (parent: the old `LOADER` value) that
      overrides only `defineClass(String, byte[], Object)` and uses
      one delegate `DynamicClassLoader` (parent: the capture loader's
      parent). Non-target names: delegate defines unchanged bytes.
      Target name (exact string equality): transform with
      `->value-class` (no wrap); when `*compile-files*` is true, write
      bytes′ with `Compiler/writeClassFile` (internal name: `.` →
      `/`); then call the private define-and-link step with bytes′ and
      the `src` argument of the call.
- [x] Add `install!`: when `(.isBound Compiler/LOADER)` is true, set
      the binding to a new capture loader and return `true`; else
      return `false`. Write the docstring (input, output, effect on
      `Compiler/LOADER`, call point at macroexpansion time, error
      contract).
- [x] Load the namespace with `*warn-on-reflection*` true; fix any
      reflection warnings.

## Slice 2: test scaffolding

- [x] Create `test/value_type/impl/loader_test.clj` with namespace
      `value-type.impl.loader-test`.
- [x] Add the test macro: calls `install!` at macroexpansion time with
      the fully qualified binary name; expands to
      `(let [] (deftype ...) <fq class symbol>)`; a variant expands to
      a `defrecord` form for the transform-error test.
      - The macro computes the binary name at expansion time from
        `*ns*`, as `deftype` does:
        `(str (namespace-munge *ns*) "." name)`.
      - The tests bind `*ns*` to the test namespace around `eval` and
        `load-string` (pattern: `compile-fixture` in
        `test/value_type/impl/transform_test.clj`). In the AOT test,
        the `ns` form of the source string sets `*ns*`.
- [x] Add helpers: unique class names via `gensym`; `isValue` check
      via `clojure.lang.Reflector`; temp-dir create/delete fixture.

## Slice 3: behaviour tests

- [x] Test: no binding. On a new `Thread` with no bindings, `install!`
      returns `false` and `(.isBound Compiler/LOADER)` stays false.
- [x] Test: `eval` of one test macro form gives a value class
      (`isValue` true) whose new instance has its field values.
- [x] Test: `load-string` of a source string gives a value class with
      correct field values.
- [x] Test: a top-level `do` with two test macro forms (two `LOADER`
      bindings) gives two value classes; a new instance of each class
      has its field values.
- [x] Test: a top-level `let` with two test macro forms (two installs
      in one binding) gives two value classes; a new instance of each
      class has its field values.
- [x] Test: redefinition — `eval` of the same name twice gives two
      different value classes; a new instance of each class has its
      field values.
- [x] Test: only the target. A fixture with a protocol method that
      returns an inner `fn` capturing `this`: the `fn` class is an
      identity class; the `fn` class and the target class have the
      same defining loader; calling the `fn` returns an instance
      `identical?` to the receiver.
- [x] Test: AOT. With `*compile-files*` true and `*compile-path*`
      bound to a new temp dir, `Compiler/compile` of a source string
      with an `ns` form and a test macro form; a `URLClassLoader` on
      the dir (parent: loader of `clojure.lang.IType`) loads the class
      `<munged ns of the source string>.<Name>` without initialization;
      `isValue` is true.
- [x] Test: no AOT write. With `*compile-files*` false and
      `*compile-path*` bound to a new temp dir, `eval` of a test macro
      form writes no file to the dir.

## Slice 4: error tests

- [x] Test: a `deftype` with an `:unsynchronized-mutable` `long` field
      gives a `CompilerException` whose cause is `ex-info` with
      `:value-type/error :jvm-rejected`, the correct `:class-name`, a
      `ClassFormatError` cause, and a message containing the class
      name and the cause message.
- [x] Test: a `defrecord` through the test install path gives a
      `CompilerException` whose cause is the `:unexpected-constructor`
      `ex-info` directly (no `:jvm-rejected` wrap).
- [x] Test: link. Build value class bytes with `java.lang.classfile`
      and `StackMapsOption/DROP_STACK_MAPS` whose constructor calls
      `Object.<init>` before the strict final field write; call the
      private define step through its var with a new delegate
      `DynamicClassLoader` and `src` `nil`; assert `ex-info` with
      `:value-type/error :jvm-rejected`, the correct `:class-name` and
      a `VerifyError` cause.

## Slice 5: verification runs

- [x] Run `clojure -M:test -m scry.cli` with `JAVA_CMD` set to the
      Valhalla `java`; full suite passes (exit 0).
- [x] REPL run of `value-type.impl.loader-test` with `scry.core/run`:
      `:pass? true` and `[:summary :var-count]` equal to the count of
      `deftest` vars (> 0).
- [x] Load `value-type.impl.loader` and `value-type.impl.loader-test`
      with `*warn-on-reflection*` true on the Valhalla JDK and on
      JDK 25; no warnings.

## Slice 6: documentation

- [x] Update `ramora/defvaluetype-design/decisions.md` D9: the loader
      wraps define errors and links the class; the runtime check stays
      in `value-type.core`.
- [x] Update `ramora/defvaluetype-design/embodiment.md`: components
      table (loader: D3 D4 D7, D9 define errors), loader interface
      (errors), data flow (link and wrap steps).
- [x] Update `ramora/defvaluetype-design/assertions.md`: A-12 and A-19
      to `observed` with loader source and tests as basis; add
      assertions: `defineClass` does not verify; `getDeclaredMethods`
      links and verifies (HotSpot, JDK `27-jep401ea3`).
- [x] Update `ramora/defvaluetype-design/verification.md` "Other
      observations": on JDK 25 with `--enable-preview`, the
      transformed bytes define as an identity class.
- [x] Update `ramora/defvaluetype-design.md` "Status": the loader is
      implemented.
- [x] Update `mementum/state.md`: loader done; next step is the macro
      task.

## Implementation review

- [x] `catch-paths-test`, "top-level do": make the form a `do` with two
      test macro forms, as Verification 2 says. Now it is
      `(do [m1 m2])`: one form, so both installs are in one `LOADER`
      binding (a probe shows that the second install has the first
      capture loader as its old value). This is the same as the `let`
      case. Collect the two classes with something other than the
      return value, for example `(do (def a# m1) (def b# m2) [a# b#])`
      or an atom.
- [x] `no-binding-test`: make the test fail, not hang, when
      `install!` throws on the thread. Now the promise is not
      delivered and `@result` blocks forever. For example, catch
      `Throwable` in the thread fn and deliver it, or use
      `(deref result 1000 ::timeout)`.
- [x] `ramora/defvaluetype-design/embodiment.md`, data flow: the
      `:jvm-rejected` wrap applies to the define and to the link
      (`ClassFormatError` comes from the define). Now the note is only
      on the link line.

## Test review

- [x] `install!` with a thread binding: add a test for the `true`
      case. Bind `Compiler/LOADER` to a new `DynamicClassLoader`, call
      `install!`, and assert that it returns `true`, that the binding
      is a new loader, and that `.getParent` of the new loader is the
      old value. Now no test asserts the `true` return or the parent
      rule (design scope 1, invariant "`install!` returns `false`
      exactly when it does not change `LOADER`"). The "top-level let"
      case of `catch-paths-test` does not check the parent either.
- [x] AOT write before define: add a test that the target class file
      is the transformed bytes also when the JVM rejects them. With
      `*compile-files*` true and a temp `*compile-path*`, compile the
      `:unsynchronized-mutable` `long` fixture; assert that the
      compile fails and that the class file of the target has the
      value-class flags (for example, no `ACC_IDENTITY`; read with
      `ClassFile/parse`). Design: "Write the AOT file before the
      define", invariant "the class file of the target equals the
      bytes that the loader gave to the JVM".
- [x] `jvm-rejected-test`, `link-test`: compare only the contract keys
      of `ex-data` (`:value-type/error`, `:class-name`), for example
      with `select-keys`. Now `=` on the full map fails when the
      loader adds a key, but the error contract does not forbid more
      keys.

## Test review (pass 3)

- [x] `point-ok?`: make a failure tell which check failed. Now it
      returns one boolean for `isValue` and the two field values, so
      a failure in `catch-paths-test` shows only `false`. For example,
      return `{:value? (value? cls) :x .. :y ..}` and assert
      `(= {:value? true :x 3 :y :b} (point-state cls))`.

## Test review (pass 4)

- [x] `no-aot-write-test`: assert that the `eval` result is a value
      class (for example `(is (value? cls))`). Now the test also
      passes when the install has no effect, because then the target
      path does not run and no file is written. The assertion shows
      that the write was skipped on the target path.

## Docs review

- [x] `ramora/defvaluetype-design/assertions.md`, A-25: give a basis
      that a reader can find. Now the basis is "task 003 probe", and
      the probe was not kept. A-26 has the same gap: `link-test` does
      not show that the `VerifyError` comes from the link and not
      from the define. Add the observation to
      `ramora/defvaluetype-design/verification.md` ("Other
      observations"): `DynamicClassLoader.defineClass` defines a
      value class with a bad constructor with no error;
      `Class.getDeclaredMethods` then gives `VerifyError` (HotSpot,
      JDK `27-jep401ea3`). Then use `verification.md` as the basis of
      A-25 and A-26.
- [x] `ramora/defvaluetype-design.md`, "Summary": the pipeline line
      `capture loader → transform bytes → define value class (+ write
      .class when AOT)` puts the write after the define and has no
      link step. Make it agree with the embodiment data flow and D7:
      transform, write (AOT only), define, link (`LinkageError` →
      `:jvm-rejected`).

## Code-shaper review

- [x] `link-test`: compute `cname` with `binary-name` in
      `(binding [*ns* test-ns] ...)`, as `jvm-rejected-test` does. Now
      it repeats the munged namespace as a literal
      (`"value_type.impl.loader_test."`), so there are two rules for
      the binary name in one namespace.
- [x] `aot-write-test`, `aot-write-before-define-test`: extract one
      helper for the AOT compile (bind `*compile-files*` and
      `*compile-path*` to the dir, `Compiler/compile` of a source
      string with a `sourcePath`). Now each test repeats this
      scaffolding, and the second test also repeats the
      `CompilerException` catch of `compiler-cause`. For example,
      `(aot-compile dir source-path src)` that returns the cause of a
      `CompilerException`, or nil.
- [x] `no-binding-test`: use one wait mechanism. Now `(.join t)` waits
      with no limit, so the `deref` timeout never applies. Remove
      `.join` and keep `(deref result 1000 ::timeout)`, so a hang on
      the thread fails the test and does not block it.

## Code-shaper review (pass 2)

- [x] `aot-write-test`, `aot-write-before-define-test`: derive the
      source path, the class name and the class file path from one
      namespace symbol (for example with `namespace-munge` and
      `.replace \. \/`). Now each test writes the namespace three times
      by hand (`"(ns probe.aot-rejected)"`, `"probe/aot_rejected.clj"`,
      `"probe/aot_rejected/"`), and the munge is done by hand. This is
      the same gap as the `link-test` literal of pass 1: a second rule
      for the binary name.

## Code-shaper review (pass 3)

- [x] `binary-name`, `aot-class-name`: keep one function for the
      binary-name rule. Now both compute
      `(str (namespace-munge <ns>) "." name)`, one from `*ns*` and one
      from `ns-sym`. For example, `(binary-name ns name)`; the test
      macros give `*ns*`, the AOT tests give `ns-sym`, and
      `link-test` and `jvm-rejected-test` give `test-ns` (no
      `binding` of `*ns*`).

## Code-shaper review (pass 4)

- [x] `munged-path`: make the function do one thing, and make the
      docstring agree with the code. Now the docstring says "the
      munged name `s`", but the function munges `s` again. One caller
      (`aot-compile`) gives an unmunged `ns-sym`. The other
      (`aot-class-file`) gives a binary name that `binary-name`
      already munged. For example, make `munged-path` only replace
      `.` with `/` on a munged name, and give it
      `(namespace-munge ns-sym)` in `aot-compile`.

## Code-shaper review (pass 5)

- [x] `compile-cause`, `compiler-cause`: give the two helpers names
      that show the difference. Now the names differ by two letters,
      but one takes a fn and one takes a form, so a reader must read
      both bodies to choose. For example, rename `compiler-cause` to
      `eval-cause` (it evaluates a form with `eval-here`).

## Code-shaper review (pass 6)

- [x] `catch-paths-test`, "load-string": keep the `^long` tag of `x`
      in the source string. Now `(pr-str (point-form n))` drops the
      metadata (`*print-meta*` is false), so the string has `[x y]`
      and the class has an `Object` field `x`. The `eval` case has a
      `long` field. The test passes, but the two paths do not compile
      the same fixture. For example, print with
      `(binding [*print-meta* true] (pr-str form))` in one helper.
- [x] `aot-write-test`, `aot-write-before-define-test`: derive the
      source strings from fixture forms, not hand-written strings.
      Now the point fixture is `point-form` and also the string
      `"(value-type.impl.loader-test/vt-type <n> [^long x])"`, and the
      mutable fixture is a form in `jvm-rejected-test` and also the
      string `"... [^long ^:unsynchronized-mutable x])"`. So each
      fixture has two definitions. For example, add `mutable-form`
      next to `point-form`, and give `aot-compile` the forms, printed
      with the helper of the step above.
