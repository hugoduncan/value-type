# defvaluetype design

This document gives the architecture of the `defvaluetype` macro. It
uses the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

Read this index first. Then read only the sections that you need.

## Summary

`defvaluetype` has the same syntax as `deftype`. It makes a JEP 401
value class. The macro lets the Clojure compiler make the bytecode for
a normal `deftype`. A class loader catches the bytecode before the JVM
defines the class. A pure function changes the bytecode into a value
class. The JVM then defines the changed class.

```text
defvaluetype form
  → validate (value-class rules)
  → install capture loader (thread-bound Compiler/LOADER)
  → expand to clojure.core/deftype
  → Clojure compiler emits identity-class bytes
  → capture loader → transform bytes → write .class (AOT only)
      → define value class → link (LinkageError → ex-info :jvm-rejected)
  → runtime check: class is a value class
```

## Sections

- [Purpose](defvaluetype-design/purpose.md): mission, capabilities,
  scope, behaviour, success criteria, priorities.
- [Context](defvaluetype-design/context.md): users, interacting
  systems (JVM, Clojure compiler), constraints.
- [Decisions](defvaluetype-design/decisions.md): the architectural
  decisions, with alternatives and rationale.
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

Design, verified by a spike on `openjdk-27-jep401ea3+1-1`. The spike
code is in `spike/valhalla/`. The transform (D5 D6,
`value-type.impl.transform`) is implemented and tested. The loader
(D3 D4 D7, D9 define errors, `value-type.impl.loader`) is implemented
and tested. The macro (D1 D8 D9, `value-type.core`) is implemented
and tested. The method-call benchmark (`value-type.bench`) gives an
interface ratio of 1.14 (slower) and a protocol ratio of 1.00 (equal)
on `27-jep401ea3+1-1`. A-21 is `contested` (see
[Benchmark](defvaluetype-design/benchmark.md)).
