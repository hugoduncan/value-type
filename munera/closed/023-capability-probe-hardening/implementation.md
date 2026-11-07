# Implementation

- 2026-11-06 design review (architecture): architectural review added 2 new
  design steps.
  - Context: `ramora/META.md` and `doc/architecture.md` do not exist. The
    architecture sources are `AGENTS.md`, `ramora/INDEX.md`,
    `decisions/probe-and-format.md` (D15–D17),
    `fallback/capability-boundary.md` (D12), `fallback/selected-detector.md`
    and `embodiment/runtime.md`.
  - Today `impl.capability/latest-major-version` reads `ClassFile` by
    reflection, so it is baseline-safe; `impl.transform` and
    `impl.class-file` import `java.lang.classfile` and load only behind the
    boundary. A format namespace that imports `ClassFile` must stay on the
    transform side.
  - Not raised: probe classes still use `DynamicClassLoader` directly in the
    probe namespaces. D19 moves this to the compiler boundary in task 026.
    The probe builder makes bytes only, so 026 can move definition alone.
- 2026-11-06 design review (ambiguity): ambiguity review added 6 new design
  steps.
  - Context: current `impl.array/discover` checks access with `.unreflect`
    and does not invoke the allocator. The round trip is the first
    allocation call, so its exception handling needs a decision.
    `core/policy-expansion` passes `:detail` to `runtime/enforce-fallback!`.
- 2026-11-06 design review (inconsistency): inconsistency review added 2 new
  design steps.
- 2026-11-06 guidance for the design-steps slice (10 open items):
  - Principles: edit design.md only; keep scope frozen. Resolve each item in
    line with D12, D14–D17 (firm). Where design.md and a firm decision
    disagree, change design.md, not the ramora decision. Keep it minimal: no
    row lookup machinery past what one default row needs (AGENTS.md
    `minimal ∧ ¬constrain(extend)`). Prose in ASD-STE100 style (`bb vale`).
  - Coupled items: the load-position item (architecture 1) and the row-shape
    item (ambiguity 1) decide together. If the row holds major as a function
    that calls `ClassFile`, the format namespace is transform-side only.
    The row-override item (inconsistency 2) changes the same row definition.
  - Coupled items: the probe-Class key, the array round trip and `:detail`
    all change the result maps read by `impl.array/require-allocation`,
    `core/require-value-capability!` and
    `field-capability/require-field-capability!`.
  - Files: `src/value_type/impl/{capability,field_capability,array,transform,class_file}.clj`,
    `src/value_type/capability.clj` (`report` docstring lists reasons),
    `src/value_type/core.clj` (`policy-expansion`, lazy `requiring-resolve`
    pattern), `test/cross-jvm/value_type/field_capability_test.clj`,
    `test/runtime/value_type/impl/field_capability_test.clj`,
    `dev/spike/fallback/format-check.clj`,
    `ramora/defvaluetype-design/embodiment.md` (gap table),
    `ramora/defvaluetype-design/embodiment/runtime.md`,
    `ramora/defvaluetype-design/assertions/fallback-architecture.md` (A-34
    owners), `ramora/non-nil-field-design.md` (line ~79, ignored restriction).
