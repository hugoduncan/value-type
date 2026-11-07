# deftype* design

This design follows the architecture model in
`~/projects/hugoduncan/architecture/ramora/INDEX.md`:

```text
Purpose + Context → Architecture → Embodiment → Observed qualities
```

It extends the [defvaluetype design](defvaluetype-design.md). Read that
design first for the capture loader, transform, fallback policy and AOT rules.

Read this index first. Then read only the sections that you need.

## Summary

`defvaluetype*` takes the arguments of the `deftype*` special form and
evaluates to the defined class. It accepts the plain shape (one constructor)
and the record shape (the compiler record suffix `__meta`, `__extmap`,
`__hash`, `__hasheq`). It keeps the compiler-generated record constructors
and `create`, and rewrites only the main constructor. Value output removes
`__hash` and `__hasheq`. The option `:value-type/closed true` selects the
closed record shape: value output also removes `__meta` and `__extmap`, and
the main constructor rejects non-nil values for them. Representation policy,
fallback and `:non-nil` fields are as for `defvaluetype`.

## Sections

- [Purpose](deftype-star-design/purpose.md): first user, priorities,
  success criteria.
- [Context](deftype-star-design/context.md): the compiler record code,
  hash and layout constraints, included and excluded scope.
- [Public contract](deftype-star-design/public-contract.md): syntax, caller
  obligations, closed record shape, non-nil fields, errors and check order.
- [Decisions](deftype-star-design/decisions.md): the choices, rejected
  alternatives and rationale.
- [Embodiment](deftype-star-design/embodiment.md): namespaces, differences
  from the design, receiving macros, backends, evidence.
- [Qualities and risks](deftype-star-design/qualities.md): compatibility,
  cost, layout, observed behaviour, risks.

## Decisions

- D1: add `defvaluetype*` with `deftype*` syntax.
- D2: identify the record shape by the compiler rule, and reject partial
  forms.
- D3: keep the compiler-generated constructors and `create`; rewrite only the
  main constructor.
- D4: remove the hash fields from value output; keep the constructor
  parameters.
- D5: use the same representation policy and fallback as `defvaluetype`.
- D6: support `:non-nil` fields as `defvaluetype` does.
- D7: verification.
- D8: enforce non-nil fields in the main constructor.
- D9: select the closed record shape with an option.
- D10: closed record shape: no metadata and no extension map.

## Gate records

- [D3 feasibility gate](deftype-star-design/feasibility-gate.md)
- [Non-nil gate](deftype-star-design/non-nil-gate.md)
- [Closed gate](deftype-star-design/closed-gate.md)
