# deftype* decisions

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
commitment⟩. Commitment: `firm` or `provisional` (change when evidence
requires it).

## D1. Add `defvaluetype*` with `deftype*` syntax (interface)

See [decision details](decisions/interface-and-shape.md).

## D2. Identify the record shape by the compiler rule (data model)

See [decision details](decisions/interface-and-shape.md).

## D3. Keep the generated constructors; rewrite only the main one (behaviour)

See [decision details](decisions/interface-and-shape.md).

## D4. Remove the hash fields from value output (state)

See [decision details](decisions/interface-and-shape.md).

## D5. Same representation policy as `defvaluetype` (scope and authority)

See [decision details](decisions/policy-and-non-nil.md).

## D6. Support non-nil fields as `defvaluetype` does (scope)

See [decision details](decisions/policy-and-non-nil.md).

## D7. Verification (verification)

See [decision details](decisions/verification.md).

## D8. Enforce non-nil fields in the main constructor (behaviour)

See [decision details](decisions/policy-and-non-nil.md).

## D9. Select the closed record shape with an option (interface)

See [decision details](decisions/closed-record-shape.md).

## D10. Closed record shape: no metadata and no extension map (state and behaviour)

See [decision details](decisions/closed-record-shape.md).