- 2026-11-06 design-steps slice: all 10 items resolved in design.md. No
  `SCOPE_QUESTION:` items existed.
  - `dev/spike/fallback/format-check.clj` does not call
    `capability/probe-bytes`. It has its own `clojure.asm` copy ("independent
    of the production transformer"). It is outside acceptance 1; leave it.
  - Runtime suite JVM: `deps.edn` `:test` has only `--enable-preview`;
    `release.yml` adds `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
    Run the acceptance 3/4 array tests with the export.
  - Risk (malformed rows): `clojure.asm` probe uses `ClassWriter 0` (no
    frames). `java.lang.classfile` generates stack maps by default and can
    reject the `FieldProbeBadSlots` (`I` with `aload`) class at build time,
    not with the JVM `VerifyError`. If so, build probes with
    `ClassFile$StackMapsOption/DROP_STACK_MAPS` (straight-line code needs no
    frames).
  - `:probe-class` (a Class) now crosses child-process cell results where
    checks return the whole value-class result:
    `field_transfer_checks/consume`, `identity_receiving_checks`. They
    observe only `:reason`/`:available?`; check that printing a Class does
    not break the cell reader.
  - `NullRestricted` descriptor is also in `impl.field-storage`. D15 does not
    list it, so it is not a format-row key.
  - Unavailable array results carry `:cause` (Throwable), value-class results
    carry `:detail`. `require-allocation` puts `:detail` into ex-data for
    inherited results already (it removes only `:cause`, `:available?`).
- 2026-11-06 design review pass 2 (architecture): architectural review added
  2 new design steps.
  - Checked: format constants exist only in `impl.capability` and
    `impl.transform` (`value_backend`, `class_file`, `loader` have none), so
    acceptance 1 covers all owners. `NullRestricted` in `impl.field-storage`
    stays out of the row (D15 does not list it).
  - Both new steps are doc-owner gaps, not design changes: the runtime and
    array embodiments describe behavior that this task changes.
- 2026-11-06 design review pass 2 (ambiguity): ambiguity review added 1 new
  design step.
  - The stack-map risk of the malformed rows did not reproduce on JDK 25:
    `ClassFile.of().build` accepts straight-line `aload` of an `int` slot and
    returns bytes, so the JVM verifier can still give `VerifyError`. Recheck
    on the Valhalla EA3 build.
- 2026-11-06 design review pass 2 (inconsistency): inconsistency review added
  1 new design step.
- 2026-11-06 guidance for the pass-2 design-steps slice (4 open items):
  - Principles: the earlier slice guidance still applies (edit design.md only;
    scope frozen; firm D12, D14–D17; minimal). Do not change behavior to close
    a doc or test gap: the two architecture items only extend acceptance 6, and
    the inconsistency item only extends acceptance 5.
  - Test entry point (ambiguity item): existing tests reach private fns via
    the var (`@#'value-type.impl.field-capability/verify-enforcement`) and
    replace cached results with `with-redefs` (docstrings of
    `allocation-capability`). Both styles are project practice. The
    allocator for a direct round-trip call needs the export; the
    `impl.array/discover` path gets it from discovery.
  - Coupled: the `:probe-class` key causes the `consume` item and the
    `fallback-interfaces.md` part of the runtime-doc item. If the contract
    changes how `:probe-class` is carried, recheck both.
  - Paths: `ramora/defvaluetype-design/embodiment/runtime.md` (Invariants,
    State), `ramora/defvaluetype-design/embodiment/runtime/fallback-interfaces.md`,
    `ramora/value-type-array-design/embodiment.md` (adapter paragraph),
    `ramora/value-type-array-design/decisions.md` (D3 boundary),
    `test/compatibility/value_type/field_transfer_checks.clj` (`consume`),
    `test/cross-jvm/value_type/field_transfer_test.clj`,
    `test/compatibility/value_type/jvm_runner.clj` (`edn/read-string`),
    `test/compatibility/value_type/cell_harness.clj` (`prn` of cell values),
    `test/compatibility/value_type/jvm_config.clj` (`receivers`).
- 2026-11-06 pass-2 design-steps slice: all 4 items resolved in design.md
  (acceptance 3–6, Classification test entry points). No `SCOPE_QUESTION:`
  items existed.
  - `passes-round-trip?` tests take the allocator from
    `(:allocator (impl.array/allocation-capability))`. That result is
    available only when discovery's own round trip on the builder probe
    passed. If it did not, the constant-class test has no allocator; read the
    discovery result first when that test fails.
  - `value-type.capability-test/report-translation-test` replaces
    `allocation-capability` in both states, so the new
    `IllegalStateException` (available result without `:probe-class`) does
    not reach it. `controlled_value_capability_test` replaces `capability`
    with an unavailable result only.
  - `vale` on design.md reports `APIs` (quoted runtime.md row), `Throwable`
    and `readback` as spelling. `bb vale` does not check `munera/`.
- 2026-11-06 design review pass 3 (architecture): no architectural review
  feedback.
  - Checked: the D12 boundary, D15–D17 and the selected detector agree with
    the Architecture section and the dependency edges. Array D3 permits the
    round trip in `impl.array`. No dependency cycle.
  - For the inconsistency turn: `test/runtime/value_type/impl/transform_test.clj`
    and `loader_test.clj` read `@#'transform/ACC_IDENTITY` and
    `@#'transform/ACC_STRICT_INIT`. Acceptance 1 removes these vars, but
    acceptance 5 does not list these tests as expected changes.
  - For knowledge extraction: `mementum/knowledge/java-lang-classfile.md`
    ("private literal vars in one namespace", `@#'ns/ACC_IDENTITY`) becomes
    stale; the owner becomes `impl.jvm-format/row`.
- 2026-11-06 design review pass 3 (ambiguity): no ambiguity review feedback.
  - Implementer note: the current probe adds `NullRestricted` as a
    runtime-visible annotation (`visitAnnotation … true`). With
    `java.lang.classfile`, use `RuntimeVisibleAnnotationsAttribute`.
    `native-probe-enforcement-test` fails if the annotation is not visible.
  - Checked: `report` already uses `select-keys`, so `:probe-class` does not
    reach it. `fields/select-enforcement` selects generated enforcement when
    the field result is unavailable, so the field guard sees
    `:native-restriction-ignored` only for fixed `:native` modes in
    receiving expansions.
- 2026-11-06 design review pass 3 (inconsistency): inconsistency review added
  1 new design step.
  - Checked, no other gap: the only `with-redefs` of `capability/capability`
    with an available result is `capability_test` `:no-export`, which also
    replaces `allocation-capability`. `bb api-docs` covers only public
    namespaces, so the new public functions in `impl.*` do not change
    `doc/API.md`.
- 2026-11-06 guidance for the pass-3 design-steps slice (1 open item):
  - Principles: the earlier slice guidance still applies (edit design.md
    only; scope frozen; firm D12, D14–D17; minimal). Close the item by
    extending the acceptance 5 list. Do not keep the transform vars as
    aliases: acceptance 1 and D15 want one owner.
  - Use the existing acceptance 5 entry form: test var, suite dir in
    parentheses, the change, "Its assertions do not change."
    Both tests are in `test/runtime`. They need a require of
    `value-type.impl.jvm-format`. The runtime suite has `java.lang.classfile`,
    so the require is safe there.
  - `loader_test.clj` lines 221–229 build a test class with
    `latestMajorVersion`, `PREVIEW_MINOR_VERSION` and
    `@#'transform/ACC_STRICT_INIT`. Only the flag var must change. Reading
    `:major`/`:minor` from the row is optional; do not require it.
  - Paths: `test/runtime/value_type/impl/transform_test.clj`,
    `test/runtime/value_type/impl/loader_test.clj`.
- 2026-11-06 pass-3 design-steps slice: the 1 item resolved in design.md
  (acceptance 5, one entry for both namespaces). No `SCOPE_QUESTION:` items
  existed.
  - The 9 private-var reads are in shared builders as well as in deftests:
    `transform_test` `build-class` (line 118) and the record-class builder
    (line 1042); `loader_test` line 229 is in `super-before-write-class`.
    Replace each read; `grep -n "transform/ACC_"` must then give no result.
- 2026-11-06 design review pass 4 (architecture): no architectural review
  feedback.
  - Only change since pass 3: the acceptance 5 transform/loader test entry.
    Its `jvm-format` require is in `test/runtime` (has
    `java.lang.classfile`), so the D12 boundary holds.
- 2026-11-06 design review pass 4 (ambiguity): no ambiguity review feedback.
- 2026-11-06 design review pass 4 (inconsistency): no inconsistency review
  feedback.
- 2026-11-06 after design review pass 4: no open design steps. design.md is
  stable since `39912bc2`; the next step is the plan (`create-task-plan`).
  - Implementer note: `verify-enforcement`
    (`src/value_type/impl/field_capability.clj`) already maps an
    `InvocationTargetException` with a `NullPointerException` cause to
    rejection and rethrows other causes. Keep that path. Only the accept-nil
    branch changes, from `IllegalStateException` to the
    `:native-restriction-ignored` result.
- 2026-11-06 plan review (ambiguity): ambiguity review added 3 new design
  steps.
  - For the inconsistency turn: steps.md slice 2 removes
    `capability/probe-bytes` and `latest-major-version`, but
    `impl.field-capability` and its two tests use them until slice 3. Then
    `value-type.core` does not load at the slice 2 boundary. plan.md says
    each slice compiles.
  - For the inconsistency turn: Clojure `prn` prints a Class as its name, and
    `edn/read-string` reads it back as a symbol (checked with
    `(edn/read-string (pr-str {:a String}))`; the cell harness does not bind
    `*print-dup*`). The design and plan rationale "a printed Class does not
    read" is false. `capability-test` still must drop `:probe-class` (the
    symbol differs from `{:available? true}`). The `consume` change is safe,
    but its stated cause is wrong.
  - Implementer note: `malformed-probe-test` row 1 uses
    `cell-registry/failed-call`, which gives `{:status :unexpected-success}`
    when the call returns. The new row needs `outcome` with a success
    function (or a direct call) to observe the returned result.
  - Verification commands (from `release.yml`): runtime suite needs
    `-J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED` (existing
    array tests need it); "Java 8 load tests" is `fallback-java8`
    (`JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:fallback-test`);
    "isolated source-JAR" is `test/build` `isolated-consumer-test`.
  - Slice 3 "after the API checks": `impl.field-capability` has no API
    checks. Resolve the builder in `probe`, which runs only when the
    value-class result is available.
