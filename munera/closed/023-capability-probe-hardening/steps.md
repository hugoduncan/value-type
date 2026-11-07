# Steps

## Slice 1: format row and probe builder

- [x] Create `src/value_type/impl/jvm_format.clj` with `row`: no
      arguments, returns the default row map `{:major :minor
      :identity-class-flag :strict-field-flag :constructor-order}`;
      `:major` from `ClassFile/latestMajorVersion` read once at namespace
      load, `:minor` = `ClassFile/PREVIEW_MINOR_VERSION`,
      `:identity-class-flag` = `0x20`, `:strict-field-flag` = `0x800`,
      `:constructor-order` = `:fields-before-super`. No value-type
      requires.
- [x] Create `src/value_type/impl/probe_builder.clj` with
      `(probe-bytes owner descriptor restricted?)` on
      `java.lang.classfile`: public final class, superclass `Object`,
      version from the row; one public final field `value` of
      `descriptor` with `:strict-field-flag`; `restricted?` adds
      `NullRestricted` with `RuntimeVisibleAnnotationsAttribute`; one
      public constructor (fields before super; `lload` for `J`, `aload`
      otherwise) that asserts `:constructor-order` and throws
      `IllegalStateException` on any other value.
- [x] Add `value-type.impl.jvm-format-test`
      (`test/runtime/value_type/impl/jvm_format_test.clj`): `row` has all
      keys with the expected values.
- [x] Add `value-type.impl.probe-builder-test`
      (`test/runtime/value_type/impl/probe_builder_test.clj`): define the
      `J` non-restricted probe bytes on EA3; check the class loads, the
      field and constructor exist, and the value predicate is true.
- [x] In `value-type.impl.probe-builder-test`, add a build-only test of
      the malformed `I` probe (the EA3 recheck of the stack-map risk):
      `(probe-bytes owner "I" false)` (an `aload` of an `int` slot)
      returns bytes and does not throw. The test does not define the
      class; the JVM `VerifyError` row stays in `malformed-probe-test`
      (slice 2). If the build throws on EA3, add
      `ClassFile$StackMapsOption/DROP_STACK_MAPS` to the builder in this
      slice.
- [x] In `value-type.impl.probe-builder-test`, add a committed test of
      the `NullRestricted` attribute: parse
      `(probe-bytes owner "Lvalue_type/probe/Leaf;" true)` with
      `(.parse (ClassFile/of) bytes)`; the field `value` has
      `{["Ljdk/internal/vm/annotation/NullRestricted;" {}] 1}` from
      `(annotation-support/annotation-contents field
      RuntimeVisibleAnnotationsAttribute)`, and none from
      `RuntimeInvisibleAnnotationsAttribute`; the bytes with
      `restricted?` false have no `NullRestricted`. Do not read the
      annotation by reflection (`jdk.internal.vm.annotation` is not
      exported). The existing `native-probe-enforcement-test` observes
      nil rejection.
- [x] Run the runtime suite (plan.md Verification); run
      `bb clj-fmt:check` and `bb clj-kondo:lint`.

## Slice 2: value probe classification

- [x] In `impl.capability`, replace the eager `clojure.asm` probe: keep
      API checks 1–2 unchanged; resolve
      `impl.probe-builder/probe-bytes` with `requiring-resolve` only
      after the API checks pass; define the `J` non-restricted probe;
      `UnsupportedClassVersionError` still gives
      `:unsupported-class-version`.
- [x] Add public `passes-self-check?`: takes a Class with a public
      `long` field `value` and a public `(long)` constructor; makes
      instances 42, 43, 42; checks own values, 42/43 not `identical?`,
      the two 42s `identical?`, none `identical?` to nil; no value
      predicate read.
- [x] Add public `classify`: value predicate false →
      `{:available? false :reason :probe-not-value-class :detail
      "The JVM defined the probe class, but the probe class is not a
      value class."}`; self-check false → `:failed-self-check` with its
      fixed `:detail`; else `{:available? true :probe-class c}`.
