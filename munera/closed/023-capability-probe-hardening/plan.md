# Plan: capability probe hardening

## Approach

Bottom-up, in dependency order. Each slice compiles and the full test
matrix passes at each slice boundary, except where a later slice completes
an expected change. The last step of each slice runs the suites that its
changes reach (see Verification); a suite that a slice does not run does
not load the changed code.

Key decisions:

- Build `impl.jvm-format` and `impl.probe-builder` first, with direct
  runtime tests, before any caller changes. This isolates the
  `java.lang.classfile` emission risk (annotation visibility, stack maps)
  from the classification logic. The slice 1 builder tests also build the
  malformed `I` probe (an `aload` of an `int` slot), the only probe with
  the stack-map risk.
- `impl.jvm-format/row` returns a complete map; `:major` reads
  `ClassFile/latestMajorVersion` once at namespace load. The namespace
  imports `ClassFile`, so it stays on the transform side of the D12
  boundary. Only `impl.probe-builder` and `impl.transform` require it.
- `impl.capability` and `impl.field-capability` get the builder only with
  `requiring-resolve`, in the `core` lazy-loader pattern:
  `impl.capability` after its API checks, `impl.field-capability` in
  `probe` (it runs only when the value-class result is available). This removes the `clojure.asm` import and keeps baseline JVMs
  from loading the builder.
- `NullRestricted` goes on the field with
  `RuntimeVisibleAnnotationsAttribute` (the asm probe uses a
  runtime-visible annotation; `native-probe-enforcement-test` depends on
  it). A committed runtime test parses the builder bytes with
  `ClassFile/of` and finds the annotation in the field attribute. It does
  not use reflection: `jdk.internal.vm.annotation` is not exported, so a
  reflective read can fail for a reason that is not the attribute.
- The value-class result gains `:probe-class` when available; `impl.array`
  reads the probe Class from that result and has no edge to the builder.
- Unavailable self-check results carry a fixed-text `:detail`; the field
  and array results inherit the value-class `:detail`.
- Convert the transform and switch the field probe to the builder in the
  same slice that removes the constants and `probe-bytes` from
  `impl.capability`, so the one-owner acceptance (1) holds and
  `value-type.core` loads at the slice boundary. The `transform_test` and
  `loader_test` private-var reads (`@#'transform/ACC_IDENTITY`,
  `@#'transform/ACC_STRICT_INIT`) and the two field-probe tests change in
  the same slice. The `impl.transform` docstrings name the row keys, not
  `ACC_IDENTITY` and `ACC_STRICT_INIT`. A grep over the four namespaces
  verifies acceptance 1. The `:constructor-order` assertion has one owner,
  `impl.jvm-format/require-constructor-order!`, which both emitters call;
  the grep has no `:fields-before-super` match in the four namespaces.
- `verify-enforcement` keeps its exception mapping; only the accept-nil
  branch changes from `IllegalStateException` to
  `:native-restriction-ignored`.
- The array round trip unwraps `InvocationTargetException` as
  `impl.array/invoke` does; only a completed false check gives
  `:failed-self-check`.
- Tests use real classes through the public entry points `classify`,
  `passes-self-check?` and `passes-round-trip?`; no mocks.
- Test homes (all `test/runtime`):
  - `value-type.impl.jvm-format-test` (`impl/jvm_format_test.clj`): row
    tests.
  - `value-type.impl.probe-builder-test` (`impl/probe_builder_test.clj`):
    builder tests, with the `NullRestricted` parse check.
  - `value-type.impl.capability-test` (`impl/capability_test.clj`):
    `passes-self-check?` and `classify` tests.
  - `value-type.impl.field-capability-test` (existing): the
    `:native-restriction-ignored` test of `verify-enforcement`.
  - `value-type.impl.array-test` (`impl/array_test.clj`):
    `passes-round-trip?` tests.
  - Shared fixtures: `value-type.probe-test-support`
    (`probe_test_support.clj`, next to `array_test_support.clj`). It
    defines, once at namespace load: `IdentityLong` (a `deftype` with a
    `^long value` field), `builder-probe-class` (the builder `J`
    non-restricted probe Class) and `constant-value-class` (the constant
    value class of acceptance 4, made with `java.lang.classfile` and
    `jvm-format/row`). The capability and array test namespaces require
    it. It loads only in `test/runtime`.
- The stale page `mementum/knowledge/java-lang-classfile.md` (flag owner,
  `@#'ns/ACC_IDENTITY` test reads) is not edited in this task.
  `implementation.md` records it for `extract-task-knowledge`, which
  updates knowledge pages after the implementation review.