- 2026-11-06 plan review (inconsistency): inconsistency review added 3 new
  design steps.
  - Checked, no gap: `ramora/non-nil-field-design.md` line 79 and
    `fallback/capability-boundary.md` already state the D12 classified
    absences; steps.md slice 6 covers every acceptance 6 doc owner.
- 2026-11-06 guidance for the plan-review design-steps slice (6 open items):
  - Scope: these items are about the plan. Edit plan.md and steps.md (the
    review treated steps.md as read-only; this slice does not). Only the
    `consume` item can touch design.md (acceptance 5). Keep scope frozen and
    D12, D14–D17 firm. Do not reopen checked items from earlier passes.
  - Compile-break item: prefer moving the field-probe switch into slice 2.
    plan.md wants acceptance 1 (one owner) to hold at the slice 2 boundary.
    If the vars stay until slice 3, `impl.capability` keeps the format
    constants at that boundary.
  - `consume` item: minimal (AGENTS.md) favors removing the change. If it is
    removed, also remove the plan.md risk and the steps.md slice 5 step. The
    pass-2 design step that says "A Class prints as `#object[...]`" stays as
    history. Evidence: `cell_harness.clj` line ~314 binds only
    `*print-meta*`, `*print-length*`, `*print-level*` and
    `*print-namespace-maps*`; `field_transfer_test.clj` line ~44 uses
    `(harness/observed [:capability :reason] …)`.
  - `NullRestricted` item: parsing the builder bytes with `ClassFile/of`
    works on JDK 25 as well as on EA3 (no class definition is needed). The
    test file `native-probe-enforcement-test` already parses holder bytes, so
    a parse check there or next to it reuses that pattern.
  - Test-home item: defining the constant value class needs the Valhalla
    runtime. The `deftype` identity class needs no Valhalla. A support
    namespace loads only in `test/runtime`.
  - Knowledge-page item: by the mementum protocol, `extract-task-knowledge`
    updates knowledge pages after the implementation review. A record for
    that step needs no edit of the page in slice 6.
  - Paths: `test/compatibility/value_type/field_transfer_checks.clj`
    (`consume`), `test/cross-jvm/value_type/field_transfer_test.clj`,
    `test/compatibility/value_type/cell_harness.clj`,
    `test/runtime/value_type/array_test_support.clj`,
    `test/runtime/value_type/impl/field_capability_test.clj`,
    `doc/API.md` (lines ~126–135, `report` reasons),
    `mementum/knowledge/java-lang-classfile.md` (lines ~19–24).
