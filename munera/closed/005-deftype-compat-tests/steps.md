# Steps

## Slice 0: source corpus

- [x] Get the tag `clojure-1.12.6` into `~/src/clojure` without a
  change of its HEAD or work tree:
  `git -C ~/src/clojure fetch --no-tags origin tag clojure-1.12.6`.
  If this fails (SSH remote), use HTTPS:
  `git -C ~/src/clojure fetch --no-tags https://github.com/clojure/clojure.git tag clojure-1.12.6`.
  If both fail, stop and ask the user. Do not copy from another tag
  or from `master`.
- [x] Verify: `git -C ~/src/clojure rev-parse --verify 'clojure-1.12.6^{commit}'`
  gives a commit, and `git -C ~/src/clojure status --short` shows only
  `?? deps.edn`, as before.
- Read each source file with
  `git -C ~/src/clojure show clojure-1.12.6:test/clojure/<path>`
  (for example `test_clojure/protocols.clj`, `test_helper.clj`).
  Do not check out the tag.

## Slice 1: shared new-JVM helper

- [x] Move `run-java` from `value-type.core-test` to
  `value-type.test-support` as a public fn; update
  `aot-new-jvm-test` to use `support/run-java`.
- [x] Run the full suite (`clojure -M:test -m scry.cli`); exit 0.

## Slice 2: corpus namespace scaffold

- [x] Create `test/value_type/deftype_compat_test.clj` with ns form
  (`:require` `[clojure.test :refer :all]`,
  `[value-type.core :refer [defvaluetype]]`,
  `[value-type.test-support :as support]`) and the EPL 1.0 copyright
  notice from the Clojure test files in the header. `:refer :all` is
  the smallest change from the Clojure `(:use clojure.test)` (plan
  "Key decisions"); the copied `assert-expr` methods then resolve
  `report`, `do-report` and `assert-expr` to `clojure.test`.
- [x] Copy the `assert-expr` methods `thrown-with-cause-msg?` and
  `fails-with-cause?` (and `causes`) from
  `test/clojure/test_helper.clj`.
- [x] Copy the supporting definitions used by the groups:
  `ExampleProtocol`, `MarkerProtocol`, `MarkerProtocol2`,
  `Dasherizer`, `definterface I`, `definterface Foo`,
  the hash-collision protocols and mock hash functions, and
  `annotation->map` from `annotations/java_9.clj`.
- [x] Load the namespace in the REPL; no error.

## Rules for each corpus slice (Slices 3 to 7)

Each group: a comment naming the Clojure source files and tests;
`deftype` → `defvaluetype`; the assertions named in the design
"Corpus" table; one `support/value?` assertion per class defined
without error.

Qualified names (plan "Key decisions"):

- class names (reader literals, the `extend` class, the
  `read-string` strings of `hinting-test`) use
  `value_type.deftype_compat_test.`;
- var names use `value-type.deftype-compat-test/`;
- the names in a quoted `eval` form stay fully qualified;
- the `MyType` message uses the dashed `*ns*` name (Slice 6).

## Slice 3: protocol groups

- [x] Markers: `HasMarkers`, `WillGetMarker` with `extend-type`;
  the `satisfies?` assertions of `marker-tests`.
- [x] Extend: `ExtendTestWidget`; the "you can extend deftype types"
  assertion of `extend-test`.
- [x] Illegal extend: `HasProtocolInline`; the first assertion of
  `illegal-extending`. In the quoted form change
  `clojure.test_clojure.protocols.HasProtocolInline` to
  `value_type.deftype_compat_test.HasProtocolInline` and
  `clojure.test-clojure.protocols.examples/ExampleProtocol` to
  `value-type.deftype-compat-test/ExampleProtocol`.
- [x] Disabled upstream: `ExtendsTestWidget`, `ExtendersTestWidget`,
  `SatisfiesTestWidget`, `ReExtendingTestWidget`; value-class
  assertions only.