## Risks

- `java.lang.classfile` emission differences from the asm probe: annotation
  visibility, stack maps, preview minor version. Mitigation: slice 1 tests
  the builder bytes directly on the Valhalla EA3 runtime before callers
  change, with a build-only test of the malformed `I` probe. If EA3
  rejects that build, add `ClassFile$StackMapsOption/DROP_STACK_MAPS` to
  the builder in slice 1 (noted in implementation.md; did not reproduce on
  JDK 25).
- A too-strict self-check gives fallback on a good JVM. The checks use a
  `long` field, whose `==` is well defined. The runtime suite validates
  on EA3.
- `:probe-class` (a Class) in the cached result crosses cell-child `prn`
  boundaries. `prn` prints a Class as its name, and `edn/read-string`
  reads it back as a symbol, so the cell reader does not fail.
  `runtime-test/capability-test` must still drop `:probe-class` (the
  symbol differs from `{:available? true}`). Design acceptance 5 lists
  the `field_transfer_checks/consume` `select-keys` change: it keeps the
  cell value plain data, without the generated probe Class name.
  `field-transfer-test` reads only `:reason`, so its assertions do not
  change.
- The constant-value-class test fixture (constructor writes 0) needs
  `java.lang.classfile` emission in a test namespace; keep it in
  `test/runtime` where the API exists.
- The array round-trip tests need the `jdk.internal.value` export; they
  run under the export configuration (`release.yml` adds it), not plain
  `:test`.

## Verification

The full test matrix is the suites of design acceptance 5. The commands
are those of `.github/workflows/release.yml`:

- runtime: `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m
  scry.cli --dir test/runtime`.
- fallback (`:fallback-test`, `test/compatibility`), in three
  configurations: fallback-java8 `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA"
  clojure -M:fallback-test`; fallback-preview-off
  `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test`;
  fallback-preview-on `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure
  -J--enable-preview -M:fallback-test`. "Fallback suite" means all three.
- cross-JVM: `JAVA_CMD="$VALUE_TYPE_BASELINE_JAVA" clojure
  -M:cross-jvm-test`. It holds `value-type.capability-matrix-test`.
- build: `JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:build-test -m
  scry.cli --dir test/build`. It holds the isolated source-JAR consumer
  test (`isolated-consumer-test`).
- tooling: `clojure -Srepro -M:quickdoc:tooling-test -m scry.cli --dir
  test/tooling`.

Suites per slice:

1. runtime. No `src` namespace requires `impl.jvm-format` or
   `impl.probe-builder` yet.
2. runtime, fallback, cross-JVM, build.
3. runtime, fallback, cross-JVM, build.
4. runtime, fallback, cross-JVM, build.
5. and 6. The full test matrix.

## Slice order

1. Format row and probe builder: `impl.jvm-format`, `impl.probe-builder`,
   direct runtime tests for both, with the build-only malformed `I`
   probe test.
2. Value probe classification: `impl.capability` rewrite — lazy builder
   resolution, `classify`, `passes-self-check?`, `:probe-class`,
   `:detail`; remove `probe-bytes`, `latest-major-version` and the
   `clojure.asm` import; switch `impl.field-capability/probe` to the
   builder via `requiring-resolve`, and `malformed-probe-test` and
   `native-probe-enforcement-test` to `probe-builder/probe-bytes` (no
   major; expected results unchanged); convert `impl.transform` (code
   and docstrings), `transform_test` and `loader_test` to the row; verify
   acceptance 1 by grep; create
   `value-type.probe-test-support`; update
   `runtime-test/capability-test`.
3. Field probe: `impl.field-capability` — `:native-restriction-ignored`
   branch; the nullable-holder row of `malformed-probe-test`; the
   `verify-enforcement` runtime test.
4. Array round trip: `impl.array` — `passes-round-trip?`, the
   `IllegalStateException` guard for an available result without
   `:probe-class`, the `require-allocation` `:failed-self-check` message.
5. Downstream contract: `report` docstring reasons and the regenerated
   `doc/API.md` (it holds the `report` docstring, so `bb api-docs --check`
   passes at the slice boundary), `field_transfer_checks/consume`
   `select-keys`, capability-matrix and full-suite verification.
6. Docs and design artifacts: usage guide, `CHANGELOG.md`, embodiment gap
   table, runtime and array embodiment pages, `fallback-interfaces.md`,
   `mementum/state.md`.
