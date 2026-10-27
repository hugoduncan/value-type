# Steps

Follow [plan.md](plan.md) in order. Each item is one implementation action with
its verification target. Record commands, results and decisions in
`implementation.md` as work proceeds. Mark items complete only after implementation and verification.

## Slice 1 — Native capability (acceptance 5)

- [x] Add a uniquely named nested-value probe that proves valid construction, readback and native NullPointerException on null without constructor null checks.
- [x] Add separate lazy field-capability access behind the baseline-safe runtime boundary; verify namespace require does not force optional JDK loading.
- [x] Define narrow absence classifications for the field probe; verify unexpected linkage, verification and probe-invariant failures remain errors with real malformed fixtures.
- [x] Add isolated capability tests for Java 8, preview-disabled EA3 and preview-enabled EA3 without exports; require explicit expected results and zero child exit status.

## Slice 2 — Declaration contract (acceptance 1, 2, 5)

- [x] Record the field-specification shape, structured error keys/reasons and validation precedence in implementation.md, using existing class-name and error conventions.
- [x] Implement internal metadata normalization; test shorthand, explicit true, absent, false, nil and invalid values without enabling incomplete public construction.
- [x] Implement explicit tag resolution in the defining environment; test imported names, namespace-qualified references, missing/unresolved tags and forward references.
- [x] Implement eligibility validation; test primitives, identity classes, interfaces, abstract classes, arrays and enclosing-type references, including redefinition of the enclosing name.
- [x] Add an independently compiled external value-class fixture; verify eligible external Classes and library-defined Classes produce ordered field specifications.
- [x] Add declaration regression tests for mutable-field rejection, unrelated metadata and disabled-field primitive/type-hint behavior; assert error data and compiler cause chains where applicable.

## Slice 3 — Native runtime integration (acceptance 1–5)

- [x] Connect storage, constructor casts and owner accesses in one class transform; verify loaded native fields, mixed primitive slots, reads and nil/type rejection. Loader and companion integration remain below.
- [x] Extend capture-loader transport for explicit field specifications; verify empty specifications preserve the existing ordinary transformation.
- [x] Add an isolated concrete-storage emitter; verify descriptor replacement, annotation merging, retained field flags and invisible annotations, and nullable controls. Integration remains below.
- [x] Extend storage transformation to emit concrete descriptors and merge NullRestricted annotations; verify unrelated attributes and unmarked descriptors survive.
- [x] Add isolated constructor emission with concrete casts; verify original Object signatures, long/double slots and empty-storage behavior in class-file models. Native integration remains below.
- [x] Extend constructor rewriting with casts before restricted writes; verify Object parameters, argument order, two-slot indexing and original constructor-shape rejection remain intact.
- [x] Add an isolated field-access transform; verify selected Object instance references change for owner and companion byte models, with unrelated references and empty specifications unchanged. Loader integration and linkage verification remain below.
- [x] Update affected owner and generated-companion field-access descriptors; verify transformed classes link and unrelated methods/fields remain unchanged.
- [x] Add an internal native-field requirement guard; verify structured rejection, repeated rejection and independence from all fallback policies on Java 8 and EA3 with preview off/on. Public selection and receiving integration remain below.
- [x] Add an internal receiving-phase field guard; verify discarded expansion, uninvoked-function analysis and evaluation reject repeatedly across the JVM/policy matrix. Public placement and transfer remain below.
- [x] Connect declaration validation and native transformation to defvaluetype; verify enabled fields reject unavailable capability under every fallback policy without ordinary replacement.
- [x] Add selection and receiving compilation/evaluation guards for enabled declarations; verify discarded and transferred expansions cannot bypass native requirements.
- [x] Add public construction tests for both metadata forms through direct constructors, factories and reflection; assert success, NullPointerException and ClassCastException with ordinary reflection wrapping.
- [x] Add mixed-field tests covering every restricted position, multiple restrictions, nullable controls, long/double arguments and nested values containing nullable fields.
- [x] Add direct and reflective read tests that verify declared field types and unchanged constructor signatures; confirm supported construction needs preview but no exports.

## Slice 4 — Compiler and deployment (acceptance 4–7)

