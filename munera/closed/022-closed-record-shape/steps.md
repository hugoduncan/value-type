# Steps

## Slice 1 — Closed gate (D7)

- [x] Write `dev/spike/record-value-type/closed-gate.clj`: spike transform of
  a record with `[^long x __meta __extmap ^int __hash ^int __hasheq]` that
  removes all four hidden fields and adds the D10 check in the form of
  plan step 5 (`aload`, `ifnull`, `new`/`dup`/`ldc`/`invokespecial`/`athrow`
  of `IllegalArgumentException(String)`; `__meta`, then `__extmap`) at the
  start of the main constructor, before the first field write. Emit it in
  `CodeBuilder` instructions; frames come from `java.lang.classfile`
  stack-map generation, as in the value output of plan step 4.
- [x] Add the gate command to `dev/spike/record-value-type/README.md`;
  do not change `gate.clj` or `non-nil-gate.clj`.
- [x] Run the gate on EA3 with preview and `-Xverify:all`: verify value
  class with single field `x`; each constructor, `create`, both record
  literals and a compiled record constant work with valid input; non-nil
  `__meta`/`__extmap` and a map literal with an undeclared key give
  `IllegalArgumentException`; `ValueClass.isFlatArray` is true for a
  null-restricted atomic array.
- [x] Write `ramora/deftype-star-design/closed-gate.md` (method, results,
  limits, SHA-256 of `closed-gate.clj`, a link to the raw result, as in
  `non-nil-gate.md`) and
  `ramora/deftype-star-design/evidence/closed-gate-ea3-preview.edn`.
- [x] Link only the gate record (not the raw result) from
  `ramora/deftype-star-design/embodiment.md`, in the intro "Gate records"
  line and in "Evidence", and from the "Gate records" list in
  `ramora/deftype-star-design.md`. In `embodiment.md`, remove the
  "Differences from the design" entry "The closed gate (D7) has not run".
- [x] If the gate passes: set D10 in
  `ramora/deftype-star-design/decisions/closed-record-shape.md` to
  "Commitment: firm (D7 closed gate passed)". Do not change D9 (it stays
  provisional: its reason is the first caller, not the gate).
- [x] If the gate fails: record evidence in `implementation.md` and stop.
  Not applicable: the gate passed (all 25 cases).

## Slice 2 — Option in value-type.core

- [x] Parse `:value-type/closed` in `defvaluetype*` option handling; the
  last occurrence applies; remove each occurrence from the `deftype*` call
  for the values `true`, `false` and `nil`.
- [x] Add validation in the public-contract order (`:mutable-field`,
  `:record-shape`, `:invalid-option`, metadata keys, `:hidden-field`, tag,
  type): other values → `:invalid-option` `:reason :invalid-value`;
  `true` on a plain shape → `:reason :record-shape-required`; data
  `:option`, `:value`, `:class-name`.
- [x] Change `policy-expansion` so a form with enabled fields or the
  closed shape uses the receiving expansion.
- [x] Extend the shape value given to the capture loader
  (`loader/install!`) to distinguish plain, record and closed; pass it to
  both backends; update the `install!` docstring.

## Slice 3 — Receiving macros

- [x] `value-field-deftype*`: add the `:invalid-option` check between the
  `:record-shape` check and `require-value-capability!`; derive the closed
  shape from the form; remove the option from its `deftype*` call.
- [x] `identity-field-deftype*`: add the `:invalid-option` check between
  `:record-shape` and the metadata-key checks; derive the closed shape from
  the form; remove the option from its `deftype*` call.
- [x] Make closed forms without enabled fields take the receiving path in
  both representations (empty specifications); no AOT companion write for
  them in the value path.

## Slice 4 — Value output

- [x] In `transform/->record-value-class`: for the closed shape remove
  `__meta`, `__extmap`, `__hash` and `__hasheq`; keep constructor
  descriptors, delegating constructors, `create` and `getBasis` unchanged.
- [x] Add `fields/closed-check-message` (class name → text); it gives the
  check message for both outputs (plan step 4). The text is not contract.
- [x] Add the D10 check to the existing main-constructor rewrite
  (`transform/constructor-code`), before the first field write and thus
  before the non-nil field checks: non-nil `__meta` or `__extmap`
  argument throws `IllegalArgumentException`. Use the check form of plan
  step 5 in `CodeBuilder` instructions (slots from `field-slots`; the
  message from `fields/closed-check-message`).
