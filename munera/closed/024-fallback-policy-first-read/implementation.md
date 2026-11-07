# Implementation

- 2026-11-06 design review (architecture): architectural review added 2 new design steps.
  - `ramora/META.md` and `doc/architecture.md` do not exist. Sources used:
    `ramora/INDEX.md`, D10 (`fallback.md`), D13 (`fallback/lifecycle.md`),
    D14 (`decisions/capability-report.md`), A-34, `embodiment/runtime.md`.
  - The design fits D10/D13/D14 (ownership, no cycle, no load of
    `value-type.core` from `report`). Findings are about the owner pattern and
    the doc set only.
  - A Clojure `delay` caches a thrown exception. An invalid value thus stays a
    failure after the first read; a later property fix has no effect. This
    agrees with "fixed at the first read".
- 2026-11-06 design review (ambiguity): ambiguity review added 3 new design steps.
- 2026-11-06 design review (inconsistency): inconsistency review added 1 new design step.
- 2026-11-06 notes for the design-steps slice:
  - Principles: scope is frozen. Edit design.md only; D10, D13 and D14 already
    state the target, so align design.md with them and do not edit the ramora
    decisions. Write contracts, not status (ramora/INDEX.md rules); use STE.
  - Facts: the policy is a string var today (`"silent"` etc.). Policy readers
    in `src/value_type/core.clj`: the unavailable branch of `policy-expansion`
    (`enforce-fallback! detail`), the identity receiving expansion
    (`enforce-fixed-fallback!` at analysis and evaluation), and the
    `guarded-deftype`/`guarded-deftype*` expansions. `report-translation-test`
    replaces owner accessors with `with-redefs`, so a redefinable policy accessor
    fits the same test style. The matrix `check` calls `report` first when the
    require order starts with `value-type.capability`, which matches Acceptance 2/3.
  - Paths: `src/value_type/impl/runtime.clj`, `src/value_type/capability.clj`,
    `src/value_type/core.clj` (`defvaluetype` docstring ~l.343),
    `test/runtime/value_type/capability_test.clj`,
    `test/compatibility/value_type/{runtime_test,capability_matrix_checks,cell_registry}.clj`,
    `test/tooling/value_type/api_docs_test.clj` (l.26, l.79),
    `doc/usage.md` (l.27, l.356), `README.md` (~l.163),
    `ramora/defvaluetype-design/embodiment.md` (gap table),
    `ramora/defvaluetype-design/embodiment/runtime.md`,
    `ramora/defvaluetype-design/assertions/fallback-architecture.md` (A-34).
- 2026-11-06 design steps executed (all 6; no `SCOPE_QUESTION:` items).
  - Accessor name chosen: `impl.runtime/fallback-policy` (matches the
    report key). The private string var `policy` goes away.
  - Checked on Clojure 1.12.6: a throw in a macro body gives
    `[CompilerException IllegalArgumentException]` for `eval`, `macroexpand`
    and `macroexpand-1`. A throw at evaluation of a top-level form is not
    wrapped (plain exception). Use `cell_harness` `source-rejection-chain`
    style for the definition case.
  - `api_docs_test` forbids "value-type.impl." in `doc/API.md`; the
    `report` docstring must not name `impl.runtime`.
  - `invalid-policy-report-test` also asserts `:loaded []` for
    `value-type.impl.runtime`; the new dependency breaks that check.
  - Internal docstrings of `enforce-fallback!` and `enforce-fixed-fallback!`
    say "initialized policy"; update them with the code.
  - runtime-test children run on the `:selected` configuration (the host
    JVM); per-runtime acceptance comes from running the suite on each host.
- 2026-11-06 design review pass 2 (architecture): no architectural review feedback.
  - Checked the revised design against D10, D13, D14, A-34, `embodiment/runtime.md`
    and current `impl.runtime`/`capability` sources. The accessor-over-delay owner,
    the `capability → runtime` edge (no cycle, no optional APIs) and the doc list agree.
- 2026-11-06 design review pass 2 (ambiguity): ambiguity review added 1 new design step.
  - Reason: on Java 8 or preview-disabled EA3, a top-level `defvaluetype` in a
    dependency makes the first read when that namespace loads. If the docs only
    say "set it before the first read", users can set it in `-main` and still
    get `silent`. This conflicts with priority 1.
- 2026-11-06 design review pass 2 (inconsistency): inconsistency review added 1 new design step.
  - Checked `core.clj`: `policy-expansion` calls `capability/capability` before
    `enforce-fallback!`. A definition thus runs the probe before the policy read;
    only `report` must read the policy first. The Contract agrees.
