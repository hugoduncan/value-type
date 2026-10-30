# Public constructor acceptance audit

## Scope

The slice 3 shared construction and public field-position items are complete. Existing receiving fixtures apply
one outcome table to direct Clojure constructors, generated factories and reflective
constructors. No additional fixture or production change is needed.

## Coverage

- `identity-receiving-test/receiving-identity-fields-test` checks generated identity
  fields with Date or a nullable defvaluetype child, String, nullable Object and
  long/double slots. Both constrained positions accept valid arguments and reject
  nil with NullPointerException and a wrong type with ClassCastException.
- `value-receiving-test/receiving-value-fields-test` checks generated value storage
  for Date and a nullable Object control with the same three constructor entries
  and exception outcomes.
- `value-receiving-test/receiving-native-and-mixed-fields-test` checks native/native
  and native/generated fields with nullable child data, nullable owner storage and
  long/double slots. Each constrained position rejects nil and wrong types.

Each fixture runs both public expansion entry points through eval, function analysis
and metadata-preserving source loading. Internal fixed-mode cases are additional
controls. Reflection unwraps only InvocationTargetException; direct and factory
calls must throw the expected exception without that wrapper. Successful construction
also checks retained field values. Tests use real compilers and JVMs without mocks.

## Verification

On 2026-10-27, identity cases passed on Java 8 and preview-disabled EA3 in normal
and interpreter execution: four runs, each 1 test / 585 assertions. Both value cases
passed on preview-enabled EA3 in normal and interpreter execution: two runs, each
2 tests / 954 assertions. All six test runs exited 0 with empty Scry failure
directories. Commands and the corrected runtime CLI invocation are in implementation.md.

## Policy coverage

Subsequent recorded runs cover both value fixtures under silent, warn and error,
in normal and interpreter execution: six runs of 2 tests / 954 assertions.
Baseline identity fixtures also pass warn on Java 8 and preview-disabled EA3,
in both execution modes: four runs of 1 test / 585 assertions.

The isolated `nested-public-warning-scope-test` checks silent and warn across a
nullable child and repeated public parent declarations. It asserts one warning
per runtime under warn and none under silent. The isolated
`strict-nested-public-declaration-test` checks repeated parent rejection without
installation or warnings. Its child is an ordinary deftype: strict policy would
reject a baseline defvaluetype child before parent analysis.

A focused rerun of these two tests passed on 2026-10-27: 2 tests / 40 assertions,
exit 0 and an empty Scry failure directory. Child JVMs cover Java 8 and
preview-disabled EA3 in normal and interpreter execution. The command is recorded
in implementation.md.

Together, these cases cover every constrained position, multiple constraints,
nullable controls, nested nullable data and long/double slots through public
receiving declarations. Successful direct, factory and reflective calls establish
argument order and arity. No new test fixture is needed for that checklist item.

## Limits

These runs do not establish controlled native absence, arbitrary constructor shapes
or all of acceptance 2–4. The broader runtime matrix remains open. Separate Java construction
is covered by [aot-acceptance-audit.md](aot-acceptance-audit.md). Field reads have a
separate [public read audit](public-read-acceptance-audit.md).
