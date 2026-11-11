# deftype* decisions: verification

Each decision is d = ⟨subject, choice, rejected, rationale, qualities,
commitment⟩. Commitment: `firm` or `provisional` (change when evidence
requires it). The [decision index](../decisions.md) lists all decisions.

## D7. Verification (verification)

- Spike first, on Valhalla `27-jep401ea3+1-1` with preview: transform a
  record-shaped `deftype*`, then call each constructor and `create`, read a
  record literal, and emit a record as a code constant.
- Non-nil feasibility gate for D6 and D8, on the same JVM with
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
- Closed feasibility gate for D9 and D10, on the same JVM with
  preview and `-Xverify:all`: a spike transform of a closed record with the
  fields `[^long x __meta __extmap ^int __hash ^int __hasheq]` removes all
  four hidden fields and adds the D10 check. The class verifies and is a
  value class with the single field `x`. Each constructor, `create`, both
  record literals and a compiled record constant work with valid input. A
  non-nil `__meta` or `__extmap` argument and a map literal with an
  undeclared key give `IllegalArgumentException`. A null-restricted atomic
  array of the class is flat. A failure stops the work for a design
  decision.
- Closed tests, in both representations and for each construction path:
  option errors and their order (D9); option removal from the `deftype*`
  call; field sets (value: declared fields only; identity: all four hidden
  fields, `__meta` and `__extmap` nil); the D10 check, before the non-nil
  checks; removed-field access of `__meta` and `__extmap` (value); transfer
  and AOT; Java 8 identity output.
- Fallback tests, for both shapes: `silent`, `warn` and `error` policies
  give the same results as for `defvaluetype`, including identity output
  with generated checks on Java 8.
- Commitment: firm.
