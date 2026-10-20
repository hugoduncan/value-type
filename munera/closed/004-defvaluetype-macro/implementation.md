# Implementation

## Design review

- 2026-10-20 architecture review added 2 new design steps. Sources:
  `AGENTS.md`, `ramora/INDEX.md`, `ramora/defvaluetype-design/*`
  (`ramora/META.md` and `doc/architecture.md` do not exist). Both
  steps are architecture-document alignment within scope item 5; no
  structural misfit: expansion (D1), `core → loader` dependency, and
  the boundary invariants fit.
- 2026-10-20 ambiguity review added 3 new design steps. Fact for the
  validation step: Clojure 1.12.6 `validate-fields` also throws (a
  `CompilerException` that wraps an `AssertionError`) when a field is
  not a symbol, after the reserved-name check.
- 2026-10-20 inconsistency review added 4 new design steps.

## Notes for the design-steps pass

- Principles: change only design.md text and its "Documentation" list;
  scope is frozen; no step needs a `loader`/`transform` change. Prefer
  the smaller macro and narrower doc rules (AGENTS.md, design Intent).
- Group the architecture-doc steps: step 1 (failure rule) and step 2
  (`core` boundary) both edit `qualities.md`; step 2 and the D1 step
  both add to `decisions.md`/`embodiment.md` items. One edit per
  "Documentation" bullet.
- Message step: causes of a non-value class that the messages must
  cover: no LOADER binding; `(eval (macroexpand form))`; nested
  `defvaluetype`; JVM without value classes (JDK 25, any binding).
- AOT step: `test/value_type/impl/loader_test.clj` (`aot-compile`,
  `aot-class-file`) has the in-JVM AOT recipe to reuse; the new-JVM
  part has no prior code in the repo.
- `value-type.environment-test` (`test/value_type/environment_test.clj`)
  detects `Class::isValue` with `.getMethod` and no warning: a pattern
  for the JDK 25 check in `check-value-class`.
- Clojure source for facts: `clojure/core_deftype.clj` in
  `~/.m2/.../clojure-1.12.6.jar` (`validate-fields`, line ~295).

## Design-steps pass (2026-10-20)

- All 9 design steps done in design.md. No `SCOPE_QUESTION:` items.
- Choices made beyond the step text:
  - Validation checks only the symbol elements of a vector `fields`
    (smaller code: no `every? symbol?` guard).
  - The Error clarity fitness condition in `qualities.md` also says
    "each error has `:value-type/error`"; narrowed with Correctness.
  - Invariant on reflection warnings aligned with acceptance
    criterion 3: `core-test` need not load warning-free on JDK 25.
  - README lib name `org.hugoduncan/value-type` follows the scry
    group; it is only the consumer's `deps.edn` key.
- AOT new-JVM test (Verification 8): the fixture `ns` must require
  `value-type.core`. Without it, the AOT `__init` calls an unbound
  `check-value-class` var in the new JVM. `clojure.main -e` exits 1
  on an uncaught exception. Not yet checked: that `java.class.path`
  under `clojure -M:test` and under nREPL holds the full classpath.

## Design review, pass 2

- 2026-10-20 architecture review added 1 new design step (the
  `transform` boundary sentence in `embodiment.md` conflicts with the
  predicate in `core`). Checked and not raised: the embodiment public
  "Errors" line (lists only `value-type` errors; not a misfit), the
  `<installed>` literal under AOT (a fresh-JVM load runs the check with
  `true`, which is correct), the risks table (it is about the class
  file only).
- 2026-10-20 ambiguity review (pass 2) added 2 new design steps.
- 2026-10-20 no inconsistency review feedback (pass 2).

## Notes for the design-steps pass 2

- Open steps: 3 (`transform` boundary, reserved-name error, plan
  item). Each edits only design.md; the pass-1 principles still apply.
- `transform` boundary: edit the existing `embodiment.md` bullet in
  "Documentation"; one bullet per document. Text to change:
  `ramora/defvaluetype-design/embodiment.md` "Boundaries" (~line 85).
  Match the wording of the Changeability condition
  (`qualities.md` line 14).
- Reserved-name error: reproduce with
  `clojure -M -e "(try (eval '(deftype Foo [__hash])) (catch Throwable t (prn (map class (take-while some? (iterate ex-cause t))))))"`
  → `(CompilerException AssertionError)`. Also put this case in
  "Error contract" ("How the caller gets the error"), so that it agrees
  with Verification 7.
