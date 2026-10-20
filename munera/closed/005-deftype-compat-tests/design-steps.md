# Design steps

- [x] Architecture: "Documentation" does not record the corpus in the
  architecture decisions and risks. The corpus is a new verification
  element. It is copied from Clojure 1.12.6 under EPL 1.0. It detects
  the risk "Clojure changes `deftype` output or the `LOADER` order".
  Add to "Documentation":
  - `ramora/defvaluetype-design/decisions.md` D11: the `deftype`
    compatibility corpus (`value-type.deftype-compat-test`), copied
    from the Clojure 1.12.6 test suite.
  - `ramora/defvaluetype-design/risks-and-evolution.md`, risk
    "Clojure changes `deftype` output …": add the corpus to the
    mitigation. When the project changes the Clojure version, copy
    the corpus again from the new tag.
- [x] Ambiguity: the "Errors" group does not say how each error form
  is evaluated. Scope item 1 puts the forms "at the top level", but an
  error form at the top level stops the `require`. In Clojure
  (`clojure-1.12.6` sources), `StringBuffer` is in the `deftest` body
  (`thrown-with-msg?`), and `MyType [:key1]` and `Foo/2 [a]` are
  `(eval '(deftype …))`. The quoted `defvaluetype` resolves only when
  `*ns*` refers it. Under `-m scry.cli`, `*ns*` is `user`. Then
  `(thrown? Exception (eval '(defvaluetype Foo/2 [a])))` passes on
  "Unable to resolve symbol", and checks nothing. Also, "bound or
  matched" for a `*ns*` message gives two options. State:
  - the place of each error form (in the `deftest` body, or `eval`);
  - how a quoted `defvaluetype` resolves (one choice: `*ns*` bound to
    the test namespace, for example with `support/eval-in`, or the
    qualified symbol `value-type.core/defvaluetype`);
  - the expected `MyType` message (one choice: bind `*ns*`, and
    expect `value_type.deftype_compat_test.MyType`);
  - a check that the `Foo/2` error does not come from an unresolved
    symbol.
- [x] Ambiguity: Verification 2 does not give the order of the checks
  in the new JVM, or how "no prior load" and "is then loaded" are
  observed. A CLJ-979 check that calls `->X` or `->T` must load the
  namespace first. If it runs before the CLJ-1208 check, CLJ-1208
  checks nothing. State the order: (1) the namespace is not loaded
  (for example `(find-ns 'ns)` is nil); (2) construct `x`, call `.f`
  (the Clojure assertion uses `.f`), expect 1; (3) the namespace is
  loaded; (4) the CLJ-979 checks.
- [x] Ambiguity: "Out of scope" says "mark the form as excluded with
  the reason" when an assertion fails. It does not say where.
  Acceptance criterion 3 accepts a form "in 'Out of scope', with the
  reason". State the place of the mark (for example a comment in the
  test namespace at the place of the form, plus an entry in
  `implementation.md`), and make criterion 3 accept that place.
- [x] Inconsistency: "Invariants" says that each copied form differs
  from its source only by `deftype` → `defvaluetype` (and the `Bar`
  annotation types). "Corpus" allows more changes: changed qualified
  names in reader literals and messages, a `*ns*` binding or match,
  one added value-class assertion for each class, and the `Bar`
  expected set without the `javax.xml.ws` entries. Make the invariant
  name each change that "Corpus" allows, or make it apply to the
  `defvaluetype` forms only and give the assertion changes separately.
- [x] Inconsistency: the "Corpus" rule "Keep each Clojure assertion of
  the tests in 'Context'" disagrees with the "Assertions" column. The
  column keeps only some assertions: `marker-tests` without the
  `method-names` assertion, `extend-test` only "you can extend
  deftype types", `illegal-extending` only the first assertion. The
  rule "Assertions … that use only records stay out" does not explain
  these omissions (they use no record). Make the rule refer to the
  "Assertions" column, for example: keep the assertions that the
  column names; the other assertions stay out.
- [x] Inconsistency: the form accounting does not agree.
  - "Decisions" says "about 30 forms". "Context" lists 40. By the
    tables, the test namespace has 34 forms, the AOT source string
    has 3 (`X`, `T`, `x`), and 3 stay out (`t`, `Something`, the
    `java_8.clj` `Bar`).
  - Acceptance criterion 3 needs each form of the "Context" table in
    the test namespace or in "Out of scope". The `java_8.clj` `Bar`
    is in neither. It is the same form as the `java_9.clj` `Bar`
    (only the expected `Deprecated` map differs). State that the one
    adapted `Bar` stands for both, and that the AOT forms in the
    source string count as forms in the test namespace.
- [x] Inconsistency: "Corpus" requires a comment for "each form or
  group". Acceptance criterion 4 requires a comment for "each copied
  form". Use one rule in both places.
- [x] Inconsistency: the A-27 text in "Documentation" says "the
  `deftype` test forms of Clojure 1.12.6 without mutable fields
  compile as value classes". The corpus does not show this for
  `Something` (out of scope, no mutable field), for the three error
  forms (they must not compile), or for `Bar` as written (adapted).
  Make the A-27 object name these exceptions, so that the `observed`
  status agrees with the basis.
- [x] Inconsistency: "Out of scope" excludes "a nested
  `defvaluetype`". In Clojure, the `StringBuffer` form is in the
  `deftest` body, inside `thrown-with-msg?` (a nested form). The
  smallest-change rule keeps it there. `mementum/state.md` names the
  excluded case "nested `defvaluetype` in a method body (needs a
  loader change)". Make "Out of scope" use the same qualifier ("in a
  method body"), so that the `StringBuffer` form does not conflict
  with it.