- [x] Extend `value-backend/check-removed-fields` for closed forms to the
  ordered fields `__meta __extmap __hash __hasheq` (first accessed field
  is `:field`, D4) and adjust the error text.

## Slice 5 — Identity output

- [x] Pass the shape to `identity-backend/prepare` as its fourth argument
  (`loader/capture-loader`), as for `value-backend/prepare`.
- [x] Apply the constructor rules (control-flow rejection, "both creates
  and calls the target", delegating-write and complete-write rules) when
  the owner has enabled fields or the shape is closed (plan step 5); no
  closed-only exceptions.
- [x] For a closed owner, add a first read pass that marks each
  constructor as delegating when it calls `invokespecial <owner>.<init>`
  (D8 rule unchanged). Constructors that the mark does not classify
  (control flow, creates and calls) get `:unsupported-shape` in the
  rewrite pass.
- [x] Emit the D10 entry check in each non-delegating constructor of a
  closed form, in `visitCode` on the delegate visitor, before the first
  original instruction, in the form of plan step 5: for parameter n − 4
  (`__meta`), then n − 3 (`__extmap`): `aload` slot, `ifnull`,
  `new`/`dup`/`ldc`/`invokespecial`/`athrow` of
  `IllegalArgumentException` (the message from
  `fields/closed-check-message`), `F_SAME` frame at the target. Compute the
  slots from the descriptor (`long`/`double` take two). Set max stack to
  max(original, 3). Keep `(ClassWriter. 0)`. Verify with `-Xverify:all`
  on Java 8 and on EA3 with preview off.
- [x] In the first pass, raise `:unsupported-shape` for a non-delegating
  constructor with fewer than four parameters or a non-`Object` parameter
  at n − 4 or n − 3, and for a closed class with no non-delegating
  constructor.

## Slice 6 — Tests

- [x] Option error tests: `:invalid-value`, `:record-shape-required`,
  error data, and the last-occurrence rule for a repeated option, at
  `defvaluetype*` and both receiving macros.
- [x] Order-pair tests with `:invalid-option`. Fixture of each
  `:record-shape`/`:invalid-option` pair: a form with a `:record-shape`
  error (an incomplete or misplaced hidden field set, for example
  `[x __meta]`) and an invalid option value (`:reason :invalid-value`);
  expect `:record-shape`. Do not use `true`: `:record-shape-required`
  needs a plain-shape form, which has no `:record-shape` error.
  - `defvaluetype*`: `:record-shape`/`:invalid-option`,
    `:invalid-option`/metadata keys.
  - `value-field-deftype*` (direct): `:record-shape`/`:invalid-option`,
    `:invalid-option`/`:value-capability-unavailable` on a JVM without
    value capability.
  - `identity-field-deftype*` (direct): `:record-shape`/`:invalid-option`,
    `:invalid-option`/metadata keys.
- [x] Construction-path tests, both outputs, closed forms with and without
  enabled fields: main constructor (direct, reflective), both delegating
  constructors (direct, reflective), `create`, map literal, positional
  literal, compiled record constant, caller `assoc`. Valid input
  constructs; identity output has nil `__meta`/`__extmap` after each valid
  construction.
- [x] Negative tests: non-nil `__meta` or `__extmap` and a map literal
  with an undeclared key give `IllegalArgumentException`, found in the
  cause chain as in task 020; the check runs before the non-nil checks
  (undeclared key plus nil enabled field → `IllegalArgumentException`).
- [x] Byte-inspection tests: value closed output has only declared fields,
  unchanged descriptors and delegating constructors, check in the main
  constructor only; identity closed output has all four hidden fields and
  the check in the main constructor only; `deftype*` calls of
  `defvaluetype*` and both receiving macros have no `:value-type/closed`
  for `true`, `false`, `nil` and a repeated option. Check signature (plan
  step 5): exactly one constructor, the main constructor, has a
  `new java/lang/IllegalArgumentException` instruction, before its first
  `requireNonNull` call when it has one. Value bytes: `java.lang.classfile`
  on `transform/->record-value-class` output; identity bytes:
  `clojure.asm.ClassReader` on the identity owner transform output.
- [x] Direct identity backend tests with synthetic class bytes
  (`clojure.asm.ClassWriter`, as in
  `test/compatibility/value_type/identity_backend_test.clj`), closed
  shape: each closed `:unsupported-shape` case (fewer than four
  parameters, non-`Object` parameter at n − 4 or n − 3, no
  non-delegating constructor); without enabled fields, a constructor that
  both creates and calls the target, and a constructor with a jump, give
  `:unsupported-shape`.
- [x] Removed-field access tests: owner and companion access to `__meta`
  or `__extmap` in value output gives `:removed-field-access`; `:field`
  follows the order `__meta`, `__extmap`, `__hash`, `__hasheq`.
- [x] Transferred-expansion tests (`macroexpand`, `macroexpand-1`) of
  closed forms in both representations; AOT in a subprocess JVM fixes the
  representation; fallback policies `silent`, `warn`, `error` behave as
  for forms with enabled fields.
- [x] Cross-JVM: identity output of closed forms on Java 8 and on EA3 with
  preview off.
- [x] EA3 evidence test: `value-type.array` array of a closed record with
  `[^long x]` gives `:flat? true` on EA3 with preview and the
  `jdk.internal.value` export (evidence only, not contract).
- [x] Regression: all existing `defvaluetype*`, `defvaluetype` and array
  tests pass; forms without the option keep their expansion.

- [x] Expansion test: the `deftype*` calls of both receiving macros have
  no `:value-type/closed` for `true` and a repeated option (needs a
  compiler loader; done with Slice 4/5 closed construction tests). The
  `defvaluetype*` part for `false`, `nil` and a repeated option is done
  (`closed-option-removal-test`).

## Slice 7 — Documentation and quality

- [x] Update the `defvaluetype*` docstring (`src/value_type/core.clj`)
  with the content of plan step 7: the option (values, last occurrence,
  removal from the `deftype*` call), the closed caller obligations, the
  closed outputs and the `IllegalArgumentException` behaviour,
  `:invalid-option` in the "Errors" order list after `:record-shape`, all
  four fields for `:removed-field-access` of the closed shape, and
  "enabled fields or the closed shape" in the receiving, identity-output
  and transferred-expansion statements ("neither enabled fields nor the
  closed shape" in the statement on value expansions without enabled
  fields).
- [x] Regenerate `doc/API.md` with `bb api-docs`; do not edit it by hand.
- [x] Update `doc/usage.md` "`deftype*` forms" with the content of plan
  step 7: option paragraph, `:invalid-option` as item 3 of the numbered
  order list, closed caller obligations, closed outputs,
  `:removed-field-access` fields, and "enabled fields or the closed shape"
  in the `:unsupported-shape` sentence and the "Representation policy,
  fallback and AOT" paragraph ("neither enabled fields nor the closed
  shape" in its statements on expansions without enabled fields). Do not
  list the closed
  `:unsupported-shape` cases.
- [x] Update the README `deftype*` callers sentence: plain,
  `defrecord`-shaped and closed record classes.
- [x] Update `CHANGELOG.md` and `mementum/state.md` (closed shape is
  implemented).
- [x] Update `ramora/deftype-star-design/embodiment.md` with the content
  of plan step 7, so that it describes the closed code: the intro (status
  sentence, `value-type.core` and `value-type.impl.runtime` elements),
  "Declaration checks (D6)", "Receiving macros", "Record value storage
  and checks (D8)", "Identity delegation rule (D8)" and "Evidence" (closed
  tests). Remove each "Differences from the design" entry that no longer
  applies; when no entry remains, remove the section and the intro link
  to it.
