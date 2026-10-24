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
when support is available. Otherwise application
policy selects silent fallback, one warning with fallback, or an `Error`
on every attempt. Fallback uses ordinary `deftype` semantics.

The value-class path and controlled fallback are implemented and tested.
The configuration contract is user-approved. Verification is limited to
the JVM configurations recorded below.

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

## Sections

- [Purpose](defvaluetype-design/purpose.md): mission, capabilities,
  scope, behaviour, success criteria, priorities.
- [Context](defvaluetype-design/context.md): users, interacting
  systems (JVM, Clojure compiler), constraints.
- [Decisions](defvaluetype-design/decisions.md): the architectural
  decisions, with alternatives and rationale.
- [Controlled fallback](defvaluetype-design/fallback.md): policy authority,
  capability boundary, state, AOT limits and verification evidence (D10 D12 D13).
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

## Status

Value-class path, verified by a spike on `openjdk-27-jep401ea3+1-1`. The spike
code is in `spike/valhalla/`. The transform (D5 D6,
`value-type.impl.transform`) is implemented and tested. The loader
(D3 D4 D7, D9 define errors, `value-type.impl.loader`) is implemented
and tested. The macro (D1 D8 D9, `value-type.core`) is implemented
and tested. The method-call benchmark (`value-type.bench`) gives an
interface ratio of 1.14 (slower) and a protocol ratio of 1.00 (equal)
on `27-jep401ea3+1-1`. A-21 is `contested` (see
[Benchmark](defvaluetype-design/benchmark.md)).

Controlled fallback (D10 D12 D13) is implemented and verified by production
tests with Clojure 1.12.6 on Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1` with preview disabled and enabled. Tests cover source
policy, transferred expansions, warning scope and source-free AOT guards,
including runtime-only calls and strict retries. See
[fallback verification](defvaluetype-design/fallback.md#verification) and
[task 009 evidence](../munera/closed/009-controlled-deftype-fallback/implementation.md).

Fallback adds no minimum JDK beyond the supported Clojure runtime. The
recorded matrix does not establish support for every supported Clojure JVM
or future EA formats.
