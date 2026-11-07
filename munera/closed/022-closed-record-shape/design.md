# Closed record shape for defvaluetype*

## Purpose and priorities

Let a caller of `defvaluetype*` select a record shape with no metadata and
no extension map. Value output then has only the declared fields, so the JVM
can flatten small records. The first caller is closed-record: its
`:value-type true` records cannot flatten now (closed-record task 022).

Implement the changes to the
[deftype* design](../../../ramora/deftype-star-design.md) and its
[decisions](../../../ramora/deftype-star-design/decisions.md): D9 (the
`:value-type/closed` option), D10 (removal of the hidden fields and the
constructor check), the closed gate and closed tests in D7, and the related
text in D1, D4, D5, D6 and D8. The design is the authority. If this file and the design
differ, the design applies; record the difference in `implementation.md`.

Priorities, in order: a map literal with an undeclared key never constructs
a closed record silently; the record contract of D3 (constructors, `create`,
literals, constants) keeps working; the application keeps its authority over
representation; forms without the option do not change; the public surface
grows by one option.

## Problem and context

A record-shaped value class keeps `__meta` and `__extmap`. With compressed
references they take 64 bits. On Valhalla `27-jep401ea3+1-1`, a
null-restricted atomic array is flat only when the instance fields fit in
64 bits. Observed with closed-record on that JVM: arrays of record-shaped
value classes with fields `[^long x]` or `[^int x ^int y]` are not flat;
`defvaluetype` classes with the same fields are flat.

The compiler needs all four hidden fields in the `deftype*` form (D2), so a
caller cannot omit them. Only value-type can remove them from the output, as
D4 already does for the hash fields. Removal alone loses data: `create`
passes the undeclared keys of a map in `__extmap`. D10 therefore adds a
constructor check. `create` passes nil for `__meta` and nil for `__extmap`
unless the map has an undeclared key (design Context), so one check in the
main constructor covers all paths.

Current implementation, relevant parts:

- `value-type.core/defvaluetype*` passes the options to `deftype*` with no
  change. Forms without enabled fields expand to the value expansion or
  `runtime/guarded-deftype*` (no transform on fallback). Forms with enabled
  fields expand to the receiving macros `value-field-deftype*` and
  `identity-field-deftype*`.
- `value-type.impl.transform/->record-value-class` removes the hash fields
  and rewrites only the main constructor.
- `value-type.impl.value-backend` checks removed-field access for
  `__hash` and `__hasheq`.
- `value-type.impl.identity-backend` (ASM, Java 8) inserts generated checks
  in constructors that write fields; delegating constructors are accepted
  (D8).

Verification environments: Clojure 1.12.6, Java 8 and Valhalla
`27-jep401ea3+1-1` (EA3), with preview off and on. Identity output with the
check must work on Java 8.

## Scope

Include:

- The option `:value-type/closed` with the D9 values, errors, check order and
  removal from the `deftype*` call, also in the receiving macros.
- Value output of a closed form: no `__meta`, `__extmap`, `__hash` or
  `__hasheq`; constructor descriptors, delegating constructors, `create` and
  `getBasis` unchanged; the D4 access check for all four fields.
- Identity output of a closed form: all four fields; the D10 check in the
  main constructor; the identity backend on fallback and on Java 8.
- The D10 check before the non-nil field checks, in both outputs, with and
  without enabled fields.
- The receiving expansion for closed forms without enabled fields (D5, D10),
  transferred expansions and AOT.
- The closed gate of D7, before the implementation.
- Documentation: `defvaluetype*` docstring and `doc/API.md`, the
  "`deftype*` forms" part of `doc/usage.md`, the README sentence on
  `deftype*` callers, `CHANGELOG.md`, `mementum/state.md`, and the
  deftype* embodiment (remove "Differences from the design" when they no
  longer apply).

Exclude:

- Changes to forms without `:value-type/closed true`, including the
  current record shape, other than the removal of a `false` or `nil`
  option from the `deftype*` call.
- Changes to the non-nil field contract.
- Loosely consistent classes and non-atomic arrays (D10 rejects them).
- An `ex-info` for the D10 check, or a specified error text.
- A promise of flattening. Tests may check layout only on EA3, as evidence.
- Closed-record changes (closed-record task 022), serialization guarantees,
  benchmarks.

