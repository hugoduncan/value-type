# Plan

## Approach

Implement the contract in `design.md` as one feature, in the slice order
below. Configuration and phase timing are settled. Slice 1 resolves the
technical choices before production code changes. Boundary inspection and the initial API matrix are complete. Slice 1 is complete. The detector and guard choices below are selected; production verification remains open.

### Capability and policy boundary

Use one internal responsibility to own initialized policy, cached capability
with its unavailable reason, and an atomic warning flag. Read and validate
`value-type.fallback` once per loaded library runtime: `silent`, `warn`, or
`error`, with `silent` as the default. Reject invalid values clearly. Do not
support later changes or per-type overrides.

Keep `value-type.core` and the fallback dependency path free of eager loader,
transformer and optional JDK API dependencies. Cross the optional dependency
boundary only after capability checks permit it. Preserve field validation
before selection and the existing value-class pipeline, including D9 tooling
behaviour when `Compiler/LOADER` is not bound.

Prefer small API checks followed, if needed, by an isolated known-format
probe. Do not probe by defining a user's type. Slice 1 must select the
mechanism and list the exact absence conditions it recognizes. Unexpected
probe, loader or transform failures must remain errors, not successful
fallback. Do not classify all `LinkageError` instances as absence.

### Phase enforcement

Fallback selection during macroexpansion applies policy immediately. A
returned fallback expansion fixes representation but carries no permission
from its producer. Use the slice 1 guard mechanism to enforce policy before
compiler analysis defines a source type, and again during evaluation and
AOT namespace initialization. A runtime call placed before `deftype` alone
is insufficient: analysis can define the class first.

All enforcement points use the same state owner. Warn once across phases,
types, namespaces and concurrent attempts. Strict policy throws the selected
Java `Error` on every attempt. A fallback artifact must enforce policy even
on a capable JVM, with a diagnostic that identifies its fixed representation.
No guard promises to prevent prior JVM class loading or direct class access
that bypasses namespace initialization. Add no per-instance checks.

### Verification structure

Use real JVM processes with explicit Java executable, preview flags, policy
and classpath. Select actual versions in slice 1: at least a supported
Clojure JVM without `java.lang.classfile`, plus supported Valhalla with
preview enabled and disabled. Tested coverage is not a new minimum JDK.

Do not use `value-type.test-support` unchanged for fallback tests: it eagerly
loads the loader, and its subprocess helper uses the host JDK, preview and
checkout classpath. Add a small baseline-safe test runner. Keep fallback
checks separate from `environment-test` and other value-only tests; the
`:test` alias currently enables preview.

Use structured subprocess results and Scry results. Synchronize concurrency
tests with barriers or latches, not sleeps. Use fresh processes for independent
runtime state. For JAR tests, follow `test-build/build_test.clj` and
`mementum/knowledge/testing-library-artifacts.md`: resolve dependencies from
the packaged POM, use only JAR classpath entries, and verify the public source
resource comes from the exact artifact. Build in temporary projects because
build commands delete `target/`.

## Risks and controls

- Capability APIs and class formats can differ between EA builds. Record
  exact versions, commands and recognized failure cases; keep unknown
  failures visible.
- Compiler analysis, evaluation and AOT initialization have different timing.
  Gate production changes on experiments that prove pre-definition rejection
  and namespace-load enforcement, including transferred expansions.
- Clojure can wrap an `Error`. Select the concrete type from observed cause
  chains; do not promise an unwrapped outer `Error`.
- State or inherited classpaths can conceal defects. Isolate policy runs and
  artifact consumers; test warning concurrency in one shared runtime.
- Missing JDKs block the required verification, not the configuration contract.
  Record missing coverage and obtain it before completion. Do not claim a
  skipped matrix entry passed.
- If experiments show that the contract cannot be met, stop production work
  and report the conflict. Do not weaken the design to fit an implementation.

## Slice order

1. **Resolve technical decisions.** Inspect current boundaries and test support;
   run capability, Error and phase-guard experiments. Record evidence in
   `implementation.md`; select interfaces, JDKs and absence classification.
   Align this plan and architecture before production code changes.
