# 004 defvaluetype macro

## Intent

Make the public macro `defvaluetype`. A programmer changes `deftype`
to `defvaluetype` and gets a JEP 401 value class. The macro rejects
mutable fields before expansion. It never gives an identity class
silently: when the class is not a value class, the form throws.

This is decisions D1, D8 and the D9 runtime check in
`ramora/defvaluetype-design`. The transform (D5, D6, task
`002-value-class-transform`) and the loader (D3, D4, D7, D9 define
errors, task `003-capture-loader`) are done. This task connects them
to the user.

When a choice is not clear, choose the smaller macro. Keep the
`deftype` behaviour. Never give an identity class silently.

## Problem

`value-type.core` has only a namespace docstring. A user cannot make a
value class. `loader/install!` works only when a macro calls it at
macroexpansion time. Some paths skip or defeat the install (no
`LOADER` binding, `(eval (macroexpand form))`, a JDK without value
classes). On these paths the class is an identity class and nothing
tells the user. The spike macro (`spike/valhalla/src/spike/vt.clj`)
shows the shape. It has no validation, no clear errors and no tests.

## Context

- Design: `ramora/defvaluetype-design/decisions.md` (D1, D8, D9, D10,
  D11), `embodiment.md` (`value-type.core`, public interface, error
  keys, data flow), `qualities.md` (semantic differences from
  `deftype`), `purpose.md` (capabilities, success criteria).
- Knowledge: `mementum/memories/clojure-compiler-loader-hook.md`,
  `mementum/knowledge/testing-clojure-generated-classes.md`,
  `mementum/memories/scry-pass-true-with-zero-tests.md`,
  `mementum/memories/valhalla-jdk-selection-java-cmd.md`.
- Call pattern: the test macro `vt-type` in
  `value-type.impl.loader-test`, and `defvaluetype` in
  `spike/valhalla/src/spike/vt.clj`.
- Facts from the Clojure 1.12.6 source (`core_deftype.clj`):
  - `deftype` computes the class name as
    `(str (namespace-munge *ns*) "." name)`. The type name is not
    munged (`Foo-Bar` gives the binary name `ns.Foo-Bar`).
  - `deftype` calls `validate-fields` first. It throws
    `AssertionError` when `fields` is not a vector, or when a field
    name is in `#{__meta __hash __hasheq __extmap}`. After these
    checks, it throws a `CompilerException` that wraps an
    `AssertionError` when an element of `fields` is not a symbol.
  - The arglists of `deftype` are `([name [& fields] & opts+specs])`.
  - `eval` of a `deftype` form with a reserved field name (also inside
    a `let` from a macro) throws a `CompilerException`. Its direct
    cause (`ex-cause`) is the `AssertionError` of `validate-fields`.
- Facts from the Clojure 1.12 compiler (`Compiler.java`): a field is
  mutable when its metadata has a truthy `:volatile-mutable` or
  `:unsynchronized-mutable` value (`RT.booleanCast`). A mutable field
  is not final. So `^{:volatile-mutable false}` gives a final field.
- Facts checked for this design on JDK `27-jep401ea3`, Clojure 1.12.6,
  with a `vt-type`-like macro:
  - A hyphenated name (`Foo-Bar`), zero fields, fields hinted `long`,
    `double`, `String` and `objects`, a field with
    `{:volatile-mutable false}`, and `:load-ns true` all give a value
    class.
  - Two instances of a zero-field value class are `=` and
    `identical?`.
  - A macro call inside a `defn` body installs in the binding of the
    `defn` top-level form. The class is a value class.
  - `macroexpand` on a new `Thread` (no `LOADER` binding), then `eval`
    of the expansion: `install!` gives `false`, and the class is an
    identity class.
  - `(eval '(eval (macroexpand form)))`: `install!` gives `true`, and
    the class is an identity class.
  - An `ex-info` thrown by a macro reaches the caller of `eval` and of
    `macroexpand-1` as the cause of a `CompilerException`.
  - An `ex-info` thrown at run time of an `eval` form reaches the
    caller of `eval` directly (no wrap). Through `load-string`
    (`Compiler.load`), it is the cause of a `CompilerException`.