- Plan item: the munera protocol removes a task from `munera/plan.md`
  when the task moves to `closed/`. "Remove the entry on close" follows
  the protocol.

## Design-steps pass 2 (2026-10-20)

- All 3 open design steps done in design.md. No `SCOPE_QUESTION:`
  items. All design steps are now checked.
- Checked on Clojure 1.12.6 (JDK default): `eval` of a reserved-name
  `deftype` form, direct or inside a macro `let`, and of a non-symbol
  field (`[a 1]`) each give the cause chain
  `(CompilerException AssertionError)`. Test idiom:
  `(map class (take-while some? (iterate ex-cause t)))`.
- `munera/plan.md` has only this task; on close the file keeps the
  `# Plan` heading with no entries.

## Design review, pass 3

- 2026-10-20 no architectural review feedback. Checked and not raised:
  the `risks-and-evolution.md` risks table and assumption 5 (the
  reflection API change now touches `core`; the "When an assumption
  becomes false" rule already covers it), the embodiment "Components"
  and "Invariants" tables (still true with `check-value-class` in
  `core`), the public `check-value-class` (a chosen, documented API
  surface), the nested `defvaluetype` limitation (fails loudly; the
  `:not-value-class` message names it).
- 2026-10-20 no ambiguity review feedback (pass 3). Not raised: "no
  reflection warnings" (acceptance 3) follows the project rule that
  each namespace sets `*warn-on-reflection*` true; reuse versus copy
  of `aot-compile` from `loader-test` is an implementation choice.
- 2026-10-20 inconsistency review (pass 3) added 1 new design step
  (`:not-value-class` message has no fix; Error clarity target needs
  one).

## Notes for the design-steps pass 3

- Open step: 1 (`:not-value-class` fix). Edit only the
  `:not-value-class` row of "Error contract" in design.md. Keep the
  JDK 25 rule as it is: on a JVM with no `Class::isValue`, the message
  names only the missing value-class support and gives no compile-path
  fix.
- Fix per cause: `(eval (macroexpand form))` → evaluate the
  `defvaluetype` form directly (same fix as `:not-installed`); nested
  `defvaluetype` → move the inner form to the top level (no loader
  change, so scope stays frozen).
- Only the message changes. Do not add data keys, and do not change
  the Verification 7 assertions (they check keys, not message text).
- No "Documentation" edit is needed: the Error clarity target in
  `ramora/defvaluetype-design/qualities.md` (line 16) already says
  "names the cause and the fix".

## Design-steps pass 3 (2026-10-20)

- The 1 open design step is done: the `:not-value-class` row of "Error
  contract" now gives a fix per known cause. No `SCOPE_QUESTION:`
  items. All design steps are checked.
- Only the message text changed: no new data keys, no Verification or
  "Documentation" change. The JDK 25 rule (no compile-path fix) stays.
- The nested-form fix ("move the inner form to the top level") needs
  no loader change, so the "Out of scope" item stays true.

## Design review, pass 4

- 2026-10-20 no architectural review feedback. The only change since
  pass 3 is the `:not-value-class` message text (a fix per cause). It
  fits the Error clarity target and needs no loader change.
- 2026-10-20 no ambiguity review feedback (pass 4). The new
  `:not-value-class` fix text names one fix per known cause; the
  Verification 7 assertions check keys, not message text.
- 2026-10-20 no inconsistency review feedback (pass 4). Re-checked
  against `assertions.md` (A-11, A-16, A-20, A-23 are the macro claims,
  all `intended`), `purpose.md` "Failure" and `decisions.md` D8/D9:
  each difference is in the "Documentation" list.

## Notes after design review, pass 4

- Pass 4 added no design steps; all design steps are checked. The
  design is stable: the next lifecycle step is the plan
  (`create-task-plan`), not a design-steps pass.
- Keep for the plan: do not edit design.md to fit the plan; a change
  of approach goes in plan.md. Scope stays frozen.

## Plan review, pass 1

- 2026-10-20 ambiguity review added 1 new design step (`:not-installed`
  test thread recipe).
- Facts checked (Clojure 1.12.6): `Compiler.eval` always binds
  `LOADER`, so in a REPL run (acceptance 2) the test thread has a
  binding; under `-m scry.cli` it may not. Thus a wrong thread recipe
  can pass on the CLI and fail at the REPL.
- Plan risk "`java.class.path` holds the full classpath": under
  `clojure -M:test` it is the full classpath (`test:src:<jars>`).
  `test` and `src` are relative, so the new JVM must keep the working
  directory of the test JVM (the `ProcessBuilder` default). nREPL
  started by the same CLI (`-M:test:nrepl`) gives the same property.
- Not raised: `aot-compile` in `loader-test` is private and writes
  `(ns ns-sym)` with no `:require`, so Slice 5 must adapt it (copy vs
  reuse is an implementation choice, see pass 3); the timeout value
  and the slice-1 value-class fixture (a `vt-type`-like test macro)
  are implementation choices.
- 2026-10-20 no inconsistency review feedback (plan, pass 1). Not
  raised: plan Slice 7 and steps.md omit the design "Documentation"
  item `munera/plan.md`. The rule applies only when the task moves to
  `closed/`, and the munera transition already removes the entry, so
  the implementation slices need no step. The plan risk on
  `java.class.path` is stale (checked above); the Slice 5 fallback step
  is still harmless.

## Notes for the design-steps pass (plan review, pass 1)

- Open step: 1 (`:not-installed` thread recipe). Edit only plan.md (the
  `:not-installed` Risks bullet) and the Slice 4 `:not-installed` item
  in steps.md. Do not edit design.md: the design is stable, and
  Verification 7 already gives the behaviour.
- Patterns to cite: `no-binding-test` (raw `Thread`, `promise`,
  `deref` with a timeout) at
  `test/value_type/impl/loader_test.clj:135`, and `test-ns` /
  `eval-here` (lines 51, 58) for the `*ns*` binding.
- The recipe must work under both runners: a REPL run (`LOADER` is
  bound on the test thread) and `-m scry.cli`.

## Plan-steps pass (plan review, pass 1, 2026-10-20)

- The 1 open step is done: plan.md Risks and the steps.md Slice 4
  `:not-installed` item now give the raw-`Thread` recipe. design.md
  not changed. All design steps are checked.
- Why the recipe works: the `Thread` binds `*ns*` so that `install!`
  and the `deftype` (expanded later by `eval` on the test thread, also
  with `*ns*` bound) use the same binary name. `eval` binds a fresh
  `LOADER` (not the capture loader), so the class is an identity class
  and the `false` literal gives `:not-installed`.
- Build the test form with syntax quote (qualified
  `value-type.core/defvaluetype`), so that `macroexpand` does not
  depend on the refers of the bound `*ns*`.

## Plan review, pass 2

- 2026-10-20 ambiguity review added 1 new design step (reflection
  warning check of acceptance 3). Why actionable: a `require` in a REPL
  that already loaded the namespace, or a `core` with no
  `*warn-on-reflection*`, prints nothing; the check cannot fail. The
  JDK 25 run (Slice 8) is the case that matters: there `(.isValue cls)`
  reflects. Not raised: the timeout value, the slice-1 value-class
  fixture, `aot-compile` copy vs reuse (see pass 1).
- 2026-10-20 no inconsistency review feedback (plan, pass 2). Not
  raised: plan "Slice order" item 6 names acceptance 1–3, but steps.md
  Slice 6 also does the acceptance 4 review (a label only); the stale
  `java.class.path` plan risk (see pass 1).

## Notes for the design-steps pass (plan review, pass 2)

- Open step: 1 (reflection-warning check). Edit only plan.md and
  steps.md (Slice 1 `check-value-class` item, Slices 6 and 8). Do not
  edit design.md: invariants and acceptance 3 already give the rule.
- Pattern: `(set! *warn-on-reflection* true)` after the `ns` form, as
  `src/value_type/impl/loader.clj:9` and `transform.clj:19`.
- Keep the check manual (a command and a stderr read), not a new test:
  Verification 1–8 is the fixed test list.
- JDK 25 (Slice 8): load only `value-type.core`; acceptance 3 needs no
  `core-test` load there. The `:test` alias adds `--enable-preview`.
  Select the JDK with `JAVA_CMD`, not `JAVA_HOME`
  (`mementum/memories/valhalla-jdk-selection-java-cmd.md`).

## Plan-steps pass (plan review, pass 2, 2026-10-20)

- The 1 open step is done: plan.md (Approach item 1, Risks, Slice
  order 6 and 8) and steps.md (new Slice 1 `set!` item, Slices 6 and
  8) give the new-JVM reflection-warning recipe. design.md not changed.
  All design steps are checked.
- Checked (Clojure 1.12.6, `clojure -M -e "(require 'ns)"`): a
  reflection warning goes to standard error and the exit code stays 0.
  Thus the check must read stderr; exit 0 alone proves nothing.
- Scratch file is `/tmp/vt-err.txt`, not in the repo.

## Plan review, pass 3

- 2026-10-20 no ambiguity review feedback (plan, pass 3). Not raised
  (implementation choices or covered by notes): steps Slice 1 "nil
  method" (`.getMethod` throws `NoSuchMethodException`; catch it, as
  `environment-test`); cache of the `isValue` `Method`; the
  `LOADER`-unchanged check (`with-bindings` pattern,
  `loader_test.clj:149`); JDK paths as `JAVA_CMD` placeholders (see
  `valhalla-jdk-selection-java-cmd.md`); Verification 8 `<form>` must
  end in `prn` (nil), so `clojure.main -e` prints no extra line.