2. **Add isolated verification support.** Provide a baseline-safe subprocess
   runner and explicit fallback test selection without changing value-only
   suite requirements.
3. **Implement capability, policy and source fallback.** Add the state owner,
   lazy value-path boundary and guarded expansion. Verify configuration,
   source semantics, macroexpansion timing, strict retries and defect visibility.
4. **Verify runtime isolation and AOT.** Test transferred expansions, shared
   warnings, fallback compilation and fresh-process artifact loading across
   policies and capabilities. Preserve value-artifact constraints.
5. **Verify packaging and regressions.** Test isolated source-JAR consumers in
   both representations; run existing library and build suites on Valhalla.
6. **Document and close verification.** Update public docs and architecture
   from evidence; map all eight acceptance criteria to results and resolve
   review findings.

Commit each completed implementation action with its tests and task notes.
Update `steps.md` as work completes and append dated evidence to
`implementation.md`. Production implementation starts after the isolated verification support.

## Selected experiment runtimes

- Baseline: `/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java`,
  Temurin `1.8.0_492-b09` (VM `25.492-b09`, x86_64).
- Value runtime: `~/.local/jdks/jdk-27.jdk/Contents/Home/bin/java`,
  OpenJDK `27-jep401ea3+1-1`, with and without `--enable-preview`.

Both run Clojure 1.12.6. The baseline lacks both optional APIs. Valhalla
exposes both APIs even without preview, so API presence alone cannot select
the value path. Continue slice 1 with a known-format definition/link probe
and the compiler-phase guard experiments before selecting production code.

### Fixed-format probe evidence

`spike/fallback/format-check.clj` defines and instantiates an empty final
value class in format 71.65535 using Clojure's bundled ASM. It does not
load the transformer or require optional APIs to construct bytes. Java 8
rejects the version; Valhalla without preview rejects preview; both throw
`UnsupportedClassVersionError`. Preview-enabled Valhalla defines the class,
instantiates it and reports `isValue=true`.

The extended probe also verifies a public final strict `long` field, assigned
before `Object.<init>`, and reads back the constructor argument (42). All
three runtime configurations retain the outcomes above. This supports an
isolated format probe after API checks. Final detector selection must still
account for the transformer using the running JDK's latest format rather
than the probe's fixed format on other JDK releases. Only a version
rejection from the known probe is a candidate absence condition; do not
catch general linkage errors or failures from user classes. Error and
phase-guard experiments remain pending.

### Source Error evidence

`spike/fallback/error-check.clj` verifies `java.lang.Error` on all three
selected JVM configurations. Macroexpansion (both entry points), `eval` of
a rejected macro, and source loading wrap it in `Compiler$CompilerException`.
Evaluation of a direct runtime rejection exposes the Error itself. Each
operation rejects twice after catching the first failure; no rejected type
is resolvable or loadable. Plain `java.lang.Error` remains the simplest
candidate. Final selection waits for AOT cause-chain verification and the
transferred-expansion guard experiment; these checks are not yet complete.

### Transferred-expansion evidence

`spike/fallback/phase_guard.clj` returns a `do` containing a guard macro.
The outer `do` stops both macroexpand entry points from removing that guard.
The guard applies policy during analysis and emits a runtime policy call
before ordinary `deftype`. A Java 8 silent producer serialized the expansion;
fresh Java 8 and Valhalla (preview on/off) consumers verified silent identity
creation and strict rejection through eval and source loading, twice each.
Strict consumers neither resolve nor load the rejected class. Cause chains
remain `[Compiler$CompilerException, java.lang.Error]`. This selects a
candidate analysis guard, not a complete phase solution: AOT initialization,
runtime-only enforcement and warning state still need verification.

### AOT initialization evidence

