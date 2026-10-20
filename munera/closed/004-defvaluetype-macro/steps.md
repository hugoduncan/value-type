# Steps

## Slice 1: runtime check

- [x] Add `(set! *warn-on-reflection* true)` to `value-type.core`
      after the `ns` form (as `src/value_type/impl/loader.clj:9`).
- [x] Add `check-value-class` to `value-type.core`: args `[cls installed]`;
      value-class predicate via `.getMethod` lookup of `isValue`
      (nil method → not a value class); returns `nil` on a value
      class; else throws `ex-info` with `:value-type/error`
      (`:not-installed` when `installed` is false, else
      `:not-value-class`) and `:class-name` (binary name string);
      messages per the Error contract, branching on `isValue`
      presence; docstring says the `defvaluetype` expansion calls it.
- [x] Create `test/value_type/core_test.clj` with
      `*warn-on-reflection*` true and tests: `check-value-class` on a
      `deftype` identity class gives `:not-value-class` when
      `installed` is true and `:not-installed` when false;
      on a value class (made via the loader, as in
      `value-type.impl.loader-test`) gives `nil`.
- [x] Run the tests and confirm they pass.

## Slice 2: macro

- [x] Add `defvaluetype` to `value-type.core`: arglists
      `([name [& fields] & opts+specs])`; when `fields` is a vector,
      collect symbol elements with truthy `:volatile-mutable` or
      `:unsynchronized-mutable` metadata and throw `ex-info`
      `:mutable-field` with `:value-type/error`, `:class-name` and
      `:fields` (field order) when any exist; else call
      `loader/install!` with `(str (namespace-munge *ns*) "." name)`;
      return `(let [] (deftype ...) (check-value-class <class-sym>
      <installed>) <class-sym>)` with arguments unchanged and
      `<installed>` a boolean literal; docstring per the Macro
      contract (deftype syntax reference, JEP 401 / `--enable-preview`
      requirement, no mutable fields, semantic differences, error
      keys).
- [x] Add basic tests (Verification 1): `long` and `Object` fields;
      result is the class and `isValue` true; `->Name` and imported
      constructor make instances with the field values; same-value
      instances `=` and `identical?`; different values not `=`.
- [x] Add compile-path tests (Verification 2): `eval` of one form,
      `load-string` of a source string, and a call inside a `defn`
      body each give a value class.
- [x] Run the tests and confirm they pass.

## Slice 3: behaviour tests

- [x] Add spec tests (Verification 3): inline protocol method and
      interface method results; `Object` overrides (`toString`,
      `equals`, `hashCode`) used; a method returning an inner `fn`
      capturing `this` returns an instance `identical?` to the
      receiver.
- [x] Add field tests (Verification 4): zero fields; fields hinted
      `long`, `double` and a class type have the field types
      `long`, `double`, `Object` (as `deftype`); a
      field with `{:volatile-mutable false}` is accepted; each gives
      a value class.
- [x] Add options test (Verification 5): `:load-ns true` gives a
      value class.
- [x] Add redefinition test (Verification 6): a second form with the
      same name gives a new value class and `->Name` makes instances
      of the new class.
- [x] Run the tests and confirm they pass.

## Slice 4: error tests

- [x] Add mutable-field tests (Verification 7): one field with
      `:volatile-mutable true`, and two fields with
      `:unsynchronized-mutable true`: `:mutable-field` with correct
      `:class-name` and `:fields`; `macroexpand-1` inside a `LOADER`
      binding does not change `LOADER`.
- [x] Add reserved-field test: `eval` of a form with `__hash` and no
      mutable field throws `CompilerException` with `ex-cause` an
      `AssertionError`.
- [x] Add `:not-installed` test: start a raw `Thread` (as
      `no-binding-test` in `value-type.impl.loader-test`) whose body
      binds only `*ns*` to the test namespace and delivers
      `(macroexpand form)` (or the caught `Throwable`) to a
      `promise`; `deref` the promise with a timeout; `eval` the
      expansion on the test thread with `*ns*` bound to the test
      namespace; assert it throws `:not-installed`. Do not use
      `future`, `bound-fn` or `binding-conveyor-fn`: they convey the
      `LOADER` binding, and the test then gets `:not-value-class`.
