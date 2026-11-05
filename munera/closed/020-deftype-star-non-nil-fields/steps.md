# Steps

## Slice 1: Feasibility gate

- [x] Write `spike/record-value-type/non-nil-gate.clj`: record-shaped value
      class with one native field (`NullRestricted`, value-class storage)
      and one generated field (identity-class storage), spike-local
      transform; exercise each constructor, `create`, both record literal
      forms and a compiled record constant (nil-in-map-view evidence) for
      store-valid and reject-nil; do not change `gate.clj`
- [x] Add the gate command to `spike/record-value-type/README.md`
- [x] Run the gate on EA3 with preview and `-Xverify:all`; save the raw
      result to
      `ramora/deftype-star-design/evidence/non-nil-gate-ea3-preview.edn`
- [x] Write `ramora/deftype-star-design/non-nil-gate.md`: method, results,
      limits (including "production transform not covered") and the
      SHA-256 of `non-nil-gate.clj`
- [x] If the gate fails: record evidence, stop for a design decision
      (not needed: gate passed, `:failed []`)

## Slice 2: Declaration validation

- [x] Split or expose the metadata and tag/type phases in
      `value-type.impl.fields` so `:hidden-field` can run between them;
      keep `fields` the one owner of non-nil declaration rules
- [x] Add the `:hidden-field` check (`:non-nil true` on a hidden-name
      field; `:field` is the first such field in field order)
- [x] Wire the D6 error order in `value-type.core` for `defvaluetype*`:
      `:mutable-field`, `:record-shape`, metadata keys, `:hidden-field`,
      then tag and type reasons; first failure wins
- [x] Remove the `:non-nil-unsupported` rejection and reason, no alias
- [x] Replace `field-metadata-rejection-test` in
      `test/runtime/value_type/deftype_star_test.clj` with D6 tests
- [x] Update the "declaration error" policy cell in
      `test/compatibility/value_type/runtime_test.clj` (`^:non-nil x` with
      no tag gives `:missing-tag`)
- [x] Add a test per declaration error of the contract, including
      `:hidden-field`, and adjacent-pair error-order tests
- [x] Verify existing `defvaluetype`, non-nil and fields tests still pass

## Slice 3: Value backend

- [x] In `->record-value-class`, give the main constructor concrete
      storage, casts, native restriction and `nil` checks by reusing the
      plain-path `transform/constructor-code` storage argument; rewrite
      owner and companion field references to concrete descriptors
- [x] Keep delegating constructors, `create` and constructor descriptors
      unchanged; keep D3 shape validation, the D4 removed-field check
      (`:invalid-field` `:reason :removed-field-access`) and hash-field
      removal
- [x] In `value_backend/prepare`, pass the `:record` specifications to
      the record transform; update its docstring (it now says that
      `:record` ignores specifications)
- [x] Add to `deftype_star_record_test.clj` the private fixture helper
      and the `value-installed*` macro of plan.md ("Fixture macros"):
      `tagname classname fields & opts+specs`; shape from the
      `record-tail` names; specifications from `fields/specifications`,
      modes from `fields/select-enforcement` with
      `#(:available? (runtime/field-capability))`; `loader/install!`, then
      `(let [] (deftype* …) classname)` (precedent: `restricted-type` and
      `mixed-type` in `test/runtime/value_type/impl/loader_test.clj`); use
      it as the `definer` of `record-form` for record forms
- [x] Runtime tests through `value-installed*` (value output, both
      shapes): valid values construct through each contract construction
      path and read back; `nil` gives `NullPointerException` and wrong type
      gives `ClassCastException` through each path, found in the cause
      chain; record shape covers one native and one generated field per
      path, including `create` with an absent key, both literals, compiled
      constant and reflection; plain shape uses a plain `deftype*` class
      (the plain value path needs no code change); the native field tag is
      a `defvaluetype` class defined first
- [x] Bytecode inspection test: value record output has concrete storage,
      native restriction where selected, no hash fields, unchanged
      constructor descriptors and delegating constructors

## Slice 4: Identity backend

- [x] In `value-type.impl.identity-backend` (ASM only), detect delegation:
      `invokespecial <init>` with owner = target on the receiver; accept
      delegating constructors that write no enabled field
- [x] Reject with `:unsupported-shape`: a delegating constructor that
      writes an enabled field, and a constructor with a `NEW` of the
      target that also calls one of its constructors; keep the
      write-each-enabled-field-once rule for other constructors;
      shape-independent
- [x] Keep hash fields in identity record output; do not check access to
      them
