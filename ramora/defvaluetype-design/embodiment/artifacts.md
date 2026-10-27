# Artifact embodiment

## Project artifacts

- `deps.edn`: Clojure 1.12.6. Alias `:test` with `--enable-preview` in
  `:jvm-opts`. The JDK is a Valhalla early-access build (spike:
  `~/.local/jdks/jdk-27.jdk/Contents/Home`). Alias `:bench`: extra
  path `bench`, criterium `0.5.245-ALPHA`, `--enable-preview` and the
  criterium blackhole JVM options (`-XX:+UnlockExperimentalVMOptions`,
  `-XX:CompileCommand=blackhole,criterium.blackhole.Blackhole::consume`).
- `deps.edn` build aliases: `:build` selects `build` with tools.build
  `0.10.10`; `:build-test` adds the separate build suite and preview
  support. Build dependencies are not runtime dependencies.
- `build.clj`: source-only JAR assembly, target cleanup and credential-checked deployment. See
  [Library packaging](#library-packaging).
- `LICENSE`: full EPL-2.0 text, also included at the JAR root.
- `test-build/build_test.clj`: build contract, real CLI, input
  preservation and isolated JAR consumer checks. See the development guide
  [build-test command](../../../doc/development/runtime.md#run-the-build-tests).
- `src/value_type/core.clj`, `src/value_type/impl/loader.clj`,
  `src/value_type/impl/transform.clj`.
- `src/value_type/impl/capability.clj`, `src/value_type/impl/runtime.clj`:
  baseline-safe capability and policy implementation.
- `test/value_type/...`: unit, integration and AOT tests (D11).
- `test-fallback/value_type/...`: explicit fresh-JVM policy, transfer and AOT
  tests. Alias `:fallback-test` adds no preview flags or value-only tests.
- `bench/value_type/bench.clj`: the method-call benchmark
  (`value-type.bench`, D11).
- `README.md`: usage, JDK requirement, semantic differences from
  `deftype`.

## Development quality workflow

Development tools do not change runtime dependencies, the library JDK
minimum or source-only packaging. `mise.toml` pins the tools and a Java 21
JVM without preview. The `deps.edn` aliases `:quickdoc` and `:quickdoc-test`
add Quickdoc and focused Scry tests only for development. See the development guide
[tool setup](../../../doc/development/quality-checks.md#install-development-tools).

`bb.edn` exposes formatting, dependency-config import, clj-kondo lint,
Vale and API generation tasks. Their implementation is in `bb/value_type/`;
focused tests are in `test-quickdoc/`. See the development guide
[commands and check scopes](../../../doc/development/quality-checks.md#quality-checks), including
excluded paths and warning/error handling.

`doc/API.md` is generated from docstrings in `src/value_type/core.clj` and
`src/value_type/array.clj`, with configuration and introductory prose from
`bb/value_type/api_docs.clj`. It documents the `value-type.core/defvaluetype`
macro and the `value-type.array/make-array`, `value-type.array/into-array` and
`value-type.array/array-info` functions, not private vars,
implementation namespaces or the expansion helper `check-value-class`. Run `bb api-docs` to regenerate it, or
`bb api-docs --check` to detect missing or stale output without writing.
Change the sources, not the generated file.

`.pre-commit-config.yaml` selects local scripts in `.pre-commit-hooks/`
for formatting and project lint. Formatting can edit files and stop a commit;
hooks never stage edits. See the development guide
[hook setup and edit behavior](../../../doc/development/quality-checks.md#install-commit-hooks).

`.github/workflows/ci.yml` runs on pull requests and pushes to `master`.
It prepares dependency imports, then checks formatting, code lint, prose,
API freshness and focused tooling and release tests. It checks that tracked files remain
unchanged; it does not repair files. This quality CI does not run the separate
runtime suites: [library tests](../../../doc/development/runtime.md#run-the-tests), the
[fallback matrix](../../../doc/development/runtime.md#verified-fallback-matrix) and
[build tests](../../../doc/development/runtime.md#run-the-build-tests) need their documented
JVM configurations.

## Library packaging

`build.clj` creates `target/value-type-0.1.N.jar` for
`org.hugoduncan/value-type`. The JAR contains library sources, Maven
project and runtime dependency metadata, and the EPL-2.0 license.
It excludes compiled classes, bundled dependencies and development
files. Packaging does not load or AOT-compile library namespaces.
The metadata basis excludes user configuration and development aliases.

See the development guide [local JAR build guide](../../../doc/development/runtime.md#build-a-local-jar)
for build and clean commands, return values, Git requirements, version
limits and the target-deletion warning. `N` is the Git commit count;
shallow history gives a partial count, and branches can share a version.
Both commands remove existing `target/` output, including benchmark
results. This creates a local artifact, not an installed or published
release.

Source-only library packaging is distinct from
[D7 consumer AOT output](../decisions.md#d7-aot-output-deployment).
Consumers can still compile their value types ahead of time; those
class files require the same JDK feature release used to compile them.
JAR consumers need runtime dependencies. Value-class creation needs a
supported JEP 401 Valhalla JDK with `--enable-preview`. Value-array allocation
also needs `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
Allocation has no ordinary-array fallback. Array inspection needs the same
export and a supported JVM. Ordinary reference-array inspection does not need
preview on the tested EA3 build; value-class creation and restricted-array
allocation still need preview. See the
[diagnostic guide](../../../doc/usage.md#value-array-diagnostics).
Fallback class creation and namespace
loading need only the supported Clojure runtime and ordinary JDK interfaces, subject
to the class fallback policy. See the usage guide
[value-array requirements](../../../doc/usage.md#value-array-allocation).

Isolated source-JAR consumers passed in four configurations:

- Valhalla EA3 with preview and exports: allocation, initial contents, native
  reads and writes, rejected null and wrong-type stores, and complete ordinary
  and restricted array diagnostics. Sequence construction covers both arities,
  ordered contents and explicit-seed empty input.
- Java 8 without flags and EA3 without preview or exports: namespace loading,
  repeated unavailable-allocation checks and unavailable Object-array inspection.
- EA3 with exports but without preview: Object-array inspection before value
  creation, while allocation remains unavailable.

All three unsupported-allocation configurations, including export-only EA3,
reject both sequence arities repeatedly before value creation and without input
effects. They also check that null, non-array and primitive-array diagnostic
inputs report `:invalid-array` before capability checks. All four configurations retain the macro checks and verify that the
core and both array source resources resolve from the exact built JAR, without
checkout sources. See the development
[build-test guide](../../../doc/development/runtime.md#run-the-build-tests).

Fallback AOT fixes identity representation and enforces receiving policy where
the form is evaluated, including on capable JVMs. Top-level forms enforce
policy during namespace initialization. Forms inside functions allow namespace
loading and enforce policy on invocation. Direct class access that bypasses
the guarded form is not protected; failed initializer retries do not re-enter
the guard. See the README
[consumer JDK requirement](../../../README.md#jdk-requirement).

Deployment uses tooling-only deps-deploy 0.2.2 through `:deploy`, after credential
validation. Packaging and consumer requirements do not change. See the
[release architecture](../../release-workflow.md) and
[operator guide](../../../doc/development/release.md).
