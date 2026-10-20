# Implementation

## Design review

- 2026-10-20 architecture review: the architecture is
  `ramora/defvaluetype-design.md` and its sections. `ramora/META.md`
  and `doc/architecture.md` do not exist. Checked as fit: the test-only
  slice (D11, compatibility fitness condition in `qualities.md`); the
  helper move to `value-type.test-support`; fixed top-level names (the
  namespace differs from `value-type.core-test`, so the
  `DynamicClassLoader` cache has no collision); no
  `*warn-on-reflection*` (stated exception); the `Bar` check of the
  transform invariant "changes only version, flags, constructor".
- architectural review added 1 new design step
- ambiguity review: Clojure sources are at `~/src/clojure`
  (`clojure-1.12.5-rc1-75`, not the `clojure-1.12.6` tag; check out
  the tag before copying). The annotation tests compare
  `annotation->map` maps, not strings, so the `Bar` expectation is
  clear. `TypeToTestHugeFactories` is a `defrecord` (it stays out).
- ambiguity review added 3 new design steps
- inconsistency review: the 34 / 3 / 3 form count comes from the
  "Context" table rows. The `java_8.clj` and `java_9.clj` `Bar` forms
  are identical. Only `expected-annotations` differs (`Deprecated`
  has no `:forRemoval` / `:since` on Java 8).
- inconsistency review added 6 new design steps

## Notes for addressing design-steps

- Principles: edit `design.md` in place (no amendment notes). Keep
  the scope boundary. Prefer the Clojure form with the smallest
  change. Write in ASD-STE100 style.
- Linked steps (resolve together):
  - ambiguity "Errors" group + inconsistency "nested `defvaluetype`":
    the place chosen for `StringBuffer` decides the "Out of scope"
    wording.
  - the `*ns*` and qualified-name choices of the "Errors" step set
    the change list of the "Invariants" step.
  - architecture D11/risk step + A-27 step: both edit "Documentation".
- Clojure source locations (`~/src/clojure/test/clojure/test_clojure/`):
  `ns_libs.clj` 72-105 (`StringBuffer`, `MyType`), `array_symbols.clj`
  67 (`Foo/2`), `compilation.clj` 312-325 (CLJ-979, clj-1208),
  `annotations/java_9.clj` (`Bar`, `annotation->map`).
- Project files: `test/value_type/test_support.clj` (`eval-in`,
  `aot-compile`), `test/value_type/core_test.clj` (`run-java` ~248,
  `aot-new-jvm-test`), `ramora/defvaluetype-design/{decisions,risks-and-evolution,assertions,qualities}.md`.

## Design steps pass (2026-10-20)

- All 10 design steps done in `design.md`. No `SCOPE_QUESTION:` item.
- Probes (JDK `27-jep401ea3`, `support/eval-in` to the test namespace):
  - `MyType` message uses `(str *ns*)`: the dashed
    `value-type.deftype-compat-test.MyType`, not the munged name that
    the design step proposed.
  - `Foo/2` cause: `NoClassDefFoundError` "IllegalName:
    compile__stub.value_type.deftype_compat_test.Foo/2". A quoted
    `defvaluetype` evaluated under `user` gives "Unable to resolve
    symbol: defvaluetype".
  - `StringBuffer` in a `deftest` body inside `thrown-with-msg?`
    passes. The class is defined (a value class) before `import`
    throws.
- `thrown-with-cause-msg?` (line 134) and `fails-with-cause?` (line 47,
  uses `causes`, line 39) are in `~/src/clojure/test/clojure/test_helper.clj`,
  not in the Clojure jar. They call `do-report` unqualified.
- New-JVM order: `clojure.main -e` reads and evals one form at a
  time, so separate forms in the `-e` string keep the order. Do not
  start the form with `(require …)`, as `aot-new-jvm-test` does.

## Design review, pass 2 (2026-10-20)

- architecture review, pass 2: no new architectural review feedback.
  Checked as fit after the design-steps pass: the `deftest`-body
  `StringBuffer` form (top-level form, not a method body, so the
  loader needs no change); `assert-expr` methods in the test namespace
  (global, as in Clojure `test_helper.clj`); `with-redefs [hash …]`
  (scry runs vars in sequence); fixed AOT names; the Documentation
  list against D11, the compatibility fitness condition and the
  `LOADER` risk. A-27 is the next free id. The A-23 record
  (`verifiedBy`) is a precedent for the A-27 subject and predicate.
