# Design steps

- [x] Architecture: name the policy owner interface in `## Architecture`.
  Use the D14 owner pattern: one zero-arity accessor in `impl.runtime` over a
  private delay. `enforce-fallback!`, the guards and `report` read only this
  accessor. It is also the test replacement point. State what it returns
  (keyword or string) and where the translation to `:silent`/`:warn`/`:error`
  occurs, so that `value-type.capability` does not duplicate policy logic.
- [x] Architecture: complete the doc list in Acceptance 6 with the
  architecture sources that describe load-time policy or the old dependency
  graph. `ramora/defvaluetype-design/embodiment/runtime.md`: component rows
  for `value-type.capability` ("Reads no policy") and `impl.runtime`
  ("Initialized policy"), the dependency direction (add
  `value-type.capability → impl.runtime`), and the fallback boundary text.
  Assertion A-34 ("initialized policy"). The `defvaluetype` docstring ("before
  loading") and its `api_docs_test` strings. README fallback text.
- [x] Ambiguity: state what happens after an invalid first read. Either later
  reads throw the same fixed failure (a later property fix has no effect) or
  each read tries the property again until one is valid. Also state the
  exception that tests expect: plain `IllegalArgumentException` from
  `report`; from a definition, `Compiler$CompilerException` with that cause
  (macroexpansion and evaluation), or another form.
- [x] Ambiguity: define "fallback decision" in `## Concepts` by call path.
  State that only an `impl.runtime` fallback guard call (unavailable
  value-class support or a fixed fallback artifact) and `report` read the
  policy. State that value-class definitions on a capable JVM and missing
  native non-nil field support alone do not read it. State whether `report`
  reads the policy before or after the probes, so that the result is known
  when both a probe defect and an invalid policy occur, and whether a `report`
  call that throws from a probe defect still fixes the policy.
- [x] Ambiguity: name the runtimes for Acceptance 1, 2 and 4: Java 8 only, or
  also preview-disabled EA3. In Acceptance 2, state that "falls back
  silently" means no standard error output.
- [x] Inconsistency: `## Qualities and risks` names only
  `invalid-policy-report-test` as a test that conflicts with the new
  `report` contract. Other tests also conflict with "four capability keys and
  `:fallback-policy`": `value-type.capability-test/report-translation-test`
  (exact four-key maps, named in the D14 commitment),
  `capability_matrix_checks` `owner-report` (compares `report` with the four
  owner accessors) and `api_docs_test` (expects "does not read the
  `value-type.fallback` policy" in the `report` docs). List them in the risk.
- [x] Ambiguity: Acceptance 6 names the old texts ("before the library
  loads", "before loading", "read and validated once", "fail at load time",
  usage "before dependencies load, not in `-main`") but not the new user
  guidance. State the rule that the README, the usage guide and the
  `defvaluetype` docstring give. For example: the policy applies if it is set
  before the first read (the first fallback decision or `report` call). A
  dependency that defines a type at top level on a runtime without
  value-class support makes that read when it loads. Thus the JVM option
  stays the recommended form, and a late `System/setProperty` is reliable
  only before any such load; `report` shows the policy in effect. Also state
  whether invalid values now "fail at the first read" in these texts.
- [x] Inconsistency: Scope excludes "changes to ... the guards", but
  `## Architecture` makes `enforce-fallback!` read the new accessor (keyword
  values), and the internal docstrings of `enforce-fallback!` and
  `enforce-fixed-fallback!` lose "initialized policy". D14 names
  `enforce-fallback!`, `guarded-deftype` and `guarded-deftype*` as "the
  fallback guards"; `## Concepts` names `enforce-fixed-fallback!`,
  `guarded-deftype` and `guarded-deftype*`. Do not edit Scope. In
  `## Architecture`, state that the guards change only in their policy source
  (the accessor) and docstrings, with no change to behaviour, call sites or
  messages. Use one guard set that agrees with D14.
- [x] Plan ambiguity: Slice 3 child-JVM steps (Acceptance 1, 2, 4 and the
  later-read step) name no namespace or runtime source. State one of:
  `value-type.runtime-test` (compatibility suite, `:selected` host only;
  per-runtime coverage needs a suite run on each host) or a cross-JVM test
  over `config/incompatible-receivers` and `config/receivers` (explicit Java
  8, preview-off and preview-on runtimes from one host). If the tests use
  `:selected`, state the expected result on a capable host (value class,
  no throw) or that the cell is skipped. Align the plan Risk "cross-JVM
  test infrastructure must be available" with the choice.
- [x] Plan ambiguity: the step "Move tests that rely on load-time
  validation" names no tests. Name them and their new form:
  `runtime-test/invalid-policy-test` (expects the throw at
  `(require 'value-type.impl.runtime)`; the require now succeeds and the
  throw moves to the first read) and `runtime-test/policy-test` (sets
  `"invalid-later"` after the require but before the first read; with
  first-read semantics, `enforce-fallback!` then throws
  `IllegalArgumentException`, so a first read must occur before the
  `System/setProperty` call). Add `policy-test` to the plan Risk list.
- [x] Plan ambiguity: the rewritten `invalid-policy-report-test` must show
  "before any probe", but the step gives no observable. State it, for
  example: the probe delays stay unrealized after the throw
  (`value-type.impl.capability/detected-capability` and the field and
  array probe delays), or an in-process `with-redefs` test in
  `value-type.capability-test` that records probe accessor calls. Also
  state the new `:loaded` check: `value-type.core` is absent;
  `value-type.impl.runtime` is now loaded.
- [x] Plan ambiguity: the later-read step says "throws the same exception".
  State the assertion: `identical?` throwable (the delay rethrows the
  cached instance; check inside the child form) or equal class and
  message. State the read paths: `report` then `report`, and `report`
  then a definition (`Compiler$CompilerException` whose cause is that
  exception).
- [x] Plan inconsistency: plan.md Key decisions and the Slice 1 docstring
  step change "initialized policy" to "policy in effect" in the
  `guarded-deftype` and `guarded-deftype*` docstrings. Those docstrings do
  not contain "initialized policy" (they say "Enforce fixed fallback
  policy"); only `enforce-fallback!` and `enforce-fixed-fallback!` do.
  Limit the edit to those two docstrings in plan.md and state that the
  `guarded-deftype` and `guarded-deftype*` docstrings stay unchanged.
- [x] Plan inconsistency: the Slice 3 `api_docs_test` step adds
  expectations for the new `defvaluetype` docstring text, but Slice 4
  writes that docstring and regenerates `doc/API.md`, which
  `api_docs_test` reads. State in plan.md that the `defvaluetype` string
  expectations change after the Slice 4 docstring and regeneration (or
  move that part to Slice 4), so the expected strings match the written
  text.
- [x] Plan ambiguity: the Slice 4 usage-guide step names three old texts
  and "the `:fallback-policy` report key", but not the capability report
  section texts that the new contract makes false. Name them in the step:
  `doc/usage.md` l.356 "`report` does not read the `value-type.fallback`
  policy" (remove; state that `report` reads the policy first and throws on
  an invalid value), the example result (l.316-320) and the key table
  (l.326-331), which get `:fallback-policy`. The plan Risk says "The
  Acceptance 6 list is the checklist", and that list does not name the
  l.356 text; extend the Risk to name it.
- [x] Plan ambiguity: the plan adds the cross-JVM namespace
  `value-type.fallback-policy-test`, but Slice 4 does not state whether
  `doc/development/runtime.md` changes. That guide gives a focused command
  and a coverage text for each cross-JVM matrix (for example "Verify the
  capability report": "Each report must equal the expected row"), and its
  child isolation rules list rule 2 "Fallback policy" (cited by the
  `runtime-test` comment "Policy is process-wide (isolation rule 2)") and
  rule 5 "First-event evidence" without the first policy read. State one:
  add a Slice 4 step (focused command and coverage text for
  `value-type.fallback-policy-test`, the `:fallback-policy` entry in the
  report-matrix text, and the first policy read as first-event evidence in
  rule 5), or state that the guide does not change and why.