- 2026-11-06 plan-review design-steps slice: 5 of 6 items resolved in
  plan.md and steps.md. The `consume` item stays open for its design.md part
  (see its note in design-steps.md); design.md was read-only in this slice.
  - Slice 2 now holds the field-probe builder switch and both field-probe
    test updates, so `value-type.core` loads at the slice 2 boundary. Slice
    3 keeps only the `:native-restriction-ignored` behavior and tests.
  - `impl.field-capability/detect` already returns the value-class result
    when it is unavailable; slice 3 only confirms it.
  - `NullRestricted` check reuses
    `test/runtime/value_type/annotation_support.clj`
    (`annotation-contents`); `impl/field_storage_test.clj` lines ~57–67 show
    the expected map form `{["Ljdk/internal/vm/annotation/NullRestricted;"
    {}] 1}`.
  - New test namespace names were checked free:
    `value-type.impl.capability-test` differs from the existing
    `value-type.capability-test` (`test/runtime/value_type/capability_test.clj`,
    `report` translation); `value-type.impl.array-test` differs from
    `value-type.array-test`. scry runs `--dir test/runtime`; a support
    namespace with no tests (like `array_test_support.clj`) is fine there.
  - `doc/API.md` regenerates with `bb api-docs` (check: `bb api-docs
    --check`); slice 5 owns it now.
- 2026-11-06 plan review pass 2 (ambiguity): ambiguity review added 1 new
  design step.
  - Checked, no gap: `constructor-code` is the only transform constructor
    emitter (the record path reuses it; `delegating-code` only matches
    delegating constructors), so one assertion point covers the transform.
    `runtime-test` `optional-loaded-form` lists only `impl.loader` and
    `impl.transform`, so lazy loading of `impl.probe-builder` and
    `impl.jvm-format` on Valhalla does not change it.
- 2026-11-06 plan review pass 2 (inconsistency): inconsistency review added
  1 new design step.
  - Checked, no gap: `ramora/defvaluetype-design/risks-and-evolution.md`
    ("except the classified absences of D12") and A-34 owners stay true.
    The `deftype-star-design/*-gate.md` mentions of `ACC_IDENTITY` are
    gate evidence, not owners; acceptance 6 need not list them.
- 2026-11-06 guidance for the plan-review pass-2 design-steps slice (3 open
  items):
  - Scope: edit plan.md and steps.md. Only the `consume` item touches
    design.md (acceptance 5 sentence); it needs the design profile. Keep
    scope frozen; D12, D14–D17 firm; minimal.
  - Grep item (ambiguity): the design requires the `:fields-before-super`
    assertion in both emitters, so permit it; do not move the assertion
    into `impl.jvm-format` (the row owns data only). Docstring choice: the
    lowest-cost option keeps the prose; renaming to row keys makes an empty
    flag-name grep possible. Match `0x20` with a word boundary: `0x200`,
    `0x2000` etc. are not the identity flag.
  - Stack-map item (inconsistency): prefer the slice 1 test. It is one
    `probe-bytes` call, and it moves the EA3 recheck before the capability
    rewrite. Build-only (bytes returned) suffices; the `VerifyError` row
    stays in `malformed-probe-test`. If EA3 throws at build, add
    `DROP_STACK_MAPS` in slice 1, not slice 2, and update the slice 2 note.
  - Paths: `src/value_type/impl/transform.clj` (`constructor-code` ~210,
    docstrings ~259 and ~319, vars ~28–29),
    `test/cross-jvm/value_type/field_capability_test.clj` (malformed rows
    ~58–65), `test/runtime/value_type/impl/` (new
    `probe_builder_test.clj`).
