# Changelog

## [Unreleased]

- Add experimental [`value-type.array/make-array`](doc/usage.md#value-array-allocation)
  with an explicit non-null seed, native null-restricted atomic arrays and safe
  namespace loading on unsupported JVMs. Allocation requires the tested Valhalla
  `27-jep401ea3+1-1` build, `--enable-preview` and
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. There is no ordinary-array
  fallback or flattening guarantee.

- Add experimental [`value-type.array/into-array`](doc/usage.md#value-array-sequence-construction)
  with inferred or explicit seeds and the same allocation requirements above.
  Copy finite seqable input once in order using O(n) temporary references.
  Empty or nil input needs a valid explicit seed; the seed adds no output slot.
  Reject null or incompatible entries with indexed errors. Input failures remain
  visible and do not undo input effects. Infinite input is unsupported; memory
  can run out before the integer length limit. No speed or allocation gain is
  promised for sequence construction.

- Add experimental [`value-type.array/array-info`](doc/usage.md#value-array-diagnostics)
  for ordinary and restricted reference arrays on the tested Valhalla EA3 build.
  Report component class, component value status, null restriction, atomicity
  and actual flattening. Reject invalid input before capability checks; report
  unavailable inspection rather than unknown properties as false. Inspection
  requires the internal package export above; ordinary arrays do not require
  preview. Atomicity does not guarantee compound updates or safe publication.

- Add experimental [value-array JVM probes and validation commands](spike/value-array-semantics/README.md).
  These verify JVM behavior; they do not add a public array API. Reproduction
  deliberately crashes isolated JVMs for null diagnostic inputs.

- Add `defvaluetype` with value classes on supported Valhalla JVMs and
  ordinary `deftype` fallback on other JVMs.
- Add source-only JAR packaging and isolated consumer checks on Java 8
  and Valhalla.
- Add development quality checks and generated API documentation.
- Add release tooling for deployment credential checks and changelog
  validation, version stamping and release-note selection.
- Add local release preparation and notes-export commands with complete-history
  checks. Preparation commits only the changelog and does not publish.
- Add a manual release workflow with default preview, explicit JVM checks and
  fail-stop publication, plus maintainer release instructions.
