# Steps

## Slice 1: Feasibility gate (D3/D7 spike)

- [x] Create `spike/record-value-type/` with a README (commands, JVM build
  `27-jep401ea3+1-1`, preview flags), modeled on
  `spike/value-array-semantics/`.
- [x] In the spike, transform a record-shaped `deftype*` class: rewrite the
  main constructor for value output, remove `__hash`/`__hasheq`, keep the
  two delegating constructors and `create` unchanged.
- [x] In the spike, with verification enabled on EA3 preview, exercise: the
  main constructor, both delegating constructors, `create`, the map record
  literal `#ns.Name{…}`, the positional literal `#ns.Name[…]`, and a
  compiled record constant (via a form that embeds the record as a constant).
- [x] Record the gate result and commands in a ramora evidence report and in
  `implementation.md`. If the gate fails, record the evidence and stop for a
  design decision.
  - Gate passed; see implementation.md (Slice 1).

## Slice 2: Declaration validation and macro skeleton

- [x] Add a shared helper that selects symbols in a `fields` vector (and
  passes other shapes through unchecked), used by all three declaration
  checks.
- [x] Implement the record-shape predicate per D2: hidden names
  `__meta __extmap __hash __hasheq`; plain shape = no hidden name; record
  shape = n ≥ 4, last four hidden, each hidden-name field in place
  (unqualified symbol at index n − 4 + k, required tag: none for
  `__meta`/`__extmap`, symbol `int` for `__hash`/`__hasheq`); all other
  forms give `:invalid-field` `:reason :record-shape` with the design's
  `:field` selection and `:class-name`.
- [x] Implement the field metadata check per D6: `:nil-restricted` by
  presence gives `:removed-nil-restricted` (`:field`, `:value`); else
  `:non-nil` by presence gives `:non-nil-unsupported` (`:field`); first
  faulty field in field order; checks per field with `:nil-restricted`
  first.
- [x] Implement `value-type.core/defvaluetype*` taking `deftype*` arguments
  `tagname classname fields & opts+specs`: run `:mutable-field` (reusing
  `check-fields`, including hash fields), then record shape, then field
  metadata, at macroexpansion before policy. Take the class from
  `classname` (second argument, not `tagname`): `(str classname)` for
  `:class-name` and `install!`, `(symbol (str classname))` for `<class>`.
- [x] Wire capability check and `value-type.fallback` policy as in
  `defvaluetype`; value path installs the capture loader at expansion and
  expands to `(let [] (deftype* …) (check-value-class <class> installed)
  <class>)`.
- [x] Add `guarded-deftype*` to `value-type.impl.runtime` beside
  `guarded-deftype`: it takes the `deftype*` arguments unchanged (so field
  metadata stays), calls `enforce-fixed-fallback!` at expansion, and emits
  `(do (enforce-fixed-fallback!) (deftype* …) <class>)`. The runtime macro
  adds `<class>` because `deftype*` evaluates to nil. Emit
  `(do (runtime/guarded-deftype* …))` as the `value-type.core` fallback
  expansion, with nothing after it.
- [x] Fallback return tests: in the runtime suite, evaluating a
  `runtime/guarded-deftype*` form (plain and record shape) returns a Class
  equal to the class named by `classname`, not nil, and the class is not a
  value class. In the compatibility suite (beside `public-definition-test`
  in `test/compatibility/value_type/runtime_test.clj`), when fallback is
  selected, `defvaluetype*` with policies `silent` and `warn` returns the
  identity Class.
- [x] Unit tests: plain and record shapes accepted; each rejection case of
  the design's Record shape and Field metadata sections (e.g. `[__meta x]`,
  `[x __hash __hasheq]`, tag on `__meta`, `^long __hash`, `^{:tag 'int}`
  source form, `Integer`/`"int"` tags, mutable hash field, `:non-nil`
  `false`/`nil`, `:nil-restricted`); validation order on a multi-fault form;
  non-symbol elements and non-vector `fields` go to `deftype*`.
  - Done; see implementation.md (Slice 2). Record-shape value output fails
    with `:unexpected-constructor` until Slice 3 carries the shape.

## Slice 3: Value transform record path

