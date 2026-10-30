# Source-free AOT acceptance audit

This is a partial acceptance audit for task 017, not final acceptance. Paths below
are relative to `test/compatibility/value_type/`. Recorded execution results are
in implementation.md; this audit does not claim a new runtime execution.

## Verified deployment contracts

| Contract | Public fixture | Evidence |
| --- | --- | --- |
| Identity-generated storage and construction | `public_field_aot_test.clj` | Java 8 producer; source-free Java 8 and EA3 preview off/on consumers; direct Clojure, factory, reflection and separate Java construction. |
| Generated-value storage and construction | `public_field_aot_test.clj` | EA3 producer and consumers; concrete String storage without native annotations; all four construction entries. |
| Native storage and construction | `public_native_field_aot_test.clj` | EA3 producer and consumers; concrete Long storage with native annotation; all four construction entries. |
| Mixed enforcement | `public_mixed_field_aot_test.clj` | EA3 producer and consumers; Long native and String generated fields; each constrained position checked through all four entries. |
| Fixed identity policy | `public_field_aot_test.clj` | Silent/warn namespace loads and reloads retain identity checks; strict loading rejects on baseline and capable receivers. |
| Function-contained timing | All three public fixture namespaces | Namespace loading is silent; repeated function invocation applies fixed identity policy or retains value enforcement without warnings. |
| Incompatible value output | All three public fixture namespaces | Java 8 and preview-disabled EA3 reject repeated direct class loads under every policy. |

Fixtures delete producer source before consumption and omit the source directory
from consumer classpaths. Supported Clojure and Java consumers also assert that the
source resource is absent. Public namespace regression records report 8 tests /
288 assertions for identity/generated-value and 8 tests / 204 assertions for
native/mixed on each of the three host configurations. Children select their own
policies and normal/interpreter options; changing host flags adds no child cases.

## Limits and remaining work

- Direct Java class access does not execute Clojure namespace policy guards.
  Strict Java construction of identity output is therefore permitted, not evidence
  that strict namespace loading succeeds.
- A failed top-level initializer remains failed. Reload rejection does not imply
  policy reevaluation; repeated function invocation tests establish retry timing.
- Function-contained tests use factories. Top-level fixtures provide the direct
  Clojure, reflective and separate Java constructor evidence.
- String establishes native ineligibility, not controlled native-capability absence.
  The [controlled-capability audit](controlled-capability-audit.md) now supplies
  separate eligible-Long evidence and closes the combined checklist item.
- Saved descriptor inspection and broad attribute/shape coverage require their own
  audit. Execution proves the tested reads, not every transformation invariant.
- Full regression and acceptance 1–9 audits remain open. This audit completes the four source-free deployment checklist items and the
  separate Java constructor item that have matching public tests and recorded
  passing results.

## Separate Java constructor audit

The public `java-public-identity-consumer`, `java-public-generated-value-consumer`,
`java-public-native-value-consumer` and `java-public-mixed-value-consumer` tests
compile Java consumers after deleting the producer source. Each consumer calls
`new Holder` directly, accepts valid arguments, and catches NullPointerException
for nil and ClassCastException for a wrong type. Mixed construction checks each
constrained position independently. All retain long/double slots, nullable storage
and captured reads. These tests use real compilers and JVMs, not mocks.

`IdentityJavaConsumer.java` serves both generated representations;
`NativeJavaConsumer.java` and `MixedJavaConsumer.java` cover native and mixed output.
Files are under `test/resources/non-nil-fields/`. Identity consumers run on Java 8
and EA3 preview off/on. Value consumers run on EA3 with preview. Each uses all
three policies in normal and interpreter execution. The focused rerun passes
4 tests / 124 assertions; commands are in implementation.md.

This completes the slice 3 separate-Java-constructor item. It does not establish
controlled native absence, public nested all-policy behavior or final acceptance.