- [x] In `impl.field-capability/probe`, resolve
      `impl.probe-builder/probe-bytes` with `requiring-resolve` (`probe`
      runs only when the value-class result is available; the namespace
      has no API checks of its own); call it without major; remove the
      `capability/latest-major-version` and `capability/probe-bytes` uses.
- [x] Update
      `value-type.impl.field-capability-test/native-probe-enforcement-test`
      (`test/runtime`) to use `probe-builder/probe-bytes` (no major);
      assertions unchanged.
- [x] Update `value-type.field-capability-test/malformed-probe-test`
      (`test/cross-jvm`): the cell form requires
      `value-type.impl.probe-builder` and builds probes with
      `probe-builder/probe-bytes` (no major). All four expected errors
      stay in this slice (slice 3 changes the nullable-holder row).
      Slice 1 checked that the builder builds the `I` probe on EA3.
- [x] Remove `probe-bytes` and `latest-major-version` from
      `impl.capability`; remove the `clojure.asm` import. `grep -rn
      "capability/probe-bytes\|latest-major-version" src test` gives no
      result.
- [x] Convert `impl.transform` to read `:identity-class-flag`,
      `:strict-field-flag`, `:major` and `:minor` from
      `(jvm-format/row)`; remove `ACC_IDENTITY`, `ACC_STRICT_INIT`,
      `latestMajorVersion` and `PREVIEW_MINOR_VERSION` constants; the
      constructor emitter asserts `:constructor-order` and throws
      `IllegalStateException` on any other value. In the
      `value-class-transform` and `->value-class` docstrings, replace
      `ACC_IDENTITY` with `:identity-class-flag` and `ACC_STRICT_INIT`
      with `:strict-field-flag` (the row keys).
- [x] Update `test/runtime/value_type/impl/transform_test.clj` and
      `loader_test.clj`: require `value-type.impl.jvm-format`; replace
      every `@#'transform/ACC_IDENTITY` and `@#'transform/ACC_STRICT_INIT`
      read (including `build-class` line ~118, record builder line ~1042,
      `super-before-write-class` line ~229) with row reads;
      `grep -n "transform/ACC_"` gives no result; assertions unchanged.
- [x] Create `value-type.probe-test-support`
      (`test/runtime/value_type/probe_test_support.clj`). It defines,
      once at namespace load: `IdentityLong` (`deftype` with a
      `^long value` field); `builder-probe-class` (the builder `J`
      non-restricted probe, defined with a `DynamicClassLoader` and a
      unique name); `constant-value-class` (made with
      `java.lang.classfile` and `jvm-format/row`: a value class with a
      public final `long` field `value` and a public `(long)` constructor
      that writes 0).
- [x] Add `value-type.impl.capability-test`
      (`test/runtime/value_type/impl/capability_test.clj`), with the
      fixtures of `value-type.probe-test-support`:
      `passes-self-check?` true for `builder-probe-class` and false for
      `IdentityLong`; `classify` of `IdentityLong` →
      `:probe-not-value-class`; `classify` of `constant-value-class` →
      `:failed-self-check`.
- [x] Update `value-type.runtime-test/capability-test`
      (`test/compatibility`) to compare the available result without
      `:probe-class`.
- [x] Verify acceptance 1 by grep:
      `grep -niE '0x20\b|0x800|0xffff|ACC_IDENTITY|ACC_STRICT|latestMajorVersion|PREVIEW_MINOR_VERSION|fields-before-super' src/value_type/impl/{capability,field_capability,probe_builder,transform}.clj`.
      The permitted matches are only `:fields-before-super` in the
      `:constructor-order` assertion of the `impl.probe-builder`
      constructor emitter and of `impl.transform/constructor-code`. Any
      other match is a failure. `-i` also finds `0xFFFF` (the design
      form).
