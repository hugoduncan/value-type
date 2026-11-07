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

- Format row: the flag values, the version rule and the value-constructor
  order rule. The default row has all row keys. A non-default row holds only
  the keys that differ for one feature release or build. It overrides the
  default row (D15).
- Default row: the current rule. A build with no row uses it.
- Probe builder: makes probe class bytes with `java.lang.classfile` and the
  format row.
- Semantic self-check: checks of value semantics on probe instances.
- Classified absence: a probe observation that D12 accepts as capability
  absence.

## Scope

Include:

- One format namespace, `value-type.impl.jvm-format`, with the default row.
  The probe builder and the transform read it. No other row exists yet.
- One probe builder, `value-type.impl.probe-builder`, on
  `java.lang.classfile`, used by both probes. It loads only after the D12 API
  checks find `java.lang.classfile`.
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

## Format row

`value-type.impl.jvm-format/row` takes no arguments. It returns the complete
row of the running JVM. Until a second row exists, it returns the default
row. This task adds no lookup by feature release or build. The first
non-default row adds the lookup and merges that row over the default row.
Callers of `row` do not change.

Default row:

| Key | Value | Use |
|---|---|---|
| `:major` | `int` from `ClassFile/latestMajorVersion`, read once at namespace load | Class major version |
| `:minor` | `0xFFFF` (`ClassFile/PREVIEW_MINOR_VERSION`) | Class minor version |
| `:identity-class-flag` | `0x20` (`ACC_IDENTITY`) | The transform clears it. The builder does not write it. |
| `:strict-field-flag` | `0x800` (`ACC_STRICT_INIT`) | Set on each kept instance field |
| `:constructor-order` | `:fields-before-super` | Value-constructor order rule |

Constructor order rule: `:fields-before-super` means that a value
constructor writes its instance fields, then calls `Object.<init>`. It is a
value that each emitter asserts. It does not select emission. The transform
constructor emitter and the builder emit only this order. Each throws
`IllegalStateException` when `:constructor-order` has a different value.

Builder: `(probe-builder/probe-bytes owner descriptor restricted?)` returns
the bytes of a class with internal name `owner`, superclass `Object`, class
flags public and final, and the version `:major`, `:minor`. The class has one
public final field `value` of `descriptor` with `:strict-field-flag`, and one
public constructor that takes the field value. `restricted?` adds the
`NullRestricted` annotation to the field. The constructor loads a `J`
argument with `lload` and other arguments with `aload`. The parameters are
those of the current `impl.capability/probe-bytes` without `major`.

## Contract

- Report values keep their shape: `{:available? true}` or
  `{:available? false :reason r}`. New `:reason` values:
  - `:value-class`: `:probe-not-value-class`, `:failed-self-check`.
  - `:native-non-nil-field`: `:probe-not-value-class` and
    `:failed-self-check` from the value-class result;
    `:native-restriction-ignored` from the field probe.
  - `:array-allocation`: `:probe-not-value-class` and `:failed-self-check`
    from the value-class result; `:failed-self-check` from the array round
    trip.
- On the tested JVMs, all report rows of `value-type.capability-matrix-test`
  stay as they are.
- Other probe failures still propagate (D12).
- `value-type.core` and `value-type.capability` load without
  `java.lang.classfile` and without `clojure.asm` in `impl.capability`.

Internal results:

- The available value-class result is `{:available? true :probe-class c}`.
  `c` is the value probe Class: a value class with a public `long` field
  `value` and a public `(long)` constructor. `:probe-class` exists only when
  `:available?` is true. `report` drops it (D14).
- If the value-class result is available and has no `:probe-class`,
  allocation discovery throws `IllegalStateException`. It does not skip the
  round trip. A test that replaces `capability` with an available result
  before allocation discovery must supply a real probe Class.
- A new unavailable result is `{:available? false :reason r :detail d}`. No
  Throwable exists, so `d` is a fixed text:

  | Source | `:reason` | `:detail` |
  |---|---|---|
  | Value probe | `:probe-not-value-class` | `The JVM defined the probe class, but the probe class is not a value class.` |
  | Value probe | `:failed-self-check` | `The value-class probe failed the semantic self-check.` |
  | Field probe | `:native-restriction-ignored` | `The JVM accepted nil in a NullRestricted probe field.` |
  | Array round trip | `:failed-self-check` | `The value-array round trip failed the semantic self-check.` |

  The fallback warning, the policy `error` message,
  `core/require-value-capability!` and the field guard message use
  `:detail`. The field and array results inherit the value-class `:detail`.
- `require-allocation` message for `:failed-self-check`: `Value-array
  allocation is unavailable. ` followed by `:detail`. Other reasons keep the
  current message.

## Classification

Value probe (`impl.capability/detect`), in this order:

