# Design steps

- [x] Architecture: state the load position of the format namespace and the
  probe builder relative to the D12 boundary. The D15 default row uses
  `ClassFile.latestMajorVersion`. Choose one rule: the format namespace loads
  only behind the boundary, and only the probe builder and the transform
  require it (`impl.capability` and `impl.field-capability` reach it only
  through the lazily resolved builder); or the format namespace is
  baseline-safe (no `java.lang.classfile` import at load). Give the
  dependency edges: builder → format, transform → format, capability
  namespaces → builder only through lazy resolution, and `impl.array` reads
  the probe Class from the value-class result.
  - Source: D12 boundary, D15, D16; `embodiment/runtime.md` dependency
    direction.
- [x] Acceptance 6: add `ramora/defvaluetype-design/embodiment/runtime.md`
  (components table rows for the format namespace and the probe builder,
  changed `impl.capability` responsibility, and the dependency direction).
  The ramora rules keep implementation structure in embodiment sections; the
  current list removes only gap-table rows.
- [x] Ambiguity: name the format namespace and the probe builder namespace,
  and define the row. Give the row keys and value forms (major as a value or
  a function, minor, class flag to clear, field flag, constructor order).
  Define the form of the "value-constructor order rule" and how the transform
  and the builder use it: data that selects emission, or a value that each
  emitter asserts. State the row accessor: a lookup by feature release or
  build that returns the default row, or only the default row until a second
  row exists. State if `ClassFile/PREVIEW_MINOR_VERSION` in `impl.transform`
  counts as a version-rule constant for acceptance 1.
- [x] Ambiguity: `:native-non-nil-field` with `:probe-not-value-class` and
  `:failed-self-check`. State if these come only from the value-class result
  (the field probe returns its prerequisites), or if the field probe also
  classifies its own leaf and holder classes. Give the same answer for
  `:array-allocation` and `:probe-not-value-class`.
- [x] Ambiguity: "the internal value-class result can carry the probe Class".
  Make it normative. Give the key, state that it exists only when
  `:available?` is true, and state what array discovery does when the key is
  absent (for example, a test replaces `capability` with `with-redefs`).
- [x] Ambiguity: the array round trip. Give the array length, the written
  value (a new probe instance, for example 43), the checks on read-back
  (`identical?`, field value), and the order relative to the existing
  API, signature and access checks. State if an exception during allocation,
  write or read is `:failed-self-check` or propagates (D12). State the
  `require-allocation` message for `:failed-self-check`; the current message
  names only the JVM, preview and export.
- [x] Ambiguity: `:detail` of the new unavailable results. No Throwable
  exists for `:probe-not-value-class`, `:failed-self-check` or
  `:native-restriction-ignored`. Give the `:detail` text (or state that it is
  absent). The fallback warning, the policy `error` message and the field
  guard message use `:detail`.
- [x] Ambiguity: the classification interface and its tests. Give the check
  order (API checks, version, value predicate, self-check) and the inputs of
  the self-check (a Class with a public `long` field `value` and a `(long)`
  constructor?). Acceptance 3 says "the right failure" for an identity class:
  state the expected result of the self-check function on that class, apart
  from `:probe-not-value-class` from the full classification. For acceptance
  4, state which real classes give `:failed-self-check` and
  `:native-restriction-ignored` (for example, an identity holder that accepts
  nil), and on which JVMs these tests run.
- [x] Inconsistency: acceptance 5 says all cross-JVM and runtime suites pass
  unchanged, but the new contract changes existing tests.
  `test/cross-jvm/value_type/field_capability_test.clj` `malformed-probe-test`
  expects `IllegalStateException` from `verify-enforcement` with a nullable
  holder; the design classifies this as `:native-restriction-ignored`. That
  test and `test/runtime/value_type/impl/field_capability_test.clj` call
  `capability/probe-bytes` with a major version (removed by D15/D16).
  `dev/spike/fallback/format-check.clj` also uses it. List these expected
  changes in acceptance 5 (or in the contract), and keep "unchanged" for the
  other rows.
- [x] Inconsistency: Concepts define a format row as the flag values, the
  version rule and the order rule for one release. D15 says other rows record
  only known differences from the default. Align the concept with D15 (a
  non-default row overrides the default row), so the row accessor in the
  format namespace has one meaning.
- [x] Architecture (pass 2): acceptance 6 does not update all owners of the
  changed runtime architecture. Add to the acceptance 6 docs:
  `embodiment/runtime.md` Invariants row "Fallback needs no optional JDK
  APIs" (enforcement point: also the lazy builder resolution in
  `impl.capability` and `impl.field-capability`); `embodiment/runtime.md`
  State (the cached value-class result holds the probe Class, and thus its
  loader, for the library runtime); `embodiment/runtime/fallback-interfaces.md`
  (the cached value-class map has `:probe-class` when available).
  - Source: ramora keeps implementation structure in embodiment sections;
    D13 state owners; D14 (`report` drops `:probe-class`).
