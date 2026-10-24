---
title: Keep capability detection and fallback policy separate across compiler phases
status: active
category: architecture
tags: [fallback, capability, macroexpansion, aot, testing, valhalla]
related: [mementum/knowledge/java-lang-classfile.md, mementum/knowledge/testing-clojure-generated-classes.md, mementum/knowledge/testing-library-artifacts.md]
depends-on: []
---

# Controlled fallback boundaries

Source: task `009-controlled-deftype-fallback`, its design, plan, steps and
implementation notes. Key evidence: detector `c7f224f`, source integration
`15ff690`, transferred expansions `879eb17`, source-free AOT `167532b`,
runtime-only production regression `f0e34d2`, and test configuration
`1b1ff50`. Checks used Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1` with preview disabled and enabled. This is not proof of
support for every Clojure JVM or future EA build.

## API presence does not prove usable value-class support

Valhalla exposes `java.lang.classfile.ClassFile` and `Class.isValue` even
with preview disabled. Check required APIs, then define and instantiate a
small known-format value class. Include a strict final field, assignment
before `Object.<init>`, a value-class predicate check and field readback.
An empty class alone does not test the constructor contract.

Use the running JDK's `ClassFile.latestMajorVersion` and preview minor
version, as the transformer does. A fixed probe version can test a different
format from production. Use a unique probe name: see the static
`DynamicClassLoader` cache rule in `testing-clojure-generated-classes.md`.

Keep the detector and public namespace safe to load without optional APIs.
Resolve the loader/transformer dependency only after a positive result.
Reflective API checks in core do not help if an eager dependency imports
`java.lang.classfile` first.

Only these narrowly scoped failures establish absence:

- `ClassNotFoundException` from lookup of the required ClassFile class;
- `NoSuchMethodException` from lookup of `Class.isValue`;
- `UnsupportedClassVersionError` from definition of the known probe.

Do not catch general linkage errors, parse exception messages, or retry a
failed user-class definition as an identity class. Missing later API members,
verification failures, bad probe results and transform defects remain errors.

## An expansion fixes representation, not permission

Clojure defines `deftype` classes during analysis. A runtime check written
before `deftype` cannot by itself prevent source class definition.

Apply fallback policy at selection, including discarded macroexpansions.
Return an outer `do` with a nested guard macro. The `do` prevents both
`macroexpand` and `macroexpand-1` from removing the receiving compiler's
guard. That guard applies policy during analysis and emits a runtime check.
Test transfer to fresh receivers, including capable receivers: they must
apply their own policy to the fixed identity representation.

One runtime owner holds startup policy, cached capability and the warning
flag. All phases share it. Use atomic compare-and-set only for warn-once
emission; strict policy must reject on every call. Requiring the namespace
alone is not a fallback attempt.

## AOT policy runs where the form is evaluated

A top-level fallback form enforces runtime policy during namespace
initialization. A fallback form inside a compiled function permits namespace
loading and applies policy when the function runs. Test these separately
with production macros, not only prototype guards.

AOT fixes representation. Capable receivers do not upgrade identity
artifacts; incapable receivers do not convert value artifacts. Namespace
guards do not intercept direct class access and do not guarantee that no
class was loaded before rejection.

Error expectations also depend on phase:

- Source compiler operations wrap policy `Error` in `CompilerException`.
- Runtime-only function calls expose plain `Error` on repeated attempts.
- First strict top-level AOT initialization throws `Error`. A retry throws
  `NoClassDefFoundError`; the failed initializer does not run policy again.
  The recorded Valhalla retry includes an `ExceptionInInitializerError`
  cause; the recorded Java 8 retry has no cause.

Do not promise one concrete Error chain across phases or JVM versions.

## Test expectations must be independent of observed capability

Choose expected capability from the explicit test JVM configuration, not
from the production detector's result or the child's result shape. Otherwise
an incorrect unavailable result can make a capable-JVM test accept fallback,
or an unexpected `:value-mode` result can skip strict-policy assertions.
Require exact expected outcomes and complete result maps. Unknown matrix
configurations must fail clearly, not silently skip checks.

Use the baseline-safe `value-type.jvm-config` and `value-type.jvm-runner`
support under `test-fallback`. Supply the executable, options, policy and
classpath explicitly. The value-only helper eagerly loads optional code
and adds preview; it cannot verify the unavailable path unchanged.

Capture diagnostics at the required phase. A final warning count after both
expansion and evaluation cannot prove that a discarded expansion warned.
Check the warning and type absence before evaluation, then check that
evaluation adds no warning. Use fresh processes for independent policy and
warning state, and synchronized workers for shared warn-once checks.

For source-free AOT tests, delete fixture source, exclude it from the consumer
classpath and assert resource absence. Keep producer/consumer policy and
phase assertions visible even when fixture setup and cleanup are shared.
