# Support non-nil fields in defvaluetype*

## Purpose and priorities

Give `defvaluetype*` the `:non-nil` field contract of `defvaluetype`, for
the plain and the record shape. One field contract then applies to both
macros, and callers that emit `deftype*` can declare required, typed fields.

Implement the changes to the
[deftype* design](../../../ramora/deftype-star-design.md) and its
[decisions](../../../ramora/deftype-star-design/decisions.md): D6 (support
non-nil fields), D8 (enforce them in the main constructor), the related
text in D1, D4, D5 and D7, and the D3 commitment. The
[non-nil field design](../../../ramora/non-nil-field-design.md) gives the
field contract. It does not change.

Priorities, in order: the field constraint holds through each construction
path; the application keeps its authority over representation; the existing
`defvaluetype`, `defvaluetype*` and array contracts do not change apart from
D6; the public surface stays the same macro.

## Problem and context

`defvaluetype*` now rejects `:non-nil` metadata with
`:reason :non-nil-unsupported` (the earlier D6). The rejection has no
technical cause: in the record shape the main constructor holds all field
writes, and the two delegating constructors and `create` call it.

Three parts of the implementation do not accept the combination now:

- The record value path (`->record-value-class`) rewrites the main
  constructor without field storage. It has no concrete storage, casts,
  native restriction or `nil` checks, and it does not rewrite field
  references.
- The identity backend requires each constructor to write each enabled
  field. The record delegating constructors write no fields, so it rejects
  them.
- `defvaluetype*` has no receiving boundary for enabled fields. Its fallback
  is a guarded `deftype*` with no capture loader, so identity output has no
  generated checks.

`defvaluetype` already solves the last point with receiving macros that fix
the enforcement modes at expansion and install capture during analysis
(non-nil D2, D5). `defvaluetype*` follows the same pattern.

The supported verification environments are Clojure 1.12.6, Java 8 and
Valhalla `27-jep401ea3+1-1` (EA3), with preview off and on. Identity output
with generated checks must work on Java 8. Optional JDK APIs must stay out of
baseline namespace loading. ASM is the bytecode library of the identity
backend; `java.lang.classfile` is the library of value output.

## Scope

Include:

- `:non-nil` metadata on fields of plain and record-shaped `defvaluetype*`
  forms, with the rules of the non-nil field design: metadata values, tag
  resolution, eligible types, per-field native or generated enforcement.
- The D6 declaration rules: `:hidden-field` for `:non-nil true` on a
  hidden-name field, and the D6 order of declaration errors.
- Value output with enabled fields for both shapes (D8). The record path
  keeps D3 shape validation and the D4 removed-field check.
- Identity output with generated checks for both shapes, on fallback and on
  Java 8. The identity backend accepts delegating constructors (D8).
- Transferred expansions with enabled fields, from `macroexpand` and
  `macroexpand-1`, with receiving analysis, as for `defvaluetype`. AOT fixes
  representation and enforcement.
- Forms without enabled fields keep their current expansion and behaviour.

Exclude:

- Changes to the non-nil field contract, including error text, the order in
  which invalid arguments fail, and new eligible types.
- Defaults or special messages for absent `create` keys, and other
  record-specific `nil` contracts (D6 rejects them).
- `:non-nil` on hidden-name fields.
- Exact record-shape validation in the identity backend (D8 rejects it).
- Closed-record integration, serialization guarantees, mutable caches,
  value-equality emulation, new representation policy and benchmarks.
- Task 018's unresolved review items.

The project does not keep backward compatibility. The reason
`:non-nil-unsupported` is removed with no alias.

## Contract

The deftype* design states the contract. These points define done:

- Errors and order. At macroexpansion, before representation policy:
  `:mutable-field`, `:record-shape`, metadata keys
  (`:removed-nil-restricted`, `:invalid-non-nil`), `:hidden-field`, then the
  tag and type reasons of `defvaluetype`. The first failure gives the error.
  The receiving, loader (`:field-class-mismatch`, `:jvm-rejected`), identity
  backend (`:unsupported-shape`), value transform (`:unexpected-constructor`)
  and value backend (`:invalid-field` `:reason :removed-field-access`)
  errors of the design also apply.