- 2026-10-20 no inconsistency review feedback (plan, pass 3). Checked
  each Verification 1–8 item, acceptance 1–7, the "Documentation" list
  and the invariants against plan slices and steps.md: each has a step.
  Known drifts (not raised, see passes 1–2): Slice order 6 label, stale
  `java.class.path` risk, `munera/plan.md` doc item.

## Notes after plan review, pass 3

- Pass 3 added no design steps; all design steps are checked. Plan and
  steps are stable: the next lifecycle step is implementation
  (`implement-task`), not a plan-steps pass.
- Keep: do not edit design.md or plan.md to fit the code; a change of
  approach goes in plan.md with a note here.
- JDK paths: `/Library/Java/JavaVirtualMachines` has only
  `temurin-8.jdk`. Find the Valhalla and JDK 25 homes through mise or
  the user's `.psi/project.local.edn` (not committed); set `JAVA_CMD`.

## Implementation pass 1 (2026-10-20): slices 1–2

- `value-type.core`: `check-value-class` caches the `isValue` `Method`
  in a private `delay` (nil on JDK 25) and calls `.invoke` (no
  reflection warning). Message text is built in private `error-message`;
  on a JVM with no `isValue` it names only the missing support.
- `defvaluetype` validates before `install!` (so `:mutable-field` does
  not change `LOADER`); `installed` coerced with `boolean`.
