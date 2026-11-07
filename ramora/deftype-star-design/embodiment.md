# deftype* embodiment

Implemented, including D6, D8, D9, D10 and the D7 non-nil and closed gates.
The elements:

- `value-type.core`: `defvaluetype*` runs the D6 declaration checks at
  macroexpansion (`:mutable-field`, `:record-shape`, `:invalid-option`, metadata
  keys, `:hidden-field`, tag and type). It removes each `:value-type/closed`
  option from the `deftype*` call. With enabled fields or the closed shape, it
  expands to the receiving macros `value-field-deftype*` or
  `identity-field-deftype*`.
- `value-type.impl.fields`: the one owner of the non-nil declaration rules,
  including `:hidden-field`.
- `value-type.impl.transform` and `value-type.impl.value-backend`: record
  value output with field storage and checks in the main constructor only.
- `value-type.impl.identity-backend`: the D8 delegation rule.
- `value-type.impl.runtime`: `guarded-deftype*` for forms with neither
  enabled fields nor the closed shape.

Gate records: [D3 feasibility gate](feasibility-gate.md),
[non-nil gate](non-nil-gate.md) and [closed gate](closed-gate.md).

## Declaration checks (D6)

`defvaluetype*` checks at macroexpansion, before representation policy:
`:mutable-field`, `:record-shape`, `:invalid-option` (D9; the last
`:value-type/closed` occurrence applies), the metadata keys
(`:removed-nil-restricted`, `:invalid-non-nil`), `:hidden-field`, then the
`defvaluetype` tag and type reasons. The first failure wins.
`fields/specifications` and `fields/receiving-specifications` take
`{:hidden-field? true}`; the `:hidden-field` check runs after the metadata
keys and before the modes and tags. Name match ignores the namespace.

## Receiving macros

With enabled fields or the closed shape, the value expansion is
`value-field-deftype*` and the fallback expansion is `identity-field-deftype*`.
Both carry `tagname classname modes fields & opts+specs`, not the shape. Each
receiver derives the shape (`:plain`, `:record` or `:closed`) from the fields
and the option, removes the option from its `deftype*` call and passes the shape
to `install-receiving!`. Order:

- `value-field-deftype*`: `:mutable-field`, `:record-shape`, `:invalid-option`,
  value capability, metadata keys, `:hidden-field`, modes, tag and type, native
  field capability.
- `identity-field-deftype*`: `:mutable-field`, `:record-shape`,
  `:invalid-option`, metadata keys, `:hidden-field`, modes, tag and type, fixed
  fallback policy.

Forms with neither enabled fields nor the closed shape keep the value expansion
and `guarded-deftype*`. A closed form without enabled fields takes the receiving
path with empty specifications. Both receivers are `:no-doc`.

## Record value storage and checks (D8)

`transform/->record-value-class` takes the plain-path storage map. Only the
main constructor gets concrete storage, casts, `NullRestricted` on native
fields and `Objects.requireNonNull`. Delegating constructors, `create` and
constructor descriptors do not change. The `:record` and `:closed` companion
transforms rewrite field references, then check removed-field access.

For `:closed`, value output removes `__meta`, `__extmap`, `__hash` and
`__hasheq`. The main constructor starts with the D10 check (`aload`,
`ifnull`, `new`/`athrow` of `IllegalArgumentException`, message from
`fields/closed-check-message`) for `__meta`, then `__extmap`, before the
first field write and thus before the non-nil checks. Removed-field access
checks the ordered fields `__meta`, `__extmap`, `__hash`, `__hasheq`; the
first accessed field is `:field`.

## Identity delegation rule (D8)

The identity backend flags a constructor that calls
`invokespecial owner.<init>` (delegating) and one that has `NEW owner`. A
constructor with both, or a delegating constructor that writes an enabled
field, gives `:unsupported-shape`. Each other constructor writes each enabled
field once. The rule does not depend on the shape. Identity record output
keeps the hash fields.

`identity-backend/prepare` takes the shape as its fourth argument. It
processes the owner constructors when the owner has enabled fields or the
shape is closed. For a closed owner, a first read pass marks each
constructor that calls `invokespecial owner.<init>` as delegating, and gives
`:unsupported-shape` for a non-delegating constructor with fewer than four
parameters or a non-`Object` parameter at n − 4 or n − 3, and for a class
with no non-delegating constructor. The rewrite pass emits the D10 check at
the entry of each non-delegating constructor (parameters n − 4, n − 3; two
`F_SAME` frames; max stack at least 3). Identity closed output keeps all
four hidden fields; `__meta` and `__extmap` hold nil.

## Evidence

- Gate: [non-nil gate](non-nil-gate.md), EA3 preview, `-Xverify:all`.
- Gate: [closed gate](closed-gate.md), EA3 preview, `-Xverify:all`.
- Runtime suite: backend and public-form construction paths, bytecode
  inspection, receiving order and errors.
- Fallback suite (Java 8, EA3 preview off and on): delegation rule, identity
  receiving errors, transfer cells, policy cells.
- Closed tests: option errors and orders, construction paths and rejection
  in both outputs, byte inspection, removed-field access, transfer cells
  with the `:closed` shape, closed identity backend cases (runtime and
  fallback suites); EA3 flat-array evidence (`closed-record-layout-test`).
- Cross-JVM: AOT fixtures `PlainNonNil`, `RecNonNil`, `Closed` and
  `ClosedNonNil`.
