# Design steps

- [x] Define "receiving compilation" in Scope and Acceptance 5. Ordinary value
  definitions do not support separate expansion and evaluation today, and D6
  gives `defvaluetype*` no non-nil specifications. State whether
  `(eval (macroexpand form))` and other transferred value expansions are
  supported or must fail with `:not-installed` or `:not-value-class`. State
  which receiving cases the evidence must cover (for example, the guarded
  fallback expansion only).
- [x] State the scope of the removed-field rule. Say whether the rejection of
  owner and companion access to `__hash` and `__hasheq` applies only to value
  output, or also to identity fallback, where the fields remain and no
  transform runs. Define "access" (`getfield` and `putfield` on the target
  class, other than the main-constructor writes that the transform removes).
  Give the `:field` value when both fields are accessed.
- [x] Define "any `:non-nil` field metadata". `defvaluetype` accepts `false`
  and `nil` as disabled, rejects other values with `:invalid-non-nil`, and
  rejects `:nil-restricted` by presence. State whether `defvaluetype*` rejects
  the key by presence (including `false` and `nil`) and which reason it gives
  for `:nil-restricted`.
- [x] Make the record-shape predicate exact. State the result for: hidden
  names that are not trailing (for example `[__meta x]`); a trailing subset;
  tags on `__meta` and `__extmap`; the accepted forms of the `int` tag
  (`^int`, `{:tag int}`, `{:tag 'int}`) and other primitive tags. State the
  error precedence when one form has several faults (mutable hash field,
  record-shape fault, `:non-nil` metadata).
- [x] Define "supported methods" in Acceptance 1 and the record-shaped test
  forms. Map and code-constant behaviour needs record interfaces (for example
  `IRecord`, which the compiler checks to emit a constant through `create`).
  State which interfaces and methods the test forms implement, and that their
  hash methods do not read the hash fields.
- [x] Align the identity hash-field statement. design.md says compliant
  callers leave the identity hash fields zero and that this is a caller
  precondition, not validated. `ramora/deftype-star-design.md` (Public
  contract) says the identity fields "always hold 0". An explicit call to the
  main constructor with non-zero hash arguments contradicts "always". Choose one
  statement and use it in design.md, the ramora contract and the usage
  documentation (Acceptance 7).
- [x] Fix the architecture guide reference in Purpose. The Markdown link target
  `~/projects/hugoduncan/architecture/ramora/INDEX.md` does not resolve as a
  link. All ramora designs give this path as code text. Use the same form.
- [x] State which elements the field metadata check examines. Field metadata
  says "every field". Record shape leaves elements that are not symbols, and
  a `fields` value that is not a vector, to `deftype*`. `defvaluetype`
  (`fields/enabled-fields`) checks only symbols in a vector. For example,
  `(^:non-nil a)` as `fields`, or a list element with `:non-nil` metadata,
  can give `:non-nil-unsupported` or the `deftype*` error. State one result
  in design.md and in the D6 text of `ramora/deftype-star-design/decisions.md`.
- [x] Fix the decision reference in Purpose. design.md says D1–D7 are
  "recorded in `99da8ad`". Commit `f0d53ae` changed D2, D4, D6 and the error
  table, so `99da8ad` holds an old contract (for example, no `int` tag rule,
  no removed-field rule, and a different `:non-nil` rule). Refer to the current
  files without the commit, or name the commit that holds the current text.
- [x] Align the record-shape statement in `ramora/deftype-star-design.md`
  (Context and scope). It says the compiler gives the record shape "when its
  last four fields are" the hidden names. D2 (Rationale) and design.md (Record
  shape) say the compiler counts any trailing hidden names and emits the
  record constructors for a trailing subset. Make Context state the compiler
  rule of D2.
- [x] Plan review (ambiguity): state where the owner removed-field check
  runs. plan.md (Removed-field check) and steps.md Slice 4 put the check in
  `capture-loader` for owner and companion classes, but do not say if the
  owner check reads the bytes before or after the owner transform. After the
  transform, the main-constructor writes are already gone, and the transform
  gives `:unexpected-constructor` first. Before the transform, the check must
  find the main constructor itself, and the access error comes first. State
  the choice and the error order for an owner with both faults, and add a
  Slice 4 test for that order.
- [x] Plan review (inconsistency): update the task 019 entry in
  `munera/plan.md`. It says "Design only; planning and the constructor
  feasibility gate come next", but the task now has plan.md and steps.md.
  State that the plan exists and that the next work is Slice 1, the D3/D7
  feasibility gate.
