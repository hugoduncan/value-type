# deftype* decisions: interface, shape and value output

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
commitment⟩. Commitment: `firm` or `provisional` (change when evidence
requires it). The [decision index](../decisions.md) lists all decisions.

## D1. Add `defvaluetype*` with `deftype*` syntax (interface)

- Choice: a public macro `value-type.core/defvaluetype*` that takes the
  arguments of the `deftype*` special form. It validates fields and applies
  capability and policy (D5). Without enabled non-nil fields and without
  the closed shape, value output installs the capture loader at
  macroexpansion and expands to
  `(let [] (deftype* …) (check-value-class <class> <installed>) <class>)`,
  and fallback expands to a guarded `deftype*`. With enabled fields or the
  closed shape, the expansion is a receiving macro that installs the
  capture loader during analysis (D5, D10). Each expansion evaluates to the
  class. Its `deftype*` call gets the macro arguments with no change, except
  the removal of each `:value-type/closed` occurrence (D9), so field
  metadata stays. Changed by defvaluetype
  [D20](../../defvaluetype-design/decisions/compiler-boundary.md): value
  output without enabled fields also expands to a receiving macro, with no
  `<installed>` literal.
- Rejected:
  - A `defvaluetype` option. `deftype` rejects the hidden record names and
    does not emit the record constructors.
  - A function that wraps any form that defines one class. It cannot validate
    the shape before compilation, and its contract is wider than necessary.
  - Callers that call `value-type.impl.loader/install!` directly. That is
    internal, and it skips policy and checks.
- Rationale: callers that emit `deftype*` already have the exact form. A
  one-to-one replacement is the smallest interface. The `*` suffix follows the
  Clojure convention for the lower-level form.
- Improves: minimality, compatibility. Degrades: callers depend on the
  `deftype*` special form, which is not public Clojure API.
- Commitment: firm.

## D2. Identify the record shape by the compiler rule (data model)

- Choice: the hidden names are `__meta`, `__extmap`, `__hash` and
  `__hasheq`, compared by symbol name as in the compiler. A form has the
  plain shape when no field has a hidden name. It has the record shape when
  it has n ≥ 4 fields, the last four fields have hidden names, and every
  hidden-name field is in place. A hidden-name field is in place when it is
  the unqualified symbol at index n − 4 + k, where k is its index in
  (`__meta` `__extmap` `__hash` `__hasheq`), and it has its required tag:
  no tag (`:tag` absent or nil) for `__meta` and `__extmap`, and the
  unqualified symbol `int` for `__hash` and `__hasheq`. Reject all other
  forms at macroexpansion with `:invalid-field` `:reason :record-shape`.
  `:field` is the first hidden-name field, in field order, that is not in
  place; if there is none, it is the first hidden-name field. Elements that
  are not symbols, and a `fields` value that is not a vector, go to
  `deftype*`.
- Rejected:
  - Accept any trailing subset, as the compiler does. The compiler then
    emits constructors that do not verify.
  - Accept hidden names that are not trailing as ordinary fields, as the
    compiler does. Value output would keep a field named `__hash`, and the
    names would have two meanings.
  - Accept other tags. Most do not verify with the constants that the
    compiler pushes (`null` and `int` 0). `defrecord` uses exactly these
    tags, and no use for other tags is known.
- Rationale: the compiler counts any trailing hidden names, but its generated
  code assumes all four, in order, with `int` hash fields. Early rejection
  gives a clear error instead of a `VerifyError`.
- Improves: correctness, diagnosability. Degrades: nothing found.
- Commitment: firm.

## D3. Keep the generated constructors; rewrite only the main one (behaviour)

- Choice: the value transform finds the main constructor by its descriptor
  (all fields). It rewrites only that constructor. It accepts the two record
  constructors when each body only loads `this` and its arguments, pushes the
  expected constants, and calls the main constructor with `invokespecial`.
  Other constructor sets give `:unexpected-constructor`. `create` and
  `getBasis` do not change.
- Rejected:
  - Remove the extra constructors and `create`. Record literals, code
    constants and `create` callers then fail.
  - Rewrite every constructor (the current rule). The record constructors
    write no fields.
- Rationale: the extra constructors and `create` only call the main
  constructor. Strict field rules then apply only in the main constructor.
- Improves: compatibility. Degrades: more coupling to compiler output.
- Assumption: JEP 401 permits `this(…)` calls in value-class constructors.
  D7 verifies this first.
- Commitment: firm (D7 gate passed).

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
    fallback now needs none.
- Rationale: a value record cannot cache hashes, so the fields have no use.
  The compiler needs only the parameters. The transform already rewrites the
  main constructor, so the removal adds little.
- Improves: memory. Degrades: the two outputs have different field sets. Code
  that accesses `__hash` from outside fails on value output.
- Commitment: firm.
