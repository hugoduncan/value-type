# Plan

## Approach

Implement the stable [design](design.md) and architecture D4. Task 013 is the
only implementation dependency. Do not use public diagnostics to construct or
accept results. No design blocker remains.

### Public boundary and validation order

Add both `into-array` arities to `src/value_type/array.clj`; exclude the core
name in `:refer-clojure`. Keep optional JDK calls in the existing adapter.
Extract small private component and seed validators for reuse. Preserve
`make-array` validation order, messages and error data.

Use this sequence order:

1. Require allocation capability, then validate the component, before calling
   `seq` or otherwise consuming input. Unsupported calls fail with
   `:array-unavailable`, regardless of component, seed or input validity.
2. For the explicit-seed arity, validate the seed before consuming input,
   including empty or nil input. Seed errors retain the allocator's reasons
   and have no index. No length is known yet; do not invent one in error data.
3. Obtain the input sequence through ordinary Clojure handling. Nil is empty;
   non-seqable input uses the ordinary Clojure exception. Empty inferred input
   produces `:empty-array-input` with explicit-seed guidance.
4. For each entry, check the proposed next length before appending, then validate
   the element. Use `:invalid-array-element`, `:component`, zero-based `:index`
   and `:reason` (`:null-element` or `:incompatible-element`). This includes
   index zero when the first element supplies the inferred seed. Do not include
   the input, buffer, unfinished array or rejected value in error data.
5. After successful buffering, call the existing `make-array` with the checked
   length and seed. Its repeated checks are harmless and preserve one allocation
   contract. Copy the buffer in order with ordinary array stores; return only
   after the copy completes.

Capability/component failures consume no input. Invalid explicit seeds consume
no input. Later failures may follow input effects; no rollback or full-consumption
promise applies. Chunked producers may realize ahead. Let realization exceptions,
ordinary input errors, native failures and JVM errors propagate unchanged.

### Buffer and length accounting

Use a call-local transient vector, made persistent for indexed copying. Traverse
one sequence cursor with `seq`/`first`/`next`; never count or restart the original
input. Buffer traversal is separate from input traversal. Do not retain buffers,
seeds or arrays in library state or pass the unfinished array to caller code.

Use a private checked-next-length operation in the actual buffering loop. Keep
the accepted count within 0 through `Integer/MAX_VALUE`, compute the next value
with non-narrowing arithmetic, and reuse `checked-length` before append or int
conversion. Test MAX_VALUE minus one advancing successfully and MAX_VALUE
advancing to `:invalid-array-length` with length 2147483648. Test this private
arithmetic boundary directly; do not allocate a huge buffer or add a public hook.
The integer limit is not an allocation guarantee. Storage costs O(n) references;
memory exhaustion can occur earlier.

## Slice order

1. **Construction and validation.** Add shared private validation, bounded length
   accounting, both public arities and focused success/error tests. Preserve the
   allocator contract. Covers acceptance 1, 2 and 4.
2. **Input effects and native semantics.** Prove no repeated input traversal,
   exception propagation, complete-only return and later store semantics. Run
   enabled/disabled flattening controls without diagnostic dependencies. Covers
   acceptance 3 and 5.
3. **Startup controls and packaging.** Add baseline-safe sequence checks using
   the existing JVM runner/configuration helpers. Extend isolated JAR consumers
   and run allocator/fallback regressions. Covers acceptance 6.
4. **Documentation and acceptance.** Update user/API/architecture documentation,
   run combined checks and record scoped structured evidence. Covers acceptance 7
   and the final verification of 1–6.

## Verification

Use `test/value_type/array_sequence_test.clj` and shared `array-test-support`
fixtures: zero-field, one-long, two-long and reference-field values, including
null reference payloads. Cover lists, vectors, lazy inputs, both arities, lengths
one and several, and explicit empty/nil input. Seeds must not add output slots.
Use observable non-caching Seqable input as well as lazy realization effects:
counting callbacks on a cached lazy sequence alone cannot detect a second walk.
Use real inputs and JVMs, not mocked capabilities.