- `core-test`: value-class fixture for `check-value-class` is a private
  `loader-value-type` macro (calls `loader/install!` directly), eval'd
  in the test ns. Field access on instances needs `^Point` hints.
- Full suite on Valhalla JDK (`/Users/duncan/.local/jdks/jdk-27.jdk`):
  34 tests pass, no reflection warnings in test output.

## Implementation pass 2 (2026-10-20): slices 3–4

- Deviation (design Verification 4 updated): a class-hinted field
  (`^String s`) is an `Object` field, as in `deftype`; only primitive
  hints change the field type. Test asserts `[long double Object]`.
- `eval` wraps a macro `ex-info` in `CompilerException`; test helper
  `error-data` now finds the first `ExceptionInfo` in the cause chain.
- `:not-installed` raw-`Thread` recipe works under `-m scry.cli`; REPL
  run not yet checked (Slice 6).
- Full suite on Valhalla JDK: 42 tests, 243 assertions pass.

## Implementation pass 3 (2026-10-20): slice 5

- `aot-new-jvm-test` in `core-test` has its own `aot-compile` (takes a
  source string; `loader-test` one is private and writes a bare `ns`).
- The class name in the new-JVM `-e` form must be munged
  (`value_type.aot_fixture.Pt`); the dashed name gives
  `ClassNotFoundException`.
- Last stdout line compared as the string `"[true 42]"` (no
  `read-string` on empty output when the JVM fails).
- `java.class.path` under `clojure -M:test -m scry.cli` was complete;
  no fallback needed. Do not alias `clojure.string` as `str` (shadows
  `clojure.core/str`).
- Full suite on Valhalla JDK: 43 tests, 246 assertions pass.

## Implementation pass 4 (2026-10-20): slice 6

- Valhalla JDK (`/Users/duncan/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`):
  `-m scry.cli` exit 0, 43 tests; `scry.core/run` of `core-test` in
  `-e` (Compiler.eval binds LOADER) → `:pass? true`, `:var-count` 12
  = 12 `deftest`; new-JVM require of `core-test` → exit 0, no
  `Reflection warning`.