- [x] Architecture (pass 2): D17 changes array allocation discovery, but
  acceptance 6 does not update the array design that owns `impl.array`.
  Add `ramora/value-type-array-design/embodiment.md` to acceptance 6: discovery
  now invokes the allocator once in a round trip with the value probe Class
  from the value-class result, and gives `:failed-self-check`. State in that
  text that the round trip is the D17 semantic check, not use of the
  class-format detector as proof of array support (array D3 boundary).
  - Source: array D3 (adapter boundary, "do not reuse the class-format
    detector as proof of array support"); array embodiment ("checks access
    with a method handle"); defvaluetype D17.
- [x] Ambiguity (pass 2): the test entry points of acceptance 3 and 4.
  Acceptance 3 and 4 call `classify`, `passes-self-check?` and "the array
  round trip" on given classes, but the design does not name the round trip
  as a unit. Give its namespace, name, inputs (the probe Class; the allocator
  from discovery or a fresh lookup) and result (a boolean, or the allocation
  result map with `:failed-self-check`). Alternatively, state that tests call
  the private `impl.array/discover` with `capability/capability` replaced by
  an available result with `:probe-class`. State the namespace and the
  visibility (public, or private and called through the var) of `classify`
  and `passes-self-check?`.
- [x] Inconsistency (pass 2): acceptance 5 lists `runtime-test/capability-test`
  as the only test that changes because of `:probe-class`. But
  `value-type.field-transfer-checks/consume` also returns the whole
  value-class result as `:capability`, and
  `value-type.field-transfer-test/strict-identity-public-transfer-test`
  (`test/cross-jvm`) runs it on the `valhalla` receiver, where the result is
  available. The cell child prints results with `prn` and the parent reads them
  with `edn/read-string` (`jvm_runner.clj`). A Class prints as `#object[...]`,
  so the cell gets a read error. Add this expected change to acceptance 5
  (for example, `consume` returns only `:available?` and `:reason` of the
  result), or remove the need for the change in the contract.
  - Checked: `identity_receiving_checks/incompatible-value` also returns the
    whole result, but runs only on `incompatible-receivers` (unavailable, no
    `:probe-class`). `capability_matrix_checks` uses `select-keys`.
- [x] Inconsistency (pass 3): acceptance 1 removes the format constants from
  `impl.transform`, and acceptance 5 says the runtime suite passes unchanged
  except the listed tests. But two runtime tests read the private transform
  vars that acceptance 1 removes:
  `test/runtime/value_type/impl/transform_test.clj` (`@#'transform/ACC_IDENTITY`
  at lines 118, 422, 423, 896, 1042; `@#'transform/ACC_STRICT_INIT` at 433,
  893) and `test/runtime/value_type/impl/loader_test.clj`
  (`@#'transform/ACC_IDENTITY` at 179; `@#'transform/ACC_STRICT_INIT` at
  229). Add these tests to the expected changes of acceptance 5 (for example,
  they read `:identity-class-flag` and `:strict-field-flag` from
  `(jvm-format/row)`; their assertions do not change).
  - Checked: the direct `ClassFile/PREVIEW_MINOR_VERSION` and
    `ClassFile/latestMajorVersion` reads in these tests do not depend on
    `impl.transform` and can stay.
- [x] Ambiguity (plan review 1): steps.md slice 1 "Verify the restricted
  builder probe on EA3: the annotation is runtime visible (reflection sees
  `NullRestricted`)". State if this is a committed runtime test or a one-time
  check. State the observation: parse the builder bytes with
  `ClassFile/of` and find `NullRestricted` in the field
  `RuntimeVisibleAnnotationsAttribute`, or read the field annotations by
  reflection. `jdk.internal.vm.annotation` is not exported, so reflection
  can fail for a reason that is not the attribute. Slice 3
  `native-probe-enforcement-test` already observes nil rejection.
- [x] Ambiguity (plan review 1): the homes of the new tests. Give the test
  namespaces of the slice 1 row and builder tests, the slice 2
  classification tests and the slice 4 round-trip tests. Slice 2
  (`classify`) and slice 4 (`passes-round-trip?`) both use the constant value
  class, and slice 2 also uses the `deftype` identity class. State the one
  `test/runtime` namespace that defines these fixtures (for example a support
  namespace next to `array_test_support.clj`).
- [x] Ambiguity (plan review 1): steps.md slice 6 "note that
  `mementum/knowledge/java-lang-classfile.md` is stale". Give the target of
  the note: edit the knowledge page in this task (the owner of the format
  flags becomes `impl.jvm-format/row`; tests read the row, not
  `@#'ns/ACC_IDENTITY`), a line in `mementum/state.md`, or a record for
  `extract-task-knowledge` only.
- [x] Inconsistency (plan review 1): plan.md says each slice compiles at its
  boundary. steps.md slice 2 removes `impl.capability/probe-bytes` and
  `latest-major-version`, but `impl.field-capability/probe` and the two
  field-capability tests use them until slice 3. Then `value-type.core` does
  not load after slice 2. Move the field-probe builder switch (and the two
  test updates) into slice 2, or remove the two vars in slice 3. Also correct
  the slice 3 text "remove the `probe-bytes` use from `impl.capability`" (the
  use is in `impl.field-capability`).
- [x] Inconsistency (plan review 1): plan.md slice order and steps.md give
  different slices for two changes. plan.md slice 5 lists
  `runtime-test/capability-test`; steps.md updates it in slice 2 (correct:
  `:probe-class` starts in slice 2). steps.md slice 5 changes the `report`
  docstring and runs `bb api-docs --check`, but regenerates `doc/API.md`
  only in slice 6. `doc/API.md` holds the `report` docstring, so the slice 5
  check fails. Align plan.md slice 5 with steps.md, and regenerate
  `doc/API.md` in slice 5.
- [x] Inconsistency (plan review 1): design.md acceptance 5 (`consume`
  entry: "A printed Class does not read") and the plan.md risk
  ("`field_transfer_checks/consume` must `select-keys` before printing")
  contradict Clojure behavior. `prn` prints a Class as its name, and
  `edn/read-string` reads it as a symbol (checked:
  `(edn/read-string (pr-str {:a String}))` gives `{:a java.lang.String}`; the
  cell harness does not bind `*print-dup*`). `field-transfer-test` reads only
  `:reason`, so `consume` needs no change. Correct the rationale, or remove
  the `consume` change from acceptance 5 and slice 5 (minimal). The
  `capability-test` change stays: the probe Class symbol differs from
  `{:available? true}`.
  - Plan part done (2026-11-06): plan.md risk and steps.md slice 5 now give
    the correct rationale and keep the `consume` change, because design.md
    acceptance 5 lists it. Open: the design.md acceptance 5 sentence "A
    printed Class does not read" is still false. design.md is read-only in
    the plan profile; the design profile must correct the sentence (or
    remove the `consume` entry, and then the steps.md slice 5 step).
- [x] Ambiguity (plan review 2): steps.md slice 2 "Verify acceptance 1 by
  grep: no flag, version or order constants remain in `impl.capability`,
  `impl.field-capability`, `impl.probe-builder` or `impl.transform`" gives
  no pattern and no permitted matches. Two matches are by design but look
  like failures: the builder and the transform constructor emitter
  (`constructor-code`) must name `:fields-before-super` to assert
  `:constructor-order` (design Format row), and the `impl.transform`
  docstrings of `value-class-transform` (line ~259) and `->value-class`
  (line ~319) name `ACC_IDENTITY` and `ACC_STRICT_INIT` in prose. Give the
  grep command (for example `0x20\b`, `0x800`, `0xffff`, `ACC_IDENTITY`,
  `ACC_STRICT_INIT`, `latestMajorVersion`, `PREVIEW_MINOR_VERSION`,
  `fields-before-super` over the four files) and the permitted matches: the
  asserted `:fields-before-super` in the two emitters; and either the
  docstring prose stays, or the docstrings name the row keys
  (`:identity-class-flag`, `:strict-field-flag`) and the grep is empty for
  the flag names.
- [x] Inconsistency (plan review 2): plan.md (key decision 1 and the
  emission risk) says slice 1 tests the builder bytes before callers change
  and so isolates the stack-map risk from the classification logic. But
  steps.md slice 1 builds only the `J` probe and the restricted reference
  probe. The stack-map risk applies only to the malformed `I` probe with
  `aload` (`FieldProbeBadSlots`), which steps.md first builds in slice 2
  (`malformed-probe-test`), together with the `impl.capability` rewrite.
  The EA3 recheck of implementation.md (design review pass 2) has no step
  of its own. Add a slice 1 builder test that builds
  `(probe-bytes owner "I" false)` on EA3 and gets bytes (or defines the
  class and gets `VerifyError`), or change plan.md to state that the
  stack-map risk shows first in slice 2.
- [x] Inconsistency (plan review 3): plan.md says "the full test matrix
  passes at each slice boundary", but the steps.md slice runs check less.
  Slice 4 changes `impl.array/discover` and runs only the runtime suite,
  but `value-type.capability-matrix-test` (`test/cross-jvm`, via
  `capability_matrix_checks` → `impl.array/allocation-capability`) checks
  the `:array-allocation` rows. Slice 3 changes `verify-enforcement` and
  does not run the fallback suite. The suite names also differ: design.md
  acceptance 5 says "fallback"; steps.md slice 2 says "compatibility";
  steps.md slice 5 lists "fallback" and "compatibility" as two suites. Both
  names are `:fallback-test` (`test/compatibility`), which `release.yml`
  runs as `fallback-java8`, `fallback-preview-off` and
  `fallback-preview-on`. Use one name, and run the suites that each slice
  affects (at least cross-JVM in slice 4 and fallback in slice 3), or
  weaken the plan.md statement.
- [x] Inconsistency (plan review 3): the steps.md slice 2 acceptance 1 grep
  is case-sensitive and finds `0xffff`. design.md (Format row) writes the
  minor version as `0xFFFF`, so the grep does not find a literal in the
  form that the design gives. Use `grep -niE` (today it gives the same
  matches) or add `0xFFFF` to the pattern.