The project does not keep backward compatibility.

## Contract

The deftype* design states the contract. These points define done:

- Option. `:value-type/closed` `true` selects the closed shape; `false`,
  `nil` and absent do not, and the form keeps its plain or record shape.
  Other values give `:invalid-option` `:reason :invalid-value`; `true` on a
  plain-shape form gives `:invalid-option` `:reason :record-shape-required`;
  data `:option`, `:value`, `:class-name`. When the option occurs more than
  once in the option position, the last occurrence applies, as in
  `deftype*`; the checks examine only its value. Order (public contract):
  `:mutable-field`, `:record-shape`, `:invalid-option`, metadata keys,
  `:hidden-field`, tag and type. The `deftype*` calls of `defvaluetype*`
  and of both receiving macros have no `:value-type/closed` option, for
  each option value.
- Construction paths: as task 020 (closed): the main constructor (direct and
  reflective), both delegating constructors (direct and reflective),
  `create`, the map literal, the positional literal, compiled record
  constants, and a caller `assoc` that calls the constructor with only the
  declared fields.
- Behaviour on each path, in both outputs: valid input constructs. A non-nil
  `__meta` or `__extmap` argument gives `IllegalArgumentException`; thus
  `create` and the map literal with an undeclared key fail. The check runs
  before the non-nil field checks: an undeclared key and a nil enabled field
  give `IllegalArgumentException`. Tests find the exception in the cause
  chain (`CompilerException`, `ExceptionInInitializerError`,
  `InvocationTargetException` wrappers as in task 020).
- Field sets. Value output: the declared fields only. Identity output: the
  declared fields and all four hidden fields; `__meta` and `__extmap` are nil
  after each valid construction.
- Removed-field access. In value output, a captured owner or companion class
  that reads or writes `__meta` or `__extmap` gives `:invalid-field`
  `:reason :removed-field-access` with `:field`. When one class accesses
  more than one removed field, `:field` is the first of them in the order
  `__meta`, `__extmap`, `__hash`, `__hasheq` (D4).
- Forms without `:value-type/closed true`: the expansion and the output do
  not change, except that `defvaluetype*` removes a `false` or `nil` option
  from the `deftype*` call.

## Architecture and responsibility

- `value-type.core` owns the option: validation, order, removal from the
  `deftype*` call and the choice of expansion. A closed form always uses the
  receiving expansion, so the receiver installs a transform in both outputs.
  The receiving macros derive the closed shape from the form (the fields and
  the option), repeat the declaration checks in the public-contract order
  and remove the option from their `deftype*` call. Do not transport a
  separate flag that can disagree with the form. Receiver orders (D5):
  - `value-field-deftype*`: `:mutable-field`, `:record-shape`,
    `:invalid-option`, value capability, metadata keys, `:hidden-field`,
    modes, tag and type, native field capability.
  - `identity-field-deftype*`: `:mutable-field`, `:record-shape`,
    `:invalid-option`, metadata keys, `:hidden-field`, modes, tag and type,
    fixed fallback policy.
- The shape value that the capture loader gets distinguishes plain, record
  and closed. Both backends get it. The value transform and the value
  backend own field removal and the access check. The identity backend owns
  the identity check; it stays ASM only and baseline-safe on Java 8.
- One D10 check site per output (D10), before the non-nil checks:
  - Value output: the main constructor, in the main-constructor rewrite
    that D8 already has.
  - Identity output: the entry of each non-delegating constructor (D8),
    before the first original instruction. The check reads the parameters
    at index n − 4 (`__meta`) and n − 3 (`__extmap`), where n is the
    parameter count of that constructor. The identity backend processes
    the constructors of closed forms also without enabled fields. A
    non-delegating constructor with fewer than four parameters or with a
    parameter at n − 4 or n − 3 that is not of type `Object`, and a closed
    class with no non-delegating constructor, give `:unsupported-shape`.
    Compiler output of a form that passes the declaration checks does not
    have these cases (public contract).
  - D8 stays: the delegation and write rule does not depend on the shape,
    and the identity backend does not validate the exact record shape. The
    closed shape only adds the entry check by parameter position. In
    compiler output, the main constructor is the only non-delegating
    constructor.