- On JDK 25 with `--enable-preview`, the transformed class defines as
  an identity class (`ramora/defvaluetype-design/verification.md`).
  JDK 25 has no `Class::isValue`.
- Test run: `clojure -M:test -m scry.cli` with `JAVA_CMD` set to the
  Valhalla `java`. A REPL run must also check `[:summary :var-count]`.

## Scope

In scope:

1. The macro `value-type.core/defvaluetype` (see "Macro contract").
2. The public function `value-type.core/check-value-class`, the D9
   runtime check (see "Runtime check contract").
3. A test namespace `value-type.core-test` (see "Verification").
4. README: a "Usage" section and a "Differences from `deftype`"
   section (see "Documentation").
5. Updates to the design documents, `mementum/state.md` and
   `munera/plan.md` (see "Documentation").

## Out of scope

- Changes to `value-type.impl.loader` and
  `value-type.impl.transform`.
- A `defvaluetype` inside a method body of another `defvaluetype`.
  The inner install replaces the outer capture loader, so the outer
  class is an identity class. The runtime check of the outer form
  throws `:not-value-class`. A fix needs a loader change. This is an
  adjacent task.
- A run of the `deftype` test forms of the Clojure test suite (the
  success criterion "every `deftype` test form without mutable fields
  compiles as a value class"). Adjacent task.
- A benchmark of method calls (the performance success criterion).
  Adjacent task.
- A public predicate `value-class?` for users.
- A fallback to `deftype` (D10).
- Records, abstract value classes, null-restricted fields, `reify`.
- A test on JDK 25 in the test suite. The suite needs the Valhalla
  JDK. One manual run on JDK 25 is in scope. It checks acceptance
  criteria 3 (the JDK 25 part) and 5, and records both results in
  `implementation.md`.

## Decisions

### Expansion shape (D1)

- Choice: `defvaluetype` returns

  ```clojure
  (let []
    (clojure.core/deftype name fields & opts+specs)
    (value-type.core/check-value-class <class-symbol> <installed>)
    <class-symbol>)
  ```

  - `name`, `fields` and `opts+specs` are the macro arguments, with no
    change (the same objects, so the metadata stays).
  - `<class-symbol>` is the fully qualified class symbol. The `import`
    of `deftype` has not run when the compiler analyzes the `let`.
  - `<installed>` is the boolean result of `loader/install!`, as a
    literal.
- Rationale: D1 is firm. `deftype` keeps the option parsing, the
  factory, the import, and its own errors. The result of the form is
  the class, as for `deftype`.

### Class name rule

- Choice: the binary name is `(str (namespace-munge *ns*) "." name)`
  at macroexpansion time. This is the rule of `deftype`.
- Rationale: the capture loader changes only the class whose name
  equals the install name exactly. Only the rule of `deftype` gives
  this name for each name that `deftype` accepts.

### Order at macroexpansion

1. Validate the fields (D8). On error, throw. No install.
2. `loader/install!` with the binary name.
3. Return the expansion.

Rationale: a rejected form must not change `LOADER`. When `deftype`
rejects the form after the install (for example a reserved field
name), the capture loader stays in the binding with no effect. The
loader changes only its target name, so this is harmless.

### Validation: mutable fields only (D8)

- Choice: when `fields` is a vector, each element that is a symbol
  with a truthy `:volatile-mutable` or `:unsynchronized-mutable`
  metadata value is a mutable field. When there is one or more,
  throw `ex-info` `:mutable-field` (see "Error contract").
- The macro checks only the symbol elements of `fields`. It ignores
  the other elements; `deftype` rejects them later. So
  `[^:volatile-mutable a 1]` gives `:mutable-field`.
- The truthy rule is the rule of the compiler. So a field with
  `{:volatile-mutable false}` is accepted, as the compiler makes it
  final.
- When `fields` is not a vector, the macro does not validate.
  `deftype` gives its own error.
- Rationale: a mutable field cannot be a value class field. Without
  this check the JVM rejects the class with a `ClassFormatError`
  (`:jvm-rejected`), which does not name the field.

### No separate reserved-field check (changes D8)

- Choice: `defvaluetype` does not check the reserved field names.
  `deftype` already rejects them with `AssertionError` at its
  macroexpansion, before any class is defined.
- Rationale: the reserved names are a `deftype` rule, not a
  value-class rule. The same error as `deftype` is the most compatible
  behaviour. No identity class is made, so the D9 priority stays true.
  The check would duplicate Clojure code.
- Effect: remove the reserved-name item from D8, and
  `:reserved-field` from the error keys in `embodiment.md`.
- Rejected: a `:reserved-field` `ex-info` (current D8). It adds code
  and a second error for one rule.

### Runtime check (D9)

- Choice: a public function `check-value-class` in `value-type.core`.
  The expansion calls it. It throws when the class is not a value
  class. It has two error keys, so that the message can name the
  cause:
  - `:not-installed`: `install!` returned `false`. The form was
    macroexpanded with no `LOADER` binding (for example by a tool on
    a different thread), then evaluated.
  - `:not-value-class`: `install!` returned `true`, but the class is
    an identity class. Causes: the JVM has no value classes, or the
    capture loader did not catch the class. Known causes of the
    second: the expansion was evaluated in a different compilation
    unit (for example `(eval (macroexpand form))`), and a nested
    `defvaluetype` (see "Out of scope").
  - On a JVM with no value classes, each key can occur (the
    `installed` value selects it). No change of the compile path
    gives a value class there.
- `check-value-class` puts the value-class predicate in `core`: the
  check for a value class (`Class::isValue`, which can change in each
  early-access build, risk assumption 5), and the check for a JVM with
  no `Class::isValue`. No other namespace in `src` knows the
  predicate.
- The check must work on a JVM with no `Class::isValue` (JDK 25). There
  the class is not a value class. The check must not use reflection
  that warns.
- Rationale: some paths skip the capture loader. The check is the only
  guard on these paths. A public function keeps the expansion small.
  A private var called through `#'` is also possible, but it is
  unusual in an expansion.
- Rejected: throw `:not-installed` at macroexpansion. Tools call
  `macroexpand` with no `LOADER` binding only to show the expansion.
  The error must come only when the class is defined.

## Macro contract

```clojure
(defvaluetype name [fields*] options* specs*)
```

- Arglists: `([name [& fields] & opts+specs])`, the same as `deftype`.
- Syntax, options (`:load-ns`), specs, factory `->name`, import and
  result (the class): the same as `deftype`.
- Result class: a final value class with final fields.
- Docstring: states that the syntax is the syntax of `deftype` (it
  refers to the `deftype` docstring), the JDK requirement (JEP 401,
  `--enable-preview`), the field restriction (no mutable fields), the
  semantic differences from `deftype` (see "Documentation"), and the
  error keys.

## Runtime check contract

```clojure
(value-type.core/check-value-class cls installed) ;=> nil
```

- Input: `cls`, a `Class`; `installed`, a boolean.
- Output: `nil` when `cls` is a value class.
- Else it throws `ex-info` with the key `:not-installed` when
  `installed` is false, and `:not-value-class` when `installed` is
  true (see "Error contract").
- Docstring: states that the expansion of `defvaluetype` calls it.

## Error contract

Each error of `value-type` (the keys below) is an `ex-info` with
`:value-type/error` and `:class-name`, the binary name of the target
class (a string).

| Key | When | Extra data | Message names |
|---|---|---|---|
| `:mutable-field` | macroexpansion (D8) | `:fields`: the mutable field symbols, in field order | the fields; that value class fields are final; the fix (remove the mutable metadata) |
| `:not-installed` | run time (D9) | — | the class; on a JVM with no `Class::isValue`, see below; else that the form was macroexpanded outside compilation, and the fix (evaluate the `defvaluetype` form directly) |
| `:not-value-class` | run time (D9) | — | the class; on a JVM with no `Class::isValue`, see below; else that the capture loader did not catch the class, with the known causes as examples, and the fix for each cause (the expansion was evaluated apart from its macroexpansion: evaluate the `defvaluetype` form directly; a nested `defvaluetype`: move the inner `defvaluetype` form out of the method body to the top level) |
| `:jvm-rejected`, `:unexpected-constructor` | analysis (loader) | as the loader gives | as the loader gives |

On a JVM with no `Class::isValue`, the `:not-installed` and
`:not-value-class` messages name only one cause: the JVM has no value
classes (a JEP 401 JDK is necessary). They give no other cause and no
fix of the compile path.

How the caller gets the error:

- `:mutable-field`: the cause of a `CompilerException` (macroexpansion
  error).
- `:not-installed`, `:not-value-class`: from `eval`, the `ex-info`
  directly; from `load`, `require` and `load-string`, the cause of a
  `CompilerException`.
- The loader errors pass through `value-type.core` with no change.
- A reserved field name: from `eval`, the cause of a
  `CompilerException` is the `AssertionError` of `deftype`.

A fields form that is not a vector gives the error of `deftype`.
When `fields` is a vector with no mutable field, a reserved field
name, a field that is not a symbol, and other `deftype` syntax errors
give the error of `deftype`. When `fields` has a mutable field, the
form gives `:mutable-field` first (see "Order at macroexpansion").

## Invariants

- Each `defvaluetype` form that returns gives a value class. Else it
  throws.
- A rejected form (`:mutable-field`) does not call `install!`.
- The expansion contains the `deftype` form with the macro arguments
  unchanged.
- The install name equals the binary name that `deftype` gives.
- `value-type.core` does not refer to `Compiler/LOADER`,
  `DynamicClassLoader`, `Compiler/writeClassFile` or
  `java.lang.classfile` (embodiment boundary rules). It uses only
  `loader/install!`.
- `value-type.core` loads with no reflection warnings on the Valhalla
  JDK and on JDK 25. `value-type.core-test` loads with no reflection
  warnings on the Valhalla JDK.

## Documentation

- README:
  - "Usage": the dependency, the JDK requirement, `--enable-preview`,
    an example that changes `deftype` to `defvaluetype`. The project
    has no release and no git remote. So the dependency is a
    `deps.edn` `:deps` entry
    `org.hugoduncan/value-type {:local/root "<value-type-dir>"}`.
    `<value-type-dir>` is a placeholder for the directory of a
    checkout of this repository.
  - "Differences from `deftype`": the list in
    `ramora/defvaluetype-design/qualities.md` ("Semantic differences
    from `deftype`"), no mutable fields, the errors, and that AOT
    output is tied to one JDK feature release.
- `ramora/defvaluetype-design/decisions.md`: D1 (the expansion in
  "Expansion shape (D1)": the two-argument `check-value-class`, the
  fully qualified class symbol, the `<installed>` literal), D8
  (mutable fields only, with the reason), D9 (the name
  `check-value-class`, the two runtime keys, the JDK 25 case).
- `ramora/defvaluetype-design/embodiment.md`: the public interface
  (`check-value-class`), the error key set (remove `:reserved-field`),
  the data flow (expansion with the two-argument `check-value-class`),
  the `core` boundary in "Boundaries" (`core` knows the `deftype`
  syntax and the value-class predicate, see "Runtime check (D9)"),
  the `transform` boundary in "Boundaries" (a change of the class
  file format in a Valhalla build, that is the flag values or the
  version rule, changes only `transform`; a change of the value-class
  predicate changes only `core`).
- `ramora/defvaluetype-design/qualities.md`:
  - Correctness and Error clarity fitness conditions: each failure
    with an error key of `value-type` gives `ex-info` with
    `:value-type/error`. A `deftype` syntax error gives the `deftype`
    error.
  - Changeability fitness condition: the flag values and the version
    rule are only in `transform`; the value-class predicate is only in
    `core`.
- `ramora/defvaluetype-design/purpose.md`, "Behaviour", "Failure": the
  rule of the Correctness fitness condition above.
- `ramora/defvaluetype-design/assertions.md`: A-11, A-16, A-20 and
  A-23 to `observed`, with the source and tests as basis.
- `ramora/defvaluetype-design.md`, "Status": the macro is implemented.
- `mementum/state.md`: the macro is done; the next step (the adjacent
  tasks in "Out of scope").
- `munera/plan.md`: remove the entry of this task when the task moves
  to `munera/closed/`. No other change.

## Verification

The test namespace `value-type.core-test` must cover these items. The
fixtures use unique class names (`gensym`) and bind `*ns*` to the test
namespace (`mementum/knowledge/testing-clojure-generated-classes.md`).

1. Basic. A `defvaluetype` form with `long` and `Object` fields:
   - the result is the class, and the class is a value class;
   - `->Name` and the imported constructor make instances with the
     field values;
   - two instances with the same field values are `=` and
     `identical?`; instances with different values are not `=`.
2. Compile paths: `eval` of one form, `load-string` of a source
   string, and a call inside a `defn` body. Each gives a value class.
3. Specs:
   - an inline protocol method and an interface method give the
     expected results;
   - `Object` overrides (`toString`, `equals`, `hashCode`) are used;
   - a method that returns an inner `fn` that captures `this`: the
     `fn` returns an instance `identical?` to the receiver.
4. Fields: zero fields; fields hinted `long`, `double` and a class
   type have the `deftype` field types (`long`, `double`, `Object`:
   `deftype` keeps only primitive hints as field types); a field with
   `{:volatile-mutable false}` is accepted. Each is a value class.
5. Options: `:load-ns true` gives a value class.
6. Redefinition: a second form with the same name gives a new value
   class, and `->Name` makes instances of the new class.
7. Errors:
   - a field with `:volatile-mutable true`, and a form with two fields
     with `:unsynchronized-mutable true`: `:mutable-field`, the
     correct `:class-name` and `:fields`; `LOADER` is not changed
     (`macroexpand-1` inside a `LOADER` binding);
   - a reserved field name (`__hash`) with no mutable field: `eval`
     throws a `CompilerException`, and its `ex-cause` is an
     `AssertionError` (the error of `deftype`);
   - `macroexpand` on a new `Thread`, then `eval` of the expansion:
     `:not-installed`;
   - `eval` of `(eval (macroexpand form))`: `:not-value-class`;
   - `check-value-class` on an identity class (a `deftype` class):
     `:not-value-class` when `installed` is true, `:not-installed`
     when it is false; on a value class: `nil`.
8. AOT in a new JVM:
   - Compile: the `Compiler/compile` source-string recipe in
     `mementum/knowledge/testing-clojure-generated-classes.md` ("AOT
     without a new JVM"), as `aot-compile` in
     `value-type.impl.loader-test`. The source string is an
     `(ns <fixture-ns> (:require value-type.core))` form and a
     `defvaluetype` form. The output goes to a temporary directory.
     `value-type.core` is already loaded, so the compile writes only
     the classes of the fixture namespace.
   - New JVM: the command
     `<java.home>/bin/java --enable-preview -cp <temp-dir><path.separator><java.class.path> clojure.main -e <form>`.
     `java.home`, `path.separator` and `java.class.path` are the
     system properties of the test JVM. The current classpath gives
     Clojure and `src`, so the new JVM loads `value-type.core` from
     source. The fixture namespace has no source file.
   - `<form>` requires the fixture namespace (the load runs the
     runtime check). Then it prints with `prn` a vector: the
     `isValue` result of the class, and a field value of an instance
     that `->Name` makes.
   - Pass: the exit code is 0, and the last line of standard output,
     read with `clojure.edn/read-string`, is `[true <field value>]`.
     The test waits for the process with a timeout. On failure, the
     test message contains the standard error output.
   - The test deletes the temporary directory.

## Acceptance criteria

1. The tests in "Verification" exist and pass on the Valhalla JDK with
   `clojure -M:test -m scry.cli` (exit 0). The full suite passes.
2. A REPL run of `value-type.core-test` with `scry.core/run` gives
   `:pass? true` and a `[:summary :var-count]` equal to the count of
   `deftest` vars in the namespace. This count is more than 0.
3. Loading `value-type.core` and `value-type.core-test` gives no
   reflection warnings on the Valhalla JDK. Loading `value-type.core`
   gives no reflection warnings on JDK 25. The JDK 25 result is
   recorded in `implementation.md`.
4. `value-type.core` has no reference to `Compiler/LOADER`,
   `DynamicClassLoader`, `Compiler/writeClassFile` or
   `java.lang.classfile` (checked by review).
5. On JDK 25 with `--enable-preview`, `eval` of a `defvaluetype` form
   throws `:not-value-class` with a message that names the missing
   value class support. The result is recorded in
   `implementation.md`.
6. `defvaluetype` and `check-value-class` have docstrings as in
   "Macro contract" and "Runtime check contract".
7. The documents in "Documentation" agree with this design.