1. API checks: `:missing-classfile-api`, `:missing-value-predicate`
   (no change).
2. Version: define the builder probe (`J`, not restricted). An
   `UnsupportedClassVersionError` gives `:unsupported-class-version` (no
   change).
3. `(classify probe-class)`:
   1. The value predicate is false: `:probe-not-value-class`.
   2. `(passes-self-check? probe-class)` is false: `:failed-self-check`.
   3. Else: `{:available? true :probe-class probe-class}`.

`passes-self-check?` takes a Class with a public `long` field `value` and a
public `(long)` constructor. It makes instances 42, 43 and 42 with the
constructor. It returns true only when all D17 checks pass: each instance
reads its own value; 42 and 43 are not `identical?`; the two 42 instances are
`identical?`; no instance is `identical?` to nil. It does not read the value
predicate. For an identity class it returns false, because the two 42
instances are not `identical?`.

Field probe (`impl.field-capability/detect`): it runs only when the
value-class result is available. Else it returns the value-class result.
Thus `:probe-not-value-class` and `:failed-self-check` come only from the
value-class result. The field probe does not classify its leaf and holder
classes. `verify-enforcement` keeps its readback checks (failure:
`IllegalStateException`). Then it constructs the holder with nil:

- `NullPointerException`: `{:available? true}`.
- The construction succeeds: `:native-restriction-ignored`.
- A different exception propagates.

Array allocation (`impl.array/discover`): when the value-class result is
unavailable, discovery returns it (no change). Thus `:probe-not-value-class`
comes only from the value-class result. The round trip runs after the API,
signature and access checks succeed, so their reasons keep precedence.
Discovery calls `(passes-round-trip? allocator probe-class)`. `allocator` is
the `ValueClass/newNullRestrictedAtomicArray` Method that discovery found.
`probe-class` is `:probe-class` of the value-class result. The round trip,
with the `(long)` constructor of the probe Class:

1. Make instances `a` (42) and `b` (43).
2. Allocate a null-restricted array of length 2 with initial value `a`.
3. Write `b` at index 1. Read index 0 and index 1. Use
   `java.lang.reflect.Array`.
4. Check: element 0 is `identical?` to `a` and its `value` is 42; element 1
   is `identical?` to `b` and its `value` is 43.

`passes-round-trip?` returns true when all checks are true, else false.
True: discovery returns the available allocation result (no change of
shape). False: discovery returns `:failed-self-check`.

An exception in the self-check or in the round trip (allocation, write or
read) propagates (D12). The round trip removes `InvocationTargetException`
as `impl.array/invoke` does. Only a completed check with a false result gives
`:failed-self-check`.

Test entry points:

- `impl.capability/classify`, `impl.capability/passes-self-check?` and
  `impl.array/passes-round-trip?` are public functions of their internal
  namespaces. Tests call them directly.
- `classify` takes a Class and returns the value-class result.
  `passes-self-check?` takes a Class and returns a boolean.
- `passes-round-trip?` takes the allocator Method and a Class, and returns a
  boolean. Tests get the allocator from
  `(:allocator (impl.array/allocation-capability))`, so they need the export.
- `impl.field-capability/verify-enforcement` stays private. Tests call it
  through the var, as now. It takes the leaf Class and the holder Class.

## Architecture

- Load position: `impl.jvm-format` imports `java.lang.classfile.ClassFile`
  and loads only behind the D12 boundary. Only `impl.probe-builder` and
  `impl.transform` require it.
- Dependency edges:
  - `impl.probe-builder → impl.jvm-format`.
  - `impl.transform → impl.jvm-format`.
  - `impl.capability` and `impl.field-capability` resolve
    `impl.probe-builder/probe-bytes` only with `requiring-resolve`, after the
    API checks pass, in the pattern of the lazy loader resolution in `core`.
    Baseline JVMs never load the builder or the format namespace.
  - `impl.array` reads the probe Class from the value-class result. It has
    no edge to the builder or the format namespace.
  - `impl.jvm-format` requires no value-type namespace. It owns format data
    only.
- `impl.capability/probe-bytes` and `impl.capability/latest-major-version`
  are removed. The builder and the row replace them.
- Classification takes observations of real classes, so tests can drive it
  with real classes (no mocks).

Rejected: a probe that keeps `clojure.asm`; a fail-closed format table;
self-checks on each definition.

## Qualities and risks

- Better: correctness, maintainability, loading robustness.
- Cost: once per runtime, three reflective constructions in the value probe;
  two reflective constructions and one array of length 2 in allocation
  discovery.
- Risk: a too-strict self-check gives fallback on a good JVM. The checks use a
  `long` field, whose `==` is well defined.
- Risk: the transform and the probe must read the same row in the same way.

## Acceptance