- ambiguity review, pass 2: no new ambiguity review feedback.
  - `~/src/clojure` has no `clojure-1.12.6` tag (`git show` fails).
    Run `git fetch --tags` before copying.
  - The copied names do not collide in one namespace. The
    supporting definitions also include `definterface Foo` (`Bar`
    implements it) and `annotation->map` from `java_9.clj`.
    `Foo` and the `Foo/2` error form both stay in the namespace.
- inconsistency review, pass 2: added 1 new design step (A-27 against
  the `Excluded:` path). The form count 34 + 1 `Bar` + 3 AOT + 2 out
  = 40 agrees with `grep "(deftype"`. `Foo/2` is in Clojure test
  `test-array-symbols`. Use this name in the group comment.

## Notes for addressing design-steps (pass 2)

- One open step: A-27 against the `Excluded:` path. It edits only the
  A-27 bullet in "Documentation". Do not change "Out of scope", the
  exclusion rule, or the scope boundary.
- Keep A-27 `observed`. Name the excluded forms in the object (a
  conditional "and each form with an `Excluded:` comment, if any").
  Do not lower the status. The probe found no failure.
- Optional: make the qualities fitness-condition bullet agree with the
  same exception wording
  (`ramora/defvaluetype-design/qualities.md`, "Compatibility" row).
- A-27 record format and the `verifiedBy` precedent (A-23):
  `ramora/defvaluetype-design/assertions.md`.

## Design steps pass 2 (2026-10-20)

- The A-27 step is done: the A-27 bullet in "Documentation" now lists
  the exceptions, and adds each `Excluded:` form with its proposed fix
  task. No `SCOPE_QUESTION:` item.
- `qualities.md` "Compatibility" row is not changed. Its fitness
  condition is a target, not an observation. An excluded form must
  show as a gap against it, so it keeps no exception list.
- When an `Excluded:` comment is added, update three places together:
  the comment in the test namespace, `implementation.md` (failure and
  proposed task), and the A-27 object.

## Design review, pass 3 (2026-10-20)

- architecture review, pass 3: no new architectural review feedback.
  The only design change since pass 2 is the A-27 exception list. A
  conditional object ("each `Excluded:` form, if any") keeps A-27
  `observed` and fits the assertion record format.
  `verification.md` is the spike record, so "Documentation" correctly
  leaves it out.
- ambiguity review, pass 3: no new ambiguity review feedback. The
  new-JVM result report is not stated; follow `aot-new-jvm-test`
  (`prn` a vector, read the last line of `out`).
- inconsistency review, pass 3: no new inconsistency review feedback.
  Checked against `~/src/clojure`: Verification 2 matches `CLJ-979`
  and `clj-1208` (the `Y` assertion of `CLJ-979` is a record, so it
  stays out). `thrown-with-cause-msg?` catches the outer class, so
  `Exception` matches the `NoClassDefFoundError` cause. Group-comment
  test names: `test-annotations-on-type`, `-on-field`, `-on-method`
  (`-on-constructor` is `gen-class`, out of scope).

## Notes after design review, pass 3

- No open design steps. All three reviews of pass 3 found nothing.
  The design is stable. The next step is `create-task-plan`, not
  a design-steps pass.
- For the plan: the earlier notes in this file cover the source
  locations, probes, and update rules. Do not copy them into
  `design.md`.

## Plan review (2026-10-20)

- ambiguity review of plan and steps: checked against
  `~/src/clojure` (`protocols.clj`, `protocols/examples.clj`,
  `hash_collisions.clj`, `java_interop.clj`, `compilation.clj`,
  `compilation/{examples,load_ns}.clj`, `ns_libs.clj`,
  `array_symbols.clj`, `annotations/java_9.clj`, `test_helper.clj`).
  - `ExampleProtocol` has `foo`, `bar`, `baz`, `with-quux`. The
    copied test namespace then has vars `foo`, `bar`, `baz`, and the
    classes `Foo`, `Bar`. No collision (case differs).
  - `expected-annotations` (a `def` in `java_9.clj`) is also a
    supporting definition. Slice 2 does not list it.
  - AOT source string: the quoted `(eval '(defvaluetype X []))`
    resolves under the compiled `*ns*`, at compile time and again at
    load time in the new JVM. Its ns form must refer `defvaluetype`
    (the `aot-new-jvm-test` ns form does not).
  - New-JVM checks as separate forms: keep the results of checks 1
    and 2 (for example `def` in `user`) before the last `prn`. Not
    recorded as a step: each choice passes the acceptance criteria.