- [x] Run `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint`,
  `bb vale`, and the runtime, fallback and cross-JVM suites; record
  commands, results and limits in `implementation.md`.

## Implementation review follow-up

- [x] Simplify the identity closed classification in
  `src/value_type/impl/identity_backend.clj`: `closed-checks` reads the
  class bytes twice before `rewrite` reads them again
  (`delegating-constructors` with code, then a `SKIP_CODE` pass that only
  collects `<init>` descriptors). Collect the constructor descriptors and
  the delegation marks in the one `delegating-constructors` pass, and
  remove the `SKIP_CODE` pass. Keep the closed `:unsupported-shape` tests
  green.
- [x] Reflow the text that Slice 7 edited, so that lines are filled again:
  the `defvaluetype*` docstring in `src/value_type/core.clj` ("…the
  expansion is a receiving / form, also on / fallback…" and the 105-column
  line "works, and only a full expansion outside compilation…"), then
  regenerate `doc/API.md` with `bb api-docs`; and `doc/usage.md`
  "`deftype*` forms" (the `:unsupported-shape` sentence and the
  "Representation policy, fallback and AOT" paragraph). Text content does
  not change. Run `bb api-docs --check` and `bb vale`.
- [x] Update the docstring of `fields/record-hidden-fields`
  (`src/value_type/impl/fields.clj`): "Value output removes the fields with
  tag `int`" is true only for the record shape; the closed shape removes
  all four (`transform/closed-field-names`).