1. `impl.jvm-format` holds the flag values, the version rule (major and
   minor) and the constructor order rule. `impl.capability`,
   `impl.field-capability`, `impl.probe-builder` and `impl.transform` contain
   none of these constants. `ClassFile/latestMajorVersion` and
   `ClassFile/PREVIEW_MINOR_VERSION` are version-rule constants. Standard
   class-file flags, for example `ClassFile/ACC_STATIC`, are not format-row
   constants.
2. `impl.capability` has no `clojure.asm` import. The existing Java 8 and
   isolated source-JAR load tests pass.
3. Self-check tests in the runtime suite (Valhalla EA3, `--enable-preview`
   and the export): `passes-self-check?` is true for the builder probe class
   and false for a `deftype` identity class with a `^long value` field.
   `passes-round-trip?` is true for the builder probe class.
4. Classification tests in the runtime suite, with real classes:
   - `classify` of the identity class of acceptance 3:
     `:probe-not-value-class`.
   - `classify` of a constant value class: `:failed-self-check`.
     `passes-round-trip?` of this class is false (discovery maps false to
     `:failed-self-check`). The test makes
     this class with `java.lang.classfile` and `jvm-format/row`: a value class
     with a public final `long` field `value` and a public `(long)`
     constructor that writes 0.
   - `verify-enforcement` of a builder leaf and a builder holder with
     `restricted?` false: `:native-restriction-ignored`.
5. `value-type.capability-matrix-test` and all runtime, fallback, cross-JVM,
   build and tooling suites pass unchanged in their expected rows, except
   these expected changes:
   - `value-type.runtime-test/capability-test` (`test/compatibility`):
     compares the available result without `:probe-class`.
   - `value-type.field-capability-test/malformed-probe-test`
     (`test/cross-jvm`): makes probes with `probe-builder/probe-bytes`
     (no major). The nullable-holder row expects the returned
     `:native-restriction-ignored` result, not `IllegalStateException`. The
     other three rows keep their expected errors.
   - `value-type.impl.field-capability-test/native-probe-enforcement-test`
     (`test/runtime`): uses `probe-builder/probe-bytes` (no major). Its
     assertions do not change.
   - `value-type.field-transfer-checks/consume` (`test/compatibility`):
     returns as `:capability` only `:available?` and `:reason` of the
     value-class result (`select-keys`). The cell child prints results with
     `prn`, and the parent reads them with `edn/read-string`. A printed Class
     reads back as a symbol; `select-keys` keeps the cell value plain
     data. `value-type.field-transfer-test` (`test/cross-jvm`) reads only
     `:reason` of `:capability`, and `:attempts`, so its assertions do not
     change.
   - `value-type.impl.transform-test` and `value-type.impl.loader-test`
     (`test/runtime`): each read of `@#'transform/ACC_IDENTITY` and
     `@#'transform/ACC_STRICT_INIT` (acceptance 1 removes these vars) reads
     `:identity-class-flag` and `:strict-field-flag` of `(jvm-format/row)`.
     Both namespaces require `value-type.impl.jvm-format`. Their direct
     reads of `ClassFile/latestMajorVersion` and
     `ClassFile/PREVIEW_MINOR_VERSION` can stay. Their assertions do not
     change.

   `bb clj-fmt:check`, `bb clj-kondo:lint`, `bb vale` and `bb api-docs --check`
   pass.
6. Docs: `report` docstring reasons, `doc/API.md` (generated), usage guide
   capability-report section, `CHANGELOG.md`, the embodiment gap table
   (remove D12, D15, D16 and the non-AOT part of D17), `mementum/state.md`.
   `ramora/defvaluetype-design/embodiment/runtime.md`: components table rows
   for `impl.jvm-format` and `impl.probe-builder`; the changed
   `impl.capability` responsibility (API checks, classification with the
   self-check, cached value-class result with the probe Class, lazy builder
   resolution); the dependency direction of the Architecture section; the
   Boundaries text on the class file format (a format change edits
   `impl.jvm-format`); the enforcement point of the Invariants row "Fallback
   needs no optional JDK APIs" (also the lazy builder resolution in
   `impl.capability` and `impl.field-capability`); the State text (the cached
   value-class result holds the probe Class, and thus its class loader, for
   the loaded library runtime).
   `ramora/defvaluetype-design/embodiment/runtime/fallback-interfaces.md`: the
   cached value-class map has `:probe-class` when available.
   `ramora/value-type-array-design/embodiment.md`: allocation discovery
   invokes the allocator once, in the round trip with the probe Class from the
   value-class result. A failed round trip gives `:failed-self-check`. The
   text states that the round trip is the D17 semantic check of the allocated
   array. It does not use the class-format detector as proof of array support
   (array D3 boundary).

Create `plan.md`, `steps.md` and `implementation.md` before execution.
