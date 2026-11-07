# Design steps

- [x] Architecture: specify the identity-output mechanism for the D10 check.
  "Put the D10 check in the main-constructor rewrite that D8 already has"
  and "one constructor-check mechanism for both outputs" fit only value
  output (`java.lang.classfile` main-constructor rewrite). The identity
  backend (ASM, Java 8) has no main-constructor rewrite: it adds
  `requireNonNull` at each `putfield` of an enabled field, it does not get
  the shape (`install!`: "only value output reads it"), it does not process
  constructors when no field is enabled, and D8 keeps it shape-independent.
  The compiler writes declared fields before `__meta` and `__extmap`, so a
  check at their `putfield` runs after the non-nil checks and breaks the
  contract order. State in the Architecture section: (a) the identity
  backend gets the shape (plain, record, closed); (b) how it finds the
  `__meta` and `__extmap` arguments (for example, the parameters at
  n − 4 and n − 3 of each non-delegating constructor) and puts the check at
  constructor entry, before the generated non-nil checks; (c) that it
  processes constructors of closed forms without enabled fields; (d) how
  this limited shape knowledge agrees with D8 (shape-independent rule, no
  exact record-shape validation), or which D8 text changes. Replace "one
  mechanism for both outputs" with one check site per output: the main
  constructor in value output and the non-delegating constructors in
  identity output.
- [x] Ambiguity: state the treatment of `:value-type/closed false` and
  `:value-type/closed nil`. The public contract says `defvaluetype*`
  removes the option from the `deftype*` call, with no value condition. The
  task says "Forms without the option: the expansion and the output do not
  change" and excludes "forms without `:value-type/closed true`". State
  whether `false` and `nil` are removed from the `deftype*` call (and from
  the receiving-macro `deftype*` calls), and that these forms otherwise keep
  the record-shape expansion. Also state the result when the option occurs
  more than once in the option position (`deftype*` keeps the last value):
  an error, or the last value applies.
- [x] Ambiguity: state where `:invalid-option` runs in
  `value-field-deftype*`, relative to the value capability check. Its
  current order is `:mutable-field`, `:record-shape`, value capability,
  metadata keys. "Repeat the option checks in the D9 order" permits both
  "before value capability" and "after value capability". Acceptance 2
  tests each adjacent pair in each receiving macro, so the order must be
  exact.
- [x] Ambiguity: state the `:field` value of `:removed-field-access` when
  one class accesses more than one removed field of a closed form. D4
  gives `__hash` only for "both" hash fields. Give one rule for all four
  fields (for example, the first in the order `__meta`, `__extmap`,
  `__hash`, `__hasheq`), so that tests are deterministic.
- [x] Inconsistency: align D5 and D6 with D9 and the task Architecture
  section. D5 says the receiving boundary "derives the shape from the
  fields" and repeats the checks "in the D6 order". The closed shape
  derives from the fields and the option, and the task says the receivers
  "derive the closed shape from the form". The D6 order list goes from
  `:record-shape` directly to the metadata keys, with no `:invalid-option`;
  D9 and the public-contract order put `:invalid-option` between them.
  Change the D5 text to "from the form (the fields and the option)" and
  name the public-contract order, or add `:invalid-option` to the D6 list,
  so that the task does not inherit two orders from its authority.