- Boundary grep: only hit is the text "Compiler/LOADER" inside the
  `:not-value-class` message string; no code use.

## Implementation pass 5 (2026-10-20): slices 7–8

- Docs updated: README "Usage" / "Differences from `deftype`"
  (example checked on Valhalla JDK: `true true "(1,2)"`); ramora
  decisions D1 D8 D9, embodiment, qualities, purpose, assertions
  A-11 A-16 A-20 A-23 → observed; design Status; mementum/state.md.
- `defvaluetype` docstring now lists all semantic differences from
  `qualities.md` (acceptance 6).
- JDK 25 manual run (Temurin 25.0.4.1, downloaded to /tmp/jdk25):
  `require 'value-type.core` exit 0, 0 `Reflection warning` lines
  (acceptance 3 JDK 25 part). `eval` of `(defvaluetype P25 [^long x])`
  → `:not-value-class`, `:class-name "user.P25"`, message "…The JVM
  has no value classes (Class::isValue is missing). Use a JEP 401 JDK
  with --enable-preview." (acceptance 5).
- Remaining (task close, not implementation): move task to
  `munera/closed/` and drop its `munera/plan.md` entry.

## Implementation review (2026-10-20)

- Added 5 steps. Full suite re-run on the Valhalla JDK: 43 tests, 246
  assertions pass. Extra checks (not raised): a hyphenated name
  (`Foo-Bar`) gives a value class; a nested `defvaluetype` gives
  `:not-value-class` for the outer class.

## Implementation review follow-up (2026-10-20)

- Addressed 5 review steps. New `value-type.test-support`: `vt-type`
  fixture (replaces `loader-value-type`), `value?`, `eval-in`,
  `binary-name`, temp-dir helpers, `compile-cause`, `aot-compile`
  (source string). `aot-new-jvm-test` reads stdout with
  `edn/read-string`. `mutable-field-test` asserts `:mutable-field` in
  the `LOADER` case. Docs name `identical?` / Java `==` (`acmp`), not
  `clojure.core/==`. `mementum/state.md` corrected.
- Valhalla JDK: 43 tests, 247 assertions pass; new-JVM require of
  both test namespaces: 0 `Reflection warning` lines.

## Implementation review, pass 2 (2026-10-20)

- Added 1 step. Valhalla JDK: 43 tests, 247 assertions pass.

## Implementation review, pass 2 follow-up (2026-10-20)

- Addressed 1 review step. `core-test` eval'd fixtures use `gensym`
  names (one name for both redefinition forms; the `defn` var too);
  expected `:class-name` from `support/binary-name` (local
  `class-name` helper). Valhalla JDK: 43 tests, 247 assertions pass;
  new-JVM require: 0 `Reflection warning` lines.

## Implementation review, pass 3 (2026-10-20)

- Added 1 step. Valhalla JDK: 43 tests, 247 assertions pass.

## Implementation review, pass 3 follow-up (2026-10-20)

- Addressed 1 review step. `aot-new-jvm-test` uses a `gensym` class
  name in the source string and the `-e` form; class name from
  `support/binary-name`. Valhalla JDK: 43 tests, 247 assertions pass;
  new-JVM require: 0 `Reflection warning` lines.

## Implementation review, pass 4 (2026-10-20)

- No new steps. Valhalla JDK: 43 tests, 247 assertions pass.

## Test review (2026-10-20)

- Added 2 steps. Valhalla JDK: 43 tests, 247 assertions pass.

## Test review follow-up (2026-10-20)

- Addressed 2 review steps. `compile-path-test` asserts each result
  is `identical?` to the class of its name (`ns-resolve`); the `defn`
  case calls the fn with `*ns*` bound to the test namespace, as the
  `deftype` import runs at the call, in `*ns*`. Error tests check
  message substrings (`message-names?`): class name and mutable
  field names; class name and "evaluate the defvaluetype form
  directly". Valhalla JDK: 43 tests, 251 assertions pass; new-JVM
  require: 0 `Reflection warning` lines.

## Test review, pass 2 (2026-10-20)

- No new steps.

## Test review, pass 3 (2026-10-20)

- Added 2 steps. Valhalla JDK: 43 tests, 251 assertions pass.

## Test review, pass 3 follow-up (2026-10-20)