- [x] Add `identity-installed*` over the slice 3 helper (`:identity`
      keeps the `:generated` modes of `fields/specifications`)
- [x] Runtime tests through `identity-installed*` (identity output, both
      shapes): construction-path positives and negatives as in slice 3
- [x] Internal tests in
      `test/compatibility/value_type/identity_backend_test.clj`: the
      backend accepts compiler delegating constructors and gives
      `:unsupported-shape` for each D8-rejected case; `:identity :record`
      fixture macros go next to
      `test/compatibility/value_type/identity_backend/fixture.clj`; the
      fallback suite also runs these tests on Java 8
- [x] Bytecode inspection test: identity record output has concrete
      storage, generated checks only in the main constructor, and the
      hash fields

## Slice 5: Receiving, public forms and AOT

- [x] Add the `defvaluetype*` receiving macros `value-field-deftype*`
      and `identity-field-deftype*` in `value-type.core`, carrying
      `tagname classname modes fields & opts+specs`, following the
      `defvaluetype` receiving macros; expansion transports the form and fixed
      enforcement modes, not the shape
- [x] Receiver check order (plan.md "Receiving boundary"):
      `value-field-deftype*`: `:mutable-field`, `:record-shape` (derives
      the shape), value capability, metadata keys, `:hidden-field`, modes,
      tag and type, native field capability; `identity-field-deftype*`:
      `:mutable-field`, `:record-shape`, metadata keys, `:hidden-field`,
      modes, tag and type, fixed fallback policy
- [x] Pass the derived shape to the capture loader; remove the hard-coded
      `:plain` in `install-receiving!` for this path
- [x] Add `:no-doc` overrides for the new receiving macros in
      `bb/value_type/api_docs.clj`
- [x] Public-form tests of Acceptance 2 and 3 in
      `deftype_star_record_test.clj`, both shapes, both representations:
      value through `vt/defvaluetype*` on a capable JVM; identity through
      a test macro `identity-receiving*` (`tagname classname fields &
      opts+specs`) that expands to `vt/identity-field-deftype*` with one
      `:generated` mode for each field of `fields/enabled-fields` (the
      fallback expansion with enabled fields), in place of the
      `runtime/guarded-deftype*` definer for enabled-field forms;
      each contract construction path, positives and negatives in the
      cause chain; value record shape with one native and one generated
      field per path
