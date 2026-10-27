# Array verification and evolution

Status: direct-JVM verification complete for the recorded EA3 matrix; public
allocation tests pass in task 013, with final acceptance passed. Public
diagnostics are implemented in task 014;
[final acceptance](../../munera/closed/014-value-array-diagnostics/acceptance.md)
passed within the recorded JVM scope. Task 014 remains open for review.
Sequence construction remains unimplemented.
See the [design](../value-type-array-design.md) and [decisions](decisions.md).

## Observed qualities

Evidence: [typed-array investigation](../defvaluetype-design/typed-array.md),
Valhalla `27-jep401ea3+1-1`, Clojure `1.12.6`, macOS aarch64.

For 1,048,576 one-long elements, the restricted flat array retained about
8 MiB, versus 28 MiB for identity arrays and 36 MiB for ordinary value arrays.
Measured current-thread allocation was about 8/28/36 bytes per element.
Disabling array flattening removed the storage and allocation advantage.

Six extended timing runs measured median sequential traversal at 0.239
ns/element flat, 0.305 identity and 0.360 ordinary value. These are scoped
observations, not API performance targets. The two-long eligibility probe
did not flatten. Reference payloads, random access, concurrency and general
application speed are not established by this evidence.

The probe constructs arrays directly. Its allocation numbers do not describe
the proposed sequence constructor, which adds temporary storage.

## Semantic observations

The [semantic report](semantic-verification.md), [reproduction instructions](../../spike/value-array-semantics/README.md)
and [retained matrix](evidence/matrix-selected-slots/validation.edn) cover task 012.
Valid seeds permit lengths 0, 1 and 3 for zero-field, one-long, two-long and
reference-field value classes. Null and wrong-type seeds reject at all three
lengths. Native Clojure and Java stores enforce restrictions. Disabling
flattening preserves the tested semantic outcomes.

Null crashes all three diagnostics; String and primitive long arrays return
false. These native outcomes do not replace the proposed public input domain.
Value status, allocation eligibility, null restriction, atomicity and physical
flattening are separate observations. No new performance measurements were made.

## Public allocation verification

[Task 013 implementation notes](../../munera/closed/013-value-array-allocation/implementation.md)
record commands and structured Scry summaries for the implemented allocator:

- Native fixtures at lengths 0, 1 and 3, selected-slot Clojure and Java stores,
  and an independent Java value class: 10 tests, 399 assertions per layout.
- Six startup configurations, three fallback policies and both namespace
  require orders: 36 child JVMs, 612 assertions.
- Existing fallback suites on Java 8 and EA3 preview off/on: 17 tests each.
- JAR-only supported allocation and unsupported loading: 9 build tests,
  101 assertions, including the existing macro checks.

These checks use Temurin `1.8.0_492-b09` and Valhalla `27-jep401ea3+1-1`,
Clojure 1.12.6, macOS aarch64. They do not establish broader JDK support.
[Final combined regression and quality acceptance](../../munera/closed/013-value-array-allocation/acceptance.md)
passed. The plan below covers the full design; sequence checks remain future
work. Diagnostic results are recorded separately below.

## Public diagnostic verification

[Task 014 implementation notes](../../munera/closed/014-value-array-diagnostics/implementation.md)
record commands and retained structured results on the same JVMs and host:

- Direct JDK comparisons and ownership checks, together with allocation/layout
  regressions: 14 tests in each layout, 1728/1848 assertions.
- Six startup configurations, three policies and both require orders:
  36 children distinguish API absence, signatures and invocation access.
  Export-only checks confirm inspection does not require allocation capability.
  The diagnostic namespace passes 2 tests, 1455 assertions.
- Existing fallback/loading controls pass on Java 8 and EA3 preview off/on.
- JAR-only diagnostic controls and existing consumers pass: 9 build tests,
  103 assertions.

Invalid public inputs are tested in disposable JVMs with timeouts and clean
exit requirements. No native null diagnostic is called. Ordinary reference
arrays work with exports and without preview on EA3. Missing support or access
produces an error, not false properties.
[Final combined regression and quality acceptance](../../munera/closed/014-value-array-diagnostics/acceptance.md)
passed. The task remains open for review.

