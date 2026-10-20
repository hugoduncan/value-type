# 005 deftype compatibility tests

## Intent

Show that `defvaluetype` is compatible with `deftype` on the forms
that Clojure itself uses to test `deftype`. Take each `deftype` form
from the Clojure 1.12.6 test suite that a value class can express.
Change `deftype` to `defvaluetype`. Keep the Clojure assertions. Each
class must be a value class, and each assertion must pass.

This checks the success criterion in
`ramora/defvaluetype-design/purpose.md`: "Every `deftype` test form
without mutable fields compiles as a value class." Compatibility is
priority 2 of the design.

When a choice is not clear, keep the Clojure form and assertion with
the smallest change.

## Problem

`value-type.core-test` (task 004) tests forms that the project wrote.
These forms cover the cases that the design knows. They do not show
that `defvaluetype` keeps the `deftype` behaviour that Clojure itself
relies on: marker protocols, `extend` on the type, reader constructor
literals, field and method name ambiguity, embedded instances in code
(CLJ-1399), protocol dispatch with class hash collisions, `:load-ns`
in a new JVM (CLJ-1208), a type defined by `eval` in an AOT compiled
file (CLJ-979), annotations, and the `deftype` errors. No test checks
the success criterion.

## Context

- Design: `ramora/defvaluetype-design/purpose.md` (success criteria,
  priorities), `qualities.md` (compatibility target and fitness
  condition), `assertions.md`.
- Test support: `value-type.test-support` (`aot-compile`,
  `with-temp-dir`, `value?`, `binary-name`) and the new-JVM run in
  `value-type.core-test/aot-new-jvm-test` (private `run-java`).
- Knowledge: `mementum/knowledge/testing-clojure-generated-classes.md`,
  `mementum/memories/scry-pass-true-with-zero-tests.md`.
- Source corpus: the Clojure repository at tag `clojure-1.12.6`,
  directory `test/clojure/test_clojure/`. The source files are not in
  the Clojure jar. They are under the Eclipse Public License 1.0, and
  each file has a copyright notice.
- The corpus has 40 `deftype` forms (`grep "(deftype"`):

  | Source file | Forms | Clojure test |
  |---|---|---|
  | `protocols.clj` | `HasMarkers`, `WillGetMarker` | `marker-tests` |
  | `protocols.clj` | `ExtendTestWidget` | `extend-test` |
  | `protocols.clj` | `HasProtocolInline` | `illegal-extending` |
  | `protocols.clj` | `ExtendsTestWidget`, `ExtendersTestWidget`, `SatisfiesTestWidget`, `ReExtendingTestWidget` | tests disabled with `#_` |
  | `protocols.clj` | `Something` (inside the disabled `extenders-test`) | disabled |
  | `protocols.clj` | `TypeToTestFactory` | `deftype-factory-fn`, `test-ctor-literals` |
  | `protocols.clj` | `TypeToTestLiterals` | `exercise-literals` |
  | `protocols.clj` | `TypeToTestLongHint`, `TypeToTestByteHint` | `hinting-test` |
  | `protocols.clj` | `Dashed` | `test-leading-dashes` |
  | `protocols/hash_collisions.clj` | `TestType1` … `TestType15` | `protocols-with-hash-collisions`, `protocols-with-no-min-hash-in-13-bits` |
  | `java_interop.clj` | `T` | `test-reflective-field-name-ambiguous` |
  | `java_interop.clj` | `t` (`:unsynchronized-mutable` field) | `test-set!` |
  | `compilation.clj` | `CLJ1399` | `clj-1399` |
  | `compilation/examples.clj` | `X` (by `eval`), `T` | `CLJ-979` (AOT) |
  | `compilation/load_ns.clj` | `x` (`:load-ns true`) | `clj-1208` (AOT) |
  | `ns_libs.clj` | `StringBuffer` | `naming-types` (error) |
  | `ns_libs.clj` | `MyType [:key1]` | `test-defrecord-deftype-err-msg` (error) |
  | `array_symbols.clj` | `Foo/2` | array class symbol test (error) |
  | `annotations/java_8.clj`, `annotations/java_9.clj` | `Bar` | annotation tests, loaded only on Java 8, 9 and 10 |

