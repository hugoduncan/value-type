---
title: Bound JVM setup, execution and process cleanup
status: active
category: testing
tags: [jvm, subprocesses, timeouts, java8, scry]
related: [mementum/knowledge/jvm-selection-and-launcher-boundaries.md, mementum/knowledge/validating-retained-jvm-evidence.md, mementum/memories/scry-pass-true-with-zero-tests.md]
depends-on: []
---

# Bound JVM setup, execution and process cleanup

Source: task `027-jvm-facts-test-tiers-and-releases`, implementation Slices
6k–6x and process-lifecycle and executable-verification review follow-ups.

## Setup needs its own bound

A test-runner timeout cannot stop a JVM build check that blocks before the
runner starts. Executable resolution, cached provisioning and archive
installation all invoke build verification. Keep that process bounded too.

`value-type.jvm-executable/verify!` uses a 30-second default, file-backed
stdout/stderr capture, forced termination and bounded termination waits.
Files avoid pipe backpressure and unbounded reader joins. Errors retain
role, platform, expected build, executable, timeout, output and termination
status. Preserve Java 8 APIs. Test with a real stalled executable, and check
both diagnostics and PID termination (`35e10fa0`).

## Timeout is not proof of process cleanup

Stopping a Babashka launcher can leave Clojure classpath resolution or Scry
children running. A shell timeout with a kill grace also left orphan JVMs
in local emulation. Check descendants explicitly after process-heavy runs.

For launcher integration tests, stop and await children before parents so
parents can reap their children. Bound cleanup separately from execution.
Verify recorded handles are stopped before deleting the temporary project;
retain the project and output when cleanup fails.

The test-only Babashka supervisor in
`test-resources/forced-jit/supervise.clj` provides process-tree support without
loading `ProcessHandle` in the Java 8 test parent. A real child readiness
signal exercises the timeout path without waiting for the full execution
limit (`40d88938`). This is test support, not a reason to change production
launcher behavior.

## Preserve what timing tests distinguish

A child timeout includes JVM startup; a fixed budget that works natively can
fail under emulation before useful work starts. Measure isolated serial and
concurrent startup before changing the fixture.

Increasing a timeout can silently remove the behavior under test. For
permit-wait exclusion, require:

- Startup plus work fits within the process timeout.
- Queue wait plus work exceeds that timeout.
- The exact concurrency-overlap assertion still holds.

For aggregate timeout tests, keep a marker after the base timeout but within
the aggregate allowance, then work beyond the aggregate limit to require
termination. Preserve these distinctions when adding startup allowance
(`f9246f28`, `67bf0377`).

## Diagnose grouped runs without changing their grouping

An empty Scry failure directory after timeout is not a pass. Require a
complete passing result and nonzero test count. Passing isolated vars do
not explain a grouped timeout or replace the full gate.

For grouped diagnostics, Scry 0.1.61 supports a synchronous
`:progress-callback` with each completed var's canonical result. Use one
`scry/run` call to preserve discovery and fixture grouping. Separate per-var
calls change those conditions. Callbacks identify completed vars, not the
currently stalled var; pair them with bounded external process observations
(`4d898aaa`). Do not infer coverage from progress dots or keep increasing a
full-suite limit from isolated timing estimates.
