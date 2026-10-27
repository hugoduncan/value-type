---
title: Preserve sequence traversal and input-effect boundaries
status: active
category: testing
tags: [clojure, sequences, arrays, validation, side-effects]
related: [mementum/knowledge/value-array-jvm-boundaries.md]
depends-on: []
---

# Sequence traversal and input effects

Source: task `015-value-array-sequence-construction`, its design, plan,
steps and implementation notes. Construction: `b4e0ba0`; observable-input
tests: `2587ce4`; failure-consumption review fix: `d137ba8`.
Reusable examples: `test/value_type/array_sequence_test.clj`.

## Traversal is not realization

A cached lazy sequence can hide a second traversal: its callbacks need not
run again. Callback counts alone cannot prove that an API walks input once.
Use a real non-caching `Seqable` that supplies a fresh lazy cursor on each
`seq` request. Count cursor requests and record realization effects, then
check ordered output. Keep ordinary lists, vectors and chunked inputs as
separate success cases.

Chunked producers can realize entries before the consumer requests them.
A single-traversal contract does not promise one callback per requested
entry. Test output order without imposing an exact chunk size or callback
schedule.

## Failure tests must not strengthen the contract

The sequence constructor promises neither rollback of input effects nor
full consumption after failure. Its first failure-effects test required an
exact effects vector, which also required stopping at the rejected entry.
Review changed that assertion to check the valid prefix and rejected entry
in order while permitting later effects. Apply this rule to unchunked inputs
too: their current behavior is not an API guarantee.

Keep separate assertions for the rejected index, no returned partial array
and unchanged input exceptions. Use a known thrown object to test exception
identity, then verify that a later valid call succeeds. Do not reclassify
input realization failures as unavailable JVM capability.

## Validation order is an observable boundary

For `value-type.array/into-array`, allocation capability and component checks
precede `seq`; explicit-seed validation also precedes input consumption.
Test these early failures with observable input that must remain untouched.
Unsupported-JVM controls can use ordinary placeholders without loading value
fixtures. Diagnostic access does not establish allocation capability.

The constructor buffers and validates input once, then allocates and copies.
Copying the buffer is not another input traversal. Keep input, temporary
storage and unfinished arrays call-local, and exclude them and rejected
values from exception data. Complete-only return does not undo input effects.
Temporary storage costs O(n) references; direct-allocation measurements do
not establish this constructor's memory cost or speed.