- [x] In `value-type.impl.transform`, add the public function
  `->record-value-class [bs class-loader]` (no `storage`) beside
  `->value-class`, with its own class transform; `->value-class`,
  `constructor-difference` and `value-class-transform` do not change.
  Identify the main constructor by descriptor (all fields, record forms:
  `(declared…, Object, Object, int, int)`).
- [x] Validate the delegating constructors: each body only loads `this` and
  its arguments, pushes the expected constants (`null, null, 0, 0` /
  `meta, extmap, 0, 0`), and calls the main constructor with
  `invokespecial`; any other constructor set gives
  `:unexpected-constructor` with `:class-name`.
- [x] Validate the main-constructor body before the rewrite: reuse the
  private `expected-code` over all instance fields of the input bytes
  (hash fields included) and `first-difference` on the body without
  debug elements (`debug-element?`). A difference gives
  `:unexpected-constructor` with `:class-name` and the message part
  `super call` (position 1 or 2) or `field writes`, as in
  `constructor-difference`.
- [x] Rewrite only the main constructor; remove the `__hash`/`__hasheq`
  field declarations and their writes; keep the constructor descriptor and
  all argument slots (compute field slots from the full parameter list);
  leave `create`, `getBasis`, delegating constructors and metadata
  unchanged; kept instance fields get only `ACC_STRICT_INIT`, as in
  `->value-class`.
- [x] Carry the shape (`:plain` or `:record`) from `defvaluetype*` through
  `value-type.impl.loader` (`install!` and `capture-loader`, new last
  argument, passed to `value-backend/prepare` only) and
  `value-type.impl.value-backend` to the transform. `value-backend/prepare`
  takes the shape as a new last argument (`[target specifications delegate
  shape]`): `:plain` keeps the current transforms, `:record` selects
  `->record-value-class` in `:owner`. `defvaluetype` passes `:plain`;
  `identity-backend/prepare` does not change; `loader.clj` gets no new
  import and its `defineClass` branches do not change; update the
  `install!` docstring to name `->record-value-class` for the record
  shape. Add the shape argument to the direct callers in `test/`
  (`grep -rln "install!\|capture-loader" test`). The `->value-class`
  calls in `transform_test.clj` and `method_attributes_test.clj` do not
  change.
- [x] Unit tests on emitted bytes, calling `->record-value-class`
  directly (in `test/runtime/value_type/impl/transform_test.clj`): value
  output has no hash fields;
  descriptors unchanged; delegation valid; kept fields preserved; cases for
  zero declared fields, reference fields and mixed primitives including
  long/double slots; unsupported constructor sets rejected with
  `:unexpected-constructor`.
- [x] Rejection test for the main-constructor body (in
  `transform_test.clj`): crafted record-shaped bytes (`build-class`, valid
  delegating constructors) whose main constructor has other code, for
  example one field write missing or two writes swapped, give
  `:unexpected-constructor` from `->record-value-class` with `:class-name`
  and the part `field writes`; a write before the super call gives the
  part `super call`. Assert as `check-error` does (key, `:class-name`,
  part and "Clojure 1.12" in the message); `check-error` calls
  `->value-class` through `thrown-data`, so the record test needs a
  variant that calls `->record-value-class`.
  - Done; see implementation.md (Slice 3).

## Slice 4: Removed-field access check

- [x] Add the removed-field scan to `value-type.impl.value-backend` (not
  `value-type.impl.loader`). For the record shape, `prepare` composes the
  owner check after `->record-value-class` in `:owner` and the companion
  check in `:companion` (companion bytes returned unchanged). The owner
  check thus runs inside `(owner bs)`, before `writeClassFile`; the
  companion check runs before the companion define.
- [x] Reject a `getfield`/`putfield` naming the target class and `__hash`
  or `__hasheq` with `:invalid-field` `:reason :removed-field-access`,
  `:field` and `:class-name` (the target class); `:field` is `__hash` when
  both are accessed; the main-constructor writes are already gone from the
  transformed owner bytes, so every remaining instruction counts; do not
  check reflective access.
- [x] Unit tests: owner access rejected; companion access rejected; both
  fields accessed gives `:field __hash`; identity fallback does not check
  (no loader installed); existing `defvaluetype` loader behaviour unchanged.
