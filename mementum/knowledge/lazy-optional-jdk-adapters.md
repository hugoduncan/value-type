---
title: Verify lazy direct-call adapters for optional JDK APIs
status: active
category: architecture
tags: [capability, lazy-loading, bytecode, aot, negative-tests]
related: [mementum/knowledge/value-array-jvm-boundaries.md, mementum/knowledge/controlled-fallback-boundaries.md, mementum/knowledge/testing-library-artifacts.md, mementum/knowledge/benchmarking-value-types.md, mementum/memories/observe-capability-state-before-report.md]
depends-on: []
---

# Lazy optional JDK adapters

Source: task `029-lazy-direct-call-array-adapter`, its design, plan, steps
and implementation notes. Key evidence: direct selection `370509ff` and
`9ae7ef12`, bytecode `0d2b229e`, AOT `e6aaf5e1` and `67b9aafd`, damaged
artifacts `facfc826`, measurement `79d19617`, access checks `8dc0b519`.
Verified scope: Clojure 1.12.6, Java 8 and Valhalla `27-jep401ea3+1-1`.
These checks do not establish support on another JVM build.

## Discovery reflection does not require repeated reflection

Keep optional imports and typed JDK calls in separate source namespaces.
Baseline-safe discovery checks API signatures and access first, then uses
`requiring-resolve` to select functions. Cache those functions, not caller data.
Access-check success should return status, not obsolete reflective Methods
that selection discards. Keep access status distinct from operation state.

Keep capabilities independent when their prerequisites differ. Array
allocation needs value support; ordinary-array inspection needs exports but
not preview on EA3. Separate adapters prevent either discovery path from
loading the other. Small predicate duplication can be simpler than shared
optional code that couples them.

Run the semantic self-check through the selected allocator before publishing
success. Keep optional loading, compilation, linkage and semantic checks
outside the handlers that classify API/access absence. A missing adapter is
a broken library artifact, not evidence that the JVM lacks an API.

## Prove direct dispatch in compiled output

Direct-looking Clojure source and no reflection warnings are not sufficient.
Compile on the target JVM and inspect invocation instructions. Include
operation wrappers, helper functions and forwarding methods. Require each
intended JDK target; reject `Method.invoke`, Reflector dispatch and method-handle
invocation on those paths. Discovery reflection is outside this check.

The reusable example is `dev/bench/array-adapter/check-bytecode.clj`.
Bytecode proves dispatch shape, not speed or safe loading on another JVM.

## Require, AOT and receiving-runtime selection are separate checks

Use fresh processes for first-discovery observations. Check public require,
require-only application AOT compilation, and source-free application loading.
Assert that optional namespaces remain unloaded and that require-only AOT
emits no adapter classes. Unavailable operations must leave their adapters
unloaded. Check Java 8 plain and all independent preview/export combinations
on EA3 with facts-selected launch profiles.

A same-profile producer and consumer cannot prove receiver-local selection.
Also compile a require-only application without support enabled, delete its
source, then load it on a capable receiver and perform repeated operations.
Keep the library source-only; target-specific adapter classes need not load
on Java 8. Test JAR-only consumers separately from application AOT.

## Test artifact failures after prerequisites pass

In isolated source copies, remove an adapter, introduce a real compiler error,
or compile it and change its class-file major version to an unsupported value.
Exclude the original source path so it cannot repair the damaged fixture.

Verify API/access prerequisites first. Give each capability accessor and
public operation a fresh JVM. Require the expected escaping failure and no
translation to capability absence. Run both independent adapters. These
negative controls verify the error boundary, not JVM support. See
`test/cross-jvm/value_type/array_adapter_failure_test.clj`.

## Measure first use separately from warmed operations

Lazy source compilation adds first-use work even when direct calls reduce
warmed operation time. Separate namespace/fixture setup, first discovery and
warmed calls. State which prerequisite discoveries setup already triggered.
Calling `report` before timing can consume the discovery being measured.

Use fresh-process preloaded-adapter controls to investigate a repeatable
first-use regression without forcing capability discovery. Treat preloading
as an experiment, not a startup fix: eager loading breaks the baseline-safe
contract. It also changes compiler/class-loading state, so timing differences
are not an exact additive cost breakdown.

Task 029 observed lower warmed times and about 3–4 ms extra first-use cost
on its host. Those figures are not thresholds or performance guarantees.