Add `test-fallback/value_type/array_sequence_boundary_test.clj` with baseline-safe
child helpers. Test six configurations: Java 8; EA3 with neither flag, export
only, preview only, both flags, and both flags with `-XX:-UseArrayFlattening`.
Flags mean `--enable-preview` and
`--add-exports=java.base/jdk.internal.value=ALL-UNNAMED`. Cross each row with all
three fallback policies and both public namespace require orders. Unsupported
rows must reject both arities repeatedly without consuming observable input or
loading value fixtures. Supported rows construct both forms. Require complete
EDN outcomes, correct flags/reasons, no new fallback warnings, exit zero and a
120-second child timeout.

Follow the [runtime](../../../doc/development/runtime.md) and
[quality](../../../doc/development/quality-checks.md) commands. Use Scry structured
results; final CLI checks require exit zero and no failure EDN files. Record
commands, JVM versions, flags, host and acceptance mapping in `acceptance.md`;
retain evidence outside `target/` before later runs replace `.scry-results/`.

## Risks and review notes

- Changed EA APIs or missing Java 8/EA3 executables block verification, not
  justify skipped controls. No broader runtime claim is planned.
- Chunking and memory exhaustion prevent exact callback-count, streaming or
  universal maximum-length promises. No performance study is required.
- JAR build/clean deletes `target/`; preserve evidence elsewhere.
- Never invoke native diagnostics on null. Sequence tests need no diagnostics
  or flat-layout assertion.
- The design intentionally leaves validation order and boundary-test mechanics
  to planning; the decisions above resolve them without changing scope.
- Referenced architecture pages still say task 014 is open for review, although
  it is closed. This status inconsistency does not change the array contract or
  task 015 dependency. Reconcile it with architecture status in slice 4.

## Current progress

Slice 1 is complete. Empty/nil, explicit-seed, invalid-component and indexed
invalid-element checks pass on real EA3 arrays. Exact error maps exclude input,
rejected values and temporary storage. Non-seqable input uses ordinary Clojure
errors. Slice 2 input-effect tests now pass: non-caching traversal, early validation,
indexed rejection, unchanged input exceptions and chunked ordered output.
Slice 2 is complete: selected-slot stores preserve all other slots, rejected
stores preserve all contents, and construction state review found no retained
caller data or unfinished-array exposure. Sequence/allocation/layout checks pass
with flattening explicitly enabled and disabled. Slice 3 startup controls pass all 36 fresh-JVM combinations. Both arities
reject repeatedly before input effects on unsupported rows; supported rows
construct ordered arrays and explicit empty input. Supported JAR-only sequence consumers now pass. Unsupported JAR-only sequence consumers also pass on Java 8 and EA3 without
preview, including export-only EA3. The full build suite passes. Slice 3 is complete: the full fallback/loading
suite passes on Java 8 and EA3 with preview off/on, including all array startup
controls. Documentation and final acceptance are next. Design and approach are
unchanged.

Slice 4a completes user/docstring examples and generated API contract checks.
Slice 4b completes runtime commands and packaged sequence coverage.
Slice 4c completes architecture/release status, including closed task 014.
Final combined verification and acceptance remain next.

Slice 4d completes final quality checks and Quickdoc verification. Structured
and CLI runs pass. Final runtime/layout, fallback/loading and build verification
remain before acceptance.md. The design and approach are unchanged.

Slice 4e completes final supported-library and enabled/disabled layout checks.
Structured and CLI runs pass. Final fallback/loading and build verification,
then acceptance.md, remain. Design and approach are unchanged.

Slice 4f completes final fallback/loading verification on Java 8 and EA3
preview off/on. Structured and CLI runs pass. Final build verification and
acceptance.md remain. Design and approach are unchanged.

Slice 4g completes final full build verification. Structured and CLI runs pass,
including isolated JAR consumers. Only acceptance mapping and final architecture
status remain. Design and approach are unchanged.

Slice 4h completes acceptance mapping and final architecture status. All seven
criteria pass within the recorded JVM scope; see [acceptance](acceptance.md).
All implementation steps are complete. The task remains open for review.
Design and approach are unchanged.
