# Probe and JVM format decisions

Scope: capability detection and the class-file format rules that change with
each early-access build.

## D15. JVM format table (data model, boundary)

- Subject: the class-file format rules that change with each EA build.
- Choice: one namespace owns the JEP 401 flag values (no `ACC_IDENTITY`,
  `ACC_STRICT_INIT` on instance fields), the version rule and the
  value-constructor order rule. The default row uses major
  from `ClassFile.latestMajorVersion`, minor 0xFFFF and the JEP 401 flag values.
  Other rows record only known differences, keyed by feature release or build.
  A build with no row uses the default row. The probe (D16, D17) and the
  transform (D5) read the same row.
- Failure: no new reason. A wrong row, or a wrong default for a new build,
  fails in the probe, because the probe defines and checks a real value class.
  D12 classifies the result.
- Rejected:
  - Constants in each namespace. Two places change for each build, and the
    probe and the transform can disagree.
  - A table that fails closed (`:unknown-jvm-format` for a build with no row).
    A compatible new build would lose value classes until a row is added.
- Rationale: one place changes when the format changes. A compatible build
  needs no change. The probe keeps format errors visible.
- Qualities: improves maintainability; the probe and the transform agree by
  construction. Adds one namespace.
- Commitment: firm.

## D16. Probes built with `java.lang.classfile` (technology, dependency)

- Subject: the bytecode library of the value probe and the native-field probe.
- Choice: one probe builder makes the bytes of both probes with
  `java.lang.classfile` and the D15 row. It loads only after the D12 API checks
  find `java.lang.classfile`. `impl.capability` does not import `clojure.asm`.
  `impl.identity-backend` keeps bundled `clojure.asm` (field decision D3): it
  must run on JVMs without `java.lang.classfile`. The API is final in JDK 24;
  Java 8 to 23 do not have it.
- Boundary: D12 does not change. `value-type.core` and
  `value-type.capability` load without `java.lang.classfile`.
- Rejected:
  - A probe built with `clojure.asm`. If Clojure changes or removes it, the
    public namespaces do not load on any JVM.
  - `org.ow2.asm` for the identity backend. A new runtime dependency.
  - An identity backend on `java.lang.classfile`. It needs a JDK 24 baseline.
- Rationale: the probe runs only where the API exists, so it loses nothing.
  A namespace that `core` loads on every JVM no longer depends on `clojure.asm`.
- Commitment: firm.

## D17. Semantic self-check (failure handling)

- Subject: a JVM that defines value classes but gives wrong value semantics.
- Choice:
  - After the format probe passes, the value probe makes three instances of
    the probe class with field values 42, 43 and 42. It checks that each
    instance reads its own value, that 42 and 43 are not `identical?`, that the
    two 42 instances are `identical?`, and that no instance is `identical?` to
    nil.
  - Array allocation discovery (`impl.array`) allocates a small
    null-restricted array of the probe class, writes it and reads it back.
  - Every value expansion has the value-class guard
    (`impl.capability/require-value-capability!`) as its first form. It reads
    the cached value-class result where the form is evaluated: at namespace
    initialization for a top-level form of AOT output, at each invocation for
    a form inside a function. When the result is unavailable, it throws
    `ex-info` `:value-capability-unavailable` with the reason. Value-class
    output cannot fall back (D13). The guard stops the definition form; it
    does not stop class loading.
- Failure: a failed check gives `:failed-self-check`. Value-class capability
  is then unavailable and definitions fall back by policy. A failed array round
  trip makes allocation unavailable; array functions fail clearly.
- Rejected: no check (a semantic JVM defect passes the probe silently);
  checks for each instance (cost).
- Rationale: identity fallback has correct semantics. A defective value-class
  implementation does not.
- Qualities: improves correctness. Cost: once per runtime, three reflective
  constructions and no new class. The AOT guard runs the probe at startup
  only when a top-level value definition is evaluated, capability is not
  already cached, and the switch and API checks permit the probe. A definition
  inside a function defers the guard until invocation. The `off` switch rejects
  the definition without running a probe.
- Commitment: firm. The AOT check is provisional until its startup cost is
  measured.

## D18. Value-class switch (configuration)

- Subject: a return to identity semantics after a JVM defect is found.
- Choice: JVM property `value-type.value-classes`, values `auto` (default) and
  `off`. Read it with the value-class capability, once per loaded library
  runtime. Invalid values fail clearly. `off` gives value-class capability
  unavailable with reason `:disabled`. Native-field and array-allocation
  capability derive from it, so they are also unavailable. Definitions fall
  back by policy. Array allocation fails clearly. Value-class AOT output throws
  `:value-capability-unavailable` `:disabled` where the form is evaluated
  (D17), also for native-field output. The capability report shows `:disabled`.
- Interaction: under policy `error`, `off` throws on each definition (D10). Use
  `off` with `silent` or `warn`.
- Rejected: start without preview (also disables other preview code); a
  per-type switch (D10 has no per-type overrides).
- Rationale: the application returns to identity semantics by configuration
  only.
- Commitment: firm.
