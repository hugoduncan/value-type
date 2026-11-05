# deftype* decisions

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
commitment⟩. Commitment: `firm` or `provisional` (change when evidence
requires it).

## D1. Add `defvaluetype*` with `deftype*` syntax (interface)

- Choice: a public macro `value-type.core/defvaluetype*` that takes the
  arguments of the `deftype*` special form. It validates fields and applies
  capability and policy (D5). Without enabled non-nil fields, value output
  installs the capture loader at macroexpansion and expands to
  `(let [] (deftype* …) (check-value-class <class> <installed>) <class>)`,
  and fallback expands to a guarded `deftype*`. With enabled fields, the
  expansion is a receiving macro that installs the capture loader during
  analysis (D5). Each expansion evaluates to the class. Its `deftype*` call
  gets the macro arguments with no change, so field metadata stays.
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
  not checked. When one class accesses both fields, `:field` is `__hash`.
  Identity output keeps the fields and does not check access. It installs a
  capture loader only for enabled non-nil fields (D8). Removal is implicit
  for the record shape.
- Rejected:
  - Keep final fields that are always 0. Simple, but it adds 8 unused bytes
    to each instance.
  - An explicit option that names the fields to remove. No other use is
    known, and it makes the contract wider.
  - Remove the fields from identity output too. That needs a transform where
    fallback now needs none.
- Rationale: a value record cannot cache hashes, so the fields have no use.
  The compiler needs only the parameters. The transform already rewrites the
  main constructor, so the removal adds little.
- Improves: memory. Degrades: the two outputs have different field sets. Code
  that accesses `__hash` from outside fails on value output.
- Commitment: firm.

## D5. Same representation policy as `defvaluetype` (scope and authority)

- Choice: `defvaluetype*` uses the capability check and `value-type.fallback`
  policy of `defvaluetype` (D10, D12, D13). Without enabled non-nil fields,
  fallback emits a guarded `deftype*` with no transform. With enabled fields,
  fallback gives identity output with generated checks, and enforcement modes
  are fixed at expansion, as for `defvaluetype` (non-nil D2, D5). The
  receiving boundary derives the shape from the fields and repeats the
  declaration checks, in the D6 order; the expansion does not transport the
  shape. AOT fixes the representation, as for `defvaluetype` (D7).
- Rejected: separate policy or options for `deftype*` callers.
- Rationale: the application controls representation. One policy for all
  definitions keeps that control simple.
- Improves: consistency. Degrades: nothing found.
- Commitment: firm.

## D6. Support non-nil fields as `defvaluetype` does (scope)

- Choice: `defvaluetype*` accepts `:non-nil` field metadata in the plain and
  the record shape. The rules of the
  [non-nil field design](../non-nil-field-design.md) apply with no change:
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
- Declaration order, after `:mutable-field` and `:record-shape`:
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
  - Keep the rejection of `:non-nil` (the earlier choice). The main
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
- Commitment: firm (D7 non-nil gate passed).

## D7. Verification (verification)

- Spike first, on Valhalla `27-jep401ea3+1-1` with preview: transform a
  record-shaped `deftype*`, then call each constructor and `create`, read a
  record literal, and emit a record as a code constant.
- Non-nil gate, before D6 and D8 are implemented, on the same JVM with
  preview and `-Xverify:all`: a record-shaped value class with one native
  field (`NullRestricted`, concrete value-class storage) and one generated
  field (concrete identity-class storage) verifies. Each constructor,
  `create`, both record literals and a compiled record constant store valid
  values and reject `nil`. The compiler emits a record constant as a call
  to `create` with the map view of the instance, so a valid instance whose
  map view gives `nil` for an enabled field shows the rejection. A failure
  stops the work for a design decision.
- Unit tests: shape validation and its errors; transform output (no hash
  fields, rewritten main constructor, unchanged record constructors);
  removed-field access is rejected; non-nil declaration errors and their
  order (D6), also as the receiving macros repeat them (D5); record value
  output with concrete storage and checks; identity backend acceptance of
  delegating constructors and rejection of the shapes that D8 rejects.
- Integration tests in both representations: plain and record shapes, record
  literals, code constants, `create`, metadata, AOT in a subprocess JVM.
  With enabled fields: valid construction and `nil` or wrong-type rejection
  through every construction path of D6, native and generated enforcement,
  transferred expansions and AOT.
- Fallback tests, for both shapes: `silent`, `warn` and `error` policies
  give the same results as for `defvaluetype`, including identity output
  with generated checks on Java 8.
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
    constructor writes each enabled field once, as now. A delegating
    constructor that writes an enabled field, and a constructor that both
    creates an instance of the target class and calls one of its
    constructors, give `:unsupported-shape`. The rule does not depend on
    the shape. The identity backend does not validate the exact record
    shape.
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
- Commitment: firm (D7 non-nil gate passed).
