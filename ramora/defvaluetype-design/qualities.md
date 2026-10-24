# Qualities

Q = f(E, C, W, T). The scope of each statement is the supported
environment in [Purpose](purpose.md).

## Target qualities

| Quality | Target | Realized by | Fitness condition |
|---|---|---|---|
| Correctness | Obey policy; no unintended identity class. | D8 D9 D10 D12 | Value-path results are value classes. Fallback follows policy. Every strict attempt throws an `Error`; defects do not trigger fallback. |
| Compatibility | `deftype` syntax and non-identity semantics. | D1 D2 | The `deftype` test forms without mutable fields pass (`value-type.deftype-compat-test`). |
| Simplicity | Small components and one pure transform. | D1 D2 D5 D12 | Capability and policy remain separate from byte transformation. Each state item has one owner. |
| Performance | No added runtime cost. | D2 | Method bytecode is the same as `deftype`. Method call time is equal to `deftype` ([Benchmark](benchmark.md)). |
| Changeability | Local format and capability knowledge. | D5 D12 | Format changes affect the transformer and, when needed, capability detection; not fallback semantics. |
| Testability | The transform is testable without a Valhalla JVM. | D5 | Unit tests run on bytes. |
| Error clarity | The error names the cause and the fix. | D8 D9 D10 | Strict `Error` and one-time warning identify unavailable support or an AOT fallback artifact. Existing implementation errors retain their causes. Compiler wrapping is documented. |
| Portability | Fallback adds no JDK requirement beyond Clojure. | D12 | Isolated source JAR consumer loads and defines fallback types without preview or optional APIs. |
| Diagnostic stability | One warning, persistent strict rejection. | D13 | Concurrent attempts produce at most one warning per library runtime. Caught errors do not permit later fallback. |

Fallback is implemented. Production tests verify policy, diagnostics,
identity semantics, AOT guards and source-JAR use on the recorded Java 8 /
Valhalla 27 matrix; see [fallback verification](fallback.md#verification).
This coverage does not establish support for every supported Clojure JVM
or future EA format. The general constraint remains: fallback adds no JDK
requirement beyond the supported Clojure runtime. Benchmarks cover only
the value-class path. There is no per-instance fallback check.

## Trade-offs

- Compatibility versus coupling. D2 and D3 reuse the Clojure compiler.
  This gives full `deftype` compatibility. It couples the library to
  the compiler evaluation order and constructor shape. The tests and
  the D6 shape check make a break visible, not silent.
- Semantic stability versus portability. D10 lets applications choose.
  Strict policy requires value classes; silent and warn policies permit
  ordinary identity semantics. Libraries must work with both if they
  permit fallback. The library does not emulate value semantics.
- Minimality versus features. Records, abstract value classes and
  null-restricted fields are out of scope. The design does not block
  them (see [Risks and evolution](risks-and-evolution.md)).

## Semantic differences from `deftype`

These apply to the value-class path and come from the JVM. They are not
defects. Fallback has ordinary `deftype` semantics. The README describes
both representations and the application policy.

- `identical?` (the Java `==` operator, `acmp`) compares field values.
- The default `equals` and `hashCode` use field values.
- A `double` field compares by bit pattern in `identical?`. So `NaN`
  equals `NaN`, and `0.0` does not equal `-0.0`.
- A field that holds an identity object compares by reference. Two
  equal vectors that are not the same object make the value objects
  not equal.
- A field that holds a value object (for example a boxed `Long` under
  `--enable-preview`, or a `defvaluetype` instance) compares by its
  field values.
- `locking`, `WeakReference` and other identity operations throw
  `IdentityException`.