- [x] Add `:not-value-class` test: `eval` of
      `(eval (macroexpand form))` throws `:not-value-class`.
- [x] Run the tests and confirm they pass.

## Slice 5: AOT new-JVM test

- [x] Add the AOT compile part (Verification 8): reuse the
      `aot-compile` source-string recipe from
      `value-type.impl.loader-test`; source is an
      `(ns <fixture-ns> (:require value-type.core))` form plus a
      `defvaluetype` form; output to a temporary directory.
- [x] Add the new-JVM part: run
      `<java.home>/bin/java --enable-preview -cp <temp-dir><path.separator><java.class.path> clojure.main -e <form>`
      where `<form>` requires the fixture namespace and prints with
      `prn` a vector of the class `isValue` result and a field value
      of a `->Name` instance; assert exit 0 and last stdout line
      reads as `[true <field value>]`; wait with a timeout; include
      stderr in the failure message; delete the temporary directory.
- [x] Run the test and confirm it passes. If `java.class.path` is
      incomplete under `clojure -M:test` or nREPL, build the
      classpath another way and note it in implementation.md.

## Slice 6: quality gates

- [x] Run the full suite on the Valhalla JDK:
      `JAVA_CMD=<valhalla java> clojure -M:test -m scry.cli`; exit 0
      (acceptance 1).
- [x] REPL run of `value-type.core-test` with `scry.core/run`:
      `:pass? true` and `[:summary :var-count]` equal to the `deftest`
      count, more than 0 (acceptance 2).
- [x] Confirm no reflection warnings on the Valhalla JDK
      (acceptance 3): in a new JVM, run
      `JAVA_CMD=<valhalla java> clojure -M:test -e "(require 'value-type.core-test)" 2>/tmp/vt-err.txt`
      (this also loads `value-type.core`); exit 0 and `/tmp/vt-err.txt` has
      no `Reflection warning` line. Do not use a `require` in a REPL
      that already loaded the namespaces.
- [x] Review `value-type.core` for the boundary invariant: no
      `Compiler/LOADER`, `DynamicClassLoader`,
      `Compiler/writeClassFile`, `java.lang.classfile`
      (acceptance 4).

## Slice 7: documentation

- [x] README: add "Usage" (local `:deps` entry
      `org.hugoduncan/value-type {:local/root "<value-type-dir>"}`,
      JDK requirement, `--enable-preview`, a `deftype` →
      `defvaluetype` example) and "Differences from `deftype`"
      (the `qualities.md` list, no mutable fields, the errors, AOT
      output tied to one JDK feature release).
- [x] Update `ramora/defvaluetype-design/decisions.md`: D1 expansion
      shape, D8 mutable fields only with reason, D9 name and two
      runtime keys and JDK 25 case.
- [x] Update `ramora/defvaluetype-design/embodiment.md`: public
      interface (`check-value-class`), error keys (remove
      `:reserved-field`), data flow, `core` and `transform` boundary
      text per the design "Documentation" list.
- [x] Update `ramora/defvaluetype-design/qualities.md`: Correctness,
      Error clarity and Changeability fitness conditions per the
      design "Documentation" list.
- [x] Update `ramora/defvaluetype-design/purpose.md` "Behaviour",
      "Failure" with the Correctness rule.
- [x] Update `ramora/defvaluetype-design/assertions.md`: A-11, A-16,
      A-20, A-23 to `observed` with source and tests as basis.
- [x] Update `ramora/defvaluetype-design.md` "Status": the macro is
      implemented.
- [x] Update `mementum/state.md`: the macro is done; next steps are
      the adjacent tasks in design.md "Out of scope".
- [x] Verify docstrings of `defvaluetype` and `check-value-class`
      match the contracts (acceptance 6).

## Slice 8: manual JDK 25 run