- [x] Add protocol/interface and Object-override fixtures; verify restricted fields retain correct method behavior.
- [x] Add inner-function, closure, companion-class and nested-control-flow fixtures; verify generated field references link and return expected values.
- [x] Add same-name/different-loader and redefinition cases; verify field resolution and construction use the intended Class rather than a binary-name match.
- [x] Extend fresh-JVM policy tests across Java 8 and EA3 with preview off/on under silent, warn and error; verify safe require, enabled rejection and unchanged ordinary fallback behavior.
- [x] Verify capable public macroexpand and macroexpand-1 producers cannot bypass unsupported receiving analysis or evaluation; repeat both attempts under all fallback policies on Java 8 and preview-disabled EA3.
- [x] Extend macroexpand and macroexpand-1 transfer fixtures to fresh receivers; verify capability enforcement at selection, receiving analysis and evaluation without weakening repeated rejection.
- [x] Add AOT producer fixtures with restricted fields and companions (including persisted companion reference rewriting); verify written class bytes retain native descriptors, annotation and constructor signatures.
- [x] Add a fresh source-free Clojure AOT consumer; assert fixture-source absence, valid construction, reads and native nil/type rejection.
- [x] Add a separately compiled Clojure consumer of the AOT artifacts; verify generated access code and factories work without producer sources.
- [x] Add a direct Java consumer compiled with matching EA3 javac; verify constructor success, reads and native nil/type rejection without exports.
- [x] Add incompatible-JVM AOT consumers on Java 8 and preview-disabled EA3; assert rejection without identity conversion under all fallback policies.
- [x] Run the field semantic suite in all four flattening on/off and normal/-Xint combinations without exports; retain commands and results separately from any layout observations.
- [x] Run the existing deftype compatibility corpus and fallback/AOT suites; resolve regressions before documentation completion.

## Slice 5 — Documentation and final verification (acceptance 8)

- [x] Update doc/usage.md with syntax, eligibility, exceptions, unsupported-JVM rejection, AOT limits and no flattening guarantee.
- [x] Update public docstrings and regenerate doc/API.md with bb api-docs; verify bb api-docs --check passes.
- [x] Update doc/development/runtime.md with reproducible field-matrix and consumer commands, including no-export controls.
- [x] Update Ramora embodiment with implemented boundaries and verified evidence; preserve design contracts and experiment limits.
- [x] Run bb clj-fmt:check, bb clj-kondo:lint and bb vale; resolve failures using the maintained quality workflow.
- [x] Run the full runtime, compatibility, build and tooling suites using their separate aliases/directories; verify Scry exit status and failure artifacts.
- [x] Complete implementation.md acceptance evidence for criteria 1–8 with exact commands, JVM builds, results and remaining limits; identify any unmet criterion before claiming completion.

## Implementation review follow-ups

- [x] Remove the redundant capture-loader installation for enabled fields in `src/value_type/core.clj` (`defvaluetype`). That branch installs a loader, then returns `native-field-deftype`, which resolves specifications and installs another loader before emitting the definition. Keep selection-time validation and capability rejection, but let the receiving boundary own the required installation. Verify direct definitions, both transferred expansion forms, companions and AOT output still pass.
- [x] Remove the unused `runtime/guarded-fields` macro and its standalone `receiving-field-guard-test`, or use it in the actual receiving path without duplicating guards. The production expansion uses `core/native-field-deftype`; only the standalone test calls `guarded-fields`. Keep analysis, evaluation and repeated policy rejection coverage on the public expansion path instead of maintaining a second unused compiler boundary and its process matrix.
- [x] Extend `ordered-specifications-test` in `test/runtime/value_type/field_eligibility_test.clj` with public construction coverage for the independently compiled `ExternalValue.java` fixture. The current test stops at `fields/specifications`, so it does not verify acceptance criterion 2 through the capture loader and native transform. Make the external class available to the compiler loader; define a restricted holder through `defvaluetype`, verify exact reflected field Class and valid readback, and check nil and wrong-type rejection without exports.

## Test review follow-ups

- [x] Preserve field metadata in the unsupported `:source` transfer path in `test/compatibility/value_type/field_transfer_checks.clj` (`consume`). Its `load-string` input uses `pr-str` without `*print-meta*`, unlike `produce` and `consume-supported`, so this case tests a declaration with its tag and restriction removed. Use metadata-preserving serialization for both receiver paths and rerun both expansion forms, repeated attempts and all unsupported JVM/policy cases.
- [x] Add a runtime companion fixture that reads restricted and nullable fields through an explicit captured receiver, and include it in the four-way field semantic matrix. `BehavioralFields/field-reader` in `test/runtime/value_type/restricted_fields_test.clj` and `restricted-companion-test` in `test/runtime/value_type/impl/loader_test.clj` use bare field symbols, which capture values rather than exercise companion-to-owner field references. The AOT fixture already uses explicit receiver access, but is not in this matrix. Verify escaping nested functions and both branches with flattening on/off and normal/interpreter execution; do not require a particular closure layout.

