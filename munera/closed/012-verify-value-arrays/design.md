# 012 Verify value arrays

## Intent

Resolve the JVM-level uncertainties in the proposed value-array API before
implementation. Supply reproducible evidence and a clear explanation of
which contracts the selected JVM supports. A rejected assumption is a valid
result; an unsupported claim is not.

## Problem and context

The [array design](../../../ramora/value-type-array-design.md) proposes native,
null-restricted atomic arrays, explicit initialization, collection construction
and layout diagnostics. It does not promise flattening or array fallback.

The [typed-array investigation](../../../ramora/defvaluetype-design/typed-array.md)
proves a storage benefit for one-long elements on Valhalla
`27-jep401ea3+1-1`. It does not resolve empty allocation, all diagnostic
contracts, wider-class eligibility or the capability boundary needed by a
library API. The proposed design marks these as verification work.

Use the installed Valhalla EA3 build as the primary target:
`~/.local/jdks/jdk-27.jdk/Contents/Home`. Record its actual version rather than
assuming it matches. Java 8 is an unsupported-runtime control, not an array
implementation target. If the required JVM is unavailable, record the obstacle;
do not silently substitute a different build.

Relevant sources:

- [Decisions](../../../ramora/value-type-array-design/decisions.md).
- [Verification assumptions](../../../ramora/value-type-array-design/verification.md).
- Existing probes in `spike/typed-array/`.
- [Class fallback boundary](../../../ramora/defvaluetype-design/fallback.md).

## Questions and scope

1. What are the exact allocation and diagnostic signatures and access
   requirements? Distinguish API absence, disabled preview, missing exports,
   rejected component classes and invalid values.
2. Can a null-restricted atomic array have length zero when supplied a valid
   explicit seed? What restrictions apply to that seed, including at length
   zero? Is there a supported seed-free empty route in the selected internal
   API, or is the design's explicit-seed overload justified?
3. Which component classes are accepted? Cover zero-field, one-long,
   two-long and reference-field `defvaluetype` classes. Include identity and
   primitive classes as rejection controls. Record value status separately
   from allocation eligibility and physical flattening.
4. Are all slots initialized as expected? Do Clojure `aget`, `aset` and
   `alength`, and representative Java array access, preserve contents and
   enforce null and component-type restrictions after construction?
5. What do the diagnostic methods actually report for ordinary reference
   arrays, restricted arrays, primitive arrays and invalid inputs? Distinguish
   false results from unsupported queries and failures.
6. Does disabling flattening preserve the semantic contract? Explain atomic
   element access, mutable array slots and immutable value objects without
   claiming compound-update safety or a synchronization protocol.

Explain findings from runtime evidence and, where necessary, the matching
JDK source or documentation. Label a mechanism as unresolved if the evidence
only establishes an outcome. Do not infer implementation causes from class
names or timing.

## Boundaries and ownership

Keep verification outside production namespaces. The probe owns temporary
arrays and results; each fresh JVM owns its startup configuration. The JVM
supplies the array invariants. Do not emulate missing capabilities or alter
module access at runtime.

Follow the existing isolated-spike and retained-evidence pattern. Prefer
small direct API probes over a prototype public wrapper: a wrapper could hide
the behavior under investigation. No general experiment framework is needed.

Preserve historical measurements. Update proposed architecture only where
new evidence resolves or contradicts an assumption. Separate observed facts
from proposed public validation behavior. A verified JVM failure does not
by itself select a new library error contract.

## Acceptance

1. Documented commands reproduce a bounded semantic matrix on the recorded
   JVM, with machine-readable results and retained raw diagnostics. Record
   JDK build, Clojure version, OS, architecture and exact JVM flags.
2. The report answers every question above, with links to probe cases and
   evidence. Every case has a pass, rejection or explicit unresolved result;
   no skipped case is reported as verified.
3. Fresh-JVM controls cover exports present/absent, preview enabled/disabled,
   and flattening enabled/disabled where supported. An ordinary Java 8 control
   records API absence without requiring optional JDK classes at load time.
   Expected failures retain their actual exception/cause information.
4. Zero-length, one-element and multi-element cases establish seed behavior,
   initial contents, replacement and null/type store checks. Invalid lengths
   include negative input; do not attempt allocations near the heap or JVM
   length limit merely to provoke exhaustion.
5. The report distinguishes value status, null restriction, atomic access and
   flattening. It explains the limits of the existing one-long measurements
   and does not claim a storage or speed benefit for unmeasured layouts.
6. Design assumptions and verification gates are updated from the evidence.
   Any remaining implementation blocker names the unanswered question and
   the decision or further experiment needed. Contradictions are surfaced,
   not hidden by weakening the contract or adding fallback.
7. Runnable instructions, the report and retained evidence are linked from
   the array design and Ramora index. Production code and existing runtime
   behavior are unchanged; documentation links and whitespace checks pass.
   If shared test or dependency configuration changes, run its affected
   checks and record the result.

## Out of scope and adjacent work

Implementing the public array API, its production capability adapter or
collection builder; changing class generation or class fallback; choosing
new public API semantics beyond evidence-driven design corrections; broad
JDK support; non-atomic arrays; wrappers or persistent collections; concurrency
stress testing; clone/copy guarantees; timing, allocation or GC benchmark
campaigns; optimization of wider layouts. These are separate tasks if needed.
