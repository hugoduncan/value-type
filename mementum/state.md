# Current project state

This file summarizes the current project. Keep task planning in
`munera/plan.md`, contracts in the design and usage documents, and verification
records in Munera. Replace stale facts; do not append development history,
task progress or test-run records.

## Library

value-type is an experimental Clojure library. Version `0.1.127` is published
on Clojars and GitHub from tag `v0.1.127` at
`3557ed0ecd84f0edb70cb845647bb566b7762a3f`. Its API is not stable. It provides:

- `value-type.core/defvaluetype`: `deftype`-style definitions of JEP 401 value
  classes, with ordinary `deftype` fallback on unsupported JVMs.
- `value-type.core/defvaluetype*`: low-level definitions using plain or record
  `deftype*` arguments, including a closed record shape.
- `:non-nil` fields with concrete identity or value type tags. Native field
  restrictions or generated constructor checks enforce the constraint,
  including on identity fallback.
- `value-type.array/make-array` and `into-array`: native null-restricted atomic
  arrays. Allocation has no fallback. `array-info` inspects reference arrays.
- `value-type.capability/report`: cached capability availability and reasons,
  plus the fallback policy in effect.

## Runtime boundaries

- Value classes need a supported Valhalla JVM and `--enable-preview`. Native
  array operations also need
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
- `value-type.fallback` selects `silent` (default), `warn` or `error`. The
  policy is fixed at its first read, including through `report`, not at load.
  `value-type.value-classes=off` disables value-class support.
- Expansion and AOT compilation fix the representation and field enforcement.
  Incompatible receivers reject value-class output; they cannot fall back.
- Compiler coupling is isolated in `impl.compiler`. Value output uses
  `java.lang.classfile`; identity output uses bundled ASM. Capture and optional
  JDK support load lazily. Ordinary fallback does not install capture support.
- Array allocation and inspection use separate lazy direct-call adapters.
  Requiring the public namespaces does not load them. First successful
  discovery compiles the relevant adapter, including discovery through `report`.
- Performance depends on the JVM, layout and workload. Semantic correctness
  does not establish flattening or performance benefits.

## Development and verification

- The library ships as a source-only JAR. Quality checks, API generation,
  isolated consumer tests and release tooling are available.
- `dev/jvm/jvms.edn` is the source for pinned JVM builds, platforms and launch
  profiles. `bb jvm:provision ROLE [PLATFORM [ROOT]]` checks archive SHA-256
  and the exact JVM build before installation.
- The full compatibility matrix uses Java 8 and Valhalla EA3 with Clojure
  1.12.6. The ordinary-JDK tier is smoke-only. Forced-JIT testing applies to
  the runtime suite parent, not its child JVMs. Future JVM formats are not
  verified.
- Hosted CI targets Linux x64. Local Linux verification uses native ARM64;
  its results do not establish x64 or hosted success.
- Test suites have separate classpaths. Runtime Scry discovery must use
  `--dir test/runtime`, not all of `test/`. Tooling tests require
  `-M:quickdoc:tooling-test`; release tests require `-M:release:release-test`.
  See the runtime guide for the complete suite commands and isolation rules.
- Release versions use the Git commit count. Preparation commits matching
  README coordinates and changelog notes together. Preview runs checks and
  builds without publication. Publication is separate and requires explicit
  authorization. Hosted publication and matching Clojars/GitHub artifacts are
  verified for `0.1.127`. The one-off `recover-0.1.127.yml` workflow is complete;
  do not rerun it. It stops when published artifacts exist.
- Current design gaps are listed in the
  [embodiment gap table](../ramora/defvaluetype-design/embodiment.md#decided-changes-not-yet-implemented).

## Orientation

- [Task plan](../munera/plan.md): current work.
- [README](../README.md): overview and quick starts.
- [Usage guide](../doc/usage.md) and [API](../doc/API.md): public contracts.
- [Development guide](../doc/development.md): setup, quality checks, builds
  and benchmarks.
- [Runtime guide](../doc/development/runtime.md): JVM selection, test tiers
  and commands.
- [Release guide](../doc/development/release.md): preparation, preview,
  publication and failure handling.
- [Architecture index](../ramora/INDEX.md): designs and implementation boundaries.
  Only files named `embodiment.md` describe implementation status; they state
  current coverage and gaps, not development history.
- `mementum/knowledge/`: reusable lessons on JVM boundaries, testing,
  benchmarking and release behavior.
- `munera/closed/`: detailed implementation and acceptance evidence.
