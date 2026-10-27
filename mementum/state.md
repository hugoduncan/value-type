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
- Native null-restricted fields are available through opt-in `:nil-restricted`
  metadata and an explicit value-class type hint on EA3. A separate lazy probe
  verifies native enforcement without exports. Unsupported JVMs reject enabled
  definitions under every fallback policy.
- Field specifications retain exact Class identity. The capture loader transforms
  concrete storage, constructor casts and owner/companion references together.
  Owner and companion transforms share optional-loader ClassFile configuration
  behind the optional JDK boundary. Constructor reference parameters remain Object. Mixed primitive slots, nullable
  controls, methods, closures and same-name/different-loader cases are verified.
- Transferred expansions install receiver-local loaders. Source-free AOT works
  with separately compiled Clojure and direct Java consumers. Java 8 and
  preview-disabled EA3 reject saved restricted-field classes under all policies.
  Semantic tests pass with flattening on/off and normal/interpreter execution;
  they do not establish layout or performance guarantees.
- See the [field design](../ramora/nil-restricted-field-design.md) and
  [task evidence](../munera/closed/016-nil-restricted-fields/implementation.md).
  Public usage, API contracts, runtime verification guidance and Ramora embodiment
  describe native field limits. Detailed compiler decisions distinguish ordinary
  selection-time installation from restricted receiving installation and companion
  rewriting. Separate full runtime, compatibility, build and tooling suites pass.
  External Java value classes are verified through public restricted-field
  construction, including exact field Class identity and native rejection.
  Public declaration tests verify structured compiler errors for invalid metadata
  and ineligible tags, including enclosing-Class redefinition and validation order.

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

- `README.md`: project overview, value-class, nil-restricted field and value-array quick starts,
  JVM flags and performance limits.
- `doc/usage.md` and `doc/API.md`: public contracts and limits, including
  ordinary fallback versus native field requirements and expansion transfer.
- `doc/development.md`: setup, quality checks, builds and benchmarks.
- `doc/development/runtime.md`: JVM selection and test commands.
- `doc/development/release.md`: release operations.
- `ramora/INDEX.md`: architecture and retained experiment evidence. Design
  documents contain no project history. Implementation status and gaps belong
  only in embodiment sections; task and acceptance records belong in Munera.
- `mementum/knowledge/`: reusable lessons on JVM boundaries, testing,
  benchmarking and release behavior.