- [x] Run the runtime, fallback (all three configurations; includes
      fallback-java8), cross-JVM and build (includes the isolated
      source-JAR `isolated-consumer-test`) suites (plan.md
      Verification); `bb clj-fmt:check`, `bb clj-kondo:lint`.

## Slice 3: field probe

- [x] Confirm that `detect` runs the probe only when the value-class
      result is available, else returns the value-class result (with its
      `:reason` and `:detail`). The current `detect` does this; no change
      is expected.
- [x] In `verify-enforcement`, keep the readback checks and the existing
      NPE/rethrow mapping; change the accept-nil branch from
      `IllegalStateException` to `{:available? false :reason
      :native-restriction-ignored :detail "The JVM accepted nil in a
      NullRestricted probe field."}`.
- [x] Update `value-type.field-capability-test/malformed-probe-test`
      (`test/cross-jvm`): the nullable-holder row expects the
      `:native-restriction-ignored` result; the other three rows keep
      their expected errors. Row 1 uses `cell-registry/failed-call`,
      which gives `{:status :unexpected-success}` when the call returns;
      observe the returned result with a direct call (or `outcome` with
      a success function).
- [x] Add to `value-type.impl.field-capability-test` (`test/runtime`):
      `verify-enforcement` (via the var) of a builder leaf and a builder
      holder with `restricted?` false → `:native-restriction-ignored`.
- [x] Run the runtime, fallback (all three configurations), cross-JVM
      and build suites (plan.md Verification); lint and format checks.

## Slice 4: array round trip

- [x] In `impl.array/discover`: unavailable value-class result passes
      through unchanged; available result without `:probe-class` throws
      `IllegalStateException`; after the API, signature and access
      checks pass, call `(passes-round-trip? allocator probe-class)`.
- [x] Add public `passes-round-trip?`: takes the allocator Method and a
      Class; makes `a` (42) and `b` (43) with the `(long)` constructor;
      allocates a null-restricted array of length 2 with initial value
      `a`; writes `b` at index 1 with `java.lang.reflect.Array`; reads
      both; true only when element 0 is `identical?` to `a` with value
      42 and element 1 is `identical?` to `b` with value 43; unwraps
      `InvocationTargetException`; other exceptions propagate.
- [x] False round trip → discovery returns `{:available? false :reason
      :failed-self-check :detail "The value-array round trip failed the
      semantic self-check."}`; true → the available allocation result
      (unchanged shape).
- [x] `require-allocation`: for `:failed-self-check`, the message is
      `Value-array allocation is unavailable. ` followed by `:detail`;
      other reasons keep the current message.
- [x] Add `value-type.impl.array-test`
      (`test/runtime/value_type/impl/array_test.clj`; runs under the
      `jdk.internal.value` export), with the fixtures of
      `value-type.probe-test-support`: `passes-round-trip?` true for
      `builder-probe-class`, false for `constant-value-class`, with the
      allocator from `(:allocator (impl.array/allocation-capability))`.
- [x] Run the runtime (with `--enable-preview` and the export),
      fallback (all three configurations), cross-JVM (includes
      `value-type.capability-matrix-test`, the `:array-allocation` rows)
      and build suites (plan.md Verification); lint and format checks.

## Slice 5: downstream contract

- [x] Add the new `:reason` values to the `report` docstring in
      `src/value_type/capability.clj`; confirm `report` drops
      `:probe-class` (`select-keys`). Regenerate `doc/API.md` with
      `bb api-docs` (it holds the `report` docstring).
- [x] Update `value-type.field-transfer-checks/consume`
      (`test/compatibility`) to return as `:capability` only
      `(select-keys result [:available? :reason])` (design acceptance 5).
      This keeps the cell value plain data; a printed Class reads back as
      a symbol, so the reader does not fail without it.
- [x] Confirm the fallback warning, the policy `error` message,
      `core/require-value-capability!` and the field guard message use
      `:detail` for the new reasons.