- [x] Unit test for error order: an owner with an unsupported constructor
  set and a removed-field access (for example crafted record-shaped bytes
  with one extra constructor and a `hasheq` that reads `__hash`, given to
  the capture loader's `defineClass` with `*compile-files*` true and
  `*compile-path*` bound to a temporary directory) gives
  `:unexpected-constructor`, not `:removed-field-access`, and no class file
  exists for the class: the capture loader writes no transformed bytes for
  a rejected class.
- [x] AOT test through the compiler: a record-shaped `defvaluetype*`
  whose `hasheq` reads `__hash`, compiled with `*compile-files*` true,
  gives `:removed-field-access`, and its class file holds the
  untransformed bytes (both hash fields present). Do not assert that the
  file is absent: the compiler writes the original bytes before the
  capture loader defines the class (defvaluetype D7), so the untransformed
  owner or companion file stays after a rejection.
- [x] Java 8 baseline loading check: in the compatibility suite (for
  example beside `public-require-test` in
  `test/compatibility/value_type/runtime_test.clj`), a fresh child that
  requires `value-type.impl.loader` loads none of
  `value-type.impl.value-backend`, `value-type.impl.transform` and
  `value-type.impl.field-access`. Run `clojure -M:fallback-test` with the
  Java 8 parent; existing `identity_loader_test.clj` and
  `capture_failure_test.clj` (they require the loader) still pass there.
  - Done; see implementation.md (Slice 4).

## Slice 5: Integration, fallback, receiving and AOT evidence

- [x] Add record-shaped test forms implementing `IRecord`, `java.util.Map`,
  `ILookup`, `IObj`, `IHashEq` with `valAt` (both arities), `entrySet`,
  `meta`, `withMeta` (via the meta-and-extmap constructor), `hasheq` and
  `hashCode` computed from entries (never reading hash fields); plain test
  forms implement `IType`.
- [x] Integration tests, both representations: all three constructors,
  `create`, `getBasis`, map and positional record literals, compiled record
  constants and metadata; field variants per Acceptance 2 (zero fields,
  reference fields, mixed primitives with long/double).
- [x] Inspection tests per Acceptance 3: value output has no hash fields
  and valid delegation; identity output retains immutable hash fields;
  construction preserves kept fields.
  - Done in `test/runtime/value_type/deftype_star_record_test.clj`; see
    implementation.md (Slice 5a).
- [x] Policy tests: `silent`, `warn` and `error` give existing behaviour
  and warning scope for `defvaluetype*` (mirror
  `field_policy_checks`/`warning_checks` patterns).
  - `runtime_test/deftype*-policy-test`; see implementation.md (Slice 5b).
- [x] Receiving tests: fallback expansions from `macroexpand` and
  `macroexpand-1` enforce receiving policy in modes `:eval`, `:analysis`
  and `:source` (mirror `transfer_checks.clj`); one transferred value
  expansion fails with `:not-installed` (no `LOADER` binding at expansion)
  and one with `:not-value-class` (e.g. `(eval (macroexpand form))`); value
  expansions never give an identity class and never select fallback.
  - Fallback: `deftype_star_transfer_test.clj`; value:
    `deftype_star_test/value-expansion-receiving-test`; see
    implementation.md (Slice 5b).
- [x] AOT tests: representation fixed at compile; source-free consumers
  retain identity policy; incompatible receivers reject value artifacts
  without conversion (mirror `aot_checks`/`consumer_aot_checks`).
  - `test/cross-jvm/value_type/deftype_star_aot_test.clj`; see
    implementation.md (Slice 5c).
- [x] Run the cross-JVM matrix: Java 8, EA3 preview off, EA3 preview on,
  both shapes, including fresh-process policy and AOT cases; record
  commands, structured results and limits in `implementation.md`.
  - Slice 5c record.
- [x] Run existing regression, packaging, tooling and quality checks and
  record the results in `implementation.md`.
  - Slice 5c record; two expected failures moved to Slice 6.

## Slice 6: Documentation and ramora embodiment

- [x] Write usage documentation for `defvaluetype*`: syntax, caller
  obligations (int-tagged immutable hash fields, no hash-field reads or
  writes, zero hash arguments, work with both outputs), fallback behaviour,
  exclusions and evidence limits, using the design's identity hash-field
  statement.
