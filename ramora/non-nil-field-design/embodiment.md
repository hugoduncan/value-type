# Field implementation and evidence

The public `:non-nil` declaration uses native or generated enforcement. The old
`:nil-restricted` key is rejected by presence, including disabled uses. Concrete
identity and value tags are accepted; representation policy is separate from
field enforcement. See the [usage contract](../../doc/usage.md#portable-non-nil-fields).

## Implementation

Baseline-safe declaration resolution (`impl.fields`) validates metadata and tags.
Ordered specifications retain exact Classes and enforcement modes; backends
derive descriptors from the Classes.
Native capability is probed lazily. Public selection fixes representation and
per-field modes; missing native support alone does not select identity fallback.
No global field registry is used.

Constrained public expansions retain `core/identity-field-deftype` or
`core/value-field-deftype` as a receiving compiler boundary. Receiving analysis
resolves local Classes and installs the capture loader. The loader rejects a
same-name Class mismatch. Transferred forms do not reselect enforcement.

`defvaluetype*` uses the same declarations in the plain and the record shape,
through `core/value-field-deftype*` and `core/identity-field-deftype*`. See the
[deftype* embodiment](../deftype-star-design/embodiment.md).

The identity backend uses Clojure's bundled ASM without optional JDK APIs. The
value backend uses `java.lang.classfile`. Both retype final storage and matching
owner/companion references. Generated enforcement casts and rejects nil in actual
constructors; native fields use casts and native restriction. Reference parameters
remain Object, including mixed long/double signatures. Unsupported bytecode shapes
fail rather than retry with weaker output.

Runtime definitions and AOT writes use transformed owner and companion bytes.
AOT fixes representation and enforcement. Identity namespace guards retain receiving
policy, including on capable JVMs; function-contained definitions defer runtime
policy until invocation. Direct Java class access does not evaluate namespace guards.
Value artifacts reject incompatible JVMs rather than convert to identity output.

## Verified behavior and limits

Task 017 records Clojure 1.12.6, Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1` evidence. Public expansion transfer covers both expansion entry
points, eval, source loading and function analysis, with repeated installation or
rejection. Public source-free AOT covers identity-generated, value-generated,
native and mixed fields with separate Clojure and Java consumers. Constructor
checks, concrete storage, companion reads, reload and function-contained timing
have normal/interpreter evidence under explicit child-JVM policies.

Native semantic controls pass with field flattening enabled and disabled, in
normal and interpreter execution without internal exports. Packaged-source nested
Point/String consumers pass on Java 8 and EA3 preview off/on under silent policy.
This is not all-policy packaging evidence. Host JVM variations do not extend the
explicit child-process matrix.

Controlled-capability evidence is separate from real JVM evidence.
`value-type.controlled-field-capability-test` and
`value-type.function-redefinition-test/controlled-capability-repeated-definition-test`
replace only the native capability result in EA3 child JVMs; compilation,
loaders, transforms and constructors stay real. They cover generated checks in
value owners through public expansion, same-name redefinition, independent
receiving, source-free Clojure AOT and separate Java AOT, with no fallback
warning under silent, warn and error policies. This is controlled
evidence, not JVM support. See the
[controlled-capability audit](../../munera/closed/017-portable-non-nil-fields/controlled-capability-audit.md)
and [runtime commands](../../doc/development/runtime.md).

The user accepted this evidence for the closed task. Accepted limits: packaging
evidence covers the silent policy only, and the child-process matrix is the
evidence scope. See the
[task checklist](../../munera/closed/017-portable-non-nil-fields/steps.md),
[recorded evidence](../../munera/closed/017-portable-non-nil-fields/implementation.md)
and [runtime commands](../../doc/development/runtime.md). No new JVM support,
flat layout or performance guarantee follows from these results.

## Separate historical experiments

The [native experiment](../../spike/null-restricted-fields/report.md) retains its
layout observations and prototype limits. It is not the production transform.
The portable experiment in `spike/portable-fields/verify.sh` established the backend
feasibility gate: checked identity output on baseline JVMs and generated checks
in EA3 value constructors with native annotations deliberately absent. That is
controlled generated-only evidence, not a new JVM capability configuration.
The experiment ran at commit `6318c36`. It does not run against the current
tree, so `verify.sh` runs it in a temporary git worktree at that commit.
Historical task and experiment records retain their original scope.
