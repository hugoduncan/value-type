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
- `build.clj`: source-only JAR assembly and target cleanup. See
  [Library packaging](#library-packaging).
- `LICENSE`: full EPL-2.0 text, also included at the JAR root.
- `test-build/build_test.clj`: build contract, real CLI, input
  preservation and isolated JAR consumer checks. See the README
  [build-test command](../../../README.md#run-the-build-tests).
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

## Library packaging

`build.clj` creates `target/value-type-0.1.N.jar` for
`org.hugoduncan/value-type`. The JAR contains library sources, Maven
project and runtime dependency metadata, and the EPL-2.0 license.
It excludes compiled classes, bundled dependencies and development
files. Packaging does not load or AOT-compile library namespaces.
The metadata basis excludes user configuration and development aliases.

See the README [local JAR build guide](../../../README.md#build-a-local-jar)
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
JAR consumers need runtime dependencies. Value-class consumers need a
supported JEP 401 Valhalla JDK with `--enable-preview`. Fallback consumers
need only the supported Clojure runtime and ordinary JDK APIs. Isolated
source-JAR consumers passed on Java 8 and preview-enabled Valhalla 27 EA.
Fallback AOT fixes identity representation and enforces receiving policy where
the form is evaluated, including on capable JVMs. Top-level forms enforce
policy during namespace initialization. Forms inside functions allow namespace
loading and enforce policy on invocation. Direct class access that bypasses
the guarded form is not protected; failed initializer retries do not re-enter
the guard. See the README
[consumer JDK requirement](../../../README.md#jdk-requirement).