- `value-type.impl.fields` stays the owner of the non-nil rules. The option
  is not a field rule.

## Closed gate

D9 and D10 are provisional. First run the D7 closed gate on EA3 with preview
and `-Xverify:all`, as a separate script
`dev/spike/record-value-type/closed-gate.clj`, with its command in
`dev/spike/record-value-type/README.md`. `gate.clj` and `non-nil-gate.clj`
do not change, so their recorded SHA-256 values stay valid.

The gate: a spike transform of a record with fields
`[^long x __meta __extmap ^int __hash ^int __hasheq]` removes all four
hidden fields and adds the D10 check. The class verifies and is a value
class with the single field `x`. Each constructor, `create`, both record
literals and a compiled record constant work with valid input. A non-nil
`__meta` or `__extmap` argument and a map literal with an undeclared key
give `IllegalArgumentException`. A null-restricted atomic array of the
class is flat (`ValueClass.isFlatArray`).

Record the method, results, limits and the SHA-256 of `closed-gate.clj` in
`ramora/deftype-star-design/closed-gate.md`, and the raw result in
`ramora/deftype-star-design/evidence/closed-gate-ea3-preview.edn`. Link the
gate record from the design embodiment. If the gate fails, record the
evidence and stop for a design decision.

## Acceptance

1. The closed gate passes, with retained evidence.
2. Each option error of the contract has a test, and the last-occurrence
   rule for a repeated option has a test. Order tests cover each adjacent
   pair that includes `:invalid-option`: a form with both errors gives the
   earlier one.
   - `defvaluetype*` (public-contract order): `:record-shape` and
     `:invalid-option`; `:invalid-option` and metadata keys.
   - `value-field-deftype*`, called directly (receiver order):
     `:record-shape` and `:invalid-option`; `:invalid-option` and value
     capability (`:value-capability-unavailable`), on a JVM without value
     capability (Java 8, or EA3 with preview off).
   - `identity-field-deftype*`, called directly (receiver order):
     `:record-shape` and `:invalid-option`; `:invalid-option` and metadata
     keys.

   The other pairs keep their existing tests (tasks 017 and 020).
3. In both representations, closed forms with and without enabled fields
   construct through each construction path with valid input, and fail with
   `IllegalArgumentException` for a non-nil `__meta` or `__extmap` and for a
   map literal with an undeclared key. The check runs before the non-nil
   checks. Negative tests prove rejection.
4. Inspection of emitted bytes proves: value closed output has only the
   declared fields, unchanged constructor descriptors and delegating
   constructors, and the check in the main constructor only; identity closed
   output has all four hidden fields and the check in the main constructor
   (the only non-delegating constructor) only. The `deftype*` calls of
   `defvaluetype*` and of both receiving macros have no `:value-type/closed`
   option, for the values `true`, `false` and `nil` and for a repeated
   option.
5. In value output, access to `__meta` or `__extmap` from an owner or a
   companion class gives `:removed-field-access`.
6. Transferred expansions of closed forms, from `macroexpand` and
   `macroexpand-1`, work in both representations. AOT fixes representation
   for closed forms, in a subprocess JVM. Fallback policies `silent`, `warn`
   and `error` behave as for forms with enabled fields. Identity output of
   closed forms works on Java 8 and on EA3 with preview off.
7. On EA3 with preview and the `jdk.internal.value` export, a
   `value-type.array` array of a closed record with fields `[^long x]` gives
   `:flat? true`. This is EA3 evidence, not a contract.
8. Forms without `:value-type/closed true` keep their behaviour; forms
   without the option also keep their expansion. All existing
   `defvaluetype*`, `defvaluetype` and array tests pass.
9. Relevant regression, packaging, tooling and quality checks pass:
   runtime, fallback and cross-JVM suites, `bb api-docs --check`,
   `bb clj-fmt:check`, `bb clj-kondo:lint` and `bb vale`. Record commands,
   results and limits in `implementation.md`.
