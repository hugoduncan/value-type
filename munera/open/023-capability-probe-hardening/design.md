# Capability probe hardening

## Intent

Make capability detection survive class-format change, `clojure.asm` change
and JVM semantic defects, with one place for the format rules. Realize
defvaluetype D15, D16, D17 (object, nil and array checks) and the D12
classified absences.

Priorities, in order: no silent wrong value semantics; public namespaces load
on every JVM; one owner of the format rules; defect visibility for
unclassified failures.

## Problem and context

- The JEP 401 flag values and the version rule are in two places:
  `impl.capability/probe-bytes` (`0x800`, `0xffff0000 | major`) and
  `impl.transform` (`ACC_IDENTITY`, `ACC_STRICT_INIT`, `latestMajorVersion`,
  `PREVIEW_MINOR_VERSION`). Each format change edits both, and they can
  disagree.
- `impl.capability` imports `clojure.asm` at namespace load. `value-type.core`
  loads it on every JVM. If Clojure changes or removes `clojure.asm`, the
  public namespaces do not load anywhere. `impl.field-capability` uses the
  same `probe-bytes`.
- The value probe checks only the value predicate and one field read. A JVM
  that defines value classes with wrong `==`, wrong field values or wrong
  arrays passes, and every definition is silently wrong.
- Two known JVM states throw invariant errors instead of a classified
  absence: a probe class that is defined but is not a value class, and a JVM
  that accepts null in the `NullRestricted` probe field.

Source: residual (stressor) analysis, residues-skill
`examples/value-type/recommendations.md`. Design: defvaluetype D12, D15, D16,
D17 (`ramora/defvaluetype-design/decisions/probe-and-format.md`,
`fallback/capability-boundary.md`, `fallback/selected-detector.md`).

## Concepts

- Format row: flag values, version rule and value-constructor order rule for
  one feature release or build.
- Default row: the current rule. Used for each build with no row.
- Probe builder: makes probe class bytes with `java.lang.classfile` and a
  format row.
- Semantic self-check: checks of value semantics on probe instances.
- Classified absence: a probe observation that D12 accepts as capability
  absence.

## Scope

Include:

- One format namespace with the default row. The value probe, the field
  probe and the transform read it. No other row exists yet.
- One probe builder on `java.lang.classfile`, used by both probes. It loads
  only after the D12 API checks find `java.lang.classfile`.
- `impl.capability` with no `clojure.asm` import.
- Value probe self-check: instances 42, 43, 42; own values; 42 and 43 not
  `identical?`; the two 42 instances `identical?`; no instance `identical?`
  to nil. Failure: `:failed-self-check`.
- Array self-check in allocation discovery: allocate a small null-restricted
  array of the probe class, write and read back. Failure: allocation
  unavailable with `:failed-self-check`.
- Classified absences: `:probe-not-value-class` (value probe),
  `:native-restriction-ignored` (field probe; generated enforcement applies).
- The new reasons in the capability report contract and docs.

Exclude:

- The AOT initialization check of D17 (task 026, after the receiving
  expansion).
- The value-class switch D18 (task 025).
- Format rows other than the default; any change of the default rule.
- `impl.identity-backend`: it keeps `clojure.asm` (D16).
- A `:unknown-jvm-format` reason (rejected in D15).

## Contract

- Capability results keep their shape. New `:reason` values:
  - `:value-class`, `:native-non-nil-field`, `:array-allocation`:
    `:probe-not-value-class`, `:failed-self-check`.
  - `:native-non-nil-field` also: `:native-restriction-ignored`.
  - `:array-allocation` also: `:failed-self-check` from the array round trip.
- On the tested JVMs, all report rows of `value-type.capability-matrix-test`
  stay as they are.
- Other probe failures still propagate (D12).
- `value-type.core` and `value-type.capability` load without
  `java.lang.classfile` and without `clojure.asm` in `impl.capability`.

## Architecture

- The format namespace owns format data only. It has no dependency on the
  probes or the transform.
- The probe builder is resolved lazily, in the pattern of the lazy loader
  resolution in `core`, so baseline JVMs never load it.
- The internal value-class result can carry the probe Class for the array
  check. The report already selects only `:available?` and `:reason` (D14).
- Classification takes observations of real classes, so tests can drive it
  with real identity classes (no mocks). An identity class gives "not a value
  class" and fails "equal instances are `identical?`".

Rejected: a probe that keeps `clojure.asm`; a fail-closed format table;
self-checks on each definition.

## Qualities and risks

- Better: correctness, maintainability, loading robustness.
- Cost: once per runtime, three reflective constructions and one small array.
- Risk: a too-strict self-check gives fallback on a good JVM. The checks use a
  `long` field, whose `==` is well defined.
- Risk: the transform and the probe must read the same row in the same way.

## Acceptance

1. One namespace holds the flag values, the version rule and the
   constructor order rule. `impl.capability`, `impl.field-capability` and
   `impl.transform` contain none of these constants.
2. `impl.capability` has no `clojure.asm` import. The existing Java 8 and
   isolated source-JAR load tests pass.
3. Self-check tests: the check accepts the EA3 probe class and rejects a real
   identity class with the right failure. The array round trip passes on EA3
   with the export.
4. Classification tests cover `:probe-not-value-class`,
   `:native-restriction-ignored` and `:failed-self-check` with real
   classes.
5. `value-type.capability-matrix-test` and all runtime, fallback, cross-JVM,
   build and tooling suites pass unchanged in their expected rows.
   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
6. Docs: `report` docstring reasons, `doc/API.md` (generated), usage guide
   capability-report section, `CHANGELOG.md`, the embodiment gap table
   (remove D12, D15, D16 and the non-AOT part of D17), `mementum/state.md`.

Create `plan.md`, `steps.md` and `implementation.md` before execution.