- Facts checked for this design on JDK `27-jep401ea3`, Clojure 1.12.6,
  with `defvaluetype` in place of `deftype` in a namespace loaded by
  `require`:
  - Each form in scope (see "Scope") gives a value class, and each
    Clojure assertion passes.
  - `ExtendsTestWidget` (a protocol with no method bodies) is a value
    class. A call of the protocol method throws `AbstractMethodError`,
    as for `deftype`.
  - The error forms give the `deftype` errors: `StringBuffer` gives
    `IllegalStateException` "StringBuffer already refers to"; `[:key1]`
    gives `AssertionError` "defrecord and deftype fields must be
    symbols, value-type.deftype-compat-test.MyType had: :key1" as the
    cause of a `CompilerException` (the message uses `*ns*`, not the
    munged class name); `Foo/2` gives a `CompilerException` with the
    cause `NoClassDefFoundError` "IllegalName:
    compile__stub.value_type.deftype_compat_test.Foo/2". The
    `StringBuffer` form gives its error also in a `deftest` body,
    inside `thrown-with-msg?`, as in Clojure.
  - An adapted `Bar` (annotations `Deprecated`, `Retention` and
    `SupportedOptions` on the type, the field `b` and the method
    `foo`) is a value class. Reflection gives the three annotation
    types on each element, and the `SupportedOptions` value
    `["foo" "bar" "baz"]`.
  - AOT: a namespace with `(eval '(defvaluetype X []))`, a
    `(defvaluetype T [])`, and a `defvaluetype x` with `:load-ns true`
    was compiled. In a new JVM with no source of the namespace on the
    classpath: `X` and `T` are value classes and the CLJ-979
    assertions hold; constructing `x` and calling `f` returns 1, and
    the namespace is loaded (CLJ-1208).
- So no failure is known. The task adds a regression corpus. It does
  not change `src/`.

## Scope

In scope:

1. A test namespace `value-type.deftype-compat-test` with the forms
   and assertions in "Corpus". Each form is at the place that the
   Clojure sources use: at the top level of the namespace, except the
   error forms (see "Errors"). The namespace is loaded by `require`
   (the `Compiler.load` path).
2. The AOT checks CLJ-979 and CLJ-1208 in a new JVM (see
   "Verification").
3. Move the new-JVM run helper from `value-type.core-test` to
   `value-type.test-support`, so that both test namespaces use one
   helper.
4. Documentation updates (see "Documentation").

## Out of scope

- Changes to `src/`. When an assertion fails during implementation,
  record the failure in `implementation.md`, and propose a new task
  for the fix. Do not fix it in this task. Mark the form as excluded:
  replace the form and its assertions in the test namespace with a
  comment at the same place. The comment starts with `Excluded:`,
  names the form, and gives the reason.
- `defrecord`, `reify`, `gen-class` and `definterface` forms of the
  Clojure test suite.
- The `deftype` form `t` with a mutable field. A value class cannot
  express it. `value-type.core-test/mutable-field-test` already tests
  the `:mutable-field` error.
- `Something`: it is inside the body of a disabled test.
- Other Clojure versions.
- A side-by-side comparison of `deftype` and `defvaluetype` results.
  The Clojure assertions already give the expected `deftype`
  behaviour.
- The method-call benchmark.
- A nested `defvaluetype` in a method body (it needs a loader change).

## Corpus

Rules for each copied form and assertion:

- Change `deftype` to `defvaluetype`. Keep the type name, the fields,
  the options, the specs and the method bodies.
- Keep the Clojure assertions that the "Assertions" column of the
  table names. The other assertions of these tests stay out (for
  example the assertions that use only records, the `method-names`
  assertion of `marker-tests`, the `String` assertions of
  `extend-test`).
- Add one assertion for each class that a form defines without
  error: it is a value class. The error forms have no such assertion.
- Copy the supporting definitions that the forms and assertions need
  (for example `ExampleProtocol`, `MarkerProtocol`, `MarkerProtocol2`,
  `Dasherizer`, the interface `I`, the hash-collision protocols and
  mock hash functions, and the `assert-expr` methods
  `thrown-with-cause-msg?` and `fails-with-cause?` of
  `test/clojure/test_helper.clj`). Define them in the test namespace.
- Change a fully qualified name in a reader literal, an expected
  message or a quoted form (for example
  `#clojure.test_clojure.protocols.TypeToTestFactory[42]`,
  `user.MyType`) to the name in the test namespace (or, for the AOT
  test, in the compiled namespace).
- Change the error forms as "Errors" states.
- Change `Bar` and its expected annotations as "Annotations
  adaptation" states.
- Each group in the table below, and the AOT test, has a comment that
  names the Clojure source files and the Clojure tests of its forms.

The forms:

