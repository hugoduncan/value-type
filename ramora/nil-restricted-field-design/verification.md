# Nil-restricted field verification

Parent: [Design](../nil-restricted-field-design.md).

## Required qualities

- Correctness: all supported construction paths enforce type and nil restriction.
- Predictability: unsupported definitions fail; fallback cannot weaken the contract.
- Local change: metadata resolution, loader transport and byte transformation have
  separate responsibilities. Internal JDK dependencies do not reach public loading.
- Compatibility with compiler conventions: constructor arity, factories, methods
  and unrelated metadata remain intact.
- Verifiability: semantic tests do not require flattened storage. Layout evidence
  remains separate from correctness and performance claims.

## Contract checks

Check shorthand and explicit metadata forms. Cover absent, true, false, nil and
invalid metadata values. Verify that disabled restriction preserves ordinary
field behavior and that an ordinary type hint does not change the descriptor.

For enabled fields, check missing and unresolved tags, aliases/imported class
names, concrete value classes, primitives, identity classes, interfaces, abstract
classes, arrays, self-reference and forward references. Accept eligible value
classes not created by this library. Reject mutable fields consistently.

Verify multiple restricted fields mixed with nullable and primitive fields,
including two-slot constructor arguments. A valid nested value may itself contain
nil. Check every restricted constructor position and wrong-type input.

Exercise direct construction, generated factories and reflection. Check direct
and reflective reads, protocols/interfaces, Object overrides, inner functions,
closures and nested control flow. Use generated classes to detect stale field
references, not only reflection over field declarations. Preserve unrelated
annotations and field attributes. Include the existing compatibility corpus.

## Runtime and deployment checks

Run isolated JVM cases for supported preview, preview disabled and unsupported
JVMs. Requiring the library must remain safe. Under each fallback policy, enabled
fields must fail without native support, while unmarked definitions retain their
existing behavior. Cover discarded and transferred macroexpansions so callers
cannot bypass the capability requirement.

The capability probe must distinguish absent support from unexpected linkage,
verification or implementation defects. A successful probe must demonstrate null
rejection rather than just class definition. No layout diagnostic or annotation
package export should be needed to construct supported classes.

Compile AOT output and consume it without source in a fresh process. Include
separately compiled consumers, generated companion classes, direct Java callers
and incompatible JVM rejection. Check redefinition and class-loader boundaries:
resolution must use the intended Class, not merely a matching binary name.

Run semantic cases with field flattening enabled and disabled, and with the
interpreter and normal compiler configuration. Warming a loop is only a smoke
check; do not claim coverage of all JIT paths from it.

## Layout and performance evidence

Query only valid field objects through diagnostics in isolated processes.
Restriction must remain true when flattening is disabled. Compare restricted and
typed nullable fields with identical nested payloads. A non-flat result is valid.
Measure size, allocation or throughput separately before making benefit claims.

## Evidence and limits

The [experiment report](../../spike/null-restricted-fields/report.md) and its
retained output give the current bounded observations. The prototype verifies
selected constructor, read, closure, interpreter, flattening-disabled and AOT
cases on `27-jep401ea3+1-1` with Clojure 1.12.6. It does not establish the full
contract above. Outstanding implementation gaps belong in the parent document's
embodiment section, not in task-status annotations here.

Native support is internal. New EA builds may change annotation recognition,
eligibility, initialization or layout. Revalidate these boundaries rather than
extending support from API-name matches. Mutual type cycles and future support
for forward declarations need a separate design; this contract requires already
defined field types.
