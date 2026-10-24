# Controlled deftype fallback

## Intent

Library authors need to use `defvaluetype` on JVMs with and without value
classes. Applications must control whether identity-class fallback is
silent, produces one warning, or fails. Success means portable source
libraries without loss of application control or hidden implementation
errors.

## Context

The current macro always loads the transformer, installs a capture loader
and checks for a value class. Its eager dependency on `java.lang.classfile`
can prevent the public namespace from loading on older JVMs.

The architecture is in
[the design index](../../../ramora/defvaluetype-design.md), especially
[D10, D12 and D13](../../../ramora/defvaluetype-design/fallback.md).
The fixed-format capability probe, including a strict field and pre-super
assignment, is verified on the selected JVMs. The running-JDK version rule
also passes on Valhalla with preview enabled and rejects disabled preview. Source Error wrapping and retries are verified on the selected JVMs.
Transferred-expansion analysis guards are verified for silent and strict policy.
AOT initialization rejects strict consumers on all selected JVMs. A failed
initializer stays failed: retries throw NoClassDefFoundError, also a Java Error.
Strict AOT compilation rejects before type definition or output on all selected
JVMs, including retries. Runtime-only guards also reject repeated calls to a silent-compiled function
under strict receiving policy, without compiler wrapping. The initial API
matrix and selected test JVMs are recorded in `plan.md` and
`implementation.md`. Slice 1 selects the narrow detector, plain Error and phase guards in the
plan. The production capability detector and policy state owner are implemented and
verified on the selected matrix. Public macro integration and basic source policy tests are implemented.
Source compatibility and field validation now pass on the selected matrix.
Macroexpansion timing and strict source retries pass on both incapable configurations.
Capable-JVM D9 tooling and existing loader/transform defect regressions pass
under all three policies. Production transferred expansions now pass receiving-policy tests through eval
and source loading on all three JVM settings. Synchronized concurrent fallback
attempts across namespaces share one warning; fresh runtimes each warn once.
Production fallback AOT compilation now verifies identity output under silent
and warn, plus repeated strict rejection without type output. Source-free fallback AOT loading now passes all receiving policies on the three
selected JVM settings. Source-free value artifacts load on preview-enabled Valhalla and fail with
UnsupportedClassVersionError on Java 8 and preview-disabled Valhalla under
all policies; no conversion occurs. Isolated source-JAR consumers now pass on
Java 8 and preview-enabled Valhalla. Source-only archive checks also pass.
Full fallback matrix, value-path regression and build suites pass on the
selected JVMs. README and public macro documentation now describe the verified
contract. Architecture now records the implemented mechanisms and scoped evidence.
All eight acceptance criteria are mapped to evidence in `implementation.md`.
Implementation review found no remaining actionable work; external review and
closure remain separate.
Slice 2 verification support is implemented and tested on the selected three
runtime configurations. It now tests basic production fallback definitions.
The value-class path and source-only JAR are implemented.

The user confirmed that strict policy must throw a Java `Error` on the
first attempt and every subsequent attempt if an earlier error is caught
or ignored. Fallback adds no minimum JDK beyond the supported Clojure
runtime. Tested JDK coverage is a separate claim.

## Scope and contract

- Use value classes whenever this library can define its supported format.
  Otherwise select ordinary `deftype` under application policy:
  - `silent`: no diagnostic.
  - `warn`: one warning to standard error per loaded library runtime,
    then fallback. Concurrent attempts share the same warning state.
  - `error`: throw an `Error` on every attempted fallback; do not define
    a fallback type during source compilation.
- Applications own policy before dependencies load. Libraries cannot
  weaken it through per-type overrides. Requiring the public namespace
  alone does not count as fallback or produce an availability warning.
- Separate capability from policy. Cache capability and its unavailable
  reason. Capability includes required APIs, supported class format and
  preview availability, not merely the presence of `Class::isValue`.
- The public namespace and fallback path load without JEP 401, preview
  flags or `java.lang.classfile`. Optional dependencies must not cross
  this boundary before the capability checks permit them.
- Only established capability absence permits fallback. Invalid forms,
  transformation defects and loader failures remain errors. Do not retry
  failed user-class definitions as identity classes.
