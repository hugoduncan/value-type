# Assertions

Format: ⟨id, status, subject, predicate, object, basis, confidence⟩.
Default scope: the supported environment (Valhalla JDK with
`--enable-preview`, Clojure 1.12). Status values come from the
architecture metamodel.

## Purpose and context

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-01 | intended | `defvaluetype` | supports | make a JEP 401 value class from `deftype` syntax | README, [Purpose](purpose.md) | high |
| A-02 | intended | `defvaluetype` | isOutOfScope | mutable fields, records, abstract value classes, fallback | [Purpose](purpose.md) | high |
| A-03 | observed | `clojure.core/deftype` | expandsTo | `(let [] (deftype* ...) ...)` | `core_deftype.clj` 1.12.1 | high |
| A-04 | observed | `Compiler.eval`, `Compiler.compile1` | binds | `Compiler/LOADER` before macroexpand | `Compiler.java` 1.12.1 | high |
| A-05 | observed | `NewInstanceExpr` | calls | `LOADER.deref().defineClass(name, bytes, src)` | `Compiler.java` 1.12.1 | high |
| A-06 | observed | `NewInstanceExpr` | ordersBefore | write `.class`, then define class | `Compiler.java` 1.12.1 | high |
| A-07 | observed | `deftype` constructor | hasShape | `Object.<init>` then one `putfield` per field | `javap` on 1.12.6 output | high |
| A-08 | observed | `deftype` class | hasFlags | `ACC_PUBLIC ACC_FINAL ACC_SUPER`, version 52 | `javap` on 1.12.6 output | high |
| A-09 | observed | value class | requires | no `ACC_IDENTITY`, strict final fields, fields set before super call, preview version | `javap` on `javac` output, JDK 27 jep401ea3 | high |
| A-10 | observed | `java.lang.classfile` | supports | Valhalla flags and the D6 constructor | spike, JDK 27 jep401ea3 | high |

## Decisions and embodiment

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-11 | intended | D1 | realizedBy | `value-type.core` | [Decisions](decisions.md) | high |
| A-12 | observed | D3 D4 D7 | realizedBy | `value-type.impl.loader` | `src/value_type/impl/loader.clj`, `value-type.impl.loader-test` | high |
| A-13 | observed | D5 D6 | realizedBy | `value-type.impl.transform` | `src/value_type/impl/transform.clj`, `value-type.impl.transform-test` | high |
| A-14 | observed | D3 | respondsTo | A-03, A-04, A-05 | spike: `require`, `eval`, `do`, redefinition, AOT | high |
| A-15 | observed | D7 | respondsTo | A-06 | spike: AOT file is a value class in a new JVM | high |
| A-16 | intended | `value-type.core` | dependsOn | `value-type.impl.loader` | [Embodiment](embodiment.md) | high |
| A-17 | intended | `value-type.impl.loader` | dependsOn | `value-type.impl.transform` | [Embodiment](embodiment.md) | high |
| A-18 | observed | dependency rule | forbids | `transform` depends on `clojure.lang.Compiler` | `src/value_type/impl/transform.clj`, `value-type.impl.transform-test` | high |
| A-19 | observed | capture loader | owns | the target bytes for one top-level form | `src/value_type/impl/loader.clj`, `value-type.impl.loader-test` | high |

## Qualities

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-20 | intended | D9 | intendedToImprove | correctness: no silent identity class | [Qualities](qualities.md) | high |
| A-21 | intended | D2 | intendedToImprove | compatibility, performance | [Qualities](qualities.md) | high |
| A-22 | inferred | D2 D3 | mayDegrade | changeability against Clojure releases | A-04 to A-07 | high |
| A-23 | intended | invariant "each target class is a value class" | verifiedBy | runtime check, integration tests | D9, D11 | high |
| A-24 | observed | `install!` true | doesNotImply | class caught | spike: `(eval (macroexpand form))` | high |
| A-25 | observed | `DynamicClassLoader.defineClass` | doesNotDo | verification | `verification.md` "Other observations", JDK 27 jep401ea3 | high |
| A-26 | observed | `Class.getDeclaredMethods` | causes | link and verification (HotSpot) | `verification.md` "Other observations"; `value-type.impl.loader-test/link-test`, JDK 27 jep401ea3 | high |