- Addressed 2 review steps. `mutable-field-test`: one form per case
  (fields `a` `mut-b`; `mut-a` `mut-b`), one `error-info` result for
  `ex-data` and message. Each `message-names?` assertion has the
  `ex-message` as the `is` message. `the-class?` replaced by
  `is-the-class`: two assertions (`value?`; `identical?` to the
  resolved class). Valhalla JDK: 43 tests, 254 assertions pass;
  new-JVM require: 0 `Reflection warning` lines.

## Test review, pass 4 (2026-10-20)

- No new steps.

## Docs review (2026-10-20)

- Added 2 steps. No `CHANGELOG.md` or `doc/` in the repo.

## Docs review follow-up (2026-10-20)

- Addressed 2 review steps. `qualities.md` and the `defvaluetype`
  docstring: added "fields that hold references compare by reference".
  README: added how the caller gets each error (`CompilerException`
  cause, `ex-cause`). Valhalla JDK: 43 tests, 254 assertions pass;
  0 `Reflection warning` lines.

## Docs review, pass 2 (2026-10-20)

- Added 2 steps.

## Docs review, pass 2 follow-up (2026-10-20)

- Addressed 2 review steps: `state.md` status parenthesis; A-01 to `observed`.

## Docs review, pass 3 (2026-10-20)

- Added 1 step.

## Docs review, pass 3 follow-up (2026-10-20)

- Addressed 1 review step: README, `defvaluetype` docstring,
  `qualities.md`, `purpose.md` (and the testing knowledge note) now
  split reference fields: identity object → by reference; value object
  (boxed `Long` under `--enable-preview`, `defvaluetype` instance) → by
  field values. Checked on Valhalla JDK: `true true false`. Suite:
  43 tests, 254 assertions pass; 0 `Reflection warning` lines.

## Docs review, pass 4 (2026-10-20)

- Added 2 steps.

## Docs review, pass 4 follow-up (2026-10-20)

- Addressed 2 review steps: `purpose.md` "Behaviour" has the `double`
  bit-pattern item; README `:not-value-class` gives the fix for each
  cause.

## Docs review, pass 5 (2026-10-20)

- Added 2 steps.

## Docs review, pass 5 follow-up (2026-10-20)

- Addressed 2 review steps: README "How you get the error" names
  `:jvm-rejected` and `:unexpected-constructor` as `CompilerException`
  causes (analysis); `decisions.md` D9 gives the fix for each
  `:not-value-class` cause.

## Docs review, pass 6 (2026-10-20)

- No new steps.

## Code shape review (2026-10-20)

- Added 3 steps.

## Code shape review follow-up (2026-10-20)

- Addressed 3 review steps: private `check-fields` holds the
  `:mutable-field` check (uses the `mutable-fields` vector directly);
  `reserved-field-test` uses `support/compile-cause`; `core-test`
  imports `Compiler`, `IDeref`, `RT`, `List`, `TimeUnit`.
  43 tests, 254 assertions pass on the Valhalla JDK; 0 `Reflection
  warning` lines.

## Code shape review, pass 2 (2026-10-20)

- Added 3 steps.

## Code shape review, pass 2 follow-up (2026-10-20)

- Addressed 3 review steps: `support/on-raw-thread` (default timeout
  10000 ms) used by `not-installed-test` and `no-binding-test`;
  `check-fields` holds the `vector?` guard; `clojure.string` alias is
  `str` in both test namespaces. 43 tests, 254 assertions pass on the
  Valhalla JDK; 0 `Reflection warning` lines.

## Code shape review, pass 3 (2026-10-20)

- Added 2 steps.

## Code shape review, pass 3 follow-up (2026-10-20)

- Addressed 2 review steps: private `value-class?` holds the
  `isValue` predicate; `check-value-class` is the check and throw;
  `defvaluetype` uses the `install!` result with no `boolean`. 43 tests,
  254 assertions pass on the Valhalla JDK; 0 `Reflection warning` lines.

## Code shape review, pass 4 (2026-10-20)

- Added 1 step.

## Code shape review, pass 4 follow-up (2026-10-20)

- Addressed 1 review step: `transform-test` uses `support/value?`;
  private `is-value?` removed. 43 tests, 254 assertions pass on the
  Valhalla JDK; 0 `Reflection warning` lines.

## Code shape review, pass 5 (2026-10-20)

- No new steps.