- [x] Factory and literals: `TypeToTestFactory`, `TypeToTestLiterals`;
  the `deftype` assertions of `deftype-factory-fn`,
  `test-ctor-literals`, `exercise-literals`; change
  `#clojure.test_clojure.protocols.<Type>[…]` to
  `#value_type.deftype_compat_test.<Type>[…]`.
- [x] Hints: `TypeToTestLongHint`, `TypeToTestByteHint`; the
  `deftype` assertions of `hinting-test`; in the `read-string`
  strings change `#clojure.test_clojure.protocols.TypeToTestLongHint`
  to `#value_type.deftype_compat_test.TypeToTestLongHint`.
- [x] Leading dashes: `Dashed`; `test-leading-dashes`.
- [x] Each group above has its comment: `protocols.clj` and the
  Clojure tests of the group (the disabled tests for "Disabled
  upstream").
- [x] Run the namespace with `scry.core/run`; `:pass? true`.

## Slice 4: hash collisions

- [x] Copy `TestType1`…`TestType15` and both tests of
  `protocols/hash_collisions.clj` (`protocols-with-hash-collisions`,
  `protocols-with-no-min-hash-in-13-bits`), keeping
  `with-redefs [hash …]`; add value-class assertions.
- [x] Group comment names `protocols/hash_collisions.clj`
  (`protocols-with-hash-collisions`,
  `protocols-with-no-min-hash-in-13-bits`).
- [x] Run the namespace; `:pass? true`.

## Slice 5: interop and compilation

- [x] Name ambiguity: `T` with interface `I`;
  `test-reflective-field-name-ambiguous` assertions; value-class
  assertion.
- [x] Embedded instance: `CLJ1399`; the `clj-1399` assertions;
  value-class assertion.
- [x] Group comments: "Name ambiguity" names `java_interop.clj`
  (`test-reflective-field-name-ambiguous`); "Embedded instance"
  names `compilation.clj` (`clj-1399`).
- [x] Run the namespace; `:pass? true`.

## Slice 6: errors

- [x] `StringBuffer`: in the `deftest` body inside `thrown-with-msg?`
  as in `naming-types`; only the `deftype` assertion.
- [x] `MyType [:key1]`: via
  `(support/eval-in 'value-type.deftype-compat-test '(defvaluetype …))`;
  expect cause message
  `#"defrecord and deftype fields must be symbols, value-type\.deftype-compat-test\.MyType had: :key1"`
  (use `fails-with-cause?` / `thrown-with-cause-msg?` as the Clojure
  test does).
- [x] `Foo/2 [a]`: via `support/eval-in`; keep
  `(thrown? Exception …)` and add
  `(thrown-with-cause-msg? Exception #"IllegalName: .*Foo/2" …)` on
  the same form.
- [x] Group comment names `ns_libs.clj` (`naming-types`,
  `test-defrecord-deftype-err-msg`) and `array_symbols.clj`
  (`test-array-symbols`).
- [x] Run the namespace; `:pass? true` under both `scry.core/run`
  (REPL) and `-m scry.cli` (where `*ns*` is `user`).

## Slice 7: annotations

- [x] Copy `Bar` adapted: annotation types `Deprecated`, `Retention`,
  `javax.annotation.processing.SupportedOptions` on the type, field
  `b` and method `foo`; expected set is the `java_9.clj`
  `expected-annotations` without the `javax.xml.ws` entries.
- [x] Assertions via `annotation->map` on the type, field and method
  (tests `test-annotations-on-type`, `-on-field`, `-on-method`);
  value-class assertion; group comment names `annotations/java_8.clj`
  and `annotations/java_9.clj` and states that the one `Bar` stands
  for both.
- [x] Run the namespace; `:pass? true`.

## Slice 8: AOT new-JVM test

- [x] Build one source string for a compiled namespace with the
  CLJ-979 forms (`(eval '(defvaluetype X []))`, `(defvaluetype T [])`)
  and the CLJ-1208 forms (`a`, protocol `p`, `defvaluetype x` with
  `:load-ns true`).
- [x] Compile with `support/aot-compile` into a `with-temp-dir`
  directory (no source on the classpath).
- [x] Run one new JVM with `support/run-java` (compile dir + test
  classpath). The `-e` string has separate top-level forms in this
  order: (1) `(find-ns '<ns>)` is nil; (2) `(.f (<pkg>.x.))` returns
  1; (3) `(find-ns '<ns>)` is not nil and `x` is a value class;
  (4) `X` and `T` are value classes, `(class (<ns>/->X))` is `X`,
  `(class (<pkg>.T.))` and `(class (<ns>/->T))` are `T`. Do not start
  the string with `(require …)`. `prn` a result vector; assert on the
  last line of `out`.
- [x] Test comment names `compilation.clj` (`CLJ-979`, `clj-1208`),
  `compilation/examples.clj` and `compilation/load_ns.clj`.
- [x] Confirm the temp directory is deleted after the test.
- [x] Run the full suite; exit 0.

## Slice 9: documentation

- [x] `ramora/defvaluetype-design/assertions.md`: add A-27
  (`observed`), with the exception list (`Something`, the three error
  forms, adapted `Bar`, and each `Excluded:` form if any); basis
  `value-type.deftype-compat-test`, JDK `27-jep401ea3`; follow the
  A-23 record format.
- [x] `ramora/defvaluetype-design/decisions.md`: in the D11 that
  exists ("D11. Verification"), add one item to the "Choice" list:
  the `deftype` compatibility corpus
  (`value-type.deftype-compat-test`), copied from the Clojure 1.12.6
  test suite. Do not add a new decision id. Keep the D11 id and
  title (`embodiment.md` refers to D11).
- [x] `ramora/defvaluetype-design/risks-and-evolution.md`: add the
  corpus to the mitigation of the risk "Clojure changes `deftype`
  output or the `LOADER` order"; note the re-copy rule on a Clojure
  version change.
- [x] `ramora/defvaluetype-design/qualities.md`: the compatibility
  fitness condition names `value-type.deftype-compat-test` (no
  exception list here).
- [x] `mementum/state.md`: in "Status", add that the compatibility
  corpus is done (`value-type.deftype-compat-test`, task 005, no
  `src/` change). In "Next", replace the 005 item and the "Other
  adjacent tasks" bullet with one bullet: no next task is created;
  the candidates are the two adjacent tasks (nested `defvaluetype` in
  a method body, which needs a loader change; a method-call
  benchmark); the user selects the next task.
- [x] `munera/plan.md`: no edit in this task. Check that the entry
  for 005 agrees with `design.md` "Intent". The move of the task to
  `munera/closed/` removes the entry (munera protocol).

## Slice 10: verification

- [x] `clojure -M:test -m scry.cli` exits 0 on the Valhalla JDK; full
  suite passes, including `value-type.core-test/aot-new-jvm-test`
  with the shared helper.
- [x] REPL `scry.core/run` on `value-type.deftype-compat-test` gives
  `:pass? true` and `[:summary :var-count]` equal to the `deftest`
  count (> 0).
- [x] Check each form of the design "Context" table is in the test
  namespace, in the AOT source string, in "Out of scope", or has an
  `Excluded:` comment with the reason. The one adapted `Bar` stands
  for the `java_8.clj` and the `java_9.clj` `Bar` (design "Form
  count").
- [x] Check each group and the AOT test has its source comment; the
  namespace header has the EPL notice.
- [x] `git diff src/` is empty.
- [x] The documents in "Documentation" agree with `design.md`.

## Implementation review follow-up

- [x] `hash-collision-value-classes-test`: replace the one
  `(is (every? value? [TestType1 … TestType15]))` with one
  `(is (value? TestTypeN))` for each class, as `disabled-upstream-test`
  does. The design rule is one value-class assertion for each class,
  and a failure must name the class.

## Test-shaper review follow-up

- [x] `aot-clj-979-clj-1208-test`: the new JVM prints a positional
  vector (`[true 1 [true true] [true true true true]]`). A failure
  shows only a changed position, and the reader must count positions
  against the `-e` comments to find the failed check. Make the new JVM
  `prn` a map with one key for each check (for example
  `{:ns-unloaded-before true :clj-1208-f 1 :ns-loaded-after true
  :x-value true :X-value true :T-value true :X-ctor-class true
  :T-ctor-class true}`), and compare it with `=` to the expected map.
  Then a failure names the check that failed. Keep the check order of
  design "Verification" 2 and one JVM.

## Docs review follow-up

- [x] `ramora/defvaluetype-design/risks-and-evolution.md`, risk
  "Clojure changes `deftype` output or the `LOADER` order": the
  mitigation says "Pin Clojure 1.12 in tests", but `deps.edn` pins
  `1.12.6`, and the new re-copy rule applies "when the Clojure version
  changes". With "1.12", a patch change (for example `1.12.7`) does
  not look like a version change. Change the text to "Pin Clojure
  1.12.6 (`deps.edn`)", so that the pin and the corpus tag agree and
  each change of the pin triggers the re-copy.

## Docs review follow-up, pass 2

- [x] `ramora/defvaluetype-design/embodiment.md`, "Project
  artifacts": the `deps.edn` item says "Clojure 1.12", but `deps.edn`
  pins `1.12.6`, and `risks-and-evolution.md` now says "Pin Clojure
  1.12.6 (`deps.edn`)". Change the item to "Clojure 1.12.6", so that
  the two descriptions of the pin agree with the corpus tag.

## Code-shaper review follow-up

- [x] New-JVM helper: `value-type.core-test/aot-new-jvm-test` and
  `aot-clj-979-clj-1208-test` each build the classpath
  (`dir` + `path.separator` + `java.class.path`) and each read the
  result (`edn/read-string` of the last line of `out`). The shared
  helper owns only the process start, so the protocol "compile dir on
  the test classpath, `prn` the result as the last form" is in three
  places. Move the two parts into `value-type.test-support` (for
  example `(run-in-new-jvm dir form)` that returns
  `{:exit :result :err}`, with `:result` the EDN of the last line of
  `out`). Make both tests use it, and remove the `clojure.edn` and
  `clojure.string` requires that they then do not use. Keep
  `run-java` unchanged if another caller needs it; else make it
  private. Run the full suite; exit 0.

## Code-shaper review follow-up, pass 2

- [x] `value-type.test-support/run-in-new-jvm`: `:result` is
  `(edn/read-string (last (str/split-lines out)))` for each exit.
  When a check form throws in the new JVM, `clojure.main -e` stops,
  and the last line of `out` is the printed result of an earlier
  form (for example `#'user/r1`). `edn/read-string` then throws
  "No dispatch macro for: '", the test has an error, and the `:err`
  of the new JVM (the real cause) is lost. This is the failure path
  that the CLJ-1208 check must report. Read `:result` only when
  `:exit` is 0 (else `:result` nil), so that the `(= 0 exit)`
  assertion fails with the stderr. Run the full suite; exit 0.

## Code-shaper review follow-up, pass 3

- [x] `value-type.test-support/run-in-new-jvm`: `(zero? exit)` throws
  `ClassCastException` when `run-java` returns `{:exit ::timeout}`
  (`zero?` of a keyword). The test then has an error, and the `:err`
  of the new JVM is lost. This is the hung-JVM failure path. Use
  `(= 0 exit)`, so that `:result` is nil and the `(= 0 exit)`
  assertion fails with the stderr. State the `::timeout` exit in the
  docstring. Run the full suite; exit 0.

## Code-shaper review follow-up, pass 4

- [x] `aot-clj-979-clj-1208-test`: the test builds the class names
  with `(namespace-munge aot-ns)` and `(str pkg ".x")`, `".X"`, `".T"`.
  `support/binary-name` (`[ns name]`) already owns this rule, and
  `aot-new-jvm-test` uses it. Bind the three class names with
  `(support/binary-name aot-ns 'x)` (and `'X`, `'T`), and use them in
  the `-e` forms (constructor `(<cls>.)`, `value-form`, the `class`
  comparisons). Remove `pkg`. Run the full suite; exit 0.