| Group | Forms | Assertions |
|---|---|---|
| Markers | `HasMarkers`, `WillGetMarker` with `extend-type` | `marker-tests` (the `satisfies?` assertions) |
| Extend | `ExtendTestWidget` | `extend-test` ("you can extend deftype types") |
| Illegal extend | `HasProtocolInline` | `illegal-extending` (first assertion) |
| Disabled upstream | `ExtendsTestWidget`, `ExtendersTestWidget`, `SatisfiesTestWidget`, `ReExtendingTestWidget` | value class only |
| Factory and literals | `TypeToTestFactory`, `TypeToTestLiterals` | the `deftype` assertions of `deftype-factory-fn`, `test-ctor-literals`, `exercise-literals` |
| Hints | `TypeToTestLongHint`, `TypeToTestByteHint` | the `deftype` assertions of `hinting-test` |
| Leading dashes | `Dashed` | `test-leading-dashes` |
| Hash collisions | `TestType1` … `TestType15` | both tests of `hash_collisions.clj` |
| Name ambiguity | `T` with interface `I` | `test-reflective-field-name-ambiguous` |
| Embedded instance | `CLJ1399` | `clj-1399` |
| Errors | `StringBuffer`, `MyType [:key1]`, `Foo/2` | the Clojure error assertions |
| Annotations | `Bar`, adapted (see below) | type, field and method annotations |

Form count: the "Context" table has 40 forms. 34 are in the test
namespace (the one adapted `Bar` stands for the `java_8.clj` and the
`java_9.clj` `Bar`, which are the same form; only the expected
`Deprecated` map differs). 3 are in the AOT source string (`X`, `T`,
`x`). 2 are in "Out of scope" (`t`, `Something`).

### Errors

- `StringBuffer`: in the `deftest` body, inside `thrown-with-msg?`, as
  in `naming-types`. `defvaluetype` resolves through the `:refer` of
  the test namespace. Keep only the `deftype` assertion of
  `naming-types`.
- `MyType [:key1]` and `Foo/2 [a]`: change `(eval '(deftype …))` to
  `(support/eval-in 'value-type.deftype-compat-test '(defvaluetype …))`
  (`value-type.test-support/eval-in` binds `*ns*` to the test
  namespace). So the quoted `defvaluetype` resolves, and the `MyType`
  message is the same under the CLI (where `*ns*` is `user`) and in a
  REPL.
- `MyType` expects the cause message
  `#"defrecord and deftype fields must be symbols, value-type\.deftype-compat-test\.MyType had: :key1"`.
- `Foo/2`: keep `(thrown? Exception …)`. Add one assertion,
  `(thrown-with-cause-msg? Exception #"IllegalName: .*Foo/2" …)`, on
  the same form. So the error does not come from an unresolved
  symbol.

### Annotations adaptation

The Clojure annotation tests load only on Java 8, 9 and 10. The form
uses `javax.xml.ws` annotation types. These types were removed from
the JDK in Java 11.

- Choice: keep the `Bar` form with only the annotation types that
  JDK 27 has: `Deprecated`, `Retention` and
  `javax.annotation.processing.SupportedOptions`. Expect the
  `expected-annotations` set of `java_9.clj` without the `javax.xml.ws`
  entries, on the type, the field `b` and the method `foo`.
- Rationale: the transform must keep the annotation attributes of the
  class, the fields and the methods. This is the only form in the
  corpus that checks it.

## Decisions

### Copy the forms, do not load the Clojure test files

- Choice: copy the forms and assertions into the project, with a
  source comment for each, and the EPL copyright notice of the Clojure
  test files in the namespace header.
- Rejected:
  - Load the Clojure test files at test time with `deftype` replaced.
    This needs the Clojure sources at test time (a network fetch or a
    git dependency), and it changes `clojure.core/deftype` globally.
  - Write similar forms. This loses the link to the Clojure tests,
    which is the point of the task.
- Rationale: the copy is small (the 40 forms of the "Context" table,
  see "Form count"), stable for one Clojure version, and easy to
  trace.

### Clojure places, fixed names

- Choice: the forms are at the place that the Clojure sources use
  (top-level forms, except the error forms, see "Errors"), with their
  Clojure names, not `gensym` names.
- Rationale: this is the shape of the Clojure tests and of real use.
  It tests the `require` path. Each name is defined once per load. A
  reload in a REPL is a redefinition, which `defvaluetype` supports.

### Reflection warnings

- Choice: `value-type.deftype-compat-test` does not set
  `*warn-on-reflection*`.
- Rationale: some Clojure assertions use reflection on purpose (for
  example `(. t a)` in the name ambiguity test, and `(.name this)` in
  `extend-test`). The Clojure test files do not set it. The project
  rule "no reflection warnings" applies to the other namespaces.

