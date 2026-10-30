# Public field-read acceptance audit

## Scope

The slice 3 public read item is complete. Existing tests use real compiler output
and JVM execution, without mocks. No new fixture is needed for this item.

Both `macroexpand` and `macroexpand-1` declarations pass eval, function analysis
and metadata-preserving source loading. Internal fixed-mode cases are controls,
not substitutes for these public cases.

## Coverage

- `identity-receiving-test/receiving-identity-fields-test` checks generated identity
  storage with Date or a nullable defvaluetype child, String, nullable Object and
  long/double fields. Type-hinted direct reads, reflective reads, protocol dispatch,
  Callable, Object toString, multi-arity captures and nested closures retain values.
  Both closure branches preserve the selected reference with nil and String data.
- `value-receiving-test/receiving-value-fields-test` checks generated value storage
  with Date and nullable Object. Direct and reflective reads, protocol dispatch,
  Callable, Object toString and nested closures retain values. Branch selection
  precedes the inner closure and uses both nil and String nullable inputs.
- `value-receiving-test/receiving-native-and-mixed-fields-test` checks native/native
  and native/generated value fields, nullable Object and long/double slots. Public
  mixed declarations use a native child and generated String. Direct reads,
  protocols, Callable, Object overrides and nested captured reads retain values.
  Exact field Classes and native annotations distinguish the selected modes.

Separate saved-bytecode and source-free Java consumer evidence is recorded in
[saved-bytecode-audit.md](saved-bytecode-audit.md) and
[aot-acceptance-audit.md](aot-acceptance-audit.md).

## Verification

On 2026-10-27, reran the identity test on Java 8 and preview-disabled EA3, each
in normal and interpreter execution: four runs, each 1 test / 585 assertions.
Reran both value tests on preview-enabled EA3 in normal and interpreter execution:
two runs, each 2 tests / 954 assertions. All six CLI exits were 0, with no failures
or errors. Each empty Scry failure directory was copied before the next run.
See implementation.md for commands and JVM paths. No internal exports were used.

## Limits

This closes the public read checklist item, not all of acceptance 4 or 8. These
local fixtures do not prove all-policy nested construction, controlled native
absence, arbitrary hierarchy merges or unrelated attribute preservation. The
separate backend rewrite/hierarchy and constructor items remain open. No new JVM
support or cross-process read coverage is inferred from these local runs.
