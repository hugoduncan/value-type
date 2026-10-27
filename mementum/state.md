# Current project state

Keep this file as a current-state summary. Replace stale facts; do not append
history, task progress, review notes or test-run records. Task planning belongs
in `munera/plan.md`; detailed guidance and evidence belong in the linked documents.

## Library

value-type is an experimental Clojure library with no published release.
Its API is not stable.

- `value-type.core/defvaluetype` provides `deftype`-style syntax for JEP 401
  value objects. It transforms generated class bytes through a capture loader
  and `java.lang.classfile`.
- On unsupported JVMs, the macro uses ordinary `deftype` semantics. The startup
  property `value-type.fallback` selects `silent` (default), `warn` or `error`.
  AOT compilation fixes the representation; value-class output cannot fall back.
- `value-type.array/make-array` allocates native null-restricted atomic arrays
  with an explicit seed. `into-array` constructs them from finite sequences.
  `array-info` inspects reference-array properties. Allocation has no fallback.
- Source-only JAR builds, quality checks, API generation and release tooling
  are available. Hosted Actions and live publication remain unverified.

## Runtime and verification limits

- Tested coverage: Clojure 1.12.6, Java 8 and Valhalla
  `27-jep401ea3+1-1` (EA3), with preview off and on. Future JVM formats are
  not verified.
- Value classes need a supported Valhalla JVM with `--enable-preview`.
  Array allocation also needs
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
  Array inspection needs the export; ordinary-array inspection does not need
  preview on EA3. Public validation rejects null diagnostic inputs because
  native queries can crash EA3.
- Tests share `test/`, with separate runtime, compatibility, build, release
  and tooling suites and classpaths. Runtime Scry discovery must use
  `--dir test/runtime`, not all of `test/`.
- Performance depends on the JVM, layout and workload. Recorded tests show
  larger standalone value objects, no stable interface-call penalty, and
  storage and traversal benefits for specific flattened arrays. These are
  not general performance guarantees. The maintained typed-array benchmark
  is in `bench/typed-array/` and uses the public allocator.

## Orientation

- `README.md`: project overview and quick start.
- `doc/usage.md` and `doc/API.md`: public contracts and limits.
- `doc/development.md`: setup, quality checks, builds and benchmarks.
- `doc/development/runtime.md`: JVM selection and test commands.
- `doc/development/release.md`: release operations.
- `ramora/INDEX.md`: architecture and retained experiment evidence. Design
  documents contain no project history. Implementation status and gaps belong
  only in embodiment sections; task and acceptance records belong in Munera.
- `mementum/knowledge/`: reusable lessons on JVM boundaries, testing,
  benchmarking and release behavior.
