# deftype* decision: record field storage

Part of the [interface and shape decisions](interface-and-shape.md).

## D4. Remove the hash fields from value output (state)

- Choice: for the record shape, value output does not declare `__hash` and
  `__hasheq`. The main constructor keeps its descriptor. Field slots are
  computed from the full parameter list, and the constructor writes only the
  kept fields. In value output, the value backend rejects each captured owner
  or companion class that accesses a removed field, with `:invalid-field`
  `:reason :removed-field-access`. An access is a `getfield` or `putfield`
  that names the target class and a removed field, other than the
  main-constructor writes that the transform removes. Reflective access is
  not checked. When one class accesses more than one removed field,
  `:field` is the first of them in the order `__meta`, `__extmap`, `__hash`,
  `__hasheq` (D2).
  Identity output keeps the fields and does not check access. It installs a
  capture loader only for constructor checks: enabled non-nil fields (D8) or
  the closed shape (D10). Removal of the hash fields is implicit for the
  record shape. The closed shape also removes `__meta` and `__extmap` (D10).
- Rejected:
  - Keep final fields that are always 0. Simple, but it adds 8 unused bytes
    to each instance.
  - An explicit option that names the fields to remove. It makes the
    contract wider. The only other use, `__meta` and `__extmap`, needs a
    constructor check as well as removal, so it is a shape (D9), not a
    field list.
  - Remove the fields from identity output too. That needs a transform where
    ordinary fallback needs none.
- Rationale: a value record cannot cache hashes, so the fields have no use.
  The compiler needs only the parameters. The transform already rewrites the
  main constructor, so the removal adds little.
- Improves: memory. Degrades: the two outputs have different field sets. Code
  that accesses `__hash` from outside fails on value output.
- Commitment: firm.