`spike/fallback/aot-check.clj` loads a Java 8 silent-compiled fixture with
no fixture source on the consumer classpath. Silent consumers create identity
objects on all three JVM settings. Strict consumers first throw plain
`java.lang.Error`; retries throw `NoClassDefFoundError`. On Java 8 the retry
has no cause; on Valhalla its cause is `ExceptionInInitializerError`.
Both retry exceptions are Java Errors. JVM failed-initializer state prevents
re-entry into the guard; do not promise the same concrete Error on retries.
This supports plain Error for policy rejection. Strict AOT compilation also rejects twice on all three settings with
`[Compiler$CompilerException, java.lang.Error]`, without emitted, resolvable
or loadable type classes. Silent compilation succeeds on each setting.
Runtime-only evaluation is verified below; detector selection remains open.


### Runtime-only enforcement evidence

`spike/fallback/runtime-check.clj` invokes a source-free, Java 8 AOT-compiled
function whose body contains the fallback expansion. Compile under silent
policy. Fresh silent/error consumers on all three JVM settings load the
namespace successfully, then call the function twice. Silent calls return an
identity class with working fields and factory; strict calls each throw plain
`java.lang.Error`. No compiler analysis occurs in these calls.

Select plain `java.lang.Error` for direct policy rejection. Source compiler
operations wrap it in `Compiler$CompilerException`; failed AOT initializer
retries instead throw `NoClassDefFoundError`. The do barrier plus analysis
macro and runtime call meets the phase experiment gate. Final detector and
state-owner interfaces still need selection before production changes.


### Running-JDK format alignment

The format experiment accepts an optional `latest` argument. It obtains
`ClassFile.latestMajorVersion` reflectively and builds the same strict-field
probe at that version. Both Valhalla modes passed their expected assertions:
preview enabled defines a value class and reads 42; preview disabled throws
UnsupportedClassVersionError. Java 8 still runs the fixed-format experiment
without optional APIs.

Select the running-JDK version rule for the production probe, after required
API checks, rather than pinning the transformer to Java 27. This aligns the
probe and transformer without excluding later runtimes solely by version.
A future incompatible flag or verifier change must remain a visible error,
not automatic fallback. Finalize the required API checks, precise absence
classification and state-owner interfaces before production changes. This
experiment verifies only the selected Java 27 EA build, not future releases.


## Selected implementation interfaces (Slice 1 complete)

These choices supersede the provisional conclusions in the experiment history
above. The experiments establish feasibility, not production acceptance.

- `value-type.impl.capability` is baseline-safe and has no loader or transformer
  dependency. `detect` returns `{:available? true}` or
  `{:available? false :reason <keyword> :detail <string>}`.
- Check `java.lang.classfile.ClassFile` by name without initialization and
  public zero-argument `Class.isValue`. Only `ClassNotFoundException` from
  that class lookup (`:missing-classfile-api`) or `NoSuchMethodException`
  from that method lookup (`:missing-value-predicate`) selects API absence.
  Do not catch failures from namespace loading as API absence.
- After those checks, obtain `ClassFile.latestMajorVersion` reflectively.
  Define the tested strict-field probe at that major version with preview
  minor 65535 in a fresh DynamicClassLoader. Invoke its long constructor,
  check `isValue`, and read back 42. Use a unique binary name per probe to
  avoid the DynamicClassLoader class cache sharing names with other runtimes.
- Only `UnsupportedClassVersionError` from known-probe definition selects
  `:unsupported-class-version`. Preserve its message; do not infer disabled
  preview by parsing it. All other failures propagate. A false predicate or
  wrong field value is a probe invariant error, not permission to fall back.
  Missing later API members, security errors, verification errors, reflection
  invocation failures and optional namespace linkage defects remain errors.
  This deliberately narrow rule does not claim support for future EA formats.
- `value-type.impl.runtime` owns policy read and validation at namespace
  initialization, a delayed capability result, and an atomic warning flag.
  `capability` returns the cached map. `enforce-fallback!` accepts a diagnostic
  context and returns nil or throws plain `java.lang.Error`. Each call applies
  strict policy; only warning emission uses compare-and-set. Neither capability
  detection nor requiring the namespace emits a warning.
- `core` validates fields before selection. Only the available branch resolves
  `loader/install!` lazily. This is an optional dependency boundary, not a
  dependency-cycle workaround. Preserve D9 when the loader binding is absent.
