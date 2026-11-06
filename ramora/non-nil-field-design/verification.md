# Non-nil field verification

Parent: [Design](../non-nil-field-design.md).

## Required qualities

- Correctness: supported constructors preserve the type and non-nil constraint.
- Portability: the field constraint survives permitted identity fallback.
- Policy authority: error rejects identity fallback, not generated enforcement.
- Local change: validation, compiler transport and bytecode backends stay separate.
- Predictability: source capability selects mechanisms; AOT fixes them.
- Verifiability: semantic tests do not depend on flat storage or timing.

## Backend feasibility gate

Before committing to a baseline bytecode library, use the public declaration path
in a small experiment. On the oldest supported Clojure JVM, prove concrete final
storage, constructor casts and nil checks, owner/companion rewriting, mixed primitive
slots and source-free AOT. Public loading and transformation must not resolve
`java.lang.classfile`, native annotations or other optional APIs. Record the chosen
backend and its limits in embodiment, not as an assumed result in the design.

Also prove generated enforcement in a value owner for a concrete identity-class
field and for a value-class field with native enforcement unavailable. These cases
must preserve strict initialization rules. A controlled capability input can cover
a combination unavailable in the test JVM matrix; label it as such, not as evidence
of support on an untested JVM. Do not use a swallowed transform failure to select it.

## Declaration and construction checks

Check `^:non-nil` and explicit metadata, with absent, true, false, nil and invalid
values. Reject the old `:nil-restricted` key clearly. Verify that disabled constraints
and plain hints preserve ordinary field behavior. Reject mutable fields in all modes.

Check missing/unresolved tags, imported names, aliases, concrete value and identity
classes, external classes, primitives, interfaces, abstract classes, arrays, enclosing
types and forward references. A nested defvaluetype must remain eligible after it
falls back. Do not require global provenance state. Test exact Class identity with
same-name classes from different loaders and redefinition.

For every enforcement path, test valid inputs, nil and wrong-type values through
factories, direct Clojure constructors, reflection and separately compiled Java.
Expect NullPointerException or ClassCastException, allowing reflective wrapping.
Do not assert message text or ordering among multiple invalid inputs.

Verify each position, multiple enabled fields, mixed nullable and primitive fields,
including long/double slots. Nested objects may have nullable fields. Inspect concrete
final field descriptors and read values directly and reflectively. Exercise protocols,
interfaces, Object overrides, closures, companions and nested control flow. Preserve
unrelated attributes. Run the existing compatibility corpus.

Access-suppressed reflective mutation, Unsafe and constructor-bypassing allocation
or restoration are excluded; do not claim native-strength enforcement for them.

## Runtime and deployment matrix

Run isolated processes on the baseline JVM, preview-disabled Valhalla and supported
preview-enabled Valhalla, under silent, warn and error policies.

- Without value support: silent and warn define checked identity classes; warn emits
  at most one fallback warning per runtime. Error repeatedly rejects definitions.
- With value support: all policies produce value owners. Native enforcement is used
  where eligible and available; other fields use generated checks without fallback
  warnings. Missing native support alone must not cause strict rejection.
- Requiring the public namespace is safe everywhere. Unexpected probe, linkage,
  verification and transform defects remain visible; they never select weaker output.

Check discarded and transferred expansions, including macroexpand and macroexpand-1.
Receiving analysis must install local compiler state and preserve the selected modes
or reject an incompatible receiver; it must not lose checks or reselect silently.
Verify exact receiver Classes. Check top-level evaluation and definitions in functions.

Compile both representations and consume them without source in fresh processes.
Include companions, Clojure consumers and direct Java consumers. Identity artifacts
keep checks on capable JVMs and obey receiving application fallback policy. Value
artifacts require compatible JVMs and do not fall back. Generated-check value artifacts
must not acquire a native field requirement merely because they use value classes.

## Layout and performance evidence

Run native semantics with flattening on/off and interpreter/normal execution.
Check generated semantics under interpreter and normal execution where supported.
Do not infer JIT coverage from a warm-up loop. Measure constructor cost, layout,
allocation and throughput separately; neither native nor generated enforcement
promises a particular result. Exports needed only for diagnostics are not construction
requirements.

## Evidence boundary

The [native experiment](../../dev/spike/null-restricted-fields/report.md) covers the
native prototype only. It does not establish the baseline backend, the `:non-nil`
keyword or portable enforcement. [Embodiment](embodiment.md) records that evidence.
Revalidate internal native mechanisms on each supported EA build. Do not infer
support from annotation or method presence alone.
