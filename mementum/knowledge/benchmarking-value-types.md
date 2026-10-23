---
title: Benchmark value types against deftype
status: active
category: performance
tags: [benchmark, criterium, valhalla, jit, deftype]
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
