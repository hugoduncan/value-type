# Current project state

Keep this file as a current-state summary. Replace stale facts; do not append
history, task progress, review notes or test-run records. Task planning belongs
in `munera/plan.md`; detailed guidance and evidence belong in the linked documents.
Git keeps earlier versions of this file.

## Library

value-type is an experimental Clojure library with no published release.
Its API is not stable.

- `value-type.core/defvaluetype` provides `deftype`-style syntax for JEP 401
  value objects. It transforms generated class bytes through a capture loader.
  Value output uses `java.lang.classfile`; identity output uses bundled ASM.
- On unsupported JVMs, the macro uses ordinary `deftype` semantics. The startup
  property `value-type.fallback` selects `silent` (default), `warn` or `error`.
  AOT compilation and expansion transfer fix the representation; value-class
  output cannot fall back.
- `value-type.core/defvaluetype*` takes `deftype*` arguments (plain or
  compiler record shape) and evaluates to the Class. Value output removes
  `__hash`/`__hasheq` and keeps all three record constructors and `create`.
  Same fallback policy; `:non-nil` fields in both shapes (main constructor
  checks; receiving macros `value-field-deftype*`/`identity-field-deftype*`). See
  [deftype* design](../ramora/deftype-star-design.md) and the usage guide.
- `value-type.array/make-array` allocates native null-restricted atomic arrays
  with an explicit seed. `into-array` constructs them from finite sequences.
  `array-info` inspects reference-array properties. Allocation has no fallback.
- `value-type.capability/report` returns the `:available?` and `:reason`
  entries of the four cached capability results (`:value-class`,
  `:native-non-nil-field`, `:array-allocation`, `:array-inspection`) as EDN.
  It reads no fallback policy. The probe namespaces (`impl.capability`,
  `impl.field-capability`, `impl.array`) own the cached results through
  zero-arity accessors; `impl.runtime` owns only policy, the warning flag
  and the fallback guards (`enforce-fallback!`, `guarded-deftype`,
  `guarded-deftype*`).
  See D14 in the defvaluetype decisions.
- Source-only JAR builds, quality checks, API generation and release tooling
  are available. Hosted Actions and live publication remain unverified.

## Non-nil fields

Task 017 is complete and closed. Public `:non-nil` field metadata replaces
`:nil-restricted` (no alias). It accepts concrete identity and value tags.

- Selection is per field and fixed at expansion. Native enforcement applies when
  the owner is a value class, the tag is native-eligible and native support is
  available. Otherwise generated constructor checks and concrete final storage
  enforce the constraint, also on identity fallback.
- Direct, factory and reflective constructors reject nil and wrong-type arguments.
  Methods, protocols, Object overrides and closures read all fields.
- Receivers of transferred expansions and AOT artifacts keep the fixed
  enforcement. Incompatible receivers reject value output without fallback.
- Field specifications keep exact Class identity across redefinition, sibling
  loaders and cross-process replacement.

Evidence limits: the evidence is bounded, not an exhaustive cross-product.
No JVM other than those below is claimed. Semantic tests do not establish layout
or performance. See the
[acceptance audit](../munera/closed/017-portable-non-nil-fields/acceptance-audit.md),
its linked per-criterion audits and the
[implementation record](../munera/closed/017-portable-non-nil-fields/implementation.md).
The [field design](../ramora/non-nil-field-design.md) is the contract.

## Runtime and verification limits

- Tested coverage: Clojure 1.12.6, Java 8 and Valhalla
  `27-jep401ea3+1-1` (EA3), with preview off and on, in normal and interpreter
  execution. Future JVM formats are not verified.
- Value classes need a supported Valhalla JVM with `--enable-preview`.
  Array allocation also needs
  `--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`.
  Array inspection needs the export; ordinary-array inspection does not need
  preview on EA3. Public validation rejects null diagnostic inputs because
  native queries can crash EA3.
- Tests share `test/`, with separate runtime, compatibility
  (`:fallback-test`, per parent JVM), cross-JVM (`:cross-jvm-test`,
  `test/cross-jvm/`, run once under Java 8), build, release and tooling
  suites and classpaths. Runtime Scry discovery must use
  `--dir test/runtime`, not all of `test/`.
- Tooling tests require `-M:quickdoc:tooling-test`. Release tests require
  `-M:release:release-test`.
- Matrix cells share child JVMs under the isolation rules in
  `doc/development/runtime.md`; `VALUE_TYPE_TEST_JVM_LIMIT` (default 4) sets
  the concurrent children. The four commands (`:cross-jvm-test` and three
  `:fallback-test` parents) take about 9 minutes in total at limit 4.
  The [matrix task record](../munera/closed/018-fast-compatibility-matrix/implementation.md)
  retains the evidence and closure limits. Two review items remain unresolved:
  label-order agreement and the loader-isolation negative check.
- Performance depends on the JVM, layout and workload. Recorded tests show
  larger standalone value objects, no stable interface-call penalty, and
  storage and traversal benefits for specific flattened arrays. These are
  not general performance guarantees. The maintained typed-array benchmark
  is in `dev/bench/typed-array/` and uses the public allocator.

## Orientation

- `munera/plan.md` and `munera/open/`: current tasks.
- `README.md`: project overview, value-class, non-nil field and value-array quick starts,
  JVM flags and performance limits.
- `doc/usage.md` and `doc/API.md`: public contracts and limits.
- `doc/development.md`: setup, quality checks, builds and benchmarks.
- `doc/development/runtime.md`: JVM selection and test commands.
- `doc/development/release.md`: release operations.
- `ramora/INDEX.md`: architecture and retained experiment evidence. Design
  documents contain no project history. Implementation status and gaps belong
  only in embodiment sections; task and acceptance records belong in Munera.
- `mementum/knowledge/`: reusable lessons on JVM boundaries, testing,
  benchmarking and release behavior.
