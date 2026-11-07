# Fallback policy at first read

## Intent

Let an application set `value-type.fallback` after value-type loads, and
let it see the policy in effect. Realize defvaluetype D10 (first read), D13
(policy owner state) and D14 (`:fallback-policy` in the report).

Priorities, in order: a strict policy is never skipped silently; the report
agrees with the policy that definitions use; `silent` stays silent.

## Problem and context

`impl.runtime` reads and validates the property when the namespace loads.
An application that sets `error` with `System/setProperty` after a dependency
has loaded value-type gets `silent`, with no message. A strict failure is then
skipped silently (D10 F2). The capability report does not show the policy, so
the application cannot check it.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D10
(`fallback.md`), D13 (`fallback/lifecycle.md`), D14
(`decisions/capability-report.md`).

## Concepts

- Fallback guards (D14): `impl.runtime/enforce-fallback!`,
  `guarded-deftype` and `guarded-deftype*`. The helper
  `enforce-fixed-fallback!` calls `enforce-fallback!` for a fixed fallback
  artifact.
- Policy read: a call of the policy accessor `impl.runtime/fallback-policy`
  (see Architecture). Only two call paths read the policy:
  - A fallback decision: a call of `enforce-fallback!`. Callers: the
    unavailable branch of definition representation selection (value-class
    support is unavailable), and the fixed fallback artifacts through
    `enforce-fixed-fallback!` (the identity receiving expansion and the
    `guarded-deftype` and `guarded-deftype*` expansions, at analysis and at
    evaluation).
  - A `value-type.capability/report` call.
- No other path reads the policy. Namespace loading, value-class definitions
  on a capable JVM, missing native non-nil field support alone
  (`require-field-capability!`) and array functions do not read it.
- First read: the first policy read in the loaded library runtime.
- Policy in effect: the policy value fixed at the first read.

## Scope

Include:

- `impl.runtime` reads and validates the policy at the first read and fixes
  it for the loaded library runtime. Namespace loading reads no policy.
- `value-type.capability/report` adds `:fallback-policy` (`:silent`, `:warn`
  or `:error`). A `report` call is a first read.
- An invalid value throws at the first read, with the current message that
  names the valid values.
- Docs and design gap updates.

Exclude:

- Policy changes after the first read (not supported, D10).
- Any message under `silent`.
- Per-type policy; changes to the warning-once behaviour or the guards.

## Contract

- Loading `value-type.core`, `value-type.capability` or `impl.runtime`
  does not read `value-type.fallback`.
- A property value set before the first read applies.
- After the first read, a new property value has no effect.
- An invalid value at the first read fixes the failure. Each later read
  throws the same exception. A later property fix has no effect.
- Exceptions of an invalid value: a `report` call throws plain
  `IllegalArgumentException`. A definition through the compiler (`eval`,
  `load`, `macroexpand` or `macroexpand-1`) reads the policy first during
  macroexpansion and throws `Compiler$CompilerException` with that
  `IllegalArgumentException` as its cause.
- `report` returns the four capability keys and `:fallback-policy`. An
  invalid policy value makes `report` throw; there is no partial report.
- `report` reads the policy before it runs the probes. If the policy value
  is invalid, `report` throws the policy exception before it runs a probe.
  If a probe defect propagates, the policy is already read and fixed.
- `report` does not load `value-type.core`.
- Fallback behaviour (`silent`, `warn` once, `error` on each attempt) does not
  change.

## Architecture

- `impl.runtime` keeps sole ownership of the policy and the warning flag.
- Policy owner interface (D14 owner pattern): one public zero-arity accessor
  `impl.runtime/fallback-policy` over a private delay. The delay reads
  `value-type.fallback` (default `silent`), validates it and returns a
  keyword: `:silent`, `:warn` or `:error`. The translation from the property
  string to the keyword occurs only in this delay. An invalid value makes the
  delay throw; the delay keeps that exception.
