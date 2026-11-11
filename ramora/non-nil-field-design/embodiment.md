# Field implementation and evidence

The public `:non-nil` declaration uses native or generated enforcement. The old
`:nil-restricted` key is rejected by presence, including disabled uses. Concrete
identity and value tags are accepted; representation policy is separate from
field enforcement. See the [usage contract](../../doc/usage.md#portable-non-nil-fields).

## Implementation

Baseline-safe declaration resolution (`impl.fields`) validates metadata and tags.
Ordered specifications retain exact Classes and enforcement modes; backends
derive descriptors from the Classes.
`impl.field-capability` probes native capability lazily and caches the result
for one loaded library runtime. It reads the value-class prerequisite through
`impl.capability/capability`, and it owns the native-field guard
(`require-field-capability!`). Public selection fixes representation and
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

## Verification scope and limits

Coverage uses Clojure 1.12.6 and the primary baseline and Valhalla builds in
the [JVM facts](../../dev/jvm/jvms.edn). It includes:

- Public expansion transfer through eval, source loading and function analysis,
  with repeated installation or rejection.
- Source-free AOT of identity-generated, value-generated, native and mixed
  fields, with separate Clojure and Java consumers.
- Constructor checks, concrete storage, companion reads, reload and
  function-contained policy timing under explicit child-JVM policies.
- Native semantic controls with field flattening enabled and disabled, in
  normal and interpreter execution without internal exports.
- Packaged-source nested Point/String consumers under silent policy. This
  does not establish all-policy packaging coverage.

Controlled-capability tests replace only the native capability result in EA3
child JVMs; compilation, loaders, transforms and constructors stay real. They
exercise generated checks in value owners, redefinition, receiving analysis
and source-free AOT. These controls do not establish support on another JVM.

The explicit child-process matrix bounds the evidence. Semantic checks do not
establish flat layout or performance. See the
[acceptance audit](../../munera/closed/017-portable-non-nil-fields/acceptance-audit.md),
[controlled-capability audit](../../munera/closed/017-portable-non-nil-fields/controlled-capability-audit.md)
and [runtime guide](../../doc/development/runtime.md) for detailed scope.
