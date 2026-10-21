# Qualities

Q = f(E, C, W, T). The scope of each statement is the supported
environment in [Purpose](purpose.md).

## Target qualities

| Quality | Target | Realized by | Fitness condition |
|---|---|---|---|
| Correctness | Never an identity class silently. | D8 D9 | Each test class is a value class. Each failure with an error key of `value-type` gives `ex-info` with `:value-type/error`. A `deftype` syntax error gives the `deftype` error. |
| Compatibility | `deftype` syntax and non-identity semantics. | D1 D2 | The `deftype` test forms without mutable fields pass (`value-type.deftype-compat-test`). |
| Simplicity | Three small namespaces. One pure transform. | D1 D2 D5 | Each namespace has one responsibility. |
| Performance | No added runtime cost. | D2 | Method bytecode is the same as `deftype`. Method call time is equal to `deftype` ([Benchmark](benchmark.md)). |
| Changeability | A Valhalla format change touches one namespace. | D5, `transform` boundary | Flag values and version rule are only in `transform`. The value-class predicate is only in `core`. |
| Testability | The transform is testable without a Valhalla JVM. | D5 | Unit tests run on bytes. |
| Error clarity | The error names the cause and the fix. | D8 D9 | Each failure with an error key of `value-type` gives `ex-info` with `:value-type/error`, `:class-name` and a message that names the cause and the fix. A `deftype` syntax error gives the `deftype` error. |

## Trade-offs

- Compatibility versus coupling. D2 and D3 reuse the Clojure compiler.
  This gives full `deftype` compatibility. It couples the library to
  the compiler evaluation order and constructor shape. The tests and
  the D6 shape check make a break visible, not silent.
- Safety versus portability. D10 has no fallback. Programs fail on a
  JVM without value classes. This keeps equality semantics the same on
  each JVM.
- Minimality versus features. Records, abstract value classes and
  null-restricted fields are out of scope. The design does not block
  them (see [Risks and evolution](risks-and-evolution.md)).

## Semantic differences from `deftype`

These come from the JVM. They are not defects. The README must state
them.

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