- Every policy reader reads only this accessor: `enforce-fallback!` (and
  thus the fixed fallback artifacts) and `report`. `report` puts the
  accessor value unchanged in `:fallback-policy`; `value-type.capability`
  contains no policy logic. Tests replace the accessor with `with-redefs`,
  as for the D14 capability accessors.
- The fallback guards and `enforce-fixed-fallback!` change only in their
  policy source (the accessor, with keyword values) and in their docstrings
  ("initialized policy" becomes "policy in effect"). Their behaviour, call
  sites and messages do not change.
- `value-type.capability` gains a dependency on `impl.runtime`.
  `impl.runtime` still depends on no probe namespace. No cycle.

Rejected: a warning on drift (prints under `silent`); reading the property on
each decision (policy can change during a run).

## Qualities and risks

- Better: application control, diagnosability.
- Cost: an invalid value is found later. On a capable JVM with no fallback,
  only the report shows it.
- Risk: these tests assert the old `report` contract and must change to the
  new contract:
  - `value-type.runtime-test/invalid-policy-report-test`: `report` ignores
    the policy and does not load `impl.runtime`.
  - `value-type.capability-test/report-translation-test` (named in the D14
    commitment): exact four-key report maps.
  - `capability_matrix_checks` `owner-report`: compares `report` with a map
    of the four owner accessors only.
  - `api_docs_test`: expects "does not read the `value-type.fallback`
    policy" in the `report` docs.
- Risk: tests that rely on load-time validation must move to first-read
  validation.

## Acceptance

Runtimes without value-class support: Java 8 and Valhalla EA3 with preview
disabled.

1. Child-JVM tests on each runtime without value-class support: load
   `value-type.core`, set `error` with `System/setProperty`, then define an
   ordinary type: it throws the fallback Error. Same with `warn`: one warning.
2. On each runtime without value-class support: after a `report` call that
   shows `:silent`, setting `error` has no effect. A definition falls back
   silently: it defines an identity class and writes nothing to standard
   error.
3. `value-type.capability-matrix-test` checks `:fallback-policy` in each
   cell against the cell policy.
4. The invalid-policy tests show the throw at the first read, with the
   exceptions in the Contract; namespace loading succeeds. On Java 8, EA3
   with preview disabled and EA3 with preview enabled: `report` throws. On
   each runtime without value-class support: a definition throws. On EA3
   with preview enabled: a definition defines a value class and does not
   throw.
5. All runtime, fallback, cross-JVM, build and tooling suites pass.
   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
6. Docs: `report` docstring, `doc/API.md` (generated), usage guide
   (fallback and capability report), `CHANGELOG.md`, the embodiment gap table
   (remove D10, D14), `mementum/state.md`. Also the sources that describe
   load-time policy or the old dependency graph:
   - `ramora/defvaluetype-design/embodiment/runtime.md`: the component rows
     of `value-type.capability` ("Reads no policy") and `impl.runtime`
     ("Initialized policy"), the dependency direction (add
     `value-type.capability → impl.runtime`), and the "initialized policy"
     text in the fallback boundary and state sections.
   - Assertion A-34 ("initialized policy") in
     `ramora/defvaluetype-design/assertions/fallback-architecture.md`.
   - The `defvaluetype` docstring ("before loading", "read and validated
     once") and its `api_docs_test` strings.
   - The README fallback text ("before the library loads").
   - The usage guide texts "before dependencies load, not in `-main`",
     "read and validated once" and "fail at load time".

   The README, the usage guide and the `defvaluetype` docstring replace
   these old texts with this rule:
   - The policy applies if it is set before the first read: the first
     fallback decision or the first `report` call.
   - A namespace that evaluates a fallback definition at top level makes the
     first read when it loads. Examples: a source definition on a runtime
     without value-class support, or an AOT-compiled fallback definition on
     any runtime.
   - Thus the JVM option (`-Dvalue-type.fallback=...`) stays the
     recommended form. A `System/setProperty` call, for example in `-main`,
     applies only if no policy read occurred before it.
   - `report` shows the policy in effect in `:fallback-policy`; an
     application can call it to check the policy.
   - An invalid value fails at the first read, not at load. A property
     change after the first read has no effect.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