## Follow-up test review

- [x] Remove incidental ordering and bytecode-shape requirements from the new field tests. In `test/runtime/value_type/impl/field_storage_test.clj` (`concrete-storage-preserves-field-elements`) and `test/runtime/value_type/impl/transform_test.clj` (`integrated-storage-attributes-test`), compare annotation contents without requiring NullRestricted to precede existing annotations. In `restricted-constructor-code-test`, replace the exact load-slot vector and exact cast count with checks of constructor signatures, concrete writes and observable mixed-slot construction/type rejection (reuse the existing loaded-class tests). Keep native enforcement and attribute-preservation checks. Equivalent annotation ordering or valid constructor emission must not fail these tests; lost annotations, shifted arguments and missing type enforcement must still fail.

## Public declaration test review

- [x] Add public `defvaluetype` rejection tests for invalid restriction metadata and missing, unresolved/forward, primitive, identity, interface, abstract, array and enclosing-type tags. `fields_test.clj` checks these through internal helpers; only mutable declarations currently check public compiler wrapping. On preview-enabled EA3, assert `CompilerException` with an `ExceptionInfo` cause and the expected `:value-type/error`, `:class-name`, `:field` and `:reason`. Include an existing enclosing Class for the redefinition case and competing invalid fields to check the documented metadata-before-type precedence. Keep the helper tests, but verify that the public macro actually applies validation rather than leaving rejection to the JVM.

## Annotation-value test review

- [x] Verify annotation element values survive restricted-field transformation. In `test/runtime/value_type/impl/field_storage_test.clj` (`concrete-storage-preserves-field-elements`) and `test/runtime/value_type/impl/transform_test.clj` (`integrated-storage-attributes-test`), all fixture annotations have empty element lists. Assertions compare visible annotation types and invisible types or attribute counts, so lost or changed annotation values can pass. Add representative visible and invisible annotations with non-default element values; compare their types and element contents after isolated and composed transformation, without requiring annotation order. Keep the nullable/unmarked controls and native annotation checks. The tests must fail if an annotation retains its type but loses or changes its values.

## Transfer isolation test review

- [x] Isolate supported transfer cases in `test/compatibility/value_type/field_transfer_checks.clj` (`consume-supported`) and `field_transfer_test.clj`. All modes, expansion forms and retries currently reuse `user.TransferredField` and `->TransferredField` in one receiver. After the first success, construction checks can use that earlier definition even if a later operation does not define a class. Give each mode/expansion case a fresh receiving context, and verify that each repeated operation installs a new Class and that its factory constructs that Class. Keep both expansion forms, eval/source/function-analysis paths, all policies and native nil/type rejection. A skipped second definition or skipped function invocation must fail rather than pass against a previous factory.

## Documentation review follow-ups

- [x] Add nil-restricted fields to `README.md`. It has no example or feature summary, and its opening and fallback section promise ordinary `deftype` on unsupported JVMs without the enabled-field exception. Show a valid tagged nested-value declaration, link to `doc/usage.md#native-nil-restricted-fields`, and state that enabled fields require native support under every policy, need preview but no internal export, and do not guarantee flattening.
- [x] Add an entry under `[Unreleased]` in `CHANGELOG.md` for native `:nil-restricted` fields. Describe the opt-in metadata and explicit value-class tag, native nil/type rejection and unsupported-JVM rejection under every fallback policy. Link to the usage contract and retain the experimental JVM and layout limits. The current changelog records only ordinary class fallback and array features.
- [x] Update the existing Ramora architecture to agree with the field design. `ramora/defvaluetype-design.md` and `ramora/defvaluetype-design/fallback.md` still describe unsupported definitions as always subject to ordinary fallback; scope that path to definitions without enabled restrictions. In `ramora/defvaluetype-design/embodiment/runtime.md`, update the components, errors, selection/receiving flow and interfaces, or link to the field design with explicit scope. Remove the now-false invariants that the loader changes only the target and the transform changes only version/flags/constructor, and the claim that the transform does not depend on a class loader. Verify against `core/native-field-deftype`, `loader/capture-loader` and the loader-aware `transform/->value-class` arity, including companion rewriting.
- [x] Qualify older fallback and macroexpansion guidance in `doc/usage.md` and the `defvaluetype` docstring in `src/value_type/core.clj`, then regenerate `doc/API.md`. The general fallback statements omit the enabled-field exception; the usage error guidance presents `(eval (macroexpand form))` as a value-class failure without distinguishing ordinary definitions from restricted definitions, whose receiver-local installation supports this path. Keep ordinary-definition limits, but explicitly distinguish the verified restricted-field behavior. Check the revised text against `field-capability-test` and `field-transfer-test`, and run `bb api-docs --check`.