- 2026-11-06 plan-review pass-2 design-steps slice: 2 of 3 items resolved in
  plan.md and steps.md. The `consume` item stays open (design.md part only,
  needs the design profile).
  - Grep item: chose to rename the two `impl.transform` docstrings to row
    keys, so the slice 2 grep has no flag-name match. The pattern adds
    `ACC_STRICT` (covers `ACC_STRICT_INIT` and a `ClassFile/ACC_STRICT`
    read). BSD grep 2.6.0 on macOS supports `\b` with `-E` (checked: `0x20\b`
    does not match `0x200`). Today the pattern matches
    `impl.capability` lines ~47, 58, 62 (removed with `probe-bytes`) and
    `impl.transform` lines 28–29, 255, 259–260, 268–269, 273, 319;
    `impl.field-capability` has no match.
  - Stack-map item: slice 1 has a build-only `(probe-bytes owner "I" false)`
    test; `DROP_STACK_MAPS`, if needed, goes in slice 1. The slice 2 note
    now only refers back to it.
- 2026-11-06 plan review pass 3 (ambiguity): no ambiguity review feedback.
  - Implementer note: in `impl.array/discover`, call `passes-round-trip?`
    outside the existing `try`. Its `ClassNotFoundException`,
    `NoSuchMethodException` and `IllegalAccessException` catches would
    otherwise turn a round-trip exception into a classified absence (D12:
    it must propagate).
  - Implementer note: the slice 3 `verify-enforcement` test needs its own
    class names, not the `NativeEvidence*` names of
    `native-probe-enforcement-test` (`probe` comment: `DynamicClassLoader`
    caches classes across loaders).
  - For the inconsistency turn: the slice 2 acceptance 1 grep is
    case-sensitive (`0xffff`), but design.md writes `0xFFFF`. Today only
    `impl.capability` line 58 matches (lower case). `grep -niE` gives the
    same matches today and also finds `0xFFFF`.
- 2026-11-06 plan review pass 3 (inconsistency): inconsistency review added
  2 new design steps.
- 2026-11-06 guidance for the plan-review pass-3 design-steps slice (3 open
  items: the `consume` design.md part and the 2 pass-3 inconsistency items):
  - Scope: the 2 pass-3 items edit plan.md and steps.md only. Do not change
    behavior or design.md for them. The `consume` item still needs the
    design profile. Keep scope frozen; D12, D14–D17 firm; minimal.
  - Suite item: prefer to keep the plan.md statement and fix the steps. Use
    one suite name (`fallback`, as in design.md acceptance 5). Name the
    affected suites in each slice run step. Commands (`release.yml` lines
    ~90–162): fallback-preview-off
    `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test`;
    fallback-preview-on `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure
    -J--enable-preview -M:fallback-test`; cross-JVM
    `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure -M:cross-jvm-test`;
    build `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:build-test -m
    scry.cli --dir test/build`; tooling `clojure -Srepro
    -M:quickdoc:tooling-test -m scry.cli --dir test/tooling`.
    `capability-matrix-test` is in the cross-JVM suite.
  - Grep item: `grep -niE` with the same pattern gives the same matches
    today (checked on BSD grep).
  - Paths: `.github/workflows/release.yml`, `deps.edn` (`:fallback-test`,
    `:cross-jvm-test`, `:test`),
    `test/cross-jvm/value_type/capability_matrix_test.clj`,
    `test/compatibility/value_type/capability_matrix_checks.clj`.
- 2026-11-06 plan-review pass-3 design-steps slice: the 2 pass-3 items
  resolved in plan.md and steps.md. The `consume` item stays open (design.md
  acceptance 5 sentence; needs the design profile).
  - Suite item: kept the plan.md "full test matrix" statement. plan.md now
    has a Verification section (suite names, `release.yml` commands, suites
    per slice); each steps.md run step names its suites. "Fallback suite"
    means the three `:fallback-test` configurations; "compatibility" is no
    longer a suite name (only the `test/compatibility` directory).
  - Suites per slice: slice 1 runtime only (no `src` namespace requires the
    new namespaces); slices 2–4 runtime, fallback, cross-JVM, build; slices
    5–6 add tooling. The build suite counts for slices 3–4:
    `test/resources/build/isolated-consumer.clj` uses value arrays on the
    export configuration (`-Dconsumer.arrays=true`). The tooling suite only
    analyzes source (quickdoc, clj-kondo); it does not load `impl.*`.
  - Grep item: the slice 2 grep is now `grep -niE`. Checked: same matches as
    `-nE` today.
- 2026-11-06 plan review pass 4 (ambiguity): no ambiguity review feedback.
  - Checked: the plan.md Verification commands agree with `release.yml` and
    `deps.edn` (`:test` and `:build-test` add `--enable-preview`). The
    `test/release` suite is outside the design matrix and does not load
    `impl.*`. `test/tooling` loads no `impl.*` namespace.
