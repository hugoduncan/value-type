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
- Docs: the property, its interaction with policy `error`, the reason.

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
- An invalid value throws at the first capability read, with a message that
  names the valid values. Namespace loading succeeds.

## Architecture

- `impl.capability` owns the switch, inside its cached result. Readers do not
  read the property.
- The switch check runs before the API checks and the probe, so `off` defines
  no probe class.

Rejected: a separate cache for the switch; switching off inspection (it does
not use value classes).

## Qualities and risks

- Better: recoverability.
- Risk: users can leave `off` set and lose value classes without a message
  under `silent`. The report shows it.

## Acceptance

1. Cross-JVM child tests on EA3 with preview and export: with `off`, the
   report row is `:disabled` for the three derived keys and inspection is
   available; an ordinary `defvaluetype` gives an identity class under
   `silent`; `make-array` throws `:array-unavailable` `:disabled`; under
   `error`, a definition throws the fallback Error.
2. An invalid value throws at the first capability read; loading succeeds.
3. With `auto` and without the property, all existing matrices pass in their
   current rows.
4. All runtime, fallback, cross-JVM, build and tooling suites pass.
   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
5. Docs: `report` docstring, `doc/API.md` (generated), usage guide,
   `CHANGELOG.md`, the embodiment gap table (D18 except AOT),
   `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
