# Acceptance evidence map

This map identifies reusable tests, not proof of the new contract. All current
field fixtures use the native-only contract. Keep probe checks independent from
public declaration checks when changing these fixtures.

Paths below are relative to `test/`. Namespace names use the `value-type` prefix.

| Acceptance | Reusable evidence | Missing evidence for task 017 |
| --- | --- | --- |
| 1 — Declarations | `compatibility/value_type/fields_test.clj`: metadata normalization, validation order, disabled behavior, enclosing types and unrelated metadata. `runtime/value_type/field_eligibility_test.clj`: ordered specifications and public errors. | New key, presence-based old-key errors, concrete identity and fallen-back tags, both metadata forms and public structured errors across JVMs. |
| 2 — Construction | `runtime/value_type/restricted_fields_test.clj`: public construction. `compatibility/value_type/field_aot_test.clj`: separate Java consumer. | Generated checks in actual constructors on both representations; shared factory/direct/reflection/Java cases with concrete final storage and Object parameters. |
| 3 — Policy | `compatibility/value_type/runtime_test.clj`: policy, public definitions and expansion warning timing. `field_capability_test.clj`: real capability matrix and public policy checks. | Replace native-only public rejection expectations; retain probe checks. Add nested baseline declarations and value owners with identity tags or controlled native absence under every policy. |
| 4 — Field behavior | `runtime/value_type/restricted_fields_test.clj`: mixed fields, methods and companions. `impl/field_access_test.clj`, `impl/field_storage_test.clj`, `impl/transform_test.clj`: references, attributes and constructor shapes. | Mixed native/generated/nullable fields; generated checks across two-slot arguments; same semantic fixtures on identity output. |
| 5 — Boundaries | `compatibility/value_type/runtime_test.clj`: public require. `field_capability_test.clj`: malformed probe errors. `runtime/value_type/impl/loader_test.clj`: transform/link errors. `build/build_test.clj`: source JAR and isolated consumer. | Baseline-safe capture and ASM backend; Java 8 class format; generated portable fields in packaged consumers; failures must not retry weaker output. |
| 6 — Compilation | `compatibility/value_type/field_transfer_test.clj` and `transfer_test.clj`: supported/unsupported transfers. `runtime/value_type/impl/loader_test.clj`: redefinition and Class mismatch. | Fixed generated/native modes, identity receiving loaders, discarded constrained expansions and exact Class identity for all modes. |
| 7 — AOT | `compatibility/value_type/field_aot_test.clj`: persisted output, source-free loading, separate Clojure/Java consumers and incompatible receivers. `aot_test.clj`: ordinary fallback timing. | Identity producer without preview/strict policy; Java 8 javac; generated-only value artifacts; mixed modes; receiving policy without representation upgrade. |
| 8 — Matrix | `compatibility/value_type/jvm_config.clj` and `jvm_runner.clj`: explicit child JVMs. Runtime field matrix in `doc/development/runtime.md`. | Cross product of policy, representation and enforcement; controlled absence labelled separately; generated normal/interpreter runs. Existing flattening controls prove only native semantics. |
| 9 — Delivery | `build/build_test.clj`; `tooling/value_type/api_docs_test.clj`, `lint_config_test.clj`, `prose_test.clj`; separate runtime, compatibility and release suites. | Updated public docs, generated API and embodiment; full regression and quality runs after integration. |

## Feasibility gate

Before production changes, use real declarations and real JVM execution, without
mocked transformers or loaders. The experiment must prove constructor enforcement,
concrete storage, mixed slots, owner/companion reads and source-free identity AOT.
Value-owner generated enforcement is a separate gate. A passing native probe or
metadata test is not evidence for either gate.

The current capture namespace eagerly loads the optional value transformer and
uses `java.lang.constant.ClassDesc`. It cannot serve as the Java 8 experiment
boundary unchanged. Keep the experiment isolated until both backend gates pass.
