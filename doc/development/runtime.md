# Tests, builds and benchmarks

[Development guide](../development.md)

Run commands from the repository root.

## JDK requirement

The value-path tests need a JDK with JEP 401 value classes: the Valhalla
early-access build `27-jep401ea3+1-1`. Array tests also need its `javac`
and the internal-package export shown below. Download it from https://jdk.java.net/valhalla/.
Other JDKs do not have value classes. On another JDK, the environment
test fails and names the cause.

## Select the JDK

The `clojure` script uses `JAVA_CMD` first. Set it to the `java` of
the Valhalla JDK. `<valhalla-jdk-home>` is the home directory of the
Valhalla JDK.

```sh
export JAVA_CMD=<valhalla-jdk-home>/bin/java
```

`JAVA_HOME` alone is not sufficient: the `clojure` script uses `java`
on `PATH` before `JAVA_HOME`.

## Run the tests

```sh
clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test -m scry.cli
```

The command exits 0 only when at least one test runs and all tests
pass. Failure details go to `.scry-results/`. Each run replaces that directory;
retain results before the next run. The `:test` alias enables preview, but
does not add the export. Do not use it for preview-free controls.

## Verified fallback matrix

Tests use Clojure 1.12.6 with Temurin `1.8.0_492-b09` and Valhalla
`27-jep401ea3+1-1`, with preview disabled and enabled. This is tested
coverage, not a new minimum JDK or a claim about future EA formats.
Run the separate fallback suite without inheriting the value-only alias:

```sh
JAVA_CMD=<java8-home>/bin/java clojure -M:fallback-test
JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -M:fallback-test
JAVA_CMD=<valhalla-jdk-home>/bin/java clojure -J--enable-preview -M:fallback-test
```

Cross-JVM tests also need both executables. Set `VALUE_TYPE_BASELINE_JAVA`
and `VALUE_TYPE_VALHALLA_JAVA` to their full executable paths when they are
not at the local defaults recorded in the task 009 plan. Build tests use
both JVMs for isolated JAR consumers. Fallback tests use real processes,
including source-free AOT consumers, and the same Scry exit/failure rules.

## Verify array allocation

Set both cross-JVM executable paths explicitly:

```sh
export VALUE_TYPE_BASELINE_JAVA=<java8-home>/bin/java
export VALUE_TYPE_VALHALLA_JAVA=<valhalla-jdk-home>/bin/java
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test --namespace value-type.array-matrix-test
```

This test starts fresh JVMs for six configurations: Java 8; EA3 without
preview, with and without exports; EA3 with preview but no exports; EA3
with preview and exports; and the supported configuration with array
flattening disabled. Each configuration tests all three class-fallback
policies and both public namespace require orders. Child flags are explicit;
the parent does not need preview or exports. Unsupported configurations
check safe loading and repeated capability errors, not value allocation.
The full fallback commands above also include this matrix.

Run native semantics and layout checks with flattening disabled:

```sh
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure \
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -J-XX:-UseArrayFlattening -M:test -m scry.cli \
  --namespace value-type.array-test --namespace value-type.array-layout-test
```

