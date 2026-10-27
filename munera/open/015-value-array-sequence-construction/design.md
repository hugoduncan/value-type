# 015 Value-array sequence construction

## Intent

Build native null-restricted atomic arrays from finite Clojure input sequences
without requiring callers to manage allocation seeds and element copying.
Preserve input order and avoid repeated input traversal.

## Context and dependencies

Depends on [task 013](../../closed/013-value-array-allocation/design.md), which supplies
the allocator and capability boundary. The recommended order places
[task 014](../014-value-array-diagnostics/design.md) first, but diagnostics are
not an implementation or acceptance dependency.

Follow the [array contract](../../../ramora/value-type-array-design.md) and
[D4](../../../ramora/value-type-array-design/decisions.md#d4-explicit-initialization-and-bounded-array-lengths).
Task 012 established explicit-seed allocation, including zero length, on the
recorded EA3 build. It did not verify sequence realization or public length
validation. Its direct-allocation performance figures do not describe this
constructor's temporary storage.

## Scope and contract

The public provider is `value-type.array`:

```clojure
(va/into-array Point values)
(va/into-array Point (Point. 0) values)
```

- `[component values]`: accept non-empty finite seqable input. Use its first
  element as the allocation seed. Reject empty input with `:empty-array-input`
  and guidance to use the explicit-seed arity.
- `[component initial values]`: accept finite seqable input, including empty
  input. Validate the explicit seed even for empty input; it is not an added
  output element. Clojure `nil` input is an empty sequence.

Both arities preserve order and exact length, reject null or incompatible
input elements, and apply the allocator's component and length constraints.
Non-seqable input fails through ordinary Clojure input handling; this task
adds no separate collection-type error taxonomy. Infinite input is unsupported.

Use the architecture's error keys, with element indexes for invalid sequence
entries. Avoid retaining the whole input in exception data. Errors from input
realization propagate without being reclassified as capability failures.
Return only a complete array. Failure does not undo input side effects.

## Boundaries and approach for planning

Follow D4: traverse and validate the original input once into temporary
storage, then allocate and copy. Do not count the original input and traverse
it again. Copying buffered values is not a second input traversal. Keep the
JVM-specific work in the existing adapter and use the established allocator
contract; diagnostics are not needed.

Construction owns temporary storage and the unfinished array. Neither remains
in library state after completion. The caller owns returned mutable slots;
the JVM enforces later type and null stores. The sequence API adds no concurrency
or identity guarantees.

Temporary storage costs O(n) references; memory exhaustion can occur before the
integer length limit. Enforce that limit without narrowing or overflow. A
zero-copy or streaming promise would contradict this design. Incremental array
growth and an indexed builder are alternatives for separate work, not hidden
requirements here.

Planning must specify early validation/capability ordering and input effects.
Do not promise exactly one callback execution per requested element for all
chunked lazy producers; require no repeated traversal by this API. On failure,
no guarantee is made that the rest of the input is consumed.

## Acceptance

1. Both arities construct arrays from finite lists, vectors and lazy inputs.
   Cover one and several elements, explicit seeds different from the first
   input element, and the verified primitive-field and reference-field types.
   Output length, component type, order and contents match the input.
2. Empty and nil input reject in the inferred-seed arity and succeed with a
   valid explicit seed. Null and wrong-type explicit seeds reject even for
   empty input. Invalid components and null/wrong-type elements fail clearly;
   sequence-element errors identify the correct index.
3. Observable finite inputs verify no repeated traversal. Input exceptions
   retain their identity or original cause as appropriate and are not hidden
   as capability failures. Document the failure-side-effect boundary.
4. Length accounting cannot truncate or overflow. Verify its rejection boundary
   without realizing billions of values or exhausting the heap. Planning may
   select a focused internal arithmetic check; do not add a public test hook.
5. Returned arrays retain the allocator's semantics through ordinary Clojure
   access and stores, also with flattening disabled. Tests do not require
   task 014 or a flat layout. No partial array escapes from a failed call.
6. Unsupported-runtime loading and relevant capability controls still pass.
   Existing allocation and class-fallback behavior remain unchanged. An
   isolated packaged-source consumer uses both public arities.
7. Usage examples, API reference, release notes and architecture status explain
   empty input, seeds, finite-input limits, errors and temporary allocation.
   Relevant library, fallback/loading, packaging and quality checks pass with
   structured results. No unmeasured allocation or speed claim is added.

## Out of scope

Indexed generators, infinite or streaming input, primitive arrays, reducers
or transducers as new public interfaces, new fallback modes, diagnostics,
non-atomic storage, wrappers, parallel construction and performance studies.