- [x] On JDK 25 with `--enable-preview`: in a new JVM, run
      `JAVA_CMD=<jdk 25 java> clojure -M:test -e "(require 'value-type.core)" 2>/tmp/vt-err.txt`
      (the `:test` alias adds `--enable-preview`); confirm exit 0 and
      no `Reflection warning` line in `/tmp/vt-err.txt`; `eval` a `defvaluetype` form
      and confirm it throws `:not-value-class` with a message naming
      the missing value-class support; record both results in
      implementation.md (acceptance 3 JDK 25 part, acceptance 5).

## Implementation review

- [x] Extract the test helpers that `core-test` copies from
      `loader-test` into one test support namespace (for example
      `test/value_type/test_support.clj`): `value?`, `eval-here` (with
      the namespace as an argument), `temp-dir` / `delete-tree` /
      `with-temp-dir`, and `aot-compile` (source string argument; the
      `loader-test` caller builds its `ns` + form string). Make
      `loader-value-type` (`core-test`) and `vt-type` (`loader-test`)
      one fixture macro if possible. Use the support namespace from both
      test namespaces.
- [x] `aot-new-jvm-test`: read the last stdout line with
      `clojure.edn/read-string` and compare to `[true 42]`, as
      Verification 8 states. `(clojure.edn/read-string "")` gives
      `nil`, so empty output does not throw; the reason in
      implementation.md (pass 3) is not correct.
- [x] `mutable-field-test` "LOADER is not changed": assert that the
      `macroexpand-1` call inside the `LOADER` binding gives
      `:mutable-field` (now the `error-data` result is discarded, so an
      other early error also passes the test).
- [x] `defvaluetype` docstring, README "Differences from `deftype`"
      `qualities.md` and `purpose.md`: "`identical?` and `==` compare field values"
      and "compares by bit pattern in `==`" use the Java `==` (`acmp`).
      Clojure `clojure.core/==` is numeric and throws on these objects.
      Name the Java operator (or `identical?` only) so that a Clojure
      user does not read `clojure.core/==`.
- [x] `mementum/state.md`: remove "manual JDK 25 run pending" (Slice 8
      is done), and change the scaffold item
      "`src/value_type/core.clj` (docstring only)", which is no longer
      true.

## Implementation review, pass 2

- [x] `core-test`: the eval'd fixtures use fixed class names
      (`LoaderVT`, `EvalVT`, `LoadVT`, `DefnVT`, `Redef`, `NoInstall`,
      `MacroExpanded`, `M1`–`M3`, `Reserved`). Design "Verification"
      and `mementum/knowledge/testing-clojure-generated-classes.md`
      ("Static cache") require a unique name (`gensym`) for each
      fixture. Use `gensym` names (one name for both forms of the
      redefinition test); compute the expected `:class-name` with
      `support/binary-name`.

## Implementation review, pass 3

- [x] `core-test` `aot-new-jvm-test`: the fixture class has the fixed
      name `Pt` (`value_type.aot_fixture.Pt`). `support/aot-compile`
      also defines the class in the test JVM, so it goes in the
      `DynamicClassLoader` static cache. Design "Verification" and
      `mementum/knowledge/testing-clojure-generated-classes.md`
      ("Static cache") require a `gensym` name for each fixture, as
      `aot-write-test` in `loader-test` does. Use a `gensym` name in
      the source string and in the new-JVM `-e` form.

## Test review

- [x] `core-test`: Verification 1 "the result is the class" has no
      assertion. `basic-test` checks the top-level `Point` class, and
      `compile-path-test` checks only that the result is a value
      class. Assert that the result of `eval` of a `defvaluetype` form
      is `identical?` to the class of that name (for example
      `(ns-resolve test-ns n)`).
- [x] `core-test`: no test checks the "Message names" column of the
      design "Error contract". Assert with substrings, not full text:
      the `:mutable-field` message names the class and each mutable
      field; the `:not-installed` and `:not-value-class` messages name
      the class and the fix "evaluate the defvaluetype form directly".
      Keep the key assertions as they are.

## Test review, pass 3

