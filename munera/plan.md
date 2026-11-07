# Plan

Source: residual (stressor) analysis of commit `5073893f`. Design: defvaluetype
D15–D24 and the changed decisions; the
[embodiment gap table](../ramora/defvaluetype-design/embodiment.md#decided-changes-not-yet-implemented)
lists each gap.

1. `023-capability-probe-hardening`: format table (D15), probes on
   `java.lang.classfile` (D16), semantic self-check without the AOT part (D17),
   classified absences (D12).
2. `024-fallback-policy-first-read`: policy at first read (D10, D13) and
   `:fallback-policy` in the report (D14). Independent of 023.
3. `025-value-class-switch`: `value-type.value-classes` (D18) without the AOT
   part. After 023 (same probe namespace and reasons).
4. `026-compiler-boundary-and-receiving-expansion`: compiler boundary (D19),
   receiving expansion (D20), AOT initialization check (D17, D18). After 023
   and 025.
5. `027-jvm-facts-test-tiers-and-releases`: facts file (D21), test tiers with
   the forced-JIT row (D22), tested-release installation (D24). Independent.
6. `028-disassembly-snapshots`: D23. Last: 023 and 026 change emitted output.