- ambiguity review added 4 new design steps
- inconsistency review of plan and steps: D11 in `decisions.md` is
  "Verification" (it exists; edit it, do not add it). Not recorded as a
  step: Slice 6 `MyType` names `fails-with-cause?` /
  `thrown-with-cause-msg?`. Clojure uses only
  `thrown-with-cause-msg? clojure.lang.Compiler$CompilerException`;
  keep that ("as the Clojure test does" decides it).
- inconsistency review added 3 new design steps

## Notes for addressing design-steps (plan review)

- The 7 open steps edit `plan.md` and `steps.md`. `design.md` is
  stable. Its "Corpus" rules already give the correct behaviour. Do not
  change it, except for a stated exception that the design must own
  (see the refer item).
- Principles: smallest change from the Clojure source; edit in place;
  ASD-STE100 style; no `src/` change; the steps must not need a user
  decision during implementation.
- Linked steps (resolve together):
  - qualified names + Slice 3/4/5 source comments: all edit the
    Slice 3 header and the group steps. One header rule ("each corpus
    slice") can cover the comments and the name forms.
  - D11 edit + Slice 9 `munera/plan.md` / `mementum/state.md`: all
    edit Slice 9 and plan "Approach" item 5.
- `clojure.test` refer: the project namespaces use an explicit list
  (`test/value_type/core_test.clj` ns form). `:refer :all` is an
  exception, as "Reflection warnings" is. If you choose it, record the
  reason in plan "Key decisions" (copied forms and `test_helper.clj`
  methods stay unchanged). In a syntax quote, `do-report` resolves at
  read time, so the refer must come before the `defmethod`.
- Slice 0: `~/src/clojure` belongs to the user (`master`, untracked
  `deps.edn`). Prefer read-only `git show <tag>:<path>`. Do not assume
  that SSH or network access works.
- Slice 9: the munera protocol removes a `plan.md` entry only at the
  move to `closed/`. `mementum/state.md` "Next" lists two adjacent
  tasks that are not created yet. The next step can name them as
  candidates. It does not choose one.
- Paths: `ramora/defvaluetype-design/decisions.md` (D11 at line 184),
  `ramora/defvaluetype-design/embodiment.md` (line 126 refers to D11),
  `ramora/defvaluetype-design/qualities.md` ("Compatibility" row),
  `ramora/defvaluetype-design/risks-and-evolution.md` (`LOADER` risk
  row), `mementum/state.md`, `munera/plan.md`.

## Plan-review steps pass (2026-10-20)

- All 7 plan-review steps done in `plan.md` and `steps.md`.
  `design.md` not changed. The `:refer :all` exception is recorded in
  plan "Key decisions".
- `~/src/clojure` still has no `clojure-1.12.6` tag (latest
  `clojure-1.12.5`). No fetch was done in this pass. Slice 0 does it.
- Qualified names in kept assertions (`protocols.clj` at the
  current local HEAD; check the line numbers against the tag): 140-141
  (`illegal-extending`, quoted `extend`), 433 (`test-ctor-literals`),
  445 and 486 (`exercise-literals`), 557 (`hinting-test`
  `read-string`). `protocols/hash_collisions.clj`, the
  `java_interop.clj` `T` test and `compilation.clj` `clj-1399` have
  none.
- `fails-with-cause?` expands to unqualified `report` and `causes`.
  `thrown-with-cause-msg?` expands to `do-report`. With
  `:refer :all`, `report`/`do-report` resolve to `clojure.test`, and
  `causes` resolves to the copied var.

## Plan review, pass 2 (2026-10-20)

- ambiguity review of plan and steps, pass 2: no new ambiguity review
  feedback. Checked against `~/src/clojure` (HEAD, no tag yet).
  Not recorded as steps (each choice passes the acceptance criteria):
  - Slice 7: the top-level `import` of `java_9.clj` also names
    `javax.xml.ws` classes. Remove them from the copied `import` too,
    or the namespace does not load on JDK 27.
  - Disabled upstream: the Clojure tests are `#_`, so the value-class
    assertions need a new `deftest` (name free).
  - Slice 8: `Compiler/compile` also loads `<ns>` in the test JVM.
    Use a `<ns>` that differs from the test namespaces.
- inconsistency review of plan and steps, pass 2: added 1 new design
  step (plan "Slice order" has no Slice 0). Not recorded as a step:
  steps Slice 9 replaces the 005 item of `mementum/state.md` "Next"
  with the two candidate tasks, but the "Other adjacent tasks" bullet
  already names them. Merge the two bullets into one when you edit.

## Notes for addressing design-steps (plan review, pass 2)

- One open step: add Slice 0 to plan "Slice order". Edit only
  `plan.md`. Do not change `design.md` (stable) or `steps.md` (its
  Slice 0 is already correct). Use the wording of steps "Slice 0".
  Do not change the numbers of Slices 1 to 10.
- Optional, in the same edit: the steps Slice 9 `mementum/state.md`
  bullet can say "merge the 005 item and the 'Other adjacent tasks'
  bullet into one" (see the pass 2 inconsistency note above).
- Files: `munera/open/005-deftype-compat-tests/plan.md` ("Slice
  order", at the end), `mementum/state.md` ("Next").

## Plan-review follow-up, pass 2 (2026-10-20)

- Plan "Slice order" now starts with Slice 0 (source corpus), as
  steps "Slice 0". Slices 1 to 10 keep their numbers. Plan "Order of
  work" is a separate list (items 1 to 6); it does not use slice
  numbers, and Slice 0 comes before its item 1.
- Steps Slice 9 now merges the `mementum/state.md` "Next" 005 item
  and the "Other adjacent tasks" bullet into one bullet.
- No implementation step is done yet: the `clojure-1.12.6` tag is
  still not in `~/src/clojure`.

## Plan review, pass 3 (2026-10-20)

- ambiguity review of plan and steps, pass 3: no new ambiguity review
  feedback.
  - Probe (JDK `27-jep401ea3`, macOS, case-insensitive temp dir): the
    Slice 8 source string puts `X` and `x` in one package. AOT writes
    `x` into `X.class` (`javap`: "does not contain class …X"). The
    four Verification 2 checks still pass: check 3 loads the
    namespace, its `__init` evaluates `(eval '(defvaluetype X []))`
    again, and `X` then comes from a `DynamicClassLoader` (`T` comes
    from the AOT file). This is the same as upstream: after the load,
    the in-memory `X` wins. Do not rename `X` or `x`. Do not add a
    check that reads `X.class`.
  - `clojure.main -e` prints each non-nil result (`#'user/r1` for a
    `def`). The last line of `out` is the result only when the final
    form is the `prn`.
  - `protocols/hash_collisions.clj`, `compilation/load_ns.clj` and
    `annotations/java_9.clj` have no copyright notice. The one EPL
    notice in the namespace header (from `protocols.clj`) covers the
    corpus.
- inconsistency review of plan and steps, pass 3: no new
  inconsistency review feedback. Checked across `design.md`,
  `plan.md`, `steps.md`, `munera/plan.md` and the Slice 9 targets
  (D11 "Choice" list, A-23 row format, qualities "Compatibility" row,
  `LOADER` risk row). The form count (34 + 3 + 2, `Bar` for both) and
  the slice order agree.
  - Slice 8 source string: keep the CLJ-979 forms before the CLJ-1208
    forms (the design order). On a case-insensitive file system, the
    class written last owns `X.class`/`x.class`. If `X` is written
    last, `x` has no class file and CLJ-1208 fails.

## Notes after plan review, pass 3

- No open design steps. Plan pass 3 found nothing (ambiguity and
  inconsistency). Do not run a design-steps pass. The next step is
  implementation, from Slice 0 (the `clojure-1.12.6` tag is still
  not in `~/src/clojure`).
- To probe an AOT result by hand, use `javap -cp <dir> <class>`
  (JDK at `~/.local/jdks/jdk-27.jdk/Contents/Home/bin/`). A
  "does not contain class" warning shows a case collision.

## Implementation pass (2026-10-20)

- All slices done in one pass. Tag `clojure-1.12.6` fetched from
  `origin` (SSH worked), commit `31a3138a`. `~/src/clojure` HEAD and
  work tree unchanged.
- No form failed: no `Excluded:` comment. A-27 says "no `Excluded:`
  form".
- `run-java` is now public in `value-type.test-support`.
  `core-test` no longer imports `List` and `TimeUnit`.
- New `deftest` vars (no Clojure counterpart): `disabled-upstream-test`
  and `hash-collision-value-classes-test` (value-class checks). The
  other value-class checks are added to the copied tests.
- AOT namespace: `value-type.deftype-compat-aot`. New-JVM checks are
  `def r1`..`r4` forms, then `(prn [r1 r2 r3 r4])`; `isValue` is
  called by reflection in the child JVM.
- Results: `clojure -M:test -m scry.cli` exit 0 (64 tests, 318
  assertions) with `PATH` at the JDK 27 `bin` (the default `java` is
  JDK 25, which fails at load). `scry.core/run {:namespaces [...]}`:
  `:pass? true`, `:var-count 21` = 21 `deftest` forms.

## Implementation review (2026-10-20)

- added 1 step to be addressed

## Implementation review follow-up (2026-10-20)

- addressed 1 review step: `hash-collision-value-classes-test` has one
  `(is (value? TestTypeN))` per class. Full suite exit 0 (64 tests,
  332 assertions).

## Implementation review, pass 2 (2026-10-20)

- no new steps

## Implementation test review (2026-10-20)

- no new steps

## Test-shaper review (2026-10-20)

- added 1 step to be addressed

## Test-shaper review follow-up (2026-10-20)

- addressed 1 review step: `aot-clj-979-clj-1208-test` prints a map
  with one key for each check; plan "Key decisions" updated. Full
  suite exit 0 (64 tests, 332 assertions).

## Test-shaper review, pass 2 (2026-10-20)

- no new steps

## Docs review (2026-10-20)

- added 1 step to be addressed

## Docs review follow-up (2026-10-20)

- addressed 1 review step: `risks-and-evolution.md` mitigation says
  "Pin Clojure 1.12.6 (`deps.edn`)".

## Docs review, pass 2 (2026-10-20)

- added 1 step to be addressed

## Docs review follow-up, pass 2 (2026-10-20)

- addressed 1 review step: `embodiment.md` "Project artifacts" says
  "Clojure 1.12.6".

## Docs review, pass 3 (2026-10-20)

- no new steps

## Code-shaper review (2026-10-20)

- added 1 step to be addressed

## Code-shaper review follow-up (2026-10-20)

- addressed 1 review step: `value-type.test-support/run-in-new-jvm`
  (`dir form` → `{:exit :result :err}`) owns the classpath and the
  EDN read; both AOT tests use it; `run-java` is private (no other
  caller); unused `clojure.edn`/`clojure.string` requires removed.
  Full suite exit 0 (64 tests, 332 assertions; `JAVA_CMD` = JEP 401
  JDK).

## Code-shaper review, pass 2 (2026-10-20)

- added 1 step to be addressed

## Code-shaper review follow-up, pass 2 (2026-10-20)

- addressed 1 review step: `run-in-new-jvm` reads `:result` only when
  `:exit` is 0 (else nil), so a thrown check form gives a failed
  `(= 0 exit)` assertion with the stderr, not an EDN read error.
  Checked: a throwing form gives `:exit 1`, `:result nil`, cause in
  `:err`. Full suite exit 0 (64 tests, 332 assertions; `JAVA_CMD` =
  JEP 401 JDK).

## Code-shaper review, pass 3 (2026-10-20)

- added 1 step to be addressed

## Code-shaper review follow-up, pass 3 (2026-10-20)

- addressed 1 review step: `run-in-new-jvm` uses `(= 0 exit)`, so a
  `::timeout` exit gives `:result` nil and a failed `(= 0 exit)`
  assertion with the stderr; the docstring states the timeout exit.
  Full suite exit 0 (64 tests, 332 assertions; `JAVA_CMD` = JEP 401
  JDK).

## Code-shaper review, pass 4 (2026-10-20)

- added 1 step to be addressed

## Code-shaper review follow-up, pass 4 (2026-10-20)

- addressed 1 review step: `aot-clj-979-clj-1208-test` binds the class
  names with `support/binary-name` (`x`, `X`, `T`); `pkg` removed.
  Full suite exit 0 (64 tests, 332 assertions; `JAVA_CMD` = JEP 401
  JDK).

## Code-shaper review, pass 5 (2026-10-20)

- no new steps