- [x] `core-test` `mutable-field-test`: each case evaluates two forms
      with the same class name (fields `b` for `ex-data`, `mut-b` for
      the message). Use one form per case (for example fields `a`
      `mut-b`, and `mut-a` `mut-b`) and one `error-info` result.
      Assert `ex-data` (`:fields '[mut-b]`) and the message on it, as
      `not-installed-test` and `not-value-class-test` do.
- [x] `core-test`: `message-names?` and `the-class?` failures show
      only `false`. Give each `message-names?` assertion the
      `ex-message` as the `is` message. Split `the-class?` into two
      assertions (`value?`, and `identical?` to the resolved class),
      so that a failure names the broken part of the contract.

## Docs review

- [x] `qualities.md` "Semantic differences from `deftype`" and the
      `defvaluetype` docstring omit "fields that hold references
      compare by reference" (README and `purpose.md` have it). Checked
      on the Valhalla JDK: `(defvaluetype P [v])`,
      `(= (->P [1]) (->P (vector 1)))` gives `false`. The design says
      the README and docstring follow the `qualities.md` list. Add the
      item to `qualities.md` and to the docstring.
- [x] README "Differences from `deftype`", errors: the text says only
      "Errors are `ex-info`". The caller gets `:mutable-field` (and
      `:not-installed` / `:not-value-class` through `load`, `require`,
      `load-string`) as the cause of a `CompilerException` (design
      "Error contract", "How the caller gets the error"). State this,
      and that `ex-cause` gives the `ex-info`, so that a
      `(catch ExceptionInfo ...)` user is not surprised.

## Docs review, pass 2

- [x] `mementum/state.md` "Status": "The design is in
      `ramora/defvaluetype-design.md` (status: design)" is stale. The
      "Status" of that document now says that the transform, the
      loader and the macro are implemented and tested. Change the
      parenthesis to agree (or remove it).
- [x] `ramora/defvaluetype-design/assertions.md` A-01
      (`defvaluetype` supports make a JEP 401 value class from
      `deftype` syntax) is still `intended` with basis README and
      Purpose. The macro and `value-type.core-test` now show it.
      Change it to `observed` with `src/value_type/core.clj` and
      `value-type.core-test` as basis, as A-11.

## Docs review, pass 3

- [x] "Fields that hold references compare by reference" (README
      "Differences from `deftype`", `defvaluetype` docstring,
      `qualities.md`, `purpose.md`) is not correct for a field that
      holds a value object. `identical?` (`acmp`) compares such a
      field by its field values. Checked on the Valhalla JDK with
      `(defvaluetype P [v])`: `(= (->P (Long/valueOf 100000))
      (->P (Long/valueOf 100000)))` and `(= (->P (->Q 1)) (->P (->Q 1)))`
      (`Q` a `defvaluetype`) give `true`; `(->P [1])` vs
      `(->P (vector 1))` gives `false`. State: a field that holds an
      identity object compares by reference; a field that holds a
      value object (for example a boxed `Long` under
      `--enable-preview`, or a `defvaluetype` instance) compares by
      its field values.

## Docs review, pass 4