- 2026-11-06 plan review pass 4 (inconsistency): no inconsistency review
  feedback.
- 2026-11-06 after plan review pass 4: both turns gave no feedback; plan.md
  and steps.md are stable. The one open design step is the `consume` item
  (design.md acceptance 5 sentence "A printed Class does not read"). It
  needs the design profile.
  - Lowest-cost fix: replace only that sentence with the true rationale
    (a printed Class reads back as a symbol; `select-keys` keeps the cell
    value plain data). Then plan.md (risk) and steps.md (slice 5) already
    agree and need no edit. If the `consume` entry is removed instead,
    remove both, as in the earlier guidance.
  - Either fix changes only rationale or test scope, not behavior, so no
    other acceptance item or slice changes.
- 2026-11-06 slice 1 (format row and probe builder): done.
  - Valhalla JVM: `~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`
    (the `jvm_config.clj` default); env vars are unset locally.
  - EA3 builds the malformed `I` probe without `DROP_STACK_MAPS`; no
    stack-map option is needed.
  - `jvm-format/row` returns one private `default-row` map (major read at
    load). `probe-bytes` asserts `:constructor-order` before the build.
  - The probe-builder test checks field flags `0x811` (public, final,
    strict) by parse.
  - `.scry-results` stays empty with `--dir`; check new tests by
    `clojure.test/run-tests` on the namespace.
  - Fixed the design.md acceptance 5 `consume` rationale (a printed Class
    reads back as a symbol); closed the last open design step.
- 2026-11-06 slice 2 (value probe classification): done.
  - `impl.capability`: `probe` takes no args; it resolves the builder with
    `requiring-resolve` and calls `classify`. `:failed-self-check` `:detail`
    is a private constant `self-check-detail` (slice 4 array result has its
    own text per design).
  - `impl.transform`: `strict-init-transform` is now a fn of the flag;
    `value-class-transform` reads the row once per call; `constructor-code`
    asserts `:constructor-order` when it builds the Consumer.
  - `loader_test` no longer requires `impl.transform` (only the removed vars
    used it; kondo flagged the unused require).
  - `impl.capability-test` also has `detect-test` (the cached result holds a
    value-class `:probe-class`).
  - Fallback preview-on reports `value-receiving-star-closed-option-order-test`
    as "1 unknown": it has no assertions when capability is available.
    Pre-existing; not a failure.
  - Local runs: export `VALUE_TYPE_VALHALLA_JAVA` and
    `VALUE_TYPE_BASELINE_JAVA` (temurin-8 path) for cross-JVM and fallback.
- 2026-11-06 slice 3 (field probe): done.
  - `detect` unchanged (already passes an unavailable value-class result
    through). `detect` docstring now names `:native-restriction-ignored`.
  - `verify-enforcement` readback failure still throws
    `IllegalStateException`; only accept-nil returns the absence map.
  - `malformed-probe-test` row 1 uses `cell-registry/outcome` with
    `identity` as success fn; expects the full map (with `:detail`).
  - New runtime test `native-restriction-ignored-test` uses class names
    `IgnoredLeaf`/`IgnoredHolder` (unique per `DynamicClassLoader` cache).
- 2026-11-06 slice 4 (array round trip): done.
  - `impl.array/invoke` moved above `passes-round-trip?` (the round trip
    reuses it to unwrap `InvocationTargetException`).
  - `discover` runs the round trip outside the API `try` (D12 propagation);
    `:failed-self-check` `:detail` is private `round-trip-detail`.
  - `require-allocation` ex-data keeps `:reason`/`:detail` (only `:cause`,
    `:available?` removed); message switches only for `:failed-self-check`.
  - `impl.array-test` also has a `require-allocation` message test via
    `with-redefs` of `allocation-capability`.
- 2026-11-06 slice 5 (downstream contract): done.
  - `report` docstring lists reasons per key: value-class adds
    `:probe-not-value-class`, `:failed-self-check`; field adds
    `:native-restriction-ignored` (plus value-class reasons); array
    allocation adds `:failed-self-check`. `report` already drops
    `:probe-class` (`select-keys`).
  - `core/require-value-capability!`, `policy-expansion`
    (`enforce-fallback!`), `require-field-capability!` and
    `require-allocation` already use `:detail`; no change.
  - Tooling suite failed before this slice: `api-docs-test` wants the
    contiguous text "not the plain or the record shape"; task 022 had
    wrapped it in the `defvaluetype*` docstring. Fixed by a reflow in
    `core.clj` (no text change); `doc/API.md` regenerated.
  - Full matrix green; fallback preview-on keeps its known "1 unknown".