## Follow-up documentation review

- [x] Remove stale field exclusions and deployment guidance from the remaining design details. `ramora/defvaluetype-design/purpose.md` excludes null-restricted fields and lists them as future work; `ramora/defvaluetype-design/qualities.md` also calls them out of scope. Link these sections to the native field contract and distinguish eligible nested value fields from excluded general non-null references. In `doc/development/runtime.md` under “Build a local JAR”, qualify “other supported Clojure JVMs use the fallback policy”: enabled restrictions reject unavailable support under every policy. Check these statements against `defvaluetype` and the field capability tests.
- [x] Update the linked decision details, not only the runtime embodiment. `ramora/defvaluetype-design/decisions/compiler-and-transform.md` D1–D6 still describe selection-time installation for all value definitions, target-only byte changes, unchanged method bytecode and attributes, and constructor writes without selected reference casts. Describe or explicitly scope the ordinary path, and link the restricted path's receiving loader, storage/annotation changes, owner/companion references and preserved Object constructor parameters. In `decisions/validation-and-errors.md` D9, scope the separate-expansion failure and capability-to-fallback statements to ordinary definitions. Also qualify the target-only tooling-loader claim in `risks-and-evolution.md`. Verify against `native-field-deftype`, `capture-loader`, the field transforms and the supported transfer tests; retain the ordinary-path limits.

## Qualities documentation review

- [x] Correct the Performance row in `ramora/defvaluetype-design/qualities.md`. Its fitness condition says “Method bytecode is the same as deftype”, but enabled restricted fields require changed field-reference descriptors in owner and companion methods. Scope unchanged method bytecode to ordinary definitions and describe preserved method behavior for restricted definitions. Keep the recorded ordinary method-call benchmark separate from restricted-field performance: the field semantic matrix establishes correctness, not equal call time or zero added cost. Check against `impl.field-access/rewrite`, the composed transform and the evidence limits in the native field design.

## Code-shaper review follow-ups

- [x] Give owner and companion transforms one shared ClassFile configuration function. `impl.transform/->value-class` and `impl.field-access/rewrite-class` repeat the same optional-loader branch and hierarchy-resolver construction. This is one stack-map resolution policy with two maintenance sites; a change to only one can break dynamic-type branch merges. Keep the helper behind the optional JDK boundary, preserve default behavior when no loader is supplied, and use it from both transforms. Verify owner methods, explicit captured-receiver companion reads and source-free AOT with the existing field tests; retain safe public loading on Java 8.
- [x] Extract the repeated restricted AOT producer setup in `test/compatibility/value_type/field_aot_test.clj` into a local helper. All five tests separately build the producer classpath, compilation form and process options, then check the same exit/stderr contract. Keep producer compilation and its success gate in one place, while leaving source deletion, consumer classpaths and consumer assertions explicit in each test. Do not share compiled artifacts between tests or move deletion before the persisted-byte inspection. Run the complete `value-type.field-aot-test` namespace to verify persisted bytes, source-free Clojure/Java consumers and incompatible receivers.

## Follow-up code-shaper review

- [x] Share the identical `annotation-contents` helper in `test/runtime/value_type/impl/field_storage_test.clj` and `test/runtime/value_type/impl/transform_test.clj` through a runtime-only test helper. Both copies define the same annotation comparison representation, including named constant values and occurrence counts; changes to that representation currently need two edits. Keep fixture construction and expected values in each test, retain order-independent comparisons and duplicate counts, and keep ClassFile imports out of baseline compatibility helpers. Run both test namespaces to verify isolated and composed annotation preservation.
