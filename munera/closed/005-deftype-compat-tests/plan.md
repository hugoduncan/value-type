# Plan

## Approach

Build the corpus in one new test namespace,
`value-type.deftype-compat-test`, copied from the Clojure 1.12.6
test sources in `~/src/clojure`. Read each source file with
`git -C ~/src/clojure show clojure-1.12.6:test/clojure/<path>`. Do not
check out the tag: the clone belongs to the user (branch `master`,
untracked `deps.edn`), and its HEAD and work tree do not change.
The clone has no `clojure-1.12.6` tag now. Get only this tag (Slice 0).
Each copied form changes only as the "Corpus" rules of `design.md`
state.

Order of work:

1. Move the new-JVM run helper (`run-java`) from
   `value-type.core-test` to `value-type.test-support`, and make
   `aot-new-jvm-test` use it. This is a self-contained refactor;
   do it first so the AOT slice builds on the shared helper.
2. Create the test namespace: ns header with the EPL 1.0 notice,
   the supporting definitions (protocols, interfaces `I` and `Foo`,
   `Dasherizer`, hash-collision protocols and mock hash functions,
   `annotation->map`, and the `assert-expr` methods
   `thrown-with-cause-msg?` and `fails-with-cause?` from
   `test_helper.clj`).
3. Add the corpus groups in the order of the "Corpus" table, each
   with its source comment and the value-class assertion per class.
4. Add the error forms (deftest body for `StringBuffer`;
   `support/eval-in` for `MyType [:key1]` and `Foo/2 [a]`), the
   adapted `Bar` annotations group, and the AOT new-JVM test
   (one source string, one compile, one JVM, four ordered checks).
5. Documentation updates (A-27, the corpus item in the "Choice"
   list of the D11 that exists, risk mitigation, qualities,
   `mementum/state.md`). `munera/plan.md` has no edit before the
   close of the task: the move to `munera/closed/` removes the entry
   (munera protocol).
6. Full verification runs.

Key decisions (from `design.md`, kept, not re-decided):

- Copy the forms; do not load the Clojure test files.
- Fixed Clojure names at top level; error forms via `eval-in` or in
  a `deftest` body.
- No `*warn-on-reflection*` in the corpus namespace.
- The ns form refers `[clojure.test :refer :all]`. This is an
  exception to the project rule of an explicit refer list. Reason:
  it is the smallest change from the `(:use clojure.test)` of the
  Clojure test files. The copied forms and the `test_helper.clj`
  `assert-expr` methods use `report`, `do-report` and `assert-expr`
  unqualified in a syntax quote. Syntax quote resolves them at read
  time, so the refer must come before the `defmethod` forms. With
  `:refer :all` the copied forms do not change.
- Qualified names (the "Corpus" rule that changes a fully qualified
  name):
  - a class name (a reader literal, the class in the quoted `extend`
    form, the `read-string` strings of `hinting-test`) uses the
    package `value_type.deftype_compat_test.`;
  - a var name uses the namespace `value-type.deftype-compat-test/`;
  - a name in a quoted `eval` form stays fully qualified: `*ns*` is
    `user` under `-m scry.cli`, so a short name does not resolve;
  - the `MyType` message uses the dashed `*ns*` name
    `value-type.deftype-compat-test.MyType`.
- Keep `with-redefs [hash …]` for the hash-collision tests.
- One adapted `Bar` stands for the `java_8.clj` and `java_9.clj`
  forms; expected annotations are the `java_9.clj` set without the
  `javax.xml.ws` entries.
- New-JVM result report: `prn` a map with one key for each check,
  read the last line of `out`, compare with `=` to the expected map. Do not start the `-e` string with
  `(require …)`.

If a kept Clojure assertion fails: do not fix `src/`. Record the
failure in `implementation.md` with a proposed fix task, replace the
form and its assertions with an `Excluded:` comment at the same
place, and add the form to the A-27 exception list. Update all three
places together.

## Risks

- The local Clojure clone has no `clojure-1.12.6` tag, and its
  remote is SSH (`git@github.com:clojure/clojure.git`). Mitigation:
  fetch only the tag, first from `origin`, then over HTTPS from
  `https://github.com/clojure/clojure.git`. If both fail, stop and
  ask the user. Do not copy from another tag (for example
  `clojure-1.12.5-rc1`) or from `master`.
- A form that passed the design probes fails in the full corpus
  namespace (name interactions, load order). Mitigation: the
  `Excluded:` path above; the probes found no failure, so this is
  unlikely.
- The `assert-expr` methods are global (`defmethod` on
  `clojure.test/assert-expr`). They are additive new dispatch values,
  as in Clojure `test_helper.clj`, so no collision is expected.
- CLJ-1208 checks nothing if the namespace loads before the check.
  Mitigation: the fixed check order in Verification 2; separate
  top-level forms in the `-e` string.
- Moving `run-java` can break `aot-new-jvm-test`. Mitigation: run
  the full suite after the move, before the corpus work.

## Slice order

0. Source corpus: get the `clojure-1.12.6` tag into `~/src/clojure`
   (no change of HEAD or work tree).
1. Shared new-JVM helper in `value-type.test-support`.
2. Corpus namespace scaffold (ns header, EPL notice, supporting
   definitions, `assert-expr` methods).
3. Protocol groups: Markers, Extend, Illegal extend, Disabled
   upstream, Factory and literals, Hints, Leading dashes.
4. Hash collisions group (`TestType1`…`TestType15`, both tests).
5. Name ambiguity (`T` with `I`) and embedded instance (`CLJ1399`).
6. Errors group (`StringBuffer`, `MyType [:key1]`, `Foo/2`).
7. Annotations group (adapted `Bar`).
8. AOT new-JVM test (CLJ-979 and CLJ-1208).
9. Documentation updates.
10. Verification and acceptance checks.