- [x] Run the full test matrix (plan.md Verification): the runtime,
      fallback (all three configurations), cross-JVM (includes
      `value-type.capability-matrix-test`), build and tooling suites; all
      rows unchanged except the acceptance 5 expected changes.
- [x] Run `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and
      `bb api-docs --check`.

## Slice 6: docs and design artifacts

- [x] Update the usage guide capability-report section and
      `CHANGELOG.md`.
- [x] Update the embodiment gap table: remove D12, D15, D16 and the
      non-AOT part of D17.
- [x] Update `ramora/defvaluetype-design/embodiment/runtime.md`:
      components rows for `impl.jvm-format` and `impl.probe-builder`;
      the `impl.capability` responsibility; the Architecture dependency
      direction; the Boundaries class-file-format text; the Invariants
      row "Fallback needs no optional JDK APIs" (lazy builder
      resolution); the State text (cached result holds the probe Class
      and its class loader).
- [x] Update
      `ramora/defvaluetype-design/embodiment/runtime/fallback-interfaces.md`:
      the cached value-class map has `:probe-class` when available.
- [x] Update `ramora/value-type-array-design/embodiment.md`: discovery
      invokes the allocator once in the round trip with the probe Class;
      a failed round trip gives `:failed-self-check`; the round trip is
      the D17 semantic check; no class-format detector as array-support
      proof (array D3 boundary).
- [x] Update `mementum/state.md`. Do not edit
      `mementum/knowledge/java-lang-classfile.md`: `implementation.md`
      records that it is stale, for `extract-task-knowledge`.
- [x] Final full verification: the full test matrix (plan.md
      Verification), `bb clj-fmt:check`,
      `bb clj-kondo:lint`, `bb vale`, `bb api-docs --check`.

## Implementation review

- [x] Set `impl.capability/self-check-detail` to the design text `The
      value-class probe failed the semantic self-check.` (design.md
      Contract table). The current text differs. In
      `value-type.impl.capability-test/classify-test`, compare the full
      `:failed-self-check` map of `constant-value-class`, not only
      `string?` of `:detail`.

## Implementation review pass 2

- [x] Correct the `doc/usage.md` capability-report paragraph on the new
      reasons. Today it says "definitions use fallback, as for other
      reasons" for all three reasons. That is false for two of them:
      `:native-restriction-ignored` selects generated enforcement (only
      fixed `:native` modes fail), and array `:failed-self-check` has no
      fallback (`make-array` and `into-array` throw
      `:array-unavailable`). Also, the field probe checks nil rejection, not
      a semantic self-check. State the effect per capability key.
- [x] Add a runtime test for the `impl.array/discover` guard (design
      Contract, internal results): with `capability/capability` replaced by
      `(constantly {:available? true})`, a call of `discover` through the
      var throws `IllegalStateException`. No test covers this branch now.

## Test review

- [x] Add a runtime test in `value-type.impl.array-test` for the
      discovery mapping of a false round trip (design Classification,
      "False: discovery returns `:failed-self-check`"). With
      `capability/capability` replaced by `(constantly {:available? true
      :probe-class support/constant-value-class})`, a call of
      `#'array/discover` returns `{:available? false :reason
      :failed-self-check :detail "The value-array round trip failed the
      semantic self-check."}`. Today only `passes-round-trip?` false is
      tested; the `discover` branch that maps it has no test.