- 2026-11-06 slice 6 (docs and design artifacts): done.
  - Usage guide: one paragraph after the report contract on the new reasons
    (no example change; EA3 report rows unchanged).
  - Gap table keeps only the AOT part of D17 ("No AOT initialization check").
  - `mementum/knowledge/java-lang-classfile.md` is stale (flag owner is now
    `impl.jvm-format/row`; `@#'ns/ACC_IDENTITY` test reads are gone). Left
    for `extract-task-knowledge`.
- 2026-11-06 implementation review: added 1 step to be addressed.
- 2026-11-06 review follow-up: addressed 1 review step.
  - `self-check-detail` = design text; `classify-test` compares the full
    `:failed-self-check` map. Runtime suite, fmt, lint green.
- 2026-11-06 implementation review pass 2: added 2 steps to be addressed.
- 2026-11-06 review pass 2 follow-up: addressed 2 review steps.
  - `doc/usage.md`: probe reasons stated per capability key (value-class →
    fallback; field `:native-restriction-ignored` → generated enforcement,
    fixed `:native` fails; array `:failed-self-check` → no fallback).
  - `impl.array-test/discover-guard-test` covers the missing
    `:probe-class` guard. Runtime and tooling suites, fmt, lint, vale green.
- 2026-11-06 implementation review pass 3: no new steps.
- 2026-11-06 test review: added 2 steps to be addressed.
- 2026-11-06 test review follow-up: addressed 2 review steps.
  - `impl.array-test/discover-failed-round-trip-test`: `discover` maps a
    false round trip to `:failed-self-check`.
  - `constructor-order-assertion-test` in `impl.probe-builder-test` and
    `impl.transform-test`: a redefined row with another
    `:constructor-order` gives `IllegalStateException`.
  - Runtime suite, fmt, lint green.
- 2026-11-06 test review pass 2: added 1 step to be addressed.
- 2026-11-06 test review pass 2 follow-up: addressed 1 review step.
  - `impl.array-test/round-trip-exception-test`: with `IdentityLong`,
    `passes-round-trip?` and `#'array/discover` throw
    `IllegalArgumentException` ("not a value class"). Runtime suite, fmt,
    lint green.
- 2026-11-06 test review pass 3: no new steps.
- 2026-11-06 test review pass 4: added 1 step to be addressed.
- 2026-11-06 test review pass 4 follow-up: addressed 1 review step.
  - `report-translation-test` `:no-export` fixture has `:probe-class
    String`; expected report unchanged, so `report` drops `:probe-class`.
    Runtime suite, fmt, lint green.
- 2026-11-06 test review pass 5: added 2 steps to be addressed.
- 2026-11-06 test review pass 5 follow-up: addressed 2 review steps.
  - `detect-inherits-value-class-absence-test` (`impl.field-capability-test`)
    and `discover-inherits-value-class-absence-test` (`impl.array-test`):
    an unavailable value-class result passes through unchanged, `:detail`
    included.
  - `long-probe-test` asserts the probe class is public.
  - Runtime suite (EA3), fmt, lint green.
- 2026-11-06 test review pass 6: added 2 steps to be addressed.
- 2026-11-06 test review pass 6 follow-up: addressed 2 review steps.
  - `constructor-order-assertion-test` (`impl.probe-builder-test`,
    `impl.transform-test`) and `discover-guard-test` (`impl.array-test`)
    use `thrown-with-msg?` with the contract messages.
  - `malformed-probe-test`: local `failed` and one `failure` function for
    all four rows; expected values unchanged.
  - Runtime and cross-JVM suites, fmt, lint green.
- 2026-11-06 test review pass 7: added 1 step to be addressed.
- 2026-11-06 test review pass 7 follow-up: addressed 1 review step.
  - `long-probe-test` asserts the `J` probe field flags `0x811`;
    `null-restricted-attribute-test` has no flag block. Runtime suite
    (EA3), fmt, lint green.
- 2026-11-06 test review pass 8: added 1 step to be addressed.
- 2026-11-06 test review pass 8 follow-up: addressed 1 review step.
  - `detect-test` calls `capability/detect` (not the cache); asserts key
    set `#{:available? :probe-class}` and a value-class probe Class.
    Runtime suite (EA3), fmt, lint green.
- 2026-11-06 test review pass 9: no new steps.
- 2026-11-06 docs review: added 2 steps to be addressed.
- 2026-11-06 docs review follow-up: addressed 2 review steps.
  - `CHANGELOG.md`: semantic self-check only for value class and array;
    field probe checks nil rejection; classified absences replace
    invariant errors; probes use `java.lang.classfile`.
  - `doc/usage.md`: names the probes that check instances; array
    inspection checks only the JDK API and access. `bb vale` green.
- 2026-11-06 docs review pass 2: added 1 step to be addressed.
- 2026-11-06 docs review pass 2 follow-up: addressed 1 review step.
  - `mementum/state.md`: semantic self-checks only for the value-class
    probe and the array round trip; the field probe checks nil rejection.