- [x] Extend `deftype_star_transfer_checks` (plan.md "Transfer
      evidence"): `enabled?` spec element (`^Long ^:non-nil x`) in
      `produce`; `consume-enabled` with `:stored` and `:rejections`
      through the one-argument constructor by reflection, failures as
      `registry/root-failure`
- [x] Add two tests to `deftype_star_transfer_test.clj`, cells mode ×
      expand × shape with enabled fields, policies `silent`/`warn`/`error`;
      keep `deftype*-transferred-expansion-test` unchanged:
      identity producer (`config/identity-producer`): identity output with
      checks under `silent`/`warn`, fallback rejection under `error`;
      value producer (`config/value-producer`): value output with checks
      and `:hash-fields 0` on a receiver with capability, else
      `:value-capability-unavailable`
- [x] The value-producer record cell on a receiver with capability
      (`:hash-fields 0`) is the evidence that the receiver passes
      `:record` to the capture loader; run the fallback suite on the
      host JVM (slice 6 runs all three)
- [x] Internal-form receiving-order tests (plan.md "Receiving order and
      errors"): per receiving macro, for each adjacent pair in
      `:mutable-field`, `:record-shape`, metadata keys, `:hidden-field`,
      tag/type, a form with both errors gives the first; fixtures carry
      valid enforcement modes; metadata/`:hidden-field` pair uses a
      metadata error on a later field; `value-field-deftype*` cases in
      `test/runtime/value_type/value_receiving_test.clj` (runtime suite),
      `identity-field-deftype*` cases in
      `test/compatibility/value_type/identity_receiving_test.clj`
      (fallback suite)
- [x] Internal-form receiving-error tests (plan.md "Receiving order and
      errors"), assert `:data`, not the `fields/direct-fix` text:
      `:invalid-field-enforcement` for `identity-field-deftype*` with
      `[:native]` (`identity_receiving_test.clj`) and for
      `value-field-deftype*` with `[]` for one enabled field
      (`value_receiving_test.clj`); `:not-installed` for
      `clojure.walk/macroexpand-all` on a raw thread of a public
      record-shaped `defvaluetype*` form with `^String ^:non-nil a`
      (`test/runtime/value_type/deftype_star_test.clj`); the transfer
      cells cover `:value-capability-unavailable` and the fixed fallback
      rejection; no test for `:field-capability-unavailable` (no JVM
      reaches it)
- [x] Add the AOT fixtures `PlainNonNil` and `RecNonNil` to
      `deftype_star_aot_checks` (plan.md "AOT evidence"); keep `Plain`
      and `Rec` unchanged; `facts` adds `:rejections` for the new classes;
      extend `expected`
- [x] In `test/cross-jvm/value_type/deftype_star_aot_test.clj`: identity
      and value artifact tests use the extended `expected`;
      `incompatible-value-artifact-test` adds the owners
      `star.aot_fixture.PlainNonNil` and `star.aot_fixture.RecNonNil`

## Slice 6: Fallback policies and baseline JVMs

- [x] Add an option to `deftype*-form`
      (`test/compatibility/value_type/runtime_test.clj`) that adds the
      outcome keys `:nil` and `:wrong-type` (root class name of the cause
      chain of `(new cn nil)` and `(new cn "s")`, through
      `registry/failed-call`), and for the record shape `:create-stored`,
      `:create-nil` and `:create-wrong-type` through `create`
- [x] Add the cells "plain non-nil" (`^Long ^:non-nil x`) and "record
      non-nil" (the same field, then the record tail) to
      `deftype*-policy-test`, for `silent`, `warn` and `error`; expected
      `:stored 42`, `:create-stored 42`, `NullPointerException` and
      `ClassCastException`, or the failed definition under `error` without
      capability; keep the existing cells
- [x] Run the fallback suite on Java 8, on EA3 with preview off and on EA3
      with preview on; record results (no cross-JVM cells for
      Acceptance 7)

## Slice 7: Docs and commitments

- [x] Update the `defvaluetype*` docstring (`src/value_type/core.clj`):
      non-nil support, record behaviour; "does not validate constructor
      arguments" → "does not validate the hash arguments"; "Restrictions"
      keeps no mutable fields and no `:nil-restricted`, without
      `:non-nil`; the Errors list gives the D6 order (`:mutable-field`,
      `:record-shape`, `:removed-nil-restricted`/`:invalid-non-nil`,
      `:hidden-field`, then the `defvaluetype` tag and type reasons),
      without `:non-nil-unsupported`; remove `:non-nil` fields from
      "Excluded"
- [x] Update `doc/usage.md`: the hash-argument sentence (~line 427);
      non-nil support; replace the error-order paragraph (~lines 409–413)
      with the D6 order; remove `:non-nil` fields from "Exclusions"
      (~line 437); state that with enabled fields the fallback expansion
      is the identity receiving macro (~line 431, now "guarded `deftype*`
      output with no transform")
- [x] Regenerate `doc/API.md` with `bb api-docs`
- [x] If the docstring change alters text that
      `test/tooling/value_type/api_docs_test.clj` asserts (lines ~24–39),
      update those assertions in this slice
- [x] Update `CHANGELOG.md`: edit the existing `## [Unreleased]`
      `defvaluetype*` bullet in place; replace "`:non-nil` fields are
      excluded." with the non-nil support, record behaviour and evidence
      limits; add no separate bullet; do not name the removed reason
      (`defvaluetype*` is not released)
- [x] Update `mementum/state.md`: replace "no `:non-nil` fields" in the
      `defvaluetype*` bullet (~line 23) with the non-nil support
- [x] Name `defvaluetype*` in the non-nil field design embodiment
- [x] Rewrite the deftype* design Embodiment: no D6/D8 exception or Gap
      paragraph; name the D6 declaration checks (incl. `:hidden-field`,
      D6 order), the receiving macros, the delegation rule and the record
      value storage and checks; link the non-nil gate record; put detail
      in a linked file under `ramora/deftype-star-design/` (split rule)
- [x] Replace "This evidence has no non-nil fields" in "Qualities and
      risks" with the non-nil gate evidence and limits
- [x] Set D6, D8 and D3 to `firm` in
      `ramora/deftype-star-design/decisions.md` (gate-passed rule)

## Slice 8: Quality and regression

- [x] Run the runtime, fallback and cross-JVM suites
      (`doc/development/runtime.md`):
      `JAVA_CMD=<valhalla-java> clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli --dir test/runtime`;
      `JAVA_CMD=<java8> clojure -M:fallback-test`;
      `JAVA_CMD=<valhalla-java> clojure -M:fallback-test`;
      `JAVA_CMD=<valhalla-java> clojure -J--enable-preview -M:fallback-test`;
      `JAVA_CMD=<java8> clojure -M:cross-jvm-test`
- [x] Run the tooling suite
      `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir test/tooling`
      (exact documented names prove the slice 5 `:no-doc` overrides;
      docstring text of slice 7)
- [x] Run the release suite
      `clojure -M:release:release-test -m scry.cli --dir test/release`
      (`CHANGELOG.md` entries of slice 7)
- [x] Run the build suite
      `JAVA_CMD=<valhalla-java> clojure -M:build-test -m scry.cli --dir test/build`
      (packaging)
- [x] Run `bb api-docs --check`, `bb clj-fmt:check`, `bb clj-kondo:lint`,
      `bb vale`
- [x] `git grep non-nil-unsupported -- ':!munera'` finds no match
      (Acceptance 5)
- [x] Record commands, results and limits in `implementation.md`

## Implementation review follow-ups

- [x] Remove the duplication between the `defvaluetype` and `defvaluetype*`
      receiving macros (`src/value_type/core.clj`). `value-field-deftype*`
      repeats the body of `value-field-deftype` (value capability,
      `receiving-specifications`, native capability, `install-receiving!`,
      expansion), and `identity-field-deftype*` repeats
      `identity-field-deftype`. The differences are the shape, the
      `{:hidden-field? true}` option and the definition form. Extract one
      private helper per representation that takes these three values, and
      make the four macros call it. Keep the check order of each receiver;
      the `receiving-*` order tests must stay green.
- [x] Fix the `defvaluetype*` docstring (`src/value_type/core.clj`, Errors):
      `:unsupported-shape` is listed under "Errors of value output", but it
      is an identity output error. Put it under its own identity output
      line (for example "Errors of identity output with enabled fields"),
      then regenerate `doc/API.md` with `bb api-docs` and run
      `bb api-docs --check` and the tooling suite.
- [x] Remove the duplicated policy selection between the `defvaluetype` and
      `defvaluetype*` macro bodies (`src/value_type/core.clj`). Both repeat
      the same sequence: `runtime/capability`, `fields/select-enforcement`
      with the same field-capability thunk, `modes`, then the four-way
      branch (value receiving / value expansion / `enforce-fallback!` then
      identity receiving / guarded definition). The design says "Do not add
      a second policy path". Extract one private helper that takes the
      class name, shape, validated specifications and the three per-macro
      forms (value receiving form for `modes`, identity receiving form for
      `modes`, plain definition), and make both macros call it. Keep the
      D6 declaration order (validation before `runtime/capability`); the
      runtime, fallback and transfer suites must stay green.
- [x] Remove the duplicated storage composition in
      `src/value_type/impl/transform.clj`. `->value-class` and
      `->record-value-class` both compose the constructor transform with
      `(storage-transform storage)` and
      `(ClassTransform/transformingMethodBodies (field-access/rewrite this storage))`.
      Extract one private function that takes `this`, `storage` and the
      constructor transform, and make both functions call it. The
      `transform_test.clj` storage-structure tests must stay green.
- [x] Remove the duplication between `consume` and `consume-enabled` in
      `test/compatibility/value_type/deftype_star_transfer_checks.clj`.
      `consume-enabled` repeats the `consume` body; the differences are the
      failure function (`registry/root-failure`) and the keys `:stored` and
      `:rejections`. Make one private helper that takes these two values,
      and make both functions call it. The transfer tests must stay green.
- [x] Define the record tail once in `test/compatibility`. It is now in
      `runtime_test.clj` (`record-tail`), `identity_receiving_test.clj`
      (`star-tail`) and `deftype_star_transfer_checks.clj` (`fields`).
      Put one public definition in a shared compatibility namespace (for
      example `value-type.cell-registry`), and use it in the three files.
      Run the fallback suite.
- [x] Remove the copied inspection code in
      `test/runtime/value_type/impl/transform_test.clj`.
      `record-storage-structure-test` and
      `identity-record-storage-structure-test` repeat the main-constructor
      descriptor expression of `record-structure-test` (3 copies), the
      field name/type `juxt` (2 copies) and the `requireNonNull`
      predicate (2 copies). The identity test also adds its own
      constructor-code map next to `ctor-code-by-descriptor`. Extract
      private helpers (for example `main-descriptor`, `field-types`,
      `null-check?`, and `ctor-code-by-descriptor` with an instruction
      function), and make the three tests use them. The runtime suite must
      stay green.
- [x] Remove the copied ASM class setup in
      `test/compatibility/value_type/identity_backend_test.clj`.
      `delegation-bytes` repeats `constructor-bytes`: the class header,
      the `label` field and the checked `(Ljava/lang/Object;)V`
      constructor (super call, one write). The difference is the body of
      the second `()V` constructor. Make `constructor-bytes` take the
      second constructor body as an optional function (the "extra
      constructor" case passes the super call), and make
      `constructor-coverage` and `constructor-delegation-rule` use it.
      Remove `delegation-bytes`. The fallback suite must stay green.
- [x] Remove the repeated child forms in
      `test/compatibility/value_type/runtime_test.clj`. `non-nil-checks`
      repeats `(value-type.cell-registry/failed-call #(:class
      (value-type.cell-registry/thrown %)) #(eval …))` five times. Make
      one private function that takes the call form and returns this
      child form. In the "record non-nil" cell, get `:create-nil` and
      `:create-wrong-type` from `rejected-checks` (for example with
      key renames) and do not repeat the exception class names. The
      fallback suite must stay green.
- [x] Remove the unused argument of `nn` in `receiving-star-order-test`
      (`test/runtime/value_type/value_receiving_test.clj`). Each call
      passes `{}`. Use one argument, as `identity_receiving_test.clj`
      does.
- [x] Remove the copied shape detection in `installed*`
      (`test/runtime/value_type/deftype_star_record_test.clj`). It
      compares the names of the last four fields with `record-tail`.
      This is a second, weaker copy of `core/record-shape`: it ignores
      tags, namespaces and misplaced hidden fields. Call
      `@#'value-type.core/record-shape` with the class name and fields
      (precedent for private var access: `non_nil_fields_test.clj`).
      The runtime suite must stay green.
- [x] Remove the copied cell scaffolding in
      `test/compatibility/value_type/deftype_star_transfer_test.clj`.
      `deftype*-enabled-identity-transfer-test` repeats
      `deftype*-transferred-expansion-test`: the same identity producer,
      the `produce` check form, the `run-cells`/`for policy`/`cell-child`
      loop, the context vector, `:fallback? true` and `:warning
      harness/fallback-context`. `deftype*-enabled-value-transfer-test`
      repeats the producer, loop and context again. The spec mapping is
      inline in the first test and `enabled-specs` in the others. The
      differences are the producer, the `enabled?` flag, the consume
      function and the expected value per cell (and the fallback keys).
      Make one private helper that takes these values (for example
      `transfer-cells [producer enabled? consume expected & cell-keys]`),
      give it one spec function with an `enabled?` argument, and make the
      three tests call it. The fallback suite must stay green.
- [x] Add the direct-call construction path to the non-nil tests in
      `test/runtime/value_type/deftype_star_record_test.clj`. The design
      Contract lists "called directly and by reflection" for the main
      constructor (both shapes) and the delegating constructors (record
      shape), and Acceptance 2 and 3 need each path. `non-nil-record-paths`
      and `check-plain-non-nil` call constructors only through
      `Reflector/invokeConstructor`. Add compiled `(new cn …)` calls
      (through `eval`) for each record constructor arity and for the plain
      main constructor, with the valid, `nil` and wrong-type cases. The
      runtime suite must stay green.
- [x] Update the namespace docstring of
      `test/runtime/value_type/deftype_star_record_test.clj`. It says that
      identity output comes from `runtime/guarded-deftype*` only. The
      namespace now also tests non-nil fields through the fixture macros
      `value-installed*`/`identity-installed*`, `vt/defvaluetype*` and
      `identity-receiving*` (`vt/identity-field-deftype*`). In
      `check-record-non-nil`, count the hash fields with `field-names`, as
      `record-entry-points-test` does, in place of the inline
      `java.lang.reflect.Field` filter.

## Test review follow-ups

- [x] Prove the native field in the value non-nil tests
      (`test/runtime/value_type/deftype_star_record_test.clj`).
      Acceptance 3 needs one native and one generated field in value
      output. `check-record-non-nil` and `check-plain-non-nil` check only
      the field types, so if enforcement selection gives `:generated` for
      `p` (for example, no field capability), the value tests still pass.
      When `value?`, assert that field `p` has the
      `jdk.internal.vm.annotation.NullRestricted` annotation and field `s`
      does not (precedent: `value_receiving_test.clj` ~line 160,
      `.getAnnotations` on the field). Identity output: no annotation on
      either field. The runtime suite must stay green.

## Test review follow-ups, pass 2

- [x] Test companion reads of enabled fields in record-shaped value
      output. Task 020 changed the `:record` companion transform in
      `value_backend/prepare` to rewrite field references
      (`field-access/rewrite-class` with the storage map). The Contract
      (Value record output) says that companion references use the concrete
      descriptors. No test runs a companion that reads `p` or `s` of a
      record-shaped class: `record-form` methods in
      `test/runtime/value_type/deftype_star_record_test.clj` make no nested
      `fn`. If the record companion rewrite is removed, all tests still
      pass. Add a method that returns a nested `fn` that reads `p` and `s`
      to the non-nil record fixture (or one record test), and call the
      closure for value output (`value-installed*` and `vt/defvaluetype*`).
      Precedent: `restricted-companion-test`
      (`test/runtime/value_type/impl/loader_test.clj`) and
      `public-companion-behavior-test`
      (`test/runtime/value_type/non_nil_fields_test.clj`). The runtime
      suite must stay green.

## Test review follow-ups, pass 3

- [x] Test the native field with a wrong type through both literal paths
      (`test/runtime/value_type/deftype_star_record_test.clj`).
      `check-record-non-nil` skips `(f "x" "a")` for `:map-literal` and
      `:positional-literal`, so Acceptance 3 ("one native and one
      generated field through each construction path of the record
      shape") has no wrong-type evidence for `p` on these paths. A string
      literal for `p` works: on EA3 preview the cause chain of both paths
      has `ClassCastException`, in value and identity output. Remove the
      `when-not` guard. Update the slice 3 note in implementation.md
      ("reader form only"), because it is not correct. The runtime suite
      must stay green.
- [x] Test the D4 removed-field check with an enabled field
      (`test/runtime/value_type/impl/removed_field_test.clj`). Task 020
      changed the `:record` path of `value_backend/prepare`. With a
      non-empty storage, the owner transform composes `with-storage`, and
      the companion bytes go through `field-access/rewrite-class` before
      `check-removed-fields`. The Scope says that the record path keeps the
      D4 check. But each removed-field test uses only the untagged field
      `a`, so the storage is empty. Add one owner case (`hasheq` reads
      `__hash`) and one companion case (a closure reads `__hasheq`) with a
      `^String ^:non-nil a` field. Expect `:removed-field-access` with the
      same data. The runtime suite must stay green.

## Test review follow-ups, pass 5

- [x] Observe the enforcement and storage of `x` in the transfer and AOT
      evidence of enabled fields. Acceptance 6 says that AOT fixes
      representation and enforcement, and the value expansion carries
      `[:native]`. But `consume-enabled`
      (`test/compatibility/value_type/deftype_star_transfer_checks.clj`)
      and `facts` (`test/compatibility/value_type/deftype_star_aot_checks.clj`)
      give only `:value?`, `:stored`, `:hash-fields` and `:rejections`.
      Generated checks give the same rejections, so if a receiver or a
      saved class ignores the transported modes, or stores `x` as
      `Object`, all cells still pass. Add `:native?` (annotations of field
      `x` against `consumer-aot-checks/native-annotations`, precedent:
      `native-field?` in `field_transfer_checks.clj`) and `:type`
      (`(:type (checks/field-facts field))`) for the enabled fields.
      Expected: value output `:native? true`, identity output
      `:native? false`, `:type "java.lang.Long"` in both. Update
      `expected` and the expected cells of
      `deftype*-enabled-identity-transfer-test` and
      `deftype*-enabled-value-transfer-test`. The fallback and cross-JVM
      suites must stay green.

## Test review follow-ups, pass 6

- [x] Define the enabled-field facts and their expected value once.
      The fact body is in two places: `facts`
      (`test/compatibility/value_type/deftype_star_aot_checks.clj`) and
      `consume-enabled`
      (`test/compatibility/value_type/deftype_star_transfer_checks.clj`)
      both merge `checks/enforcement-facts` of `x`, `:stored` (`x` of the
      instance from 42) and `:rejections` (`checked-outcomes` with
      `(Object.)`). The expected value is in three places: `enabled` in
      `expected` (`deftype_star_aot_checks.clj`) and the identity and value
      cells of `deftype_star_transfer_test.clj` repeat
      `{:native? … :type "java.lang.Long" :stored 42 :rejections
      checked-rejections}`. Make one public function that takes field `x`
      and a function from a value of `x` to an instance, and one public
      function that takes `value?` and gives the expected map; make the
      AOT and transfer checks and tests use them. The fallback and
      cross-JVM suites must stay green.
- [x] Make `consumer-aot-checks/enforcement-facts` the one source of the
      `:native?` and `:type` facts. It repeats the `:type` expression of
      `field-facts` (`(.getName (.getType field))`); get `:type` from
      `field-facts`. `consume-supported`
      (`test/compatibility/value_type/field_transfer_checks.clj`) still
      builds the same two keys inline (`checks/native-field?` and
      `(:type (checks/field-facts field))`); merge
      `(checks/enforcement-facts field)` there. The cross-JVM suite must
      stay green.

## Test review follow-ups, pass 7

- [x] Make the non-nil rejection assertions show the actual outcome
      (`test/runtime/value_type/deftype_star_record_test.clj`).
      `check-record-non-nil` and `check-plain-non-nil` assert
      `(is (rejects? NullPointerException …))` and
      `(is (rejects? ClassCastException …))`. `rejects?` gives a boolean,
      so a failure shows only `false`: it does not show if the call
      returned or which exceptions the cause chain has. Per path, compute
      one map of the four outcomes (for example `{:nil-p :nil-s :wrong-p
      :wrong-s}`, each value the expected class if the cause chain has it,
      else `:returned` or the chain classes) and compare it with one
      expected map, as `checks/checked-outcomes` and
      `checks/checked-rejections` do in `test/compatibility`. Remove
      `rejects?` if no use stays. The runtime suite must stay green.

## Test review follow-ups, pass 8

- [x] Check the field storage types in `check-plain-non-nil`
      (`test/runtime/value_type/deftype_star_record_test.clj`), as
      `check-record-non-nil` does. The plain check asserts the
      representation, the enforcement and the construction outcomes, but
      not the types of `p` and `s`. If plain output stores `s` as
      `Object` with a generated check, all plain tests still pass.
      Extract the type assertion of `check-record-non-nil`
      (`[pt-tag 'java.lang.String]`) into one helper, next to
      `check-enforcement`, and call it from both checks. The runtime
      suite must stay green.
- [x] Rename `root-cause-classes` in
      `test/runtime/value_type/deftype_star_record_test.clj`
      (for example `cause-chain-classes`). It gives the classes of the
      full cause chain, not of the root cause, and `rejection` relies on
      the full chain.

## Test review follow-ups, pass 9

- [x] Make each tag and type case of `declaration-error-test`
      (`test/runtime/value_type/deftype_star_test.clj`) one assertion
      in its own context. Now each case expands the form two times, and
      `(testing "carries the field" …)` is a sibling of
      `(testing (name reason) …)`, so a failure of the field assertion
      does not name the reason. In `(testing (name reason) …)`, expand
      once and compare `(select-keys data [:value-type/error :class-name
      :field :reason])` with `(field-error f reason)`. The runtime suite
      must stay green.
- [x] Remove the copied code-element access in
      `test/runtime/value_type/impl/transform_test.clj`.
      `method-elements`, the `null-checks` function and the
      `instructions` function of `identity-record-storage-structure-test`
      each repeat `(.elementList ^CodeModel (.orElseThrow (.code m)))`.
      Extract one private `code-elements` (a `MethodModel` to its code
      elements), and make `method-elements` and both functions use it.
      The runtime suite must stay green.

## Test review follow-ups, pass 10

- [x] Make the order-test failures show the actual error. The adjacent-pair
      tests `declaration-order-test`
      (`test/runtime/value_type/deftype_star_test.clj`) and
      `receiving-star-order-test` (`value_receiving_test.clj`,
      `identity_receiving_test.clj`) compare only `(:reason …)` or
      `(:value-type/error …)`. If the form expands with no error, or fails
      with an error that has no `:reason` (for example
      `:invalid-field-enforcement` when the fixture modes do not match the
      enabled fields), the failure shows only `nil`. Compare
      `(select-keys data [:value-type/error :reason])` (or the full data)
      with one expected map per case, so that a failure shows the error
      that fired. The runtime and fallback suites must stay green.
- [x] Explain the `:constant` path of `non-nil-record-paths`
      (`test/runtime/value_type/deftype_star_record_test.clj`). It builds a
      valid record `(NnPt. 1) "ok"` with `__extmap {:p … :s …}`, so the
      map view has the tested values and the compiled constant calls
      `create` with them (the nil-in-map-view evidence). The code does not
      say this, and the valid `p` and `"ok"` look like the tested values.
      Add a short comment or docstring text. The runtime suite must stay
      green.

## Docs review follow-ups

- [x] State the exception wrapping for `defvaluetype*` record paths in
      `doc/usage.md` (`deftype*` forms section) and the `defvaluetype*`
      docstring (`src/value_type/core.clj`; then `bb api-docs`). Both say
      that record literals, compiled record constants and reflection
      "reject nil with `NullPointerException`", with no wrapping clause.
      The design Contract and the deftype* design Public contract say the
      compiler, class initialization and reflection can wrap them: a
      literal through `load-string` gives `CompilerException`, a compiled
      constant gives `ExceptionInInitializerError`, and reflection gives
      `InvocationTargetException`. No user doc names
      `ExceptionInInitializerError`. Add one sentence: these can be
      wrapped; find the exception in the cause chain. Keep error text
      unspecified.
- [x] State the record caller consequence in `doc/usage.md` (`deftype*`
      forms section). The deftype* design ("Qualities and risks — Risk")
      and the design Contract say that `create` from a map with no entry
      for an enabled field fails, and that an `assoc` that the caller
      implements with a constructor call (as `defrecord` does) rejects
      `nil` for an enabled field. Callers that build records from partial
      maps (closed-record) must supply each enabled field. The usage guide
      says only that `create` rejects nil. Add this to the record caller
      obligations or the non-nil paragraph.

## Docs review follow-ups, pass 2

- [x] Update "Verify `deftype*` forms" in `doc/development/runtime.md`.
      The task did not change it, so it omits the non-nil evidence. The
      EA3 preview run lists only `deftype-star-test`,
      `deftype-star-record-test` and `impl.removed-field-test`; add
      `value-type.value-receiving-test` (receiving order and errors) and
      `value-type.impl.transform-test` (record storage bytecode). The
      three-JVM `:fallback-test` loop lists only `runtime-test` and
      `deftype-star-transfer-test`; add `value-type.identity-receiving-test`
      and `value-type.identity-backend-test` (delegation rule). The
      coverage sentences must name non-nil fields (both shapes, both
      outputs), the receiving order, the D8 delegation rule and the
      `PlainNonNil`/`RecNonNil` AOT fixtures. The Limits paragraph names
      one feasibility gate; also name the non-nil gate
      (`spike/record-value-type/non-nil-gate.clj`, same README) and its
      `--add-exports` need. Run the changed commands once; `bb vale`
      must stay clean.

## Docs review follow-ups, pass 3

- [x] Limit the "evaluated apart from its macroexpansion" sentence to forms
      without enabled fields in `doc/usage.md` (`deftype*` forms section,
      ~line 457) and the `defvaluetype*` docstring (`src/value_type/core.clj`
      ~line 436; then `bb api-docs`). Both state with no condition that a
      value expansion evaluated apart from its macroexpansion fails with
      `:not-installed` or `:not-value-class`. With enabled fields the
      expansion is a receiving form: separate `eval` of a `macroexpand` or
      `macroexpand-1` result works (transfer tests), and only a full
      expansion outside compilation (`clojure.walk/macroexpand-all`) gives
      `:not-installed`, at macroexpansion. The deftype* design (Context and
      scope, ~line 78) already says "without enabled non-nil fields". Use
      the same condition, and refer to the transfer paragraph of
      `doc/usage.md` (~line 133) for enabled fields.
- [x] Remove the task reference from the Limits of
      `ramora/deftype-star-design/non-nil-gate.md` ("Task 020 slices 3 to 6
      cover the production path"). `ramora/INDEX.md` keeps task progress in
      Munera and Git, and experiment reports do not report delivery status.
      Name the evidence instead, for example: the runtime and fallback
      suites cover the production transform (as "Qualities and risks —
      Observed" in `ramora/deftype-star-design.md` says). `bb vale` must
      stay clean.

## Code-shaper review follow-ups

- [x] Remove the copied definition cells in `deftype*-policy-test`
      (`test/compatibility/value_type/runtime_test.clj`). The cells
      "plain", "record", "plain non-nil" and "record non-nil" each repeat
      the same scaffolding: `(fallback policy (merge {:named? true
      :new-class? true :value? available? :hash-fields … :stored 42} …))`,
      `:fallback? (not available?)` and `:warning context`; the
      `:hash-fields` expression `(if available? 0 2)` is in the two record
      cells. The differences are the label, the class name, the fields,
      the `non-nil-checks` option and the additional expected keys. Make
      one local function that takes these values (and gives `:hash-fields`
      from the shape), and make the four cells call it. The fallback suite
      must stay green.

## Implementation review follow-ups, pass 10

- [x] Remove the unused two-argument arity of `deftype*-form`
      (`test/compatibility/value_type/runtime_test.clj`). After the
      code-shaper follow-up, the one caller (`definition` in
      `deftype*-policy-test`) always passes `checks`, so the arity that
      supplies `{}` is dead code. Keep one arity `[simple-name fields
      checks]`. The fallback suite must stay green. When the step is done,
      the `munera/plan.md` entry ("Next: close the task") is correct again.