- Initial fallback selection calls `enforce-fallback!` with the unavailable
  reason. Return an outer `do` containing `runtime/guarded-deftype`, with the
  original type name, fields, options and specs. This macro is public only so
  expansions in other namespaces can call it; it is not an application API.
  It checks fixed-fallback policy during analysis and emits a runtime check
  before ordinary `deftype`. Receiving guards use fixed-representation context,
  not a producer's capability reason. They never select a new representation.
- Plain Error is the selected direct rejection. Source compiler operations
  wrap it; failed AOT initializer retries can throw NoClassDefFoundError.
  Do not promise the same concrete Error or guard re-entry on those retries.

Slice 2 is complete. `test-fallback/value_type/jvm_runner.clj` accepts explicit
`:java`, `:classpath`, `:jvm-opts`, `:policy`, `:form` and `:timeout-ms`. It
returns exit status, stdout, stderr and last-line EDN. File redirection avoids
pipe blocking and reader threads; all paths remove temporary output files.
Run `JAVA_CMD=<java> clojure -M:fallback-test`; this alias selects only
`test-fallback` and adds no preview flag. Existing `:test` is unchanged.
Slice 3 now has the detector and runtime state owner, with isolated capability
and configuration tests. The public dependency is now lazy and the analysis/runtime guard is integrated.
Source compatibility and field validation are verified: both mutable keys reject true
values and accept false values as final fields. Invalid field forms still fail.
Macroexpansion timing and strict retry tests now pass on both incapable configurations,
including raw threads without compiler-loader bindings and bound threads.
Slice 3 is complete: capable-JVM D9 tooling and loader/transform defect
regressions pass under all three policies. Next: Slice 4 production transferred
expansions and AOT verification. Production tests must cover warning concurrency,
invalid configuration, complete deftype syntax and the optional dependency
boundary; the prototypes do not establish those acceptance criteria.

Production transfer tests now cover both macroexpand entry points, eval and
source loading, all receiving policies and all three JVM settings. The producer
uses Java 8 (override its executable with VALUE_TYPE_BASELINE_JAVA). Each
consumer repeats attempts and checks identity behaviour or rejected-class
absence. Synchronized warning scope is now verified with eight namespace workers, two
types each, in two fresh consumers per JVM setting. Production AOT compilation now passes on Java 8 and preview-disabled Valhalla
under all policies. Tests explicitly omit child preview flags even when the
host enables preview. Source-free fallback AOT receiving-policy tests now pass for silent/warn Java 8
producers and all policies on the three consumer settings. Value-class AOT artifact constraints now pass under all receiving policies
on the three JVM settings. The producer uses preview-enabled Valhalla
(override with VALUE_TYPE_VALHALLA_JAVA). Next: packaging checks.


Slice 5 packaging checks pass: isolated Java 8 and preview-enabled Valhalla
consumers verify fields, factories and representation-specific equality from
the source JAR. The full build suite also verifies source-only payload and
runtime dependencies. Full fallback matrix and value-path regressions now pass. Slice 5 is complete.
Next: Slice 6 documentation, acceptance evidence mapping and final review.

Slice 6 public documentation is updated. README and the macro docstring now
state phase timing, strict wrapping/retries, identity semantics, AOT limits
and the verified JVM commands. Architecture now matches the implemented detector, guards, artifact behavior
and recorded matrix. Acceptance mapping and final implementation review are complete; see
`implementation.md`. No concrete implementation work remains.
No behavior changes were needed.

Review follow-ups are complete. Production runtime-only AOT coverage now
compiles a function containing `defvaluetype` under Java 8 silent fallback,
deletes its source, and tests two calls in fresh silent/warn/error consumers.
The AOT suite passes on Java 8 and Valhalla with preview disabled/enabled.

Code-shaper review follow-ups are complete. Baseline-safe `jvm-config` owns
selected and producer JVM maps plus independent matrix expectations.
`aot-fixture/with-fixture` owns temporary fixture setup and cleanup; tests
retain explicit policy, source deletion, classpaths and phase assertions.
The full fallback matrix and the extracted AOT scenarios passed on all three
documented entries. No production behavior changed.
