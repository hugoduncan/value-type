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

- First read: the first fallback decision, the first fallback-guard
  evaluation, or the first `report` call, whichever comes first.
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
- `report` returns the four capability keys and `:fallback-policy`. An
  invalid policy value makes `report` throw; there is no partial report.
- `report` does not load `value-type.core`.
- Fallback behaviour (`silent`, `warn` once, `error` on each attempt) does not
  change.

## Architecture

- `impl.runtime` keeps sole ownership of the policy and the warning flag.
  The policy becomes lazy state with one computation per runtime.
- `value-type.capability` gains a dependency on `impl.runtime`.
  `impl.runtime` still depends on no probe namespace. No cycle.
- AOT guards and receiving analysis read the policy through the same
  accessor, so their first read is also the fixing read.

Rejected: a warning on drift (prints under `silent`); reading the property on
each decision (policy can change during a run).

## Qualities and risks

- Better: application control, diagnosability.
- Cost: an invalid value is found later. On a capable JVM with no fallback,
  only the report shows it.
- Risk: `value-type.runtime-test/invalid-policy-report-test` asserts that
  `report` ignores the policy. It must change to the new contract.
- Risk: tests that rely on load-time validation must move to first-read
  validation.

## Acceptance

1. Child-JVM tests on an unsupported JVM (Java 8): load `value-type.core`,
   set `error` with `System/setProperty`, then define an ordinary type: it
   throws the fallback Error. Same with `warn`: one warning.
2. After a `report` call that shows `:silent`, setting `error` has no effect:
   a definition falls back silently.
3. `value-type.capability-matrix-test` checks `:fallback-policy` in each
   cell against the cell policy.
4. The invalid-policy tests show the throw at the first read, both from a
   definition and from `report`; namespace loading succeeds.
5. All runtime, fallback, cross-JVM, build and tooling suites pass.
   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
6. Docs: `report` docstring, `doc/API.md` (generated), usage guide
   (fallback and capability report), `CHANGELOG.md`, the embodiment gap table
   (remove D10, D14), `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