## Implementation review follow-up, pass 2

- [x] Reflow the over-long docstring lines that this task added: the first
  line of `transform/constructor-code` (`src/value_type/impl/transform.clj`,
  130 columns, "Emit nil checks of `checks` …") and the `install!`
  docstring line "`value-type.impl.transform/->record-value-class` for
  `:record` and `:closed`; `:identity` uses" (`src/value_type/impl/loader.clj`,
  96 columns). Text content does not change. Run `bb clj-fmt:check`.
- [x] In `transform/closed-checks` (`src/value_type/impl/transform.clj`), do
  not parse the binary class name from `(.descriptorString this)` with
  `subs`. Use the existing pattern of `unexpected-constructor!`
  (`(.replace (.asInternalName (.thisClass cm)) \/ \.)`): compute the name
  once in `->record-value-class` and pass it (or the message) down. Keep
  the closed `transform_test` cases green.

## Implementation review follow-up, pass 3

- [x] Reflow the `install!` docstring in `src/value_type/impl/loader.clj`
  again: the pass-2 reflow made the line "`*compile-files*` is true, it
  defines them and links the class. With field specifications, it also
  rewrites" 109 columns long (line ~96). Fill the paragraph to the column
  of the other lines. Text content does not change. Run
  `bb clj-fmt:check`.
- [x] Reflow the text that Slice 7 edited in
  `ramora/deftype-star-design/embodiment.md`: the intro `value-type.core`
  element ("…expands to the / receiving macros…", short line) and
  "Receiving macros" (lines ~37–50: 81 to 108 columns, for example
  "`value-field-deftype*` and the fallback expansion is
  `identity-field-deftype*`. Both carry" and the two receiver order
  items). The other lines of the file are at most 80 columns. Text content
  does not change. Run `bb vale`.

## Implementation review follow-up, pass 4

- [x] In `identity-backend/prepare` (`src/value_type/impl/identity_backend.clj`),
  return the companion bytes unchanged when `types` is empty, as
  `value-backend/prepare` does with `(if (empty? storage) % …)`. Closed
  identity forms without enabled fields now take the receiving path, so
  each companion class gets an ASM read and write that changes nothing.
  Keep the identity backend and closed construction tests green.
- [x] In `src/value_type/impl/transform.clj`, rename `nil-check!` (it
  throws when the reference is NOT nil), for example to `require-nil!`,
  and fix the `constructor-code` docstring: "Emit nil checks of `checks`"
  and "non-native fields get explicit null checks" use the same word for
  opposite checks. Name the D10 checks "checks that `__meta` and
  `__extmap` are nil" (or similar). Run `bb clj-fmt:check` and the
  `transform_test` closed cases.

## Test review follow-up

- [x] In `closed-identity-entry-check`
  (`test/compatibility/value_type/identity_backend_test.clj`), the
  "companions get no check" case calls `prepare` with `[]`
  specifications. Since pass 4, the companion transform then returns its
  input unchanged, so the case cannot fail. Use one enabled-field
  specification there (the companion is then rewritten), so that the case
  shows that the companion rewrite adds no D10 check.
- [x] In `closed-identity-compiler-output-bytes` (same file), the "one
  enabled field" specification uses the key `:mode :generated`. The other
  backend tests and `fields/receiving-specifications` use
  `:enforcement :generated`. Use `:enforcement`.
- [x] Remove the duplicate `meta-rejected?` helper: the same function is in
  `test/compatibility/value_type/deftype_star_transfer_checks.clj` and
  `test/compatibility/value_type/deftype_star_aot_checks.clj` (the AOT
  copy differs only in the plain-shape guard). Put one helper in a shared
  support namespace (for example `cell-registry`, next to
  `hash-field-count`) and use it from both. Keep the transfer and AOT
  suites green.

