# Saved-bytecode coverage audit

This audit separates saved-byte inspection from execution of saved output. Test
paths are relative to `test/compatibility/value_type/`. The identity inspection result is recorded in implementation.md.

## Existing evidence

`field_aot_test.clj` has a `persisted-restricted-field-output-test` that compiles
public Leaf/Holder declarations and calls `field_aot_checks.clj/inspect-output`.
The inspector parses files, not loaded Classes. Assertions cover:

- Concrete Leaf storage, nullable Object storage and long/double descriptors.
- The native field annotation and Object reference constructor parameters.
- A non-empty set of companion references, all with the concrete Leaf descriptor.

The recorded native field AOT namespace runs passed 5 tests / 48 assertions on
Java 8 and EA3 preview off/on hosts. Inspection itself runs in an EA3 child with
preview. This is native output evidence, not baseline bytecode inspection.

`identity_aot_test.clj` and `generated_value_aot_test.clj` instead verify saved
output by execution after deleting producer source. Their consumers reflect on
concrete final String storage and Object constructor parameters, execute captured
reads and reject nil/wrong types. Separate Java consumers also execute saved
constructors and companion reads. These fixtures install capture explicitly.

The public fixtures listed in [aot-acceptance-audit.md](aot-acceptance-audit.md)
provide source-free execution for identity-generated, value-generated, native
and mixed output. They do not replace direct inspection of saved instructions.

`public_field_aot_test.clj/persisted-public-identity-output-test` now compiles
public String constraints on Java 8 and inspects the saved files in a separate
EA3 process. Concrete String storage, absent native annotations, Object reference
constructor parameters and non-empty concrete companion references pass. The
inspector does not load the saved Classes. Existing native inspection still passes.

`public_field_aot_test.clj/persisted-public-generated-value-output-test` now
compiles the same public String constraint on preview-enabled EA3 under strict
policy. Separate file inspection verifies concrete storage, absent native annotations,
Object reference constructor parameters and non-empty concrete companion references.
The combined generated identity/value and native controls pass 3 tests / 19 assertions.
This does not test controlled native absence.

## Mixed output and scope

`public_mixed_field_aot_test.clj/persisted-public-mixed-output-test` compiles
public Long/String constraints on EA3 under strict policy. Separate file inspection
checks each field: concrete descriptors, native annotation on Long only, Object
reference constructor parameters and non-empty matching companion references.
All four saved-file controls pass together: 4 tests / 31 assertions.

The combined persistence item is complete. Saved descriptors and constructor
signatures complement the source-free execution evidence in the AOT audit;
inspection alone does not prove constructor enforcement or successful linkage.
Whole-class attribute preservation, unsupported shapes and controlled native
absence remain separate audit items.
