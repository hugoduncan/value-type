---
title: Benchmark value types against deftype
status: active
category: performance
tags: [benchmark, criterium, valhalla, jit, deftype, object-size, measurement-evidence]
related: [mementum/memories/valhalla-jdk-selection-java-cmd.md, mementum/memories/reflection-warning-check-new-jvm.md]
depends-on: []
---

# Benchmark value types against deftype

Source: task `006-method-call-benchmark`, `design.md`, `plan.md`,
`steps.md` and `implementation.md`; implementation commit `2f8cbc8`
and interpretation correction `f69243b`. The checks used Clojure
1.12.6, Valhalla JDK `27-jep401ea3` and Criterium `0.5.245-ALPHA`.
The reusable benchmark is `bench/value_type/bench.clj`.

## Convert batch time to operation time

In Criterium `0.5.245-ALPHA`, the value at
`[:data :stats :stats :elapsed-time :median]` in `last-bench` is
elapsed nanoseconds per sample. A sample is a batch, not one
expression evaluation. Divide by `[:data :stats :batch-size]` to
get time per evaluation. If the expression makes one pass over `n`
instances, divide again by `n` to get time per operation.

Omitting the batch division produced incorrect times in the first
implementation run. Keep these internal alpha-library paths in one
helper. Check their meaning again when Criterium changes.

## Keep the comparison symmetric without sharing call sites

A call on one captured value object can be folded or moved out of
repeated work by the JIT. The design probes found an apparent large
speedup with one instance that did not persist over many instances.
Blackholing the result alone does not prevent constant folding.

- Prepare many instances with different field values outside the
  measurement. Sum results in the measured loop and return the sum.
- Give each case and type kind its own pass function. A shared element
  call site sees both classes and can mix their JIT type profiles.
  Generate the paired functions from the same template.
- Use `Object` arrays with element type hints when comparing method
  calls. Typed value-class arrays can introduce flattened storage,
  which would also compare different array layouts.
- Keep unrelated loads out of the measured loop. Review found that a
  shared template loaded a second array in cases that did not use it.
- Check pass time at more than one array length. Approximate growth
  with the amount of work is a useful check against removed work,
  not proof that no optimization occurred.

Use the compiler blackhole options in `:bench`. Without them, this
Criterium version uses a runtime blackhole whose cost can dominate
these small operations. Check reflection warnings separately.

## Limit claims to the evidence

Unchanged method bytecode does not prove equal runtime speed for
identity and value classes. Record the JDK build and measurement
setup; repeat measurements on each new early-access build.

Runs at different array lengths do not establish run-to-run variance
or classification stability. Similar field-read and constructor/read
times can suggest escape analysis, but do not prove identical generated
code or removed allocations. Those claims need separate evidence.

After changing measured work, repeat both the recorded run and its
scaling check. Keep them from the same code and setup. Diagnose a
failed check rather than repeat unchanged runs until one passes.

## Measure standalone size separately from operation time

Allocation timing can be affected by escape analysis. It does not establish
how many bytes a materialized object occupies. For standalone shallow size,
use per-object evidence, not elapsed time, heap differences or nominal field
widths. This size excludes referenced objects and flattened array storage.

Keep paired declarations and supplied values equal except for the defining
macro and class name. Verify actual instance fields and value-class status.
Retain samples through record collection, with `Reference.reachabilityFence`.
Value-object identity comparisons do not prove separate allocations.

Select records by exact generated class and allocating thread. Require the
expected sample count, positive sizes, no freed targets and one common size
per class. Missing or inconsistent records are errors, not zero or a reason
to average. Keep all raw records for diagnosis.

JVMTI `SampledObjectAlloc.size` uses `GetObjectSize` semantics: the VM's
implementation-specific shallow storage approximation, including header and
padding. A size difference does not identify header contents or field
offsets, or prove a general saving. Capture the measurement JVM's build,
startup arguments, dependency versions, agent hash, alignment, compressed
reference settings and compact-header setting. Mark unavailable VM settings
explicitly as unknown.

## Verify the packaged agent route

For published Criterium `0.5.251-ALPHA`, use `criterium.agent/extract-agent!`
in a setup JVM, then start the measurement JVM with
`-agentpath:<absolute-path>` and `--enable-preview`. Check `attached?` at
measurement entry, not namespace load. Use `with-allocation-tracing` and
per-record `:object_size`. This route was verified on Valhalla
`27-jep401ea3+1-1`, macOS/aarch64; other builds and platforms need checks.

The presence of `criterium.agent.instrumentation/object-size` in that JAR
does not make it a usable startup agent. The artifact lacks an agent
manifest entry and compiled instrumentation agent class; its `-premain`
only logs and does not store the instrumentation. Recheck packaging and
initialization when changing versions. Keep measurement dependencies
isolated when a timing benchmark uses a different release.

## Test the published evidence, not only its helpers

Pure validation tests with synthetic data and successful file creation do
not prove that a real measurement publishes its required evidence. Separate
metadata-reader tests also do not prove that the entry point writes metadata.

Read the real launcher's raw and result files in an independent integration
test. Check required fields as well as agreement between files: both files
can omit the same field. Check class/layout evidence, supplied values,
selected records, complete rows and measurement-environment metadata.
Derive row expectations from raw records, not fixed host-specific byte sizes.

Keep pure state tests separate from native extraction and JVM integration.
Test publication with isolated files: save raw evidence before validation,
publish results only after success, remove stale runner-owned output before
setup, preserve unrelated files and propagate child failures.

Sources for these size and evidence rules: task `007-object-size-comparison`,
`design.md`, `plan.md`, `steps.md` and `implementation.md`; native route probe
in planning commit `8f6cc35`; evidence-test commits `413ae16` and `4b5ea7e`.
Review follow-ups confirmed that removing supplied values or environment
metadata from temporary evidence copies made the new checks fail.
Reusable entry points: `bench/object-size.sh` and
`bench/object-size-integration-test.sh`.