- 2026-11-06 docs review pass 3: added 1 step to be addressed.
- 2026-11-06 docs review pass 3 follow-up: addressed 1 review step.
  - D9 (`validation-and-errors.md`): the probe uses the builder (D16) and
    the self-check (D17); fallback for missing APIs, switch `off` (D18) and
    the D12 classified absences; links the selected detector. `bb vale` green.
- 2026-11-06 docs review pass 4: no new steps.
- 2026-11-06 code-shaper review: added 3 steps to be addressed.
- 2026-11-06 code-shaper review follow-up: addressed 3 review steps.
  - `impl.capability/probe-instance-fn` owns `(long)` construction and
    removes `InvocationTargetException`; used by `passes-self-check?`,
    `passes-round-trip?` and the `verify-enforcement` leaf. Test fixture
    `probe-test-support/throwing-class`; tests in `capability-test` and
    `array-test`.
  - All four fixed `:detail` texts are private vars.
  - `impl.array` imports `Array` and `Modifier`.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on has 1 `unknown` row; it also occurs
    without this change.
- 2026-11-06 code-shaper review pass 2: added 2 steps to be addressed.
- 2026-11-06 code-shaper review pass 2 follow-up: addressed 2 review steps.
  - `impl.probe-builder/unique-probe-name` (states the never-reuse
    invariant) and `define-probe` (loader, internal name, bytes) own the
    probe name and definition. `impl.capability/probe` and
    `impl.field-capability/probe` resolve them with `requiring-resolve`;
    the field probe uses one loader for leaf and holder;
    `UnsupportedClassVersionError` still gives `:unsupported-class-version`.
    `probe-test-support` uses them.
  - `impl.array` sets `*warn-on-reflection*`; no reflection warning.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on keeps its 1 `unknown` row
    (`value-receiving-star-closed-option-order-test`); it also occurs
    without this change.
- 2026-11-06 code-shaper review pass 3: added 3 steps to be addressed.
- 2026-11-06 code-shaper review pass 3 follow-up: addressed 3 review steps.
  - `impl.jvm-format/require-constructor-order!` (of a row) owns the
    `:constructor-order` assertion; `impl.probe-builder/probe-bytes` and
    `impl.transform/constructor-code` call it. The acceptance 1 grep has
    no match. plan.md key decision updated.
  - `impl.array/allocation-methods` holds the allocator API `try`;
    `discover` is only flow control.
  - `probe-test-support/long-class-bytes` builds the constant and the
    throwing class skeletons.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on keeps its 1 `unknown` row.
- 2026-11-06 code-shaper review pass 4: added 2 steps to be addressed.
- 2026-11-06 code-shaper review pass 4 follow-up: addressed 2 review steps.
  - `native-probe-enforcement-test`, `native-restriction-ignored-test` and
    `long-probe-test` use `unique-probe-name` and `define-probe`; holder
    descriptors come from the leaf name. The cross-JVM
    `malformed-probe-test` cell form uses `define-probe`.
  - `verify-enforcement` binds `holder-field` and `leaf-field` once.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on keeps its 1 `unknown` row.
- 2026-11-06 code-shaper review pass 5: added 1 step to be addressed.
- 2026-11-06 code-shaper review pass 5 follow-up: addressed 1 review step.
  - `impl.probe-builder/define-probe-class` (loader, prefix, descriptor,
    restricted?) owns name, bytes, define. `impl.capability/probe` and
    `impl.field-capability/probe` resolve only that var.
  - Deviation: the holder descriptor is
    `(str "L" (.replace (.getName leaf) "." "/") ";")`, not
    `(.descriptorString leaf)`. `Class.descriptorString` is Java 12+;
    on Java 8 the call reflects, and the reflection warning failed 21
    fallback-java8 and 29 cross-JVM tests.
  - Added `probe-builder-test/define-probe-class-test`.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on keeps its 1 `unknown` row.

- 2026-11-06 code-shaper review pass 6: added 1 step to be addressed.
- 2026-11-06 code-shaper review pass 6 follow-up: addressed 1 review step.
  - `probe-test-support/builder-probe-class` and
    `native-restriction-ignored-test` use `define-probe-class`; the holder
    descriptor comes from the leaf Class name (as `impl.field-capability/probe`).
  - Runtime suite, fmt, lint green. Other suites do not load the changed
    `test/runtime` files.
- 2026-11-06 code-shaper review pass 7: added 1 step to be addressed.
- 2026-11-06 code-shaper review pass 7 follow-up: addressed 1 review step.
  - `probe-builder/define-probe-class` takes the field type as a Class and
    gets the descriptor with `.descriptorString` (safe: the namespace loads
    only behind the D12 checks). `impl.capability/probe`,
    `impl.field-capability/probe` and the tests pass `Long/TYPE` or the
    leaf Class; the Java 8 string form and its comment are removed.
  - Runtime, fallback (3 configurations), cross-JVM, build suites, fmt,
    lint green. Fallback preview-on keeps its 1 `unknown` row.
- 2026-11-06 code-shaper review pass 8: no new steps.