- [x] Inconsistency: "Out of scope" lets the implementation exclude a
  form (an `Excluded:` comment) when an assertion fails. The A-27
  text in "Documentation" has `observed` status and a fixed list of
  exceptions (`Something`, the three error forms, `Bar`). It does not
  include the forms with an `Excluded:` comment. Then an excluded
  form makes A-27 false, and acceptance criterion 6 ("the documents
  agree with this design") fails. Make the A-27 object also name each
  form with an `Excluded:` comment, if there is one, with the
  proposed fix task.

## Plan review

- [x] Ambiguity (plan): the steps do not say which form of a qualified
  name to use for each reference. Slice 3 "Factory and literals" says
  "change qualified names in reader literals to
  `value-type.deftype-compat-test`". A reader literal names a class,
  so `#value-type.deftype-compat-test.TypeToTestFactory[42]` does not
  read. The class form is `value_type.deftype_compat_test`. Clojure
  `illegal-extending` (first assertion) has
  `(eval '(extend clojure.test_clojure.protocols.HasProtocolInline
  clojure.test-clojure.protocols.examples/ExampleProtocol …))`, with a
  class name and a var name. Slice 3 "Illegal extend" does not give
  this change. Under `-m scry.cli`, `*ns*` is `user`, so a short name
  in this quoted form does not resolve. State:
  - class names (reader literals, the `extend` class, the
    `read-string` strings of `hinting-test`) use
    `value_type.deftype_compat_test.`;
  - var names use `value-type.deftype-compat-test/`;
  - the names in the quoted `eval` forms stay fully qualified;
  - the `MyType` message uses the dashed `*ns*` name (as now).
- [x] Ambiguity (plan): Slice 2 gives the ns form as "`:require`
  `clojure.test`", but not the refer. The project namespaces refer a
  list (`[clojure.test :refer [deftest is testing]]`). The copied
  `assert-expr` methods of `test_helper.clj` use `do-report` and
  `assert-expr` unqualified in syntax quote. With the project list,
  they expand to `value-type.deftype-compat-test/do-report` and fail.
  The Clojure test files use `(:use clojure.test)`. State the refer
  (one choice: `[clojure.test :refer :all]`, the smallest change from
  `:use`, so that the copied forms do not change), or the list of
  referred vars (`deftest`, `is`, `testing`, `do-report`,
  `assert-expr`).
- [x] Ambiguity (plan): Slice 0 checks out the tag in `~/src/clojure`.
  This repository is outside the project (now on `master`, with an
  untracked `deps.edn`). Its remote is SSH
  (`git@github.com:clojure/clojure.git`). The plan does not say what
  to do when `git fetch --tags` fails or the tag is not found. State:
  - how to read the sources (one choice: `git show
    clojure-1.12.6:test/clojure/test_clojure/<file>`, with no change
    of the `~/src/clojure` HEAD; or a checkout, then return to
    `master`);
  - the fallback when the tag is not available (for example fetch
    over HTTPS from `https://github.com/clojure/clojure`, or stop and
    ask the user). Do not copy from `clojure-1.12.5-rc1-75`.
- [x] Ambiguity (plan): Slice 9 "`munera/plan.md`: update for this
  task" and "`mementum/state.md`: the corpus is done; the next step"
  do not give the change. By the munera protocol, the task entry
  leaves `munera/plan.md` at the move to `closed/`, not before.
  `mementum/state.md` names two adjacent tasks (nested
  `defvaluetype` in a method body, a method-call benchmark), and no
  next one. State the `munera/plan.md` edit in this task (for
  example: no edit before the close; the close removes the entry),
  and the next step that `mementum/state.md` records.
- [x] Inconsistency (plan): steps Slice 9 says "`decisions.md`: add
  D11 (the compatibility corpus …)". D11 already exists in
  `ramora/defvaluetype-design/decisions.md` ("D11. Verification":
  unit, integration, AOT and error-path tests), and
  `embodiment.md` refers to it. The design ("`decisions.md` D11: the
  `deftype` compatibility corpus") and the architecture review (fit
  with D11) mean an edit of this decision. Change the step (and the
  plan "D11" item, if needed) to: add the corpus
  (`value-type.deftype-compat-test`, copied from the Clojure 1.12.6
  test suite) to the "Choice" list of the D11 that exists. Do not add
  a new decision id.
- [x] Inconsistency (plan): the design "Corpus" rule gives a source
  comment to each group. Steps Slice 3 states it in its header, for
  Slice 3 only. Slices 6, 7 and 8 have a comment step. Slices 4 (Hash
  collisions) and 5 (Name ambiguity, Embedded instance) have no
  comment step. Add a comment step to Slices 4 and 5 (names
  `protocols/hash_collisions.clj`; `java_interop.clj`
  `test-reflective-field-name-ambiguous`; `compilation.clj`
  `clj-1399`), or make the Slice 3 header rule apply to each corpus
  slice.
- [x] Inconsistency (plan): steps Slice 10 checks that each form of the
  "Context" table is in the test namespace, in the AOT source string,
  in "Out of scope", or has an `Excluded:` comment. Acceptance
  criterion 3 also says that the one adapted `Bar` stands for the
  `java_8.clj` and the `java_9.clj` `Bar`. Without this clause, the
  check finds no place for the `java_8.clj` `Bar`. Add the clause to
  the Slice 10 check.

## Plan review, pass 2

- [x] Inconsistency (plan): plan "Slice order" lists Slices 1 to 10.
  It has no Slice 0. Plan "Approach" ("Get only this tag (Slice 0)")
  and steps "Slice 0: source corpus" name Slice 0, and the other
  slices copy from its tag. Add "0. Source corpus: get the
  `clojure-1.12.6` tag into `~/src/clojure` (no change of HEAD or work
  tree)" at the start of plan "Slice order".