- [x] Add the docstring and regenerate API documentation; verify the same
  statements appear there.
- [x] Add `value-type.core/defvaluetype*` to the documented public list in
  `test/tooling/value_type/api_docs_test.clj` (`public-reference-test`);
  `bb api-docs --check` and the tooling suite must pass.
- [x] Update `ramora/deftype-star-design.md` Embodiment (and Observed
  qualities) to describe delivered behaviour; keep design.md and ramora
  equal if any contract changed during implementation.
- [x] Update `mementum/state.md` with the new public API; verify
  `implementation.md` holds all verification records; final quality check
  pass.
  - Done; see implementation.md (Slice 6).

## Implementation review 1

- [x] Make `field-symbols` the shared selector of all three declaration
  checks, as Slice 2 and plan.md state ("used by all three declaration
  checks"). Now only `check-field-metadata` calls it; `check-fields` /
  `mutable-fields` and `record-shape` / `hidden-index` repeat the
  `vector?` and `symbol?` selection in `src/value_type/core.clj`.
- [x] Fix the `defvaluetype*` docstring (and regenerate `doc/API.md`): it
  says "Run time and loader errors are as for `defvaluetype`", but
  `:invalid-field` `:reason :removed-field-access` (data `:field`) is new to
  this API, and `:unexpected-constructor` for value output now also covers
  the record constructor set. List both, as the error table of
  `ramora/deftype-star-design.md` does. Add the key phrase to
  `api_docs_test/public-reference-test`.
- [x] Keep one definition of the removed hash field names:
  `transform/hash-field-names` and `value-backend/removed-fields` hold the
  same names; `value-backend` already requires `transform`. Keep the
  report order (`__hash` first) where the scan needs it.
- [x] In `transform/->record-value-class`, reuse the main-constructor
  descriptor that `record-constructor-difference` finds, instead of
  building it again from the field types with `str`.

## Implementation review 2

- [x] Make `:field` of `:invalid-field` `:reason :removed-field-access` the
  symbol, not the string: design.md (Removed fields) says "`:field` is the
  symbol `__hash`", and the other `defvaluetype*` errors give `:field` as a
  symbol. Now `value-backend/check-removed-fields` puts the string from
  `transform/hash-field-names` in the data. Change the data (message text
  can keep the name) and the expectations in
  `test/runtime/value_type/impl/removed_field_test.clj` (`removed-access`,
  line 108 `:field "__hash"`).
- [x] In `src/value_type/impl/transform.clj`, keep one form of the shared
  constructor-check steps: `constructor-difference` inlines the parameter
  descriptor list and the body-without-debug-elements comparison that the
  new `parameter-descriptors` and `code-difference` hold; make it call
  them. Use `field-name` at the other `(.stringValue (.fieldName f))`
  sites, or remove it. Remove the `main-params` alias of `types` in
  `record-constructor-difference`.

## Implementation review 3

- [x] In `src/value_type/impl/transform.clj`, share the value-class parts
  of `value-class-transform` and `record-value-class-transform`: both
  repeat the `ClassFileVersion` and `AccessFlags` (`ACC_IDENTITY`)
  branches, and the record path writes an inline `MethodTransform` that
  repeats `constructor-transform` with another code `Consumer`. Use one
  helper for version and flags, and let `constructor-transform` take the
  code `Consumer` (or add a helper that does), so that both paths use it.

## Implementation review 4

- [x] In `src/value_type/impl/transform.clj`, use one constructor-code
  emitter for both paths: `record-constructor-code` repeats the
  `constructor-code` loop (`aload 0`, `loadLocal` from `field-slots`,
  `putfield`, then `Object.<init>` and `return`) with no storage. Let
  `constructor-code` take the kept-field selection (slots stay computed
  over all fields), and use it from `record-value-class-transform`. In
  `delegating-code`, take the slot after the declared fields from the same
  slot computation as `field-slots`, not from a second `reduce`.
- [x] Put the shared test helpers of the new tests in
  `test/runtime/value_type/test_support.clj`: `record-tail` has the same
  definition in `deftype_star_test.clj`, `deftype_star_record_test.clj`,
  `impl/removed_field_test.clj` and `impl/transform_test.clj`; the
  cause-chain `ex-data` lookup (`(some … (iterate ex-cause t))`) is
  repeated in `deftype_star_test.clj` (`expansion-error`, `error-data`)
  and `impl/removed_field_test.clj` (`error-data`).

## Test review 1

- [x] Remove tests that repeat Slice 5 coverage. In
  `test/runtime/value_type/deftype_star_test.clj`,
  `record-value-definition-test` (value class, no hash fields, three
  constructors, kept fields) and `plain-value-definition-test` repeat
  `deftype_star_record_test.clj` (`record-entry-points-test`,
  `plain-entry-points-test`); `guarded-deftype*-test` repeats the identity
  representation there (Class, not a value class, hash fields kept). In
  `test/compatibility/value_type/runtime_test.clj`,
  `public-deftype*-definition-test` repeats the plain cell of
  `deftype*-policy-test`; move any fact that only it checks (`:stored`,
  `:new-class?`) into that cell, then remove it.
- [x] Make the accept assertions exact in `deftype_star_test.clj`:
  `accepted-shapes-test` and `unchecked-shapes-test` assert
  `(not (map? …))` or `(not= :invalid-field …)`, which also pass for other
  errors. Assert `:no-error` from `expansion-error`. For
  `unchecked-shapes-test`, also show that `deftype*` rejects the form
  (evaluation fails with no `value-type` error data), as design.md
  (Record shape, Field metadata) states.
- [x] Keep one throw-capture helper in
  `test/runtime/value_type/test_support.clj`: `expansion-error` and
  `error-data` in `deftype_star_test.clj` and `error-data` in
  `impl/removed_field_test.clj` repeat the same
  `try`/`ex-info-data`/rethrow form around `support/ex-info-data`.
- [x] Cover the other record constructor sets in
  `test/runtime/value_type/impl/transform_test.clj`: no test gives three
  constructors with a wrong descriptor (for example the `(J)V` delegating
  constructor replaced by `(I)V`), and only the `(J)V` delegating
  constructor gets a body with other code; add one case for each that
  asserts `:unexpected-constructor` with `:class-name`.

## Test review 2

- [x] Cover the access rule of design.md (Removed fields) beyond owner and
  companion `getfield`, in
  `test/runtime/value_type/impl/removed_field_test.clj`: no test gives a
  `putfield` of `__hash` or `__hasheq`, and no test shows that a
  `getfield` of a field with a hidden name on another class is accepted.
  The compiler path cannot emit these, so build companion bytes with
  `java.lang.classfile` and define them through `#'loader/capture-loader`
  (`:value :record`), as `error-order-test` does. Assert
  `:removed-field-access` with `:field` for the `putfield` case, and no
  error for the other-owner case.
  - `removed_field_test/companion-bytes-test`; see implementation.md.

## Test review 3

- [x] Show that value output ignores the hash arguments (design.md, Scope:
  "Value output removes both fields and ignores those arguments"). In
  `test/runtime/value_type/impl/transform_test.clj`,
  `record-round-trip-test` ("main constructor ignores the hash
  arguments") passes `(int 0) (int 0)`, which cannot show this. Pass
  non-zero values (for example `(int 7) (int 8)`) and assert the same kept
  fields.
  - Done; `record-round-trip-test` passes `(int 7) (int 8)`.

## Test review 4

- [x] Cover the accepted nil tag of design.md (Record shape: "`__meta` and
  `__extmap` have no tag (`:tag` absent or nil)"). In
  `test/runtime/value_type/deftype_star_test.clj`, `accepted-shapes-test`
  has no field with `{:tag nil}`. Add a record-shape case with
  `(with-meta '__meta {:tag nil})` and `(with-meta '__extmap {:tag nil})`
  that asserts `:no-error`.
  - Done; `accepted-shapes-test` "nil tags on __meta and __extmap".
- [x] Keep one error-check helper in
  `test/runtime/value_type/impl/transform_test.clj`: `record-thrown-data`
  and `check-record-error` repeat `thrown-data` and `check-error` with
  only the transform call changed. Let `thrown-data` (and `check-error`)
  take the transform function, or a bytes-to-result function, and use it
  for both paths.
  - Done; `thrown-data` takes `f`; `check-error` takes an optional
    `failure` (`value-class-failure` default, `record-failure`).

## Test review 5

- [x] In `test/compatibility/value_type/runtime_test.clj`, the cells
  "declaration error" (`deftype*-policy-test`) and
  `deftype*-removed-field-check-test` repeat a `try`/`catch` and an inline
  cause-chain `(some #(:reason (ex-data %)) …)` lookup. Use
  `value-type.cell-registry/outcome` with `registry/root-data`, as
  `expansion_checks.clj` does, and assert the error data
  (`:value-type/error`, `:class-name`, `:reason`, and `:field` for
  `:removed-field-access`), not only `:reason`.
- [x] In `test/runtime/value_type/impl/removed_field_test.clj`,
  `aot-removed-field-test` builds the record form inline although
  `record-form` builds the same form for `test-ns`; let `record-form` take
  the namespace (or the class name) and use it. Keep one helper for the
  class-file path (`(File. dir (str (.replace … \. \/) ".class"))`), which
  `aot-removed-field-test` and `record-bytes` repeat.

## Test review 6

- [x] In `test/compatibility/value_type/runtime_test.clj`,
  `deftype*-policy-test`, the "record" cell declares `a`, not `x`, so
  `:stored` is always nil and the cell does not construct the record. Use
  `x` in the record fields and expect `:stored 42` (the declared-fields
  constructor), then remove the "nil when x is not a field" branch of
  `deftype*-form`.
- [x] Keep one hash-field count in `test/compatibility/value_type/`:
  `deftype_star_aot_checks.clj` (`facts`), `deftype_star_transfer_checks.clj`
  (`hash-fields`) and the child form of `runtime_test/deftype*-form` repeat
  `(count (filter #(#{"__hash" "__hasheq"} …) (.getDeclaredFields c)))`.
  Make one public, baseline-safe helper and use it from all three (the child
  form can require its namespace).
  - `cell-registry/hash-field-count`; see implementation.md.

## Docs review 1

- [x] Add a `CHANGELOG.md` entry under `[Unreleased]` for
  `value-type.core/defvaluetype*`: plain and record `deftype*` shapes, value
  output removes `__hash`/`__hasheq`, identity fallback under
  `value-type.fallback`, caller obligations, exclusions (`:non-nil`) and the
  tested JVMs. Link to `doc/usage.md#deftype-forms`. The change is
  user-visible and `CHANGELOG.md` has no entry.
- [x] Fix the validation order in `doc/usage.md` ("`deftype*` forms"). The
  text gives `:record-shape`, then `:mutable-field`, then the metadata
  errors, and says "in that order". `defvaluetype*` checks `:mutable-field`
  first, then `:record-shape`, then field metadata (design.md Validation
  order; `doc/API.md`; ramora Public contract). State the errors in that
  order.
- [x] Mention `defvaluetype*` in `README.md`: one short statement for
  libraries that emit `deftype*` (for example closed-record), with a link to
  `doc/usage.md#deftype-forms`. README now names only `defvaluetype` and
  `value-type.array`.
- [x] Remove the stale claim in `ramora/deftype-star-design.md` (Context and
  scope): "The current value transform requires exactly one constructor …
  Neither accepts the record shape now." The value transform now has a
  record path (`->record-value-class`; Embodiment says "Implemented"). State
  it as the `defvaluetype` path (`->value-class`) and the identity backend,
  not as the present state of value-type.

## Docs review 2

- [x] Add a "Verify `deftype*` forms" section to
  `doc/development/runtime.md`, as for the other features ("Verify portable
  non-nil fields", "Verify array allocation"). The README, `CHANGELOG.md` and
  `doc/usage.md` claim Java 8 and EA3 preview off/on evidence for
  `defvaluetype*`, but the guide names none of its checks. Give the focused
  commands: runtime (EA3 preview) `value-type.deftype-star-test`,
  `value-type.deftype-star-record-test`, `value-type.impl.removed-field-test`;
  `:fallback-test` `value-type.runtime-test` on the three JVMs;
  `:cross-jvm-test` `value-type.deftype-star-transfer-test` and
  `value-type.deftype-star-aot-test`. State the limits (`-Xint` only for value
  artifact consumers; reflective access not checked) and link the gate
  command in `spike/record-value-type/README.md`.
  - Done; the transfer test is in `test/compatibility/`, so the guide runs
    it with `:fallback-test`, not `:cross-jvm-test`.

## Code-shaper review 1

- [x] In `src/value_type/impl/transform.clj`, make
  `record-constructor-difference` total over its input: move the
  "fewer than four instance fields gives `parameters`" check from
  `->record-value-class` into it. Now it calls `subvec` with
  `(- (count fields) 4)` and fails with `IndexOutOfBoundsException` when
  the caller does not check first. Then `->record-value-class` reads as
  `->value-class` does: one difference call, then
  `unexpected-constructor!` when there is a part.
  - Done; transform_test `record-constructor-set-error-test` "fewer than
    four instance fields".

## Code-shaper review 2

- [x] In `src/value_type/impl/value_backend.clj`, `prepare` builds
  `storage` from `specifications` before the `case` on `shape`, but only
  the `:plain` branch uses it; the docstring says `:record` ignores the
  specifications. Move the `storage` binding into the `:plain` branch, so
  that each branch reads only the data it uses.
  - Done; `storage` is bound in the `:plain` branch only.

## Code-shaper review 3

- [x] In `src/value_type/impl/transform.clj`, use one predicate for an
  `invokespecial` of `<init>`: `invoke-main?` repeats `object-init?`
  (opcode, owner, name `<init>`, descriptor) with other values, and only
  `object-init?` checks `(not (.isInterface i))`. Add one predicate that
  takes owner and descriptor (with the interface check), and define
  `object-init?` and the main-constructor call check with it.
- [x] In the `ns` form of `src/value_type/impl/transform.clj`, put
  `Instruction` in the existing `(java.lang.classfile …)` import list; the
  separate `(java.lang.classfile Instruction)` clause repeats the package.
  - Done.

## Code-shaper review 4

- [x] In `src/value_type/impl/transform.clj`, keep one main-constructor
  body check: `constructor-difference` and `record-constructor-difference`
  both hold `(when-let [pos (code-difference m (expected-code owner
  fields))] (if (<= pos 2) "super call" "field writes"))` and both build
  the field descriptor list
  `(mapv #(.stringValue (.fieldType ^FieldModel %)) fields)`. Add one
  helper for each (for example `main-constructor-part [m owner fields]`
  and `field-descriptors [fields]`) and use them on both paths, so that
  the part names stay equal.
- [x] In `src/value_type/impl/transform.clj`, `->value-class` and
  `->record-value-class` repeat the same prelude: parse with
  `class-file/configured`, select instance fields, then
  `unexpected-constructor!` with the binary name
  `(.replace (.asInternalName entry) \/ \.)`. Let `unexpected-constructor!`
  take the `ClassModel` (or add a `binary-name` helper), so that the
  `:class-name` value has one definition.
  - Done; `main-constructor-part`, `field-descriptors`;
    `unexpected-constructor!` takes the `ClassModel`.

## Code-shaper review 5

- [x] Keep one definition of the `:removed-nil-restricted` error data:
  `value-type.core/check-field-metadata` repeats the `ex-info` data of
  `value-type.impl.fields/non-nil?` (`:value-type/error :invalid-field`,
  `:class-name`, `:field`, `:reason :removed-nil-restricted`, `:value`);
  only the fix text is different ("use :non-nil instead" versus
  "defvaluetype* does not support it"). Add one baseline-safe helper in
  `value-type.impl.fields` that takes the class name, field and fix text,
  and call it from both sites, so that the data shape stays equal.
- [x] Keep one predicate for an instance-field access to an owner class:
  `value-backend/removed-field-accesses` repeats the test of
  `field-access/rewrite` (`FieldInstruction`, opcode in
  `#{GETFIELD PUTFIELD}`, `(= owner (.asSymbol (.owner i)))`). Make it a
  public function in `value-type.impl.field-access` and use it at both
  sites. In the same scan, use one hash-field membership test: it uses
  `(some #{field} transform/hash-field-names)` while `transform` holds the
  private set `hash-field?`; make `hash-field?` public and use it.
  - Done; `fields/removed-nil-restricted!`,
    `field-access/owner-field-access?`, public `transform/hash-field?`.

## Code-shaper review 6

- [x] In `src/value_type/core.clj`, hold the record-shape rule as data:
  `in-place?` encodes the required tags as `(if (< k 2) (nil? tag) (= 'int
  tag))`, an index rule apart from `hidden-names`. Keep each hidden name
  with its required tag in one table (for example `[["__meta" nil]
  ["__extmap" nil] ["__hash" 'int] ["__hasheq" 'int]]`), and let
  `hidden-index` and `in-place?` read it.
- [x] Make the new `defvaluetype*` error messages end with `Fix: …`, as
  its other errors do (`:mutable-field`, `:record-shape`,
  `:non-nil-unsupported`): `core/check-field-metadata` passes
  "defvaluetype* does not support it." to `fields/removed-nil-restricted!`
  (no fix), and `value-backend/check-removed-fields` ends with
  "compute hashes from entries." For example "defvaluetype* does not
  support it. Fix: remove the metadata." and "Fix: compute hashes from
  entries and do not read or write __hash or __hasheq."
  - Done; `core/hidden-fields` table; both messages end with `Fix: …`.

## Code-shaper review 7

- [x] In `src/value_type/core.clj`, keep one value expansion: the plain
  value branch of `defvaluetype` and the value branch of `defvaluetype*`
  both install capture with `install-capture!` and emit
  `(let [] <definition> (check-value-class <class> installed) <class>)`;
  only the definition form (`deftype` or `deftype*`) and the shape are
  different. Add one private helper (for example
  `value-expansion [class-name shape definition]`) and use it from both
  macros. Update the `check-value-class` docstring, which says only "The
  expansion of `defvaluetype` calls it".
- [x] In `src/value_type/impl/value_backend.clj`,
  `removed-field-accesses` parses with `(ClassFile/of)`; all other parse
  sites use `class-file/configured`. Use `(class-file/configured nil)`
  and remove the `ClassFile` import, so that one function makes the
  `ClassFile`.
  - Done; `core/value-expansion`; `value-backend` parses with
    `(class-file/configured nil)`.

## Code-shaper review 8

- [x] Keep one definition of the record hidden field names. The record
  shape rule (`core/hidden-fields`: `__meta __extmap __hash __hasheq` with
  tags) and the value-output removal (`transform/hash-field-names`, used by
  `transform/hash-field?` and `value-backend/check-removed-fields`) hold
  `"__hash"` and `"__hasheq"` separately, so the accepted shape and the
  removed fields can diverge. Put the table in the baseline-safe
  `value-type.impl.fields` (for example `fields/record-hidden-fields`), read
  it from `core`, and derive `transform/hash-field-names` from its entries
  with tag `int` (report order `__hash` first). `core` must still load on
  Java 8 with no `java.lang.classfile` namespace.
  - Done; `fields/record-hidden-fields`; `transform/hash-field-names`
    derived from it.

## Code-shaper review 9

- [x] In `src/value_type/core.clj`, simplify the accept test of
  `record-shape` to `(and (= 4 (count hidden)) (empty? misplaced))`. When
  no hidden field is misplaced, each is at its own index n − 4 + k, so four
  hidden fields fill the last four places and give n ≥ 4. The
  `(>= n 4)` test and the `(every? hidden-index (subvec fields (- n 4)))`
  scan of the raw `fields` add no case. The `:field` selection of the
  error does not change.
- [x] In `src/value_type/impl/transform.clj`,
  `record-constructor-difference` looks up `by-params` six times
  (`(by-params types)` three times, the short and meta groups two times
  each). Bind the three constructor groups (main, short, meta-and-extmap)
  one time and read the `parameters` check, the delegating-constructor
  checks and `main` from them.
- [x] Update the docstrings that name only `deftype` but now also serve
  `defvaluetype*`: the `value-type.impl.transform` ns docstring ("Change
  the bytes of a Clojure `deftype` class …") does not name the record
  path (`->record-value-class`), and `core/check-fields` says "`deftype`
  rejects it" for a non-vector `fields`, also for `deftype*`.
  - Done; `record-shape` accepts on four hidden, none misplaced;
    constructor groups bound once; docstrings name `deftype*`.
