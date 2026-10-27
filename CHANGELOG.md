# Changelog

## [Unreleased]

- Add experimental [`value-type.array/make-array`](doc/usage.md#value-array-allocation)
  with an explicit non-null seed, native null-restricted atomic arrays and safe
  namespace loading on unsupported JVMs. Allocation requires the tested Valhalla
  `27-jep401ea3+1-1` build, `--enable-preview` and
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. There is no ordinary-array
  fallback or flattening guarantee. Public diagnostics and sequence construction
  are not included.

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
