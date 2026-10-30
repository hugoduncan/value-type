# Declaration acceptance audit

This audit covers acceptance 1 and the two declaration integration items in
slice 2. It does not complete representation policy or controlled native absence.
Test names below refer to `test/compatibility/value_type/fields_test.clj`.

## Metadata and ordinary fields

- `metadata-normalization-test` selects shorthand and explicit true metadata in
  declaration order and retains unrelated metadata.
- `portable-specifications-test` preserves ordered concrete Classes, descriptors
  and field metadata under both explicit native-capability inputs.
- `public-portable-metadata-errors-test` rejects invalid non-nil values and removed
  key presence through both public expansion entry points, without an owner or
  factory. `removed-metadata-test` also covers disabled and combined old-key uses.
- `mutable-declaration-error-test` checks both mutable keys with enabled, disabled
  and invalid constraints through both expansion entry points. Structured errors
  retain field context; no owner or factory is installed.
- `disabled-declaration-behavior-test` evaluates public declarations with absent,
  false and nil metadata. Primitive storage remains long and reference storage
  remains Object. Real factories and reflective constructors accept nil, String
  and non-String arguments for the ordinary reference field.
- `unrelated-field-metadata-test` verifies metadata retention in public expansion.
  This is expansion evidence, not whole-class bytecode attribute preservation.

## Concrete tags and errors

`public-imported-concrete-field-test` exercises imported, fully qualified and
Class-aliased Date tags, imported and fully qualified concrete nested Java tags,
and local, binary-name and Class-aliased defvaluetype child tags. The child holds
nil. Both expansion entry points retain exact final storage and valid references;
factory and reflective construction reject nil and wrong types. Baseline runs
exercise fallen-back children; the preview run exercises value children.
Namespace aliases are not Class aliases.

`public-portable-declaration-errors-test` rejects missing, forward, unresolved,
primitive, interface, abstract and array tags with structured definition context
and no installed owner or factory. `public-enclosing-type-error-test` rejects a
recursive replacement while preserving the existing Class and working factory.
Pure specification tests separately check validation order and exact Class identity.

## Verification and limits

The audit rerun used Java 8 and EA3 preview off/on with the fallback-test classpath.
Each fields namespace run passed 22 tests / 537 assertions. Commands and results
are recorded in implementation.md. No mocks or production changes were needed.

These default-policy declaration runs do not establish all-policy nested behavior,
controlled native absence, direct Java construction, whole-class attribute
preservation or public defect propagation. Those remain separate checklist items.
The declaration integration and structured-error items can be marked complete;
the policy/enforcement selection item remains open.