## Test review follow-up, pass 2

- [x] Remove the duplicate macroexpansion helpers that this task added to
  the receiving tests. `star-option-error`
  (`test/runtime/value_type/value_receiving_test.clj` and
  `test/compatibility/value_type/identity_receiving_test.clj`) is
  `star-error` of the same file with option pairs after `fields`, and
  `value-star-error` (`identity_receiving_test.clj`) is the same again
  with `value-field-deftype*`. Give `star-error` a `& opts` parameter and
  use it; in `identity_receiving_test.clj`, let one helper take the macro
  symbol. Keep the receiving tests green.
- [x] Do not count the instructions of the D10 check in the byte tests.
  `closed-record-structure-test`
  (`test/runtime/value_type/impl/transform_test.clj`, `{main 2}`) and
  `closed-identity-compiler-output-bytes`
  (`test/compatibility/value_type/identity_backend_test.clj`,
  `[:iae :iae]` / `[:iae :iae :nn]`) require two
  `new IllegalArgumentException` instructions, so a check form with one
  throw site fails them with the same behaviour. Assert that the main
  constructor is the only constructor with the check signature, and (with
  an enabled field) that the first `:iae` comes before the first `:nn`.
  Behaviour order stays in `closed-check-before-non-nil-test` and
  `closed-record-check-order-test`.

## Test review follow-up, pass 3

- [x] Remove `closed-check-constructors` from
  `test/compatibility/value_type/identity_backend_test.clj`. It repeats
  the `new IllegalArgumentException` scan of `class-structure` in the same
  file. In `closed-identity-entry-check`, get the checked constructors from
  `class-structure` (the descriptors whose markers contain `:iae`), as
  `closed-identity-compiler-output-bytes` does. Keep the identity backend
  tests green.
- [x] Limit `deftype*-closed-value-transfer-test`
  (`test/compatibility/value_type/deftype_star_transfer_test.clj`) to the
  closed shape. Its plain and record cells (`not-transferred`) assert
  behaviour that this task does not change (value expansions without
  enabled fields do not transfer), and each cell costs a producer and
  child-JVM run. For example, give `transfer-cells` the shapes to run, and
  remove `not-transferred`. Keep the transfer suite green.
- [x] Make the field-set assertion of `check-closed`
  (`test/runtime/value_type/deftype_star_record_test.clj`) exact. Now it
  removes the four hidden names from the instance fields before the
  compare, so identity output without `__hash` or `__hasheq` also passes.
  Compare `(instance-field-names c)` with the declared names, plus
  `__meta`, `__extmap`, `__hash` and `__hasheq` for identity output.

## Test review follow-up, pass 4

- [x] Make the contract of `meta-rejected?`
  (`test/compatibility/value_type/cell_registry.clj`) match its code. The
  docstring says "a record-shaped Class c", but the code finds the
  meta-and-extmap constructor by parameter count 3 and calls it with
  `[42 {:m 1} nil]`, so it is correct only for one declared field that
  accepts `42` (the transfer and AOT fixtures). A record-shaped class with
  two declared fields gives nil, as for a plain class. State the
  one-field fixture assumption in the docstring (or derive the arity
  from the declared fields). Keep the transfer and AOT suites green.

## Test review follow-up, pass 5

- [x] Remove the `identity-closed*` macro from
  `test/runtime/value_type/deftype_star_record_test.clj`. Its body is the
  same as `identity-receiving*` in the same file. Use
  `identity-receiving*` in `closed-definers`, and change its docstring so
  that it also covers closed forms without enabled fields (empty modes).
  Keep the record tests green.
- [x] Remove `closed-value-class` from
  `test/runtime/value_type/impl/transform_test.clj`. It is
  `record-value-class` of the same file with a `storage` argument and
  `closed?` true. Give `record-value-class` the `storage` and `closed?`
  arguments (or one options map) and use it in the closed tests. Keep the
  transform tests green.
- [x] Make the comment of `deftype*-transferred-expansion-test`
  (`test/compatibility/value_type/deftype_star_transfer_test.clj`) match
  its cells. It says "Fixed fallback expansions", but the `:closed` cells
  are identity receiving expansions (`identity-field-deftype*`) that also
  keep the D10 check (`:meta-rejected? true`).