Repeat with `-J-XX:+UseArrayFlattening` for the enabled control. A non-flat
restricted atomic array is valid. These checks use real JVM allocation and
stores; they do not establish support for other JDK builds. See the
[allocation guide](../usage.md#value-array-allocation) for the public contract.

## Verify sequence construction

Use the explicit executable paths from "Verify array allocation":

```sh
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test \
  --namespace value-type.array-sequence-boundary-test
```

This matrix uses the same six configurations, three fallback policies and
both require orders as allocation. Each child has a 120-second timeout and
must exit zero with complete EDN results and no fallback warning. Unsupported
configurations reject both forms repeatedly before input effects. Supported
configurations check component type, length and ordered contents for both
forms, plus explicit-seed nil input. Export-only EA3 does not enable sequence
construction. Child flags are explicit; the parent needs neither preview nor
exports. The full fallback suite includes this matrix.

Run construction, allocation and layout checks together:

```sh
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure \
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -J-XX:+UseArrayFlattening -M:test -m scry.cli \
  --namespace value-type.array-sequence-test \
  --namespace value-type.array-test --namespace value-type.array-layout-test
```

Repeat with `-J-XX:-UseArrayFlattening`. Sequence checks use real arrays and
stores without diagnostics or a flat-layout requirement. They cover finite
input, seed and indexed-element errors, one input traversal, input exceptions
and later slot stores. The length boundary check uses internal arithmetic,
not a large allocation. These checks do not establish streaming behavior or
performance. See the [sequence guide](../usage.md#value-array-sequence-construction)
for temporary storage and failure-side-effect limits.

## Verify array diagnostics

Use the explicit executable paths from "Verify array allocation". Run the
public validation and inspection checks in disposable JVMs first:

```sh
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure -M:fallback-test \
  --namespace value-type.array-info-boundary-test
```

This namespace checks all six startup configurations, all three fallback
policies and both require orders. Each child has a 120-second timeout and
must exit zero with complete EDN results. Independent native observations
separate API absence from denied invocation access. Invalid public inputs
(null, non-arrays and primitive arrays) must report `:invalid-array` before
inspection capability is checked. A valid Object array reports
`:array-inspection-unavailable` on Java 8 or EA3 without the export.

EA3 with the export can inspect ordinary reference arrays without preview.
This does not enable value-class creation or allocation. The export-only
checks verify that inspection works before and after allocation fails.
The parent command adds neither preview nor exports; child flags are explicit.
Do not add global flags to make unsupported controls pass.

**Never call native diagnostics with null.** All three native array queries
crashed the tested EA3 JVM on null. Keep invalid public-input checks in
children, not a shared REPL. Do not rerun task 012 crash probes as library tests.

After the disposable checks pass, compare valid-array results with direct
JDK queries and run allocation/layout regressions:

```sh
JAVA_CMD="$VALUE_TYPE_VALHALLA_JAVA" clojure \
  -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED \
  -J-XX:+UseArrayFlattening -M:test -m scry.cli \
  --namespace value-type.array-info-test \
  --namespace value-type.array-test --namespace value-type.array-layout-test
```

Repeat with `-J-XX:-UseArrayFlattening`. Disabled flattening must not remove
null restriction or atomic access. These tests compare all five properties,
including empty arrays and a wider non-flat fixture, and check unchanged
contents before and after caller stores. Atomic access does not guarantee
compound updates, safe publication or caller synchronization. See the
[inspection guide](../usage.md#value-array-diagnostics) for the public contract.

## Build a local JAR

Run these commands from the repository root with the Clojure CLI and
Git installed. Dependency downloads need repository access unless they
are cached.

```sh
clojure -T:build jar
clojure -T:build clean
```

**Both commands remove all existing `target/` output**, including
`target/object-size/` benchmark results. Copy any results you need to
keep outside `target/` first. `jar` cleans before packaging; `clean`
only removes generated output and also succeeds when `target/` is absent.

The artifact is `target/value-type-0.1.N.jar`, with Maven coordinates
`org.hugoduncan/value-type`. `N` is `git rev-list --count HEAD`.
The `build/jar` function returns
the version and artifact paths:

```clojure
{:jar-file "target/value-type-0.1.N.jar"
 :pom-file "target/classes/META-INF/maven/org.hugoduncan/value-type/pom.xml"
 :version "0.1.N"}
```
The JAR command needs Git and usable checkout history; failed Git
commands or invalid counts cause an error, with no fallback version.
The clean command does not need Git metadata.

A shallow checkout counts only available history. Different branches
can have the same count, so this version is not a unique release ID.
This command creates a local artifact; it does not install or publish
it. See the [release procedure](release.md) for deployment, credential
requirements and manual publication. The project has no published release.

The source-only JAR contains the library sources, Maven dependency and
project metadata, and the full EPL-2.0 license. It contains no compiled
classes, bundled dependencies, tests, benchmarks or build code. Packaging
does not load or AOT-compile the library. Consumers still need the declared
runtime dependencies. Value classes also need a supported JEP 401 Valhalla
JDK with `--enable-preview`; other supported Clojure JVMs use the fallback
policy. Array allocation also needs
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`; it has no fallback.

## Run the build tests

Select the supported Valhalla JDK with `JAVA_CMD` as above. These checks
also need Git, the Clojure CLI and access to uncached dependencies.

```sh
clojure -M:build-test -m scry.cli --dir test-build
```

This separate suite checks the build contract in temporary projects and
runs an isolated consumer with only the built JAR and runtime dependency
JARs. The supported consumer gets preview and the internal-value export.
Java 8 and preview-disabled EA3 consumers check safe loading and unavailable
allocation without those flags. They also check that invalid diagnostic inputs
keep their public error and valid arrays report inspection unavailable. An
additional export-only EA3 consumer inspects an Object array before value
creation while allocation remains unavailable. The supported consumer checks
ordinary and restricted array diagnostics. It also checks both sequence
forms, ordered contents and explicit-seed empty input. Unsupported consumers,
including export-only EA3, reject both sequence forms repeatedly before value
creation and without input effects. All consumers load library resources
from the JAR, with no checkout sources on the class path.
Set `VALUE_TYPE_BASELINE_JAVA` explicitly;
the supported consumer uses `JAVA_CMD`. It selects `test-build/`; it does not replace the library test
command above. Exit status and failure files follow the same Scry rules.

## Run the benchmark

The method-call benchmark compares `defvaluetype` with `deftype`. Set
`JAVA_CMD` to the Valhalla `java` (see "Select the JDK"), then:

```sh
clojure -M:bench -m value-type.bench
```

An optional argument sets the array length (default 1024). It must be
an integer from 1 to 2147483647, inclusive (the JVM array-length
range). Available memory can impose a lower limit. Invalid input is
rejected before measurement. The results are in
`ramora/defvaluetype-design/benchmark.md`.

## Compare standalone object size

Use a Valhalla JDK with JEP 401 support (see "Select the JDK"). Run
from the repository root:

```sh
JAVA_CMD=<valhalla-jdk-home>/bin/java bash bench/object-size.sh
```

This compares shallow size for paired types with 0–3 primitive `long`
fields. The isolated `:object-size` alias enables preview and uses
Criterium `0.5.251-ALPHA`. The wrapper extracts its bundled native agent
and loads it at JVM startup. This route is verified on macOS/aarch64.

Results and raw evidence go to `target/object-size/results.edn` and
`raw.edn`; setup and measurement logs go to `stdout.log` and `stderr.log`
in that directory. Each run replaces these four files. On a nonzero
exit, inspect the logs. See the [size report](../../ramora/defvaluetype-design/object-size.md)
for the recorded results, environment and limits. This is a manual
measurement, not a performance gate.

## Start a REPL

```sh
clojure -J--add-exports=java.base/jdk.internal.value=ALL-UNNAMED -M:test:nrepl
```

The REPL has preview, the internal-value export and the test path. Run the tests in
the REPL with:

```clojure
(do (require 'scry.core)
    (scry.core/run {:namespaces ['value-type.environment-test]}))
```
