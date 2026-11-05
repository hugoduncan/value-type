# deftype* implementation detail

## Declaration checks (D6)

`defvaluetype*` checks at macroexpansion, before representation policy:
`:mutable-field`, `:record-shape`, the metadata keys
(`:removed-nil-restricted`, `:invalid-non-nil`), `:hidden-field`, then the
`defvaluetype` tag and type reasons. The first failure wins.
`fields/specifications` and `fields/receiving-specifications` take
`{:hidden-field? true}`; the `:hidden-field` check runs after the metadata
keys and before the modes and tags. Name match ignores the namespace.

## Receiving macros

With enabled fields, the value expansion is `value-field-deftype*` and the
fallback expansion is `identity-field-deftype*`. Both carry
`tagname classname modes fields & opts+specs`, not the shape. Each receiver
derives the shape and passes it to `install-receiving!`. Order:

- `value-field-deftype*`: `:mutable-field`, `:record-shape`, value
  capability, metadata keys, `:hidden-field`, modes, tag and type, native
  field capability.
- `identity-field-deftype*`: `:mutable-field`, `:record-shape`, metadata
  keys, `:hidden-field`, modes, tag and type, fixed fallback policy.

Forms without enabled fields keep the value expansion and
`guarded-deftype*`. Both receivers are `:no-doc`.

## Record value storage and checks (D8)

`transform/->record-value-class` takes the plain-path storage map. Only the
main constructor gets concrete storage, casts, `NullRestricted` on native
fields and `Objects.requireNonNull`. Delegating constructors, `create` and
constructor descriptors do not change. The `:record` companion transform
rewrites field references, then checks removed-field access.

## Identity delegation rule (D8)

The identity backend flags a constructor that calls
`invokespecial owner.<init>` (delegating) and one that has `NEW owner`. A
constructor with both, or a delegating constructor that writes an enabled
field, gives `:unsupported-shape`. Each other constructor writes each enabled
field once. The rule does not depend on the shape. Identity record output
keeps the hash fields.

## Evidence

- Gate: [non-nil gate](non-nil-gate.md), EA3 preview, `-Xverify:all`.
- Runtime suite: backend and public-form construction paths, bytecode
  inspection, receiving order and errors.
- Fallback suite (Java 8, EA3 preview off and on): delegation rule, identity
  receiving errors, transfer cells, policy cells.
- Cross-JVM: AOT fixtures `PlainNonNil` and `RecNonNil`.