- [x] Add runtime tests for the `:constructor-order` assertion of both
      emitters (design Format row: "Each throws `IllegalStateException`
      when `:constructor-order` has a different value"). With
      `jvm-format/row` replaced by the default row with a different
      `:constructor-order` (inject state with `with-redefs`), a call of
      `probe-builder/probe-bytes` throws `IllegalStateException`
      (`value-type.impl.probe-builder-test`), and a transform of a
      class with a constructor through `impl.transform` throws
      `IllegalStateException` (`value-type.impl.transform-test`). No test
      covers these branches now.

## Test review pass 2

- [x] Add a runtime test in `value-type.impl.array-test` for the D12
      round-trip exception path (design Classification: "An exception in
      the self-check or in the round trip ... propagates"; "The round trip
      removes `InvocationTargetException`"). No test covers it now. Use the
      real `IdentityLong` class of `value-type.probe-test-support`: (1)
      `passes-round-trip?` with the discovery allocator and `IdentityLong`
      throws `IllegalArgumentException`, not `InvocationTargetException`;
      (2) with `capability/capability` replaced by `(constantly
      {:available? true :probe-class IdentityLong})`, a call of
      `#'array/discover` throws `IllegalArgumentException` and does not
      return a classified absence (the round trip is outside the API
      `try`). Checked on EA3: both give `IllegalArgumentException`
      "Element class is not a value class".

## Test review pass 4

- [x] In `value-type.capability-test/report-translation-test`
      (`test/runtime/value_type/capability_test.clj`), the `:no-export`
      state replaces `capability/capability` with `{:available? true}`.
      The real available value-class result now always has `:probe-class`
      (design Contract, internal results), so the fixture shape is stale
      and no unit test shows that `report` drops `:probe-class` (D14).
      Use `{:available? true :probe-class String}` (any Class; `report`
      does not read it). The expected `{:value-class {:available? true}}`
      does not change. Today only the cross-JVM matrix covers the drop.

## Test review pass 5

- [x] Add a runtime test of the `:detail` inheritance (design Contract:
      "The field and array results inherit the value-class `:detail`").
      With `capability/capability` replaced by `(constantly {:available?
      false :reason :failed-self-check :detail "The value-class probe
      failed the semantic self-check."})`, `(field-capability/detect)`
      (`value-type.impl.field-capability-test`) and `(#'array/discover)`
      (`value-type.impl.array-test`) each return that map unchanged.
      Today `controlled-value-capability-test` covers only `:reason`
      through `report`, which drops `:detail`; the field guard message
      reads the inherited `:detail`.
- [x] In `value-type.impl.probe-builder-test/long-probe-test`, the
      testing label says "public final value class", but no assertion
      checks public (design Builder: class flags public and final). Add
      `(is (Modifier/isPublic (.getModifiers probe)))`, or change the
      label to match the assertions.

## Test review pass 6

- [x] Make the `IllegalStateException` tests fail only for their contract.
      `constructor-order-assertion-test` (`value-type.impl.probe-builder-test`
      and `value-type.impl.transform-test`) and `discover-guard-test`
      (`value-type.impl.array-test`) use a bare `thrown?`. An unrelated
      `IllegalStateException` (for example from the transform) also passes.
      Use `thrown-with-msg?` with `#"Unsupported value-constructor order"`
      and `#"no :probe-class"` (the messages of `impl.probe-builder`,
      `impl.transform/constructor-code` and `impl.array/discover`).
- [x] In `value-type.field-capability-test/malformed-probe-test`
      (`test/cross-jvm`), the local `outcome` is bound to
      `cell-registry/failed-call`, but row 1 calls
      `value-type.cell-registry/outcome`. The same name has two meanings in
      one form. Rename the local to `failed`, and bind the
      `(fn [e] (symbol (.getName (class e))))` failure function once, so
      that all four rows use it. Expected values do not change.

## Test review pass 7

- [x] In `value-type.impl.probe-builder-test`, move the field flag
      assertion out of `null-restricted-attribute-test`. Today its testing
      block "the field has the strict flag" (`0x811`) checks the builder
      field contract (design Builder: public final field with
      `:strict-field-flag`, for every probe), not `NullRestricted`, and only
      on the restricted field. A failure then shows under the wrong test
      name. Assert `0x811` on the `J` probe field in `long-probe-test`
      (parse the same bytes with `value-field`), and remove the block from
      `null-restricted-attribute-test`.

## Test review pass 8

- [x] In `value-type.impl.capability-test/detect-test`, the name says
      `detect`, but the test calls the cached `capability/capability`.
      The cache is the concern of `value-type.runtime-test/capability-test`
      (`:cached`). Call `(capability/detect)`, so that the test fails only
      for the `detect` contract. Also assert the design shape of the
      available result (design Contract, internal results: `{:available?
      true :probe-class c}`): the key set is `#{:available? :probe-class}`,
      and `c` is a value class. Today an extra key (for example a leaked
      `:detail`) passes.

## Docs review

- [x] Correct the `CHANGELOG.md` entry of this task. "Capability probes run
      a semantic self-check" is false for the field probe: it checks nil
      rejection, not a semantic self-check (the same fix as the
      `doc/usage.md` paragraph in implementation review pass 2). "The probe
      no longer loads `clojure.asm` at namespace load" implies a later load;
      the probes do not use `clojure.asm` (they use `java.lang.classfile`).
      Also state the user-visible fix: a probe class that is not a value
      class, and a JVM that accepts nil in a `NullRestricted` field, now
      give classified absences, not invariant errors.
- [x] In `doc/usage.md` (Capability report), "Each probe checks real
      instances before it reports a capability" is false for
      `:array-inspection`: `impl.array/discover-inspection` checks only
      APIs and access. Name the probes that check instances (value class,
      native field, array allocation), or remove the sentence.

## Docs review pass 2

- [x] In `mementum/state.md` (Decided design changes), "the probes run
      semantic self-checks (classified absences `:probe-not-value-class`,
      `:failed-self-check`, `:native-restriction-ignored`)" is false for
      the field probe. It checks nil rejection, not a semantic self-check
      (the same fix as `CHANGELOG.md` in the docs review). State that the
      value-class probe and the value-array round trip run semantic
      self-checks, and that the field probe checks nil rejection
      (`:native-restriction-ignored`).

## Docs review pass 3

- [x] In `ramora/defvaluetype-design/decisions/validation-and-errors.md`
      (D9, the paragraph "For ordinary definitions, `value-type.impl.capability`
      checks ..."), "Only missing required APIs or an unsupported probe class
      version select fallback. Other failures propagate." is now false. The
      classified absences `:probe-not-value-class` and `:failed-self-check`
      (D12) also select fallback, and the probe now also runs the semantic
      self-check (D17) with the probe builder (D16). It also disagrees with
      `fallback/selected-detector.md` and `fallback/capability-boundary.md`.
      State the D12 classified absences, or refer to the
      [selected detector](../fallback/selected-detector.md) for the list.

## Code-shaper review

- [x] Give the `(long)` probe-instance construction one owner, and remove
      `InvocationTargetException` in all of it. Today three copies get the
      `(long)` constructor and call `.newInstance`:
      `impl.capability/passes-self-check?`, `impl.array/passes-round-trip?`
      and `impl.field-capability/verify-enforcement` (the leaf value). In
      `passes-round-trip?`, a constructor exception propagates wrapped in
      `InvocationTargetException`. Only the allocator call goes through
      `impl.array/invoke`. That disagrees with design Classification ("The
      round trip removes `InvocationTargetException` as `impl.array/invoke`
      does"). Add one public function in `impl.capability` (for example
      `probe-instance-fn`: Class → fn of a `long` that unwraps
      `InvocationTargetException`); `impl.array` and `impl.field-capability`
      already require `impl.capability`. Keep the nil construction in
      `verify-enforcement` as it is (it reads the
      `InvocationTargetException` cause). Add a runtime test: a round trip
      with a Class whose `(long)` constructor throws gives the cause, not
      `InvocationTargetException`.
- [x] Use one idiom for the fixed `:detail` texts of the design Contract
      table. Today `self-check-detail` (`impl.capability`) and
      `round-trip-detail` (`impl.array`) are private vars, each used once,
      but the `:probe-not-value-class` text (`impl.capability/classify`) and
      the `:native-restriction-ignored` text
      (`impl.field-capability/verify-enforcement`) are inline literals.
      Inline all four, or make all four private vars.
- [x] In `impl.array`, import `java.lang.reflect.Array` and `Modifier` with
      the other `java.lang.reflect` classes. Today `passes-round-trip?` and
      `inspection-method` write `java.lang.reflect.Array/set`,
      `java.lang.reflect.Array/get` and
      `java.lang.reflect.Modifier/isStatic` in full.

## Code-shaper review pass 2

- [x] Give the probe class name and definition one owner. Today three
      copies make a unique `value_type/probe/...` name from
      `UUID/randomUUID` and define the bytes in a `DynamicClassLoader`:
      `impl.capability/probe`, `impl.field-capability/probe` and
      `value-type.probe-test-support` (`unique-name`, `define`). They use
      two idioms for the binary name (`impl.capability` writes the dotted
      name again; the others `.replace "/" "."`), and only
      `impl.capability` states the "never reuse a name" invariant. Add
      both functions to `impl.probe-builder` (for example
      `unique-probe-name` of a prefix, and `define-probe` of a loader, an
      internal name and the bytes). Resolve them with `requiring-resolve`
      as `probe-bytes` is resolved now (the D12 lazy-load boundary does not
      change). The field probe passes one loader for leaf and holder.
      `UnsupportedClassVersionError` still gives
      `:unsupported-class-version`. Use them in the test support.
- [x] Add `(set! *warn-on-reflection* true)` to `impl.array`. The other
      probe namespaces (`impl.capability`, `impl.field-capability`,
      `impl.probe-builder`, `impl.jvm-format`) set it; this task added
      interop to `impl.array` (`passes-round-trip?`). Checked: the
      namespace loads with no reflection warning today.

## Code-shaper review pass 3

- [x] Give the `:constructor-order` assertion one owner. Today two copies
      check `:fields-before-super` and throw `IllegalStateException`
      "Unsupported value-constructor order: ...":
      `impl.probe-builder/assert-constructor-order!` and an inline copy in
      `impl.transform/constructor-code`. `impl.jvm-format` states that it
      owns the value-constructor order rule (D15). Add one public function
      to `impl.jvm-format` (for example `require-constructor-order!` of a
      row) and call it from both emitters. The message does not change, so
      `constructor-order-assertion-test` stays as it is. The acceptance 1
      grep then has no `:fields-before-super` match in the four
      namespaces.
- [x] In `impl.array/discover`, move the allocator API `try` (the
      `getMethod`, `unreflect` and the three classified catches) to a
      private function (for example `allocation-methods`). Then
      `discover` is only flow control: pass-through, guard, API result,
      round trip. The D12 rule "a round-trip exception propagates" then
      follows from the structure, and the comment "Outside the try" is not
      necessary.
- [x] In `value-type.probe-test-support`, `constant-bytes` and
      `throwing-bytes` repeat the class skeleton (public final class,
      superclass `Object`, public final `long` field `value`, public
      `(long)` constructor). Add one private builder (for example
      `long-class-bytes` of owner, value-class?, and a constructor-body
      fn of the `CodeBuilder`) and use it for both. Expected classes do
      not change: the constant class keeps the row version and the
      `:strict-field-flag`; the throwing class stays an identity class.

## Code-shaper review pass 4

- [x] Use the probe name and definition owner
      (`probe-builder/unique-probe-name`, `probe-builder/define-probe`) in
      the remaining probe tests. Code-shaper pass 2 changed only
      `value-type.probe-test-support`. Today three runtime tests write fixed
      names and call `.defineClass` directly, with two idioms for the binary
      name: `native-probe-enforcement-test` (dotted name, then
      `.replace "." "/"`; the holder descriptor repeats the leaf name) and
      `native-restriction-ignored-test` (internal name, then
      `.replace "/" "."`) in `value-type.impl.field-capability-test`, and
      `long-probe-test` in `value-type.impl.probe-builder-test` (dotted
      name). Fixed names also depend on the `DynamicClassLoader` cache
      (implementation.md slice 3 note). Use `unique-probe-name` and
      `define-probe`, and make the holder descriptor from the leaf name
      (`(str "L" leaf-name ";")`), as `impl.field-capability/probe` does.
      In the cross-JVM `malformed-probe-test` cell form, use `define-probe`
      in place of the local `.defineClass` call. Assertions do not change.
- [x] In `impl.field-capability/verify-enforcement`, bind the holder field
      `value` once. Today it calls `(.getField holder "value")` two times
      (type check and readback) and makes the leaf field in the readback
      check inline. Bind `holder-field` and `leaf-field` in the `let`, so the
      readback invariant reads as one expression of named parts. Behavior
      and the exception mapping do not change.

## Code-shaper review pass 5

- [x] Give the probe definition sequence one entry point for the D12 lazy
      boundary. Today `impl.capability/probe` and
      `impl.field-capability/probe` each resolve three builder vars with
      `requiring-resolve` (`probe-bytes`, `unique-probe-name`,
      `define-probe`), and each repeats the sequence name, bytes, define
      (three times in `src`). Add one public function to
      `impl.probe-builder` (for example `define-probe-class` of a loader, a
      prefix, a descriptor and `restricted?`; it returns the Class). Each
      caller resolves only that var. The field probe makes the holder
      descriptor from the leaf Class (`(.descriptorString leaf)`) and passes
      one loader for leaf and holder. `UnsupportedClassVersionError` still
      gives `:unsupported-class-version`. Keep `probe-bytes`,
      `unique-probe-name` and `define-probe` public: tests parse bytes and
      build malformed probes. Behavior does not change.
      Done: `probe-builder/define-probe-class`; the holder descriptor
      comes from the leaf name, not `.descriptorString` (Java 8).

## Code-shaper review pass 6

- [x] Use `probe-builder/define-probe-class` in the tests that need only
      the probe Class. Code-shaper pass 5 made it the owner of the
      sequence name, bytes, define, but two tests still repeat it with a
      local helper: `value-type.probe-test-support/builder-probe-class`
      (`unique-probe-name`, `probe-bytes`, then `define`) and
      `value-type.impl.field-capability-test/native-restriction-ignored-test`
      (local `define` of `define-probe` and `probe-bytes`). Make
      `builder-probe-class` with `(probe-builder/define-probe-class
      (DynamicClassLoader.) "SupportProbe" "J" false)`. In
      `native-restriction-ignored-test`, define leaf and holder with
      `define-probe-class` and one loader; make the holder descriptor from
      the leaf Class, as `impl.field-capability/probe` does. Keep
      `probe-bytes`, `unique-probe-name` and `define-probe` where a test
      reads the bytes (`native-probe-enforcement-test`, `long-probe-test`)
      or builds a malformed probe (cross-JVM `malformed-probe-test`).
      Assertions do not change.

## Code-shaper review pass 7

- [x] Give the probe field descriptor one owner in `impl.probe-builder`.
      Today three idioms make a descriptor from a Class:
      `impl.field-capability/probe` and
      `value-type.impl.field-capability-test/native-restriction-ignored-test`
      write `(str "L" (.replace (.getName leaf) "." "/") ";")`, and
      `value-type.impl.probe-builder-test` uses `(.descriptorString leaf)`.
      The `src` copy and its comment exist only because
      `impl.field-capability` compiles on Java 8. `impl.probe-builder`
      loads only behind the D12 checks, so `.descriptorString` is safe
      there. Make `define-probe-class` take the field type as a Class
      (`Long/TYPE` or the leaf Class) and get the descriptor with
      `.descriptorString`. Callers pass `Long/TYPE` (gives `"J"`) or the
      leaf; remove the comment and the string form. Keep `probe-bytes`
      on descriptor strings (tests build the malformed probes with it).
      Behavior and assertions do not change.
      Done: `define-probe-class` takes a field-type Class; callers pass
      `Long/TYPE` or the leaf Class.