- [x] Architecture: align D1 (firm interface decision) with D9 and D10.
  `decisions/interface-and-shape.md` D1 says "Its `deftype*` call gets the
  macro arguments with no change", and it selects the expansion only by
  enabled non-nil fields ("Without enabled non-nil fields, value output
  installs the capture loader at macroexpansion … With enabled fields, the
  expansion is a receiving macro"). D9 removes each `:value-type/closed`
  occurrence from the `deftype*` call, and D10 sends each closed form to
  the receiving expansion. The task lists the related text of D4, D5, D6
  and D8, but not D1, so the authority keeps a firm interface rule that
  the implementation breaks. Change D1: the `deftype*` call gets the macro
  arguments with no change, except the removal of `:value-type/closed`
  (D9); a form with enabled fields or the closed shape uses the receiving
  expansion (D5, D10). Add D1 to the list of related text in the task
  "Purpose and priorities" section.
- [x] Ambiguity: state which adjacent pairs Acceptance 2 requires.
  "each adjacent pair in the declaration order, at the public macro and in
  each receiving macro" permits two readings: (a) only the pairs that
  include `:invalid-option`, or (b) every pair of the full order, also the
  pairs that existing tests (tasks 017, 020) already cover. It also does
  not name the order for the receiving macros: the public-contract order,
  or the receiver orders of the Architecture section, which add value
  capability, modes, native field capability and fixed fallback policy.
  State the required pairs, for example: at the public macro,
  `:record-shape`/`:invalid-option` and `:invalid-option`/metadata keys;
  in `value-field-deftype*`, `:record-shape`/`:invalid-option` and
  `:invalid-option`/value capability (testable where value capability is
  unavailable, for example Java 8); in `identity-field-deftype*`,
  `:record-shape`/`:invalid-option` and `:invalid-option`/metadata keys.
- [x] Inconsistency: align `context.md` and the public-contract error text
  with D10 (same cause as the D1 step; edit all three together).
  - `context.md` "Included" says that a value expansion without enabled
    non-nil fields, evaluated apart from its macroexpansion, fails with
    `:not-installed` or `:not-value-class`, and that only an expansion with
    enabled fields keeps receiving analysis. D10 sends each closed form to
    the receiving expansion, and Acceptance 6 requires transferred
    expansions of closed forms to work, also without enabled fields. State
    that a closed form behaves as a form with enabled fields here.
  - `public-contract.md` "Errors" names `:unsupported-shape` only as an
    identity backend error "of `defvaluetype`". D10 and the task
    Architecture section add closed-shape cases (a non-delegating
    constructor with fewer than four parameters or a non-`Object`
    parameter at n − 4 or n − 3; a closed class with no non-delegating
    constructor). Name these cases in the contract, or state that they
    cannot occur with compiler output and are internal.
- [x] Ambiguity (plan review): choose the bytecode form of the identity
  D10 check, and state how tests find the check in emitted bytes. Plan
  step 5 and Slice 5 permit "a stack map frame and max stack for
  `(ClassWriter. 0)`, or a branch-free form". The contract requires
  `IllegalArgumentException`; no branch-free sequence of JDK methods on
  Java 8 throws it for a non-nil reference (a `checkcast` gives
  `ClassCastException`). A branch-free form thus needs a helper method,
  and the project has no Java sources: generated identity classes would
  then reference a new value-type class (Java 8, AOT and packaging
  effects). State the form, for example: `aload`, `ifnull`, `new`/`athrow`
  of `IllegalArgumentException`, a frame at the target equal to the
  constructor entry frame, max stack + 3. Acceptance 4 requires "the check
  in the main constructor only" in both outputs, so also state the byte
  signature that tests look for (for example, `new
  java/lang/IllegalArgumentException` in a constructor).
- [x] Ambiguity (plan review): state which identity-backend constructor
  rules apply to a closed form without enabled fields. Now
  `identity-backend/rewrite` applies the D8 rules (control-flow rejection,
  "both creates and calls the target", write tracking) only when `types`
  is not empty. Plan step 5 says "first read pass classifies constructors
  (D8 rule unchanged)" and "process closed forms without enabled fields",
  but does not say if the creates-and-calls and control-flow rejections
  apply then. Classification needs them: a constructor that creates an
  instance and calls a target constructor, or that calls `this(…)` on one
  path only, has no defined class, so the entry check can be skipped.
  State the rule, and state if the closed `:unsupported-shape` cases
  (fewer than four parameters, non-`Object` parameter at n − 4 or n − 3,
  no non-delegating constructor) get direct backend tests with synthetic
  class bytes; the Acceptance and Slice 6 do not name them.
- [x] Inconsistency (plan review): fix the fixture of the
  `:record-shape`/`:invalid-option` order tests in plan.md step 6 and
  steps.md Slice 6. Both say "a plain-shape form with an invalid option
  value". A plain-shape form has no `:record-shape` error
  (`core/record-shape` gives `:plain`), so the test cannot show the order.
  implementation.md ("Second design-step slice applied") states the
  intent: the form has a `:record-shape` error (for example a misplaced
  hidden field) and an invalid option value (`:reason :invalid-value`),
  because `:record-shape-required` needs a plain-shape form. Change both
  texts to this fixture, for `defvaluetype*` and both receiving macros.
- [x] Ambiguity (plan review, pass 2): state the content of the user doc
  changes in plan.md step 7 and steps.md Slice 7. "Update the
  `defvaluetype*` docstring and `doc/API.md` with the option, errors and
  order" and "update `doc/usage.md` "`deftype*` forms"" do not say which
  current statements change. The docstring and `doc/usage.md` tie
  receiving and fallback behaviour to enabled fields only, and closed
  forms make these statements false: "With enabled fields, the expansion
  is a receiving form", "The fallback expansion without enabled fields is
  guarded `deftype*` output with no transform", "A value expansion
  without enabled fields evaluated apart from its macroexpansion fails
  with `:not-installed` or `:not-value-class`", and `:removed-field-access`
  "reads or writes `__hash` or `__hasheq`". The numbered order list in
  `doc/usage.md` and the docstring error list have no `:invalid-option`.
  State the content, from `public-contract.md` "Closed record shape" and
  "Errors": the option (values, last occurrence, removal from the
  `deftype*` call); the closed caller obligations; the outputs and the
  `IllegalArgumentException` behaviour; `:invalid-option` in each order
  list; "enabled fields or the closed shape" in the receiving, fallback
  and transferred-expansion statements; all four fields for
  `:removed-field-access` of closed forms. Also state that `doc/API.md`
  is generated from the docstrings (`bb api-docs`), not edited.
- [x] Inconsistency (plan review, pass 2): align steps.md with plan.md in
  two places.
  - Verification JVMs of the identity entry check. plan.md "Risks" says
    "Verify with `-Xverify:all` on Java 8 and on EA3 with preview off";
    steps.md Slice 5 says "Verify with `-Xverify:all` on Java 8" only.
    Use one list in both files.
  - Slice order of `fields/closed-check-message`. steps.md Slice 4 (value
    output) uses the function; Slice 5 (identity output) adds it, and
    plan.md step 5 defines it after step 4 uses the check form. Move the
    "Add `fields/closed-check-message`" item to Slice 4 (or before it),
    and let Slice 5 use it.
- [x] Ambiguity (plan review, pass 3): state the check form of the closed
  gate spike in plan.md step 1 and steps.md Slice 1. Both say only "add
  the D10 check"; the check form is first defined in plan step 5, after
  the gate. The gate spike has its own transform (`gate.clj` and
  `non-nil-gate.clj` do not use the production transform), so it can use
  another form and still pass. The D10 check is the first branch in a
  value-class constructor: the current value constructors have no branch
  (`requireNonNull` and `checkcast` only), so no stack map frame occurs
  before the strict-field writes. The EA3 verification risk is in this
  frame. State that the spike uses the step 5 form (`aload`, `ifnull`,
  `new`/`dup`/`ldc`/`invokespecial`/`athrow` of
  `IllegalArgumentException(String)`) at the start of the main
  constructor, before the first field write, with frames from
  `java.lang.classfile` stack-map generation, as the value output of
  plan step 4 does.
- [x] Inconsistency (plan review, pass 3): align the D10 commitment with
  the plan and the authority. plan.md "Approach" says the D9/D10 text "is
  final after three review passes". design.md ("Closed gate": "D9 and D10
  are provisional") and `decisions/closed-record-shape.md` (D9
  "Commitment: provisional (first caller: closed-record)", D10
  "Commitment: provisional (closed gate)") say provisional. The decision
  header says to change the commitment when evidence requires it. The
  precedent is D3 "firm (D7 gate passed)" and D8 "firm (D7 non-nil gate
  passed)". plan.md and steps.md Slice 1 do not change the D10 commitment
  after the gate passes, and the handoff note ("Do not re-edit decision
  files unless the closed gate fails") excludes the change. Replace
  "final" in plan.md with "D9 and D10 stay provisional until the closed
  gate". Add to plan step 1 and to Slice 1: when the gate passes, set
  D10 to "Commitment: firm (D7 closed gate passed)". D9 stays
  provisional because its reason is the first caller, not the gate.
- [x] Inconsistency (plan review, pass 4): align the embodiment updates in
  plan.md (steps 1 and 7) and steps.md (Slices 1 and 7) with design.md
  scope and the Ramora rule: `embodiment.md` describes the current code.
  - Plan step 7 and Slice 7 name only "Receiving macros", "Identity
    delegation rule", "Declaration checks (D6)" and "Differences from the
    design". The task code also makes other text false:
    - The intro: "D9 and D10 are not implemented (task 022); see
      differences", "With enabled fields, it expands to the receiving
      macros", and "`guarded-deftype*` for forms without enabled fields".
    - "Record value storage and checks (D8)": it gives only the
      hash-field removal and the `:record` companion transform. It does
      not give the closed field removal, the D10 check in the main
      constructor or the closed shape value.
    - "Evidence": the closed gate and the closed tests are not in it.
    Add these sections to the list. When all "Differences" entries go,
    remove the section and the intro link to it.
  - Gate links. Plan step 1 says to link "both" files (`closed-gate.md`
    and the evidence EDN) from the embodiment and the "Gate records"
    list. design.md and Slice 1 link only the gate record. The precedent
    (`non-nil-gate.md`, `feasibility-gate.md`) is that the gate record
    links its raw result, and the embodiment and the design list link
    only the gate record. Make plan step 1 agree. Also name the two
    embodiment places for the link: the intro "Gate records" line and
    "Evidence".