### AOT checks in a new JVM

- Choice: compile one source string (no file on the classpath) with
  `support/aot-compile`. The string has the CLJ-979 forms
  (`(eval '(defvaluetype X []))`, `(defvaluetype T [])`) and the
  CLJ-1208 forms (`a`, protocol `p`, `defvaluetype x` with
  `:load-ns true`). Run one new JVM with the compile directory and the
  test classpath.
- Rationale: CLJ-1208 needs a JVM where the namespace is not loaded.
  CLJ-979 needs the AOT output of an `eval` inside a compiled file.
  One compile and one JVM keep the test time small. This is the
  pattern of `value-type.core-test/aot-new-jvm-test`.

### `with-redefs` of `hash`

- Choice: keep the `with-redefs [hash …]` of the Clojure
  hash-collision tests.
- Rationale: this is the Clojure test. The test runner runs tests one
  at a time, so the redefinition does not affect other tests.

## Invariants

- Each copied form and assertion differs from its Clojure source only
  by the changes that the rules in "Corpus" list.
- Each class that a form of the corpus defines without error is a
  value class.
- `src/` does not change.

## Documentation

- `ramora/defvaluetype-design/assertions.md`: a new assertion A-27,
  `observed`: the `deftype` test forms of Clojure 1.12.6 without
  mutable fields compile as value classes and keep the Clojure
  assertions, except:
  - `Something` (not copied, inside a disabled test);
  - the three error forms (they give the `deftype` errors);
  - `Bar` (adapted, without the `javax.xml.ws` annotation types);
  - each form with an `Excluded:` comment (see "Out of scope"), if
    there is one, with its proposed fix task.

  Basis `value-type.deftype-compat-test`, JDK `27-jep401ea3`.
- `ramora/defvaluetype-design/decisions.md` D11: the `deftype`
  compatibility corpus (`value-type.deftype-compat-test`), copied from
  the Clojure 1.12.6 test suite.
- `ramora/defvaluetype-design/risks-and-evolution.md`, risk "Clojure
  changes `deftype` output or the `LOADER` order": add the corpus to
  the mitigation. When the project changes the Clojure version, copy
  the corpus again from the new tag.
- `ramora/defvaluetype-design/qualities.md`: the compatibility fitness
  condition names `value-type.deftype-compat-test`.
- `mementum/state.md`: the corpus is done; the next step.
- `munera/plan.md`: this task.

## Verification

1. `value-type.deftype-compat-test` has each group in "Corpus". Each
   class that a form defines without error is a value class, and each
   kept Clojure assertion passes.
2. AOT in a new JVM, with no source of the compiled namespace on the
   classpath. `<ns>` is the compiled namespace, and `<pkg>` is
   `(namespace-munge '<ns>)`. The new JVM does these checks in this
   order:
   1. The namespace is not loaded: `(find-ns '<ns>)` is nil.
   2. CLJ-1208: `(.f (<pkg>.x.))` returns 1 (the Clojure assertion).
   3. The namespace is loaded: `(find-ns '<ns>)` is not nil. `x` is a
      value class.
   4. CLJ-979: `X` and `T` are value classes. `(class (<ns>/->X))` is
      `X`. `(class (<pkg>.T.))` and `(class (<ns>/->T))` are `T`.

   Each check is a separate top-level form, so that a var of the
   namespace (for example `<ns>/->X`) is resolved only after check 1
   and check 2. The test deletes the temporary directory.
3. `value-type.core-test/aot-new-jvm-test` still passes with the
   shared new-JVM helper.

## Acceptance criteria

1. The tests in "Verification" pass on the Valhalla JDK with
   `clojure -M:test -m scry.cli` (exit 0). The full suite passes.
2. A REPL run of `value-type.deftype-compat-test` with
   `scry.core/run` gives `:pass? true` and a `[:summary :var-count]`
   equal to the count of `deftest` vars in the namespace. This count
   is more than 0.
3. Each form of the corpus table in "Context" is in the test
   namespace, or in "Out of scope" with the reason, or replaced by an
   `Excluded:` comment with the reason (see "Out of scope"). The
   forms in the AOT source string count as forms in the test
   namespace. The one adapted `Bar` stands for the `java_8.clj` and
   the `java_9.clj` `Bar` (see "Form count").
4. Each group of "Corpus", and the AOT test, has a comment with the
   Clojure source files and tests of its forms (rule in "Corpus").
   The namespace header has the EPL notice.
5. `src/` has no change.
6. The documents in "Documentation" agree with this design.