## Test review follow-up, pass 6

- [x] Remove `option-error` from
  `test/runtime/value_type/deftype_star_test.clj`. It is
  `expansion-error` of the same file with option pairs after `fields`.
  Give `expansion-error` a `& opts` parameter (as `star-error` in
  `value_receiving_test.clj`) and use it in `closed-option-error-test`
  and `closed-option-order-test`. Keep the `deftype_star_test` tests
  green.

## Test review follow-up, pass 7

- [x] Remove `root-cause` from
  `test/compatibility/value_type/identity_backend_test.clj`. It is
  `value-type.cell-registry/root` (Java 8 safe, same suite). Require
  `cell-registry` and use `root` in `closed-identity-entry-check`. Keep
  the identity backend tests green.
- [x] In `meta-rejected?` (`test/compatibility/value_type/cell_registry.clj`),
  use the private `chain` of the same namespace for the cause chain,
  not a second `(take-while some? (iterate ex-cause thrown))`. Keep the
  transfer and AOT suites green.
- [x] Remove `cause-chain` and `thrown-in-chain` from
  `test/runtime/value_type/impl/transform_test.clj`. They repeat
  `cause-chain-classes` and `rejection` of
  `test/runtime/value_type/deftype_star_record_test.clj`. Move
  `rejection` (with its chain helper) to `value-type.test-support` and
  use it from both files, for example
  `(= NullPointerException (rejection NullPointerException f))`. Keep the
  transform and record tests green.

## Test review follow-up, pass 8

- [x] Test that `getBasis` of closed output is unchanged (design Scope:
  "`create` and `getBasis` unchanged"). No closed test reads it now;
  `record-structure-test` and `record-entry-points-test` check it only
  for the record shape. In `check-closed`
  (`test/runtime/value_type/deftype_star_record_test.clj`), assert that
  `getBasis` gives the declared fields, as `record-entry-points-test`
  does, in both outputs. Keep the record tests green.
- [x] Remove the two inline copies of the "checked constructors" filter
  in `test/compatibility/value_type/identity_backend_test.clj`:
  `checked` in `closed-identity-entry-check` (`keep` over `:ctors`) and
  `checked` in `closed-identity-compiler-output-bytes`
  (`(into {} (filter …))`). Add one private helper next to
  `class-structure` (descriptor → markers of the constructors whose
  markers contain `:iae`) and use it in both tests. Keep the identity
  backend tests green.

## Test review follow-up, pass 10

- [x] Remove the stray review note "- test review added 2 steps to be
  addressed" from the end of "Test review follow-up, pass 8" in this
  file. It is a review note (it is already in `implementation.md`), not
  a step.

## Docs review follow-up

- [x] Update "Verify `deftype*` forms" in `doc/development/runtime.md` for
  the closed shape. Now it names only plain and record shapes. Make it
  state: the coverage paragraphs include the closed shape (option errors
  and order, closed construction and rejection in both outputs, closed
  bytes, removed-field access for all four fields, closed transfer cells,
  the closed identity backend cases); the AOT fixtures also include
  `Closed` and `ClosedNonNil`; "Limits" names reflective access to
  `__meta` and `__extmap` of closed value output as not checked, and
  names the closed gate (`dev/spike/record-value-type/closed-gate.clj`,
  which also needs the `jdk.internal.value` export) next to the non-nil
  gate. Name where the EA3 flat-array evidence runs
  (`value-type.array-info-test` `closed-record-layout-test`, runtime
  suite with the export), or state that this section does not cover it.
  Run `bb vale`.

## Code-shaper review follow-up

