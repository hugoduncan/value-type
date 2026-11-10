# Lazily loaded direct-call array adapter

## Intent

Remove reflective invocation from repeated array operations without losing
safe loading on unsupported JVMs, runtime capability checks, or native array
semantics. Keep optional JDK coupling small and local. Success means direct
JDK calls after discovery, not a promised speed increase.

## Context and problem

`value-type.impl.array` caches reflective methods for allocation and inspection.
It uses `MethodHandles.lookup().unreflect` to check access, discards the handles,
and calls `Method.invoke` for each operation. Discovery is already lazy and
cached. Reflection on each call is therefore a separate choice, not a requirement
of first-use capability detection.

The library ships source and loads on Java 8. Valhalla array APIs are optional
and need module exports. Allocation also needs enabled value-class support and
preview; ordinary-array inspection on EA3 needs exports but not preview.
Compile-time API presence alone proves neither deployment access nor semantics.

This design follows the purpose → context → decisions → embodiment → observed
qualities model in `~/projects/hugoduncan/architecture/ramora/INDEX.md`. The local
contracts are [array decisions](../../../ramora/value-type-array-design/decisions.md),
[array embodiment](../../../ramora/value-type-array-design/embodiment.md), and
[JVM boundary knowledge](../../../mementum/knowledge/value-array-jvm-boundaries.md).

## Scope

Include allocation, the component value predicate used by array operations,
and the three array diagnostic calls. Cover `make-array`, both `into-array`
arities, `array-info`, their capability report entries, and the allocation
round-trip self-check.

Exclude general capability-probe reflection, compiler and field-enforcement
changes, new public APIs, provider frameworks, method-handle optimization,
new JVM support, and changes to array construction or element access.
Tasks 027 and 028 remain separate. This task builds on closed task 026 and
has no functional dependency on 027 or 028.

## Decisions and ownership

### Separate capability validation from invocation

The baseline-safe boundary owns discovery, classified absence, cached results
and adapter selection. Optional internal code owns direct calls to the JDK.
The public API continues to own input validation and sequence construction.
No optional class imports or direct optional calls enter baseline-safe code.

Select the operation implementation once per loaded library runtime, when its
capability is first requested. Capability reports may trigger this discovery.
Publish a successful result only after access checks, adapter loading and the
applicable semantic check succeed. Keep allocation and inspection independent:
inspection must not trigger value-class probing, and allocation must not resolve
or require diagnostic methods. Choose the smallest internal separation that
enforces this rule; namespace names and function signatures belong in planning.

Reflection and access-check handles remain permitted during discovery. Supported
operation bodies use statically resolved JVM calls, including their component
predicate. No `Method.invoke`, Clojure Reflector dispatch, or method-handle
invocation remains on those paths. A cached Clojure function call is acceptable;
this task does not require direct bytecode calls from application call sites.

### Bind on the receiving JVM

Load optional code only after the relevant API and access prerequisites pass.
Retain the allocation round-trip check, exercised through the selected direct
allocator rather than a separate reflective implementation. Runtime results
remain authoritative; do not embed the build JVM's capability result in an
application artifact. Keep the source-only library artifact. Application AOT
must not eagerly load optional code merely because it requires the public API.
Target-specific compiled adapter classes need not become Java 8 portable.

### Preserve behavior, not internal representation

Keep public signatures, validation order, error keys and classified reasons.
There is no ordinary-array fallback. Inspection still returns all five facts
or fails. Reject invalid diagnostic inputs before native calls; never call
native diagnostics with null. Preserve startup configuration and cache scope.

Native operation failures propagate without reflective wrappers and do not
poison a successful capability result. Unexpected adapter compilation, linkage,
and JVM failures propagate; do not turn them into generic capability absence.
Cache implementation state only, never caller arrays, seeds or sequences.
Internal method maps may change; no compatibility layer is required.

## Alternatives and qualities

- Keep cached reflection: smallest change, but retains per-call reflection.
- Cache typed method handles: can be efficient, but needs typed call sites and
  adds machinery that this boundary does not otherwise need.
- Eager direct imports or build-time selection: simple supported path, but
  breaks baseline-safe loading or trusts the wrong JVM.
- Selected lazy direct calls: retains runtime checks and permits normal JVM
  optimization, at the cost of a small optional-code boundary.

Correctness and safe loading take priority over speed. Main risks are accidental
reflection in Clojure output, eager optional loading, coupled capabilities, and
changed error precedence. Direct calls still link at runtime and need exports.

## Acceptance

1. Generated bytecode shows direct JDK calls for every scoped operation and
   no reflective or method-handle invocation in their operation bodies. Evidence
   identifies compiled functions and call targets, not only source spelling.
2. Fresh-process require and require-only application AOT/load checks pass
   with these JVM configurations, using the profiles in
   `dev/jvm/jvms.edn`:
   - Java 8: baseline launch options (`:baseline` role, `:plain` profile),
     with neither preview nor export flags.
   - EA3: all four independent preview/export on/off combinations
     (`:valhalla` role, `:plain`, `:preview`, `:export` and
     `:preview-export` profiles).

   These configurations apply to both AOT compilation and loading, as well
   as require checks. Unsupported paths do not load optional implementations.
   Export-only inspection works; disabled value classes prevent allocation
   but not ordinary-array inspection.
3. Existing public semantics, validation order, capability reports, classified
   failures and native failure propagation pass. A rejected native allocation
   does not prevent a later valid allocation. Allocation discovery uses the
   selected direct allocator; inspection does not force allocation discovery.
4. JAR-only consumers pass supported operations and unsupported-JVM loading.
   Evidence covers repeated use and records JVM builds, flags and commands.
5. A bounded before/after measurement reports first-use cost and warmed repeated
   small allocations and inspections. Record variability and limits; no minimum
   speedup is required. Explain any repeatable regression before acceptance.
6. Update array architecture documents and project state. Relevant suites and
   quality checks pass. Record evidence and remaining limits in implementation.md.

Create plan.md, steps.md and implementation.md before execution.