- 2026-11-06 notes for the pass-2 design-steps slice (2 open steps):
  - Principles: the same rules as the first slice. Also, the doc-guidance step
    must keep the JVM option as the main advice. D10 "Authority" ("before
    dependent namespaces load") stays true; do not contradict it. The guard step
    must not edit Scope; resolve it in `## Architecture` and `## Concepts` only.
  - Exact stale texts: `doc/usage.md` l.15 ("before dependencies load, not in
    `-main`") and l.27-28 ("read and validated once", "fail at load time");
    `README.md` l.173; `src/value_type/core.clj` l.343-345 (`defvaluetype`
    docstring).
  - Guard call sites in `src/value_type/core.clj`: l.261/l.264
    (`identity-receiving`, `enforce-fixed-fallback!`), l.289 (`policy-expansion`,
    `enforce-fallback!`), l.444/l.539 (`guarded-deftype`/`guarded-deftype*`
    emitted). D14 guard set: `decisions/capability-report.md` "Ownership".
- 2026-11-06 pass-2 design steps executed (both; no `SCOPE_QUESTION:` items).
  - Guard set: Concepts now uses the D14 set (`enforce-fallback!`,
    `guarded-deftype`, `guarded-deftype*`); `enforce-fixed-fallback!` is a
    helper. Scope text "changes to the guards" stays; Architecture limits the
    guard change to policy source and docstrings.
  - Doc rule is in Acceptance 6. The AOT case comes from the existing
    `defvaluetype` docstring: a top-level AOT fallback form enforces policy at
    namespace initialization, even on a capable JVM, so it is a first read.
  - Usage guide l.15 shows the JVM option example; keep it and replace only
    the "not in `-main`" clause.
- 2026-11-06 design review pass 3 (architecture): no architectural review feedback.
- 2026-11-06 design review pass 3 (ambiguity): no ambiguity review feedback.
  - Not filed: the Contract gives no exception form for an invalid first read
    in an AOT fallback artifact (namespace initialization or invocation, with
    no macroexpansion). The JVM/Clojure wrapping decides it; no acceptance item
    tests it, and the implementation is the same. Do not raise it again unless
    a test for it is added.
- 2026-11-06 design review pass 3 (inconsistency): no inconsistency review feedback.
  - Checked against source: the fallback messages do not contain the policy
    value, so the change to keyword values keeps the messages. Only
    `enforce-fallback!` and `enforce-fixed-fallback!` docstrings say
    "initialized policy"; the `guarded-deftype*` docstrings do not.
  - Not filed: Concepts "namespace loading ... do not read it" means the
    library namespaces (the Contract names them). A user namespace with a
    top-level fallback definition reads through the fallback-decision path,
    as the Acceptance 6 doc rule says.
- 2026-11-06 notes after design review pass 3:
  - No design steps are open; all three reviews passed. design.md is stable.
    The next step is the plan (create-task-plan), not a design-steps slice.
  - Doc line refs for the plan: `ramora/defvaluetype-design/embodiment.md`
    l.32-33 (D10/D14 gap rows); `embodiment/runtime.md` l.8-9 (component rows),
    l.19 (dependency direction), l.37 and l.65-66 ("initialized policy").
- 2026-11-06 plan review (ambiguity): ambiguity review added 4 new design steps.
  - Facts: `jvm_config/incompatible-receivers` gives Java 8 and preview-off
    runtimes; cross-JVM tests (`capability_matrix_test`) use explicit
    runtimes, compatibility tests (`runtime_test`) use only `:selected`.
  - `runtime-test/policy-test` breaks under first-read semantics: it sets
    `"invalid-later"` before the first `enforce-fallback!` call. It is not
    in the plan Risk list. Filed here as an ambiguity of the "Move tests"
    step; the inconsistency pass need not re-file it.
  - Left for the inconsistency pass: the steps change "initialized policy"
    in `guarded-deftype`/`guarded-deftype*` docstrings, but those docstrings
    do not contain that text (see pass-3 note).
- 2026-11-06 plan review (inconsistency): inconsistency review added 2 new design steps.
  - Checked: no D13 row in the embodiment gap table, so "remove D10, D14"
    is complete. Acceptance 6 doc list and Slice 4 steps agree.
- 2026-11-06 notes for the plan design-steps slice (6 open plan steps):
  - Principles: design.md is stable; resolve the plan steps in plan.md and
    steps.md only. Keep the slice order; move single items, not slices.
    Choose one test location per acceptance item and use it the same way in
    the plan Risks and in steps.md.
  - Runtime helpers: `test/compatibility/value_type/jvm_config.clj`
    (`receivers`, `incompatible-receivers`, `policies`, `:selected`);
    `test/cross-jvm/value_type/array_capability.clj` (`runtimes`, labels);
    the cross-JVM pattern is `capability_matrix_test.clj` (`harness/cell-child`
    with `(assoc runtime :policy p)`).
  - Probe delays for a "before any probe" observable (all private):
    `impl.capability/detected-capability` (l.113),
    `impl.field-capability/detected-field-capability` (l.57),
    `impl.array/detected-allocation-capability` (l.76),
    `impl.array/detected-inspection-capability` (l.135).
    `capability-test/report-defect-propagation-test` shows the in-process
    `identical?` style.
  - `capability_rows/report` is policy-independent and has other users
    (array tests). Add `:fallback-policy` in the expected maps of
    `capability_matrix_test` and `runtime-test`; do not change `rows/report`.
  - `runtime_test.clj` lines: `policy-test` l.52, `invalid-policy-test` l.76,
    `invalid-policy-report-test` l.~482. `api_docs_test.clj`: the
    `defvaluetype` strings are at l.24-34; the `report` string is at l.79.
- 2026-11-06 plan design-steps executed (all 6; plan.md and steps.md only).
  - Choice: acceptance 1, 2, 4 and later reads go to a new cross-JVM
    `value-type.fallback-policy-test` + `value-type.fallback-policy-checks`;
    `runtime-test` keeps only host-independent policy tests.
  - `api_docs_test` reads `value-type.api-docs/generated-markdown` (source
    docstrings), not `doc/API.md`; the plan item's premise was wrong, the
    fix (defvaluetype strings in Slice 4) still holds.
  - Found two more exact four-key report checks, not in design.md risks:
    `test/cross-jvm/value_type/controlled_value_capability_test.clj` and
    `test/build/build_test.clj` `consumer-reports` (l.277). Added to plan
    Risks and a Slice 3 step.
  - Harness facts: cells run through `load-string`, so a top-level `do`
    runs its `require` before later subforms compile. Per-cell `*err*` is
    expected empty unless the launch `:policy` is `warn`
    (`cell_harness/expected-err`); a property set inside the child needs
    its own `*err*` binding to observe the warning.
  - Checked on Clojure 1.12.6: a throwing `delay` rethrows the `identical?`
    instance and is then `realized?`.
- 2026-11-06 plan review pass 2 (ambiguity): ambiguity review added 2 new design steps.
  - Checked the resolved Slice 3 steps against `cell_harness`,
    `cell_registry`, `jvm_config`, `capability_matrix_checks`, the probe
    delays and the report test sites; no new test ambiguity. `:policy nil`
    children omit `-D` (`jvm_runner/start-command`), so `expected-err` is "".
  - Both findings are doc checklist gaps: the plan Risk makes Acceptance 6 the
    checklist, so an unnamed contradicting text (usage l.356) or an
    undocumented new matrix namespace stays stale.
- 2026-11-06 plan review pass 2 (inconsistency): no inconsistency review feedback.
  - Checked plan.md and steps.md against design.md, D10 (`fallback.md`
    Configuration and Verification), D14 and the other stale-text sites
    (`grep` of ramora, doc, README, src). The only unlisted stale text is
    usage l.356, which the ambiguity pass already filed. `embodiment/runtime/*.md`
    has no load-time policy text.
  - Not filed: D14 Ownership says tests read results through the accessor.
    The `invalid-policy-report-test` `realized?` check of the private probe
    delays does not read a result; it shows only that no probe ran. An earlier
    plan design step chose this observable.
- 2026-11-06 notes for the plan-review pass-2 design-steps slice (2 open steps):
  - Principles: the same as the first plan slice: edit plan.md and steps.md
    only; design.md stays stable (do not add to Acceptance 6). Put new
    doc items in Slice 4 steps and in the plan Risk checklist. Keep rule 2
    "Fallback policy" true: it means the launch property; first-read cells
    already get their own child in steps.md.
  - `doc/usage.md` "Capability report" (l.308): example result l.316-320,
    key table l.326-331, probe paragraph l.333-338, the "does not read"
    sentence l.356. The rest of l.356-359 (no core load, no JEP 401 needs,
    per-runtime cache) stays true.
  - `doc/development/runtime.md`: "Verified fallback matrix" l.59 (the full
    `:cross-jvm-test` command already runs a new namespace), "Child JVMs"
    isolation rules l.106-112, "Verify the capability report" l.423-452
    (focused-command pattern; "Each report must equal the expected row").
    The `runtime_test.clj` l.12 comment cites rule 2.
- 2026-11-06 plan-review pass-2 design steps executed (both; plan.md and
  steps.md only).
  - Usage guide: Slice 4 now has two steps (fallback section, capability
    report section). The capability report intro (l.310-312, "returns the
    optional JVM capabilities") was also stale; added to the step.
  - Runtime guide: chose to change it. New section "Verify the fallback
    policy" after "Verify the capability report"; 11 children
    (acceptance 1: 4, acceptance 2: 2, acceptance 4: 3 + 2). Rule 2 stays
    (launch property); rule 5 gets the first policy read. The sentence
    "The 'Verified fallback matrix' commands include the invalid-policy
    report check" (capability report section) stays true:
    `invalid-policy-report-test` stays in `runtime-test`.
  - Line refs checked: `doc/development/runtime.md` rule 2 l.107, rule 5
    l.110-112; "Each report must equal the expected row" wraps over two
    lines (grep the phrase across lines).
- 2026-11-06 plan review pass 3 (ambiguity): no ambiguity review feedback.
  - Checked every steps.md reference against source: the four probe delay
    names, `consumer-aot-checks/value-class?`, `harness/rejection-context`,
    `config/receivers`/`incompatible-receivers`, `cell-registry/failed-call`
    and `thrown`, the 11-child count, and a grep of all stale load-time texts
    (all in the Slice 4 checklist).
  - Not filed (implementation hint): in the Acceptance 1 `warn` check, define
    the types with `eval` inside the `*err*` binding, as
    `capability_matrix_checks/definition` does. A literal `defvaluetype` in
    the binding form expands when that form compiles, before the binding is
    in effect, so the warning would go to the cell `*err*`.
- 2026-11-06 plan review pass 3 (inconsistency): no inconsistency review feedback.
- 2026-11-06 notes after plan review pass 3:
  - No design steps are open; both plan reviews passed. plan.md and steps.md
    are stable. The next step is implementation (Slice 1), not a
    design-steps slice.
  - Do not re-raise the items set aside in earlier notes (AOT invalid-read
    exception form; `realized?` of the private probe delays versus D14
    accessor use).
- 2026-11-06 implementation pass 1 (Slices 1-3 done; Slice 4 docs and
  Slice 5 full verification remain).
  - `impl.runtime`: private `policy` is now a delay; public
    `fallback-policy` derefs it (keywords). `report` binds the accessor in
    `let` before the probe calls, and puts `:fallback-policy` first.
  - New `capability-test/report-policy-before-probes-test` (in-process,
    `with-redefs`): an invalid policy throws before any probe accessor runs.
  - `cell_harness/any-warning-line` is now public; the new
    `fallback-policy-test` uses it for the preview-off warn child.
  - `runtime-test` no longer requires `capability-rows`.
  - `fallback-policy-checks/invalid-report` returns
    `{:thrown :second-identical? :definition}`; the definition is `:value`
    on preview on, else `{:class CompilerException :cause-identical? true}`.
  - Verified: runtime suite (EA3 + export) 187/187; `runtime-test` on Java
    8, preview off, preview on; tooling 11/11; cross-JVM
    `capability-matrix-test`, `controlled-value-capability-test`,
    `fallback-policy-test` (11 children). Not yet run: build suite
    (`consumer-reports` changed), full cross-JVM and fallback suites.
  - `bb api-docs --check` fails until `doc/API.md` is regenerated (Slice 4).
- 2026-11-06 implementation pass 1, Slice 4 done.
  - Docs use the Acceptance 6 rule. `api_docs_test` `defvaluetype` strings
    added: "before the first read", "changes after the first read have no
    effect". `report` strings: ":fallback-policy", "fallback policy in
    effect", "reads the `value-type.fallback` policy before it runs a probe".
  - `bb vale`, `bb api-docs --check`, fmt, kondo and tooling pass.
  - Remaining: Slice 5 full suites (fallback suite per parent JVM, full
    cross-JVM, build).
- 2026-11-06 Slice 5 verification (all pass):
  - Runtime suite (EA3, preview + export) 187/187; tooling 11/11; build 9/9
    (EA3); fallback suite on Java 8 74/74 and EA3 preview off 74/74;
    cross-JVM (Java 8 parent) 103/103; fmt, kondo, vale, api-docs --check.
  - Fallback suite on EA3 preview on: 73 passed, 0 failed, 1 "unknown"
    (`identity-receiving-test/value-receiving-star-closed-option-order-test`).
    The same "unknown" occurs on the pre-task commit d2806d26, so it is not
    from this task. It is out of scope; a separate task can examine it.
- 2026-11-07 implementation review: added 3 steps to be addressed.
- 2026-11-07 addressed 3 review steps.
  - `fallback_policy_test`: explicit `[runtime label]` pairs from
    `config/configuration`; `:fallback?` and `:warning` removed.
  - Reflowed the listed paragraphs and the `report` docstring; `doc/API.md`
    regenerated.
  - Verified: `fallback-policy-test` 4/4 (44 assertions), tooling 11/11,
    vale, api-docs --check, fmt, kondo.
- 2026-11-07 implementation review pass 2: added 1 step to be addressed.
- 2026-11-07 addressed 1 review step (pass 2).
  - Reflowed `doc/usage.md` "Each capability value is ..." and the
    `embodiment/runtime.md` state item. `bb vale` passes.
- 2026-11-07 implementation review pass 3: added 1 step to be addressed.
- 2026-11-07 addressed 1 review step (pass 3).
  - D14 commitment evidence names `report-policy-before-probes-test` and
    `value-type.fallback-policy-test`. `bb vale` passes.
- 2026-11-07 implementation review pass 4: added 1 step to be addressed.
- 2026-11-07 addressed 1 review step (pass 4).
  - `README.md`: "startup property" → "property". `bb vale` passes.
- 2026-11-07 implementation review pass 5: no new findings.
- 2026-11-07 test review (task-test-review): added 2 steps to be addressed.
- 2026-11-07 addressed 2 test review steps.
  - Removed `report-policy-before-probes-test` and its D14 evidence entry.
  - `invalid-policy-report-test`: `:loaded` → `:core-loaded? false`.
  - Verified: runtime suite (EA3) 186/186; `value-type.runtime-test` on
    Java 8 and EA3 15/15; vale, fmt, kondo.
- 2026-11-07 test review pass 2 (task-test-review): no new findings.
- 2026-11-07 test review pass 3 (test-shaper): added 1 step to be addressed.
- 2026-11-07 addressed 1 test review step (pass 3).
  - `invalid-policy-test`: keeps only the load contract (requires succeed,
    `:loaded`); accessor call and `:thrown` expectation removed.
  - Verified: `value-type.runtime-test` on Java 8 and EA3 15/15; fmt, kondo.
- 2026-11-07 test review pass 4 (test-shaper): no new findings.
- 2026-11-07 docs review (review-task-docs): no new findings.
- 2026-11-07 code-shaper review: added 2 steps to be addressed.
- 2026-11-07 addressed 2 code-shaper review steps.
  - `fallback_policy_test`: expectations in the runtime rows
    (`[runtime label definition]`, `[runtime label context warning]`); no
    label comparisons.
  - `fallback_policy_checks`: `set-after-load` split into
    `set-after-load-error` and `set-after-load-warn`; `representation`
    helper shared by `definition` and `invalid-report`.
  - Verified: `fallback-policy-test` on Java 8 4/4 (44 assertions); fmt,
    kondo.
- 2026-11-07 code-shaper review pass 2: added 2 steps to be addressed.
- 2026-11-07 addressed 2 code-shaper review steps (pass 2).
  - `cell_harness/expected-warning` of a context, used by `expected-err`
    and `fallback_policy_test`; `any-warning-line` private; warning column
    removed.
  - `cell-registry/invalid-policy-message`, used by `runtime_test` and
    `fallback_policy_test`.
  - Verified: `fallback-policy-test` on Java 8 4/4 (44 assertions);
    `value-type.runtime-test` on Java 8 and EA3 15/15; fmt, kondo.
- 2026-11-07 code-shaper review pass 3: added 2 steps to be addressed.
- 2026-11-07 addressed 2 code-shaper review steps (pass 3).
  - `fallback_policy_test` `child`: `args` parameter removed; uses
    `(registry/check-form check)`.
  - `cell_registry` ns docstring reflowed.
  - Verified: `fallback-policy-test` on Java 8 4/4 (44 assertions); fmt,
    kondo.
- 2026-11-07 code-shaper review pass 4: added 1 step to be addressed.
- 2026-11-07 addressed 1 code-shaper review step (pass 4).
  - `invalid-report` `:definition`: `registry/outcome` with a local
    `failure-of` and `representation`; no `resolve`, no long line.
  - Verified: `fallback-policy-test` on Java 8 4/4 (44 assertions); fmt,
    kondo.
- 2026-11-07 code-shaper review pass 5: no new findings.