- Construction paths. These are the paths of Acceptance 2 and 3:
  - Both shapes: the main constructor, called directly and by reflection.
    `deftype*` emits no `->Name` factory.
  - Record shape, in addition: both delegating constructors, called
    directly and by reflection; `create`; the map literal `#ns.Name{…}`;
    the positional literal `#ns.Name[…]`; compiled record constants; and
    `assoc` that the caller implements with a constructor call, as
    `defrecord` does (`deftype*` emits no `assoc`).
- Behaviour. On each construction path, a `nil` value for an enabled field
  gives `NullPointerException`, and a value of the wrong type gives
  `ClassCastException`. `create` from a map with no entry for an enabled
  field fails. Valid values construct instances as now.
- Exception wrapping. The compiler, class initialization and reflection
  can wrap these exceptions. On Clojure 1.12.6, `load-string` of a literal
  gives `CompilerException`, `read-string` gives the exception directly,
  a compiled constant gives `ExceptionInInitializerError`, and
  `Constructor.newInstance` gives `InvocationTargetException`. Tests find
  the exception type in the cause chain.
- Record constant evidence. The compiler emits a record constant (an
  instance of `IRecord` and not of `IType`) as a call to `create` with the
  `java.util.Map` view of the instance (Clojure 1.12.6,
  `Compiler$ObjExpr.emitValue`). A valid instance with a map view that
  gives `nil` or a wrong-type value for an enabled field (for example, an
  `__extmap` entry with the field key, when the map view merges `__extmap`
  last) thus shows the rejection when the constant is evaluated. The
  feasibility gate and Acceptance 3 use this evidence.
- Identity delegation (D8). A constructor that calls a constructor of the
  target class on its own receiver delegates. Each other constructor writes
  each enabled field once. The identity backend checks the rule: a
  delegating constructor that writes an enabled field, and a constructor
  that both creates an instance of the target class and calls one of its
  constructors, give `:unsupported-shape`. The rule does not depend on the
  shape.
- Value record output (D8). The main constructor has the concrete storage,
  casts, native restriction and `nil` checks of the plain path. Owner and
  companion references to enabled fields use the concrete descriptors. The
  delegating constructors, `create` and the constructor descriptors do not
  change. Hash fields are removed as now.
- Identity record output keeps the hash fields and does not check access to
  them (D4).

## Architecture and responsibility

- The public boundary (`value-type.core`) owns declaration validation, the
  selection of enforcement modes and the Class-valued interface. Shared
  field specification code (`value-type.impl.fields`) stays the one owner of
  the non-nil declaration rules; `defvaluetype*` adds only `:hidden-field`.
- The receiving boundary for `defvaluetype*` follows the `defvaluetype`
  receiving macros. The expansion transports the form and the fixed
  enforcement modes, not the shape. The receiving macro derives the shape
  from the fields with the `:record-shape` check of the public boundary, and
  passes it to the capture loader. (The current `install-receiving!` always
  passes `:plain`.) It repeats the `defvaluetype*` declaration checks:
  `:record-shape` directly after `:mutable-field`, and `:hidden-field`
  directly after the metadata keys. The other checks keep the order of the
  `defvaluetype` receiving macros. Thus no transported shape can be invalid,
  and no new receiving error is necessary. Do not add a second policy path.
- The value backend owns record storage and checks, and composes them with
  the existing record shape validation and removed-field check.
- The identity backend owns the delegation rule. It stays baseline-safe
  (ASM only) and does not depend on the shape.
- The non-nil field design stays the authority for the field contract.

Prefer reuse of the plain-path storage, check and reference-rewrite code
over a second record implementation.

## Feasibility gate

D6 and D8 are provisional. First run the D7 non-nil gate on EA3 with preview
and `-Xverify:all`:

- A record-shaped value class with one native field (`NullRestricted`,
  concrete value-class storage) and one generated field (concrete
  identity-class storage) verifies.
- Each constructor, `create`, both record literal forms and a compiled
  record constant store valid values and reject `nil`. The compiled
  constant uses the record constant evidence of the Contract.

