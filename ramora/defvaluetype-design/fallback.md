# Controlled fallback

Status: implemented and tested on Java 8 and Java 27 EA. Task 009 implementation
acceptance mapping and final review are complete. D10 replaces
its former no-fallback choice. Existing value-class evidence remains
scoped to the tested Valhalla build.

## D10. Controlled fallback

- Subject: unavailable value-class support and policy authority.
- Choice: use value classes whenever this library can define them.
  Otherwise apply the application policy:

  | Policy | Result when support is unavailable |
  |---|---|
  | `silent` | Define an ordinary `deftype`, with no message. |
  | `warn` | Print one warning to standard error, then use `deftype`. |
  | `error` | Throw a Java `Error` on every attempt. Define no fallback class. |

- Strict retries: catching or ignoring an earlier `Error` does not
  permit a later definition. Cache capability, not permission to skip
  an error. Direct calls throw plain `java.lang.Error`; source compiler operations
  wrap it in `Compiler$CompilerException`. Failed AOT initializer retries
  throw `NoClassDefFoundError`, also an Error, without guard re-entry.
- Authority: applications control policy before dependent namespaces
  load. Libraries use `defvaluetype` without weakening application
  policy. There are no per-type overrides in this design.
- Approved configuration: JVM property `value-type.fallback`, with
  values `silent`, `warn`, `error`; default `silent`. Read and validate
  it once per loaded library runtime. Invalid values fail clearly.
  Changing it after initialization is not supported. This gives source
  libraries silent fallback by default and lets applications set policy
  before dependency loading. The user approved the property, default
  and initialization contract on 2026-10-23.
- Semantics: fallback is ordinary `deftype`, not value-semantics
  emulation. Identity, default equality, hashing and identity-sensitive
  operations can differ. Libraries that permit fallback must tolerate
  both representations. Mutable fields remain invalid in both modes
  (D8); changing JVM capability must not make such source valid.
- Rejected: unconditional failure (prevents portable libraries), one
  warning per type (noise), throw only once then continue (breaks strict
  policy), and runtime emulation (new semantics and cost).
- Qualities: improves portability and application control; loses uniform
  identity and equality semantics across JVMs when fallback is allowed.
- Commitment: firm for the three behaviours, repeated strict failure
  and configuration surface. Plain `java.lang.Error` is selected. Source compilation wraps it in
  CompilerException; failed AOT initializer retries can throw
  NoClassDefFoundError instead.

## D12. Capability boundary

- Subject: deciding availability without loading unsupported APIs.
- Choice: separate JVM capability from application policy. Capability
  answers whether this library can define its supported value-class
  format, with an unavailable reason for diagnostics. It must cover
  missing support, required API availability and disabled preview.
  `Class::isValue` presence alone is insufficient.
- Boundary: the public namespace and fallback path must load without
  JEP 401, preview flags or `java.lang.classfile`. Load the value-class
  machinery only after the initial capability checks permit it.
  Capability detection must use APIs safe on the fallback runtime.
- Failure classification: only established capability absence permits
  fallback. Invalid forms, unexpected constructor shapes, loader defects
  and transformation defects remain errors. Do not catch all definition
  failures and retry the user's type as an identity class.
- Rejected: eager transformer loading; detecting availability by failing
  to define each user type; treating every `LinkageError` as absence.
- Rationale: missing optional APIs must not prevent fallback. A defect
  must remain visible, including on a supported JVM.
- Qualities: improves portability and fault isolation. Adds a capability
  check and its verification burden.
- Commitment: firm boundary. Use API checks followed by the isolated strict-field format probe
  specified below. No new
  minimum fallback JDK is imposed beyond the supported Clojure runtime;
  the tested JDK matrix is a separate verification decision.

## D13. Fallback lifecycle

- Subject: state ownership, warning frequency and AOT representation.
- Choice: the policy/capability responsibility owns cached capability,
  initialized policy and an atomic warning-once flag. Scope is one loaded
  library runtime, not each type or namespace. Concurrent fallback
  attempts produce at most one warning. Warn on actual fallback, not
  merely on requiring the public namespace. Independent JVMs or isolated
  library copies have separate state. No persistent storage is needed.
- AOT: compilation fixes representation. Value-class files cannot fall
  back on an unsupported JVM. Identity-class files do not become value
  classes on a capable JVM. The emitted fallback expansion enforces
  application policy during compilation and where the form is evaluated.
  Top-level forms enforce policy during namespace initialization. Forms inside
  functions permit namespace loading, even under `error`, and enforce policy
  on invocation. Strict calls each throw plain Error, even on a capable JVM;
  `warn` reports the fallback representation once. Diagnostics distinguish
  this case from unavailable JVM support. Failed namespace initialization
  retries can throw NoClassDefFoundError without guard re-entry. The guard
  cannot prevent prior class loading or protect direct class access that
  bypasses evaluation of the guarded form.
- Rejected: a process-wide cross-class-loader registry; resetting strict
  policy after an error; automatic conversion of compiled artifacts.
- Rationale: one small state owner is enough; application policy must
  not be bypassed by dependencies compiled under another policy.
- Qualities: bounded diagnostics and predictable deployment; AOT adds a
  guard at form evaluation, not per-instance overhead.
- Commitment: firm. Production transfer, concurrent-warning and source-free
  AOT tests verify the do barrier, analysis macro and runtime guard.

## Verification

D11 now includes isolated JVM tests:

- Public namespace and packaged source JAR load without optional APIs.
- All policies on a non-value JVM and Valhalla with preview disabled.
- Value classes on supported Valhalla with preview enabled, for all policies.
- No output in silent mode; one warning across types, namespaces and
  concurrent attempts; repeated strict attempts fail without defining types.
- Error cause and compiler-wrapper behaviour; invalid policy rejection.
- Same field validation in both modes; no fallback for implementation defects.
- Fallback protocols, factories, type hints and ordinary identity semantics.
- AOT compilation and fresh-JVM namespace loading under different policies
  and capabilities, including strict rejection of fallback artifacts.

Recorded matrix: Clojure 1.12.6, Temurin 1.8.0_492-b09, and Valhalla
27-jep401ea3+1-1 with preview off/on. The full fallback suite passed
18 tests with 148/148/129 assertions respectively. Value-path regressions
passed 64 tests / 332 assertions; build tests passed 6 / 63. Commands and
phase-specific results are in task 009 implementation.md. These results
do not establish support for future EA formats or every supported Clojure JVM.


## Selected detector

See [selected detector](fallback/selected-detector.md).
