# Fallback lifecycle

Part of [controlled fallback](../fallback.md).

## D13. Fallback lifecycle

- Subject: state ownership, warning frequency and AOT representation.
- Choice: the probe namespaces (`impl.capability`, `impl.field-capability`,
  `impl.array`) own the cached capability results. `impl.runtime` owns the
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
- Rationale: each probe owner caches its own result, so readers that do
  not need policy (the capability report) never read it; application
  policy must not be bypassed by dependencies compiled under another
  policy.
- Qualities: bounded diagnostics and predictable deployment; AOT adds a
  guard at form evaluation, not per-instance overhead.
- Commitment: firm. Transfer, concurrent-warning and source-free AOT tests
  must verify the do barrier, analysis macro and runtime guard.
