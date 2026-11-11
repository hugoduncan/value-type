# deftype* decisions: policy and non-nil fields

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
commitment⟩. Commitment: `firm` or `provisional` (change when evidence
requires it). The [decision index](../decisions.md) lists all decisions.

## D5. Same representation policy as `defvaluetype` (scope and authority)

- Choice: `defvaluetype*` uses the capability check and `value-type.fallback`
  policy of `defvaluetype` (D10, D12, D13 of the defvaluetype design).
  Without constructor checks (no enabled non-nil fields and no closed shape),
  fallback emits a guarded `deftype*` with no transform. With enabled fields
  or the closed shape, fallback gives identity output with generated checks,
  and enforcement modes are fixed at expansion, as for `defvaluetype`
  (non-nil D2, D5). A closed form uses the receiving expansion, as a form
  with enabled fields does (D10). The
  receiving boundary derives the shape from the form (the fields and the
  `:value-type/closed` option) and repeats the declaration checks in the
  public-contract order; the expansion does not transport the shape. In
  `value-field-deftype*`, `:invalid-option` runs directly after
  `:record-shape` and before the value capability check. AOT fixes the representation, as for `defvaluetype` (D7).
- Rejected: separate policy or options for `deftype*` callers.
- Rationale: the application controls representation. One policy for all
  definitions keeps that control simple.
- Improves: consistency. Degrades: nothing found.
- Commitment: firm.

## D6. Support non-nil fields as `defvaluetype` does (scope)

- Choice: `defvaluetype*` accepts `:non-nil` field metadata in the plain and
  the record shape. The rules of the
  [non-nil field design](../../non-nil-field-design.md) apply with no change:
  metadata values, tag resolution, eligible types, per-field native or
  generated enforcement, receiving analysis of transferred expansions and
  fixed AOT enforcement (non-nil D1–D5). The key `:nil-restricted` is
  rejected by presence, with `:reason :removed-nil-restricted`. In a
  record-shaped form, `:non-nil true` on a hidden-name field gives
  `:invalid-field` `:reason :hidden-field` with `:field`. Absent, `false`
  and `nil` disable the constraint, as on other fields. The checks examine
  only fields that are symbols in a `fields` vector. Elements that are not
  symbols, and a `fields` value that is not a vector, go to `deftype*`, as
  in D2.
- Declaration order, after `:mutable-field`, `:record-shape` and
  `:invalid-option` (public-contract order):
  1. metadata keys (`:removed-nil-restricted`, `:invalid-non-nil`), for
     the first such field in field order;
  2. `:hidden-field`, for the first such field in field order;
  3. tag and type reasons of `defvaluetype` (`:missing-tag`,
     `:unresolved-tag`, `:enclosing-type`, `:primitive-type`,
     `:array-type`, `:interface-type`, `:abstract-type`), for each enabled
     field in field order.
- Record behaviour: the main constructor enforces the constraint (D8). Thus
  the delegating constructors, `create`, both record literal forms, record
  code constants and an `assoc` that the caller implements with a
  constructor call reject `nil` for an enabled field with
  `NullPointerException`, and a value of the wrong type with
  `ClassCastException`. `create` from a map that has no entry for an
  enabled field gives `nil` for it and fails. The compiler, class
  initialization and reflection can wrap these exceptions. Error text is
  not specified, as in the non-nil design.
- Rejected:
  - Reject `:non-nil`. The main
    constructor holds all field writes, so the restriction has no technical
    cause, and the two macros would have different field contracts.
  - Let the existing errors reject `:non-nil` on hidden fields
    (`:missing-tag` for `__meta` and `__extmap`, `:primitive-type` for the
    hash fields). Their fixes ("add a tag") lead to `:record-shape`. A
    dedicated reason names the real cause.
  - A record-specific nil contract, for example a default for absent
    `create` keys. It adds a second contract to the non-nil design.
- Rationale: one field contract for both macros. The record shape needs no
  new enforcement concept, only one check site (D8).
- Improves: consistency. Degrades: record operations that pass `nil` for an
  enabled field fail, although records usually accept `nil`; more
  verification.
- Commitment: firm.

## D8. Enforce non-nil fields in the main constructor (behaviour)

- Choice: the checks for enabled fields are in the constructors that write
  fields. In the record shape, only the main constructor writes fields; the
  two delegating constructors and `create` reach the checks through it.
  - Value output: the record path gives the main constructor the concrete
    storage, casts, native restriction and `nil` checks of the plain path,
    and rewrites owner and companion references to enabled fields to the
    concrete descriptors. The delegating constructors, `create` and all
    constructor descriptors do not change. D3 shape validation and the D4
    access check still apply.
  - Identity output: a constructor delegates when it calls a constructor of
    the target class on its own receiver (`this(…)`). Each other
    constructor writes each enabled field once. A delegating
    constructor that writes an enabled field, and a constructor that both
    creates an instance of the target class and calls one of its
    constructors, give `:unsupported-shape`. The rule does not depend on
    the shape. For the closed shape, the identity backend also adds the
    D10 check by parameter position in each non-delegating constructor
    (D10); this is the only use of the shape in the identity backend. The
    identity backend does not validate the exact record shape;
    `defvaluetype*` validates it at macroexpansion (D2).
- Rejected:
  - Checks in each constructor. The delegating constructors write no
    fields, and their arguments reach the main constructor unchanged.
  - Exact record-shape validation in the identity backend. It would repeat
    D3 in a second bytecode library (ASM, which loads on Java 8). Write
    tracking with delegation already proves that each completed construction
    checks each enabled field.
  - Checks in `create` only. Java callers and reflection bypass it
    (non-nil D4).
- Rationale: each construction path calls the main constructor once, so one
  check site covers all paths. The identity rule stays a property of
  constructor writes, as for `defvaluetype`.
- Improves: minimality, correctness. Degrades: the identity backend accepts
  a wider set of constructors than the plain shape needs.
- Commitment: firm.