- [x] In `policy-expansion` (`src/value_type/core.clj`), compute the
  receiving rule `(or (seq specifications) (= :closed shape))` once (for
  example a local `receiving?`) and use it in both branches. Now the two
  branches repeat it, so a change to the rule ("enabled fields or the
  closed shape") must be made twice. Add the closed shape to the
  docstring statement of when a receiving form is returned.
- [x] Use one data shape and one name for the D10 check in both outputs.
  Now `transform/constructor-code` takes `checks` as `[[slot message] …]`
  and emits each pair with `require-nil!`, while
  `identity-backend/method-visitor` takes `check` as `[slots message]` and
  emits it with `emit-closed-check!`. Both outputs use one message for
  both slots. Select one shape (for example `[slots message]`) and one
  emitter name (for example `require-nil!` in both namespaces), and update
  `transform/closed-checks`, `identity-backend/closed-checks` and the
  docstrings. Keep the closed `transform_test` and `identity_backend_test`
  cases green.
- [x] In `transform/closed-checks` (`src/value_type/impl/transform.clj`),
  do not repeat the literal names `["__meta" "__extmap"]`. Derive them from
  `fields/record-hidden-fields` (the source of `closed-field-names`), for
  example the fields of `closed-field-names` that are not in
  `hash-field-names`.
- [x] In `identity-backend/closed-checks`
  (`src/value_type/impl/identity_backend.clj`), separate the validation
  from the computation. Now the `for` body throws `:unsupported-shape`
  inside a `do` and the eager `into {}` makes it run. First check each
  non-delegating descriptor (for example `doseq`), then build the
  descriptor → check map with no side effects.
- [x] In `identity-backend/rewrite`, replace the two positional booleans
  `owner? closed?` with one value that cannot hold the invalid combination
  (companion and closed). Now the companion call is
  `(rewrite % owner types false false)` and `rewrite` guards with
  `(and owner? closed?)`. For example, `prepare` passes a role
  (`:companion`, `:owner`, `:closed-owner`) or the shape for the owner
  and nil for the companion. Keep the identity backend tests green.

## Code-shaper review follow-up, pass 2

- [x] Reflow the `require-nil!` docstring in
  `src/value_type/impl/identity_backend.clj` (line ~29, 90 columns:
  "the parameter in one of `slots` is not nil. Each jump target gets an
  `F_SAME` frame; the"). The pass-1 follow-up made it. Fill the paragraph
  to the column of the other lines. Text content does not change. Run
  `bb clj-fmt:check`.
- [x] Use one source for the removed-field set of each record shape. Now
  two namespaces select it from `closed?`:
  `transform/record-value-class-transform`
  (`(if closed? closed-field? hash-field?)`) and `value-backend/prepare`
  (`(if closed? transform/closed-field-names transform/hash-field-names)`).
  A change to one gives a `check-removed-fields` set that is not the set
  that the transform removes. Add one function in `transform` (for
  example `removed-field-names`, `closed?` → ordered vector) and use it
  in both places. Keep the record and closed `transform_test` and
  removed-field access tests green.

## Test review follow-up, pass 12

- [x] Make the comments of the two value transfer tests in
  `test/compatibility/value_type/deftype_star_transfer_test.clj` match
  their cells and facts. `deftype*-enabled-value-transfer-test` says
  "remove the hash fields … (the receiver passes the :record shape to the
  capture loader)", but its `:closed` cells pass `:closed` and also keep
  the D10 check (`:meta-rejected? true`). `deftype*-closed-value-transfer-test`
  says "removes all four hidden fields", but its facts count only
  `__hash` and `__hasheq` (`:hash-fields 0`); removal of `__meta` and
  `__extmap` is tested in `check-closed`
  (`test/runtime/value_type/deftype_star_record_test.clj`). State only
  what the cells assert. Comment change only.

## Test review follow-up, pass 13

- [x] Make the docstrings of `consume` and `evaluate-twice`
  (`test/compatibility/value_type/deftype_star_transfer_checks.clj`) match
  the facts. This task added `:meta-rejected?` to the facts of
  `evaluate-twice`, but `consume` still lists only `{:named? :value?
  :hash-fields :new-class?}`, and the `evaluate-twice` docstring has a
  broken line ("merged with" / "(extra-facts c)"). Add `:meta-rejected?`
  to the `consume` list and fill the `evaluate-twice` paragraph.
  Docstring change only.

## Test review follow-up, pass 14

- [x] Make the `construct` docstring
  (`test/compatibility/value_type/deftype_star_transfer_checks.clj`) agree
  with the shapes. `consume-enabled` calls it for each cell of
  `checks/shapes`, which now includes `:closed`, but the docstring names
  only "the main constructor of the plain shape, the declared-fields
  constructor of the record shape". Name the record and closed shapes.
  Docstring change only.

## Test review follow-up, pass 15

- [x] Fill the `consume` docstring paragraph
  (`test/compatibility/value_type/deftype_star_aot_checks.clj`). This task
  added `:meta-rejected? for record shapes` and left a short line
  ("shapes, plus for an enabled field x its"). Fill the paragraph to the
  column of the other lines, as pass 13 did for `evaluate-twice`.
  Docstring change only. Run `bb clj-fmt:check`.

## Code-shaper review follow-up, pass 3

- [x] In `src/value_type/impl/transform.clj`, make `hash-field-names`,
  `hash-field?` and `closed-field-names` private. Since pass 2, no other
  namespace (src, test) uses them; `removed-field-names` is the one public
  source of the removed-field set. Public defs let a new caller select the
  set again without `removed-field-names`. Run `bb clj-kondo:lint` and the
  `transform_test` cases.
- [x] In `src/value_type/core.clj`, do not repeat the hidden-field fix
  text. `record-shape` ("end the fields with __meta __extmap ^int __hash
  ^int __hasheq.") and `closed-shape` (`:record-shape-required`) each
  spell the four fields as a literal. Make one private fix string (or
  derive it from `fields/record-hidden-fields`) and use it in both
  messages. Message text is not contract; keep the `deftype_star_test`
  option and record-shape tests green.

## Code-shaper review follow-up, pass 4

- [x] In `src/value_type/impl/transform.clj`, select the removed-field set
  and the D10 check from the shape keyword, not from a boolean.
  `removed-field-names` and `->record-value-class` take a positional
  `closed?`, while both `prepare` functions and the loader take `shape`
  (`:record`, `:closed`). `value-backend/prepare` converts the shape to
  `closed?` only to pass it on. Give both functions the shape (for example
  `(removed-field-names shape)`, `(->record-value-class bs storage loader
  shape)`), remove the `closed?` local in `value-backend/prepare`, and
  update the docstrings and the `transform_test` call sites (`false` →
  `:record`, `true` → `:closed`). Keep the record and closed
  `transform_test` cases green.
- [x] In `src/value_type/impl/transform.clj`, compute the binary class name
  in one place. `unexpected-constructor!` and `->record-value-class` each
  have `(.replace (.asInternalName (.thisClass cm)) \/ \.)`. Add one
  private function (for example `binary-name`, `cm` → String) and use it
  in both. Run `bb clj-kondo:lint` and the `transform_test` cases.

## Code-shaper review follow-up, pass 5

- [x] In `identity-backend/rewrite` and `method-visitor`
  (`src/value_type/impl/identity_backend.clj`), make the
  `:unsupported-shape` texts true for a closed owner without enabled
  fields. Now the constructor rules apply to it, but the texts name checks
  that it does not have: the prefix "Cannot add generated field checks to"
  and the constructor parts "unsupported non-nil constructor control
  flow" and "non-nil constructor both creates and calls the target".
  `closed-identity-*` tests (`identity_backend_test.clj`, lines ~509–518)
  assert these texts for a closed class with `[]` specifications. Use
  texts that name the constructor rewrite (for example "constructor
  control flow", "constructor both creates and calls the target") and
  update the expected parts in the tests. Message text is not contract;
  keep the identity backend tests green.
- [x] In `value-backend/check-removed-fields`
  (`src/value_type/impl/value_backend.clj`), make the fix text agree with
  the removed fields. For the closed shape it names `__meta, __extmap,
  __hash, __hasheq` and then says only "compute hashes from entries",
  which does not apply to `__meta` and `__extmap`. Give the fix for both
  sets (for example add "pass nil for __meta and __extmap" when they are
  removed), or a fix that is true for both. Keep the removed-field access
  tests green.

## Code-shaper review follow-up, pass 6

- [x] In `value-backend/check-removed-fields`
  (`src/value_type/impl/value_backend.clj`), do not select the closed fix
  text with the literal set `#{"__meta" "__extmap"}`. Pass 5 added it; it
  repeats the names that `fields/record-hidden-fields` owns (pass 1
  removed the same literal from `transform/closed-checks`). Give
  `check-removed-fields` the `shape` instead of `removed`: it gets the
  ordered names from `transform/removed-field-names` and adds the closed
  fix when the shape is `:closed`. `prepare` then passes `shape` and drops
  the `removed` local. Keep the removed-field access tests green.