## Fitness conditions

| Decision | Intended quality | Required verification |
|---|---|---|
| D1 | Native interop and store safety | Read, replace and measure length through ordinary Clojure operations; reject later null and wrong-type stores. |
| D2 | Stable semantics | No ordinary-array fallback under any class-fallback policy; clear failure without array support. |
| D3 | Safe loading and local dependency | Require public namespaces on Java 8 and preview-disabled Valhalla; inspect optional dependency loading and test missing exports in fresh JVMs. |
| D4 | Correct construction | Preserve order and length; consume input once; validate seed, empty input, element types and lengths. |
| D5 | Honest layout reporting | Compare diagnostics with direct JDK inspection; accept a non-flat restricted atomic result. |

## Verification plan

1. Direct-JVM prerequisite complete on EA3 (task 012); retain as adapter regression coverage:
   allocator signature, access checks, zero-length allocation with a seed,
   eligible value classes and each diagnostic method's domain and meaning.
   Check whether any capability beyond value-class status is required.
2. Test both constructors with zero, one and several elements, primitive-field
   and reference-field value classes, explicit seeds and the inferred seed.
   Include wider classes without requiring flattening.
3. Test invalid component types, `nil`, incompatible elements, fractional,
   negative and out-of-range lengths, and empty input without a seed.
   Test large-length validation without attempting maximum-size allocation.
4. Use a finite lazy input with visible realization effects to verify one
   traversal. Verify original exceptions from input realization remain visible.
5. Verify `aget`, `aset`, `alength` and representative Java array interop.
   Check initial contents and post-write contents. Verify null and type store
   failures. Do not infer clone or copy-operation semantics without tests.
6. Run fresh JVMs with preview enabled/disabled, exports present/absent and
   flattening enabled/disabled. Include ordinary Java 8 namespace loading and
   repeated unsupported calls. Run all class-fallback policies without changing
   their existing behavior or warnings.
7. Test `array-info` on ordinary reference arrays, restricted arrays and invalid
   inputs. Verify unavailable inspection produces an error, not invented values.
8. Add packaged-source consumer checks and update API reference, usage guide,
   required flags and experimental-support limits. Run library regressions.

Use real JVM capabilities, not mocks. Scry supplies structured test results.
Keep VM-specific layout assertions in controlled checks, separate from portable
semantic assertions. A non-flat but valid array is a passing semantic result.

If performance is measured, distinguish seed allocation, sequence construction
and traversal. Retain payload, flags, JVM version and flattening diagnostics.
Do not require a speed ratio as API acceptance.

## Architectural assertions

| ID | Status | Relation and scope | Basis / confidence |
|---|---|---|---|
| A1 | observed | Internal restricted allocation permits flat one-long storage on the recorded EA setup. | Probe and retained reports; high within that scope. |
| A2 | observed | Native stores enforce null and type restrictions for tested fixtures in both layouts. | Task 012 evidence and task 013 selected-slot public API tests. |
| A3 | observed | D2 preserves null restriction for explicit-seed allocation. | Task 013 policy matrix; sequence construction remains pending. |
| A4 | observed | D3 isolates optional JDK dependencies from namespace loading. | Task 013 Java 8 and preview-disabled EA3 loading controls. |
| A5 | observed | Direct explicit-seed allocation supports length zero for tested value classes. | Task 012 allocation evidence; seed validation still applies. |
| A6 | inferred | Sequence construction can allocate more than the direct probe. | D4 temporary storage; amount unmeasured. |

## Risks and evolution

Internal APIs can change or disappear. Verify each supported JDK explicitly;
change the adapter rather than exposing internal methods as the public API.
No backward-compatibility layer is required by this design.

Module exports are an application deployment choice. The library must not
alter module access at runtime. Preview-enabled value artifacts retain their
existing JDK constraints; this API does not make them portable.

A public JDK replacement, wider flattening support or evidence that temporary
storage is costly can justify later work. Indexed construction, non-atomic
storage and wrappers require new contracts; they are not implicit extensions
of this design. Verified empty-array behavior supports the explicit-seed
proposal; it does not permit ordinary-array fallback.

## Implementation gates

See [remaining implementation gates](implementation-gates.md).