- [x] `ramora/defvaluetype-design/purpose.md` "Behaviour" (runtime
      semantics list) omits the `double` item ("a `double` field
      compares by bit pattern in `identical?`: `NaN` equals `NaN`,
      `0.0` does not equal `-0.0`"). README, the `defvaluetype`
      docstring and `qualities.md` have it. Add it, so that the four
      lists agree.
- [x] README "Differences from `deftype`", errors: `:not-installed`
      gives the fix, but `:not-value-class` gives only the causes.
      Add the fix for each cause, as the design "Error contract" and
      the message state: `(eval (macroexpand form))` → evaluate the
      `defvaluetype` form directly; a nested `defvaluetype` → move the
      inner form to the top level; no value classes → use a JEP 401
      JDK with `--enable-preview`.

## Docs review, pass 5

- [x] README "Differences from `deftype`", "How you get the error":
      the text names only `:mutable-field`, `:not-installed` and
      `:not-value-class`. `:jvm-rejected` (and
      `:unexpected-constructor`) occur in analysis, so they are also
      the cause of a `CompilerException`. Checked on the Valhalla JDK
      with no `--enable-preview`: `eval` of `(defvaluetype P [^long x])`
      gives the chain `(CompilerException ExceptionInfo
      UnsupportedClassVersionError)`, `ex-data`
      `{:value-type/error :jvm-rejected, :class-name "user.P"}`. State
      it, so that the `(catch ExceptionInfo ...)` note covers all keys.
- [x] `ramora/defvaluetype-design/decisions.md` D9: `:not-installed`
      gives the fix, but `:not-value-class` gives only the causes.
      Add the fix for each cause, as the design "Error contract", the
      message and the README state: `(eval (macroexpand form))` →
      evaluate the `defvaluetype` form directly; a nested
      `defvaluetype` → move the inner form out of the method body to
      the top level.

## Code shape review

- [x] `value-type.core` `defvaluetype`: the macro body does field
      validation (message text and `throw`), the loader install and
      the expansion. The runtime errors have their message in
      `error-message` and the check in `check-value-class`, but the
      `:mutable-field` message and check are inline. Move the check to
      a private function (for example `check-fields [class-name
      fields]`, `nil` or throw), so that the macro body is only
      install and expansion. `mutable-fields` already gives a vector:
      use it directly, not `(vec ms)` two times on its `seq`.
- [x] `core-test` `reserved-field-test`: it repeats the cause-chain
      idiom of `error-info` with its own `try`. `loader-test` uses
      `support/compile-cause` for the same check (`eval-cause`). Use
      `(support/compile-cause #(eval-here ...))` and assert that the
      cause is an `AssertionError`.
- [x] `core-test`: classes are written fully qualified in the code
      (`clojure.lang.RT`, `clojure.lang.Compiler/LOADER`,
      `clojure.lang.Compiler$CompilerException`,
      `java.util.concurrent.TimeUnit`, `java.util.List`), but
      `ExceptionInfo` is imported. Import them in the `ns` form, as
      `loader-test` does for `Compiler`.

## Code shape review, pass 2

- [x] `core-test` `not-installed-test` and `loader-test`
      `no-binding-test` each write the same raw-`Thread` recipe
      (`Thread`, `promise`, `deliver` the result or the `Throwable`,
      `deref` with a timeout), with different details (`^Runnable`
      hint, timeout 10000 vs 1000, local `thread` vs `t`). Move it to
      one `test-support` function (for example `on-raw-thread [f]`:
      call `f` on a new `Thread` with no bindings; return the result,
      the `Throwable`, or `::timeout`). Use it from both tests.
- [x] `value-type.core` `defvaluetype`: the "validate only what
      `deftype` accepts" rule is split. The macro has the
      `(vector? fields)` guard and `mutable-fields` has the `symbol?`
      guard. Move the `vector?` guard into `check-fields` (or
      `mutable-fields`), so that the macro body is only the check
      call, the install and the expansion.
- [x] Test namespaces use two aliases for `clojure.string`: `str`
      (`loader-test`) and `string` (`core-test`). Use one alias in
      both.

## Code shape review, pass 3

- [x] `value-type.core` `check-value-class`: the body computes the
      value-class predicate (`@is-value-method`, `.invoke`, the `and`
      nil guard) and does the flow control (key choice and `throw`).
      Move the predicate to a private `value-class?` (`[^Class cls]`,
      boolean; false when `isValue` is missing), so that
      `check-value-class` is only `(when-not (value-class? cls) ...)`
      and the throw.
- [x] `value-type.core` `defvaluetype`: `(boolean (loader/install!
      class-name))` coerces a value that is already a boolean
      (`install!` docstring and code: `true` or `false`). Remove the
      `boolean` call.

## Code shape review, pass 4

- [x] `transform-test` keeps a private `is-value?`
      (`test/value_type/impl/transform_test.clj:307`). It is the same
      predicate as `support/value?` (`Reflector` call of `isValue`,
      `boolean`). `core-test` and `loader-test` use `support/value?`.
      Remove `is-value?` and use `support/value?`, so that the test
      namespaces have one value-class predicate.
