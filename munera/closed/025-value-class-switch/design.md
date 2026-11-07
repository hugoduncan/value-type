# Value-class switch

## Intent

Give applications a configuration-only return to identity semantics after a
JVM defect is found. Realize defvaluetype D18, except the AOT initialization
check (task 026).

## Problem and context

No property switches off value classes on a capable JVM. After a value-class
defect is found, the only returns are another JVM or no preview, which also
disables other preview code.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D18
(`ramora/defvaluetype-design/decisions/probe-and-format.md`).

## Scope

Include:

- JVM property `value-type.value-classes`, values `auto` (default) and `off`.
- Read with the value-class capability, once per loaded library runtime.
- `off`: value-class capability unavailable with `:disabled`, before any
  probe class is defined. Native-field and array-allocation capability
  derive from it, so they report `:disabled`. Array inspection does not
  change.
- Docs: the property, its values and purpose (a configuration-only return to
  identity semantics after a JVM defect), the `:disabled` reason keyword, its
  interaction with policy `error`, and the AOT limit of this slice.

Exclude:

- The AOT initialization check for value-class output (task 026).
- Per-type switches.
- Any change to policy behaviour.

## Contract

- `auto` or no property: behaviour does not change.
- `off`: definitions fall back by policy. Under `error` each definition
  throws the fallback Error (D10); docs say to use `off` with `silent` or
  `warn`. `make-array` and `into-array` throw `:array-unavailable` with
  `:reason :disabled`. `report` shows `:disabled` for `:value-class`,
  `:native-non-nil-field` and `:array-allocation`.
- `off` on a JVM without the required APIs (Java 8, JDK 24 to 26): the result is
  the same as on a capable JVM. The three keys show `:disabled`, not
  `:missing-classfile-api` or `:missing-value-predicate`. `:array-inspection`
  does not change.
- `:disabled` messages:
  - The value-class capability `:detail` is
    `Value classes are disabled by value-type.value-classes=off.` Native-field
    and array-allocation capability inherit it. Fallback passes it to
    `enforce-fallback!`, so it is the text of the `warn` warning and of the
    `error` Error after the prefix `value-type: identity deftype fallback: `.
  - The `:array-unavailable` message is the prefix `Value-array allocation
    is unavailable.`, a space and the `:detail`, as for `:failed-self-check`:
    `Value-array allocation is unavailable. Value classes are disabled by
    value-type.value-classes=off.` It does not name preview or export flags.
- Value-class AOT output in this slice (task 026 adds the general check):
  - Output with native non-nil fields calls `require-field-capability!` at
    initialization. Under `off` it throws `:field-capability-unavailable`
    with `:reason :disabled` (in the cause chain).
  - Other value-class AOT output does not read the switch. Under `off` its
    classes load and run as value classes.
  - The usage guide states this limit until task 026.
- An invalid value:
  - Valid values are exactly `auto` and `off`, case-sensitive. Other values
    are invalid, for example `OFF`, ` off` and the empty string.
  - The first capability read throws `IllegalArgumentException` with the
    message `Invalid value-type.value-classes: <pr-str of value>; expected auto
    or off`. The failure is fixed for the loaded library runtime: each later
    read throws the same exception, and a later property fix has no effect.
  - The result is the same on every JVM, with or without the required APIs.
  - Entry points that read the value-class capability and throw it:
    `report` (plain exception); a definition through the compiler, which
    throws `Compiler$CompilerException` with the exception as its cause;
    `make-array` and `into-array` (plain exception, before argument checks
    and input realization); initialization of AOT value output with native
    non-nil fields (the exception is in the cause chain).
  - `array-info` does not read the value-class capability and does not throw
    it.
  - `report` reads the policy before the capability (task 024). With an
    invalid policy, it throws the policy exception first.
  - Namespace loading succeeds.

## Architecture

- `impl.capability` owns the switch, inside its cached result. Readers do not
  read the property.
- The switch check runs first, before the API checks and the probe. Thus
  `off` defines no probe class, and the `off` reason and the invalid-value
  failure are the same on every JVM.

Rejected: a separate cache for the switch; switching off inspection (it does
not use value classes); a switch check after the API checks (the result of
`off` and of an invalid value then depends on the JVM).

## Qualities and risks

- Better: recoverability.
- Risk: users can leave `off` set and lose value classes without a message
  under `silent`. The report shows it.
- Risk: until task 026, `off` does not switch off most value-class AOT
  output. The usage guide states it.

## Acceptance

1. Cross-JVM child tests on EA3 with preview and export: with `off`, the
   report row is `:disabled` for `:value-class`, `:native-non-nil-field` and
   `:array-allocation`, and inspection is available; an ordinary
   `defvaluetype` gives an identity class under `silent`; under `warn` the
   warning contains the `:disabled` `:detail`; `make-array` throws
   `:array-unavailable` `:disabled` with the stated message; under `error`, a
   definition throws the fallback Error.
2. Cross-JVM child tests on Java 8: with `off`, the report row is `:disabled`
   for the same three keys and `:array-inspection` is `:missing-array-api`.
3. Invalid values `OFF`, ` off` and the empty string, on EA3 with preview and
   on Java 8: `report` throws the stated `IllegalArgumentException` at the
   first read and the identical exception at a later read; a definition
   throws `Compiler$CompilerException` with that cause; `make-array` and
   `into-array` throw it; `array-info` does not; loading succeeds.
4. With `auto` and without the property, all existing matrices pass in their
   current rows.
5. All runtime, fallback, cross-JVM, build and tooling suites pass.
   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
6. Docs: `report` docstring, `doc/API.md` (generated), usage guide,
   `CHANGELOG.md`, the embodiment gap table (D18 except AOT),
   `ramora/defvaluetype-design/fallback/selected-detector.md` (switch read
   first), `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