The gate is a separate script, `spike/record-value-type/non-nil-gate.clj`,
with its command in `spike/record-value-type/README.md`. `gate.clj` does not
change, so the SHA-256 that `ramora/deftype-star-design/feasibility-gate.md`
records for the D3 gate stays valid. Record the method, results, limits and
the SHA-256 of `non-nil-gate.clj` in
`ramora/deftype-star-design/non-nil-gate.md`, and the raw result in
`ramora/deftype-star-design/evidence/non-nil-gate-ea3-preview.edn`. If the
gate fails, record the evidence and stop for a design decision.

## Acceptance

1. The feasibility gate passes, with retained evidence.
2. Plain and record-shaped forms with enabled fields return the defined
   Class in both representations. Valid values construct through each
   construction path of the contract, and the fields read back with the
   declared values.
3. In both representations, `nil` and wrong-type values for an enabled
   field fail through each construction path of the contract. In value
   output, the record-shape tests cover one native and one generated field
   through each construction path of the record shape. Negative tests
   prove rejection, not only the absence of a result.
4. Inspection of emitted bytes proves: value record output has concrete
   storage, native restriction where selected, no hash fields and unchanged
   constructor descriptors and delegating constructors; identity record
   output has concrete storage, generated checks only in the main
   constructor, and the hash fields.
5. Each declaration error of the contract has a test, including
   `:hidden-field` and the error order. The identity backend accepts
   delegating constructors and gives `:unsupported-shape` for each
   constructor that D8 rejects. `:non-nil-unsupported` no longer exists.
6. Transferred expansions with enabled fields, from `macroexpand` and
   `macroexpand-1`, work for both shapes in both representations, in the
   receiving modes of `defvaluetype`, and give its receiving errors.
   Internal-form tests call each `defvaluetype*` receiving macro directly,
   with valid enforcement modes, and prove the order of the declaration
   checks that it repeats: for each adjacent pair in `:mutable-field`,
   `:record-shape`, metadata keys, `:hidden-field`, then tag and type
   reasons, a form with both errors gives the first. In value output, a received record-shaped expansion has no hash fields,
   which proves that the receiver passes the `:record` shape to the capture
   loader. AOT fixes representation and enforcement for both shapes in both
   representations, in a subprocess JVM.
7. Fallback policies `silent`, `warn` and `error` behave as for
   `defvaluetype` with enabled fields, for both shapes. Identity output with
   generated checks works for both shapes on Java 8 and on EA3 with preview
   off.
8. Forms without enabled fields keep their current behaviour. D6 tests
   replace the tests of the earlier `:non-nil-unsupported` rejection:
   `field-metadata-rejection-test` in
   `test/runtime/value_type/deftype_star_test.clj`, and the "declaration
   error" policy cell in `test/compatibility/value_type/runtime_test.clj`
   (`^:non-nil x` with no tag gives `:missing-tag` after D6). The other
   existing `defvaluetype*`, `defvaluetype` and array tests pass.
9. Relevant regression, packaging, tooling and quality checks pass:
   runtime, fallback and cross-JVM suites, `bb api-docs --check`,
   `bb clj-fmt:check`, `bb clj-kondo:lint` and `bb vale`. Record commands,
   results and limits in `implementation.md`.
10. The `defvaluetype*` docstring, `doc/API.md`, `doc/usage.md`,
    `CHANGELOG.md` and `mementum/state.md` state the non-nil support, the
    record behaviour and the evidence limits. In the docstring and
    `doc/usage.md`, "value-type does not validate constructor arguments"
    becomes "value-type does not validate the hash arguments", as in the
    Public contract of the deftype* design. The non-nil field design
    embodiment names `defvaluetype*`. In the deftype* design:
    - The Embodiment lists no D6/D8 exception or Gap paragraph. Its
      elements name the D6 declaration checks of `value-type.core`
      (including `:hidden-field`, in the D6 order), the `defvaluetype*`
      receiving macros, the delegation rule of the identity backend, and
      the record value storage and checks. It links the non-nil gate
      record.
    - "Qualities and risks" states the non-nil gate evidence and its
      limits in place of "This evidence has no non-nil fields".
    - One commitment rule applies: a decision that is provisional "until"
      a gate passes becomes `firm` when that gate passes with retained
      evidence. Thus D6 and D8 become `firm`, and D3 also becomes `firm`
      (the D7 gate of task 019 passed).

Create `plan.md`, `steps.md` and `implementation.md` before execution.
Planning must keep the feasibility gate first and map each acceptance
criterion to bounded evidence.
