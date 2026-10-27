# defvaluetype design

This document gives the architecture of the `defvaluetype` macro. It
uses the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

Read this index first. Then read only the sections that you need.

## Summary

`defvaluetype` has the same syntax as `deftype`. It selects a value class
when support is available. For definitions without enabled nil restrictions,
application
policy selects silent fallback, one warning with fallback, or an `Error`
on every attempt when support is unavailable. Fallback uses ordinary `deftype` semantics.

```text
defvaluetype → validate fields → capability and application policy
  supported → install capture loader → deftype → transform bytes
              → write .class (AOT) → define and link → check value class
  unavailable → silent: deftype
                warn: warn once, then deftype
                error: Error on every attempt; no fallback class
```

AOT fixes the representation. The fallback guard enforces application policy
where the form is evaluated, including on capable JVMs. Top-level forms enforce
policy during namespace initialization. Forms inside functions allow namespace
loading and enforce policy on invocation. Direct class access that bypasses
the guarded form is not protected. Failed initializer retries do not re-enter
the guard. No instance-use checks are added.

Enabled nil-restricted fields instead require native field capability under
every policy. Selection validates the declaration; receiving analysis installs
a local capture loader and evaluation checks capability again. See the
[field design](nil-restricted-field-design.md). The fallback flow above applies
only to definitions without enabled restrictions.

## Sections

- [Purpose](defvaluetype-design/purpose.md): mission, capabilities,
  scope, behaviour, success criteria, priorities.
- [Context](defvaluetype-design/context.md): users, interacting
  systems (JVM, Clojure compiler), constraints.
- [Decisions](defvaluetype-design/decisions.md): the architectural
  decisions, with alternatives and rationale.
- [Nil-restricted fields](nil-restricted-field-design.md): opt-in native field
  restriction, concrete field types, failure semantics and implementation boundary.
- [Controlled fallback](defvaluetype-design/fallback.md): policy authority,
  capability boundary, state, AOT limits and verification requirements (D10 D12 D13).
- [Embodiment](defvaluetype-design/embodiment.md): namespaces,
  interfaces, data flow, invariants, tests.
- [Qualities](defvaluetype-design/qualities.md): target qualities,
  trade-offs, fitness conditions.
- [Risks and evolution](defvaluetype-design/risks-and-evolution.md):
  assumptions to verify, risks, expected changes.
- [Assertions](defvaluetype-design/assertions.md): the key claims in
  the metamodel record format, with status and basis.
- [Verification](defvaluetype-design/verification.md): spike results
  on the Valhalla early-access JDK.
- [Benchmark](defvaluetype-design/benchmark.md): method call time of
  `defvaluetype` against `deftype`, with environment and results.

- [Object size](defvaluetype-design/object-size.md): shallow size of
  standalone objects with 0–3 primitive `long` fields.