- Keep the existing macro syntax, factory, import and protocol/interface
  behaviour. Fallback has ordinary identity semantics, not emulated value
  semantics. Reject mutable fields in both representations.
- One responsibility owns initialized policy, cached capability and the
  atomic warning flag. Independent library runtimes have separate state.
  No persistent state or per-instance policy checks are needed.
- AOT fixes representation. A fallback namespace enforces the loading
  application's policy: strict rejects it even on a capable JVM; warn
  reports it once. This does not convert classes or guarantee that the
  JVM has not loaded a class before namespace rejection. Direct class
  loads that bypass namespace initialization are outside this guard.
- Preserve source-only packaging. Verify fallback from the built JAR,
  without checkout sources on the consumer classpath.

### Macroexpansion and later evaluation

Selecting fallback during macroexpansion counts as an attempted fallback,
even without evaluation: `silent` is silent; `warn` emits and consumes the
one warning; `error` throws every time, returning no expansion or type.
A missing compiler-loader binding is not unavailable capability;
capable-JVM tooling retains D9 behaviour.

A fallback expansion fixes representation, not permission. Compiling it,
including through `eval`, enforces policy before source type definition.
Evaluation and AOT namespace initialization also enforce policy. All phases
share warning state. Independent runtimes apply their own policy and warning
state, even on capable JVMs. The AOT class-loading limits still apply.

## Decisions before implementation

Resolve these in the task plan and align the architecture before coding:

1. Approved configuration: JVM property
   `value-type.fallback`, values `silent`, `warn`, `error`, default
   `silent`, validated once per loaded library runtime. Invalid values
   fail clearly; later property changes are unsupported. A startup
   property avoids configuration in `-main`, after dependencies load.
   The user approved this contract on 2026-10-23; no further approval
   is needed for these configuration choices.
2. Select capability detection and a narrow classification of unavailable
   conditions through real-JVM experiments. Prefer a small capability
   boundary over retrying user definitions or a general plugin framework.
3. Select the concrete `Error` type and document Clojure compiler wrapping.
   A dedicated subtype is a candidate, not a requirement. Do not promise
   an unwrapped outer `Error` without evidence.
4. Select actual test JDKs, including one without `java.lang.classfile` and
   supported Valhalla with preview enabled and disabled. Record versions
   and commands; do not silently narrow the fallback baseline.

## Acceptance criteria

1. On supported Valhalla with preview enabled, all policies produce value
   classes. Existing transform, loader and compatibility tests pass.
2. On a non-value JVM and Valhalla with preview disabled, all three
   policies follow the contract. Default and invalid configuration are
   tested in isolated JVMs once the configuration contract is settled.
3. Silent fallback produces no diagnostic. Warn produces exactly one
   warning across multiple types, namespaces and concurrent attempts.
   Requiring alone produces no warning. Test `macroexpand` and
   `macroexpand-1` under all policies on an incapable JVM, with and without
   a compiler-loader binding. Cover discarded expansions, repeated strict
   failures and expansion followed by evaluation. Compile returned fallback
   expansions in independent strict runtimes, both capable and incapable.
4. After catching strict failure, another attempt also fails. Source
   attempts do not define the rejected types. Tests establish and record
   the `Error` and compiler cause chain.
5. Fallback retains factories, type hints, methods, protocols and ordinary
   identity behaviour. Both modes reject mutable fields. Existing defect
   paths remain failures rather than successful fallback.
6. Fresh-JVM AOT tests cover fallback compilation and namespace loading
   under different policies and capabilities. Strict rejects fallback
   artifacts on capable and incapable JVMs. Value-class artifacts retain
   their existing JVM constraints.
7. An isolated consumer uses the built source JAR on a JVM without the
   optional APIs and successfully defines fallback types. Value-class JAR
   consumer verification also remains successful.
8. README and architecture state configuration, semantic differences,
   warning scope, strict retries, Error wrapping, AOT limits and verified
   JDK coverage. Assertions distinguish evidence from remaining assumptions.

## Out of scope

Value-semantics emulation, per-type overrides, runtime policy mutation,
forced identity mode on capable JVMs, AOT representation conversion,
cross-class-loader diagnostic coordination, new type forms, performance
benchmarks, publication and unrelated loader changes.

Capability, policy and deployment verification form one acceptance unit.
No separate implementation tasks are needed for its internal stages.