- [x] Plan review 2 (ambiguity): correct the AOT statement of the
  removed-field check. plan.md (Check point) says "AOT never writes rejected
  bytes", and steps.md Slice 4 (error-order test) says "no class file is
  written under `*compile-files*`". But the compiler writes the original
  bytes before it defines the class (defvaluetype D7,
  `ramora/defvaluetype-design/decisions/compiler-and-transform.md`). So
  after `:unexpected-constructor` or `:removed-field-access` in AOT, the
  untransformed compiler file stays, for owner and companion classes. Only
  the capture loader does not write. State the guarantee as "the capture
  loader writes no transformed bytes for a rejected class", and say what the
  test asserts: with crafted bytes given to `defineClass`, no file exists;
  a test through the compiler must not assert that the file is absent.
- [x] Plan review 2 (inconsistency): put the removed-field scan outside
  baseline loading. plan.md (Removed-field check) and steps.md Slice 4 add
  the check to `value-type.impl.loader/capture-loader`. design.md (Problem
  and context) says optional JDK APIs must stay outside baseline namespace
  loading. `value-type.impl.loader` also loads on Java 8: identity
  definitions with non-nil fields install it through the ASM identity
  backend. A scan that uses `java.lang.classfile` in that namespace breaks
  Java 8. State which namespace holds the scan (for example
  `value-type.impl.value-backend`, which the loader resolves only for value
  output, with the owner check composed after `->value-class` in `:owner`
  and the companion check in `:companion`). Say whether `loader.clj` still
  changes. Add a Slice 4 or Slice 5 check that baseline loading on Java 8
  stays the same.
- [x] Plan review 3 (ambiguity): state how the fallback expansion gives the
  Class. plan.md (Expansion mirrors `defvaluetype`) and steps.md Slice 2 say
  only "a guarded `deftype*` like `guarded-deftype`". `guarded-deftype`
  returns the value of `deftype`, which ends with the class symbol. The
  `deftype*` special form evaluates to nil (`NewInstanceExpr.eval` returns
  null when `isDeftype()`). Thus a guarded `deftype*` that only copies
  `guarded-deftype` evaluates to nil, and Acceptance 1 fails for permitted
  fallback. State that the fallback expansion ends with the class symbol, as
  the value expansion does, and say which part adds it (the new runtime
  macro or the `value-type.core` expansion). Name the `deftype*` argument
  that gives the class (`classname`, the second argument, not `tagname`) for
  `<class>`, `:class-name` and `install!`. Add a Slice 2 test that the
  fallback form returns the identity Class.
- [x] Plan review 3 (inconsistency): correct the return statement in
  `ramora/deftype-star-design.md` (Public contract). It says "The expansion
  evaluates to the class, as `deftype*` does." design.md (Scope) says only
  "It evaluates to the defined Class". The `deftype*` special form
  evaluates to nil (`NewInstanceExpr.eval`), so "as `deftype*` does" is
  false, and it hides the fallback return gap of the ambiguity step above.
  Remove "as `deftype*` does" (for example, "unlike `deftype*`, which
  evaluates to nil"), so that ramora agrees with design.md. Resolve this
  step with the fallback-return step, in one commit.
- [x] Plan review 4 (ambiguity): state how the transform receives the
  shape. plan.md (Removed-field check) and steps.md Slice 3 carry the shape
  through `install!`, `capture-loader` and `value-backend/prepare` "to the
  transform", and list the direct `test/` callers of `install!` and
  `capture-loader` that change. They do not say how
  `value-type.impl.transform` takes the shape: a new required argument of
  `->value-class [bs storage class-loader]`, a new arity, or a separate
  record function that `prepare` selects. A new required argument changes
  the direct callers in `test/runtime/value_type/impl/transform_test.clj`
  and `method_attributes_test.clj`, which the plan does not list. State the
  interface (and whether `prepare` takes the shape as a new last argument),
  and list the callers that change.
- [x] Plan review 5 (ambiguity): state if the record path checks the body
  of the main constructor. plan.md (Transform via a new record path) and
  steps.md Slice 3 find the main constructor by descriptor and check only
  the two delegating constructors. The plain path
  (`constructor-difference`) also checks the constructor body with
  `expected-code` (`super call`, `field writes`), and design.md
  (Architecture) gives the transform "exact constructor-shape validation".
  The rewrite replaces the main-constructor body, so with no check, other
  code in that body is lost with no error. State the choice (for example,
  reuse `expected-code` over all fields, hash fields included), the
  `:unexpected-constructor` result for a different body, and add a Slice 3
  rejection test for it.
