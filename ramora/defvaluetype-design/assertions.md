# Assertions

Format: ⟨id, status, subject, predicate, object, basis, confidence⟩.
Default scope: Valhalla JDK with
`--enable-preview`, Clojure 1.12. Status values follow the
architecture metamodel. Intended rows state constraints, not delivery status.
See [embodiment](embodiment.md) for implementation coverage. Fallback rows
have their own scope below.

## Purpose and context

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-01 | intended | `defvaluetype` | supports | make a JEP 401 value class from `deftype` syntax | [Purpose](purpose.md) | high |
| A-02 | intended | `defvaluetype` | isOutOfScope | mutable fields, records, abstract value classes, emulated value semantics | [Purpose](purpose.md) | high |
| A-03 | observed | `clojure.core/deftype` | expandsTo | `(let [] (deftype* ...) ...)` | `core_deftype.clj` 1.12.1 | high |
| A-04 | observed | `Compiler.eval`, `Compiler.compile1` | binds | `Compiler/LOADER` before macroexpand | `Compiler.java` 1.12.1 | high |
| A-05 | observed | `NewInstanceExpr` | calls | `LOADER.deref().defineClass(name, bytes, src)` | `Compiler.java` 1.12.1 | high |
| A-06 | observed | `NewInstanceExpr` | ordersBefore | write `.class`, then define class | `Compiler.java` 1.12.1 | high |
| A-07 | observed | `deftype` constructor | hasShape | `Object.<init>` then one `putfield` per field | `javap` on 1.12.6 output | high |
| A-08 | observed | `deftype` class | hasFlags | `ACC_PUBLIC ACC_FINAL ACC_SUPER`, version 52 | `javap` on 1.12.6 output | high |
| A-09 | observed | value class | requires | no `ACC_IDENTITY`, strict final fields, fields set before super call, preview version | `javap` on `javac` output, JDK 27 jep401ea3 | high |
| A-10 | observed | `java.lang.classfile` | supports | Valhalla flags and the D6 constructor | spike, JDK 27 jep401ea3 | high |

## Design constraints

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-11 | intended | D1 value-class path | realizedBy | `value-type.core` | D1 | high |
| A-12 | intended | D3 D4 D7 | realizedBy | `value-type.impl.loader` | D3 D4 D7 | high |
| A-13 | intended | D5 D6 | realizedBy | `value-type.impl.transform` | D5 D6 | high |
| A-14 | observed | D3 | respondsTo | A-03, A-04, A-05 | spike: `require`, `eval`, `do`, redefinition, AOT | high |
| A-15 | observed | D7 | respondsTo | A-06 | spike: AOT file is a value class in a new JVM | high |
| A-16 | intended | `value-type.core` | dependsOn | capture support only during receiving analysis; optional value backend only for value output | D12; non-nil field D3 | high |
| A-17 | intended | value backend | dependsOn | `value-type.impl.transform` | D3 D5; non-nil field D3 | high |
| A-18 | intended | dependency rule | forbids | `transform` depends on `clojure.lang.Compiler` | D19 | high |
| A-19 | intended | capture loader | owns | the target bytes for one top-level form | D3 | high |

## Qualities

| id | status | subject | predicate | object | basis | confidence |
|---|---|---|---|---|---|---|
| A-20 | intended | D9 | intendedToImprove | correctness: no identity class on the value-class path | D9 | high |
| A-21 | contested | D2 | intendedToImprove | compatibility, performance | [Qualities](qualities.md); A-28, [Benchmark](benchmark.md) | high |
| A-28 | observed | `defvaluetype` | exhibits | method call time / `deftype` time for each criterion case: `interface` 1.14, `protocol` 1.00 | [Benchmark](benchmark.md), JDK 27 jep401ea3 | medium |
| A-29 | observed | interface workload on Valhalla 27 EA / Apple M5 Max | exhibits | no stable slowdown in 16 fresh JVMs; Clojure median ratios 1.028 / 1.008 at 1024 / 2048 elements; Java controls 1.015 / 0.998; paired main C2 loops have identical instruction bytes in separate diagnostic runs | [Interface-call investigation](interface-call.md), 2026-10-24 | medium |
| A-22 | inferred | D2 D3 | mayDegrade | changeability against Clojure releases | A-04 to A-07 | high |
| A-23 | intended | invariant "each value-path target class is a value class" | requiresVerificationBy | runtime check, integration tests | D9; [verification requirements](decisions.md#d11-verification-verification) | high |
| A-24 | observed | `install!` true | doesNotImply | class caught | spike: `(eval (macroexpand form))` | high |
| A-25 | observed | `DynamicClassLoader.defineClass` | doesNotDo | verification | `verification.md` "Other observations", JDK 27 jep401ea3 | high |
| A-26 | observed | `Class.getDeclaredMethods` | causes | link and verification (HotSpot) | `verification.md` "Other observations"; `value-type.impl.loader-test/link-test`, JDK 27 jep401ea3 | high |
| A-27 | intended | immutable forms in the Clojure `deftype` compatibility corpus | mustCompileAs | value classes that keep the Clojure assertions | D11; `value-type.deftype-compat-test` defines the corpus and exclusions | high |

## Fallback architecture

See [fallback architecture](assertions/fallback-architecture.md).
