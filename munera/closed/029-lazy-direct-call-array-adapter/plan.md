# Implementation plan

## Approach

Preserve the public API and change only the invocation boundary. The stable
[design](design.md) is the acceptance authority. Existing
[implementation notes](implementation.md) record the completed design reviews.
Do not change compiler, field, element-access or sequence-construction behavior.

### Internal boundary

Keep `value-type.impl.array` baseline-safe. It owns the two existing delays,
classified discovery failures, access checks and capability accessors. Retain
reflective discovery and `MethodHandles.lookup().unreflect` access checks.
Keep their exception handlers narrow: optional namespace loading, compilation,
linkage and semantic checks occur outside absence-classification handlers.

Add two optional source namespaces, with no dependency on each other:

- `value-type.impl.array-allocation`: `value-class? [component]` calls
  `Class.isValue`; `allocate [component length initial]` calls
  `ValueClass.newNullRestrictedAtomicArray`, with explicit Class and int types.
- `value-type.impl.array-inspection`: `value-class? [component]` calls
  `Class.isValue`; `inspect-array [array component]` returns the five existing
  facts through that predicate and the three direct ValueClass diagnostics.

The small predicate duplication avoids a third namespace and keeps allocation
independent of diagnostic resolution. Use reflection warnings and compiled
bytecode checks to verify static resolution. Do not add a provider abstraction.

After each capability's own API and access checks pass, use lazy symbol
resolution to obtain its functions. Cache those functions in its successful
result: allocation uses `:allocator` and `:predicate`; inspection uses
`:inspect-array`. Preserve `:available?` and absence data. Existing baseline
wrappers call these functions; public signatures and validation order stay
unchanged. Internal method-map compatibility is not required.

Change `passes-round-trip?` to take an allocator function. Run the existing
probe round trip through the selected direct function before publishing
allocation success. Probe constructors and field observations can still use
reflection; they are discovery work, not repeated operation bodies.
Inspection neither reads value-class capability nor forces allocation discovery.
Allocation never loads the inspection namespace. Cache no caller data.

### Receiving-runtime safety and verification

Do not statically require either optional namespace from baseline code. Keep
the library JAR source-only. Reuse the child-JVM harness, facts-selected
executables and `aot-fixture/compile-producer`; delete application fixture source
before load checks. For require, AOT compilation and AOT loading, cover:

| Role | Profiles |
| --- | --- |
| `:baseline` | `:plain` |
| `:valhalla` | `:plain`, `:preview`, `:export`, `:preview-export` |

Use `dev/jvm/jvms.edn`, not copied flags or the preview-enabled `:test` alias,
for these children. Give each first-discovery observation a fresh process.
Assert optional namespaces are absent after require-only compilation/loading,
and remain absent for unavailable operations. Inspect saved AOT output for
unexpected optional adapter classes. Also compile a require-only application
on EA3 `:plain` and load it on EA3 `:preview-export`; successful operations
must use receiver capabilities, not producer results. Target-specific adapter
classes themselves need not load on Java 8.

Extend existing runtime, cross-JVM and isolated JAR tests instead of building
another matrix harness. Use real JVM operations and state observations. Preserve
existing controlled capability tests as explicitly controlled evidence, separate
from actual JVM support. Check native rejection followed by valid allocation,
all error keys/reasons, sequence effects and report agreement. Run invalid public
diagnostic inputs in disposable children before shared valid-array tests;
never pass null to a native diagnostic.

Compile the optional functions in a temporary EA3 output directory. Inspect
operation methods and their helper call paths for direct `Class.isValue` and
all four scoped ValueClass targets. Reject `Method.invoke`, Reflector dispatch
and method-handle invocation there. Retain function names, JVM descriptors,
commands and disassembly; this is a focused task check, not task 028's general
snapshot system. Exercise inspection compilation with exports and no preview.

### Bounded measurement

Create a task-specific runner under `dev/bench/array-adapter/` before changing
production code. Compare the unchanged reflective revision and final revision
with the same runner, EA3 build, flags, dependencies and machine. Record exact
revisions and executable identities. Keep evidence in this task directory,
not only under `target/`, which JAR builds delete.

Include namespace loading and fixture creation in setup timing (not JVM startup).
Value-class fixture creation precedes timing; array capability discovery does not.
Use a volatile result sink and a per-operation clock check in both revisions;
these add shared overhead, so measurements are not isolated JDK call costs.

Measure first public allocation and first inspection in separate fresh processes,
after namespace and fixture setup but before capability discovery. Report setup
separately. Measure warmed `make-array` at lengths 0 and 4 and `array-info` on
prepared ordinary and restricted arrays. Use five forks per revision, one second
of warmup and five 200-ms samples per warmed case, with a 120-second timeout
per fork. Use five fresh forks per first-use case and five warmed forks that
run all warmed cases. Consume results and vary seeds/arrays. Each raw record
identifies revision, build, flags, host, case, fork, phase, sample, elapsed
nanoseconds, operation count and GC observations. Record medians, ranges and
normalized time per operation. Investigate consistent regressions across forks;
report limits rather than claim a minimum speedup.

## Risks

- Clojure can emit reflection despite direct-looking source; bytecode is the gate.
- Eager AOT resolution or shared optional code can couple capabilities. Fresh
  namespace observations and exported, preview-free inspection test this boundary.
- Broad discovery catches can hide compilation or linkage defects. Keep loading
  outside those catches; native failures must not replace successful cache state.
- Native null diagnostics can crash EA3. Only public validation receives null.
- Required JVMs, `javac`/`javap` or dependencies might be unavailable. Report this
  as missing evidence, not a pass or support absence. No blocker is known now.
- First-use compilation and GC can dominate short measurements. Preserve samples
  and distinguish variability from a repeatable regression.

## Slice order

1. Capture the reflective baseline and establish the measurement runner.
2. Select the direct allocation functions and retain the semantic self-check.
3. Select independent direct inspection and preserve diagnostic validation.
   Both operation wrappers now use selected functions. Both discovery directions
   and disabled-allocation inspection/report agreement are verified.
4. Verify bytecode, fresh loading, receiving AOT and isolated JAR consumers.
   The focused bytecode gate now passes for both adapters and three baseline
   wrappers, compiled with exports and no preview. Fresh public require and
   unavailable-operation loading checks pass for both require orders in all five
   rows. Same-profile require-only AOT compilation and source-free loading now
   pass in all five rows, with no adapters loaded or emitted. Cross-profile
   receiving-runtime repeated operations also pass. Focused array, report and switch cross-JVM regression also passes.
   Isolated JAR consumers now pass repeated operations and optional-loading
   checks in all four build-suite rows. The full cross-JVM suite also passes under its Java 8 parent.
5. Compare measurements, run regression/quality gates and update architecture.
   Measurement comparison is complete. Warmed samples improved in this run;
   first use adds about 3–4 ms from lazy source loading and compilation,
   supported by fresh preloaded controls. The full runtime suite and both explicit flattening controls pass.
   Full cross-JVM and all three fallback parent runs pass. Forced-JIT runtime
   and ordinary-JDK smoke gates also pass; forced compilation covers only the
   runtime parent. Architecture now describes the implemented boundary and measured limits.
   Quality gates and the final acceptance audit pass. See `evidence/comparison.md` for limits.

[Steps](steps.md) gives concrete actions in this order. Append dated decisions,
commands, results and limits to `implementation.md` during execution. Commit each
completed implementation step with its evidence. Acceptance 1 maps to slice 4;
2 to slices 3–4; 3 to slices 2–4; 4 to slice 4; 5 to slices 1 and 5; 6 to slice 5.
