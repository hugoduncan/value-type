# Design steps

- [x] Architecture: align the failure rule with the D8 change. The
  design keeps the `deftype` errors (`AssertionError` for a reserved
  field name, the `deftype` syntax errors). But
  `ramora/defvaluetype-design/qualities.md` (Correctness fitness: "Each
  failure path gives `ex-info`") and `purpose.md` (Behaviour: "Failure:
  an `ex-info` with a `:value-type/error` key") state that each failure
  is an `ex-info`. Add these two documents to "Documentation", with the
  narrowed rule: each value-class failure gives `ex-info` with
  `:value-type/error`; a `deftype` syntax error gives the `deftype`
  error.
- [x] Architecture: record the new knowledge in `value-type.core`.
  `check-value-class` makes `core` know the value-class reflection API
  (`Class::isValue`, missing on JDK 25) and the JVM capability for the
  `:not-value-class` message. `embodiment.md` "Boundaries" says "`core`
  knows only the `deftype` syntax", and `qualities.md` Changeability
  puts Valhalla knowledge only in `transform`. Risk assumption 5 says
  this API can change per early-access build. Add to "Documentation":
  update the `core` boundary in `embodiment.md` (and the Changeability
  fitness condition in `qualities.md`) so that `core` owns the
  value-class predicate. Do not move the predicate (`loader` and
  `transform` changes are out of scope).
- [x] Ambiguity: the README "Usage" item says "the dependency", but
  the project has no release, no Maven coordinate and no git remote.
  `deps.edn` has no library name. State the coordinate form that the
  example shows (for example a `:git/url` + `:git/sha` dep, or
  `:local/root`), or a placeholder rule.
- [x] Ambiguity: "Validation" says the macro does not validate when
  `fields` "has elements that are not symbols". It is not clear if
  this skips the full check, or only the non-symbol elements. The
  error differs for `[^:volatile-mutable a 1]`: the `deftype` error
  (`validate-fields` rejects non-symbols, a fact not in "Context") or
  `:mutable-field`. State one rule.
- [x] Ambiguity: Verification 8 does not give the full classpath of
  the new JVM, or how the test reads the result. `value-type.core` is
  already loaded in the test JVM, so the AOT compile does not write
  its classes; the new JVM also needs Clojure and `src` (for example
  the current `java.class.path` plus the temporary directory). State
  the classpath, how the fixture namespace is compiled (the
  `Compiler/compile` source-string recipe in
  `mementum/knowledge/testing-clojure-generated-classes.md`, or a
  file), and the pass signal (exit code or printed data).
- [x] Inconsistency: the `:not-value-class` message names "the
  expansion was evaluated apart from its macroexpansion" when the JVM
  has `Class::isValue`. But "Out of scope" says that a nested
  `defvaluetype` also throws `:not-value-class`, with a different
  cause (the inner install replaced the outer capture loader). So the
  message names a false cause there. Likewise `:not-installed` on
  JDK 25 gives a fix (evaluate the form directly) that does not work.
  Make the message rules agree with all the causes that the design
  lists (for example "the capture loader did not catch the class",
  with the known causes as examples).
- [x] Inconsistency: the design changes the expansion (two-argument
  `check-value-class`, fully qualified class symbol, `<installed>`
  literal), but "Documentation" does not list D1 in `decisions.md`.
  D1 still shows `(let [] (clojure.core/deftype ...) (check Name)
  Name)`. Add D1 to the `decisions.md` item.
- [x] Inconsistency: "Out of scope" allows "one manual check on JDK 25
  (acceptance criterion 5)", but acceptance criterion 3 also needs a
  JDK 25 run (no reflection warnings when `value-type.core` loads).
  Name both criteria in the exception, or state that the criterion 5
  run also records the criterion 3 result.
- [x] Inconsistency: "Error contract" says a reserved field name gives
  the error of `deftype`. But "Order at macroexpansion" validates
  mutable fields first, so a form with a reserved name and a mutable
  field gives `:mutable-field`. Qualify the sentence (when the form
  has no mutable field).
- [x] Architecture: the `transform` boundary in `embodiment.md`
  ("Boundaries") says "A change in a Valhalla build changes only this
  namespace". The design puts the value-class predicate in `core`
  (`Class::isValue`, which can change in each early-access build, risk
  assumption 5). So a Valhalla build can also change `core`, and that
  sentence becomes false. The `embodiment.md` item in "Documentation"
  updates only the `core` boundary. Add the `transform` boundary
  sentence to that item: limit it to the class file format (flag
  values, version rule), as the Changeability fitness condition does.
- [x] Ambiguity: Verification 7 expects "the `AssertionError` of
  `deftype`" for a reserved field name (`__hash`). It does not say
  where the test finds it. Checked on Clojure 1.12: `eval` of the form
  (also inside a macro `let`) throws a `CompilerException` whose
  direct cause is the `AssertionError`. Add this fact to "Context",
  and state the assertion in Verification 7 (for example the
  `ex-cause` of the `CompilerException` is an `AssertionError`), as
  "Error contract" does for `:mutable-field`.
- [x] Ambiguity: the "Documentation" item "`munera/plan.md`: this
  task" does not state the change. `munera/plan.md` already lists this
  task. State the edit (for example: remove the entry when the task
  closes, or no change).
- [x] Inconsistency: the Error clarity target in
  `ramora/defvaluetype-design/qualities.md` is "The error names the
  cause and the fix". In "Error contract", the `:mutable-field` and
  `:not-installed` messages name a fix, but the `:not-value-class`
  message (JVM with `Class::isValue`) names only causes. Add a fix for
  each known cause to that row (for example: evaluate the
  `defvaluetype` form directly; do not put a `defvaluetype` in a
  method body of another `defvaluetype`), or state why this key has
  no fix.
- [x] Ambiguity (plan, Slice 4 `:not-installed` test): the step does
  not say how the new thread gets `*ns*` and no `LOADER` binding.
  Checked on Clojure 1.12.6 (`clojure -M -e`): `future` and `bound-fn`
  convey the `LOADER` binding of `eval` (`install!` gives `true`, so
  the test gets `:not-value-class`); a raw `Thread` has no `LOADER`
  binding but `*ns*` is `clojure.core`, so the install name and the
  `deftype` class name differ. State the recipe in plan.md and
  steps.md: a raw `Thread` (as `no-binding-test` in
  `value-type.impl.loader-test`) whose body binds only `*ns*` to the
  test namespace, then `macroexpand`; deliver the expansion with a
  `promise` and a timeout; `eval` it on the test thread with `*ns*`
  bound. No `future`, `bound-fn` or `binding-conveyor-fn`.
- [x] Ambiguity (plan, Slices 1, 6 and 8, reflection-warning check):
  the steps do not say how the check makes a warning visible. Slice 1
  sets `*warn-on-reflection*` true only in `core_test.clj`, not in
  `value-type.core` (`loader` and `transform` set it). When `core` does
  not set it, or when the check is a `require` in a REPL that already
  loaded `core`, the load prints no warning, so acceptance 3 passes
  with no check (on JDK 25 also). State in plan.md and steps.md: Slice
  1 adds `(set! *warn-on-reflection* true)` to `value-type.core`;
  Slices 6 and 8 load each namespace in a new JVM (for example
  `JAVA_CMD=<java> clojure -M:test -e "(require 'value-type.core-test)"`)
  and check that standard error has no `Reflection warning` line.
