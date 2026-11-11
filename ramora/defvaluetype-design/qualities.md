# Qualities

Q = f(E, C, W, T). The scope of each statement is the supported
environment in [Purpose](purpose.md).

## Target qualities

| Quality | Target | Realized by | Fitness condition |
|---|---|---|---|
| Correctness | Obey policy; no unintended identity class. | D8 D9 D10 D12 | Value-path results are value classes. Fallback follows policy. Every strict attempt throws an `Error`; defects do not trigger fallback. |
| Compatibility | `deftype` syntax and non-identity semantics. | D1 D2 | The `deftype` test forms without mutable fields pass (`value-type.deftype-compat-test`). |
| Simplicity | Small components and one pure transform. | D1 D2 D5 D12 | Capability and policy remain separate from byte transformation. Each state item has one owner. |
| Performance | No added method-call cost for ordinary definitions; measure non-nil field costs separately. | D2 | Without enabled non-nil fields, non-constructor method bytecode is unchanged from `deftype`; the [benchmark](benchmark.md) records ordinary method-call measurements, not a general equal-time guarantee. Definitions with enabled non-nil fields preserve method behavior while changing selected field-reference descriptors in owner and companion methods. |
| Changeability | Local format and capability knowledge. | D5 D12 | Format changes affect the transformer and, when needed, capability detection; not fallback semantics. |
| Testability | The transform is testable without a Valhalla JVM. | D5 | Unit tests run on bytes. |
| Error clarity | The error names the cause and the fix. | D8 D9 D10 | Strict `Error` and one-time warning identify unavailable support or an AOT fallback artifact. Existing implementation errors retain their causes. Compiler wrapping is documented. |
| Portability | Fallback adds no JDK requirement beyond Clojure. | D12 | Isolated source JAR consumer loads and defines fallback types without preview or optional APIs. |
| Diagnostic stability | One warning, persistent strict rejection. | D13 | Concurrent attempts produce at most one warning per library runtime. Caught errors do not permit later fallback. |

Fallback adds no JDK requirement beyond the supported Clojure runtime.
[Non-nil fields](../non-nil-field-design.md) preserve typed storage and
constructor rejection through native or generated enforcement. There is no
per-instance capability or policy check; generated constructors can add cost.
The field design records the contract and verification limits.

The non-nil field semantic matrix must verify correctness, not equal call time
or zero added cost. It does not extend the ordinary method-call benchmark to
definitions with enabled non-nil fields. See the
[native field evidence limits](../non-nil-field-design/verification.md#layout-and-performance-evidence);
layout and performance require separate measurements.

## Trade-offs

- Compatibility versus coupling. D2 and D3 reuse the Clojure compiler.
  This gives full `deftype` compatibility. It couples the library to
  the compiler evaluation order and constructor shape. The tests and
  the D6 shape check make a break visible, not silent.
- Semantic stability versus portability. D10 lets applications choose.
  Strict policy requires value
  classes; silent and warn policies permit
  ordinary identity semantics. Libraries must work with both if they
  permit fallback. The library does not emulate value semantics.
- Minimality versus features. Records, abstract value classes and
  interface/abstract/array field tags are out of scope. Concrete non-nil fields
  follow the portable field contract linked above. The design does not block
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
