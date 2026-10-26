---
title: Validate retained JVM evidence without changing it
status: active
category: testing
tags: [jvm, retained-evidence, validation, mutation-testing, subprocesses]
related: [mementum/knowledge/benchmarking-value-types.md, mementum/knowledge/value-array-jvm-boundaries.md, mementum/knowledge/verifying-development-quality-checks.md]
depends-on: []
---

# Validate retained JVM evidence without changing it

Source: task `012-verify-value-arrays`, its plan, steps and implementation
notes. Review fixes: required semantic contracts `211eee7`, read-only
validation `1242546`, isolated-process configuration `bc5196e`, result status
`315ed6f` and discovered signatures `c956fa4`.

This extends the published-evidence checks in
[benchmarking-value-types.md](benchmarking-value-types.md). It applies when
future experiments reuse retained results rather than rerun their JVMs.

## Validate the contract, not only agreement

Equal counts, unique case IDs and agreement between configurations can all
pass when both results are wrong. The array validator initially accepted a
required diagnostic changed to false in both layouts.

Require the intended case IDs, operations, inputs, status and values. A
correct value must not make an unresolved or rejected observation pass.
For discovery evidence, require parameter and return types, not just a
successful discovery status. Check any absence claim used by the design,
such as no seed-free overload of the selected allocator, without rejecting
unrelated API members.

Keep semantic assertions independent of optional JVM fixtures so both live
and retained observations can use them. Retained validation must not load
value classes or require the experiment's JVM.

## Check every process against its intended configuration

Raw logs and normalized records can agree but belong to the wrong matrix
cell. Copying default-layout isolated processes into the disabled-layout
cell exposed this gap.

Validate each child process's recorded runtime and flags against the expected
cell, including isolated failure probes. Do not trust only the parent run's
environment or directory name. Results can share an adjacent environment
record; make that relationship explicit rather than assuming every result
has top-level metadata.

For expected fatal outcomes, require query-entry evidence, case identity,
exit status, signal and fatal log. Reject stale successful result files.
Do not turn a crashed process into a false diagnostic or a Java exception.

## Keep record creation separate from validation

The first retained validator regenerated isolated records and changed their
fatal-log paths. That could overwrite the evidence it was meant to check.

Only the experiment runner should create records. A retained validator should
read existing records and compare them with raw process evidence. Use
portable relative references within the evidence directory, not paths
rewritten for the current checkout.

Test this boundary on disposable copies. Inject one defect at a time:
missing/renamed cases, wrong semantic values, incompatible status, empty or
changed signatures, wrong runtime/flags and substituted process evidence.
Require each invalid copy to fail while an unchanged copy passes. Compare
file paths and content hashes before and after validation on both success
and failure. Never repair historical evidence as a side effect of checking it.

Reusable examples: `spike/value-array-semantics/validate_matrix.clj`,
`validate_startup.clj`, `validate_isolated.clj` and `check_validation.clj`.
